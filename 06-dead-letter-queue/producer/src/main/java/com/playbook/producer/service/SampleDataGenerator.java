package com.playbook.producer.service;

import com.playbook.producer.model.PaymentEvent;
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

    private final PaymentProducerService producerService;
    private final AtomicInteger paymentSequence = new AtomicInteger(1001);
    private final AtomicInteger orderSequence = new AtomicInteger(5001);

    private static final List<String> FIRST_NAMES = List.of(
            "alice", "bob", "carol", "dave", "eve", "frank", "grace",
            "heidi", "ivan", "judy", "karl", "linda", "mike", "nancy"
    );

    private static final List<String> DOMAINS = List.of(
            "gmail.com", "outlook.com", "company.io", "shop.co", "mail.com"
    );

    private static final List<String> CURRENCIES = List.of("USD", "EUR", "GBP");

    private static final List<String> CARD_LAST4 = List.of(
            "4532", "7891", "1234", "5678", "9012", "3456", "6789",
            "2345", "8901", "0123", "4567", "7890", "3210", "6543"
    );

    public SampleDataGenerator(PaymentProducerService producerService) {
        this.producerService = producerService;
    }

    @Scheduled(fixedRate = 10000, initialDelay = 5000)
    public void sendPeriodicPayment() {
        var payment = generateRandomPayment();
        log.info("Auto-sending payment {} for ${} {}", payment.paymentId(), payment.amount(), payment.currency());
        producerService.sendPayment(payment);
    }

    public String nextPaymentId() {
        return "PAY-" + paymentSequence.getAndIncrement();
    }

    public String nextOrderId() {
        return "ORD-" + orderSequence.getAndIncrement();
    }

    public PaymentEvent generateRandomPayment() {
        var random = ThreadLocalRandom.current();
        var paymentId = nextPaymentId();
        var orderId = nextOrderId();
        var name = FIRST_NAMES.get(random.nextInt(FIRST_NAMES.size()));
        var domain = DOMAINS.get(random.nextInt(DOMAINS.size()));
        var email = name + "@" + domain;
        var currency = CURRENCIES.get(random.nextInt(CURRENCIES.size()));
        var cardLast4 = CARD_LAST4.get(random.nextInt(CARD_LAST4.size()));

        // ~70% valid payments ($10-$400), ~30% invalid payments ($501-$2000)
        BigDecimal amount;
        if (random.nextDouble() < 0.70) {
            amount = BigDecimal.valueOf(random.nextDouble(10.0, 400.0)).setScale(2, RoundingMode.HALF_UP);
        } else {
            amount = BigDecimal.valueOf(random.nextDouble(501.0, 2000.0)).setScale(2, RoundingMode.HALF_UP);
        }

        return new PaymentEvent(paymentId, orderId, amount, currency, cardLast4, email, Instant.now());
    }
}
