package com.foggyframework.dataset.model.service;

import com.foggyframework.dataset.client.domain.PagingRequest;
import com.foggyframework.dataset.model.def.query.request.DbQueryRequestDef;
import com.foggyframework.dataset.model.ecommerce.EcommerceTestSupport;
import com.foggyframework.dataset.model.PagingResultImpl;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * QueryFacadeImpl 集成测试
 *
 * <p>通过 EcommerceTestSupport 复用 Spring Context + SQLite 环境，
 * 验证查询门面的完整生命周期。</p>
 */
@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("QueryFacade 集成测试")
class QueryFacadeImplTest extends EcommerceTestSupport {

    @Resource
    private AdvancedQueryFacade queryFacade;

    // ==========================================
    // Bean 注册验证
    // ==========================================

    @Test
    @Order(1)
    @DisplayName("QueryFacade Bean 已注册")
    void testQueryFacadeBeanRegistered() {
        assertNotNull(queryFacade, "QueryFacade should be registered as bean");
    }

    // ==========================================
    // queryModelData 端到端测试
    // ==========================================

    @Test
    @Order(10)
    @DisplayName("queryModelData - 基础明细查询")
    void testQueryModelDataBasic() {
        DbQueryRequestDef param = new DbQueryRequestDef();
        param.setQueryModel("FactSalesQueryModel");
        param.setColumns(Arrays.asList("orderId", "salesAmount"));

        PagingRequest<DbQueryRequestDef> form = new PagingRequest<>();
        form.setParam(param);
        form.setPageSize(10);

        PagingResultImpl result = queryFacade.queryModelData(form);

        assertNotNull(result, "Query result should not be null");
        assertNotNull(result.getItems(), "Rows should not be null");
        assertTrue(result.getItems().size() > 0, "Should return at least 1 row");
        assertTrue(result.getItems().size() <= 10, "Should respect page size");

        log.info("Query returned {} rows", result.getItems().size());
    }

    @Test
    @Order(11)
    @DisplayName("queryModelData - 带 namespace（null = 默认）")
    void testQueryModelDataWithNamespace() {
        DbQueryRequestDef param = new DbQueryRequestDef();
        param.setQueryModel("FactSalesQueryModel");
        param.setColumns(Arrays.asList("orderId", "salesAmount"));

        PagingRequest<DbQueryRequestDef> form = new PagingRequest<>();
        form.setParam(param);
        form.setPageSize(5);

        // null namespace = 默认命名空间
        PagingResultImpl result = queryFacade.queryModelData(form, (String)null);

        assertNotNull(result);
        assertNotNull(result.getItems());

        log.info("Query with null namespace returned {} rows", result.getItems().size());
    }

    @Test
    @Order(12)
    @DisplayName("queryModelData - 空字符串 namespace = 默认")
    void testQueryModelDataWithEmptyNamespace() {
        DbQueryRequestDef param = new DbQueryRequestDef();
        param.setQueryModel("FactSalesQueryModel");
        param.setColumns(Arrays.asList("orderId", "salesAmount"));

        PagingRequest<DbQueryRequestDef> form = new PagingRequest<>();
        form.setParam(param);
        form.setPageSize(5);

        PagingResultImpl result = queryFacade.queryModelData(form, "");

        assertNotNull(result);
        assertNotNull(result.getItems());
    }

    // ==========================================
    // 异常场景
    // ==========================================

    @Test
    @Order(20)
    @DisplayName("queryModelData - 不存在的模型名抛异常")
    void testQueryModelDataNonExistentModel() {
        DbQueryRequestDef param = new DbQueryRequestDef();
        param.setQueryModel("NonExistentModel_XYZ");
        param.setColumns(Arrays.asList("field1"));

        PagingRequest<DbQueryRequestDef> form = new PagingRequest<>();
        form.setParam(param);

        assertThrows(Exception.class, () -> queryFacade.queryModelData(form),
                "Should throw exception for non-existent model");
    }
}
