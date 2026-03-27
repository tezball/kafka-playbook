package com.playbook.producer.service;

import com.playbook.producer.model.DashboardOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class SampleDataGenerator {

    private static final Logger log = LoggerFactory.getLogger(SampleDataGenerator.class);

    private final OrderProducerService producerService;
    private final AtomicInteger orderSequence = new AtomicInteger(1001);

    private static final Map<String, List<String>> CATEGORY_PRODUCTS = Map.of(
            "Electronics", List.of(
                    "Wireless Headphones", "Bluetooth Speaker", "USB-C Hub",
                    "Portable Charger", "Smart Watch", "Webcam HD Pro"
            ),
            "Clothing", List.of(
                    "Cotton T-Shirt", "Denim Jacket", "Running Shoes",
                    "Wool Sweater", "Baseball Cap", "Silk Scarf"
            ),
            "Books", List.of(
                    "Design Patterns", "Clean Code", "The Pragmatic Programmer",
                    "Kafka: The Definitive Guide", "Java Concurrency in Practice", "Domain-Driven Design"
            ),
            "Home & Garden", List.of(
                    "LED Desk Lamp", "Plant Pot Set", "Kitchen Scale",
                    "Garden Hose", "Throw Pillow", "Wall Clock"
            ),
            "Sports", List.of(
                    "Yoga Mat", "Resistance Bands", "Water Bottle",
                    "Running Belt", "Jump Rope", "Foam Roller"
            )
    );

    private static final List<String> CATEGORIES = List.of(
            "Electronics", "Clothing", "Books", "Home & Garden", "Sports"
    );

    private static final List<BigDecimal> UNIT_PRICES = List.of(
            new BigDecimal("9.99"), new BigDecimal("19.99"), new BigDecimal("29.99"),
            new BigDecimal("49.99"), new BigDecimal("79.99"), new BigDecimal("99.99"),
            new BigDecimal("149.99"), new BigDecimal("199.99")
    );

    public SampleDataGenerator(OrderProducerService producerService) {
        this.producerService = producerService;
    }

    @Scheduled(fixedRate = 10000, initialDelay = 5000)
    public void sendPeriodicOrder() {
        var order = generateRandomOrder();
        log.info("Auto-sending order {} for {} in {}", order.orderId(), order.productName(), order.category());
        producerService.sendOrder(order);
    }

    public String nextOrderId() {
        return "ORD-" + orderSequence.getAndIncrement();
    }

    public DashboardOrder generateRandomOrder() {
        var random = ThreadLocalRandom.current();
        var category = CATEGORIES.get(random.nextInt(CATEGORIES.size()));
        var products = CATEGORY_PRODUCTS.get(category);
        var product = products.get(random.nextInt(products.size()));
        var quantity = random.nextInt(1, 5);
        var unitPrice = UNIT_PRICES.get(random.nextInt(UNIT_PRICES.size()));
        var total = unitPrice.multiply(BigDecimal.valueOf(quantity)).setScale(2, RoundingMode.HALF_UP);

        return new DashboardOrder(nextOrderId(), category, product, total, Instant.now());
    }
}
