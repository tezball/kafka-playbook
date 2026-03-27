package com.playbook.aggregator.config;

import com.playbook.aggregator.model.CategoryCount;
import com.playbook.aggregator.model.DashboardOrder;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.utils.Bytes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.*;
import org.apache.kafka.streams.state.WindowStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.support.serializer.JsonSerde;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Configuration
public class StreamsTopologyConfig {

    private static final Logger log = LoggerFactory.getLogger(StreamsTopologyConfig.class);
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

    public static final String CATEGORY_COUNTS_STORE = "category-counts-store";

    @Autowired
    public void buildTopology(StreamsBuilder builder) {

        // Serde for DashboardOrder values
        var orderSerde = new JsonSerde<>(DashboardOrder.class);
        orderSerde.configure(
                java.util.Map.of("spring.json.trusted.packages", "com.playbook.*"),
                false
        );

        // Serde for CategoryCount values
        var countSerde = new JsonSerde<>(CategoryCount.class);

        // 1. Read from dashboard-orders topic
        KStream<String, DashboardOrder> orders = builder.stream(
                "dashboard-orders",
                Consumed.with(Serdes.String(), orderSerde)
        );

        // 2. Re-key by category
        KStream<String, DashboardOrder> byCategory = orders.selectKey(
                (key, order) -> order.category()
        );

        // 3. Group by category key
        KGroupedStream<String, DashboardOrder> grouped = byCategory.groupByKey(
                Grouped.with(Serdes.String(), orderSerde)
        );

        // 4. Tumbling window of 1 minute, count per category
        KTable<Windowed<String>, Long> windowedCounts = grouped
                .windowedBy(TimeWindows.ofSizeWithNoGrace(Duration.ofMinutes(1)))
                .count(
                        Materialized.<String, Long, WindowStore<Bytes, byte[]>>as(CATEGORY_COUNTS_STORE)
                                .withKeySerde(Serdes.String())
                                .withValueSerde(Serdes.Long())
                );

        // 5. Convert to a stream of CategoryCount and write to output topic
        windowedCounts
                .toStream()
                .peek((windowedKey, count) -> {
                    String category = windowedKey.key();
                    String start = TIME_FMT.format(Instant.ofEpochMilli(windowedKey.window().start()));
                    String end = TIME_FMT.format(Instant.ofEpochMilli(windowedKey.window().end()));
                    log.info("[COUNT] {}\t| {} orders | window: {} - {}", padRight(category, 13), count, start, end);
                })
                .map((windowedKey, count) -> {
                    String category = windowedKey.key();
                    String start = TIME_FMT.format(Instant.ofEpochMilli(windowedKey.window().start()));
                    String end = TIME_FMT.format(Instant.ofEpochMilli(windowedKey.window().end()));
                    var categoryCount = new CategoryCount(category, count, start, end);
                    return KeyValue.pair(category, categoryCount);
                })
                .to("category-counts", Produced.with(Serdes.String(), countSerde));
    }

    private static String padRight(String s, int width) {
        return String.format("%-" + width + "s", s);
    }
}
