package com.foggyframework.analytics.console.catalog;

/** Safe product error exposed by the Console API handler. */
public final class AnalyticsConsoleCatalogException extends RuntimeException {

    private final String code;

    public AnalyticsConsoleCatalogException(String code, String message) {
        super(message);
        this.code = code;
    }

    public AnalyticsConsoleCatalogException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
