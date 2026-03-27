package com.playbook.orderservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.playbook.orderservice.model.Order;
import com.playbook.orderservice.model.OrderCreatedEvent;
import com.playbook.orderservice.model.OutboxEvent;
import com.playbook.orderservice.repository.OrderRepository;
import com.playbook.orderservice.repository.OutboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orderRepository;
    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public OrderService(OrderRepository orderRepository, OutboxRepository outboxRepository) {
        this.orderRepository = orderRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    /**
     * Creates an order AND writes an outbox event in a SINGLE database transaction.
     * This is the KEY teaching point of the outbox pattern — both writes succeed
     * or both fail. No event is ever lost due to a partial failure.
     */
    @Transactional
    public Order createOrder(String orderId, String customerEmail, String productName,
                             int quantity, BigDecimal totalPrice) {
        // 1. Save the order to the orders table
        var order = new Order(orderId, customerEmail, productName, quantity, totalPrice);
        orderRepository.save(order);

        log.info("[ORDER] Created {} | {} | {} (x{}) | ${}",
                orderId, customerEmail, productName, quantity, totalPrice);

        // 2. Create the event payload
        var event = new OrderCreatedEvent(
                orderId, customerEmail, productName, quantity, totalPrice, Instant.now()
        );

        String payload;
        try {
            payload = objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize OrderCreatedEvent", e);
        }

        // 3. Save the outbox event to the outbox_events table (same transaction!)
        var outboxEvent = new OutboxEvent("Order", orderId, "ORDER_CREATED", payload);
        outboxRepository.save(outboxEvent);

        log.info("[OUTBOX] Written to outbox: event_type=ORDER_CREATED, aggregate_id={}", orderId);

        return order;
    }

    public List<Order> listOrders() {
        return orderRepository.findAll();
    }
}
