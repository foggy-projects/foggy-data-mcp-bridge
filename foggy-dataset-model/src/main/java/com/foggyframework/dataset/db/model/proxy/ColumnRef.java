package com.foggyframework.dataset.db.model.proxy;

import lombok.Getter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 字段引用对象
 *
 * <p>用于在 QM V2 格式中引用 TableModel 的字段，支持以下语法：
 * <ul>
 *   <li>{@code fo.orderId} - 普通字段引用</li>
 *   <li>{@code fo.customer$memberLevel} - 维度属性引用</li>
 *   <li>{@code fo.product.category$categoryId} - 嵌套维度属性引用（新语法）</li>
 * </ul>
 *
 * <p>示例：
 * <pre>{@code
 * const fo = loadTableModel('FactOrderModel');
 * // fo.orderId 返回 ColumnRef 对象
 * columnGroups: [
 *     { ref: fo.orderId },
 *     { ref: fo.product.category$categoryId }  // 嵌套维度
 * ]
 * }</pre>
 *
 * @author Foggy Framework
 * @since 2.0
 */
@Getter
public class ColumnRef {

    /**
     * 所属的表模型代理
     */
    private final TableModelProxy tableModelProxy;

    /**
     * 维度路径
     * <p>支持多层嵌套维度：
     * <ul>
     *   <li>普通字段：["orderId"]</li>
     *   <li>一级维度：["customer"]</li>
     *   <li>嵌套维度：["product", "category"]</li>
     * </ul>
     */
    private final List<String> dimensionPath;

    /**
     * 属性名称（用于维度属性，如 customer$memberLevel 中的 memberLevel）
     * <p>如果为 null，表示访问维度的 caption
     */
    private final String property;

    // ==================== 构造方法 ====================

    /**
     * 创建普通字段引用（单层）
     *
     * @param tableModelProxy 表模型代理
     * @param columnName      字段名称
     */
    public ColumnRef(TableModelProxy tableModelProxy, String columnName) {
        this(tableModelProxy, columnName, null);
    }

    /**
     * 创建带属性的字段引用（单层）
     *
     * @param tableModelProxy 表模型代理
     * @param columnName      字段名称
     * @param property        属性名称（可为 null）
     */
    public ColumnRef(TableModelProxy tableModelProxy, String columnName, String property) {
        this.tableModelProxy = tableModelProxy;
        this.dimensionPath = List.of(columnName);
        this.property = property;
    }

    /**
     * 创建嵌套维度引用（多层路径）
     *
     * @param tableModelProxy 表模型代理
     * @param dimensionPath   维度路径
     * @param property        属性名称（可为 null）
     */
    public ColumnRef(TableModelProxy tableModelProxy, List<String> dimensionPath, String property) {
        this.tableModelProxy = tableModelProxy;
        this.dimensionPath = Collections.unmodifiableList(new ArrayList<>(dimensionPath));
        this.property = property;
    }

    // ==================== 路径访问方法 ====================

    /**
     * 获取完整的引用路径（使用 . 分隔）
     *
     * <p>格式：
     * <ul>
     *   <li>普通字段：{@code orderId}</li>
     *   <li>维度属性：{@code customer$memberLevel}</li>
     *   <li>嵌套维度：{@code product.category$categoryId}</li>
     * </ul>
     *
     * @return 完整引用路径（用于 QM ref 语法）
     */
    public String getFullRef() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.join(".", dimensionPath));
        if (property != null && !property.isEmpty()) {
            sb.append("$").append(property);
        }
        return sb.toString();
    }

    /**
     * 获取别名格式的引用路径（使用 _ 分隔）
     *
     * <p>格式：
     * <ul>
     *   <li>普通字段：{@code orderId}</li>
     *   <li>维度属性：{@code customer$memberLevel}</li>
     *   <li>嵌套维度：{@code product_category$categoryId}</li>
     * </ul>
     *
     * @return 别名格式引用路径（用于列名/alias，避免前端 JS 处理带 . 的属性名）
     */
    public String getAliasRef() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.join("_", dimensionPath));
        if (property != null && !property.isEmpty()) {
            sb.append("$").append(property);
        }
        return sb.toString();
    }

    /**
     * 获取第一层字段/维度名称（向后兼容）
     *
     * @return 第一层名称
     */
    public String getColumnName() {
        return dimensionPath.isEmpty() ? null : dimensionPath.get(0);
    }

    /**
     * 获取最后一层维度名称
     *
     * @return 最后一层维度名称
     */
    public String getLeafDimensionName() {
        return dimensionPath.isEmpty() ? null : dimensionPath.get(dimensionPath.size() - 1);
    }

    /**
     * 获取维度路径深度
     *
     * @return 路径深度
     */
    public int getPathDepth() {
        return dimensionPath.size();
    }

    /**
     * 判断是否为嵌套维度引用
     *
     * @return 如果路径深度 > 1 返回 true
     */
    public boolean isNestedDimension() {
        return dimensionPath.size() > 1;
    }

    // ==================== 属性访问方法（向后兼容）====================

    /**
     * 获取子属性名称（向后兼容）
     *
     * @return 属性名称
     */
    public String getSubProperty() {
        return property;
    }

    /**
     * 判断是否有属性
     *
     * @return 如果有属性返回 true
     */
    public boolean hasSubProperty() {
        return property != null && !property.isEmpty();
    }

    // ==================== 其他方法 ====================

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

    @Override
    public String toString() {
        String alias = getTableAlias();
        String prefix = alias != null ? alias + "." : tableModelProxy.getModelName() + ".";
        return prefix + getFullRef();
    }
}
