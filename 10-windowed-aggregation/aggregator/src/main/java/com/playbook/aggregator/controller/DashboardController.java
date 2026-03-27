package com.playbook.aggregator.controller;

import com.playbook.aggregator.config.StreamsTopologyConfig;
import com.playbook.aggregator.model.CategoryCount;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StoreQueryParameters;
import org.apache.kafka.streams.state.QueryableStoreTypes;
import org.apache.kafka.streams.state.ReadOnlyWindowStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.config.StreamsBuilderFactoryBean;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private static final Logger log = LoggerFactory.getLogger(DashboardController.class);
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

    private final StreamsBuilderFactoryBean factoryBean;

    public DashboardController(StreamsBuilderFactoryBean factoryBean) {
        this.factoryBean = factoryBean;
    }

    @GetMapping("/counts")
    public ResponseEntity<?> getCounts() {
        try {
            KafkaStreams kafkaStreams = factoryBean.getKafkaStreams();
            if (kafkaStreams == null || kafkaStreams.state() != KafkaStreams.State.RUNNING) {
                return ResponseEntity.status(503)
                        .body(Map.of("error", "Kafka Streams is not yet running", "state",
                                kafkaStreams == null ? "NULL" : kafkaStreams.state().toString()));
            }

            ReadOnlyWindowStore<String, Long> store = kafkaStreams.store(
                    StoreQueryParameters.fromNameAndType(
                            StreamsTopologyConfig.CATEGORY_COUNTS_STORE,
                            QueryableStoreTypes.windowStore()
                    )
            );

            // Query the last 5 minutes of windows
            Instant now = Instant.now();
            Instant from = now.minus(Duration.ofMinutes(5));

            List<CategoryCount> counts = new ArrayList<>();
            var iterator = store.fetchAll(from, now);
            while (iterator.hasNext()) {
                var kv = iterator.next();
                var windowedKey = kv.key;
                String category = windowedKey.key();
                long count = kv.value;
                String start = TIME_FMT.format(Instant.ofEpochMilli(windowedKey.window().start()));
                String end = TIME_FMT.format(Instant.ofEpochMilli(windowedKey.window().end()));
                counts.add(new CategoryCount(category, count, start, end));
            }
            iterator.close();

            return ResponseEntity.ok(counts);
        } catch (Exception e) {
            log.error("Error querying state store", e);
            return ResponseEntity.status(503)
                    .body(Map.of("error", "State store not available: " + e.getMessage()));
        }
    }
}
