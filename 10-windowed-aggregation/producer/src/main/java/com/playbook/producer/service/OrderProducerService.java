package com.playbook.producer.service;

import com.playbook.producer.model.DashboardOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class OrderProducerService {

    private static final Logger log = LoggerFactory.getLogger(OrderProducerService.class);
    private static final String TOPIC = "dashboard-orders";

    private final KafkaTemplate<String, DashboardOrder> kafkaTemplate;

    public OrderProducerService(KafkaTemplate<String, DashboardOrder> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void sendOrder(DashboardOrder order) {
        log.info("[ORDER] {} | {} | {} | ${}", order.orderId(), order.category(), order.productName(), order.amount());
        kafkaTemplate.send(TOPIC, order.category(), order)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to send order {}: {}", order.orderId(), ex.getMessage());
                    } else {
                        var metadata = result.getRecordMetadata();
                        log.info("Order {} sent to partition {} offset {}",
                                order.orderId(), metadata.partition(), metadata.offset());
                    }
                });
    }
}
