package com.foggyframework.dataset.db.model.cache.pivot;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.foggyframework.dataset.db.model.engine.pivot.PivotOuterCacheInvalidationEvent;

import java.util.Objects;

public final class RedisPivotOuterCacheInvalidationCodec {

    private final ObjectMapper objectMapper;

    public RedisPivotOuterCacheInvalidationCodec() {
        this(new ObjectMapper());
    }

    public RedisPivotOuterCacheInvalidationCodec(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    public String encode(PivotOuterCacheInvalidationEvent event) {
        PivotOuterCacheInvalidationEvent scoped =
                event == null ? PivotOuterCacheInvalidationEvent.all() : event;
        ObjectNode node = objectMapper.createObjectNode();
        putNullable(node, "namespace", scoped.namespace());
        putNullable(node, "model", scoped.model());
        putNullable(node, "eventId", scoped.eventId());
        putNullable(node, "sourceNodeId", scoped.sourceNodeId());
        node.put("issuedAtMillis", scoped.issuedAtMillis());
        try {
            return objectMapper.writeValueAsString(node);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("failed to encode Pivot outer-cache invalidation event", e);
        }
    }

    public PivotOuterCacheInvalidationEvent decode(String payload) {
        if (payload == null || payload.isBlank()) {
            throw new IllegalArgumentException("payload must not be blank");
        }
        try {
            JsonNode node = objectMapper.readTree(payload);
            if (node == null || !node.isObject()) {
                throw new IllegalArgumentException("payload must be a JSON object");
            }
            return new PivotOuterCacheInvalidationEvent(
                    namespaceOrNull(node),
                    textOrNull(node, "model"),
                    textOrNull(node, "eventId"),
                    textOrNull(node, "sourceNodeId"),
                    longOrZero(node, "issuedAtMillis"));
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("failed to decode Pivot outer-cache invalidation event", e);
        }
    }

    private void putNullable(ObjectNode node, String field, String value) {
        if (value == null) {
            node.putNull(field);
        } else {
            node.put(field, value);
        }
    }

    private String textOrNull(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        String text = value.asText();
        return text == null || text.isBlank() ? null : text;
    }

    private String namespaceOrNull(JsonNode node) {
        JsonNode value = node.get("namespace");
        if (value == null || value.isNull()) {
            return null;
        }
        return value.asText();
    }

    private long longOrZero(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return 0L;
        }
        return value.asLong();
    }
}
