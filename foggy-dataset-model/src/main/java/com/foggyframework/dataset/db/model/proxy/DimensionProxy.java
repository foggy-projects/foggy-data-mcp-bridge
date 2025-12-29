package com.foggyframework.dataset.db.model.proxy;

import com.foggyframework.fsscript.parser.spi.PropertyHolder;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 维度代理对象
 *
 * <p>用于支持链式维度访问，如 {@code fs.product.category$categoryId}。
 *
 * <p>当访问 {@code fs.product} 时返回 DimensionProxy，
 * 继续访问 {@code .category} 返回新的 DimensionProxy，
 * 访问 {@code .category$categoryId} 或 {@code $categoryId} 时返回 ColumnRef。
 *
 * <p>示例：
 * <pre>{@code
 * const fs = loadTableModel('FactSalesNestedDimModel');
 *
 * // 链式维度访问
 * { ref: fs.product.category$categoryId }
 * // 等价于：DimensionProxy(["product"]).getProperty("category$categoryId")
 * // 返回：ColumnRef with path=["product", "category"], property="categoryId"
 * }</pre>
 *
 * @author Foggy Framework
 * @since 2.0
 */
@Getter
public class DimensionProxy implements PropertyHolder {

    /**
     * 根表模型代理
     */
    private final TableModelProxy rootProxy;

    /**
     * 当前维度路径
     * <p>例如：["product"] 或 ["product", "category"]
     */
    private final List<String> dimensionPath;

    /**
     * 创建维度代理（单层）
     *
     * @param rootProxy     根表模型代理
     * @param dimensionName 维度名称
     */
    public DimensionProxy(TableModelProxy rootProxy, String dimensionName) {
        this.rootProxy = rootProxy;
        this.dimensionPath = List.of(dimensionName);
    }

    /**
     * 创建维度代理（从父路径扩展）
     *
     * @param rootProxy     根表模型代理
     * @param parentPath    父路径
     * @param dimensionName 新维度名称
     */
    public DimensionProxy(TableModelProxy rootProxy, List<String> parentPath, String dimensionName) {
        this.rootProxy = rootProxy;
        List<String> path = new ArrayList<>(parentPath);
        path.add(dimensionName);
        this.dimensionPath = Collections.unmodifiableList(path);
    }

    /**
     * 属性访问：支持链式维度和属性访问
     *
     * <p>处理逻辑：
     * <ul>
     *   <li>{@code proxy.category$categoryId} → ColumnRef with extended path and property</li>
     *   <li>{@code proxy.category} → new DimensionProxy with extended path</li>
     * </ul>
     *
     * @param name 属性名
     * @return DimensionProxy 或 ColumnRef
     */
    @Override
    public Object getProperty(String name) {
        // 处理维度属性语法：category$categoryId
        if (name.contains("$")) {
            String[] parts = name.split("\\$", 2);
            String subDimension = parts[0];
            String property = parts[1];

            // 创建新路径
            List<String> newPath = new ArrayList<>(dimensionPath);
            newPath.add(subDimension);

            return new ColumnRef(rootProxy, newPath, property);
        }

        // 链式维度访问：返回新的 DimensionProxy
        return new DimensionProxy(rootProxy, dimensionPath, name);
    }

    /**
     * 转换为 ColumnRef（访问维度本身，如 caption）
     *
     * @return ColumnRef
     */
    public ColumnRef toColumnRef() {
        return new ColumnRef(rootProxy, dimensionPath, null);
    }

    /**
     * 转换为带属性的 ColumnRef
     *
     * @param property 属性名
     * @return ColumnRef
     */
    public ColumnRef toColumnRef(String property) {
        return new ColumnRef(rootProxy, dimensionPath, property);
    }

    /**
     * 获取完整路径字符串（使用 . 分隔）
     *
     * @return 路径字符串，如 "product.category"（用于 QM ref 语法）
     */
    public String getFullPath() {
        return String.join(".", dimensionPath);
    }

    /**
     * 获取别名格式的路径字符串（使用 _ 分隔）
     *
     * @return 路径字符串，如 "product_category"（用于列名/alias）
     */
    public String getAliasPath() {
        return String.join("_", dimensionPath);
    }

    @Override
    public String toString() {
        String alias = rootProxy.getAlias();
        String prefix = alias != null ? alias + "." : rootProxy.getModelName() + ".";
        return prefix + getFullPath();
    }
}
