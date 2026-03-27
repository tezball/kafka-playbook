package com.playbook.consumer;

import com.playbook.consumer.model.OrderEvent;
import com.playbook.consumer.service.EmailNotificationService;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.*;

@SpringBootTest
@Testcontainers
class OrderNotificationFlowTest {

    @Container
    static KafkaContainer kafka = new KafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.6.1")
    );

    @DynamicPropertySource
    static void kafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @SpyBean
    private EmailNotificationService emailNotificationService;

    private org.apache.kafka.clients.producer.KafkaProducer<String, OrderEvent> createTestProducer() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        props.put(JsonSerializer.TYPE_MAPPINGS, "orderEvent:com.playbook.consumer.model.OrderEvent");
        return new org.apache.kafka.clients.producer.KafkaProducer<>(props);
    }

    @Test
    @DisplayName("Given an order event is published to the order-events topic, " +
            "when the email notification consumer processes the message, " +
            "then the order details are logged as a formatted email notification")
    void givenOrderEventPublished_whenConsumerProcesses_thenEmailNotificationLogged() throws Exception {
        OrderEvent order = new OrderEvent(
                "ORD-TEST-001",
                "alice@example.com",
                "Wireless Headphones",
                2,
                new BigDecimal("79.98"),
                Instant.now()
        );

        try (var producer = createTestProducer()) {
            producer.send(new ProducerRecord<>("order-events", order.orderId(), order)).get();
        }

        ArgumentCaptor<OrderEvent> captor = ArgumentCaptor.forClass(OrderEvent.class);

        await().atMost(Duration.ofSeconds(15))
                .untilAsserted(() -> {
                    verify(emailNotificationService, atLeastOnce()).handleOrderEvent(captor.capture());
                    assertThat(captor.getAllValues())
                            .anyMatch(e -> e.orderId().equals("ORD-TEST-001"));
                });

        OrderEvent received = captor.getAllValues().stream()
                .filter(e -> e.orderId().equals("ORD-TEST-001"))
                .findFirst()
                .orElseThrow();

        assertThat(received.customerEmail()).isEqualTo("alice@example.com");
        assertThat(received.productName()).isEqualTo("Wireless Headphones");
        assertThat(received.quantity()).isEqualTo(2);
        assertThat(received.totalPrice()).isEqualByComparingTo(new BigDecimal("79.98"));
    }

    @Test
    @DisplayName("Given multiple orders are published rapidly, " +
            "when the consumer processes them, " +
            "then all orders are received in the correct partition order")
    void givenMultipleOrdersPublished_whenConsumerProcesses_thenAllReceivedInOrder() throws Exception {
        OrderEvent order1 = new OrderEvent("ORD-BATCH-001", "bob@example.com", "Laptop", 1, new BigDecimal("999.99"), Instant.now());
        OrderEvent order2 = new OrderEvent("ORD-BATCH-002", "carol@example.com", "Mouse", 3, new BigDecimal("29.97"), Instant.now());
        OrderEvent order3 = new OrderEvent("ORD-BATCH-003", "dave@example.com", "Keyboard", 1, new BigDecimal("149.99"), Instant.now());

        try (var producer = createTestProducer()) {
            // Send all with the same key so they go to the same partition (guarantees ordering)
            producer.send(new ProducerRecord<>("order-events", "same-key", order1)).get();
            producer.send(new ProducerRecord<>("order-events", "same-key", order2)).get();
            producer.send(new ProducerRecord<>("order-events", "same-key", order3)).get();
        }

        ArgumentCaptor<OrderEvent> captor = ArgumentCaptor.forClass(OrderEvent.class);

        await().atMost(Duration.ofSeconds(15))
                .untilAsserted(() -> {
                    verify(emailNotificationService, atLeast(3)).handleOrderEvent(captor.capture());
                    long batchCount = captor.getAllValues().stream()
                            .filter(e -> e.orderId().startsWith("ORD-BATCH-"))
                            .count();
                    assertThat(batchCount).isGreaterThanOrEqualTo(3);
                });

        // Verify ordering: filter to our batch events and check they arrived in order
        var batchEvents = captor.getAllValues().stream()
                .filter(e -> e.orderId().startsWith("ORD-BATCH-"))
                .toList();

        assertThat(batchEvents).extracting(OrderEvent::orderId)
                .containsSubsequence("ORD-BATCH-001", "ORD-BATCH-002", "ORD-BATCH-003");
    }
}
