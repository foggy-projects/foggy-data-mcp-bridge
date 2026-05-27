package com.foggyframework.dataset.db.model.impl.model;

import com.foggyframework.core.ex.RX;
import com.foggyframework.core.trans.ObjectTransFormatter;
import com.foggyframework.dataset.db.model.impl.AiObject;
import com.foggyframework.dataset.db.model.impl.utils.ViewSqlQueryObject;
import com.foggyframework.dataset.db.model.proxy.AggregateJoinBuilder;
import com.foggyframework.dataset.db.model.proxy.AggregateRelationProxy;
import com.foggyframework.dataset.db.model.proxy.ColumnRef;
import com.foggyframework.dataset.db.model.proxy.JoinBuilder;
import com.foggyframework.dataset.db.model.proxy.JoinCondition;
import com.foggyframework.dataset.db.model.proxy.TableModelProxy;
import com.foggyframework.dataset.db.model.spi.DbAggregation;
import com.foggyframework.dataset.db.model.spi.DbColumn;
import com.foggyframework.dataset.db.model.spi.DbColumnType;
import com.foggyframework.dataset.db.model.spi.DbMeasure;
import com.foggyframework.dataset.db.model.spi.DbMeasureColumn;
import com.foggyframework.dataset.db.model.spi.DbModelType;
import com.foggyframework.dataset.db.model.spi.QueryObject;
import com.foggyframework.dataset.db.model.spi.TableModel;
import com.foggyframework.dataset.db.model.spi.support.SimpleSqlJdbcColumn;
import com.foggyframework.dataset.db.table.SqlColumn;
import com.foggyframework.dataset.db.table.SqlTable;
import jakarta.persistence.criteria.JoinType;

import java.sql.Types;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * aggregate join / aggregate relation 运行时合成模型。
 *
 * <p>它把右侧 TM 渲染为一个受控聚合子查询，并暴露 group key 与聚合指标作为可选列。
 */
public class AggregateJoinTableModel extends TableModelSupport {

    private static final Pattern SIMPLE_IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    private static final String SOURCE_ALIAS = "agg_src";

    private final TableModel sourceModel;

    private AggregateJoinTableModel(TableModel sourceModel, AggregateRelationInput input) {
        this.sourceModel = sourceModel;
        validateInput(input);

        this.name = sourceModel.getName();
        this.caption = sourceModel.getCaption() + "聚合关联";
        this.description = "Aggregate relation generated from " + sourceModel.getName();
        this.tableName = sourceModel.getTableName();
        this.modelType = DbModelType.jdbc;

        SqlTable sqlTable = new SqlTable(sourceModel.getName() + "_aggregate_join", this.caption);
        List<OutputColumn> outputColumns = buildOutputColumns(sqlTable, input);
        this.idColumn = outputColumns.stream()
                .filter(OutputColumn::groupKey)
                .map(OutputColumn::outputAlias)
                .findFirst()
                .orElse(null);
        if (this.idColumn != null) {
            sqlTable.setIdColumn(sqlTable.getSqlColumn(this.idColumn, true));
        }

        GeneratedAggregateRelationQueryObject aggregateQueryObject = buildAggregateQueryObject(sqlTable, input, outputColumns);
        aggregateQueryObject.setName(sourceModel.getName() + "_aggregate_join");
        aggregateQueryObject.setCaption(this.caption);
        aggregateQueryObject.setAlias(input.right().getEffectiveAlias());
        aggregateQueryObject.setPrimaryKey(this.idColumn);
        this.queryObject = aggregateQueryObject;

        registerOutputColumns(outputColumns);
    }

    public static AggregateJoinTableModel from(TableModel sourceModel, AggregateJoinBuilder builder) {
        return new AggregateJoinTableModel(sourceModel, new AggregateRelationInput(
                builder.getRight(),
                builder.getLeft(),
                builder.getJoinType(),
                builder.getConditions(),
                builder.getGroupByColumns(),
                builder.getMeasures(),
                builder.getFilters()));
    }

