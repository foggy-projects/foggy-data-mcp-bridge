package com.foggyframework.dataset.model.engine.preagg;

import com.foggyframework.dataset.model.spi.DbAggregation;
import com.foggyframework.dataset.model.spi.preagg.PreAggregation;
import com.foggyframework.dataset.model.spi.preagg.TimeGranularity;
import com.foggyframework.dataset.model.semantic.permission.PermissionPredicate;
import lombok.Data;

import java.util.*;

/**
 * 预聚合查询需求
 * <p>
 * 从 JdbcQuery 中提取的查询需求，用于匹配预聚合表。
 * </p>
 *
 * @author foggy-framework
 * @since 8.2.0
 */
@Data
public class PreAggQueryRequirement {

    /**
     * 查询的维度名称集合
     */
    private Set<String> dimensionNames = new LinkedHashSet<>();

    /**
     * 查询的维度属性（维度名 -> 属性名集合）
     */
    private Map<String, Set<String>> dimensionProperties = new LinkedHashMap<>();

    /**
     * 查询的度量及其聚合方式（度量名 -> 聚合类型）
     */
    private Map<String, DbAggregation> measureAggregations = new LinkedHashMap<>();

    /**
     * 时间维度的查询粒度（维度名 -> 粒度）
     */
    private Map<String, TimeGranularity> queryGranularities = new LinkedHashMap<>();

    /**
     * 是否有分组（GROUP BY）
     */
    private boolean hasGroupBy;

    /**
     * 是否有 WHERE 条件（用于判断是否可以使用预聚合）
     * <p>
     * 当查询包含 WHERE 条件时，需要检查预聚合是否支持这些条件。
     * 如果预聚合不支持 WHERE 条件透传，则不应使用预聚合。
     * </p>
     */
    private boolean hasWhereConditions;

    /**
     * 是否有自定义 SQL 条件（query.andSql() 等）
     * <p>
     * 自定义 SQL 条件无法解析，因此不支持预聚合。
     * </p>
     */
    private boolean hasCustomSqlConditions;

    /**
     * Slice 条件涉及的列（维度名$属性名 -> 原始字段名）
     * <p>
     * 用于检查预聚合是否包含这些列。
     * </p>
     */
    private Map<String, SliceColumnInfo> sliceColumns = new LinkedHashMap<>();

    /**
     * Typed row-permission obligations. They are tracked separately from user
     * slices so candidate rejection can fail closed with a stable reason.
     */
    private List<PermissionPredicate> securityPredicates = new ArrayList<>();

    /**
     * Protected routing requires one engine-generated permission signature.
     */
    private boolean securityContextCacheable = true;

    /**
     * Slice 列信息
     */
    @lombok.Data
    @lombok.AllArgsConstructor
    public static class SliceColumnInfo {
        /** 维度名（如 product） */
        private String dimensionName;
        /** 属性名（如 categoryName），可能为 null 表示维度主键 */
        private String propertyName;
        /** 原始字段名（如 product$categoryName） */
        private String originalField;
    }

    /**
     * 添加维度
     */
    public void addDimension(String dimensionName) {
        dimensionNames.add(dimensionName);
    }

    /**
     * 添加维度属性
     */
    public void addDimensionProperty(String dimensionName, String propertyName) {
        dimensionProperties.computeIfAbsent(dimensionName, k -> new LinkedHashSet<>()).add(propertyName);
    }

    /**
     * 添加度量
     */
    public void addMeasure(String measureName, DbAggregation aggregation) {
        measureAggregations.put(measureName, aggregation);
    }

    /**
     * 设置时间维度的查询粒度
     */
    public void setTimeGranularity(String dimensionName, TimeGranularity granularity) {
        if (dimensionName == null || granularity == null) {
            return;
        }
        queryGranularities.merge(dimensionName, granularity,
                PreAggQueryRequirement::mergeRequiredGranularity);
    }

    private static TimeGranularity mergeRequiredGranularity(TimeGranularity existing,
                                                             TimeGranularity candidate) {
        if (existing == candidate) {
            return existing;
        }
        // Natural weeks are not nested inside calendar months/quarters/years.
        // A query that needs both shapes requires at least DAY materialization
        // grain; retaining WEEK would incorrectly accept a weekly candidate.
        if ((existing == TimeGranularity.WEEK && isCalendarPeriod(candidate))
                || (candidate == TimeGranularity.WEEK && isCalendarPeriod(existing))) {
            return TimeGranularity.DAY;
        }
        return existing.getMinuteMultiplier() <= candidate.getMinuteMultiplier()
                ? existing : candidate;
    }

    private static boolean isCalendarPeriod(TimeGranularity granularity) {
        return granularity == TimeGranularity.MONTH
                || granularity == TimeGranularity.QUARTER
                || granularity == TimeGranularity.YEAR;
    }

