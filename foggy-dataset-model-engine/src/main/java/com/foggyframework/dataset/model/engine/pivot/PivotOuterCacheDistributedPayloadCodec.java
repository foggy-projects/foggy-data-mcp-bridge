package com.foggyframework.dataset.model.engine.pivot;

/**
 * Payload codec boundary for distributed Pivot outer-cache providers.
 */
public interface PivotOuterCacheDistributedPayloadCodec {

    byte[] encode(PivotOuterCacheDistributedPayload payload);

    PivotOuterCacheDistributedPayload decode(byte[] payloadBytes);
}
