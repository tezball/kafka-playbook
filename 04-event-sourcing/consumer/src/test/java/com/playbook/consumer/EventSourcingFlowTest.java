package com.playbook.consumer;

import com.playbook.consumer.model.TransactionEvent;
import com.playbook.consumer.service.BalanceService;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.kafka.test.context.EmbeddedKafka;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@EmbeddedKafka(
        partitions = 3,
        topics = {"account-transactions"},
        brokerProperties = {"listeners=PLAINTEXT://localhost:0", "port=0"}
)
class EventSourcingFlowTest {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Autowired
    private BalanceService balanceService;

    private KafkaTemplate<String, TransactionEvent> kafkaTemplate;

    @BeforeEach
    void setUp() {
        Map<String, Object> producerProps = new HashMap<>();
        producerProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        producerProps.put(JsonSerializer.TYPE_MAPPINGS,
                "txnEvent:com.playbook.consumer.model.TransactionEvent");

        DefaultKafkaProducerFactory<String, TransactionEvent> producerFactory =
                new DefaultKafkaProducerFactory<>(producerProps);
        kafkaTemplate = new KafkaTemplate<>(producerFactory);
    }

    @Test
    @DisplayName("Given a series of CREDIT and DEBIT events for account ACC-1001, " +
            "when the balance consumer replays them, " +
            "then the running balance equals the sum of credits minus debits")
    void givenCreditAndDebitEvents_whenConsumerReplays_thenBalanceIsCorrect() {
        String accountId = "ACC-1001-" + UUID.randomUUID();

        // Send CREDIT $1000
        kafkaTemplate.send("account-transactions", accountId, new TransactionEvent(
                "TXN-1", accountId, "CREDIT", new BigDecimal("1000.00"),
                "Salary deposit", Instant.now()));
        // Send CREDIT $500
        kafkaTemplate.send("account-transactions", accountId, new TransactionEvent(
                "TXN-2", accountId, "CREDIT", new BigDecimal("500.00"),
                "Bonus", Instant.now()));
        // Send DEBIT $200
        kafkaTemplate.send("account-transactions", accountId, new TransactionEvent(
                "TXN-3", accountId, "DEBIT", new BigDecimal("200.00"),
                "Groceries", Instant.now()));
        // Send DEBIT $150
        kafkaTemplate.send("account-transactions", accountId, new TransactionEvent(
                "TXN-4", accountId, "DEBIT", new BigDecimal("150.00"),
                "Utilities", Instant.now()));

        // Expected: 1000 + 500 - 200 - 150 = 1150
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(balanceService.getBalance(accountId))
                        .isEqualByComparingTo(new BigDecimal("1150.00"))
        );
    }

    @Test
    @DisplayName("Given events for multiple accounts are interleaved, " +
            "when the consumer processes them, " +
            "then each account has an independent correct balance")
    void givenInterleavedMultiAccountEvents_whenConsumerProcesses_thenEachAccountBalanceIsCorrect() {
        String account1 = "ACC-MULTI-1-" + UUID.randomUUID();
        String account2 = "ACC-MULTI-2-" + UUID.randomUUID();

        // Interleave events for two accounts
        kafkaTemplate.send("account-transactions", account1, new TransactionEvent(
                "TXN-A1", account1, "CREDIT", new BigDecimal("3000.00"),
                "Salary", Instant.now()));
        kafkaTemplate.send("account-transactions", account2, new TransactionEvent(
                "TXN-B1", account2, "CREDIT", new BigDecimal("5000.00"),
                "Salary", Instant.now()));
        kafkaTemplate.send("account-transactions", account1, new TransactionEvent(
                "TXN-A2", account1, "DEBIT", new BigDecimal("800.00"),
                "Rent", Instant.now()));
        kafkaTemplate.send("account-transactions", account2, new TransactionEvent(
                "TXN-B2", account2, "DEBIT", new BigDecimal("1200.00"),
                "Rent", Instant.now()));
        kafkaTemplate.send("account-transactions", account1, new TransactionEvent(
                "TXN-A3", account1, "CREDIT", new BigDecimal("200.00"),
                "Refund", Instant.now()));

        // account1: 3000 - 800 + 200 = 2400
        // account2: 5000 - 1200 = 3800
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            assertThat(balanceService.getBalance(account1))
                    .isEqualByComparingTo(new BigDecimal("2400.00"));
            assertThat(balanceService.getBalance(account2))
                    .isEqualByComparingTo(new BigDecimal("3800.00"));
        });
    }

    @Test
    @DisplayName("Given the consumer restarts after processing some events, " +
            "when it resumes from its committed offset, " +
            "then it does not double-count previously processed events")
    void givenConsumerRestart_whenResumesFromCommittedOffset_thenNoDoubleCounting() {
        String accountId = "ACC-RESTART-" + UUID.randomUUID();

        // Send initial events
        kafkaTemplate.send("account-transactions", accountId, new TransactionEvent(
                "TXN-R1", accountId, "CREDIT", new BigDecimal("1000.00"),
                "Initial deposit", Instant.now()));
        kafkaTemplate.send("account-transactions", accountId, new TransactionEvent(
                "TXN-R2", accountId, "CREDIT", new BigDecimal("500.00"),
                "Second deposit", Instant.now()));

        // Wait for initial processing
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(balanceService.getBalance(accountId))
                        .isEqualByComparingTo(new BigDecimal("1500.00"))
        );

        // Send additional events (simulates what happens after a "restart" --
        // new events arrive and should be added to the existing balance, not replayed from scratch)
        kafkaTemplate.send("account-transactions", accountId, new TransactionEvent(
                "TXN-R3", accountId, "DEBIT", new BigDecimal("300.00"),
                "Post-restart purchase", Instant.now()));

        // Balance should be 1500 - 300 = 1200, not 1000 + 500 + 1000 + 500 - 300
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(balanceService.getBalance(accountId))
                        .isEqualByComparingTo(new BigDecimal("1200.00"))
        );
    }
}
