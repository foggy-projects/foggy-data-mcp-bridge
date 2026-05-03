package com.foggyframework.dataset.db.model.engine.pivot.sql;

import com.foggyframework.dataset.db.dialect.FDialect;
import com.foggyframework.dataset.db.dialect.DbType;
import com.foggyframework.dataset.db.model.plugins.query_execution.AdditiveKind;
import com.foggyframework.dataset.db.model.plugins.query_execution.ManagedMetricMetadata;
import com.foggyframework.dataset.db.model.plugins.query_execution.ManagedSqlRelation;
import com.foggyframework.dataset.db.model.semantic.domain.pivot.AxisField;
import com.foggyframework.dataset.db.model.semantic.domain.pivot.MetricFilter;
import com.foggyframework.dataset.db.model.semantic.domain.pivot.PivotRequest;
import lombok.Builder;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Pivot 轴成员域 SQL Planner
 *
 * <p>根据 PivotRequest 中的轴级 having + limit/orderBy 配置，在 SQL 层生成
 * CTE + Window Function 下放，替代内存 {@code AxisHavingFilter + AxisTopNTruncator}。</p>
 *
 * <p>语义保证：having → TopN（先过滤再截断）。</p>
 *
 * <p>Planner 只消费 {@link ManagedSqlRelation} 的 capability metadata，
 * 不重新推断度量可加性。</p>
 */
public class PivotAxisDomainSqlPlanner {

