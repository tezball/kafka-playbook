package com.playbook.producer.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    @Bean
    public NewTopic transferRequestsTopic() {
        return TopicBuilder.name("transfer-requests")
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic accountDebitsTopic() {
        return TopicBuilder.name("account-debits")
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic accountCreditsTopic() {
        return TopicBuilder.name("account-credits")
                .partitions(3)
                .replicas(1)
                .build();
    }
}
