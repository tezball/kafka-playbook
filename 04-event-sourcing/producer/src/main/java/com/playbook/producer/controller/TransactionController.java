package com.playbook.producer.controller;

import com.playbook.producer.model.TransactionEvent;
import com.playbook.producer.service.LedgerProducerService;
import com.playbook.producer.service.SampleDataGenerator;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/transactions")
public class TransactionController {

    private final LedgerProducerService producerService;
    private final SampleDataGenerator sampleDataGenerator;

    public TransactionController(LedgerProducerService producerService, SampleDataGenerator sampleDataGenerator) {
        this.producerService = producerService;
        this.sampleDataGenerator = sampleDataGenerator;
    }

    @PostMapping
    public ResponseEntity<Map<String, String>> createTransaction(@RequestBody TransactionRequest request) {
        var txnId = sampleDataGenerator.nextTransactionId();
        var event = new TransactionEvent(
                txnId,
                request.accountId(),
                request.type(),
                request.amount(),
                request.description(),
                Instant.now()
        );
        producerService.sendTransaction(event);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(Map.of("transactionId", txnId, "status", "ACCEPTED"));
    }

    @PostMapping("/sample")
    public ResponseEntity<Map<String, String>> createSampleTransaction() {
        var event = sampleDataGenerator.generateRandomTransaction();
        producerService.sendTransaction(event);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(Map.of("transactionId", event.transactionId(), "status", "ACCEPTED"));
    }

    public record TransactionRequest(
            String accountId,
            String type,
            BigDecimal amount,
            String description
    ) {}
}
