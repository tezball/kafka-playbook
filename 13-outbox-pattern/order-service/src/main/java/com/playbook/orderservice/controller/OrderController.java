package com.playbook.orderservice.controller;

import com.playbook.orderservice.model.Order;
import com.playbook.orderservice.service.OrderService;
import com.playbook.orderservice.service.SampleDataGenerator;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;
    private final SampleDataGenerator sampleDataGenerator;

    public OrderController(OrderService orderService, SampleDataGenerator sampleDataGenerator) {
        this.orderService = orderService;
        this.sampleDataGenerator = sampleDataGenerator;
    }

    @PostMapping
    public ResponseEntity<Map<String, String>> createOrder(@RequestBody OrderRequest request) {
        var orderId = sampleDataGenerator.nextOrderId();
        var order = orderService.createOrder(
                orderId,
                request.customerEmail(),
                request.productName(),
                request.quantity(),
                request.totalPrice()
        );
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(Map.of("orderId", orderId, "status", "ACCEPTED"));
    }

    @PostMapping("/sample")
    public ResponseEntity<Map<String, String>> createSampleOrder() {
        var orderId = sampleDataGenerator.nextOrderId();
        sampleDataGenerator.createRandomOrder();
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(Map.of("status", "ACCEPTED"));
    }

    @GetMapping
    public ResponseEntity<List<Order>> listOrders() {
        return ResponseEntity.ok(orderService.listOrders());
    }

    public record OrderRequest(
            String customerEmail,
            String productName,
            int quantity,
            BigDecimal totalPrice
    ) {}
}
