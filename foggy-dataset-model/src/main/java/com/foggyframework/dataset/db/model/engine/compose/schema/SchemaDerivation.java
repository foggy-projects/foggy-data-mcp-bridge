package com.foggyframework.dataset.db.model.engine.compose.schema;

import com.foggyframework.dataset.db.model.def.query.request.CalculatedFieldDef;
import com.foggyframework.dataset.db.model.engine.compose.ComposeFeatureFlags;
import com.foggyframework.dataset.db.model.engine.compose.compilation.ComposePlanner;
import com.foggyframework.dataset.db.model.engine.compose.plan.AggregateColumn;
import com.foggyframework.dataset.db.model.engine.compose.plan.BaseModelPlan;
import com.foggyframework.dataset.db.model.engine.compose.plan.DerivedQueryPlan;
import com.foggyframework.dataset.db.model.engine.compose.plan.JoinOn;
import com.foggyframework.dataset.db.model.engine.compose.plan.JoinPlan;
import com.foggyframework.dataset.db.model.engine.compose.plan.PlanColumnRef;
import com.foggyframework.dataset.db.model.engine.compose.plan.PlanId;
import com.foggyframework.dataset.db.model.engine.compose.plan.ProjectedColumn;
import com.foggyframework.dataset.db.model.engine.compose.plan.QueryPlan;
import com.foggyframework.dataset.db.model.engine.compose.plan.UnionPlan;
import com.foggyframework.dataset.db.model.engine.compose.plan.WindowColumn;
import com.foggyframework.dataset.db.model.engine.compose.plan.expr.ColumnExpr;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Walk a {@link QueryPlan} tree and derive each node's declared output schema.
 *
 * <p>The rules encoded here come directly from
 * {@code docs/8.2.0.beta/P0-ComposeQuery-QueryPlan派生查询与关系复用规范-需求.md}
 * §核心语义 and §union / §join 规范:
 * <ul>
 *   <li>Derived query may only reference names present in source's output.</li>
 *   <li>Union aligns columns positionally; left side defines the output
 *       shape, right side must match on count.</li>
 *   <li>Join preserves both sides' columns; any output-name collision must
 *       be resolved by explicit alias in a subsequent {@code .query()} step.</li>
 *   <li>Duplicate output names inside a single plan's {@code columns} list
 *       is rejected (usually the user aliased two entries to the same
 *       name).</li>
 * </ul>
 *
 * <p>What the rules do NOT yet do (deferred):
 * <ul>
 *   <li>Type-compatibility check on union branches (needs M6 type inference).</li>
 *   <li>{@code fieldAccess} / {@code deniedColumns} subtraction from
 *       BaseModel output (M5 applies authority binding).</li>
 *   <li>Full SQL expression parsing — M4 uses a lint-quality bare-identifier
 *       scanner; M6 SQL compile will do precise binding.</li>
 * </ul>
 *
 * <p>Cross-repo invariant: mirrors Python
 * {@code foggy.dataset_model.engine.compose.schema.derive.derive_schema}.</p>
 *
 * @since 8.2.0.beta
 */
public final class SchemaDerivation {

    private SchemaDerivation() { /* utility */ }

    /**
     * Return {@code plan}'s declared output schema.
     *
     * <p>Does NOT cache on the plan object — plans are immutable value
     * types and equal values produce equal schemas, so callers that need
     * repeated access are free to memoise externally.</p>
     */
    public static OutputSchema derive(QueryPlan plan) {
        if (plan == null) {
            throw new IllegalArgumentException(
                    "derive expects a non-null QueryPlan, got null");
        }
        return deriveInternal(plan, "");
    }

    // ------------------------------------------------------------------
    // Internal dispatch
    // ------------------------------------------------------------------

