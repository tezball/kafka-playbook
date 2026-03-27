package com.playbook.consumer.model;

import java.math.BigDecimal;
import java.time.Instant;

public record RegionalOrderEvent(
        String orderId,
        String region,
        String customerName,
        String product,
        BigDecimal amount,
        Instant createdAt
) {}
