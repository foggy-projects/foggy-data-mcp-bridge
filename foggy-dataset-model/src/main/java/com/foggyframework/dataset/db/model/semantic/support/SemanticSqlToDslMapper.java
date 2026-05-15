package com.foggyframework.dataset.db.model.semantic.support;

import com.foggyframework.dataset.db.model.semantic.domain.SemanticRequestContext;
import com.foggyframework.dataset.db.model.spi.QueryModel;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.DateValue;
import net.sf.jsqlparser.expression.DoubleValue;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.Function;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.NullValue;
import net.sf.jsqlparser.expression.SignedExpression;
import net.sf.jsqlparser.expression.StringValue;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.conditional.OrExpression;
import net.sf.jsqlparser.expression.operators.relational.Between;
import net.sf.jsqlparser.expression.operators.relational.ComparisonOperator;
import net.sf.jsqlparser.expression.operators.relational.ExpressionList;
import net.sf.jsqlparser.expression.operators.relational.InExpression;
import net.sf.jsqlparser.expression.operators.relational.IsNullExpression;
import net.sf.jsqlparser.expression.operators.relational.ParenthesedExpressionList;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.AllColumns;
import net.sf.jsqlparser.statement.select.GroupByElement;
import net.sf.jsqlparser.statement.select.OrderByElement;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SelectItem;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Maps the accepted Virtual Semantic SQL v1 subset to auditable DSL-shaped evidence.
 *
 * <p>This class intentionally does not execute or compile SQL. It is a bounded
 * evaluation contract so we can measure whether SQL helps LLM planning before
 * wiring it into the engine runtime.</p>
 */
public final class SemanticSqlToDslMapper {

    private static final String STATUS_MAPPED = "MAPPED";
    private static final String STATUS_DEFERRED = "DEFERRED";

    private SemanticSqlToDslMapper() {
    }

    public static Map<String, Object> map(String requestedModel, String sql,
                                          QueryModel queryModel,
                                          SemanticRequestContext context) {
        MappingState state = new MappingState();
        Map<String, Object> plan = new LinkedHashMap<>();
        plan.put("version", "v1");
        plan.put("dialect", "foggy_virtual_semantic_sql_subset");
        plan.put("execution_enabled", false);

        PlainSelect select;
        try {
            Statement statement = CCJSqlParserUtil.parse(sql);
            if (!(statement instanceof Select parsedSelect) || !(parsedSelect instanceof PlainSelect plainSelect)) {
                state.defer("only single SELECT can be mapped to DSL v1 evidence");
                return finish(plan, state);
            }
            select = plainSelect;
        } catch (JSQLParserException ex) {
            state.defer("semantic SQL parse failed after whitelist validation");
            return finish(plan, state);
        }

        plan.put("from", fromModel(select, requestedModel));
        mapProjection(select, plan, state);
        mapWhere(select.getWhere(), plan, state);
        mapGroupBy(select.getGroupBy(), plan);
        mapHaving(select.getHaving(), plan, state);
        mapOrderBy(select.getOrderByElements(), plan);
        mapLimit(select, plan, state);
        detectControlledRelation(select, state);

        return finish(plan, state);
    }

    private static Map<String, Object> finish(Map<String, Object> plan, MappingState state) {
        plan.put("mapping_status", state.deferred ? STATUS_DEFERRED : STATUS_MAPPED);
        plan.put("unsupported", List.copyOf(state.unsupported));
        plan.put("requires_declared_relation", state.requiresDeclaredRelation);
        if (!state.relationReasons.isEmpty()) {
            plan.put("relation_control_reasons", List.copyOf(state.relationReasons));
        }
        return plan;
    }

    private static String fromModel(PlainSelect select, String fallback) {
        if (select.getFromItem() instanceof Table table) {
            return unquote(table.getName());
        }
        return fallback;
    }

    private static void mapProjection(PlainSelect select, Map<String, Object> plan, MappingState state) {
        List<String> columns = new ArrayList<>();
        List<Map<String, Object>> metrics = new ArrayList<>();
        if (select.getSelectItems() == null) {
            plan.put("columns", columns);
            plan.put("metrics", metrics);
            return;
        }
        for (SelectItem<?> item : select.getSelectItems()) {
            Expression expression = item.getExpression();
            String alias = item.getUnquotedAliasName();
            if (expression instanceof Column column) {
                columns.add(field(column));
            } else if (expression instanceof Function function) {
                metrics.add(metric(function, alias));
            } else if (expression instanceof AllColumns) {
                state.defer("wildcard projection is not mappable to DSL v1 evidence");
            } else {
                Map<String, Object> calculated = new LinkedHashMap<>();
                calculated.put("expr", expression.toString());
                if (alias != null) {
                    calculated.put("alias", alias);
                }
                calculated.put("mapping", "expression_projection");
                metrics.add(calculated);
            }
        }
        plan.put("columns", columns);
        plan.put("metrics", metrics);
    }

