package com.foggyframework.runtime.api.dto;

/** Stable lifecycle failure categories exposed without changing legacy error.code. */
public enum RuntimeLifecycleErrorCode {
    CATALOG_BUILD_FAILED,
    CATALOG_VALIDATION_FAILED,
    CATALOG_CANDIDATE_STALE,
    DATASOURCE_BINDING_NOT_CURRENT,
    SINGLE_FLIGHT_CYCLIC_DEPENDENCY,
    NAMESPACE_SCOPE_MISUSE,
    REFRESH_SCOPE_UNKNOWN,
    SOURCE_REVISION_STALE,
    DATASOURCE_BINDING_REVOKED
}