    private static OutputSchema deriveInternal(QueryPlan plan, String path) {
        if (plan instanceof BaseModelPlan) {
            return deriveBaseModel((BaseModelPlan) plan, path);
        }
        if (plan instanceof DerivedQueryPlan) {
            return deriveDerived((DerivedQueryPlan) plan, path);
        }
        if (plan instanceof UnionPlan) {
            return deriveUnion((UnionPlan) plan, path);
        }
        if (plan instanceof JoinPlan) {
            return deriveJoin((JoinPlan) plan, path);
        }
        throw new IllegalArgumentException(
                "derive: unknown QueryPlan subclass "
                        + plan.getClass().getSimpleName());
    }

    // ------------------------------------------------------------------
    // Per-plan derivations
    // ------------------------------------------------------------------

    private static OutputSchema deriveBaseModel(BaseModelPlan plan, String path) {
        String currentPath = path + "BaseModelPlan[" + plan.model() + "]";
        List<ColumnSpec> specs = columnsToSpecs(plan.columns(), plan.model(), currentPath);
        specs.addAll(calculatedFieldsToSpecs(plan.calculatedFields(), plan.model(), currentPath));
        OutputSchema output = OutputSchema.of(specs);
        validateGroupAndOrderBy(plan.groupBy(), plan.orderBy(), output, currentPath);
        return output;
    }

    private static OutputSchema deriveDerived(DerivedQueryPlan plan, String path) {
        String sourcePath = path + "DerivedQueryPlan/source/";
        OutputSchema sourceSchema = deriveInternal(plan.source(), sourcePath);
        Set<String> sourceNames = sourceSchema.nameSet();

        String currentPath = path + "DerivedQueryPlan";
        List<ColumnAliasParts> partsList = new ArrayList<>(plan.columns().size());
        for (Object c : plan.columns()) {
            partsList.add(parseObjectOrRaise(c, currentPath));
        }

        // Every expression must reference only names in sourceNames.
        // We can't fully parse SQL-ish expressions at M4 (that's M6's job),
        // but we can catch the common "bare identifier" miss.
        for (ColumnAliasParts parts : partsList) {
            List<String> referenced = extractBareIdentifiers(parts.expression());
            for (String ident : referenced) {
                if (isReservedToken(ident)) {
                    continue;
                }
                if (!sourceNames.contains(ident)) {
                    throw new ComposeSchemaException(
                            ComposeSchemaErrorCodes.DERIVED_QUERY_UNKNOWN_FIELD,
                            "derived query references unknown field '"
                                    + ident + "' not present in source's output schema "
                                    + "(available: " + sortedList(sourceNames) + ")",
                            ComposeSchemaErrorCodes.PHASE_SCHEMA_DERIVE,
                            currentPath,
                            ident);
                }
            }
        }

        List<ColumnSpec> specs = partsToSpecs(partsList, null, currentPath);
        OutputSchema output = OutputSchema.of(specs);
        validateGroupAndOrderBy(plan.groupBy(), plan.orderBy(), output, currentPath);
        return output;
    }

    private static OutputSchema deriveUnion(UnionPlan plan, String path) {
        String leftPath = path + "UnionPlan/left/";
        String rightPath = path + "UnionPlan/right/";
        OutputSchema leftSchema = deriveInternal(plan.left(), leftPath);
        OutputSchema rightSchema = deriveInternal(plan.right(), rightPath);

        if (leftSchema.size() != rightSchema.size()) {
            throw new ComposeSchemaException(
                    ComposeSchemaErrorCodes.UNION_COLUMN_COUNT_MISMATCH,
                    "union column count mismatch: left has "
                            + leftSchema.size() + " columns "
                            + leftSchema.names()
                            + ", right has " + rightSchema.size() + " columns "
                            + rightSchema.names(),
                    ComposeSchemaErrorCodes.PHASE_SCHEMA_DERIVE,
                    path + "UnionPlan",
                    null);
        }

        // Output schema takes left's names/expressions verbatim; drop any
        // source_model attribution since union erases per-source identity.
        return OutputSchema.of(stripSourceModel(leftSchema.columns()));
    }

