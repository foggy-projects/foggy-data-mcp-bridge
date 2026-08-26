package com.foggyframework.analytics.console.api;

import com.foggyframework.analytics.console.catalog.AnalyticsConsoleCatalogException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.UUID;

@RestControllerAdvice(assignableTypes = {
        AnalyticsConsoleController.class,
        AnalyticsConsoleAgentController.class,
        AnalyticsConsoleFapPublicationController.class
})
public class AnalyticsConsoleExceptionHandler {

    @ExceptionHandler(AnalyticsConsoleCatalogException.class)
    public ResponseEntity<AnalyticsConsoleEnvelope<Void>> known(
            AnalyticsConsoleCatalogException error,
            HttpServletRequest request) {
        return ResponseEntity.status(status(error.code())).body(
                AnalyticsConsoleEnvelope.fail(
                        error.code(), error.getMessage(), requestId(request)));
    }

    @ExceptionHandler({
            IllegalArgumentException.class,
            MethodArgumentNotValidException.class,
            HttpMessageNotReadableException.class
    })
    public ResponseEntity<AnalyticsConsoleEnvelope<Void>> invalid(
            Exception ignored,
            HttpServletRequest request) {
        return ResponseEntity.badRequest().body(AnalyticsConsoleEnvelope.fail(
                "ANALYTICS_CONSOLE_REQUEST_INVALID",
                "Analytics Console request is invalid",
                requestId(request)));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<AnalyticsConsoleEnvelope<Void>> unexpected(
            Exception ignored,
            HttpServletRequest request) {
        return ResponseEntity.internalServerError().body(AnalyticsConsoleEnvelope.fail(
                "ANALYTICS_CONSOLE_INTERNAL",
                "Analytics Console operation failed",
                requestId(request)));
    }

    private static HttpStatus status(String code) {
        if (code.endsWith("_FORBIDDEN")) {
            return HttpStatus.FORBIDDEN;
        }
        if (code.endsWith("_NOT_FOUND")) {
            return HttpStatus.NOT_FOUND;
        }
        if (code.endsWith("_REVISION_CONFLICT")
                || code.endsWith("_ALREADY_PUBLISHED")
                || code.endsWith("_VALIDATION_REQUIRED")) {
            return HttpStatus.CONFLICT;
        }
        if (code.endsWith("_UNAVAILABLE")) {
            return HttpStatus.SERVICE_UNAVAILABLE;
        }
        return HttpStatus.UNPROCESSABLE_ENTITY;
    }

    private static String requestId(HttpServletRequest request) {
        String supplied = request.getHeader("X-Request-Id");
        return supplied == null || supplied.isBlank()
                ? "console-" + UUID.randomUUID()
                : supplied;
    }
}
