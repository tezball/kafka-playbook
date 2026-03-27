package com.playbook.producer.service;

import com.playbook.producer.model.TransactionEvent;
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

    private final LedgerProducerService producerService;
    private final AtomicInteger txnSequence = new AtomicInteger(1001);

    private static final List<String> ACCOUNT_IDS = List.of(
            "ACC-1001", "ACC-1002", "ACC-1003", "ACC-1004", "ACC-1005"
    );

    private static final List<CreditTemplate> CREDIT_TEMPLATES = List.of(
            new CreditTemplate("Salary deposit", new BigDecimal("3500.00"), new BigDecimal("5500.00")),
            new CreditTemplate("Transfer in", new BigDecimal("100.00"), new BigDecimal("2000.00")),
            new CreditTemplate("Refund", new BigDecimal("15.00"), new BigDecimal("250.00"))
    );

    private static final List<DebitTemplate> DEBIT_TEMPLATES = List.of(
            new DebitTemplate("Monthly rent", new BigDecimal("1200.00"), new BigDecimal("2500.00")),
            new DebitTemplate("Groceries", new BigDecimal("35.00"), new BigDecimal("180.00")),
            new DebitTemplate("Utilities", new BigDecimal("75.00"), new BigDecimal("250.00")),
            new DebitTemplate("Subscription", new BigDecimal("9.99"), new BigDecimal("49.99"))
    );

    public SampleDataGenerator(LedgerProducerService producerService) {
        this.producerService = producerService;
    }

    @Scheduled(fixedRate = 10000, initialDelay = 5000)
    public void sendPeriodicTransaction() {
        var txn = generateRandomTransaction();
        log.info("Auto-sending transaction {} for {}", txn.transactionId(), txn.accountId());
        producerService.sendTransaction(txn);
    }

    public String nextTransactionId() {
        return "TXN-" + txnSequence.getAndIncrement();
    }

    public TransactionEvent generateRandomTransaction() {
        var random = ThreadLocalRandom.current();
        var txnId = nextTransactionId();
        var accountId = ACCOUNT_IDS.get(random.nextInt(ACCOUNT_IDS.size()));

        // 50/50 chance of credit vs debit
        if (random.nextBoolean()) {
            var template = CREDIT_TEMPLATES.get(random.nextInt(CREDIT_TEMPLATES.size()));
            var amount = randomAmount(template.minAmount(), template.maxAmount());
            return new TransactionEvent(txnId, accountId, "CREDIT", amount, template.description(), Instant.now());
        } else {
            var template = DEBIT_TEMPLATES.get(random.nextInt(DEBIT_TEMPLATES.size()));
            var amount = randomAmount(template.minAmount(), template.maxAmount());
            return new TransactionEvent(txnId, accountId, "DEBIT", amount, template.description(), Instant.now());
        }
    }

    private BigDecimal randomAmount(BigDecimal min, BigDecimal max) {
        var random = ThreadLocalRandom.current();
        var range = max.subtract(min).doubleValue();
        var value = min.doubleValue() + (random.nextDouble() * range);
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }

    private record CreditTemplate(String description, BigDecimal minAmount, BigDecimal maxAmount) {}
    private record DebitTemplate(String description, BigDecimal minAmount, BigDecimal maxAmount) {}
}