    private static OutputSchema deriveJoin(JoinPlan plan, String path) {
        String leftPath = path + "JoinPlan/left/";
        String rightPath = path + "JoinPlan/right/";
        OutputSchema leftSchema = deriveInternal(plan.left(), leftPath);
        OutputSchema rightSchema = deriveInternal(plan.right(), rightPath);
        String currentPath = path + "JoinPlan";

        Set<String> leftNames = leftSchema.nameSet();
        Set<String> rightNames = rightSchema.nameSet();

        for (int i = 0; i < plan.on().size(); i++) {
            JoinOn j = plan.on().get(i);
            if (!leftNames.contains(j.left())) {
                throw new ComposeSchemaException(
                        ComposeSchemaErrorCodes.JOIN_ON_LEFT_UNKNOWN_FIELD,
                        "JoinPlan.on[" + i + "].left='" + j.left()
                                + "' not in left side's output schema "
                                + sortedList(leftNames),
                        ComposeSchemaErrorCodes.PHASE_SCHEMA_DERIVE,
                        currentPath,
                        j.left());
            }
            if (!rightNames.contains(j.right())) {
                throw new ComposeSchemaException(
                        ComposeSchemaErrorCodes.JOIN_ON_RIGHT_UNKNOWN_FIELD,
                        "JoinPlan.on[" + i + "].right='" + j.right()
                                + "' not in right side's output schema "
                                + sortedList(rightNames),
                        ComposeSchemaErrorCodes.PHASE_SCHEMA_DERIVE,
                        currentPath,
                        j.right());
            }
        }

        // Compute overlap (sorted so first offender is deterministic).
        TreeSet<String> overlap = new TreeSet<>();
        for (String n : leftNames) {
            if (rightNames.contains(n)) {
                overlap.add(n);
            }
        }

        // ----------------------------------------------------------
        // G10 PR2 · Flag-gated branch
        // ----------------------------------------------------------
        // flag=false (legacy): any overlap throws JOIN_OUTPUT_COLUMN_CONFLICT
        //                      and ColumnSpec.sourceModel is cleared on merge.
        // flag=true  (G10):    overlap is allowed; each overlapping column is
        //                      marked isAmbiguous=true and carries a PlanId
        //                      pointing at the producing side. sourceModel is
        //                      preserved so downstream consumers (PR3 / PR4)
        //                      can route reads back to the origin plan.
        boolean g10 = ComposeFeatureFlags.g10Enabled();

        if (!g10 && !overlap.isEmpty()) {
            String first = overlap.first();
            throw new ComposeSchemaException(
                    ComposeSchemaErrorCodes.JOIN_OUTPUT_COLUMN_CONFLICT,
                    "JoinPlan output has name collisions "
                            + new ArrayList<>(overlap)
                            + "; resolve via an explicit alias in a subsequent "
                            + ".query(...) step (e.g. `a.partnerName AS salesPartnerName`)",
                    ComposeSchemaErrorCodes.PHASE_SCHEMA_DERIVE,
                    currentPath,
                    first);
        }

        List<ColumnSpec> merged = new ArrayList<>(leftSchema.size() + rightSchema.size());
        if (g10) {
            // G10: capture per-side plan provenance; mark overlapping
            // columns ambiguous; keep the legacy sourceModel string so
            // existing consumers that read it still see something.
            appendAnnotatedSide(merged, leftSchema.columns(),
                    PlanId.of(plan.left()), overlap);
            appendAnnotatedSide(merged, rightSchema.columns(),
                    PlanId.of(plan.right()), overlap);
        } else {
            // Legacy merge: source_model cleared (per-side attribution dropped).
            for (ColumnSpec c : leftSchema.columns()) {
                merged.add(withSourceModelCleared(c));
            }
            for (ColumnSpec c : rightSchema.columns()) {
                merged.add(withSourceModelCleared(c));
            }
        }
        return OutputSchema.of(merged);
    }

    private static void appendAnnotatedSide(List<ColumnSpec> out,
                                            List<ColumnSpec> sideColumns,
                                            PlanId sidePid, Set<String> overlap) {
        for (ColumnSpec c : sideColumns) {
            out.add(annotateForJoin(c, sidePid, overlap.contains(c.name())));
        }
    }

