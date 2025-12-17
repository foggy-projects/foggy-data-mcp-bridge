package com.foggyframework.semantic.common.annotates;

import com.foggyframework.semantic.common.BundleSemanticUseDefLoaderImpl;
import org.springframework.context.annotation.Import;

import java.lang.annotation.*;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
@Import({BundleSemanticUseDefLoaderImpl.class})
public @interface SemanticUse {
    /**
     *
     * @return
     */
    String []useScopes() default "";

}
