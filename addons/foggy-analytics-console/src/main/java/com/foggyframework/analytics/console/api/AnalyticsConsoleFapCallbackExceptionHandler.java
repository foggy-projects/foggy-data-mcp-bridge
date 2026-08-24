package com.foggyframework.analytics.console.api;

import com.foggyframework.analytics.console.catalog.AnalyticsConsoleCatalogException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/** FAP callbacks receive only the allowlisted provider error shape. */
@RestControllerAdvice(assignableTypes = AnalyticsConsoleFapCallbackController.class)
public class AnalyticsConsoleFapCallbackExceptionHandler {

    @ExceptionHandler(AnalyticsConsoleCatalogException.class)
    public ResponseEntity<Map<String, Object>> known(AnalyticsConsoleCatalogException error) {
        int status = error.code().endsWith("_FORBIDDEN") ? 403 : 422;
        return ResponseEntity.status(status).body(Map.of(
                "code", error.code(),
                "message", error.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> unexpected(Exception ignored) {
        return ResponseEntity.internalServerError().body(Map.of(
                "code", "ANALYTICS_CONSOLE_FAP_CALLBACK_FAILED",
                "message", "Analytics Console FAP callback failed"));
    }
}
