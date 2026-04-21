package com.foggyframework.dataset.db.model.engine.compose.security;

import com.foggyframework.dataset.db.model.def.query.request.SliceRequestDef;
import com.foggyframework.dataset.db.model.semantic.domain.DeniedPhysicalColumn;

import java.util.Collections;
import java.util.List;

/**
 * Per-model authority binding returned by the resolver.
 *
 * <p>Reuses v1.3 types deliberately — no compose-specific duplicates:
 * <ul>
 *   <li>{@link DeniedPhysicalColumn} — physical column blacklist entries
 *       (already wired through {@code SemanticRequestContext.deniedColumns}).</li>
 *   <li>{@link SliceRequestDef} — row-level slice conditions, same shape
 *       the query pipeline already consumes via
 *       {@code SemanticRequestContext.systemSlice}.</li>
 * </ul></p>
 *
 * <p><b>{@code fieldAccess} semantics.</b>
 * <ul>
 *   <li>{@code null} — no QM-field allowlist; {@code deniedColumns} drives
 *       visibility. This is the first-version default for Odoo Pro.</li>
 *   <li>Empty list — explicit "no field is visible" (pathological but legal).</li>
 *   <li>Non-empty list — whitelist; only these QM field names are visible.</li>
 * </ul>
 * These three cases are semantically distinct; callers must not collapse
 * {@code null} into an empty list.</p>
 *
 * @since 8.2.0.beta
 */
public final class ModelBinding {

    private final List<String> fieldAccess;
    private final List<DeniedPhysicalColumn> deniedColumns;
    private final List<SliceRequestDef> systemSlice;

    private ModelBinding(Builder b) {
        // fieldAccess may be null (main path); if provided, defensively copy.
        this.fieldAccess = b.fieldAccess == null
                ? null
                : Collections.unmodifiableList(List.copyOf(b.fieldAccess));

        if (b.deniedColumns == null) {
            throw new IllegalArgumentException(
                    "ModelBinding.deniedColumns must not be null; use empty list for no denies");
        }
        this.deniedColumns = Collections.unmodifiableList(List.copyOf(b.deniedColumns));

        if (b.systemSlice == null) {
            throw new IllegalArgumentException(
                    "ModelBinding.systemSlice must not be null; use empty list for no slices");
        }
        this.systemSlice = Collections.unmodifiableList(List.copyOf(b.systemSlice));
    }

    /** QM-field allowlist. {@code null} means "no allowlist, use deniedColumns";
     *  empty list means "no field visible"; non-empty list is a whitelist. */
    public List<String> fieldAccess() { return fieldAccess; }

    public List<DeniedPhysicalColumn> deniedColumns() { return deniedColumns; }
    public List<SliceRequestDef> systemSlice() { return systemSlice; }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private List<String> fieldAccess;                       // nullable
        private List<DeniedPhysicalColumn> deniedColumns = Collections.emptyList();
        private List<SliceRequestDef> systemSlice = Collections.emptyList();

        public Builder fieldAccess(List<String> v) { this.fieldAccess = v; return this; }
        public Builder deniedColumns(List<DeniedPhysicalColumn> v) { this.deniedColumns = v; return this; }
        public Builder systemSlice(List<SliceRequestDef> v) { this.systemSlice = v; return this; }

        public ModelBinding build() { return new ModelBinding(this); }
    }

    @Override
    public String toString() {
        return "ModelBinding{fieldAccess=" + fieldAccess
                + ", deniedColumnsSize=" + deniedColumns.size()
                + ", systemSliceSize=" + systemSlice.size() + '}';
    }
}