    /**
     * 添加 Slice 列信息
     *
     * @param field 字段名（如 product$categoryName）
     */
    public void addSliceColumn(String field) {
        if (field == null || field.isEmpty()) {
            return;
        }
        int dollarIndex = field.indexOf('$');
        String dimensionName;
        String propertyName;
        if (dollarIndex > 0) {
            dimensionName = field.substring(0, dollarIndex);
            propertyName = field.substring(dollarIndex + 1);
        } else {
            // 没有 $，可能是度量或维度主键
            dimensionName = field;
            propertyName = null;
        }
        sliceColumns.put(field, new SliceColumnInfo(dimensionName, propertyName, field));
    }

    /**
     * 获取维度数量
     */
    public int getDimensionCount() {
        return dimensionNames.size();
    }

    /**
     * 获取粒度级别（用于评分）
     * <p>
     * 返回所有时间维度中最细粒度的级别。
     * 级别越小表示粒度越细。
     * </p>
     */
    public int getGranularityLevel() {
        if (queryGranularities.isEmpty()) {
            return 0;
        }
        return queryGranularities.values().stream()
                .filter(Objects::nonNull)
                .mapToInt(TimeGranularity::getLevel)
                .min()
                .orElse(0);
    }

    /**
     * 检查预聚合是否满足此查询需求
     * <p>
     * 匹配规则：
     * <ul>
     *   <li>查询维度 ⊆ 预聚合维度</li>
     *   <li>查询属性 ⊆ 明确声明的物化属性</li>
     *   <li>查询粒度 ≥ 预聚合粒度（可向上聚合）</li>
     *   <li>查询度量 ⊆ 预聚合度量</li>
     *   <li>聚合方式兼容</li>
     * </ul>
     * </p>
     *
     * @param preAgg 预聚合
     * @return 是否满足
     */
    public boolean isSatisfiableBy(PreAggregation preAgg) {
        return incompatibilityReasonCode(preAgg) == null;
    }

    /**
     * Evaluates one materialization candidate without exposing query values.
     *
     * <p>The returned reason code is deliberately stable and field-value free,
     * so explain mode can report why a candidate was rejected without parsing
     * debug logs or reverse engineering generated SQL.</p>
     */
    public String incompatibilityReasonCode(PreAggregation preAgg) {
        if (preAgg == null) {
            return "PREAGG_CANDIDATE_UNAVAILABLE";
        }
        String securityFailure = securityFailureReason(preAgg);
        if (securityFailure != null) {
            return "PREAGG_SECURITY_" + securityFailure;
        }
        // A permanently filtered materialization is not equivalent to an
        // unfiltered semantic model unless its filter implication can be
        // proven. That proof is not represented in the current requirement,
        // so filtered pre-aggregations must fail closed.
        if (preAgg.getFilters() != null && !preAgg.getFilters().isEmpty()) {
            return "PREAGG_FILTER_IMPLICATION_UNPROVEN";
        }

        // 1. 检查维度：查询的维度必须都在预聚合中
        for (String dim : dimensionNames) {
            if (!preAgg.hasDimension(dim)) {
                return "PREAGG_DIMENSION_MISSING";
            }
        }

        // 2. 每个查询属性都必须有明确物化契约。粒度只证明 rollup
        // 兼容性，不能证明 month/year/caption/id 等物理列存在。
        for (Map.Entry<String, Set<String>> entry : dimensionProperties.entrySet()) {
            String dimName = entry.getKey();
            Set<String> queryProps = entry.getValue();

            for (String prop : queryProps) {
                if (!preAgg.hasMaterializedDimensionProperty(dimName, prop)) {
                    return "PREAGG_DIMENSION_PROPERTY_NOT_MATERIALIZED";
                }
            }
        }

        // 3. 检查时间粒度：查询粒度 >= 预聚合粒度
        for (Map.Entry<String, TimeGranularity> entry : queryGranularities.entrySet()) {
            String dimName = entry.getKey();
            TimeGranularity queryGranularity = entry.getValue();
            TimeGranularity preAggGranularity = preAgg.getGranularity(dimName);

            // Once the semantic query proves a temporal grain, the
            // materialization must declare its own grain as well. Treating a
            // missing declaration as an implicit fine-grained key would turn
            // an unknown contract into an unsafe optimization assumption.
            if (queryGranularity != null && preAggGranularity == null) {
                return "PREAGG_TIME_GRAIN_UNDECLARED";
            }
            if (preAggGranularity != null && queryGranularity != null
                    && !preAggGranularity.canRollupTo(queryGranularity)) {
                return "PREAGG_TIME_GRAIN_INCOMPATIBLE";
            }
        }

        // 4. 检查度量：查询的度量必须都在预聚合中
        for (Map.Entry<String, DbAggregation> entry : measureAggregations.entrySet()) {
            String measureName = entry.getKey();
            DbAggregation queryAgg = entry.getValue();

            if (!preAgg.hasMeasure(measureName)) {
                return "PREAGG_MEASURE_MISSING";
            }

            // 5. 检查聚合兼容性
            DbAggregation preAggAgg = preAgg.getMeasureAggregations().get(measureName);
            if (!isAggregationCompatible(preAggAgg, queryAgg)) {
                return "PREAGG_AGGREGATION_INCOMPATIBLE";
            }
        }

        // 6. 检查 Slice 列：Slice 涉及的列必须都在预聚合中
        for (SliceColumnInfo sliceCol : sliceColumns.values()) {
            String dimName = sliceCol.getDimensionName();
            String propName = sliceCol.getPropertyName();

            // 首先检查维度是否存在
            if (!preAgg.hasDimension(dimName)) {
                return "PREAGG_SLICE_DIMENSION_MISSING";
            }

            // Slice 属性同样不能依赖粒度或列名猜测。
            if (propName != null && !propName.isEmpty()) {
                if (!preAgg.hasMaterializedDimensionProperty(dimName, propName)) {
                    return "PREAGG_SLICE_PROPERTY_NOT_MATERIALIZED";
                }
            } else if (!preAgg.hasMaterializedDimensionProperty(dimName, "id")) {
                return "PREAGG_SLICE_PROPERTY_NOT_MATERIALIZED";
            }
        }

        return null;
    }

