package com.foggyframework.dataset.db.model.def.preagg;

import lombok.Data;

/**
 * 预聚合度量定义
 * <p>
 * 定义预聚合表中包含的度量及其聚合方式。
 * </p>
 *
 * @author foggy-framework
 * @since 8.2.0
 */
@Data
public class PreAggMeasureDef {

    /**
     * 度量名称（引用 TM 模型中的度量）
     */
    private String name;

    /**
     * 聚合方式：SUM, COUNT, MIN, MAX, AVG
     * <p>
     * 注意：AVG 会被转换为 SUM + COUNT 存储
     * </p>
     */
    private String aggregation;

    /**
     * 预聚合表中的列名（默认为 name_aggregation，如 salesAmount_sum）
     */
    private String columnName;

    /**
     * 获取预聚合表中的实际列名
     */
    public String getActualColumnName() {
        if (columnName != null && !columnName.isEmpty()) {
            return columnName;
        }
        // 默认命名：measureName_aggregation
        String agg = aggregation != null ? aggregation.toLowerCase() : "sum";
        return name + "_" + agg;
    }
}
