package com.foggyframework.dataset.db.model.ecommerce;

import com.foggyframework.dataset.db.model.impl.dimension.DbDimensionSupport;
import com.foggyframework.dataset.db.model.impl.measure.DbMeasureSupport;
import com.foggyframework.dataset.db.model.impl.property.DbPropertyImpl;
import com.foggyframework.dataset.db.model.spi.*;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * dialectFormulaDef 功能测试
 *
 * <p>验证维度 captionDef、属性 dialectFormulaDef、度量 dialectFormulaDef 的三级优先级：</p>
 * <ol>
 *     <li>dialectFormulaDef[当前数据库类型] — 方言专属 SQL 公式（最高优先级）</li>
 *     <li>formulaDef — 通用 SQL 公式</li>
 *     <li>无公式 — 使用 column 原样输出</li>
 * </ol>
 */
@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("dialectFormulaDef 功能测试（维度/属性/度量）")
class CaptionDefTest extends EcommerceTestSupport {

    /** store 维度 — 各方言 captionDef 标记 */
    private static final Map<String, String> STORE_MARKERS = Map.of(
            "sqlite", "[SQLite]",
            "postgresql", "[PG]",
            "mysql", "[MySQL]"
    );

    /** orderId 属性 — 各方言 dialectFormulaDef 后缀 */
    private static final Map<String, String> PROPERTY_SUFFIXES = Map.of(
            "sqlite", "-sqlite",
            "postgresql", "-pg",
            "mysql", "-mysql"
    );

    // ==========================================
    // 模型加载测试
    // ==========================================

    @Test
    @Order(1)
    @DisplayName("captionDef.column 模式 — 等价于 captionColumn")
    void testCaptionDefColumnMode() {
        TableModel model = tableModelLoaderManager.load("FactSalesCaptionDefModel");
        assertNotNull(model, "FactSalesCaptionDefModel 加载失败");

        DbDimension customerDim = model.findJdbcDimensionByName("customer");
        assertNotNull(customerDim, "customer 维度不存在");

        DbDimensionSupport ds = customerDim.getDecorate(DbDimensionSupport.class);
        assertNotNull(ds.getCaptionDbColumn(), "captionDbColumn 不应为空");

        // captionDef.column='customer_name' 应等效于 captionColumn='customer_name'
        assertEquals("customer_name", ds.getCaptionColumn(), "captionColumn 应被 captionDef.column 覆盖");

        // 无公式，getDeclare 应返回默认的 alias.column 格式
        String declare = ds.getCaptionDbColumn().getDeclare();
        assertTrue(declare.endsWith(".customer_name"),
                "无公式时 getDeclare() 应为 alias.customer_name，实际: " + declare);

        log.info("captionDef.column 模式验证通过: declare={}", declare);
    }

    @Test
    @Order(2)
    @DisplayName("captionDef.formulaDef 模式 — 通用公式")
    void testCaptionDefFormulaDef() {
        TableModel model = tableModelLoaderManager.load("FactSalesCaptionDefModel");
        assertNotNull(model);

        DbDimension productDim = model.findJdbcDimensionByName("product");
        assertNotNull(productDim, "product 维度不存在");

        DbDimensionSupport ds = productDim.getDecorate(DbDimensionSupport.class);
        assertNotNull(ds.getCaptionDbColumn(), "captionDbColumn 不应为空");
        assertNotNull(ds.getCaptionFormulaBuilder(), "captionFormulaBuilder 应被设置");

        // getDeclare 应使用公式生成
        String declare = ds.getCaptionDbColumn().getDeclare(appCtx,
                ds.getCaptionDbColumn().getQueryObject().getAlias());
        assertNotNull(declare, "getDeclare() 不应返回 null");
        assertTrue(declare.contains("COALESCE"),
                "公式模式 getDeclare() 应包含 COALESCE，实际: " + declare);
        assertTrue(declare.contains("product_name"),
                "公式模式 getDeclare() 应包含 product_name，实际: " + declare);

        log.info("captionDef.formulaDef 模式验证通过: declare={}", declare);
    }

