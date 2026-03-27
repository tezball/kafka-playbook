package com.playbook.producer.service;

import com.playbook.producer.model.UserSignupEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class SampleDataGenerator {

    private static final Logger log = LoggerFactory.getLogger(SampleDataGenerator.class);

    private final SignupProducerService producerService;
    private final AtomicInteger userSequence = new AtomicInteger(1001);

    private static final List<String> FIRST_NAMES = List.of(
            "Alice", "Bob", "Carol", "Dave", "Eve", "Frank", "Grace",
            "Heidi", "Ivan", "Judy", "Karl", "Linda", "Mike", "Nancy"
    );

    private static final List<String> LAST_NAMES = List.of(
            "Johnson", "Smith", "Williams", "Brown", "Jones", "Garcia",
            "Miller", "Davis", "Rodriguez", "Martinez", "Wilson", "Anderson"
    );

    private static final List<String> DOMAINS = List.of(
            "gmail.com", "outlook.com", "company.io", "startup.co", "mail.com"
    );

    private static final List<String> PLANS = List.of(
            "FREE", "PRO", "ENTERPRISE"
    );

    public SampleDataGenerator(SignupProducerService producerService) {
        this.producerService = producerService;
    }

    @Scheduled(fixedRate = 10000, initialDelay = 5000)
    public void sendPeriodicSignup() {
        var signup = generateRandomSignup();
        log.info("Auto-sending signup {} for {}", signup.userId(), signup.name());
        producerService.sendSignup(signup);
    }

    public String nextUserId() {
        return "USR-" + userSequence.getAndIncrement();
    }

    public UserSignupEvent generateRandomSignup() {
        var random = ThreadLocalRandom.current();
        var userId = nextUserId();
        var firstName = FIRST_NAMES.get(random.nextInt(FIRST_NAMES.size()));
        var lastName = LAST_NAMES.get(random.nextInt(LAST_NAMES.size()));
        var name = firstName + " " + lastName;
        var domain = DOMAINS.get(random.nextInt(DOMAINS.size()));
        var email = firstName.toLowerCase() + "@" + domain;
        var plan = PLANS.get(random.nextInt(PLANS.size()));

        return new UserSignupEvent(userId, email, name, plan, Instant.now());
    }
}
