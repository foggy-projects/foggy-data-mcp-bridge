package com.foggyframework.dataset.db.model.engine.compose.security;

import com.foggyframework.dataset.db.model.engine.compose.plan.AggregateColumn;
import com.foggyframework.dataset.db.model.engine.compose.plan.BaseModelPlan;
import com.foggyframework.dataset.db.model.engine.compose.plan.DerivedQueryPlan;
import com.foggyframework.dataset.db.model.engine.compose.plan.JoinPlan;
import com.foggyframework.dataset.db.model.engine.compose.plan.PlanColumnRef;
import com.foggyframework.dataset.db.model.engine.compose.plan.ProjectedColumn;
import com.foggyframework.dataset.db.model.engine.compose.plan.QueryPlan;
import com.foggyframework.dataset.db.model.engine.compose.plan.UnionPlan;
import com.foggyframework.dataset.db.model.engine.compose.plan.WindowColumn;
import com.foggyframework.dataset.db.model.engine.compose.plan.expr.ColumnExpr;
import com.foggyframework.dataset.db.model.engine.compose.schema.AliasExtractor;
import com.foggyframework.dataset.db.model.engine.compose.schema.ColumnSpec;
import com.foggyframework.dataset.db.model.engine.compose.schema.ComposeSchemaErrorCodes;
import com.foggyframework.dataset.db.model.engine.compose.schema.ComposeSchemaException;
import com.foggyframework.dataset.db.model.engine.compose.schema.OutputSchema;

import java.util.List;
import java.util.Set;

/**
 * <b>G10 PR4</b> · Compose-layer plan-aware permission validation.
 *
 * <p>Independent class, deliberately <b>not</b> a Spring component and
 * deliberately not part of the global {@code FieldAccessPermissionStep}
 * pipeline (which assumes a single flat {@code fieldAccess} whitelist).
 * The Compose pipeline can route a column through multiple plans, so the
 * routing rule (column → producing plan → that plan's binding's
 * {@code fieldAccess}) belongs in its own validator.</p>
 *
 * <p><b>Position in the pipeline.</b> Called by
 * {@code ComposePlanner.compilePlanToSql} after schema derivation, before
 * SQL emission, only when {@link com.foggyframework.dataset.db.model.engine.compose.ComposeFeatureFlags#g10Enabled()}
 * is true. The single-base / pre-G10 path remains unchanged: the global
 * {@link FieldAccessPermissionStep} continues to enforce the flat
 * whitelist at {@code @Order(-25)}.</p>
 *
 * <p><b>Validation rules</b> (per G10 spec §6.4):
 * <ol>
 *   <li>F5 plan-qualified ({@link PlanColumnRef} with non-null
 *       {@code plan()}): route to that plan's binding; fail closed when
 *       the plan is not registered ({@code COLUMN_PLAN_NOT_BOUND}); fail
 *       open with {@code FIELD_ACCESS_DENIED} when the field is outside
 *       the plan's whitelist; pass when the binding declares no
 *       whitelist (no whitelist = no restriction, by spec).</li>
 *   <li>Bare field (string column or columnExpr/projectedColumn without
 *       a plan ref): resolve via {@link OutputSchema}.
 *       <ul>
 *         <li>Not in schema → {@code COLUMN_FIELD_NOT_FOUND}.</li>
 *         <li>Ambiguous in schema → {@code JOIN_AMBIGUOUS_COLUMN}
 *             (carries the candidate plans so the user can disambiguate
 *             via F5).</li>
 *         <li>Unique in schema → if the matched ColumnSpec carries
 *             {@code planProvenance}, route to that plan's binding;
 *             otherwise (single-base case with no provenance) skip —
 *             the legacy {@link FieldAccessPermissionStep} handles it.</li>
 *       </ul></li>
 * </ol></p>
 *
 * <p>Cross-repo invariant: mirrors Python
 * {@code foggy.dataset_model.engine.compose.security.compose_plan_aware_permission_validator}
 * (PR5 / Python sync).</p>
 *
 * @since 8.3.0.beta
 */
public final class ComposePlanAwarePermissionValidator {

    private ComposePlanAwarePermissionValidator() { /* utility */ }

