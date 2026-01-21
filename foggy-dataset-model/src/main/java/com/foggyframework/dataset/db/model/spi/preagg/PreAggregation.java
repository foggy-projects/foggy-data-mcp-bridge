package com.foggyframework.dataset.db.model.spi.preagg;

import com.foggyframework.dataset.db.model.def.preagg.PreAggFilterDef;
import com.foggyframework.dataset.db.model.def.preagg.PreAggRefreshDef;
import com.foggyframework.dataset.db.model.spi.DbAggregation;
import com.foggyframework.dataset.db.model.spi.QueryObject;

import java.time.LocalDate;
import java.time.LocalDateTime;
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
     * 获取维度属性在预聚合表中的列名映射
     * <p>
     * key 格式：dimensionName$propertyName（如 salesDate$caption, product$categoryName）
     * value: 预聚合表中的实际列名（如 full_date, category_name）
     * </p>
     *
     * @return 维度属性到列名的映射
     */
    Map<String, String> getDimensionPropertyColumnNames();

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

    // ==================== 混合查询支持（Hybrid Query） ====================

    /**
     * 获取最后一次刷新时间
     * <p>
     * 用于混合查询时判断预聚合数据的时效性。
     * </p>
     *
     * @return 最后刷新时间，如果从未刷新返回 null
     */
    LocalDateTime getLastRefreshTime();

    /**
     * 设置最后一次刷新时间
     *
     * @param lastRefreshTime 刷新时间
     */
    void setLastRefreshTime(LocalDateTime lastRefreshTime);

    /**
     * 获取数据水位线（最后处理的数据时间）
     * <p>
     * 对于增量刷新的预聚合，水位线表示已处理数据的最大时间戳。
     * 查询时，水位线之后的数据需要从原始表获取。
     * </p>
     *
     * @return 水位线值（通常是日期或时间戳），如果没有水位线返回 null
     */
    Object getDataWatermark();

    /**
     * 设置数据水位线
     *
     * @param watermark 水位线值
     */
    void setDataWatermark(Object watermark);

    /**
     * 获取水位线对应的维度列名
     * <p>
     * 用于构建混合查询的 WHERE 条件。
     * 格式：dimensionName$propertyName 或 dimensionName$id
     * </p>
     *
     * @return 水位线列名
     */
    default String getWatermarkColumn() {
        PreAggRefreshDef refreshConfig = getRefreshConfig();
        return refreshConfig != null ? refreshConfig.getWatermarkColumn() : null;
    }

    /**
     * 判断是否支持混合查询
     * <p>
     * 只有配置了水位线列的预聚合才支持混合查询。
     * </p>
     *
     * @return true 如果支持混合查询
     */
    default boolean supportsHybridQuery() {
        String watermarkColumn = getWatermarkColumn();
        return watermarkColumn != null && !watermarkColumn.isEmpty();
    }

    /**
     * 判断预聚合数据是否过期
     * <p>
     * 根据数据水位线或最后刷新时间判断数据是否需要重新加载。
     * <ul>
     *   <li>如果设置了水位线且为日期类型，则根据水位线日期判断</li>
     *   <li>如果水位线为今天或之后，数据不过期</li>
     *   <li>如果水位线在今天之前，数据过期</li>
     *   <li>如果没有水位线，则根据最后刷新时间判断（超过24小时视为过期）</li>
     * </ul>
     * </p>
     *
     * @return true 如果数据可能过期
     */
    default boolean isDataStale() {
        // 优先检查数据水位线
        Object watermark = getDataWatermark();
        if (watermark != null) {
            LocalDate today = LocalDate.now();
            if (watermark instanceof LocalDate watermarkDate) {
                // 水位线为今天或之后，数据不过期
                return watermarkDate.isBefore(today);
            }
            if (watermark instanceof LocalDateTime watermarkDateTime) {
                // 水位线为今天或之后，数据不过期
                return watermarkDateTime.toLocalDate().isBefore(today);
            }
        }

        // 没有水位线，根据最后刷新时间判断
        LocalDateTime lastRefresh = getLastRefreshTime();
        if (lastRefresh == null) {
            return true; // 从未刷新，视为过期
        }
        // 简单判断：超过24小时视为过期（可根据 refresh config 调整）
        return lastRefresh.plusHours(24).isBefore(LocalDateTime.now());
    }
}
