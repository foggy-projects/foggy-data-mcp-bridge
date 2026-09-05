package com.foggyframework.dataset.model.semantic.support;

import com.foggyframework.dataset.model.semantic.domain.QueryInputWarning;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Inspects raw public Query DSL objects before permissive Jackson/manual mapping can discard keys.
 */
public final class SemanticQueryPropertyInspector {

    public static final String IGNORED_CODE = "UNKNOWN_QUERY_PROPERTY_IGNORED";
    public static final String STRICT_CODE = "UNKNOWN_QUERY_PROPERTY";
    public static final String DUPLICATE_CODE = "DUPLICATE_QUERY_PROPERTY";
    public static final String PROTECTED_CODE = "PROTECTED_QUERY_PROPERTY";

    private static final Set<String> ROOT = orderedSet(
            "columns", "calculatedFields", "slice", "having", "postSlice", "groupBy", "orderBy",
            "start", "limit", "cursor", "hints", "extData", "stream", "captionMatchMode",
            "mismatchHandleStrategy", "returnTotal", "distinct", "withSubtotals", "timeWindow",
            "postAggregateCalculations", "outputFormatting", "pivot", "route", "status",
            "risk_flags", "riskFlags", "clarifying_questions", "clarifyingQuestions", "why",
            "executable_plan", "executablePlan", "semantic_sql", "semanticSql",
            "memory_grid_plan", "memoryGridPlan", "grid_sql", "gridSql", "bindings",
            "memoryGridBindings");
    private static final Set<String> CALCULATED_FIELD = orderedSet(
            "name", "caption", "expression", "description", "agg", "partitionBy", "windowOrderBy",
            "windowFrame", "type", "emptyDefault");
    private static final Set<String> WINDOW_ORDER = orderedSet("field", "dir");
    private static final Set<String> SLICE = orderedSet(
            "$or", "$and", "$expr", "field", "op", "value", "maxDepth");
    private static final Set<String> GROUP_BY = orderedSet("field", "agg");
    private static final Set<String> ORDER_BY = orderedSet(
            "field", "column", "dir", "direction", "nullFirst", "nullLast");
    private static final Set<String> TIME_WINDOW = orderedSet(
            "field", "grain", "comparison", "range", "value", "targetMetrics", "rollingAggregator");
    private static final Set<String> POST_AGGREGATE = orderedSet(
            "name", "kind", "measure", "scope", "format");
    private static final Set<String> OUTPUT_FORMATTING = orderedSet(
            "field", "kind", "scale", "mode", "scope");
    private static final Set<String> MEMORY_GRID_BINDING = orderedSet(
            "alias", "result_handle", "resultHandle", "source_route", "sourceRoute", "metadata");
    private static final Set<String> PIVOT = orderedSet(
            "rows", "columns", "metrics", "properties", "options", "outputFormat", "layout");
    private static final Set<String> PIVOT_AXIS = orderedSet(
            "field", "orderBy", "limit", "start", "offset", "domainSlice", "slice", "having",
            "hierarchyMode", "expandDepth");
    private static final Set<String> METRIC_FILTER = orderedSet("metric", "op", "value");
    private static final Set<String> PIVOT_METRIC = orderedSet(
            "name", "expr", "type", "of", "axis", "level", "parentLevel", "baseline",
            "baselineScope", "denominatorScope");
    private static final Set<String> PIVOT_OPTIONS = orderedSet(
            "crossjoin", "rowSubtotals", "columnSubtotals", "grandTotal");
    private static final Set<String> PIVOT_LAYOUT = orderedSet("metricPlacement");

    private static final Set<String> PROTECTED_NAMES = orderedSet(
            "auth", "authentication", "authorization", "permission", "permissions", "namespace",
            "datasource", "model", "route", "routing", "mutation", "governance", "tenant", "principal",
            "security", "write", "delete", "update", "insert", "upsert");

    public List<QueryInputWarning> inspect(
            Map<String, ?> payload,
            UnknownQueryPropertyPolicy requestedPolicy
    ) {
        return inspect(payload, requestedPolicy, List.of());
    }

    public List<QueryInputWarning> inspect(
            Map<String, ?> payload,
            UnknownQueryPropertyPolicy requestedPolicy,
            List<DuplicateQueryProperty> duplicateProperties
    ) {
        if ((payload == null || payload.isEmpty())
                && (duplicateProperties == null || duplicateProperties.isEmpty())) {
            return List.of();
        }
        UnknownQueryPropertyPolicy policy = UnknownQueryPropertyPolicy.orDefault(requestedPolicy);
        Inspection inspection = new Inspection(policy);
        if (payload != null && !payload.isEmpty()) {
            inspectRoot(payload, inspection);
        }
        if (duplicateProperties != null) {
            duplicateProperties.forEach(inspection::addDuplicate);
        }
        if (!inspection.protectedViolations.isEmpty()) {
            List<QueryInputWarning> violations = new ArrayList<>(inspection.protectedViolations);
            if (policy == UnknownQueryPropertyPolicy.STRICT) {
                violations.addAll(inspection.ordinaryViolations);
            }
            throw new QueryInputValidationException(PROTECTED_CODE, violations);
        }
        if (policy == UnknownQueryPropertyPolicy.STRICT && !inspection.ordinaryViolations.isEmpty()) {
            String code = inspection.ordinaryViolations.stream()
                    .allMatch(warning -> DUPLICATE_CODE.equals(warning.code()))
                    ? DUPLICATE_CODE
                    : STRICT_CODE;
            throw new QueryInputValidationException(code, inspection.ordinaryViolations);
        }
        return policy == UnknownQueryPropertyPolicy.WARN
                ? List.copyOf(inspection.ordinaryViolations)
                : List.of();
    }

