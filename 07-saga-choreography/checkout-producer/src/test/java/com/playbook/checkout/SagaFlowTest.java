package com.playbook.checkout;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.playbook.checkout.model.CheckoutRequest;
import com.playbook.checkout.service.CheckoutProducerService;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.context.EmbeddedKafka;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * End-to-end integration test for the saga choreography checkout producer.
 *
 * <p>This test verifies that the checkout-producer correctly publishes
 * {@link CheckoutRequest} events to the {@code checkout-requested} topic.
 * An in-process Kafka broker is started via {@code @EmbeddedKafka}.</p>
 *
 * <p><strong>Note on full saga testing:</strong> The complete saga flow spans
 * four independent Spring Boot services (checkout, inventory, payment, shipping).
 * This test covers only the producer side. Testing the full choreography
 * end-to-end would require starting all four application contexts, which is
 * better suited for a Docker Compose-based integration test.</p>
 */
@SpringBootTest
@EmbeddedKafka(
        partitions = 3,
        topics = {"checkout-requested"},
        brokerProperties = {"listeners=PLAINTEXT://localhost:0", "port=0"}
)
class SagaFlowTest {

    private static final String CHECKOUT_REQUESTED_TOPIC = "checkout-requested";

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Autowired
    private CheckoutProducerService producerService;

    // ---------------------------------------------------------------
    //  Helpers
    // ---------------------------------------------------------------

    /**
     * Creates a raw Kafka consumer that reads String values from
     * the given topic, starting from the earliest offset.
     */
    private KafkaConsumer<String, String> createTestConsumer(String topic) {
        var consumer = new KafkaConsumer<String, String>(Map.<String, Object>of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                ConsumerConfig.GROUP_ID_CONFIG, "saga-test-" + topic + "-" + System.nanoTime(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName(),
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName()
        ));
        consumer.subscribe(List.of(topic));
        return consumer;
    }

    // ---------------------------------------------------------------
    //  Tests
    // ---------------------------------------------------------------

    @Test
    @DisplayName("Given a checkout request is submitted, " +
            "when the producer publishes it, " +
            "then the event appears on the checkout-requested topic with the correct orderId as key")
    void givenCheckoutRequest_whenProducerPublishes_thenEventAppearsWithCorrectKey() {

        // -- Given --
        var request = new CheckoutRequest(
                "ORD-TEST-001",
                "CUST-101",
                "Laptop Pro",
                2,
                new BigDecimal("1599.98"),
                Instant.now()
        );

        try (var consumer = createTestConsumer(CHECKOUT_REQUESTED_TOPIC)) {

            // -- When --
            producerService.sendCheckout(request);

            // -- Then --
            await().atMost(Duration.ofSeconds(30))
                    .pollInterval(Duration.ofMillis(500))
                    .untilAsserted(() -> {
                        ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
                        assertThat(records.count()).isGreaterThan(0);

                        var record = records.iterator().next();
                        assertThat(record.key()).isEqualTo("ORD-TEST-001");
                        assertThat(record.topic()).isEqualTo(CHECKOUT_REQUESTED_TOPIC);
                    });
        }
    }

    @Test
    @DisplayName("Given a checkout request with specific items and amount, " +
            "when serialized and published, " +
            "then the message payload contains all order details")
    void givenCheckoutWithDetails_whenSerializedAndPublished_thenPayloadContainsAllDetails() {

        // -- Given --
        var request = new CheckoutRequest(
                "ORD-TEST-002",
                "CUST-202",
                "Ergonomic Chair",
                1,
                new BigDecimal("449.99"),
                Instant.now()
        );

        try (var consumer = createTestConsumer(CHECKOUT_REQUESTED_TOPIC)) {

            // -- When --
            producerService.sendCheckout(request);

            // -- Then: accumulate records across polls until we find our target --
            var collected = new ArrayList<org.apache.kafka.clients.consumer.ConsumerRecord<String, String>>();
            await().atMost(Duration.ofSeconds(30))
                    .pollInterval(Duration.ofMillis(500))
                    .untilAsserted(() -> {
                        ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
                        records.forEach(collected::add);

                        var match = collected.stream()
                                .filter(r -> "ORD-TEST-002".equals(r.key()))
                                .findFirst();
                        assertThat(match).isPresent();

                        JsonNode payload = MAPPER.readTree(match.get().value());
                        assertThat(payload.get("orderId").asText()).isEqualTo("ORD-TEST-002");
                        assertThat(payload.get("customerId").asText()).isEqualTo("CUST-202");
                        assertThat(payload.get("productName").asText()).isEqualTo("Ergonomic Chair");
                        assertThat(payload.get("quantity").asInt()).isEqualTo(1);
                        assertThat(payload.get("totalAmount").asDouble()).isEqualTo(449.99);
                    });
        }
    }
}
