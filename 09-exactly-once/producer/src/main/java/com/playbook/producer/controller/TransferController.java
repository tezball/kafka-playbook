package com.playbook.producer.controller;

import com.playbook.producer.model.TransferRequest;
import com.playbook.producer.service.TransferProducerService;
import com.playbook.producer.service.SampleDataGenerator;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/transfers")
public class TransferController {

    private final TransferProducerService producerService;
    private final SampleDataGenerator sampleDataGenerator;

    public TransferController(TransferProducerService producerService, SampleDataGenerator sampleDataGenerator) {
        this.producerService = producerService;
        this.sampleDataGenerator = sampleDataGenerator;
    }

    @PostMapping
    public ResponseEntity<Map<String, String>> createTransfer(@RequestBody TransferRequestPayload request) {
        var transferId = sampleDataGenerator.nextTransferId();
        var event = new TransferRequest(
                transferId,
                request.fromAccount(),
                request.toAccount(),
                request.amount(),
                request.description(),
                Instant.now()
        );
        producerService.sendTransfer(event);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(Map.of("transferId", transferId, "status", "ACCEPTED"));
    }

    @PostMapping("/sample")
    public ResponseEntity<Map<String, String>> createSampleTransfer() {
        var event = sampleDataGenerator.generateRandomTransfer();
        producerService.sendTransfer(event);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(Map.of("transferId", event.transferId(), "status", "ACCEPTED"));
    }

    public record TransferRequestPayload(
            String fromAccount,
            String toAccount,
            BigDecimal amount,
            String description
    ) {}
}
