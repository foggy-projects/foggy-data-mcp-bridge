package com.foggyframework.dataset.model.engine.compose.plan;

import com.foggyframework.dataset.model.engine.compose.ComposeOrderByNormalizer;
import com.foggyframework.dataset.model.engine.compose.schema.AliasExtractor;
import com.foggyframework.dataset.model.engine.compose.schema.ComposeSchemaErrorCodes;
import com.foggyframework.dataset.model.engine.compose.schema.ComposeSchemaException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Normalizes post-join qualified field references such as
 * {@code left.partner$caption}, {@code right.total}, or
 * {@code firstOrders.partner$caption} to the actual output column visible in
 * the derived source relation.
 */
final class PlanQualifiedFieldResolver {

    private static final Set<String> LOGICAL_OPS = Set.of("$or", "$and", "$not");

    private PlanQualifiedFieldResolver() {
    }

    record NormalizedDerivedOptions(
            List<Object> columns,
            List<Object> slice,
            List<String> groupBy,
            List<String> orderBy) {
    }

    static NormalizedDerivedOptions normalizeDerivedOptions(
            QueryPlan source,
            List<Object> columns,
            List<Object> slice,
            List<String> groupBy,
            List<String> orderBy) {
        Scope scope = Scope.forDerivedSource(source);
        List<Object> normalizedColumns = normalizeColumns(columns, scope);
        rejectProjectedSourceAliasShadowing(normalizedColumns, aliasesVisibleFromDerivedSource(source));
        return new NormalizedDerivedOptions(
                normalizedColumns,
                normalizeSlice(slice, scope),
                normalizeStrings(groupBy, scope),
                normalizeOrderBy(orderBy, scope));
    }

    static List<JoinOn> normalizeJoinOn(QueryPlan left, QueryPlan right, List<JoinOn> on) {
        if (on == null || on.isEmpty()) {
            return on == null ? List.of() : on;
        }
        Scope leftScope = Scope.forSingleSource(left, "left");
        Scope rightScope = Scope.forSingleSource(right, "right");
        List<JoinOn> out = new ArrayList<>(on.size());
        for (JoinOn entry : on) {
            out.add(JoinOn.of(
                    leftScope.resolve(entry.left()),
                    entry.op(),
                    rightScope.resolve(entry.right())));
        }
        return out;
    }

    private static List<Object> normalizeColumns(List<Object> columns, Scope scope) {
        if (columns == null || columns.isEmpty()) {
            return columns == null ? List.of() : List.copyOf(columns);
        }
        List<Object> out = new ArrayList<>(columns.size());
        for (Object col : columns) {
            out.add(normalizeColumn(col, scope));
        }
        return out;
    }

    private static Object normalizeColumn(Object col, Scope scope) {
        if (!(col instanceof String text)) {
            return col;
        }
        String trimmed = text.trim();
        int asIdx = lastAsIndex(trimmed);
        if (asIdx >= 0) {
            String expr = trimmed.substring(0, asIdx).trim();
            String alias = trimmed.substring(asIdx + 4).trim();
            String normalizedExpr = scope.resolve(expr);
            return normalizedExpr + " AS " + alias;
        }
        return scope.resolve(trimmed);
    }

    private static List<Object> normalizeSlice(List<Object> slice, Scope scope) {
        if (slice == null || slice.isEmpty()) {
            return slice == null ? List.of() : List.copyOf(slice);
        }
        List<Object> out = new ArrayList<>(slice.size());
        for (Object entry : slice) {
            out.add(normalizeSliceEntry(entry, scope));
        }
        return out;
    }

    private static Object normalizeSliceEntry(Object entry, Scope scope) {
        if (!(entry instanceof Map<?, ?> map)) {
            return entry;
        }
        Map<Object, Object> out = new LinkedHashMap<>();
        if (map.size() == 1) {
            Map.Entry<?, ?> only = map.entrySet().iterator().next();
            Object key = only.getKey();
            Object value = only.getValue();
            if (key instanceof String keyText && LOGICAL_OPS.contains(keyText)) {
                out.put(key, normalizeLogicalSliceValue(value, scope));
                return out;
            }
            if (key instanceof String keyText && !map.containsKey("field")) {
                out.put(scope.resolve(keyText), value);
                return out;
            }
        }
        for (Map.Entry<?, ?> e : map.entrySet()) {
            Object key = e.getKey();
            Object value = e.getValue();
            if (key instanceof String keyText
                    && ("field".equals(keyText) || "fieldName".equals(keyText) || "column".equals(keyText))
                    && value instanceof String valueText) {
                out.put(key, scope.resolve(valueText));
            } else if (key instanceof String keyText
                    && ("fieldReferenceValue".equals(keyText) || "ref".equals(keyText))
                    && value instanceof String valueText) {
                out.put(key, scope.resolve(valueText));
            } else {
                out.put(key, value);
            }
        }
        return out;
    }

