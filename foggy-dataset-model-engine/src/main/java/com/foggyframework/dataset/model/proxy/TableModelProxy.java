package com.foggyframework.dataset.model.proxy;

import com.foggyframework.fsscript.exp.PropertyFunction;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;
import com.foggyframework.fsscript.parser.spi.PropertyHolder;
import jakarta.persistence.criteria.JoinType;
import lombok.Getter;

import java.util.Objects;

/**
 * 表模型代理对象
 *
 * <p>用于在 QM V2 格式中代理 TableModel，提供：
 * <ul>
 *   <li>动态字段访问：{@code fo.orderId} 返回 {@link DimensionProxy}</li>
 *   <li>维度属性访问：{@code fo.customer$memberLevel} 返回 {@link ColumnRef}</li>
 *   <li>链式维度访问：{@code fo.product.category$categoryId} 返回 {@link ColumnRef}</li>
 *   <li>JOIN 方法：{@code fo.leftJoin(fp)} 返回 {@link JoinBuilder}</li>
 *   <li>聚合 JOIN 方法：{@code fo.leftJoinAggregate(fs)} 返回 {@link AggregateJoinBuilder}</li>
 *   <li>聚合关系方法：{@code fs.filterEq(...).groupBy(...)} 返回 {@link AggregateRelationProxy}</li>
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
    private String alias;

    /**
     * 是否为 QM 作者显式声明的公开别名。
     *
     * <p>Builder 在加载时也会给未命名表分配运行时别名（如 t1/t2），
     * 这类别名不能进入 QM 对外字段名，否则会破坏既有模型 schema。</p>
     */
    private boolean explicitAlias;

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
        this.explicitAlias = alias != null && !alias.isEmpty();
    }

    // ==================== PropertyHolder 实现 ====================

    /**
     * 动态属性访问：支持 fo.orderId、fo.customer$memberLevel、fo.product.category 等语法
     *
     * <p>处理逻辑：
     * <ul>
     *   <li>{@code fo.$alias} → 返回表别名字符串（用于 queryBuilder 中构建 SQL）</li>
     *   <li>{@code fo.customer$memberLevel} → {@code new ColumnRef(this, "customer", "memberLevel")}</li>
     *   <li>{@code fo.orderId} → {@code new DimensionProxy(this, "orderId")}（支持链式访问）</li>
     *   <li>{@code fo.product.category$categoryId} → 链式调用最终返回 ColumnRef</li>
     * </ul>
     *
     * @param name 属性名
     * @return ColumnRef（带$属性访问）或 DimensionProxy（普通访问，支持链式）或字符串（内置属性）
     */
    @Override
    public Object getProperty(String name) {
        // 支持访问内置属性（用于 queryBuilder 中构建 SQL）
        // 使用 $alias 避免与字段名冲突
        if ("$alias".equals(name)) {
            return getEffectiveAlias();
        }

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
     *   <li>{@code leftJoinAggregate(other)} - 右表预聚合后左连接</li>
     * </ul>
     *
     * @param evaluator  表达式求值器
     * @param methodName 方法名
     * @param args       参数
     * @return JoinBuilder 或 NO_MATCH
     */
    @Override
    public Object invoke(ExpEvaluator evaluator, String methodName, Object[] args) {
        if ("as".equals(methodName)) {
            if (args == null || args.length != 1 || !(args[0] instanceof String aliasName)
                    || aliasName.isBlank()) {
                return PropertyHolder.NO_MATCH;
            }
            return new TableModelProxy(this.modelName, aliasName.trim());
        }

        if (isAggregateRelationMethod(methodName)) {
            return AggregateRelationProxy.from(this).invoke(evaluator, methodName, args);
        }

        // 检查参数
        if (args == null || args.length == 0 || !(args[0] instanceof TableModelProxy)) {
            return PropertyHolder.NO_MATCH;
        }

        TableModelProxy other = (TableModelProxy) args[0];

        return switch (methodName) {
            case "leftJoin" -> new JoinBuilder(this, other, JoinType.LEFT);
            case "innerJoin" -> new JoinBuilder(this, other, JoinType.INNER);
            case "rightJoin" -> new JoinBuilder(this, other, JoinType.RIGHT);
            case "leftJoinAggregate" -> new AggregateJoinBuilder(this, other, JoinType.LEFT);
            default -> PropertyHolder.NO_MATCH;
        };
    }

    private boolean isAggregateRelationMethod(String methodName) {
        return switch (methodName) {
            case "groupBy", "by",
                 "filterEq", "whereEq",
                 "filterNeq", "whereNeq",
                 "filterGt", "whereGt",
                 "filterGte", "whereGte",
                 "filterLt", "whereLt",
                 "filterLte", "whereLte",
                 "filterIn", "whereIn" -> true;
            default -> false;
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

    public void setAlias(String alias) {
        this.alias = alias;
    }

    protected void setExplicitAlias(boolean explicitAlias) {
        this.explicitAlias = explicitAlias;
    }

    public boolean hasExplicitAlias() {
        return explicitAlias && hasAlias();
    }

    public String getPublicQualifier() {
        return hasExplicitAlias() ? alias : null;
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
        return modelName.equals(that.modelName)
                && Objects.equals(alias, that.alias);
    }

    @Override
    public int hashCode() {
        return Objects.hash(modelName, alias);
    }
}
