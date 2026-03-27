package com.playbook.clickproducer.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    @Bean
    public NewTopic clicksTopic() {
        return TopicBuilder.name("clicks")
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic userProfilesTopic() {
        return TopicBuilder.name("user-profiles")
                .partitions(3)
                .replicas(1)
                .config("cleanup.policy", "compact")
                .build();
    }

    @Bean
    public NewTopic enrichedClicksTopic() {
        return TopicBuilder.name("enriched-clicks")
                .partitions(3)
                .replicas(1)
                .build();
    }
}
