package com.playbook.producerv2;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ProducerV2Application {

    public static void main(String[] args) {
        SpringApplication.run(ProducerV2Application.class, args);
    }
}
