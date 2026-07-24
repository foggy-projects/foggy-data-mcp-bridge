package com.foggyframework.dataviewer.service;

import com.foggyframework.dataset.client.domain.PagingRequest;
import com.foggyframework.dataset.model.def.query.request.DbQueryRequestDef;
import com.foggyframework.dataset.model.api.QueryFacadeRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StableQueryFacadeRequestMapperTest {

    @Test
    void preservesQueryPaginationAndExplicitNamespaceSemantics() {
        DbQueryRequestDef query = new DbQueryRequestDef();
        query.setQueryModel("FactOrders");
        PagingRequest<DbQueryRequestDef> request = new PagingRequest<>();
        request.setParam(query);
        request.setPage(2);
        request.setPageSize(25);
        request.setStart(25);
        request.setLimit(25);

        QueryFacadeRequest result = StableQueryFacadeRequestMapper.from(
                request, "Bearer token", null);

        assertEquals("FactOrders", result.getQuery().get("queryModel"));
        assertEquals(2, result.getPage());
        assertEquals(25, result.getPageSize());
        assertEquals(25, result.getStart());
        assertEquals(25, result.getLimit());
        assertEquals("Bearer token", result.getAuthorization());
        assertTrue(result.isNamespaceProvided());
        assertNull(result.getNamespace());
        assertThrows(UnsupportedOperationException.class,
                () -> result.getQuery().put("queryModel", "mutated"));
    }
}
