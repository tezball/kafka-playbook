package com.playbook.producer.service;

import com.playbook.producer.model.OrderEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class OrderProducerService {

    private static final Logger log = LoggerFactory.getLogger(OrderProducerService.class);
    private static final String TOPIC = "order-events";

    private final KafkaTemplate<String, OrderEvent> kafkaTemplate;

    public OrderProducerService(KafkaTemplate<String, OrderEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void sendOrder(OrderEvent event) {
        log.info("Producing order event: {} -> {}", event.orderId(), event.customerEmail());
        kafkaTemplate.send(TOPIC, event.orderId(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to send order {}: {}", event.orderId(), ex.getMessage());
                    } else {
                        var metadata = result.getRecordMetadata();
                        log.info("Order {} sent to partition {} offset {}",
                                event.orderId(), metadata.partition(), metadata.offset());
                    }
                });
    }
}
