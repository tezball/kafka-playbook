package com.playbook.consumer.service;

import com.playbook.consumer.model.OrderEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
public class EmailNotificationService {

    private static final Logger log = LoggerFactory.getLogger(EmailNotificationService.class);

    @KafkaListener(topics = "order-events", groupId = "email-notification-group")
    public void handleOrderEvent(OrderEvent event) {
        log.info("""

                ============================================
                  EMAIL NOTIFICATION
                --------------------------------------------
                  To:      {}
                  Order:   {}
                  Product: {} (x{})
                  Total:   ${}
                  Status:  CONFIRMED
                ============================================""",
                event.customerEmail(),
                event.orderId(),
                event.productName(),
                event.quantity(),
                event.totalPrice()
        );
    }
}