    private static Object normalizeLogicalSliceValue(Object value, Scope scope) {
        if (value instanceof Collection<?> collection) {
            List<Object> out = new ArrayList<>(collection.size());
            for (Object sub : collection) {
                out.add(normalizeSliceEntry(sub, scope));
            }
            return out;
        }
        return normalizeSliceEntry(value, scope);
    }

    private static List<String> normalizeStrings(List<String> values, Scope scope) {
        if (values == null || values.isEmpty()) {
            return values == null ? List.of() : List.copyOf(values);
        }
        List<String> out = new ArrayList<>(values.size());
        for (String value : values) {
            out.add(scope.resolve(value));
        }
        return out;
    }

    private static List<String> normalizeOrderBy(List<String> values, Scope scope) {
        if (values == null || values.isEmpty()) {
            return values == null ? List.of() : List.copyOf(values);
        }
        List<String> out = new ArrayList<>(values.size());
        for (String value : values) {
            ComposeOrderByNormalizer.OrderSpec spec = ComposeOrderByNormalizer.parse(value);
            String field = scope.resolve(spec.field());
            out.add("desc".equals(spec.dir()) ? "-" + field : field);
        }
        return out;
    }

    private static int lastAsIndex(String text) {
        String upper = text.toUpperCase(Locale.ROOT);
        int idx = upper.lastIndexOf(" AS ");
        if (idx < 0) {
            return -1;
        }
        String alias = text.substring(idx + 4).trim();
        return alias.matches("[A-Za-z_][A-Za-z0-9_$]*") ? idx : -1;
    }

    private static List<String> declaredOutputColumns(QueryPlan plan) {
        if (plan instanceof BaseModelPlan base) {
            return outputNames(base.columns());
        }
        if (plan instanceof DerivedQueryPlan derived) {
            if (derived.columns().isEmpty()) {
                return declaredOutputColumns(derived.source());
            }
            return outputNames(derived.columns());
        }
        if (plan instanceof JoinPlan join) {
            List<String> left = declaredOutputColumns(join.left());
            List<String> right = declaredOutputColumns(join.right());
            Set<String> seen = new LinkedHashSet<>(left);
            List<String> out = new ArrayList<>(left);
            for (String col : right) {
                if (seen.add(col)) {
                    out.add(col);
                }
            }
            return out;
        }
        if (plan instanceof UnionPlan union) {
            return declaredOutputColumns(union.left());
        }
        return Collections.emptyList();
    }

    private static List<String> outputNames(List<?> columns) {
        if (columns == null || columns.isEmpty()) {
            return List.of();
        }
        List<String> out = new ArrayList<>(columns.size());
        for (Object col : columns) {
            String text;
            if (col instanceof PlanColumnRef ref) {
                text = ref.name();
            } else if (col instanceof ProjectedColumn projected) {
                text = projected.toColumnExpr();
            } else {
                text = String.valueOf(col);
            }
            out.add(AliasExtractor.extract(text).outputName());
        }
        return out;
    }

    private static void rejectProjectedSourceAliasShadowing(List<Object> columns, Set<String> aliases) {
        if (columns == null || columns.isEmpty() || aliases.isEmpty()) {
            return;
        }
        for (Object col : columns) {
            String text;
            if (col instanceof String stringCol) {
                text = stringCol;
            } else if (col instanceof ProjectedColumn projected) {
                text = projected.toColumnExpr();
            } else {
                continue;
            }
            var parts = AliasExtractor.extract(text);
            if (parts.hasAlias() && aliases.contains(parts.outputName())) {
                throw new ComposeSchemaException(
                        ComposeSchemaErrorCodes.JOIN_AMBIGUOUS_COLUMN,
                        "projected column alias '" + parts.outputName()
                                + "' shadows a visible source alias; use a distinct output alias",
                        ComposeSchemaErrorCodes.PHASE_SCHEMA_DERIVE,
                        "DerivedQueryPlan",
                        parts.outputName());
            }
        }
    }

    private static Set<String> aliasesVisibleFromDerivedSource(QueryPlan source) {
        if (source instanceof UnionPlan) {
            return new LinkedHashSet<>(source.composeSourceAliases());
        }
        return collectAliases(source);
    }

    private static Set<String> collectAliases(QueryPlan plan) {
        Set<String> aliases = new LinkedHashSet<>();
        collectAliasesRecursive(plan, aliases);
        return aliases;
    }

    private static void collectAliasesRecursive(QueryPlan plan, Set<String> aliases) {
        if (plan == null) {
            return;
        }
        aliases.addAll(plan.composeSourceAliases());
        if (plan instanceof DerivedQueryPlan derived) {
            collectAliasesRecursive(derived.source(), aliases);
        } else if (plan instanceof JoinPlan join) {
            collectAliasesRecursive(join.left(), aliases);
            collectAliasesRecursive(join.right(), aliases);
        } else if (plan instanceof UnionPlan union) {
            collectAliasesRecursive(union.left(), aliases);
            collectAliasesRecursive(union.right(), aliases);
        }
    }

