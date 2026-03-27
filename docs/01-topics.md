# Topics

A **topic** is Kafka's core abstraction — a named, append-only log of records. Think of it as a category or feed name to which messages are published.

## The Basics

- A topic is identified by its **name** (e.g., `order-events`)
- Messages written to a topic are **immutable** — once published, they cannot be changed or deleted (only expired by retention policy)
- Topics are **append-only** — new messages are always added to the end
- Multiple producers can write to the same topic, and multiple consumers can read from it independently

## Partitions

Every topic is divided into one or more **partitions**. Partitions are the unit of parallelism in Kafka.

```
order-events topic (3 partitions):

  Partition 0:  [msg0] [msg3] [msg6] [msg9]  → offset 0, 1, 2, 3
  Partition 1:  [msg1] [msg4] [msg7]          → offset 0, 1, 2
  Partition 2:  [msg2] [msg5] [msg8]          → offset 0, 1, 2
```

Key points:
- **Ordering is guaranteed within a partition**, not across partitions
- Each partition is an independent, ordered log
- The number of partitions determines the maximum parallelism of consumers (one consumer per partition in a group)

## Retention

Kafka topics retain messages based on configurable policies:

| Policy | Config | Default | Description |
|--------|--------|---------|-------------|
| Time-based | `retention.ms` | 7 days | Delete messages older than this |
| Size-based | `retention.bytes` | unlimited | Delete oldest messages when partition exceeds this size |
| Compaction | `cleanup.policy=compact` | — | Keep only the latest value per key |

Unlike traditional message queues, **consuming a message does not remove it**. Messages remain available until the retention policy removes them. This means multiple consumers can read the same messages independently.

## Replication

In production, topics are replicated across multiple brokers for fault tolerance:

- **Replication factor** — how many copies of each partition exist (e.g., 3)
- **Leader** — the broker that handles all reads and writes for a partition
- **Followers** — brokers that replicate the leader's data
- **ISR (In-Sync Replicas)** — followers that are caught up with the leader

The playbook lessons use `replicas(1)` since they run a single broker. In production, a replication factor of 3 is standard.

## Creating Topics

Topics can be created:

1. **Automatically** — when a producer first writes to a non-existent topic (if `auto.create.topics.enable=true`)
2. **Programmatically** — via admin APIs (e.g., Spring's `NewTopic` bean or Kafka's `AdminClient`)
3. **Via CLI** — using `kafka-topics.sh`

Example using Spring Kafka:

```java
TopicBuilder.name("order-events")
    .partitions(3)
    .replicas(1)
    .build();
```

## Hands-On

- **[Lesson 01](../01-simple-pub-sub/LESSON.md)** creates an `order-events` topic with 3 partitions — inspect it in AKHQ to see partition layout, message counts, and topic configuration
- Use `kafka-topics.sh --describe` inside the Kafka container to view topic metadata from the CLI

## Further Reading

- Topic configuration options number in the dozens — explore them in AKHQ under a topic's "Config" tab
- The relationship between partition count and consumer parallelism is covered in [Consumer Groups](04-consumer-groups.md)
- How message keys determine partition assignment is covered in [Message Keys](05-message-keys.md)
- How topics support event sourcing patterns is covered in [Event Sourcing & Log Compaction](13-event-sourcing.md)
