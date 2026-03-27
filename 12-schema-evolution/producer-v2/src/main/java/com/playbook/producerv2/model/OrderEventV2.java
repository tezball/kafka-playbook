package com.playbook.producerv2.model;

import java.math.BigDecimal;
import java.time.Instant;

public record OrderEventV2(
        String orderId,
        String customerEmail,
        BigDecimal totalPrice,
        String shippingAddress,
        String loyaltyTier,
        Instant createdAt
) {}
