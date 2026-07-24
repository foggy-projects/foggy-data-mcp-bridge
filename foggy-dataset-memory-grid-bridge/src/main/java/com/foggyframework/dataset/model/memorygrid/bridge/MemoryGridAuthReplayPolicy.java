package com.foggyframework.dataset.model.memorygrid.bridge;

import com.foggyframework.dataset.model.semantic.domain.SemanticRequestContext;
import com.foggyframework.dataset.model.semantic.memorygrid.MemoryGridResultResolver;

/**
 * Replays the request-side security policy before a stored Memory Grid handle is read.
 */
public interface MemoryGridAuthReplayPolicy {

    void verify(MemoryGridResultResolver.ResolvedResult result, SemanticRequestContext context);

    static MemoryGridAuthReplayPolicy strict() {
        return StrictMemoryGridAuthReplayPolicy.INSTANCE;
    }
}
