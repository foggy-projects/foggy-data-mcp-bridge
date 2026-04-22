package com.foggyframework.dataset.db.model.engine.compose.authority;

import java.util.List;
import java.util.Optional;

/**
 * Host-supplied lookup for QM &rarr; physical tables.
 *
 * <p>The M1 {@code AuthorityRequest} protocol requires each
 * {@code ModelQuery} to carry the QM model name <b>and</b> the underlying
 * physical table list. Physical tables are not part of the
 * {@code QueryPlan} object model itself — they live in the v1.3
 * {@code JoinGraph} that the host (Foggy engine / Odoo Pro bridge) owns.</p>
 *
 * <p>Rather than drag {@code JoinGraph} into the compose subpackage
 * (creating a cross-layer dependency we'd regret at Odoo Pro vendored-sync
 * time), we accept a small injection point here. Hosts that know their
 * physical tables implement this interface; hosts that don't (or plain
 * unit tests) fall back to {@link NullModelInfoProvider} which returns an
 * empty list.</p>
 *
 * <p><b>Fallback rationale.</b> Empty {@code tables} is not a security
 * hole — the resolver on the other side of the SPI is what decides what
 * to do with it. Odoo Pro's {@code OdooEmbeddedAuthorityResolver} ignores
 * {@code tables} entirely (it looks up {@code ir.rule} by Odoo model name
 * directly). The HTTP resolver can still request table info if it needs
 * physical-table-level rule matching.</p>
 *
 * <p>Cross-repo invariant: mirrors Python
 * {@code foggy.dataset_model.engine.compose.authority.model_info.ModelInfoProvider}
 * ({@code @runtime_checkable Protocol}).</p>
 *
 * @since 8.2.0.beta
 */
@FunctionalInterface
public interface ModelInfoProvider {

    /**
     * Return the physical tables that back {@code modelName}.
     *
     * <p>Semantics:
     * <ul>
     *   <li>{@link Optional#empty()} — "no lookup available / model unknown";
     *       pipeline coerces to {@code List.of()}.</li>
     *   <li>{@code Optional.of(List.of())} — model is known but has no
     *       discoverable tables; pipeline forwards the empty list.</li>
     *   <li>{@code Optional.of(List.of(...))} — normal case.</li>
     * </ul>
     * Python's single-type {@code Optional[List[str]]} (None OR []) is
     * coerced to {@code List.of()} uniformly by the pipeline; the Java
     * side distinguishes at the interface but the pipeline also coerces
     * {@link Optional#empty()} to {@code List.of()}.</p>
     *
     * @param modelName QM model name (e.g. {@code "SaleOrderQM"})
     * @param namespace the active namespace (forwarded to host for tenant
     *                  scoping); never {@code null}
     * @return physical tables or {@link Optional#empty()}
     */
    Optional<List<String>> getTablesForModel(String modelName, String namespace);
}
