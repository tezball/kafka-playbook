package com.playbook.consumer.model;

import java.math.BigDecimal;
import java.time.Instant;

public record TransactionEvent(
        String transactionId,
        String accountId,
        String type,
        BigDecimal amount,
        String description,
        Instant timestamp
) {}
