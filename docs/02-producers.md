# Producers

A **producer** is any application that writes (publishes) records to a Kafka topic. The producer decides which topic and — optionally — which partition to send each message to.

## How Producing Works

```
Your Application
     │
     ▼
┌─────────────┐
│  Serializer │  Convert key + value to bytes
└─────┬───────┘
      │
      ▼
┌─────────────┐
│  Partitioner│  Decide which partition gets this message
└─────┬───────┘
      │
      ▼
┌─────────────┐
│ Record Batch│  Buffer messages, send in batches for efficiency
└─────┬───────┘
      │
      ▼
   Kafka Broker
```

1. **Serialize** — the key and value are converted to bytes using the configured serializer
2. **Partition** — the partitioner decides the target partition (based on the key, round-robin, or custom logic)
3. **Batch** — messages are buffered and sent in batches to reduce network overhead
4. **Acknowledge** — the broker confirms receipt based on the `acks` setting

## Key Configuration

| Config | Values | Description |
|--------|--------|-------------|
| `acks` | `0`, `1`, `all` | How many replicas must confirm before success. `0` = fire and forget, `1` = leader only, `all` = all ISR replicas |
| `retries` | integer | Number of retry attempts on transient failure |
| `batch.size` | bytes | Maximum batch size before sending |
| `linger.ms` | milliseconds | How long to wait for more messages before sending a batch |
| `buffer.memory` | bytes | Total memory available for buffering unsent messages |

## Acknowledgment Modes

The `acks` setting controls the durability guarantee:

- **`acks=0`** — Producer doesn't wait for any confirmation. Fastest, but messages can be lost.
- **`acks=1`** — Producer waits for the leader to write the message. Good balance of speed and safety.
- **`acks=all`** — Producer waits for all in-sync replicas to confirm. Slowest, but strongest guarantee.

## Synchronous vs Asynchronous

Producers can send messages two ways:

**Asynchronous (default):**
```java
kafkaTemplate.send(topic, key, value)
    .whenComplete((result, ex) -> {
        // callback after broker confirms or fails
    });
```
The application continues immediately. The callback fires later.

**Synchronous:**
```java
kafkaTemplate.send(topic, key, value).get();  // blocks until confirmed
```
The application blocks until the broker responds. Simpler error handling, lower throughput.

## Idempotent Producers

When `enable.idempotence=true` (default in Kafka 3.x), the producer assigns a sequence number to each message. The broker detects and deduplicates retries, guaranteeing exactly-once delivery from producer to broker. This prevents duplicate messages caused by network retries.

## Example: Spring Kafka Producer

```java
kafkaTemplate.send("order-events", event.orderId(), event)
    .whenComplete((result, ex) -> {
        if (ex != null) {
            log.error("Send failed: {}", ex.getMessage());
        } else {
            var meta = result.getRecordMetadata();
            log.info("Sent to partition {} offset {}", meta.partition(), meta.offset());
        }
    });
```

- The **key** (`orderId`) determines the target partition
- The **value** (`event`) is serialized to JSON
- The callback fires after the broker acknowledges or rejects the message

## Hands-On

- **[[../01-simple-pub-sub/LESSON|Lesson 01]]** implements an async producer with callbacks — watch the producer logs to see partition and offset confirmations
- Try sending messages via the REST API and observe which partition each message lands on in AKHQ

## Further Reading

- How the key determines partition assignment is covered in [[05-message-keys|Message Keys]]
- How the value is serialized to JSON is covered in [[06-json-serialization|JSON Serialization]]
- Exactly-once semantics (transactions) are covered in Lesson 09
