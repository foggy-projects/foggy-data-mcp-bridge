package com.foggyframework.dataset.model.semantic.service.impl;

import com.foggyframework.dataset.model.def.dict.DbDictionaryDiscoveryDef;
import com.foggyframework.dataset.model.semantic.domain.DictionaryDiscoveryResult;
import com.foggyframework.dataset.model.semantic.domain.SemanticQueryRequest;
import com.foggyframework.dataset.model.semantic.domain.SemanticQueryResponse;
import com.foggyframework.dataset.model.semantic.domain.SemanticRequestContext;
import com.foggyframework.dataset.model.semantic.service.SemanticQueryServiceV3;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.*;

class DictionaryDiscoveryServiceImplTest {

    @Test
    void groupByDiscoveryBuildsGovernedTopCountRequestAndPropagatesContext() {
        SemanticQueryServiceV3 queryService = mock(SemanticQueryServiceV3.class);
        DictionaryDiscoveryServiceImpl service = service(queryService);
        SemanticRequestContext context = SemanticRequestContext.of("tenant_a", null, Set.of("status"));

        when(queryService.queryModel(eq("OrderQueryModel"), any(SemanticQueryRequest.class),
                eq("execute"), same(context))).thenReturn(response(List.of(
                Map.of("status", "COMPLETED", "__foggyDictionaryCount", 6),
                Map.of("status", "PENDING", "__foggyDictionaryCount", 1),
                Map.of("status", "CANCELLED", "__foggyDictionaryCount", 1)
        )));

        DictionaryDiscoveryResult result = service.discover("OrderQueryModel", "status", groupBy(2, 0), context);

        ArgumentCaptor<SemanticQueryRequest> captor = ArgumentCaptor.forClass(SemanticQueryRequest.class);
        verify(queryService).queryModel(eq("OrderQueryModel"), captor.capture(), eq("execute"), same(context));
        SemanticQueryRequest request = captor.getValue();
        assertEquals(List.of("status", "COUNT(status) AS __foggyDictionaryCount"), request.getColumns());
        assertEquals(1, request.getGroupBy().size());
        assertEquals("status", request.getGroupBy().get(0).getField());
        assertEquals(1, request.getOrderBy().size());
        assertEquals("__foggyDictionaryCount", request.getOrderBy().get(0).getField());
        assertEquals("desc", request.getOrderBy().get(0).getDir());
        assertEquals(3, request.getLimit());
        assertEquals(Boolean.FALSE, request.getReturnTotal());

        assertEquals(DictionaryDiscoveryResult.STATUS_SAMPLED, result.getStatus());
        assertTrue(result.isTruncated());
        assertEquals(2, result.getValues().size());
        assertEquals("COMPLETED", result.getValues().get(0).getValue());
        assertEquals(6L, result.getValues().get(0).getCount());
    }

    @Test
    void distinctDiscoveryUsesDistinctRequestWithoutCountOrOrderBy() {
        SemanticQueryServiceV3 queryService = mock(SemanticQueryServiceV3.class);
        DictionaryDiscoveryServiceImpl service = service(queryService);
        SemanticRequestContext context = SemanticRequestContext.empty();

        when(queryService.queryModel(eq("OrderQueryModel"), any(SemanticQueryRequest.class),
                eq("execute"), same(context))).thenReturn(response(List.of(
                Map.of("status", "COMPLETED"),
                Map.of("status", "PENDING")
        )));

        DictionaryDiscoveryResult result = service.discover(
                "OrderQueryModel", "status", distinct(10, 0), context);

        ArgumentCaptor<SemanticQueryRequest> captor = ArgumentCaptor.forClass(SemanticQueryRequest.class);
        verify(queryService).queryModel(eq("OrderQueryModel"), captor.capture(),
                eq("execute"), same(context));
        SemanticQueryRequest request = captor.getValue();
        assertEquals(List.of("status"), request.getColumns());
        assertEquals(Boolean.TRUE, request.getDistinct());
        assertNull(request.getGroupBy());
        assertNull(request.getOrderBy());
        assertEquals(11, request.getLimit());
        assertEquals(2, result.getValues().size());
        assertNull(result.getValues().get(0).getCount());
    }

    @Test
    void ttlCacheReusesSampledResultForSameGovernedContext() {
        SemanticQueryServiceV3 queryService = mock(SemanticQueryServiceV3.class);
        DictionaryDiscoveryServiceImpl service = service(queryService);
        SemanticRequestContext context = SemanticRequestContext.of("tenant_a", null, Set.of("status"));

        when(queryService.queryModel(eq("OrderQueryModel"), any(SemanticQueryRequest.class),
                eq("execute"), same(context))).thenReturn(response(List.of(
                Map.of("status", "COMPLETED", "__foggyDictionaryCount", 6)
        )));

        DbDictionaryDiscoveryDef discovery = groupBy(2, 60);
        DictionaryDiscoveryResult first = service.discover("OrderQueryModel", "status", discovery, context);
        DictionaryDiscoveryResult second = service.discover("OrderQueryModel", "status", discovery, context);

        assertSame(first, second);
        verify(queryService, times(1)).queryModel(eq("OrderQueryModel"), any(SemanticQueryRequest.class),
                eq("execute"), same(context));
    }

    @Test
    void invisibleDiscoveryDoesNotQueryRuntimeValues() {
        SemanticQueryServiceV3 queryService = mock(SemanticQueryServiceV3.class);
        DictionaryDiscoveryServiceImpl service = service(queryService);
        DbDictionaryDiscoveryDef discovery = groupBy(2, 60);
        discovery.setSensitive(true);

        DictionaryDiscoveryResult result = service.discover(
                "OrderQueryModel", "status", discovery, SemanticRequestContext.empty());

        assertEquals(DictionaryDiscoveryResult.STATUS_SAMPLED, result.getStatus());
        assertTrue(result.getValues().isEmpty());
        verifyNoInteractions(queryService);
    }

    private DictionaryDiscoveryServiceImpl service(SemanticQueryServiceV3 queryService) {
        DictionaryDiscoveryServiceImpl service = new DictionaryDiscoveryServiceImpl();
        ReflectionTestUtils.setField(service, "semanticQueryServiceV3", queryService);
        return service;
    }

    private SemanticQueryResponse response(List<Map<String, Object>> rows) {
        SemanticQueryResponse response = new SemanticQueryResponse();
        response.setItems(rows);
        return response;
    }

    private DbDictionaryDiscoveryDef groupBy(int maxValues, long ttlSeconds) {
        DbDictionaryDiscoveryDef discovery = new DbDictionaryDiscoveryDef();
        discovery.setEnabled(true);
        discovery.setStrategy(DbDictionaryDiscoveryDef.STRATEGY_GROUP_BY);
        discovery.setMaxValues(maxValues);
        discovery.setRefreshTtlSeconds(ttlSeconds);
        return discovery;
    }

    private DbDictionaryDiscoveryDef distinct(int maxValues, long ttlSeconds) {
        DbDictionaryDiscoveryDef discovery = groupBy(maxValues, ttlSeconds);
        discovery.setStrategy(DbDictionaryDiscoveryDef.STRATEGY_DISTINCT);
        return discovery;
    }
}
