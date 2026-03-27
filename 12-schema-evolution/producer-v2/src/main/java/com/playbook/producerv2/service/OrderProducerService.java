package com.playbook.producerv2.service;

import com.playbook.producerv2.model.OrderEventV2;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class OrderProducerService {

    private static final Logger log = LoggerFactory.getLogger(OrderProducerService.class);
    private static final String TOPIC = "versioned-events";

    private final KafkaTemplate<String, OrderEventV2> kafkaTemplate;

    public OrderProducerService(KafkaTemplate<String, OrderEventV2> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void sendOrder(OrderEventV2 event) {
        log.info("[PRODUCER-V2] Sent {} | ${} | {} | {} | {}",
                event.orderId(), event.totalPrice(), event.customerEmail(),
                event.shippingAddress(), event.loyaltyTier());
        kafkaTemplate.send(TOPIC, event.orderId(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("[PRODUCER-V2] Failed to send order {}: {}", event.orderId(), ex.getMessage());
                    } else {
                        var metadata = result.getRecordMetadata();
                        log.info("[PRODUCER-V2] Order {} sent to partition {} offset {}",
                                event.orderId(), metadata.partition(), metadata.offset());
                    }
                });
    }
}
