package com.foggyframework.dataset.db.model.semantic.support;

import com.foggyframework.core.ex.RX;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Compiles the signed relation result-stage derived expression subset.
 *
 * <p>This component intentionally keeps the current P8-P27 runtime surface
 * narrow. It extracts formula recognition and SQL rendering from
 * {@link DslCteDslRequestMapper} without accepting arbitrary expressions.</p>
 */
final class RelationResultExpressionCompiler {

    private static final Pattern SAFE_ALIAS_PATTERN = Pattern.compile("[A-Za-z_][A-Za-z0-9_$.]*");
    private static final Pattern METRIC_SAFE_RATIO_PATTERN = Pattern.compile(
            "(?i)^\\s*([A-Za-z_][A-Za-z0-9_$]*)\\s*/\\s*(?:"
                    + "([A-Za-z_][A-Za-z0-9_$]*)|nullif\\s*\\(\\s*"
                    + "([A-Za-z_][A-Za-z0-9_$]*)\\s*,\\s*0(?:\\.0+)?\\s*\\))\\s*$");
    private static final Pattern METRIC_DIFFERENCE_PATTERN = Pattern.compile(
            "(?i)^\\s*([A-Za-z_][A-Za-z0-9_$]*)\\s*-\\s*([A-Za-z_][A-Za-z0-9_$]*)\\s*$");
    private static final Pattern METRIC_DELTA_RATIO_PATTERN = Pattern.compile(
            "(?i)^\\s*\\(\\s*([A-Za-z_][A-Za-z0-9_$]*)\\s*-\\s*([A-Za-z_][A-Za-z0-9_$]*)"
                    + "\\s*\\)\\s*/\\s*(?:([A-Za-z_][A-Za-z0-9_$]*)|nullif\\s*\\(\\s*"
                    + "([A-Za-z_][A-Za-z0-9_$]*)\\s*,\\s*0(?:\\.0+)?\\s*\\))\\s*$");
    private static final Pattern METRIC_ABSOLUTE_DELTA_RATIO_PATTERN = Pattern.compile(
            "(?i)^\\s*abs\\s*\\(\\s*\\(?\\s*([A-Za-z_][A-Za-z0-9_$]*)\\s*-\\s*"
                    + "([A-Za-z_][A-Za-z0-9_$]*)\\s*\\)?\\s*\\)\\s*/\\s*(?:"
                    + "([A-Za-z_][A-Za-z0-9_$]*)|nullif\\s*\\(\\s*"
                    + "([A-Za-z_][A-Za-z0-9_$]*)\\s*,\\s*0(?:\\.0+)?\\s*\\))\\s*$");
    private static final Pattern METRIC_CASE_LABEL_PATTERN = Pattern.compile(
            "(?i)^\\s*case\\s+when\\s+([A-Za-z_][A-Za-z0-9_$]*)\\s*(<=|>=|<>|!=|==|=|<|>)\\s*"
                    + "(-?\\d+(?:\\.\\d+)?)\\s+then\\s*'((?:[^']|'')*)'\\s+else\\s*'((?:[^']|'')*)'"
                    + "\\s+end\\s*$");
    private static final Pattern METRIC_ORDERED_BUCKET_PATTERN = Pattern.compile(
            "(?i)^\\s*case\\s+(.+)\\s+else\\s*'((?:[^']|'')*)'\\s+end\\s*$");
    private static final Pattern METRIC_ORDERED_BUCKET_WHEN_PATTERN = Pattern.compile(
            "(?i)\\G\\s*when\\s+([A-Za-z_][A-Za-z0-9_$]*)\\s*(<=|>=|<>|!=|==|=|<|>)\\s*"
                    + "(-?\\d+(?:\\.\\d+)?)\\s+then\\s*'((?:[^']|'')*)'\\s*");

    private RelationResultExpressionCompiler() {
    }

