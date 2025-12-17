package com.foggyframework.core.spring.bean;

import com.foggyframework.core.filter.FoggyFilterChain;
import com.foggyframework.core.spring.bean.annotation.TestMethod;
import com.foggyframework.core.utils.StringUtils;

import java.lang.reflect.Method;

public class TestFoggyMethodFilter implements FoggyMethodFilterBuilder {

    @Override
    public FoggyMethodFilter build(Method method, String beanName, Class beanClass) {
        TestMethod tt = method.getAnnotation(TestMethod.class);
        if (tt != null) {

            return new FoggyMethodFilter() {

                @Override
                public void doFilter(FoggyMethodCtx ctx, FoggyFilterChain<FoggyMethodCtx> chain) {
                    if(StringUtils.equals("testJump",tt.value())){
                        chain.doFilter(ctx);
                        return ;
                    }
                    ctx.setResult(tt.value());
                }
            };
        }
        return null;
    }
}
