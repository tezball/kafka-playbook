package com.playbook.clickproducer.service;

import com.playbook.clickproducer.model.ClickEvent;
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

    private final ClickProducerService producerService;
    private final AtomicInteger clickSequence = new AtomicInteger(1);

    private static final List<String> USER_IDS = List.of(
            "USR-1001", "USR-1002", "USR-1003", "USR-1004", "USR-1005",
            "USR-1006", "USR-1007", "USR-1008", "USR-1009", "USR-1010"
    );

    private static final List<String> PAGES = List.of(
            "/home", "/products", "/products/123", "/products/456",
            "/cart", "/checkout", "/profile", "/orders", "/search",
            "/products/789", "/categories/electronics"
    );

    private static final List<String> ACTIONS = List.of(
            "VIEW", "CLICK", "SCROLL"
    );

    public SampleDataGenerator(ClickProducerService producerService) {
        this.producerService = producerService;
    }

    @Scheduled(fixedRate = 10000, initialDelay = 15000)
    public void sendPeriodicClick() {
        var click = generateRandomClick();
        log.info("Auto-sending click {} for {} on {}", click.clickId(), click.userId(), click.page());
        producerService.sendClick(click);
    }

    public ClickEvent generateRandomClick() {
        var random = ThreadLocalRandom.current();
        var clickId = "CLK-" + clickSequence.getAndIncrement();
        var userId = USER_IDS.get(random.nextInt(USER_IDS.size()));
        var page = PAGES.get(random.nextInt(PAGES.size()));
        var action = ACTIONS.get(random.nextInt(ACTIONS.size()));

        return new ClickEvent(clickId, userId, page, action, Instant.now());
    }
}
