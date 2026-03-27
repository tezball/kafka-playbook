# Kafka Connect

**Kafka Connect** is a distributed, scalable framework for streaming data between Kafka and external systems. Instead of writing custom producers and consumers for every database, search engine, or data lake, you configure a connector and let Connect handle the rest.

## Source vs Sink Connectors

```
┌──────────┐    Source Connector     ┌───────┐    Sink Connector     ┌──────────────┐
│ Database │ ──────────────────────► │ Kafka │ ──────────────────── ► │ Elasticsearch│
│ (Postgres)│    (reads changes)     │ Topic │    (writes records)   │              │
└──────────┘                         └───────┘                       └──────────────┘

┌──────────┐    Source Connector     ┌───────┐    Sink Connector     ┌──────────────┐
│ Files/   │ ──────────────────────► │ Kafka │ ──────────────────── ► │ S3 / HDFS    │
│ Logs     │                         │ Topic │                       │              │
└──────────┘                         └───────┘                       └──────────────┘
```

- **Source connectors** read from external systems and write to Kafka topics
- **Sink connectors** read from Kafka topics and write to external systems
- A single Kafka Connect cluster can run many connectors simultaneously

## Connector Configuration via REST API

Connectors are managed through the Connect REST API — no code deployment needed:

```bash
# Create a connector
curl -X POST http://localhost:8083/connectors \
  -H "Content-Type: application/json" \
  -d '{
    "name": "postgres-source",
    "config": {
      "connector.class": "io.debezium.connector.postgresql.PostgresConnector",
      "database.hostname": "postgres",
      "database.port": "5432",
      "database.user": "postgres",
      "database.password": "postgres",
      "database.dbname": "shop",
      "topic.prefix": "cdc"
    }
  }'

# Check connector status
curl http://localhost:8083/connectors/postgres-source/status

# List all connectors
curl http://localhost:8083/connectors

# Delete a connector
curl -X DELETE http://localhost:8083/connectors/postgres-source
```

## Debezium: Change Data Capture

**Debezium** is the most popular Kafka Connect source connector. It captures row-level changes from databases and streams them to Kafka topics in real time. Supported databases include PostgreSQL, MySQL, MongoDB, SQL Server, and Oracle.

### How CDC Works

Instead of polling the database for changes, Debezium reads the **write-ahead log (WAL)** — the same log the database uses for replication and crash recovery:

```
Application ──► INSERT INTO orders ──► PostgreSQL
                                           │
                                      WAL (write-ahead log)
                                           │
                                      Debezium reads WAL
                                           │
                                           ▼
                                      Kafka Topic
                                      (cdc.public.orders)
```

This approach is:
- **Non-intrusive** — no triggers, no polling queries, no application changes
- **Low latency** — changes appear in Kafka within milliseconds
- **Complete** — captures all changes, including those made by direct SQL or other applications

### Logical Decoding (PostgreSQL)

PostgreSQL exposes WAL changes through **logical decoding**. Debezium uses the `pgoutput` plugin (built into PostgreSQL 10+) to receive a stream of row-level changes. The database must be configured with `wal_level=logical`.

### CDC Event Envelope

Each Debezium event wraps the change in a standard envelope:

```json
{
  "before": { "id": 1, "status": "pending", "total": 29.99 },
  "after":  { "id": 1, "status": "shipped", "total": 29.99 },
  "op": "u",
  "source": {
    "connector": "postgresql",
    "db": "shop",
    "schema": "public",
    "table": "orders",
    "lsn": 234567890
  },
  "ts_ms": 1711540800000
}
```

| Field | Description |
|-------|-------------|
| `before` | Row state before the change (`null` for inserts) |
| `after` | Row state after the change (`null` for deletes) |
| `op` | Operation: `c` (create), `u` (update), `d` (delete), `r` (snapshot read) |
| `source` | Metadata: connector, database, table, WAL position |
| `ts_ms` | Timestamp of the change in the database |

## Single Message Transforms (SMTs)

**SMTs** are lightweight transformations applied to each message as it flows through Connect — no separate stream processing needed:

| SMT | Purpose |
|-----|---------|
| `ExtractNewRecordState` | Flatten Debezium envelope to just the `after` state |
| `ExtractField` | Pull a single field from the value (e.g., extract just the `id`) |
| `ReplaceField` | Rename, drop, or add fields |
| `TimestampConverter` | Convert between timestamp formats |
| `ValueToKey` | Copy fields from the value into the message key |
| `RegexRouter` | Rename the target topic using a regex |

Example — flattening Debezium events:

```json
{
  "transforms": "unwrap",
  "transforms.unwrap.type": "io.debezium.transforms.ExtractNewRecordState",
  "transforms.unwrap.drop.tombstones": "false",
  "transforms.unwrap.add.fields": "op,table,ts_ms"
}
```

This converts the nested Debezium envelope into a flat record containing only the `after` state, with operation metadata added as extra fields.

## Common Sink Connectors

| Connector | Target | Use Case |
|-----------|--------|----------|
| Elasticsearch Sink | Elasticsearch/OpenSearch | Full-text search, dashboards |
| S3 Sink | Amazon S3 | Data lake, archival |
| JDBC Sink | Any RDBMS | Replicate data to another database |
| BigQuery Sink | Google BigQuery | Analytics, data warehousing |
| MongoDB Sink | MongoDB | Document store replication |

## Distributed vs Standalone Mode

| Mode | Workers | Use Case |
|------|---------|----------|
| **Distributed** | Multiple workers share tasks, automatic failover | Production |
| **Standalone** | Single worker, no fault tolerance | Development, testing |

In distributed mode, Connect stores connector configurations and offsets in internal Kafka topics (`connect-configs`, `connect-offsets`, `connect-status`), enabling any worker to pick up tasks from a failed worker.

## Hands-On

- [Lesson 11](../11-change-data-capture/LESSON.md) — Debezium PostgreSQL source connector; connector registered via REST API after Kafka Connect starts; CDC events with before/after payloads flow to `dbserver1.public.products` topic; Spring Boot consumer parses the Debezium envelope
- [Lesson 13](../13-outbox-pattern/LESSON.md) — alternative to CDC: polling-based outbox relay (lesson mentions Debezium as a production alternative)

## Further Reading

- How topics receive and organize CDC events is covered in [Topics](01-topics.md)
- Using CDC with the transactional outbox pattern for reliable event publishing is covered in [Outbox Pattern](16-outbox-pattern.md)
