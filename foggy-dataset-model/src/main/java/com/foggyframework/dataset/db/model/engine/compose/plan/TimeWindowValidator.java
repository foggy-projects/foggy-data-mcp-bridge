package com.foggyframework.dataset.db.model.engine.compose.plan;

import com.foggyframework.dataset.db.model.engine.expression.CalculateExpressionAnalyzer;
import com.foggyframework.dataset.db.model.engine.expression.CalculatedFieldService;
import com.foggyframework.fsscript.parser.spi.Exp;

import java.util.*;

/**
 * Validates a {@link TimeWindowDef} against model metadata and the compatibility matrix.
 * <p>
 * Returns an error code string if validation fails, or {@code null} if valid.
 *
 * @since 8.3.0.beta
 */
public final class TimeWindowValidator {

    // ---- Error codes (registered in ComposeCompileErrorCodes) ----
    public static final String FIELD_NOT_FOUND = "TIMEWINDOW_FIELD_NOT_FOUND";
    public static final String FIELD_NOT_TIME = "TIMEWINDOW_FIELD_NOT_TIME";
    public static final String GRAIN_INCOMPATIBLE = "TIMEWINDOW_GRAIN_INCOMPATIBLE";
    public static final String GRAIN_FIELD_NOT_FOUND = "TIMEWINDOW_GRAIN_FIELD_NOT_FOUND";
    public static final String VALUE_PARSE_FAILED = "TIMEWINDOW_VALUE_PARSE_FAILED";
    public static final String TARGET_NOT_AGGREGATE = "TIMEWINDOW_TARGET_NOT_AGGREGATE";
    public static final String RANGE_INVALID = "TIMEWINDOW_RANGE_INVALID";
    public static final String AGG_INVALID = "TIMEWINDOW_AGG_INVALID";

    // ---- Error codes for calculatedFields interaction (8.4.0 contract / 8.5.0 impl) ----
    public static final String TARGET_CALCULATED_FIELD_UNSUPPORTED = "TIMEWINDOW_TARGET_CALCULATED_FIELD_UNSUPPORTED";
    public static final String POST_CALC_FIELD_NOT_FOUND = "TIMEWINDOW_POST_CALCULATED_FIELD_NOT_FOUND";
    public static final String POST_CALC_FIELD_AGG_UNSUPPORTED = "TIMEWINDOW_POST_CALCULATED_FIELD_AGG_UNSUPPORTED";
    public static final String POST_CALC_FIELD_WINDOW_UNSUPPORTED = "TIMEWINDOW_POST_CALCULATED_FIELD_WINDOW_UNSUPPORTED";
    public static final String POST_CALC_FIELD_DEPENDENCY_UNSUPPORTED = "TIMEWINDOW_POST_CALCULATED_FIELD_DEPENDENCY_UNSUPPORTED";
    public static final String POST_CALC_CALCULATE_UNSUPPORTED = "CALCULATE_TIMEWINDOW_POST_CALC_UNSUPPORTED";

    private static final Set<String> VALID_GRAINS = Set.of("day", "week", "month", "quarter", "year");
    private static final Set<String> VALID_COMPARISONS = Set.of(
            "yoy", "mom", "wow", "ytd", "mtd", "rolling_7d", "rolling_30d", "rolling_90d");
    private static final Set<String> VALID_RANGES = Set.of("[)", "[]");
    private static final Set<String> VALID_ROLLING_AGGS = Set.of("sum", "avg", "count", "min", "max");

    /**
     * Grain field names that the model must expose for a given grain.
     * E.g. yoy + month grain requires a field like {@code salesDate$month}.
     */
    private static final Map<String, String> GRAIN_TO_PROPERTY = Map.of(
            "year", "year",
            "quarter", "quarter",
            "month", "month",
            "week", "week",
            "day", "id"  // day grain uses the date field itself ($id)
    );

    /**
     * Compatibility matrix: comparison → allowed grains.
     * Entry missing = allowed. Entry present with false = incompatible.
     */
    private static final Map<String, Set<String>> COMPATIBLE_GRAINS;

