package com.foggyframework.dataset.model.engine.expression;

import com.foggyframework.dataset.model.def.query.request.CalculatedFieldDef;
import java.util.*;

/** Complete semantic dependencies, including window specifications. Never resolves output aliases. */
public final class CalculatedFieldDependencies {
    private CalculatedFieldDependencies() { }

    public static Map<String, CalculatedFieldDef> index(List<CalculatedFieldDef> fields) {
        Map<String, CalculatedFieldDef> result = new LinkedHashMap<>();
        if (fields != null) for (CalculatedFieldDef field : fields) {
            if (field != null && field.getName() != null) result.put(field.getName(), field);
        }
        return result;
    }

    private static Set<String> expressionReferences(String expression) {
        try {
            return CalculatedFieldService.extractColumnReferences(expression);
        } catch (RuntimeException failure) {
            // These expressions are normalized later by the existing result-stage planner.
            // Only its recognized grammar may bypass the scalar expression parser.
            Set<String> candidates = new LinkedHashSet<>();
            var matcher = java.util.regex.Pattern.compile("[A-Za-z_][A-Za-z0-9_$]*").matcher(expression);
            while (matcher.find()) candidates.add(matcher.group());
            var post = com.foggyframework.dataset.model.engine.postagg.PostAggregateRatioToTotalSupport
                    .toCalculation("dependency", expression, candidates);
            if (post == null) throw failure;
            return Set.of(post.getMeasure());
        }
    }

    public static Set<String> direct(CalculatedFieldDef field) {
        Set<String> refs = new LinkedHashSet<>();
        if (field.getExpression() != null) refs.addAll(expressionReferences(field.getExpression()));
        if (field.getPartitionBy() != null) refs.addAll(field.getPartitionBy());
        if (field.getWindowOrderBy() != null) field.getWindowOrderBy().forEach(order -> refs.add(order.getField()));
        refs.remove(null);
        return refs;
    }

    public static Set<String> base(String expression, Map<String, CalculatedFieldDef> fields) {
        return expand(expressionReferences(expression), fields);
    }

    public static Set<String> base(CalculatedFieldDef field, Map<String, CalculatedFieldDef> fields) {
        return expand(direct(field), fields);
    }

    private static Set<String> expand(Set<String> refs, Map<String, CalculatedFieldDef> fields) {
        Set<String> result = new LinkedHashSet<>();
        for (String ref : refs) visit(ref, fields, result, new LinkedHashSet<>());
        return result;
    }

    private static void visit(String ref, Map<String, CalculatedFieldDef> fields,
                              Set<String> result, Set<String> path) {
        CalculatedFieldDef field = fields.get(ref);
        if (field == null) { result.add(ref); return; }
        if (!path.add(ref)) throw new IllegalArgumentException("Calculated field dependency cycle: " + path + " -> " + ref);
        for (String dependency : direct(field)) visit(dependency, fields, result, path);
        path.remove(ref);
    }

    /** Expand required definitions without mutating definitions or exposing them as selected columns. */
    public static void includeDependencies(Set<String> required, Map<String, CalculatedFieldDef> fields) {
        Deque<String> pending = new ArrayDeque<>(required);
        while (!pending.isEmpty()) {
            CalculatedFieldDef field = fields.get(pending.removeFirst());
            if (field != null) for (String ref : direct(field)) if (required.add(ref)) pending.addLast(ref);
        }
    }
}
