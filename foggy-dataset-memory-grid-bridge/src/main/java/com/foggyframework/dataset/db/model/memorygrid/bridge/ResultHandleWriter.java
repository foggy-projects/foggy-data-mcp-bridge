package com.foggyframework.dataset.db.model.memorygrid.bridge;

import com.foggyframework.core.ex.RX;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticRequestContext;
import com.foggyframework.dataset.db.model.semantic.memorygrid.MemoryGridResultResolver;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Writes governed bounded results and returns an opaque system-generated handle.
 */
public final class ResultHandleWriter {

    private final ResultHandleStore store;
    private final ResultStorageAdapter storageAdapter;
    private final Clock clock;
    private final Supplier<String> handleSupplier;

    public ResultHandleWriter(ResultHandleStore store, ResultStorageAdapter storageAdapter) {
        this(store, storageAdapter, Clock.systemUTC(), () -> "mgr_" + UUID.randomUUID());
    }

    ResultHandleWriter(ResultHandleStore store,
                       ResultStorageAdapter storageAdapter,
                       Clock clock,
                       Supplier<String> handleSupplier) {
        this.store = Objects.requireNonNull(store, "store");
        this.storageAdapter = Objects.requireNonNull(storageAdapter, "storageAdapter");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.handleSupplier = Objects.requireNonNull(handleSupplier, "handleSupplier");
    }

    public String write(WriteRequest request, SemanticRequestContext context) {
        if (request == null) {
            throw RX.throwB(MemoryGridExecutor.GOVERNANCE_MISMATCH + ": result handle write request is required.");
        }
        if (request.sourceRoute() == null || request.sourceRoute().isBlank()) {
            throw RX.throwB(MemoryGridExecutor.GOVERNANCE_MISMATCH + ": source_route is required.");
        }
        if (request.schema() == null || request.schema().isEmpty()) {
            throw RX.throwB(MemoryGridExecutor.SCHEMA_MISMATCH + ": schema is required.");
        }
        if (request.rows() == null) {
            throw RX.throwB(MemoryGridExecutor.GOVERNANCE_MISMATCH + ": rows are required.");
        }
        if (request.rowLimit() <= 0 || request.rows().size() > request.rowLimit()) {
            throw RX.throwB(MemoryGridExecutor.GOVERNANCE_MISMATCH + ": rows exceed row_limit.");
        }
        int cellCount = cellCount(request.rows());
        if (request.cellLimit() > 0 && cellCount > request.cellLimit()) {
            throw RX.throwB(MemoryGridExecutor.GOVERNANCE_MISMATCH + ": rows exceed cell_limit.");
        }
        if (request.ttl() == null || request.ttl().isNegative() || request.ttl().isZero()) {
            throw RX.throwB(MemoryGridExecutor.GOVERNANCE_MISMATCH + ": positive ttl is required.");
        }

        String handle = nextHandle();
        Instant createdAt = Instant.now(clock);
        String storageRef = "memory-grid://result/" + handle;
        storageAdapter.write(storageRef, request.rows());
        MemoryGridResultResolver.ResultHandleMetadata metadata =
                new MemoryGridResultResolver.ResultHandleMetadata(
                        handle,
                        namespace(context),
                        MemoryGridPolicySupport.ownerContextHash(context),
                        request.sourceRoute(),
                        request.sourceModelRefs() == null ? List.of() : List.copyOf(request.sourceModelRefs()),
                        request.queryHash(),
                        createdAt,
                        createdAt.plus(request.ttl()),
                        null,
                        request.rows().size(),
                        request.rowLimit(),
                        cellCount,
                        byteSize(request.rows()),
                        request.lineage() == null ? Map.of() : new LinkedHashMap<>(request.lineage()),
                        storageRef,
                        0,
                        request.maxReadCount(),
                        new MemoryGridResultResolver.PolicySnapshot(
                                MemoryGridPolicySupport.ownerContextHash(context),
                                MemoryGridPolicySupport.fieldAccessHash(context),
                                MemoryGridPolicySupport.schemaHash(request.schema()),
                                request.policyVersion(),
                                request.schemaVersion()
                        )
                );
        MemoryGridResultResolver.ResolvedResult result = new MemoryGridResultResolver.ResolvedResult(
                handle,
                request.sourceRoute(),
                namespace(context),
                request.grain() == null ? List.of() : List.copyOf(request.grain()),
                Map.copyOf(request.schema()),
                List.of(),
                request.lineage() == null ? Map.of() : new LinkedHashMap<>(request.lineage()),
                metadata
        );
        store.save(new ResultHandleRecord(result));
        return handle;
    }

    private String nextHandle() {
        String handle = handleSupplier.get();
        if (handle == null || handle.isBlank()) {
            throw RX.throwB(MemoryGridExecutor.GOVERNANCE_MISMATCH + ": generated result_handle is blank.");
        }
        return handle;
    }

    private static String namespace(SemanticRequestContext context) {
        return context == null ? null : context.getNamespace();
    }

    private static int cellCount(List<Map<String, Object>> rows) {
        int count = 0;
        for (Map<String, Object> row : rows) {
            count += row == null ? 0 : row.size();
        }
        return count;
    }

    private static long byteSize(List<Map<String, Object>> rows) {
        return rows.toString().getBytes(StandardCharsets.UTF_8).length;
    }

    public record WriteRequest(String sourceRoute,
                               List<String> sourceModelRefs,
                               String queryHash,
                               List<String> grain,
                               Map<String, MemoryGridResultResolver.Column> schema,
                               List<Map<String, Object>> rows,
                               Map<String, Object> lineage,
                               int rowLimit,
                               int cellLimit,
                               Duration ttl,
                               int maxReadCount,
                               String policyVersion,
                               String schemaVersion) {
        public WriteRequest(String sourceRoute,
                            List<String> sourceModelRefs,
                            String queryHash,
                            List<String> grain,
                            Map<String, MemoryGridResultResolver.Column> schema,
                            List<Map<String, Object>> rows,
                            Map<String, Object> lineage,
                            int rowLimit,
                            int cellLimit,
                            Duration ttl,
                            int maxReadCount) {
            this(sourceRoute, sourceModelRefs, queryHash, grain, schema, rows, lineage, rowLimit, cellLimit,
                    ttl, maxReadCount, null, null);
        }
    }
}
