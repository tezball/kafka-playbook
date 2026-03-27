package com.playbook.producer.model;

import java.math.BigDecimal;
import java.time.Instant;

public record PaymentEvent(
        String paymentId,
        String orderId,
        BigDecimal amount,
        String currency,
        String cardLast4,
        String customerEmail,
        Instant timestamp
) {}
