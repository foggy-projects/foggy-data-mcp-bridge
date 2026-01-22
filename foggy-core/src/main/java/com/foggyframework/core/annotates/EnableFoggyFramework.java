package com.foggyframework.core.annotates;

import org.springframework.context.annotation.Import;

import java.lang.annotation.*;

/**
 * @author fengjianguang
 */
@Retention(value = RetentionPolicy.RUNTIME)
@Target(value = {ElementType.TYPE})
@Documented
@Import({FoggyFrameworkLoader.class})
public @interface EnableFoggyFramework {
    String[] basePackages() default {};
    String bundleName() default "";

    /**
     * 命名空间（用于模型隔离）
     * <p>默认为空字符串表示默认命名空间。
     * <p>示例：namespace="dev" 则模型全名为 "dev:ModelName"
     */
    String namespace() default "";
}
