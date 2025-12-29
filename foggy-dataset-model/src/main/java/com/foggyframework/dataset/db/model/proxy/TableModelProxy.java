package com.foggyframework.dataset.db.model.proxy;

import com.foggyframework.fsscript.exp.PropertyFunction;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;
import com.foggyframework.fsscript.parser.spi.PropertyHolder;
import jakarta.persistence.criteria.JoinType;
import lombok.Getter;
import lombok.Setter;

/**
 * 表模型代理对象
 *
 * <p>用于在 QM V2 格式中代理 TableModel，提供：
 * <ul>
 *   <li>动态字段访问：{@code fo.orderId} 返回 {@link DimensionProxy}</li>
 *   <li>维度属性访问：{@code fo.customer$memberLevel} 返回 {@link ColumnRef}</li>
 *   <li>链式维度访问：{@code fo.product.category$categoryId} 返回 {@link ColumnRef}</li>
 *   <li>JOIN 方法：{@code fo.leftJoin(fp)} 返回 {@link JoinBuilder}</li>
 * </ul>
 *
 * <p>使用示例：
 * <pre>{@code
 * const fo = loadTableModel('FactOrderModel');
 * const fp = loadTableModel('FactPaymentModel');
 *
 * model: [
 *     fo,
 *     fo.leftJoin(fp).on(fo.orderId, fp.orderId)
 * ],
 * columnGroups: [
 *     { items: [
 *         { ref: fo.orderId },
 *         { ref: fo.product.category$categoryId }  // 嵌套维度
 *     ]}
 * ]
 * }</pre>
 *
 * @author Foggy Framework
 * @since 2.0
 */
@Getter
public class TableModelProxy implements PropertyHolder, PropertyFunction {

    /**
     * 表模型名称
     */
    private final String modelName;

    /**
     * 表别名（用于 SQL 生成）
     */
    @Setter
    private String alias;

    /**
     * 创建表模型代理
     *
     * @param modelName 模型名称
     */
    public TableModelProxy(String modelName) {
        this.modelName = modelName;
    }

    /**
     * 创建带别名的表模型代理
     *
     * @param modelName 模型名称
     * @param alias     表别名
     */
    public TableModelProxy(String modelName, String alias) {
        this.modelName = modelName;
        this.alias = alias;
    }

    // ==================== PropertyHolder 实现 ====================

    /**
     * 动态属性访问：支持 fo.orderId、fo.customer$memberLevel、fo.product.category 等语法
     *
     * <p>处理逻辑：
     * <ul>
     *   <li>{@code fo.customer$memberLevel} → {@code new ColumnRef(this, "customer", "memberLevel")}</li>
     *   <li>{@code fo.orderId} → {@code new DimensionProxy(this, "orderId")}（支持链式访问）</li>
     *   <li>{@code fo.product.category$categoryId} → 链式调用最终返回 ColumnRef</li>
     * </ul>
     *
     * @param name 属性名
     * @return ColumnRef（带$属性访问）或 DimensionProxy（普通访问，支持链式）
     */
    @Override
    public Object getProperty(String name) {
        // 处理维度属性语法：customer$memberLevel
        if (name.contains("$")) {
            String[] parts = name.split("\\$", 2);
            String columnName = parts[0];
            String subProperty = parts[1];
            // 带子属性的引用直接返回 ColumnRef
            return new ColumnRef(this, columnName, subProperty);
        }

        // 返回 DimensionProxy 支持链式访问
        // 例如：fs.product 返回 DimensionProxy，可继续访问 .category
        return new DimensionProxy(this, name);
    }

    // ==================== PropertyFunction 实现 ====================

    /**
     * 方法调用：支持 JOIN 方法
     *
     * <p>支持的方法：
     * <ul>
     *   <li>{@code leftJoin(other)} - 左连接</li>
     *   <li>{@code innerJoin(other)} - 内连接</li>
     *   <li>{@code rightJoin(other)} - 右连接</li>
     * </ul>
     *
     * @param evaluator  表达式求值器
     * @param methodName 方法名
     * @param args       参数
     * @return JoinBuilder 或 NO_MATCH
     */
    @Override
    public Object invoke(ExpEvaluator evaluator, String methodName, Object[] args) {
        // 检查参数
        if (args == null || args.length == 0 || !(args[0] instanceof TableModelProxy)) {
            return PropertyHolder.NO_MATCH;
        }

        TableModelProxy other = (TableModelProxy) args[0];

        return switch (methodName) {
            case "leftJoin" -> new JoinBuilder(this, other, JoinType.LEFT);
            case "innerJoin" -> new JoinBuilder(this, other, JoinType.INNER);
            case "rightJoin" -> new JoinBuilder(this, other, JoinType.RIGHT);
            default -> PropertyHolder.NO_MATCH;
        };
    }

    // ==================== 辅助方法 ====================

    /**
     * 获取有效的别名（优先使用设置的别名，否则返回模型名）
     *
     * @return 别名或模型名
     */
    public String getEffectiveAlias() {
        return alias != null && !alias.isEmpty() ? alias : modelName;
    }

    /**
     * 判断是否已设置别名
     *
     * @return 如果已设置别名返回 true
     */
    public boolean hasAlias() {
        return alias != null && !alias.isEmpty();
    }

    @Override
    public String toString() {
        if (alias != null && !alias.isEmpty()) {
            return modelName + " AS " + alias;
        }
        return modelName;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        TableModelProxy that = (TableModelProxy) obj;
        return modelName.equals(that.modelName);
    }

    @Override
    public int hashCode() {
        return modelName.hashCode();
    }
}