    private record Scope(Map<String, String> qualified,
                         Set<String> knownPrefixes,
                         Set<String> ambiguousPrefixes) {

        static Scope forDerivedSource(QueryPlan source) {
            Map<String, String> map = new LinkedHashMap<>();
            Set<String> prefixes = new LinkedHashSet<>();
            Set<String> ambiguousPrefixes = new LinkedHashSet<>();
            if (source instanceof JoinPlan join) {
                Set<String> leftAliases = collectAliases(join.left());
                Set<String> rightAliases = collectAliases(join.right());
                for (String alias : leftAliases) {
                    if (rightAliases.contains(alias)) {
                        ambiguousPrefixes.add(alias);
                    }
                }
                addSide(map, prefixes, "left", join.left(), declaredOutputColumns(join.left()), Set.of(), true);
                Set<String> leftNames = new LinkedHashSet<>(declaredOutputColumns(join.left()));
                addSide(map, prefixes, "right", join.right(), declaredOutputColumns(join.right()), leftNames, true);
            } else {
                addSide(map, prefixes, null, source, declaredOutputColumns(source), Set.of(), !(source instanceof UnionPlan));
                if (source instanceof UnionPlan) {
                    Set<String> localAliases = new LinkedHashSet<>(source.composeSourceAliases());
                    for (String alias : collectAliases(source)) {
                        if (!localAliases.contains(alias)) {
                            prefixes.add(alias);
                        }
                    }
                }
            }
            return new Scope(map, prefixes, ambiguousPrefixes);
        }

        static Scope forSingleSource(QueryPlan source, String sideName) {
            Map<String, String> map = new LinkedHashMap<>();
            Set<String> prefixes = new LinkedHashSet<>();
            addSide(map, prefixes, sideName, source, declaredOutputColumns(source), Set.of(), true);
            return new Scope(map, prefixes, Set.of());
        }

        private static void addSide(Map<String, String> map, Set<String> prefixes, String sideName, QueryPlan plan,
                                    List<String> outputColumns, Set<String> hiddenColumns, boolean recursiveAliases) {
            Set<String> visible = new LinkedHashSet<>();
            for (String col : outputColumns) {
                if (!hiddenColumns.contains(col)) {
                    visible.add(col);
                }
            }
            if (sideName != null) {
                prefixes.add(sideName);
                for (String col : visible) {
                    map.put(sideName + "." + col, col);
                }
            }
            Set<String> aliases = recursiveAliases
                    ? collectAliases(plan)
                    : new LinkedHashSet<>(plan.composeSourceAliases());
            for (String alias : aliases) {
                prefixes.add(alias);
                for (String col : visible) {
                    map.put(alias + "." + col, col);
                }
            }
        }

        String resolve(String ref) {
            if (ref == null) {
                return null;
            }
            String trimmed = ref.trim();
            if (!isSimpleQualifiedRef(trimmed)) {
                return ref;
            }
            String prefix = trimmed.substring(0, trimmed.indexOf('.'));
            if (ambiguousPrefixes.contains(prefix)) {
                throw new ComposeSchemaException(
                        ComposeSchemaErrorCodes.JOIN_AMBIGUOUS_COLUMN,
                        "qualified source alias '" + prefix
                                + "' is ambiguous across join sides; use left/right "
                                + "or distinct source aliases",
                        ComposeSchemaErrorCodes.PHASE_SCHEMA_DERIVE,
                        "DerivedQueryPlan",
                        trimmed);
            }
            String mapped = qualified.get(trimmed);
            if (mapped != null) {
                return mapped;
            }
            if (knownPrefixes.contains(prefix)) {
                throw new ComposeSchemaException(
                        ComposeSchemaErrorCodes.DERIVED_QUERY_UNKNOWN_FIELD,
                        "derived query references unknown field '" + trimmed
                                + "' not present in source output schema",
                        ComposeSchemaErrorCodes.PHASE_SCHEMA_DERIVE,
                        "DerivedQueryPlan",
                        trimmed);
            }
            return ref;
        }

        private static boolean isSimpleQualifiedRef(String ref) {
            int dot = ref.indexOf('.');
            return dot > 0
                    && dot == ref.lastIndexOf('.')
                    && isIdentifier(ref.substring(0, dot))
                    && isIdentifier(ref.substring(dot + 1));
        }

        private static boolean isIdentifier(String text) {
            if (text == null || text.isEmpty()) {
                return false;
            }
            char first = text.charAt(0);
            if (!(first == '_' || first == '$' || Character.isLetter(first))) {
                return false;
            }
            for (int i = 1; i < text.length(); i++) {
                char c = text.charAt(i);
                if (!(c == '_' || c == '$' || Character.isLetterOrDigit(c))) {
                    return false;
                }
            }
            return true;
        }
    }
}
