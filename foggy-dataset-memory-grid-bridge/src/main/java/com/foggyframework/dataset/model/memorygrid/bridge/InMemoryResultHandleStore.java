package com.foggyframework.dataset.model.memorygrid.bridge;

import com.foggyframework.dataset.model.semantic.memorygrid.MemoryGridResultResolver;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * In-memory result handle metadata store for scoped P0.11 verification.
 */
public final class InMemoryResultHandleStore implements ResultHandleStore {

    private final Map<String, ResultHandleRecord> records = new LinkedHashMap<>();

    @Override
    public synchronized void save(ResultHandleRecord record) {
        records.put(record.result().metadata().handleId(), record);
    }

    @Override
    public synchronized Optional<ResultHandleRecord> find(String handleId) {
        return Optional.ofNullable(records.get(handleId));
    }

    @Override
    public synchronized List<ResultHandleRecord> list() {
        return new ArrayList<>(records.values());
    }

    @Override
    public synchronized void incrementReadCount(String handleId) {
        ResultHandleRecord record = records.get(handleId);
        if (record == null) {
            return;
        }
        MemoryGridResultResolver.ResultHandleMetadata metadata = record.result().metadata();
        records.put(handleId, new ResultHandleRecord(record.result().withMetadata(
                metadata.withReadCount(metadata.readCount() + 1))));
    }

    @Override
    public synchronized void invalidate(String handleId) {
        ResultHandleRecord record = records.get(handleId);
        if (record == null) {
            return;
        }
        MemoryGridResultResolver.ResultHandleMetadata metadata = record.result().metadata();
        records.put(handleId, new ResultHandleRecord(record.result().withMetadata(
                metadata.withInvalidatedAt(Instant.now()))));
    }

    @Override
    public synchronized boolean delete(String handleId) {
        return records.remove(handleId) != null;
    }
}
