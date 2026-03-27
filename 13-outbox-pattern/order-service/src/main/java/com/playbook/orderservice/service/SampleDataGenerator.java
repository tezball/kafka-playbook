package com.playbook.orderservice.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class SampleDataGenerator {

    private static final Logger log = LoggerFactory.getLogger(SampleDataGenerator.class);

    private final OrderService orderService;
    private final AtomicInteger orderSequence = new AtomicInteger(1001);

    private static final List<String> FIRST_NAMES = List.of(
            "alice", "bob", "carol", "dave", "eve", "frank", "grace",
            "heidi", "ivan", "judy", "karl", "linda", "mike", "nancy"
    );

    private static final List<String> DOMAINS = List.of(
            "gmail.com", "outlook.com", "company.io", "shop.co", "mail.com"
    );

    private static final List<String> PRODUCTS = List.of(
            "Wireless Headphones", "Mechanical Keyboard", "USB-C Hub",
            "27\" 4K Monitor", "Webcam HD Pro", "Wireless Mouse",
            "Laptop Stand", "Blue Light Glasses", "Desk Lamp",
            "Noise Cancelling Earbuds", "Portable SSD 1TB", "Ergonomic Chair",
            "Standing Desk Mat", "Thunderbolt Dock", "Microphone Kit"
    );

    private static final List<BigDecimal> UNIT_PRICES = List.of(
            new BigDecimal("29.99"), new BigDecimal("49.99"), new BigDecimal("79.99"),
            new BigDecimal("99.99"), new BigDecimal("149.99"), new BigDecimal("199.99"),
            new BigDecimal("249.99"), new BigDecimal("349.99"), new BigDecimal("449.99")
    );

    public SampleDataGenerator(OrderService orderService) {
        this.orderService = orderService;
    }

    @Scheduled(fixedRate = 10000, initialDelay = 5000)
    public void sendPeriodicOrder() {
        createRandomOrder();
    }

    public String nextOrderId() {
        return "ORD-" + orderSequence.getAndIncrement();
    }

    public void createRandomOrder() {
        var random = ThreadLocalRandom.current();
        var orderId = nextOrderId();
        var name = FIRST_NAMES.get(random.nextInt(FIRST_NAMES.size()));
        var domain = DOMAINS.get(random.nextInt(DOMAINS.size()));
        var email = name + "@" + domain;
        var product = PRODUCTS.get(random.nextInt(PRODUCTS.size()));
        var quantity = random.nextInt(1, 5);
        var unitPrice = UNIT_PRICES.get(random.nextInt(UNIT_PRICES.size()));
        var total = unitPrice.multiply(BigDecimal.valueOf(quantity)).setScale(2, RoundingMode.HALF_UP);

        log.info("Auto-generating order {} for {}", orderId, product);
        orderService.createOrder(orderId, email, product, quantity, total);
    }
}