    static DslCteDslRequestMapper.ResultStageDerivedMetrics compile(
            Object rawDerived,
            List<String> metricAliases,
            List<String> availableFormulaAliases,
            List<String> existingDerivedAliases,
            List<String> unsupported) {
        List<Map<String, Object>> derived = mapList(rawDerived);
        if (derived.isEmpty() || derived.size() > 6) {
            unsupported.add("relation result-stage metric bridge requires one to six derived fields");
            return DslCteDslRequestMapper.ResultStageDerivedMetrics.emptyMetrics();
        }
        List<DslCteDslRequestMapper.MetricRatioDerived> ratios = new ArrayList<>();
        List<DslCteDslRequestMapper.MetricArithmeticDerived> arithmetic = new ArrayList<>();
        for (Map<String, Object> item : derived) {
            String name = stringValue(item.get("name"));
            String expr = stringValue(item.get("expr"));
            if (name == null || !SAFE_ALIAS_PATTERN.matcher(name).matches()) {
                unsupported.add("relation result-stage metric derived field must declare a governed alias");
                continue;
            }
            if (metricAliases.contains(name) || existingDerivedAliases.contains(name)
                    || aliasAlreadyUsed(name, ratios, arithmetic)) {
                unsupported.add("relation result-stage metric derived aliases must be unique");
                continue;
            }
            if (compileRatio(name, expr, metricAliases, ratios, unsupported)) {
                continue;
            }
            DslCteDslRequestMapper.MetricArithmeticDerived absoluteDeltaRatio =
                    compileAbsoluteDeltaRatio(name, expr, metricAliases, unsupported);
            if (absoluteDeltaRatio != null) {
                arithmetic.add(absoluteDeltaRatio);
                continue;
            }
            DslCteDslRequestMapper.MetricArithmeticDerived deltaRatio =
                    compileDeltaRatio(name, expr, metricAliases, unsupported);
            if (deltaRatio != null) {
                arithmetic.add(deltaRatio);
                continue;
            }
            DslCteDslRequestMapper.MetricArithmeticDerived difference =
                    compileDifference(name, expr, metricAliases, unsupported);
            if (difference != null) {
                arithmetic.add(difference);
                continue;
            }
            DslCteDslRequestMapper.MetricArithmeticDerived caseLabel =
                    compileCaseLabel(name, expr, availableFormulaAliases, unsupported);
            if (caseLabel != null) {
                arithmetic.add(caseLabel);
                continue;
            }
            DslCteDslRequestMapper.MetricArithmeticDerived orderedBucket =
                    compileOrderedBucket(name, expr, availableFormulaAliases, unsupported);
            if (orderedBucket != null) {
                arithmetic.add(orderedBucket);
                continue;
            }

            unsupported.add("relation result-stage metric bridge supports only metric ratio or metric difference formulas, "
                    + "metric delta ratio formulas, absolute metric delta ratio formulas, "
                    + "or signed CASE bucket label formulas");
        }
        return unsupported.isEmpty()
                ? new DslCteDslRequestMapper.ResultStageDerivedMetrics(ratios, arithmetic)
                : DslCteDslRequestMapper.ResultStageDerivedMetrics.emptyMetrics();
    }

    private static boolean compileRatio(String name, String expr, List<String> metricAliases,
                                        List<DslCteDslRequestMapper.MetricRatioDerived> ratios,
                                        List<String> unsupported) {
        Matcher matcher = METRIC_SAFE_RATIO_PATTERN.matcher(expr == null ? "" : expr);
        if (!matcher.matches()) {
            return false;
        }
        String numerator = matcher.group(1);
        String denominator = matcher.group(2) == null ? matcher.group(3) : matcher.group(2);
        if (!metricAliases.contains(numerator) || !metricAliases.contains(denominator)) {
            unsupported.add("relation result-stage metric ratio must reference aggregate metric aliases");
            return true;
        }
        ratios.add(new DslCteDslRequestMapper.MetricRatioDerived(name, numerator, denominator));
        return true;
    }

    private static DslCteDslRequestMapper.MetricArithmeticDerived compileAbsoluteDeltaRatio(
            String name, String expr, List<String> metricAliases, List<String> unsupported) {
        Matcher matcher = METRIC_ABSOLUTE_DELTA_RATIO_PATTERN.matcher(expr == null ? "" : expr);
        if (!matcher.matches()) {
            return null;
        }
        String left = matcher.group(1);
        String right = matcher.group(2);
        String denominator = matcher.group(3) == null ? matcher.group(4) : matcher.group(3);
        if (!metricAliases.contains(left) || !metricAliases.contains(right) || !metricAliases.contains(denominator)) {
            unsupported.add("relation result-stage absolute metric delta ratio must reference aggregate metric aliases");
            return null;
        }
        if (!denominator.equals(left) && !denominator.equals(right)) {
            unsupported.add("relation result-stage absolute metric delta ratio denominator must match one difference operand");
            return null;
        }
        return new DslCteDslRequestMapper.MetricArithmeticDerived(name,
                "(1.0 * ABS(" + quoteAlias(left) + " - " + quoteAlias(right)
                        + ") / NULLIF(" + quoteAlias(denominator) + ", 0))",
                "relation_metric_absolute_delta_ratio");
    }

