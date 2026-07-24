package com.foggyframework.dataset.model.odoo;

import com.foggyframework.dataset.client.domain.PagingRequest;
import com.foggyframework.dataset.model.def.query.request.CondRequestDef;
import com.foggyframework.dataset.model.def.query.request.DbQueryRequestDef;
import com.foggyframework.dataset.model.def.query.request.SliceRequestDef;
import com.foggyframework.dataset.model.ecommerce.EcommerceTestSupport;
import com.foggyframework.dataset.model.service.JdbcService;
import com.foggyframework.dataset.model.PagingResultImpl;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Permission Slice Test — reproduce the 500 error when using dimension $id in slice conditions.
 *
 * <p>This test validates that slice conditions using dimension $id fields (e.g., company$id, salesperson$id)
 * work correctly, including $or logic groups.</p>
 */
@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Permission Slice Test")
class PermissionSliceTest extends EcommerceTestSupport {

    @Resource
    private JdbcService jdbcService;

    @Test
    @Order(1)
    @DisplayName("Basic query without slice should work")
    void testBasicQueryWithoutSlice() {
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("OdooSaleOrderQueryModel");
        queryRequest.setColumns(Arrays.asList("id", "name"));

        PagingRequest<DbQueryRequestDef> form = PagingRequest.buildPagingRequest(queryRequest, 3);
        PagingResultImpl result = jdbcService.queryModelData(form);

        assertNotNull(result, "查询结果不应为空");
        List<Map<String, Object>> items = (List<Map<String, Object>>) result.getItems();
        log.info("Basic query returned {} items", items.size());
        assertTrue(items.size() > 0, "应该有返回结果");
    }

    @Test
    @Order(10)
    @DisplayName("Query with company$id slice should work")
    void testQueryWithCompanyIdSlice() {
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("OdooSaleOrderQueryModel");
        queryRequest.setColumns(Arrays.asList("id", "name", "company$caption"));

        // Slice: company$id IN [1, 2]
        List<SliceRequestDef> slices = new ArrayList<>();
        SliceRequestDef slice = new SliceRequestDef();
        slice.setField("company$id");
        slice.setOp("in");
        slice.setValue(Arrays.asList(1, 2));
        slices.add(slice);
        queryRequest.setSlice(slices);

        PagingRequest<DbQueryRequestDef> form = PagingRequest.buildPagingRequest(queryRequest, 10);
        PagingResultImpl result = jdbcService.queryModelData(form);

        assertNotNull(result, "查询结果不应为空");
        List<Map<String, Object>> items = (List<Map<String, Object>>) result.getItems();
        log.info("Query with company$id slice returned {} items", items.size());
        // Just verify it doesn't throw an exception
    }

    @Test
    @Order(20)
    @DisplayName("Query with salesperson$id slice should work")
    void testQueryWithSalespersonIdSlice() {
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("OdooSaleOrderQueryModel");
        queryRequest.setColumns(Arrays.asList("id", "name", "salesperson$caption"));

        // Slice: salesperson$id = 6
        List<SliceRequestDef> slices = new ArrayList<>();
        SliceRequestDef slice = new SliceRequestDef();
        slice.setField("salesperson$id");
        slice.setOp("=");
        slice.setValue(6);
        slices.add(slice);
        queryRequest.setSlice(slices);

        PagingRequest<DbQueryRequestDef> form = PagingRequest.buildPagingRequest(queryRequest, 10);
        PagingResultImpl result = jdbcService.queryModelData(form);

        assertNotNull(result, "查询结果不应为空");
        List<Map<String, Object>> items = (List<Map<String, Object>>) result.getItems();
        log.info("Query with salesperson$id slice returned {} items", items.size());
    }

