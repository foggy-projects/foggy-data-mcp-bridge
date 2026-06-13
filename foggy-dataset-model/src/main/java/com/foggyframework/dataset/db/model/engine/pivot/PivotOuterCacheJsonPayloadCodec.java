package com.foggyframework.dataset.db.model.engine.pivot;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;

/**
 * Default JSON payload codec for distributed Pivot outer-cache provider modules.
 */
public final class PivotOuterCacheJsonPayloadCodec implements PivotOuterCacheDistributedPayloadCodec {

    private final ObjectMapper objectMapper;

    public PivotOuterCacheJsonPayloadCodec() {
        this(new ObjectMapper());
    }

    public PivotOuterCacheJsonPayloadCodec(ObjectMapper objectMapper) {
        if (objectMapper == null) {
            throw new IllegalArgumentException("objectMapper must not be null");
        }
        this.objectMapper = objectMapper;
    }

    @Override
    public byte[] encode(PivotOuterCacheDistributedPayload payload) {
        if (payload == null) {
            throw new IllegalArgumentException("payload must not be null");
        }
        try {
            return objectMapper.writeValueAsBytes(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to encode Pivot outer-cache payload", e);
        }
    }

    @Override
    public PivotOuterCacheDistributedPayload decode(byte[] payloadBytes) {
        if (payloadBytes == null || payloadBytes.length == 0) {
            throw new IllegalArgumentException("payloadBytes must not be empty");
        }
        try {
            return objectMapper.readValue(payloadBytes, PivotOuterCacheDistributedPayload.class);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to decode Pivot outer-cache payload", e);
        }
    }
}
