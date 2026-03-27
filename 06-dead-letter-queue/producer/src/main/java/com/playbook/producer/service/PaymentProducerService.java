package com.playbook.producer.service;

import com.playbook.producer.model.PaymentEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class PaymentProducerService {

    private static final Logger log = LoggerFactory.getLogger(PaymentProducerService.class);
    private static final String TOPIC = "payments";

    private final KafkaTemplate<String, PaymentEvent> kafkaTemplate;

    public PaymentProducerService(KafkaTemplate<String, PaymentEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void sendPayment(PaymentEvent event) {
        log.info("Producing payment event: {} -> ${} {}", event.paymentId(), event.amount(), event.currency());
        kafkaTemplate.send(TOPIC, event.paymentId(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to send payment {}: {}", event.paymentId(), ex.getMessage());
                    } else {
                        var metadata = result.getRecordMetadata();
                        log.info("Payment {} sent to partition {} offset {}",
                                event.paymentId(), metadata.partition(), metadata.offset());
                    }
                });
    }
}
