package com.playbook.aggregator.model;

import java.math.BigDecimal;
import java.time.Instant;

public record DashboardOrder(
        String orderId,
        String category,
        String productName,
        BigDecimal amount,
        Instant timestamp
) {}
