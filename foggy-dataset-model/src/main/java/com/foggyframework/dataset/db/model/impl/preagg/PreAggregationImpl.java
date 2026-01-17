package com.foggyframework.dataset.db.model.impl.preagg;

import com.foggyframework.dataset.db.model.def.preagg.PreAggFilterDef;
import com.foggyframework.dataset.db.model.def.preagg.PreAggMeasureDef;
import com.foggyframework.dataset.db.model.def.preagg.PreAggRefreshDef;
import com.foggyframework.dataset.db.model.def.preagg.PreAggregationDef;
import com.foggyframework.dataset.db.model.spi.DbAggregation;
import com.foggyframework.dataset.db.model.spi.QueryObject;
import com.foggyframework.dataset.db.model.spi.preagg.PreAggregation;
import com.foggyframework.dataset.db.model.spi.preagg.TimeGranularity;
import lombok.Getter;

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
    private final List<PreAggFilterDef> filters;
    private final PreAggRefreshDef refreshConfig;
    private final boolean enabled;
    private final QueryObject queryObject;

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

        // 处理度量
        this.measureAggregations = new LinkedHashMap<>();
        this.measureColumnNames = new LinkedHashMap<>();
        if (def.getMeasures() != null) {
            for (PreAggMeasureDef measure : def.getMeasures()) {
                DbAggregation agg = parseAggregation(measure.getAggregation());
                measureAggregations.put(measure.getName(), agg);
                measureColumnNames.put(measure.getName(), measure.getColumnName());
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