    public static AggregateJoinTableModel from(TableModel sourceModel, AggregateRelationProxy relationProxy,
                                               JoinBuilder joinBuilder) {
        return new AggregateJoinTableModel(sourceModel, new AggregateRelationInput(
                relationProxy,
                joinBuilder.getLeft(),
                joinBuilder.getJoinType(),
                joinBuilder.getConditions(),
                relationProxy.getGroupByColumns(),
                buildDefaultMeasures(sourceModel),
                relationProxy.getFilters()));
    }

    @Override
    public List<DbColumn> getVisibleSelectColumns() {
        return columns;
    }

    private void validateInput(AggregateRelationInput input) {
        if (input.joinType() != JoinType.LEFT) {
            throw RX.throwAUserTip("aggregate relation 首版仅支持 LEFT JOIN");
        }
        if (input.conditions().isEmpty()) {
            throw RX.throwAUserTip("aggregate relation 必须声明 on 条件");
        }
        if (input.groupByColumns().isEmpty()) {
            throw RX.throwAUserTip("aggregate relation 必须声明 groupBy 字段");
        }
        if (input.measures().isEmpty()) {
            throw RX.throwAUserTip("aggregate relation 必须至少暴露一个可聚合指标");
        }
        validateJoinKeysCoveredByGroupBy(input);
    }

    private void validateJoinKeysCoveredByGroupBy(AggregateRelationInput input) {
        Set<String> groupByRefs = input.groupByColumns().stream()
                .flatMap(columnRef -> columnRefKeys(columnRef).stream())
                .collect(Collectors.toSet());

        for (JoinCondition condition : input.conditions()) {
            if (!"=".equals(condition.getOperator())) {
                throw RX.throwAUserTip("aggregate relation on 条件仅支持等值 join key");
            }
            ColumnRef leftRef = condition.getLeft();
            if (!input.left().getModelName().equals(leftRef.getModelName())) {
                throw RX.throwAUserTip("aggregate relation on 左侧字段必须来自左表模型: " + input.left().getModelName());
            }

            ColumnRef rightRef = condition.getRightAsColumnRef();
            if (rightRef == null) {
                throw RX.throwAUserTip("aggregate relation on 右侧必须是字段引用；固定条件请使用 filterEq/filterIn");
            }
            if (!input.right().getModelName().equals(rightRef.getModelName())) {
                throw RX.throwAUserTip("aggregate relation on 右侧字段必须来自右表模型: " + input.right().getModelName());
            }
            if (columnRefKeys(rightRef).stream().noneMatch(groupByRefs::contains)) {
                throw RX.throwAUserTip("aggregate relation groupBy 必须覆盖右侧 join key: " + rightRef.getFullRef());
            }
        }
    }

    private Set<String> columnRefKeys(ColumnRef columnRef) {
        Set<String> keys = new LinkedHashSet<>();
        keys.add(columnRef.getFullRef());
        keys.add(columnRef.getAliasRef());
        keys.add(columnRef.getColumnName());
        return keys;
    }

    private List<OutputColumn> buildOutputColumns(SqlTable sqlTable, AggregateRelationInput input) {
        List<OutputColumn> outputColumns = new ArrayList<>();

        for (ColumnRef groupByColumn : input.groupByColumns()) {
            DbColumn sourceColumn = resolveSourceColumn(groupByColumn);
            String alias = validateOutputAlias(groupByColumn.getAliasRef(), "groupBy");
            String caption = outputCaption(alias, sourceColumn);
            SqlColumn sqlColumn = cloneSqlColumn(alias, caption, sourceColumn.getSqlColumn());
            sqlTable.addSqlColumn(sqlColumn);
            String sourceExpression = sourceColumnSql(sourceColumn);
            outputColumns.add(new OutputColumn(true, groupByColumn.getFullRef(), groupByColumn.getAliasRef(), alias,
                    caption, sourceColumn, sqlColumn, null, sourceExpression, null));
        }

        for (AggregateJoinBuilder.AggregateMeasure measure : input.measures()) {
            String alias = validateOutputAlias(measure.getAlias(), "measure");
            DbColumn sourceColumn = measure.getColumn() == null ? null : resolveSourceColumn(measure.getColumn());
            String caption = outputCaption(alias, sourceColumn);
            SqlColumn sqlColumn = buildMeasureSqlColumn(alias, measure, sourceColumn);
            String sourceExpression = sourceColumn == null ? null : sourceColumnSql(sourceColumn);
            String aggregateExpression = renderAggregateExpression(measure);
            sqlTable.addSqlColumn(sqlColumn);
            outputColumns.add(new OutputColumn(false, alias, alias, alias, caption, sourceColumn, sqlColumn, measure,
                    sourceExpression, aggregateExpression));
        }

        return outputColumns;
    }