    // ------------------------------------------------------------------
    // Shared helpers
    // ------------------------------------------------------------------

    private static List<ColumnSpec> columnsToSpecs(
            List<Object> columns, String sourceModel, String planPath) {
        List<ColumnAliasParts> partsList = new ArrayList<>(columns.size());
        for (Object c : columns) {
            partsList.add(parseObjectOrRaise(c, planPath));
        }
        return partsToSpecs(partsList, sourceModel, planPath);
    }

    private static List<ColumnSpec> partsToSpecs(
            List<ColumnAliasParts> partsList, String sourceModel, String planPath) {
        Set<String> seen = new LinkedHashSet<>();
        List<ColumnSpec> specs = new ArrayList<>(partsList.size());
        for (ColumnAliasParts parts : partsList) {
            if (!seen.add(parts.outputName())) {
                throw new ComposeSchemaException(
                        ComposeSchemaErrorCodes.DUPLICATE_OUTPUT_COLUMN,
                        "duplicate output column '" + parts.outputName() + "'; "
                                + "the plan projects the same output name twice. "
                                + "Use explicit aliases to disambiguate.",
                        ComposeSchemaErrorCodes.PHASE_SCHEMA_DERIVE,
                        planPath,
                        parts.outputName());
            }
            specs.add(ColumnSpec.builder()
                    .name(parts.outputName())
                    .expression(parts.expression())
                    .sourceModel(sourceModel)
                    .hasExplicitAlias(parts.hasAlias())
                    .build());
        }
        return specs;
    }

    private static List<ColumnSpec> calculatedFieldsToSpecs(
            List<CalculatedFieldDef> calculatedFields, String sourceModel, String planPath) {
        List<ColumnAliasParts> partsList = new ArrayList<>(calculatedFields.size());
        for (CalculatedFieldDef cf : calculatedFields) {
            String name = cf.getName();
            if (name == null || name.trim().isEmpty()) {
                throw new ComposeSchemaException(
                        ComposeSchemaErrorCodes.COLUMN_SPEC_MALFORMED,
                        "calculatedFields entries require a non-empty name",
                        ComposeSchemaErrorCodes.PHASE_PLAN_BUILD,
                        planPath,
                        null);
            }
            String trimmed = name.trim();
            String expression = cf.getExpression() == null || cf.getExpression().isEmpty()
                    ? trimmed
                    : cf.getExpression();
            partsList.add(new ColumnAliasParts(expression, trimmed, false));
        }
        return partsToSpecs(partsList, sourceModel, planPath);
    }

    private static ColumnAliasParts parseAliasOrRaise(String columnSpec, String planPath) {
        try {
            return AliasExtractor.extract(columnSpec);
        } catch (IllegalArgumentException e) {
            throw new ComposeSchemaException(
                    ComposeSchemaErrorCodes.COLUMN_SPEC_MALFORMED,
                    "malformed column spec: " + e.getMessage(),
                    ComposeSchemaErrorCodes.PHASE_PLAN_BUILD,
                    planPath,
                    null,
                    e);
        }
    }

    /** Schema derivation only needs an SQL-ish text form for
     *  {@link #extractBareIdentifiers(String)} — actual dialect-specific
     *  quoting happens later in {@link ComposePlanner#compileToComposedSql}.
     *  Pin to {@code "mysql"} so identifiers stay unquoted on mixed case
     *  (PostgreSQL/SQLite would otherwise wrap them in double-quotes,
     *  which the regex scanner tolerates but is needless noise). */
    private static final String IDENTIFIER_RENDER_DIALECT = "mysql";

