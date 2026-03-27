package com.playbook.consumer.model;

import java.math.BigDecimal;
import java.time.Instant;

public record OrderEvent(
        String orderId,
        String customerEmail,
        String productName,
        int quantity,
        BigDecimal totalPrice,
        Instant createdAt
) {}
