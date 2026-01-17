package com.foggyframework.dataset.db.model.engine.preagg;

import com.foggyframework.dataset.db.model.spi.DbAggregation;
import com.foggyframework.dataset.db.model.spi.preagg.PreAggregation;
import com.foggyframework.dataset.db.model.spi.preagg.TimeGranularity;
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
        queryGranularities.put(dimensionName, granularity);
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
     *   <li>查询属性 ⊆ 预聚合属性</li>
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
        // 1. 检查维度：查询的维度必须都在预聚合中
        for (String dim : dimensionNames) {
            if (!preAgg.hasDimension(dim)) {
                return false;
            }
        }

        // 2. 检查维度属性：查询的属性必须都在预聚合中
        for (Map.Entry<String, Set<String>> entry : dimensionProperties.entrySet()) {
            String dimName = entry.getKey();
            Set<String> queryProps = entry.getValue();
            Set<String> preAggProps = preAgg.getDimensionProperties(dimName);

            for (String prop : queryProps) {
                if (!preAggProps.contains(prop)) {
                    return false;
                }
            }
        }

        // 3. 检查时间粒度：查询粒度 >= 预聚合粒度
        for (Map.Entry<String, TimeGranularity> entry : queryGranularities.entrySet()) {
            String dimName = entry.getKey();
            TimeGranularity queryGranularity = entry.getValue();
            TimeGranularity preAggGranularity = preAgg.getGranularity(dimName);

            if (preAggGranularity != null && queryGranularity != null) {
                // 预聚合粒度必须能够 rollup 到查询粒度
                if (!preAggGranularity.canRollupTo(queryGranularity)) {
                    return false;
                }
            }
        }

        // 4. 检查度量：查询的度量必须都在预聚合中
        for (Map.Entry<String, DbAggregation> entry : measureAggregations.entrySet()) {
            String measureName = entry.getKey();
            DbAggregation queryAgg = entry.getValue();

            if (!preAgg.hasMeasure(measureName)) {
                return false;
            }

            // 5. 检查聚合兼容性
            DbAggregation preAggAgg = preAgg.getMeasureAggregations().get(measureName);
            if (!isAggregationCompatible(preAggAgg, queryAgg)) {
                return false;
            }
        }

        return true;
    }

    /**
     * 检查聚合类型是否兼容
     * <p>
     * 兼容规则：
     * <ul>
     *   <li>SUM → SUM（可 rollup）</li>
     *   <li>COUNT → SUM（rollup 时 COUNT 变成 SUM）</li>
     *   <li>MIN → MIN（可 rollup）</li>
     *   <li>MAX → MAX（可 rollup）</li>
     *   <li>AVG → 需要 SUM + COUNT（特殊处理）</li>
     * </ul>
     * </p>
     *
     * @param preAggAgg 预聚合中的聚合方式
     * @param queryAgg  查询请求的聚合方式
     * @return 是否兼容
     */
    private boolean isAggregationCompatible(DbAggregation preAggAgg, DbAggregation queryAgg) {
        if (preAggAgg == null || queryAgg == null) {
            return true; // 默认兼容
        }

        if (preAggAgg == queryAgg) {
            return true;
        }

        // SUM 可以 rollup 到 SUM
        if (preAggAgg == DbAggregation.SUM && queryAgg == DbAggregation.SUM) {
            return true;
        }

        // COUNT 可以 rollup 到 SUM
        if (preAggAgg == DbAggregation.COUNT && queryAgg == DbAggregation.SUM) {
            return true;
        }

        // MIN 可以 rollup 到 MIN
        if (preAggAgg == DbAggregation.MIN && queryAgg == DbAggregation.MIN) {
            return true;
        }

        // MAX 可以 rollup 到 MAX
        if (preAggAgg == DbAggregation.MAX && queryAgg == DbAggregation.MAX) {
            return true;
        }

        // AVG 需要特殊处理（需要 SUM + COUNT）
        // 暂时不支持直接 AVG rollup
        return false;
    }

    @Override
    public String toString() {
        return "PreAggQueryRequirement{" +
                "dimensions=" + dimensionNames +
                ", properties=" + dimensionProperties +
                ", measures=" + measureAggregations.keySet() +
                ", granularities=" + queryGranularities +
                ", hasGroupBy=" + hasGroupBy +
                '}';
    }
}
