package com.playbook.clickproducer.service;

import com.playbook.clickproducer.model.ClickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class ClickProducerService {

    private static final Logger log = LoggerFactory.getLogger(ClickProducerService.class);
    private static final String TOPIC = "clicks";

    private final KafkaTemplate<String, ClickEvent> kafkaTemplate;

    public ClickProducerService(KafkaTemplate<String, ClickEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void sendClick(ClickEvent event) {
        log.info("Producing click event: {} -> {} on {}", event.clickId(), event.userId(), event.page());
        kafkaTemplate.send(TOPIC, event.userId(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to send click {}: {}", event.clickId(), ex.getMessage());
                    } else {
                        var metadata = result.getRecordMetadata();
                        log.info("Click {} sent to partition {} offset {}",
                                event.clickId(), metadata.partition(), metadata.offset());
                    }
                });
    }
}