    private void registerOutputColumns(List<OutputColumn> outputColumns) {
        this.columns = new ArrayList<>();
        this.name2JdbcColumn.clear();

        for (OutputColumn outputColumn : outputColumns) {
            DbAggregation aggregation = outputColumn.measure() == null
                    ? null
                    : toDbAggregation(outputColumn.measure().getFunction());
            DbColumnType type = resolveOutputType(outputColumn.measure(), outputColumn.sourceColumn());
            SimpleSqlJdbcColumn dbColumn = new AggregateOutputDbColumn(
                    queryObject,
                    outputColumn.sqlColumn(),
                    outputColumn.outputAlias(),
                    outputColumn.logicalName(),
                    outputColumn.caption(),
                    aggregation,
                    type,
                    outputColumn.groupKey(),
                    outputColumn.sourceColumn(),
                    outputColumn.sourceExpression(),
                    outputColumn.aggregateExpression());
            columns.add(dbColumn);
            name2JdbcColumn.put(outputColumn.logicalName(), dbColumn);
            name2JdbcColumn.put(outputColumn.aliasRef(), dbColumn);
            name2JdbcColumn.put(outputColumn.outputAlias(), dbColumn);
        }
    }

    private GeneratedAggregateRelationQueryObject buildAggregateQueryObject(SqlTable sqlTable,
                                                                           AggregateRelationInput input,
                                                                           List<OutputColumn> outputColumns) {
        List<String> selectParts = new ArrayList<>();
        List<String> groupByParts = new ArrayList<>();

        for (OutputColumn outputColumn : outputColumns) {
            if (!outputColumn.groupKey()) {
                continue;
            }
            selectParts.add(outputColumn.sourceExpression() + " " + outputColumn.outputAlias());
            groupByParts.add(outputColumn.sourceExpression());
        }

        for (OutputColumn outputColumn : outputColumns) {
            if (outputColumn.groupKey()) {
                continue;
            }
            selectParts.add(outputColumn.aggregateExpression() + " " + outputColumn.outputAlias());
        }

        return new GeneratedAggregateRelationQueryObject(
                sqlTable,
                selectParts,
                sourceBody(),
                renderFilterSql(input.filters()),
                groupByParts,
                buildJoinKeyPushdownMappings(input));
    }

    private String renderAggregateExpression(AggregateJoinBuilder.AggregateMeasure measure) {
        AggregateJoinBuilder.AggregateFunction function = measure.getFunction();
        if (function == AggregateJoinBuilder.AggregateFunction.COUNT && measure.getColumn() == null) {
            return "count(*)";
        }

        DbColumn sourceColumn = resolveSourceColumn(measure.getColumn());
        String columnSql = sourceColumnSql(sourceColumn);
        if (function == AggregateJoinBuilder.AggregateFunction.COUNT_DISTINCT) {
            return "count(distinct " + columnSql + ")";
        }
        return function.name().toLowerCase() + "(" + columnSql + ")";
    }

    private String renderFilterSql(List<AggregateJoinBuilder.AggregateFilter> filters) {
        if (filters == null || filters.isEmpty()) {
            return "";
        }
        return filters.stream()
                .map(this::renderFilter)
                .collect(Collectors.joining(" and "));
    }

