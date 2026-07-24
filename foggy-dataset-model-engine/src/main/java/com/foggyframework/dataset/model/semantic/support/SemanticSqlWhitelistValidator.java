package com.foggyframework.dataset.model.semantic.support;

import com.foggyframework.core.ex.RX;
import com.foggyframework.dataset.model.def.query.request.CalculatedFieldDef;
import com.foggyframework.dataset.model.semantic.domain.SemanticRequestContext;
import com.foggyframework.dataset.model.spi.DbQueryColumn;
import com.foggyframework.dataset.model.spi.QueryModel;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.ExpressionVisitorAdapter;
import net.sf.jsqlparser.expression.Function;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.AllColumns;
import net.sf.jsqlparser.statement.select.FromItem;
import net.sf.jsqlparser.statement.select.GroupByElement;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SelectItem;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Phase-1 guardrail for Virtual Semantic SQL.
 *
 * <p>This validator accepts only a small SQL surface and returns structured
 * AST evidence. It validates SQL syntax and statement shape through JSqlParser,
 * but it does not compile semantic SQL to physical SQL.</p>
 */
public final class SemanticSqlWhitelistValidator {

    public static final String PHYSICAL_TABLE_DENIED = "SEMANTIC_SQL_PHYSICAL_TABLE_DENIED";
    public static final String FIELD_NOT_DECLARED = "SEMANTIC_SQL_FIELD_NOT_DECLARED";
    public static final String JOIN_NOT_DECLARED = "SEMANTIC_SQL_JOIN_NOT_DECLARED";
    public static final String FUNCTION_NOT_ALLOWED = "SEMANTIC_SQL_FUNCTION_NOT_ALLOWED";
    public static final String SENSITIVE_FIELD_DENIED = "SEMANTIC_SQL_SENSITIVE_FIELD_DENIED";

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

        PlainSelect select = parsePlainSelect(stripComments(sql).trim());
        validatePlainSelectShape(select);
        if (select.getJoins() != null && !select.getJoins().isEmpty()) {
            throw RX.throwB(JOIN_NOT_DECLARED + ": free SQL JOIN is not declared for Semantic SQL phase 1.");
        }

        String fromModel = parseSingleFromModel(select.getFromItem());
        validateVirtualModel(requestedModel, queryModel, fromModel);

        References references = collectReferences(select);
        if (references.wildcardProjection()) {
            throw RX.throwB(FIELD_NOT_DECLARED + ": wildcard projection is not allowed by Semantic SQL phase 1.");
        }
        if (references.subquery()) {
            throw RX.throwB(JOIN_NOT_DECLARED + ": subqueries are not declared for Semantic SQL phase 1.");
        }

        Set<String> declaredFields = declaredFields(queryModel);
        Set<String> uniqueFields = new LinkedHashSet<>();
        for (String field : references.fields()) {
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

        for (String function : references.functions()) {
            if (!ALLOWED_FUNCTIONS.contains(function)) {
                throw RX.throwB(FUNCTION_NOT_ALLOWED + ": function '" + function + "' is not allowed.");
            }
        }

        Map<String, Object> ast = new LinkedHashMap<>();
        ast.put("from", List.of(Map.of("name", fromModel, "kind", "virtual_semantic_model")));
        ast.put("fields", uniqueFields.stream()
                .map(field -> Map.<String, Object>of("name", field, "source", "tm_qm"))
                .toList());
        ast.put("functions", references.functions().stream()
                .map(function -> Map.<String, Object>of("name", function, "allowed", true))
                .toList());
        ast.put("joins", inferredSemanticRelations(requestedModel, uniqueFields));
        ast.put("denied", List.of());
        return ast;
    }

    private static PlainSelect parsePlainSelect(String sql) {
        if (isBlank(sql)) {
            throw RX.throwB(FIELD_NOT_DECLARED + ": semantic_sql must be non-empty.");
        }
        Statement statement;
        try {
            statement = CCJSqlParserUtil.parse(sql);
        } catch (JSQLParserException ex) {
            throw RX.throwB(FUNCTION_NOT_ALLOWED + ": semantic_sql is not valid SQL.");
        }
        if (!(statement instanceof Select select)) {
            throw RX.throwB(FUNCTION_NOT_ALLOWED + ": only SELECT semantic SQL is allowed.");
        }
        if (select.getWithItemsList() != null && !select.getWithItemsList().isEmpty()) {
            throw RX.throwB(JOIN_NOT_DECLARED + ": CTE is not declared for Semantic SQL phase 1.");
        }
        if (!(select instanceof PlainSelect plainSelect)) {
            throw RX.throwB(JOIN_NOT_DECLARED + ": only single SELECT semantic SQL is allowed in phase 1.");
        }
        return plainSelect;
    }

