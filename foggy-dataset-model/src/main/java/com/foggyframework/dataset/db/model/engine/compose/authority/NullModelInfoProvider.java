package com.foggyframework.dataset.db.model.engine.compose.authority;

import java.util.List;
import java.util.Optional;

/**
 * Fallback {@link ModelInfoProvider} — always returns an empty table list
 * and unknown datasource identity.
 *
 * <p>Used in unit tests that don't care about physical tables and by hosts
 * that choose not to surface {@code JoinGraph} details. The resolver on
 * the other side of the SPI still gets the model name, which is the
 * minimum needed to bind authority.</p>
 *
 * <p>Datasource identity ({@link #getDatasourceId(String, String)})
 * returns {@link Optional#empty()} — the compiler treats this as "single
 * datasource / unknown" and skips the cross-datasource check.</p>
 *
 * <p>Immutable and stateless; safe to share across threads.</p>
 *
 * <p>Cross-repo invariant: mirrors Python
 * {@code foggy.dataset_model.engine.compose.authority.model_info.NullModelInfoProvider}.</p>
 *
 * @since 8.2.0.beta
 */
public final class NullModelInfoProvider implements ModelInfoProvider {

    @Override
    public Optional<List<String>> getTablesForModel(String modelName, String namespace) {
        // Return Optional.of(emptyList()) rather than Optional.empty() so
        // callers that distinguish "unknown model" from "no tables" see
        // this as the latter — the pipeline coerces both to List.of()
        // anyway, but parity with Python (which returns `[]` not `None`)
        // is clearer.
        return Optional.of(List.of());
    }

    @Override
    public Optional<String> getDatasourceId(String modelName, String namespace) {
        return Optional.empty();
    }
}
