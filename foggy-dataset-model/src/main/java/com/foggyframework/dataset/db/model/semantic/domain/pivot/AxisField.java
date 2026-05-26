package com.foggyframework.dataset.db.model.semantic.domain.pivot;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticQueryRequest;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.List;

/**
 * 轴字段 AST（Axis Field）
 *
 * <p>取代晦涩的 topNPerGroup 全局配置。每个轴字段可携带针对其"隐式父级分组"的排序、截断和过滤指令。</p>
 *
 * <p>覆盖 MDX {@code Generate(parentMembers, TopCount(childMembers, N, metric))} 的高频受控子集：
 * 父子层级都明确出现在同一个轴上，且子级只做 TopN 截断。</p>
 */
@Data
@ApiModel("轴字段定义")
public class AxisField {

    @ApiModelProperty(value = "字段引用表达式", required = true,
            example = "product$subCategory",
            notes = "JSON 键名为 field，与 DSL 示例保持一致")
    private String field;

    @ApiModelProperty(value = "按度量排序规则",
            example = "[\"-salesAmount\", \"orderCount\"]",
            notes = "负号前缀表示降序；支持引用 calculatedFields 中的虚拟字段")
    private List<String> orderBy;

    @ApiModelProperty(value = "截断阈值",
            notes = "在其所有父级分组（即在 rows 数组中排在它前面的所有字段）确定的上下文中，取 Top N。" +
                    "当 hierarchyMode=tree 时，语义变为：每个父节点下取 Top N 子节点（逐层截断）")
    private Integer limit;

    @ApiModelProperty(value = "起始偏移",
            notes = "轴域分页起始位置。仅作用于轴成员选择，不作用于 cell 聚合结果；与 offset 等价。")
    private Integer start;

    @ApiModelProperty(value = "起始偏移别名",
            notes = "轴域分页偏移量。仅作用于轴成员选择，不作用于 cell 聚合结果；与 start 等价。")
    private Integer offset;

    @ApiModelProperty(value = "轴域筛选",
            notes = "仅用于选择当前轴的候选成员集合。cell 聚合查询不透传该条件，只受顶层 slice 控制。")
    @JsonProperty("domainSlice")
    @JsonAlias("slice")
    private List<SemanticQueryRequest.SliceItem> domainSlice;

    @ApiModelProperty(value = "轴级过滤（Having）",
            notes = "在当前分组粒度完成基础聚合后，过滤掉不符合条件的成员。" +
                    "执行顺序：Having → TopN（先过滤再截断）")
    private List<MetricFilter> having;

    @ApiModelProperty(value = "父子维度层级展开模式",
            allowableValues = "tree",
            notes = "null: 普通维度（默认）; tree: 启用父子维度树形展开，" +
                    "引擎通过 TM 元数据获取 parentKey 邻接关系，在内存中动态建树并递归卷起度量")
    private String hierarchyMode;

    @ApiModelProperty(value = "树形展开深度（仅 hierarchyMode=tree 时生效）",
            notes = "-1: 全展开到叶子节点（默认）; 0: 仅根节点; N: 展开到第 N 层。" +
                    "expandDepth 控制的是展示层级，不影响聚合范围——折叠的节点度量仍含后代汇总")
    private Integer expandDepth;

    /**
     * 是否启用了父子维度树形展开
     */
    public boolean isTreeMode() {
        return "tree".equals(hierarchyMode);
    }

    /**
     * 获取有效的展开深度（默认全展开）
     */
    public int getEffectiveExpandDepth() {
        return expandDepth != null ? expandDepth : -1;
    }

    /**
     * 获取轴域分页偏移量；start 和 offset 都未指定时为 0。
     */
    public int getEffectiveOffset() {
        if (start != null) {
            return start;
        }
        return offset != null ? offset : 0;
    }
}
