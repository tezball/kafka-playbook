package com.playbook.producer.service;

import com.playbook.producer.model.TransferRequest;
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

    private final TransferProducerService producerService;
    private final AtomicInteger transferSequence = new AtomicInteger(1001);

    private static final List<String> ACCOUNTS = List.of(
            "ACC-1001", "ACC-1002", "ACC-1003", "ACC-1004", "ACC-1005"
    );

    private static final List<String> DESCRIPTIONS = List.of(
            "Rent payment", "Dinner split", "Freelance invoice",
            "Utility bill", "Gym membership", "Concert tickets",
            "Grocery reimbursement", "Loan repayment", "Birthday gift",
            "Office supplies", "Travel expense", "Subscription fee"
    );

    public SampleDataGenerator(TransferProducerService producerService) {
        this.producerService = producerService;
    }

    @Scheduled(fixedRate = 10000, initialDelay = 5000)
    public void sendPeriodicTransfer() {
        var transfer = generateRandomTransfer();
        log.info("Auto-sending transfer {} from {} to {} (${})",
                transfer.transferId(), transfer.fromAccount(), transfer.toAccount(), transfer.amount());
        producerService.sendTransfer(transfer);
    }

    public String nextTransferId() {
        return "TXF-" + transferSequence.getAndIncrement();
    }

    public TransferRequest generateRandomTransfer() {
        var random = ThreadLocalRandom.current();
        var transferId = nextTransferId();

        // Pick two different accounts
        var fromIndex = random.nextInt(ACCOUNTS.size());
        var toIndex = random.nextInt(ACCOUNTS.size());
        while (toIndex == fromIndex) {
            toIndex = random.nextInt(ACCOUNTS.size());
        }
        var fromAccount = ACCOUNTS.get(fromIndex);
        var toAccount = ACCOUNTS.get(toIndex);

        // Random amount between $10 and $500
        var amount = BigDecimal.valueOf(random.nextDouble(10.0, 500.0))
                .setScale(2, RoundingMode.HALF_UP);

        var description = DESCRIPTIONS.get(random.nextInt(DESCRIPTIONS.size()));

        return new TransferRequest(transferId, fromAccount, toAccount, amount, description, Instant.now());
    }
}
