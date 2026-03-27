package com.playbook.producer.service;

import com.playbook.producer.model.TransactionEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class LedgerProducerService {

    private static final Logger log = LoggerFactory.getLogger(LedgerProducerService.class);
    private static final String TOPIC = "account-transactions";

    private final KafkaTemplate<String, TransactionEvent> kafkaTemplate;

    public LedgerProducerService(KafkaTemplate<String, TransactionEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void sendTransaction(TransactionEvent event) {
        log.info("Producing transaction event: {} -> {} {}", event.transactionId(), event.type(), event.accountId());
        kafkaTemplate.send(TOPIC, event.accountId(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to send transaction {}: {}", event.transactionId(), ex.getMessage());
                    } else {
                        var metadata = result.getRecordMetadata();
                        log.info("Transaction {} sent to partition {} offset {}",
                                event.transactionId(), metadata.partition(), metadata.offset());
                    }
                });
    }
}
