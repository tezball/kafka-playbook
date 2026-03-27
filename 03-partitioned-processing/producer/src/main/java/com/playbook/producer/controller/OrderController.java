package com.playbook.producer.controller;

import com.playbook.producer.model.RegionalOrderEvent;
import com.playbook.producer.service.OrderProducerService;
import com.playbook.producer.service.SampleDataGenerator;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
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

    @PostMapping
    public ResponseEntity<Map<String, String>> createOrder(@RequestBody OrderRequest request) {
        var orderId = sampleDataGenerator.nextOrderId();
        var event = new RegionalOrderEvent(
                orderId,
                request.region(),
                request.customerName(),
                request.product(),
                request.amount(),
                Instant.now()
        );
        producerService.sendOrder(event);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(Map.of("orderId", orderId, "region", event.region(), "status", "ACCEPTED"));
    }

    @PostMapping("/sample")
    public ResponseEntity<Map<String, String>> createSampleOrder() {
        var event = sampleDataGenerator.generateRandomOrder();
        producerService.sendOrder(event);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(Map.of("orderId", event.orderId(), "region", event.region(), "status", "ACCEPTED"));
    }

    public record OrderRequest(
            String region,
            String customerName,
            String product,
            BigDecimal amount
    ) {}
}
