package com.foggyframework.fsscript.client.annotates;

import org.springframework.core.annotation.AliasFor;

import java.lang.annotation.*;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
public @interface FsscriptClient {
//    @AliasFor("name")

    /**
     *
     *
     * @return
     */
    String value() default "";



    boolean primary() default true;

    /**
     * @return The service id with optional protocol prefix. Synonym for {@link #value()
     * value}.
     */
//    @AliasFor("value")
//    String name() default "";


}
