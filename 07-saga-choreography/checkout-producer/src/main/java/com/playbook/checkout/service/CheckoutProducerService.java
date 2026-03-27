package com.playbook.checkout.service;

import com.playbook.checkout.model.CheckoutRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class CheckoutProducerService {

    private static final Logger log = LoggerFactory.getLogger(CheckoutProducerService.class);
    private static final String TOPIC = "checkout-requested";

    private final KafkaTemplate<String, CheckoutRequest> kafkaTemplate;

    public CheckoutProducerService(KafkaTemplate<String, CheckoutRequest> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void sendCheckout(CheckoutRequest event) {
        log.info("[CHECKOUT] {} | {} — {} (x{}) — ${}",
                event.orderId(), event.customerId(), event.productName(),
                event.quantity(), event.totalAmount());
        kafkaTemplate.send(TOPIC, event.orderId(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("[CHECKOUT] {} | Failed to send: {}", event.orderId(), ex.getMessage());
                    } else {
                        var metadata = result.getRecordMetadata();
                        log.info("[CHECKOUT] {} | Sent to partition {} offset {}",
                                event.orderId(), metadata.partition(), metadata.offset());
                    }
                });
    }
}
