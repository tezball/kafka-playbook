package com.playbook.payment.model;

import java.time.Instant;

public record PaymentFailed(
        String orderId,
        String reason,
        Instant timestamp
) {}
