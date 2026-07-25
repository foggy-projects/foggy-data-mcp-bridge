package com.foggyframework.dataset.model.service.internal;

import com.foggyframework.dataset.client.domain.PagingRequest;
import com.foggyframework.dataset.model.def.query.request.CalculatedFieldDef;
import com.foggyframework.dataset.model.def.query.request.CondRequestDef;
import com.foggyframework.dataset.model.def.query.request.DbQueryRequestDef;
import com.foggyframework.dataset.model.def.query.request.SliceRequestDef;
import com.foggyframework.dataset.model.PagingResultImpl;
import com.foggyframework.dataset.model.api.QueryFacadeRequest;
import com.foggyframework.dataset.model.api.QueryFacadeResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QueryFacadeDtoMapperTest {

    @Test
    void legacyDslRoundTripsAcrossDtoBoundary() {
        DbQueryRequestDef query = new DbQueryRequestDef();
        query.setQueryModel("orders");
        query.setColumns(List.of("orderId", "salesAmount"));
        query.setSlice(List.of(SliceRequestDef.or(List.of(
                new CondRequestDef("status", "=", "PAID"),
                CondRequestDef.expr("salesAmount > costAmount")
        ))));
        query.setCalculatedFields(List.of(new CalculatedFieldDef("margin", "salesAmount - costAmount")));
        query.setReturnTotal(true);
        query.setDistinct(true);
        query.setExtData(Map.of("tenant", "T1"));

        PagingRequest<DbQueryRequestDef> legacy = new PagingRequest<>();
        legacy.setParam(query);
        legacy.setStart(20);
        legacy.setLimit(10);

        QueryFacadeRequest dto = QueryFacadeDtoMapper.toRequest(legacy, "Bearer token", "tenant-a");
        PagingRequest<DbQueryRequestDef> roundTrip = QueryFacadeDtoMapper.toLegacyRequest(dto);

        assertEquals("Bearer token", dto.getAuthorization());
        assertEquals("tenant-a", dto.getNamespace());
        assertTrue(dto.isNamespaceProvided());
        assertEquals(20, roundTrip.getStart());
        assertEquals(10, roundTrip.getLimit());
        assertEquals("orders", roundTrip.getParam().getQueryModel());
        assertEquals(List.of("orderId", "salesAmount"), roundTrip.getParam().getColumns());
        assertEquals("PAID", roundTrip.getParam().getSlice().get(0).getOr().get(0).getValue());
        assertEquals("salesAmount > costAmount", roundTrip.getParam().getSlice().get(0).getOr().get(1).getExpr());
        assertEquals("margin", roundTrip.getParam().getCalculatedFields().get(0).getName());
        assertTrue(roundTrip.getParam().isReturnTotal());
        assertTrue(roundTrip.getParam().isDistinct());
        assertEquals(Map.of("tenant", "T1"), roundTrip.getParam().getExtData());
    }

    @Test
    void scalarSliceRoundTripDoesNotBecomeNullExpression() {
        DbQueryRequestDef query = new DbQueryRequestDef();
        query.setQueryModel("orders");
        query.setColumns(List.of("level"));
        query.setSlice(List.of(new SliceRequestDef("level", "<", 4)));

        PagingRequest<DbQueryRequestDef> legacy = PagingRequest.buildPagingRequest(query);
        QueryFacadeRequest dto = QueryFacadeDtoMapper.toRequest(legacy);

        List<?> slices = (List<?>) dto.getQuery().get("slice");
        Map<?, ?> condition = (Map<?, ?>) slices.get(0);
        assertFalse(condition.containsKey("$expr"));

        PagingRequest<DbQueryRequestDef> roundTrip = QueryFacadeDtoMapper.toLegacyRequest(dto);
        SliceRequestDef slice = roundTrip.getParam().getSlice().get(0);
        assertEquals("level", slice.getField());
        assertEquals("<", slice.getOp());
        assertEquals(4, slice.getValue());
        assertNull(slice.getExpr());
        assertNull(slice.getMaxDepth());
        assertFalse(slice._isExpressionCondition());
    }

    @Test
    void omittedNamespaceRemainsInheritedWhileExplicitNullSelectsDefault() {
        PagingRequest<DbQueryRequestDef> legacy = PagingRequest.buildPagingRequest(query("orders"));

        QueryFacadeRequest inherited = QueryFacadeDtoMapper.toRequest(legacy);
        QueryFacadeRequest explicitDefault = QueryFacadeDtoMapper.toRequest(legacy, null);

        assertFalse(inherited.isNamespaceProvided());
        assertNull(inherited.getNamespace());
        assertTrue(explicitDefault.isNamespaceProvided());
        assertNull(explicitDefault.getNamespace());
    }

    @Test
    void resultRoundTripPreservesPublicPagingContract() {
        PagingResultImpl<Map<String, Object>> legacy = new PagingResultImpl<>();
        legacy.setTotal(42);
        legacy.setHasNext(true);
        legacy.setStart(10);
        legacy.setLimit(5);
        legacy.setItems(List.of(Map.of("id", 1)));
        legacy.setTotalData(Map.of("sum", 99));

        QueryFacadeResult dto = QueryFacadeDtoMapper.toResult(legacy);
        PagingResultImpl<?> roundTrip = QueryFacadeDtoMapper.toLegacyResult(dto);

        assertEquals(42, roundTrip.getTotal());
        assertTrue(roundTrip.isHasNext());
        assertEquals(10, roundTrip.getStart());
        assertEquals(5, roundTrip.getLimit());
        assertEquals(List.of(Map.of("id", 1)), roundTrip.getItems());
        assertEquals(Map.of("sum", 99), roundTrip.getTotalData());
    }

    private DbQueryRequestDef query(String model) {
        DbQueryRequestDef query = new DbQueryRequestDef();
        query.setQueryModel(model);
        return query;
    }
}