    static {
        COMPATIBLE_GRAINS = new HashMap<>();
        COMPATIBLE_GRAINS.put("yoy", Set.of("week", "month", "quarter", "year"));       // day ❌
        COMPATIBLE_GRAINS.put("mom", Set.of("month"));                                    // only month
        COMPATIBLE_GRAINS.put("wow", Set.of("day", "week"));                              // month+ ❌
        COMPATIBLE_GRAINS.put("ytd", Set.of("day", "week", "month", "quarter"));          // year ❌
        COMPATIBLE_GRAINS.put("mtd", Set.of("day"));                                      // only day
        COMPATIBLE_GRAINS.put("rolling_7d", Set.of("day"));                               // only day
        COMPATIBLE_GRAINS.put("rolling_30d", Set.of("day"));                              // only day
        COMPATIBLE_GRAINS.put("rolling_90d", Set.of("day", "week"));                      // day/week
    }

    /**
     * Validate the TimeWindowDef.
     *
     * @param tw the time window definition to validate
     * @param availableFields set of field names available in the model
     * @param timeFields set of field names that have timeRole=business_date
     * @param measureFields set of field names that are aggregate measures
     * @return error code string, or null if validation passes
     */
    public static String validate(TimeWindowDef tw,
                                  Set<String> availableFields,
                                  Set<String> timeFields,
                                  Set<String> measureFields) {

        // 1. field existence
        if (!availableFields.contains(tw.field())) {
            return FIELD_NOT_FOUND;
        }

        // 2. field must be a time field (business_date)
        if (!timeFields.contains(tw.field())) {
            return FIELD_NOT_TIME;
        }

        // 3. grain validity
        if (!VALID_GRAINS.contains(tw.grain())) {
            return VALUE_PARSE_FAILED;
        }

        // 4. comparison validity
        if (!VALID_COMPARISONS.contains(tw.comparison())) {
            return VALUE_PARSE_FAILED;
        }

        // 5. comparison × grain compatibility
        Set<String> allowedGrains = COMPATIBLE_GRAINS.get(tw.comparison());
        if (allowedGrains != null && !allowedGrains.contains(tw.grain())) {
            return GRAIN_INCOMPATIBLE;
        }

        // 5b. grain field strict check — verify the model has the required
        //     grain-level property (e.g. salesDate$month for yoy+month).
        //     Only applies to comparative modes that need grain fields.
        //     Guard: only enforce if the model has exposed at least one
        //     dimension property beyond $id/$caption (i.e. the caller has
        //     populated availableFields with property-level names).
        if (tw.isComparative()) {
            String grainProp = GRAIN_TO_PROPERTY.get(tw.grain());
            if (grainProp != null && !"id".equals(grainProp)) {
                String baseField = tw.field();
                if (baseField.endsWith("$id")) {
                    baseField = baseField.substring(0, baseField.length() - 3);
                }
                final String baseDim = baseField;
                String expectedGrainField = baseDim + "$" + grainProp;
                // Only enforce if the model has ANY property fields for this dimension
                // (beyond $id and $caption). If no properties are exposed, the v1.3
                // engine will derive grain fields at query time.
                String dimPrefix = baseDim + "$";
                boolean hasPropertyFields = availableFields.stream()
                        .anyMatch(f -> f.startsWith(dimPrefix)
                                && !f.equals(baseDim + "$id")
                                && !f.equals(baseDim + "$caption"));
                if (hasPropertyFields && !availableFields.contains(expectedGrainField)) {
                    return GRAIN_FIELD_NOT_FOUND;
                }
            }
        }

        // 6. range validity
        if (!VALID_RANGES.contains(tw.range())) {
            return RANGE_INVALID;
        }

        // 7. value must have exactly 2 elements (if provided)
        if (tw.value() != null && !tw.value().isEmpty()) {
            if (tw.value().size() != 2) {
                return VALUE_PARSE_FAILED;
            }
            // 8. validate each value is parseable
            for (String v : tw.value()) {
                if (!RelativeDateParser.isValid(v)) {
                    return VALUE_PARSE_FAILED;
                }
            }
        }

        // 9. targetMetrics: each must be a known measure
        if (tw.targetMetrics() != null) {
            for (String metric : tw.targetMetrics()) {
                if (!measureFields.contains(metric)) {
                    return TARGET_NOT_AGGREGATE;
                }
            }
        }

        // 10. rollingAggregator validity (if provided)
        if (tw.rollingAggregator() != null && !VALID_ROLLING_AGGS.contains(tw.rollingAggregator().toLowerCase())) {
            return AGG_INVALID;
        }

        return null; // all checks passed
    }

