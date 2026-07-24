package com.foggyframework.dataset.model.semantic.member;

import com.foggyframework.dataset.client.domain.PagingRequest;
import com.foggyframework.dataset.model.common.query.DimensionDataQueryForm;
import com.foggyframework.dataset.model.common.result.DbDataItem;
import com.foggyframework.dataset.model.def.query.request.DbQueryRequestDef;
import com.foggyframework.dataset.model.def.query.request.OrderRequestDef;
import com.foggyframework.dataset.model.def.query.request.SliceRequestDef;
import com.foggyframework.dataset.model.engine.compose.SqlGenerationResult;
import com.foggyframework.dataset.model.ecommerce.EcommerceTestSupport;
import com.foggyframework.dataset.model.plugins.result_set_filter.ModelResultContext;
import com.foggyframework.dataset.model.service.JdbcService;
import com.foggyframework.dataset.model.service.AdvancedQueryFacade;
import com.foggyframework.dataset.model.spi.QueryModel;
import com.foggyframework.dataset.model.PagingResultImpl;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Synthetic member-QM 运行时链路测试")
class SyntheticMemberQueryModelRuntimeTest extends EcommerceTestSupport {

    @Resource
    private AdvancedQueryFacade queryFacade;

    @Resource
    private JdbcService jdbcService;

    @Test
    @DisplayName("可直接通过 synthetic model 名生成维度表 SQL")
    void buildSqlFromSyntheticModel() {
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("FactSalesNestedDimQueryModel#product");
        queryRequest.setColumns(List.of("id", "caption", "productCategory$caption"));

        ModelResultContext context = new ModelResultContext(PagingRequest.buildPagingRequest(queryRequest, 20), null);
        SqlGenerationResult sqlResult = queryFacade.buildSqlOnly(context);

        String sql = sqlResult.getSql();
        assertNotNull(sql);
        assertTrue(sql.contains("dim_product_nested"), "SQL 应以产品维表为 root");
        assertTrue(sql.contains("dim_category_nested"), "SQL 应包含嵌套品类维 JOIN");
        assertFalse(sql.contains("fact_sales_nested"), "synthetic member-QM 不应回退到事实表 root");
    }

    @Test
    @DisplayName("simple 入口可通过嵌套字段解析并走 synthetic 主链")
    void queryDimensionDataViaSyntheticModel() {
        DimensionDataQueryForm queryForm = new DimensionDataQueryForm(
                "FactSalesNestedDimQueryModel",
                "categoryGroup$groupType"
        );

        PagingResultImpl<DbDataItem> result = jdbcService.queryDimensionData(PagingRequest.buildPagingRequest(queryForm, 20));
        assertNotNull(result);
        assertNotNull(result.getItems());
        assertFalse(result.getItems().isEmpty(), "simple 入口应可解析到 product synthetic member-QM");

        DbDataItem first = result.getItems().get(0);
        assertNotNull(first.getId(), "simple 入口应返回 canonical id");
        assertNotNull(first.getCaption(), "simple 入口应返回 canonical caption");
    }

    @Test
    @DisplayName("synthetic member-QM 不暴露原业务字段")
    void rejectBusinessColumnsOutsideMemberTree() {
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("FactSalesNestedDimQueryModel#product");
        queryRequest.setColumns(List.of("salesAmount"));

        ModelResultContext context = new ModelResultContext(PagingRequest.buildPagingRequest(queryRequest, 20), null);

        RuntimeException ex = assertThrows(RuntimeException.class, () -> queryFacade.buildSqlOnly(context));
        assertTrue(ex.getMessage().contains("salesAmount"));
    }

