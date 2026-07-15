package com.foggyframework.dataset.db.model.cache.provider;

import com.foggyframework.dataset.client.domain.PagingRequest;
import com.foggyframework.dataset.db.model.cache.config.QueryCacheProperties;
import com.foggyframework.dataset.db.model.cache.fingerprint.QueryFingerprintBuilder;
import com.foggyframework.dataset.db.model.def.query.request.DbQueryRequestDef;
import com.foggyframework.dataset.db.model.plugins.result_set_filter.ModelResultContext;
import com.foggyframework.dataset.db.model.spi.JdbcQueryModel;
import com.foggyframework.dataset.model.PagingResultImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("Caffeine 真实数据源缓存隔离集成测试")
class CaffeineQueryCacheProviderDataSourceIsolationIT {

    private static final String MODEL_NAME = "SharedModel";
    private static final String SQL = "SELECT sentinel FROM cache_probe WHERE id = ?";
    private static final List<Integer> PARAMS = Collections.singletonList(1);

    @TempDir
    Path tempDir;

    private CaffeineQueryCacheProvider cacheProvider;

    @BeforeEach
    void setUp() {
        QueryCacheProperties properties = new QueryCacheProperties();
        properties.setEnabled(true);
        properties.setDefaultTtl(Duration.ofMinutes(5));
        properties.getCaffeine().setMaximumSize(100);
        properties.getCaffeine().setInitialCapacity(10);
        properties.getCaffeine().setRecordStats(true);
        cacheProvider = new CaffeineQueryCacheProvider(new QueryFingerprintBuilder(), properties);
    }

    @Test
    @DisplayName("未跟踪 DelegatingDataSource 不得依赖物理实例生成缓存身份")
    void shouldFailClosedInsteadOfUsingPhysicalDataSourceInstanceIdentity() throws SQLException {
        DataSource tenantA = createSqliteDataSource("tenant-a.db", "sentinel-a");
        DataSource tenantB = createSqliteDataSource("tenant-b.db", "sentinel-b");
        DelegatingDataSource activeDataSource = new DelegatingDataSource(tenantA);

        JdbcQueryModel queryModel = mock(JdbcQueryModel.class);
        when(queryModel.getName()).thenReturn(MODEL_NAME);
        when(queryModel.getDataSource()).thenReturn(activeDataSource);
        ModelResultContext context = createContext(queryModel);

        assertNull(readCache(context), "tenant A 首次查询必须 miss");
        PagingResultImpl tenantAResult = execute(activeDataSource);
        assertEquals("sentinel-a", sentinel(tenantAResult));
        writeCache(context, tenantAResult);
        assertNull(readCache(context), "未跟踪上下文不得写入 tenant A 结果");

        activeDataSource.setTargetDataSource(tenantB);
        assertNull(readCache(context), "切换到 tenant B 后仍必须 fail closed");
        PagingResultImpl tenantBResult = execute(activeDataSource);
        assertEquals("sentinel-b", sentinel(tenantBResult));
        writeCache(context, tenantBResult);
        assertNull(readCache(context), "未跟踪上下文不得写入 tenant B 结果");
        assertEquals(0L, cacheProvider.getStats().get("l2EstimatedSize"));
    }

    private DataSource createSqliteDataSource(String fileName, String sentinel) throws SQLException {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.sqlite.JDBC");
        dataSource.setUrl("jdbc:sqlite:" + tempDir.resolve(fileName));

        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE cache_probe (id INTEGER PRIMARY KEY, sentinel TEXT NOT NULL)");
        }
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO cache_probe (id, sentinel) VALUES (?, ?)")) {
            statement.setInt(1, 1);
            statement.setString(2, sentinel);
            statement.executeUpdate();
        }
        return dataSource;
    }

    private ModelResultContext createContext(JdbcQueryModel queryModel) {
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel(MODEL_NAME);

        PagingRequest<DbQueryRequestDef> pagingRequest = new PagingRequest<>();
        pagingRequest.setParam(queryRequest);

        ModelResultContext context = new ModelResultContext(pagingRequest, null);
        context.setNamespace("shared-namespace");
        context.pinUntrackedQueryModel(queryModel);
        context.setSecurityContext(ModelResultContext.SecurityContext.builder()
                .authorization("Bearer shared-token")
                .userId("shared-user")
                .tenantId("shared-tenant")
                .roles(Collections.singletonList("reader"))
                .build());
        return context;
    }

    private PagingResultImpl execute(DataSource dataSource) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(SQL)) {
            statement.setInt(1, PARAMS.get(0));
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return PagingResultImpl.of(Collections.emptyList(), 0, 1, null, 0);
                }
                return PagingResultImpl.of(
                        Collections.singletonList(Map.of("sentinel", resultSet.getString("sentinel"))),
                        0,
                        1,
                        null,
                        1);
            }
        }
    }

    private PagingResultImpl readCache(ModelResultContext context) {
        return cacheProvider.checkL2Cache(MODEL_NAME, SQL, PARAMS, context);
    }

    private void writeCache(ModelResultContext context, PagingResultImpl result) {
        cacheProvider.writeL2Cache(MODEL_NAME, SQL, PARAMS, result, context);
    }

    private String sentinel(PagingResultImpl result) {
        assertNotNull(result);
        assertNotNull(result.getItems());
        assertEquals(1, result.getItems().size());
        Object item = result.getItems().get(0);
        return String.valueOf(((Map<?, ?>) item).get("sentinel"));
    }
}
