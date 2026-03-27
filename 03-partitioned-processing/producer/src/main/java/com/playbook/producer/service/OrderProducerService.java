package com.playbook.producer.service;

import com.playbook.producer.model.RegionalOrderEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class OrderProducerService {

    private static final Logger log = LoggerFactory.getLogger(OrderProducerService.class);
    private static final String TOPIC = "regional-orders";

    private final KafkaTemplate<String, RegionalOrderEvent> kafkaTemplate;

    public OrderProducerService(KafkaTemplate<String, RegionalOrderEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void sendOrder(RegionalOrderEvent event) {
        // Use REGION as the key — all orders for the same region go to the same partition
        log.info("Producing order event: {} -> region {}", event.orderId(), event.region());
        kafkaTemplate.send(TOPIC, event.region(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to send order {}: {}", event.orderId(), ex.getMessage());
                    } else {
                        var metadata = result.getRecordMetadata();
                        log.info("Order {} [region={}] sent to partition {} offset {}",
                                event.orderId(), event.region(), metadata.partition(), metadata.offset());
                    }
                });
    }
}