    @Test
    @DisplayName("loader 返回的 synthetic member-QM 仅包含单维子树字段")
    void syntheticModelColumnsStayWithinDimensionTree() {
        QueryModel queryModel = queryModelLoader.getJdbcQueryModel("FactSalesNestedDimQueryModel#product", null);
        assertNotNull(queryModel);

        Map<String, ?> columns = queryModel.getJdbcQueryColumns().stream()
                .collect(java.util.stream.Collectors.toMap(c -> c.getName(), c -> c, (a, b) -> a));

        assertTrue(columns.containsKey("id"), "actual columns: " + columns.keySet());
        assertTrue(columns.containsKey("caption"), "actual columns: " + columns.keySet());
        assertTrue(columns.containsKey("productCategory$categoryGroup$groupType"), "actual columns: " + columns.keySet());
        assertFalse(columns.containsKey("salesAmount"));
        assertFalse(columns.containsKey("store$caption"));
    }

    @Test
    @DisplayName("synthetic member-QM 可查询根维度 canonical 字段与一级二级路径字段")
    void querySyntheticMemberWithRootAndNestedPaths() {
        String expectedSql = """
                SELECT p.product_key AS id,
                       p.product_name AS caption,
                       p.brand AS brand,
                       c.category_key AS category_id,
                       c.category_name AS category_caption,
                       g.group_key AS group_id,
                       g.group_name AS group_caption,
                       g.group_type AS group_type
                  FROM dim_product_nested p
                  LEFT JOIN dim_category_nested c ON p.category_key = c.category_key
                  LEFT JOIN dim_category_group g ON c.group_key = g.group_key
                 ORDER BY p.product_key ASC
                """;
        List<Map<String, Object>> expectedRows = executeQuery(expectedSql);

        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("FactSalesNestedDimQueryModel#product");
        queryRequest.setColumns(List.of(
                "id",
                "caption",
                "brand",
                "productCategory$id",
                "productCategory$caption",
                "productCategory$categoryGroup$id",
                "productCategory$categoryGroup$caption",
                "productCategory$categoryGroup$groupType"
        ));
        queryRequest.setOrderBy(List.of(order("id", "ASC")));

        PagingResultImpl result = queryFacade.queryModelData(PagingRequest.buildPagingRequest(queryRequest, 20));
        List<Map<String, Object>> items = castItems(result);

        assertEquals(expectedRows.size(), items.size(), "根维度及路径字段查询结果数应一致");
        for (int i = 0; i < items.size(); i++) {
            Map<String, Object> actual = items.get(i);
            Map<String, Object> expected = expectedRows.get(i);
            assertEquals(String.valueOf(expected.get("id")), String.valueOf(actual.get("id")));
            assertEquals(expected.get("caption"), actual.get("caption"));
            assertEquals(expected.get("brand"), actual.get("brand"));
            assertEquals(String.valueOf(expected.get("category_id")), String.valueOf(actual.get("productCategory$id")));
            assertEquals(expected.get("category_caption"), actual.get("productCategory$caption"));
            assertEquals(String.valueOf(expected.get("group_id")), String.valueOf(actual.get("productCategory$categoryGroup$id")));
            assertEquals(expected.get("group_caption"), actual.get("productCategory$categoryGroup$caption"));
            assertEquals(expected.get("group_type"), actual.get("productCategory$categoryGroup$groupType"));
        }
    }

