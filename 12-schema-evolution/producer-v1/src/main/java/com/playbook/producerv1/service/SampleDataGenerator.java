package com.playbook.producerv1.service;

import com.playbook.producerv1.model.OrderEventV1;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class SampleDataGenerator {

    private static final Logger log = LoggerFactory.getLogger(SampleDataGenerator.class);

    private final OrderProducerService producerService;
    private final AtomicInteger orderSequence = new AtomicInteger(1001);

    private static final List<String> FIRST_NAMES = List.of(
            "alice", "bob", "carol", "dave", "eve", "frank", "grace",
            "heidi", "ivan", "judy", "karl", "linda", "mike", "nancy"
    );

    private static final List<String> DOMAINS = List.of(
            "gmail.com", "outlook.com", "company.io", "shop.co", "mail.com"
    );

    private static final List<BigDecimal> PRICES = List.of(
            new BigDecimal("29.99"), new BigDecimal("49.99"), new BigDecimal("79.99"),
            new BigDecimal("99.99"), new BigDecimal("149.99"), new BigDecimal("199.99"),
            new BigDecimal("249.99"), new BigDecimal("349.99"), new BigDecimal("449.99")
    );

    public SampleDataGenerator(OrderProducerService producerService) {
        this.producerService = producerService;
    }

    @Scheduled(fixedRate = 10000, initialDelay = 5000)
    public void sendPeriodicOrder() {
        var order = generateRandomOrder();
        log.info("[PRODUCER-V1] Auto-sending order {}", order.orderId());
        producerService.sendOrder(order);
    }

    public String nextOrderId() {
        return "ORD-" + orderSequence.getAndIncrement();
    }

    public OrderEventV1 generateRandomOrder() {
        var random = ThreadLocalRandom.current();
        var orderId = nextOrderId();
        var name = FIRST_NAMES.get(random.nextInt(FIRST_NAMES.size()));
        var domain = DOMAINS.get(random.nextInt(DOMAINS.size()));
        var email = name + "@" + domain;
        var price = PRICES.get(random.nextInt(PRICES.size()));
        var quantity = random.nextInt(1, 5);
        var total = price.multiply(BigDecimal.valueOf(quantity)).setScale(2, RoundingMode.HALF_UP);

        return new OrderEventV1(orderId, email, total, Instant.now());
    }
}
