package com.foggyframework.dataset.model.semantic.memorygrid;

import com.foggyframework.dataset.model.semantic.domain.SemanticRequestContext;

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

        public ResolvedResult withRows(List<Map<String, Object>> rows) {
            return new ResolvedResult(resultHandle, sourceRoute, namespace, grain, schema, rows, lineage, metadata);
        }

        public ResolvedResult withMetadata(ResultHandleMetadata metadata) {
            return new ResolvedResult(resultHandle, sourceRoute, namespace, grain, schema, rows, lineage, metadata);
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
                                String ownerContextHash,
                                String sourceRoute,
                                List<String> sourceModelRefs,
                                String queryHash,
                                Instant createdAt,
                                Instant expiresAt,
                                Instant invalidatedAt,
                                int rowCount,
                                int rowLimit,
                                int cellCount,
                                long byteSize,
                                Map<String, Object> lineage,
                                String storageRef,
                                int readCount,
                                int maxReadCount,
                                PolicySnapshot policySnapshot) {
        public ResultHandleMetadata(String handleId,
                                    String namespace,
                                    String ownerContextHash,
                                    String sourceRoute,
                                    List<String> sourceModelRefs,
                                    String queryHash,
                                    Instant createdAt,
                                    Instant expiresAt,
                                    Instant invalidatedAt,
                                    int rowCount,
                                    int rowLimit,
                                    int cellCount,
                                    long byteSize,
                                    Map<String, Object> lineage,
                                    String storageRef,
                                    int readCount,
                                    int maxReadCount) {
            this(handleId, namespace, ownerContextHash, sourceRoute, sourceModelRefs, queryHash, createdAt,
                    expiresAt, invalidatedAt, rowCount, rowLimit, cellCount, byteSize, lineage, storageRef,
                    readCount, maxReadCount, null);
        }

        public ResultHandleMetadata(String handleId,
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
            this(handleId, namespace, null, sourceRoute, sourceModelRefs, queryHash, createdAt, expiresAt,
                    null, rowCount, rowLimit, -1, -1L, lineage, storageRef, 0, -1, null);
        }

        public ResultHandleMetadata withReadCount(int readCount) {
            return new ResultHandleMetadata(handleId, namespace, ownerContextHash, sourceRoute, sourceModelRefs,
                    queryHash, createdAt, expiresAt, invalidatedAt, rowCount, rowLimit, cellCount, byteSize,
                    lineage, storageRef, readCount, maxReadCount, policySnapshot);
        }

        public ResultHandleMetadata withExpiresAt(Instant expiresAt) {
            return new ResultHandleMetadata(handleId, namespace, ownerContextHash, sourceRoute, sourceModelRefs,
                    queryHash, createdAt, expiresAt, invalidatedAt, rowCount, rowLimit, cellCount, byteSize,
                    lineage, storageRef, readCount, maxReadCount, policySnapshot);
        }

        public ResultHandleMetadata withInvalidatedAt(Instant invalidatedAt) {
            return new ResultHandleMetadata(handleId, namespace, ownerContextHash, sourceRoute, sourceModelRefs,
                    queryHash, createdAt, expiresAt, invalidatedAt, rowCount, rowLimit, cellCount, byteSize,
                    lineage, storageRef, readCount, maxReadCount, policySnapshot);
        }
    }

    record PolicySnapshot(String ownerContextHash,
                          String fieldAccessHash,
                          String schemaHash,
                          String policyVersion,
                          String schemaVersion) {
    }
}
