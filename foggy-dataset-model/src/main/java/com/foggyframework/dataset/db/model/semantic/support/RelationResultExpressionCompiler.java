package com.foggyframework.dataset.db.model.semantic.support;

import com.foggyframework.core.ex.RX;
import com.foggyframework.dataset.db.model.semantic.support.RelationArithmeticExpressionParser.RelationAbsNode;
import com.foggyframework.dataset.db.model.semantic.support.RelationArithmeticExpressionParser.RelationAliasNode;
import com.foggyframework.dataset.db.model.semantic.support.RelationArithmeticExpressionParser.RelationBinaryNode;
import com.foggyframework.dataset.db.model.semantic.support.RelationArithmeticExpressionParser.RelationDenominatorGuardNode;
import com.foggyframework.dataset.db.model.semantic.support.RelationArithmeticExpressionParser.RelationExpressionNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
            if (compileArithmetic(name, expr, metricAliases, ratios, arithmetic, unsupported)) {
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

    static List<DslCteDslRequestMapper.ResultStageDerivedMetrics> compileLayered(
            Object rawDerived,
            List<String> metricAliases,
            List<String> availableFormulaAliases,
            List<String> existingDerivedAliases,
            List<String> unsupported) {
        List<Map<String, Object>> derived = mapList(rawDerived);
        if (derived.isEmpty() || derived.size() > 6) {
            unsupported.add("relation result-stage metric bridge requires one to six derived fields");
            return List.of(DslCteDslRequestMapper.ResultStageDerivedMetrics.emptyMetrics());
        }

        Set<String> sameStageAliases = new LinkedHashSet<>();
        for (Map<String, Object> item : derived) {
            String name = stringValue(item.get("name"));
            if (name == null || !SAFE_ALIAS_PATTERN.matcher(name).matches()) {
                unsupported.add("relation result-stage metric derived field must declare a governed alias");
                return List.of(DslCteDslRequestMapper.ResultStageDerivedMetrics.emptyMetrics());
            }
            if (metricAliases.contains(name) || existingDerivedAliases.contains(name) || !sameStageAliases.add(name)) {
                unsupported.add("relation result-stage metric derived aliases must be unique");
                return List.of(DslCteDslRequestMapper.ResultStageDerivedMetrics.emptyMetrics());
            }
        }

        List<Map<String, Object>> pending = new ArrayList<>(derived);
        List<String> visibleAliases = new ArrayList<>(availableFormulaAliases);
        List<String> emittedAliases = new ArrayList<>();
        List<DslCteDslRequestMapper.ResultStageDerivedMetrics> layers = new ArrayList<>();

        while (!pending.isEmpty()) {
            List<Map<String, Object>> ready = new ArrayList<>();
            for (Map<String, Object> item : pending) {
                if (visibleAliases.containsAll(sameStageDependencies(item, sameStageAliases))) {
                    ready.add(item);
                }
            }
            if (ready.isEmpty()) {
                unsupported.add("relation result-stage same-stage alias DAG must be acyclic and reference only signed aliases");
                return List.of(DslCteDslRequestMapper.ResultStageDerivedMetrics.emptyMetrics());
            }

            List<String> disallowedAliases = new ArrayList<>(existingDerivedAliases);
            disallowedAliases.addAll(emittedAliases);
            DslCteDslRequestMapper.ResultStageDerivedMetrics layer = compile(
                    ready, metricAliases, visibleAliases, disallowedAliases, unsupported);
            if (layer.empty()) {
                return List.of(DslCteDslRequestMapper.ResultStageDerivedMetrics.emptyMetrics());
            }
            layers.add(layer);
            emittedAliases.addAll(layer.aliases());
            visibleAliases.addAll(layer.aliases());
            pending.removeAll(ready);
        }

        return layers;
    }

    private static boolean compileArithmetic(String name,
                                             String expr,
                                             List<String> metricAliases,
                                             List<DslCteDslRequestMapper.MetricRatioDerived> ratios,
                                             List<DslCteDslRequestMapper.MetricArithmeticDerived> arithmetic,
                                             List<String> unsupported) {
        RelationExpressionNode node = RelationArithmeticExpressionParser.parse(expr);
        if (node == null) {
            return false;
        }

        RatioShape ratio = ratioShape(node);
        if (ratio != null) {
            if (!metricAliases.contains(ratio.numerator()) || !metricAliases.contains(ratio.denominator())) {
                unsupported.add("relation result-stage metric ratio must reference aggregate metric aliases");
                return true;
            }
            ratios.add(new DslCteDslRequestMapper.MetricRatioDerived(
                    name, ratio.numerator(), ratio.denominator()));
            return true;
        }

        DeltaRatioShape absoluteDeltaRatio = absoluteDeltaRatioShape(node);
        if (absoluteDeltaRatio != null) {
            if (!validDeltaRatioAliases(absoluteDeltaRatio, metricAliases)) {
                unsupported.add("relation result-stage absolute metric delta ratio must reference aggregate metric aliases");
                return true;
            }
            if (!denominatorMatchesDifferenceOperand(absoluteDeltaRatio)) {
                unsupported.add("relation result-stage absolute metric delta ratio denominator must match one difference operand");
                return true;
            }
            arithmetic.add(new DslCteDslRequestMapper.MetricArithmeticDerived(name,
                    "(1.0 * ABS(" + quoteAlias(absoluteDeltaRatio.left()) + " - "
                            + quoteAlias(absoluteDeltaRatio.right()) + ") / NULLIF("
                            + quoteAlias(absoluteDeltaRatio.denominator()) + ", 0))",
                    "relation_metric_absolute_delta_ratio"));
            return true;
        }

        DeltaRatioShape deltaRatio = deltaRatioShape(node);
        if (deltaRatio != null) {
            if (!validDeltaRatioAliases(deltaRatio, metricAliases)) {
                unsupported.add("relation result-stage metric delta ratio must reference aggregate metric aliases");
                return true;
            }
            if (!denominatorMatchesDifferenceOperand(deltaRatio)) {
                unsupported.add("relation result-stage metric delta ratio denominator must match one difference operand");
                return true;
            }
            arithmetic.add(new DslCteDslRequestMapper.MetricArithmeticDerived(name,
                    "(1.0 * (" + quoteAlias(deltaRatio.left()) + " - " + quoteAlias(deltaRatio.right())
                            + ") / NULLIF(" + quoteAlias(deltaRatio.denominator()) + ", 0))",
                    "relation_metric_delta_ratio"));
            return true;
        }

        DifferenceShape difference = differenceShape(node);
        if (difference != null) {
            if (!metricAliases.contains(difference.left()) || !metricAliases.contains(difference.right())) {
                unsupported.add("relation result-stage metric difference must reference aggregate metric aliases");
                return true;
            }
            arithmetic.add(new DslCteDslRequestMapper.MetricArithmeticDerived(name,
                    quoteAlias(difference.left()) + " - " + quoteAlias(difference.right()),
                    "relation_metric_difference"));
            return true;
        }

        return false;
    }

    private static RatioShape ratioShape(RelationExpressionNode node) {
        if (!(node instanceof RelationBinaryNode binary) || !"/".equals(binary.op())) {
            return null;
        }
        String numerator = alias(binary.left());
        String denominator = denominatorAlias(binary.right());
        if (numerator == null || denominator == null) {
            return null;
        }
        return new RatioShape(numerator, denominator);
    }

    private static DeltaRatioShape absoluteDeltaRatioShape(RelationExpressionNode node) {
        if (!(node instanceof RelationBinaryNode binary) || !"/".equals(binary.op())) {
            return null;
        }
        if (!(binary.left() instanceof RelationAbsNode abs)) {
            return null;
        }
        DifferenceShape difference = differenceShape(abs.child());
        String denominator = denominatorAlias(binary.right());
        if (difference == null || denominator == null) {
            return null;
        }
        return new DeltaRatioShape(difference.left(), difference.right(), denominator);
    }

    private static DeltaRatioShape deltaRatioShape(RelationExpressionNode node) {
        if (!(node instanceof RelationBinaryNode binary) || !"/".equals(binary.op())) {
            return null;
        }
        DifferenceShape difference = differenceShape(binary.left());
        String denominator = denominatorAlias(binary.right());
        if (difference == null || denominator == null) {
            return null;
        }
        return new DeltaRatioShape(difference.left(), difference.right(), denominator);
    }

    private static DifferenceShape differenceShape(RelationExpressionNode node) {
        if (!(node instanceof RelationBinaryNode binary) || !"-".equals(binary.op())) {
            return null;
        }
        String left = alias(binary.left());
        String right = alias(binary.right());
        if (left == null || right == null) {
            return null;
        }
        return new DifferenceShape(left, right);
    }

    private static String denominatorAlias(RelationExpressionNode node) {
        if (node instanceof RelationDenominatorGuardNode denominator) {
            return denominator.alias();
        }
        return alias(node);
    }

    private static String alias(RelationExpressionNode node) {
        return node instanceof RelationAliasNode alias ? alias.alias() : null;
    }

    private static boolean validDeltaRatioAliases(DeltaRatioShape shape, List<String> metricAliases) {
        return metricAliases.contains(shape.left())
                && metricAliases.contains(shape.right())
                && metricAliases.contains(shape.denominator());
    }

    private static boolean denominatorMatchesDifferenceOperand(DeltaRatioShape shape) {
        return shape.denominator().equals(shape.left()) || shape.denominator().equals(shape.right());
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
        List<Map<String, Object>> conditionDescriptors = new ArrayList<>();
        for (OrderedBucketCondition condition : conditions) {
            sql.append(" WHEN ").append(quoteAlias(field)).append(" ").append(condition.op())
                    .append(" ").append(condition.threshold())
                    .append(" THEN ").append(quoteStringLiteral(condition.label()));
            Map<String, Object> descriptor = new LinkedHashMap<>();
            descriptor.put("op", condition.op());
            descriptor.put("threshold", condition.threshold());
            descriptor.put("label", condition.label());
            conditionDescriptors.add(descriptor);
        }
        sql.append(" ELSE ").append(quoteStringLiteral(elseLabel)).append(" END");
        Map<String, Object> descriptor = new LinkedHashMap<>();
        descriptor.put("source_alias", field);
        descriptor.put("conditions", conditionDescriptors);
        descriptor.put("else_label", elseLabel);
        descriptor.put("source_policy", "single_visible_numeric_alias");
        descriptor.put("label_policy", "short_single_line_literal");
        descriptor.put("postSlice_policy", "label_alias_equality_only");
        descriptor.put("postSlice_allowed_ops", List.of("=", "!=", "<>"));
        descriptor.put("unsupported_bucket_shapes", List.of(
                "multi_field_case",
                "nested_case",
                "non_numeric_threshold",
                "unsafe_label_literal"));
        return new DslCteDslRequestMapper.MetricArithmeticDerived(name, sql.toString(),
                "relation_metric_ordered_bucket", descriptor);
    }

    private static boolean aliasAlreadyUsed(String alias,
                                            List<DslCteDslRequestMapper.MetricRatioDerived> ratios,
                                            List<DslCteDslRequestMapper.MetricArithmeticDerived> arithmetic) {
        return ratios.stream().anyMatch(existing -> existing.ratioAlias().equals(alias))
                || arithmetic.stream().anyMatch(existing -> existing.alias().equals(alias));
    }

    private static List<String> sameStageDependencies(Map<String, Object> item, Set<String> sameStageAliases) {
        List<String> result = new ArrayList<>();
        for (String dependency : expressionReferences(stringValue(item.get("expr")))) {
            if (sameStageAliases.contains(dependency) && !result.contains(dependency)) {
                result.add(dependency);
            }
        }
        return result;
    }

    private static List<String> expressionReferences(String expr) {
        RelationExpressionNode node = RelationArithmeticExpressionParser.parse(expr);
        if (node != null) {
            List<String> refs = new ArrayList<>();
            collectExpressionReferences(node, refs);
            return refs;
        }

        Matcher caseMatcher = METRIC_CASE_LABEL_PATTERN.matcher(expr == null ? "" : expr);
        if (caseMatcher.matches()) {
            return List.of(caseMatcher.group(1));
        }

        Matcher bucketMatcher = METRIC_ORDERED_BUCKET_PATTERN.matcher(expr == null ? "" : expr);
        if (bucketMatcher.matches()) {
            Matcher whenMatcher = METRIC_ORDERED_BUCKET_WHEN_PATTERN.matcher(bucketMatcher.group(1));
            List<String> refs = new ArrayList<>();
            while (whenMatcher.find()) {
                String field = whenMatcher.group(1);
                if (!refs.contains(field)) {
                    refs.add(field);
                }
            }
            return refs;
        }

        return List.of();
    }

    private static void collectExpressionReferences(RelationExpressionNode node, List<String> refs) {
        if (node instanceof RelationAliasNode alias) {
            refs.add(alias.alias());
        } else if (node instanceof RelationDenominatorGuardNode denominator) {
            refs.add(denominator.alias());
        } else if (node instanceof RelationBinaryNode binary) {
            collectExpressionReferences(binary.left(), refs);
            collectExpressionReferences(binary.right(), refs);
        } else if (node instanceof RelationAbsNode abs) {
            collectExpressionReferences(abs.child(), refs);
        }
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

    private record RatioShape(String numerator, String denominator) {
    }

    private record DifferenceShape(String left, String right) {
    }

    private record DeltaRatioShape(String left, String right, String denominator) {
    }
}
