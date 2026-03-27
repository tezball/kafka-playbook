package com.playbook.queryservice.controller;

import com.playbook.queryservice.model.ProductView;
import com.playbook.queryservice.service.ProductMaterializerService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collection;
import java.util.List;

@RestController
@RequestMapping("/api/products")
public class ProductQueryController {

    private final ProductMaterializerService materializerService;

    public ProductQueryController(ProductMaterializerService materializerService) {
        this.materializerService = materializerService;
    }

    @GetMapping
    public ResponseEntity<Collection<ProductView>> getAllProducts() {
        return ResponseEntity.ok(materializerService.getAllProducts());
    }

    @GetMapping("/{productId}")
    public ResponseEntity<ProductView> getProduct(@PathVariable String productId) {
        var product = materializerService.getProduct(productId);
        if (product == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(product);
    }

    @GetMapping("/search")
    public ResponseEntity<List<ProductView>> searchByCategory(@RequestParam String category) {
        return ResponseEntity.ok(materializerService.searchByCategory(category));
    }
}
