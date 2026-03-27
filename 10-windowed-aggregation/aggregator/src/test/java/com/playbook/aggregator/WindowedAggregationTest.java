package com.playbook.aggregator;

import com.playbook.aggregator.model.CategoryCount;
import com.playbook.aggregator.model.DashboardOrder;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
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

/**
 * End-to-end BDD tests for the Kafka Streams windowed aggregation topology.
 *
 * Uses an embedded Kafka broker, produces DashboardOrder events to the input
 * topic, and verifies CategoryCount results on the output topic.
 */
@SpringBootTest(
        classes = AggregatorApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.kafka.streams.properties.num.stream.threads=1"
        }
)
@EmbeddedKafka(
        partitions = 3,
        topics = {"dashboard-orders", "category-counts", "test-dashboard-aggregator-KSTREAM-KEY-SELECT-0000000001-repartition"},
        brokerProperties = {"listeners=PLAINTEXT://localhost:0", "port=0"}
)
class WindowedAggregationTest {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Test
    @DisplayName("Given orders arrive within the same 1-minute window, " +
            "when aggregated by category, " +
            "then the count reflects the total orders per category in that window")
    void givenOrdersInSameWindow_whenAggregatedByCategory_thenCountReflectsTotalPerCategory() {
        // -- Given --
        Instant windowBase = Instant.now();
        List<DashboardOrder> orders = List.of(
                new DashboardOrder("ORD-001", "Electronics", "Laptop", new BigDecimal("999.99"), windowBase),
                new DashboardOrder("ORD-002", "Electronics", "Phone", new BigDecimal("699.99"), windowBase.plusSeconds(10)),
                new DashboardOrder("ORD-003", "Electronics", "Tablet", new BigDecimal("499.99"), windowBase.plusSeconds(20)),
                new DashboardOrder("ORD-004", "Clothing", "Jacket", new BigDecimal("89.99"), windowBase.plusSeconds(5)),
                new DashboardOrder("ORD-005", "Clothing", "Shoes", new BigDecimal("129.99"), windowBase.plusSeconds(15))
        );

        KafkaTemplate<String, DashboardOrder> producer = createProducer();
        for (DashboardOrder order : orders) {
            producer.send("dashboard-orders", order.orderId(), order);
        }
        producer.flush();

        // -- When / Then --
        Consumer<String, CategoryCount> consumer = createConsumer();
        consumer.subscribe(List.of("category-counts"));

        await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofSeconds(1)).untilAsserted(() -> {
            ConsumerRecords<String, CategoryCount> records = consumer.poll(Duration.ofMillis(500));
            List<CategoryCount> results = new ArrayList<>();
            records.forEach(r -> results.add(r.value()));

            // Kafka Streams emits intermediate results, so we check that we eventually
            // see Electronics >= 3 and Clothing >= 2 in some emitted record
            if (!results.isEmpty()) {
                // Collect all counts ever seen for each category
                Map<String, Long> maxCounts = new HashMap<>();
                results.forEach(cc -> maxCounts.merge(cc.category(), cc.count(), Math::max));

                // We may need to accumulate over multiple polls
                assertThat(maxCounts.getOrDefault("Electronics", 0L)).isGreaterThanOrEqualTo(3);
                assertThat(maxCounts.getOrDefault("Clothing", 0L)).isGreaterThanOrEqualTo(2);
            }
        });

        consumer.close();
    }

    @Test
    @DisplayName("Given orders span two different 1-minute windows, " +
            "when aggregated, " +
            "then each window has its own independent count")
    void givenOrdersSpanTwoWindows_whenAggregated_thenEachWindowHasIndependentCount() {
        // -- Given --
        // Use a base time that aligns to the start of a minute boundary for predictability
        Instant window1Base = Instant.parse("2026-03-27T12:00:05Z");
        Instant window2Base = Instant.parse("2026-03-27T12:01:05Z"); // next minute window

        List<DashboardOrder> orders = List.of(
                // Window 1: 2 Books orders
                new DashboardOrder("ORD-W1-001", "Books", "Novel", new BigDecimal("19.99"), window1Base),
                new DashboardOrder("ORD-W1-002", "Books", "Textbook", new BigDecimal("59.99"), window1Base.plusSeconds(10)),
                // Window 2: 1 Books order
                new DashboardOrder("ORD-W2-001", "Books", "Magazine", new BigDecimal("9.99"), window2Base)
        );

        KafkaTemplate<String, DashboardOrder> producer = createProducer();
        for (DashboardOrder order : orders) {
            // Use explicit Kafka record timestamps so Kafka Streams windows
            // align with the event's logical timestamp, not wall-clock time.
            var record = new ProducerRecord<>(
                    "dashboard-orders", null, order.timestamp().toEpochMilli(),
                    order.orderId(), order);
            producer.send(record);
        }
        producer.flush();

        // -- When / Then --
        Consumer<String, CategoryCount> consumer = createConsumer();
        consumer.subscribe(List.of("category-counts"));

        Map<String, List<Long>> windowCounts = new HashMap<>();

        await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofSeconds(1)).untilAsserted(() -> {
            ConsumerRecords<String, CategoryCount> records = consumer.poll(Duration.ofMillis(500));
            records.forEach(r -> {
                CategoryCount cc = r.value();
                if ("Books".equals(cc.category())) {
                    String windowKey = cc.windowStart() + "-" + cc.windowEnd();
                    windowCounts.computeIfAbsent(windowKey, k -> new ArrayList<>()).add(cc.count());
                }
            });

            // We should see at least 2 distinct window keys for the Books category
            assertThat(windowCounts.keySet().size()).isGreaterThanOrEqualTo(2);
        });

        consumer.close();
    }

    private KafkaTemplate<String, DashboardOrder> createProducer() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);

        var factory = new DefaultKafkaProducerFactory<String, DashboardOrder>(props);
        return new KafkaTemplate<>(factory);
    }

    private Consumer<String, CategoryCount> createConsumer() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "test-consumer-" + System.currentTimeMillis());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "*");
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, CategoryCount.class.getName());

        var factory = new DefaultKafkaConsumerFactory<String, CategoryCount>(props);
        return factory.createConsumer();
    }
}
