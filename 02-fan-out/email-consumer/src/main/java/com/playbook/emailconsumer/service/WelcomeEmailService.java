package com.playbook.emailconsumer.service;

import com.playbook.emailconsumer.model.UserSignupEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
public class WelcomeEmailService {

    private static final Logger log = LoggerFactory.getLogger(WelcomeEmailService.class);

    @KafkaListener(topics = "user-signups", groupId = "email-welcome-group")
    public void handleSignupEvent(UserSignupEvent event) {
        var firstName = event.name().split(" ")[0];
        log.info("""

                ============================================
                  WELCOME EMAIL
                --------------------------------------------
                  To:      {}
                  Name:    {}
                  Plan:    {}
                  Subject: Welcome to our platform, {}!
                ============================================""",
                event.email(),
                event.name(),
                event.plan(),
                firstName
        );
    }
}
