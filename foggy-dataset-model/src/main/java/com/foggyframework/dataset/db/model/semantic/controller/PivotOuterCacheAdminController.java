package com.foggyframework.dataset.db.model.semantic.controller;

import com.foggyframework.dataset.db.model.engine.pivot.PivotOuterCacheInvalidationBroadcaster;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
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
        int removed = pivotOuterCacheInvalidationBroadcaster.evict(namespace, model);
        log.info("Pivot outer-cache evicted: namespace={}, model={}, removed={}", namespace, model, removed);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("namespace", namespace);
        response.put("model", model);
        response.put("removed", removed);
        response.put("scope", scope(namespace, model));
        return ResponseEntity.ok(response);
    }

    private String scope(String namespace, String model) {
        boolean allNamespaces = namespace == null;
        boolean allModels = model == null || model.isBlank();
        if (allNamespaces && allModels) {
            return "all-namespaces/all-models";
        }
        if (allNamespaces) {
            return "all-namespaces/model";
        }
        if (allModels) {
            return "namespace/all-models";
        }
        return "namespace/model";
    }
}
