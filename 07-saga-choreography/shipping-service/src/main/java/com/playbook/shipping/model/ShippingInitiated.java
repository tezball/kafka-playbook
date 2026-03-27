package com.playbook.shipping.model;

import java.time.Instant;

public record ShippingInitiated(
        String orderId,
        String paymentId,
        String trackingId,
        Instant timestamp
) {}
