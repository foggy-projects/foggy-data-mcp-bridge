package com.foggyframework.dataset.db.model.odoo;

import com.foggyframework.dataset.db.model.ecommerce.EcommerceTestSupport;
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