    private static void validatePlainSelectShape(PlainSelect select) {
        if (select.getIntoTables() != null && !select.getIntoTables().isEmpty()) {
            throw RX.throwB(FUNCTION_NOT_ALLOWED + ": SELECT INTO output tables are not allowed in Semantic SQL phase 1.");
        }
    }

    private static String parseSingleFromModel(FromItem fromItem) {
        if (!(fromItem instanceof Table table)) {
            throw RX.throwB(PHYSICAL_TABLE_DENIED + ": missing virtual semantic model in FROM.");
        }
        if (table.getAlias() != null) {
            throw RX.throwB(JOIN_NOT_DECLARED + ": table aliases and multiple FROM entries are not allowed in phase 1.");
        }
        if (!isBlank(table.getSchemaName()) || !isBlank(table.getCatalogName()) || !isBlank(table.getDatabaseName())) {
            throw RX.throwB(PHYSICAL_TABLE_DENIED + ": FROM '" + table.getFullyQualifiedName()
                    + "' is not the declared virtual semantic model.");
        }
        return unquote(table.getName());
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

    private static References collectReferences(PlainSelect select) {
        AstReferenceCollector collector = new AstReferenceCollector();
        if (select.getSelectItems() != null) {
            for (SelectItem<?> item : select.getSelectItems()) {
                Expression expression = item.getExpression();
                if (expression instanceof AllColumns) {
                    collector.wildcardProjection = true;
                    continue;
                }
                collectExpression(expression, collector);
            }
        }
        collectExpression(select.getWhere(), collector);
        collectGroupBy(select.getGroupBy(), collector);
        collectExpression(select.getHaving(), collector);
        collectExpression(select.getQualify(), collector);
        if (select.getOrderByElements() != null) {
            select.getOrderByElements().forEach(orderBy -> collectExpression(orderBy.getExpression(), collector));
        }
        return new References(collector.fields, collector.functions, collector.wildcardProjection, collector.subquery);
    }

    private static void collectGroupBy(GroupByElement groupBy, AstReferenceCollector collector) {
        if (groupBy == null || groupBy.getGroupByExpressionList() == null) {
            return;
        }
        groupBy.getGroupByExpressionList().forEach(expression -> collectExpression((Expression) expression, collector));
    }

    private static void collectExpression(Expression expression, AstReferenceCollector collector) {
        if (expression != null) {
            expression.accept(collector, null);
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

    private static String stripComments(String sql) {
        return sql.replaceAll("(?s)/\\*.*?\\*/", " ")
                .replaceAll("(?m)--.*$", " ");
    }

    private static String unquoteQualified(String token) {
        if (token == null) {
            return null;
        }
        String[] parts = token.split("\\.");
        for (int i = 0; i < parts.length; i++) {
            parts[i] = unquote(parts[i]);
        }
        return String.join(".", parts);
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

    private static final class AstReferenceCollector extends ExpressionVisitorAdapter<Void> {
        private final Set<String> fields = new LinkedHashSet<>();
        private final Set<String> functions = new LinkedHashSet<>();
        private boolean wildcardProjection;
        private boolean subquery;

        @Override
        public <S> Void visit(Column column, S context) {
            fields.add(unquoteQualified(column.getFullyQualifiedName()));
            return null;
        }

        @Override
        public <S> Void visit(Function function, S context) {
            String functionName = function.getName().toUpperCase(Locale.ROOT);
            functions.add(functionName);
            if (function.getParameters() != null
                    && function.getParameters().stream().anyMatch(AllColumns.class::isInstance)
                    && !"COUNT".equals(functionName)) {
                wildcardProjection = true;
            }
            return super.visit(function, context);
        }

        @Override
        public <S> Void visit(Select select, S context) {
            subquery = true;
            return null;
        }
    }

    private record References(Set<String> fields,
                              Set<String> functions,
                              boolean wildcardProjection,
                              boolean subquery) {
    }
}
