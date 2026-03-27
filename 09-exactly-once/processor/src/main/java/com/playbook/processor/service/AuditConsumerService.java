package com.playbook.processor.service;

import com.playbook.processor.model.AccountCredit;
import com.playbook.processor.model.AccountDebit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
public class AuditConsumerService {

    private static final Logger log = LoggerFactory.getLogger(AuditConsumerService.class);

    @KafkaListener(topics = "account-debits", groupId = "audit-group")
    public void handleDebit(AccountDebit debit) {
        log.info("[AUDIT-DEBIT]  {} | {} | -${} | {}",
                debit.transferId(),
                debit.accountId(),
                debit.amount(),
                debit.description()
        );
    }

    @KafkaListener(topics = "account-credits", groupId = "audit-group")
    public void handleCredit(AccountCredit credit) {
        log.info("[AUDIT-CREDIT] {} | {} | +${} | {}",
                credit.transferId(),
                credit.accountId(),
                credit.amount(),
                credit.description()
        );
    }
}
