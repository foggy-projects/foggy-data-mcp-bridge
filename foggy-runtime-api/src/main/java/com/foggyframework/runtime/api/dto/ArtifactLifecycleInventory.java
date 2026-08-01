package com.foggyframework.runtime.api.dto;

import java.util.List;

/** Redacted, read-only filesystem lifecycle facts for Runtime-managed artifacts. */
public record ArtifactLifecycleInventory(
        String capturedAt,
        String health,
        List<RootHealth> roots,
        Summary summary,
        List<InventoryObject> objects,
        List<String> blockedReasons
) {
    public ArtifactLifecycleInventory {
        roots = roots == null ? List.of() : List.copyOf(roots);
        objects = objects == null ? List.of() : List.copyOf(objects);
        blockedReasons = blockedReasons == null
                ? List.of() : List.copyOf(blockedReasons);
    }

    public record RootHealth(
            String store,
            String health,
            long objectCount,
            long bytes,
            List<String> blockedReasons
    ) {
        public RootHealth {
            blockedReasons = blockedReasons == null
                    ? List.of() : List.copyOf(blockedReasons);
        }
    }

    public record Summary(
            long totalObjects,
            long totalBytes,
            long mustRetain,
            long provablyUnreachableCandidates,
            long unknownPreserve,
            long blockedObjects
    ) {
    }

    public record InventoryObject(
            String store,
            String type,
            String identity,
            String status,
            long bytes,
            String referenceClass,
            List<String> references,
            String blockedReason
    ) {
        public InventoryObject {
            references = references == null
                    ? List.of() : references.stream().sorted().distinct().toList();
        }
    }
}
