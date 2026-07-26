package com.foggyframework.dataset.model.config;

import com.foggyframework.dataset.model.engine.formula.SqlFormulaService;
import com.foggyframework.dataset.model.plugins.query_execution.QueryExecutionStepExecutor;
import com.foggyframework.dataset.model.plugins.result_set_filter.DataSetResultFilterManager;
import com.foggyframework.dataset.model.semantic.permission.AuthorizationSignatureService;
import com.foggyframework.dataset.model.semantic.permission.ModelPermissionService;
import com.foggyframework.dataset.model.semantic.port.ComposeExecutionPort;
import com.foggyframework.dataset.model.spi.QueryModelLoader;
import com.foggyframework.dataset.model.spi.TableModelLoaderManager;
import com.foggyframework.dataset.model.test.JdbcModelTestApplication;
import com.foggyframework.dataset.model.validation.DetachedModelValidationFactory;
import com.foggyframework.dataset.model.api.QueryFacade;
import com.foggyframework.dataset.model.api.backend.BackendCapability;
import com.foggyframework.dataset.model.api.backend.AtomicRefreshBackendProvider;
import com.foggyframework.dataset.model.api.backend.ModelLoadBackendProvider;
import com.foggyframework.dataset.model.api.backend.QueryBackendProvider;
import com.foggyframework.dataset.model.core.backend.BackendProviderCatalog;
import com.foggyframework.dataset.model.jdbc.JdbcQueryBackendProvider;
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
    @DisplayName("DetachedModelValidationFactory Bean 已注册")
    void testDetachedModelValidationFactoryRegistered() {
        DetachedModelValidationFactory factory =
                applicationContext.getBean(DetachedModelValidationFactory.class);
        assertNotNull(factory, "DetachedModelValidationFactory should be registered");
    }

    @Test
    @DisplayName("ComposeExecutionPort Bean 已注册")
    void testComposeExecutionPortRegistered() {
        ComposeExecutionPort port = applicationContext.getBean(ComposeExecutionPort.class);
        assertNotNull(port, "ComposeExecutionPort should be registered");
    }

    @Test
    @DisplayName("权限决策与签名服务 Bean 已注册")
    void testPermissionServicesRegistered() {
        assertNotNull(applicationContext.getBean(ModelPermissionService.class),
                "ModelPermissionService should be registered");
        assertNotNull(applicationContext.getBean(AuthorizationSignatureService.class),
                "AuthorizationSignatureService should be registered");
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

    @Test
    @DisplayName("SPI v2 JDBC provider wraps the governed QueryFacade")
    void testBackendProviderCatalogRegistered() {
        BackendProviderCatalog catalog = applicationContext.getBean(BackendProviderCatalog.class);
        QueryBackendProvider provider = catalog.require(
                JdbcQueryBackendProvider.JDBC,
                BackendCapability.QUERY,
                QueryBackendProvider.class);

        assertSame(applicationContext.getBean(QueryFacade.class), provider.queryFacade());

        ModelLoadBackendProvider loader = catalog.require(
                JdbcQueryBackendProvider.JDBC,
                BackendCapability.MODEL_LOAD,
                ModelLoadBackendProvider.class);
        AtomicRefreshBackendProvider refresh = catalog.require(
                JdbcQueryBackendProvider.JDBC,
                BackendCapability.ATOMIC_REFRESH,
                AtomicRefreshBackendProvider.class);
        assertSame(provider, loader);
        assertSame(provider, refresh);
        assertNotNull(loader.modelLoader());
        assertNotNull(refresh.atomicRefresh());
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
