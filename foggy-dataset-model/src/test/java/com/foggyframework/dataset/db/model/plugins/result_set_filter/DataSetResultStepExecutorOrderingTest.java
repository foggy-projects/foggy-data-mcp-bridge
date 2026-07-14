package com.foggyframework.dataset.db.model.plugins.result_set_filter;

import com.foggyframework.dataset.client.domain.PagingRequest;
import com.foggyframework.dataset.db.model.def.query.request.DbQueryRequestDef;
import com.foggyframework.dataset.db.model.def.query.request.SliceRequestDef;
import com.foggyframework.dataset.db.model.spi.QueryCacheProvider;
import com.foggyframework.dataset.model.PagingResultImpl;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.core.annotation.Order;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DataSetResultStepExecutorOrderingTest {

    @Test
    void springOrderAndExplicitOrderUseTheSameLowerValueFirstSemantics() {
        List<String> trace = new ArrayList<>();
        DataSetResultStep springOrdered = new SpringOrderedStep(trace);
        DataSetResultStep explicitOrder = new ExplicitOrderedStep(trace);

        DataSetResultStepExecutor executor = new DataSetResultStepExecutor(
                List.of(explicitOrder, springOrdered));

        executor.executeBeforeQuery(new ModelResultContext());

        assertEquals(List.of("spring", "explicit"), trace);
        assertEquals(-10, springOrdered.order());
        assertEquals(10, explicitOrder.order());
    }

    @Test
    void duplicateBeforeQueryOrderFailsClosed() {
        DataSetResultStep first = beforeStep(10);
        DataSetResultStep second = beforeStep(10);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> new DataSetResultStepExecutor(List.of(first, second)));

        assertTrue(error.getMessage().contains("Duplicate DataSetResultStep beforeQuery order=10"));
    }

    @Test
    void duplicateProcessOrderFailsClosed() {
        DataSetResultStep first = processStep(10);
        DataSetResultStep second = processStep(10);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> new DataSetResultStepExecutor(List.of(first, second)));

        assertTrue(error.getMessage().contains("Duplicate DataSetResultStep process order=10"));
    }

    @Test
    void springOrderAndPhaseDetectionUseTargetClassForJdkAndCglibProxies() {
        List<String> trace = new ArrayList<>();
        DataSetResultStep jdkProxy = jdkProxy(new JdkProxyOrderedStep(trace));
        DataSetResultStep cglibProxy = cglibProxy(new CglibProxyOrderedStep(trace));

        DataSetResultStepExecutor executor = new DataSetResultStepExecutor(
                List.of(cglibProxy, jdkProxy));
        executor.executeBeforeQuery(new ModelResultContext());

        assertEquals(List.of("jdk-proxy", "cglib-proxy"), trace);
        assertEquals(-20, jdkProxy.order());
        assertEquals(-10, cglibProxy.order());

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> new DataSetResultStepExecutor(List.of(
                        jdkProxy(new JdkProxyOrderedStep(new ArrayList<>())),
                        jdkProxy(new JdkProxyOrderedStep(new ArrayList<>())))));
        assertTrue(error.getMessage().contains("Duplicate DataSetResultStep beforeQuery order=-20"));
    }

    @Test
    void l1LookupRunsAfterSystemSliceAndHitMissApplyPostProcessingExactlyOnce() {
        InMemoryCacheProvider provider = new InMemoryCacheProvider();
        L1CacheStep l1 = new L1CacheStep();
        l1.setQueryCacheProvider(provider);
        DataSetResultStep postProcessor = new IncrementAmountStep();
        DataSetResultStepExecutor executor = new DataSetResultStepExecutor(
                List.of(l1, postProcessor, new SystemSliceMergeStep()));

        ModelResultContext missContext = queryContext();
        executor.executeBeforeQuery(missContext);
        assertTrue(provider.lookupObservedMergedSystemSlice);
        assertEquals(false, missContext.isSkipQuery());

        missContext.setPagingResult(result(1));
        executor.executeProcess(missContext);

        assertEquals(2, amount(missContext.getPagingResult()));
        assertNotNull(provider.cached);
        assertEquals(1, amount(provider.cached), "cache must retain the raw result snapshot");

        ModelResultContext hitContext = queryContext();
        executor.executeBeforeQuery(hitContext);
        assertTrue(hitContext.isSkipQuery());
        executor.executeProcess(hitContext);

        assertEquals(2, amount(hitContext.getPagingResult()));
        assertEquals(1, amount(provider.cached), "processing a hit must not mutate the cached snapshot");
    }

    @Test
    @SuppressWarnings("unchecked")
    void l1SnapshotCopiesMutableJdbcLeavesOnWriteAndRead() {
        InMemoryCacheProvider provider = new InMemoryCacheProvider();
        L1CacheStep l1 = new L1CacheStep();
        l1.setQueryCacheProvider(provider);
        DataSetResultStepExecutor executor = new DataSetResultStepExecutor(List.of(l1));
        ModelResultContext miss = queryContext();

        Timestamp timestamp = new Timestamp(1_000L);
        timestamp.setNanos(123_456_789);
        long initialTimestampMillis = timestamp.getTime();
        byte[] payload = new byte[]{1, 2};
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("timestamp", timestamp);
        row.put("payload", payload);
        miss.setPagingResult(PagingResultImpl.of(
                new ArrayList<>(List.of(row)), 0, 10, null, 1));
        executor.executeProcess(miss);

        timestamp.setTime(9_000L);
        payload[0] = 9;
        Map<String, Object> cachedRow = (Map<String, Object>) provider.cached.getItems().get(0);
        assertEquals(initialTimestampMillis, ((Timestamp) cachedRow.get("timestamp")).getTime());
        assertEquals(123_456_789, ((Timestamp) cachedRow.get("timestamp")).getNanos());
        assertEquals(1, ((byte[]) cachedRow.get("payload"))[0]);

        ModelResultContext hit = queryContext();
        executor.executeBeforeQuery(hit);
        Map<String, Object> hitRow = (Map<String, Object>) hit.getPagingResult().getItems().get(0);
        ((Timestamp) hitRow.get("timestamp")).setTime(7_000L);
        ((byte[]) hitRow.get("payload"))[0] = 7;

        assertEquals(initialTimestampMillis, ((Timestamp) cachedRow.get("timestamp")).getTime());
        assertEquals(123_456_789, ((Timestamp) cachedRow.get("timestamp")).getNanos());
        assertEquals(1, ((byte[]) cachedRow.get("payload"))[0]);
    }

    @Test
    void l1SnapshotFailsClosedForCyclicValuesWithoutBreakingTheQuery() {
        InMemoryCacheProvider provider = new InMemoryCacheProvider();
        L1CacheStep l1 = new L1CacheStep();
        l1.setQueryCacheProvider(provider);
        DataSetResultStepExecutor executor = new DataSetResultStepExecutor(List.of(l1));
        Map<String, Object> cyclicRow = new LinkedHashMap<>();
        cyclicRow.put("self", cyclicRow);
        PagingResultImpl<Map<String, Object>> cyclicResult = PagingResultImpl.of(
                new ArrayList<>(List.of(cyclicRow)), 0, 10, null, 1);

        provider.cached = cyclicResult;
        ModelResultContext read = queryContext();
        executor.executeBeforeQuery(read);
        assertFalse(read.isSkipQuery());
        assertNull(read.getPagingResult());

        provider.cached = null;
        ModelResultContext write = queryContext();
        write.setPagingResult(cyclicResult);
        executor.executeProcess(write);
        assertNull(provider.cached);
        assertEquals(0, provider.writes);
    }

    private static DataSetResultStep beforeStep(int order) {
        return new DataSetResultStep() {
            @Override
            public int beforeQuery(ModelResultContext ctx) {
                return CONTINUE;
            }

            @Override
            public int order() {
                return order;
            }
        };
    }

    private static DataSetResultStep processStep(int order) {
        return new DataSetResultStep() {
            @Override
            public int process(ModelResultContext ctx) {
                return CONTINUE;
            }

            @Override
            public int order() {
                return order;
            }
        };
    }

    private static ModelResultContext queryContext() {
        DbQueryRequestDef query = new DbQueryRequestDef();
        query.setQueryModel("SalesQueryModel");
        PagingRequest<DbQueryRequestDef> request = PagingRequest.buildPagingRequest(query, 10);
        ModelResultContext.SecurityContext security = ModelResultContext.SecurityContext.builder()
                .authorization("Bearer isolated-token")
                .build();
        ModelResultContext context = new ModelResultContext(request, null, security);
        context.setCacheConfig(ModelResultContext.QueryCacheConfig.enableL1());

        SliceRequestDef systemSlice = new SliceRequestDef();
        systemSlice.setField("tenant$id");
        systemSlice.setOp("=");
        systemSlice.setValue("tenant-a");
        context.setSystemSlice(List.of(systemSlice));
        return context;
    }

    private static DataSetResultStep jdkProxy(DataSetResultStep target) {
        ProxyFactory factory = new ProxyFactory();
        factory.setTarget(target);
        factory.setInterfaces(DataSetResultStep.class);
        return (DataSetResultStep) factory.getProxy();
    }

    private static DataSetResultStep cglibProxy(DataSetResultStep target) {
        ProxyFactory factory = new ProxyFactory(target);
        factory.setProxyTargetClass(true);
        return (DataSetResultStep) factory.getProxy();
    }

    private static PagingResultImpl<Map<String, Object>> result(int amount) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("amount", amount);
        return PagingResultImpl.of(new ArrayList<>(List.of(row)), 0, 10, null, 1);
    }

    private static int amount(PagingResultImpl result) {
        Map<?, ?> row = (Map<?, ?>) result.getItems().get(0);
        return ((Number) row.get("amount")).intValue();
    }

    @Order(-10)
    private static final class SpringOrderedStep implements DataSetResultStep {
        private final List<String> trace;

        private SpringOrderedStep(List<String> trace) {
            this.trace = trace;
        }

        @Override
        public int beforeQuery(ModelResultContext ctx) {
            trace.add("spring");
            return CONTINUE;
        }
    }

    private static final class ExplicitOrderedStep implements DataSetResultStep {
        private final List<String> trace;

        private ExplicitOrderedStep(List<String> trace) {
            this.trace = trace;
        }

        @Override
        public int beforeQuery(ModelResultContext ctx) {
            trace.add("explicit");
            return CONTINUE;
        }

        @Override
        public int order() {
            return 10;
        }
    }

    @Order(-20)
    private static final class JdkProxyOrderedStep implements DataSetResultStep {
        private final List<String> trace;

        private JdkProxyOrderedStep(List<String> trace) {
            this.trace = trace;
        }

        @Override
        public int beforeQuery(ModelResultContext ctx) {
            trace.add("jdk-proxy");
            return CONTINUE;
        }
    }

    @Order(-10)
    static class CglibProxyOrderedStep implements DataSetResultStep {
        private final List<String> trace;

        CglibProxyOrderedStep(List<String> trace) {
            this.trace = trace;
        }

        @Override
        public int beforeQuery(ModelResultContext ctx) {
            trace.add("cglib-proxy");
            return CONTINUE;
        }
    }

    private static final class IncrementAmountStep implements DataSetResultStep {
        @Override
        @SuppressWarnings("unchecked")
        public int process(ModelResultContext ctx) {
            Map<String, Object> row = (Map<String, Object>) ctx.getPagingResult().getItems().get(0);
            row.put("amount", ((Number) row.get("amount")).intValue() + 1);
            return CONTINUE;
        }
    }

    private static final class InMemoryCacheProvider implements QueryCacheProvider {
        private PagingResultImpl cached;
        private boolean lookupObservedMergedSystemSlice;
        private int writes;

        @Override
        public PagingResultImpl checkL1Cache(ModelResultContext context, String authorization) {
            List<SliceRequestDef> slices = context.getRequest().getParam().getSlice();
            lookupObservedMergedSystemSlice = slices != null && slices.size() == 1
                    && "tenant-a".equals(slices.get(0).getValue());
            return cached;
        }

        @Override
        public void writeL1Cache(ModelResultContext context, String authorization, PagingResultImpl result) {
            cached = result;
            writes++;
        }
    }
}
