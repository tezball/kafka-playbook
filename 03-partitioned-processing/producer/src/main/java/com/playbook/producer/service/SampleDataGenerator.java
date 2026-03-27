package com.playbook.producer.service;

import com.playbook.producer.model.RegionalOrderEvent;
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

    private static final List<String> REGIONS = List.of("NA", "EU", "APAC");

    private static final Map<String, List<String>> CUSTOMER_NAMES = Map.of(
            "NA", List.of(
                    "John Smith", "Emily Johnson", "Michael Williams", "Sarah Davis",
                    "James Wilson", "Jessica Brown", "Robert Miller", "Ashley Taylor"
            ),
            "EU", List.of(
                    "Hans Mueller", "Sophie Dupont", "Marco Rossi", "Anna Johansson",
                    "Pierre Martin", "Elena Garcia", "Klaus Weber", "Ingrid Larsen"
            ),
            "APAC", List.of(
                    "Yuki Tanaka", "Wei Chen", "Priya Sharma", "Min-jun Kim",
                    "Sakura Yamamoto", "Raj Patel", "Hana Suzuki", "Li Wei"
            )
    );

    private static final List<String> PRODUCTS = List.of(
            "Laptop Pro 16", "Wireless Headphones", "Mechanical Keyboard",
            "USB-C Hub", "27\" 4K Monitor", "Webcam HD Pro",
            "Wireless Mouse", "Laptop Stand", "Noise Cancelling Earbuds",
            "Portable SSD 1TB", "Ergonomic Chair", "Thunderbolt Dock",
            "Microphone Kit", "Standing Desk Mat", "Blue Light Glasses"
    );

    private static final List<BigDecimal> PRICES = List.of(
            new BigDecimal("29.99"), new BigDecimal("49.99"), new BigDecimal("79.99"),
            new BigDecimal("149.99"), new BigDecimal("249.99"), new BigDecimal("349.99"),
            new BigDecimal("499.99"), new BigDecimal("799.99"), new BigDecimal("1299.99")
    );

    public SampleDataGenerator(OrderProducerService producerService) {
        this.producerService = producerService;
    }

    @Scheduled(fixedRate = 10000, initialDelay = 5000)
    public void sendPeriodicOrder() {
        var order = generateRandomOrder();
        log.info("Auto-sending order {} for region {}", order.orderId(), order.region());
        producerService.sendOrder(order);
    }

    public String nextOrderId() {
        return "ORD-" + orderSequence.getAndIncrement();
    }

    public RegionalOrderEvent generateRandomOrder() {
        var random = ThreadLocalRandom.current();
        var orderId = nextOrderId();
        var region = REGIONS.get(random.nextInt(REGIONS.size()));
        var names = CUSTOMER_NAMES.get(region);
        var customerName = names.get(random.nextInt(names.size()));
        var product = PRODUCTS.get(random.nextInt(PRODUCTS.size()));
        var amount = PRICES.get(random.nextInt(PRICES.size()));

        return new RegionalOrderEvent(orderId, region, customerName, product, amount, Instant.now());
    }
}