    private static ColumnAliasParts parseObjectOrRaise(Object obj, String planPath) {
        if (obj instanceof String s) {
            return parseAliasOrRaise(s, planPath);
        }
        if (obj instanceof ProjectedColumn pc) {
            // ColumnAliasParts(expression, outputName, hasAlias) — preserve
            // arg order; pre-G5-PR-J2 this swapped expression/outputName,
            // which was latent because no upstream code path landed a
            // ProjectedColumn in DerivedQueryPlan.columns until F5 wired
            // {plan, field, as} into the derived layer.
            String exprText = ComposePlanner.compileExpression(pc.expr(), IDENTIFIER_RENDER_DIALECT);
            return new ColumnAliasParts(exprText, pc.alias(), true);
        }
        if (obj instanceof ColumnExpr ce) {
            return new ColumnAliasParts(ce.name(), ce.name(), false);
        }
        if (obj instanceof AggregateColumn agg) {
            String exprText = agg.toColumnExpr();
            return new ColumnAliasParts(exprText, exprText, false);
        }
        if (obj instanceof WindowColumn win) {
            String exprText = win.toColumnExpr();
            return new ColumnAliasParts(exprText, exprText, false);
        }
        if (obj instanceof PlanColumnRef ref) {
            return new ColumnAliasParts(ref.name(), ref.name(), false);
        }
        return parseAliasOrRaise(obj.toString(), planPath);
    }

    private static void validateGroupAndOrderBy(
            List<String> groupBy, List<String> orderBy,
            OutputSchema outputSchema, String planPath) {
        if (groupBy.isEmpty() && orderBy.isEmpty()) {
            return;
        }
        Set<String> outputNames = outputSchema.nameSet();
        for (String fieldName : groupBy) {
            assertReferenceVisible(fieldName, outputNames,
                    "this plan's output columns", planPath, "group_by");
        }
        for (String ob : orderBy) {
            // strip leading '-' for desc sort
            String stripped = ob.startsWith("-") ? ob.substring(1) : ob;
            assertReferenceVisible(stripped, outputNames,
                    "this plan's output columns", planPath, "order_by");
        }
    }

    /** Return {@code c} with {@code sourceModel} null, reusing {@code c}
     *  itself when it already has a null {@code sourceModel} (avoids a
     *  per-column builder allocation when plans stack). */
    private static ColumnSpec withSourceModelCleared(ColumnSpec c) {
        if (c.sourceModel() == null) {
            return c;
        }
        return ColumnSpec.builder()
                .name(c.name())
                .expression(c.expression())
                .sourceModel(null)
                .hasExplicitAlias(c.hasExplicitAlias())
                .planProvenance(c.planProvenance())
                .isAmbiguous(c.isAmbiguous())
                .build();
    }

    /**
     * G10 PR2 · Build the merged {@link ColumnSpec} that
     * {@link #deriveJoin} emits for one side of the join.
     *
     * <p>Compared to {@link #withSourceModelCleared} (legacy):</p>
     * <ul>
     *   <li>Preserves {@link ColumnSpec#sourceModel()} — under G10 the
     *       per-side QM attribution is still useful even after joining,
     *       and clearing it would discard provenance information for no
     *       gain (PR3 routes via {@code planProvenance}, not
     *       {@code sourceModel}).</li>
     *   <li>Sets {@link ColumnSpec#planProvenance()} to the side's
     *       {@link PlanId} so downstream consumers can resolve the
     *       column back to its producing plan (and to its alias in
     *       {@code planAliasMap}, when PR3 lands).</li>
     *   <li>Sets {@link ColumnSpec#isAmbiguous()} = {@code overlap}: the
     *       caller has already detected which names appear on both sides
     *       and passes that boolean in.</li>
     * </ul>
     */
    private static ColumnSpec annotateForJoin(ColumnSpec c, PlanId planPid, boolean overlap) {
        // Fast-path: already annotated with the same provenance/ambiguity.
        if (planPid.equals(c.planProvenance()) && c.isAmbiguous() == overlap) {
            return c;
        }
        return ColumnSpec.builder()
                .name(c.name())
                .expression(c.expression())
                .sourceModel(c.sourceModel())
                .dataType(c.dataType())
                .hasExplicitAlias(c.hasExplicitAlias())
                .planProvenance(planPid)
                .isAmbiguous(overlap)
                .build();
    }

