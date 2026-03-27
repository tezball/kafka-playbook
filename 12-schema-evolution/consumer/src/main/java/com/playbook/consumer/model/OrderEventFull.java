package com.playbook.consumer.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Superset model that handles both V1 and V2 event schemas.
 *
 * V1 events have: orderId, customerEmail, totalPrice, createdAt
 * V2 events add:  shippingAddress, loyaltyTier
 *
 * Jackson ignores unknown properties and defaults missing fields to null,
 * so this single class gracefully handles both versions.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class OrderEventFull {

    @JsonProperty("orderId")
    private String orderId;

    @JsonProperty("customerEmail")
    private String customerEmail;

    @JsonProperty("totalPrice")
    private BigDecimal totalPrice;

    @JsonProperty("shippingAddress")
    private String shippingAddress;

    @JsonProperty("loyaltyTier")
    private String loyaltyTier;

    @JsonProperty("createdAt")
    private Instant createdAt;

    public OrderEventFull() {}

    public OrderEventFull(String orderId, String customerEmail, BigDecimal totalPrice,
                          String shippingAddress, String loyaltyTier, Instant createdAt) {
        this.orderId = orderId;
        this.customerEmail = customerEmail;
        this.totalPrice = totalPrice;
        this.shippingAddress = shippingAddress;
        this.loyaltyTier = loyaltyTier;
        this.createdAt = createdAt;
    }

    public String getOrderId() { return orderId; }
    public String getCustomerEmail() { return customerEmail; }
    public BigDecimal getTotalPrice() { return totalPrice; }
    public String getShippingAddress() { return shippingAddress; }
    public String getLoyaltyTier() { return loyaltyTier; }
    public Instant getCreatedAt() { return createdAt; }

    /**
     * Detect schema version based on presence of V2-only fields.
     */
    public int detectSchemaVersion() {
        return (shippingAddress != null || loyaltyTier != null) ? 2 : 1;
    }
}
