package com.playbook.processor.service;

import com.playbook.processor.model.PaymentEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Service;

@Service
public class DlqMonitorService {

    private static final Logger log = LoggerFactory.getLogger(DlqMonitorService.class);

    @KafkaListener(topics = "payments-dlq", groupId = "dlq-monitor-group")
    public void handleDlqMessage(
            PaymentEvent event,
            @Header(name = KafkaHeaders.DLT_EXCEPTION_MESSAGE, required = false) String exceptionMessage) {

        String reason = exceptionMessage != null ? extractReason(exceptionMessage) : "Unknown error";

        log.warn("""

                !!! DEAD LETTER QUEUE !!!
                ============================================
                  PAYMENT FAILED — SENT TO DLQ
                --------------------------------------------
                  Payment:  {}
                  Order:    {}
                  Amount:   ${} {}
                  Card:     ****{}
                  Reason:   {}
                  Retries:  3/3 exhausted
                ============================================""",
                event.paymentId(),
                event.orderId(),
                event.amount(),
                event.currency(),
                event.cardLast4(),
                reason
        );
    }

    private String extractReason(String exceptionMessage) {
        // Extract "Fraud detected" from the full exception message
        if (exceptionMessage.contains("Fraud detected")) {
            return "Fraud detected";
        }
        return exceptionMessage;
    }
}
