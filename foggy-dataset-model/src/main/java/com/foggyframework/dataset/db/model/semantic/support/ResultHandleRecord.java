package com.foggyframework.dataset.db.model.semantic.support;

/**
 * Store record tying handle metadata to an internal storage reference.
 */
public record ResultHandleRecord(MemoryGridResultResolver.ResolvedResult result) {

    public ResultHandleRecord {
        if (result == null) {
            throw new IllegalArgumentException("result is required");
        }
        if (result.metadata() == null) {
            throw new IllegalArgumentException("metadata is required");
        }
        if (result.metadata().storageRef() == null || result.metadata().storageRef().isBlank()) {
            throw new IllegalArgumentException("metadata.storageRef is required");
        }
    }
}