    private static List<ColumnSpec> stripSourceModel(List<ColumnSpec> cols) {
        List<ColumnSpec> out = new ArrayList<>(cols.size());
        for (ColumnSpec c : cols) {
            out.add(withSourceModelCleared(c));
        }
        return out;
    }

    private static void assertReferenceVisible(
            String fieldName, Set<String> visible,
            String sourceLabel, String planPath, String slot) {
        if (!visible.contains(fieldName)) {
            throw new ComposeSchemaException(
                    ComposeSchemaErrorCodes.DERIVED_QUERY_UNKNOWN_FIELD,
                    slot + " references unknown field '" + fieldName
                            + "' not in " + sourceLabel
                            + " (available: " + sortedList(visible) + ")",
                    ComposeSchemaErrorCodes.PHASE_SCHEMA_DERIVE,
                    planPath,
                    fieldName);
        }
    }

    // ------------------------------------------------------------------
    // Bare-identifier scan
    // ------------------------------------------------------------------

    /** Match identifiers: letter/underscore start, then letter/digit/underscore/{@code $}. */
    private static final Pattern IDENT_SCAN =
            Pattern.compile("[A-Za-z_][A-Za-z0-9_$]*");

    /**
     * Reserved tokens / functions / keywords that appear inside allowed
     * expressions and must NOT be validated as field references.
     *
     * <p>28 tokens — exact parity with Python
     * {@code foggy.dataset_model.engine.compose.schema.derive._RESERVED_TOKENS}.</p>
     */
    static final Set<String> RESERVED_TOKENS = Set.of(
            // SQL-ish aggregate / scalar / control
            "SUM", "COUNT", "AVG", "MIN", "MAX",
            "IIF", "IF", "CASE", "WHEN", "THEN", "ELSE", "END",
            "COALESCE", "NULLIF",
            "IS_NULL", "IS_NOT_NULL", "BETWEEN", "IN", "NOT",
            "DATE_DIFF", "DATE_ADD", "NOW",
            "AND", "OR",
            // Booleans / null
            "TRUE", "FALSE", "NULL",
            // Wildcard / placeholder
            "DISTINCT"
    );

    static boolean isReservedToken(String ident) {
        return RESERVED_TOKENS.contains(ident.toUpperCase());
    }

    static List<String> extractBareIdentifiers(String expression) {
        String masked = maskStringLiterals(expression);
        List<String> out = new ArrayList<>();
        Matcher m = IDENT_SCAN.matcher(masked);
        while (m.find()) {
            out.add(m.group());
        }
        return out;
    }

    /** Replace contents of single- / double-quoted string segments with
     *  spaces so downstream identifier scanning skips them. Escapes
     *  ({@code \\'}) are handled simply: the escape char stays, the
     *  following char is also masked. */
    static String maskStringLiterals(String text) {
        // Fast path: most expressions (SUM(x), amount * rate, etc.) contain
        // no string literals at all; skip the per-char scan.
        if (text.indexOf('\'') < 0 && text.indexOf('"') < 0) {
            return text;
        }
        StringBuilder out = new StringBuilder(text.length());
        Character quote = null;
        boolean escaped = false;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (quote == null) {
                if (ch == '\'' || ch == '"') {
                    quote = ch;
                    out.append(' ');
                } else {
                    out.append(ch);
                }
            } else {
                if (escaped) {
                    escaped = false;
                    out.append(' ');
                    continue;
                }
                if (ch == '\\') {
                    escaped = true;
                    out.append(' ');
                } else if (ch == quote) {
                    quote = null;
                    out.append(' ');
                } else {
                    out.append(' ');
                }
            }
        }
        return out.toString();
    }

    // ------------------------------------------------------------------
    // Tiny helpers
    // ------------------------------------------------------------------

    private static List<String> sortedList(Set<String> in) {
        List<String> out = new ArrayList<>(in);
        java.util.Collections.sort(out);
        return out;
    }
}
