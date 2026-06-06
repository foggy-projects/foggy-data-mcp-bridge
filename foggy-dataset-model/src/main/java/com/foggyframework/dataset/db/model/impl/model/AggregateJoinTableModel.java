package com.foggyframework.dataset.db.model.impl.model;

import com.foggyframework.core.ex.RX;
import com.foggyframework.core.trans.ObjectTransFormatter;
import com.foggyframework.dataset.db.model.engine.join.JoinEdge;
import com.foggyframework.dataset.db.model.engine.join.JoinGraph;
import com.foggyframework.dataset.db.model.impl.AiObject;
import com.foggyframework.dataset.db.model.impl.utils.ViewSqlQueryObject;
import com.foggyframework.dataset.db.model.proxy.AggregateJoinBuilder;
import com.foggyframework.dataset.db.model.proxy.AggregateRelationProxy;
import com.foggyframework.dataset.db.model.proxy.ColumnRef;
import com.foggyframework.dataset.db.model.proxy.JoinBuilder;
import com.foggyframework.dataset.db.model.proxy.JoinCondition;
import com.foggyframework.dataset.db.model.proxy.TableModelProxy;
import com.foggyframework.dataset.db.model.plugins.result_set_filter.ModelResultContext;
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
import com.foggyframework.fsscript.exp.FsscriptFunction;
import jakarta.persistence.criteria.JoinType;

