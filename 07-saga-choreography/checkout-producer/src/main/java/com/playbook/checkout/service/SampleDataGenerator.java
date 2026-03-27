package com.playbook.checkout.service;

import com.playbook.checkout.model.CheckoutRequest;
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

    private final CheckoutProducerService producerService;
    private final AtomicInteger orderSequence = new AtomicInteger(1001);

    private static final List<String> CUSTOMER_IDS = List.of(
            "CUST-101", "CUST-102", "CUST-103", "CUST-104", "CUST-105",
            "CUST-106", "CUST-107", "CUST-108", "CUST-109", "CUST-110"
    );

    private static final List<String> PRODUCTS = List.of(
            "Laptop Pro", "Wireless Keyboard", "USB-C Hub",
            "27\" 4K Monitor", "Webcam HD Pro", "Wireless Mouse",
            "Mechanical Keyboard", "Noise Cancelling Earbuds",
            "Portable SSD 1TB", "Ergonomic Chair",
            "Thunderbolt Dock", "Microphone Kit"
    );

    private static final List<BigDecimal> UNIT_PRICES = List.of(
            new BigDecimal("29.99"), new BigDecimal("49.99"), new BigDecimal("79.99"),
            new BigDecimal("149.99"), new BigDecimal("249.99"), new BigDecimal("449.99"),
            new BigDecimal("799.99"), new BigDecimal("1299.99")
    );

    public SampleDataGenerator(CheckoutProducerService producerService) {
        this.producerService = producerService;
    }

    @Scheduled(fixedRate = 10000, initialDelay = 5000)
    public void sendPeriodicCheckout() {
        var checkout = generateRandomCheckout();
        log.info("Auto-sending checkout {} for {}", checkout.orderId(), checkout.productName());
        producerService.sendCheckout(checkout);
    }

    public String nextOrderId() {
        return "ORD-" + orderSequence.getAndIncrement();
    }

    public CheckoutRequest generateRandomCheckout() {
        var random = ThreadLocalRandom.current();
        var orderId = nextOrderId();
        var customerId = CUSTOMER_IDS.get(random.nextInt(CUSTOMER_IDS.size()));
        var product = PRODUCTS.get(random.nextInt(PRODUCTS.size()));
        var quantity = random.nextInt(1, 5);
        var unitPrice = UNIT_PRICES.get(random.nextInt(UNIT_PRICES.size()));
        var total = unitPrice.multiply(BigDecimal.valueOf(quantity)).setScale(2, RoundingMode.HALF_UP);

        return new CheckoutRequest(orderId, customerId, product, quantity, total, Instant.now());
    }
}
