package com.foggyframework.dataset;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import javax.sql.DataSource;

@SpringBootTest(classes = FoggyFrameworkDataSetTestApplication.class)
public abstract class DatasetTestSupport {

    @Autowired
    protected ApplicationContext appCtx;

    @Autowired
    protected DataSource dataSource;
}
