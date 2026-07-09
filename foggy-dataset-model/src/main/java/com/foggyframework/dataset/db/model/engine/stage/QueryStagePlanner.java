package com.foggyframework.dataset.db.model.engine.stage;

import com.foggyframework.core.utils.StringUtils;
import com.foggyframework.dataset.db.dialect.FDialect;
import com.foggyframework.dataset.db.model.def.query.request.CalculatedFieldDef;
import com.foggyframework.dataset.db.model.def.query.request.CondRequestDef;
import com.foggyframework.dataset.db.model.def.query.request.DbQueryRequestDef;
import com.foggyframework.dataset.db.model.def.query.request.OrderRequestDef;
import com.foggyframework.dataset.db.model.def.query.request.PostAggregateCalculationDef;
import com.foggyframework.dataset.db.model.def.query.request.SliceRequestDef;
import com.foggyframework.dataset.db.model.engine.query.JdbcQuery;
import com.foggyframework.dataset.db.model.spi.DbAggregation;
import com.foggyframework.dataset.db.model.spi.DbColumn;
import com.foggyframework.dataset.db.model.spi.support.AggregationDbColumn;
import com.foggyframework.dataset.db.model.spi.support.CalculatedDbColumn;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class QueryStagePlanner {

    public QueryStagePlan plan(DbQueryRequestDef request,
                               JdbcQuery jdbcQuery,
                               FDialect dialect,
                               List<CalculatedDbColumn> calculatedColumns,
                               List<SliceRequestDef> postAggregateSlice,
                               boolean hasWindowCalculatedFields,
                               boolean hasPostAggregateCalculations,
                               boolean hasPostSlice) {
        boolean aggregateStageRequired = (request != null && request.hasGroupBy()) || hasAggregateSelect(jdbcQuery);
        boolean postAggregateStageRequired = hasPostAggregateCalculations || !isEmpty(postAggregateSlice);
        boolean windowResultStageRequired = hasWindowCalculatedFields || hasPostSlice;
        boolean multiStageRequired = postAggregateStageRequired || windowResultStageRequired;

        List<String> fallbacks = new ArrayList<>();
        List<String> unsupported = new ArrayList<>();
        String renderStrategy = "single";
        if (multiStageRequired) {
            if (dialect != null && dialect.supportsCte()) {
                renderStrategy = "cte";
            } else {
                renderStrategy = "derived";
                fallbacks.add(derivedFallbackName(dialect));
            }
        }
        if (hasWindowCalculatedFields && dialect != null && !dialect.supportsWindowFunctions()) {
            unsupported.add("window-functions-unsupported");
        }

        List<QueryStagePlan.Stage> stages = new ArrayList<>();
        List<String> selectAliases = selectedAliases(jdbcQuery);
        List<String> finalOutputAliases = finalOutputAliases(selectAliases, request);
        List<String> rowFilters = filterAliases(request != null ? request.getSlice() : null);
        List<String> rowOutputs = aggregateStageRequired
                ? unique(concat(rowFilters, requestColumns(request), groupByFields(request)))
                : selectAliases;
        stages.add(new QueryStagePlan.Stage(
                "row",
                QueryStageType.ROW_STAGE,
                multiStageRequired ? "stage0" : "base",
                List.of(),
                rowOutputs,
                rowFilters,
                List.of(),
                multiStageRequired,
                parameterCount(request != null ? request.getSlice() : null)
        ));

        List<String> previousOutputs = rowOutputs;
        if (aggregateStageRequired) {
            List<String> aggregateFilters = filterAliases(request != null ? request.getHaving() : null);
            stages.add(new QueryStagePlan.Stage(
                    "agg",
                    QueryStageType.AGGREGATE_STAGE,
                    multiStageRequired ? "stage1" : "base",
                    unique(concat(previousOutputs, groupByFields(request))),
                    selectAliases,
                    aggregateFilters,
                    List.of(),
                    multiStageRequired,
                    parameterCount(request != null ? request.getHaving() : null)
            ));
            previousOutputs = selectAliases;
        }

        if (postAggregateStageRequired) {
            List<String> postAggregateOutputs = unique(concat(previousOutputs, postAggregateNames(request)));
            stages.add(new QueryStagePlan.Stage(
                    "post_agg",
                    QueryStageType.POST_AGGREGATE_STAGE,
                    "stage" + stages.size(),
                    previousOutputs,
                    postAggregateOutputs,
                    filterAliases(postAggregateSlice),
                    List.of(),
                    true,
                    parameterCount(postAggregateSlice)
            ));
            previousOutputs = postAggregateOutputs;
        }

        if (windowResultStageRequired) {
            List<String> windowOutputs = unique(concat(previousOutputs, windowAliases(calculatedColumns), finalOutputAliases));
            stages.add(new QueryStagePlan.Stage(
                    "window_result",
                    QueryStageType.WINDOW_RESULT_STAGE,
                    "stage" + stages.size(),
                    previousOutputs,
                    windowOutputs,
                    filterAliases(request != null ? request.getPostSlice() : null),
                    List.of(),
                    true,
                    parameterCount(request != null ? request.getPostSlice() : null)
            ));
            previousOutputs = windowOutputs;
        }

        stages.add(new QueryStagePlan.Stage(
                "final",
                QueryStageType.FINAL_STAGE,
                "final",
                previousOutputs,
                finalOutputAliases,
                List.of(),
                orderAliases(request),
                false,
                0
        ));

        String returnTotalStrategy = request != null && request.isReturnTotal()
                ? "final-stage-count"
                : "disabled";
        return new QueryStagePlan(
                true,
                dialectName(dialect),
                renderStrategy,
                "final",
                returnTotalStrategy,
                stages,
                fallbacks,
                unsupported
        );
    }

    private boolean hasAggregateSelect(JdbcQuery jdbcQuery) {
        if (jdbcQuery == null || jdbcQuery.getSelect() == null || jdbcQuery.getSelect().getColumns() == null) {
            return false;
        }
        for (DbColumn column : jdbcQuery.getSelect().getColumns()) {
            if (column instanceof AggregationDbColumn aggregationColumn) {
                DbAggregation aggregation = aggregationColumn.getAggregation();
                if (aggregation != null && aggregation != DbAggregation.NONE) {
                    return true;
                }
            }
            if (column instanceof CalculatedDbColumn calculatedColumn && calculatedColumn.hasAggregate()) {
                return true;
            }
        }
        return false;
    }

    private String dialectName(FDialect dialect) {
        if (dialect == null) {
            return "unknown";
        }
        String simpleName = dialect.getClass().getSimpleName().toLowerCase(Locale.ROOT);
        if (simpleName.contains("mysql8")) {
            return "mysql8";
        }
        if (dialect.getDbType() == null) {
            return simpleName;
        }
        return dialect.getDbType().name().toLowerCase(Locale.ROOT);
    }

    private String derivedFallbackName(FDialect dialect) {
        String dialectName = dialectName(dialect);
        if ("mysql".equals(dialectName)) {
            return "mysql57-derived-table";
        }
        return dialectName + "-derived-table";
    }

    private List<String> selectedAliases(JdbcQuery jdbcQuery) {
        if (jdbcQuery == null || jdbcQuery.getSelect() == null || jdbcQuery.getSelect().getColumns() == null) {
            return List.of();
        }
        LinkedHashSet<String> aliases = new LinkedHashSet<>();
        for (DbColumn column : jdbcQuery.getSelect().getColumns()) {
            addIfPresent(aliases, columnAlias(column));
        }
        return List.copyOf(aliases);
    }

    private String columnAlias(DbColumn column) {
        if (column == null) {
            return null;
        }
        if (StringUtils.isNotEmpty(column.getAlias())) {
            return column.getAlias();
        }
        if (StringUtils.isNotEmpty(column.getField())) {
            return column.getField();
        }
        return column.getName();
    }

    private List<String> finalOutputAliases(List<String> selectAliases, DbQueryRequestDef request) {
        return unique(concat(selectAliases, postAggregateNames(request)));
    }

    private List<String> requestColumns(DbQueryRequestDef request) {
        if (request == null || request.getColumns() == null) {
            return List.of();
        }
        return clean(request.getColumns());
    }

    private List<String> groupByFields(DbQueryRequestDef request) {
        if (request == null || request.getGroupBy() == null) {
            return List.of();
        }
        LinkedHashSet<String> aliases = new LinkedHashSet<>();
        request.getGroupBy().forEach(group -> {
            if (group != null) {
                addIfPresent(aliases, group.getField());
            }
        });
        return List.copyOf(aliases);
    }

    private List<String> postAggregateNames(DbQueryRequestDef request) {
        if (request == null || request.getPostAggregateCalculations() == null) {
            return List.of();
        }
        LinkedHashSet<String> aliases = new LinkedHashSet<>();
        for (PostAggregateCalculationDef calculation : request.getPostAggregateCalculations()) {
            if (calculation != null) {
                addIfPresent(aliases, calculation.getName());
            }
        }
        return List.copyOf(aliases);
    }

    private List<String> windowAliases(List<CalculatedDbColumn> calculatedColumns) {
        if (calculatedColumns == null) {
            return List.of();
        }
        LinkedHashSet<String> aliases = new LinkedHashSet<>();
        for (CalculatedDbColumn column : calculatedColumns) {
            if (column != null && (column.hasWindow() || column.isNeedsCteWrapping())) {
                addIfPresent(aliases, column.getAlias());
            }
        }
        return List.copyOf(aliases);
    }

    private List<String> orderAliases(DbQueryRequestDef request) {
        if (request == null || request.getOrderBy() == null) {
            return List.of();
        }
        LinkedHashSet<String> aliases = new LinkedHashSet<>();
        for (OrderRequestDef order : request.getOrderBy()) {
            if (order != null) {
                addIfPresent(aliases, order.getField());
            }
        }
        return List.copyOf(aliases);
    }

    private List<String> filterAliases(List<? extends CondRequestDef> filters) {
        if (filters == null || filters.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> aliases = new LinkedHashSet<>();
        for (CondRequestDef filter : filters) {
            collectFilterAliases(filter, aliases);
        }
        return List.copyOf(aliases);
    }

    private void collectFilterAliases(CondRequestDef filter, Set<String> aliases) {
        if (filter == null) {
            return;
        }
        if (filter._isLogicalGroup()) {
            List<CondRequestDef> children = filter._getGroupChildren();
            if (children != null) {
                children.forEach(child -> collectFilterAliases(child, aliases));
            }
            return;
        }
        if (filter._isExpressionCondition()) {
            addIfPresent(aliases, "$expr");
            return;
        }
        addIfPresent(aliases, filter.getField());
        addIfPresent(aliases, filter._getReferencedField());
    }

    private int parameterCount(List<? extends CondRequestDef> filters) {
        if (filters == null || filters.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (CondRequestDef filter : filters) {
            count += parameterCount(filter);
        }
        return count;
    }

    private int parameterCount(CondRequestDef filter) {
        if (filter == null) {
            return 0;
        }
        if (filter._isLogicalGroup()) {
            List<CondRequestDef> children = filter._getGroupChildren();
            if (children == null) {
                return 0;
            }
            int count = 0;
            for (CondRequestDef child : children) {
                count += parameterCount(child);
            }
            return count;
        }
        Object value = filter.getValue();
        if (value instanceof Collection<?> collection) {
            return collection.size();
        }
        return value == null ? 0 : 1;
    }

    private List<String> clean(List<String> values) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        if (values != null) {
            values.forEach(value -> addIfPresent(result, value));
        }
        return List.copyOf(result);
    }

    @SafeVarargs
    private final List<String> concat(List<String>... lists) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        if (lists != null) {
            for (List<String> list : lists) {
                if (list != null) {
                    list.forEach(value -> addIfPresent(result, value));
                }
            }
        }
        return List.copyOf(result);
    }

    private List<String> unique(List<String> values) {
        return clean(values);
    }

    private boolean isEmpty(List<?> values) {
        return values == null || values.isEmpty();
    }

    private void addIfPresent(Set<String> values, String value) {
        if (StringUtils.isNotEmpty(value)) {
            values.add(value);
        }
    }
}
