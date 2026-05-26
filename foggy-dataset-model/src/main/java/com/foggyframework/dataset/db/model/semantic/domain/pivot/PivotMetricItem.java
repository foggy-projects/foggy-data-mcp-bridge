package com.foggyframework.dataset.db.model.semantic.domain.pivot;

import lombok.Data;

/**
 * 统一 metric 定义 AST — 支持三种形态：
 * <ol>
 *   <li>原生度量：字符串简写 {@code "salesAmount"}</li>
 *   <li>计算指标：{@code {"name":"grossProfit","expr":"revenueAmount - costAmount"}}</li>
 *   <li>派生指标：{@code {"name":"share","type":"parentShare","of":"salesAmount"}}</li>
 * </ol>
 *
 * <p>S11 设计约束：
 * <ul>
 *   <li>{@code expr} 第一版不开放（无计算引擎，静默丢失），输入即拒绝</li>
 *   <li>{@code type} 第一版只允许 {@code parentShare}</li>
 *   <li>{@code parentShare} 必须有 {@code of}</li>
 *   <li>{@code axis} 第一版只允许 {@code rows}; {@code columns} 暂缓</li>
 *   <li>不暴露 ROLLUP_TO / CELL_AT / AXIS_MEMBER 字符串函数</li>
 * </ul>
 */
@Data
public class PivotMetricItem {

    /** 指标名称（原生度量时等于字段名，派生指标时为自定义名） */
    private String name;

    /** 算术表达式（与 type 互斥） */
    private String expr;

    /** 派生类型：parentShare（与 expr 互斥） */
    private String type;

    /** 基于哪个度量（type 为 parentShare 时必填） */
    private String of;

    /** 指定轴：rows / columns（parentShare 消歧时使用） */
    private String axis;

    /** 当前级别字段（parentShare 显式消歧时使用） */
    private String level;

    /** 父级字段（parentShare 显式消歧时使用） */
    private String parentLevel;

    /** 基准（baselineRatio 时必填，支持 first/last） */
    private String baseline;

    /** 基准范围（baselineRatio 与 axis domain window 组合时使用） */
    private String baselineScope;

    // ===== Factory Methods =====

    /** 创建原生度量 */
    public static PivotMetricItem ofNative(String name) {
        PivotMetricItem item = new PivotMetricItem();
        item.setName(name);
        return item;
    }

    // ===== Query Methods =====

    /** 是否为原生度量（无 expr 无 type） */
    public boolean isNative() {
        return expr == null && type == null;
    }

    /** 是否为计算指标（有 expr） */
    public boolean isExpr() {
        return expr != null;
    }

    /** 是否为 parentShare 类型 */
    public boolean isParentShare() {
        return "parentShare".equals(type);
    }

    /** 是否为 baselineRatio 类型 */
    public boolean isBaselineRatio() {
        return "baselineRatio".equals(type);
    }

    /** 是否为派生指标（有 type） */
    public boolean isDerived() {
        return type != null;
    }

    // ===== Validation =====

    /**
     * 校验该 metric item 的合法性
     * @throws IllegalArgumentException 如果不合法
     */
    public void validate() {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("pivot.metrics 中的对象元素必须包含 name 字段");
        }

        // expr 与 type 互斥
        if (expr != null && type != null) {
            throw new IllegalArgumentException(
                    "pivot.metrics 中 '" + name + "' 的 expr 与 type 互斥，不能同时指定");
        }

        // 没有 expr 也没有 type 则视为 native（合法）
        if (expr == null && type == null) {
            return;
        }

        // S11 第一版：expr 暂不开放（无计算引擎，静默丢失）
        if (expr != null) {
            throw new IllegalArgumentException(
                    "pivot.metrics 中 '" + name + "' 的 expr 当前版本不支持。" +
                    "请使用原生度量或 calculatedFields 代替");
        }

        // type 第一版只允许 parentShare
        // type 支持 parentShare 和 baselineRatio
        if (type != null) {
            if (!"parentShare".equals(type) && !"baselineRatio".equals(type)) {
                throw new IllegalArgumentException(
                        "pivot.metrics 中 '" + name + "' 的 type='" + type +
                        "' 不受支持。当前仅支持 parentShare 或 baselineRatio");
            }

            if ("parentShare".equals(type)) {
                // parentShare 必须有 of
                if (of == null || of.isBlank()) {
                    throw new IllegalArgumentException(
                            "pivot.metrics 中 parentShare 类型 '" + name + "' 必须指定 of 字段");
                }
                // axis 第一版只允许 rows / null
                if (axis != null && !"rows".equals(axis)) {
                    throw new IllegalArgumentException(
                            "pivot.metrics 中 '" + name + "' 的 axis='" + axis +
                            "' 当前版本不支持。第一版仅支持 axis=rows");
                }
            } else if ("baselineRatio".equals(type)) {
                if (of == null || of.isBlank()) {
                    throw new IllegalArgumentException(
                            "pivot.metrics 中 baselineRatio 类型 '" + name + "' 必须指定 of 字段");
                }
                if (axis == null || !"columns".equals(axis)) {
                    throw new IllegalArgumentException(
                            "pivot.metrics 中 baselineRatio 类型 '" + name + "' 必须指定 axis='columns'");
                }
                if (!"first".equals(baseline) && !"last".equals(baseline)) {
                    throw new IllegalArgumentException(
                            "pivot.metrics 中 baselineRatio 类型 '" + name + "' 必须指定 baseline 为 'first' 或 'last'");
                }
                if (baselineScope != null && !"prePageAxisDomain".equals(baselineScope)) {
                    throw new IllegalArgumentException(
                            "pivot.metrics 中 baselineRatio 类型 '" + name + "' 的 baselineScope='" +
                            baselineScope + "' 当前版本不支持。当前仅支持 prePageAxisDomain");
                }
                if (level != null || parentLevel != null) {
                    throw new IllegalArgumentException(
                            "pivot.metrics 中 baselineRatio 类型 '" + name + "' 不支持 level 或 parentLevel");
                }
            }
        }
    }
}
