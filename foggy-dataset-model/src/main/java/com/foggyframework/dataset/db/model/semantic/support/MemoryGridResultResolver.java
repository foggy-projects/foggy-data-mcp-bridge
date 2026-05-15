package com.foggyframework.dataset.db.model.semantic.support;

import com.foggyframework.dataset.db.model.semantic.domain.SemanticRequestContext;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Resolves governed result handles into bounded rows for Memory Grid execution.
 *
 * <p>Rows must come from a prior governed engine result. LLM requests are not
 * allowed to provide rows directly.</p>
 */
public interface MemoryGridResultResolver {

    ResolvedResult resolve(String resultHandle, SemanticRequestContext context);

    record ResolvedResult(String resultHandle,
                          String sourceRoute,
                          String namespace,
                          List<String> grain,
                          Map<String, Column> schema,
                          List<Map<String, Object>> rows,
                          Map<String, Object> lineage,
                          ResultHandleMetadata metadata) {
        public ResolvedResult(String resultHandle,
                              String sourceRoute,
                              String namespace,
                              List<String> grain,
                              Map<String, Column> schema,
                              List<Map<String, Object>> rows,
                              Map<String, Object> lineage) {
            this(resultHandle, sourceRoute, namespace, grain, schema, rows, lineage, null);
        }
    }

    record Column(String name,
                  String type,
                  boolean joinAllowed,
                  boolean derivedAllowed,
                  boolean outputAllowed,
                  boolean sensitive) {
        public Column(String name,
                      String type,
                      boolean joinAllowed,
                      boolean derivedAllowed,
                      boolean outputAllowed) {
            this(name, type, joinAllowed, derivedAllowed, outputAllowed, false);
        }
    }

    record ResultHandleMetadata(String handleId,
                                String namespace,
                                String sourceRoute,
                                List<String> sourceModelRefs,
                                String queryHash,
                                Instant createdAt,
                                Instant expiresAt,
                                int rowCount,
                                int rowLimit,
                                Map<String, Object> lineage,
                                String storageRef) {
    }
}
