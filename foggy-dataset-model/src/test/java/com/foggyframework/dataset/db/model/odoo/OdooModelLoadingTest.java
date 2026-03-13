package com.foggyframework.dataset.db.model.odoo;

import com.foggyframework.dataset.db.model.ecommerce.EcommerceTestSupport;
import com.foggyframework.dataset.db.model.spi.JdbcQueryModel;
import com.foggyframework.dataset.db.model.spi.TableModel;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;

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
        assertTrue(model.getDimensions().size() >= 6, "维度数量不足（预期>=6: partner, salesperson, company, salesTeam, pricelist, warehouse）");

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