    private String renderFilter(AggregateJoinBuilder.AggregateFilter filter) {
        DbColumn sourceColumn = resolveSourceColumn(filter.getColumn());
        String columnSql = sourceColumnSql(sourceColumn);
        if (filter.isMultiValue()) {
            Collection<?> values = filter.getValue() instanceof Collection<?> collection ? collection : List.of(filter.getValue());
            if (values.isEmpty()) {
                return "1 = 0";
            }
            String renderedValues = values.stream()
                    .map(this::renderLiteral)
                    .collect(Collectors.joining(", "));
            return columnSql + " in (" + renderedValues + ")";
        }

        if (filter.getValue() == null) {
            return switch (filter.getOperator()) {
                case "=" -> columnSql + " is null";
                case "<>" -> columnSql + " is not null";
                default -> columnSql + " " + filter.getOperator() + " null";
            };
        }
        return columnSql + " " + filter.getOperator() + " " + renderLiteral(filter.getValue());
    }

    private List<JoinKeyPushdownMapping> buildJoinKeyPushdownMappings(AggregateRelationInput input) {
        List<JoinKeyPushdownMapping> mappings = new ArrayList<>();
        for (JoinCondition condition : input.conditions()) {
            ColumnRef rightRef = condition.getRightAsColumnRef();
            DbColumn rightColumn = resolveSourceColumn(rightRef);
            mappings.add(new JoinKeyPushdownMapping(
                    columnRefKeys(condition.getLeft()),
                    sourceColumnSql(rightColumn)));
        }
        return mappings;
    }

    private DbColumn resolveSourceColumn(ColumnRef columnRef) {
        if (columnRef == null) {
            return null;
        }
        DbColumn dbColumn = sourceModel.findJdbcColumnByName(columnRef.getFullRef());
        if (dbColumn == null) {
            dbColumn = sourceModel.findJdbcColumnByName(columnRef.getAliasRef());
        }
        if (dbColumn == null) {
            dbColumn = sourceModel.findJdbcColumnByName(columnRef.getColumnName());
        }
        if (dbColumn == null) {
            throw RX.throwAUserTip("aggregate relation 字段不存在: " + sourceModel.getName() + "." + columnRef.getFullRef());
        }
        return dbColumn;
    }

    private String sourceColumnSql(DbColumn dbColumn) {
        return dbColumn.getDeclare(null, SOURCE_ALIAS);
    }

    private String sourceBody() {
        QueryObject queryObject = sourceModel.getQueryObject();
        if (queryObject != null && queryObject.getBody() != null && !queryObject.getBody().isBlank()) {
            return queryObject.getBody();
        }
        return sourceModel.getTableName();
    }

    private SqlColumn cloneSqlColumn(String alias, String caption, SqlColumn sourceColumn) {
        String resolvedCaption = caption == null || caption.isBlank() ? alias : caption;
        return new SqlColumn(alias, resolvedCaption, sourceColumn.getJdbcType(), sourceColumn.getLength());
    }

    private SqlColumn buildMeasureSqlColumn(String alias, AggregateJoinBuilder.AggregateMeasure measure, DbColumn sourceColumn) {
        String caption = outputCaption(alias, sourceColumn);
        if (measure.getFunction() == AggregateJoinBuilder.AggregateFunction.COUNT
                || measure.getFunction() == AggregateJoinBuilder.AggregateFunction.COUNT_DISTINCT) {
            return new SqlColumn(alias, caption, Types.BIGINT);
        }
        if (measure.getFunction() == AggregateJoinBuilder.AggregateFunction.AVG) {
            return new SqlColumn(alias, caption, Types.DECIMAL);
        }
        if (sourceColumn == null) {
            return new SqlColumn(alias, caption, Types.DECIMAL);
        }
        return cloneSqlColumn(alias, caption, sourceColumn.getSqlColumn());
    }

    private String outputCaption(String alias, DbColumn sourceColumn) {
        if (sourceColumn != null && sourceColumn.getCaption() != null && !sourceColumn.getCaption().isBlank()) {
            return sourceColumn.getCaption();
        }
        return alias;
    }