import java.sql.Types;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
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
    private static final Pattern SAFE_RUNTIME_FILTER_LITERAL = Pattern.compile("[A-Za-z0-9_-]+");
    private static final int MAX_RUNTIME_FILTER_LITERAL_LENGTH = 128;

    private static final String SOURCE_ALIAS = "agg_src";
    private static final ThreadLocal<ModelResultContext> RUNTIME_FILTER_CONTEXT = new ThreadLocal<>();

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
        AggregateSourceSqlContext sourceSqlContext = buildSourceSqlContext(input);
        List<OutputColumn> outputColumns = buildOutputColumns(sqlTable, input, sourceSqlContext);
        this.idColumn = outputColumns.stream()
                .filter(OutputColumn::groupKey)
                .map(OutputColumn::outputAlias)
                .findFirst()
                .orElse(null);
        if (this.idColumn != null) {
            sqlTable.setIdColumn(sqlTable.getSqlColumn(this.idColumn, true));
        }

        GeneratedAggregateRelationQueryObject aggregateQueryObject = buildAggregateQueryObject(sqlTable, input, outputColumns, sourceSqlContext);
        aggregateQueryObject.setName(sourceModel.getName() + "_aggregate_join");
        aggregateQueryObject.setCaption(this.caption);
        aggregateQueryObject.setAlias(input.right().getEffectiveAlias());
        aggregateQueryObject.setPrimaryKey(this.idColumn);
        this.queryObject = aggregateQueryObject;

        registerOutputColumns(outputColumns);
    }

    public static AggregateJoinTableModel from(TableModel sourceModel, AggregateJoinBuilder builder) {
        return from(sourceModel, builder, List.of());
    }

    public static AggregateJoinTableModel from(TableModel sourceModel, AggregateJoinBuilder builder,
                                               Collection<TableModel> visibleLeftModels) {
        return new AggregateJoinTableModel(sourceModel, new AggregateRelationInput(
                builder.getRight(),
                builder.getLeft(),
                builder.getJoinType(),
                builder.getConditions(),
                builder.getGroupByColumns(),
                builder.getMeasures(),
                builder.getFilters(),
                buildLeftJoinScopes(builder.getLeft(), visibleLeftModels)));
    }

    public static AggregateJoinTableModel from(TableModel sourceModel, AggregateRelationProxy relationProxy,
                                               JoinBuilder joinBuilder) {
        return from(sourceModel, relationProxy, joinBuilder, List.of());
    }

    public static AggregateJoinTableModel from(TableModel sourceModel, AggregateRelationProxy relationProxy,
                                               JoinBuilder joinBuilder, Collection<TableModel> visibleLeftModels) {
        return new AggregateJoinTableModel(sourceModel, new AggregateRelationInput(
                relationProxy,
                joinBuilder.getLeft(),
                joinBuilder.getJoinType(),
                joinBuilder.getConditions(),
                relationProxy.getGroupByColumns(),
                buildDefaultMeasures(sourceModel),
                relationProxy.getFilters(),
                buildLeftJoinScopes(joinBuilder.getLeft(), visibleLeftModels)));
    }

    public static void setRuntimeFilterContext(ModelResultContext context) {
        if (context == null) {
            RUNTIME_FILTER_CONTEXT.remove();
            return;
        }
        RUNTIME_FILTER_CONTEXT.set(context);
    }

    public static void clearRuntimeFilterContext() {
        RUNTIME_FILTER_CONTEXT.remove();
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
            if (!isVisibleLeftJoinRef(input, leftRef)) {
                throw RX.throwAUserTip("aggregate relation on 左侧字段必须来自当前 query graph 已注册左侧模型或别名: "
                        + input.leftJoinScopes().stream()
                        .map(JoinLeftScope::displayName)
                        .collect(Collectors.joining(", ")));
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

    private boolean isVisibleLeftJoinRef(AggregateRelationInput input, ColumnRef leftRef) {
        String refAlias = leftRef.getTableAlias();
        if (refAlias == null || refAlias.isEmpty()) {
            // Preserve the original shorthand contract for the declared left model.
            return input.left().getModelName().equals(leftRef.getModelName());
        }
        for (JoinLeftScope scope : input.leftJoinScopes()) {
            if (scope.matches(leftRef.getModelName(), refAlias)) {
                return true;
            }
        }
        return false;
    }

    private Set<String> columnRefKeys(ColumnRef columnRef) {
        Set<String> keys = new LinkedHashSet<>();
        keys.add(columnRef.getFullRef());
        keys.add(columnRef.getAliasRef());
        keys.add(columnRef.getColumnName());
        return keys;
    }

    private List<OutputColumn> buildOutputColumns(SqlTable sqlTable,
                                                  AggregateRelationInput input,
                                                  AggregateSourceSqlContext sourceSqlContext) {
        List<OutputColumn> outputColumns = new ArrayList<>();

        for (ColumnRef groupByColumn : input.groupByColumns()) {
            DbColumn sourceColumn = resolveSourceColumn(groupByColumn);
            String alias = validateOutputAlias(groupByColumn.getAliasRef(), "groupBy");
            String caption = outputCaption(alias, sourceColumn);
            SqlColumn sqlColumn = cloneSqlColumn(alias, caption, sourceColumn.getSqlColumn());
            sqlTable.addSqlColumn(sqlColumn);
            String sourceExpression = sourceColumnSql(sourceColumn, sourceSqlContext);
            outputColumns.add(new OutputColumn(true, groupByColumn.getFullRef(), groupByColumn.getAliasRef(), alias,
                    caption, sourceColumn, sqlColumn, null, sourceExpression, null));
        }

        for (AggregateJoinBuilder.AggregateMeasure measure : input.measures()) {
            String alias = validateOutputAlias(measure.getAlias(), "measure");
            DbColumn sourceColumn = measure.getColumn() == null ? null : resolveSourceColumn(measure.getColumn());
            String caption = outputCaption(alias, sourceColumn);
            SqlColumn sqlColumn = buildMeasureSqlColumn(alias, measure, sourceColumn);
            String sourceExpression = sourceColumn == null ? null : sourceColumnSql(sourceColumn, sourceSqlContext);
            String aggregateExpression = renderAggregateExpression(measure, sourceSqlContext);
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
                                                                           List<OutputColumn> outputColumns,
                                                                           AggregateSourceSqlContext sourceSqlContext) {
        List<String> groupByParts = new ArrayList<>();

        for (OutputColumn outputColumn : outputColumns) {
            if (!outputColumn.groupKey()) {
                continue;
            }
            groupByParts.add(outputColumn.sourceExpression());
        }

        return new GeneratedAggregateRelationQueryObject(
                sqlTable,
                outputColumns,
                sourceBody(),
                sourceSqlContext.joinParts(),
                input.filters(),
                sourceSqlContext,
                groupByParts,
                buildJoinKeyPushdownMappings(input, sourceSqlContext));
    }

    private AggregateSourceSqlContext buildSourceSqlContext(AggregateRelationInput input) {
        QueryObject rootQueryObject = sourceModel.getQueryObject();
        Map<String, String> aliases = new LinkedHashMap<>();
        if (rootQueryObject != null && rootQueryObject.getAlias() != null) {
            aliases.put(rootQueryObject.getAlias(), SOURCE_ALIAS);
        }

        Set<QueryObject> targets = new LinkedHashSet<>();
        for (ColumnRef groupByColumn : input.groupByColumns()) {
            collectSourceQueryObjectTarget(groupByColumn, targets);
        }
        if (input.filters() != null) {
            for (AggregateJoinBuilder.AggregateFilter filter : input.filters()) {
                collectSourceQueryObjectTarget(filter.getColumn(), targets);
            }
        }
        for (AggregateJoinBuilder.AggregateMeasure measure : input.measures()) {
            collectSourceQueryObjectTarget(measure.getColumn(), targets);
        }
        for (JoinCondition condition : input.conditions()) {
            collectSourceQueryObjectTarget(condition.getRightAsColumnRef(), targets);
        }

        targets.removeIf(target -> isRootSourceQueryObject(rootQueryObject, target));
        if (targets.isEmpty()) {
            return new AggregateSourceSqlContext(aliases, List.of());
        }

        JoinGraph joinGraph = sourceModel.getJoinGraph();
        if (joinGraph == null) {
            throw RX.throwAUserTip("aggregate relation RHS 维度字段需要 JoinGraph 支持: " + sourceModel.getName());
        }

        List<String> joinParts = new ArrayList<>();
        for (JoinEdge edge : joinGraph.getPath(targets)) {
            QueryObject from = edge.getFrom();
            QueryObject to = edge.getTo();
            aliases.putIfAbsent(from.getAlias(), aggregateSourceAlias(rootQueryObject, from));
            aliases.putIfAbsent(to.getAlias(), aggregateSourceAlias(rootQueryObject, to));
            joinParts.add(renderSourceJoin(edge, aliases));
        }
        return new AggregateSourceSqlContext(aliases, joinParts);
    }

    private void collectSourceQueryObjectTarget(ColumnRef columnRef, Set<QueryObject> targets) {
        if (columnRef == null) {
            return;
        }
        DbColumn sourceColumn = resolveSourceColumn(columnRef);
        QueryObject queryObject = sourceColumn.getQueryObject();
        if (queryObject != null) {
            targets.add(queryObject);
        }
    }

    private boolean isRootSourceQueryObject(QueryObject rootQueryObject, QueryObject queryObject) {
        if (rootQueryObject == null || queryObject == null) {
            return false;
        }
        if (rootQueryObject.getAlias() != null && rootQueryObject.getAlias().equals(queryObject.getAlias())) {
            return true;
        }
        return queryObject.isRootEqual(rootQueryObject);
    }

    private String aggregateSourceAlias(QueryObject rootQueryObject, QueryObject queryObject) {
        if (isRootSourceQueryObject(rootQueryObject, queryObject)) {
            return SOURCE_ALIAS;
        }
        return "agg_" + queryObject.getAlias();
    }

    private String renderSourceJoin(JoinEdge edge, Map<String, String> aliases) {
        if (edge.hasOnBuilder()) {
            throw RX.throwAUserTip("aggregate relation RHS 维度字段暂不支持自定义 onBuilder 维度: "
                    + edge.getTo().getAlias());
        }
        if (edge.getForeignKey() == null || edge.getForeignKey().isBlank()) {
            throw RX.throwAUserTip("aggregate relation RHS 维度字段缺少 join foreignKey: "
                    + edge.getTo().getAlias());
        }
        if (edge.getTo().getPrimaryKey() == null || edge.getTo().getPrimaryKey().isBlank()) {
            throw RX.throwAUserTip("aggregate relation RHS 维度字段缺少 join primaryKey: "
                    + edge.getTo().getAlias());
        }

        String fromAlias = aliases.get(edge.getFrom().getAlias());
        String toAlias = aliases.get(edge.getTo().getAlias());
        return edge.getJoinTypeString()
                + edge.getTo().getBody()
                + " "
                + toAlias
                + " on "
                + fromAlias
                + "."
                + edge.getForeignKey()
                + "="
                + toAlias
                + "."
                + edge.getTo().getPrimaryKey();
    }

    private String renderAggregateExpression(AggregateJoinBuilder.AggregateMeasure measure,
                                             AggregateSourceSqlContext sourceSqlContext) {
        AggregateJoinBuilder.AggregateFunction function = measure.getFunction();
        if (function == AggregateJoinBuilder.AggregateFunction.COUNT && measure.getColumn() == null) {
            return "count(*)";
        }

        DbColumn sourceColumn = resolveSourceColumn(measure.getColumn());
        String columnSql = sourceColumnSql(sourceColumn, sourceSqlContext);
        if (function == AggregateJoinBuilder.AggregateFunction.COUNT_DISTINCT) {
            return "count(distinct " + columnSql + ")";
        }
        return function.name().toLowerCase() + "(" + columnSql + ")";
    }

    private String renderFilterSql(List<AggregateJoinBuilder.AggregateFilter> filters,
                                   AggregateSourceSqlContext sourceSqlContext) {
        if (filters == null || filters.isEmpty()) {
            return "";
        }
        return renderFilterFragments(filters, sourceSqlContext).stream()
                .map(SqlFragment::sql)
                .collect(Collectors.joining(" and "));
    }

    private List<SqlFragment> renderFilterFragments(List<AggregateJoinBuilder.AggregateFilter> filters,
                                                    AggregateSourceSqlContext sourceSqlContext) {
        if (filters == null || filters.isEmpty()) {
            return List.of();
        }
        return filters.stream()
                .map(filter -> renderFilter(filter, sourceSqlContext))
                .toList();
    }

    private SqlFragment renderFilter(AggregateJoinBuilder.AggregateFilter filter,
                                     AggregateSourceSqlContext sourceSqlContext) {
        DbColumn sourceColumn = resolveSourceColumn(filter.getColumn());
        String columnSql = sourceColumnSql(sourceColumn, sourceSqlContext);
        Object filterValue = resolveFilterValue(filter.getValue());
        if (filter.isMultiValue()) {
            Collection<?> values = filterValue instanceof Collection<?> collection ? collection : List.of(filterValue);
            if (values.isEmpty()) {
                return SqlFragment.of("1 = 0");
            }
            String placeholders = values.stream()
                    .map(value -> "?")
                    .collect(Collectors.joining(", "));
            return new SqlFragment(columnSql + " in (" + placeholders + ")", List.copyOf(values));
        }

        if (filterValue == null) {
            return switch (filter.getOperator()) {
                case "=" -> SqlFragment.of(columnSql + " is null");
                case "<>" -> SqlFragment.of(columnSql + " is not null");
                default -> SqlFragment.of(columnSql + " " + filter.getOperator() + " null");
            };
        }
        return new SqlFragment(columnSql + " " + filter.getOperator() + " ?", List.of(filterValue));
    }

    private Object resolveFilterValue(Object value) {
        if (!(value instanceof FsscriptFunction function)) {
            return value;
        }
        ModelResultContext context = RUNTIME_FILTER_CONTEXT.get();
        if (context == null) {
            throw RX.throwAUserTip("aggregate relation runtime filter 缺少 ModelResultContext");
        }
        Object resolved;
        try {
            resolved = function.threadSafeAccept(context);
        } catch (Exception e) {
            throw RX.throwAUserTip("aggregate relation runtime filter 执行失败: " + e.getMessage());
        }
        return validateRuntimeFilterValue(resolved);
    }

    private Object validateRuntimeFilterValue(Object value) {
        if (value == null) {
            throw RX.throwAUserTip("aggregate relation runtime filter 值不能为空");
        }
        if (value instanceof Collection<?> collection) {
            List<Object> validatedValues = new ArrayList<>();
            for (Object item : collection) {
                validatedValues.add(validateRuntimeFilterScalar(item));
            }
            return validatedValues;
        }
        return validateRuntimeFilterScalar(value);
    }

    private Object validateRuntimeFilterScalar(Object value) {
        if (value == null) {
            throw RX.throwAUserTip("aggregate relation runtime filter 值不能为空");
        }
        if (value instanceof Number number) {
            if (number instanceof Double doubleValue && !Double.isFinite(doubleValue)) {
                throw RX.throwAUserTip("aggregate relation runtime filter 数字值非法");
            }
            if (number instanceof Float floatValue && !Float.isFinite(floatValue)) {
                throw RX.throwAUserTip("aggregate relation runtime filter 数字值非法");
            }
            return value;
        }
        if (value instanceof Boolean) {
            return value;
        }
        if (value instanceof Enum<?> enumValue) {
            return validateRuntimeFilterString(enumValue.name());
        }
        if (value instanceof CharSequence charSequence) {
            return validateRuntimeFilterString(charSequence.toString());
        }
        throw RX.throwAUserTip("aggregate relation runtime filter 仅支持字符串、数字、布尔或字符串集合");
    }

    private String validateRuntimeFilterString(String value) {
        if (value == null || value.isBlank()) {
            throw RX.throwAUserTip("aggregate relation runtime filter 字符串值不能为空");
        }
        if (value.length() > MAX_RUNTIME_FILTER_LITERAL_LENGTH
                || !SAFE_RUNTIME_FILTER_LITERAL.matcher(value).matches()) {
            throw RX.throwAUserTip("aggregate relation runtime filter 字符串仅支持字母、数字、下划线和中划线");
        }
        return value;
    }

    private List<JoinKeyPushdownMapping> buildJoinKeyPushdownMappings(AggregateRelationInput input,
                                                                      AggregateSourceSqlContext sourceSqlContext) {
        List<JoinKeyPushdownMapping> mappings = new ArrayList<>();
        for (JoinCondition condition : input.conditions()) {
            ColumnRef rightRef = condition.getRightAsColumnRef();
            DbColumn rightColumn = resolveSourceColumn(rightRef);
            mappings.add(new JoinKeyPushdownMapping(
                    columnRefKeys(condition.getLeft()),
                    sourceColumnSql(rightColumn, sourceSqlContext)));
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

    private String sourceColumnSql(DbColumn dbColumn, AggregateSourceSqlContext sourceSqlContext) {
        return dbColumn.getDeclare(null, sourceSqlContext.aliasFor(dbColumn.getQueryObject()));
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

    private static Set<JoinLeftScope> buildLeftJoinScopes(TableModelProxy declaredLeft,
                                                          Collection<TableModel> visibleLeftModels) {
        Set<JoinLeftScope> scopes = new LinkedHashSet<>();
        addLeftJoinScope(scopes, declaredLeft.getModelName(), declaredLeft.getAlias());
        if (visibleLeftModels != null) {
            for (TableModel model : visibleLeftModels) {
                addLeftJoinScope(scopes, model.getName(), model.getAlias());
            }
        }
        return scopes;
    }

    private static void addLeftJoinScope(Set<JoinLeftScope> scopes, String modelName, String alias) {
        if (modelName == null || modelName.isEmpty()) {
            return;
        }
        scopes.add(new JoinLeftScope(modelName, alias));
    }

    private record AggregateRelationInput(
            TableModelProxy right,
            TableModelProxy left,
            JoinType joinType,
            List<JoinCondition> conditions,
            List<ColumnRef> groupByColumns,
            List<AggregateJoinBuilder.AggregateMeasure> measures,
            List<AggregateJoinBuilder.AggregateFilter> filters,
            Set<JoinLeftScope> leftJoinScopes) {
    }

    private record JoinLeftScope(String modelName, String alias) {
        boolean matches(String refModelName, String refAlias) {
            return modelName.equals(refModelName)
                    && alias != null
                    && !alias.isEmpty()
                    && alias.equals(refAlias);
        }

        String displayName() {
            return alias == null || alias.isEmpty() ? modelName : modelName + " as " + alias;
        }
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

    private record SqlFragment(String sql, List<Object> values) {
        private SqlFragment {
            values = values == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(values));
        }

        static SqlFragment of(String sql) {
            return new SqlFragment(sql, List.of());
        }
    }

    private record AggregateSourceSqlContext(
            Map<String, String> aliases,
            List<String> joinParts) {

        private String aliasFor(QueryObject queryObject) {
            if (queryObject == null || queryObject.getAlias() == null) {
                return SOURCE_ALIAS;
            }
            return aliases.getOrDefault(queryObject.getAlias(), SOURCE_ALIAS);
        }
    }

    private class GeneratedAggregateRelationQueryObject extends ViewSqlQueryObject implements AggregateRelationQueryObject {
        private final List<OutputColumn> outputColumns;
        private final String sourceBody;
        private final List<String> sourceJoinParts;
        private final List<AggregateJoinBuilder.AggregateFilter> baseFilters;
        private final AggregateSourceSqlContext sourceSqlContext;
        private final List<String> groupByParts;
        private final List<JoinKeyPushdownMapping> joinKeyPushdownMappings;
        private final ThreadLocal<PushdownState> pushdownState = ThreadLocal.withInitial(PushdownState::new);

        GeneratedAggregateRelationQueryObject(SqlTable sqlTable,
                                              List<OutputColumn> outputColumns,
                                              String sourceBody,
                                              List<String> sourceJoinParts,
                                              List<AggregateJoinBuilder.AggregateFilter> baseFilters,
                                              AggregateSourceSqlContext sourceSqlContext,
                                              List<String> groupByParts,
                                              List<JoinKeyPushdownMapping> joinKeyPushdownMappings) {
            super("", sqlTable);
            this.outputColumns = List.copyOf(outputColumns);
            this.sourceBody = sourceBody;
            this.sourceJoinParts = List.copyOf(sourceJoinParts);
            this.baseFilters = baseFilters == null ? List.of() : List.copyOf(baseFilters);
            this.sourceSqlContext = sourceSqlContext;
            this.groupByParts = List.copyOf(groupByParts);
            this.joinKeyPushdownMappings = List.copyOf(joinKeyPushdownMappings);
        }

        @Override
        public String getBody() {
            PushdownState state = pushdownState.get();
            List<Object> bodyParameters = new ArrayList<>();
            StringBuilder sql = new StringBuilder();
            sql.append("select ")
                    .append(String.join(", ", renderSelectParts(state)))
                    .append(" from ")
                    .append(sourceBody)
                    .append(" ")
                    .append(SOURCE_ALIAS);
            if (!sourceJoinParts.isEmpty()) {
                sql.append(String.join("", sourceJoinParts));
            }

            List<String> whereParts = new ArrayList<>();
            List<SqlFragment> baseWhereFragments = renderFilterFragments(baseFilters, sourceSqlContext);
            for (SqlFragment fragment : baseWhereFragments) {
                whereParts.add(fragment.sql());
                bodyParameters.addAll(fragment.values());
            }
            for (SqlFragment fragment : state.whereFragments) {
                whereParts.add(fragment.sql());
                bodyParameters.addAll(fragment.values());
            }
            if (!whereParts.isEmpty()) {
                sql.append(" where ").append(String.join(" and ", whereParts));
            }

            if (!groupByParts.isEmpty()) {
                sql.append(" group by ").append(String.join(", ", groupByParts));
            }
            if (!state.havingFragments.isEmpty()) {
                sql.append(" having ")
                        .append(state.havingFragments.stream()
                                .map(SqlFragment::sql)
                                .collect(Collectors.joining(" and ")));
                for (SqlFragment fragment : state.havingFragments) {
                    bodyParameters.addAll(fragment.values());
                }
            }

            state.lastBodyParameters = List.copyOf(bodyParameters);
            return "(" + sql + ")";
        }

        @Override
        public List<Object> getBodyParameters() {
            return pushdownState.get().lastBodyParameters;
        }

        private List<String> renderSelectParts(PushdownState state) {
            if (!state.projectionPruningEnabled || state.requiredOutputAliases.isEmpty()) {
                return outputColumns.stream()
                        .map(this::renderSelectPart)
                        .toList();
            }
            return outputColumns.stream()
                    .filter(outputColumn -> outputColumn.groupKey()
                            || state.requiredOutputAliases.contains(outputColumn.outputAlias()))
                    .map(this::renderSelectPart)
                    .toList();
        }

        private String renderSelectPart(OutputColumn outputColumn) {
            if (outputColumn.groupKey()) {
                return outputColumn.sourceExpression() + " " + outputColumn.outputAlias();
            }
            return outputColumn.aggregateExpression() + " " + outputColumn.outputAlias();
        }

        @Override
        public void clearAggregateRelationPushdowns() {
            pushdownState.remove();
        }

        @Override
        public void setAggregateRelationProjectionPruningEnabled(boolean enabled) {
            pushdownState.get().projectionPruningEnabled = enabled;
        }

        @Override
        public void markAggregateRelationOutput(AggregateRelationOutputColumn column) {
            if (!(column instanceof AggregateOutputDbColumn aggregateColumn)) {
                return;
            }
            markAggregateRelationOutputAlias(aggregateColumn.getSqlColumn().getName());
        }

        @Override
        public void markAggregateRelationOutputAlias(String alias) {
            if (alias == null || alias.isBlank()) {
                return;
            }
            pushdownState.get().requiredOutputAliases.add(alias);
        }

        @Override
        public boolean pushAggregateRelationCondition(AggregateRelationOutputColumn column, String op, Object value) {
            if (column == null) {
                return false;
            }
            markAggregateRelationOutput(column);
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
            List<SqlFragment> fragments = renderConditionFragments(expression, op, value);
            if (fragments.isEmpty()) {
                return false;
            }
            PushdownState state = pushdownState.get();
            List<SqlFragment> target = having ? state.havingFragments : state.whereFragments;
            for (SqlFragment fragment : fragments) {
                if (!target.contains(fragment)) {
                    target.add(fragment);
                }
            }
            return true;
        }

        private List<SqlFragment> renderConditionFragments(String expression, String op, Object value) {
            String normalizedOp = normalizeOp(op);
            if (normalizedOp == null) {
                return List.of();
            }

            if (isRangeOp(normalizedOp)) {
                return renderRangeConditionFragments(expression, normalizedOp, value);
            }

            if ("is null".equals(normalizedOp) || value == null && "=".equals(normalizedOp)) {
                return List.of(SqlFragment.of(expression + " is null"));
            }
            if ("is not null".equals(normalizedOp)
                    || value == null && ("<>".equals(normalizedOp) || "!=".equals(normalizedOp))) {
                return List.of(SqlFragment.of(expression + " is not null"));
            }
            if (value == null) {
                return List.of();
            }

            if ("in".equals(normalizedOp) || "not in".equals(normalizedOp)) {
                Collection<?> values = value instanceof Collection<?> collection ? collection : List.of(value);
                if (values.isEmpty()) {
                    return List.of();
                }
                String placeholders = values.stream()
                        .map(item -> "?")
                        .collect(Collectors.joining(", "));
                return List.of(new SqlFragment(expression + " " + normalizedOp + " (" + placeholders + ")",
                        new ArrayList<>(values)));
            }

            if (isSimpleComparisonOp(normalizedOp)) {
                return List.of(new SqlFragment(expression + " " + normalizedOp + " ?", List.of(value)));
            }

            return List.of();
        }

        private List<SqlFragment> renderRangeConditionFragments(String expression, String op, Object value) {
            if (!(value instanceof List<?> values) || values.size() < 2) {
                return List.of();
            }
            List<SqlFragment> fragments = new ArrayList<>();
            Object start = values.get(0);
            Object end = values.get(1);
            if (start != null) {
                fragments.add(new SqlFragment(
                        expression + ("[".equals(op.substring(0, 1)) ? " >= ?" : " > ?"),
                        List.of(start)));
            }
            if (end != null) {
                fragments.add(new SqlFragment(
                        expression + ("]".equals(op.substring(1, 2)) ? " <= ?" : " < ?"),
                        List.of(end)));
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
            private final List<SqlFragment> whereFragments = new ArrayList<>();
            private final List<SqlFragment> havingFragments = new ArrayList<>();
            private final Set<String> requiredOutputAliases = new LinkedHashSet<>();
            private List<Object> lastBodyParameters = List.of();
            private boolean projectionPruningEnabled;
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
