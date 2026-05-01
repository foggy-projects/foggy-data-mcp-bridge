package com.foggyframework.dataset.db.model.semantic.domain.pivot;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * Pivot 结果整形布局
 *
 * <p>只描述最终结果如何摆放，不改变 Phase 1 的 GROUP BY、度量聚合和上下文计算语义。</p>
 *
 * <p>用于让 LLM 明确表达"中国式报表里度量作为行头"的展示意图，
 * 避免把度量伪装成普通维度。</p>
 */
@Data
@ApiModel("Pivot结果布局")
public class PivotLayout {

    @ApiModelProperty(value = "度量摆放位置", allowableValues = "columns, rows",
            notes = "columns（默认）: 度量出现在列头叶子层; " +
                    "rows: 结果整形层将 metrics 转为固定指标行。" +
                    "当 metricPlacement=rows 时，行头指标的排序严格由 pivot.metrics 数组的声明顺序决定")
    private String metricPlacement = "columns";

    /**
     * 度量是否放在行头
     */
    public boolean isMetricOnRows() {
        return "rows".equals(metricPlacement);
    }
}
