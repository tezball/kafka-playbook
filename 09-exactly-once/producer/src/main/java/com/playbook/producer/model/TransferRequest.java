package com.playbook.producer.model;

import java.math.BigDecimal;
import java.time.Instant;

public record TransferRequest(
        String transferId,
        String fromAccount,
        String toAccount,
        BigDecimal amount,
        String description,
        Instant timestamp
) {}
