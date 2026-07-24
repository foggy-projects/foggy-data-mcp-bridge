package com.foggyframework.dataset.model.def.preagg;

import lombok.Data;

/**
 * 预聚合过滤条件定义
 * <p>
 * 定义预聚合表的永久过滤条件。
 * 只有满足这些条件的数据才会被聚合到预聚合表中。
 * </p>
 *
 * @author foggy-framework
 * @since 8.2.0
 */
@Data
public class PreAggFilterDef {

    /**
     * 字段名
     */
    private String field;

    /**
     * 操作符：=, !=, &gt;, &lt;, &gt;=, &lt;=, in, notIn, between
     */
    private String op;

    /**
     * 过滤值
     * <p>
     * 对于 in/notIn 操作符，使用数组。
     * 对于 between 操作符，使用包含两个元素的数组 [start, end]。
     * </p>
     */
    private Object value;
}
