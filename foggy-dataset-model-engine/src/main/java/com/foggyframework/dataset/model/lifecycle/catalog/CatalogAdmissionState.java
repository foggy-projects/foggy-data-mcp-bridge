package com.foggyframework.dataset.model.lifecycle.catalog;

/** Long-lived read admission state of one namespace catalog. */
public enum CatalogAdmissionState {
    ACTIVE,
    ACTIVE_OLD_PRESERVED,
    STALE_ADMISSION_BLOCKED,
    ABSENT
}
