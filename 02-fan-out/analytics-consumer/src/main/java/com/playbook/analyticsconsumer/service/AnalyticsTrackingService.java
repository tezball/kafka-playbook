package com.playbook.analyticsconsumer.service;

import com.playbook.analyticsconsumer.model.UserSignupEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class AnalyticsTrackingService {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsTrackingService.class);

    private static final List<String> SOURCES = List.of(
            "organic", "referral", "social", "paid_search", "direct", "email_campaign"
    );

    @KafkaListener(topics = "user-signups", groupId = "analytics-tracking-group")
    public void handleSignupEvent(UserSignupEvent event) {
        var source = SOURCES.get(ThreadLocalRandom.current().nextInt(SOURCES.size()));
        log.info("[ANALYTICS] New signup tracked: {} | Plan: {} | Source: {}",
                event.userId(),
                event.plan(),
                source
        );
    }
}
