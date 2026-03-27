package com.playbook.checkout.model;

import java.math.BigDecimal;
import java.time.Instant;

public record CheckoutRequest(
        String orderId,
        String customerId,
        String productName,
        int quantity,
        BigDecimal totalAmount,
        Instant timestamp
) {}
