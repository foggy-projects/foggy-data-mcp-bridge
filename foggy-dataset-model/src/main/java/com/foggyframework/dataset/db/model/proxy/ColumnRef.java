package com.foggyframework.dataset.db.model.proxy;

import lombok.Getter;

/**
 * 字段引用对象
 *
 * <p>用于在 QM V2 格式中引用 TableModel 的字段，支持以下语法：
 * <ul>
 *   <li>{@code fo.orderId} - 普通字段引用</li>
 *   <li>{@code fo.customer$memberLevel} - 维度属性引用</li>
 * </ul>
 *
 * <p>示例：
 * <pre>{@code
 * const fo = loadTableModel('FactOrderModel');
 * // fo.orderId 返回 ColumnRef 对象
 * columnGroups: [
 *     { ref: fo.orderId }
 * ]
 * }</pre>
 *
 * @author� Foggy Framework
 * @since 2.0
 */
@Getter
public class ColumnRef {

    /**
     * 所属的表模型代理
     */
    private final TableModelProxy tableModelProxy;

    /**
     * 字段名称（如 orderId, customer）
     */
    private final String columnName;

    /**
     * 子属性名称（用于维度属性，如 customer$memberLevel 中的 memberLevel）
     */
    private final String subProperty;

    /**
     * 创建普通字段引用
     *
     * @param tableModelProxy 表模型代理
     * @param columnName      字段名称
     */
    public ColumnRef(TableModelProxy tableModelProxy, String columnName) {
        this(tableModelProxy, columnName, null);
    }

    /**
     * 创建带子属性的字段引用
     *
     * @param tableModelProxy 表模型代理
     * @param columnName      字段名称
     * @param subProperty     子属性名称（可为 null）
     */
    public ColumnRef(TableModelProxy tableModelProxy, String columnName, String subProperty) {
        this.tableModelProxy = tableModelProxy;
        this.columnName = columnName;
        this.subProperty = subProperty;
    }

    /**
     * 获取完整的引用路径
     *
     * <p>格式：
     * <ul>
     *   <li>普通字段：{@code orderId}</li>
     *   <li>维度属性：{@code customer$memberLevel}</li>
     * </ul>
     *
     * @return 完整引用路径
     */
    public String getFullRef() {
        if (subProperty != null && !subProperty.isEmpty()) {
            return columnName + "$" + subProperty;
        }
        return columnName;
    }

    /**
     * 获取所属表模型名称
     *
     * @return 模型名称
     */
    public String getModelName() {
        return tableModelProxy.getModelName();
    }

    /**
     * 获取表别名（用于 SQL 生成）
     *
     * @return 表别名，可能为 null
     */
    public String getTableAlias() {
        return tableModelProxy.getAlias();
    }

    /**
     * 判断是否有子属性
     *
     * @return 如果有子属性返回 true
     */
    public boolean hasSubProperty() {
        return subProperty != null && !subProperty.isEmpty();
    }

    @Override
    public String toString() {
        String alias = getTableAlias();
        String prefix = alias != null ? alias + "." : tableModelProxy.getModelName() + ".";
        return prefix + getFullRef();
    }
}
