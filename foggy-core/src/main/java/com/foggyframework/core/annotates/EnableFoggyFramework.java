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
}
