package com.playbook.orderservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.playbook.orderservice.model.Order;
import com.playbook.orderservice.model.OrderCreatedEvent;
import com.playbook.orderservice.model.OutboxEvent;
import com.playbook.orderservice.repository.OrderRepository;
import com.playbook.orderservice.repository.OutboxRepository;
import com.playbook.orderservice.service.OrderService;
import com.playbook.orderservice.service.OutboxPoller;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * End-to-end BDD tests for the transactional outbox pattern.
 *
 * Uses Testcontainers to spin up real Kafka and PostgreSQL instances.
 * Verifies the full flow: order creation -> outbox write -> poll -> Kafka publish.
 */
@SpringBootTest(
        classes = OrderServiceApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@Testcontainers
class OutboxFlowTest {

    @Container
    static final KafkaContainer kafka = new KafkaContainer("apache/kafka:3.9.0");

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("orderdb")
            .withUsername("postgres")
            .withPassword("postgres")
            .withInitScript("init-db.sql");

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OutboxRepository outboxRepository;

    @Autowired
    private OutboxPoller outboxPoller;

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Test
    @DisplayName("Given an order is created via the order service, " +
            "when the transaction commits to both orders and outbox_events tables, " +
            "then the outbox row is marked as unsent")
    void givenOrderCreated_whenTransactionCommits_thenOutboxRowIsUnsent() {
        // -- Given / When --
        Order order = orderService.createOrder(
                "ORD-TEST-001",
                "test@example.com",
                "Test Product",
                2,
                new BigDecimal("99.98")
        );

        // -- Then --
        // Verify the order was saved
        assertThat(orderRepository.findById("ORD-TEST-001")).isPresent();
        Order savedOrder = orderRepository.findById("ORD-TEST-001").get();
        assertThat(savedOrder.getCustomerEmail()).isEqualTo("test@example.com");
        assertThat(savedOrder.getProductName()).isEqualTo("Test Product");
        assertThat(savedOrder.getQuantity()).isEqualTo(2);
        assertThat(savedOrder.getTotalPrice()).isEqualByComparingTo(new BigDecimal("99.98"));
        assertThat(savedOrder.getStatus()).isEqualTo("CREATED");

        // Verify the outbox event was created and is unsent
        List<OutboxEvent> unsentEvents = outboxRepository.findBySentFalse();
        OutboxEvent outboxEvent = unsentEvents.stream()
                .filter(e -> "ORD-TEST-001".equals(e.getAggregateId()))
                .findFirst()
                .orElse(null);

        assertThat(outboxEvent).isNotNull();
        assertThat(outboxEvent.getAggregateType()).isEqualTo("Order");
        assertThat(outboxEvent.getEventType()).isEqualTo("ORDER_CREATED");
        assertThat(outboxEvent.isSent()).isFalse();
        assertThat(outboxEvent.getPayload()).contains("ORD-TEST-001");
    }

    @Test
    @DisplayName("Given an unsent outbox event exists, " +
            "when the outbox poller runs, " +
            "then the event is published to Kafka and marked as sent")
    void givenUnsentOutboxEvent_whenPollerRuns_thenPublishedAndMarkedSent() {
        // -- Given --
        Order order = orderService.createOrder(
                "ORD-TEST-002",
                "poller-test@example.com",
                "Poller Test Product",
                1,
                new BigDecimal("49.99")
        );

        // Confirm the event is initially unsent
        List<OutboxEvent> unsentBefore = outboxRepository.findBySentFalse();
        assertThat(unsentBefore.stream().anyMatch(e -> "ORD-TEST-002".equals(e.getAggregateId()))).isTrue();

        // -- When --
        outboxPoller.pollAndPublish();

        // -- Then --
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            // The outbox event should now be marked as sent
            List<OutboxEvent> unsentAfter = outboxRepository.findBySentFalse();
            assertThat(unsentAfter.stream().noneMatch(e -> "ORD-TEST-002".equals(e.getAggregateId()))).isTrue();
        });
    }

    @Test
    @DisplayName("Given the full e2e flow, " +
            "when an order is created, " +
            "then a notification event arrives on the order-events Kafka topic")
    void givenFullE2eFlow_whenOrderCreated_thenEventArrivesOnKafkaTopic() {
        // -- Given --
        Order order = orderService.createOrder(
                "ORD-TEST-E2E-001",
                "e2e@example.com",
                "E2E Test Widget",
                3,
                new BigDecimal("149.97")
        );

        // -- When --
        // Trigger the poller to publish the outbox event to Kafka
        outboxPoller.pollAndPublish();

        // -- Then --
        // Consume from the order-events topic and verify the event arrived
        Consumer<String, OrderCreatedEvent> consumer = createConsumer();
        consumer.subscribe(List.of("order-events"));

        await().atMost(Duration.ofSeconds(15)).pollInterval(Duration.ofSeconds(1)).untilAsserted(() -> {
            ConsumerRecords<String, OrderCreatedEvent> records = consumer.poll(Duration.ofMillis(500));
            List<OrderCreatedEvent> events = new ArrayList<>();
            records.forEach(r -> events.add(r.value()));

            OrderCreatedEvent target = events.stream()
                    .filter(e -> "ORD-TEST-E2E-001".equals(e.orderId()))
                    .findFirst()
                    .orElse(null);

            assertThat(target).isNotNull();
            assertThat(target.customerEmail()).isEqualTo("e2e@example.com");
            assertThat(target.productName()).isEqualTo("E2E Test Widget");
            assertThat(target.quantity()).isEqualTo(3);
            assertThat(target.totalPrice()).isEqualByComparingTo(new BigDecimal("149.97"));
        });

        consumer.close();
    }

    private Consumer<String, OrderCreatedEvent> createConsumer() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "test-consumer-" + System.currentTimeMillis());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "*");
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, OrderCreatedEvent.class.getName());
        props.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, "false");

        var factory = new DefaultKafkaConsumerFactory<String, OrderCreatedEvent>(props);
        return factory.createConsumer();
    }
}
