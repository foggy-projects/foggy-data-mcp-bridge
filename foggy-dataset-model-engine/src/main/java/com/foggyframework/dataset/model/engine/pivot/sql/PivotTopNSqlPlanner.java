package com.foggyframework.dataset.model.engine.pivot.sql;

import com.foggyframework.dataset.db.dialect.FDialect;
import com.foggyframework.dataset.model.engine.pivot.rollup.MetricAdditivityAnalyzer;
import com.foggyframework.dataset.model.plugins.query_execution.ManagedSqlRelation;
import com.foggyframework.dataset.model.semantic.domain.pivot.AxisField;
import com.foggyframework.dataset.model.semantic.domain.pivot.PivotRequest;
import com.foggyframework.dataset.model.spi.DbAggregation;
import com.foggyframework.dataset.model.spi.QueryModel;
import lombok.Builder;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Pivot SQL Planner for TopN pushdown.
 * Generates CTEs with Window Functions to truncate axis domains at the SQL level.
 */
public class PivotTopNSqlPlanner {

    private static final Logger log = LoggerFactory.getLogger(PivotTopNSqlPlanner.class);

    @Getter
    @Builder
    public static class PlannedSql {
        private final String sql;
        private final List<Object> params;
    }

    /**
     * 判断当前环境是否支持 Pivot SQL 推导
     */
    public static boolean isSupported(FDialect dialect) {
        return dialect != null && dialect.supportsCte() && dialect.supportsWindowFunctions();
    }

    /**
     * 生成推导后的 SQL
     */
    public static PlannedSql plan(ManagedSqlRelation baseRelation, 
                                  PivotRequest pivot, 
                                  List<String> rowFields, 
                                  List<String> colFields, 
                                  List<String> metrics, 
                                  QueryModel queryModel) {
        
        FDialect dialect = baseRelation.getDialect();
        if (!isSupported(dialect)) {
            throw new UnsupportedOperationException("Dialect " + dialect.getProductName() + " does not support CTE/Window Functions for Pivot TopN pushdown.");
        }

        List<DomainLimitDef> rowLimits = extractLimits(pivot.getRows(), rowFields);
        List<DomainLimitDef> colLimits = extractLimits(pivot.getColumns(), colFields);

        if (rowLimits.isEmpty() && colLimits.isEmpty()) {
            return PlannedSql.builder()
                    .sql(baseRelation.getSql())
                    .params(baseRelation.getParams())
                    .build();
        }

        StringBuilder sql = new StringBuilder();
        List<Object> finalParams = new ArrayList<>(baseRelation.getParams());

        sql.append("WITH _base_relation AS (\n");
        sql.append(baseRelation.getSql());
        sql.append("\n)");

        List<String> joinConditions = new ArrayList<>();
        int cteIndex = 1;

        // Process row limits
        cteIndex = processLimits(sql, rowLimits, "row", cteIndex, joinConditions, queryModel, dialect, metrics);

        // Process col limits
        cteIndex = processLimits(sql, colLimits, "col", cteIndex, joinConditions, queryModel, dialect, metrics);

        // Final filtered select
        sql.append(",\n_filtered AS (\n");
        sql.append("  SELECT b.* FROM _base_relation b\n");
        for (String joinCond : joinConditions) {
            sql.append("  ").append(joinCond).append("\n");
        }
        sql.append(")\n");
        sql.append("SELECT * FROM _filtered");

        return PlannedSql.builder()
                .sql(sql.toString())
                .params(finalParams)
                .build();
    }

