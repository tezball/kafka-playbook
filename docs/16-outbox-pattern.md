# Outbox Pattern

The **outbox pattern** guarantees that database changes and Kafka events are published consistently, solving the dual-write problem that arises when an application needs to update a database and send a message to Kafka within the same business operation.

## The Dual-Write Problem

Consider an order service that saves an order to the database and publishes an `OrderCreated` event to Kafka:

```
1. BEGIN transaction
2. INSERT INTO orders (...)        ← succeeds
3. COMMIT
4. kafkaProducer.send(event)       ← fails (broker down, network error)
```

The order exists in the database, but the event was never published. Downstream services never learn about it. The reverse is also problematic — publishing the event first, then crashing before the DB commit, means the event describes a state that does not exist.

**The fundamental issue:** writing to two different systems (database and Kafka) is not atomic. There is no distributed transaction between them.

## How the Outbox Pattern Works

Instead of publishing directly to Kafka, the application writes the event to an **outbox table** within the same database transaction as the business data:

```
1. BEGIN transaction
2. INSERT INTO orders (...)
3. INSERT INTO outbox (aggregate_type, aggregate_id, event_type, payload)
4. COMMIT
```

A separate process — the **relay** — reads from the outbox table and publishes events to Kafka:

```
Relay:
  1. SELECT * FROM outbox WHERE sent = false ORDER BY created_at
  2. For each row: kafkaProducer.send(row.payload)
  3. UPDATE outbox SET sent = true WHERE id = ...
```

Because steps 2 and 3 in the transaction are a single atomic commit, the business data and the outbox row are always consistent.

## Outbox Table Design

A typical outbox table schema:

```sql
CREATE TABLE outbox (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_type  VARCHAR(255) NOT NULL,   -- e.g., 'Order', 'Product'
    aggregate_id    VARCHAR(255) NOT NULL,   -- e.g., 'order-456'
    event_type      VARCHAR(255) NOT NULL,   -- e.g., 'OrderCreated'
    payload         JSONB NOT NULL,          -- full event data
    sent            BOOLEAN DEFAULT false,
    created_at      TIMESTAMP DEFAULT now()
);

CREATE INDEX idx_outbox_unsent ON outbox (sent, created_at) WHERE sent = false;
```

Key design decisions:
- **`aggregate_id` as Kafka key** — ensures all events for the same entity land in the same partition, preserving per-entity ordering
- **`payload` as JSONB** — the complete event, ready to publish without transformation
- **`sent` flag** — marks rows the relay has successfully published
- **Partial index on unsent rows** — the relay only queries unsent events, so the index keeps queries fast as the table grows

## Polling-Based Relay

The simplest relay implementation polls the outbox table on a schedule:

```
Every 500ms:
  1. SELECT unsent rows (batch of 100, ordered by created_at)
  2. Publish each to Kafka using aggregate_id as the message key
  3. Mark rows as sent
```

**Pros:** Simple to implement, works with any database, no infrastructure beyond a scheduled job.

**Cons:** Polling interval introduces latency (events are delayed up to the poll interval). Under high load, the poller must keep up with insert rate.

## CDC-Based Relay (Debezium)

A more sophisticated approach uses **Change Data Capture (CDC)** to stream outbox rows directly from the database's write-ahead log (WAL):

```
Database WAL → Debezium Connector → Kafka Topic
```

Debezium reads the Postgres WAL (or MySQL binlog) and publishes each new outbox row to Kafka in near-real-time. No polling, no delay.

### Debezium Outbox Event Router

Debezium provides a dedicated **Outbox Event Router** Single Message Transform (SMT) that:
- Reads from the outbox table's CDC stream
- Routes events to the correct Kafka topic based on `aggregate_type`
- Uses `aggregate_id` as the Kafka message key
- Extracts the `payload` field as the message value
- Optionally deletes processed outbox rows

This eliminates the need for a custom relay entirely — Debezium handles publication, routing, and key assignment.

## At-Least-Once Delivery and Ordering

Both relay approaches provide **at-least-once** delivery. If the relay crashes after publishing but before marking rows as sent, events will be re-published on restart. Consumers must therefore be **idempotent** — processing the same event twice must produce the same result. Common strategies include deduplication tables, database upserts, and idempotency checks before execution.

Using `aggregate_id` as the Kafka message key ensures all events for the same entity land in the same partition, preserving per-entity ordering. Events for different aggregates may arrive out of order relative to each other, but this is typically acceptable.

## Hands-On

- [Lesson 13](../13-outbox-pattern/LESSON.md) demonstrates a polling-based outbox pattern using Spring Data JPA and Kafka — observe how the outbox table accumulates events and the relay publishes them in order
- Experiment with stopping the relay, creating several orders, then restarting — watch the backlog drain

## Further Reading

- CDC with Debezium and Kafka Connect is covered in [Kafka Connect & CDC](12-kafka-connect.md)
- Exactly-once semantics and transactional producers are covered in [Transactions](09-transactions.md)
- How event sourcing provides a complementary approach to durable event storage is covered in [Event Sourcing](13-event-sourcing.md)
