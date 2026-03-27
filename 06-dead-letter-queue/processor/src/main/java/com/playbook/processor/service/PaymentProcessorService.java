package com.playbook.processor.service;

import com.playbook.processor.model.PaymentEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class PaymentProcessorService {

    private static final Logger log = LoggerFactory.getLogger(PaymentProcessorService.class);
    private static final BigDecimal FRAUD_THRESHOLD = new BigDecimal("500");

    @KafkaListener(topics = "payments", groupId = "payment-processor-group")
    public void processPayment(PaymentEvent event) {
        log.info("Processing payment {} for ${} {}", event.paymentId(), event.amount(), event.currency());

        // Simulate fraud detection — amounts over $500 are flagged
        if (event.amount().compareTo(FRAUD_THRESHOLD) > 0) {
            throw new RuntimeException("Fraud detected: payment " + event.paymentId()
                    + " amount $" + event.amount() + " exceeds threshold of $" + FRAUD_THRESHOLD);
        }

        log.info("""

                ============================================
                  PAYMENT PROCESSED
                --------------------------------------------
                  Payment:  {}
                  Order:    {}
                  Amount:   ${} {}
                  Card:     ****{}
                  Status:   APPROVED
                ============================================""",
                event.paymentId(),
                event.orderId(),
                event.amount(),
                event.currency(),
                event.cardLast4()
        );
    }
}
