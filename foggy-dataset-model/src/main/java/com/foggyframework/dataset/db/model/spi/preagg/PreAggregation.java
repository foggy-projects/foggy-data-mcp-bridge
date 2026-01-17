package com.foggyframework.dataset.db.model.spi.preagg;

import com.foggyframework.dataset.db.model.def.preagg.PreAggFilterDef;
import com.foggyframework.dataset.db.model.def.preagg.PreAggRefreshDef;
import com.foggyframework.dataset.db.model.spi.DbAggregation;
import com.foggyframework.dataset.db.model.spi.QueryObject;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 预聚合运行时接口
 * <p>
 * 表示一个已加载的预聚合定义，提供查询匹配和 SQL 重写所需的信息。
 * </p>
 *
 * @author foggy-framework
 * @since 8.2.0
 */
public interface PreAggregation {

    /**
     * 获取预聚合名称
     */
    String getName();

    /**
     * 获取显示名称
     */
    String getCaption();

    /**
     * 获取预聚合表名
     */
    String getTableName();

    /**
     * 获取 schema（可选）
     */
    String getSchema();

    /**
     * 获取预聚合表作为 QueryObject
     */
    QueryObject getQueryObject();

    /**
     * 获取优先级（1-100）
     */
    int getPriority();

    /**
     * 获取包含的维度名称集合
     */
    Set<String> getDimensionNames();

    /**
     * 获取维度数量
     */
    default int getDimensionCount() {
        Set<String> dims = getDimensionNames();
        return dims != null ? dims.size() : 0;
    }

    /**
     * 获取各维度的粒度配置
     *
     * @return key: 维度名称, value: 时间粒度（非时间维度返回 null）
     */
    Map<String, TimeGranularity> getGranularities();

    /**
     * 获取维度属性配置
     *
     * @return key: 维度名称, value: 包含的属性名称集合
     */
    Map<String, Set<String>> getDimensionProperties();

    /**
     * 获取度量及其聚合方式
     *
     * @return key: 度量名称, value: 聚合方式
     */
    Map<String, DbAggregation> getMeasureAggregations();

    /**
     * 获取度量在预聚合表中的列名映射
     *
     * @return key: 度量名称, value: 预聚合表中的列名
     */
    Map<String, String> getMeasureColumnNames();

    /**
     * 获取永久过滤条件
     */
    List<PreAggFilterDef> getFilters();

    /**
     * 获取刷新配置
     */
    PreAggRefreshDef getRefreshConfig();

    /**
     * 是否启用
     */
    boolean isEnabled();

    /**
     * 获取粒度级别（用于评分）
     * <p>
     * 返回所有时间维度中最细粒度的级别。
     * 级别越小表示粒度越细。
     * </p>
     */
    default int getGranularityLevel() {
        Map<String, TimeGranularity> granularities = getGranularities();
        if (granularities == null || granularities.isEmpty()) {
            return 0;
        }
        return granularities.values().stream()
                .filter(g -> g != null)
                .mapToInt(TimeGranularity::getLevel)
                .min()
                .orElse(0);
    }

    /**
     * 检查是否包含指定维度
     */
    default boolean hasDimension(String dimensionName) {
        Set<String> dims = getDimensionNames();
        return dims != null && dims.contains(dimensionName);
    }

    /**
     * 检查是否包含指定度量
     */
    default boolean hasMeasure(String measureName) {
        Map<String, DbAggregation> measures = getMeasureAggregations();
        return measures != null && measures.containsKey(measureName);
    }

    /**
     * 获取指定维度的粒度
     *
     * @param dimensionName 维度名称
     * @return 粒度，如果该维度不存在或非时间维度返回 null
     */
    default TimeGranularity getGranularity(String dimensionName) {
        Map<String, TimeGranularity> granularities = getGranularities();
        return granularities != null ? granularities.get(dimensionName) : null;
    }

    /**
     * 获取指定维度的属性集合
     *
     * @param dimensionName 维度名称
     * @return 属性名称集合，如果该维度不存在返回空集合
     */
    default Set<String> getDimensionProperties(String dimensionName) {
        Map<String, Set<String>> props = getDimensionProperties();
        return props != null ? props.getOrDefault(dimensionName, Set.of()) : Set.of();
    }
}
