package com.playbook.queryservice.model;

import java.math.BigDecimal;
import java.time.Instant;

public record ProductView(
        String productId,
        String name,
        String category,
        BigDecimal price,
        Instant lastUpdated
) {}
