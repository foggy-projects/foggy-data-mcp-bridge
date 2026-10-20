package com.foggyframework.dataset.model.engine.expression;

import java.util.Map;

/** A planning failure with the original public payload location, before business JDBC execution. */
public final class QueryCalculationException extends IllegalArgumentException {
    private final String code;
    private final String field;
    private final String path;
    private final String suggestion;
    private final Map<String, Object> details;

    public QueryCalculationException(String code, String field, String path, String message,
                                     String suggestion, Map<String, Object> details) {
        super(code + ": " + message);
        this.code = code; this.field = field; this.path = path;
        this.suggestion = suggestion; this.details = Map.copyOf(details);
    }
    public String getCode() { return code; }
    public String getField() { return field; }
    public String getPath() { return path; }
    public String getSuggestion() { return suggestion; }
    public Map<String, Object> getDetails() { return details; }
    public static QueryCalculationException find(Throwable failure) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (current instanceof QueryCalculationException diagnostic) return diagnostic;
            if (current.getCause() == current) break;
        }
        return null;
    }
}
