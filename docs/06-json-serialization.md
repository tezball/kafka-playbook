# JSON Serialization

Kafka stores messages as **raw bytes**. Serializers convert your objects to bytes on the producer side, and deserializers convert bytes back to objects on the consumer side.

## The Serialization Pipeline

```
Producer side:                          Consumer side:

Java Object                             byte[]
     │                                       │
     ▼                                       ▼
JsonSerializer                          JsonDeserializer
     │                                       │
     ▼                                       ▼
byte[] ──────► Kafka Broker ──────► Java Object
```

## Serialization Formats

| Format | Pros | Cons |
|--------|------|------|
| **JSON** | Human-readable, easy to debug, no schema registry needed | Larger payloads, no built-in schema enforcement |
| **Avro** | Compact binary, schema registry, backward/forward compatibility | Requires Schema Registry, harder to debug |
| **Protobuf** | Compact binary, strong typing, language-neutral | Requires .proto files, Schema Registry recommended |
| **String** | Simplest possible | No structure, manual parsing |

JSON is the best choice for learning and prototyping. Avro and Protobuf are preferred in production for performance and schema enforcement (covered in [Lesson 12](../12-schema-evolution/LESSON.md)).

## Spring Kafka JSON Configuration

### Producer Side

```yaml
spring.kafka.producer:
  key-serializer: org.apache.kafka.common.serialization.StringSerializer
  value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
  properties:
    spring.json.type.mapping: orderEvent:com.playbook.producer.model.OrderEvent
```

- **key-serializer** — order IDs are strings, so `StringSerializer`
- **value-serializer** — `JsonSerializer` converts Java objects to JSON bytes using Jackson
- **type.mapping** — maps the alias `orderEvent` to the Java class, stored in a message header

### Consumer Side

```yaml
spring.kafka.consumer:
  key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
  value-deserializer: org.springframework.kafka.support.serializer.JsonDeserializer
  properties:
    spring.json.trusted-packages: "com.playbook.*"
    spring.json.type.mapping: orderEvent:com.playbook.consumer.model.OrderEvent
```

- **value-deserializer** — `JsonDeserializer` converts JSON bytes back to Java objects
- **trusted-packages** — security measure; only deserialize classes from these packages
- **type.mapping** — maps the `orderEvent` alias to the consumer's own `OrderEvent` class

## How Type Mapping Works

The producer embeds a **type header** in each Kafka message:

```
Headers:
  __TypeId__ = "orderEvent"
```

The consumer reads this header, looks up `orderEvent` in its type mapping, and deserializes to the corresponding class. This decouples the producer and consumer — they don't need to share the same Java package names.

## What the JSON Looks Like

In AKHQ or a console consumer, you can inspect the raw JSON value:

```json
{
  "orderId": "ORD-1001",
  "customerEmail": "alice@gmail.com",
  "productName": "Wireless Headphones",
  "quantity": 2,
  "totalPrice": 79.98,
  "createdAt": "2024-03-27T12:00:00Z"
}
```

## Schema Compatibility Pitfalls

Since JSON has no enforced schema, producer and consumer can drift:

| Change | Effect |
|--------|--------|
| Producer adds a new field | Consumer ignores it (safe with Jackson defaults) |
| Producer removes a field | Consumer gets `null` for that field (may cause NPE) |
| Producer renames a field | Consumer sees `null` for old name (breaking) |
| Producer changes field type | Consumer deserialization fails (breaking) |

This is why production systems use a Schema Registry with Avro or Protobuf — it catches incompatible changes before deployment.

## Hands-On

- **[Lesson 01](../01-simple-pub-sub/LESSON.md)** uses JSON serialization with type mapping — browse messages in AKHQ to see JSON payloads and `__TypeId__` headers
- The producer and consumer each define their own `OrderEvent` record and agree on a type alias — this works for self-contained apps, but in multi-team systems a Schema Registry or shared library is essential

## Further Reading

- Schema evolution and the Schema Registry are covered in [Lesson 12](../12-schema-evolution/LESSON.md) and [Schema Evolution](08-schema-evolution.md)
- Message keys and their serialization are covered in [Message Keys](05-message-keys.md)
