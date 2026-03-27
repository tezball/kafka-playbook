package com.playbook.checkout.controller;

import com.playbook.checkout.model.CheckoutRequest;
import com.playbook.checkout.service.CheckoutProducerService;
import com.playbook.checkout.service.SampleDataGenerator;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/checkout")
public class CheckoutController {

    private final CheckoutProducerService producerService;
    private final SampleDataGenerator sampleDataGenerator;

    public CheckoutController(CheckoutProducerService producerService, SampleDataGenerator sampleDataGenerator) {
        this.producerService = producerService;
        this.sampleDataGenerator = sampleDataGenerator;
    }

    @PostMapping
    public ResponseEntity<Map<String, String>> createCheckout(@RequestBody CheckoutRequestBody request) {
        var orderId = sampleDataGenerator.nextOrderId();
        var event = new CheckoutRequest(
                orderId,
                request.customerId(),
                request.productName(),
                request.quantity(),
                request.totalAmount(),
                Instant.now()
        );
        producerService.sendCheckout(event);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(Map.of("orderId", orderId, "status", "CHECKOUT_INITIATED"));
    }

    @PostMapping("/sample")
    public ResponseEntity<Map<String, String>> createSampleCheckout() {
        var event = sampleDataGenerator.generateRandomCheckout();
        producerService.sendCheckout(event);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(Map.of("orderId", event.orderId(), "status", "CHECKOUT_INITIATED"));
    }

    public record CheckoutRequestBody(
            String customerId,
            String productName,
            int quantity,
            BigDecimal totalAmount
    ) {}
}
