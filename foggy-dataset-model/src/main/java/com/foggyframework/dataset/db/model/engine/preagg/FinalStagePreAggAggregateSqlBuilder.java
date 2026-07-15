package com.foggyframework.dataset.db.model.engine.preagg;

import com.foggyframework.core.utils.StringUtils;
import com.foggyframework.dataset.db.dialect.FDialect;
import com.foggyframework.dataset.db.model.def.query.request.CalculatedFieldDef;
import com.foggyframework.dataset.db.model.def.query.request.DbQueryRequestDef;
import com.foggyframework.dataset.db.model.def.query.request.GroupRequestDef;
import com.foggyframework.dataset.db.model.engine.query.JdbcQuery;
import com.foggyframework.dataset.db.model.spi.DbAggregation;
import com.foggyframework.dataset.db.model.spi.DbColumn;
import com.foggyframework.dataset.db.model.spi.JdbcQueryModel;
import com.foggyframework.dataset.db.model.spi.preagg.PreAggregation;
import com.foggyframework.dataset.db.model.spi.support.AggregationDbColumn;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Builds pre-aggregation backed aggregate SQL for final-stage returnTotal.
 */
final class FinalStagePreAggAggregateSqlBuilder {

    private static final Pattern INLINE_AGGREGATE_PATTERN = Pattern.compile(
            "(?i)^\\s*(sum|count|min|max)\\s*\\(\\s*([A-Za-z_][A-Za-z0-9_$]*)\\s*\\)\\s+as\\s+([A-Za-z_][A-Za-z0-9_$]*)\\s*$"
    );
    private static final Pattern AGGREGATE_EXPRESSION_PATTERN = Pattern.compile(
            "(?i)^\\s*(sum|count|min|max)\\s*\\(\\s*([A-Za-z_][A-Za-z0-9_$]*)\\s*\\)\\s*$"
    );

    private final JdbcQueryModel queryModel;
    private final PreAggQueryRewriter rewriter;

    FinalStagePreAggAggregateSqlBuilder(JdbcQueryModel queryModel, PreAggQueryRewriter rewriter) {
        this.queryModel = queryModel;
        this.rewriter = rewriter;
    }

    PreAggQueryRewriter.PreAggAggregateSqlResult build(PreAggregation preAgg,
                                                       JdbcQuery jdbcQuery,
                                                       DbQueryRequestDef queryRequest) {
        if (preAgg == null || !preAgg.isEnabled()) {
            return null;
        }

        String alias = "pa";
        FDialect dialect = queryModel.getDialect();
        List<String> selectColumns = new ArrayList<>();
        List<String> groupByColumns = new ArrayList<>();

        List<String> groupFields = collectGroupFields(preAgg, jdbcQuery, queryRequest);
        if (groupFields == null || groupFields.isEmpty()) {
            return null;
        }

        for (String groupField : groupFields) {
            String preAggColumn = rewriter.mapFieldToPreAggColumn(preAgg, groupField);
            String expression = alias + "." + preAggColumn;
            selectColumns.add(expression + " AS " + quoteIdentifier(dialect, groupField));
            groupByColumns.add(expression);
        }

        Map<String, FinalStageMeasure> measures = collectMeasures(preAgg, jdbcQuery, queryRequest);
        if (measures == null) {
            return null;
        }
        for (FinalStageMeasure measure : measures.values()) {
            selectColumns.add(measure.aggregationFunction() + "("
                    + alias + "." + measure.preAggColumnName()
                    + ") AS " + quoteIdentifier(dialect, measure.alias()));
        }

        StringBuilder innerSql = new StringBuilder();
        innerSql.append("SELECT ")
                .append(String.join(", ", selectColumns))
                .append(" FROM ")
                .append(rewriter.getFullTableName(preAgg))
                .append(" ")
                .append(alias);

        PreAggQueryRewriter.ProvableWhereClauseResult whereResult =
                rewriter.buildProvableWhereClauseFromSlices(preAgg, queryRequest, alias);
        if (!whereResult.isApplied()) {
            throw new PreAggQueryRewriter.PredicateNotProvableException(whereResult.getUnsupportedReason());
        }
        List<Object> params = new ArrayList<>();
        if (whereResult.getClause() != null && !whereResult.getClause().isEmpty()) {
            innerSql.append(" WHERE ").append(whereResult.getClause());
            params.addAll(whereResult.getParams());
        }

        innerSql.append(" GROUP BY ").append(String.join(", ", groupByColumns));

        String outerAlias = "preagg_final";
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) AS total");
        for (FinalStageMeasure measure : measures.values()) {
            sql.append(", ")
                    .append(measure.aggregationFunction())
                    .append("(")
                    .append(outerAlias)
                    .append(".")
                    .append(quoteIdentifier(dialect, measure.alias()))
                    .append(") AS ")
                    .append(quoteIdentifier(dialect, measure.alias()));
        }
        sql.append(" FROM (\n")
                .append(innerSql)
                .append("\n) ")
                .append(outerAlias);

