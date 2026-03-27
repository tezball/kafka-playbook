package com.playbook.shipping.service;

import com.playbook.shipping.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class ShippingService {

    private static final Logger log = LoggerFactory.getLogger(ShippingService.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final AtomicInteger trackingSequence = new AtomicInteger(9001);

    public ShippingService(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @KafkaListener(topics = "payment-completed", groupId = "shipping-group")
    public void handlePaymentCompleted(PaymentCompleted event) {
        var trackingId = "TRK-" + trackingSequence.getAndIncrement();

        log.info("[SHIPPING] {} | Initiating shipment — trackingId: {}",
                event.orderId(), trackingId);

        var shipped = new ShippingInitiated(
                event.orderId(),
                event.paymentId(),
                trackingId,
                Instant.now()
        );
        kafkaTemplate.send("shipping-initiated", event.orderId(), shipped);

        var completed = new OrderCompleted(
                event.orderId(),
                event.paymentId(),
                trackingId,
                Instant.now()
        );
        kafkaTemplate.send("order-completed", event.orderId(), completed);

        log.info("[SHIPPING] {} | ORDER COMPLETED \u2713", event.orderId());
    }

    @KafkaListener(topics = "payment-failed", groupId = "shipping-group")
    public void handlePaymentFailed(PaymentFailed event) {
        log.info("[SHIPPING] {} | ORDER CANCELLED \u2717 — {}", event.orderId(), event.reason());

        var cancelled = new OrderCancelled(event.orderId(), event.reason(), Instant.now());
        kafkaTemplate.send("order-cancelled", event.orderId(), cancelled);
    }

    @KafkaListener(topics = "inventory-failed", groupId = "shipping-group")
    public void handleInventoryFailed(InventoryFailed event) {
        log.info("[SHIPPING] {} | ORDER CANCELLED \u2717 — {}", event.orderId(), event.reason());

        var cancelled = new OrderCancelled(event.orderId(), event.reason(), Instant.now());
        kafkaTemplate.send("order-cancelled", event.orderId(), cancelled);
    }
}
