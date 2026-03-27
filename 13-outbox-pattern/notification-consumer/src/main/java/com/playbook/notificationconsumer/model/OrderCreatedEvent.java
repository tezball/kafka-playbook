package com.playbook.notificationconsumer.model;

import java.math.BigDecimal;
import java.time.Instant;

public record OrderCreatedEvent(
        String orderId,
        String customerEmail,
        String productName,
        int quantity,
        BigDecimal totalPrice,
        Instant createdAt
) {}
