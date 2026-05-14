package com.foggyframework.dataset.db.model.semantic.support;

import com.foggyframework.core.ex.RX;
import com.foggyframework.dataset.db.model.def.query.request.CalculatedFieldDef;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticRequestContext;
import com.foggyframework.dataset.db.model.spi.DbQueryColumn;
import com.foggyframework.dataset.db.model.spi.QueryModel;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Phase-1 guardrail for Virtual Semantic SQL.
 *
 * <p>This validator deliberately accepts only a small SQL surface and returns
 * structured AST evidence. It does not compile SQL to physical SQL.</p>
 */
public final class SemanticSqlWhitelistValidator {

    public static final String PHYSICAL_TABLE_DENIED = "SEMANTIC_SQL_PHYSICAL_TABLE_DENIED";
    public static final String FIELD_NOT_DECLARED = "SEMANTIC_SQL_FIELD_NOT_DECLARED";
    public static final String JOIN_NOT_DECLARED = "SEMANTIC_SQL_JOIN_NOT_DECLARED";
    public static final String FUNCTION_NOT_ALLOWED = "SEMANTIC_SQL_FUNCTION_NOT_ALLOWED";
    public static final String SENSITIVE_FIELD_DENIED = "SEMANTIC_SQL_SENSITIVE_FIELD_DENIED";

    private static final Pattern IDENTIFIER_PATTERN = Pattern.compile(
            "\\b[A-Za-z_][A-Za-z0-9_$]*(?:\\.[A-Za-z_][A-Za-z0-9_$]*)*\\b");
    private static final Pattern FUNCTION_PATTERN = Pattern.compile(
            "\\b([A-Za-z_][A-Za-z0-9_$]*)\\s*\\(");

    private static final Set<String> KEYWORDS = Set.of(
            "SELECT", "FROM", "WHERE", "GROUP", "BY", "HAVING", "ORDER", "LIMIT", "OFFSET",
            "AS", "AND", "OR", "NOT", "NULL", "IS", "IN", "LIKE", "BETWEEN", "DISTINCT",
            "CASE", "WHEN", "THEN", "ELSE", "END", "ASC", "DESC", "TRUE", "FALSE",
            "DATE", "INTERVAL", "CURRENT_DATE", "CURRENT_TIMESTAMP", "ON", "JOIN", "LEFT",
            "RIGHT", "FULL", "INNER", "OUTER", "CROSS", "UNION", "ALL", "EXISTS"
    );

    private static final Set<String> ALLOWED_FUNCTIONS = Set.of(
            "SUM", "COUNT", "COUNT_DISTINCT", "AVG", "MIN", "MAX",
            "DATE_DIFF", "DATEDIFF", "START_OF_QUARTER", "START_OF_NEXT_QUARTER",
            "START_OF_MONTH", "START_OF_NEXT_MONTH", "COALESCE", "ABS", "ROUND"
    );

    private SemanticSqlWhitelistValidator() {
    }

    public static Map<String, Object> validate(String requestedModel, String sql,
                                                QueryModel queryModel,
                                                SemanticRequestContext context) {
        if (isBlank(sql)) {
            throw RX.throwB(FIELD_NOT_DECLARED + ": semantic_sql must be non-empty.");
        }
        if (queryModel == null) {
            throw RX.throwB(PHYSICAL_TABLE_DENIED + ": virtual semantic model is not declared.");
        }

        String sanitized = stripComments(sql).trim();
        String upper = sanitized.toUpperCase(Locale.ROOT);
        if (!upper.startsWith("SELECT ")) {
            throw RX.throwB(FUNCTION_NOT_ALLOWED + ": only SELECT semantic SQL is allowed.");
        }

        ClauseBounds bounds = locateClauses(sanitized);
        validateNoWildcardProjection(bounds.selectClause(sanitized));
        String fromClause = bounds.fromClause(sanitized).trim();
        validateNoJoin(fromClause, sanitized);
        String fromModel = parseSingleFromModel(fromClause);
        validateVirtualModel(requestedModel, queryModel, fromModel);

        Set<String> declaredFields = declaredFields(queryModel);
        List<String> referencedFields = referencedFields(sanitized);
        Set<String> uniqueFields = new LinkedHashSet<>();
        for (String field : referencedFields) {
            if (field.equals(fromModel)) {
                continue;
            }
            if (!declaredFields.contains(field)) {
                throw RX.throwB(FIELD_NOT_DECLARED + ": field '" + field + "' is not declared by TM/QM.");
            }
            if (!isFieldAllowed(field, context)) {
                throw RX.throwB(SENSITIVE_FIELD_DENIED + ": field '" + field + "' is denied by semantic field access policy.");
            }
            uniqueFields.add(field);
        }

        List<String> functions = referencedFunctions(sanitized);
        for (String function : functions) {
            if (!ALLOWED_FUNCTIONS.contains(function)) {
                throw RX.throwB(FUNCTION_NOT_ALLOWED + ": function '" + function + "' is not allowed.");
            }
        }

        Map<String, Object> ast = new LinkedHashMap<>();
        ast.put("from", List.of(Map.of("name", fromModel, "kind", "virtual_semantic_model")));
        ast.put("fields", uniqueFields.stream()
                .map(field -> Map.<String, Object>of("name", field, "source", "tm_qm"))
                .toList());
        ast.put("functions", functions.stream()
                .map(function -> Map.<String, Object>of("name", function, "allowed", true))
                .toList());
        ast.put("joins", inferredSemanticRelations(requestedModel, uniqueFields));
        ast.put("denied", List.of());
        return ast;
    }

