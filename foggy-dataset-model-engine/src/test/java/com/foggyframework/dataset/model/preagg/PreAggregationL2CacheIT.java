package com.foggyframework.dataset.model.preagg;

import com.foggyframework.dataset.client.domain.PagingRequest;
import com.foggyframework.dataset.model.def.query.request.DbQueryRequestDef;
import com.foggyframework.dataset.model.engine.query.DbQueryResult;
import com.foggyframework.dataset.model.plugins.result_set_filter.ModelResultContext;
import com.foggyframework.dataset.model.service.AdvancedQueryFacade;
import com.foggyframework.dataset.model.spi.QueryCacheProvider;
import com.foggyframework.dataset.model.test.JdbcModelTestApplication;
import com.foggyframework.dataset.model.PagingResultImpl;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(classes = {
        JdbcModelTestApplication.class,
        PreAggregationL2CacheIT.CacheTestConfiguration.class
})
@ActiveProfiles("sqlite")
@DisplayName("PreAgg 与 L2 生产链路集成测试")
class PreAggregationL2CacheIT {

    private static final String MODEL_NAME = "FactSalesPreAggQueryModel";
    private static final String PRE_AGG_NAME = "daily_product_sales";
    private static final String PRE_AGG_TABLE = "preagg_daily_product_sales";

    @Resource
    private AdvancedQueryFacade queryFacade;

    @Resource
    private RecordingQueryCacheProvider cacheProvider;

    @BeforeEach
    void resetCache() {
        cacheProvider.reset();
    }

    @Test
    @DisplayName("L2 必须在 PreAgg 改写后按最终 SQL 与参数读写并可命中")
    void shouldUsePostPreAggregationIdentityForL2LookupAndWrite() {
        ModelResultContext firstContext = context();
        DbQueryResult first = queryFacade.queryModelResult(firstContext);

        assertNotNull(first.getPagingResult());
        assertFalse(first.getPagingResult().getItems().isEmpty());
        assertTrue(firstContext.getCacheConfig().isPreAggHit(), "首轮查询必须实际命中预聚合");
        assertEquals(PRE_AGG_NAME, firstContext.getCacheConfig().getPreAggName(),
                "首轮查询必须命中完整 snapshot fixture");
        assertFalse(firstContext.getCacheConfig().isL2CacheHit(), "首轮查询应为 L2 miss");

        CacheCall firstLookup = cacheProvider.onlyLookupFor(MODEL_NAME);
        CacheCall firstWrite = cacheProvider.onlyWriteFor(MODEL_NAME);
        assertTrue(firstLookup.sql().contains("FROM " + PRE_AGG_TABLE + " "),
                "L2 lookup 必须使用 daily_product_sales 改写后的 SQL");
        assertFalse(firstLookup.sql().contains("FROM fact_sales "),
                "snapshot fixture 不得把 raw SQL 冒充 PreAgg 最终身份");
        assertEquals(firstLookup.key(), firstWrite.key(), "L2 lookup 与 write 必须使用同一最终身份");

        ModelResultContext secondContext = context();
        DbQueryResult second = queryFacade.queryModelResult(secondContext);

        assertTrue(secondContext.getCacheConfig().isPreAggHit(), "缓存命中前仍应完成 PreAgg 路由");
        assertEquals(PRE_AGG_NAME, secondContext.getCacheConfig().getPreAggName(),
                "缓存命中前必须复用相同的 PreAgg 路由");
        assertTrue(secondContext.getCacheConfig().isL2CacheHit(), "第二轮相同查询必须命中 L2");
        assertEquals(first.getPagingResult().getItems(), second.getPagingResult().getItems());
        assertEquals(first.getPagingResult().getTotal(), second.getPagingResult().getTotal());

        List<CacheCall> lookups = cacheProvider.lookupsFor(MODEL_NAME);
        assertEquals(2, lookups.size());
        assertEquals(firstLookup.key(), lookups.get(1).key(), "第二轮 lookup 必须复用相同最终身份");
        assertEquals(1, cacheProvider.writesFor(MODEL_NAME).size(), "L2 hit 后不得重复写入");
    }

    private ModelResultContext context() {
        DbQueryRequestDef query = new DbQueryRequestDef();
        query.setQueryModel(MODEL_NAME);
        query.setColumns(Arrays.asList(
                "salesDate$caption",
                "product$caption",
                "salesAmount"
        ));
        query.setReturnTotal(false);

        PagingRequest<DbQueryRequestDef> request = new PagingRequest<>();
        request.setParam(query);
        request.setStart(0);
        request.setLimit(100);

        ModelResultContext context = new ModelResultContext(request, null);
        context.setCacheConfig(ModelResultContext.QueryCacheConfig.builder()
                .l1Enabled(false)
                .l2Enabled(true)
                .preAggEnabled(true)
                .hybridQueryEnabled(false)
                .build());
        return context;
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class CacheTestConfiguration {

        @Bean
        RecordingQueryCacheProvider recordingQueryCacheProvider() {
            return new RecordingQueryCacheProvider();
        }
    }

    static final class RecordingQueryCacheProvider implements QueryCacheProvider {

        private final Map<CacheKey, PagingResultImpl> cache = new LinkedHashMap<>();
        private final List<CacheCall> lookups = new ArrayList<>();
        private final List<CacheCall> writes = new ArrayList<>();

        @Override
        public PagingResultImpl checkL2Cache(String modelName,
                                             String sql,
                                             List<?> params,
                                             ModelResultContext context) {
            CacheKey key = CacheKey.of(modelName, sql, params);
            lookups.add(new CacheCall(key));
            return cache.get(key);
        }

        @Override
        public void writeL2Cache(String modelName,
                                 String sql,
                                 List<?> params,
                                 PagingResultImpl result,
                                 ModelResultContext context) {
            CacheKey key = CacheKey.of(modelName, sql, params);
            writes.add(new CacheCall(key));
            cache.put(key, result);
        }

        void reset() {
            cache.clear();
            lookups.clear();
            writes.clear();
        }

        CacheCall onlyLookupFor(String modelName) {
            List<CacheCall> calls = lookupsFor(modelName);
            assertEquals(1, calls.size(), "expected one L2 lookup for " + modelName);
            return calls.get(0);
        }

        CacheCall onlyWriteFor(String modelName) {
            List<CacheCall> calls = writesFor(modelName);
            assertEquals(1, calls.size(), "expected one L2 write for " + modelName);
            return calls.get(0);
        }

        List<CacheCall> lookupsFor(String modelName) {
            return lookups.stream()
                    .filter(call -> call.key().modelName().equals(modelName))
                    .toList();
        }

        List<CacheCall> writesFor(String modelName) {
            return writes.stream()
                    .filter(call -> call.key().modelName().equals(modelName))
                    .toList();
        }
    }

    record CacheCall(CacheKey key) {
        String sql() {
            return key.sql();
        }
    }

    record CacheKey(String modelName, String sql, List<Object> params) {
        static CacheKey of(String modelName, String sql, List<?> params) {
            List<Object> snapshot = params == null
                    ? List.of()
                    : java.util.Collections.unmodifiableList(new ArrayList<>(params));
            return new CacheKey(modelName, sql, snapshot);
        }
    }
}
