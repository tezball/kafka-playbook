package com.playbook.aggregator.service;

import com.playbook.aggregator.model.CategoryCount;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
public class CountLogger {

    private static final Logger log = LoggerFactory.getLogger(CountLogger.class);

    @KafkaListener(topics = "category-counts", groupId = "count-logger-group")
    public void handleCategoryCount(CategoryCount count) {
        log.info("[DASHBOARD] {} | {} orders | window: {} - {}",
                padRight(count.category(), 13),
                count.count(),
                count.windowStart(),
                count.windowEnd()
        );
    }

    private static String padRight(String s, int width) {
        return String.format("%-" + width + "s", s);
    }
}
