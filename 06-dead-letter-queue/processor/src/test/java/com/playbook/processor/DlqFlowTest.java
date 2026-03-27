package com.playbook.processor;

import com.playbook.processor.model.PaymentEvent;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.kafka.test.context.EmbeddedKafka;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@EmbeddedKafka(
        partitions = 3,
        topics = {"payments", "payments.DLT"},
        brokerProperties = {"listeners=PLAINTEXT://localhost:0", "port=0"}
)
class DlqFlowTest {

    private static final String DLQ_TOPIC = "payments.DLT";

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    private KafkaTemplate<String, PaymentEvent> kafkaTemplate;

    @BeforeEach
    void setUp() {
        Map<String, Object> producerProps = new HashMap<>();
        producerProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        producerProps.put(JsonSerializer.TYPE_MAPPINGS,
                "paymentEvent:com.playbook.processor.model.PaymentEvent");

        DefaultKafkaProducerFactory<String, PaymentEvent> producerFactory =
                new DefaultKafkaProducerFactory<>(producerProps);
        kafkaTemplate = new KafkaTemplate<>(producerFactory);
    }

    private KafkaConsumer<String, byte[]> createDlqConsumer() {
        Map<String, Object> consumerProps = new HashMap<>();
        consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, "test-dlq-reader-" + UUID.randomUUID());
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class);

        KafkaConsumer<String, byte[]> consumer = new KafkaConsumer<>(consumerProps);
        consumer.subscribe(Collections.singletonList(DLQ_TOPIC));
        return consumer;
    }

    @Test
    @DisplayName("Given a valid payment (amount < $500) is published, " +
            "when the processor handles it, " +
            "then the payment is processed successfully (no DLQ)")
    void givenValidPayment_whenProcessorHandles_thenNoMessageInDlq() {
        String paymentId = "PAY-VALID-" + UUID.randomUUID();

        PaymentEvent validPayment = new PaymentEvent(
                paymentId, "ORD-1001", new BigDecimal("99.99"),
                "USD", "4532", "valid@example.com", Instant.now());

        kafkaTemplate.send("payments", paymentId, validPayment);

        // Wait long enough for the payment to be processed (including potential retries)
        // then verify nothing ended up in the DLQ
        try (KafkaConsumer<String, byte[]> dlqConsumer = createDlqConsumer()) {
            await().during(Duration.ofSeconds(8)).atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
                ConsumerRecords<String, byte[]> records = dlqConsumer.poll(Duration.ofMillis(500));
                // Filter for our specific payment key
                long matchingRecords = StreamSupport.stream(records.records(DLQ_TOPIC).spliterator(), false)
                        .filter(r -> r.key() != null && r.key().equals(paymentId))
                        .count();
                assertThat(matchingRecords).isZero();
            });
        }
    }

    @Test
    @DisplayName("Given a fraudulent payment (amount > $500) is published, " +
            "when the processor retries 3 times and fails, " +
            "then the payment is sent to the DLQ topic")
    void givenFraudulentPayment_whenRetriesExhausted_thenSentToDlq() {
        String paymentId = "PAY-FRAUD-" + UUID.randomUUID();

        PaymentEvent fraudPayment = new PaymentEvent(
                paymentId, "ORD-2001", new BigDecimal("999.99"),
                "USD", "7891", "fraud@example.com", Instant.now());

        kafkaTemplate.send("payments", paymentId, fraudPayment);

        // The processor retries 3 times with 1-second backoff, then sends to DLQ
        // Total wait: ~3s retries + processing time
        try (KafkaConsumer<String, byte[]> dlqConsumer = createDlqConsumer()) {
            await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
                ConsumerRecords<String, byte[]> records = dlqConsumer.poll(Duration.ofMillis(500));
                long matchingRecords = StreamSupport.stream(records.records(DLQ_TOPIC).spliterator(), false)
                        .filter(r -> r.key() != null && r.key().equals(paymentId))
                        .count();
                assertThat(matchingRecords).isGreaterThanOrEqualTo(1);
            });
        }
    }

    @Test
    @DisplayName("Given a mix of valid and fraudulent payments, " +
            "when the processor handles the batch, " +
            "then valid payments succeed and fraudulent ones end up in the DLQ")
    void givenMixedPayments_whenProcessorHandlesBatch_thenValidSucceedAndFraudulentGoToDlq() {
        String validId1 = "PAY-MIX-V1-" + UUID.randomUUID();
        String validId2 = "PAY-MIX-V2-" + UUID.randomUUID();
        String fraudId1 = "PAY-MIX-F1-" + UUID.randomUUID();
        String fraudId2 = "PAY-MIX-F2-" + UUID.randomUUID();
        Set<String> fraudIds = Set.of(fraudId1, fraudId2);

        // Send a mix of valid and fraudulent payments
        kafkaTemplate.send("payments", validId1, new PaymentEvent(
                validId1, "ORD-3001", new BigDecimal("49.99"),
                "USD", "1111", "ok1@example.com", Instant.now()));
        kafkaTemplate.send("payments", fraudId1, new PaymentEvent(
                fraudId1, "ORD-3002", new BigDecimal("750.00"),
                "USD", "2222", "bad1@example.com", Instant.now()));
        kafkaTemplate.send("payments", validId2, new PaymentEvent(
                validId2, "ORD-3003", new BigDecimal("25.00"),
                "USD", "3333", "ok2@example.com", Instant.now()));
        kafkaTemplate.send("payments", fraudId2, new PaymentEvent(
                fraudId2, "ORD-3004", new BigDecimal("1200.00"),
                "USD", "4444", "bad2@example.com", Instant.now()));

        // Collect DLQ messages over time and verify exactly the fraudulent ones land there
        Set<String> dlqPaymentIds = new HashSet<>();
        try (KafkaConsumer<String, byte[]> dlqConsumer = createDlqConsumer()) {
            await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
                ConsumerRecords<String, byte[]> records = dlqConsumer.poll(Duration.ofMillis(500));
                records.records(DLQ_TOPIC).forEach(r -> {
                    if (r.key() != null) {
                        dlqPaymentIds.add(r.key());
                    }
                });
                // Both fraudulent payments should be in the DLQ
                assertThat(dlqPaymentIds).containsAll(fraudIds);
            });
        }

        // Valid payments should not be in the DLQ
        assertThat(dlqPaymentIds).doesNotContain(validId1, validId2);
    }
}
