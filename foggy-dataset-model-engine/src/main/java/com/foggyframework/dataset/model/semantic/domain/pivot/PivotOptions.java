package com.foggyframework.dataset.model.semantic.domain.pivot;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * Pivot 行为控制开关
 *
 * <p>用声明式开关代替复杂的 MDX 操作函数。</p>
 */
@Data
@ApiModel("Pivot行为开关")
public class PivotOptions {

    @ApiModelProperty(value = "稀疏补全：是否在内存中对行、列截断后的成员做笛卡尔积，补全 0/null",
            notes = "互斥约束：当任意轴字段启用了 hierarchyMode=tree 时，crossjoin 必须为 false")
    private boolean crossjoin = false;

    @ApiModelProperty(value = "行轴小计：是否在树形结构中注入父级维度的汇总节点")
    private boolean rowSubtotals = false;

    @ApiModelProperty(value = "列轴小计")
    private boolean columnSubtotals = false;

    @ApiModelProperty(value = "总计行/列")
    private boolean grandTotal = false;

    /**
     * 校验选项合法性
     *
     * @param pivotRequest 所属的 PivotRequest，用于检查是否存在 hierarchyMode=tree 字段
     * @throws IllegalArgumentException 如果校验不通过
     */
    public void validate(PivotRequest pivotRequest) {
        if (crossjoin && pivotRequest.hasHierarchyField()) {
            throw new IllegalArgumentException(
                    "crossjoin 与 hierarchyMode=\"tree\" 互斥：" +
                    "不支持父子维度树展开与笛卡尔积补全同时启用，请关闭 crossjoin 或移除 hierarchyMode");
        }
    }
}
