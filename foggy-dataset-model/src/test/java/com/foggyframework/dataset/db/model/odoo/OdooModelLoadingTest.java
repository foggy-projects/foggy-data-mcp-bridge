package com.foggyframework.dataset.db.model.odoo;

import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.dataset.db.model.def.query.request.CalculatedFieldDef;
import com.foggyframework.dataset.db.model.def.query.request.DbQueryRequestDef;
import com.foggyframework.dataset.db.model.def.query.request.GroupRequestDef;
import com.foggyframework.dataset.db.model.def.query.request.OrderRequestDef;
import com.foggyframework.dataset.db.model.def.query.request.PostAggregateCalculationDef;
import com.foggyframework.dataset.db.model.def.query.request.SliceRequestDef;
import com.foggyframework.dataset.db.model.ecommerce.EcommerceTestSupport;
import com.foggyframework.dataset.db.model.engine.JdbcModelQueryEngine;
import com.foggyframework.dataset.db.model.engine.formula.SqlFormulaService;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticMetadataRequest;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticMetadataResponse;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticQueryRequest;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticQueryResponse;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticRequestContext;
import com.foggyframework.dataset.db.model.semantic.service.SemanticQueryServiceV3;
import com.foggyframework.dataset.db.model.semantic.service.SemanticServiceV3;
import com.foggyframework.dataset.db.model.spi.DbColumn;
import com.foggyframework.dataset.db.model.spi.DbDimension;
import com.foggyframework.dataset.db.model.spi.JdbcQueryModel;
import com.foggyframework.dataset.db.model.spi.TableModel;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Odoo TM/QM 模型加载测试
 *
 * <p>验证所有 Odoo 模型文件能正确加载，维度/属性/度量配置完整。</p>
 * <p>同时验证 Odoo 表结构（06-odoo-schema.sql）与 TM 模型列定义一致。</p>
 *
 * @author foggy-odoo-bridge
 */
@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Odoo 模型加载测试")
class OdooModelLoadingTest extends EcommerceTestSupport {

    @Resource
    private SqlFormulaService sqlFormulaService;

    @Resource
    private SystemBundlesContext systemBundlesContext;

    @Resource
    private SemanticServiceV3 semanticServiceV3;

    @Resource
    private SemanticQueryServiceV3 semanticQueryServiceV3;

    // ==========================================
    // TM 模型加载测试
    // ==========================================

    @Test
    @Order(1)
    @DisplayName("加载 OdooSaleOrderModel")
    void testLoadSaleOrderModel() {
        TableModel model = tableModelLoaderManager.load("OdooSaleOrderModel");
        assertNotNull(model, "OdooSaleOrderModel 加载失败");
        assertEquals("OdooSaleOrderModel", model.getName());
        assertEquals("sale_order", model.getTableName());
        assertEquals("id", model.getIdColumn());

        assertNotNull(model.getDimensions(), "维度定义为空");
        assertTrue(model.getDimensions().size() >= 7, "维度数量不足（预期>=7: dateOrder, partner, salesperson, company, salesTeam, pricelist, warehouse）");

        assertNotNull(model.getMeasures(), "度量定义为空");
        assertTrue(model.getMeasures().size() >= 4, "度量数量不足（预期>=4）");

        log.info("OdooSaleOrderModel 加载成功: 维度数={}, 属性数={}, 度量数={}",
            model.getDimensions().size(),
            model.getProperties().size(),
            model.getMeasures().size());
    }

    @Test
    @Order(2)
    @DisplayName("加载 OdooSaleOrderLineModel")
    void testLoadSaleOrderLineModel() {
        TableModel model = tableModelLoaderManager.load("OdooSaleOrderLineModel");
        assertNotNull(model, "OdooSaleOrderLineModel 加载失败");
        assertEquals("OdooSaleOrderLineModel", model.getName());
        assertEquals("sale_order_line", model.getTableName());

        assertNotNull(model.getDimensions(), "维度定义为空");
        assertTrue(model.getDimensions().size() >= 4, "维度数量不足（预期>=4: order, product, uom, salesperson, company）");

        log.info("OdooSaleOrderLineModel 加载成功: 维度数={}, 属性数={}, 度量数={}",
            model.getDimensions().size(),
            model.getProperties().size(),
            model.getMeasures().size());
    }

    @Test
    @Order(3)
    @DisplayName("加载 OdooPurchaseOrderModel")
    void testLoadPurchaseOrderModel() {
        TableModel model = tableModelLoaderManager.load("OdooPurchaseOrderModel");
        assertNotNull(model, "OdooPurchaseOrderModel 加载失败");
        assertEquals("OdooPurchaseOrderModel", model.getName());
        assertEquals("purchase_order", model.getTableName());

        assertNotNull(model.getDimensions(), "维度定义为空");
        assertTrue(model.getDimensions().size() >= 4, "维度数量不足");

        log.info("OdooPurchaseOrderModel 加载成功: 维度数={}, 属性数={}, 度量数={}",
            model.getDimensions().size(),
            model.getProperties().size(),
            model.getMeasures().size());
    }