    private static DslCteDslRequestMapper.MetricArithmeticDerived compileDeltaRatio(
            String name, String expr, List<String> metricAliases, List<String> unsupported) {
        Matcher matcher = METRIC_DELTA_RATIO_PATTERN.matcher(expr == null ? "" : expr);
        if (!matcher.matches()) {
            return null;
        }
        String left = matcher.group(1);
        String right = matcher.group(2);
        String denominator = matcher.group(3) == null ? matcher.group(4) : matcher.group(3);
        if (!metricAliases.contains(left) || !metricAliases.contains(right) || !metricAliases.contains(denominator)) {
            unsupported.add("relation result-stage metric delta ratio must reference aggregate metric aliases");
            return null;
        }
        if (!denominator.equals(left) && !denominator.equals(right)) {
            unsupported.add("relation result-stage metric delta ratio denominator must match one difference operand");
            return null;
        }
        return new DslCteDslRequestMapper.MetricArithmeticDerived(name,
                "(1.0 * (" + quoteAlias(left) + " - " + quoteAlias(right)
                        + ") / NULLIF(" + quoteAlias(denominator) + ", 0))",
                "relation_metric_delta_ratio");
    }

    private static DslCteDslRequestMapper.MetricArithmeticDerived compileDifference(
            String name, String expr, List<String> metricAliases, List<String> unsupported) {
        Matcher matcher = METRIC_DIFFERENCE_PATTERN.matcher(expr == null ? "" : expr);
        if (!matcher.matches()) {
            return null;
        }
        String left = matcher.group(1);
        String right = matcher.group(2);
        if (!metricAliases.contains(left) || !metricAliases.contains(right)) {
            unsupported.add("relation result-stage metric difference must reference aggregate metric aliases");
            return null;
        }
        return new DslCteDslRequestMapper.MetricArithmeticDerived(name, quoteAlias(left) + " - " + quoteAlias(right),
                "relation_metric_difference");
    }

    private static DslCteDslRequestMapper.MetricArithmeticDerived compileCaseLabel(
            String name, String expr, List<String> availableFormulaAliases, List<String> unsupported) {
        Matcher matcher = METRIC_CASE_LABEL_PATTERN.matcher(expr == null ? "" : expr);
        if (!matcher.matches()) {
            return null;
        }
        String field = matcher.group(1);
        if (!availableFormulaAliases.contains(field)) {
            unsupported.add("relation result-stage CASE label must reference a visible aggregate or prior derived alias");
            return null;
        }
        String op = sqlOperator(matcher.group(2));
        if (op == null) {
            unsupported.add("relation result-stage CASE label comparison operator is unsupported");
            return null;
        }
        String threshold = matcher.group(3);
        String thenLabel = unescapeSqlStringLiteral(matcher.group(4));
        String elseLabel = unescapeSqlStringLiteral(matcher.group(5));
        if (!safeCaseLabelLiteral(thenLabel) || !safeCaseLabelLiteral(elseLabel)) {
            unsupported.add("relation result-stage CASE label literals must be short single-line strings");
            return null;
        }
        String sql = "CASE WHEN " + quoteAlias(field) + " " + op + " " + threshold
                + " THEN " + quoteStringLiteral(thenLabel)
                + " ELSE " + quoteStringLiteral(elseLabel) + " END";
        return new DslCteDslRequestMapper.MetricArithmeticDerived(name, sql, "relation_metric_case_label");
    }

