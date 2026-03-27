# Transactions

Kafka transactions enable **exactly-once semantics (EOS)** — the guarantee that each message is processed exactly once, even in the face of producer retries, consumer restarts, or application crashes.

## Idempotent Producers

An **idempotent producer** guarantees that retries don't create duplicates on the broker. This is the foundation for exactly-once delivery from producer to broker.

When `enable.idempotence=true` (the default in Kafka 3.x), the broker assigns each producer a **ProducerID (PID)** and the producer attaches a **sequence number** to every message:

```
Producer (PID=7)
     │
     ├── send msg (seq=0) ────► Broker writes [PID=7, seq=0]
     ├── send msg (seq=1) ────► Broker writes [PID=7, seq=1]
     ├── send msg (seq=1) ────► Broker detects duplicate, discards
     │   (retry due to timeout)
```

The broker tracks the latest sequence number per PID per partition. If it receives a message with a sequence number it has already seen, it silently discards the duplicate.

**Key requirements for idempotence:**
- `acks=all` (enforced automatically)
- `retries > 0` (default is very high)
- `max.in.flight.requests.per.connection <= 5`

## Transactional APIs

Idempotence prevents duplicates for a single send, but what about operations that span multiple topics or partitions? **Transactions** let you atomically write to multiple topics and commit consumer offsets together.

```java
// Spring Kafka transactional producer
kafkaTemplate.executeInTransaction(ops -> {
    ops.send("debit-events", debitEvent);
    ops.send("credit-events", creditEvent);
    return true;
});
```

The key configuration is `transactional.id`:

| Config | Description |
|--------|-------------|
| `transactional.id` | Unique identifier for this producer's transactions. Must be stable across restarts. |
| `transaction.timeout.ms` | Maximum time a transaction can remain open (default: 60s) |

**Transaction lifecycle:**
1. `initTransactions()` — register the transactional.id with the broker (called once at startup)
2. `beginTransaction()` — start a new transaction
3. `send()` — produce messages (buffered until commit)
4. `sendOffsetsToTransaction()` — include consumer offset commits in the transaction
5. `commitTransaction()` — atomically write all messages and offsets
6. `abortTransaction()` — discard all messages if something goes wrong

Spring Kafka's `executeInTransaction()` wraps steps 2-6 automatically.

## Consumer Isolation Levels

Consumers must opt in to seeing only committed transactional messages:

| Isolation Level | Behavior |
|-----------------|----------|
| `read_uncommitted` (default) | Reads all messages, including those from aborted transactions |
| `read_committed` | Only reads messages from committed transactions (or non-transactional messages) |

```yaml
spring.kafka.consumer:
  properties:
    isolation.level: read_committed
```

Without `read_committed`, transactions on the producer side are meaningless — the consumer would process aborted messages anyway.

## Exactly-Once Semantics (EOS)

EOS is achieved by combining three mechanisms:

```
┌─────────────────────────────────────────────────────┐
│                  Exactly-Once =                      │
│                                                      │
│   Idempotent Producer   (no duplicates on send)      │
│ + Transactions          (atomic multi-write + offset)│
│ + read_committed        (consumers skip aborted)     │
└─────────────────────────────────────────────────────┘
```

This guarantees that a consume-transform-produce pipeline processes each input message exactly once, producing exactly one set of output messages.

## Transaction State Topic

Kafka stores transaction metadata in an internal topic called `__transaction_state`. This topic tracks:
- Active transactions and their associated partitions
- Transaction status (ongoing, prepare-commit, committed, aborted)
- ProducerID-to-transactional.id mappings

Like `__consumer_offsets`, this topic is compacted and managed automatically by the broker. You can inspect it in AKHQ under internal topics.

## Zombie Fencing

When a producer crashes mid-transaction and a new instance starts with the same `transactional.id`, the broker must prevent the old ("zombie") producer from completing its transaction. Kafka uses **epoch numbers** for this:

```
Producer A (PID=7, epoch=1) ── starts transaction ──► crash!
Producer B (PID=7, epoch=2) ── initTransactions() ──► broker bumps epoch
Producer A (epoch=1) ── tries to commit ──► REJECTED (stale epoch)
```

Each call to `initTransactions()` increments the epoch. The broker rejects any request from a producer with a stale epoch, effectively fencing off zombies.

## Performance Cost

Transactions add overhead:

| Aspect | Impact |
|--------|--------|
| Latency | Higher — messages are buffered until commit |
| Throughput | Lower — commit is a synchronous broker round-trip |
| Broker load | Higher — tracking transaction state, managing epochs |
| Consumer lag | Slightly higher — `read_committed` consumers wait for transaction resolution |

For most applications, the overhead is acceptable. Use transactions only where correctness requires it — financial operations, inventory updates, cross-topic consistency — and leave non-critical paths (logging, analytics) non-transactional.

## Hands-On

- [Lesson 01](../01-simple-pub-sub/LESSON.md) — idempotent producer enabled by default (Kafka 3.x), visible in startup logs
- [Lesson 09](../09-exactly-once/LESSON.md) — full transactional producer: `executeInTransaction()` atomically writes debits + credits; consumer uses `read_committed` to only see committed messages
- [Lesson 13](../13-outbox-pattern/LESSON.md) — database transaction + outbox as an alternative to Kafka-native transactions

## Further Reading

- How idempotent producers work at the protocol level is covered in [Producers](02-producers.md)
- How offset commits interact with delivery guarantees is covered in [Offsets](07-offsets.md)