    private static void inspectRoot(Map<String, ?> payload, Inspection inspection) {
        inspectObject(payload, "$", ROOT, inspection);
        inspectObjectList(payload.get("calculatedFields"), "$.calculatedFields", CALCULATED_FIELD,
                inspection, (map, path, current) -> inspectObjectList(
                        map.get("windowOrderBy"), path + ".windowOrderBy", WINDOW_ORDER, current, null));
        inspectSlices(payload.get("slice"), "$.slice", inspection);
        inspectSlices(payload.get("having"), "$.having", inspection);
        inspectSlices(payload.get("postSlice"), "$.postSlice", inspection);
        inspectObjectList(payload.get("groupBy"), "$.groupBy", GROUP_BY, inspection, null);
        inspectObjectList(payload.get("orderBy"), "$.orderBy", ORDER_BY, inspection, null);
        inspectOptionalObject(payload.get("timeWindow"), "$.timeWindow", TIME_WINDOW, inspection, null);
        inspectObjectList(payload.get("postAggregateCalculations"), "$.postAggregateCalculations",
                POST_AGGREGATE, inspection, null);
        inspectObjectList(payload.get("outputFormatting"), "$.outputFormatting",
                OUTPUT_FORMATTING, inspection, null);
        Object bindings = payload.containsKey("bindings")
                ? payload.get("bindings")
                : payload.get("memoryGridBindings");
        inspectObjectList(bindings, "$.bindings", MEMORY_GRID_BINDING, inspection, null);
        inspectPivot(payload.get("pivot"), "$.pivot", inspection);
    }

    private static void inspectSlices(Object value, String path, Inspection inspection) {
        if (!(value instanceof List<?> items)) {
            return;
        }
        for (int i = 0; i < items.size(); i++) {
            Object item = items.get(i);
            if (!(item instanceof Map<?, ?> map)) {
                continue;
            }
            String itemPath = path + "[" + i + "]";
            if (isSliceShorthand(map)) {
                continue;
            }
            inspectObject(map, itemPath, SLICE, inspection);
            inspectSlices(map.get("$or"), itemPath + ".$or", inspection);
            inspectSlices(map.get("$and"), itemPath + ".$and", inspection);
        }
    }

    private static boolean isSliceShorthand(Map<?, ?> map) {
        if (map.size() != 1) {
            return false;
        }
        Object key = map.keySet().iterator().next();
        return key instanceof String text && !SLICE.contains(text);
    }

    private static void inspectPivot(Object value, String path, Inspection inspection) {
        if (!(value instanceof Map<?, ?> pivot)) {
            return;
        }
        inspectObject(pivot, path, PIVOT, inspection);
        inspectObjectList(pivot.get("rows"), path + ".rows", PIVOT_AXIS, inspection,
                SemanticQueryPropertyInspector::inspectPivotAxis);
        inspectObjectList(pivot.get("columns"), path + ".columns", PIVOT_AXIS, inspection,
                SemanticQueryPropertyInspector::inspectPivotAxis);
        inspectObjectList(pivot.get("metrics"), path + ".metrics", PIVOT_METRIC, inspection, null);
        inspectOptionalObject(pivot.get("options"), path + ".options", PIVOT_OPTIONS, inspection, null);
        inspectOptionalObject(pivot.get("layout"), path + ".layout", PIVOT_LAYOUT, inspection, null);
    }

    private static void inspectPivotAxis(Map<?, ?> axis, String path, Inspection inspection) {
        inspectSlices(axis.get("domainSlice"), path + ".domainSlice", inspection);
        inspectSlices(axis.get("slice"), path + ".slice", inspection);
        inspectObjectList(axis.get("having"), path + ".having", METRIC_FILTER, inspection, null);
    }

    private static void inspectOptionalObject(
            Object value,
            String path,
            Set<String> allowed,
            Inspection inspection,
            ObjectVisitor visitor
    ) {
        if (!(value instanceof Map<?, ?> map)) {
            return;
        }
        inspectObject(map, path, allowed, inspection);
        if (visitor != null) {
            visitor.visit(map, path, inspection);
        }
    }

