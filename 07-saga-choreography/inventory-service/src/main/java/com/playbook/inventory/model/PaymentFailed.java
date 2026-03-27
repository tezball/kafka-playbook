package com.playbook.inventory.model;

import java.time.Instant;

public record PaymentFailed(
        String orderId,
        String reason,
        Instant timestamp
) {}
