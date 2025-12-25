package com.foggyframework.dataset.jdbc.model.ecommerce;

import com.foggyframework.dataset.jdbc.model.spi.JdbcModel;
import com.foggyframework.dataset.jdbc.model.spi.JdbcQueryModel;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 模型加载测试
 *
 * <p>验证TM和QM模型文件能正确加载</p>
 *
 * @author foggy-dataset
 * @since 1.0.0
 */
@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("模型加载测试")
class ModelLoadingTest extends EcommerceTestSupport {

    // ==========================================
    // TM 模型加载测试
    // ==========================================

    @Test
    @Order(1)
    @DisplayName("加载日期维度模型")
    void testLoadDimDateModel() {
        JdbcModel model = jdbcModelLoader.load("DimDateModel");
        assertNotNull(model, "DimDateModel 加载失败");
        assertEquals("DimDateModel", model.getName());
        assertEquals("dim_date", model.getTableName());
        assertEquals("date_key", model.getIdColumn());
        log.info("DimDateModel 加载成功: 属性数={}", model.getProperties().size());
    }

    @Test
    @Order(2)
    @DisplayName("加载商品维度模型")
    void testLoadDimProductModel() {
        JdbcModel model = jdbcModelLoader.load("DimProductModel");
        assertNotNull(model, "DimProductModel 加载失败");
        assertEquals("DimProductModel", model.getName());
        assertEquals("dim_product", model.getTableName());
        log.info("DimProductModel 加载成功: 属性数={}", model.getProperties().size());
    }

    @Test
    @Order(3)
    @DisplayName("加载客户维度模型")
    void testLoadDimCustomerModel() {
        JdbcModel model = jdbcModelLoader.load("DimCustomerModel");
        assertNotNull(model, "DimCustomerModel 加载失败");
        assertEquals("DimCustomerModel", model.getName());
        assertEquals("dim_customer", model.getTableName());
        log.info("DimCustomerModel 加载成功: 属性数={}", model.getProperties().size());
    }

    @Test
    @Order(4)
    @DisplayName("加载门店维度模型")
    void testLoadDimStoreModel() {
        JdbcModel model = jdbcModelLoader.load("DimStoreModel");
        assertNotNull(model, "DimStoreModel 加载失败");
        assertEquals("DimStoreModel", model.getName());
        assertEquals("dim_store", model.getTableName());
        log.info("DimStoreModel 加载成功: 属性数={}", model.getProperties().size());
    }

    @Test
    @Order(5)
    @DisplayName("加载渠道维度模型")
    void testLoadDimChannelModel() {
        JdbcModel model = jdbcModelLoader.load("DimChannelModel");
        assertNotNull(model, "DimChannelModel 加载失败");
        assertEquals("DimChannelModel", model.getName());
        assertEquals("dim_channel", model.getTableName());
        log.info("DimChannelModel 加载成功: 属性数={}", model.getProperties().size());
    }

    @Test
    @Order(6)
    @DisplayName("加载促销维度模型")
    void testLoadDimPromotionModel() {
        JdbcModel model = jdbcModelLoader.load("DimPromotionModel");
        assertNotNull(model, "DimPromotionModel 加载失败");
        assertEquals("DimPromotionModel", model.getName());
        assertEquals("dim_promotion", model.getTableName());
        log.info("DimPromotionModel 加载成功: 属性数={}", model.getProperties().size());
    }

    @Test
    @Order(10)
    @DisplayName("加载订单事实表模型")
    void testLoadFactOrderModel() {
        JdbcModel model = jdbcModelLoader.load("FactOrderModel");
        assertNotNull(model, "FactOrderModel 加载失败");
        assertEquals("FactOrderModel", model.getName());
        assertEquals("fact_order", model.getTableName());

        // 验证维度定义
        assertNotNull(model.getDimensions(), "维度定义为空");
        assertTrue(model.getDimensions().size() >= 5, "维度数量不足");
        log.info("FactOrderModel 加载成功: 维度数={}, 属性数={}, 度量数={}",
            model.getDimensions().size(),
            model.getProperties().size(),
            model.getMeasures().size());
    }

