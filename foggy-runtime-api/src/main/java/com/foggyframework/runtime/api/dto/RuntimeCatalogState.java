package com.foggyframework.runtime.api.dto;

/** Observable Runtime API state of a namespace catalog. */
public enum RuntimeCatalogState {
    ACTIVE,
    ACTIVE_OLD_PRESERVED,
    STALE_ADMISSION_BLOCKED,
    ABSENT
}
