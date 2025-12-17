package com.foggyframework.core.spring.bean;

import com.foggyframework.core.filter.SimpleFoggyFilterCtx;
import lombok.Getter;
import lombok.Setter;

/**
 * @author fengjianguang
 */
@Getter
@Setter
public class FoggyMethodCtx extends SimpleFoggyFilterCtx<Object[], Object> {
    Object bean;
    String[] parameterNames;

    public FoggyMethodCtx(Object bean, String[] parameterNames, Object[] args) {
        this.bean = bean;
        this.args = args;
        this.parameterNames = parameterNames;
    }

}
