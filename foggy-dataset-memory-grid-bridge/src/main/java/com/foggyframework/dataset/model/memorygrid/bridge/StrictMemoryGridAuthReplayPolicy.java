package com.foggyframework.dataset.model.memorygrid.bridge;

import com.foggyframework.core.ex.RX;
import com.foggyframework.dataset.model.semantic.domain.SemanticRequestContext;
import com.foggyframework.dataset.model.semantic.memorygrid.MemoryGridResultResolver;

import java.util.Set;

final class StrictMemoryGridAuthReplayPolicy implements MemoryGridAuthReplayPolicy {

    static final StrictMemoryGridAuthReplayPolicy INSTANCE = new StrictMemoryGridAuthReplayPolicy();

    private StrictMemoryGridAuthReplayPolicy() {
    }

    @Override
    public void verify(MemoryGridResultResolver.ResolvedResult result, SemanticRequestContext context) {
        MemoryGridResultResolver.ResultHandleMetadata metadata = result.metadata();
        if (metadata == null || metadata.policySnapshot() == null) {
            return;
        }
        MemoryGridResultResolver.PolicySnapshot snapshot = metadata.policySnapshot();
        String handle = result.resultHandle();
        if (snapshot.ownerContextHash() != null
                && !snapshot.ownerContextHash().equals(MemoryGridPolicySupport.ownerContextHash(context))) {
            throw RX.throwB(MemoryGridExecutor.AUTH_REPLAY_MISMATCH
                    + ": owner context changed for " + handle + ".");
        }
        if (snapshot.fieldAccessHash() != null
                && !snapshot.fieldAccessHash().equals(MemoryGridPolicySupport.fieldAccessHash(context))) {
            throw RX.throwB(MemoryGridExecutor.AUTH_REPLAY_MISMATCH
                    + ": field access policy changed for " + handle + ".");
        }
        String currentSchemaHash = MemoryGridPolicySupport.schemaHash(result.schema());
        if (snapshot.schemaHash() != null && !snapshot.schemaHash().equals(currentSchemaHash)) {
            throw RX.throwB(MemoryGridExecutor.SCHEMA_DRIFT
                    + ": schema snapshot changed for " + handle + ".");
        }
        Set<String> fieldAccess = context == null ? null : context.getFieldAccess();
        if (fieldAccess == null) {
            return;
        }
        result.schema().values().stream()
                .filter(column -> column.joinAllowed() || column.derivedAllowed() || column.outputAllowed())
                .filter(column -> !fieldAccess.contains(column.name()))
                .findFirst()
                .ifPresent(column -> {
                    throw RX.throwB(MemoryGridExecutor.AUTH_REPLAY_MISMATCH
                            + ": field access narrowed for " + handle + ": " + column.name());
                });
    }
}
