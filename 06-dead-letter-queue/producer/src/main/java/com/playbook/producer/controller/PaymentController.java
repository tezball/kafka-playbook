package com.playbook.producer.controller;

import com.playbook.producer.model.PaymentEvent;
import com.playbook.producer.service.PaymentProducerService;
import com.playbook.producer.service.SampleDataGenerator;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentProducerService producerService;
    private final SampleDataGenerator sampleDataGenerator;

    public PaymentController(PaymentProducerService producerService, SampleDataGenerator sampleDataGenerator) {
        this.producerService = producerService;
        this.sampleDataGenerator = sampleDataGenerator;
    }

    @PostMapping
    public ResponseEntity<Map<String, String>> createPayment(@RequestBody PaymentRequest request) {
        var paymentId = sampleDataGenerator.nextPaymentId();
        var event = new PaymentEvent(
                paymentId,
                request.orderId(),
                request.amount(),
                request.currency(),
                request.cardLast4(),
                request.customerEmail(),
                Instant.now()
        );
        producerService.sendPayment(event);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(Map.of("paymentId", paymentId, "status", "ACCEPTED"));
    }

    @PostMapping("/sample")
    public ResponseEntity<Map<String, String>> createSamplePayment() {
        var event = sampleDataGenerator.generateRandomPayment();
        producerService.sendPayment(event);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(Map.of("paymentId", event.paymentId(), "status", "ACCEPTED"));
    }

    public record PaymentRequest(
            String orderId,
            BigDecimal amount,
            String currency,
            String cardLast4,
            String customerEmail
    ) {}
}
