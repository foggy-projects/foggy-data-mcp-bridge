package com.foggyframework.dataset.model.semantic.support;

/** A duplicate JSON object property observed before object binding collapses repeated keys. */
public record DuplicateQueryProperty(
        String path,
        String property,
        int occurrences
) {
}
