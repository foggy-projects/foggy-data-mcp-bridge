package com.foggyframework.dataset.db.model.impl.preagg;

import com.foggyframework.core.utils.StringUtils;
import com.foggyframework.dataset.db.model.def.preagg.PreAggFilterDef;
import com.foggyframework.dataset.db.model.def.preagg.PreAggMeasureDef;
import com.foggyframework.dataset.db.model.def.preagg.PreAggRefreshDef;
import com.foggyframework.dataset.db.model.def.preagg.PreAggregationDef;
import com.foggyframework.dataset.db.model.spi.DbAggregation;
import com.foggyframework.dataset.db.model.spi.QueryObject;
import com.foggyframework.dataset.db.model.spi.preagg.PreAggregation;
import com.foggyframework.dataset.db.model.spi.preagg.TimeGranularity;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 预聚合实现类
 * <p>
 * 从 {@link PreAggregationDef} 构建运行时预聚合对象。
 * </p>
 *
 * @author foggy-framework
 * @since 8.2.0
 */
@Getter
public class PreAggregationImpl implements PreAggregation {

    private final String name;
    private final String caption;
    private final String tableName;
    private final String schema;
    private final int priority;
    private final Set<String> dimensionNames;
    private final Map<String, TimeGranularity> granularities;
    private final Map<String, Set<String>> dimensionProperties;
    private final Map<String, DbAggregation> measureAggregations;
    private final Map<String, String> measureColumnNames;
    private final Map<String, String> dimensionPropertyColumnNames;
    private final List<PreAggFilterDef> filters;
    private final PreAggRefreshDef refreshConfig;
    private final boolean enabled;
    private final QueryObject queryObject;

    // ==================== 混合查询支持 ====================

    /**
     * 最后刷新时间
     */
    @Setter
    private LocalDateTime lastRefreshTime;

    /**
     * 数据水位线（最后处理的数据时间戳）
     */
    @Setter
    private Object dataWatermark;

    /**
     * 从定义构建预聚合实例
     *
     * @param def   预聚合定义
     * @param queryObject 预聚合表的 QueryObject
     */
    public PreAggregationImpl(PreAggregationDef def, QueryObject queryObject) {
        this.name = def.getName();
        this.caption = def.getCaption();
        this.tableName = def.getTableName();
        this.schema = def.getSchema();
        this.priority = def.getPriority();
        this.enabled = def.isEnabled();
        this.refreshConfig = def.getRefresh();
        this.filters = def.getFilters() != null ? new ArrayList<>(def.getFilters()) : Collections.emptyList();
        this.queryObject = queryObject;

        // 处理维度
        this.dimensionNames = def.getDimensions() != null
                ? new LinkedHashSet<>(def.getDimensions())
                : Collections.emptySet();

        // 处理粒度
        this.granularities = parseGranularities(def.getGranularity());

        // 处理维度属性
        this.dimensionProperties = parseDimensionProperties(def.getDimensionProperties());

        // 构建维度属性列名映射
        this.dimensionPropertyColumnNames = buildDimensionPropertyColumnNames(def);

        // 处理度量
        this.measureAggregations = new LinkedHashMap<>();
        this.measureColumnNames = new LinkedHashMap<>();
        if (def.getMeasures() != null) {
            for (PreAggMeasureDef measure : def.getMeasures()) {
                DbAggregation agg = parseAggregation(measure.getAggregation());
                measureAggregations.put(measure.getName(), agg);
                measureColumnNames.put(measure.getName(), measure.getActualColumnName());
            }
        }
    }

    /**
     * 解析粒度配置
     */
    private Map<String, TimeGranularity> parseGranularities(Map<String, String> granularityConfig) {
        if (granularityConfig == null || granularityConfig.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, TimeGranularity> result = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : granularityConfig.entrySet()) {
            TimeGranularity g = TimeGranularity.fromConfigName(entry.getValue());
            if (g != null) {
                result.put(entry.getKey(), g);
            }
        }
        return result;
    }

