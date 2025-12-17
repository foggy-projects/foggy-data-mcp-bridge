package com.foggyframework.core.spring.bean;


import com.foggyframework.core.spring.bean.annotation.TestMethod;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;

@Component
//@FoggyFramework
public class FoggyBeanPostProcessorTestBean {
@Resource
    ApplicationContext appCtx;

    @TestMethod("test11")
    public Object test() {
        return null;
    }

    public Object test22() {
        return "test22";
    }
    @TestMethod("testJump")
    public Object testJump() {
        return "testJumpHH";
    }

    public ApplicationContext getAppCtx() {
        return appCtx;
    }

    public String toString() {
        return "toString: FoggyBeanPostProcessorTestBean";
    }
}
