package com.foggyframework.dataset.db.model.proxy;

import com.foggyframework.dataset.db.model.path.DimensionPath;
import lombok.Getter;

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
     * <p>封装路径段和列名，支持多种格式转换
     */
    private final DimensionPath dimensionPath;

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
        this.dimensionPath = DimensionPath.of(List.of(columnName), property);
    }

    /**
     * 创建嵌套维度引用（多层路径）
     *
     * @param tableModelProxy 表模型代理
     * @param pathSegments    维度路径段
     * @param property        属性名称（可为 null）
     */
    public ColumnRef(TableModelProxy tableModelProxy, List<String> pathSegments, String property) {
        this.tableModelProxy = tableModelProxy;
        this.dimensionPath = DimensionPath.of(pathSegments, property);
    }

    /**
     * 创建使用 DimensionPath 的引用
     *
     * @param tableModelProxy 表模型代理
     * @param dimensionPath   维度路径
     */
    public ColumnRef(TableModelProxy tableModelProxy, DimensionPath dimensionPath) {
        this.tableModelProxy = tableModelProxy;
        this.dimensionPath = dimensionPath;
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
        return dimensionPath.toColumnRef();
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
        return dimensionPath.toColumnAlias();
    }

    /**
     * 获取第一层字段/维度名称（向后兼容）
     *
     * @return 第一层名称
     */
    public String getColumnName() {
        return dimensionPath.first();
    }

    /**
     * 获取最后一层维度名称
     *
     * @return 最后一层维度名称
     */
    public String getLeafDimensionName() {
        return dimensionPath.last();
    }

    /**
     * 获取维度路径深度
     *
     * @return 路径深度
     */
    public int getPathDepth() {
        return dimensionPath.depth();
    }

    /**
     * 判断是否为嵌套维度引用
     *
     * @return 如果路径深度 > 1 返回 true
     */
    public boolean isNestedDimension() {
        return dimensionPath.isNested();
    }

    // ==================== 属性访问方法（向后兼容）====================

    /**
     * 获取属性名称
     *
     * @return 属性名称
     */
    public String getProperty() {
        return dimensionPath.getColumnName();
    }

    /**
     * 获取子属性名称（向后兼容）
     *
     * @return 属性名称
     */
    public String getSubProperty() {
        return dimensionPath.getColumnName();
    }

    /**
     * 判断是否有属性
     *
     * @return 如果有属性返回 true
     */
    public boolean hasSubProperty() {
        return dimensionPath.hasColumnName();
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