    /**
     * 解析维度属性配置
     */
    private Map<String, Set<String>> parseDimensionProperties(Map<String, List<String>> propsConfig) {
        if (propsConfig == null || propsConfig.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, Set<String>> result = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : propsConfig.entrySet()) {
            if (entry.getValue() != null && !entry.getValue().isEmpty()) {
                result.put(entry.getKey(), new LinkedHashSet<>(entry.getValue()));
            }
        }
        return result;
    }

    /**
     * 构建维度属性列名映射
     * <p>
     * 使用命名约定推断列名：
     * <ul>
     *   <li>caption: 根据维度名推断（date维度用full_date，其他用dimName_name）</li>
     *   <li>id: 使用 dimName_key（如 date_key, product_key）</li>
     *   <li>其他属性: 使用 snake_case 转换</li>
     * </ul>
     * </p>
     */
    private Map<String, String> buildDimensionPropertyColumnNames(PreAggregationDef def) {
        Map<String, String> result = new LinkedHashMap<>();

        // 首先尝试从显式配置读取（如果有的话）
        Map<String, Map<String, String>> dimPropColumns = def.getDimensionPropertyColumnNames();
        if (dimPropColumns != null) {
            for (Map.Entry<String, Map<String, String>> dimEntry : dimPropColumns.entrySet()) {
                String dimName = dimEntry.getKey();
                Map<String, String> propColumns = dimEntry.getValue();
                for (Map.Entry<String, String> propEntry : propColumns.entrySet()) {
                    String key = dimName + "$" + propEntry.getKey();
                    result.put(key, propEntry.getValue());
                }
            }
        }

        // 使用命名约定填充未配置的属性
        if (def.getDimensions() != null) {
            for (String dimName : def.getDimensions()) {
                String snakeCaseDimName = StringUtils.to_sm_string(dimName);

                // caption 映射
                String captionKey = dimName + "$caption";
                if (!result.containsKey(captionKey)) {
                    // 根据维度名推断caption列名
                    // 对于时间维度（包含 date/time/day 等关键字），使用 full_date
                    // 对于其他维度，使用 <dimName>_name
                    String captionColumn = inferCaptionColumn(snakeCaseDimName);
                    result.put(captionKey, captionColumn);
                }

                // id 映射
                String idKey = dimName + "$id";
                if (!result.containsKey(idKey)) {
                    // 命名约定：日期维度用 date_key，其他用 <dimName>_key
                    if (snakeCaseDimName.contains("date") || snakeCaseDimName.contains("time")
                            || snakeCaseDimName.contains("day")) {
                        result.put(idKey, "date_key");
                    } else {
                        result.put(idKey, snakeCaseDimName + "_key");
                    }
                }

                // 处理 dimensionProperties 中的其他属性
                if (def.getDimensionProperties() != null && def.getDimensionProperties().containsKey(dimName)) {
                    List<String> props = def.getDimensionProperties().get(dimName);
                    if (props != null) {
                        for (String propName : props) {
                            String propKey = dimName + "$" + propName;
                            if (!result.containsKey(propKey)) {
                                result.put(propKey, StringUtils.to_sm_string(propName));
                            }
                        }
                    }
                }
            }
        }

        return result;
    }

    /**
     * 根据维度名推断caption列名
     */
    private String inferCaptionColumn(String snakeCaseDimName) {
        // 时间维度使用 full_date
        if (snakeCaseDimName.contains("date") || snakeCaseDimName.contains("time")
                || snakeCaseDimName.contains("day") || snakeCaseDimName.equals("sales_date")) {
            return "full_date";
        }
        // 其他维度使用 <dimName>_name
        return snakeCaseDimName + "_name";
    }

    /**
     * 解析聚合类型
     */
    private DbAggregation parseAggregation(String aggString) {
        if (aggString == null || aggString.isEmpty()) {
            return DbAggregation.SUM; // 默认 SUM
        }
        try {
            return DbAggregation.valueOf(aggString.toUpperCase().trim());
        } catch (IllegalArgumentException e) {
            return DbAggregation.SUM;
        }
    }

    @Override
    public String toString() {
        return "PreAggregation{" +
                "name='" + name + '\'' +
                ", tableName='" + tableName + '\'' +
                ", priority=" + priority +
                ", dimensions=" + dimensionNames +
                ", measures=" + measureAggregations.keySet() +
                ", enabled=" + enabled +
                '}';
    }
}
