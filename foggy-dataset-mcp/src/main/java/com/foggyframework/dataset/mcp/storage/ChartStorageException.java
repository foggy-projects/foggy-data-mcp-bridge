package com.foggyframework.dataset.mcp.storage;

/**
 * 图表存储异常
 */
public class ChartStorageException extends RuntimeException {

    public ChartStorageException(String message) {
        super(message);
    }

    public ChartStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
