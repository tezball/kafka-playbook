package com.playbook.enricher.service;

import com.playbook.enricher.model.EnrichedClick;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
public class EnrichedClickLogger {

    private static final Logger log = LoggerFactory.getLogger(EnrichedClickLogger.class);

    @KafkaListener(topics = "enriched-clicks", groupId = "enriched-click-logger-group")
    public void logEnrichedClick(EnrichedClick event) {
        String actionVerb = switch (event.action()) {
            case "VIEW" -> "viewed";
            case "CLICK" -> "clicked";
            case "SCROLL" -> "scrolled";
            default -> event.action().toLowerCase();
        };

        log.info("[ENRICHED] {} ({}, {}) {} {} at {}",
                event.userId(),
                event.userName(),
                event.userTier(),
                actionVerb,
                event.page(),
                event.timestamp()
        );
    }
}
