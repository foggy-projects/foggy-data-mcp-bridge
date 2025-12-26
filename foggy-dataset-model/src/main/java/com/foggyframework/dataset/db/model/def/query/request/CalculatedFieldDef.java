package com.foggyframework.dataset.db.model.def.query.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.foggyframework.fsscript.parser.spi.Exp;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 动态计算字段定义
 * <p>
 * 允许在查询请求中定义计算字段，通过表达式计算得到新的列值。
 * 表达式支持引用模型中的列（包括 formulaDef 列）和其他计算字段。
 * </p>
 *
 * <h3>示例</h3>
 * <pre>
 * {
 *     "name": "salesVsQuota",
 *     "caption": "销售额与配额差异",
 *     "expression": "totaldue - salesquota",
 *     "description": "年度总销售额减去年度销售配额"
 * }
 * </pre>
 *
 * <h3>支持的表达式</h3>
 * <ul>
 *     <li>算术运算: +, -, *, /, %</li>
 *     <li>比较运算: ==, !=, &gt;, &lt;, &gt;=, &lt;=</li>
 *     <li>数学函数: ABS, ROUND, CEIL, FLOOR, MOD, POWER, SQRT</li>
 *     <li>日期函数: YEAR, MONTH, DAY, DATE, NOW, DATE_ADD, DATE_SUB, DATEDIFF</li>
 *     <li>字符串函数: CONCAT, SUBSTRING, UPPER, LOWER, TRIM, LENGTH</li>
 *     <li>其他函数: COALESCE, NULLIF, IFNULL</li>
 * </ul>
 *
 * @author Foggy
 * @since 1.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@ApiModel("计算字段定义")
public class CalculatedFieldDef {

    /**
     * 字段名，在 columns/groupBy 中引用
     */
    @ApiModelProperty(value = "字段名", notes = "在 columns、groupBy 中引用此计算字段的名称", required = true, example = "salesVsQuota")
    private String name;

    /**
     * 显示名称
     */
    @ApiModelProperty(value = "显示名称", notes = "计算字段的中文名称，用于结果展示", example = "销售额与配额差异")
    private String caption;

    /**
     * 计算表达式
     * <p>
     * 表达式中可以引用:
     * <ul>
     *     <li>模型中的普通列名</li>
     *     <li>带 formulaDef 的列名</li>
     *     <li>维度列: dimension$caption, dimension$id</li>
     *     <li>其他计算字段名（需在当前字段之前定义）</li>
     * </ul>
     * </p>
     */
    @ApiModelProperty(value = "计算表达式", notes = "支持算术运算、函数调用、列引用", required = true, example = "totaldue - salesquota")
    private String expression;

    /**
     * 描述
     */
    @ApiModelProperty(value = "描述", notes = "计算字段的详细说明", example = "年度总销售额减去年度销售配额")
    private String description;

    /**
     * 聚合类型
     * <p>
     * 当计算字段表达式包含聚合函数时，可以指定聚合类型。
     * 支持的值: SUM, AVG, COUNT, MAX, MIN
     * </p>
     */
    @ApiModelProperty(value = "聚合类型", notes = "如 SUM, AVG, COUNT, MAX, MIN，用于autoGroupBy场景")
    private String agg;

    /**
     * 编译后的 AST，运行时使用
     */
    @JsonIgnore
    private transient Exp compiledExp;

    /**
     * 便捷构造方法
     */
    public CalculatedFieldDef(String name, String expression) {
        this.name = name;
        this.expression = expression;
    }

    /**
     * 便捷构造方法
     */
    public CalculatedFieldDef(String name, String caption, String expression) {
        this.name = name;
        this.caption = caption;
        this.expression = expression;
    }
}
