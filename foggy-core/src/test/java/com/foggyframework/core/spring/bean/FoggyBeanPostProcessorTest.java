package com.foggyframework.core.spring.bean;

import com.foggyframework.core.FoggyFrameworkTestApplication;
import org.junit.Test;
import org.junit.jupiter.api.Assertions;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import jakarta.annotation.Resource;

@RunWith(SpringRunner.class)
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