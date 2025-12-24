package com.foggyframework.fsscript.client.annotates;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
public @interface FsscriptClientMethod {
    /**
     * eg. demo.fsscript
     *
     * @return
     */
    String name() default "";

    FsscriptClientType fsscriptType() default FsscriptClientType.EL_TYPE;

    /**
     * 如果指定了functionName,则使用value脚本中export的对应函数来执行
     *
     * @return
     */
    String functionName() default "";

    /**
     * 当开启后，不会每次调用函数都eval下脚本，而是已经用编译过的fsscript函数来执行
     *
     * @return
     */
    boolean cacheScript() default false;
}
