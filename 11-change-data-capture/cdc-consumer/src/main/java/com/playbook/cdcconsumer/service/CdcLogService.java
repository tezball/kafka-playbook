package com.playbook.cdcconsumer.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class CdcLogService {

    private static final Logger log = LoggerFactory.getLogger(CdcLogService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @KafkaListener(topics = "dbserver1.public.products", groupId = "cdc-logger-group")
    public void handleCdcEvent(ConsumerRecord<String, String> record) {
        try {
            if (record.value() == null) {
                log.info("""

                        ============================================
                          CDC EVENT — DELETE (tombstone)
                        --------------------------------------------
                          Key: {}
                        ============================================""",
                        record.key());
                return;
            }

            JsonNode envelope = objectMapper.readTree(record.value());

            // Debezium wraps the payload; handle both wrapped and unwrapped formats
            JsonNode payload = envelope.has("payload") ? envelope.get("payload") : envelope;

            String op = payload.has("op") ? payload.get("op").asText() : "?";
            JsonNode before = payload.get("before");
            JsonNode after = payload.get("after");

            // Extract source metadata for the table name
            String table = "public.products";
            if (payload.has("source") && payload.get("source").has("table")) {
                table = "public." + payload.get("source").get("table").asText();
            }

            switch (op) {
                case "c", "r" -> logInsert(table, after);   // c = create, r = read (snapshot)
                case "u" -> logUpdate(table, before, after);
                case "d" -> logDelete(table, before);
                default -> log.info("Unknown CDC operation: {}", op);
            }

        } catch (Exception e) {
            log.error("Failed to parse CDC event: {}", e.getMessage());
            log.debug("Raw value: {}", record.value());
        }
    }

    private void logInsert(String table, JsonNode after) {
        if (after == null || after.isNull()) return;

        long id = after.get("id").asLong();
        String name = after.get("name").asText();
        String category = after.get("category").asText();
        String price = formatPrice(after.get("price"));
        boolean inStock = after.has("in_stock") ? after.get("in_stock").asBoolean(true) : true;

        log.info("""

                ============================================
                  CDC EVENT — INSERT
                --------------------------------------------
                  Table:    {}
                  ID:       {}
                  Record:   {} | {} | ${} | in_stock: {}
                ============================================""",
                table, id, name, category, price, inStock);
    }

    private void logUpdate(String table, JsonNode before, JsonNode after) {
        if (after == null || after.isNull()) return;

        long id = after.get("id").asLong();
        String name = after.get("name").asText();

        String beforeName = before != null && !before.isNull() ? before.get("name").asText() : "?";
        String beforePrice = before != null && !before.isNull() ? formatPrice(before.get("price")) : "?";
        boolean beforeStock = before != null && !before.isNull() && before.has("in_stock")
                ? before.get("in_stock").asBoolean(true) : true;

        String afterPrice = formatPrice(after.get("price"));
        boolean afterStock = after.has("in_stock") ? after.get("in_stock").asBoolean(true) : true;

        // Detect what changed
        List<String> changes = new ArrayList<>();
        if (!beforePrice.equals(afterPrice)) {
            changes.add("price (" + beforePrice + " -> " + afterPrice + ")");
        }
        if (beforeStock != afterStock) {
            changes.add("in_stock (" + beforeStock + " -> " + afterStock + ")");
        }
        if (changes.isEmpty()) {
            changes.add("updated_at");
        }

        log.info("""

                ============================================
                  CDC EVENT — UPDATE
                --------------------------------------------
                  Table:    {}
                  ID:       {}
                  Before:   {} | ${} | in_stock: {}
                  After:    {} | ${} | in_stock: {}
                  Changed:  {}
                ============================================""",
                table, id,
                beforeName, beforePrice, beforeStock,
                name, afterPrice, afterStock,
                String.join(", ", changes));
    }

    private void logDelete(String table, JsonNode before) {
        if (before == null || before.isNull()) {
            log.info("""

                    ============================================
                      CDC EVENT — DELETE
                    --------------------------------------------
                      Table:    {}
                      Details:  (before image unavailable)
                    ============================================""",
                    table);
            return;
        }

        long id = before.get("id").asLong();
        String name = before.get("name").asText();

        log.info("""

                ============================================
                  CDC EVENT — DELETE
                --------------------------------------------
                  Table:    {}
                  ID:       {}
                  Record:   {}
                ============================================""",
                table, id, name);
    }

    /**
     * Debezium encodes DECIMAL/NUMERIC as a special struct or as a plain number
     * depending on the converter configuration. Handle both cases.
     */
    private String formatPrice(JsonNode priceNode) {
        if (priceNode == null || priceNode.isNull()) return "0.00";

        // If it's an object with a "value" field (Debezium struct encoding)
        if (priceNode.isObject() && priceNode.has("value")) {
            return priceNode.get("value").asText();
        }

        // Plain number
        if (priceNode.isNumber()) {
            return String.format("%.2f", priceNode.asDouble());
        }

        // String fallback
        return priceNode.asText();
    }
}
