package com.playbook.producer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.playbook.producer.model.RegionalOrderEvent;
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
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@EmbeddedKafka(
        partitions = 3,
        topics = {"regional-orders"},
        brokerProperties = {"listeners=PLAINTEXT://localhost:0", "port=0"}
)
class PartitionRoutingFlowTest {

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
        consumer.subscribe(List.of("regional-orders"));
        return consumer;
    }

    private List<org.apache.kafka.clients.consumer.ConsumerRecord<String, String>> pollUntilRecords(
            KafkaConsumer<String, String> consumer, int expectedCount, Duration timeout) {
        var collected = new ArrayList<org.apache.kafka.clients.consumer.ConsumerRecord<String, String>>();
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        while (collected.size() < expectedCount && System.currentTimeMillis() < deadline) {
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
            records.forEach(collected::add);
        }
        return collected;
    }

    @Test
    @DisplayName("Given orders are published with region keys NA, EU, APAC, " +
            "when the partitioner routes them, " +
            "then all orders with the same region key land on the same partition")
    void givenRegionKeys_whenPartitionerRoutes_thenSameRegionSamePartition() throws Exception {
        List<RegionalOrderEvent> orders = List.of(
                new RegionalOrderEvent("ORD-NA-001", "NA", "John", "Laptop", new BigDecimal("999.99"), Instant.now()),
                new RegionalOrderEvent("ORD-EU-001", "EU", "Hans", "Keyboard", new BigDecimal("149.99"), Instant.now()),
                new RegionalOrderEvent("ORD-APAC-001", "APAC", "Yuki", "Monitor", new BigDecimal("499.99"), Instant.now()),
                new RegionalOrderEvent("ORD-NA-002", "NA", "Jane", "Mouse", new BigDecimal("29.99"), Instant.now()),
                new RegionalOrderEvent("ORD-EU-002", "EU", "Pierre", "Webcam", new BigDecimal("79.99"), Instant.now()),
                new RegionalOrderEvent("ORD-APAC-002", "APAC", "Wei", "Headset", new BigDecimal("199.99"), Instant.now())
        );

        for (var order : orders) {
            orderProducerService.sendOrder(order);
        }

        // Allow Kafka time to accept the messages
        Thread.sleep(1000);

        try (var consumer = createTestConsumer("test-partition-routing-group")) {
            var records = pollUntilRecords(consumer, 6, Duration.ofSeconds(15));

            assertThat(records).hasSizeGreaterThanOrEqualTo(6);

            // Group records by key (region) and verify each region's records share the same partition
            Map<String, List<Integer>> partitionsByRegion = records.stream()
                    .collect(Collectors.groupingBy(
                            org.apache.kafka.clients.consumer.ConsumerRecord::key,
                            Collectors.mapping(
                                    org.apache.kafka.clients.consumer.ConsumerRecord::partition,
                                    Collectors.toList()
                            )
                    ));

            // NA orders should all be on the same partition
            assertThat(partitionsByRegion.get("NA")).isNotNull();
            assertThat(partitionsByRegion.get("NA").stream().distinct().count())
                    .as("All NA orders should land on the same partition")
                    .isEqualTo(1);

            // EU orders should all be on the same partition
            assertThat(partitionsByRegion.get("EU")).isNotNull();
            assertThat(partitionsByRegion.get("EU").stream().distinct().count())
                    .as("All EU orders should land on the same partition")
                    .isEqualTo(1);

            // APAC orders should all be on the same partition
            assertThat(partitionsByRegion.get("APAC")).isNotNull();
            assertThat(partitionsByRegion.get("APAC").stream().distinct().count())
                    .as("All APAC orders should land on the same partition")
                    .isEqualTo(1);
        }
    }

    @Test
    @DisplayName("Given multiple NA orders are published, " +
            "when consumed from their partition, " +
            "then they arrive in the order they were produced")
    void givenMultipleNAOrders_whenConsumed_thenArrivedInProducedOrder() throws Exception {
        List<RegionalOrderEvent> naOrders = List.of(
                new RegionalOrderEvent("ORD-NA-SEQ-001", "NA", "Alice", "Item A", new BigDecimal("10.00"), Instant.now()),
                new RegionalOrderEvent("ORD-NA-SEQ-002", "NA", "Bob", "Item B", new BigDecimal("20.00"), Instant.now()),
                new RegionalOrderEvent("ORD-NA-SEQ-003", "NA", "Carol", "Item C", new BigDecimal("30.00"), Instant.now()),
                new RegionalOrderEvent("ORD-NA-SEQ-004", "NA", "Dave", "Item D", new BigDecimal("40.00"), Instant.now()),
                new RegionalOrderEvent("ORD-NA-SEQ-005", "NA", "Eve", "Item E", new BigDecimal("50.00"), Instant.now())
        );

        for (var order : naOrders) {
            orderProducerService.sendOrder(order);
        }

        // Allow Kafka time to accept the messages
        Thread.sleep(1000);

        try (var consumer = createTestConsumer("test-na-ordering-group")) {
            var records = pollUntilRecords(consumer, 5, Duration.ofSeconds(15));

            // Filter to only our NA-SEQ orders
            var seqRecords = records.stream()
                    .filter(r -> r.key() != null && r.key().equals("NA"))
                    .filter(r -> {
                        try {
                            var json = objectMapper.readTree(r.value());
                            return json.get("orderId").asText().startsWith("ORD-NA-SEQ-");
                        } catch (Exception e) {
                            return false;
                        }
                    })
                    .toList();

            assertThat(seqRecords).hasSizeGreaterThanOrEqualTo(5);

            // Verify ordering -- they should arrive in the same order they were produced
            List<String> orderIds = seqRecords.stream()
                    .map(r -> {
                        try {
                            return objectMapper.readTree(r.value()).get("orderId").asText();
                        } catch (Exception e) {
                            return "";
                        }
                    })
                    .toList();

            assertThat(orderIds).containsExactly(
                    "ORD-NA-SEQ-001",
                    "ORD-NA-SEQ-002",
                    "ORD-NA-SEQ-003",
                    "ORD-NA-SEQ-004",
                    "ORD-NA-SEQ-005"
            );
        }
    }
}
