package com.foggyframework.dataset.model.engine.compose.authority;

import com.foggyframework.dataset.model.engine.compose.schema.ColumnSpec;
import com.foggyframework.dataset.model.engine.compose.schema.OutputSchema;
import com.foggyframework.dataset.model.engine.compose.security.ModelBinding;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Filter an {@link OutputSchema} by a
 * {@link ModelBinding#fieldAccess()} whitelist.
 *
 * <p><b>Scope (M5).</b> This helper applies the QM-field-name whitelist
 * only. It does NOT touch {@code deniedColumns} — that filter requires
 * v1.3 {@code PhysicalColumnMapping} to translate physical table+column
 * back to QM fields, which lives in the SQL compiler layer (M6). Calling
 * this helper with a binding that has {@code fieldAccess == null} is a
 * no-op — the caller gets the input schema back unchanged.</p>
 *
 * <p><b>Semantics of {@code fieldAccess}</b> (parity with M1
 * {@code ModelBinding}):
 * <ul>
 *   <li>{@code null} — "no whitelist; deniedColumns owns visibility".
 *       No-op here. (Odoo Pro's embedded resolver returns {@code null}
 *       because its path is {@code deniedColumns + systemSlice}.)</li>
 *   <li>Empty list — "explicit: no field is visible". Returns an empty
 *       {@link OutputSchema}.</li>
 *   <li>Non-empty list — whitelist. Output preserves the input schema's
 *       column order; columns whose {@code name} is absent from the
 *       whitelist are removed.</li>
 * </ul></p>
 *
 * <p><b>Why this helper lives next to the resolver and not in
 * {@code schema.SchemaDerivation}.</b> Schema derivation (M4) is pure —
 * no authority. Applying a binding produces an <i>effective</i> schema,
 * which is an authority-layer concept. Keeping the helper here means the
 * authority subpackage owns the whole "bind then filter" path; the
 * {@code schema} subpackage stays authority-free and reusable for test
 * fixtures that don't want a resolver.</p>
 *
 * <p>Cross-repo invariant: mirrors Python
 * {@code foggy.dataset_model.engine.compose.authority.apply.apply_field_access_to_schema}.
 * Python raises {@code TypeError} on bad input; Java raises
 * {@link IllegalArgumentException} for parity with the rest of the
 * authority subpackage (Python's {@code TypeError} and Java's
 * {@code IllegalArgumentException} are semantically equivalent for this
 * use — both signal "programmer error").</p>
 *
 * @since 8.2.0.beta
 */
public final class FieldAccessApplier {

    private FieldAccessApplier() { /* utility */ }

    /**
     * Return a schema restricted to the columns whose {@code name} appears
     * in {@code binding.fieldAccess()}.
     *
     * @param schema the declared {@link OutputSchema} to filter; must be
     *               non-null
     * @param binding the {@link ModelBinding} whose {@code fieldAccess}
     *                drives the filter; must be non-null
     * @return <ul>
     *   <li>the input {@code schema} (same instance) when
     *       {@code binding.fieldAccess() == null};</li>
     *   <li>an empty schema when {@code binding.fieldAccess().isEmpty()};</li>
     *   <li>a new {@link OutputSchema} with only the matching columns,
     *       preserving input order, otherwise.</li>
     * </ul>
     * @throws IllegalArgumentException when either argument is {@code null}
     */
    public static OutputSchema apply(OutputSchema schema, ModelBinding binding) {
        if (schema == null) {
            throw new IllegalArgumentException(
                    "apply: schema must not be null");
        }
        if (binding == null) {
            throw new IllegalArgumentException(
                    "apply: binding must not be null");
        }

        List<String> allow = binding.fieldAccess();

        // null → no-op; the deniedColumns path (M6) owns visibility.
        if (allow == null) {
            return schema;
        }

        // Empty whitelist → explicit "no visible field". Return a real
        // empty OutputSchema; callers decide whether to surface a
        // permission error — this helper is pure, it does not decide.
        if (allow.isEmpty()) {
            return OutputSchema.empty();
        }

        Set<String> allowSet = Set.copyOf(allow);
        List<ColumnSpec> kept = new ArrayList<>(schema.size());
        for (ColumnSpec c : schema.columns()) {
            if (allowSet.contains(c.name())) {
                kept.add(c);
            }
        }
        return OutputSchema.of(kept);
    }
}
