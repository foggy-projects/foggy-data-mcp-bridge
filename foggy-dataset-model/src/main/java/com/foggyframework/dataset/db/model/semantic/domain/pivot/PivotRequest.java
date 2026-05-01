package com.foggyframework.dataset.db.model.semantic.domain.pivot;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.List;

/**
 * Pivot 请求 AST 根节点
 *
 * <p>定义多维透视表的完整输入契约。作为 {@link com.foggyframework.dataset.db.model.semantic.domain.SemanticQueryRequest}
 * 的 {@code pivot} 字段使用，与 {@code columns} 模式互斥。</p>
 *
 * <p>引擎检测到 {@code pivot != null} 时，自动切换到 Pivot Pipeline（四阶段内存加工），
 * 而非现有的扁平聚合管线。</p>
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

    @ApiModelProperty(value = "度量指标列表", required = true,
            notes = "引用 TM 中定义的度量或 calculatedFields 中的虚拟字段",
            example = "[\"salesAmount\", \"orderCount\"]")
    private List<String> metrics;

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
