package com.playbook.auditconsumer.service;

import com.playbook.auditconsumer.model.UserSignupEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
public class AuditLogService {

    private static final Logger log = LoggerFactory.getLogger(AuditLogService.class);

    @KafkaListener(topics = "user-signups", groupId = "audit-log-group")
    public void handleSignupEvent(UserSignupEvent event) {
        log.info("[AUDIT] {} | SIGNUP | userId={} | email={} | plan={}",
                event.signedUpAt(),
                event.userId(),
                event.email(),
                event.plan()
        );
    }
}