    private static int processLimits(StringBuilder sql, List<DomainLimitDef> limits, String prefix, int startIndex, 
                                     List<String> joinConditions, QueryModel queryModel, FDialect dialect, List<String> availableMetrics) {
        int cteIndex = startIndex;
        for (DomainLimitDef limitDef : limits) {
            String domainCte = "_" + prefix + "_domain_" + cteIndex;
            String rankedCte = "_" + prefix + "_ranked_" + cteIndex;
            String filteredCte = "_" + prefix + "_filtered_" + cteIndex;
            
            // Generate domain CTE
            sql.append(",\n").append(domainCte).append(" AS (\n");
            sql.append("  SELECT ");
            
            List<String> selectFields = new ArrayList<>();
            for (String pk : limitDef.getPartitionKeys()) {
                selectFields.add(dialect.quoteIdentifier(pk));
            }
            selectFields.add(dialect.quoteIdentifier(limitDef.getTargetKey()));
            
            sql.append(String.join(", ", selectFields));
            
            List<OrderSpec> validOrderSpecs = validateAndParseOrderSpecs(limitDef.getOrderBy(), queryModel, availableMetrics);
            if (!validOrderSpecs.isEmpty()) {
                sql.append(", ");
                List<String> aggExprs = new ArrayList<>();
                for (int i = 0; i < validOrderSpecs.size(); i++) {
                    OrderSpec spec = validOrderSpecs.get(i);
                    aggExprs.add(buildAggForOrder(spec, dialect) + " AS _order_metric_" + i);
                }
                sql.append(String.join(", ", aggExprs));
            }
            
            sql.append("\n  FROM _base_relation\n");
            sql.append("  GROUP BY ").append(String.join(", ", selectFields)).append("\n");
            sql.append(")");

            // Generate ranked CTE
            sql.append(",\n").append(rankedCte).append(" AS (\n");
            sql.append("  SELECT *, ROW_NUMBER() OVER (");
            if (!limitDef.getPartitionKeys().isEmpty()) {
                sql.append("PARTITION BY ");
                List<String> pks = new ArrayList<>();
                for (String pk : limitDef.getPartitionKeys()) {
                    pks.add(dialect.quoteIdentifier(pk));
                }
                sql.append(String.join(", ", pks));
            }
            sql.append(" ORDER BY ");
            List<String> orderClauses = new ArrayList<>();
            if (!validOrderSpecs.isEmpty()) {
                for (int i = 0; i < validOrderSpecs.size(); i++) {
                    OrderSpec spec = validOrderSpecs.get(i);
                    String orderCol = "_order_metric_" + i;
                    // TODO nulls last support if possible, for now just desc/asc
                    String clause = orderCol + (spec.isDesc() ? " DESC" : " ASC");
                    orderClauses.add(clause);
                }
            }
            // Always tie-break with target key ASC
            orderClauses.add(dialect.quoteIdentifier(limitDef.getTargetKey()) + " ASC");
            sql.append(String.join(", ", orderClauses));
            sql.append(") AS rn\n  FROM ").append(domainCte).append("\n");
            sql.append(")");

            // Generate filtered CTE
            sql.append(",\n").append(filteredCte).append(" AS (\n");
            sql.append("  SELECT ");
            sql.append(String.join(", ", selectFields));
            sql.append("\n  FROM ").append(rankedCte);
            sql.append("\n  WHERE rn <= ").append(limitDef.getLimit()).append("\n");
            sql.append(")");

            // Build join condition for final select
            StringBuilder joinCond = new StringBuilder();
            joinCond.append("INNER JOIN ").append(filteredCte).append(" ").append(filteredCte)
                    .append(" ON ");
            List<String> joinOn = new ArrayList<>();
            for (String field : limitDef.getPartitionKeys()) {
                // MySQL null safe equal <=> or COALESCE or just normal equal?
                // Pivot memory fallback treats null=null as group match.
                // In SQL, JOIN ON b.k = r.k drops nulls unless we do COALESCE or IS NOT DISTINCT FROM.
                String quoted = dialect.quoteIdentifier(field);
                joinOn.add("(b." + quoted + " = " + filteredCte + "." + quoted + " OR (b." + quoted + " IS NULL AND " + filteredCte + "." + quoted + " IS NULL))");
            }
            String targetQuoted = dialect.quoteIdentifier(limitDef.getTargetKey());
            joinOn.add("(b." + targetQuoted + " = " + filteredCte + "." + targetQuoted + " OR (b." + targetQuoted + " IS NULL AND " + filteredCte + "." + targetQuoted + " IS NULL))");
            
            joinCond.append(String.join(" AND ", joinOn));
            joinConditions.add(joinCond.toString());

            cteIndex++;
        }
        return cteIndex;
    }

