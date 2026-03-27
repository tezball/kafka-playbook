# Kafka Playbook

A collection of self-contained lessons teaching Apache Kafka through hands-on, real-world examples. Each lesson runs entirely in Docker — no local Java, Maven, or Kafka installation required.

Every lesson includes auto-generated sample data, formatted log output, and [AKHQ](https://akhq.io/) (a Kafka web UI) so you can visually watch events flow through the system.

## Prerequisites

- Docker & Docker Compose
- Optionally, [Obsidian](https://obsidian.md/) — open this folder as a vault for linked navigation and graph view

## Lessons

| # | Lesson | Scenario | Kafka Features |
|---|--------|----------|----------------|
| [01](01-simple-pub-sub/LESSON.md) | Simple Pub/Sub | Order placed → email notification | Topics, producers, consumers, consumer groups, JSON serialization |
| [02](02-fan-out/LESSON.md) | Fan-Out (Broadcast) | User signup → email + analytics + audit | Multiple consumer groups, independent offset tracking |
| [03](03-partitioned-processing/LESSON.md) | Partitioned Processing | Orders routed by region | Partition keys, ordering guarantees, partition assignment |
| [04](04-event-sourcing/LESSON.md) | Event Sourcing | Bank account ledger | Log compaction, event replay, event schema design |
| [05](05-cqrs/LESSON.md) | CQRS | Product catalog → search index | Materialized views, separate read/write paths |
| [06](06-dead-letter-queue/LESSON.md) | Dead Letter Queue | Payment processing with error handling | DLQ topics, retry with backoff, error handlers |
| [07](07-saga-choreography/LESSON.md) | Saga / Choreography | E-commerce checkout flow | Multi-topic coordination, compensating events, correlation IDs |
| [08](08-stream-enrichment/LESSON.md) | Stream Enrichment | Clickstream + user profiles | Kafka Streams, KTable, stream-table joins |
| [09](09-exactly-once/LESSON.md) | Exactly-Once Processing | Financial transfers | Idempotent producers, transactions, `read_committed` isolation |
| [10](10-windowed-aggregation/LESSON.md) | Windowed Aggregation | Real-time order dashboard | Kafka Streams, tumbling windows, state stores |
| [11](11-change-data-capture/LESSON.md) | Change Data Capture | Postgres → Kafka → CDC consumer | Kafka Connect, Debezium, logical decoding |
| [12](12-schema-evolution/LESSON.md) | Schema Evolution | API versioning across teams | Schema versioning, backward compatibility, tolerant reader |
| [13](13-outbox-pattern/LESSON.md) | Outbox Pattern | Reliable DB + event publishing | Transactional outbox, polling publisher, at-least-once delivery |

## Running a Lesson

```bash
cd 01-simple-pub-sub
./start.sh
```

Each `start.sh` tears down previous containers, builds fresh, waits for health checks, opens AKHQ in your browser, and tails the logs. Each lesson's `LESSON.md` contains the full exercise guide.

---

## Kafka Feature Reference

A map of Kafka's features and capabilities, linked to the lessons and docs that demonstrate them with working code.

### Core Concepts

| Feature | What it is | Docs | Lessons |
|---------|-----------|------|---------|
| **Topics** | Named, append-only log of records; the fundamental unit of organization | [Topics](docs/01-topics.md) | All |
| **Producers** | Applications that write records to topics | [Producers](docs/02-producers.md) | All |
| **Consumers** | Applications that read records from topics via a pull model | [Consumers](docs/03-consumers.md) | All |
| **Consumer Groups** | Coordinated consumers that share partitions for parallel processing | [Consumer Groups](docs/04-consumer-groups.md) | [01](01-simple-pub-sub/LESSON.md), [02](02-fan-out/LESSON.md), [03](03-partitioned-processing/LESSON.md) |
| **Partitions** | Ordered, immutable sub-logs within a topic; unit of parallelism | [Topics](docs/01-topics.md) | [03](03-partitioned-processing/LESSON.md) |
| **Offsets** | Sequential IDs tracking consumer position within a partition | [Offsets](docs/07-offsets.md) | [01](01-simple-pub-sub/LESSON.md), [02](02-fan-out/LESSON.md) |
| **Message Keys** | Determine partition routing; enable per-entity ordering | [Message Keys](docs/05-message-keys.md) | [01](01-simple-pub-sub/LESSON.md), [03](03-partitioned-processing/LESSON.md), [04](04-event-sourcing/LESSON.md) |

### Serialization & Schema

| Feature | What it is | Docs | Lessons |
|---------|-----------|------|---------|
| **JSON Serialization** | Human-readable format via Spring Kafka's JsonSerializer/JsonDeserializer | [JSON Serialization](docs/06-json-serialization.md) | All |
| **Schema Evolution** | Safely evolving message schemas across producer/consumer versions | [Schema Evolution](docs/08-schema-evolution.md) | [12](12-schema-evolution/LESSON.md) |
| **Schema Registry** | Centralized schema storage and compatibility enforcement (Confluent) | [Schema Evolution](docs/08-schema-evolution.md) | [12](12-schema-evolution/LESSON.md) |
| **Avro / Protobuf** | Compact binary serialization with schema enforcement | [JSON Serialization](docs/06-json-serialization.md) | — |

### Reliability & Delivery

| Feature | What it is | Docs | Lessons |
|---------|-----------|------|---------|
| **Idempotent Producers** | Exactly-once delivery from producer to broker via sequence numbers | [Transactions & EOS](docs/09-transactions.md) | [09](09-exactly-once/LESSON.md) |
| **Transactions** | Atomic multi-topic writes with `executeInTransaction()` | [Transactions & EOS](docs/09-transactions.md) | [09](09-exactly-once/LESSON.md) |
| **`read_committed` Isolation** | Consumers only see committed transactional messages | [Transactions & EOS](docs/09-transactions.md) | [09](09-exactly-once/LESSON.md) |
| **Dead Letter Queues** | Failed messages routed to a separate topic after retry exhaustion | [Dead Letter Queues](docs/10-dead-letter-queues.md) | [06](06-dead-letter-queue/LESSON.md) |
| **Retry with Backoff** | Automatic retry before sending to DLQ | [Dead Letter Queues](docs/10-dead-letter-queues.md) | [06](06-dead-letter-queue/LESSON.md) |
| **At-Least-Once Delivery** | Messages may be delivered more than once but never lost | [Offsets](docs/07-offsets.md) | [01](01-simple-pub-sub/LESSON.md), [13](13-outbox-pattern/LESSON.md) |

### Stream Processing (Kafka Streams)

| Feature | What it is | Docs | Lessons |
|---------|-----------|------|---------|
| **KStream** | Unbounded stream of records (event stream) | [Kafka Streams](docs/11-kafka-streams.md) | [08](08-stream-enrichment/LESSON.md), [10](10-windowed-aggregation/LESSON.md) |
| **KTable** | Changelog stream interpreted as a table (latest value per key) | [Kafka Streams](docs/11-kafka-streams.md) | [08](08-stream-enrichment/LESSON.md) |
| **Stream-Table Joins** | Enrich a stream with lookup data from a compacted table | [Kafka Streams](docs/11-kafka-streams.md) | [08](08-stream-enrichment/LESSON.md) |
| **Windowed Aggregation** | Count/sum/reduce over time windows (tumbling, hopping, session) | [Kafka Streams](docs/11-kafka-streams.md) | [10](10-windowed-aggregation/LESSON.md) |
| **State Stores** | Local key-value stores for stateful processing (backed by RocksDB) | [Kafka Streams](docs/11-kafka-streams.md) | [10](10-windowed-aggregation/LESSON.md) |
| **Interactive Queries** | Query state stores via REST for real-time dashboards | [Kafka Streams](docs/11-kafka-streams.md) | [10](10-windowed-aggregation/LESSON.md) |

### Integration (Kafka Connect)

| Feature | What it is | Docs | Lessons |
|---------|-----------|------|---------|
| **Kafka Connect** | Framework for streaming data between Kafka and external systems | [Kafka Connect & CDC](docs/12-kafka-connect.md) | [11](11-change-data-capture/LESSON.md) |
| **Source Connectors** | Push data from external systems into Kafka (e.g., Debezium for databases) | [Kafka Connect & CDC](docs/12-kafka-connect.md) | [11](11-change-data-capture/LESSON.md) |
| **Sink Connectors** | Pull data from Kafka into external systems (e.g., Elasticsearch, S3, JDBC) | [Kafka Connect & CDC](docs/12-kafka-connect.md) | — |
| **Debezium** | CDC connector that streams database changes (WAL) to Kafka | [Kafka Connect & CDC](docs/12-kafka-connect.md) | [11](11-change-data-capture/LESSON.md) |
| **Single Message Transforms (SMTs)** | Lightweight per-message transformations in Connect pipelines | [Kafka Connect & CDC](docs/12-kafka-connect.md) | — |

### Enterprise Patterns

| Pattern | What it is | Docs | Lessons |
|---------|-----------|------|---------|
| **Pub/Sub** | One producer, one or more consumers on a topic | [Topics](docs/01-topics.md) | [01](01-simple-pub-sub/LESSON.md) |
| **Fan-Out (Broadcast)** | Multiple consumer groups independently consume the same topic | [Consumer Groups](docs/04-consumer-groups.md) | [02](02-fan-out/LESSON.md) |
| **Event Sourcing** | Store all state changes as immutable events; rebuild state by replay | [Event Sourcing & Log Compaction](docs/13-event-sourcing.md) | [04](04-event-sourcing/LESSON.md) |
| **CQRS** | Separate write path (commands to Kafka) from read path (materialized views) | [CQRS](docs/14-cqrs.md) | [05](05-cqrs/LESSON.md) |
| **Saga (Choreography)** | Distributed transaction via event chain with compensating actions | [Saga Pattern](docs/15-saga-pattern.md) | [07](07-saga-choreography/LESSON.md) |
| **Outbox Pattern** | Write to DB + outbox table atomically; relay to Kafka via poller or CDC | [Outbox Pattern](docs/16-outbox-pattern.md) | [13](13-outbox-pattern/LESSON.md) |
| **Change Data Capture** | Stream database row-level changes to Kafka without application changes | [Kafka Connect & CDC](docs/12-kafka-connect.md) | [11](11-change-data-capture/LESSON.md) |
| **Log Compaction** | Retain only the latest value per key; infinite retention for state topics | [Event Sourcing & Log Compaction](docs/13-event-sourcing.md) | [04](04-event-sourcing/LESSON.md), [08](08-stream-enrichment/LESSON.md) |

### Not Yet Covered

These Kafka features are important in production but not yet demonstrated with a lesson:

| Feature | Description |
|---------|-------------|
| **Multi-Datacenter Replication** | MirrorMaker 2, Cluster Linking for DR and geo-replication |
| **Security (SSL/SASL/ACLs)** | Authentication, authorization, and encryption in transit |
| **Quotas & Rate Limiting** | Client-side and broker-side throughput controls |
| **Monitoring & Observability** | JMX metrics, consumer lag alerting, distributed tracing |
| **Performance Tuning** | Batch size, linger.ms, compression, buffer.memory optimization |
| **Exactly-Once Kafka Streams** | Full EOS semantics in stream processing topologies |
| **Foreign-Key Joins** | KTable-KTable joins on non-primary keys |
| **Grace Periods & Late Arrivals** | Handling out-of-order events in windowed aggregations |
| **Tiered Storage** | Offload old partition segments to object storage (S3, GCS) |

---

## Concept Docs

Deep-dive reference docs for each Kafka concept, with diagrams, configuration tables, and links back to lessons.

| # | Doc | Topics Covered |
|---|-----|----------------|
| [01](docs/01-topics.md) | Topics | Partitions, retention, replication, topic creation |
| [02](docs/02-producers.md) | Producers | Serialization, partitioning, batching, acks, idempotence |
| [03](docs/03-consumers.md) | Consumers | Poll loop, offset commits, rebalancing, auto.offset.reset |
| [04](docs/04-consumer-groups.md) | Consumer Groups | Partition assignment, scaling rules, assignment strategies |
| [05](docs/05-message-keys.md) | Message Keys | Partition routing, per-entity ordering, key serialization |
| [06](docs/06-json-serialization.md) | JSON Serialization | Serde pipeline, format comparison, Spring Kafka config, type mapping |
| [07](docs/07-offsets.md) | Offsets | Commit strategies, delivery guarantees, consumer lag, seeking |
| [08](docs/08-schema-evolution.md) | Schema Evolution | Compatibility modes, tolerant reader, safe schema changes |
| [09](docs/09-transactions.md) | Transactions & EOS | Idempotent producers, transactional APIs, read_committed |
| [10](docs/10-dead-letter-queues.md) | Dead Letter Queues | Error handling, retry backoff, DLQ routing, Spring Kafka config |
| [11](docs/11-kafka-streams.md) | Kafka Streams | KStream, KTable, joins, windows, state stores, serdes |
| [12](docs/12-kafka-connect.md) | Kafka Connect & CDC | Connectors, Debezium, SMTs, CDC event envelope |
| [13](docs/13-event-sourcing.md) | Event Sourcing | Log compaction, event replay, append-only logs |
| [14](docs/14-cqrs.md) | CQRS | Command/query separation, materialized views, eventual consistency |
| [15](docs/15-saga-pattern.md) | Saga Pattern | Choreography vs orchestration, compensating events, correlation IDs |
| [16](docs/16-outbox-pattern.md) | Outbox Pattern | Dual-write problem, transactional outbox, polling relay |

## Tech Stack

All lessons share a consistent stack:

| Component | Version |
|-----------|---------|
| Java | 21 |
| Spring Boot | 3.2.5 |
| Apache Kafka | 3.7.0 (KRaft, no ZooKeeper) |
| AKHQ | 0.25.1 |
| Maven | 3.9 |
| Docker images | `eclipse-temurin:21-jre-alpine`, `maven:3.9-eclipse-temurin-21` |

Additional infrastructure per lesson: PostgreSQL 16 (lessons 11, 13), Debezium Connect 2.6 (lesson 11), Confluent Schema Registry 7.6.1 (lesson 12).
