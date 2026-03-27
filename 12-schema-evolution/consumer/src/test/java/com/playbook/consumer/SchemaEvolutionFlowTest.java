package com.playbook.consumer;

import com.playbook.consumer.model.OrderEventFull;
import com.playbook.consumer.service.VersionedEventConsumer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.kafka.test.context.EmbeddedKafka;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.verify;

/**
 * End-to-end BDD tests for the schema evolution consumer.
 *
 * Uses an embedded Kafka broker, produces v1 and v2 events as raw JSON maps
 * to the versioned-events topic, and verifies the consumer deserializes both
 * correctly using its tolerant reader model.
 */
@SpringBootTest(
        classes = ConsumerApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@EmbeddedKafka(
        partitions = 3,
        topics = {"versioned-events"},
        brokerProperties = {"listeners=PLAINTEXT://localhost:0", "port=0"}
)
class SchemaEvolutionFlowTest {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @SpyBean
    private VersionedEventConsumer versionedEventConsumer;

    @Test
    @DisplayName("Given a v1 event (3 fields) is published, " +
            "when the tolerant consumer deserializes it, " +
            "then the extra fields (shippingAddress, loyaltyTier) are null")
    void givenV1Event_whenConsumed_thenExtraFieldsAreNull() {
        // -- Given --
        Map<String, Object> v1Event = new HashMap<>();
        v1Event.put("orderId", "ORD-V1-TEST-001");
        v1Event.put("customerEmail", "alice@test.com");
        v1Event.put("totalPrice", 149.99);
        v1Event.put("createdAt", Instant.now().toString());

        KafkaTemplate<String, Map<String, Object>> producer = createMapProducer();
        producer.send("versioned-events", "ORD-V1-TEST-001", v1Event);
        producer.flush();

        // -- When / Then --
        ArgumentCaptor<OrderEventFull> captor = ArgumentCaptor.forClass(OrderEventFull.class);

        await().atMost(Duration.ofSeconds(15)).pollInterval(Duration.ofSeconds(1)).untilAsserted(() -> {
            verify(versionedEventConsumer, atLeast(1)).handleOrderEvent(captor.capture());

            List<OrderEventFull> captured = captor.getAllValues();
            OrderEventFull v1 = captured.stream()
                    .filter(e -> "ORD-V1-TEST-001".equals(e.getOrderId()))
                    .findFirst()
                    .orElse(null);

            assertThat(v1).isNotNull();
            assertThat(v1.getCustomerEmail()).isEqualTo("alice@test.com");
            assertThat(v1.getTotalPrice()).isEqualByComparingTo(new BigDecimal("149.99"));
            assertThat(v1.getShippingAddress()).isNull();
            assertThat(v1.getLoyaltyTier()).isNull();
            assertThat(v1.detectSchemaVersion()).isEqualTo(1);
        });
    }

    @Test
    @DisplayName("Given a v2 event (5 fields) is published, " +
            "when the tolerant consumer deserializes it, " +
            "then all fields including shippingAddress and loyaltyTier are populated")
    void givenV2Event_whenConsumed_thenAllFieldsPopulated() {
        // -- Given --
        Map<String, Object> v2Event = new HashMap<>();
        v2Event.put("orderId", "ORD-V2-TEST-001");
        v2Event.put("customerEmail", "bob@test.com");
        v2Event.put("totalPrice", 249.99);
        v2Event.put("shippingAddress", "123 Main St, Springfield");
        v2Event.put("loyaltyTier", "GOLD");
        v2Event.put("createdAt", Instant.now().toString());

        KafkaTemplate<String, Map<String, Object>> producer = createMapProducer();
        producer.send("versioned-events", "ORD-V2-TEST-001", v2Event);
        producer.flush();

        // -- When / Then --
        ArgumentCaptor<OrderEventFull> captor = ArgumentCaptor.forClass(OrderEventFull.class);

        await().atMost(Duration.ofSeconds(15)).pollInterval(Duration.ofSeconds(1)).untilAsserted(() -> {
            verify(versionedEventConsumer, atLeast(1)).handleOrderEvent(captor.capture());

            List<OrderEventFull> captured = captor.getAllValues();
            OrderEventFull v2 = captured.stream()
                    .filter(e -> "ORD-V2-TEST-001".equals(e.getOrderId()))
                    .findFirst()
                    .orElse(null);

            assertThat(v2).isNotNull();
            assertThat(v2.getCustomerEmail()).isEqualTo("bob@test.com");
            assertThat(v2.getTotalPrice()).isEqualByComparingTo(new BigDecimal("249.99"));
            assertThat(v2.getShippingAddress()).isEqualTo("123 Main St, Springfield");
            assertThat(v2.getLoyaltyTier()).isEqualTo("GOLD");
            assertThat(v2.detectSchemaVersion()).isEqualTo(2);
        });
    }

    @Test
    @DisplayName("Given a mix of v1 and v2 events are published, " +
            "when the consumer processes them, " +
            "then both versions are handled without errors")
    void givenMixedVersionEvents_whenConsumed_thenBothVersionsHandled() {
        // -- Given --
        Map<String, Object> v1Event = new HashMap<>();
        v1Event.put("orderId", "ORD-MIX-V1-001");
        v1Event.put("customerEmail", "carol@test.com");
        v1Event.put("totalPrice", 59.99);
        v1Event.put("createdAt", Instant.now().toString());

        Map<String, Object> v2Event = new HashMap<>();
        v2Event.put("orderId", "ORD-MIX-V2-001");
        v2Event.put("customerEmail", "dave@test.com");
        v2Event.put("totalPrice", 399.99);
        v2Event.put("shippingAddress", "456 Oak Ave, Shelbyville");
        v2Event.put("loyaltyTier", "PLATINUM");
        v2Event.put("createdAt", Instant.now().toString());

        Map<String, Object> v1Event2 = new HashMap<>();
        v1Event2.put("orderId", "ORD-MIX-V1-002");
        v1Event2.put("customerEmail", "eve@test.com");
        v1Event2.put("totalPrice", 19.99);
        v1Event2.put("createdAt", Instant.now().toString());

        KafkaTemplate<String, Map<String, Object>> producer = createMapProducer();
        producer.send("versioned-events", "ORD-MIX-V1-001", v1Event);
        producer.send("versioned-events", "ORD-MIX-V2-001", v2Event);
        producer.send("versioned-events", "ORD-MIX-V1-002", v1Event2);
        producer.flush();

        // -- When / Then --
        ArgumentCaptor<OrderEventFull> captor = ArgumentCaptor.forClass(OrderEventFull.class);

        await().atMost(Duration.ofSeconds(15)).pollInterval(Duration.ofSeconds(1)).untilAsserted(() -> {
            verify(versionedEventConsumer, atLeast(3)).handleOrderEvent(captor.capture());

            List<OrderEventFull> captured = captor.getAllValues();

            OrderEventFull mixV1First = captured.stream()
                    .filter(e -> "ORD-MIX-V1-001".equals(e.getOrderId()))
                    .findFirst().orElse(null);
            OrderEventFull mixV2 = captured.stream()
                    .filter(e -> "ORD-MIX-V2-001".equals(e.getOrderId()))
                    .findFirst().orElse(null);
            OrderEventFull mixV1Second = captured.stream()
                    .filter(e -> "ORD-MIX-V1-002".equals(e.getOrderId()))
                    .findFirst().orElse(null);

            // All three events were consumed successfully
            assertThat(mixV1First).isNotNull();
            assertThat(mixV2).isNotNull();
            assertThat(mixV1Second).isNotNull();

            // v1 events have null extended fields
            assertThat(mixV1First.detectSchemaVersion()).isEqualTo(1);
            assertThat(mixV1First.getShippingAddress()).isNull();

            // v2 events have populated extended fields
            assertThat(mixV2.detectSchemaVersion()).isEqualTo(2);
            assertThat(mixV2.getShippingAddress()).isEqualTo("456 Oak Ave, Shelbyville");
            assertThat(mixV2.getLoyaltyTier()).isEqualTo("PLATINUM");

            // Second v1 event also handled correctly
            assertThat(mixV1Second.detectSchemaVersion()).isEqualTo(1);
            assertThat(mixV1Second.getCustomerEmail()).isEqualTo("eve@test.com");
        });
    }

    /**
     * Creates a producer that sends raw Maps as JSON, simulating how different
     * producer versions send events with different field sets to the same topic.
     */
    private KafkaTemplate<String, Map<String, Object>> createMapProducer() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        props.put(JsonSerializer.ADD_TYPE_INFO_HEADERS, false);

        var factory = new DefaultKafkaProducerFactory<String, Map<String, Object>>(props);
        return new KafkaTemplate<>(factory);
    }
}
