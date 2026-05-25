package com.foggyframework.dataset.db.model.memorygrid.bridge;

import com.foggyframework.dataset.db.model.semantic.domain.SemanticRequestContext;
import com.foggyframework.dataset.db.model.semantic.memorygrid.MemoryGridResultResolver;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Registry-backed resolver for governed Memory Grid result handles.
 *
 * <p>This is intentionally storage-neutral. Production deployments can back the
 * registry with durable storage while tests can register bounded results
 * directly.</p>
 */
public final class MemoryGridRegistryResultResolver implements MemoryGridResultResolver {

    private final Map<String, ResolvedResult> results = new LinkedHashMap<>();

    public MemoryGridRegistryResultResolver register(ResolvedResult result) {
        if (result != null && result.resultHandle() != null && !result.resultHandle().isBlank()) {
            results.put(result.resultHandle(), result);
        }
        return this;
    }

    @Override
    public ResolvedResult resolve(String resultHandle, SemanticRequestContext context) {
        return results.get(resultHandle);
    }
}
