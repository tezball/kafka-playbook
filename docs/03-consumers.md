# Consumers

A **consumer** is any application that reads (subscribes to) records from one or more Kafka topics. Unlike traditional message queues, reading a message does not remove it — the consumer tracks its position using offsets.

## How Consuming Works

```
   Kafka Broker
      │
      ▼
┌──────────────┐
│  Fetch       │  Consumer polls the broker for new messages
└─────┬────────┘
      │
      ▼
┌──────────────┐
│ Deserializer │  Convert bytes back to key + value objects
└─────┬────────┘
      │
      ▼
  Your Application
```

1. **Subscribe** — the consumer subscribes to one or more topics
2. **Poll** — the consumer repeatedly calls `poll()` to fetch batches of messages
3. **Deserialize** — bytes are converted back to objects using the configured deserializer
4. **Process** — the application handles each message
5. **Commit** — the consumer commits its offset to mark messages as processed

## The Poll Loop

Kafka consumers use a **pull model** — the consumer controls when and how fast it reads. This is different from push-based systems where the broker sends messages to the consumer.

```
while (running) {
    records = consumer.poll(Duration.ofMillis(100));
    for (record : records) {
        process(record);
    }
    consumer.commitSync();
}
```

Spring Kafka abstracts this into the `@KafkaListener` annotation, which manages the poll loop for you.

## Key Configuration

| Config | Default | Description |
|--------|---------|-------------|
| `group.id` | — | Consumer group this consumer belongs to (required) |
| `auto.offset.reset` | `latest` | Where to start reading if no committed offset exists: `earliest`, `latest`, or `none` |
| `enable.auto.commit` | `true` | Automatically commit offsets periodically |
| `auto.commit.interval.ms` | `5000` | How often auto-commit runs |
| `max.poll.records` | `500` | Maximum records returned per poll |
| `max.poll.interval.ms` | `300000` | Maximum time between polls before consumer is considered dead |

## auto.offset.reset Explained

This setting only matters **the first time** a consumer group reads a topic (or when its committed offset is out of range):

- **`earliest`** — start from the very first message in the topic (replay everything)
- **`latest`** — start from the end, only see new messages going forward
- **`none`** — throw an exception if no committed offset exists

The playbook lessons use `earliest` so consumers see all messages, including those produced before the consumer started.

## Offset Commits

Offsets tell Kafka "I've processed everything up to this point." If a consumer crashes and restarts, it resumes from its last committed offset.

**Auto-commit (default):** offsets are committed periodically in the background. Simple but messages can be processed twice if the consumer crashes between processing and the next auto-commit.

**Manual commit:** the application explicitly commits after processing. More control, but more code.

Auto-commit is appropriate when occasional duplicates are acceptable (e.g., logging, notifications).

## Rebalancing

When consumers join or leave a group, Kafka **rebalances** — redistributing partitions among the remaining consumers. During a rebalance:

1. All consumers in the group pause
2. Partitions are reassigned
3. Each consumer resumes from its committed offset for the newly assigned partitions

This is why committed offsets matter — they're the consumer's bookmark for surviving rebalances.

## Example: Spring Kafka Consumer

```java
@KafkaListener(topics = "order-events", groupId = "notification-group")
public void handle(OrderEvent event) {
    // process the event
}
```

- **`topics`** — subscribes to one or more topics
- **`groupId`** — joins a consumer group for offset tracking and load balancing
- Spring manages the poll loop, deserialization, and offset commits automatically

## Hands-On

- [Lesson 01](../01-simple-pub-sub/LESSON.md) — `@KafkaListener` consumer with auto-commit and `earliest` offset reset; stop and restart it to see offset-based resumption in action
- [Lesson 02](../02-fan-out/LESSON.md) — 3 consumers with independent consumer groups on the same topic, each processing every message independently
- [Lesson 03](../03-partitioned-processing/LESSON.md) — consumers filter messages by region using an environment variable, demonstrating partition-aware processing
- [Lesson 04](../04-event-sourcing/LESSON.md) — consumer replays all events from the beginning to rebuild running account balances (event sourcing replay)
- [Lesson 05](../05-cqrs/LESSON.md) — consumer builds an in-memory materialized view from command events and exposes it via a REST query API (CQRS read side)
- [Lesson 06](../06-dead-letter-queue/LESSON.md) — consumer with `DefaultErrorHandler`, automatic retry, and DLQ routing for poison-pill messages
- [Lesson 09](../09-exactly-once/LESSON.md) — consumer configured with `read_committed` isolation level so it only sees messages from committed transactions
- [Lesson 12](../12-schema-evolution/LESSON.md) — tolerant consumer that handles multiple schema versions gracefully using Jackson's `FAIL_ON_UNKNOWN_PROPERTIES=false`

## Further Reading

- How consumers share work within a group is covered in [Consumer Groups](04-consumer-groups.md)
- How offsets are tracked is covered in [Offsets](07-offsets.md)
- How to handle poison-pill messages is covered in [Dead Letter Queues](10-dead-letter-queues.md)
