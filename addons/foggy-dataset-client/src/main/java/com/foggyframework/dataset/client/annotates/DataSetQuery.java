package com.foggyframework.dataset.client.annotates;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
public @interface DataSetQuery {
    /**
     * 未传的情况下，以方法名来判断数据集名称。
     * 规则如下:
     * 如果以query,find开头，由去掉query,find，剩下的则是数据集名称
     * 如果未指定，则直接使用方法名为数据集名称,注意，第一个字母会被转大写找
     *
     * @return 数据集名称
     */
    String name() default "";

    /**
     * 查询的最大条数
     */
    int maxLimit() default 999;

    boolean returnTotal() default true;
}