    /**
     * Returns a stable, value-free reason when a candidate cannot reproduce
     * the effective permission before rollup.
     */
    public String securityFailureReason(PreAggregation preAgg) {
        if (!securityContextCacheable) {
            return "MISSING_AUTHORIZATION_SIGNATURE";
        }
        for (PermissionPredicate predicate : securityPredicates) {
            if (predicate == null || !predicate.isProvable()) {
                return "UNPROVABLE_SECURITY_PREDICATE";
            }
            if (!isSupportedSecurityOperator(predicate.getOperator())) {
                return "UNSUPPORTED_SECURITY_OPERATOR";
            }
            for (String field : predicate.getReferencedFields()) {
                if (!isMaterializedSecurityField(preAgg, field)) {
                    return "MISSING_SECURITY_DIMENSION";
                }
            }
        }
        return null;
    }

    private boolean isMaterializedSecurityField(PreAggregation preAgg, String field) {
        if (preAgg == null || field == null || field.isBlank()) {
            return false;
        }
        int dollarIndex = field.indexOf('$');
        if (dollarIndex <= 0) {
            return preAgg.hasDimension(field)
                    && preAgg.hasMaterializedDimensionProperty(field, "id");
        }
        String dimension = field.substring(0, dollarIndex);
        String property = field.substring(dollarIndex + 1);
        return !dimension.isBlank() && !property.isBlank()
                && preAgg.hasMaterializedDimensionProperty(dimension, property);
    }

    private boolean isSupportedSecurityOperator(String operator) {
        return Set.of("=", "!=", "<>", ">", ">=", "<", "<=", "in",
                        "like", "left_like", "right_like", "[)", "[]", "(]", "()")
                .contains(operator);
    }

    /**
     * 检查聚合类型是否兼容
     * <p>
     * 兼容规则：
     * <ul>
     *   <li>相同聚合类型（SUM→SUM, MIN→MIN, MAX→MAX 等）直接兼容</li>
     *   <li>COUNT → SUM（rollup 时 COUNT 变成 SUM）</li>
     *   <li>AVG → 需要 SUM + COUNT（暂不支持）</li>
     * </ul>
     * </p>
     *
     * @param preAggAgg 预聚合中的聚合方式
     * @param queryAgg  查询请求的聚合方式
     * @return 是否兼容
     */
    private boolean isAggregationCompatible(DbAggregation preAggAgg, DbAggregation queryAgg) {
        if (preAggAgg == null || queryAgg == null) {
            return false;
        }
        if (preAggAgg == queryAgg) {
            return true;
        }
        // COUNT 可以 rollup 到 SUM
        return preAggAgg == DbAggregation.COUNT && queryAgg == DbAggregation.SUM;
    }

    @Override
    public String toString() {
        return "PreAggQueryRequirement{" +
                "dimensions=" + dimensionNames +
                ", properties=" + dimensionProperties +
                ", measures=" + measureAggregations.keySet() +
                ", granularities=" + queryGranularities +
                ", hasGroupBy=" + hasGroupBy +
                ", hasWhereConditions=" + hasWhereConditions +
                ", hasCustomSqlConditions=" + hasCustomSqlConditions +
                ", sliceColumns=" + sliceColumns.keySet() +
                ", securityPredicates=" + securityPredicates.size() +
                ", securityContextCacheable=" + securityContextCacheable +
                '}';
    }
}
