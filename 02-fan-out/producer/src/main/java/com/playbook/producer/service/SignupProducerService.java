package com.playbook.producer.service;

import com.playbook.producer.model.UserSignupEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class SignupProducerService {

    private static final Logger log = LoggerFactory.getLogger(SignupProducerService.class);
    private static final String TOPIC = "user-signups";

    private final KafkaTemplate<String, UserSignupEvent> kafkaTemplate;

    public SignupProducerService(KafkaTemplate<String, UserSignupEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void sendSignup(UserSignupEvent event) {
        log.info("Producing signup event: {} -> {}", event.userId(), event.email());
        kafkaTemplate.send(TOPIC, event.userId(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to send signup {}: {}", event.userId(), ex.getMessage());
                    } else {
                        var metadata = result.getRecordMetadata();
                        log.info("Signup {} sent to partition {} offset {}",
                                event.userId(), metadata.partition(), metadata.offset());
                    }
                });
    }
}
