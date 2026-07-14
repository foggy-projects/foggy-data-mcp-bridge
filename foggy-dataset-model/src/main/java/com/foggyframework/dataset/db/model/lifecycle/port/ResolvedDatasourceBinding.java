package com.foggyframework.dataset.db.model.lifecycle.port;

import com.foggyframework.dataset.db.model.lifecycle.identity.DatasourceBindingIdentity;

import javax.sql.DataSource;
import java.util.Objects;

/** Atomic projection of a generation-pinned handle and its logical identity. */
public record ResolvedDatasourceBinding(
        DataSource dataSource,
        DatasourceBindingIdentity identity,
        boolean cacheable
) {
    public ResolvedDatasourceBinding {
        Objects.requireNonNull(dataSource, "dataSource");
        if (identity == null && cacheable) {
            throw new IllegalArgumentException("an untracked datasource binding cannot be cacheable");
        }
    }

    public static ResolvedDatasourceBinding tracked(
            DataSource dataSource,
            DatasourceBindingIdentity identity
    ) {
        return new ResolvedDatasourceBinding(dataSource, Objects.requireNonNull(identity, "identity"), true);
    }

    public static ResolvedDatasourceBinding untracked(DataSource dataSource) {
        return new ResolvedDatasourceBinding(dataSource, null, false);
    }
}
