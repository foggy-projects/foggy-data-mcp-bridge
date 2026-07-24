package com.foggyframework.dataset.model.plugins.query_execution;

import com.foggyframework.dataset.client.domain.PagingRequest;
import com.foggyframework.dataset.db.dialect.FDialect;
import com.foggyframework.dataset.model.def.query.request.DbQueryRequestDef;
import com.foggyframework.dataset.model.engine.JdbcModelQueryEngine;
import com.foggyframework.dataset.model.plugins.result_set_filter.ModelResultContext;
import com.foggyframework.dataset.model.spi.JdbcQueryModel;
import com.foggyframework.dataset.model.spi.QueryCacheProvider;
import com.foggyframework.dataset.model.PagingResultImpl;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QueryExecutionStepOrderingTest {

    @Test
    void beforeRunsDescendingAndAfterRunsAscending() {
        List<String> trace = new ArrayList<>();
        QueryExecutionStep high = recordingStep("high", 200, trace);
        QueryExecutionStep low = recordingStep("low", 100, trace);
        QueryExecutionStepExecutor executor = new QueryExecutionStepExecutor(List.of(low, high));

        executor.executeBeforeExecute(new QueryExecutionContext());
        executor.executeAfterExecute(new QueryExecutionContext());

        assertEquals(List.of("before-high", "before-low", "after-low", "after-high"), trace);
    }

    @Test
    void duplicateOrderFailsClosed() {
        QueryExecutionStep first = recordingStep("first", 100, new ArrayList<>());
        QueryExecutionStep second = recordingStep("second", 100, new ArrayList<>());

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> new QueryExecutionStepExecutor(List.of(first, second)));

        assertTrue(error.getMessage().contains("Duplicate QueryExecutionStep order=100"));
    }

    @Test
    void preAggStyleRewriteRefreshesPagingSqlAndL2ReadsAndWritesTheSameIdentity() {
        QueryCacheProvider provider = mock(QueryCacheProvider.class);
        L2CacheStep l2 = new L2CacheStep();
        l2.setQueryCacheProvider(provider);

        QueryExecutionStep preAggRewrite = new QueryExecutionStep() {
            @Override
            public int order() {
                return 1000;
            }

            @Override
            public int beforeExecute(QueryExecutionContext ctx) {
                ctx.setSql("select * from sales_preagg where tenant_id = ?");
                ctx.setParams(new ArrayList<>(List.of("tenant-a")));
                return CONTINUE;
            }
        };

        QueryExecutionContext context = executionContext(provider);
        QueryExecutionStepExecutor executor = new QueryExecutionStepExecutor(List.of(l2, preAggRewrite));

        executor.executeBeforeExecute(context);

        String expectedPagingSql = "select * from sales_preagg where tenant_id = ? /* execution page 0,10 */";
        assertEquals(expectedPagingSql, context.getPagingSql());
        verify(provider).checkL2Cache("SalesQueryModel", expectedPagingSql,
                List.of("tenant-a"), context.getModelResultContext());

        PagingResultImpl result = PagingResultImpl.of(List.of(), 0, 10, null, 0);
        context.setExecutionResult(result);
        executor.executeAfterExecute(context);

        ArgumentCaptor<PagingResultImpl> cachedResult = ArgumentCaptor.forClass(PagingResultImpl.class);
        verify(provider).writeL2Cache(eq("SalesQueryModel"), eq(expectedPagingSql),
                eq(List.of("tenant-a")), cachedResult.capture(), eq(context.getModelResultContext()));
        assertNotSame(result, cachedResult.getValue());
        assertEquals(result.getItems(), cachedResult.getValue().getItems());
    }

    @Test
    void terminalL2LookupRunsAfterEvenVeryLowOrderSqlAndParameterMutation() {
        QueryCacheProvider provider = mock(QueryCacheProvider.class);
        L2CacheStep l2 = new L2CacheStep();
        l2.setQueryCacheProvider(provider);
        QueryExecutionStep lowOrderRewrite = new QueryExecutionStep() {
            @Override
            public int order() {
                return Integer.MIN_VALUE + 1;
            }

            @Override
            public int beforeExecute(QueryExecutionContext ctx) {
                ctx.setSql("select * from final_source where tenant_id = ?");
                ctx.getParams().add("tenant-final");
                return CONTINUE;
            }
        };

        QueryExecutionContext context = executionContext(provider);
        new QueryExecutionStepExecutor(List.of(l2, lowOrderRewrite)).executeBeforeExecute(context);

        String expected = "select * from final_source where tenant_id = ? /* execution page 0,10 */";
        verify(provider).checkL2Cache(
                "SalesQueryModel", expected, List.of("tenant-final"), context.getModelResultContext());
    }

    @Test
    void l2WriteFailsClosedIfParametersChangeAfterLookup() {
        QueryCacheProvider provider = mock(QueryCacheProvider.class);
        L2CacheStep l2 = new L2CacheStep();
        l2.setQueryCacheProvider(provider);
        QueryExecutionStepExecutor executor = new QueryExecutionStepExecutor(List.of(l2));
        QueryExecutionContext context = executionContext(provider);

        executor.executeBeforeExecute(context);
        context.getParams().add("late-change");
        context.setExecutionResult(PagingResultImpl.of(List.of(), 0, 10, null, 0));
        executor.executeAfterExecute(context);

        verify(provider, never()).writeL2Cache(
                anyString(), anyString(), anyList(), any(), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void l2CacheBoundaryDoesNotShareMutableResultReferences() {
        ReferenceCacheProvider provider = new ReferenceCacheProvider();
        L2CacheStep l2 = new L2CacheStep();
        l2.setQueryCacheProvider(provider);
        QueryExecutionStepExecutor executor = new QueryExecutionStepExecutor(List.of(l2));
        QueryExecutionContext miss = executionContext(provider);

        executor.executeBeforeExecute(miss);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("amount", 1);
        row.put("payload", new byte[]{1, 2});
        PagingResultImpl<Map<String, Object>> result = PagingResultImpl.of(
                new ArrayList<>(List.of(row)), 0, 10, null, 1);
        miss.setExecutionResult(result);
        executor.executeAfterExecute(miss);

        row.put("amount", 2);
        ((byte[]) row.get("payload"))[0] = 9;
        Map<String, Object> cachedRow = (Map<String, Object>) provider.cached.getItems().get(0);
        assertEquals(1, cachedRow.get("amount"));
        assertEquals(1, ((byte[]) cachedRow.get("payload"))[0]);

        QueryExecutionContext hit = executionContext(provider);
        executor.executeBeforeExecute(hit);
        Map<String, Object> hitRow = (Map<String, Object>) hit.getCachedResult().getItems().get(0);
        hitRow.put("amount", 3);

        assertEquals(1, ((Map<?, ?>) provider.cached.getItems().get(0)).get("amount"));
    }

    private static QueryExecutionContext executionContext(QueryCacheProvider provider) {
        DbQueryRequestDef query = new DbQueryRequestDef();
        query.setQueryModel("SalesQueryModel");
        PagingRequest<DbQueryRequestDef> request = PagingRequest.buildPagingRequest(query, 10);
        ModelResultContext modelContext = new ModelResultContext(request, null);
        ModelResultContext.QueryCacheConfig cacheConfig = ModelResultContext.QueryCacheConfig.defaultConfig();
        cacheConfig.setProvider(provider);
        modelContext.setCacheConfig(cacheConfig);

        FDialect modelDialect = mock(FDialect.class);
        when(modelDialect.generatePagingSql(anyString(), eq(0), eq(10)))
                .thenAnswer(invocation -> invocation.<String>getArgument(0) + " /* wrong model dialect */");
        FDialect executionDialect = mock(FDialect.class);
        when(executionDialect.generatePagingSql(anyString(), eq(0), eq(10)))
                .thenAnswer(invocation -> invocation.<String>getArgument(0) + " /* execution page 0,10 */");
        JdbcQueryModel queryModel = mock(JdbcQueryModel.class);
        when(queryModel.getDialect()).thenReturn(modelDialect);
        JdbcModelQueryEngine queryEngine = mock(JdbcModelQueryEngine.class);
        when(queryEngine.getJdbcQueryModel()).thenReturn(queryModel);

        QueryExecutionContext context = new QueryExecutionContext();
        context.setModelName("SalesQueryModel");
        context.setModelResultContext(modelContext);
        context.setQueryEngine(queryEngine);
        context.setExecutionDialect(executionDialect);
        context.setSql("select * from sales");
        context.setParams(new ArrayList<>(List.of()));
        context.setPagingSql("select * from sales /* stale page */");
        return context;
    }

    private static final class ReferenceCacheProvider implements QueryCacheProvider {
        private PagingResultImpl cached;

        @Override
        public PagingResultImpl checkL2Cache(String modelName,
                                             String sql,
                                             List<?> params,
                                             ModelResultContext context) {
            return cached;
        }

        @Override
        public void writeL2Cache(String modelName,
                                 String sql,
                                 List<?> params,
                                 PagingResultImpl result,
                                 ModelResultContext context) {
            cached = result;
        }
    }

    private static QueryExecutionStep recordingStep(String name, int order, List<String> trace) {
        return new QueryExecutionStep() {
            @Override
            public int order() {
                return order;
            }

            @Override
            public int beforeExecute(QueryExecutionContext ctx) {
                trace.add("before-" + name);
                return CONTINUE;
            }

            @Override
            public int afterExecute(QueryExecutionContext ctx) {
                trace.add("after-" + name);
                return CONTINUE;
            }
        };
    }
}
