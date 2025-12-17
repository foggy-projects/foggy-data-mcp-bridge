package com.foggyframework.dataset;

import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.junit4.SpringRunner;

import jakarta.annotation.Resource;
import javax.sql.DataSource;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = FoggyFrameworkDataSetTestApplication.class)
public abstract class DatasetTestSupport {

    @Resource
    protected ApplicationContext appCtx;

    @Resource
    protected DataSource dataSource;
}