    @Test
    @DisplayName("synthetic member-QM 支持嵌套子维度字段过滤与排序")
    void querySyntheticMemberWithNestedDimensionFilterAndOrder() {
        String expectedSql = """
                SELECT p.product_key AS id,
                       p.product_name AS caption,
                       p.brand AS brand,
                       c.category_name AS category_caption,
                       g.group_type AS group_type
                  FROM dim_product_nested p
                  LEFT JOIN dim_category_nested c ON p.category_key = c.category_key
                  LEFT JOIN dim_category_group g ON c.group_key = g.group_key
                 WHERE g.group_type = '高价值'
                 ORDER BY c.category_name ASC, p.product_name ASC
                """;
        List<Map<String, Object>> expectedRows = executeQuery(expectedSql);

        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("FactSalesNestedDimQueryModel#product");
        queryRequest.setColumns(List.of(
                "id",
                "caption",
                "brand",
                "productCategory$caption",
                "productCategory$categoryGroup$groupType"
        ));
        queryRequest.setSlice(List.of(slice("productCategory$categoryGroup$groupType", "=", "高价值")));
        queryRequest.setOrderBy(List.of(
                order("productCategory$caption", "ASC"),
                order("caption", "ASC")
        ));

        PagingResultImpl result = queryFacade.queryModelData(PagingRequest.buildPagingRequest(queryRequest, 20));
        List<Map<String, Object>> items = castItems(result);

        assertEquals(expectedRows.size(), items.size(), "嵌套子维度过滤后的成员数应与原生 SQL 一致");
        assertFalse(items.isEmpty(), "过滤后应存在成员");

        List<String> actualIds = items.stream().map(row -> String.valueOf(row.get("id"))).collect(Collectors.toList());
        List<String> expectedIds = expectedRows.stream().map(row -> String.valueOf(row.get("id"))).collect(Collectors.toList());
        assertEquals(expectedIds, actualIds, "嵌套子维度过滤与排序后的成员顺序应一致");

        for (int i = 0; i < items.size(); i++) {
            Map<String, Object> actual = items.get(i);
            Map<String, Object> expected = expectedRows.get(i);
            assertEquals(expected.get("caption"), actual.get("caption"));
            assertEquals(expected.get("brand"), actual.get("brand"));
            assertEquals(expected.get("category_caption"), actual.get("productCategory$caption"));
            assertEquals(expected.get("group_type"), actual.get("productCategory$categoryGroup$groupType"));
        }
    }

    @Test
    @DisplayName("synthetic member-QM 支持父子维 childrenOf")
    void querySyntheticMemberWithChildrenOf() {
        String expectedSql = """
                SELECT dt.team_id AS id,
                       dt.team_name AS caption
                  FROM dim_team dt
                  INNER JOIN team_closure tc ON dt.team_id = tc.team_id
                 WHERE tc.parent_id = 'T002' AND tc.distance = 1
                 ORDER BY dt.team_name ASC
                """;
        assertHierarchyIds("childrenOf", "T002", expectedSql);
    }

    @Test
    @DisplayName("synthetic member-QM 支持父子维 descendantsOf")
    void querySyntheticMemberWithDescendantsOf() {
        String expectedSql = """
                SELECT dt.team_id AS id,
                       dt.team_name AS caption,
                       dt.parent_id AS parent_id,
                       dt.team_level AS team_level
                  FROM dim_team dt
                  INNER JOIN team_closure tc ON dt.team_id = tc.team_id
                 WHERE tc.parent_id = 'T002' AND tc.distance > 0
                 ORDER BY dt.team_name ASC
                """;
        List<Map<String, Object>> expectedRows = executeQuery(expectedSql);

        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("FactTeamSalesQueryModel#team");
        queryRequest.setColumns(List.of("id", "caption", "parentId", "teamLevel"));
        queryRequest.setSlice(List.of(slice("id", "descendantsOf", "T002")));
        queryRequest.setOrderBy(List.of(order("caption", "ASC")));

        PagingResultImpl result = queryFacade.queryModelData(PagingRequest.buildPagingRequest(queryRequest, 20));
        List<Map<String, Object>> items = castItems(result);

        assertEquals(expectedRows.size(), items.size(), "descendantsOf 结果数量应与原生 SQL 一致");
        assertEquals(
                expectedRows.stream().map(row -> String.valueOf(row.get("id"))).collect(Collectors.toList()),
                items.stream().map(row -> String.valueOf(row.get("id"))).collect(Collectors.toList())
        );

        for (int i = 0; i < items.size(); i++) {
            Map<String, Object> actual = items.get(i);
            Map<String, Object> expected = expectedRows.get(i);
            assertEquals(expected.get("caption"), actual.get("caption"));
            assertEquals(String.valueOf(expected.get("parent_id")), String.valueOf(actual.get("parentId")));
            assertEquals(String.valueOf(expected.get("team_level")), String.valueOf(actual.get("teamLevel")));
        }
    }

