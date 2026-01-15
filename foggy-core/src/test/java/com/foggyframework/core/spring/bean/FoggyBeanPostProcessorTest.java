package com.foggyframework.core.spring.bean;

import com.foggyframework.core.FoggyFrameworkTestApplication;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import jakarta.annotation.Resource;

@SpringBootTest(classes = FoggyFrameworkTestApplication.class)
public class FoggyBeanPostProcessorTest {
@Resource
    FoggyBeanPostProcessorTestBean bean;

    @Test
    public void test(){
        Assertions.assertEquals(bean.test(),"test11");
        Assertions.assertEquals(bean.test22(),"test22");
        Assertions.assertEquals(bean.testJump(),"testJumpHH");

        Assertions.assertNotNull(bean.getAppCtx());
        Assertions.assertEquals(bean.toString(),"toString: FoggyBeanPostProcessorTestBean");
    }

}
