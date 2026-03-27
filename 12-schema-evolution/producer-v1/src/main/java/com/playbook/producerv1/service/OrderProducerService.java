package com.playbook.producerv1.service;

import com.playbook.producerv1.model.OrderEventV1;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class OrderProducerService {

    private static final Logger log = LoggerFactory.getLogger(OrderProducerService.class);
    private static final String TOPIC = "versioned-events";

    private final KafkaTemplate<String, OrderEventV1> kafkaTemplate;

    public OrderProducerService(KafkaTemplate<String, OrderEventV1> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void sendOrder(OrderEventV1 event) {
        log.info("[PRODUCER-V1] Sent {} | ${} | {}",
                event.orderId(), event.totalPrice(), event.customerEmail());
        kafkaTemplate.send(TOPIC, event.orderId(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("[PRODUCER-V1] Failed to send order {}: {}", event.orderId(), ex.getMessage());
                    } else {
                        var metadata = result.getRecordMetadata();
                        log.info("[PRODUCER-V1] Order {} sent to partition {} offset {}",
                                event.orderId(), metadata.partition(), metadata.offset());
                    }
                });
    }
}
