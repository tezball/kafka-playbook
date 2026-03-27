package com.playbook.shipping.model;

import java.time.Instant;

public record InventoryFailed(
        String orderId,
        String reason,
        Instant timestamp
) {}