    @Test
    @Order(30)
    @DisplayName("Query with salesperson$id IS NULL should work")
    void testQueryWithSalespersonIsNull() {
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("OdooSaleOrderQueryModel");
        queryRequest.setColumns(Arrays.asList("id", "name"));

        // Slice: salesperson$id IS NULL
        List<SliceRequestDef> slices = new ArrayList<>();
        SliceRequestDef slice = new SliceRequestDef();
        slice.setField("salesperson$id");
        slice.setOp("is null");
        slices.add(slice);
        queryRequest.setSlice(slices);

        PagingRequest<DbQueryRequestDef> form = PagingRequest.buildPagingRequest(queryRequest, 10);
        PagingResultImpl result = jdbcService.queryModelData(form);

        assertNotNull(result, "查询结果不应为空");
        List<Map<String, Object>> items = (List<Map<String, Object>>) result.getItems();
        log.info("Query with salesperson$id IS NULL returned {} items", items.size());
    }

    @Test
    @Order(40)
    @DisplayName("Query with $or logic group should work")
    void testQueryWithOrLogicGroup() {
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("OdooSaleOrderQueryModel");
        queryRequest.setColumns(Arrays.asList("id", "name"));

        // Slice: $or: [salesperson$id = 6, salesperson$id IS NULL]
        List<SliceRequestDef> slices = new ArrayList<>();

        SliceRequestDef orGroup = new SliceRequestDef();
        List<CondRequestDef> orConditions = new ArrayList<>();

        SliceRequestDef cond1 = new SliceRequestDef();
        cond1.setField("salesperson$id");
        cond1.setOp("=");
        cond1.setValue(6);
        orConditions.add(cond1);

        SliceRequestDef cond2 = new SliceRequestDef();
        cond2.setField("salesperson$id");
        cond2.setOp("is null");
        orConditions.add(cond2);

        orGroup.setOr(orConditions);
        slices.add(orGroup);
        queryRequest.setSlice(slices);

        PagingRequest<DbQueryRequestDef> form = PagingRequest.buildPagingRequest(queryRequest, 10);
        PagingResultImpl result = jdbcService.queryModelData(form);

        assertNotNull(result, "查询结果不应为空");
        List<Map<String, Object>> items = (List<Map<String, Object>>) result.getItems();
        log.info("Query with $or logic group returned {} items", items.size());
    }

    @Test
    @Order(50)
    @DisplayName("Full permission slice scenario should work")
    void testFullPermissionSliceScenario() {
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("OdooSaleOrderQueryModel");
        queryRequest.setColumns(Arrays.asList("id", "name"));

        // Simulate the full permission slices from permission_bridge.py
        // slice: [
        //   {"field": "company$id", "op": "in", "value": [2, 1]},
        //   {"$or": [
        //     {"field": "salesperson$id", "op": "=", "value": 6},
        //     {"field": "salesperson$id", "op": "is null"}
        //   ]}
        // ]
        List<SliceRequestDef> slices = new ArrayList<>();

        // First slice: company$id IN [2, 1]
        SliceRequestDef companySlice = new SliceRequestDef();
        companySlice.setField("company$id");
        companySlice.setOp("in");
        companySlice.setValue(Arrays.asList(2, 1));
        slices.add(companySlice);

        // Second slice: $or group
        SliceRequestDef orGroup = new SliceRequestDef();
        List<CondRequestDef> orConditions = new ArrayList<>();

        SliceRequestDef cond1 = new SliceRequestDef();
        cond1.setField("salesperson$id");
        cond1.setOp("=");
        cond1.setValue(6);
        orConditions.add(cond1);

        SliceRequestDef cond2 = new SliceRequestDef();
        cond2.setField("salesperson$id");
        cond2.setOp("is null");
        orConditions.add(cond2);

        orGroup.setOr(orConditions);
        slices.add(orGroup);

        queryRequest.setSlice(slices);

        log.info("Testing full permission slice scenario with slices: {}", slices);

        PagingRequest<DbQueryRequestDef> form = PagingRequest.buildPagingRequest(queryRequest, 10);
        PagingResultImpl result = jdbcService.queryModelData(form);

        assertNotNull(result, "查询结果不应为空");
        List<Map<String, Object>> items = (List<Map<String, Object>>) result.getItems();
        log.info("Full permission slice scenario returned {} items", items.size());
    }
}