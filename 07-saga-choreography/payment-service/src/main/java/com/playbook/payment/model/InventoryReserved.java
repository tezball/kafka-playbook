package com.playbook.payment.model;

import java.math.BigDecimal;
import java.time.Instant;

public record InventoryReserved(
        String orderId,
        String customerId,
        String productName,
        int quantity,
        BigDecimal totalAmount,
        String reservationId,
        Instant timestamp
) {}
