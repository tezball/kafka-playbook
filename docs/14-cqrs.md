# CQRS (Command Query Responsibility Segregation)

**CQRS** is an architectural pattern that separates the write path (commands) from the read path (queries) of an application. Instead of a single model that handles both reads and writes, you maintain separate models optimized for each concern.

## Core Idea

In a traditional architecture, the same database table serves both writes and reads:

```
Client → [Write + Read] → Single Database
```

In CQRS, commands and queries flow through different paths:

```
                    ┌──────────────┐
  Command ─────→   │ Kafka Topic  │  (write side — durable, ordered)
                    └──────┬───────┘
                           │ consumer
                           ▼
                    ┌──────────────┐
  Query ←──────    │  Materialized │  (read side — optimized for queries)
                    │     View     │
                    └──────────────┘
```

## The Write Path

Commands represent intentions to change state. In a Kafka-based CQRS system:

1. A client sends a command (e.g., "create product", "update price")
2. The command handler validates the request and publishes an event to a Kafka topic
3. The event is durably stored in Kafka — the write is complete

The Kafka topic becomes the authoritative record of all state changes. Writes are fast because the producer only needs to append to the log.

## The Read Path

Consumers subscribe to the event topic and build **materialized views** — read-optimized projections of the data:

| View Type | Use Case | Example |
|-----------|----------|---------|
| In-memory map | Low-latency lookups | Product catalog cached in a `HashMap` |
| Relational database | Complex queries, joins | Products table with full-text search |
| Search index | Free-text search, faceted browse | Elasticsearch index of product data |
| Aggregation store | Analytics, dashboards | Pre-computed counts and sums in Redis |

Each view is independently built by its own consumer group, so you can add new views without changing the write side.

## Eventual Consistency

A key trade-off of CQRS is **eventual consistency** between the write and read sides:

```
Timeline:
  t=0   Producer publishes "ProductCreated" to Kafka          ✓ durable
  t=0   Client queries the materialized view                  ✗ not yet visible
  t=50ms Consumer processes the event, updates the view       ✓ now visible
```

- Writes are **immediately durable** in Kafka — no data loss risk
- Reads **catch up asynchronously** — there is a brief window where the read model is stale
- The lag is typically milliseconds to low seconds under normal load

Applications must be designed to tolerate this gap. Strategies include optimistic UI updates, read-your-writes consistency via version tokens, or polling for the expected version.

## When to Use CQRS

CQRS adds architectural complexity. It earns its keep in specific scenarios:

**Good fit:**
- Read and write workloads scale independently (e.g., 100x more reads than writes)
- Multiple read models are needed (search, analytics, API responses)
- Full audit trail is required (the Kafka topic is the log of record)
- Different teams own the write and read sides

**Poor fit:**
- Simple CRUD applications with one read model
- Strong consistency is required between writes and reads (e.g., bank balance checks before transfers)
- Low event volume where the overhead is not justified
- Small teams where the operational cost of running Kafka outweighs the benefits

## Combining CQRS with Event Sourcing

CQRS and [event sourcing](13-event-sourcing.md) are complementary but independent patterns. When combined:

```
Command → Validate → Publish event to Kafka (event sourcing: store events, not state)
                              │
                    ┌─────────┴──────────┐
                    ▼                    ▼
             Materialized View 1   Materialized View 2
             (read model A)        (read model B)
```

- **Event sourcing** provides the write model: an append-only log of domain events
- **CQRS** provides the read model(s): consumers that project events into query-friendly structures
- Together, you get a complete audit trail, independently scalable reads and writes, and the ability to add new read models by replaying the event log from the beginning

## Hands-On

- [Lesson 05](../05-cqrs/LESSON.md) — write path sends `ProductCommand` (CREATE/UPDATE_PRICE/DELETE) to Kafka; read path materializes a `ConcurrentHashMap<ProductView>` from the event stream; separate REST endpoints for commands (8080) and queries (8081)
- [Lesson 04](../04-event-sourcing/LESSON.md) — event sourcing as the write-side pattern; balance consumer is effectively a read model built from events

## Further Reading

- Event sourcing as the write-side companion to CQRS is covered in [Event Sourcing](13-event-sourcing.md)
- Topic fundamentals (partitions, retention, replication) are covered in [Topics](01-topics.md)
- How consumer groups enable parallel read-side processing is covered in [Consumer Groups](04-consumer-groups.md)
