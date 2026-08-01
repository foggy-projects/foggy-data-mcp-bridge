package com.foggyframework.runtime.api.dto;

public enum AuthoringWorkspaceState {
    DRAFT,
    VALIDATED,
    STALE,
    PUBLISHING,
    RECOVERY_REQUIRED,
    PUBLISHED,
    ROLLING_BACK,
    ROLLBACK_REQUIRED,
    ROLLED_BACK,
    DISCARDED
}
