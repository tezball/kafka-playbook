package com.playbook.producer.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    @Bean
    public NewTopic dashboardOrdersTopic() {
        return TopicBuilder.name("dashboard-orders")
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic categoryCountsTopic() {
        return TopicBuilder.name("category-counts")
                .partitions(3)
                .replicas(1)
                .build();
    }
}
