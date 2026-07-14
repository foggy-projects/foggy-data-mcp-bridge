package com.foggyframework.dataset.db.model.lifecycle.port;

/**
 * Whether a datasource binding identity still names the adapter's current
 * logical binding.
 */
public enum BindingCurrentness {
    CURRENT,
    STALE,
    UNKNOWN
}
