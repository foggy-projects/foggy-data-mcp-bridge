package com.foggyframework.dataset.db.model.engine.compose.compilation;

import com.foggyframework.dataset.db.model.engine.compose.ComposedSql;
import com.foggyframework.dataset.db.model.engine.compose.authority.AuthorityResolutionPipeline;
import com.foggyframework.dataset.db.model.engine.compose.authority.DatasourceIdCollector;
import com.foggyframework.dataset.db.model.engine.compose.context.ComposeQueryContext;
import com.foggyframework.dataset.db.model.engine.compose.plan.BaseModelPlan;
import com.foggyframework.dataset.db.model.engine.compose.plan.PlanId;
import com.foggyframework.dataset.db.model.engine.compose.plan.QueryPlan;
import com.foggyframework.dataset.db.model.engine.compose.plan.TimeWindowExpander;
import com.foggyframework.dataset.db.model.engine.compose.relation.*;
import com.foggyframework.dataset.db.model.engine.compose.schema.OutputSchema;
import com.foggyframework.dataset.db.model.engine.compose.schema.SchemaDerivation;
import com.foggyframework.dataset.db.model.engine.compose.security.ModelBinding;

import java.util.*;
import java.util.regex.Pattern;

/**
 * S7c · Compile a {@link QueryPlan} to a stable {@link CompiledRelation}.
 *
 * <p>This is the real runtime entry point defined by the S7b contract.
 * It wires the S7a POC relation model into the existing compose
 * compilation pipeline ({@link ComposePlanner} / {@link PerBaseCompiler}).
 *
 * <p>The compiled relation carries:
 * <ul>
 *   <li>Stable alias</li>
 *   <li>Structured {@link RelationSql}</li>
 *   <li>Flattened params in stable render order</li>
 *   <li>{@link OutputSchema} (optionally enriched with timeWindow metadata)</li>
 *   <li>Datasource identity</li>
 *   <li>Dialect / {@link RelationCapabilities}</li>
 *   <li>Permission state</li>
 * </ul>
 *
 * <h3>Fail-closed invariants</h3>
 * <ul>
 *   <li>{@code supportsOuterAggregate = false} — S7e not yet opened.</li>
 *   <li>{@code supportsOuterWindow = false} — S7f not yet opened.</li>
 *   <li>MySQL 5.7 + inner CTE → {@code RELATION_WRAP_UNSUPPORTED}.</li>
 *   <li>SQL Server inner CTE → hoisted top-level CTE (guaranteed by
 *       {@code ComposePlanner} subquery fallback on mssql/sqlserver).</li>
 *   <li>Generated SQL never contains {@code FROM (WITH} — verified
 *       post-compilation.</li>
 * </ul>
 *
 * @since 8.5.0.beta (S7c)
 */
public final class ComposeRelationCompiler {

    private ComposeRelationCompiler() { /* utility */ }

    /**
     * Compile a {@link QueryPlan} to a {@link CompiledRelation}.
     *
     * @param plan    root of the plan tree
     * @param context the {@link ComposeQueryContext} carrying the principal
     *                and the authority resolver; required when
     *                {@code opts.bindings()} is null (one-shot path)
     * @param opts    relation compile options
     * @return an immutable {@link CompiledRelation}
     *
     * @throws ComposeCompileException with a relation error code when
     *         the plan cannot be compiled to a safe relation for the
     *         target dialect
     */
    public static CompiledRelation compileToRelation(
            QueryPlan plan,
            ComposeQueryContext context,
            RelationCompileOptions opts) {

        // ---- 1. Input validation ----
        if (plan == null) {
            throw new IllegalArgumentException(
                    "compileToRelation: plan must not be null");
        }
        if (opts == null) {
            throw new IllegalArgumentException(
                    "compileToRelation: opts must not be null");
        }
        if (opts.semanticService() == null) {
            throw new IllegalArgumentException(
                    "RelationCompileOptions.semanticService is required");
        }

        String dialect = opts.dialect();

        // ---- 2. Resolve bindings (mirrors ComposeSqlCompiler) ----
        Map<String, ModelBinding> bindings = opts.bindings();
        if (bindings == null) {
            if (context == null) {
                throw new IllegalArgumentException(
                        "compileToRelation: context is required when bindings "
                                + "are not pre-supplied");
            }
            bindings = AuthorityResolutionPipeline.resolve(
                    plan, context, opts.modelInfoProvider());
        }

        // ---- 3. Collect datasource IDs ----
        Map<String, Optional<String>> datasourceIds = opts.datasourceIds();
        if (datasourceIds == null && opts.modelInfoProvider() != null) {
            String ns = context == null ? "" : context.namespace();
            datasourceIds = DatasourceIdCollector.collect(
                    plan, opts.modelInfoProvider(), ns != null ? ns : "");
        }

        // ---- 4. Resolve datasource identity for the relation ----
        String datasourceId = resolveDatasourceId(plan, datasourceIds);

        // ---- 5. Compile plan to ComposedSql ----
        String namespace = context == null ? null : context.namespace();
        ComposedSql composedSql = ComposePlanner.compileToComposedSql(
                plan, bindings, opts.semanticService(), namespace, dialect,
                datasourceIds);

        String sql = composedSql.getSql();
        List<Object> params = composedSql.getParams() != null
                ? composedSql.getParams()
                : Collections.emptyList();

        // ---- 6. Detect CTE structure ----
        boolean hasCteItems = detectCtePresence(sql) || containsFromWith(sql);

        // ---- 7. Build RelationCapabilities & fail-closed guards ----
        RelationCapabilities capabilities = RelationCapabilities.forDialect(
                dialect, hasCteItems);

        if (RelationWrapStrategy.FAIL_CLOSED.equals(
                capabilities.relationWrapStrategy())) {
            throw new ComposeCompileException(
                    ComposeCompileErrorCodes.RELATION_WRAP_UNSUPPORTED,
                    ComposeCompileErrorCodes.PHASE_RELATION_COMPILE,
                    "Cannot compile plan to relation for dialect '" + dialect
                            + "': the plan produces CTE structures that this "
                            + "dialect does not support. MySQL 5.7 does not "
                            + "support CTE; use mysql8 or a CTE-capable dialect.");
        }

        // ---- 8. Post-compilation safety check: FROM (WITH forbidden ----
        assertNoFromWith(sql, dialect);

        // ---- 9. Derive OutputSchema ----
        OutputSchema outputSchema = deriveOutputSchema(plan, opts);

        // ---- 10. Build RelationSql ----
        String alias = opts.relationAlias();
        RelationSql relationSql = RelationSql.builder()
                .bodySql(sql)
                .bodyParams(params)
                .preferredAlias(alias)
                .build();

        // ---- 11. Assemble CompiledRelation ----
        return CompiledRelation.builder()
                .alias(alias)
                .relationSql(relationSql)
                .params(params)
                .outputSchema(outputSchema)
                .datasourceId(datasourceId)
                .dialect(dialect)
                .capabilities(capabilities)
                .sourcePlanId(PlanId.of(plan))
                .permissionState(opts.permissionState())
                .build();
    }

