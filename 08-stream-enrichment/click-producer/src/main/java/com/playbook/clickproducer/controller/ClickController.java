package com.playbook.clickproducer.controller;

import com.playbook.clickproducer.model.ClickEvent;
import com.playbook.clickproducer.service.ClickProducerService;
import com.playbook.clickproducer.service.SampleDataGenerator;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/clicks")
public class ClickController {

    private final ClickProducerService producerService;
    private final SampleDataGenerator sampleDataGenerator;

    public ClickController(ClickProducerService producerService, SampleDataGenerator sampleDataGenerator) {
        this.producerService = producerService;
        this.sampleDataGenerator = sampleDataGenerator;
    }

    @PostMapping("/sample")
    public ResponseEntity<Map<String, String>> createSampleClick() {
        var event = sampleDataGenerator.generateRandomClick();
        producerService.sendClick(event);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(Map.of("clickId", event.clickId(), "userId", event.userId(), "status", "ACCEPTED"));
    }
}
