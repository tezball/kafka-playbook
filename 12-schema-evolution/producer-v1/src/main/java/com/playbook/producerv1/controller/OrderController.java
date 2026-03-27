package com.playbook.producerv1.controller;

import com.playbook.producerv1.service.OrderProducerService;
import com.playbook.producerv1.service.SampleDataGenerator;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderProducerService producerService;
    private final SampleDataGenerator sampleDataGenerator;

    public OrderController(OrderProducerService producerService, SampleDataGenerator sampleDataGenerator) {
        this.producerService = producerService;
        this.sampleDataGenerator = sampleDataGenerator;
    }

    @PostMapping("/sample")
    public ResponseEntity<Map<String, String>> createSampleOrder() {
        var event = sampleDataGenerator.generateRandomOrder();
        producerService.sendOrder(event);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(Map.of("orderId", event.orderId(), "version", "v1", "status", "ACCEPTED"));
    }
}
