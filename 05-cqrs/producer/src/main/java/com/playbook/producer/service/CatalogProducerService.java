package com.playbook.producer.service;

import com.playbook.producer.model.ProductCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class CatalogProducerService {

    private static final Logger log = LoggerFactory.getLogger(CatalogProducerService.class);
    private static final String TOPIC = "product-commands";

    private final KafkaTemplate<String, ProductCommand> kafkaTemplate;

    public CatalogProducerService(KafkaTemplate<String, ProductCommand> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void sendCommand(ProductCommand command) {
        log.info("Producing command: {} {} -> {}", command.action(), command.productId(), command.name());
        kafkaTemplate.send(TOPIC, command.productId(), command)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to send command {}: {}", command.commandId(), ex.getMessage());
                    } else {
                        var metadata = result.getRecordMetadata();
                        log.info("Command {} sent to partition {} offset {}",
                                command.commandId(), metadata.partition(), metadata.offset());
                    }
                });
    }
}
