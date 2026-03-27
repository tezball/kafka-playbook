# Message Keys

Every Kafka message has an optional **key**. The key determines which partition the message is sent to, and it enables ordering guarantees for related messages.

## Structure of a Kafka Record

```
┌─────────────────────────────────────┐
│           Kafka Record              │
├──────────┬──────────────────────────┤
│ Key      │ "ORD-1001"              │  ← optional, used for partitioning
├──────────┼──────────────────────────┤
│ Value    │ {"orderId":"ORD-1001",  │  ← the actual message payload
│          │  "customerEmail":...}    │
├──────────┼──────────────────────────┤
│ Headers  │ metadata key-value pairs │  ← optional, like HTTP headers
├──────────┼──────────────────────────┤
│ Timestamp│ 1711545600000           │  ← event time or broker time
└──────────┴──────────────────────────┘
```

## How Keys Determine Partitions

When a key is present, Kafka's default partitioner applies:

```
partition = hash(key) % number_of_partitions
```

This means:
- **Same key = same partition, always** (as long as partition count doesn't change)
- **Same partition = ordered processing** by a single consumer
- Messages with different keys are distributed across partitions

```
Key "ORD-1001" → hash → partition 2
Key "ORD-1001" → hash → partition 2   ← same partition, guaranteed
Key "ORD-1002" → hash → partition 0
Key "ORD-1003" → hash → partition 1
```

## When Keys Are Null

If no key is provided, Kafka uses a **sticky partitioner** (Kafka 2.4+):
- Messages are batched to one partition until the batch is full
- Then the next batch goes to a different partition
- Result: even distribution, but no ordering guarantees for related messages

## Why Keys Matter

Keys enable **per-entity ordering**. For example, using an order ID as the key:

| Scenario | Key | Guarantee |
|----------|-----|-----------|
| All events for order ORD-1001 | `ORD-1001` | Processed in order by one consumer |
| Events across different orders | different keys | No ordering guarantee between them |

This is critical for patterns like:
- Order lifecycle: created → paid → shipped → delivered (must be in order)
- User activity: login → browse → purchase (per-user ordering)
- Account updates: debit → credit → balance check (per-account ordering)

## Key Serialization

Keys are serialized independently from values. Common key serializers:

| Serializer | Use case |
|------------|----------|
| `StringSerializer` | Most common — IDs, names, identifiers |
| `LongSerializer` | Numeric IDs |
| `ByteArraySerializer` | Pre-serialized or binary keys |
| `AvroSerializer` | Structured keys with schema evolution |

The most common choice is `StringSerializer` for entity IDs.

## Partition Count Changes

A critical caveat: **if you change the number of partitions, the key-to-partition mapping changes**. Messages with key `ORD-1001` might go to partition 2 with 3 partitions, but partition 5 with 6 partitions. This breaks ordering guarantees for existing keys.

Best practice: choose your partition count carefully upfront. Increasing partitions is easy; decreasing is not supported.

## Example: Keyed Send

```java
kafkaTemplate.send("order-events", event.orderId(), event);
//                   topic          key              value
```

The key (`orderId`) determines the partition. All events for the same order are colocated.

## Hands-On

- **[Lesson 01](../01-simple-pub-sub/LESSON.md)** uses `orderId` as the message key — click individual messages in AKHQ to see the key, partition, and offset for each record
- **Lesson 03 (Partitioned Processing)** explores key-based routing in depth with region-based partitioning

## Further Reading

- How partitions relate to consumer parallelism is covered in [Consumer Groups](04-consumer-groups.md)
- How the value payload is serialized is covered in [JSON Serialization](06-json-serialization.md)