    @Test
    @Order(4)
    @DisplayName("加载 OdooAccountMoveModel")
    void testLoadAccountMoveModel() {
        TableModel model = tableModelLoaderManager.load("OdooAccountMoveModel");
        assertNotNull(model, "OdooAccountMoveModel 加载失败");
        assertEquals("OdooAccountMoveModel", model.getName());
        assertEquals("account_move", model.getTableName());

        assertNotNull(model.getDimensions(), "维度定义为空");
        assertTrue(model.getDimensions().size() >= 5, "维度数量不足（预期>=5: partner, journal, company, currency, salesperson, salesTeam）");

        log.info("OdooAccountMoveModel 加载成功: 维度数={}, 属性数={}, 度量数={}",
            model.getDimensions().size(),
            model.getProperties().size(),
            model.getMeasures().size());
    }

    @Test
    @Order(5)
    @DisplayName("加载 OdooStockPickingModel")
    void testLoadStockPickingModel() {
        TableModel model = tableModelLoaderManager.load("OdooStockPickingModel");
        assertNotNull(model, "OdooStockPickingModel 加载失败");
        assertEquals("OdooStockPickingModel", model.getName());
        assertEquals("stock_picking", model.getTableName());

        assertNotNull(model.getDimensions(), "维度定义为空");
        assertTrue(model.getDimensions().size() >= 5, "维度数量不足");

        log.info("OdooStockPickingModel 加载成功: 维度数={}, 属性数={}, 度量数={}",
            model.getDimensions().size(),
            model.getProperties().size(),
            model.getMeasures().size());
    }

    @Test
    @Order(6)
    @DisplayName("加载 OdooHrEmployeeModel")
    void testLoadHrEmployeeModel() {
        TableModel model = tableModelLoaderManager.load("OdooHrEmployeeModel");
        assertNotNull(model, "OdooHrEmployeeModel 加载失败");
        assertEquals("OdooHrEmployeeModel", model.getName());
        assertEquals("hr_employee", model.getTableName());

        assertNotNull(model.getDimensions(), "维度定义为空");
        assertTrue(model.getDimensions().size() >= 5, "维度数量不足");

        log.info("OdooHrEmployeeModel 加载成功: 维度数={}, 属性数={}, 度量数={}",
            model.getDimensions().size(),
            model.getProperties().size(),
            model.getMeasures().size());
    }

    @Test
    @Order(7)
    @DisplayName("加载 OdooResPartnerModel")
    void testLoadResPartnerModel() {
        TableModel model = tableModelLoaderManager.load("OdooResPartnerModel");
        assertNotNull(model, "OdooResPartnerModel 加载失败");
        assertEquals("OdooResPartnerModel", model.getName());
        assertEquals("res_partner", model.getTableName());

        assertNotNull(model.getDimensions(), "维度定义为空");
        assertTrue(model.getDimensions().size() >= 4, "维度数量不足");

        log.info("OdooResPartnerModel 加载成功: 维度数={}, 属性数={}, 度量数={}",
            model.getDimensions().size(),
            model.getProperties().size(),
            model.getMeasures().size());
    }

    @Test
    @Order(8)
    @DisplayName("加载 OdooResCompanyModel (维度表)")
    void testLoadResCompanyModel() {
        TableModel model = tableModelLoaderManager.load("OdooResCompanyModel");
        assertNotNull(model, "OdooResCompanyModel 加载失败");
        assertEquals("OdooResCompanyModel", model.getName());
        assertEquals("res_company", model.getTableName());

        log.info("OdooResCompanyModel 加载成功: 维度数={}, 属性数={}, 度量数={}",
            model.getDimensions() != null ? model.getDimensions().size() : 0,
            model.getProperties().size(),
            model.getMeasures() != null ? model.getMeasures().size() : 0);
    }

    // ==========================================
    // QM 查询模型加载测试
    // ==========================================

    @Test
    @Order(20)
    @DisplayName("加载 OdooSaleOrderQueryModel")
    void testLoadSaleOrderQueryModel() {
        JdbcQueryModel queryModel = getQueryModel("OdooSaleOrderQueryModel");
        assertNotNull(queryModel, "OdooSaleOrderQueryModel 加载失败");
        assertEquals("OdooSaleOrderQueryModel", queryModel.getName());
        assertNotNull(queryModel.getColumnGroups(), "列组定义为空");
        assertTrue(queryModel.getColumnGroups().size() >= 3, "列组数量不足");
        log.info("OdooSaleOrderQueryModel 加载成功: 列组数={}", queryModel.getColumnGroups().size());
    }

    @Test
    @Order(201)
    @DisplayName("SaleOrder self date dimension 应暴露 dateOrder 粒度字段")
    void testSaleOrderSelfDateDimensionFields() {
        JdbcQueryModel queryModel = getQueryModel("OdooSaleOrderQueryModel");
        assertNotNull(queryModel.findDimension("dateOrder"));
        assertNotNull(queryModel.findJdbcColumnForSelectByName("dateOrder$caption", true));
        assertNotNull(queryModel.findJdbcColumnForSelectByName("dateOrder$year", true));
        assertNotNull(queryModel.findJdbcColumnForSelectByName("dateOrder$month", true));
        assertNotNull(queryModel.findJdbcColumnForSelectByName("dateOrder$yearMonth", true));
    }

