package com.playbook.processor.service;

import com.playbook.processor.model.AccountCredit;
import com.playbook.processor.model.AccountDebit;
import com.playbook.processor.model.TransferRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class TransferProcessorService {

    private static final Logger log = LoggerFactory.getLogger(TransferProcessorService.class);
    private static final String DEBITS_TOPIC = "account-debits";
    private static final String CREDITS_TOPIC = "account-credits";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public TransferProcessorService(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @KafkaListener(topics = "transfer-requests", groupId = "transfer-processor-group")
    public void handleTransferRequest(TransferRequest request) {
        kafkaTemplate.executeInTransaction(ops -> {
            var now = Instant.now();

            var debit = new AccountDebit(
                    request.transferId(),
                    request.fromAccount(),
                    request.amount(),
                    request.description(),
                    now
            );

            var credit = new AccountCredit(
                    request.transferId(),
                    request.toAccount(),
                    request.amount(),
                    request.description(),
                    now
            );

            // Send debit and credit atomically within the same transaction
            ops.send(DEBITS_TOPIC, request.fromAccount(), debit);
            ops.send(CREDITS_TOPIC, request.toAccount(), credit);

            log.info("""

                    ============================================
                      TRANSFER PROCESSED (ATOMIC)
                    --------------------------------------------
                      Transfer: {}
                      From:     {} -> DEBIT  ${}
                      To:       {} -> CREDIT ${}
                      Desc:     {}
                      Status:   COMMITTED
                    ============================================""",
                    request.transferId(),
                    request.fromAccount(), request.amount(),
                    request.toAccount(), request.amount(),
                    request.description()
            );

            return null;
        });
    }
}
