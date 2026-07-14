package com.foggyframework.dataset.db.model.config;

import com.foggyframework.dataset.db.model.engine.formula.SqlFormulaService;
import com.foggyframework.dataset.db.model.plugins.query_execution.QueryExecutionStepExecutor;
import com.foggyframework.dataset.db.model.plugins.result_set_filter.DataSetResultFilterManager;
import com.foggyframework.dataset.db.model.spi.QueryModelLoader;
import com.foggyframework.dataset.db.model.spi.TableModelLoaderManager;
import com.foggyframework.dataset.db.model.test.JdbcModelTestApplication;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DbModelAutoConfiguration Bean 注册验证测试
 *
 * <p>验证 Spring 自动配置注册的所有关键 Bean 是否正确创建。</p>
 */
@SpringBootTest(classes = JdbcModelTestApplication.class)
@DisplayName("AutoConfiguration Bean 注册测试")
class DbModelAutoConfigurationTest {

    @Resource
    private ApplicationContext applicationContext;

    // ==========================================
    // 核心 Bean 注册验证
    // ==========================================

    @Test
    @DisplayName("TableModelLoaderManager Bean 已注册")
    void testTableModelLoaderManagerRegistered() {
        assertTrue(applicationContext.containsBean("tableModelLoaderManager"),
                "tableModelLoaderManager bean should be registered");

        TableModelLoaderManager manager = applicationContext.getBean(TableModelLoaderManager.class);
        assertNotNull(manager);
    }

    @Test
    @DisplayName("QueryModelLoader Bean 已注册")
    void testQueryModelLoaderRegistered() {
        QueryModelLoader loader = applicationContext.getBean(QueryModelLoader.class);
        assertNotNull(loader, "QueryModelLoader should be registered");
    }

    @Test
    @DisplayName("SqlFormulaService Bean 已注册")
    void testSqlFormulaServiceRegistered() {
        SqlFormulaService service = applicationContext.getBean(SqlFormulaService.class);
        assertNotNull(service, "SqlFormulaService should be registered");
    }

    @Test
    @DisplayName("DataSetResultFilterManager Bean 已注册")
    void testDataSetResultFilterManagerRegistered() {
        DataSetResultFilterManager manager = applicationContext.getBean(DataSetResultFilterManager.class);
        assertNotNull(manager, "DataSetResultFilterManager should be registered");
    }

    @Test
    @DisplayName("QueryExecutionStepExecutor Bean 已注册")
    void testQueryExecutionStepExecutorRegistered() {
        QueryExecutionStepExecutor executor = applicationContext.getBean(QueryExecutionStepExecutor.class);
        assertNotNull(executor, "QueryExecutionStepExecutor should be registered");
    }

    // ==========================================
    // 配置属性 Bean 注册验证
    // ==========================================

    @Test
    @DisplayName("SemanticProperties Bean 已注册")
    void testSemanticPropertiesRegistered() {
        SemanticProperties props = applicationContext.getBean(SemanticProperties.class);
        assertNotNull(props, "SemanticProperties should be registered");
    }

    @Test
    @DisplayName("DatasetProperties Bean 已注册")
    void testDatasetPropertiesRegistered() {
        DatasetProperties props = applicationContext.getBean(DatasetProperties.class);
        assertNotNull(props, "DatasetProperties should be registered");
        assertNotNull(props.getRequest(), "DatasetProperties.request should be registered");
        assertNotNull(props.getDatasource(), "DatasetProperties.datasource should be registered");
        assertFalse(props.getDatasource().isAllowGlobalFallbackForNamespace(),
                "namespace fallback must be fail-closed by default");
        assertEquals("", props.getRequest().getDefaultNamespace());
    }

    // ==========================================
    // Bean 类型正确性
    // ==========================================

    @Test
    @DisplayName("所有核心 Bean 类型正确")
    void testBeanTypes() {
        assertInstanceOf(TableModelLoaderManager.class,
                applicationContext.getBean(TableModelLoaderManager.class));
        assertInstanceOf(QueryModelLoader.class,
                applicationContext.getBean(QueryModelLoader.class));
        assertInstanceOf(SqlFormulaService.class,
                applicationContext.getBean(SqlFormulaService.class));
    }
}
