package com.playbook.consumer.service;

import com.playbook.consumer.config.ConsumerConfig;
import com.playbook.consumer.model.RegionalOrderEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.stereotype.Service;

@Service
public class RegionOrderProcessor {

    private static final Logger log = LoggerFactory.getLogger(RegionOrderProcessor.class);

    private final ConsumerConfig consumerConfig;

    public RegionOrderProcessor(ConsumerConfig consumerConfig) {
        this.consumerConfig = consumerConfig;
    }

    @KafkaListener(topics = "regional-orders", groupId = "${CONSUMER_GROUP:na-region-group}")
    public void handleRegionalOrder(
            @Payload RegionalOrderEvent event,
            @Header(KafkaHeaders.RECEIVED_KEY) String key,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset
    ) {
        // Only process orders matching this consumer's region
        if (!event.region().equals(consumerConfig.getRegion())) {
            log.debug("Skipping order {} for region {} (this consumer handles {})",
                    event.orderId(), event.region(), consumerConfig.getRegion());
            return;
        }

        log.info("""

                ============================================
                  [{}] ORDER PROCESSED
                --------------------------------------------
                  Order:    {}
                  Customer: {}
                  Product:  {}
                  Amount:   ${}
                  Region:   {}
                  Key:      {}  Partition: {}  Offset: {}
                ============================================""",
                event.region(),
                event.orderId(),
                event.customerName(),
                event.product(),
                event.amount(),
                consumerConfig.getRegionDisplayName(),
                key,
                partition,
                offset
        );
    }
}
