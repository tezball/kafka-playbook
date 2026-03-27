# Lesson 01 — Simple Pub/Sub

## Scenario

An e-commerce platform needs to send email confirmations when orders are placed. The **order service** publishes an event to Kafka whenever a new order is created. The **notification service** consumes those events and sends email confirmations.

```mermaid
flowchart LR
    A[REST API] -->|POST /api/orders| B[Order Producer]
    B -->|OrderEvent| C[Kafka Topic\norder-events]
    C -->|consume| D[Email Notification\nConsumer]
    D -->|log| E["📧 Email Sent"]
```

## Kafka Concepts Covered

- [Topics](../docs/01-topics.md) — a named stream of records (`order-events`)
- [Producers](../docs/02-producers.md) — applications that write messages to a topic
- [Consumers](../docs/03-consumers.md) — applications that read messages from a topic
- [Consumer Groups](../docs/04-consumer-groups.md) — consumers that share the work of reading a topic (`email-notification-group`)
- [Message Keys](../docs/05-message-keys.md) — the `orderId` is used as the key, ensuring all events for the same order land on the same partition
- [JSON Serialization](../docs/06-json-serialization.md) — Spring Kafka's `JsonSerializer`/`JsonDeserializer` for structured messages
- [Offsets](../docs/07-offsets.md) — Kafka tracks where each consumer group has read up to

## Architecture

| Service | Port | Role |
|---------|------|------|
| Kafka (KRaft) | 9092 | Message broker |
| Order Producer | 8080 | REST API + Kafka producer |
| Email Consumer | — | Kafka consumer, logs output |
| AKHQ | 8888 | Web UI — topic browser, live messages, consumer group lag |

## Running

```bash
./start.sh
```

This will build both Spring Boot apps inside Docker (first run downloads Maven dependencies — takes a few minutes), start Kafka in KRaft mode, launch AKHQ, and begin auto-generating orders every 10 seconds. Chrome opens automatically to the AKHQ live message view.

## Exploring

### AKHQ — Visual Kafka Dashboard

AKHQ opens automatically at [localhost:8888](http://localhost:8888). Key views:

| View | URL | What to observe |
|------|-----|-----------------|
| **Live Messages** | [order-events/data](http://localhost:8888/ui/kafka-playbook/topic/order-events/data?sort=NEWEST&partition=All) | Watch OrderEvent JSON payloads arrive every 10 seconds |
| **Topic Detail** | [order-events](http://localhost:8888/ui/kafka-playbook/topic/order-events) | Partition count, replication, message count, size |
| **Consumer Groups** | [groups](http://localhost:8888/ui/kafka-playbook/group) | See `email-notification-group` offset lag per partition |
| **All Topics** | [topics](http://localhost:8888/ui/kafka-playbook/topic) | Internal topics (`__consumer_offsets`) + your `order-events` |

Things to try in AKHQ:
- Click a message row to expand the full JSON payload, headers, key, and partition/offset
- Filter messages by key (e.g., `ORD-1001`) to see all events for one order
- Watch the consumer group lag — it should stay near 0 as the consumer keeps up
- Stop the consumer (`docker compose stop consumer`) and watch lag increase, then restart it (`docker compose start consumer`) and watch it catch up

### Watch the consumer process orders

```bash
docker compose logs -f consumer
```

You should see output like:

```
============================================
  EMAIL NOTIFICATION
--------------------------------------------
  To:      alice@example.com
  Order:   ORD-1001
  Product: Wireless Headphones (x2)
  Total:   $79.98
  Status:  CONFIRMED
============================================
```

### Send a custom order

```bash
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{
    "customerEmail": "you@example.com",
    "productName": "Mechanical Keyboard",
    "quantity": 1,
    "totalPrice": 149.99
  }'
```

### Send a random sample order

```bash
curl -X POST http://localhost:8080/api/orders/sample
```

### Inspect the topic

```bash
docker compose exec kafka /opt/kafka/bin/kafka-topics.sh \
  --bootstrap-server localhost:9092 --describe --topic order-events
```

### Read raw messages from the topic

```bash
docker compose exec kafka /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 --topic order-events --from-beginning
```

## Testing

Both the producer and consumer projects include end-to-end tests using **Testcontainers** and **Awaitility**.

### Running tests

From within Docker:
```bash
docker compose exec producer sh -c "cd /app && mvn test"
docker compose exec consumer sh -c "cd /app && mvn test"
```

Or locally (requires Docker running for Testcontainers):
```bash
cd producer && mvn test
cd consumer && mvn test
```

### Approach

Tests use **Testcontainers** to spin up a real Kafka broker (Confluent CP Kafka 7.6.1) in a Docker container. This provides a more realistic test environment than Spring's `EmbeddedKafka`, because it runs the actual Kafka binary with real networking.

**BDD naming convention** — each test uses `@DisplayName` with Given/When/Then to describe the scenario:
```java
@Test
@DisplayName("Given an order event is published to the order-events topic, " +
        "when the email notification consumer processes the message, " +
        "then the order details are logged as a formatted email notification")
void givenOrderEventPublished_whenConsumerProcesses_thenEmailNotificationLogged() {
```

### Test files

| Project | Test class | Scenarios |
|---------|-----------|-----------|
| consumer | `OrderNotificationFlowTest` | Verifies the consumer receives and processes order events; verifies ordering for multiple rapid publishes |
| producer | `OrderProducerFlowTest` | Verifies the producer sends messages with correct key and JSON payload by reading them back with a raw `KafkaConsumer` |

### Best practices for Kafka testing

- **Async assertions with Awaitility** — Kafka consumers are asynchronous, so tests use `await().atMost(...)` instead of `Thread.sleep()` to wait for messages to be processed
- **Testcontainers vs EmbeddedKafka** — Testcontainers runs a real Kafka broker, catching serialization and configuration issues that EmbeddedKafka might miss. The tradeoff is slightly slower startup (~5s)
- **Test serialization separately** — in production, consider unit tests for your serializer/deserializer configurations independent of the full Kafka flow
- **Use `@SpyBean`** — the consumer test uses `@SpyBean` on the service to capture method invocations without changing the service code

## Key Takeaways

1. **Decoupling** — the producer doesn't know or care who consumes its events. You could add a second consumer (analytics, audit) without changing the producer.
2. **Message keys** — using `orderId` as the key means Kafka guarantees ordering for events about the same order within a partition.
3. **Consumer groups** — the `email-notification-group` tracks its own offset. If you restart the consumer, it picks up where it left off.
4. **Schema duplication** — both apps define their own `OrderEvent` record. In production, you'd use a Schema Registry or shared library. Lesson 12 covers this.

## Teardown

```bash
docker compose down -v
```