    @Test
    @Order(2011)
    @DisplayName("SaleOrder self date dimension metadata 不应把 dateOrder$id 暴露为整数日期")
    void testSaleOrderSelfDateDimensionIdMetadataUsesDateType() {
        SemanticMetadataRequest request = new SemanticMetadataRequest();
        request.setQmModels(List.of("OdooSaleOrderQueryModel"));

        SemanticMetadataResponse jsonResponse = semanticServiceV3.getMetadata(
                request, "json", SemanticRequestContext.empty());
        @SuppressWarnings("unchecked")
        Map<String, Object> fields = (Map<String, Object>) jsonResponse.getData().get("fields");
        @SuppressWarnings("unchecked")
        Map<String, Object> dateOrderId = (Map<String, Object>) fields.get("dateOrder$id");

        assertNotNull(dateOrderId, "metadata 应包含 dateOrder$id");
        assertEquals("DATETIME", dateOrderId.get("type"));
        assertEquals("date", dateOrderId.get("filterType"));
        assertTrue(dateOrderId.get("meta").toString().contains("ISO date/datetime string values"));
        assertTrue(dateOrderId.get("meta").toString().contains("do not use numeric YYYYMMDD values"));

        SemanticMetadataResponse markdownResponse = semanticServiceV3.getMetadata(
                request, "markdown", SemanticRequestContext.empty());
        assertTrue(markdownResponse.getContent().contains("dateOrder$id"));
        assertTrue(markdownResponse.getContent().contains("ISO date/datetime string values"));
        assertTrue(markdownResponse.getContent().contains("do not use numeric YYYYMMDD values"));
    }

    @Test
    @Order(202)
    @DisplayName("SaleOrder self date dimension 粒度查询应生成主表表达式 SQL")
    void testSaleOrderSelfDateDimensionQueryUsesFactExpression() {
        JdbcQueryModel queryModel = getQueryModel("OdooSaleOrderQueryModel");
        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);

        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("OdooSaleOrderQueryModel");
        queryRequest.setColumns(Arrays.asList(
            "dateOrder$year",
            "dateOrder$month",
            "dateOrder$yearMonth",
            "sum(amountTotal) as totalSales"
        ));

        GroupRequestDef yearGroup = new GroupRequestDef();
        yearGroup.setField("dateOrder$year");
        GroupRequestDef monthGroup = new GroupRequestDef();
        monthGroup.setField("dateOrder$month");
        GroupRequestDef yearMonthGroup = new GroupRequestDef();
        yearMonthGroup.setField("dateOrder$yearMonth");
        queryRequest.setGroupBy(Arrays.asList(yearGroup, monthGroup, yearMonthGroup));

        queryEngine.analysisQueryRequest(systemBundlesContext, queryRequest);
        String sql = queryEngine.getSql();

        log.info("SaleOrder self date dimension SQL: {}", sql);
        assertNotNull(sql, "生成的 SQL 不应为空");
        assertFalse(sql.toLowerCase().contains("dim_date"),
            "self date dimension 不应 join dim_date: " + sql);
        assertTrue(sql.toLowerCase().contains("date_order"),
            "dateOrder 粒度字段应使用主表 date_order 表达式: " + sql);

