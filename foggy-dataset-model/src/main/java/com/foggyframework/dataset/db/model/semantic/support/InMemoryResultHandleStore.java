package com.foggyframework.dataset.db.model.semantic.support;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * In-memory result handle metadata store for scoped P0.11 verification.
 */
public final class InMemoryResultHandleStore implements ResultHandleStore {

    private final Map<String, ResultHandleRecord> records = new LinkedHashMap<>();

    @Override
    public void save(ResultHandleRecord record) {
        records.put(record.result().metadata().handleId(), record);
    }

    @Override
    public Optional<ResultHandleRecord> find(String handleId) {
        return Optional.ofNullable(records.get(handleId));
    }

    @Override
    public void incrementReadCount(String handleId) {
        ResultHandleRecord record = records.get(handleId);
        if (record == null) {
            return;
        }
        MemoryGridResultResolver.ResultHandleMetadata metadata = record.result().metadata();
        records.put(handleId, new ResultHandleRecord(record.result().withMetadata(
                metadata.withReadCount(metadata.readCount() + 1))));
    }

    @Override
    public void invalidate(String handleId) {
        ResultHandleRecord record = records.get(handleId);
        if (record == null) {
            return;
        }
        MemoryGridResultResolver.ResultHandleMetadata metadata = record.result().metadata();
        records.put(handleId, new ResultHandleRecord(record.result().withMetadata(
                metadata.withInvalidatedAt(Instant.now()))));
    }
}