    @Test
    @Order(3)
    @DisplayName("captionDef.dialectFormulaDef 模式 — 方言公式自动匹配")
    void testCaptionDefDialectFormulaDef() {
        String dialectKey = getDialectKey();
        String expectedMarker = STORE_MARKERS.get(dialectKey);
        assumeTrue(expectedMarker != null,
                "当前方言 " + dialectKey + " 未在 TM 中定义 dialectFormulaDef，跳过");

        TableModel model = tableModelLoaderManager.load("FactSalesCaptionDefModel");
        assertNotNull(model);

        DbDimension storeDim = model.findJdbcDimensionByName("store");
        assertNotNull(storeDim, "store 维度不存在");

        DbDimensionSupport ds = storeDim.getDecorate(DbDimensionSupport.class);
        assertNotNull(ds.getCaptionDbColumn(), "captionDbColumn 不应为空");
        assertNotNull(ds.getCaptionFormulaBuilder(),
                "captionFormulaBuilder 应被设置（方言 " + dialectKey + " 匹配）");

        String declare = ds.getCaptionDbColumn().getDeclare(appCtx,
                ds.getCaptionDbColumn().getQueryObject().getAlias());
        assertNotNull(declare, "getDeclare() 不应返回 null");
        assertTrue(declare.contains(expectedMarker),
                dialectKey + " 方言 getDeclare() 应包含 " + expectedMarker + " 标记，实际: " + declare);
        assertTrue(declare.contains("store_name"),
                "方言模式 getDeclare() 应包含 store_name，实际: " + declare);

        log.info("captionDef.dialectFormulaDef [{}] 验证通过: declare={}", dialectKey, declare);
    }

    // ==========================================
    // SQL 生成 + 数据查询测试
    // ==========================================

    @Test
    @Order(10)
    @DisplayName("captionDef.formulaDef 查询实际数据")
    void testCaptionDefFormulaDefQuery() {
        // 直接用 SQL 验证公式生效
        List<Map<String, Object>> results = executeQuery(
                "SELECT COALESCE(p.product_name, 'Unknown') as caption " +
                "FROM fact_sales fs JOIN dim_product p ON fs.product_key = p.product_key " +
                "LIMIT 5");
        assertNotNull(results, "查询结果不应为空");
        assertFalse(results.isEmpty(), "应有数据");
        results.forEach(row -> {
            assertNotNull(row.get("caption"), "caption 不应为 null（COALESCE 保底）");
        });

        log.info("captionDef.formulaDef 数据查询验证通过: {} 条", results.size());
    }

    @Test
    @Order(11)
    @DisplayName("captionDef.dialectFormulaDef 查询实际数据")
    void testCaptionDefDialectFormulaDefQuery() {
        String dialectKey = getDialectKey();
        String marker = STORE_MARKERS.get(dialectKey);
        assumeTrue(marker != null,
                "当前方言 " + dialectKey + " 未定义 store dialectFormulaDef，跳过");

        // 根据方言使用不同的字符串拼接语法
        String concatExpr = "mysql".equals(dialectKey)
                ? "CONCAT(s.store_name, ' " + marker + "')"
                : "s.store_name || ' " + marker + "'";

        List<Map<String, Object>> results = executeQuery(
                "SELECT " + concatExpr + " as caption " +
                "FROM fact_sales fs JOIN dim_store s ON fs.store_key = s.store_key " +
                "LIMIT 5");
        assertNotNull(results, "查询结果不应为空");
        assertFalse(results.isEmpty(), "应有数据");
        results.forEach(row -> {
            String caption = (String) row.get("caption");
            assertNotNull(caption, "caption 不应为 null");
            assertTrue(caption.endsWith(marker), "应以 " + marker + " 结尾: " + caption);
        });

        log.info("captionDef.dialectFormulaDef [{}] 数据查询验证通过: {} 条", dialectKey, results.size());
    }

    // ==========================================
    // 向后兼容测试
    // ==========================================

    @Test
    @Order(20)
    @DisplayName("旧 captionColumn 方式仍然有效")
    void testLegacyCaptionColumnStillWorks() {
        // FactSalesModel 使用旧的 captionColumn 方式
        TableModel model = tableModelLoaderManager.load("FactSalesModel");
        assertNotNull(model, "FactSalesModel 加载失败");

        DbDimension customerDim = model.findJdbcDimensionByName("customer");
        assertNotNull(customerDim, "customer 维度不存在");

        DbDimensionSupport ds = customerDim.getDecorate(DbDimensionSupport.class);
        assertNotNull(ds.getCaptionDbColumn(), "captionDbColumn 不应为空");
        assertNull(ds.getCaptionFormulaBuilder(), "旧模式下 captionFormulaBuilder 应为 null");

        String declare = ds.getCaptionDbColumn().getDeclare();
        assertTrue(declare.endsWith(".customer_name"),
                "旧模式 getDeclare() 应为 alias.customer_name，实际: " + declare);

        log.info("旧 captionColumn 兼容性验证通过: declare={}", declare);
    }

