# Dead Letter Queues

A **dead letter queue (DLQ)** is a separate Kafka topic where messages that cannot be processed after repeated attempts are sent. Instead of blocking the consumer or losing the message, the DLQ captures it for later inspection and remediation.

## When to Use a DLQ

Not every failure deserves a DLQ. Use them when:

- **Poison pills** — a single malformed message would block the entire partition forever
- **Transient failures** — downstream services are temporarily unavailable, and retries are exhausted
- **Data quality issues** — invalid data that no amount of retrying will fix
- **Compliance** — you must never lose a message, even if it can't be processed right now

```
Consumer reads message
     │
     ├── process successfully ──► commit offset, continue
     │
     └── processing fails
          │
          ├── retry (1, 2, 3...) ──► still failing
          │
          └── send to DLQ topic ──► commit offset, continue
                                     (unblocks the partition)
```

## Spring Kafka Error Handling

Spring Kafka provides a layered error handling system:

### DefaultErrorHandler

The `DefaultErrorHandler` is the standard error handler in Spring Kafka 3.x. It combines retry logic with a recovery strategy:

```java
@Bean
public DefaultErrorHandler errorHandler(
        DeadLetterPublishingRecoverer recoverer) {
    return new DefaultErrorHandler(
        recoverer,
        new FixedBackOff(1000L, 3)  // 1 second between retries, 3 attempts
    );
}
```

When all retries are exhausted, the handler delegates to the **recoverer** — typically a `DeadLetterPublishingRecoverer` that publishes the failed message to a DLQ topic.

### CommonErrorHandler

`CommonErrorHandler` is the interface that `DefaultErrorHandler` implements. You can create custom implementations for specialized behavior like circuit breaking or conditional retry logic.

## Retry Strategies

| Strategy | Behavior | Use Case |
|----------|----------|----------|
| `FixedBackOff(interval, maxAttempts)` | Same delay between each retry | Simple, predictable retry |
| `ExponentialBackOff` | Increasing delay (1s, 2s, 4s, 8s...) | Transient failures with back-pressure |
| No retries (`FixedBackOff(0, 0)`) | Immediately send to DLQ | Known non-retriable errors |

```java
// Exponential backoff: starts at 1s, multiplies by 2, caps at 30s
var backOff = new ExponentialBackOff(1000L, 2.0);
backOff.setMaxElapsedTime(30000L);
```

Choose exponential backoff when the failure is likely transient (network timeout, database overload). Choose fixed backoff for predictable failures with a known recovery time.

## DeadLetterPublishingRecoverer

The `DeadLetterPublishingRecoverer` publishes failed messages to a DLQ topic, preserving the original message content and adding diagnostic headers:

```java
@Bean
public DeadLetterPublishingRecoverer recoverer(KafkaTemplate<String, Object> template) {
    return new DeadLetterPublishingRecoverer(template,
        (record, ex) -> new TopicPartition(record.topic() + ".DLQ", record.partition()));
}
```

This routes `order-events` failures to `order-events.DLQ`, preserving the partition assignment.

## DLQ Message Headers

When a message lands in the DLQ, Spring Kafka adds headers that capture the failure context:

| Header | Content |
|--------|---------|
| `kafka_dlt-exception-fqcn` | Fully qualified exception class name |
| `kafka_dlt-exception-message` | Exception message text |
| `kafka_dlt-exception-stacktrace` | Full stack trace |
| `kafka_dlt-original-topic` | Source topic the message came from |
| `kafka_dlt-original-partition` | Source partition number |
| `kafka_dlt-original-offset` | Source offset within the partition |
| `kafka_dlt-original-timestamp` | Original message timestamp |

These headers are invaluable for debugging — you can inspect them in AKHQ to understand exactly why a message failed and where it came from.

## DLQ Topic Naming Conventions

| Convention | Example | Used By |
|------------|---------|---------|
| `.DLQ` suffix | `order-events.DLQ` | Spring Kafka default |
| `-dlq` suffix | `order-events-dlq` | Common alternative |
| `-error` suffix | `order-events-error` | Some organizations |
| Centralized DLQ | `dead-letter-queue` | Single DLQ for all topics |

Per-topic DLQs (the `.DLQ` suffix pattern) are recommended because they preserve the original topic context and make monitoring easier. A single centralized DLQ becomes hard to manage as the number of source topics grows.

## Monitoring DLQ Topics

DLQ topics should be actively monitored — a message in the DLQ means something went wrong:

- **Alert on DLQ message count** — any non-zero count deserves investigation
- **Track DLQ growth rate** — a sudden spike indicates a systemic issue (bad deployment, downstream outage)
- **Build a DLQ consumer** — a dedicated service that reads DLQ messages, displays them in a dashboard, and supports manual replay
- **Set retention carefully** — DLQ messages should be retained longer than normal topics (you need time to investigate)

## Hands-On

- **[Lesson 06](../06-dead-letter-queue/LESSON.md)** demonstrates payment fraud detection with retry logic — messages flagged as fraudulent are retried, and after exhausting retries, they are routed to a DLQ for manual review
- Inspect DLQ messages in AKHQ to see the exception headers and trace back to the original failure

## Further Reading

- How consumers process messages and handle errors is covered in [Consumers](03-consumers.md)
- How offset commits interact with retry and DLQ processing is covered in [Offsets](07-offsets.md)
