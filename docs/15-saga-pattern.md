# Saga Pattern

A **saga** is a sequence of local transactions across multiple services, where each step publishes an event that triggers the next. If any step fails, previously completed steps are undone by **compensating actions**. Sagas solve the problem of distributed transactions without requiring two-phase commit.

## The Problem

An e-commerce checkout involves multiple services that must coordinate:

```
Order Service → Inventory Service → Payment Service → Shipping Service
```

If payment fails after inventory is reserved, the reservation must be rolled back. Traditional distributed transactions (2PC) are slow and brittle. Sagas break the process into local transactions, each independently committable and reversible.

## Choreography vs Orchestration

There are two approaches to coordinating saga steps:

| | Choreography | Orchestration |
|---|---|---|
| **Coordination** | Decentralized — each service reacts to events | Centralized — a coordinator directs each step |
| **Coupling** | Services only know their own topics | Coordinator knows the full workflow |
| **Complexity** | Grows with number of steps (hard to trace) | Centralized logic (easier to reason about) |
| **Failure handling** | Each service publishes failure events | Coordinator decides which compensations to run |
| **Best for** | Simple flows (3-4 steps) | Complex flows with branching or conditional logic |

## Choreography in Detail

In a choreographed saga, each service listens for events and publishes the next:

```
Order Service                Inventory Service           Payment Service
     │                              │                          │
     │  OrderCreated                │                          │
     │─────────────────────────────→│                          │
     │                              │  InventoryReserved       │
     │                              │─────────────────────────→│
     │                              │                          │  PaymentCompleted
     │←─────────────────────────────┼──────────────────────────│
     │  (mark order as confirmed)   │                          │
```

Each service owns its local transaction and publishes an event when done. No service calls another directly.

## Compensation

When a step fails, the saga must undo previous steps by publishing compensating events:

```
Happy path:    OrderCreated → InventoryReserved → PaymentCompleted → ShippingScheduled
Failure path:  OrderCreated → InventoryReserved → PaymentFailed
                                    │
                                    ▼
                            InventoryReleased  (compensation)
                                    │
                                    ▼
                            OrderCancelled     (compensation)
```

Each compensating action is itself a local transaction. Compensation must be **semantically reversible** — you cannot un-send an email, but you can send a cancellation email.

## Correlation IDs

A single business transaction (e.g., one checkout) spans multiple topics and services. A **correlation ID** (also called saga ID or transaction ID) ties all related events together:

```json
{ "sagaId": "checkout-abc-123", "type": "InventoryReserved", "orderId": "order-456", ... }
{ "sagaId": "checkout-abc-123", "type": "PaymentCompleted", "orderId": "order-456", ... }
```

Every event in the saga carries the same `sagaId`, enabling:
- End-to-end tracing across services
- Querying all events for a given business transaction
- Debugging failures by filtering logs on the correlation ID

## Topic Design for Sagas

A clean topic design uses one topic per domain event type:

| Topic | Published By | Consumed By |
|-------|-------------|-------------|
| `order-events` | Order Service | Inventory Service |
| `inventory-events` | Inventory Service | Payment Service, Order Service |
| `payment-events` | Payment Service | Shipping Service, Inventory Service |
| `shipping-events` | Shipping Service | Order Service |

Both success and failure events flow through the same topic (distinguished by event type), or you can use separate success/failure topics if you prefer explicit routing.

## Idempotency

Network failures and retries mean a service may receive the same event more than once. Every saga step must be **idempotent** — processing the same event twice must produce the same result:

- Use the `sagaId` + step identifier as a deduplication key
- Check whether the action has already been performed before executing it
- Store processed event IDs in the local database within the same transaction as the business logic

This is especially critical during compensation, where a duplicate compensating event must not double-reverse a previous step.

## Tracking Saga State

To know whether a saga completed or failed, you need a way to track its progress:

- **Event-based tracking:** A dedicated consumer subscribes to all saga topics and maintains a state machine per `sagaId` — recording which steps have completed or failed
- **Saga table:** Each service writes saga progress to a local table (`saga_id, step, status, timestamp`), enabling queries like "show all in-progress sagas"
- **Timeout detection:** If a saga has not completed within a configured window, a scheduled job flags it for investigation or triggers compensation

## Hands-On

- [Lesson 07](../07-saga-choreography/LESSON.md) — 4 services, 8 topics: checkout, inventory (90% success), payment (80% success), shipping; failed payments trigger compensation (inventory release, order cancellation); `orderId` used as correlation ID across all topics
- [Lesson 06](../06-dead-letter-queue/LESSON.md) — DLQ as an alternative error-handling strategy when saga compensation is not needed

## Further Reading

- Topic fundamentals (partitions, naming, retention) are covered in [Topics](01-topics.md)
- How consumer groups enable each service to process its input topic is covered in [Consumer Groups](04-consumer-groups.md)
- Exactly-once processing within saga steps is covered in [Transactions](09-transactions.md)