    /**
     * Validate calculatedFields interaction with timeWindow.
     * <p>
     * Checks:
     * <ol>
     *   <li>targetMetrics must not reference any calculatedField name</li>
     *   <li>Post calc fields must not use agg</li>
     *   <li>Post calc fields must not use windowFrame/partitionBy/windowOrderBy</li>
     *   <li>Post calc field expressions must only reference columns in timeWindowOutputColumns</li>
     * </ol>
     *
     * @param tw                      the time window definition
     * @param calculatedFieldNames    names of all request-level calculatedFields
     * @param calculatedFields        the full calc field definitions (for agg/window/expression checks)
     * @param timeWindowOutputColumns all column names available in the timeWindow output
     * @return error code string, or null if validation passes
     * @since 8.5.0.beta
     */
    public static String validateCalculatedFieldInteraction(
            TimeWindowDef tw,
            Set<String> calculatedFieldNames,
            java.util.List<com.foggyframework.dataset.db.model.def.query.request.CalculatedFieldDef> calculatedFields,
            Set<String> timeWindowOutputColumns) {

        if (calculatedFieldNames == null || calculatedFieldNames.isEmpty()) {
            return null; // no calc fields, nothing to check
        }

        // 1. targetMetrics must not reference calculatedField names
        if (tw.targetMetrics() != null) {
            for (String metric : tw.targetMetrics()) {
                if (calculatedFieldNames.contains(metric)) {
                    return TARGET_CALCULATED_FIELD_UNSUPPORTED;
                }
            }
        }

        // 2-4. Validate each post calc field
        if (calculatedFields != null) {
            for (var cf : calculatedFields) {
                // 2. agg must be null/empty
                if (cf.getAgg() != null && !cf.getAgg().isEmpty()) {
                    return POST_CALC_FIELD_AGG_UNSUPPORTED;
                }

                // 3. window clause must be absent
                if (cf.getPartitionBy() != null && !cf.getPartitionBy().isEmpty()) {
                    return POST_CALC_FIELD_WINDOW_UNSUPPORTED;
                }
                if (cf.getWindowOrderBy() != null && !cf.getWindowOrderBy().isEmpty()) {
                    return POST_CALC_FIELD_WINDOW_UNSUPPORTED;
                }
                if (cf.getWindowFrame() != null && !cf.getWindowFrame().isEmpty()) {
                    return POST_CALC_FIELD_WINDOW_UNSUPPORTED;
                }

                Exp compiledExp = cf.getCompiledExp();
                if (compiledExp == null && cf.getExpression() != null) {
                    compiledExp = CalculatedFieldService.compileExpression(cf.getExpression());
                    cf.setCompiledExp(compiledExp);
                }
                if (CalculateExpressionAnalyzer.containsCalculate(compiledExp)) {
                    return POST_CALC_CALCULATE_UNSUPPORTED;
                }

                // 4. expression column references must be in timeWindowOutputColumns
                if (cf.getExpression() != null && timeWindowOutputColumns != null && !timeWindowOutputColumns.isEmpty()) {
                    Set<String> refs = new LinkedHashSet<>();
                    CalculatedFieldService.extractColumnReferences(compiledExp, refs);
                    for (String ref : refs) {
                        if (calculatedFieldNames.contains(ref) && !ref.equals(cf.getName())) {
                            return POST_CALC_FIELD_DEPENDENCY_UNSUPPORTED;
                        }
                        if (!timeWindowOutputColumns.contains(ref)) {
                            return POST_CALC_FIELD_NOT_FOUND;
                        }
                    }
                }
            }
        }

        return null; // all checks passed
    }
}
