package com.playbook.clickproducer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ClickProducerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ClickProducerApplication.class, args);
    }
}
