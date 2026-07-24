package com.foggyframework.dataset.model.engine.pivot;

import com.foggyframework.dataset.model.semantic.domain.SemanticQueryResponse;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Client-neutral adapter skeleton for distributed Pivot outer-cache providers.
 *
 * <p>Provider modules can subclass this adapter and map the protected primitive
 * operations to Redis, a KV store, or another distributed storage backend. The
 * engine keeps the key layout, payload envelope, TTL check, copy isolation, and
 * namespace/model index semantics here without taking a concrete client
 * dependency.</p>
 */
public abstract class PivotOuterCacheDistributedProviderAdapter implements PivotOuterCacheProvider {

    private final String name;
    private final boolean enabled;
    private final PivotOuterCacheDistributedProviderContract contract;
    private final PivotOuterCacheDistributedPayloadCodec codec;

    protected PivotOuterCacheDistributedProviderAdapter(String name,
                                                        boolean enabled,
                                                        PivotOuterCacheDistributedProviderContract contract,
                                                        PivotOuterCacheDistributedPayloadCodec codec) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must be non-blank");
        }
        if (contract == null) {
            throw new IllegalArgumentException("contract must not be null");
        }
        if (codec == null) {
            throw new IllegalArgumentException("codec must not be null");
        }
        this.name = name.trim();
        this.enabled = enabled;
        this.contract = contract;
        this.codec = codec;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public long ttlMillis() {
        return contract.ttlMillis();
    }

    public PivotOuterCacheDistributedProviderContract contract() {
        return contract;
    }

    @Override
    public LookupResult lookup(String keyHash, long nowMillis) {
        if (!enabled || keyHash == null || keyHash.isBlank()) {
            return LookupResult.miss();
        }
        String responseKey = contract.responseKey(keyHash);
        byte[] payloadBytes = readPayload(responseKey);
        if (payloadBytes == null || payloadBytes.length == 0) {
            return LookupResult.miss();
        }
        PivotOuterCacheDistributedPayload payload = decodeOrRemove(responseKey, payloadBytes);
        if (payload == null) {
            return LookupResult.miss();
        }
        if (payload.expired(nowMillis)) {
            removeStoredPayload(responseKey, payload);
            return LookupResult.expired(payload.ageMs(nowMillis));
        }
        return LookupResult.hit(PivotOuterResponseCache.copyResponse(payload.response()), payload.ageMs(nowMillis));
    }

    @Override
    public void store(String keyHash,
                      SemanticQueryResponse response,
                      long nowMillis,
                      String namespace,
                      String model) {
        if (!enabled || keyHash == null || keyHash.isBlank() || response == null) {
            return;
        }
        String responseKey = contract.responseKey(keyHash);
        PivotOuterCacheDistributedPayload payload = new PivotOuterCacheDistributedPayload(
                contract.payloadVersion(),
                contract.payloadContentType(),
                nowMillis,
                nowMillis + contract.ttlMillis(),
                namespace,
                model,
                PivotOuterResponseCache.copyResponse(response));
        writePayload(responseKey, codec.encode(payload), payload.expiresAtMillis());
        for (String indexKey : indexKeys(payload)) {
            addIndexMember(indexKey, responseKey, payload.expiresAtMillis());
        }
    }

    @Override
    public int evict(String namespace, String model) {
        if (!enabled) {
            return 0;
        }
        String evictionIndexKey = evictionIndexKey(namespace, model);
        Set<String> responseKeys = readIndexMembers(evictionIndexKey);
        if (responseKeys == null || responseKeys.isEmpty()) {
            return 0;
        }
        int removed = 0;
        for (String responseKey : new LinkedHashSet<>(responseKeys)) {
            byte[] payloadBytes = readPayload(responseKey);
            PivotOuterCacheDistributedPayload payload = payloadBytes == null ? null : decodeOrRemove(responseKey, payloadBytes);
            if (payload == null) {
                removeIndexMember(evictionIndexKey, responseKey);
                continue;
            }
            if (!matchesScope(payload, namespace, model)) {
                continue;
            }
            if (removeStoredPayload(responseKey, payload)) {
                removed++;
            }
        }
        return removed;
    }

    @Override
    public int estimatePayloadBytes(SemanticQueryResponse response) {
        if (response == null) {
            return 0;
        }
        PivotOuterCacheDistributedPayload payload = new PivotOuterCacheDistributedPayload(
                contract.payloadVersion(),
                contract.payloadContentType(),
                0L,
                contract.ttlMillis(),
                "",
                "",
                PivotOuterResponseCache.copyResponse(response));
        return codec.encode(payload).length;
    }

    protected abstract byte[] readPayload(String responseKey);

    protected abstract void writePayload(String responseKey, byte[] payloadBytes, long expiresAtMillis);

    protected abstract boolean deletePayload(String responseKey);

    protected abstract Set<String> readIndexMembers(String indexKey);

    protected abstract void addIndexMember(String indexKey, String responseKey, long expiresAtMillis);

    protected abstract void removeIndexMember(String indexKey, String responseKey);

    private PivotOuterCacheDistributedPayload decodeOrRemove(String responseKey, byte[] payloadBytes) {
        try {
            return codec.decode(payloadBytes);
        } catch (RuntimeException e) {
            deletePayload(responseKey);
            return null;
        }
    }

    private boolean removeStoredPayload(String responseKey, PivotOuterCacheDistributedPayload payload) {
        boolean removed = deletePayload(responseKey);
        for (String indexKey : indexKeys(payload)) {
            removeIndexMember(indexKey, responseKey);
        }
        return removed;
    }

    private List<String> indexKeys(PivotOuterCacheDistributedPayload payload) {
        if (payload.model().isBlank()) {
            return List.of(
                    contract.namespaceIndexKey(null),
                    contract.namespaceIndexKey(payload.namespace()));
        }
        return List.of(
                contract.namespaceIndexKey(null),
                contract.namespaceIndexKey(payload.namespace()),
                contract.modelIndexKey(null, payload.model()),
                contract.modelIndexKey(payload.namespace(), payload.model()));
    }

    private String evictionIndexKey(String namespace, String model) {
        boolean modelFiltered = model != null && !model.isBlank();
        if (namespace == null) {
            return modelFiltered ? contract.modelIndexKey(null, model) : contract.namespaceIndexKey(null);
        }
        return modelFiltered ? contract.modelIndexKey(namespace, model) : contract.namespaceIndexKey(namespace);
    }

    private boolean matchesScope(PivotOuterCacheDistributedPayload payload, String namespace, String model) {
        boolean namespaceFiltered = namespace != null;
        boolean modelFiltered = model != null && !model.isBlank();
        if (namespaceFiltered && !normalizeNamespace(namespace).equals(payload.namespace())) {
            return false;
        }
        return !modelFiltered || normalizeModel(model).equals(payload.model());
    }

    private String normalizeNamespace(String value) {
        return value == null || value.isBlank() ? "" : value.trim();
    }

    private String normalizeModel(String value) {
        return value == null || value.isBlank() ? "" : value.trim();
    }
}
