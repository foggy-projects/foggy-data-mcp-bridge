package com.foggyframework.dataset.db.model.cache.eviction;

import java.lang.annotation.*;

/**
 * 标记方法执行后需要清除查询缓存
 * <p>
 * 使用此注解标记的方法，在成功执行后会自动清除相关模型的查询缓存。
 * 适用于数据写入/更新/删除操作。
 * </p>
 *
 * <h3>使用示例</h3>
 * <pre>
 * // 清除单个模型的缓存
 * &#64;EvictQueryCache(models = "FactOrders")
 * public void saveOrder(Order order) { ... }
 *
 * // 清除多个模型的缓存
 * &#64;EvictQueryCache(models = {"FactOrders", "DimCustomer"})
 * public void updateOrderWithCustomer(Order order) { ... }
 *
 * // 清除所有缓存
 * &#64;EvictQueryCache(evictAll = true)
 * public void importData() { ... }
 *
 * // 使用 SpEL 表达式动态获取模型名
 * &#64;EvictQueryCache(modelsExpression = "#order.modelName")
 * public void saveByModel(Order order) { ... }
 * </pre>
 *
 * @author foggy-framework
 * @since 8.2.0
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface EvictQueryCache {

    /**
     * 需要清除缓存的模型名称列表
     * <p>
     * 方法执行成功后，这些模型的查询缓存将被清除。
     * </p>
     */
    String[] models() default {};

    /**
     * 使用 SpEL 表达式动态获取模型名称
     * <p>
     * 支持访问方法参数和返回值：
     * <ul>
     *   <li>#参数名 - 访问方法参数</li>
     *   <li>#result - 访问方法返回值</li>
     *   <li>#root.methodName - 方法名</li>
     * </ul>
     * </p>
     * <p>
     * 表达式应返回 String 或 Collection&lt;String&gt;。
     * </p>
     */
    String modelsExpression() default "";

    /**
     * 是否清除所有缓存
     * <p>
     * 设置为 true 时忽略 models 和 modelsExpression。
     * </p>
     */
    boolean evictAll() default false;

    /**
     * 是否在方法执行前清除缓存
     * <p>
     * 默认在方法成功执行后清除。
     * 设置为 true 可在方法执行前清除（适用于需要立即失效的场景）。
     * </p>
     */
    boolean beforeInvocation() default false;

    /**
     * 是否仅在方法无异常时清除缓存
     * <p>
     * 默认为 true，方法抛出异常时不清除缓存。
     * 设置为 false 则无论是否异常都会清除。
     * </p>
     */
    boolean onlyOnSuccess() default true;
}
