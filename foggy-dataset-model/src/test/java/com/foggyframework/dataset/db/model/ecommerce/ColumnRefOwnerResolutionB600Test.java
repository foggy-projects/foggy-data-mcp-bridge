package com.foggyframework.dataset.db.model.ecommerce;

import com.foggyframework.dataset.client.domain.PagingRequest;
import com.foggyframework.dataset.db.model.def.query.request.DbQueryRequestDef;
import com.foggyframework.dataset.db.model.engine.JdbcModelQueryEngine;
import com.foggyframework.dataset.db.model.engine.query.DbQueryResult;
import com.foggyframework.dataset.db.model.plugins.result_set_filter.ModelResultContext;
import com.foggyframework.dataset.db.model.service.QueryFacade;
import com.foggyframework.dataset.db.model.spi.DbColumn;
import com.foggyframework.dataset.db.model.spi.DbQueryColumn;
import com.foggyframework.dataset.db.model.spi.JdbcQueryModel;
import com.foggyframework.dataset.db.model.spi.TableModel;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * B600: unaliased ColumnRef must retain its table-model owner.
 */
class ColumnRefOwnerResolutionB600Test extends EcommerceTestSupport {

    @Resource
    private QueryFacade queryFacade;

    @Test
    @DisplayName("B600: 无显式 alias 的显式运单 ColumnRef 应保留 owner 并执行 SQLite SQL")
    void unaliasedExplicitOrderRefsShouldKeepOwnerAcrossSelectDimensionAndAccess() {
        JdbcQueryModel queryModel = getQueryModel("TerminalFulfillmentB600OwnerRefProbeQueryModel");
        assertNotNull(queryModel, "B600 owner-aware 探针 QM 加载失败");

        TableModel stockRoot = queryModel.getJdbcModelList().stream()
                .filter(model -> "FactOrderB600StockItemProbeModel".equals(model.getName()))
                .findFirst()
                .orElseThrow();
        TableModel explicitOrder = queryModel.getJdbcModelList().stream()
                .filter(model -> "FactOrderB600ExpressOrderProbeModel".equals(model.getName()))
                .findFirst()
                .orElseThrow();

        DbQueryColumn tenantColumn = queryModel.findJdbcQueryColumnByName("tenantId", true);
        assertEquals(explicitOrder.getAlias(),
                queryModel.getAlias(tenantColumn.getSelectColumn().getQueryObject()),
                "fo.tenantId 不得在列组加载时退化为库存根表的同名 tenantId");
        assertFalse(stockRoot.getAlias().equals(
                        queryModel.getAlias(tenantColumn.getSelectColumn().getQueryObject())),
                "B600 回归列不得错误绑定到库存根表");

        DbQueryColumn openingOrgCaption = queryModel.findJdbcQueryColumnByName("openingOrg$caption", true);
        DbColumn explicitOrderOpeningOrgCaption = explicitOrder.findJdbcColumnByName("openingOrg$caption");
        assertNotNull(explicitOrderOpeningOrgCaption, "显式运单模型应暴露 openingOrg$caption");
        assertEquals(queryModel.getAlias(explicitOrderOpeningOrgCaption.getQueryObject()),
                queryModel.getAlias(openingOrgCaption.getSelectColumn().getQueryObject()),
                "fo.openingOrg$caption 必须归属显式运单 join 的维度路径，而不是退化为根模型路径");
        String explicitDimensionAlias = queryModel.getAlias(openingOrgCaption.getSelectColumn().getQueryObject());

        DbQueryRequestDef request = new DbQueryRequestDef();
        request.setQueryModel("TerminalFulfillmentB600OwnerRefProbeQueryModel");
        request.setColumns(List.of("stockItemId", "orderNo", "tenantId", "openingOrg$caption"));

        ModelResultContext context = new ModelResultContext();
        context.setRequest(PagingRequest.buildPagingRequest(request, 100));
        DbQueryResult result = queryFacade.queryModelResult(context);
        JdbcModelQueryEngine queryEngine = (JdbcModelQueryEngine) result.getQueryEngine();
        String sql = normalizeSql(queryEngine.getSql());

        assertTrue(sql.contains("left join fact_order " + explicitOrder.getAlias()),
                "显式运单 join 必须保留在 SQL 中");
        assertTrue(sql.contains(explicitOrder.getAlias() + ".date_key = ?"),
                "access 中 fo.tenant$id 必须解析到显式运单别名，而不是库存根表");
        assertFalse(sql.contains(stockRoot.getAlias() + ".date_key = ?"),
                "access 中 fo.tenant$id 不得降级为库存根表字段");
        assertTrue(sql.contains("left join dim_store " + explicitDimensionAlias),
                "fo.openingOrg$caption 必须真实 join 到显式运单的 dim_store 维表");
        assertTrue(sql.contains("on " + explicitOrder.getAlias() + ".store_key="
                        + explicitDimensionAlias + ".store_key"),
                "openingOrg 维表 JOIN 左侧必须是显式运单 alias，而不能是库存根/sourceOrder");
        assertFalse(sql.contains("left join fact_order d1"),
                "根 sourceOrder 的局部 d1 不得抢占显式运单 openingOrg 的维表路径");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) result.getPagingResult().getItems();
        assertFalse(rows.isEmpty(), "B600 回归 SQL 必须能在 SQLite 真实 fixture 上执行并返回数据");
        assertTrue(rows.stream().allMatch(row -> row.get("orderNo") != null),
                "显式运单 orderNo 应作为查询结果返回");
    }

    @Test
    @DisplayName("B600: 显式 alias 自连接的维度自动展开保留 owner，遗留 String ref 仍 root-first")
    void explicitAliasSelfJoinAndLegacyStringRefShouldKeepTheirSeparateContracts() {
        JdbcQueryModel queryModel = getQueryModel("TerminalFulfillmentB600ExplicitAliasSelfJoinProbeQueryModel");
        assertNotNull(queryModel, "B600 显式 alias/self-join 探针 QM 加载失败");

        TableModel leftOrder = findModelByAlias(queryModel, "leftOrder");
        TableModel rightOrder = findModelByAlias(queryModel, "rightOrder");

        DbQueryColumn rightTenant = queryModel.findJdbcQueryColumnByName("rightOrder.tenantId", true);
        assertEquals(rightOrder.getAlias(),
                queryModel.getAlias(rightTenant.getSelectColumn().getQueryObject()),
                "显式 alias 的右侧同名 tenantId 必须绑定 rightOrder");

        DbQueryColumn legacyTenant = queryModel.findJdbcQueryColumnByName("tenantId", true);
        assertEquals(leftOrder.getAlias(),
                queryModel.getAlias(legacyTenant.getSelectColumn().getQueryObject()),
                "遗留 String ref 保持既有 root-first 解析，不应被 V2 owner 逻辑改写");

        DbQueryColumn rightOpeningOrgCaption =
                queryModel.findJdbcQueryColumnByName("rightOrder.openingOrg$caption", true);
        String rightDimensionAlias = queryModel.getAlias(rightOpeningOrgCaption.getSelectColumn().getQueryObject());
        assertEquals("rightOrder__d1", rightDimensionAlias,
                "显式 alias 的维度本体自动展开必须绑定该 self-join 实例的维表节点");

        DbQueryRequestDef request = new DbQueryRequestDef();
        request.setQueryModel("TerminalFulfillmentB600ExplicitAliasSelfJoinProbeQueryModel");
        request.setColumns(List.of(
                "leftOrder.orderNo",
                "rightOrder.tenantId",
                "rightOrder.openingOrg$caption",
                "tenantId"));

        ModelResultContext context = new ModelResultContext();
        context.setRequest(PagingRequest.buildPagingRequest(request, 100));
        DbQueryResult result = queryFacade.queryModelResult(context);
        JdbcModelQueryEngine queryEngine = (JdbcModelQueryEngine) result.getQueryEngine();
        String sql = normalizeSql(queryEngine.getSql());

        assertTrue(sql.contains("left join fact_order rightOrder"),
                "同一 TM 的显式 alias self-join 必须保留 rightOrder JOIN");
        assertTrue(sql.contains("left join dim_store " + rightDimensionAlias),
                "右侧维度自动展开必须 JOIN 到实例化后的 dim_store 节点");
        assertTrue(sql.contains("on rightOrder.store_key=" + rightDimensionAlias + ".store_key"),
                "rightOrder openingOrg 的维表 JOIN 不能错接到 leftOrder");
        assertTrue(sql.contains("rightOrder.date_key = ?"),
                "access ColumnRef 在 explicit alias/self-join 中仍必须解析到 rightOrder");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) result.getPagingResult().getItems();
        assertFalse(rows.isEmpty(), "显式 alias/self-join SQL 必须在 SQLite fixture 上真实执行");
    }

    @Test
    @DisplayName("B600: 显式 alias 的维度自动属性和嵌套子维度保留公开前缀并执行 SQLite SQL")
    void explicitAliasDimensionExpansionShouldKeepQualifiedPropertiesAndNestedChildren() {
        JdbcQueryModel queryModel = getQueryModel("TerminalFulfillmentB600ExplicitAliasNestedDimensionProbeQueryModel");
        assertNotNull(queryModel, "B600 显式 alias 嵌套维度探针 QM 加载失败");

        TableModel rightSales = findModelByAlias(queryModel, "rightSales");
        Set<String> columnNames = queryModel.getJdbcQueryColumns().stream()
                .map(DbQueryColumn::getName)
                .collect(Collectors.toSet());

        // Fully automatic right-side product expansion: both properties and nested
        // category fields must retain rightSales as their public qualifier.
        assertTrue(columnNames.contains("rightSales.product$id"));
        assertTrue(columnNames.contains("rightSales.product$caption"));
        assertTrue(columnNames.contains("rightSales.product$productId"));
        assertTrue(columnNames.contains("rightSales.product$brand"));
        assertTrue(columnNames.contains("rightSales.product_category$id"));
        assertTrue(columnNames.contains("rightSales.product_category$caption"));
        assertTrue(columnNames.contains("rightSales.product_category$categoryId"));
        assertFalse(columnNames.contains("product$productId"),
                "显式 alias 自动属性不得退化成裸 product$productId");
        assertFalse(columnNames.contains("product_category$categoryId"),
                "显式 alias 自动嵌套属性不得退化成裸 product_category$categoryId");

        // The store item deliberately has an explicit property and explicit child.
        // Those references suppress the parent item's corresponding automatic paths.
        assertTrue(columnNames.contains("rightSales.store$storeType"));
        assertFalse(columnNames.contains("store$storeId"),
                "显式 rightSales.store$storeType 必须抑制父维度的裸自动属性");
        assertTrue(columnNames.contains("rightSales.store_region$id"));
        assertTrue(columnNames.contains("rightSales.store_region$caption"));
        assertTrue(columnNames.contains("rightSales.store_region$regionId"));
        assertFalse(columnNames.contains("store_region$regionId"),
                "显式 rightSales.store.region 必须抑制父维度的裸自动子维度展开");

        assertColumnOwner(queryModel, rightSales,
                "rightSales.product$productId", "product$productId");
        assertColumnOwner(queryModel, rightSales,
                "rightSales.product_category$categoryId", "product_category$categoryId");
        assertColumnOwner(queryModel, rightSales,
                "rightSales.store_region$regionId", "store_region$regionId");

        String productAlias = queryModel.getAlias(queryModel.findJdbcQueryColumnByName(
                "rightSales.product$productId", true).getSelectColumn().getQueryObject());
        String categoryAlias = queryModel.getAlias(queryModel.findJdbcQueryColumnByName(
                "rightSales.product_category$categoryId", true).getSelectColumn().getQueryObject());
        String storeAlias = queryModel.getAlias(queryModel.findJdbcQueryColumnByName(
                "rightSales.store$storeType", true).getSelectColumn().getQueryObject());
        String regionAlias = queryModel.getAlias(queryModel.findJdbcQueryColumnByName(
                "rightSales.store_region$regionId", true).getSelectColumn().getQueryObject());

        DbQueryRequestDef request = new DbQueryRequestDef();
        request.setQueryModel("TerminalFulfillmentB600ExplicitAliasNestedDimensionProbeQueryModel");
        request.setColumns(List.of(
                "rightSales.product$caption",
                "rightSales.product$productId",
                "rightSales.product_category$caption",
                "rightSales.product_category$categoryId",
                "rightSales.store$caption",
                "rightSales.store$storeType",
                "rightSales.store_region$caption",
                "rightSales.store_region$regionId"));

        ModelResultContext context = new ModelResultContext();
        context.setRequest(PagingRequest.buildPagingRequest(request, 100));
        DbQueryResult result = queryFacade.queryModelResult(context);
        JdbcModelQueryEngine queryEngine = (JdbcModelQueryEngine) result.getQueryEngine();
        String sql = normalizeSql(queryEngine.getSql());

        assertTrue(sql.contains("left join fact_sales_nested rightSales"),
                "显式 alias self-join 必须保留 rightSales 事实表实例");
        assertTrue(sql.contains("left join dim_product_nested " + productAlias),
                "rightSales product 自动属性必须 JOIN 到 rightSales 所属维表实例");
        assertTrue(sql.contains("left join dim_category_nested " + categoryAlias),
                "rightSales product 子维度必须 JOIN 到 rightSales 所属嵌套实例");
        assertTrue(sql.contains("left join dim_store_nested " + storeAlias),
                "rightSales store 显式属性必须 JOIN 到 rightSales 所属维表实例");
        assertTrue(sql.contains("left join dim_region_nested " + regionAlias),
                "rightSales store 显式子维度必须 JOIN 到 rightSales 所属嵌套实例");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) result.getPagingResult().getItems();
        assertFalse(rows.isEmpty(), "显式 alias 嵌套维度 SQL 必须在 SQLite fixture 上真实执行并返回数据");
        assertTrue(rows.stream().allMatch(row -> row.get("rightSales.product$productId") != null
                        && row.get("rightSales.product_category$categoryId") != null
                        && row.get("rightSales.store_region$regionId") != null),
                "右侧自动/显式嵌套维度字段必须作为真实查询结果返回");
    }

    private void assertColumnOwner(JdbcQueryModel queryModel, TableModel ownerModel,
                                   String queryColumnName, String ownerColumnName) {
        DbQueryColumn queryColumn = queryModel.findJdbcQueryColumnByName(queryColumnName, true);
        DbColumn ownerColumn = ownerModel.findJdbcColumnByName(ownerColumnName);
        assertNotNull(ownerColumn, "owner TM 缺少字段: " + ownerColumnName);
        assertEquals(queryModel.getAlias(ownerColumn.getQueryObject()),
                queryModel.getAlias(queryColumn.getSelectColumn().getQueryObject()),
                queryColumnName + " 必须保留 rightSales 的维表实例归属");
    }

    private TableModel findModelByAlias(JdbcQueryModel queryModel, String alias) {
        return queryModel.getJdbcModelList().stream()
                .filter(model -> alias.equals(model.getAlias()))
                .findFirst()
                .orElseThrow();
    }

    private String normalizeSql(String sql) {
        return sql.replace('`', '"').replaceAll("\\s+", " ").trim();
    }
}
