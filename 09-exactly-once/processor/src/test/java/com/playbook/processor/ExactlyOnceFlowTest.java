package com.playbook.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.playbook.processor.model.TransferRequest;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * End-to-end integration test for the exactly-once transfer processor.
 *
 * <p>This test verifies that the {@code TransferProcessorService} reads a
 * {@link TransferRequest} from the {@code transfer-requests} topic and
 * atomically produces a debit event to {@code account-debits} and a credit
 * event to {@code account-credits} within a single Kafka transaction.</p>
 */
@SpringBootTest
@Testcontainers
class ExactlyOnceFlowTest {

    private static final String TRANSFER_REQUESTS_TOPIC = "transfer-requests";
    private static final String ACCOUNT_DEBITS_TOPIC = "account-debits";
    private static final String ACCOUNT_CREDITS_TOPIC = "account-credits";

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    @Container
    static final KafkaContainer kafka = new KafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.6.0")
    );

    @DynamicPropertySource
    static void kafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    // ---------------------------------------------------------------
    //  Helpers
    // ---------------------------------------------------------------

    /**
     * Publishes a {@link TransferRequest} to the {@code transfer-requests}
     * topic using a raw Kafka producer with the Spring JSON type header so
     * the app's deserializer can resolve the concrete type.
     */
    private void publishTransferRequest(TransferRequest request) {
        try (var producer = new KafkaProducer<>(Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName(),
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class.getName()
        ))) {
            var record = new ProducerRecord<>(
                    TRANSFER_REQUESTS_TOPIC,
                    request.fromAccount(),
                    request
            );
            // Add the Spring JSON type header so the consumer's JsonDeserializer
            // can map the payload to TransferRequest
            record.headers().add(new RecordHeader(
                    "__TypeId__",
                    "transferRequest".getBytes(StandardCharsets.UTF_8)
            ));
            producer.send(record).get();
        } catch (Exception e) {
            throw new RuntimeException("Failed to publish transfer request", e);
        }
    }

    /**
     * Creates a raw Kafka consumer for the given topic. Uses
     * {@code read_committed} isolation when {@code readCommitted} is true.
     */
    private KafkaConsumer<String, String> createTestConsumer(
            String topic, boolean readCommitted) {

        var props = new java.util.HashMap<>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "eos-test-" + topic + "-" + System.nanoTime(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName(),
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName()
        ));
        if (readCommitted) {
            props.put(ConsumerConfig.ISOLATION_LEVEL_CONFIG, "read_committed");
        }

        var consumer = new KafkaConsumer<String, String>(props);
        consumer.subscribe(List.of(topic));
        return consumer;
    }

    /**
     * Polls the consumer until at least one record matching the given
     * transferId is found, accumulating all records across multiple polls.
     */
    private List<JsonNode> pollForTransferId(
            KafkaConsumer<String, String> consumer, String transferId) {

        var matching = new ArrayList<JsonNode>();
        ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
        for (var record : records) {
            try {
                JsonNode node = MAPPER.readTree(record.value());
                if (transferId.equals(node.get("transferId").asText())) {
                    matching.add(node);
                }
            } catch (Exception ignored) {
                // skip records that fail to parse
            }
        }
        return matching;
    }

    // ---------------------------------------------------------------
    //  Tests
    // ---------------------------------------------------------------

    @Test
    @DisplayName("Given a transfer request is published, " +
            "when the processor handles it transactionally, " +
            "then a debit event appears on account-debits " +
            "AND a credit event appears on account-credits")
    void givenTransferRequest_whenProcessedTransactionally_thenDebitAndCreditAppear() {

        // -- Given --
        var transferId = "TXF-TEST-001";
        var request = new TransferRequest(
                transferId,
                "ACC-1001",
                "ACC-1002",
                new BigDecimal("250.00"),
                "Rent payment",
                Instant.now()
        );

        try (var debitConsumer = createTestConsumer(ACCOUNT_DEBITS_TOPIC, false);
             var creditConsumer = createTestConsumer(ACCOUNT_CREDITS_TOPIC, false)) {

            // -- When --
            publishTransferRequest(request);

            // -- Then: debit appears --
            await().atMost(Duration.ofSeconds(30))
                    .pollInterval(Duration.ofMillis(500))
                    .untilAsserted(() -> {
                        var debits = pollForTransferId(debitConsumer, transferId);
                        assertThat(debits).isNotEmpty();

                        JsonNode debit = debits.getFirst();
                        assertThat(debit.get("accountId").asText()).isEqualTo("ACC-1001");
                        assertThat(debit.get("amount").asDouble()).isEqualTo(250.00);
                        assertThat(debit.get("description").asText()).isEqualTo("Rent payment");
                    });

            // -- Then: credit appears --
            await().atMost(Duration.ofSeconds(30))
                    .pollInterval(Duration.ofMillis(500))
                    .untilAsserted(() -> {
                        var credits = pollForTransferId(creditConsumer, transferId);
                        assertThat(credits).isNotEmpty();

                        JsonNode credit = credits.getFirst();
                        assertThat(credit.get("accountId").asText()).isEqualTo("ACC-1002");
                        assertThat(credit.get("amount").asDouble()).isEqualTo(250.00);
                        assertThat(credit.get("description").asText()).isEqualTo("Rent payment");
                    });
        }
    }

    @Test
    @DisplayName("Given a transfer request is published, " +
            "when the processor commits the transaction, " +
            "then a read_committed consumer sees the debit and credit atomically")
    void givenTransferRequest_whenTransactionCommits_thenReadCommittedConsumerSeesBoth() {

        // -- Given --
        var transferId = "TXF-TEST-002";
        var request = new TransferRequest(
                transferId,
                "ACC-2001",
                "ACC-2002",
                new BigDecimal("100.00"),
                "Dinner split",
                Instant.now()
        );

        try (var debitConsumer = createTestConsumer(ACCOUNT_DEBITS_TOPIC, true);
             var creditConsumer = createTestConsumer(ACCOUNT_CREDITS_TOPIC, true)) {

            // -- When --
            publishTransferRequest(request);

            // -- Then: read_committed consumer sees the debit --
            await().atMost(Duration.ofSeconds(30))
                    .pollInterval(Duration.ofMillis(500))
                    .untilAsserted(() -> {
                        var debits = pollForTransferId(debitConsumer, transferId);
                        assertThat(debits)
                                .as("read_committed consumer should see committed debit for %s", transferId)
                                .isNotEmpty();

                        JsonNode debit = debits.getFirst();
                        assertThat(debit.get("accountId").asText()).isEqualTo("ACC-2001");
                        assertThat(debit.get("amount").asDouble()).isEqualTo(100.00);
                    });

            // -- Then: read_committed consumer sees the credit --
            await().atMost(Duration.ofSeconds(30))
                    .pollInterval(Duration.ofMillis(500))
                    .untilAsserted(() -> {
                        var credits = pollForTransferId(creditConsumer, transferId);
                        assertThat(credits)
                                .as("read_committed consumer should see committed credit for %s", transferId)
                                .isNotEmpty();

                        JsonNode credit = credits.getFirst();
                        assertThat(credit.get("accountId").asText()).isEqualTo("ACC-2002");
                        assertThat(credit.get("amount").asDouble()).isEqualTo(100.00);
                    });
        }
    }
}
