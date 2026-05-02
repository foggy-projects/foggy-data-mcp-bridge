package com.foggyframework.dataset.db.model.semantic.domain.pivot;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Pivot 请求 AST 根节点
 *
 * <p>定义多维透视表的完整输入契约。作为 {@link com.foggyframework.dataset.db.model.semantic.domain.SemanticQueryRequest}
 * 的 {@code pivot} 字段使用，与 {@code columns} 模式互斥。</p>
 *
 * <p>引擎检测到 {@code pivot != null} 时，自动切换到 Pivot Pipeline（四阶段内存加工），
 * 而非现有的扁平聚合管线。</p>
 *
 * <p>S11: {@code metrics} 支持混合数组（字符串 + 对象），通过 {@link PivotMetricsDeserializer} 统一反序列化。</p>
 */
@Data
@ApiModel("Pivot透视请求")
public class PivotRequest {

    @ApiModelProperty(value = "行轴字段列表", required = true,
            notes = "定义透视表的行维度层级。数组顺序即为嵌套层级：第一个字段是最外层")
    private List<AxisField> rows;

    @ApiModelProperty(value = "列轴字段列表", required = true,
            notes = "定义透视表的列维度层级")
    private List<AxisField> columns;

    @ApiModelProperty(value = "度量指标列表（S11 混合结构）", required = true,
            notes = "支持三种形态：字符串（原生度量）、{name,expr}（计算指标）、{name,type,of}（派生指标）。" +
                    "示例：[\"salesAmount\", {\"name\":\"share\",\"type\":\"parentShare\",\"of\":\"salesAmount\"}]")
    @JsonProperty("metrics")
    @JsonDeserialize(using = PivotMetricsDeserializer.class)
    private List<PivotMetricItem> metricItems;

    @ApiModelProperty(value = "附属属性列表",
            notes = "不参与 GROUP BY，通过 Phase 2.5 Post-Join 贴合。" +
                    "要求：对应维度的 $id 必须在 rows 或 columns 中，以保证函数依赖唯一性。" +
                    "格式：'维度名$属性名'，例如 'product$categoryName'")
    private List<String> properties;

    @ApiModelProperty(value = "透视行为开关")
    private PivotOptions options;

    @ApiModelProperty(value = "输出形态", allowableValues = "tree, grid, flat",
            notes = "tree: 嵌套父子树（默认）; grid: 分离表头+数据矩阵; flat: 扁平带元数据")
    private String outputFormat = "tree";

    @ApiModelProperty(value = "结果整形布局", notes = "不改变查询聚合语义，仅控制最终结果如何摆放")
    private PivotLayout layout;

    // ===== S11 向后兼容：JSON 字段名仍为 "metrics" =====

    /**
     * 设置 metricItems（来自 JSON 反序列化或编程 API）
     * <p>JSON 字段名 "metrics" 由 {@link PivotMetricsDeserializer} 处理。</p>
     */
    public void setMetricItems(List<PivotMetricItem> metricItems) {
        this.metricItems = metricItems;
    }

    /**
     * 向后兼容的 setter：接受纯字符串列表
     * <p>用于老代码 {@code pivot.setMetrics(List.of("salesAmount"))}</p>
     */
    @JsonIgnore
    public void setMetrics(List<String> metricNames) {
        if (metricNames == null) {
            this.metricItems = null;
            return;
        }
        this.metricItems = metricNames.stream()
                .map(PivotMetricItem::ofNative)
                .collect(Collectors.toList());
    }

    /**
     * 向后兼容的 getter：返回所有需要进入 Phase 1 SQL 的度量名
     * <p>包含原生度量和 expr 计算指标依赖的基础度量（expr 计算指标本身不进 SQL）。
     * 派生指标（parentShare）不在此列。</p>
     * @return 原生度量名列表（仅字符串简写的度量）
     */
    @JsonIgnore
    public List<String> getMetrics() {
        return getNativeMetricNames();
    }

    /**
     * 获取完整的 metric 定义列表
     */
    public List<PivotMetricItem> getMetricItems() {
        return metricItems;
    }

    /**
     * 获取所有原生度量名（进入 Phase 1 SQL 的度量）
     */
    public List<String> getNativeMetricNames() {
        if (metricItems == null) return Collections.emptyList();
        return metricItems.stream()
                .filter(PivotMetricItem::isNative)
                .map(PivotMetricItem::getName)
                .collect(Collectors.toList());
    }

    /**
     * 获取所有需要进入 Phase 1 SQL 的度量名
     * <p>包含原生度量 + 派生指标的 of 引用（如果 of 引用的是原生度量）</p>
     */
    public List<String> getSqlMetricNames() {
        if (metricItems == null) return Collections.emptyList();
        List<String> result = new ArrayList<>();
        for (PivotMetricItem item : metricItems) {
            if (item.isNative()) {
                result.add(item.getName());
            } else if (item.isDerived() && item.getOf() != null) {
                // parentShare 的 of 引用需要确保进入 SQL
                if (!result.contains(item.getOf())) {
                    result.add(item.getOf());
                }
            }
        }
        return result;
    }

    /**
     * 获取所有输出度量名（用于 ResultShaper）
     */
    public List<String> getAllOutputMetricNames() {
        if (metricItems == null) return Collections.emptyList();
        return metricItems.stream()
                .map(PivotMetricItem::getName)
                .collect(Collectors.toList());
    }

    /**
     * 获取 parentShare 类型的派生指标
     */
    public List<PivotMetricItem> getParentShareMetrics() {
        if (metricItems == null) return Collections.emptyList();
        return metricItems.stream()
                .filter(PivotMetricItem::isParentShare)
                .collect(Collectors.toList());
    }

    /**
     * 获取 baselineRatio 类型的派生指标
     */
    public List<PivotMetricItem> getBaselineRatioMetrics() {
        if (metricItems == null) return Collections.emptyList();
        return metricItems.stream()
                .filter(PivotMetricItem::isBaselineRatio)
                .collect(Collectors.toList());
    }

    /**
     * 获取 expr 类型的计算指标
     */
    public List<PivotMetricItem> getExprMetrics() {
        if (metricItems == null) return Collections.emptyList();
        return metricItems.stream()
                .filter(PivotMetricItem::isExpr)
                .collect(Collectors.toList());
    }

    /**
     * 校验所有 metric items 的合法性
     * @throws IllegalArgumentException 如果有非法 metric
     */
    public void validateMetrics() {
        if (metricItems == null || metricItems.isEmpty()) {
            throw new IllegalArgumentException("pivot.metrics 不能为空");
        }
        List<String> names = new ArrayList<>();
        for (PivotMetricItem item : metricItems) {
            item.validate();
            // 名称冲突检测
            if (names.contains(item.getName())) {
                throw new IllegalArgumentException(
                        "pivot.metrics 中存在重复名称：'" + item.getName() + "'");
            }
            names.add(item.getName());
        }
    }

    /**
     * 获取行轴层级数（用于小计膨胀系数计算）
     */
    public int getRowLevelCount() {
        return rows != null ? rows.size() : 0;
    }

    /**
     * 获取列轴层级数
     */
    public int getColumnLevelCount() {
        return columns != null ? columns.size() : 0;
    }

    /**
     * 检查是否存在父子维度字段
     */
    public boolean hasHierarchyField() {
        if (rows != null) {
            for (AxisField field : rows) {
                if ("tree".equals(field.getHierarchyMode())) return true;
            }
        }
        if (columns != null) {
            for (AxisField field : columns) {
                if ("tree".equals(field.getHierarchyMode())) return true;
            }
        }
        return false;
    }
}