    private static Map<String, Object> metric(Function function, String alias) {
        Map<String, Object> metric = new LinkedHashMap<>();
        String functionName = function.getName().toUpperCase(Locale.ROOT);
        metric.put("agg", function.isDistinct() && "COUNT".equals(functionName) ? "COUNT_DISTINCT" : functionName);
        if (function.isAllColumns()) {
            metric.put("field", "*");
        } else if (function.getParameters() != null && !function.getParameters().isEmpty()) {
            Expression first = function.getParameters().get(0);
            metric.put("field", first instanceof Column column ? field(column) : Map.of("expr", first.toString()));
        }
        if (alias != null) {
            metric.put("alias", alias);
        }
        return metric;
    }

    private static void mapWhere(Expression where, Map<String, Object> plan, MappingState state) {
        if (where == null) {
            return;
        }
        plan.put("slice", mapPredicate(where, state));
    }

    private static void mapHaving(Expression having, Map<String, Object> plan, MappingState state) {
        if (having == null) {
            return;
        }
        plan.put("having", mapPredicate(having, state));
    }

    private static Map<String, Object> mapPredicate(Expression expression, MappingState state) {
        if (expression instanceof AndExpression andExpression) {
            return logical("$and", andExpression.getLeftExpression(), andExpression.getRightExpression(), state);
        }
        if (expression instanceof OrExpression orExpression) {
            return logical("$or", orExpression.getLeftExpression(), orExpression.getRightExpression(), state);
        }
        if (expression instanceof IsNullExpression isNullExpression) {
            return unaryFieldPredicate(isNullExpression.getLeftExpression(),
                    isNullExpression.isNot() ? "is not null" : "is null", state);
        }
        if (expression instanceof InExpression inExpression) {
            return inPredicate(inExpression, state);
        }
        if (expression instanceof Between between) {
            return betweenPredicate(between, state);
        }
        if (expression instanceof ComparisonOperator comparison) {
            return comparisonPredicate(comparison, state);
        }
        state.defer("unsupported predicate expression: " + expression.getClass().getSimpleName());
        return expressionPredicate(expression);
    }

    private static Map<String, Object> logical(String key, Expression left, Expression right, MappingState state) {
        Map<String, Object> logical = new LinkedHashMap<>();
        logical.put(key, List.of(mapPredicate(left, state), mapPredicate(right, state)));
        return logical;
    }

    private static Map<String, Object> unaryFieldPredicate(Expression left, String op, MappingState state) {
        if (left instanceof Column column) {
            return predicate(field(column), op, null);
        }
        state.defer("unary predicate left side is not a field: " + left);
        return expressionPredicate(left);
    }

    private static Map<String, Object> inPredicate(InExpression expression, MappingState state) {
        Expression left = expression.getLeftExpression();
        if (!(left instanceof Column column)) {
            state.defer("IN predicate left side is not a field: " + left);
            return expressionPredicate(expression);
        }
        Expression right = expression.getRightExpression();
        if (right instanceof ExpressionList<?> list) {
            List<Object> values = list.getExpressions().stream()
                    .map(SemanticSqlToDslMapper::literalValue)
                    .toList();
            return predicate(field(column), expression.isNot() ? "not in" : "in", values);
        }
        if (right instanceof ParenthesedExpressionList<?> list) {
            List<Object> values = list.getExpressions().stream()
                    .map(SemanticSqlToDslMapper::literalValue)
                    .toList();
            return predicate(field(column), expression.isNot() ? "not in" : "in", values);
        }
        state.defer("IN predicate right side is not a bounded literal list: " + right);
        return expressionPredicate(expression);
    }

    private static Map<String, Object> betweenPredicate(Between between, MappingState state) {
        if (!(between.getLeftExpression() instanceof Column column)) {
            state.defer("BETWEEN predicate left side is not a field: " + between.getLeftExpression());
            return expressionPredicate(between);
        }
        return predicate(field(column), between.isNot() ? "not between" : "between",
                List.of(literalValue(between.getBetweenExpressionStart()), literalValue(between.getBetweenExpressionEnd())));
    }

    private static Map<String, Object> comparisonPredicate(ComparisonOperator comparison, MappingState state) {
        Expression left = comparison.getLeftExpression();
        Expression right = comparison.getRightExpression();
        String op = comparison.getStringExpression().trim();
        if (left instanceof Column column) {
            return predicate(field(column), op, literalValue(right));
        }
        if (right instanceof Column column) {
            return predicate(field(column), reverse(op), literalValue(left));
        }
        Map<String, Object> predicate = new LinkedHashMap<>();
        predicate.put("expr", left.toString());
        predicate.put("op", op);
        predicate.put("value", literalValue(right));
        predicate.put("mapping", "expression_filter");
        return predicate;
    }