        if (sql.toLowerCase().contains("strftime")) {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, queryEngine.getValues().toArray());
            assertFalse(rows.isEmpty(), "dateOrder 粒度聚合应返回数据");
            assertTrue(rows.stream().anyMatch(row -> Integer.valueOf(2025).equals(row.get("dateOrder$year"))),
                "测试数据应包含 2025 年销售订单");
        }
    }

    @Test
    @Order(203)
    @DisplayName("postAggregateCalculations ratioToTotal 生成外层过滤阶段")
    void testPostAggregateRatioToTotalUsesOuterFilterStage() {
        JdbcQueryModel queryModel = getQueryModel("OdooSaleOrderQueryModel");
        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);

        DbQueryRequestDef queryRequest = postAggregateSalesShareRequest();
        queryRequest.setPostAggregateCalculations(new ArrayList<>(List.of(new PostAggregateCalculationDef(
                "salesShare", "ratioToTotal", "teamSales", "grandTotal", "ratio"
        ))));

        queryEngine.analysisQueryRequest(systemBundlesContext, queryRequest);
        String sql = queryEngine.getSql();
        String normalizedSql = sql.replace('`', '"');

        assertTrue(normalizedSql.contains("WITH stage1 AS"), sql);
        assertTrue(normalizedSql.contains("post_stage AS"), sql);
        assertTrue(normalizedSql.contains("stage1.\"teamSales\" / NULLIF(SUM(stage1.\"teamSales\") OVER (), 0) AS \"salesShare\""), sql);
        assertTrue(normalizedSql.contains("FROM post_stage"), sql);
        assertTrue(normalizedSql.contains("WHERE \"salesShare\" > ?"), sql);
        assertTrue(normalizedSql.contains("ORDER BY \"teamSales\" DESC, \"salesShare\" DESC"), sql);
        assertEquals(0.2, queryEngine.getValues().get(queryEngine.getValues().size() - 1));
    }

    @Test
    @Order(204)
    @DisplayName("postAggregateCalculations 支持显式 postSlice 结果阶段过滤")
    void testPostAggregateRatioToTotalSupportsExplicitPostSlice() {
        JdbcQueryModel queryModel = getQueryModel("OdooSaleOrderQueryModel");
        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);

        DbQueryRequestDef queryRequest = postAggregateSalesShareRequest();
        queryRequest.setSlice(null);
        queryRequest.setPostSlice(List.of(new SliceRequestDef("salesShare", ">", 0.2)));
        queryRequest.setPostAggregateCalculations(new ArrayList<>(List.of(new PostAggregateCalculationDef(
                "salesShare", "ratioToTotal", "teamSales", "grandTotal", "ratio"
        ))));

        queryEngine.analysisQueryRequest(systemBundlesContext, queryRequest);
        String sql = queryEngine.getSql();
        String normalizedSql = sql.replace('`', '"');

        assertTrue(normalizedSql.contains("post_stage AS"), sql);
        assertTrue(normalizedSql.contains("FROM post_stage"), sql);
        assertTrue(normalizedSql.contains("WHERE \"salesShare\" > ?"), sql);
        assertEquals(0.2, queryEngine.getValues().get(queryEngine.getValues().size() - 1));
    }

    @Test
    @Order(205)
    @DisplayName("calculatedFields ratio_to_total 语法糖归一为 postAggregateCalculations")
    void testCalculatedFieldsRatioToTotalSugarNormalizesToPostAggregate() {
        JdbcQueryModel queryModel = getQueryModel("OdooSaleOrderQueryModel");
        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);

        DbQueryRequestDef queryRequest = postAggregateSalesShareRequest();
        queryRequest.setCalculatedFields(new ArrayList<>(List.of(new CalculatedFieldDef(
                "salesShare", "ratio_to_total(teamSales)"
        ))));

        queryEngine.analysisQueryRequest(systemBundlesContext, queryRequest);
        String sql = queryEngine.getSql();
        String normalizedSql = sql.replace('`', '"');

        assertTrue(normalizedSql.contains("post_stage AS"), sql);
        assertTrue(normalizedSql.contains("AS \"salesShare\""), sql);
        assertTrue(normalizedSql.contains("WHERE \"salesShare\" > ?"), sql);
    }

    @Test
    @Order(206)
    @DisplayName("calculatedFields 累计贡献与排名语法归一为 postAggregateCalculations")
    void testCalculatedFieldsCumulativeAndRankNormalizeToPostAggregate() {
        JdbcQueryModel queryModel = getQueryModel("OdooSaleOrderQueryModel");
        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);

        DbQueryRequestDef queryRequest = postAggregateSalesShareRequest();
        queryRequest.setSlice(null);
        queryRequest.setColumns(Arrays.asList(
                "salesTeam$id",
                "salesTeam$caption",
                "sum(amountTotal) as teamSales",
                "salesRank",
                "cumulativeSales",
                "cumulativeShare"
        ));
        queryRequest.setCalculatedFields(new ArrayList<>(List.of(
                new CalculatedFieldDef("salesRank", "rank_by(teamSales, desc)"),
                new CalculatedFieldDef("cumulativeSales", "cumulative_sum(teamSales, desc)"),
                new CalculatedFieldDef(
                        "cumulativeShare",
                        "cumulative_ratio_to_total(teamSales, desc)")
        )));
        OrderRequestDef order = new OrderRequestDef();
        order.setField("teamSales");
        order.setDir("desc");
        queryRequest.setOrderBy(List.of(order));

        queryEngine.analysisQueryRequest(systemBundlesContext, queryRequest);
        String sql = queryEngine.getSql();
        String normalizedSql = sql.replace('`', '"');

        assertTrue(normalizedSql.contains("post_stage AS"), sql);
        assertTrue(normalizedSql.contains("RANK() OVER (ORDER BY stage1.\"teamSales\" DESC) AS \"salesRank\""), sql);
        assertTrue(normalizedSql.contains("SUM(stage1.\"teamSales\") OVER (ORDER BY stage1.\"teamSales\" DESC ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW) AS \"cumulativeSales\""), sql);
        assertTrue(normalizedSql.contains("SUM(stage1.\"teamSales\") OVER (ORDER BY stage1.\"teamSales\" DESC ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW) / NULLIF(SUM(stage1.\"teamSales\") OVER (), 0) AS \"cumulativeShare\""), sql);
        assertTrue(queryRequest.getCalculatedFields().stream()
                .noneMatch(cf -> "salesRank".equals(cf.getName())
                        || "cumulativeSales".equals(cf.getName())
                        || "cumulativeShare".equals(cf.getName())));
        assertEquals(3, queryRequest.getPostAggregateCalculations().size());
    }

    @Test
    @Order(206)
    @DisplayName("postAggregateCalculations 显式累计贡献与排名生成外层结果阶段")
    void testExplicitCumulativeAndRankPostAggregateUsesOuterStage() {
        JdbcQueryModel queryModel = getQueryModel("OdooSaleOrderQueryModel");
        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);

        DbQueryRequestDef queryRequest = postAggregateSalesShareRequest();
        queryRequest.setSlice(null);
        queryRequest.setColumns(Arrays.asList(
                "salesTeam$id",
                "salesTeam$caption",
                "sum(amountTotal) as teamSales",
                "salesRank",
                "cumulativeSales",
                "cumulativeShare"
        ));
        queryRequest.setPostAggregateCalculations(new ArrayList<>(List.of(
                new PostAggregateCalculationDef("salesRank", "rankByMeasure", "teamSales", "grandTotal", "value"),
                new PostAggregateCalculationDef("cumulativeSales", "cumulativeSum", "teamSales", "grandTotal", "value"),
                new PostAggregateCalculationDef("cumulativeShare", "cumulativeRatioToTotal", "teamSales", "grandTotal", "ratio")
        )));
        queryRequest.setPostSlice(List.of(new SliceRequestDef("cumulativeShare", "<=", 0.8)));
        OrderRequestDef order = new OrderRequestDef();
        order.setField("salesRank");
        order.setDir("asc");
        queryRequest.setOrderBy(List.of(order));

        queryEngine.analysisQueryRequest(systemBundlesContext, queryRequest);
        String sql = queryEngine.getSql();
        String normalizedSql = sql.replace('`', '"');

        assertTrue(normalizedSql.contains("post_stage AS"), sql);
        assertTrue(normalizedSql.contains("RANK() OVER (ORDER BY stage1.\"teamSales\" DESC) AS \"salesRank\""), sql);
        assertTrue(normalizedSql.contains("SUM(stage1.\"teamSales\") OVER (ORDER BY stage1.\"teamSales\" DESC ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW) AS \"cumulativeSales\""), sql);
        assertTrue(normalizedSql.contains("SUM(stage1.\"teamSales\") OVER (ORDER BY stage1.\"teamSales\" DESC ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW) / NULLIF(SUM(stage1.\"teamSales\") OVER (), 0) AS \"cumulativeShare\""), sql);
        assertTrue(normalizedSql.contains("FROM post_stage"), sql);
        assertTrue(normalizedSql.contains("WHERE \"cumulativeShare\" <= ?"), sql);
        assertTrue(normalizedSql.contains("ORDER BY \"salesRank\" ASC"), sql);
        assertEquals(0.8, queryEngine.getValues().get(queryEngine.getValues().size() - 1));
    }

    @Test
    @Order(206)
    @DisplayName("calculatedFields 聚合别名总额占比公式归一为 postAggregateCalculations")
    void testCalculatedFieldsAliasRatioToTotalFormulaNormalizesToPostAggregate() {
        JdbcQueryModel queryModel = getQueryModel("OdooSaleOrderQueryModel");
        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);

        DbQueryRequestDef queryRequest = postAggregateSalesShareRequest();
        queryRequest.setCalculatedFields(new ArrayList<>(List.of(new CalculatedFieldDef(
                "salesShare", "teamSales / NULLIF(SUM(teamSales) OVER (), 0)"
        ))));

        queryEngine.analysisQueryRequest(systemBundlesContext, queryRequest);
        String sql = queryEngine.getSql();
        String normalizedSql = sql.replace('`', '"');

        assertTrue(normalizedSql.contains("post_stage AS"), sql);
        assertTrue(normalizedSql.contains("stage1.\"teamSales\" / NULLIF(SUM(stage1.\"teamSales\") OVER (), 0) AS \"salesShare\""), sql);
        assertTrue(normalizedSql.contains("WHERE \"salesShare\" > ?"), sql);
        assertTrue(queryRequest.getCalculatedFields().stream()
                .noneMatch(cf -> "salesShare".equals(cf.getName())));
        assertEquals("teamSales", queryRequest.getPostAggregateCalculations().get(0).getMeasure());
    }

    @Test
    @Order(206)
    @DisplayName("postAggregate 结果别名支持从 having 安全迁移到外层过滤")
    void testPostAggregateAliasHavingIsLiftedToResultStageFilter() {
        JdbcQueryModel queryModel = getQueryModel("OdooSaleOrderQueryModel");
        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);

        DbQueryRequestDef queryRequest = postAggregateSalesShareRequest();
        queryRequest.setSlice(null);
        queryRequest.setHaving(List.of(new SliceRequestDef("salesShare", ">", 0.2)));
        queryRequest.setCalculatedFields(new ArrayList<>(List.of(new CalculatedFieldDef(
                "salesShare", "teamSales / SUM(teamSales) OVER ()"
        ))));

        queryEngine.analysisQueryRequest(systemBundlesContext, queryRequest);
        String sql = queryEngine.getSql();
        String normalizedSql = sql.replace('`', '"');

        assertTrue(normalizedSql.contains("post_stage AS"), sql);
        assertTrue(normalizedSql.contains("FROM post_stage"), sql);
        assertTrue(normalizedSql.contains("WHERE \"salesShare\" > ?"), sql);
        assertFalse(normalizedSql.contains("HAVING \"salesShare\""), sql);
        assertEquals(0.2, queryEngine.getValues().get(queryEngine.getValues().size() - 1));
    }

    @Test
    @Order(207)
    @DisplayName("calculatedFields CALCULATE 聚合别名占比公式归一为 postAggregateCalculations")
    void testCalculatedFieldsAliasCalculateRatioToTotalFormulaNormalizesToPostAggregate() {
        JdbcQueryModel queryModel = getQueryModel("OdooSaleOrderQueryModel");
        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);

        DbQueryRequestDef queryRequest = postAggregateSalesShareRequest();
        queryRequest.setCalculatedFields(new ArrayList<>(List.of(new CalculatedFieldDef(
                "salesShare",
                "teamSales / NULLIF(CALCULATE(SUM(teamSales), REMOVE(salesTeam$id, salesTeam$caption)), 0)"
        ))));

        queryEngine.analysisQueryRequest(systemBundlesContext, queryRequest);
        String sql = queryEngine.getSql();
        String normalizedSql = sql.replace('`', '"');

        assertTrue(normalizedSql.contains("post_stage AS"), sql);
        assertTrue(normalizedSql.contains("stage1.\"teamSales\" / NULLIF(SUM(stage1.\"teamSales\") OVER (), 0) AS \"salesShare\""), sql);
        assertTrue(normalizedSql.contains("WHERE \"salesShare\" > ?"), sql);
        assertTrue(queryRequest.getCalculatedFields().stream()
                .noneMatch(cf -> "salesShare".equals(cf.getName())));
        assertEquals("teamSales", queryRequest.getPostAggregateCalculations().get(0).getMeasure());
    }

    @Test
    @Order(208)
    @DisplayName("calculatedFields 与已有字段同名应返回稳定错误码")
    void testCalculatedFieldNameCollisionUsesStableErrorCode() {
        JdbcQueryModel queryModel = getQueryModel("OdooSaleOrderQueryModel");
        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);

        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("OdooSaleOrderQueryModel");
        queryRequest.setColumns(List.of("amountTotal"));
        queryRequest.setCalculatedFields(new ArrayList<>(List.of(new CalculatedFieldDef(
                "amountTotal", "amountTotal + 1"
        ))));

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> queryEngine.analysisQueryRequest(systemBundlesContext, queryRequest));

        String messageChain = exception.toString()
                + " " + (exception.getMessage() == null ? "" : exception.getMessage())
                + " " + (exception.getCause() == null ? "" : exception.getCause().getMessage());
        assertTrue(messageChain.contains("CALCULATED_FIELD_NAME_COLLISION"), messageChain);
        assertTrue(messageChain.contains("amountTotal"), messageChain);
    }

    @Test
    @Order(208)
    @DisplayName("全局 predefined ratio measure 应聚合 measure 依赖")
    void testGlobalPredefinedRatioMeasureAggregatesDependencies() {
        JdbcQueryModel queryModel = getQueryModel("OdooAccountMoveQueryModel");
        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);

        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("OdooAccountMoveQueryModel");
        queryRequest.setColumns(List.of("collectionRate"));

        queryEngine.analysisQueryRequest(systemBundlesContext, queryRequest);
        String sql = queryEngine.getSql();

        assertTrue(sql.contains("SUM(t1.amount_total)") || sql.contains("SUM(t.amount_total)"), sql);
        assertTrue(sql.contains("SUM(t1.amount_residual)") || sql.contains("SUM(t.amount_residual)"), sql);
        assertFalse(sql.contains("t.amount_total - t.amount_residual"), sql);
        assertFalse(sql.toUpperCase().contains("GROUP BY"), sql);
        assertFalse(sql.toUpperCase().contains("ORDER BY"), sql);
    }

    private DbQueryRequestDef postAggregateSalesShareRequest() {
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("OdooSaleOrderQueryModel");
        queryRequest.setColumns(Arrays.asList(
                "salesTeam$id",
                "salesTeam$caption",
                "sum(amountTotal) as teamSales",
                "salesShare"
        ));

        GroupRequestDef group1 = new GroupRequestDef();
        group1.setField("salesTeam$id");
        GroupRequestDef group2 = new GroupRequestDef();
        group2.setField("salesTeam$caption");
        queryRequest.setGroupBy(Arrays.asList(group1, group2));

        queryRequest.setSlice(List.of(new SliceRequestDef("salesShare", ">", 0.2)));

        OrderRequestDef order1 = new OrderRequestDef();
        order1.setField("teamSales");
        order1.setDir("desc");
        OrderRequestDef order2 = new OrderRequestDef();
        order2.setField("salesShare");
        order2.setDir("desc");
        queryRequest.setOrderBy(List.of(order1, order2));
        return queryRequest;
    }

    @Test
    @Order(21)
    @DisplayName("加载 OdooPurchaseOrderQueryModel")
    void testLoadPurchaseOrderQueryModel() {
        JdbcQueryModel queryModel = getQueryModel("OdooPurchaseOrderQueryModel");
        assertNotNull(queryModel, "OdooPurchaseOrderQueryModel 加载失败");
        assertEquals("OdooPurchaseOrderQueryModel", queryModel.getName());
        assertNotNull(queryModel.getColumnGroups(), "列组定义为空");
        log.info("OdooPurchaseOrderQueryModel 加载成功: 列组数={}", queryModel.getColumnGroups().size());
    }

    @Test
    @Order(22)
    @DisplayName("加载 OdooAccountMoveQueryModel")
    void testLoadAccountMoveQueryModel() {
        JdbcQueryModel queryModel = getQueryModel("OdooAccountMoveQueryModel");
        assertNotNull(queryModel, "OdooAccountMoveQueryModel 加载失败");
        assertEquals("OdooAccountMoveQueryModel", queryModel.getName());
        assertNotNull(queryModel.getColumnGroups(), "列组定义为空");
        log.info("OdooAccountMoveQueryModel 加载成功: 列组数={}", queryModel.getColumnGroups().size());
    }

    @Test
    @Order(23)
    @DisplayName("加载 OdooStockPickingQueryModel")
    void testLoadStockPickingQueryModel() {
        JdbcQueryModel queryModel = getQueryModel("OdooStockPickingQueryModel");
        assertNotNull(queryModel, "OdooStockPickingQueryModel 加载失败");
        assertEquals("OdooStockPickingQueryModel", queryModel.getName());
        assertNotNull(queryModel.getColumnGroups(), "列组定义为空");
        log.info("OdooStockPickingQueryModel 加载成功: 列组数={}", queryModel.getColumnGroups().size());
    }

    @Test
    @Order(24)
    @DisplayName("加载 OdooHrEmployeeQueryModel")
    void testLoadHrEmployeeQueryModel() {
        JdbcQueryModel queryModel = getQueryModel("OdooHrEmployeeQueryModel");
        assertNotNull(queryModel, "OdooHrEmployeeQueryModel 加载失败");
        assertEquals("OdooHrEmployeeQueryModel", queryModel.getName());
        assertNotNull(queryModel.getColumnGroups(), "列组定义为空");
        log.info("OdooHrEmployeeQueryModel 加载成功: 列组数={}", queryModel.getColumnGroups().size());
    }

    @Test
    @Order(25)
    @DisplayName("加载 OdooResPartnerQueryModel")
    void testLoadResPartnerQueryModel() {
        JdbcQueryModel queryModel = getQueryModel("OdooResPartnerQueryModel");
        assertNotNull(queryModel, "OdooResPartnerQueryModel 加载失败");
        assertEquals("OdooResPartnerQueryModel", queryModel.getName());
        assertNotNull(queryModel.getColumnGroups(), "列组定义为空");
        log.info("OdooResPartnerQueryModel 加载成功: 列组数={}", queryModel.getColumnGroups().size());
    }

    // ==========================================
    // 模型维度验证测试
    // ==========================================

    @Test
    @Order(30)
    @DisplayName("验证 SaleOrder 维度详情")
    void testSaleOrderDimensions() {
        TableModel model = tableModelLoaderManager.load("OdooSaleOrderModel");
        assertNotNull(model);

        model.getDimensions().forEach(dim -> {
            assertNotNull(dim.getName(), "维度名称不能为空");
            assertNotNull(dim.getForeignKey(), "维度外键不能为空");

            String tableName = dim.getQueryObject() != null ? dim.getQueryObject().getName() : "(内嵌)";
            log.info("  维度 {}: 表={}, FK={}", dim.getName(), tableName, dim.getForeignKey());
        });
    }

    @Test
    @Order(31)
    @DisplayName("验证 SaleOrder 度量详情")
    void testSaleOrderMeasures() {
        TableModel model = tableModelLoaderManager.load("OdooSaleOrderModel");
        assertNotNull(model);

        model.getMeasures().forEach(measure -> {
            assertNotNull(measure.getName(), "度量名称不能为空");
            assertNotNull(measure.getCaption(), "度量描述不能为空");
            log.info("  度量 {}: 类型={}, 聚合={}",
                measure.getName(), measure.getType(), measure.getAggregation());
        });
    }

    @Test
    @Order(32)
    @DisplayName("验证 SaleOrder dateOrder 无表维度属性")
    void testSaleOrderDateOrderTablelessDimensionProperties() {
        TableModel model = tableModelLoaderManager.load("OdooSaleOrderModel");
        assertNotNull(model);

        DbDimension dateOrder = model.findJdbcDimensionByName("dateOrder");
        assertNotNull(dateOrder, "dateOrder 维度必须存在");
        assertNull(dateOrder.getQueryObject(), "dateOrder 是 self/tableless 维度，不应生成维表 QueryObject");
        assertEquals("date_order", dateOrder.getForeignKey());

        assertNull(model.findJdbcPropertyByName("dateOrder"), "dateOrder 不应再作为顶层普通属性注册");
        DbColumn year = model.findJdbcColumnByName("dateOrder$year");
        DbColumn month = model.findJdbcColumnByName("dateOrder$month");
        DbColumn yearMonth = model.findJdbcColumnByName("dateOrder$yearMonth");
        assertNotNull(year, "dateOrder$year 必须可解析");
        assertNotNull(month, "dateOrder$month 必须可解析");
        assertNotNull(yearMonth, "dateOrder$yearMonth 必须可解析");

        assertTrue(year.getQueryObject().isRootEqual(model.getQueryObject()), "dateOrder$year 应绑定主表");
        assertTrue(month.getQueryObject().isRootEqual(model.getQueryObject()), "dateOrder$month 应绑定主表");
        assertTrue(yearMonth.getQueryObject().isRootEqual(model.getQueryObject()), "dateOrder$yearMonth 应绑定主表");

        String alias = model.getQueryObject().getAlias();
        String yearDeclare = year.getDeclare(appCtx, alias);
        String monthDeclare = month.getDeclare(appCtx, alias);
        String yearMonthDeclare = yearMonth.getDeclare(appCtx, alias);

        assertTrue(yearDeclare.contains("date_order"), "year 表达式应引用主表 date_order");
        assertTrue(monthDeclare.contains("date_order"), "month 表达式应引用主表 date_order");
        assertTrue(yearMonthDeclare.contains("date_order"), "yearMonth 表达式应引用主表 date_order");
        assertFalse(yearDeclare.toLowerCase().contains("dim_date"), "year 表达式不应引用 dim_date");
        assertFalse(monthDeclare.toLowerCase().contains("dim_date"), "month 表达式不应引用 dim_date");
        assertFalse(yearMonthDeclare.toLowerCase().contains("dim_date"), "yearMonth 表达式不应引用 dim_date");
    }

    @Test
    @Order(33)
    @DisplayName("验证 SaleOrder dateOrder grain 字段元数据与查询 SQL")
    void testSaleOrderDateOrderTablelessDimensionSemanticQuery() {
        SemanticMetadataRequest metadataRequest = new SemanticMetadataRequest();
        metadataRequest.setQmModels(List.of("OdooSaleOrderQueryModel"));

        SemanticMetadataResponse metadata = semanticServiceV3.getMetadata(
                metadataRequest, "json", SemanticRequestContext.empty());
        assertNotNull(metadata);
        assertNotNull(metadata.getData());

        @SuppressWarnings("unchecked")
        Map<String, Object> fields = (Map<String, Object>) metadata.getData().get("fields");
        assertNotNull(fields);
        assertTrue(fields.containsKey("dateOrder"), "metadata 应暴露 dateOrder 时间维度根字段");
        assertTrue(fields.containsKey("dateOrder$year"), "metadata 应暴露 dateOrder$year");
        assertTrue(fields.containsKey("dateOrder$month"), "metadata 应暴露 dateOrder$month");
        assertTrue(fields.containsKey("dateOrder$yearMonth"), "metadata 应暴露 dateOrder$yearMonth");

        SemanticQueryRequest request = new SemanticQueryRequest();
        request.setColumns(List.of("dateOrder$year", "dateOrder$month", "amountTotal"));
        request.setGroupBy(List.of(
                new SemanticQueryRequest.GroupByItem("dateOrder$year", null),
                new SemanticQueryRequest.GroupByItem("dateOrder$month", null)
        ));
        SemanticQueryRequest.OrderItem orderItem = new SemanticQueryRequest.OrderItem();
        orderItem.setField("dateOrder$year");
        orderItem.setDir("ASC");
        request.setOrderBy(List.of(orderItem));

        SemanticQueryRequest.SliceItem slice = new SemanticQueryRequest.SliceItem();
        slice.setField("dateOrder");
        slice.setOp(">=");
        slice.setValue("2024-01-01");
        request.setSlice(List.of(slice));
        request.setLimit(10);

        SemanticQueryResponse response = semanticQueryServiceV3.queryModel(
                "OdooSaleOrderQueryModel", request, "execute", SemanticRequestContext.empty());
        assertNotNull(response);
        assertNotNull(response.getDebug());
        assertNotNull(response.getDebug().getExtra());

        String sql = String.valueOf(response.getDebug().getExtra().get("sql")).toLowerCase();
        assertTrue(sql.contains("date_order"), "SQL 应引用主表 date_order");
        assertTrue(sql.contains("group by"), "SQL 应按 dateOrder grain 分组");
        assertFalse(sql.contains("dim_date"), "SQL 不应引用 dim_date");
        assertFalse(sql.contains("join dim_date"), "SQL 不应 JOIN dim_date");
    }

    @Test
    @Order(34)
    @DisplayName("验证 SaleOrder dateOrder self grain 字段可用于过滤")
    void testSaleOrderDateOrderTablelessDimensionPropertySlice() {
        SemanticQueryRequest request = new SemanticQueryRequest();
        request.setColumns(List.of("dateOrder$yearMonth", "amountTotal"));
        request.setGroupBy(List.of(
                new SemanticQueryRequest.GroupByItem("dateOrder$yearMonth", null)
        ));

        SemanticQueryRequest.SliceItem yearSlice = new SemanticQueryRequest.SliceItem();
        yearSlice.setField("dateOrder$year");
        yearSlice.setOp("=");
        yearSlice.setValue(2024);

        SemanticQueryRequest.SliceItem monthSlice = new SemanticQueryRequest.SliceItem();
        monthSlice.setField("dateOrder$month");
        monthSlice.setOp("in");
        monthSlice.setValue(List.of(1, 2));

        SemanticQueryRequest.SliceItem yearMonthSlice = new SemanticQueryRequest.SliceItem();
        yearMonthSlice.setField("dateOrder$yearMonth");
        yearMonthSlice.setOp("=");
        yearMonthSlice.setValue("2024-01");

        request.setSlice(List.of(yearSlice, monthSlice, yearMonthSlice));
        request.setLimit(10);

        SemanticQueryResponse response = semanticQueryServiceV3.queryModel(
                "OdooSaleOrderQueryModel", request, "execute", SemanticRequestContext.empty());
        assertNotNull(response);
        assertNotNull(response.getDebug());
        assertNotNull(response.getDebug().getExtra());

        String sql = String.valueOf(response.getDebug().getExtra().get("sql")).toLowerCase();
        assertTrue(sql.contains("where"), "SQL 应包含 dateOrder grain 过滤条件");
        assertTrue(sql.contains("date_order"), "SQL 应使用主表 date_order 生成 grain 过滤表达式");
        assertTrue(sql.contains("amount_total"), "SQL 应保留普通度量查询");
        assertFalse(sql.contains("dim_date"), "self grain 过滤不应引用 dim_date");
        assertFalse(sql.contains("join dim_date"), "self grain 过滤不应 JOIN dim_date");
    }

    // ==========================================
    // 数据验证测试（SQLite 样本数据）
    // ==========================================

    @Test
    @Order(40)
    @DisplayName("验证 Odoo 样本数据已加载")
    void testSampleDataLoaded() {
        assertTrue(getTableCount("sale_order") > 0, "sale_order 表无数据");
        assertTrue(getTableCount("purchase_order") > 0, "purchase_order 表无数据");
        assertTrue(getTableCount("account_move") > 0, "account_move 表无数据");
        assertTrue(getTableCount("stock_picking") > 0, "stock_picking 表无数据");
        assertTrue(getTableCount("hr_employee") > 0, "hr_employee 表无数据");
        assertTrue(getTableCount("res_partner") > 0, "res_partner 表无数据");
        assertTrue(getTableCount("res_company") > 0, "res_company 表无数据");

        log.info("Odoo 样本数据: sale_order={}, purchase_order={}, account_move={}, stock_picking={}, hr_employee={}, res_partner={}",
            getTableCount("sale_order"),
            getTableCount("purchase_order"),
            getTableCount("account_move"),
            getTableCount("stock_picking"),
            getTableCount("hr_employee"),
            getTableCount("res_partner"));
    }
}
