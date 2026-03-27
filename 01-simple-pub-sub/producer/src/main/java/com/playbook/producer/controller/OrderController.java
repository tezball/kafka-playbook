package com.playbook.producer.controller;

import com.playbook.producer.model.OrderEvent;
import com.playbook.producer.service.OrderProducerService;
import com.playbook.producer.service.SampleDataGenerator;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
        var event = new OrderEvent(
                orderId,
                request.customerEmail(),
                request.productName(),
                request.quantity(),
                request.totalPrice(),
                Instant.now()
        );
        producerService.sendOrder(event);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(Map.of("orderId", orderId, "status", "ACCEPTED"));
    }

    @PostMapping("/sample")
    public ResponseEntity<Map<String, String>> createSampleOrder() {
        var event = sampleDataGenerator.generateRandomOrder();
        producerService.sendOrder(event);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(Map.of("orderId", event.orderId(), "status", "ACCEPTED"));
    }

    public record OrderRequest(
            String customerEmail,
            String productName,
            int quantity,
            BigDecimal totalPrice
    ) {}
}
