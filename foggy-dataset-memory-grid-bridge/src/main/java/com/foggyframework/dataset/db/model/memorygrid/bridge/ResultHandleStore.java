package com.foggyframework.dataset.db.model.memorygrid.bridge;

import java.util.Optional;

/**
 * Metadata store for system-generated Memory Grid result handles.
 */
public interface ResultHandleStore {

    void save(ResultHandleRecord record);

    Optional<ResultHandleRecord> find(String handleId);

    void incrementReadCount(String handleId);

    void invalidate(String handleId);
}