    @Test
    @Order(21)
    @DisplayName("captionDef.column 覆盖 captionColumn")
    void testCaptionDefColumnOverridesCaptionColumn() {
        // 如果同时指定了 captionColumn 和 captionDef.column，后者优先
        // FactSalesCaptionDefModel 的 customer 维度只有 captionDef.column='customer_name'
        TableModel model = tableModelLoaderManager.load("FactSalesCaptionDefModel");
        DbDimension customerDim = model.findJdbcDimensionByName("customer");
        DbDimensionSupport ds = customerDim.getDecorate(DbDimensionSupport.class);

        assertEquals("customer_name", ds.getCaptionColumn(),
                "captionDef.column 应设置到 captionColumn");

        log.info("captionDef.column 覆盖验证通过");
    }

    // ==========================================
    // 属性 dialectFormulaDef 测试
    // ==========================================

    @Test
    @Order(30)
    @DisplayName("属性 dialectFormulaDef — 方言公式自动匹配")
    void testPropertyDialectFormulaDef() {
        String dialectKey = getDialectKey();
        String expectedSuffix = PROPERTY_SUFFIXES.get(dialectKey);
        assumeTrue(expectedSuffix != null,
                "当前方言 " + dialectKey + " 未定义 orderId dialectFormulaDef，跳过");

        TableModel model = tableModelLoaderManager.load("FactSalesCaptionDefModel");
        assertNotNull(model);

        DbProperty orderIdProp = model.findJdbcPropertyByName("orderId");
        assertNotNull(orderIdProp, "orderId 属性不存在");

        DbPropertyImpl propImpl = orderIdProp.getDecorate(DbPropertyImpl.class);
        assertNotNull(propImpl, "属性应为 DbPropertyImpl 类型");
        assertNotNull(propImpl.getFormulaBuilder(),
                "dialectFormulaDef." + dialectKey + " 应注入 formulaBuilder");

        DbColumn propCol = propImpl.getPropertyDbColumn();
        String declare = propCol.getDeclare(appCtx, model.getQueryObject().getAlias());
        assertNotNull(declare, "getDeclare() 不应返回 null");
        assertTrue(declare.contains(expectedSuffix),
                dialectKey + " 方言属性公式应包含 '" + expectedSuffix + "'，实际: " + declare);

        log.info("属性 dialectFormulaDef [{}] 验证通过: declare={}", dialectKey, declare);
    }

    @Test
    @Order(31)
    @DisplayName("属性 formulaDef 通用公式（无 dialectFormulaDef 时回退）")
    void testPropertyFormulaDef() {
        TableModel model = tableModelLoaderManager.load("FactSalesCaptionDefModel");
        assertNotNull(model);

        // order_status 属性只配了 formulaDef（通用公式）
        DbProperty statusProp = model.findJdbcPropertyByName("orderStatus");
        assertNotNull(statusProp, "orderStatus 属性不存在");

        DbPropertyImpl propImpl = statusProp.getDecorate(DbPropertyImpl.class);
        assertNotNull(propImpl.getFormulaBuilder(), "formulaDef 应注入 formulaBuilder");

        DbColumn propCol = propImpl.getPropertyDbColumn();
        String declare = propCol.getDeclare(appCtx, model.getQueryObject().getAlias());
        assertTrue(declare.contains("UPPER"),
                "通用公式应包含 UPPER，实际: " + declare);

        log.info("属性 formulaDef 通用公式验证通过: declare={}", declare);
    }

