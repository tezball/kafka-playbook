# Consumer Groups

A **consumer group** is a set of consumers that cooperate to consume messages from a topic. Kafka distributes partitions among the consumers in a group so that each partition is read by exactly one consumer.

## Why Consumer Groups Exist

Without groups, every consumer would read every message (broadcast). Groups enable **load balancing** — the work of processing a topic is split across multiple consumers.

## Partition Assignment

Kafka assigns partitions to consumers within a group. The key rule: **each partition is assigned to exactly one consumer in the group**.

```
my-topic (3 partitions):

Consumer Group: my-group

  ┌──────────┐   Partition 0
  │Consumer A │◄──Partition 1
  └──────────┘
  ┌──────────┐
  │Consumer B │◄──Partition 2
  └──────────┘
```

Scaling rules:
| Consumers | Partitions | Result |
|-----------|------------|--------|
| 1 | 3 | One consumer reads all 3 partitions |
| 2 | 3 | One reads 2 partitions, one reads 1 |
| 3 | 3 | One partition per consumer (ideal) |
| 4 | 3 | One consumer is idle (wasted) |

**The number of partitions is the upper limit on consumer parallelism within a single group.**

## Multiple Consumer Groups

Different groups consume the same topic independently. Each group maintains its own offsets.

```
order-events topic
      │
      ├──► notification-group   (sends emails)
      │      offset: 42
      │
      ├──► analytics-group      (builds dashboards)
      │      offset: 38
      │
      └──► audit-group          (writes to audit log)
             offset: 42
```

This is Kafka's **pub/sub** model — one topic, many independent subscribers. Adding a new consumer group requires no changes to the producer or existing consumers.

## Assignment Strategies

Kafka supports different strategies for assigning partitions to consumers:

| Strategy | Behavior |
|----------|----------|
| `RangeAssignor` | Assigns partitions in contiguous ranges per topic (default) |
| `RoundRobinAssignor` | Distributes partitions round-robin across consumers |
| `StickyAssignor` | Minimizes partition movement during rebalances |
| `CooperativeStickyAssignor` | Like sticky, but allows incremental rebalancing without stop-the-world pauses |

## Group Coordinator

Kafka brokers manage consumer groups through a **group coordinator**:

1. Each group has a designated coordinator broker
2. Consumers send **heartbeats** to the coordinator to signal liveness
3. If a consumer misses heartbeats (default: 10 seconds), the coordinator triggers a **rebalance**
4. One consumer in the group is elected as the **group leader** and computes the partition assignment

## Hands-On

- **[[../01-simple-pub-sub/LESSON|Lesson 01]]** uses a single consumer group (`email-notification-group`) — view its offset progress and lag in AKHQ's Consumer Groups page
- **Experiment:** stop a consumer, let messages accumulate, then restart it. Watch lag spike and recover to 0 — this is the group's offset tracking in action
- **Lesson 02 (Fan-Out)** demonstrates multiple consumer groups on the same topic — each processes independently

## Further Reading

- How offsets are tracked per group is covered in [[07-offsets|Offsets]]
- How partition keys affect which partition receives a message is covered in [[05-message-keys|Message Keys]]
