package com.foggyframework.dataset.db.model.semantic.support;

import com.foggyframework.dataset.db.model.semantic.domain.SemanticRequestContext;

/**
 * Replays the request-side security policy before a stored Memory Grid handle is read.
 */
public interface MemoryGridAuthReplayPolicy {

    void verify(MemoryGridResultResolver.ResolvedResult result, SemanticRequestContext context);

    static MemoryGridAuthReplayPolicy strict() {
        return StrictMemoryGridAuthReplayPolicy.INSTANCE;
    }
}
