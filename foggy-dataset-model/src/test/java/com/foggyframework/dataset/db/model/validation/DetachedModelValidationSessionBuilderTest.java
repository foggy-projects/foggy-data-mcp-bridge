package com.foggyframework.dataset.db.model.validation;

import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.dataset.db.model.engine.formula.SqlFormulaService;
import com.foggyframework.dataset.db.model.engine.query_model.JdbcQueryModelBuilder;
import com.foggyframework.dataset.db.model.interceptor.SqlLoggingInterceptor;
import com.foggyframework.dataset.db.model.plugins.query_execution.QueryExecutionStepExecutor;
import com.foggyframework.dataset.db.model.spi.QueryModelBuilder;
import com.foggyframework.dataset.db.model.spi.TableModelLoaderManager;
import com.foggyframework.fsscript.loadder.FileFsscriptLoader;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import javax.sql.DataSource;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class DetachedModelValidationSessionBuilderTest {

    @Test
    void mixedRegistryCopiesJdbcBuilderAndIgnoresUnrelatedBuilder() {
        TableModelLoaderManager detachedTableManager = mock(TableModelLoaderManager.class);
        SystemBundlesContext detachedBundlesContext = mock(SystemBundlesContext.class);
        FileFsscriptLoader detachedFileLoader = mock(FileFsscriptLoader.class);
        SqlFormulaService sqlFormulaService = mock(SqlFormulaService.class);
        DataSource defaultDataSource = mock(DataSource.class);
        SqlLoggingInterceptor sqlLoggingInterceptor = mock(SqlLoggingInterceptor.class);
        QueryExecutionStepExecutor queryExecutionStepExecutor =
                mock(QueryExecutionStepExecutor.class);

        JdbcQueryModelBuilder liveJdbc = new JdbcQueryModelBuilder();
        ReflectionTestUtils.setField(
                liveJdbc, "tableModelLoaderManager", mock(TableModelLoaderManager.class));
        ReflectionTestUtils.setField(liveJdbc, "sqlFormulaService", sqlFormulaService);
        ReflectionTestUtils.setField(liveJdbc, "defaultDataSource", defaultDataSource);
        ReflectionTestUtils.setField(
                liveJdbc, "sqlLoggingInterceptor", sqlLoggingInterceptor);
        ReflectionTestUtils.setField(
                liveJdbc, "queryExecutionStepExecutor", queryExecutionStepExecutor);
        QueryModelBuilder unrelated = (definition, fsscript) -> null;

        List<QueryModelBuilder> detached =
                DetachedModelValidationSessionImpl.detachedQueryModelBuilders(
                        List.of(unrelated, liveJdbc),
                        detachedTableManager,
                        detachedBundlesContext,
                        detachedFileLoader
                );

        assertThat(detached).hasSize(1);
        assertThat(detached.get(0)).isInstanceOf(JdbcQueryModelBuilder.class)
                .isNotSameAs(liveJdbc);
        QueryModelBuilder detachedJdbc = detached.get(0);
        assertThat(ReflectionTestUtils.getField(
                detachedJdbc, "tableModelLoaderManager")).isSameAs(detachedTableManager);
        assertThat(ReflectionTestUtils.getField(
                detachedJdbc, "sqlFormulaService")).isSameAs(sqlFormulaService);
        assertThat(ReflectionTestUtils.getField(
                detachedJdbc, "defaultDataSource")).isSameAs(defaultDataSource);
        assertThat(ReflectionTestUtils.getField(
                detachedJdbc, "sqlLoggingInterceptor")).isSameAs(sqlLoggingInterceptor);
        assertThat(ReflectionTestUtils.getField(
                detachedJdbc, "queryExecutionStepExecutor")).isSameAs(
                        queryExecutionStepExecutor);
    }
}
