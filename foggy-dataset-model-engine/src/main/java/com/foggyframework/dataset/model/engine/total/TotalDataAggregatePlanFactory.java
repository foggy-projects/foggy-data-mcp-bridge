package com.foggyframework.dataset.model.engine.total;

import com.foggyframework.core.utils.StringUtils;
import com.foggyframework.dataset.model.def.query.request.DbQueryRequestDef;
import com.foggyframework.dataset.model.engine.expression.BoundSqlExpression;
import com.foggyframework.dataset.model.engine.expression.SqlFragment;
import com.foggyframework.dataset.model.engine.expression.TotalExpressionNode;
import com.foggyframework.dataset.model.engine.query.JdbcQuery;
import com.foggyframework.dataset.model.spi.DbAggregation;
import com.foggyframework.dataset.model.spi.DbColumn;
import com.foggyframework.dataset.model.spi.DbColumnType;
import com.foggyframework.dataset.model.spi.support.CalculatedDbColumn;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Builds the request-scoped algebraic aggregate plan used by grouped totalData.
 *
 * <p>The factory is deliberately free of SQL rendering and query mutation. Source
 * expressions captured before aggregate wrapping are supplied by the engine.</p>
 */
public final class TotalDataAggregatePlanFactory {

    public TotalDataAggregatePlan build(DbQueryRequestDef queryRequest,
                                        JdbcQuery jdbcQuery,
                                        boolean countToSum,
                                        List<CalculatedDbColumn> calculatedColumns,
                                        IdentityHashMap<DbColumn, String> aggregateSourceDeclares) {
        if (!countToSum || queryRequest == null || !queryRequest.hasGroupBy()
                || jdbcQuery == null || jdbcQuery.getSelect() == null
                || jdbcQuery.getSelect().getColumns() == null) {
            return TotalDataAggregatePlan.notApplicable();
        }

        List<DbColumn> publicColumns = new ArrayList<>(jdbcQuery.getSelect().getColumns());
        Map<String, CalculatedDbColumn> calculatedByAlias = new LinkedHashMap<>();
        if (calculatedColumns != null) {
            for (CalculatedDbColumn calculated : calculatedColumns) {
                calculatedByAlias.put(calculated.getAlias(), calculated);
            }
        }

        Map<String, TotalExpressionNode> materialized = new LinkedHashMap<>();
        List<String> publicAliases = new ArrayList<>();
        boolean requiresLowering = false;
        for (DbColumn column : publicColumns) {
            String alias = column.getAlias();
            publicAliases.add(alias);
            TotalExpressionNode expression = resolveTotalExpression(column, aggregateSourceDeclares);
            if (expression != null) {
                materialized.put(alias, expression);
                String nonMergeable = findNonMergeableAggregation(
                        expression, calculatedByAlias, new LinkedHashSet<>());
                if (nonMergeable != null) {
                    return TotalDataAggregatePlan.refused(
                            "aggregate '" + nonMergeable + "' in '" + alias
                                    + "' has no mergeable totalData state");
                }
                boolean aggregateThroughDependencies = containsAggregate(
                        expression, calculatedByAlias, new LinkedHashSet<>());
                boolean containsAverage = containsAggregation(
                        expression, calculatedByAlias, DbAggregation.AVG, new LinkedHashSet<>());
                boolean compositeCalculated = column instanceof CalculatedDbColumn
                        && aggregateThroughDependencies
                        && expression.getKind() != TotalExpressionNode.Kind.AGGREGATE;
                requiresLowering = requiresLowering || containsAverage || compositeCalculated;
            }
        }
        if (!requiresLowering) {
            return TotalDataAggregatePlan.notApplicable();
        }
        for (String alias : publicAliases) {
            if (alias != null && alias.toLowerCase(Locale.ROOT).startsWith("__foggy_")) {
                return TotalDataAggregatePlan.refused(
                        "output alias '" + alias + "' uses the reserved __foggy_ namespace");
            }
        }

        TotalDataAggregatePlan.Builder builder = new TotalDataAggregatePlan.Builder();
        for (String alias : publicAliases) {
            builder.addPublicExpression(alias, materialized.get(alias));
        }
        for (String alias : publicAliases) {
            materializeCalculatedDependencies(
                    alias, materialized.get(alias), calculatedByAlias,
                    materialized, builder, new LinkedHashSet<>());
        }
        if (builder.isRefused()) {
            return builder.build();
        }

        Set<String> boundAliases = new LinkedHashSet<>();
        for (String alias : publicAliases) {
            bindExpressionLeaves(alias, materialized, builder, boundAliases, new LinkedHashSet<>());
        }
        return builder.build();
    }

