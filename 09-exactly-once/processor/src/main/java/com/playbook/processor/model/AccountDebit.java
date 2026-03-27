package com.playbook.processor.model;

import java.math.BigDecimal;
import java.time.Instant;

public record AccountDebit(
        String transferId,
        String accountId,
        BigDecimal amount,
        String description,
        Instant timestamp
) {}