    private static DslCteDslRequestMapper.MetricArithmeticDerived compileOrderedBucket(
            String name, String expr, List<String> availableFormulaAliases, List<String> unsupported) {
        Matcher matcher = METRIC_ORDERED_BUCKET_PATTERN.matcher(expr == null ? "" : expr);
        if (!matcher.matches()) {
            return null;
        }
        String body = matcher.group(1);
        String elseLabel = unescapeSqlStringLiteral(matcher.group(2));
        if (!safeCaseLabelLiteral(elseLabel)) {
            unsupported.add("relation result-stage ordered bucket labels must be short single-line strings");
            return null;
        }

        Matcher whenMatcher = METRIC_ORDERED_BUCKET_WHEN_PATTERN.matcher(body);
        List<OrderedBucketCondition> conditions = new ArrayList<>();
        String field = null;
        int end = 0;
        while (whenMatcher.find()) {
            if (whenMatcher.start() != end) {
                unsupported.add("relation result-stage ordered bucket supports only simple numeric WHEN clauses");
                return null;
            }
            String currentField = whenMatcher.group(1);
            if (field == null) {
                field = currentField;
            } else if (!field.equals(currentField)) {
                unsupported.add("relation result-stage ordered bucket must compare one visible numeric alias");
                return null;
            }
            String label = unescapeSqlStringLiteral(whenMatcher.group(4));
            if (!safeCaseLabelLiteral(label)) {
                unsupported.add("relation result-stage ordered bucket labels must be short single-line strings");
                return null;
            }
            String op = sqlOperator(whenMatcher.group(2));
            if (op == null) {
                unsupported.add("relation result-stage ordered bucket comparison operator is unsupported");
                return null;
            }
            conditions.add(new OrderedBucketCondition(op, whenMatcher.group(3), label));
            end = whenMatcher.end();
        }
        if (end != body.length()) {
            unsupported.add("relation result-stage ordered bucket supports only simple numeric WHEN clauses");
            return null;
        }
        if (conditions.size() < 2) {
            return null;
        }
        if (!availableFormulaAliases.contains(field)) {
            unsupported.add("relation result-stage ordered bucket must reference a visible aggregate or prior derived alias");
            return null;
        }

        StringBuilder sql = new StringBuilder("CASE");
        for (OrderedBucketCondition condition : conditions) {
            sql.append(" WHEN ").append(quoteAlias(field)).append(" ").append(condition.op())
                    .append(" ").append(condition.threshold())
                    .append(" THEN ").append(quoteStringLiteral(condition.label()));
        }
        sql.append(" ELSE ").append(quoteStringLiteral(elseLabel)).append(" END");
        return new DslCteDslRequestMapper.MetricArithmeticDerived(name, sql.toString(),
                "relation_metric_ordered_bucket");
    }

    private static boolean aliasAlreadyUsed(String alias,
                                            List<DslCteDslRequestMapper.MetricRatioDerived> ratios,
                                            List<DslCteDslRequestMapper.MetricArithmeticDerived> arithmetic) {
        return ratios.stream().anyMatch(existing -> existing.ratioAlias().equals(alias))
                || arithmetic.stream().anyMatch(existing -> existing.alias().equals(alias));
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> mapList(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                result.add((Map<String, Object>) map);
            }
        }
        return result;
    }

    private static String stringValue(Object value) {
        return value == null ? null : String.valueOf(value).trim();
    }

    private static String sqlOperator(String op) {
        if (op == null) {
            return null;
        }
        return switch (op.trim()) {
            case "==" -> "=";
            case "=", "!=", "<>", "<", "<=", ">", ">=" -> op.trim();
            default -> null;
        };
    }

    private static String quoteAlias(String alias) {
        if (alias == null || !SAFE_ALIAS_PATTERN.matcher(alias).matches()) {
            throw RX.throwB("DSL_CTE_RESULT_STAGE_UNSAFE_ALIAS: " + alias);
        }
        return "\"" + alias.replace("\"", "\"\"") + "\"";
    }

    private static String quoteStringLiteral(String value) {
        if (!safeCaseLabelLiteral(value)) {
            throw RX.throwB("DSL_CTE_RESULT_STAGE_UNSAFE_LABEL: " + value);
        }
        return "'" + value.replace("'", "''") + "'";
    }

    private static String unescapeSqlStringLiteral(String value) {
        return value == null ? null : value.replace("''", "'");
    }

    private static boolean safeCaseLabelLiteral(String value) {
        return value != null
                && value.length() <= 64
                && !value.contains("\n")
                && !value.contains("\r")
                && !value.contains("\u0000");
    }

    private record OrderedBucketCondition(String op, String threshold, String label) {
    }
}