    private String findNonMergeableAggregation(TotalExpressionNode expression,
                                                Map<String, CalculatedDbColumn> calculatedByAlias,
                                                Set<String> visiting) {
        if (expression == null) {
            return null;
        }
        String[] found = {null};
        expression.visitAggregateLeaves(leaf -> {
            if (found[0] != null) {
                return;
            }
            DbAggregation aggregation;
            try {
                aggregation = DbAggregation.valueOf(leaf.aggregation().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                found[0] = leaf.aggregation();
                return;
            }
            if (aggregation != DbAggregation.SUM
                    && aggregation != DbAggregation.COUNT
                    && aggregation != DbAggregation.MIN
                    && aggregation != DbAggregation.MAX
                    && aggregation != DbAggregation.PK
                    && aggregation != DbAggregation.AVG) {
                found[0] = leaf.aggregation();
            }
        });
        if (found[0] != null) {
            return found[0];
        }
        List<String> references = new ArrayList<>();
        expression.visitReferences(references::add);
        for (String reference : references) {
            if (!visiting.add(reference)) {
                continue;
            }
            CalculatedDbColumn calculated = calculatedByAlias.get(reference);
            String nested = calculated == null || calculated.getSqlFragment() == null
                    ? null
                    : findNonMergeableAggregation(
                    calculated.getSqlFragment().getTotalExpression(), calculatedByAlias, visiting);
            visiting.remove(reference);
            if (nested != null) {
                return nested;
            }
        }
        return null;
    }

    private TotalExpressionNode resolveTotalExpression(
            DbColumn column,
            IdentityHashMap<DbColumn, String> aggregateSourceDeclares) {
        if (column instanceof CalculatedDbColumn calculated) {
            SqlFragment fragment = calculated.getSqlFragment();
            return fragment == null ? null : fragment.getTotalExpression();
        }
        DbAggregation aggregation = resolveAggregation(column);
        if (aggregation == DbAggregation.NONE || aggregation == DbAggregation.WINDOW) {
            return null;
        }
        String source = aggregateSourceDeclares.get(column);
        if (aggregation == DbAggregation.COUNT) {
            source = "*";
        }
        if (StringUtils.isEmpty(source)) {
            return null;
        }
        return TotalExpressionNode.aggregate(aggregation.name(), BoundSqlExpression.of(source));
    }

    private DbAggregation resolveAggregation(DbColumn column) {
        if (column instanceof CalculatedDbColumn calculated) {
            String aggregationType = calculated.getAggregationType();
            if (StringUtils.isEmpty(aggregationType)) {
                return DbAggregation.NONE;
            }
            try {
                return DbAggregation.valueOf(aggregationType.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                return DbAggregation.NONE;
            }
        }
        DbAggregation aggregation = column == null ? null : column.getAggregation();
        return aggregation == null ? DbAggregation.NONE : aggregation;
    }

    private boolean containsAggregate(TotalExpressionNode expression,
                                      Map<String, CalculatedDbColumn> calculatedByAlias,
                                      Set<String> visiting) {
        if (expression == null) {
            return false;
        }
        if (expression.containsAggregate()) {
            return true;
        }
        List<String> references = new ArrayList<>();
        expression.visitReferences(references::add);
        for (String reference : references) {
            if (!visiting.add(reference)) {
                continue;
            }
            CalculatedDbColumn calculated = calculatedByAlias.get(reference);
            if (calculated != null && calculated.getSqlFragment() != null
                    && containsAggregate(calculated.getSqlFragment().getTotalExpression(),
                    calculatedByAlias, visiting)) {
                return true;
            }
            visiting.remove(reference);
        }
        return false;
    }

    private boolean containsAggregation(TotalExpressionNode expression,
                                        Map<String, CalculatedDbColumn> calculatedByAlias,
                                        DbAggregation target,
                                        Set<String> visiting) {
        if (expression == null) {
            return false;
        }
        boolean[] found = {false};
        expression.visitAggregateLeaves(leaf -> {
            if (target.name().equalsIgnoreCase(leaf.aggregation())) {
                found[0] = true;
            }
        });
        if (found[0]) {
            return true;
        }
        List<String> references = new ArrayList<>();
        expression.visitReferences(references::add);
        for (String reference : references) {
            if (!visiting.add(reference)) {
                continue;
            }
            CalculatedDbColumn calculated = calculatedByAlias.get(reference);
            if (calculated != null && calculated.getSqlFragment() != null
                    && containsAggregation(calculated.getSqlFragment().getTotalExpression(),
                    calculatedByAlias, target, visiting)) {
                return true;
            }
            visiting.remove(reference);
        }
        return false;
    }

    private void materializeCalculatedDependencies(String ownerAlias,
                                                   TotalExpressionNode expression,
                                                   Map<String, CalculatedDbColumn> calculatedByAlias,
                                                   Map<String, TotalExpressionNode> materialized,
                                                   TotalDataAggregatePlan.Builder builder,
                                                   Set<String> visiting) {
        if (expression == null || builder.isRefused()) {
            return;
        }
        List<String> references = new ArrayList<>();
        expression.visitReferences(references::add);
        for (String reference : references) {
            TotalExpressionNode dependency = materialized.get(reference);
            if (dependency == null) {
                CalculatedDbColumn calculated = calculatedByAlias.get(reference);
                if (calculated == null || calculated.getSqlFragment() == null) {
                    builder.refuse("calculated totalData expression '" + ownerAlias
                            + "' references non-aggregate field '" + reference + "'");
                    return;
                }
                dependency = calculated.getSqlFragment().getTotalExpression();
                materialized.put(reference, dependency);
                builder.addDependencyExpression(reference, dependency);
            }
            if (!visiting.add(reference)) {
                builder.refuse("calculated totalData dependency cycle at '" + reference + "'");
                return;
            }
            materializeCalculatedDependencies(
                    reference, dependency, calculatedByAlias, materialized, builder, visiting);
            visiting.remove(reference);
        }
    }

    private void bindExpressionLeaves(String alias,
                                      Map<String, TotalExpressionNode> materialized,
                                      TotalDataAggregatePlan.Builder builder,
                                      Set<String> boundAliases,
                                      Set<String> visiting) {
        if (alias == null || boundAliases.contains(alias) || builder.isRefused()) {
            return;
        }
        if (!visiting.add(alias)) {
            builder.refuse("calculated totalData dependency cycle at '" + alias + "'");
            return;
        }
        TotalExpressionNode expression = materialized.get(alias);
        if (expression != null) {
            builder.bindLeaves(alias, expression, DbColumnType.NUMBER);
            List<String> references = new ArrayList<>();
            expression.visitReferences(references::add);
            for (String reference : references) {
                bindExpressionLeaves(reference, materialized, builder, boundAliases, visiting);
            }
        }
        visiting.remove(alias);
        boundAliases.add(alias);
    }
}
