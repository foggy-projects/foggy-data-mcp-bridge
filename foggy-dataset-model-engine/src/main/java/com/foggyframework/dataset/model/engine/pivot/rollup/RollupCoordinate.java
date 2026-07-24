package com.foggyframework.dataset.model.engine.pivot.rollup;

/**
 * 结构化小计坐标
 *
 * <p>替代字符串 {@code "ALL"} / {@code "GRAND_TOTAL"} 来表示汇总维度坐标。
 * 避免与真实业务维度值冲突。</p>
 *
 * @param field      字段名
 * @param value      字段值（真实维度值，或 null 表示被 rollup 掉）
 * @param rolledUp   该字段是否被 rollup（小计）
 * @param grandTotal 该字段是否属于 grand total 坐标
 */
public record RollupCoordinate(
        String field,
        Object value,
        boolean rolledUp,
        boolean grandTotal
) {

    /** 创建保留值的坐标 */
    public static RollupCoordinate of(String field, Object value) {
        return new RollupCoordinate(field, value, false, false);
    }

    /** 创建被 rollup 掉的坐标（小计） */
    public static RollupCoordinate rolledUp(String field) {
        return new RollupCoordinate(field, null, true, false);
    }

    /** 创建 grand total 坐标 */
    public static RollupCoordinate grandTotal(String field) {
        return new RollupCoordinate(field, null, false, true);
    }

    /**
     * 用于 debug 输出的显示值
     */
    public String displayValue() {
        if (grandTotal) return "GRAND_TOTAL";
        if (rolledUp) return "ALL";
        return String.valueOf(value);
    }
}
