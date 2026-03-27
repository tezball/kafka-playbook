package com.playbook.producer.controller;

import com.playbook.producer.model.UserSignupEvent;
import com.playbook.producer.service.SignupProducerService;
import com.playbook.producer.service.SampleDataGenerator;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/signups")
public class SignupController {

    private final SignupProducerService producerService;
    private final SampleDataGenerator sampleDataGenerator;

    public SignupController(SignupProducerService producerService, SampleDataGenerator sampleDataGenerator) {
        this.producerService = producerService;
        this.sampleDataGenerator = sampleDataGenerator;
    }

    @PostMapping
    public ResponseEntity<Map<String, String>> createSignup(@RequestBody SignupRequest request) {
        var userId = sampleDataGenerator.nextUserId();
        var event = new UserSignupEvent(
                userId,
                request.email(),
                request.name(),
                request.plan(),
                Instant.now()
        );
        producerService.sendSignup(event);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(Map.of("userId", userId, "status", "ACCEPTED"));
    }

    @PostMapping("/sample")
    public ResponseEntity<Map<String, String>> createSampleSignup() {
        var event = sampleDataGenerator.generateRandomSignup();
        producerService.sendSignup(event);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(Map.of("userId", event.userId(), "status", "ACCEPTED"));
    }

    public record SignupRequest(
            String email,
            String name,
            String plan
    ) {}
}
