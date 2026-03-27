package com.playbook.orderservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.playbook.orderservice.model.OrderCreatedEvent;
import com.playbook.orderservice.model.OutboxEvent;
import com.playbook.orderservice.repository.OutboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
public class OutboxPoller {

    private static final Logger log = LoggerFactory.getLogger(OutboxPoller.class);
    private static final String TOPIC = "order-events";

    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<String, OrderCreatedEvent> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public OutboxPoller(OutboxRepository outboxRepository,
                        KafkaTemplate<String, OrderCreatedEvent> kafkaTemplate) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    @Scheduled(fixedRate = 2000)
    @Transactional
    public void pollAndPublish() {
        List<OutboxEvent> unsent = outboxRepository.findBySentFalse();

        for (OutboxEvent event : unsent) {
            try {
                OrderCreatedEvent payload = objectMapper.readValue(
                        event.getPayload(), OrderCreatedEvent.class);

                kafkaTemplate.send(TOPIC, event.getAggregateId(), payload)
                        .whenComplete((result, ex) -> {
                            if (ex != null) {
                                log.error("[OUTBOX-POLLER] Failed to publish {}: {}",
                                        event.getAggregateId(), ex.getMessage());
                            }
                        });

                event.setSent(true);
                outboxRepository.save(event);

                log.info("[OUTBOX-POLLER] Published {} to Kafka | Marked as sent",
                        event.getAggregateId());

            } catch (JsonProcessingException e) {
                log.error("[OUTBOX-POLLER] Failed to deserialize payload for event {}: {}",
                        event.getId(), e.getMessage());
            }
        }
    }
}
