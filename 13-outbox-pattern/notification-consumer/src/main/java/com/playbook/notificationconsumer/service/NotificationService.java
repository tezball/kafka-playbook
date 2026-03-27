package com.playbook.notificationconsumer.service;

import com.playbook.notificationconsumer.model.OrderCreatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    @KafkaListener(topics = "order-events", groupId = "notification-group")
    public void handleOrderCreatedEvent(OrderCreatedEvent event) {
        log.info("""

                ============================================
                  ORDER NOTIFICATION
                --------------------------------------------
                  Order:    {}
                  Email:    {}
                  Product:  {} (x{})
                  Total:    ${}
                  Status:   Reliably delivered via outbox
                ============================================""",
                event.orderId(),
                event.customerEmail(),
                event.productName(),
                event.quantity(),
                event.totalPrice()
        );
    }
}
