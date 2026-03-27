package com.playbook.producer.service;

import com.playbook.producer.model.TransferRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class TransferProducerService {

    private static final Logger log = LoggerFactory.getLogger(TransferProducerService.class);
    private static final String TOPIC = "transfer-requests";

    private final KafkaTemplate<String, TransferRequest> kafkaTemplate;

    public TransferProducerService(KafkaTemplate<String, TransferRequest> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void sendTransfer(TransferRequest event) {
        log.info("Producing transfer request: {} -> {} to {} (${})",
                event.transferId(), event.fromAccount(), event.toAccount(), event.amount());
        kafkaTemplate.send(TOPIC, event.transferId(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to send transfer {}: {}", event.transferId(), ex.getMessage());
                    } else {
                        var metadata = result.getRecordMetadata();
                        log.info("Transfer {} sent to partition {} offset {}",
                                event.transferId(), metadata.partition(), metadata.offset());
                    }
                });
    }
}
