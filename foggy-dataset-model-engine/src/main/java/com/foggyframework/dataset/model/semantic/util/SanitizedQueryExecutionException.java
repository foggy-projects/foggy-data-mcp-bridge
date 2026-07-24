package com.foggyframework.dataset.model.semantic.util;

/**
 * Query execution failure whose message has already been sanitized by
 * {@link QueryErrorSanitizer}.
 *
 * <p>Callers that pass {@code getMessage()} on to upstream consumers (MCP,
 * AI, end user) can rely on the message being in QM vocabulary and free of
 * physical schema identifiers.  The original cause is preserved for logs
 * and observability.</p>
 *
 * @since 8.2.1 (BUG-007 v1.3)
 */
public class SanitizedQueryExecutionException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** The sanitized message, preserved for direct access. */
    private final String sanitizedMessage;

    public SanitizedQueryExecutionException(String sanitizedMessage, Throwable cause) {
        super(sanitizedMessage, cause);
        this.sanitizedMessage = sanitizedMessage;
    }

    public String getSanitizedMessage() {
        return sanitizedMessage;
    }
}