    // ------------------------------------------------------------------
    // Internal helpers
    // ------------------------------------------------------------------

    /**
     * Derive OutputSchema for the plan, optionally enriched with
     * timeWindow semantic metadata.
     *
     * <p>When {@code opts.timeWindowDef()} is provided, uses
     * {@link TimeWindowExpander#getOutputSchema} which produces
     * ColumnSpecs with semanticKind, referencePolicy, valueMeaning,
     * and lineage. Otherwise falls back to
     * {@link SchemaDerivation#derive} for a basic structural schema.</p>
     */
    private static OutputSchema deriveOutputSchema(
            QueryPlan plan, RelationCompileOptions opts) {
        if (opts.timeWindowDef() != null
                && opts.dimensionFields() != null
                && opts.measureFields() != null) {
            return TimeWindowExpander.getOutputSchema(
                    opts.timeWindowDef(),
                    opts.dimensionFields(),
                    opts.measureFields());
        }
        return SchemaDerivation.derive(plan);
    }

    /**
     * Resolve the datasource identity for the relation from the
     * datasource IDs map. Returns the single unique datasource ID
     * when all leaf models share the same datasource, or null when
     * unknown or when no datasource ID provider was supplied.
     */
    private static String resolveDatasourceId(
            QueryPlan plan,
            Map<String, Optional<String>> datasourceIds) {
        if (datasourceIds == null) {
            return null;
        }
        Set<String> seen = new TreeSet<>();
        for (BaseModelPlan base : plan.baseModelPlans()) {
            Optional<String> ds = datasourceIds.getOrDefault(
                    base.model(), Optional.empty());
            if (ds != null) {
                ds.ifPresent(id -> {
                    if (id != null && !id.isBlank()) {
                        seen.add(id);
                    }
                });
            }
        }
        // Single datasource → carry through; multiple → null (should
        // have been caught by cross-datasource guard earlier).
        return seen.size() == 1 ? seen.iterator().next() : null;
    }

    /**
     * Detect whether the compiled SQL contains CTE structures.
     *
     * <p>Checks for the presence of a top-level {@code WITH} clause
     * at the start of the SQL (with optional semicolon prefix for
     * SQL Server {@code ;WITH}).</p>
     */
    static boolean detectCtePresence(String sql) {
        if (sql == null || sql.isEmpty()) {
            return false;
        }
        String trimmed = sql.stripLeading();
        return trimmed.startsWith("WITH ")
                || trimmed.startsWith("WITH\n")
                || trimmed.startsWith("WITH\r")
                || trimmed.startsWith(";WITH ");
    }

    /** Post-compilation safety: verify the generated SQL never contains
     *  the forbidden {@code FROM (WITH} pattern. */
    private static void assertNoFromWith(String sql, String dialect) {
        if (sql != null && containsFromWith(sql)) {
            throw new ComposeCompileException(
                    ComposeCompileErrorCodes.RELATION_CTE_HOIST_UNSUPPORTED,
                    ComposeCompileErrorCodes.PHASE_RELATION_COMPILE,
                    "Compiled SQL for dialect '" + dialect
                            + "' contains forbidden 'FROM (WITH' pattern. "
                            + "CTE must be hoisted to top-level or the relation "
                            + "must fail-closed.");
        }
    }

    /** Case-insensitive check for {@code FROM (WITH} or
     *  {@code FROM ( WITH} patterns (with optional whitespace). */
    private static final Pattern FROM_WITH_PATTERN = Pattern.compile(
            "FROM\\s*\\(\\s*WITH\\b", Pattern.CASE_INSENSITIVE);

    static boolean containsFromWith(String sql) {
        return FROM_WITH_PATTERN.matcher(sql).find();
    }
}
