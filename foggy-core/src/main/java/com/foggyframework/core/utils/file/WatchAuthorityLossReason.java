package com.foggyframework.core.utils.file;

/** Reason a recursive directory watcher can no longer prove event completeness. */
public enum WatchAuthorityLossReason {
    EVENT_OVERFLOW,
    WATCH_KEY_INVALID,
    WATCHED_DIRECTORY_DELETED,
    FILE_WATCH_REGISTRATION_FAILED,
    RECONCILIATION_LIMIT_EXCEEDED
}