    private static void inspectObjectList(
            Object value,
            String path,
            Set<String> allowed,
            Inspection inspection,
            ObjectVisitor visitor
    ) {
        if (!(value instanceof List<?> items)) {
            return;
        }
        for (int i = 0; i < items.size(); i++) {
            if (!(items.get(i) instanceof Map<?, ?> map)) {
                continue;
            }
            String itemPath = path + "[" + i + "]";
            inspectObject(map, itemPath, allowed, inspection);
            if (visitor != null) {
                visitor.visit(map, itemPath, inspection);
            }
        }
    }

    private static void inspectObject(
            Map<?, ?> object,
            String parentPath,
            Set<String> allowed,
            Inspection inspection
    ) {
        object.keySet().stream()
                .map(String::valueOf)
                .sorted(Comparator.naturalOrder())
                .filter(key -> !allowed.contains(key))
                .forEach(key -> inspection.add(parentPath, key, allowed));
    }

    private static boolean isProtected(String property) {
        String normalized = property == null
                ? ""
                : property.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        for (String protectedName : PROTECTED_NAMES) {
            if (normalized.contains(protectedName)
                    || (protectedName.length() >= 5 && editDistance(normalized, protectedName) <= 1)) {
                return true;
            }
        }
        return false;
    }

    private static int editDistance(String left, String right) {
        if (left.isEmpty()) {
            return right.length();
        }
        int[] previous = new int[right.length() + 1];
        for (int j = 0; j <= right.length(); j++) {
            previous[j] = j;
        }
        for (int i = 1; i <= left.length(); i++) {
            int[] current = new int[right.length() + 1];
            current[0] = i;
            for (int j = 1; j <= right.length(); j++) {
                int cost = left.charAt(i - 1) == right.charAt(j - 1) ? 0 : 1;
                current[j] = Math.min(
                        Math.min(current[j - 1] + 1, previous[j] + 1),
                        previous[j - 1] + cost);
            }
            previous = current;
        }
        return previous[right.length()];
    }

    private static String safeProperty(String property) {
        if (property == null) {
            return "null";
        }
        String safe = property.replaceAll("[\\p{Cntrl}]", "?");
        return safe.length() <= 128 ? safe : safe.substring(0, 128);
    }

    private static Set<String> orderedSet(String... values) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        java.util.Collections.addAll(result, values);
        return java.util.Collections.unmodifiableSet(result);
    }

    private interface ObjectVisitor {
        void visit(Map<?, ?> object, String path, Inspection inspection);
    }

    private static final class Inspection {
        private final UnknownQueryPropertyPolicy policy;
        private final List<QueryInputWarning> ordinaryViolations = new ArrayList<>();
        private final List<QueryInputWarning> protectedViolations = new ArrayList<>();

        private Inspection(UnknownQueryPropertyPolicy policy) {
            this.policy = policy;
        }

        private void add(String parentPath, String rawProperty, Set<String> allowed) {
            String property = safeProperty(rawProperty);
            boolean protectedProperty = isProtected(property);
            String code = protectedProperty
                    ? PROTECTED_CODE
                    : policy == UnknownQueryPropertyPolicy.WARN ? IGNORED_CODE : STRICT_CODE;
            String path = parentPath + "." + property;
            String action = parentPath.startsWith("$.groupBy") && "grain".equals(property)
                    ? "Use a model-defined time grain field; groupBy does not accept grain."
                    : "Remove the property or replace it with a supported Query DSL property.";
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("property", property);
            details.put("allowedProperties", List.copyOf(allowed));
            QueryInputWarning warning = new QueryInputWarning(
                    code,
                    path,
                    protectedProperty
                            ? "Unknown protected Query DSL property '" + property + "' was rejected."
                            : policy == UnknownQueryPropertyPolicy.WARN
                                    ? "Unknown Query DSL property '" + property
                                            + "' was ignored; query results may differ."
                                    : "Unknown Query DSL property '" + property + "' is not allowed.",
                    action,
                    false,
                    details);
            if (protectedProperty) {
                protectedViolations.add(warning);
            } else {
                ordinaryViolations.add(warning);
            }
        }

        private void addDuplicate(DuplicateQueryProperty duplicate) {
            if (duplicate == null) {
                return;
            }
            String property = safeProperty(duplicate.property());
            boolean protectedProperty = isProtected(property);
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("property", property);
            details.put("occurrences", Math.max(2, duplicate.occurrences()));
            QueryInputWarning warning = new QueryInputWarning(
                    protectedProperty ? PROTECTED_CODE : DUPLICATE_CODE,
                    duplicate.path(),
                    protectedProperty
                            ? "Duplicate protected Query DSL property '" + property + "' was rejected."
                            : policy == UnknownQueryPropertyPolicy.STRICT
                                    ? "Duplicate Query DSL property '" + property + "' is not allowed."
                                    : "Duplicate Query DSL property '" + property
                                            + "' was collapsed to its last occurrence.",
                    "Keep exactly one occurrence of the property.",
                    false,
                    details);
            if (protectedProperty) {
                protectedViolations.add(warning);
            } else {
                ordinaryViolations.add(warning);
            }
        }
    }
}
