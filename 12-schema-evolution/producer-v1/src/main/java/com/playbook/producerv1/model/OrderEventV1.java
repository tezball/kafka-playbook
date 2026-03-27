package com.playbook.producerv1.model;

import java.math.BigDecimal;
import java.time.Instant;

public record OrderEventV1(
        String orderId,
        String customerEmail,
        BigDecimal totalPrice,
        Instant createdAt
) {}
