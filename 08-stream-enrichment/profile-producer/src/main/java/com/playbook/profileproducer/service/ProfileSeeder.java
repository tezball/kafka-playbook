package com.playbook.profileproducer.service;

import com.playbook.profileproducer.model.UserProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ProfileSeeder {

    private static final Logger log = LoggerFactory.getLogger(ProfileSeeder.class);
    private static final String TOPIC = "user-profiles";

    private final KafkaTemplate<String, UserProfile> kafkaTemplate;

    private static final List<UserProfile> PROFILES = List.of(
            new UserProfile("USR-1001", "Alice Johnson", "alice@example.com", "PRO"),
            new UserProfile("USR-1002", "Bob Smith", "bob@example.com", "FREE"),
            new UserProfile("USR-1003", "Carol Williams", "carol@example.com", "ENTERPRISE"),
            new UserProfile("USR-1004", "Dave Brown", "dave@example.com", "PRO"),
            new UserProfile("USR-1005", "Eve Davis", "eve@example.com", "FREE"),
            new UserProfile("USR-1006", "Frank Miller", "frank@example.com", "ENTERPRISE"),
            new UserProfile("USR-1007", "Grace Wilson", "grace@example.com", "PRO"),
            new UserProfile("USR-1008", "Heidi Moore", "heidi@example.com", "FREE"),
            new UserProfile("USR-1009", "Ivan Taylor", "ivan@example.com", "PRO"),
            new UserProfile("USR-1010", "Judy Anderson", "judy@example.com", "ENTERPRISE")
    );

    public ProfileSeeder(KafkaTemplate<String, UserProfile> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void seedProfiles() {
        log.info("Seeding {} user profiles to topic '{}'...", PROFILES.size(), TOPIC);

        for (UserProfile profile : PROFILES) {
            kafkaTemplate.send(TOPIC, profile.userId(), profile)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error("Failed to seed profile {}: {}", profile.userId(), ex.getMessage());
                        } else {
                            var metadata = result.getRecordMetadata();
                            log.info("Profile {} ({}, {}) seeded to partition {} offset {}",
                                    profile.userId(), profile.name(), profile.tier(),
                                    metadata.partition(), metadata.offset());
                        }
                    });
        }

        log.info("Profile seeding complete.");
    }
}
