package com.playbook.payment.service;

import com.playbook.payment.model.InventoryReserved;
import com.playbook.payment.model.PaymentCompleted;
import com.playbook.payment.model.PaymentFailed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final AtomicInteger paymentSequence = new AtomicInteger(7001);

    public PaymentService(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @KafkaListener(topics = "inventory-reserved", groupId = "payment-group")
    public void handleInventoryReserved(InventoryReserved event) {
        log.info("[PAYMENT] {} | Processing ${}...", event.orderId(), event.totalAmount());

        // Simulate payment: ~80% success, ~20% failure
        boolean approved = ThreadLocalRandom.current().nextDouble() < 0.8;

        if (approved) {
            var paymentId = "PAY-" + paymentSequence.getAndIncrement();
            log.info("[PAYMENT] {} | APPROVED — paymentId: {}", event.orderId(), paymentId);

            var completed = new PaymentCompleted(
                    event.orderId(),
                    paymentId,
                    event.totalAmount(),
                    Instant.now()
            );
            kafkaTemplate.send("payment-completed", event.orderId(), completed);
        } else {
            var reason = "Card declined for $" + event.totalAmount();
            log.info("[PAYMENT] {} | DECLINED — {}", event.orderId(), reason);

            var failed = new PaymentFailed(event.orderId(), reason, Instant.now());
            kafkaTemplate.send("payment-failed", event.orderId(), failed);
        }
    }
}
