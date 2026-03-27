package com.playbook.producer.model;

import java.math.BigDecimal;
import java.time.Instant;

public record ProductCommand(
        String commandId,
        String productId,
        String action,
        String name,
        String category,
        BigDecimal price,
        Instant timestamp
) {}