    private DbColumnType resolveOutputType(AggregateJoinBuilder.AggregateMeasure measure, DbColumn sourceColumn) {
        if (measure == null) {
            return sourceColumn == null ? null : sourceColumn.getType();
        }
        AggregateJoinBuilder.AggregateFunction function = measure.getFunction();
        if (function == AggregateJoinBuilder.AggregateFunction.COUNT
                || function == AggregateJoinBuilder.AggregateFunction.COUNT_DISTINCT) {
            return DbColumnType.BIGINT;
        }
        if (function == AggregateJoinBuilder.AggregateFunction.AVG
                && sourceColumn != null
                && (sourceColumn.getType() == DbColumnType.INTEGER || sourceColumn.getType() == DbColumnType.BIGINT)) {
            return DbColumnType.NUMBER;
        }
        return sourceColumn == null ? DbColumnType.NUMBER : sourceColumn.getType();
    }

    private String validateOutputAlias(String alias, String role) {
        if (alias == null || alias.isBlank()) {
            throw RX.throwAUserTip("aggregate relation " + role + " 输出 alias 不能为空");
        }
        if (!SIMPLE_IDENTIFIER.matcher(alias).matches()) {
            throw RX.throwAUserTip("aggregate relation " + role + " 输出 alias 仅支持简单标识符: " + alias);
        }
        return alias;
    }

