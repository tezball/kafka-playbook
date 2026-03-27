package com.playbook.producer.controller;

import com.playbook.producer.model.ProductCommand;
import com.playbook.producer.service.CatalogProducerService;
import com.playbook.producer.service.SampleDataGenerator;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/products")
public class CatalogController {

    private final CatalogProducerService producerService;
    private final SampleDataGenerator sampleDataGenerator;

    public CatalogController(CatalogProducerService producerService, SampleDataGenerator sampleDataGenerator) {
        this.producerService = producerService;
        this.sampleDataGenerator = sampleDataGenerator;
    }

    @PostMapping
    public ResponseEntity<Map<String, String>> createProduct(@RequestBody CreateProductRequest request) {
        var productId = sampleDataGenerator.nextProductId();
        var command = new ProductCommand(
                sampleDataGenerator.nextCommandId(),
                productId,
                "CREATE",
                request.name(),
                request.category(),
                request.price(),
                Instant.now()
        );
        producerService.sendCommand(command);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(Map.of("productId", productId, "action", "CREATE", "status", "ACCEPTED"));
    }

    @PutMapping("/{productId}/price")
    public ResponseEntity<Map<String, String>> updatePrice(
            @PathVariable String productId,
            @RequestBody UpdatePriceRequest request) {
        var command = new ProductCommand(
                sampleDataGenerator.nextCommandId(),
                productId,
                "UPDATE_PRICE",
                null,
                null,
                request.price(),
                Instant.now()
        );
        producerService.sendCommand(command);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(Map.of("productId", productId, "action", "UPDATE_PRICE", "status", "ACCEPTED"));
    }

    @DeleteMapping("/{productId}")
    public ResponseEntity<Map<String, String>> deleteProduct(@PathVariable String productId) {
        var command = new ProductCommand(
                sampleDataGenerator.nextCommandId(),
                productId,
                "DELETE",
                null,
                null,
                null,
                Instant.now()
        );
        producerService.sendCommand(command);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(Map.of("productId", productId, "action", "DELETE", "status", "ACCEPTED"));
    }

    @PostMapping("/sample")
    public ResponseEntity<Map<String, String>> createSampleProduct() {
        var command = sampleDataGenerator.generateSampleCommand();
        producerService.sendCommand(command);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(Map.of("productId", command.productId(), "action", "CREATE", "status", "ACCEPTED"));
    }

    public record CreateProductRequest(String name, String category, BigDecimal price) {}

    public record UpdatePriceRequest(BigDecimal price) {}
}