    @Test
    @DisplayName("synthetic member-QM 支持父子维 selfAndDescendantsOf")
    void querySyntheticMemberWithSelfAndDescendantsOf() {
        String expectedSql = """
                SELECT dt.team_id AS id,
                       dt.team_name AS caption
                  FROM dim_team dt
                  INNER JOIN team_closure tc ON dt.team_id = tc.team_id
                 WHERE tc.parent_id = 'T002'
                 ORDER BY dt.team_name ASC
                """;
        assertHierarchyIds("selfAndDescendantsOf", "T002", expectedSql);
    }

    @Test
    @DisplayName("synthetic member-QM 支持父子维 ancestorsOf")
    void querySyntheticMemberWithAncestorsOf() {
        String expectedSql = """
                SELECT dt.team_id AS id,
                       dt.team_name AS caption,
                       dt.parent_id AS parent_id,
                       dt.team_level AS team_level
                  FROM dim_team dt
                  INNER JOIN team_closure tc ON dt.team_id = tc.parent_id
                 WHERE tc.team_id = 'T006' AND tc.distance > 0
                 ORDER BY dt.team_name ASC
                """;
        List<Map<String, Object>> expectedRows = executeQuery(expectedSql);

        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("FactTeamSalesQueryModel#team");
        queryRequest.setColumns(List.of("id", "caption", "parentId", "teamLevel"));
        queryRequest.setSlice(List.of(slice("id", "ancestorsOf", "T006")));
        queryRequest.setOrderBy(List.of(order("caption", "ASC")));

        PagingResultImpl result = queryFacade.queryModelData(PagingRequest.buildPagingRequest(queryRequest, 20));
        List<Map<String, Object>> items = castItems(result);

        assertEquals(expectedRows.size(), items.size(), "ancestorsOf 结果数量应与原生 SQL 一致");
        assertEquals(
                expectedRows.stream().map(row -> String.valueOf(row.get("id"))).collect(Collectors.toList()),
                items.stream().map(row -> String.valueOf(row.get("id"))).collect(Collectors.toList())
        );
    }

    @Test
    @DisplayName("synthetic member-QM 支持父子维 selfAndAncestorsOf")
    void querySyntheticMemberWithSelfAndAncestorsOf() {
        String expectedSql = """
                SELECT dt.team_id AS id,
                       dt.team_name AS caption
                  FROM dim_team dt
                  INNER JOIN team_closure tc ON dt.team_id = tc.parent_id
                 WHERE tc.team_id = 'T006'
                 ORDER BY dt.team_name ASC
                """;
        assertHierarchyIds("selfAndAncestorsOf", "T006", expectedSql);
    }

    @Test
    @DisplayName("simple 入口可在父子维字段上归一并映射 hierarchy 参数")
    void queryDimensionDataViaSyntheticModelForParentChildDimension() {
        DimensionDataQueryForm queryForm = new DimensionDataQueryForm(
                "FactTeamSalesQueryModel",
                "team$teamLevel"
        );
        queryForm.setHierarchy("childrenOf:T002");

        PagingResultImpl<DbDataItem> result = jdbcService.queryDimensionData(PagingRequest.buildPagingRequest(queryForm, 20));
        assertNotNull(result);
        assertNotNull(result.getItems());
        assertFalse(result.getItems().isEmpty(), "父子维 simple 入口应可归一到 synthetic member-QM");

        Set<String> actualIds = result.getItems().stream()
                .map(item -> String.valueOf(item.getId()))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        assertEquals(Set.of("T003", "T004"), actualIds, "simple 入口 hierarchy 归一结果应命中直接子节点");
    }

