package com.foggyframework.dataset.model.lifecycle.refresh;

import com.foggyframework.dataset.model.lifecycle.catalog.CatalogBuildView;
import com.foggyframework.dataset.model.lifecycle.catalog.CatalogCandidate;
import com.foggyframework.dataset.model.lifecycle.catalog.CatalogModelKey;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/** Owner-thread-only context supplied to a detached refresh build callback. */
public record CatalogRefreshBuildContext(
        CatalogBuildView buildView,
        CatalogCandidate candidate,
        CatalogRefreshPlan plan,
        Set<CatalogModelKey> invalidatedModels
) {

    public CatalogRefreshBuildContext {
        Objects.requireNonNull(buildView, "buildView");
        Objects.requireNonNull(candidate, "candidate");
        Objects.requireNonNull(plan, "plan");
        if (!buildView.namespace().equals(candidate.namespace())
                || !buildView.sourceRevision().equals(candidate.sourceRevision())) {
            throw new IllegalArgumentException(
                    "refresh build context view/candidate mismatch");
        }
        invalidatedModels = Collections.unmodifiableSet(new LinkedHashSet<>(
                new TreeSet<>(invalidatedModels == null
                        ? Set.of()
                        : invalidatedModels)));
    }
}
