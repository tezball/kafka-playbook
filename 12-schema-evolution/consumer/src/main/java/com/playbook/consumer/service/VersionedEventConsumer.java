package com.playbook.consumer.service;

import com.playbook.consumer.model.OrderEventFull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
public class VersionedEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(VersionedEventConsumer.class);

    @KafkaListener(topics = "versioned-events", groupId = "versioned-consumer-group")
    public void handleOrderEvent(OrderEventFull event) {
        int version = event.detectSchemaVersion();
        String address = event.getShippingAddress() != null ? event.getShippingAddress() : "(not provided)";
        String tier = event.getLoyaltyTier() != null ? event.getLoyaltyTier() : "(not provided)";

        log.info("""

                ============================================
                  ORDER EVENT (Schema V{})
                --------------------------------------------
                  Order:    {}
                  Email:    {}
                  Total:    ${}
                  Address:  {}
                  Tier:     {}
                ============================================""",
                version,
                event.getOrderId(),
                event.getCustomerEmail(),
                event.getTotalPrice(),
                address,
                tier
        );
    }
}
