package com.playbook.producer.controller;

import com.playbook.producer.service.OrderProducerService;
import com.playbook.producer.service.SampleDataGenerator;
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
        var order = sampleDataGenerator.generateRandomOrder();
        producerService.sendOrder(order);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(Map.of(
                        "orderId", order.orderId(),
                        "category", order.category(),
                        "product", order.productName(),
                        "status", "ACCEPTED"
                ));
    }
}
