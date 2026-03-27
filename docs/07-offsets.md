# Offsets

An **offset** is a sequential ID assigned to each message within a partition. Offsets are how Kafka and consumers track position — they're the bookmark that enables resuming after restarts, crashes, or rebalances.

## How Offsets Work

Each partition maintains its own independent offset sequence, starting at 0:

```
Partition 0:  [0] [1] [2] [3] [4] [5] [6]
                                   ▲
                                   └── consumer's committed offset (5)
                                       "I've processed up to here"
```

- **Log-end offset** — the offset of the next message that will be written (the partition's "tip")
- **Committed offset** — the last offset the consumer has confirmed processing
- **Consumer lag** — the difference: `log-end offset - committed offset`

## Offset Storage

Consumer offsets are stored in a special internal Kafka topic: `__consumer_offsets`. This topic holds a compacted log of `(group, topic, partition) → offset` mappings.

You can see this topic in AKHQ under "All Topics" — it's created automatically by Kafka.

## Commit Strategies

### Auto-Commit (default)

```yaml
spring.kafka.consumer:
  enable-auto-commit: true
  auto-commit-interval-ms: 5000
```

Offsets are committed every 5 seconds in the background. Simple, but:
- If the consumer crashes after processing but before the next auto-commit, those messages will be **reprocessed** (at-least-once)
- If the consumer crashes after auto-commit but before processing completes, those messages are **skipped** (at-most-once)

### Manual Synchronous Commit

```java
consumer.commitSync();  // blocks until broker confirms
```

Commit after processing each batch. Slower, but you control exactly when offsets advance.

### Manual Asynchronous Commit

```java
consumer.commitAsync((offsets, exception) -> {
    // callback after commit completes
});
```

Non-blocking, but if the commit fails, subsequent commits may succeed with higher offsets, effectively skipping the failed batch.

## Delivery Guarantees

The interaction between offset commits and message processing determines your delivery guarantee:

| Strategy | Guarantee | Risk |
|----------|-----------|------|
| Commit before processing | At-most-once | Messages can be lost |
| Commit after processing | At-least-once | Messages can be duplicated |
| Transactional (Lesson 09) | Exactly-once | Highest overhead |

Choose based on your use case: at-least-once is appropriate for notifications (a duplicate is better than a lost message), while financial transactions require exactly-once.

## Consumer Lag

Lag is the most important operational metric for Kafka consumers. It tells you how far behind a consumer is.

```
Partition 0:  [0] [1] [2] [3] [4] [5] [6] [7] [8] [9]
                                           ▲           ▲
                                  committed: 5    log-end: 10
                                           └─── lag: 5 ───┘
```

- **Lag = 0** — consumer is caught up, processing messages in real-time
- **Lag growing** — consumer is falling behind (too slow, or stopped)
- **Lag spike then recovery** — consumer was restarted, replayed missed messages

## Seeking to a Specific Offset

Consumers can **seek** to any offset in a partition:

```java
consumer.seek(partition, 0);        // replay from beginning
consumer.seek(partition, offset);   // jump to specific offset
consumer.seekToEnd(partitions);     // skip to latest
```

This is powerful for:
- Replaying events after a bug fix
- Skipping corrupted messages
- Testing consumer logic against historical data

## Hands-On

- **[[../01-simple-pub-sub/LESSON|Lesson 01]]** uses auto-commit with `auto-offset-reset: earliest` — on first startup the consumer reads from the beginning; on restarts it resumes from the last committed offset
- **Experiment:** Stop a consumer, let messages accumulate, then restart it. Watch lag spike in AKHQ and then drop as it catches up
- Lesson 09 (Exactly-Once Processing) demonstrates transactional offset commits

## Further Reading

- How consumer groups coordinate offset tracking is covered in [[04-consumer-groups|Consumer Groups]]
- Exactly-once delivery and transactional offsets are covered in Lesson 09
