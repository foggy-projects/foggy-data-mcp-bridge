package com.foggyframework.dataset.db.model.memorygrid.bridge;

import java.util.List;
import java.util.Map;

/**
 * Storage adapter for bounded Memory Grid result rows.
 */
public interface ResultStorageAdapter {

    void write(String storageRef, List<Map<String, Object>> rows);

    List<Map<String, Object>> read(String storageRef);

    default boolean delete(String storageRef) {
        return false;
    }
}
