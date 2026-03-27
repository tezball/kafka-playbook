package com.playbook.shipping.model;

import java.time.Instant;

public record OrderCancelled(
        String orderId,
        String reason,
        Instant timestamp
) {}
