package com.foggyframework.core.utils.resource;

import com.foggyframework.core.FoggyFrameworkTestApplication;
import com.foggyframework.core.ex.ExRuntimeExceptionImpl;
import com.foggyframework.core.ex.RX;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.jupiter.api.Assertions;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.junit4.SpringRunner;

import jakarta.annotation.Resource;

@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest(classes = FoggyFrameworkTestApplication.class)
public class DefaultResourceFinderTest {
    @Resource
    ApplicationContext appCtx;

    /**
     * 测试开发环境中，直接找target中的文件
     */
    @Test
    public void testFindClasspathFile() {

        checkExistOne("classpath*:/test/templates/", "test2.txt");
        checkExistOne("classpath*:/test/templates", "test2.txt");
        try {
            //测试需要查找的文件，出现多个时的异常！
            checkExistOne("classpath*:/test/templates", "test.txt");
            throw RX.throwB("错误");
        } catch (ExRuntimeExceptionImpl t) {
            Assertions.assertEquals(t.getExCode(), "B1200");
        }
    }

    /**
     * 测试在jar包中查找文件
     */
    @Test
    public void testFindClasspathJarFile() {

        checkExistOne("classpath*:/org/springframework/", "AnnotatedTypeMetadata.class");
        checkExistOne("classpath*:/org/springframework", "AnnotatedTypeMetadata.class");

    }

    /**
     * 测试对../的查找
     */
    @Test
    public void testFindByPathFile() {

        String path = "classpath*:/org/springframework/";
        String name = "AsyncListenableTaskExecutor.class";
        DefaultResourceFinder finder = new DefaultResourceFinder(appCtx, path);
        org.springframework.core.io.Resource resource = finder.findOne(name);


        checkExist(finder, resource, "AsyncTaskExecutor.class");
        checkExist(finder, resource, "../AliasRegistry.class");

        checkExist(finder, resource, "support/ExecutorServiceAdapter.class");

    }

    private void checkExist(DefaultResourceFinder finder, org.springframework.core.io.Resource resource, String path) {
        org.springframework.core.io.Resource res = finder.findByResource(resource, path);//resource.createRelative();
        log.info("checkExist: "+res);
        Assertions.assertTrue(res.exists());

    }

    private void checkExistOne(String path, String name) {
        DefaultResourceFinder finder = new DefaultResourceFinder(appCtx, path);

        org.springframework.core.io.Resource resource = finder.findOne(name);
        RX.notNull(resource);
        Assertions.assertTrue(resource.exists());
    }

}