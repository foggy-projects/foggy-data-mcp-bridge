package com.foggyframework.dataset.db.model.memorygrid.bridge;

import com.foggyframework.core.ex.RX;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticRequestContext;
import com.foggyframework.dataset.db.model.semantic.memorygrid.MemoryGridResultResolver;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Store-backed resolver for production Memory Grid handles.
 */
public final class MemoryGridStoreBackedResultResolver implements MemoryGridResultResolver {

    private final ResultHandleStore store;
    private final ResultStorageAdapter storageAdapter;
    private final MemoryGridAuthReplayPolicy authReplayPolicy;

    public MemoryGridStoreBackedResultResolver(ResultHandleStore store, ResultStorageAdapter storageAdapter) {
        this(store, storageAdapter, MemoryGridAuthReplayPolicy.strict());
    }

    public MemoryGridStoreBackedResultResolver(ResultHandleStore store,
                                               ResultStorageAdapter storageAdapter,
                                               MemoryGridAuthReplayPolicy authReplayPolicy) {
        this.store = Objects.requireNonNull(store, "store");
        this.storageAdapter = Objects.requireNonNull(storageAdapter, "storageAdapter");
        this.authReplayPolicy = Objects.requireNonNull(authReplayPolicy, "authReplayPolicy");
    }

    @Override
    public ResolvedResult resolve(String resultHandle, SemanticRequestContext context) {
        if (resultHandle == null || resultHandle.isBlank()) {
            throw RX.throwB(MemoryGridExecutor.RESULT_HANDLE_NOT_FOUND + ": result_handle is missing.");
        }
        ResultHandleRecord record = store.find(resultHandle)
                .orElseThrow(() -> RX.throwB(MemoryGridExecutor.RESULT_HANDLE_NOT_FOUND + ": " + resultHandle));
        ResolvedResult stored = record.result();
        ResultHandleMetadata metadata = stored.metadata();
        if (metadata.invalidatedAt() != null
                || (metadata.expiresAt() != null && metadata.expiresAt().isBefore(Instant.now()))) {
            throw RX.throwB(MemoryGridExecutor.RESULT_HANDLE_EXPIRED + ": " + resultHandle);
        }
        if (metadata.maxReadCount() >= 0 && metadata.readCount() >= metadata.maxReadCount()) {
            throw RX.throwB(MemoryGridExecutor.GOVERNANCE_MISMATCH
                    + ": resolver read_count exceeds max_read_count for " + resultHandle + ".");
        }
        authReplayPolicy.verify(stored, context);
        List<Map<String, Object>> rows = storageAdapter.read(metadata.storageRef());
        store.incrementReadCount(resultHandle);
        ResultHandleRecord refreshed = store.find(resultHandle).orElse(record);
        return refreshed.result().withRows(rows);
    }
}
