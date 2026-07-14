package com.foggyframework.dataset.db.model.engine.pivot;

import com.foggyframework.dataset.db.model.engine.pivot.transport.DomainTransportPlan;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds stable response-level diagnostics for Pivot execution decisions.
 */
final class PivotDiagnosticCollector {

    private final String model;
    private final List<Map<String, Object>> diagnostics = new ArrayList<>();

    PivotDiagnosticCollector(String model) {
        this.model = model;
    }

    void sqlPushdownAttempted() {
        diagnostics.add(event("pivot.sql_pushdown.attempted", "attempted"));
    }

    void sqlPushdownSucceeded(int rowCount, long durationMs) {
        Map<String, Object> item = event("pivot.sql_pushdown.succeeded", "used");
        item.put("rowCount", rowCount);
        item.put("durationMs", durationMs);
        diagnostics.add(item);
    }

    void sqlPushdownFallback(Throwable cause) {
        Map<String, Object> item = event("pivot.sql_pushdown.fallback", "fallback");
        if (cause != null) {
            item.put("reasonClass", cause.getClass().getSimpleName());
            item.put("reason", safeReason(cause));
        }
        diagnostics.add(item);
    }

    void sqlPushdownSkipped(String reason) {
        Map<String, Object> item = event("pivot.sql_pushdown.skipped", "skipped");
        item.put("reason", reason);
        diagnostics.add(item);
    }

    void axisDomainSelectionStarted(String reason) {
        Map<String, Object> item = event("pivot.axis_domain_selection.started", "started");
        item.put("reason", reason);
        diagnostics.add(item);
    }

    void domainTransportPlanned(DomainTransportPlan plan) {
        if (plan == null) {
            return;
        }
        Map<String, Object> item = event("pivot.domain_transport.planned", "planned");
        item.put("relation", plan.getRelationName());
        item.put("fieldCount", plan.getFields() == null ? 0 : plan.getFields().size());
        item.put("tupleCount", plan.getTuples() == null ? 0 : plan.getTuples().size());
        item.put("parameterCount", plan.parameterCount());
        diagnostics.add(item);
    }

    void cacheIdentity(PivotOuterCacheTelemetry.Evaluation evaluation) {
        if (evaluation == null) {
            return;
        }
        Map<String, Object> item = event("pivot.cache.identity", evaluation.identityStatus());
        item.put("identityHash", evaluation.identityHash());
        item.put("status", evaluation.identityStatus());
        item.put("bindingCount", evaluation.bindingCount());
        item.put("manualTokenPresent", evaluation.manualTokenPresent());
        item.put("supplementaryProviderFailed", evaluation.supplementaryProviderFailed());
        if (evaluation.supplementaryProviderFailed()) {
            item.put("supplementaryProviderFailureClass",
                    evaluation.supplementaryProviderFailureClass());
        }
        diagnostics.add(item);
    }

    void cacheLookup(String keyHash, String eligibilityStage, String shapeClass) {
        Map<String, Object> item = event("pivot.cache.lookup", "started");
        item.put("keyHash", keyHash);
        item.put("eligibilityStage", eligibilityStage);
        item.put("shapeClass", shapeClass);
        diagnostics.add(item);
    }

    void cacheMiss(String keyHash, String eligibilityStage, String reason, String shapeClass) {
        Map<String, Object> item = event("pivot.cache.miss", "miss");
        item.put("keyHash", keyHash);
        item.put("eligibilityStage", eligibilityStage);
        item.put("reason", reason);
        item.put("shapeClass", shapeClass);
        diagnostics.add(item);
    }

    void cacheHit(String keyHash, String eligibilityStage, long ageMs, String cacheName, String shapeClass) {
        Map<String, Object> item = event("pivot.cache.hit", "hit");
        item.put("keyHash", keyHash);
        item.put("eligibilityStage", eligibilityStage);
        item.put("ageMs", ageMs);
        item.put("cacheName", cacheName);
        item.put("shapeClass", shapeClass);
        diagnostics.add(item);
    }

    void cacheRefused(String keyHash, String eligibilityStage, String reason, String shapeClass) {
        Map<String, Object> item = event("pivot.cache.refused", "refused");
        item.put("keyHash", keyHash);
        item.put("eligibilityStage", eligibilityStage);
        item.put("reason", reason);
        item.put("shapeClass", shapeClass);
        diagnostics.add(item);
    }

    void cacheProviderUnavailable(String keyHash,
                                  String eligibilityStage,
                                  PivotOuterCacheSafeProvider.UnavailableEvent unavailable,
                                  String shapeClass) {
        if (unavailable == null) {
            return;
        }
        Map<String, Object> item = event("pivot.cache.provider_unavailable", "degraded");
        item.put("keyHash", keyHash);
        item.put("eligibilityStage", eligibilityStage);
        item.put("operation", unavailable.operation());
        item.put("providerName", unavailable.providerName());
        item.put("reasonClass", unavailable.reasonClass());
        item.put("reason", unavailable.reason());
        item.put("shapeClass", shapeClass);
        diagnostics.add(item);
    }

    void cacheStore(String keyHash, String eligibilityStage, int payloadBytes, long ttlMs, String shapeClass) {
        Map<String, Object> item = event("pivot.cache.store", "stored");
        item.put("keyHash", keyHash);
        item.put("eligibilityStage", eligibilityStage);
        item.put("payloadBytes", payloadBytes);
        item.put("ttlMs", ttlMs);
        item.put("shapeClass", shapeClass);
        diagnostics.add(item);
    }

    void cacheStoreSkipped(String keyHash, String eligibilityStage, String reason, String shapeClass) {
        Map<String, Object> item = event("pivot.cache.store_skipped", "skipped");
        item.put("keyHash", keyHash);
        item.put("eligibilityStage", eligibilityStage);
        item.put("reason", reason);
        item.put("shapeClass", shapeClass);
        diagnostics.add(item);
    }

    void cacheEvicted(String keyHash, String eligibilityStage, String reason, String shapeClass) {
        Map<String, Object> item = event("pivot.cache.evicted", "evicted");
        item.put("keyHash", keyHash);
        item.put("eligibilityStage", eligibilityStage);
        item.put("reason", reason);
        item.put("shapeClass", shapeClass);
        diagnostics.add(item);
    }

    void executionPath(boolean sqlPushdownUsed,
                       boolean axisDomainSelectionUsed,
                       boolean cascadeRequest,
                       int resultRows,
                       int rowDomainSize,
                       int columnDomainSize) {
        Map<String, Object> item = event("pivot.execution_path", "completed");
        item.put("sqlPushdownUsed", sqlPushdownUsed);
        item.put("axisDomainSelectionUsed", axisDomainSelectionUsed);
        item.put("cascadeGenerateUsed", cascadeRequest);
        item.put("memoryShapingUsed", true);
        item.put("resultRows", resultRows);
        item.put("rowDomainSize", rowDomainSize);
        item.put("columnDomainSize", columnDomainSize);
        diagnostics.add(item);
    }

    List<Map<String, Object>> snapshot() {
        List<Map<String, Object>> snapshot = new ArrayList<>();
        for (Map<String, Object> item : diagnostics) {
            snapshot.add(new LinkedHashMap<>(item));
        }
        return snapshot;
    }

    private Map<String, Object> event(String event, String decision) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("event", event);
        item.put("decision", decision);
        item.put("model", model);
        return item;
    }

    private String safeReason(Throwable cause) {
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
