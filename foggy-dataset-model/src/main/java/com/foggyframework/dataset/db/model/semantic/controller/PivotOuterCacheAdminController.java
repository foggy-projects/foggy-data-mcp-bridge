package com.foggyframework.dataset.db.model.semantic.controller;

import com.foggyframework.dataset.db.model.engine.pivot.PivotOuterCacheInvalidationBroadcaster;
import com.foggyframework.dataset.db.model.engine.pivot.PivotOuterCacheInvalidationEvent;
import com.foggyframework.dataset.db.model.engine.pivot.PivotOuterCacheInvalidationResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Internal operational endpoint for Pivot outer-cache invalidation.
 */
@Slf4j
@RestController
@RequestMapping("/semantic/v3/admin/pivot-outer-cache")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "foggy.dataset.pivot.outer-cache",
        name = "admin-endpoint-enabled",
        havingValue = "true")
public class PivotOuterCacheAdminController {

    private final PivotOuterCacheInvalidationBroadcaster pivotOuterCacheInvalidationBroadcaster;

    @DeleteMapping("/evict")
    public ResponseEntity<Map<String, Object>> evict(
            @RequestParam(value = "namespace", required = false) String namespace,
            @RequestParam(value = "model", required = false) String model) {
        PivotOuterCacheInvalidationEvent event = PivotOuterCacheInvalidationEvent.of(namespace, model);
        PivotOuterCacheInvalidationResult result;
        try {
            result = pivotOuterCacheInvalidationBroadcaster.evict(event);
        } catch (Exception e) {
            log.warn("Pivot outer-cache eviction failed: namespace={}, model={}, error={}",
                    event.namespace(), event.model(), e.getMessage(), e);
            result = new PivotOuterCacheInvalidationResult(
                    0, 1, 0, 1, List.of("broadcaster failed: " + safeError(e)));
        }
        log.info("Pivot outer-cache evicted: namespace={}, model={}, removed={}, attemptedNodes={}, failedNodes={}",
                event.namespace(), event.model(), result.removed(), result.attemptedNodes(), result.failedNodes());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", result.success());
        response.put("namespace", event.namespace());
        response.put("model", event.model());
        response.put("removed", result.removed());
        response.put("attemptedNodes", result.attemptedNodes());
        response.put("succeededNodes", result.succeededNodes());
        response.put("failedNodes", result.failedNodes());
        response.put("errors", result.errors());
        response.put("scope", event.scope());
        return ResponseEntity.ok(response);
    }

    private String safeError(Exception e) {
        if (e == null) {
            return "unknown";
        }
        String message = e.getMessage();
        return message == null || message.isBlank() ? e.getClass().getSimpleName() : message;
    }
}
