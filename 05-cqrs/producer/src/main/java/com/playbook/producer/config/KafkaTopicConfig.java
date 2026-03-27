package com.playbook.producer.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    @Bean
    public NewTopic productCommandsTopic() {
        return TopicBuilder.name("product-commands")
                .partitions(3)
                .replicas(1)
                .build();
    }
}