    private static void validateNoJoin(String fromClause, String sql) {
        String upperFrom = " " + fromClause.toUpperCase(Locale.ROOT) + " ";
        String upperSql = " " + sql.toUpperCase(Locale.ROOT) + " ";
        if (upperFrom.contains(" JOIN ") || upperFrom.contains(",")
                || upperSql.contains(" JOIN ") || upperSql.contains(" ON ")) {
            throw RX.throwB(JOIN_NOT_DECLARED + ": free SQL JOIN is not declared for Semantic SQL phase 1.");
        }
    }

    private static void validateNoWildcardProjection(String selectClause) {
        String withoutCountStar = selectClause.replaceAll("(?i)\\bCOUNT\\s*\\(\\s*\\*\\s*\\)", "COUNT(__ALL__)");
        if (withoutCountStar.contains("*")) {
            throw RX.throwB(FIELD_NOT_DECLARED + ": wildcard projection is not allowed by Semantic SQL phase 1.");
        }
    }

    private static String parseSingleFromModel(String fromClause) {
        if (isBlank(fromClause)) {
            throw RX.throwB(PHYSICAL_TABLE_DENIED + ": missing virtual semantic model in FROM.");
        }
        String[] parts = fromClause.trim().split("\\s+");
        if (parts.length != 1) {
            throw RX.throwB(JOIN_NOT_DECLARED + ": table aliases and multiple FROM entries are not allowed in phase 1.");
        }
        return unquote(parts[0]);
    }

    private static void validateVirtualModel(String requestedModel, QueryModel queryModel, String fromModel) {
        Set<String> allowed = new LinkedHashSet<>();
        if (!isBlank(requestedModel)) {
            allowed.add(requestedModel);
        }
        if (!isBlank(queryModel.getName())) {
            allowed.add(queryModel.getName());
        }
        if (!isBlank(queryModel.getShortAlias())) {
            allowed.add(queryModel.getShortAlias());
        }
        boolean matched = allowed.stream().anyMatch(name -> name.equalsIgnoreCase(fromModel));
        if (!matched) {
            throw RX.throwB(PHYSICAL_TABLE_DENIED + ": FROM '" + fromModel + "' is not the declared virtual semantic model.");
        }
    }

    private static Set<String> declaredFields(QueryModel queryModel) {
        Set<String> fields = new LinkedHashSet<>();
        if (queryModel.getJdbcQueryColumns() != null) {
            for (DbQueryColumn column : queryModel.getJdbcQueryColumns()) {
                if (!isBlank(column.getName())) {
                    fields.add(column.getName());
                }
            }
        }
        if (queryModel.getPredefinedCalculatedFields() != null) {
            for (CalculatedFieldDef calc : queryModel.getPredefinedCalculatedFields()) {
                if (!isBlank(calc.getName())) {
                    fields.add(calc.getName());
                }
            }
        }
        return fields;
    }

    private static List<String> referencedFields(String sql) {
        String noStrings = maskStringLiterals(sql);
        Matcher matcher = IDENTIFIER_PATTERN.matcher(noStrings);
        List<String> fields = new ArrayList<>();
        String previous = null;
        while (matcher.find()) {
            String token = unquote(matcher.group());
            String upper = token.toUpperCase(Locale.ROOT);
            int next = nextNonWhitespace(noStrings, matcher.end());
            if (KEYWORDS.contains(upper)) {
                previous = upper;
                continue;
            }
            if (next < noStrings.length() && noStrings.charAt(next) == '(') {
                previous = upper;
                continue;
            }
            if ("AS".equals(previous)) {
                previous = upper;
                continue;
            }
            fields.add(token);
            previous = upper;
        }
        return fields;
    }