    @Test
    @Order(11)
    @DisplayName("加载销售事实表模型")
    void testLoadFactSalesModel() {
        JdbcModel model = jdbcModelLoader.load("FactSalesModel");
        assertNotNull(model, "FactSalesModel 加载失败");
        assertEquals("FactSalesModel", model.getName());
        assertEquals("fact_sales", model.getTableName());

        // 验证维度定义
        assertNotNull(model.getDimensions(), "维度定义为空");
        assertTrue(model.getDimensions().size() >= 6, "维度数量不足");

        // 验证度量定义
        assertNotNull(model.getMeasures(), "度量定义为空");
        assertTrue(model.getMeasures().size() >= 5, "度量数量不足");
        log.info("FactSalesModel 加载成功: 维度数={}, 属性数={}, 度量数={}",
            model.getDimensions().size(),
            model.getProperties().size(),
            model.getMeasures().size());
    }

    @Test
    @Order(12)
    @DisplayName("加载支付事实表模型")
    void testLoadFactPaymentModel() {
        JdbcModel model = jdbcModelLoader.load("FactPaymentModel");
        assertNotNull(model, "FactPaymentModel 加载失败");
        assertEquals("FactPaymentModel", model.getName());
        assertEquals("fact_payment", model.getTableName());
        log.info("FactPaymentModel 加载成功: 维度数={}, 度量数={}",
            model.getDimensions().size(),
            model.getMeasures().size());
    }

    @Test
    @Order(13)
    @DisplayName("加载退货事实表模型")
    void testLoadFactReturnModel() {
        JdbcModel model = jdbcModelLoader.load("FactReturnModel");
        assertNotNull(model, "FactReturnModel 加载失败");
        assertEquals("FactReturnModel", model.getName());
        assertEquals("fact_return", model.getTableName());
        log.info("FactReturnModel 加载成功: 维度数={}, 度量数={}",
            model.getDimensions().size(),
            model.getMeasures().size());
    }

    @Test
    @Order(14)
    @DisplayName("加载库存快照事实表模型")
    void testLoadFactInventorySnapshotModel() {
        JdbcModel model = jdbcModelLoader.load("FactInventorySnapshotModel");
        assertNotNull(model, "FactInventorySnapshotModel 加载失败");
        assertEquals("FactInventorySnapshotModel", model.getName());
        assertEquals("fact_inventory_snapshot", model.getTableName());
        log.info("FactInventorySnapshotModel 加载成功: 维度数={}, 度量数={}",
            model.getDimensions().size(),
            model.getMeasures().size());
    }

    // ==========================================
    // QM 查询模型加载测试
    // ==========================================

    @Test
    @Order(20)
    @DisplayName("加载订单查询模型")
    void testLoadFactOrderQueryModel() {
        JdbcQueryModel queryModel = getQueryModel("FactOrderQueryModel");
        assertNotNull(queryModel, "FactOrderQueryModel 加载失败");
        assertEquals("FactOrderQueryModel", queryModel.getName());
        assertNotNull(queryModel.getColumnGroups(), "列组定义为空");
        assertTrue(queryModel.getColumnGroups().size() >= 5, "列组数量不足");
        log.info("FactOrderQueryModel 加载成功: 列组数={}", queryModel.getColumnGroups().size());
    }

    @Test
    @Order(21)
    @DisplayName("加载销售查询模型")
    void testLoadFactSalesQueryModel() {
        JdbcQueryModel queryModel = getQueryModel("FactSalesQueryModel");
        assertNotNull(queryModel, "FactSalesQueryModel 加载失败");
        assertEquals("FactSalesQueryModel", queryModel.getName());
        assertNotNull(queryModel.getColumnGroups(), "列组定义为空");
        log.info("FactSalesQueryModel 加载成功: 列组数={}", queryModel.getColumnGroups().size());
    }

    @Test
    @Order(22)
    @DisplayName("加载支付查询模型")
    void testLoadFactPaymentQueryModel() {
        JdbcQueryModel queryModel = getQueryModel("FactPaymentQueryModel");
        assertNotNull(queryModel, "FactPaymentQueryModel 加载失败");
        assertEquals("FactPaymentQueryModel", queryModel.getName());
        log.info("FactPaymentQueryModel 加载成功: 列组数={}", queryModel.getColumnGroups().size());
    }

