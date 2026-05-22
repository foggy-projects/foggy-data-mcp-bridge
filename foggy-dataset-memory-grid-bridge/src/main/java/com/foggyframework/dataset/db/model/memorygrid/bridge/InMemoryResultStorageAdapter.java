package com.foggyframework.dataset.db.model.memorygrid.bridge;

import com.foggyframework.core.ex.RX;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * In-memory bounded row storage for tests and local deployments.
 */
public final class InMemoryResultStorageAdapter implements ResultStorageAdapter {

    private final Map<String, List<Map<String, Object>>> rowsByStorageRef = new LinkedHashMap<>();

    @Override
    public void write(String storageRef, List<Map<String, Object>> rows) {
        if (storageRef == null || storageRef.isBlank()) {
            throw RX.throwB(MemoryGridExecutor.STORAGE_UNAVAILABLE + ": storage_ref is required.");
        }
        rowsByStorageRef.put(storageRef, copyRows(rows));
    }

    @Override
    public List<Map<String, Object>> read(String storageRef) {
        if (storageRef == null || storageRef.isBlank()) {
            throw RX.throwB(MemoryGridExecutor.STORAGE_UNAVAILABLE + ": storage_ref is missing.");
        }
        List<Map<String, Object>> rows = rowsByStorageRef.get(storageRef);
        if (rows == null) {
            throw RX.throwB(MemoryGridExecutor.STORAGE_UNAVAILABLE + ": " + storageRef);
        }
        return copyRows(rows);
    }

    private static List<Map<String, Object>> copyRows(List<Map<String, Object>> rows) {
        if (rows == null) {
            return List.of();
        }
        List<Map<String, Object>> copy = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            copy.add(row == null ? Map.of() : new LinkedHashMap<>(row));
        }
        return copy;
    }
}