    @Test
    @DisplayName("external patch 可裁剪 synthetic member-QM 返回列")
    void externalPatchCanTrimVisibleColumns() {
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("FactSalesNestedDimQueryModel#product");
        queryRequest.setColumns(List.of("id", "caption", "brand", "productCategory$caption"));
        queryRequest.setExtData(Map.of(
                SyntheticMemberExternalPatch.EXT_DATA_KEY,
                Map.of("visibleColumns", List.of("id", "caption"))
        ));

        PagingResultImpl result = queryFacade.queryModelData(PagingRequest.buildPagingRequest(queryRequest, 20));
        List<Map<String, Object>> items = castItems(result);
        assertFalse(items.isEmpty(), "visibleColumns 裁剪后仍应返回成员");

        for (Map<String, Object> row : items) {
            assertEquals(Set.of("id", "caption"), row.keySet(), "返回列应只保留 visibleColumns 交集");
        }
    }

    @Test
    @DisplayName("external patch forcedSlice 可与请求条件合并")
    void externalPatchForcedSliceCanMergeWithRequestSlice() {
        String expectedSql = """
                SELECT p.product_key AS id,
                       p.product_name AS caption,
                       p.brand AS brand,
                       g.group_type AS group_type
                  FROM dim_product_nested p
                  LEFT JOIN dim_category_nested c ON p.category_key = c.category_key
                  LEFT JOIN dim_category_group g ON c.group_key = g.group_key
                 WHERE g.group_type = '高价值'
                   AND p.brand = 'Apple'
                 ORDER BY p.product_key ASC
                """;
        List<Map<String, Object>> expectedRows = executeQuery(expectedSql);

        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("FactSalesNestedDimQueryModel#product");
        queryRequest.setColumns(List.of("id", "caption", "brand", "productCategory$categoryGroup$groupType"));
        queryRequest.setSlice(List.of(slice("brand", "=", "Apple")));
        queryRequest.setOrderBy(List.of(order("id", "ASC")));
        queryRequest.setExtData(Map.of(
                SyntheticMemberExternalPatch.EXT_DATA_KEY,
                Map.of("forcedSlice", List.of(Map.of(
                        "field", "productCategory$categoryGroup$groupType",
                        "op", "=",
                        "value", "高价值"
                )))
        ));

        PagingResultImpl result = queryFacade.queryModelData(PagingRequest.buildPagingRequest(queryRequest, 20));
        List<Map<String, Object>> items = castItems(result);

        assertEquals(expectedRows.size(), items.size(), "forcedSlice 合并后的成员数应与原生 SQL 一致");
        assertEquals(
                expectedRows.stream().map(row -> String.valueOf(row.get("id"))).collect(Collectors.toList()),
                items.stream().map(row -> String.valueOf(row.get("id"))).collect(Collectors.toList())
        );
    }

    @Test
    @DisplayName("external patch forcedOrderBy 可覆盖同字段请求排序")
    void externalPatchForcedOrderByCanOverrideRequestOrder() {
        String expectedSql = """
                SELECT p.product_key AS id,
                       p.product_name AS caption,
                       p.brand AS brand
                  FROM dim_product_nested p
                 ORDER BY p.brand ASC
                """;
        List<Map<String, Object>> expectedRows = executeQuery(expectedSql);

        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("FactSalesNestedDimQueryModel#product");
        queryRequest.setColumns(List.of("id", "caption", "brand"));
        queryRequest.setOrderBy(List.of(order("brand", "DESC")));
        queryRequest.setExtData(Map.of(
                SyntheticMemberExternalPatch.EXT_DATA_KEY,
                Map.of("forcedOrderBy", List.of(Map.of(
                        "field", "brand",
                        "dir", "ASC"
                )))
        ));

        PagingResultImpl result = queryFacade.queryModelData(PagingRequest.buildPagingRequest(queryRequest, 20));
        List<Map<String, Object>> items = castItems(result);

        assertEquals(
                expectedRows.stream().map(row -> String.valueOf(row.get("id"))).collect(Collectors.toList()),
                items.stream().map(row -> String.valueOf(row.get("id"))).collect(Collectors.toList()),
                "forcedOrderBy 应覆盖同字段的请求排序"
        );
    }

