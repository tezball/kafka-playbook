package com.playbook.processor.model;

import java.math.BigDecimal;
import java.time.Instant;

public record AccountCredit(
        String transferId,
        String accountId,
        BigDecimal amount,
        String description,
        Instant timestamp
) {}
