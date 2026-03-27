# Kafka Streams

**Kafka Streams** is a client library for building stream processing applications. Unlike Spark or Flink, it runs inside your application — no separate cluster required. You deploy it as a normal Java/Kotlin application.

## Library, Not a Cluster

```
┌─────────────────────────────┐
│   Your Application (JVM)    │
│                             │
│  ┌───────────────────────┐  │
│  │   Kafka Streams       │  │
│  │   processing logic    │  │
│  └───────────────────────┘  │
│         │           ▲       │
│         ▼           │       │
│     output       input      │
│     topics       topics     │
└─────────┼───────────┼───────┘
          ▼           │
      ┌───────────────────┐
      │   Kafka Cluster   │
      └───────────────────┘
```

Scaling is done by running more instances of your application. Kafka Streams automatically distributes partitions across instances using the consumer group protocol.

## KStream vs KTable vs GlobalKTable

| Abstraction | Represents | Analogy |
|-------------|-----------|---------|
| **KStream** | An unbounded stream of events | An append-only log — every record is a new event |
| **KTable** | A changelog of the latest value per key | A database table — each key has one current value |
| **GlobalKTable** | A KTable replicated to all instances | A broadcast lookup table — every instance has all data |

```
KStream (order-events):
  key=ORD-1, value={status: CREATED}
  key=ORD-1, value={status: PAID}        ◄── both records exist
  key=ORD-2, value={status: CREATED}

KTable (order-events):
  key=ORD-1, value={status: PAID}         ◄── only latest per key
  key=ORD-2, value={status: CREATED}
```

Use **KStream** for events (things that happened). Use **KTable** for state (the current value of something). Use **GlobalKTable** for small reference data that every instance needs (e.g., product catalog, currency rates).

## Stream-Table Joins

Joins combine data from two sources. Stream-table joins are the most common pattern — enriching events with reference data:

```
KStream (orders)              KTable (customers)
  key=C-1, order=ORD-42  ──►  key=C-1, name="Alice"
                          join
                           │
                           ▼
              key=C-1, {order=ORD-42, name="Alice"}
```

| Join Type | Left (Stream) Key Missing | Right (Table) Key Missing |
|-----------|--------------------------|--------------------------|
| **Inner** | Dropped | Dropped |
| **Left** | Impossible (stream drives) | Right side is null |
| **Outer** | N/A for stream-table | Right side is null |

Stream-table joins are **not windowed** — the stream event is joined against the current value in the table at the time the event arrives.

## Windowed Aggregations

Windows group stream events by time for aggregation (counts, sums, averages).

### Tumbling Windows

Fixed-size, non-overlapping:
```
Time:   |  0s----5s  |  5s----10s  |  10s----15s  |
Events: | A  B  C    |  D  E       |  F           |
Count:  |     3      |      2      |      1       |
```

### Hopping Windows

Fixed-size, overlapping (advance interval < window size):
```
Window size: 10s, Advance: 5s
Time:   |  0s---------10s  |
        |       5s---------15s  |
Events at 3s, 7s, 12s:
Window [0,10):  events at 3s, 7s    → count 2
Window [5,15):  events at 7s, 12s   → count 2
```

### Session Windows

Dynamic size, driven by activity. A session closes after an inactivity gap:
```
Inactivity gap: 5s

Events: |--A--B----C---------D--E--|
Time:   0  1  2    4         12 13
        |--session 1--|  gap  |--session 2--|
        [0, 4+5=9)           [12, 13+5=18)
```

## State Stores

Kafka Streams persists aggregation results and table data in local **state stores**, backed by RocksDB:

- State stores are stored on local disk for fast access
- Each state store has a **changelog topic** in Kafka for fault tolerance
- If an instance crashes, a new instance rebuilds the state store from the changelog topic
- State stores enable fast lookups without querying an external database

## Interactive Queries

State stores can be queried directly via your application's REST API — no external database needed:

```java
ReadOnlyKeyValueStore<String, Long> store =
    streams.store(StoreQueryParameters.fromNameAndType(
        "word-counts", QueryableStoreTypes.keyValueStore()));

Long count = store.get("kafka");  // query local state
```

This turns your Kafka Streams application into a queryable, distributed data store.

## Serdes

**Serdes** (serializer/deserializer pairs) define how keys and values are converted to and from bytes in Kafka Streams:

```java
JsonSerde<OrderEvent> orderSerde = new JsonSerde<>(OrderEvent.class);
orderSerde.configure(Map.of(
    JsonDeserializer.TRUSTED_PACKAGES, "com.playbook.*",
    JsonDeserializer.USE_TYPE_INFO_HEADERS, false
), false);
```

| Serde | Use Case |
|-------|----------|
| `Serdes.String()` | String keys and values |
| `Serdes.Long()` | Numeric aggregation results |
| `JsonSerde<T>` | JSON-serialized domain objects |

Configure `TRUSTED_PACKAGES` to control which classes can be deserialized — a security measure against arbitrary class instantiation.

## Topology

A Kafka Streams application is defined as a **topology** — a directed acyclic graph of processing steps:

```
Source (input topic)
     │
     ▼
  Filter ──► Map ──► GroupByKey ──► Aggregate
                                       │
                                       ▼
                                 Sink (output topic)
```

- **Sources** read from Kafka topics
- **Processors** transform, filter, join, or aggregate
- **Sinks** write results to Kafka topics

The topology is defined once at startup and executed continuously as new messages arrive.

## Exactly-Once in Streams

Kafka Streams supports exactly-once processing via the `processing.guarantee` config:

| Value | Guarantee | Performance |
|-------|-----------|-------------|
| `at_least_once` (default) | Messages may be processed more than once after failure | Higher throughput |
| `exactly_once_v2` | Each input message produces exactly one set of outputs | Lower throughput, uses transactions internally |

Under the hood, `exactly_once_v2` wraps each processing step in a transaction — reading input, updating state stores, and writing output are committed atomically.

## Hands-On

- **[Lesson 08](../08-stream-enrichment/LESSON.md)** demonstrates stream-table joins — enriching order events with customer data from a KTable to produce a combined output stream
- **[Lesson 10](../10-windowed-aggregation/LESSON.md)** demonstrates windowed aggregation — counting events in tumbling time windows and querying the results

## Further Reading

- How topics serve as inputs and outputs for stream processing is covered in [Topics](01-topics.md)
- How message keys drive partitioning, co-partitioning, and joins is covered in [Message Keys](05-message-keys.md)