    /**
     * Validate every top-level column reference in {@code plan}'s output
     * against the per-plan whitelists in {@code planCtx}.
     *
     * <p>Top-level here means the columns the user wrote in the outermost
     * plan's {@code columns}: bare strings, {@link ColumnExpr},
     * {@link ProjectedColumn}, {@link AggregateColumn}, {@link WindowColumn},
     * and {@link PlanColumnRef}. Inner-plan columns were already validated
     * when their plan was schema-derived; we don't double-walk here to
     * avoid quadratic behaviour on deep derivation chains.</p>
     *
     * @param plan     the root plan whose columns to validate
     * @param schema   plan's already-derived {@link OutputSchema}
     *                 (post-PR2: ambiguous-column-aware)
     * @param planCtx  per-plan binding view; never null (use
     *                 {@link PlanFieldAccessContext#empty()} when no
     *                 bindings are pre-registered)
     */
    public static void validate(QueryPlan plan, OutputSchema schema,
                                 PlanFieldAccessContext planCtx) {
        if (plan == null) {
            throw new IllegalArgumentException(
                    "validate expects a non-null QueryPlan");
        }
        if (schema == null) {
            throw new IllegalArgumentException(
                    "validate expects a non-null OutputSchema");
        }
        if (planCtx == null) {
            throw new IllegalArgumentException(
                    "validate expects a non-null PlanFieldAccessContext");
        }
        for (Object column : topLevelColumns(plan)) {
            validateColumn(column, schema, planCtx);
        }
    }

    // ------------------------------------------------------------------
    // Per-column dispatch
    // ------------------------------------------------------------------

    private static void validateColumn(Object column, OutputSchema schema,
                                        PlanFieldAccessContext planCtx) {
        // F5 plan-qualified — direct routing.
        PlanColumnRef ref = extractPlanRef(column);
        if (ref != null && ref.plan() != null) {
            validatePlanQualified(ref, planCtx);
            return;
        }
        // Bare field — resolve through schema first, then permission.
        String fieldName = extractFieldName(column);
        if (fieldName == null) {
            // Expression-only (e.g. SUM(amount)) — column reference deps
            // are validated by the legacy FieldAccessPermissionStep in
            // single-base contexts; the Compose multi-plan path defers
            // to that step for now (deferred PR; spec §6.4 step 3).
            return;
        }
        validateBareField(fieldName, schema, planCtx);
    }

    private static void validatePlanQualified(PlanColumnRef ref,
                                                PlanFieldAccessContext planCtx) {
        QueryPlan plan = ref.plan();
        if (!planCtx.containsPlan(plan)) {
            throw new ComposeSchemaException(
                    ComposeSchemaErrorCodes.COLUMN_PLAN_NOT_BOUND,
                    "Plan-qualified column reference '" + ref.name()
                            + "' targets a QueryPlan that is not registered "
                            + "in the active PlanFieldAccessContext "
                            + "(plan="
                            + plan.getClass().getSimpleName()
                            + "). Compose pipeline must bind every referenced "
                            + "plan before validation.",
                    ComposeSchemaErrorCodes.PHASE_PERMISSION_VALIDATE,
                    /* planPath = */ null,
                    ref.name());
        }
        Set<String> whitelist = planCtx.resolveFieldAccess(plan);
        if (whitelist == null) {
            // Plan is bound but its binding declares no fieldAccess —
            // unrestricted access by spec. Pass.
            return;
        }
        String baseField = stripDimensionSuffix(ref.name());
        if (!whitelist.contains(baseField)) {
            throw fieldAccessDenied(ref.name(),
                    "plan-qualified reference '" + ref.name() + "' denied by "
                            + "plan's fieldAccess whitelist (allowed: "
                            + whitelist.size() + " fields).");
        }
    }