        return PreAggQueryRewriter.PreAggAggregateSqlResult.single(
                sql.toString(), params, preAgg.getName());
    }

    private List<String> collectGroupFields(PreAggregation preAgg,
                                            JdbcQuery jdbcQuery,
                                            DbQueryRequestDef queryRequest) {
        List<String> jdbcGroupFields = collectJdbcGroupFields(preAgg, jdbcQuery);
        if (jdbcGroupFields == null || !jdbcGroupFields.isEmpty()) {
            return jdbcGroupFields;
        }

        List<String> groupFields = new ArrayList<>();
        if (queryRequest != null && queryRequest.getGroupBy() != null) {
            for (GroupRequestDef group : queryRequest.getGroupBy()) {
                if (group == null || StringUtils.isEmpty(group.getField())) {
                    continue;
                }
                if (!canMapGroupField(preAgg, group.getField())) {
                    return null;
                }
                groupFields.add(group.getField());
            }
            return groupFields;
        }

        if (jdbcQuery != null && jdbcQuery.getSelect() != null && jdbcQuery.getSelect().getColumns() != null) {
            for (DbColumn column : jdbcQuery.getSelect().getColumns()) {
                String alias = column.getAlias();
                if (canMapGroupField(preAgg, alias) && !groupFields.contains(alias)) {
                    groupFields.add(alias);
                }
            }
        }
        return groupFields;
    }

    private List<String> collectJdbcGroupFields(PreAggregation preAgg, JdbcQuery jdbcQuery) {
        List<String> groupFields = new ArrayList<>();
        if (jdbcQuery == null || jdbcQuery.getSelect() == null || jdbcQuery.getSelect().getColumns() == null) {
            return groupFields;
        }
        for (DbColumn column : jdbcQuery.getSelect().getColumns()) {
            if (!(column instanceof AggregationDbColumn aggregationColumn)) {
                continue;
            }
            DbAggregation aggregation = aggregationColumn.getAggregation();
            if (aggregation != null && aggregation != DbAggregation.NONE) {
                continue;
            }
            String alias = aggregationColumn.getAlias();
            if (!canMapGroupField(preAgg, alias)) {
                return null;
            }
            if (!groupFields.contains(alias)) {
                groupFields.add(alias);
            }
        }
        return groupFields;
    }

    private boolean canMapGroupField(PreAggregation preAgg, String field) {
        if (StringUtils.isEmpty(field)) {
            return false;
        }
        String mapped = rewriter.mapFieldToPreAggColumn(preAgg, field);
        if (StringUtils.isEmpty(mapped)) {
            return false;
        }

        int dollarIndex = field.indexOf('$');
        if (dollarIndex <= 0) {
            return false;
        }

        String dimName = field.substring(0, dollarIndex);
        String propName = field.substring(dollarIndex + 1);
        if (!preAgg.hasDimension(dimName)) {
            return false;
        }

        return preAgg.hasMaterializedDimensionProperty(dimName, propName);
    }

    private Map<String, FinalStageMeasure> collectMeasures(PreAggregation preAgg,
                                                           JdbcQuery jdbcQuery,
                                                           DbQueryRequestDef queryRequest) {
        Map<String, FinalStageMeasure> measures = new LinkedHashMap<>();
        Map<String, DbAggregation> measureAggregations = preAgg.getMeasureAggregations();

        if (jdbcQuery != null && jdbcQuery.getSelect() != null && jdbcQuery.getSelect().getColumns() != null) {
            for (DbColumn column : jdbcQuery.getSelect().getColumns()) {
                if (!column.isMeasure()) {
                    continue;
                }
                String measureName = column.getName();
                String alias = column.getAlias();
                String preAggColumnName = resolvePreAggMeasureColumn(preAgg, measureName);
                if (preAggColumnName == null) {
                    return null;
                }
                DbAggregation agg = measureAggregations != null ? measureAggregations.get(measureName) : null;
                measures.put(alias, new FinalStageMeasure(
                        measureName,
                        alias,
                        preAggColumnName,
                        rewriter.getAggregationFunction(agg)
                ));
            }
        }

        if (queryRequest != null && queryRequest.getColumns() != null) {
            for (String columnDef : queryRequest.getColumns()) {
                FinalStageMeasure parsed = parseInlineAggregateMeasure(preAgg, columnDef);
                if (parsed != null) {
                    if (parsed == UNMAPPED_MEASURE) {
                        return null;
                    }
                    measures.putIfAbsent(parsed.alias(), parsed);
                }
            }
        }

        if (queryRequest != null && queryRequest.getCalculatedFields() != null) {
            for (CalculatedFieldDef fieldDef : queryRequest.getCalculatedFields()) {
                FinalStageMeasure parsed = parseCalculatedAggregateMeasure(preAgg, fieldDef);
                if (parsed != null) {
                    if (parsed == UNMAPPED_MEASURE) {
                        return null;
                    }
                    measures.putIfAbsent(parsed.alias(), parsed);
                }
            }
        }

        return measures;
    }

    private FinalStageMeasure parseInlineAggregateMeasure(PreAggregation preAgg, String columnDef) {
        if (StringUtils.isEmpty(columnDef)) {
            return null;
        }
        Matcher matcher = INLINE_AGGREGATE_PATTERN.matcher(columnDef.trim());
        if (!matcher.matches()) {
            return null;
        }

        String function = matcher.group(1).toUpperCase();
        String measureName = matcher.group(2);
        String alias = matcher.group(3);
        String preAggColumnName = resolvePreAggMeasureColumn(preAgg, measureName);
        if (preAggColumnName == null) {
            return UNMAPPED_MEASURE;
        }

        return new FinalStageMeasure(
                measureName,
                alias,
                preAggColumnName,
                rollupFunction(function)
        );
    }

    private FinalStageMeasure parseCalculatedAggregateMeasure(PreAggregation preAgg, CalculatedFieldDef fieldDef) {
        if (fieldDef == null || StringUtils.isEmpty(fieldDef.getName()) || StringUtils.isEmpty(fieldDef.getExpression())) {
            return null;
        }
        Matcher matcher = AGGREGATE_EXPRESSION_PATTERN.matcher(fieldDef.getExpression().trim());
        if (!matcher.matches()) {
            return null;
        }

        String expressionFunction = matcher.group(1).toUpperCase();
        if (fieldDef.getAgg() != null && !fieldDef.getAgg().isBlank()
                && !fieldDef.getAgg().equalsIgnoreCase(expressionFunction)) {
            return UNMAPPED_MEASURE;
        }
        String function = expressionFunction;
        String measureName = matcher.group(2);
        String preAggColumnName = resolvePreAggMeasureColumn(preAgg, measureName);
        if (preAggColumnName == null) {
            return UNMAPPED_MEASURE;
        }

        return new FinalStageMeasure(
                measureName,
                fieldDef.getName(),
                preAggColumnName,
                rollupFunction(function)
        );
    }

    private String resolvePreAggMeasureColumn(PreAggregation preAgg, String measureName) {
        if (StringUtils.isEmpty(measureName) || !preAgg.hasMeasure(measureName)) {
            return null;
        }
        Map<String, String> measureColumnNames = preAgg.getMeasureColumnNames();
        if (measureColumnNames == null) {
            return null;
        }
        String preAggColumnName = measureColumnNames.get(measureName);
        return StringUtils.isEmpty(preAggColumnName) ? null : preAggColumnName;
    }

    private String rollupFunction(String function) {
        if ("MIN".equalsIgnoreCase(function)) {
            return "MIN";
        }
        if ("MAX".equalsIgnoreCase(function)) {
            return "MAX";
        }
        return "SUM";
    }

    private String quoteIdentifier(FDialect dialect, String identifier) {
        if (dialect == null || identifier == null || identifier.isEmpty()) {
            return identifier;
        }
        return dialect.quoteIdentifier(identifier);
    }

    private static final FinalStageMeasure UNMAPPED_MEASURE =
            new FinalStageMeasure("__unmapped__", "__unmapped__", "__unmapped__", "__unmapped__");

    private record FinalStageMeasure(String measureName,
                                     String alias,
                                     String preAggColumnName,
                                     String aggregationFunction) {
    }
}