    private static Map<String, Object> predicate(String field, String op, Object value) {
        Map<String, Object> predicate = new LinkedHashMap<>();
        predicate.put("field", field);
        predicate.put("op", op);
        if (value != null) {
            predicate.put("value", value);
        }
        return predicate;
    }

    private static Map<String, Object> expressionPredicate(Expression expression) {
        Map<String, Object> predicate = new LinkedHashMap<>();
        predicate.put("expr", expression.toString());
        predicate.put("mapping", "expression_filter");
        return predicate;
    }

    private static void mapGroupBy(GroupByElement groupBy, Map<String, Object> plan) {
        if (groupBy == null || groupBy.getGroupByExpressionList() == null) {
            return;
        }
        List<Object> groupByFields = new ArrayList<>();
        groupBy.getGroupByExpressionList().forEach(expression -> {
            if (expression instanceof Column column) {
                groupByFields.add(field(column));
            } else {
                groupByFields.add(Map.of("expr", expression.toString()));
            }
        });
        plan.put("groupBy", groupByFields);
    }

    private static void mapOrderBy(List<OrderByElement> orderByElements, Map<String, Object> plan) {
        if (orderByElements == null || orderByElements.isEmpty()) {
            return;
        }
        List<Map<String, Object>> orderBy = new ArrayList<>();
        for (OrderByElement element : orderByElements) {
            Map<String, Object> item = new LinkedHashMap<>();
            Expression expression = element.getExpression();
            if (expression instanceof Column column) {
                item.put("field", field(column));
            } else {
                item.put("expr", expression.toString());
            }
            item.put("dir", element.isAsc() ? "asc" : "desc");
            orderBy.add(item);
        }
        plan.put("orderBy", orderBy);
    }

    private static void mapLimit(PlainSelect select, Map<String, Object> plan, MappingState state) {
        if (select.getLimit() == null || select.getLimit().getRowCount() == null) {
            return;
        }
        Object value = literalValue(select.getLimit().getRowCount());
        if (value instanceof Number number) {
            plan.put("limit", number.intValue());
        } else {
            state.defer("LIMIT is not a numeric literal: " + select.getLimit().getRowCount());
            plan.put("limit", value);
        }
    }

    private static Object literalValue(Expression expression) {
        if (expression instanceof StringValue value) {
            return value.getValue();
        }
        if (expression instanceof LongValue value) {
            return value.getValue();
        }
        if (expression instanceof DoubleValue value) {
            return value.getValue();
        }
        if (expression instanceof DateValue value) {
            return value.getValue().toString();
        }
        if (expression instanceof NullValue) {
            return null;
        }
        if (expression instanceof Column column) {
            return Map.of("fieldRef", field(column));
        }
        if (expression instanceof SignedExpression signedExpression) {
            Object nested = literalValue(signedExpression.getExpression());
            if (nested instanceof Long value && signedExpression.getSign() == '-') {
                return -value;
            }
            if (nested instanceof Double value && signedExpression.getSign() == '-') {
                return -value;
            }
        }
        return Map.of("expr", expression.toString());
    }

    private static void detectControlledRelation(PlainSelect select, MappingState state) {
        String sql = select.toString();
        if (sql.matches("(?is).*\\bCOUNT\\s*\\(\\s*DISTINCT\\s+[^)]*\\.[^)]*\\).*")
                || sql.matches("(?is).*\\b[A-Za-z_][A-Za-z0-9_]*\\.[A-Za-z_][A-Za-z0-9_]*\\s+IN\\s*\\([^)]*\\).*")) {
            state.requiresDeclaredRelation = true;
            state.defer("controlled relation predicate requires declared QM relation semantics");
            state.relationReasons.add("relation_membership_or_distinct_count");
        }
        if (sql.matches("(?is).*\\bNOT\\s+EXISTS\\b.*")) {
            state.requiresDeclaredRelation = true;
            state.defer("anti-existence predicate requires declared semantic relation");
            state.relationReasons.add("anti_existence");
        }
    }

    private static String reverse(String op) {
        return switch (op) {
            case ">" -> "<";
            case ">=" -> "<=";
            case "<" -> ">";
            case "<=" -> ">=";
            default -> op;
        };
    }

    private static String field(Column column) {
        return unquote(column.getFullyQualifiedName());
    }

    private static String unquote(String token) {
        if (token == null) {
            return null;
        }
        String[] parts = token.split("\\.");
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i].trim();
            if ((part.startsWith("\"") && part.endsWith("\"")) || (part.startsWith("`") && part.endsWith("`"))) {
                parts[i] = part.substring(1, part.length() - 1);
            } else {
                parts[i] = part;
            }
        }
        return String.join(".", parts);
    }

    private static final class MappingState {
        private final List<String> unsupported = new ArrayList<>();
        private final List<String> relationReasons = new ArrayList<>();
        private boolean deferred;
        private boolean requiresDeclaredRelation;

        private void defer(String reason) {
            deferred = true;
            unsupported.add(reason);
        }
    }
}