    private static String buildAggForOrder(OrderSpec spec, FDialect dialect) {
        String quoted = dialect.quoteIdentifier(spec.getField());
        if (spec.getAggregation() != null) {
            return spec.getAggregation() + "(" + quoted + ")";
        }
        // Fallback or generic metric handling
        return "SUM(" + quoted + ")";
    }

    private static List<OrderSpec> validateAndParseOrderSpecs(List<String> orderByList, QueryModel queryModel, List<String> availableMetrics) {
        if (orderByList == null || orderByList.isEmpty()) {
            return Collections.emptyList();
        }
        List<OrderSpec> specs = new ArrayList<>();
        for (String ob : orderByList) {
            boolean desc = ob.startsWith("-");
            String field = desc ? ob.substring(1) : ob;
            
            if (!availableMetrics.contains(field)) {
                // If it's a dimension, we don't aggregate it? Wait, order by can be a metric.
                // Pivot AxisHavingFilter normally orders by metrics. If dimension, it's not supported in limits typically.
                // In S13, we focus on metrics.
                throw new IllegalArgumentException("Cannot pushdown TopN order by field '" + field + "' because it is not an available metric.");
            }

            DbAggregation agg = MetricAdditivityAnalyzer.resolveAggregation(field, queryModel);
            if (agg == null || agg == DbAggregation.NONE) {
                // Default to SUM
                specs.add(new OrderSpec(field, desc, "SUM"));
            } else if (agg == DbAggregation.SUM || agg == DbAggregation.COUNT) {
                specs.add(new OrderSpec(field, desc, "SUM")); // Count gets SUMed up from base relation
            } else if (agg == DbAggregation.MIN) {
                specs.add(new OrderSpec(field, desc, "MIN"));
            } else if (agg == DbAggregation.MAX) {
                specs.add(new OrderSpec(field, desc, "MAX"));
            } else {
                // fail-closed for AVG, COUNT_DISTINCT, etc.
                throw new UnsupportedOperationException("Cannot pushdown TopN order by non-additive metric '" + field + "' (Aggregation: " + agg + "). Use memory fallback.");
            }
        }
        return specs;
    }

    private static List<DomainLimitDef> extractLimits(List<AxisField> axisFields, List<String> resolvedFieldNames) {
        if (axisFields == null || axisFields.isEmpty()) return Collections.emptyList();
        List<DomainLimitDef> limits = new ArrayList<>();
        List<String> currentPartitions = new ArrayList<>();
        
        for (int i = 0; i < axisFields.size(); i++) {
            AxisField af = axisFields.get(i);
            String fieldName = af.getField(); // Note: we should use resolvedFieldNames if needed
            
            if (af.getLimit() != null && af.getLimit() > 0) {
                DomainLimitDef def = new DomainLimitDef();
                def.setPartitionKeys(new ArrayList<>(currentPartitions));
                def.setTargetKey(fieldName);
                def.setLimit(af.getLimit());
                def.setOrderBy(af.getOrderBy() != null ? af.getOrderBy() : Collections.emptyList());
                limits.add(def);
            }
            currentPartitions.add(fieldName);
        }
        return limits;
    }

    @Getter
    private static class DomainLimitDef {
        private List<String> partitionKeys;
        private String targetKey;
        private int limit;
        private List<String> orderBy;
        
        public void setPartitionKeys(List<String> partitionKeys) { this.partitionKeys = partitionKeys; }
        public void setTargetKey(String targetKey) { this.targetKey = targetKey; }
        public void setLimit(int limit) { this.limit = limit; }
        public void setOrderBy(List<String> orderBy) { this.orderBy = orderBy; }
    }

    @Getter
    private static class OrderSpec {
        private final String field;
        private final boolean desc;
        private final String aggregation;

        public OrderSpec(String field, boolean desc, String aggregation) {
            this.field = field;
            this.desc = desc;
            this.aggregation = aggregation;
        }
    }
}
