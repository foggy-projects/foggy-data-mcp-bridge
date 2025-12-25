package com.foggyframework.dataset.client.annotates;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
public @interface OnDuplicate {
    /**
     * 需要更新的表
     *
     * @return 表名
     */
    String table();

    /**
     * on duplicate 时将根据此列进行比较，比如数据库中此列的值大于当前数据的值，则不更新
     * 常用用值有version，但对于日志型的数据，可以用对应的时间字段
     *
     * @return 版本列名
     */
    String versionColumn() default "";
}
