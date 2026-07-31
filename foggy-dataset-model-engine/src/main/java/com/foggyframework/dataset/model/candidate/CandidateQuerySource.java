package com.foggyframework.dataset.model.candidate;

/** Source coordinates supplied by a future Runtime authoring workspace. */
public record CandidateQuerySource(
        String sourceBundle,
        String namespace,
        String path,
        String baseSourceRevision
) {
}
