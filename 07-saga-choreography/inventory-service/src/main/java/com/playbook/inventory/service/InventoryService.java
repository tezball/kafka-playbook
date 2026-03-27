package com.playbook.inventory.service;

import com.playbook.inventory.model.CheckoutRequested;
import com.playbook.inventory.model.InventoryFailed;
import com.playbook.inventory.model.InventoryReserved;
import com.playbook.inventory.model.PaymentFailed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class InventoryService {

    private static final Logger log = LoggerFactory.getLogger(InventoryService.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final AtomicInteger reservationSequence = new AtomicInteger(4001);

    public InventoryService(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @KafkaListener(topics = "checkout-requested", groupId = "inventory-group")
    public void handleCheckoutRequested(CheckoutRequested event) {
        log.info("[INVENTORY] {} | Checking stock for {} (x{})...",
                event.orderId(), event.productName(), event.quantity());

        // Simulate inventory check: ~90% success, ~10% out of stock
        boolean inStock = ThreadLocalRandom.current().nextDouble() < 0.9;

        if (inStock) {
            var reservationId = "RES-" + reservationSequence.getAndIncrement();
            log.info("[INVENTORY] {} | RESERVED — reservationId: {}",
                    event.orderId(), reservationId);

            var reserved = new InventoryReserved(
                    event.orderId(),
                    event.customerId(),
                    event.productName(),
                    event.quantity(),
                    event.totalAmount(),
                    reservationId,
                    Instant.now()
            );
            kafkaTemplate.send("inventory-reserved", event.orderId(), reserved);
        } else {
            var reason = "Out of stock for " + event.productName();
            log.info("[INVENTORY] {} | FAILED — {}", event.orderId(), reason);

            var failed = new InventoryFailed(event.orderId(), reason, Instant.now());
            kafkaTemplate.send("inventory-failed", event.orderId(), failed);
        }
    }

    @KafkaListener(topics = "payment-failed", groupId = "inventory-group")
    public void handlePaymentFailed(PaymentFailed event) {
        log.info("[INVENTORY] {} | COMPENSATION — Releasing reserved stock (payment failed)",
                event.orderId());
    }
}
