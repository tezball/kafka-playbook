package com.playbook.producer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.playbook.producer.model.OrderEvent;
import com.playbook.producer.service.OrderProducerService;
import com.playbook.producer.service.SampleDataGenerator;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.test.context.EmbeddedKafka;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@EmbeddedKafka(
        partitions = 3,
        topics = {"order-events"},
        brokerProperties = {"listeners=PLAINTEXT://localhost:0", "port=0"}
)
class OrderProducerFlowTest {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Autowired
    private OrderProducerService orderProducerService;

    @MockBean
    @SuppressWarnings("unused")
    private SampleDataGenerator sampleDataGenerator;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private KafkaConsumer<String, String> createTestConsumer(String groupId) {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props);
        consumer.subscribe(List.of("order-events"));
        return consumer;
    }

    private List<org.apache.kafka.clients.consumer.ConsumerRecord<String, String>> pollUntilMatch(
            KafkaConsumer<String, String> consumer, String expectedKey, Duration timeout) {
        var collected = new ArrayList<org.apache.kafka.clients.consumer.ConsumerRecord<String, String>>();
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        while (System.currentTimeMillis() < deadline) {
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
            records.forEach(collected::add);
            if (collected.stream().anyMatch(r -> expectedKey.equals(r.key()))) {
                break;
            }
        }
        return collected;
    }

    @Test
    @DisplayName("Given a valid order request, " +
            "when the producer sends it to Kafka, " +
            "then the message arrives on the order-events topic with the correct key and JSON payload")
    void givenValidOrder_whenProducerSends_thenMessageArrivesWithCorrectKeyAndPayload() throws Exception {
        OrderEvent order = new OrderEvent(
                "ORD-PRODUCER-001",
                "test@example.com",
                "Mechanical Keyboard",
                1,
                new BigDecimal("149.99"),
                Instant.now()
        );

        orderProducerService.sendOrder(order);

        try (var consumer = createTestConsumer("test-verification-group")) {
            var records = pollUntilMatch(consumer, "ORD-PRODUCER-001", Duration.ofSeconds(15));

            var matchingRecord = records.stream()
                    .filter(r -> "ORD-PRODUCER-001".equals(r.key()))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("Expected record with key ORD-PRODUCER-001"));

            assertThat(matchingRecord.topic()).isEqualTo("order-events");

            var json = objectMapper.readTree(matchingRecord.value());
            assertThat(json.get("orderId").asText()).isEqualTo("ORD-PRODUCER-001");
            assertThat(json.get("customerEmail").asText()).isEqualTo("test@example.com");
            assertThat(json.get("productName").asText()).isEqualTo("Mechanical Keyboard");
            assertThat(json.get("quantity").asInt()).isEqualTo(1);
            assertThat(json.get("totalPrice").asDouble()).isEqualTo(149.99);
        }
    }
}
