package com.playbook.shipping.model;

import java.math.BigDecimal;
import java.time.Instant;

public record PaymentCompleted(
        String orderId,
        String paymentId,
        BigDecimal amount,
        Instant timestamp
) {}