    private static void validateBareField(String fieldName, OutputSchema schema,
                                            PlanFieldAccessContext planCtx) {
        List<ColumnSpec> matches = schema.getAll(fieldName);
        if (matches.isEmpty()) {
            throw new ComposeSchemaException(
                    ComposeSchemaErrorCodes.COLUMN_FIELD_NOT_FOUND,
                    "Bare-field reference '" + fieldName
                            + "' is not in the plan's output schema. "
                            + "Available column names: " + schema.nameSet(),
                    ComposeSchemaErrorCodes.PHASE_PERMISSION_VALIDATE,
                    null, fieldName);
        }
        if (schema.isAmbiguous(fieldName)) {
            throw new ComposeSchemaException(
                    ComposeSchemaErrorCodes.JOIN_AMBIGUOUS_COLUMN,
                    "Bare-field reference '" + fieldName + "' is ambiguous: "
                            + matches.size() + " plans in this join produce a "
                            + "column with that name. Disambiguate by writing "
                            + "{plan: <handle>, field: '" + fieldName
                            + "'} (F5 plan-qualified).",
                    ComposeSchemaErrorCodes.PHASE_PERMISSION_VALIDATE,
                    null, fieldName);
        }
        ColumnSpec sole = matches.get(0);
        QueryPlan provenance = sole.planProvenance() == null
                ? null : sole.planProvenance().resolve();
        if (provenance == null) {
            // Single-base / no-provenance case — defer to the legacy
            // FieldAccessPermissionStep in the global pipeline.
            return;
        }
        if (!planCtx.containsPlan(provenance)) {
            // Provenance plan was not registered — fail-closed.
            throw new ComposeSchemaException(
                    ComposeSchemaErrorCodes.COLUMN_PLAN_NOT_BOUND,
                    "Bare-field '" + fieldName
                            + "' resolved to plan provenance "
                            + provenance.getClass().getSimpleName()
                            + " but that plan is not registered in the "
                            + "active PlanFieldAccessContext.",
                    ComposeSchemaErrorCodes.PHASE_PERMISSION_VALIDATE,
                    null, fieldName);
        }
        Set<String> whitelist = planCtx.resolveFieldAccess(provenance);
        if (whitelist == null) {
            return;
        }
        String baseField = stripDimensionSuffix(fieldName);
        if (!whitelist.contains(baseField)) {
            throw fieldAccessDenied(fieldName,
                    "bare-field '" + fieldName
                            + "' resolved to a plan whose fieldAccess whitelist "
                            + "excludes it.");
        }
    }

    // ------------------------------------------------------------------
    // Column-shape extractors
    // ------------------------------------------------------------------

    /** Delegate to {@link QueryPlan#extractPlanRef} so plan-build-time
     *  visibility (G5 F5) and permission-validate-time routing (this
     *  class) see the same plan anchor for every column shape. The
     *  shared helper also handles the
     *  {@code ProjectedColumn(AggregateColumn(PlanColumnRef))} compound
     *  produced by F5 {@code {plan, field, agg, as}}. */
    static PlanColumnRef extractPlanRef(Object column) {
        return QueryPlan.extractPlanRef(column);
    }

    /** Return the bare column name for a column entry without a plan ref,
     *  or {@code null} when the entry is an expression that doesn't map
     *  to a single field name. Reuses {@link AliasExtractor#extract} so
     *  the {@code "expr AS alias"} parsing matches {@link OutputSchema}'s
     *  derivation byte-for-byte (case-insensitive {@code AS}, identifier
     *  validation on the alias slot, fallback-on-malformed). */
    static String extractFieldName(Object column) {
        if (column instanceof String s) {
            return AliasExtractor.extract(s).outputName();
        }
        if (column instanceof ColumnExpr ce) {
            return ce.name();
        }
        if (column instanceof ProjectedColumn pc) {
            return pc.alias();
        }
        return null;
    }

    /** Drop the {@code $caption} / {@code $id} dimension suffix used by
     *  the QM dimension-attribute syntax so {@code "salesDate$id"}
     *  matches the bare {@code "salesDate"} entry of a fieldAccess
     *  whitelist. Mirrors {@code FieldAccessPermissionStep#stripDimensionSuffix}
     *  byte-for-byte; copied here to keep
     *  {@code engine.compose.security} from depending on the
     *  {@code plugins.result_set_filter} package. */
    private static String stripDimensionSuffix(String fieldName) {
        if (fieldName == null) return null;
        int idx = fieldName.indexOf('$');
        return idx > 0 ? fieldName.substring(0, idx) : fieldName;
    }

    // ------------------------------------------------------------------
    // Top-level column extraction
    // ------------------------------------------------------------------

    /** Return the columns to validate for {@code plan}. Joins/unions
     *  surface their merged outputs (validated when the user references
     *  them in a wrapping derived plan); base / derived plans expose
     *  their own {@code columns()}. */
    private static List<Object> topLevelColumns(QueryPlan plan) {
        if (plan instanceof DerivedQueryPlan d) return d.columns();
        if (plan instanceof BaseModelPlan b) return b.columns();
        if (plan instanceof JoinPlan || plan instanceof UnionPlan) {
            // Their outputs are merged from sides — the derived/base plan
            // wrapping them validates final user-facing columns.
            return List.of();
        }
        return List.of();
    }

    private static ComposeSchemaException fieldAccessDenied(String field, String detail) {
        return new ComposeSchemaException(
                ComposeSchemaErrorCodes.FIELD_ACCESS_DENIED,
                "Field access denied: " + detail,
                ComposeSchemaErrorCodes.PHASE_PERMISSION_VALIDATE,
                null, field);
    }
}