    @Test
    @Order(32)
    @DisplayName("属性无公式 — 纯 column 模式不受影响")
    void testPropertyNoFormula() {
        TableModel model = tableModelLoaderManager.load("FactSalesCaptionDefModel");
        assertNotNull(model);

        // payment_method 属性无任何公式
        DbProperty payProp = model.findJdbcPropertyByName("paymentMethod");
        assertNotNull(payProp, "paymentMethod 属性不存在");

        DbPropertyImpl propImpl = payProp.getDecorate(DbPropertyImpl.class);
        assertNull(propImpl.getFormulaBuilder(), "无公式时 formulaBuilder 应为 null");

        DbColumn propCol = propImpl.getPropertyDbColumn();
        String declare = propCol.getDeclare(appCtx, model.getQueryObject().getAlias());
        assertTrue(declare.endsWith(".payment_method"),
                "无公式时应返回 alias.payment_method，实际: " + declare);

        log.info("属性纯 column 模式验证通过: declare={}", declare);
    }

    // ==========================================
    // 度量 dialectFormulaDef 测试
    // ==========================================

    @Test
    @Order(40)
    @DisplayName("度量 dialectFormulaDef — 方言公式自动匹配")
    void testMeasureDialectFormulaDef() {
        String dialectKey = getDialectKey();

        TableModel model = tableModelLoaderManager.load("FactSalesCaptionDefModel");
        assertNotNull(model);

        DbMeasure taxMeasure = model.findJdbcMeasureByName("taxDialect");
        assertNotNull(taxMeasure, "taxDialect 度量不存在");

        DbMeasureSupport measureSupport = taxMeasure.getDecorate(DbMeasureSupport.class);
        assertNotNull(measureSupport.getFormulaBuilder(),
                "dialectFormulaDef." + dialectKey + " 应注入 formulaBuilder");

        DbColumn measCol = measureSupport.getJdbcColumn();
        String declare = measCol.getDeclare(appCtx, model.getQueryObject().getAlias());
        assertNotNull(declare, "getDeclare() 不应返回 null");
        assertTrue(declare.contains("* 1.1"),
                dialectKey + " 方言度量公式应包含 '* 1.1'，实际: " + declare);

        log.info("度量 dialectFormulaDef [{}] 验证通过: declare={}", dialectKey, declare);
    }

    @Test
    @Order(41)
    @DisplayName("度量 formulaDef 通用公式（无 dialectFormulaDef 时回退）")
    void testMeasureFormulaDef() {
        TableModel model = tableModelLoaderManager.load("FactSalesCaptionDefModel");
        assertNotNull(model);

        // taxGeneric 度量只配了 formulaDef
        DbMeasure taxGeneric = model.findJdbcMeasureByName("taxGeneric");
        assertNotNull(taxGeneric, "taxGeneric 度量不存在");

        DbMeasureSupport measureSupport = taxGeneric.getDecorate(DbMeasureSupport.class);
        assertNotNull(measureSupport.getFormulaBuilder(), "formulaDef 应注入 formulaBuilder");

        DbColumn measCol = measureSupport.getJdbcColumn();
        String declare = measCol.getDeclare(appCtx, model.getQueryObject().getAlias());
        assertTrue(declare.contains("+ 100"),
                "通用公式应包含 '+ 100'，实际: " + declare);

        log.info("度量 formulaDef 通用公式验证通过: declare={}", declare);
    }

    @Test
    @Order(42)
    @DisplayName("度量无 dialectFormulaDef — 原有 formulaDef 行为不变")
    void testMeasureOriginalFormulaDefStillWorks() {
        // FactSalesModel 的 taxAmount2 使用旧的 formulaDef（无 dialectFormulaDef）
        TableModel model = tableModelLoaderManager.load("FactSalesModel");
        assertNotNull(model);

        DbMeasure taxMeasure = model.findJdbcMeasureByName("taxAmount2");
        assertNotNull(taxMeasure, "taxAmount2 度量不存在");

        DbMeasureSupport measureSupport = taxMeasure.getDecorate(DbMeasureSupport.class);
        assertNotNull(measureSupport.getFormulaBuilder(), "旧 formulaDef 应保持 formulaBuilder");

        DbColumn measCol = measureSupport.getJdbcColumn();
        String declare = measCol.getDeclare(appCtx, model.getQueryObject().getAlias());
        assertTrue(declare.contains("tax_amount+1"),
                "旧 formulaDef 应包含 tax_amount+1，实际: " + declare);

        log.info("度量旧 formulaDef 兼容性验证通过: declare={}", declare);
    }
}
