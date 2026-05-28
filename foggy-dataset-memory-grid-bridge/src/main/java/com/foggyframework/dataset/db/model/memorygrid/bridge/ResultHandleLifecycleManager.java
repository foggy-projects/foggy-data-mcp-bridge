package com.foggyframework.dataset.db.model.memorygrid.bridge;

import com.foggyframework.dataset.db.model.semantic.memorygrid.MemoryGridResultResolver;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Admin-only lifecycle operations for governed Memory Grid result handles.
 */
public final class ResultHandleLifecycleManager {

    private final ResultHandleStore store;
    private final ResultStorageAdapter storageAdapter;

    public ResultHandleLifecycleManager(ResultHandleStore store, ResultStorageAdapter storageAdapter) {
        this.store = Objects.requireNonNull(store, "store");
        this.storageAdapter = Objects.requireNonNull(storageAdapter, "storageAdapter");
    }

    public Map<String, Object> inspect(Instant now) {
        Instant checkedAt = Objects.requireNonNull(now, "now");
        int total = 0;
        int active = 0;
        int expired = 0;
        int invalidated = 0;
        int readExhausted = 0;
        for (ResultHandleRecord record : store.list()) {
            MemoryGridResultResolver.ResultHandleMetadata metadata = metadata(record);
            if (metadata == null) {
                continue;
            }
            total++;
            boolean isExpired = isExpired(metadata, checkedAt);
            boolean isInvalidated = metadata.invalidatedAt() != null;
            boolean isReadExhausted = isReadExhausted(metadata);
            if (isExpired) {
                expired++;
            }
            if (isInvalidated) {
                invalidated++;
            }
            if (isReadExhausted) {
                readExhausted++;
            }
            if (!isExpired && !isInvalidated && !isReadExhausted) {
                active++;
            }
        }

        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("checked_at", checkedAt.toString());
        evidence.put("total_handle_count", total);
        evidence.put("active_handle_count", active);
        evidence.put("expired_handle_count", expired);
        evidence.put("invalidated_handle_count", invalidated);
        evidence.put("read_exhausted_handle_count", readExhausted);
        evidence.put("storage_refs_redacted", true);
        evidence.put("rows_exposed", false);
        return evidence;
    }

    public CleanupReport cleanupExpired(Instant now) {
        Instant cleanedAt = Objects.requireNonNull(now, "now");
        int scanned = 0;
        int deletedHandles = 0;
        int deletedStorageRefs = 0;
        List<String> failureCodes = new ArrayList<>();

        for (ResultHandleRecord record : store.list()) {
            MemoryGridResultResolver.ResultHandleMetadata metadata = metadata(record);
            if (metadata == null) {
                continue;
            }
            scanned++;
            if (!isExpired(metadata, cleanedAt) && metadata.invalidatedAt() == null) {
                continue;
            }

            boolean storageDeleted = true;
            if (metadata.storageRef() != null && !metadata.storageRef().isBlank()) {
                try {
                    storageDeleted = storageAdapter.delete(metadata.storageRef());
                    if (storageDeleted) {
                        deletedStorageRefs++;
                    }
                } catch (RuntimeException ex) {
                    storageDeleted = false;
                    failureCodes.add(MemoryGridExecutor.STORAGE_UNAVAILABLE);
                }
            }
            if (storageDeleted && store.delete(metadata.handleId())) {
                deletedHandles++;
            } else if (!storageDeleted && !failureCodes.contains(MemoryGridExecutor.STORAGE_UNAVAILABLE)) {
                failureCodes.add(MemoryGridExecutor.STORAGE_UNAVAILABLE);
            }
        }

        return new CleanupReport(cleanedAt, scanned, deletedHandles, deletedStorageRefs, List.copyOf(failureCodes));
    }

    private static MemoryGridResultResolver.ResultHandleMetadata metadata(ResultHandleRecord record) {
        return record == null || record.result() == null ? null : record.result().metadata();
    }

    private static boolean isExpired(MemoryGridResultResolver.ResultHandleMetadata metadata, Instant now) {
        return metadata.expiresAt() != null && metadata.expiresAt().isBefore(now);
    }

    private static boolean isReadExhausted(MemoryGridResultResolver.ResultHandleMetadata metadata) {
        return metadata.maxReadCount() >= 0 && metadata.readCount() >= metadata.maxReadCount();
    }

    public record CleanupReport(Instant cleanedAt,
                                int scannedHandleCount,
                                int deletedHandleCount,
                                int deletedStorageRefCount,
                                List<String> failureCodes) {
        public Map<String, Object> toEvidence() {
            Map<String, Object> evidence = new LinkedHashMap<>();
            evidence.put("cleaned_at", cleanedAt.toString());
            evidence.put("scanned_handle_count", scannedHandleCount);
            evidence.put("deleted_handle_count", deletedHandleCount);
            evidence.put("deleted_storage_ref_count", deletedStorageRefCount);
            evidence.put("failure_codes", failureCodes == null ? List.of() : List.copyOf(failureCodes));
            evidence.put("storage_refs_redacted", true);
            evidence.put("rows_exposed", false);
            return evidence;
        }
    }
}
