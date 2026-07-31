package com.foggyframework.dataset.model.candidate;

/** Stable fail-closed categories for request-local candidate queries. */
public enum CandidateQueryErrorCode {
    CANDIDATE_SOURCE_INVALID,
    CANDIDATE_SOURCE_STALE,
    CANDIDATE_CONTENT_STALE,
    CANDIDATE_OVERLAY_FORBIDDEN,
    CANDIDATE_MODE_UNSUPPORTED,
    CANDIDATE_MODEL_NOT_IN_SOURCE,
    CANDIDATE_SESSION_CLOSED
}