    private static List<String> referencedFunctions(String sql) {
        String noStrings = maskStringLiterals(sql);
        Matcher matcher = FUNCTION_PATTERN.matcher(noStrings);
        Set<String> functions = new LinkedHashSet<>();
        while (matcher.find()) {
            String function = matcher.group(1).toUpperCase(Locale.ROOT);
            if (!KEYWORDS.contains(function)) {
                functions.add(function);
            }
        }
        return new ArrayList<>(functions);
    }

    private static boolean isFieldAllowed(String field, SemanticRequestContext context) {
        if (context == null || context.getFieldAccess() == null) {
            return true;
        }
        return context.getFieldAccess().contains(field);
    }

    private static List<Map<String, Object>> inferredSemanticRelations(String model, Set<String> fields) {
        if (fields.isEmpty()) {
            return List.of();
        }
        Set<String> relations = new LinkedHashSet<>();
        for (String field : fields) {
            int dot = field.indexOf('.');
            if (dot > 0) {
                relations.add((isBlank(model) ? "" : model + ".") + field.substring(0, dot));
            }
        }
        if (relations.isEmpty()) {
            return List.of();
        }
        return relations.stream()
                .map(relation -> Map.<String, Object>of("relationship", relation, "declared", true))
                .toList();
    }

    private static ClauseBounds locateClauses(String sql) {
        int from = findTopLevelKeyword(sql, "FROM", 0);
        if (from < 0) {
            throw RX.throwB(PHYSICAL_TABLE_DENIED + ": missing FROM virtual semantic model.");
        }
        int where = findTopLevelKeyword(sql, "WHERE", from + 4);
        int group = findTopLevelKeyword(sql, "GROUP BY", from + 4);
        int having = findTopLevelKeyword(sql, "HAVING", from + 4);
        int order = findTopLevelKeyword(sql, "ORDER BY", from + 4);
        int limit = findTopLevelKeyword(sql, "LIMIT", from + 4);
        int end = minPositive(where, group, having, order, limit, sql.length());
        return new ClauseBounds(from, end);
    }

    private static int findTopLevelKeyword(String sql, String keyword, int start) {
        String upper = sql.toUpperCase(Locale.ROOT);
        String target = keyword.toUpperCase(Locale.ROOT);
        int depth = 0;
        boolean inString = false;
        for (int i = Math.max(0, start); i <= upper.length() - target.length(); i++) {
            char ch = upper.charAt(i);
            if (ch == '\'') {
                inString = !inString;
                continue;
            }
            if (inString) {
                continue;
            }
            if (ch == '(') {
                depth++;
                continue;
            }
            if (ch == ')') {
                depth = Math.max(0, depth - 1);
                continue;
            }
            if (depth == 0 && upper.startsWith(target, i)
                    && isBoundary(upper, i - 1)
                    && isBoundary(upper, i + target.length())) {
                return i;
            }
        }
        return -1;
    }

    private static int minPositive(int... values) {
        int min = Integer.MAX_VALUE;
        for (int value : values) {
            if (value >= 0 && value < min) {
                min = value;
            }
        }
        return min == Integer.MAX_VALUE ? values[values.length - 1] : min;
    }

    private static boolean isBoundary(String text, int index) {
        if (index < 0 || index >= text.length()) {
            return true;
        }
        char ch = text.charAt(index);
        return !Character.isLetterOrDigit(ch) && ch != '_' && ch != '$';
    }

    private static int nextNonWhitespace(String text, int index) {
        int i = index;
        while (i < text.length() && Character.isWhitespace(text.charAt(i))) {
            i++;
        }
        return i;
    }

    private static String stripComments(String sql) {
        return sql.replaceAll("(?s)/\\*.*?\\*/", " ")
                .replaceAll("(?m)--.*$", " ");
    }

    private static String maskStringLiterals(String sql) {
        StringBuilder out = new StringBuilder(sql.length());
        boolean inString = false;
        for (int i = 0; i < sql.length(); i++) {
            char ch = sql.charAt(i);
            if (ch == '\'') {
                inString = !inString;
                out.append(' ');
            } else {
                out.append(inString ? ' ' : ch);
            }
        }
        return out.toString();
    }

    private static String unquote(String token) {
        if (token == null) {
            return null;
        }
        String trimmed = token.trim();
        if ((trimmed.startsWith("\"") && trimmed.endsWith("\""))
                || (trimmed.startsWith("`") && trimmed.endsWith("`"))) {
            return trimmed.substring(1, trimmed.length() - 1);
        }
        return trimmed;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record ClauseBounds(int fromIndex, int fromEndIndex) {
        String selectClause(String sql) {
            return sql.substring("SELECT".length(), fromIndex);
        }

        String fromClause(String sql) {
            return sql.substring(fromIndex + "FROM".length(), fromEndIndex);
        }
    }
}
