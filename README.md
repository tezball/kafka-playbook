# Kafka Playbook

A collection of self-contained lessons teaching Apache Kafka through hands-on, real-world examples. Each lesson runs entirely in Docker — no local Java, Maven, or Kafka installation required.

## Prerequisites

- Docker & Docker Compose
- [Obsidian](https://obsidian.md/) — open this folder as a vault to navigate lessons and concept docs via linked notes

## Lessons

| # | Lesson | Scenario | Kafka Concepts |
|---|--------|----------|----------------|
| [[01-simple-pub-sub/LESSON\|01]] | Simple Pub/Sub | Order placed → email notification | Topics, producers, consumers, consumer groups, JSON serialization |
| [[02-fan-out/LESSON\|02]] | Fan-Out (Broadcast) | User signup → multiple services | Multiple consumer groups, independent offset tracking |
| [[03-partitioned-processing/LESSON\|03]] | Partitioned Processing | Orders routed by region | Partition keys, ordering guarantees, partition assignment |
| [[04-event-sourcing/LESSON\|04]] | Event Sourcing | Bank account ledger | Log compaction, replay, event schema design |
| [[05-cqrs/LESSON\|05]] | CQRS | Product catalog → search index | Separate read/write topics, materialized views |
| [[06-dead-letter-queue/LESSON\|06]] | Dead Letter Queue | Payment processing with error handling | DLQ topics, retry topics, manual offset management |
| [[07-saga-choreography/LESSON\|07]] | Saga / Choreography | E-commerce checkout flow | Multi-topic coordination, compensating events, correlation IDs |
| [[08-stream-enrichment/LESSON\|08]] | Stream Enrichment | Clickstream + user profiles | Kafka Streams, KTable, stream-table joins |
| [[09-exactly-once/LESSON\|09]] | Exactly-Once Processing | Financial dedup | Idempotent producers, transactions, `read_committed` |
| [[10-windowed-aggregation/LESSON\|10]] | Windowed Aggregation | Real-time order dashboard | Kafka Streams, tumbling/hopping/session windows |
| [[11-change-data-capture/LESSON\|11]] | Change Data Capture | Postgres → Kafka → CDC consumer | Kafka Connect, Debezium, CDC events |
| [[12-schema-evolution/LESSON\|12]] | Schema Evolution | API versioning across teams | Schema versioning, backward compatibility, multi-version consumers |
| [[13-outbox-pattern/LESSON\|13]] | Outbox Pattern | Reliable DB + event publishing | Transactional outbox, polling publisher, at-least-once delivery |

## Running a Lesson

```bash
cd 01-simple-pub-sub
./start.sh
```

Each lesson's `LESSON.md` contains the full exercise guide with `[[wikilinks]]` to concept docs explaining each Kafka topic in depth.

## Reading the Lessons

Open this folder as an **Obsidian vault** (`File → Open Vault → Open folder as vault`). Lesson guides and concept docs are interlinked — click any highlighted term to jump to its explanation. The graph view shows how concepts connect across lessons.
