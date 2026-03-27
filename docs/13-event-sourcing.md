# Event Sourcing

**Event sourcing** is an architectural pattern where you store every state change as an immutable event, rather than overwriting the current state. The event log becomes the single source of truth, and the current state is derived by replaying events from the beginning.

## Core Idea

In traditional CRUD, you store the latest state:

```
Account #42:  balance = 750
```

In event sourcing, you store every change that led to that state:

```
Account #42 event log:
  offset 0:  AccountOpened   { amount: 0 }
  offset 1:  MoneyDeposited  { amount: 1000 }
  offset 2:  MoneyWithdrawn  { amount: 200 }
  offset 3:  MoneyWithdrawn  { amount: 50 }
```

The current balance (750) is derived by replaying all four events. Nothing is lost — you have a complete audit trail.

## The Event Log as Source of Truth

A Kafka topic is a natural fit for an event log:

- Messages are **immutable** and **append-only** — events cannot be altered after the fact
- Messages are **ordered within a partition** — events for a given entity replay in the correct sequence
- Messages are **retained durably** — the log persists across restarts and failures

Using the entity ID (e.g., account number) as the message key ensures all events for one entity land in the same partition, preserving order.

## Event Sourcing vs Traditional CRUD

| | Traditional CRUD | Event Sourcing |
|---|---|---|
| **Storage** | Latest state only | Full history of events |
| **Audit trail** | Requires separate logging | Built-in — the log is the data |
| **Debugging** | "Why is the balance 750?" — unknown | Replay the log and see every step |
| **Schema evolution** | Migrate rows in place | Old events remain; new consumers interpret them |
| **Rebuilding views** | Not possible without backups | Replay from offset 0 at any time |
| **Storage cost** | Lower (one row per entity) | Higher (one row per event) |

## Log Compaction

Over time, an event log grows without bound. Kafka's **log compaction** addresses this for key-based topics by keeping only the latest value per key.

```
Before compaction:
  key=42  offset 0: AccountOpened
  key=42  offset 1: MoneyDeposited
  key=99  offset 2: AccountOpened
  key=42  offset 3: MoneyWithdrawn    ← latest for key 42

After compaction:
  key=99  offset 2: AccountOpened     ← latest for key 99
  key=42  offset 3: MoneyWithdrawn    ← latest for key 42
```

Compaction is useful for state snapshots (see below), but it destroys full history. Choose your cleanup policy based on whether you need complete replay or just the latest snapshot per entity.

### Compaction Configuration

| Config | Description | Example |
|--------|-------------|---------|
| `cleanup.policy=compact` | Enable log compaction for this topic | Required |
| `min.compaction.lag.ms` | Minimum time before a message is eligible for compaction | `3600000` (1 hour) |
| `delete.retention.ms` | How long tombstone records (key with null value) are retained after compaction | `86400000` (24 hours) |
| `min.cleanable.dirty.ratio` | Ratio of log that must be "dirty" before compaction triggers | `0.5` |

**Tombstones** are messages with a valid key but a null value. They signal deletion — after compaction, the key is removed entirely once `delete.retention.ms` expires.

## Rebuilding State by Replay

One of event sourcing's most powerful features is the ability to rebuild state at any time:

1. Create a new consumer group (or seek existing consumers to offset 0)
2. Replay every event from the beginning of the topic
3. Apply each event to build the current state

This lets you fix bugs in projection logic, add new read models, or migrate to a new database — all without losing data.

## Snapshot Optimization

Full replay can be slow when the event log contains millions of events. **Snapshots** solve this by periodically persisting the derived state:

```
Event log:    [e0] [e1] [e2] ... [e999] [snapshot @ offset 999] [e1000] [e1001] ...
                                                                  ▲
Rebuild from snapshot:  load snapshot → replay only e1000, e1001, ...
```

A common approach is to publish snapshots to a compacted topic keyed by entity ID. On startup, load the latest snapshot, then replay only the events after the snapshot's offset.

## Hands-On

- [Lesson 04](../04-event-sourcing/LESSON.md) demonstrates a bank ledger that stores deposit and withdrawal events in a Kafka topic, then derives running balances by replaying the event stream
- Experiment with replaying from offset 0 to rebuild state after modifying the consumer logic

## Further Reading

- Kafka topics and retention policies are covered in [Topics](01-topics.md)
- Offset management and seeking to specific positions is covered in [Offsets](07-offsets.md)
- Separating read and write models with CQRS builds naturally on event sourcing — see [CQRS](14-cqrs.md)