    private static final Logger log = LoggerFactory.getLogger(PivotAxisDomainSqlPlanner.class);

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
        if (dialect == null || !dialect.supportsCte() || !dialect.supportsWindowFunctions()) {
            return false;
        }
        DbType dbType = dialect.getDbType();
        if (dbType == DbType.SQLITE || dbType == DbType.POSTGRESQL) {
            return true;
        }
        return dbType == DbType.MYSQL && dialect.getClass().getSimpleName().contains("Mysql8");
    }

    /**
     * 生成推导后的 SQL
     *
     * @param baseRelation queryModel prepare 返回的受管关系代数（含 capability metadata）
     * @param pivot        PivotRequest
     * @param rowFields    行轴字段名列表
     * @param colFields    列轴字段名列表
     * @param metrics      SQL 度量字段名列表
     * @return PlannedSql 或原始 SQL（如果无 having/limit）
     */
    public static PlannedSql plan(ManagedSqlRelation baseRelation,
                                  PivotRequest pivot,
                                  List<String> rowFields,
                                  List<String> colFields,
                                  List<String> metrics) {

        // Cascade detection and Phase 1 fallback guard are handled in PivotPipeline.
        // The planner natively supports the allowed C2 whitelist (e.g. rows two-level).

        // ===== Defensive assertions on capability metadata =====
        if (!baseRelation.isWrappable()) {
            throw new PivotPushdownUnsupportedException(
                    "ManagedSqlRelation is not wrappable. Cannot generate outer Pivot SQL. " +
                    "permissionValidated=" + baseRelation.isPermissionValidated());
        }
        if (!baseRelation.isPermissionValidated()) {
            throw new PivotPushdownUnsupportedException(
                    "ManagedSqlRelation permission has not been validated. Fail-closed for Pivot SQL pushdown.");
        }

        FDialect dialect = baseRelation.getDialect();
        if (!isSupported(dialect)) {
            throw new PivotPushdownUnsupportedException(
                    "Dialect " + dialect.getProductName() + " does not support CTE/Window Functions for Pivot SQL pushdown.");
        }

        // Build metric metadata index
        Map<String, ManagedMetricMetadata> metricIndex = new LinkedHashMap<>();
        for (ManagedMetricMetadata m : baseRelation.getMetrics()) {
            metricIndex.put(m.getMetricName(), m);
        }

        List<DomainPlanDef> rowPlans = extractDomainPlans(pivot.getRows(), rowFields, metricIndex, metrics);
        List<DomainPlanDef> colPlans = extractDomainPlans(pivot.getColumns(), colFields, metricIndex, metrics);

        if (rowPlans.isEmpty() && colPlans.isEmpty()) {
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

        cteIndex = processPlans(sql, finalParams, rowPlans, "row", cteIndex, joinConditions, dialect);
        cteIndex = processPlans(sql, finalParams, colPlans, "col", cteIndex, joinConditions, dialect);

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

    // ========== Domain Aggregate Registry & Plan Extraction ==========

    /**
     * 从轴字段列表提取 domain plan 定义。
     * <p>收集 having 和 orderBy 所需的全部聚合表达式，合并到 aggregate registry。</p>
     */
    private static List<DomainPlanDef> extractDomainPlans(List<AxisField> axisFields, List<String> resolvedFieldNames,
                                                          Map<String, ManagedMetricMetadata> metricIndex,
                                                          List<String> availableMetrics) {
        if (axisFields == null || axisFields.isEmpty()) return Collections.emptyList();
        List<DomainPlanDef> plans = new ArrayList<>();
        List<String> currentPartitions = new ArrayList<>();

        for (AxisField af : axisFields) {
            boolean hasLimit = af.getLimit() != null && af.getLimit() > 0;
            boolean hasHaving = af.getHaving() != null && !af.getHaving().isEmpty();

            if (!hasLimit && !hasHaving) {
                currentPartitions.add(af.getField());
                continue;
            }

            DomainPlanDef plan = new DomainPlanDef();
            plan.partitionKeys = new ArrayList<>(currentPartitions);
            plan.targetKey = af.getField();
            plan.limit = hasLimit ? af.getLimit() : -1; // -1 means no limit, only having
            plan.havingFilters = hasHaving ? af.getHaving() : Collections.emptyList();

            // ===== Domain Aggregate Registry =====
            // Merge all metrics needed by orderBy + having into a single registry
            Map<String, AggregateEntry> registry = new LinkedHashMap<>();

            // 1. orderBy metrics
            if (af.getOrderBy() != null) {
                for (String ob : af.getOrderBy()) {
                    boolean desc = ob.startsWith("-");
                    String field = desc ? ob.substring(1) : ob;
                    if (!availableMetrics.contains(field)) {
                        throw new PivotPushdownUnsupportedException(
                                "Cannot pushdown order by field '" + field + "': not an available metric.");
                    }
                    AggregateEntry entry = resolveAggregate(field, metricIndex);
                    entry.usedByOrderBy = true;
                    entry.orderByDesc = desc;
                    registry.putIfAbsent(field, entry);
                }
            }

            // 2. having metrics
            for (MetricFilter filter : plan.havingFilters) {
                String field = filter.getMetric();
                if (!availableMetrics.contains(field)) {
                    throw new PivotPushdownUnsupportedException(
                            "Cannot pushdown having filter on field '" + field + "': not an available metric.");
                }
                AggregateEntry existing = registry.get(field);
                if (existing == null) {
                    existing = resolveAggregate(field, metricIndex);
                    registry.put(field, existing);
                }
                existing.usedByHaving = true;
            }

            plan.aggregateRegistry = new ArrayList<>(registry.values());

            // Build orderBy spec from registry (preserve original orderBy order)
            plan.orderSpecs = new ArrayList<>();
            if (af.getOrderBy() != null) {
                for (String ob : af.getOrderBy()) {
                    boolean desc = ob.startsWith("-");
                    String field = desc ? ob.substring(1) : ob;
                    AggregateEntry entry = registry.get(field);
                    plan.orderSpecs.add(new OrderSpec(field, desc, entry.aggFunction, entry.registryAlias));
                }
            }

            plans.add(plan);
            currentPartitions.add(af.getField());
        }
        return plans;
    }

    /**
     * 从 metric metadata 解析聚合策略。fail-closed for non-additive.
     */
    private static AggregateEntry resolveAggregate(String field, Map<String, ManagedMetricMetadata> metricIndex) {
        AggregateEntry entry = new AggregateEntry();
        entry.metricName = field;
        entry.registryAlias = "_agg_" + field;

        ManagedMetricMetadata meta = metricIndex.get(field);
        if (meta == null) {
            // Not in queryModel measures — could be calculatedField
            // Default to SUM with UNKNOWN additiveKind → fail-closed
            throw new PivotPushdownUnsupportedException(
                    "Cannot pushdown domain aggregate for field '" + field +
                    "': no metric metadata available. Metric may be a calculatedField not supported for SQL pushdown.");
        }

        if (meta.getAdditiveKind() == AdditiveKind.NON_ADDITIVE) {
            throw new PivotPushdownUnsupportedException(
                    "Cannot pushdown domain aggregate for non-additive metric '" + field +
                    "' (aggregation: " + meta.getAggregationFunction() + "). Use memory fallback.");
        }
        if (meta.getAdditiveKind() == AdditiveKind.UNKNOWN) {
            throw new PivotPushdownUnsupportedException(
                    "Cannot pushdown domain aggregate for metric '" + field +
                    "' with unknown additivity. Use memory fallback.");
        }

        // ADDITIVE: determine re-aggregation function
        String origAgg = meta.getAggregationFunction();
        if (origAgg == null) origAgg = "SUM";
        switch (origAgg.toUpperCase()) {
            case "SUM":
            case "COUNT":
                entry.aggFunction = "SUM"; // COUNT gets SUMmed from base relation
                break;
            case "MIN":
                entry.aggFunction = "MIN";
                break;
            case "MAX":
                entry.aggFunction = "MAX";
                break;
            default:
                entry.aggFunction = "SUM";
                break;
        }
        return entry;
    }

    // ========== CTE SQL Generation ==========

    private static int processPlans(StringBuilder sql, List<Object> finalParams,
                                     List<DomainPlanDef> plans, String prefix,
                                    int startIndex, List<String> globalJoinConditions, FDialect dialect) {
        int cteIndex = startIndex;
        List<String> currentAxisJoinConditions = new ArrayList<>();

        for (DomainPlanDef plan : plans) {
            String domainCte = "_" + prefix + "_domain_" + cteIndex;
            String domainFilteredCte = "_" + prefix + "_domain_filtered_" + cteIndex;
            String rankedCte = "_" + prefix + "_ranked_" + cteIndex;
            String filteredCte = "_" + prefix + "_filtered_" + cteIndex;

            // ---- Domain CTE ----
            List<String> selectFields = new ArrayList<>();
            List<String> groupByFields = new ArrayList<>();
            for (String pk : plan.partitionKeys) {
                String q = dialect.quoteIdentifier(pk);
                selectFields.add("b." + q + " AS " + q);
                groupByFields.add("b." + q);
            }
            String targetQ = dialect.quoteIdentifier(plan.targetKey);
            selectFields.add("b." + targetQ + " AS " + targetQ);
            groupByFields.add("b." + targetQ);

            sql.append(",\n").append(domainCte).append(" AS (\n");
            sql.append("  SELECT ");
            sql.append(String.join(", ", selectFields));

            // Aggregate registry expressions
            if (!plan.aggregateRegistry.isEmpty()) {
                sql.append(", ");
                List<String> aggExprs = new ArrayList<>();
                for (AggregateEntry entry : plan.aggregateRegistry) {
                    aggExprs.add(entry.aggFunction + "(b." + dialect.quoteIdentifier(entry.metricName) + ") AS " + entry.registryAlias);
                }
                sql.append(String.join(", ", aggExprs));
            }

            sql.append("\n  FROM _base_relation b\n");

            // Add previous filtered CTE joins for THIS axis (Cascade surviving domain filtering)
            for (String joinCond : currentAxisJoinConditions) {
                sql.append("  ").append(joinCond).append("\n");
            }

            sql.append("  GROUP BY ").append(String.join(", ", groupByFields)).append("\n");
            sql.append(")");

            // ---- Domain Filtered CTE (Having) ----
            String sourceForRanked;
            if (!plan.havingFilters.isEmpty()) {
                sql.append(",\n").append(domainFilteredCte).append(" AS (\n");
                sql.append("  SELECT * FROM ").append(domainCte).append("\n");
                sql.append("  WHERE ");
                List<String> havingClauses = new ArrayList<>();
                for (MetricFilter filter : plan.havingFilters) {
                    AggregateEntry entry = findRegistryEntry(plan.aggregateRegistry, filter.getMetric());
                    havingClauses.add(entry.registryAlias + " " + filter.getOp() + " ?");
                    finalParams.add(filter.getValue());
                }
                sql.append(String.join(" AND ", havingClauses));
                sql.append("\n)");
                sourceForRanked = domainFilteredCte;
            } else {
                sourceForRanked = domainCte;
            }

            // ---- Ranked CTE ----
            if (plan.limit > 0) {
                sql.append(",\n").append(rankedCte).append(" AS (\n");
                sql.append("  SELECT *, ROW_NUMBER() OVER (");
                if (!plan.partitionKeys.isEmpty()) {
                    sql.append("PARTITION BY ");
                    List<String> pks = new ArrayList<>();
                    for (String pk : plan.partitionKeys) {
                        pks.add(dialect.quoteIdentifier(pk));
                    }
                    sql.append(String.join(", ", pks));
                }
                sql.append(" ORDER BY ");
                List<String> orderClauses = new ArrayList<>();
                for (OrderSpec spec : plan.orderSpecs) {
                    // NULL bucket for deterministic tie-breaking
                    orderClauses.add("CASE WHEN " + spec.registryAlias + " IS NULL THEN 1 ELSE 0 END ASC");
                    orderClauses.add(spec.registryAlias + (spec.desc ? " DESC" : " ASC"));
                }
                // Tie-breakers: full prefix key and current key with explicit NULL buckets.
                for (String pk : plan.partitionKeys) {
                    addDimensionTieBreaker(orderClauses, dialect.quoteIdentifier(pk));
                }
                addDimensionTieBreaker(orderClauses, targetQ);
                sql.append(String.join(", ", orderClauses));
                sql.append(") AS rn\n  FROM ").append(sourceForRanked).append("\n");
                sql.append(")");

                // Filtered CTE
                sql.append(",\n").append(filteredCte).append(" AS (\n");
                sql.append("  SELECT ");
                List<String> finalSelectFields = new ArrayList<>();
                for (String pk : plan.partitionKeys) {
                    finalSelectFields.add(dialect.quoteIdentifier(pk));
                }
                finalSelectFields.add(targetQ);
                sql.append(String.join(", ", finalSelectFields));
                sql.append("\n  FROM ").append(rankedCte);
                sql.append("\n  WHERE rn <= ?").append("\n");
                finalParams.add(plan.limit);
                sql.append(")");
            } else {
                // Having-only, no limit → use domainFilteredCte directly as the filtered source
                filteredCte = sourceForRanked;
            }

            // ---- Join condition for NEXT level and FINAL assembly ----
            StringBuilder joinCond = new StringBuilder();
            joinCond.append("INNER JOIN ").append(filteredCte).append(" ").append(filteredCte).append(" ON ");
            List<String> joinOn = new ArrayList<>();
            for (String field : plan.partitionKeys) {
                String q = dialect.quoteIdentifier(field);
                joinOn.add("(b." + q + " = " + filteredCte + "." + q + " OR (b." + q + " IS NULL AND " + filteredCte + "." + q + " IS NULL))");
            }
            joinOn.add("(b." + targetQ + " = " + filteredCte + "." + targetQ + " OR (b." + targetQ + " IS NULL AND " + filteredCte + "." + targetQ + " IS NULL))");
            joinCond.append(String.join(" AND ", joinOn));

            String currentJoin = joinCond.toString();
            currentAxisJoinConditions.add(currentJoin);
            globalJoinConditions.add(currentJoin);

            cteIndex++;
        }
        return cteIndex;
    }

    private static void addDimensionTieBreaker(List<String> orderClauses, String quotedKey) {
        orderClauses.add("CASE WHEN " + quotedKey + " IS NULL THEN 1 ELSE 0 END ASC");
        orderClauses.add(quotedKey + " ASC");
    }

    private static AggregateEntry findRegistryEntry(List<AggregateEntry> registry, String metricName) {
        for (AggregateEntry e : registry) {
            if (e.metricName.equals(metricName)) return e;
        }
        throw new IllegalStateException("Aggregate registry missing entry for metric: " + metricName);
    }

    // ========== Internal Data Structures ==========

    @Getter
    private static class DomainPlanDef {
        private List<String> partitionKeys;
        private String targetKey;
        private int limit; // -1 = no limit (having-only)
        private List<MetricFilter> havingFilters;
        private List<AggregateEntry> aggregateRegistry;
        private List<OrderSpec> orderSpecs;
    }

    private static class AggregateEntry {
        String metricName;
        String registryAlias;
        String aggFunction;
        boolean usedByOrderBy;
        boolean orderByDesc;
        boolean usedByHaving;
    }

    @Getter
    private static class OrderSpec {
        private final String field;
        private final boolean desc;
        private final String aggFunction;
        private final String registryAlias;

        OrderSpec(String field, boolean desc, String aggFunction, String registryAlias) {
            this.field = field;
            this.desc = desc;
            this.aggFunction = aggFunction;
            this.registryAlias = registryAlias;
        }
    }
}
