package com.playbook.seeder.controller;

import com.playbook.seeder.model.Product;
import com.playbook.seeder.repository.ProductRepository;
import com.playbook.seeder.service.DataSeederService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductRepository productRepository;
    private final DataSeederService dataSeederService;

    public ProductController(ProductRepository productRepository, DataSeederService dataSeederService) {
        this.productRepository = productRepository;
        this.dataSeederService = dataSeederService;
    }

    @GetMapping
    public List<Product> listProducts() {
        return productRepository.findAll();
    }

    @PostMapping("/sample")
    public ResponseEntity<Map<String, String>> triggerRandomChange() {
        var result = dataSeederService.performManualChange();
        return ResponseEntity.ok(Map.of("action", result));
    }
}