    @Test
    @Order(23)
    @DisplayName("加载退货查询模型")
    void testLoadFactReturnQueryModel() {
        JdbcQueryModel queryModel = getQueryModel("FactReturnQueryModel");
        assertNotNull(queryModel, "FactReturnQueryModel 加载失败");
        assertEquals("FactReturnQueryModel", queryModel.getName());
        log.info("FactReturnQueryModel 加载成功: 列组数={}", queryModel.getColumnGroups().size());
    }

    @Test
    @Order(24)
    @DisplayName("加载库存快照查询模型")
    void testLoadFactInventorySnapshotQueryModel() {
        JdbcQueryModel queryModel = getQueryModel("FactInventorySnapshotQueryModel");
        assertNotNull(queryModel, "FactInventorySnapshotQueryModel 加载失败");
        assertEquals("FactInventorySnapshotQueryModel", queryModel.getName());
        log.info("FactInventorySnapshotQueryModel 加载成功: 列组数={}", queryModel.getColumnGroups().size());
    }

    @Test
    @Order(30)
    @DisplayName("加载订单-支付联合查询模型")
    void testLoadOrderPaymentJoinQueryModel() {
        JdbcQueryModel queryModel = getQueryModel("OrderPaymentJoinQueryModel");
        assertNotNull(queryModel, "OrderPaymentJoinQueryModel 加载失败");
        assertEquals("OrderPaymentJoinQueryModel", queryModel.getName());
        log.info("OrderPaymentJoinQueryModel 加载成功: 列组数={}", queryModel.getColumnGroups().size());
    }

    @Test
    @Order(31)
    @DisplayName("加载销售-退货联合查询模型")
    void testLoadSalesReturnJoinQueryModel() {
        JdbcQueryModel queryModel = getQueryModel("SalesReturnJoinQueryModel");
        assertNotNull(queryModel, "SalesReturnJoinQueryModel 加载失败");
        assertEquals("SalesReturnJoinQueryModel", queryModel.getName());
        log.info("SalesReturnJoinQueryModel 加载成功: 列组数={}", queryModel.getColumnGroups().size());
    }

    // ==========================================
    // 模型验证测试
    // ==========================================

    @Test
    @Order(40)
    @DisplayName("验证订单模型维度配置")
    void testFactOrderModelDimensions() {
        JdbcModel model = jdbcModelLoader.load("FactOrderModel");
        assertNotNull(model, "FactOrderModel 加载失败");

        // 验证各维度配置
        model.getDimensions().forEach(dim -> {
            assertNotNull(dim.getName(), "维度名称不能为空");
            assertNotNull(dim.getForeignKey(), "维度外键不能为空");

            // 获取维度表名（如果有独立维表）
            String tableName = dim.getQueryObject() != null ? dim.getQueryObject().getName() : "(内嵌维度)";
            // 获取主键列（如果有）
            String primaryKey = dim.getPrimaryKeyJdbcColumn() != null ? dim.getPrimaryKeyJdbcColumn().getName() : "(无主键)";

            log.info("维度 {}: 表={}, 外键={}, 主键={}",
                dim.getName(),
                tableName,
                dim.getForeignKey(),
                primaryKey);
        });
    }

    @Test
    @Order(41)
    @DisplayName("验证销售模型度量配置")
    void testFactSalesModelMeasures() {
        JdbcModel model = jdbcModelLoader.load("FactSalesModel");
        assertNotNull(model, "FactSalesModel 加载失败");

        // 验证度量配置
        model.getMeasures().forEach(measure -> {
            assertNotNull(measure.getName(), "度量名称不能为空");
            assertNotNull(measure.getCaption(), "度量描述不能为空");
            assertNotNull(measure.getJdbcColumn(), "度量字段不能为空");
            log.info("度量 {}: 类型={}, 聚合={}",
                measure.getName(),
                measure.getType(),
                measure.getAggregation());
        });
    }
}
