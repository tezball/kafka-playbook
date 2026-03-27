package com.playbook.inventory.model;

import java.time.Instant;

public record InventoryFailed(
        String orderId,
        String reason,
        Instant timestamp
) {}