    @Test
    @DisplayName("simple 入口透传 external patch 后可命中同一 forcedSlice 合并逻辑")
    void simpleEntryCanForwardExternalPatch() {
        String expectedSql = """
                SELECT p.product_key AS id,
                       p.product_name AS caption
                  FROM dim_product_nested p
                 WHERE p.brand = 'Apple'
                 ORDER BY p.product_name ASC
                """;
        List<Map<String, Object>> expectedRows = executeQuery(expectedSql);

        DimensionDataQueryForm queryForm = new DimensionDataQueryForm(
                "FactSalesNestedDimQueryModel",
                "brand"
        );
        queryForm.setExtData(Map.of(
                SyntheticMemberExternalPatch.EXT_DATA_KEY,
                Map.of(
                        "forcedSlice", List.of(Map.of(
                                "field", "brand",
                                "op", "=",
                                "value", "Apple"
                        )),
                        "forcedOrderBy", List.of(Map.of(
                                "field", "caption",
                                "dir", "ASC"
                        ))
                )
        ));

        PagingResultImpl<DbDataItem> result = jdbcService.queryDimensionData(PagingRequest.buildPagingRequest(queryForm, 20));
        assertNotNull(result);
        assertNotNull(result.getItems());

        List<Map<String, Object>> expectedCanonicalRows = new java.util.ArrayList<>();
        for (Map<String, Object> expectedRow : expectedRows) {
            Map<String, Object> canonical = new LinkedHashMap<>();
            canonical.put("id", String.valueOf(expectedRow.get("id")));
            canonical.put("caption", expectedRow.get("caption"));
            expectedCanonicalRows.add(canonical);
        }

        assertEquals(
                expectedCanonicalRows.stream().map(row -> String.valueOf(row.get("id"))).collect(Collectors.toList()),
                result.getItems().stream().map(item -> String.valueOf(item.getId())).collect(Collectors.toList()),
                "simple 入口 external patch 过滤结果应与原生 SQL 一致"
        );
        assertEquals(
                expectedCanonicalRows.stream().map(row -> String.valueOf(row.get("caption"))).collect(Collectors.toList()),
                result.getItems().stream().map(DbDataItem::getCaption).collect(Collectors.toList()),
                "simple 入口 external patch 排序结果应与原生 SQL 一致"
        );
    }

    private void assertHierarchyIds(String op, String value, String expectedSql) {
        List<Map<String, Object>> expectedRows = executeQuery(expectedSql);

        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("FactTeamSalesQueryModel#team");
        queryRequest.setColumns(List.of("id", "caption"));
        queryRequest.setSlice(List.of(slice("id", op, value)));
        queryRequest.setOrderBy(List.of(order("caption", "ASC")));

        PagingResultImpl result = queryFacade.queryModelData(PagingRequest.buildPagingRequest(queryRequest, 20));
        List<Map<String, Object>> items = castItems(result);

        assertEquals(
                expectedRows.stream().map(row -> String.valueOf(row.get("id"))).collect(Collectors.toList()),
                items.stream().map(row -> String.valueOf(row.get("id"))).collect(Collectors.toList()),
                op + " 结果 ID 应与原生 SQL 一致"
        );
        assertEquals(
                expectedRows.stream().map(row -> String.valueOf(row.get("caption"))).collect(Collectors.toList()),
                items.stream().map(row -> String.valueOf(row.get("caption"))).collect(Collectors.toList()),
                op + " 结果 caption 应与原生 SQL 一致"
        );
    }

    private SliceRequestDef slice(String field, String op, Object value) {
        SliceRequestDef slice = new SliceRequestDef();
        slice.setField(field);
        slice.setOp(op);
        slice.setValue(value);
        return slice;
    }

    private OrderRequestDef order(String field, String dir) {
        OrderRequestDef order = new OrderRequestDef();
        order.setField(field);
        order.setDir(dir);
        return order;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> castItems(PagingResultImpl result) {
        return (List<Map<String, Object>>) result.getItems();
    }
}
