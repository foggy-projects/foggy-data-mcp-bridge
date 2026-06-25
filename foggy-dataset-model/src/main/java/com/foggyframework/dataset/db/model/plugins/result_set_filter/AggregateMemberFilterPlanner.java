package com.foggyframework.dataset.db.model.plugins.result_set_filter;

import com.foggyframework.dataset.db.model.def.query.request.CondRequestDef;
import com.foggyframework.dataset.db.model.def.query.request.DbQueryRequestDef;
import com.foggyframework.dataset.db.model.def.query.request.SliceRequestDef;
import com.foggyframework.dataset.db.model.impl.model.AggregateRelationOutputColumn;
import com.foggyframework.dataset.db.model.spi.DbAggregation;
import com.foggyframework.dataset.db.model.spi.DbColumn;
import com.foggyframework.dataset.db.model.spi.JdbcQueryModel;

import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Plans aggregate GROUP_CONCAT alias filters that should be interpreted as
 * source-member predicates instead of aggregate-string predicates.
 */
public final class AggregateMemberFilterPlanner {

    public static final String EXT_DATA_KEY = "__foggyAggregateMemberFilterPlans";

    private AggregateMemberFilterPlanner() {
    }

    public static IdentityHashMap<CondRequestDef, AggregateMemberFilterPlan> ensurePlanned(
            ModelResultContext context,
            JdbcQueryModel queryModel) {
        IdentityHashMap<CondRequestDef, AggregateMemberFilterPlan> existing = getPlans(context);
        if (existing != null) {
            return existing;
        }
        return plan(context, queryModel);
    }

    public static IdentityHashMap<CondRequestDef, AggregateMemberFilterPlan> plan(
            ModelResultContext context,
            JdbcQueryModel queryModel) {
        IdentityHashMap<CondRequestDef, AggregateMemberFilterPlan> plans = new IdentityHashMap<>();
        if (context == null || queryModel == null || context.getRequest() == null) {
            storePlans(context, plans);
            return plans;
        }
        DbQueryRequestDef request = context.getRequest().getParam();
        if (request == null || request.getSlice() == null || request.getSlice().isEmpty()) {
            storePlans(context, plans);
            return plans;
        }

        for (SliceRequestDef slice : request.getSlice()) {
            collectPlans(queryModel, plans, slice, true);
        }
        storePlans(context, plans);
        return plans;
    }

    public static AggregateMemberFilterPlan getPlan(ModelResultContext context, CondRequestDef condition) {
        IdentityHashMap<CondRequestDef, AggregateMemberFilterPlan> plans = getPlans(context);
        return plans == null ? null : plans.get(condition);
    }

    @SuppressWarnings("unchecked")
    private static IdentityHashMap<CondRequestDef, AggregateMemberFilterPlan> getPlans(ModelResultContext context) {
        if (context == null || context.getExtData() == null) {
            return null;
        }
        Object value = context.getExtData().get(EXT_DATA_KEY);
        if (value instanceof IdentityHashMap<?, ?> plans) {
            return (IdentityHashMap<CondRequestDef, AggregateMemberFilterPlan>) plans;
        }
        return null;
    }

    private static void storePlans(ModelResultContext context,
                                   IdentityHashMap<CondRequestDef, AggregateMemberFilterPlan> plans) {
        if (context == null) {
            return;
        }
        context.getExtData().put(EXT_DATA_KEY, plans);
    }

    private static void collectPlans(JdbcQueryModel queryModel,
                                     IdentityHashMap<CondRequestDef, AggregateMemberFilterPlan> plans,
                                     CondRequestDef condition,
                                     boolean conjunctiveContext) {
        if (condition == null) {
            return;
        }
        if (condition._isExpressionCondition() || condition._isFieldReference()) {
            return;
        }
        if (condition._isLogicalGroup()) {
            List<CondRequestDef> children = condition._getGroupChildren();
            if (children == null || children.isEmpty()) {
                return;
            }
            boolean childConjunctiveContext = conjunctiveContext && isConjunctive(condition._getGroupLink());
            for (CondRequestDef child : children) {
                collectPlans(queryModel, plans, child, childConjunctiveContext);
            }
            return;
        }
        if (!conjunctiveContext) {
            return;
        }
        AggregateMemberFilterPlan plan = buildPlan(queryModel, condition);
        if (plan != null) {
            plans.put(condition, plan);
        }
    }

    private static AggregateMemberFilterPlan buildPlan(JdbcQueryModel queryModel, CondRequestDef condition) {
        String normalizedOp = normalizeOp(condition.getOp());
        if (!"=".equals(normalizedOp) && !"in".equals(normalizedOp)) {
            return null;
        }
        Object value = condition.getValue();
        if (value == null) {
            return null;
        }
        if ("in".equals(normalizedOp) && value instanceof Collection<?> collection && collection.isEmpty()) {
            return null;
        }

        DbColumn column = queryModel.findJdbcColumnForCond(condition.getField(), false, true);
        AggregateRelationOutputColumn aggregateColumn = aggregateRelationOutputColumn(column);
        if (aggregateColumn == null
                || !aggregateColumn.isAggregateRelationMeasure()
                || column.getAggregation() != DbAggregation.GROUP_CONCAT
                || aggregateColumn.getAggregateRelationSourceColumn() == null) {
            return null;
        }

        DbColumn sourceColumn = aggregateColumn.getAggregateRelationSourceColumn();
        return new AggregateMemberFilterPlan(
                condition.getField(),
                normalizedOp,
                value,
                sourceColumn.getName());
    }

    private static AggregateRelationOutputColumn aggregateRelationOutputColumn(DbColumn column) {
        if (column == null) {
            return null;
        }
        if (column instanceof AggregateRelationOutputColumn aggregateRelationOutputColumn) {
            return aggregateRelationOutputColumn;
        }
        return column.getDecorate(AggregateRelationOutputColumn.class);
    }

    private static boolean isConjunctive(String link) {
        return link == null || link.isBlank() || "AND".equalsIgnoreCase(link);
    }

    private static String normalizeOp(String op) {
        if (op == null || op.isBlank()) {
            return null;
        }
        String normalized = op.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "=", "===" -> "=";
            case "in" -> "in";
            default -> normalized;
        };
    }

    public record AggregateMemberFilterPlan(
            String field,
            String op,
            Object value,
            String sourceField) {
    }
}
