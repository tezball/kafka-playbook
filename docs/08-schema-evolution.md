# Schema Evolution

**Schema evolution** is the ability to change the structure of your messages over time without breaking existing producers or consumers. As your system grows, fields get added, renamed, or removed — schema evolution defines the rules for doing this safely.

## Why It Matters

In a distributed system, producers and consumers are deployed independently. A producer might add a `loyaltyPoints` field to order events on Monday, but the consumer won't be updated until Thursday. If the consumer can't handle the new field — or crashes when it's missing an old one — you have a production outage.

Schema evolution provides a contract: as long as changes follow the compatibility rules, producers and consumers can evolve independently.

## Compatibility Types

| Compatibility | Rule | Use Case |
|---------------|------|----------|
| **Backward** | New schema can read data written by old schema | Consumer upgrades first |
| **Forward** | Old schema can read data written by new schema | Producer upgrades first |
| **Full** | Both backward and forward compatible | Any deployment order |
| **None** | No compatibility checks | Development/testing only |

**Backward compatibility** is the most common default. It means new consumers can read messages from old producers.

## Safe vs Unsafe Schema Changes

| Change | Backward | Forward | Full |
|--------|----------|---------|------|
| Add optional field with default | Safe | Safe | Safe |
| Remove optional field | Safe | Unsafe | Unsafe |
| Add required field (no default) | Unsafe | Safe | Unsafe |
| Remove required field | Safe | Unsafe | Unsafe |
| Rename a field | Unsafe | Unsafe | Unsafe |
| Change field type | Unsafe | Unsafe | Unsafe |

The golden rule: **add optional fields with defaults, and never remove or rename fields in a single step**.

## The Tolerant Reader Pattern

A consumer following the **tolerant reader** pattern ignores fields it doesn't understand and uses sensible defaults for fields that are missing. This maximizes compatibility without schema enforcement:

```
Producer v2 sends:                Consumer v1 reads:
{                                 {
  "orderId": "ORD-42",             "orderId": "ORD-42",
  "total": 29.99,                  "total": 29.99
  "loyaltyPoints": 150   ◄─── ignored by v1 consumer
}                                 }
```

In Java, Jackson achieves this with `@JsonIgnoreProperties(ignoreUnknown = true)` or the global `DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES = false`.

## Schema Registry Overview

A **Schema Registry** is a centralized service that stores and validates schemas. When a producer registers a new schema version, the registry checks it against the compatibility rules before allowing it.

```
Producer                     Schema Registry                  Consumer
   │                              │                              │
   ├── register schema v2 ──────►│                              │
   │                              ├── check compatibility        │
   │◄── schema id (42) ──────────┤                              │
   │                              │                              │
   ├── send message ─────────────┼──────────────────────────────►│
   │   (schema id in header)     │                              │
   │                              │◄── fetch schema by id ───────┤
   │                              ├── return schema v2 ──────────►│
```

The registry exposes a REST API for managing schemas:
- `POST /subjects/{name}/versions` — register a new schema version
- `GET /subjects/{name}/versions/latest` — retrieve the latest schema
- `GET /schemas/ids/{id}` — retrieve a schema by its global ID
- `PUT /config/{subject}` — set compatibility level for a subject

Subject naming typically follows `{topic}-value` or `{topic}-key` conventions.

## Serialization Format Comparison

| Feature | JSON | Avro | Protobuf |
|---------|------|------|----------|
| Format | Text | Binary | Binary |
| Schema required | No | Yes (.avsc) | Yes (.proto) |
| Schema Registry | Optional | Recommended | Recommended |
| Message size | Largest | Smallest | Small |
| Human-readable | Yes | No | No |
| Language support | Universal | JVM-focused | All major languages |
| Schema evolution | Manual discipline | Enforced by registry | Enforced by registry |
| Debugging ease | Easy (plain JSON) | Harder (binary) | Harder (binary) |

JSON is best for learning, prototyping, and systems with low throughput. Avro is the Kafka ecosystem standard for high-throughput production systems. Protobuf excels in polyglot environments where non-JVM services need to read Kafka messages.

## Multi-Step Migration Strategy

When you need to make a breaking change (like removing a field or changing a type), do it in stages:

1. **Add the new field** alongside the old one (safe — both versions coexist)
2. **Migrate all consumers** to read from the new field
3. **Migrate all producers** to stop writing the old field
4. **Remove the old field** once no consumer depends on it

This "expand and contract" approach works with any serialization format and avoids the need for synchronized deployments. Each step is independently backward compatible.

```
Step 1: Add new field          Step 2: Consumers read new    Step 3: Remove old field
{ "price": 29.99,             { "price": 29.99,             { "totalCents": 2999 }
  "totalCents": 2999 }          "totalCents": 2999 }
```

## Hands-On

- **[Lesson 12](../12-schema-evolution/LESSON.md)** demonstrates multi-version producers sending messages with different schema versions alongside a tolerant consumer that handles all versions gracefully
- Experiment by adding and removing fields from the event class to see which changes break deserialization

## Further Reading

- How JSON serialization and type mapping work is covered in [JSON Serialization](06-json-serialization.md)
- The schema compatibility pitfalls table in [JSON Serialization](06-json-serialization.md) shows what happens without a registry
