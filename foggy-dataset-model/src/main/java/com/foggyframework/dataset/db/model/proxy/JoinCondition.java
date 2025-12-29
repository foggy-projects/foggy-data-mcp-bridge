package com.foggyframework.dataset.db.model.proxy;

import lombok.Getter;

/**
 * JOIN 条件对象
 *
 * <p>表示 JOIN 语句中的一个条件，如 {@code fo.order_id = fp.order_id}
 *
 * <p>支持的条件类型：
 * <ul>
 *   <li>列与列比较：{@code on(fo.orderId, fp.orderId)}</li>
 *   <li>列与常量比较：{@code eq(fp.status, 'ACTIVE')}</li>
 * </ul>
 *
 * @author Foggy Framework
 * @since 2.0
 */
@Getter
public class JoinCondition {

    /**
     * 左侧字段引用
     */
    private final ColumnRef left;

    /**
     * 操作符（=, <>, <, >, <=, >=）
     */
    private final String operator;

    /**
     * 右侧值（可以是 ColumnRef 或常量值）
     */
    private final Object right;

    /**
     * 创建 JOIN 条件
     *
     * @param left     左侧字段引用
     * @param operator 操作符
     * @param right    右侧值（ColumnRef 或常量）
     */
    public JoinCondition(ColumnRef left, String operator, Object right) {
        this.left = left;
        this.operator = operator;
        this.right = right;
    }

    /**
     * 判断右侧是否为字段引用
     *
     * @return 如果右侧是 ColumnRef 返回 true
     */
    public boolean isRightColumnRef() {
        return right instanceof ColumnRef;
    }

    /**
     * 获取右侧的字段引用（如果是）
     *
     * @return 右侧字段引用，如果不是则返回 null
     */
    public ColumnRef getRightAsColumnRef() {
        return right instanceof ColumnRef ? (ColumnRef) right : null;
    }

    /**
     * 生成 SQL ON 子句片段
     *
     * <p>示例输出：
     * <ul>
     *   <li>{@code fo.order_id = fp.order_id}</li>
     *   <li>{@code fp.status = ?}</li>
     * </ul>
     *
     * @return SQL 片段
     */
    public String toSqlFragment() {
        String leftPart = buildColumnSql(left);
        String rightPart;

        if (right instanceof ColumnRef rightCol) {
            rightPart = buildColumnSql(rightCol);
        } else {
            // 常量值使用占位符
            rightPart = "?";
        }

        return leftPart + " " + operator + " " + rightPart;
    }

    /**
     * 构建字段的 SQL 表达式
     *
     * @param columnRef 字段引用
     * @return SQL 表达式（如 fo.order_id）
     */
    private String buildColumnSql(ColumnRef columnRef) {
        String alias = columnRef.getTableAlias();
        if (alias != null && !alias.isEmpty()) {
            return alias + "." + columnRef.getColumnName();
        }
        // 如果没有别名，使用模型名（后续加载时会分配别名）
        return columnRef.getColumnName();
    }

    /**
     * 获取常量参数值（如果右侧是常量）
     *
     * @return 常量值，如果右侧是 ColumnRef 则返回 null
     */
    public Object getConstantValue() {
        return isRightColumnRef() ? null : right;
    }

    @Override
    public String toString() {
        return toSqlFragment();
    }
}
