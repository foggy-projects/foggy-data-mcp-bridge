package com.foggyframework.dataset.model.memorygrid.bridge;

import com.foggyframework.dataset.model.semantic.memorygrid.MemoryGridResultResolver;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Builds external-safe resolver audit entries.
 */
public interface MemoryGridAuditExposurePolicy {

    Map<String, Object> expose(MemoryGridResultResolver.ResultHandleMetadata metadata,
                               MemoryGridResultResolver.ResolvedResult result);

    static MemoryGridAuditExposurePolicy externalSafe() {
        return ExternalSafeMemoryGridAuditExposurePolicy.INSTANCE;
    }

    final class ExternalSafeMemoryGridAuditExposurePolicy implements MemoryGridAuditExposurePolicy {
        private static final ExternalSafeMemoryGridAuditExposurePolicy INSTANCE =
                new ExternalSafeMemoryGridAuditExposurePolicy();

        private ExternalSafeMemoryGridAuditExposurePolicy() {
        }

        @Override
        public Map<String, Object> expose(MemoryGridResultResolver.ResultHandleMetadata metadata,
                                          MemoryGridResultResolver.ResolvedResult result) {
            Map<String, Object> audit = new LinkedHashMap<>();
            audit.put("result_handle", result.resultHandle());
            audit.put("row_count", result.rows().size());
            if (metadata != null) {
                audit.put("source_route", firstNonBlank(metadata.sourceRoute(), result.sourceRoute()));
                audit.put("namespace", firstNonBlank(metadata.namespace(), result.namespace()));
                audit.put("query_hash", metadata.queryHash());
                audit.put("storage_ref_redacted", metadata.storageRef() != null && !metadata.storageRef().isBlank());
                audit.put("expires_at", metadata.expiresAt() == null ? null : metadata.expiresAt().toString());
                audit.put("source_model_refs", metadata.sourceModelRefs());
                audit.put("read_count", metadata.readCount());
                audit.put("cell_count", metadata.cellCount());
            } else {
                audit.put("source_route", result.sourceRoute());
                audit.put("namespace", result.namespace());
            }
            return audit;
        }

        private static String firstNonBlank(String first, String second) {
            return first != null && !first.isBlank() ? first : second;
        }
    }
}