    private String renderLiteral(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof Number) {
            return value.toString();
        }
        if (value instanceof Boolean bool) {
            return bool ? "1" : "0";
        }
        if (value instanceof Date || value instanceof TemporalAccessor || value instanceof Enum<?>) {
            return quote(value.toString());
        }
        return quote(String.valueOf(value));
    }

    private String quote(String value) {
        return "'" + value.replace("'", "''") + "'";
    }

    private static List<AggregateJoinBuilder.AggregateMeasure> buildDefaultMeasures(TableModel sourceModel) {
        List<AggregateJoinBuilder.AggregateMeasure> measures = new ArrayList<>();
        for (DbMeasure measure : sourceModel.getMeasures()) {
            AggregateJoinBuilder.AggregateFunction function = toAggregateFunction(measure.getAggregation());
            if (function == null) {
                continue;
            }
            DbColumn dbColumn = measure.getJdbcColumn();
            String alias = defaultOutputAlias(dbColumn);
            measures.add(new AggregateJoinBuilder.AggregateMeasure(
                    function,
                    new ColumnRef(new TableModelProxy(sourceModel.getName()), alias),
                    alias));
        }
        return measures;
    }

    private static String defaultOutputAlias(DbColumn dbColumn) {
        if (dbColumn.getAlias() != null && !dbColumn.getAlias().isBlank()) {
            return dbColumn.getAlias();
        }
        if (dbColumn.getName() != null && !dbColumn.getName().isBlank()) {
            return dbColumn.getName();
        }
        return dbColumn.getSqlColumnName();
    }

    private static AggregateJoinBuilder.AggregateFunction toAggregateFunction(DbAggregation aggregation) {
        if (aggregation == null || aggregation == DbAggregation.NONE) {
            return null;
        }
        return switch (aggregation) {
            case SUM -> AggregateJoinBuilder.AggregateFunction.SUM;
            case AVG -> AggregateJoinBuilder.AggregateFunction.AVG;
            case MIN -> AggregateJoinBuilder.AggregateFunction.MIN;
            case MAX -> AggregateJoinBuilder.AggregateFunction.MAX;
            case COUNT -> AggregateJoinBuilder.AggregateFunction.COUNT;
            case COUNT_DISTINCT -> AggregateJoinBuilder.AggregateFunction.COUNT_DISTINCT;
            default -> null;
        };
    }

    private static DbAggregation toDbAggregation(AggregateJoinBuilder.AggregateFunction function) {
        return switch (function) {
            case SUM -> DbAggregation.SUM;
            case AVG -> DbAggregation.AVG;
            case MIN -> DbAggregation.MIN;
            case MAX -> DbAggregation.MAX;
            case COUNT -> DbAggregation.COUNT;
            case COUNT_DISTINCT -> DbAggregation.COUNT_DISTINCT;
        };
    }

    private record AggregateRelationInput(
            TableModelProxy right,
            TableModelProxy left,
            JoinType joinType,
            List<JoinCondition> conditions,
            List<ColumnRef> groupByColumns,
            List<AggregateJoinBuilder.AggregateMeasure> measures,
            List<AggregateJoinBuilder.AggregateFilter> filters) {
    }

    private record OutputColumn(
            boolean groupKey,
            String logicalName,
            String aliasRef,
            String outputAlias,
            String caption,
            DbColumn sourceColumn,
            SqlColumn sqlColumn,
            AggregateJoinBuilder.AggregateMeasure measure,
            String sourceExpression,
            String aggregateExpression) {
    }

    private record JoinKeyPushdownMapping(
            Set<String> leftFieldNames,
            String rightExpression) {
    }

    private class GeneratedAggregateRelationQueryObject extends ViewSqlQueryObject implements AggregateRelationQueryObject {
        private final List<String> selectParts;
        private final String sourceBody;
        private final String baseWhereSql;
        private final List<String> groupByParts;
        private final List<JoinKeyPushdownMapping> joinKeyPushdownMappings;
        private final ThreadLocal<PushdownState> pushdownState = ThreadLocal.withInitial(PushdownState::new);

        GeneratedAggregateRelationQueryObject(SqlTable sqlTable,
                                              List<String> selectParts,
                                              String sourceBody,
                                              String baseWhereSql,
                                              List<String> groupByParts,
                                              List<JoinKeyPushdownMapping> joinKeyPushdownMappings) {
            super("", sqlTable);
            this.selectParts = List.copyOf(selectParts);
            this.sourceBody = sourceBody;
            this.baseWhereSql = baseWhereSql;
            this.groupByParts = List.copyOf(groupByParts);
            this.joinKeyPushdownMappings = List.copyOf(joinKeyPushdownMappings);
        }

        @Override
        public String getBody() {
            PushdownState state = pushdownState.get();
            StringBuilder sql = new StringBuilder();
            sql.append("select ")
                    .append(String.join(", ", selectParts))
                    .append(" from ")
                    .append(sourceBody)
                    .append(" ")
                    .append(SOURCE_ALIAS);

            List<String> whereParts = new ArrayList<>();
            if (baseWhereSql != null && !baseWhereSql.isBlank()) {
                whereParts.add(baseWhereSql);
            }
            whereParts.addAll(state.whereFragments);
            if (!whereParts.isEmpty()) {
                sql.append(" where ").append(String.join(" and ", whereParts));
            }

            if (!groupByParts.isEmpty()) {
                sql.append(" group by ").append(String.join(", ", groupByParts));
            }
            if (!state.havingFragments.isEmpty()) {
                sql.append(" having ").append(String.join(" and ", state.havingFragments));
            }

            return "(" + sql + ")";
        }

        @Override
        public void clearAggregateRelationPushdowns() {
            pushdownState.remove();
        }

        @Override
        public boolean pushAggregateRelationCondition(AggregateRelationOutputColumn column, String op, Object value) {
            if (column == null) {
                return false;
            }
            String expression = column.isAggregateRelationMeasure()
                    ? column.getAggregateRelationAggregateExpression()
                    : column.getAggregateRelationSourceExpression();
            if (expression == null || expression.isBlank()) {
                return false;
            }
            return pushCondition(column.isAggregateRelationMeasure(), expression, op, value);
        }

        @Override
        public boolean pushAggregateRelationJoinKeyCondition(String leftFieldName, String op, Object value) {
            if (leftFieldName == null || leftFieldName.isBlank()) {
                return false;
            }
            boolean pushed = false;
            for (JoinKeyPushdownMapping mapping : joinKeyPushdownMappings) {
                if (!mapping.leftFieldNames().contains(leftFieldName)) {
                    continue;
                }
                pushed = pushCondition(false, mapping.rightExpression(), op, value) || pushed;
            }
            return pushed;
        }

        private boolean pushCondition(boolean having, String expression, String op, Object value) {
            List<String> fragments = renderConditionFragments(expression, op, value);
            if (fragments.isEmpty()) {
                return false;
            }
            PushdownState state = pushdownState.get();
            List<String> target = having ? state.havingFragments : state.whereFragments;
            for (String fragment : fragments) {
                if (!target.contains(fragment)) {
                    target.add(fragment);
                }
            }
            return true;
        }

        private List<String> renderConditionFragments(String expression, String op, Object value) {
            String normalizedOp = normalizeOp(op);
            if (normalizedOp == null) {
                return List.of();
            }

            if (isRangeOp(normalizedOp)) {
                return renderRangeConditionFragments(expression, normalizedOp, value);
            }

            if ("is null".equals(normalizedOp) || value == null && "=".equals(normalizedOp)) {
                return List.of(expression + " is null");
            }
            if ("is not null".equals(normalizedOp)
                    || value == null && ("<>".equals(normalizedOp) || "!=".equals(normalizedOp))) {
                return List.of(expression + " is not null");
            }
            if (value == null) {
                return List.of();
            }

            if ("in".equals(normalizedOp) || "not in".equals(normalizedOp)) {
                Collection<?> values = value instanceof Collection<?> collection ? collection : List.of(value);
                if (values.isEmpty()) {
                    return List.of();
                }
                String renderedValues = values.stream()
                        .map(AggregateJoinTableModel.this::renderLiteral)
                        .collect(Collectors.joining(", "));
                return List.of(expression + " " + normalizedOp + " (" + renderedValues + ")");
            }

            if (isSimpleComparisonOp(normalizedOp)) {
                return List.of(expression + " " + normalizedOp + " " + renderLiteral(value));
            }

            return List.of();
        }

        private List<String> renderRangeConditionFragments(String expression, String op, Object value) {
            if (!(value instanceof List<?> values) || values.size() < 2) {
                return List.of();
            }
            List<String> fragments = new ArrayList<>();
            Object start = values.get(0);
            Object end = values.get(1);
            if (start != null) {
                fragments.add(expression + ("[".equals(op.substring(0, 1)) ? " >= " : " > ") + renderLiteral(start));
            }
            if (end != null) {
                fragments.add(expression + ("]".equals(op.substring(1, 2)) ? " <= " : " < ") + renderLiteral(end));
            }
            return fragments;
        }

        private boolean isSimpleComparisonOp(String op) {
            return "=".equals(op)
                    || ">".equals(op)
                    || ">=".equals(op)
                    || "<".equals(op)
                    || "<=".equals(op)
                    || "<>".equals(op)
                    || "!=".equals(op);
        }

        private boolean isRangeOp(String op) {
            return "[]".equals(op) || "[)".equals(op) || "(]".equals(op) || "()".equals(op);
        }

        private String normalizeOp(String op) {
            if (op == null || op.isBlank()) {
                return null;
            }
            String normalized = op.trim().toLowerCase(Locale.ROOT);
            return switch (normalized) {
                case "=", ">", ">=", "<", "<=", "<>", "!=", "[]", "[)", "(]", "()" -> normalized;
                case "===" -> "=";
                case "in" -> "in";
                case "not in", "nin" -> "not in";
                case "isnull", "is null" -> "is null";
                case "isnotnull", "is not null" -> "is not null";
                default -> null;
            };
        }

        private class PushdownState {
            private final List<String> whereFragments = new ArrayList<>();
            private final List<String> havingFragments = new ArrayList<>();
        }
    }

    private class AggregateOutputDbColumn extends SimpleSqlJdbcColumn implements AggregateRelationOutputColumn {
        private final DbAggregation aggregation;
        private final DbColumnType type;
        private final boolean groupKey;
        private final DbColumn sourceColumn;
        private final DbMeasure sourceMeasure;
        private final String sourceExpression;
        private final String aggregateExpression;

        AggregateOutputDbColumn(QueryObject queryObject, SqlColumn sqlColumn, String alias, String name,
                                String caption, DbAggregation aggregation, DbColumnType type,
                                boolean groupKey, DbColumn sourceColumn,
                                String sourceExpression, String aggregateExpression) {
            super(queryObject, sqlColumn, alias, name, caption);
            this.aggregation = aggregation;
            this.type = type;
            this.groupKey = groupKey;
            this.sourceColumn = sourceColumn;
            this.sourceMeasure = resolveSourceMeasure(sourceColumn);
            this.sourceExpression = sourceExpression;
            this.aggregateExpression = aggregateExpression;
        }

        @Override
        public DbAggregation getAggregation() {
            return aggregation;
        }

        @Override
        public DbColumnType getType() {
            return type;
        }

        @Override
        public boolean isMeasure() {
            return aggregation != null && aggregation != DbAggregation.NONE;
        }

        @Override
        public boolean isAggregateRelationGroupKey() {
            return groupKey;
        }

        @Override
        public boolean isAggregateRelationMeasure() {
            return isMeasure();
        }

        @Override
        public DbColumn getAggregateRelationSourceColumn() {
            return sourceColumn;
        }

        @Override
        public String getAggregateRelationSourceExpression() {
            return sourceExpression;
        }

        @Override
        public String getAggregateRelationAggregateExpression() {
            return aggregateExpression;
        }

        @Override
        public boolean pushAggregateRelationCondition(String op, Object value) {
            if (!(getQueryObject() instanceof AggregateRelationQueryObject aggregateRelationQueryObject)) {
                return false;
            }
            return aggregateRelationQueryObject.pushAggregateRelationCondition(this, op, value);
        }

        @Override
        public String getDescription() {
            if (sourceColumn != null && sourceColumn.getDescription() != null && !sourceColumn.getDescription().isBlank()) {
                return sourceColumn.getDescription();
            }
            if (aggregateExpression != null && !aggregateExpression.isBlank()) {
                return "Aggregate relation output generated from " + aggregateExpression;
            }
            return sourceColumn == null ? null : sourceColumn.getDescription();
        }

        @Override
        public Object getExtData() {
            Map<String, Object> extData = new LinkedHashMap<>();
            Object sourceExtData = sourceColumn == null ? null : sourceColumn.getExtData();
            if (sourceExtData instanceof Map<?, ?> sourceMap) {
                for (Map.Entry<?, ?> entry : sourceMap.entrySet()) {
                    if (entry.getKey() != null) {
                        extData.put(String.valueOf(entry.getKey()), entry.getValue());
                    }
                }
            } else if (sourceExtData != null) {
                extData.put("sourceExtData", sourceExtData);
            }

            Map<String, Object> aggregateRelation = new LinkedHashMap<>();
            if (aggregation != null) {
                aggregateRelation.put("aggregation", aggregation.name());
            }
            if (sourceColumn != null) {
                aggregateRelation.put("sourceColumn", sourceColumn.getName());
                aggregateRelation.put("sourceAlias", sourceColumn.getAlias());
                aggregateRelation.put("sourceCaption", sourceColumn.getCaption());
            }
            if (sourceMeasure != null) {
                aggregateRelation.put("sourceMeasure", sourceMeasure.getName());
                aggregateRelation.put("semanticScaleFactor", sourceMeasure.getSemanticScaleFactor());
                aggregateRelation.put("semanticUnit", sourceMeasure.getSemanticUnit());
                aggregateRelation.put("semanticUnitLabel", sourceMeasure.getSemanticUnitLabel());
            }
            aggregateRelation.put("sourceExpression", sourceExpression);
            aggregateRelation.put("aggregateExpression", aggregateExpression);
            extData.put("aggregateRelation", aggregateRelation);
            return extData;
        }

        @Override
        public AiObject getAi() {
            return sourceColumn == null ? null : sourceColumn.getAi();
        }

        @Override
        public boolean _isDeprecated() {
            return sourceColumn != null && sourceColumn._isDeprecated();
        }

        @Override
        public ObjectTransFormatter<?> getFormatter() {
            if (type != null) {
                return type.getFormatter();
            }
            return sourceColumn == null ? null : sourceColumn.getFormatter();
        }

        private DbMeasure resolveSourceMeasure(DbColumn sourceColumn) {
            if (sourceColumn == null) {
                return null;
            }
            DbMeasureColumn measureColumn = sourceColumn.getDecorate(DbMeasureColumn.class);
            return measureColumn == null ? null : measureColumn.getJdbcMeasure();
        }
    }
}
