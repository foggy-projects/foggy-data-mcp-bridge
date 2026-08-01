package com.foggyframework.runtime.api.dto;

import java.util.List;

public record BundleInfo(
        String name,
        String namespace,
        String path,
        Boolean watch,
        Boolean enabled,
        String source,
        Boolean managedByRuntimeApi,
        Boolean canUpdate,
        Boolean canRemove,
        String status,
        String message,
        String sourceType,
        Boolean editable,
        Boolean workspaceEligible,
        List<String> namespaceBindings,
        String sourceIdentity,
        Boolean immutablePublication,
        String artifactRevision
) {
    public BundleInfo {
        namespaceBindings = namespaceBindings == null
                ? List.of()
                : List.copyOf(namespaceBindings);
    }

    /** Compatibility constructor retaining the pre-publication inventory surface. */
    public BundleInfo(
            String name,
            String namespace,
            String path,
            Boolean watch,
            Boolean enabled,
            String source,
            Boolean managedByRuntimeApi,
            Boolean canUpdate,
            Boolean canRemove,
            String status,
            String message,
            String sourceType,
            Boolean editable,
            Boolean workspaceEligible,
            List<String> namespaceBindings,
            String sourceIdentity
    ) {
        this(name, namespace, path, watch, enabled, source,
                managedByRuntimeApi, canUpdate, canRemove, status, message,
                sourceType, editable, workspaceEligible, namespaceBindings,
                sourceIdentity, false, null);
    }

    /** Compatibility constructor for existing Runtime API integrations. */
    public BundleInfo(
            String name,
            String namespace,
            String path,
            Boolean watch,
            Boolean enabled,
            String source,
            Boolean managedByRuntimeApi,
            Boolean canUpdate,
            Boolean canRemove,
            String status,
            String message
    ) {
        this(name, namespace, path, watch, enabled, source,
                managedByRuntimeApi, canUpdate, canRemove, status, message,
                null, false, false,
                List.of(namespace == null ? "" : namespace.trim()), null,
                false, null);
    }
}
