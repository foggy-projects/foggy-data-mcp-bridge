package com.foggyframework.dataset.model.engine.pivot;

import org.slf4j.Logger;

/**
 * Safe structured log markers for Pivot execution telemetry.
 */
public final class PivotTelemetry {

    private PivotTelemetry() {
    }

    public static void sqlPushdownAttempted(Logger logger, String model) {
        logger.info("[Pivot] Phase 1: SQL pushdown attempted, event=pivot.sql_pushdown.attempted, model={}",
                model);
    }

    public static void sqlPushdownSucceeded(Logger logger, String model, int rowCount, long durationMs) {
        logger.info("[Pivot] Phase 1: SQL pushdown succeeded, event=pivot.sql_pushdown.succeeded, " +
                        "model={}, rows={}, durationMs={}, fallback=none",
                model, rowCount, durationMs);
    }

    public static void sqlPushdownFallback(Logger logger, String model, Throwable cause) {
        logger.info("[Pivot] Phase 1: SQL pushdown not possible, event=pivot.sql_pushdown.fallback, " +
                        "model={}, fallback=memory, reasonClass={}, reason={}",
                model, cause.getClass().getSimpleName(), safeReason(cause));
    }

    public static void sqlPushdownSkipped(Logger logger, String model, String reason) {
        logger.debug("[Pivot] Phase 1: SQL pushdown skipped, event=pivot.sql_pushdown.skipped, " +
                        "model={}, reason={}",
                model, reason);
    }

    public static void cascadeRefused(Logger logger, String model, String reason, Throwable cause) {
        if (cause == null) {
            logger.info("[Pivot] Cascade request refused, event=pivot.cascade.refused, " +
                            "model={}, reason={}",
                    model, reason);
            return;
        }
        logger.info("[Pivot] Cascade request refused, event=pivot.cascade.refused, " +
                        "model={}, reason={}, reasonClass={}, detail={}",
                model, reason, cause.getClass().getSimpleName(), safeReason(cause));
    }

    public static void domainLimitExceeded(Logger logger,
                                           String model,
                                           int domainSize,
                                           int maxAllowed,
                                           boolean sqlPushdownUsed,
                                           int rowDomainSize,
                                           int colDomainSize) {
        logger.warn("[Pivot] Non-additive rollup domain limit exceeded, " +
                        "event=pivot.non_additive.domain_limit_exceeded, domainSize={}, maxAllowed={}, " +
                        "model={}, sqlPushdownUsed={}, rowDomainSize={}, colDomainSize={}",
                domainSize, maxAllowed, model, sqlPushdownUsed, rowDomainSize, colDomainSize);
    }

    public static void auxQueryStarted(Logger logger, String model, int grainCount, int auxMetricCount) {
        logger.debug("[Pivot] Non-additive auxiliary query started, " +
                        "event=pivot.non_additive.aux_query.started, model={}, grainCount={}, auxMetricCount={}",
                model, grainCount, auxMetricCount);
    }

    public static void auxQueryCompleted(Logger logger,
                                         String model,
                                         String mode,
                                         int grainCount,
                                         int batchCount,
                                         int auxMetricCount,
                                         long durationMs) {
        logger.info("[Pivot] Non-additive auxiliary query completed, " +
                        "event=pivot.non_additive.aux_query.completed, model={}, mode={}, grainCount={}, " +
                        "batchCount={}, auxMetricCount={}, durationMs={}",
                model, mode, grainCount, batchCount, auxMetricCount, durationMs);
    }

    public static void auxQueryFallback(Logger logger, String model, Throwable cause) {
        logger.warn("[Pivot] Non-additive auxiliary query fallback, " +
                        "event=pivot.non_additive.aux_query.fallback, model={}, fallback=serial, " +
                        "reasonClass={}, reason={}",
                model, cause.getClass().getSimpleName(), safeReason(cause));
    }

    public static void domainTransportPlanned(Logger logger,
                                              String model,
                                              String relationName,
                                              int fieldCount,
                                              int tupleCount,
                                              int parameterCount) {
        logger.info("[Pivot] Large-domain transport planned, " +
                        "event=pivot.domain_transport.planned, model={}, relation={}, fieldCount={}, " +
                        "tupleCount={}, parameterCount={}",
                model, relationName, fieldCount, tupleCount, parameterCount);
    }

    public static void domainTransportApplied(Logger logger,
                                              String model,
                                              String relationName,
                                              String dialect,
                                              String placement,
                                              int fieldCount,
                                              int tupleCount,
                                              int parameterCount) {
        logger.info("[Pivot] Large-domain transport applied, " +
                        "event=pivot.domain_transport.applied, model={}, relation={}, dialect={}, " +
                        "placement={}, fieldCount={}, tupleCount={}, parameterCount={}",
                model, relationName, dialect, placement, fieldCount, tupleCount, parameterCount);
    }

    public static void domainTransportRefused(Logger logger,
                                              String model,
                                              String relationName,
                                              String dialect,
                                              Throwable cause) {
        logger.warn("[Pivot] Large-domain transport refused, " +
                        "event=pivot.domain_transport.refused, model={}, relation={}, dialect={}, " +
                        "reasonClass={}, reason={}",
                model, relationName, dialect, cause.getClass().getSimpleName(), safeReason(cause));
    }

    private static String safeReason(Throwable cause) {
        String message = cause.getMessage();
        if (message == null || message.isBlank()) {
            return cause.getClass().getSimpleName();
        }
        String normalized = message.replace('\n', ' ').replace('\r', ' ').trim();
        if (normalized.length() > 240) {
            return normalized.substring(0, 240) + "...";
        }
        return normalized;
    }
}
