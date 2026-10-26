package com.foggyframework.dataset.model.plugins.result_set_filter;

import com.foggyframework.dataset.client.domain.PagingRequest;
import com.foggyframework.dataset.model.PagingResultImpl;
import com.foggyframework.dataset.model.def.query.request.DbQueryRequestDef;
import com.foggyframework.dataset.model.engine.query.DbQueryResult;
import com.foggyframework.dataset.model.semantic.permission.AuthorizationSignature;
import com.foggyframework.dataset.model.spi.QueryCacheProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** An opt-in ordinary L1 cache cannot stand in for deferred plan execution. */
class DeferredPlanL1CacheTest {
    @ParameterizedTest
    @EnumSource(value = ModelResultContext.ExecutionDisposition.class, names = {"DEFERRED", "MATERIALIZED"})
    void warmOrdinaryCacheCannotReplaceADeferredResult(ModelResultContext.ExecutionDisposition disposition) {
        RecordingProvider provider = new RecordingProvider();
        provider.cached = result(300, 3);
        L1CacheStep step = step(provider);
        ModelResultContext context = context();
        context.setExecutionDisposition(disposition);
        context.setSkipQuery(true);
        var fresh = result(290, 2);
        if (disposition == ModelResultContext.ExecutionDisposition.MATERIALIZED) {
            context.setPagingResult(fresh);
            context.setQueryResult(DbQueryResult.of(fresh, null));
        }
        var original = context.getQueryResult();

        step.beforeQuery(context);

        assertEquals(0, provider.reads, "A cached response must not suppress deferred terminal admission");
        assertSame(original, context.getQueryResult());
        assertFalse(context.getCacheConfig().isL1CacheHit());
        assertSame(provider, context.getCacheConfig().getProvider());
    }

    @ParameterizedTest
    @EnumSource(value = ModelResultContext.ExecutionDisposition.class, names = {"DEFERRED", "MATERIALIZED"})
    void timePlanResultsCannotPopulateAnOrdinaryRequestKey(ModelResultContext.ExecutionDisposition disposition) {
        RecordingProvider provider = new RecordingProvider();
        L1CacheStep step = step(provider);
        ModelResultContext context = context();
        context.setExecutionDisposition(disposition);
        context.setPagingResult(result(290, 2));

        step.process(context);

        assertEquals(0, provider.writes, "Display range and count semantics have no ordinary L1 identity");
        assertNull(provider.cached);
    }

    @Test void ordinaryQueriesRetainMissWriteHitAndIsolatedSnapshots() {
        RecordingProvider provider = new RecordingProvider();
        L1CacheStep step = step(provider);
        ModelResultContext miss = context();
        step.beforeQuery(miss);
        assertEquals(1, provider.reads);
        assertFalse(miss.isSkipQuery());
        var fresh = result(290, 2);
        miss.setPagingResult(fresh);
        step.process(miss);
        assertEquals(1, provider.writes);
        assertNotSame(fresh, provider.cached);

        ModelResultContext hit = context();
        step.beforeQuery(hit);
        assertEquals(2, provider.reads);
        assertTrue(hit.isSkipQuery());
        assertTrue(hit.getCacheConfig().isL1CacheHit());
        assertEquals(fresh.getItems(), hit.getPagingResult().getItems());
        assertEquals(fresh.getTotal(), hit.getPagingResult().getTotal());
        assertNotSame(provider.cached, hit.getPagingResult());
        step.process(hit);
        assertEquals(1, provider.writes);
    }

    private static ModelResultContext context() {
        var query = new DbQueryRequestDef();
        query.setQueryModel("TemporalCacheContract");
        query.setColumns(List.of("amount"));
        var context = new ModelResultContext(PagingRequest.buildPagingRequest(query, 10), null);
        context.setCacheConfig(ModelResultContext.QueryCacheConfig.enableL1());
        context.setAuthorizationSignature(new AuthorizationSignature("PUBLIC:local-cache-contract", true, null));
        return context;
    }

    private static PagingResultImpl<Map<String, Object>> result(int amount, int total) {
        return PagingResultImpl.of(new ArrayList<>(List.of(Map.of("amount", amount))), 0, 10, null, total);
    }

    private static L1CacheStep step(RecordingProvider provider) {
        var step = new L1CacheStep();
        step.setQueryCacheProvider(provider);
        return step;
    }

    private static class RecordingProvider implements QueryCacheProvider {
        PagingResultImpl cached;
        int reads, writes;
        @Override public PagingResultImpl checkL1Cache(ModelResultContext context, String signature) {
            reads++;
            return cached;
        }
        @Override public void writeL1Cache(ModelResultContext context, String signature, PagingResultImpl result) {
            writes++;
            cached = result;
        }
    }
}
