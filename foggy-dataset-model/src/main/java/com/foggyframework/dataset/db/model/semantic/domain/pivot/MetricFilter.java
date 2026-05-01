package com.foggyframework.dataset.db.model.semantic.domain.pivot;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * 轴级聚合后过滤条件
 *
 * <p>语义上对应 MDX {@code Filter(Set, [Measures].[X] > value)} 的高频用法。
 * 只允许引用当前 Pivot 聚合结果中的度量和计算字段。</p>
 */
@Data
@ApiModel("度量过滤条件")
public class MetricFilter {

    @ApiModelProperty(value = "引用的度量字段名", required = true,
            notes = "支持 TM 原生度量或 calculatedFields 虚拟字段",
            example = "salesAmount")
    private String metric;

    @ApiModelProperty(value = "比较运算符", required = true,
            allowableValues = ">, >=, <, <=, =, !=",
            example = ">")
    private String op;

    @ApiModelProperty(value = "比较阈值", required = true,
            example = "1000000")
    private Number value;

    /**
     * 对给定的度量值执行谓词判断
     *
     * @param actualValue 实际度量值
     * @return true 表示满足条件（保留），false 表示不满足（淘汰）
     */
    public boolean evaluate(Number actualValue) {
        if (actualValue == null || value == null) {
            return false;
        }
        double actual = actualValue.doubleValue();
        double threshold = value.doubleValue();
        return switch (op) {
            case ">" -> actual > threshold;
            case ">=" -> actual >= threshold;
            case "<" -> actual < threshold;
            case "<=" -> actual <= threshold;
            case "=" -> Double.compare(actual, threshold) == 0;
            case "!=" -> Double.compare(actual, threshold) != 0;
            default -> throw new IllegalArgumentException("Unsupported operator: " + op);
        };
    }
}
