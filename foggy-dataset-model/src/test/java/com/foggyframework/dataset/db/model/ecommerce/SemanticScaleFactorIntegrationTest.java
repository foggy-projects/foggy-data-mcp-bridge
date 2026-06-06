package com.foggyframework.dataset.db.model.ecommerce;

import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.dataset.client.domain.PagingRequest;
import com.foggyframework.dataset.db.model.config.DatasetProperties;
import com.foggyframework.dataset.db.model.def.query.request.CalculatedFieldDef;
import com.foggyframework.dataset.db.model.def.query.request.DbQueryRequestDef;
import com.foggyframework.dataset.db.model.def.query.request.GroupRequestDef;
import com.foggyframework.dataset.db.model.def.query.request.OrderRequestDef;
import com.foggyframework.dataset.db.model.def.query.request.SliceRequestDef;
import com.foggyframework.dataset.db.model.engine.query.DbQueryResult;
import com.foggyframework.dataset.db.model.impl.dimension.DbDimensionSupport;
import com.foggyframework.dataset.db.model.plugins.result_set_filter.ModelResultContext;
import com.foggyframework.dataset.db.model.semantic.domain.DeniedPhysicalColumn;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticQueryRequest;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticQueryResponse;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticRequestContext;
import com.foggyframework.dataset.db.model.semantic.domain.pivot.AxisField;
import com.foggyframework.dataset.db.model.semantic.domain.pivot.PivotRequest;
import com.foggyframework.dataset.db.model.service.QueryFacade;
import com.foggyframework.dataset.db.model.semantic.service.SemanticQueryServiceV3;
import com.foggyframework.dataset.db.model.spi.DbDimension;
import com.foggyframework.dataset.db.model.spi.DbMeasure;
import com.foggyframework.dataset.db.model.spi.DbProperty;
import com.foggyframework.dataset.db.model.spi.TableModel;
import com.foggyframework.dataset.model.PagingResultImpl;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("semanticScaleFactor 集成测试")
class SemanticScaleFactorIntegrationTest extends EcommerceTestSupport {

    private static final String TABLE_MODEL = "FactSalesSemanticScaleModel";
    private static final String QUERY_MODEL = "FactSalesSemanticScaleQueryModel";
    private static final String FORMULA_TABLE_MODEL = "FactSalesSemanticScaleFormulaModel";
    private static final String FORMULA_QUERY_MODEL = "FactSalesSemanticScaleFormulaQueryModel";

    @Resource
    private QueryFacade queryFacade;

    @Resource
    private SemanticQueryServiceV3 semanticQueryServiceV3;

    @Resource
    private SystemBundlesContext systemBundlesContext;

    @Resource
    private DatasetProperties datasetProperties;

    @Test
    @DisplayName("加载 TM 时保留 semanticScaleFactor / semanticUnit 元数据")
    void loadModel_keepsSemanticScaleMetadata() {
        TableModel model = tableModelLoaderManager.load(TABLE_MODEL);

        DbMeasure measure = model.findJdbcMeasureByName("salesAmountYuan");
        assertNotNull(measure);
        assertEquals(0, new BigDecimal("100").compareTo(measure.getSemanticScaleFactor()));
        assertEquals("CNY", measure.getSemanticUnit());
        assertEquals("元", measure.getSemanticUnitLabel());

        DbDimension product = model.findJdbcDimensionByName("product");
        assertNotNull(product);
        DbProperty property = ((DbDimensionSupport) product).getJdbcProperties().stream()
                .filter(p -> "unitPriceYuan".equals(p.getName()))
                .findFirst()
                .orElse(null);
        assertNotNull(property);
        assertEquals(0, new BigDecimal("100").compareTo(property.getSemanticScaleFactor()));
        assertEquals("CNY", property.getSemanticUnit());
        assertEquals("元", property.getSemanticUnitLabel());
    }

    @Test
    @DisplayName("禁用 namespace 加载同一套 TM 时忽略 semanticScaleFactor")
    void loadModel_disabledNamespaceClearsSemanticScaleMetadata() {
        String namespace = registerPhysicalNamespace();

        TableModel model = tableModelLoaderManager.load(TABLE_MODEL, namespace);

        DbMeasure measure = model.findJdbcMeasureByName("salesAmountYuan");
        assertNotNull(measure);
        assertNull(measure.getSemanticScaleFactor());

        DbDimension product = model.findJdbcDimensionByName("product");
        assertNotNull(product);
        DbProperty property = ((DbDimensionSupport) product).getJdbcProperties().stream()
                .filter(p -> "unitPriceYuan".equals(p.getName()))
                .findFirst()
                .orElse(null);
        assertNotNull(property);
        assertNull(property.getSemanticScaleFactor());
    }

    @Test
    @DisplayName("同名模型在默认 namespace 与禁用 namespace 下缓存隔离")
    void loadModel_disabledNamespaceDoesNotPolluteDefaultNamespace() {
        String namespace = registerPhysicalNamespace();

        TableModel physicalModel = tableModelLoaderManager.load(TABLE_MODEL, namespace);
        TableModel defaultModel = tableModelLoaderManager.load(TABLE_MODEL);
        TableModel physicalModelAgain = tableModelLoaderManager.load(TABLE_MODEL, namespace);

        DbMeasure physicalMeasure = physicalModel.findJdbcMeasureByName("salesAmountYuan");
        DbMeasure defaultMeasure = defaultModel.findJdbcMeasureByName("salesAmountYuan");
        DbMeasure physicalMeasureAgain = physicalModelAgain.findJdbcMeasureByName("salesAmountYuan");

        assertNotNull(physicalMeasure);
        assertNotNull(defaultMeasure);
        assertNotNull(physicalMeasureAgain);
        assertNull(physicalMeasure.getSemanticScaleFactor());
        assertEquals(0, new BigDecimal("100").compareTo(defaultMeasure.getSemanticScaleFactor()));
        assertNull(physicalMeasureAgain.getSemanticScaleFactor());
    }

    @Test
    @DisplayName("禁用 namespace 查询使用物理值，不执行语义缩放")
    void disabledNamespace_queryUsesPhysicalValues() {
        String namespace = registerPhysicalNamespace();
        String expectedSql = paginateSql("""
                SELECT fs.order_id AS orderId,
                       fs.sales_amount AS salesAmountYuan
                FROM fact_sales fs
                WHERE fs.sales_amount > 1000
                ORDER BY salesAmountYuan ASC, fs.order_id ASC
                """, 20);
        List<Map<String, Object>> expectedRows = executeQuery(expectedSql);
        assertFalse(expectedRows.isEmpty(), "physical namespace smoke should exercise non-empty rows");

        DbQueryRequestDef request = new DbQueryRequestDef();
        request.setQueryModel(QUERY_MODEL);
        request.setColumns(List.of("orderId", "salesAmountYuan"));
        request.setSlice(List.of(new SliceRequestDef("salesAmountYuan", ">", 1000)));
        request.setOrderBy(List.of(
                order("salesAmountYuan", "ASC"),
                order("orderId", "ASC")
        ));

        List<Map<String, Object>> actualRows = queryRows(request, null, null, namespace);

        assertRowsMatch(expectedRows, actualRows, "orderId", "salesAmountYuan");
    }

    @Test
    @DisplayName("禁用 namespace 维度属性使用物理值")
    void disabledNamespace_dimensionPropertyUsesPhysicalValues() {
        String namespace = registerPhysicalNamespace();
        String expectedSql = paginateSql("""
                SELECT fs.order_id AS orderId,
                       dp.unit_price AS unitPriceYuan
                FROM fact_sales fs
                JOIN dim_product dp ON fs.product_key = dp.product_key
                ORDER BY fs.order_id ASC, unitPriceYuan ASC
                """, 20);
        List<Map<String, Object>> expectedRows = executeQuery(expectedSql);
        assertFalse(expectedRows.isEmpty(), "physical namespace dimension property smoke should return rows");

        DbQueryRequestDef request = new DbQueryRequestDef();
        request.setQueryModel(QUERY_MODEL);
        request.setColumns(List.of("orderId", "product$unitPriceYuan"));
        request.setOrderBy(List.of(
                order("orderId", "ASC"),
                order("product$unitPriceYuan", "ASC")
        ));

        List<Map<String, Object>> actualRows = queryRows(request, null, null, namespace);

        assertEquals(expectedRows.size(), actualRows.size());
        for (int i = 0; i < expectedRows.size(); i++) {
            assertEquals(String.valueOf(expectedRows.get(i).get("orderId")),
                    String.valueOf(actualRows.get(i).get("orderId")));
            assertDecimalEquals(expectedRows.get(i).get("unitPriceYuan"),
                    actualRows.get(i).get("product$unitPriceYuan"));
        }
    }

    @Test
    @DisplayName("禁用 namespace 聚合使用物理值")
    void disabledNamespace_groupedAggregationUsesPhysicalValues() {
        String namespace = registerPhysicalNamespace();
        String expectedSql = paginateSql("""
                SELECT fs.order_id AS orderId,
                       SUM(fs.sales_amount) AS salesAmountYuan
                FROM fact_sales fs
                GROUP BY fs.order_id
                ORDER BY salesAmountYuan DESC, fs.order_id ASC
                """, 20);
        List<Map<String, Object>> expectedRows = executeQuery(expectedSql);
        assertFalse(expectedRows.isEmpty(), "physical namespace aggregation smoke should return rows");

        DbQueryRequestDef request = new DbQueryRequestDef();
        request.setQueryModel(QUERY_MODEL);
        request.setColumns(List.of("orderId", "salesAmountYuan"));
        request.setGroupBy(List.of(group("orderId")));
        request.setOrderBy(List.of(
                order("salesAmountYuan", "DESC"),
                order("orderId", "ASC")
        ));

        List<Map<String, Object>> actualRows = queryRows(request, null, null, namespace);

        assertRowsMatch(expectedRows, actualRows, "orderId", "salesAmountYuan");
    }

    @Test
    @DisplayName("禁用 namespace having 使用物理值过滤")
    void disabledNamespace_havingUsesPhysicalValues() {
        String namespace = registerPhysicalNamespace();
        String expectedSql = paginateSql("""
                SELECT fs.order_id AS orderId,
                       SUM(fs.sales_amount) AS totalSalesAmountYuan
                FROM fact_sales fs
                GROUP BY fs.order_id
                HAVING SUM(fs.sales_amount) > 1000
                ORDER BY totalSalesAmountYuan ASC, fs.order_id ASC
                """, 20);
        List<Map<String, Object>> expectedRows = executeQuery(expectedSql);
        assertFalse(expectedRows.isEmpty(), "physical namespace having smoke should return rows");

        DbQueryRequestDef request = new DbQueryRequestDef();
        request.setQueryModel(QUERY_MODEL);
        request.setColumns(List.of("orderId", "sum(salesAmountYuan) as totalSalesAmountYuan"));
        request.setGroupBy(List.of(group("orderId")));
        request.setHaving(List.of(new SliceRequestDef("totalSalesAmountYuan", ">", 1000)));
        request.setOrderBy(List.of(
                order("totalSalesAmountYuan", "ASC"),
                order("orderId", "ASC")
        ));

        List<Map<String, Object>> actualRows = queryRows(request, null, null, namespace);

        assertRowsMatch(expectedRows, actualRows, "orderId", "totalSalesAmountYuan");
    }

    @Test
    @DisplayName("禁用 namespace calculatedFields 使用物理叶子值")
    void disabledNamespace_calculatedFieldUsesPhysicalLeafValue() {
        String namespace = registerPhysicalNamespace();
        String expectedSql = paginateSql("""
                SELECT fs.order_id AS orderId,
                       fs.sales_amount + 10 AS salesAmountPlusTen
                FROM fact_sales fs
                ORDER BY fs.order_id ASC, fs.sales_amount ASC
                """, 20);
        List<Map<String, Object>> expectedRows = executeQuery(expectedSql);
        assertFalse(expectedRows.isEmpty(), "physical namespace calculated field smoke should return rows");

        DbQueryRequestDef request = new DbQueryRequestDef();
        request.setQueryModel(QUERY_MODEL);
        request.setColumns(List.of("orderId", "salesAmountYuan", "salesAmountPlusTen"));
        request.setCalculatedFields(List.of(
                new CalculatedFieldDef("salesAmountPlusTen", "salesAmountYuan + 10")
        ));
        request.setOrderBy(List.of(
                order("orderId", "ASC"),
                order("salesAmountYuan", "ASC")
        ));

        List<Map<String, Object>> actualRows = queryRows(request, null, null, namespace);

        assertRowsMatch(expectedRows, actualRows, "orderId", "salesAmountPlusTen");
    }

    @Test
    @DisplayName("属性 semanticScaleFactor 查询结果与真实 SQL 缩放一致")
    void propertySemanticScale_queryDataMatchesNativeSql() {
        String expectedSql = paginateSql("""
                SELECT fs.order_id AS orderId,
                       dp.unit_price / 100.0 AS unitPriceYuan
                FROM fact_sales fs
                JOIN dim_product dp ON fs.product_key = dp.product_key
                ORDER BY fs.order_id ASC, unitPriceYuan ASC
                """, 20);
        List<Map<String, Object>> expectedRows = executeQuery(expectedSql);

        DbQueryRequestDef request = new DbQueryRequestDef();
        request.setQueryModel(QUERY_MODEL);
        request.setColumns(List.of("orderId", "product$unitPriceYuan"));
        request.setOrderBy(List.of(
                order("orderId", "ASC"),
                order("product$unitPriceYuan", "ASC")
        ));

        List<Map<String, Object>> actualRows = queryRows(request, null);

        assertEquals(expectedRows.size(), actualRows.size());
        for (int i = 0; i < expectedRows.size(); i++) {
            assertEquals(String.valueOf(expectedRows.get(i).get("orderId")),
                    String.valueOf(actualRows.get(i).get("orderId")));
            assertDecimalEquals(expectedRows.get(i).get("unitPriceYuan"),
                    actualRows.get(i).get("product$unitPriceYuan"));
        }
    }

    @Test
    @DisplayName("度量 semanticScaleFactor 聚合与排序结果与真实 SQL 一致")
    void measureSemanticScale_groupedOrderMatchesNativeSql() {
        String expectedSql = paginateSql("""
                SELECT fs.order_id AS orderId,
                       SUM(fs.sales_amount / 100.0) AS salesAmountYuan
                FROM fact_sales fs
                GROUP BY fs.order_id
                ORDER BY salesAmountYuan DESC, fs.order_id ASC
                """, 20);
        List<Map<String, Object>> expectedRows = executeQuery(expectedSql);

        DbQueryRequestDef request = new DbQueryRequestDef();
        request.setQueryModel(QUERY_MODEL);
        request.setColumns(List.of("orderId", "salesAmountYuan"));
        request.setGroupBy(List.of(group("orderId")));
        request.setOrderBy(List.of(
                order("salesAmountYuan", "DESC"),
                order("orderId", "ASC")
        ));

        List<Map<String, Object>> actualRows = queryRows(request, null);

        assertEquals(expectedRows.size(), actualRows.size());
        for (int i = 0; i < expectedRows.size(); i++) {
            assertEquals(String.valueOf(expectedRows.get(i).get("orderId")),
                    String.valueOf(actualRows.get(i).get("orderId")));
            assertDecimalEquals(expectedRows.get(i).get("salesAmountYuan"),
                    actualRows.get(i).get("salesAmountYuan"));
        }
    }

    @Test
    @DisplayName("slice 使用 semanticScaleFactor 后的语义单位过滤")
    void sliceSemanticScale_filtersInSemanticUnit() {
        String expectedSql = paginateSql("""
                SELECT fs.order_id AS orderId,
                       fs.sales_amount / 100.0 AS salesAmountYuan
                FROM fact_sales fs
                WHERE fs.sales_amount / 100.0 > 1000
                ORDER BY salesAmountYuan ASC, fs.order_id ASC
                """, 20);
        List<Map<String, Object>> expectedRows = executeQuery(expectedSql);

        DbQueryRequestDef request = new DbQueryRequestDef();
        request.setQueryModel(QUERY_MODEL);
        request.setColumns(List.of("orderId", "salesAmountYuan"));
        request.setSlice(List.of(new SliceRequestDef("salesAmountYuan", ">", 1000)));
        request.setOrderBy(List.of(
                order("salesAmountYuan", "ASC"),
                order("orderId", "ASC")
        ));

        List<Map<String, Object>> actualRows = queryRows(request, null);

        assertRowsMatch(expectedRows, actualRows, "orderId", "salesAmountYuan");
    }

    @Test
    @DisplayName("having 使用 semanticScaleFactor 后的聚合语义单位过滤")
    void havingSemanticScale_filtersAggregatedSemanticUnit() {
        String expectedSql = paginateSql("""
                SELECT fs.order_id AS orderId,
                       SUM(fs.sales_amount / 100.0) AS totalSalesAmountYuan
                FROM fact_sales fs
                GROUP BY fs.order_id
                HAVING SUM(fs.sales_amount / 100.0) > 1000
                ORDER BY totalSalesAmountYuan ASC, fs.order_id ASC
                """, 20);
        List<Map<String, Object>> expectedRows = executeQuery(expectedSql);

        DbQueryRequestDef request = new DbQueryRequestDef();
        request.setQueryModel(QUERY_MODEL);
        request.setColumns(List.of("orderId", "sum(salesAmountYuan) as totalSalesAmountYuan"));
        request.setGroupBy(List.of(group("orderId")));
        request.setHaving(List.of(new SliceRequestDef("totalSalesAmountYuan", ">", 1000)));
        request.setOrderBy(List.of(
                order("totalSalesAmountYuan", "ASC"),
                order("orderId", "ASC")
        ));

        List<Map<String, Object>> actualRows = queryRows(request, null);

        assertRowsMatch(expectedRows, actualRows, "orderId", "totalSalesAmountYuan");
    }

    @Test
    @DisplayName("calculatedFields 引用 semanticScaleFactor 字段时不需要再手写换算")
    void calculatedField_usesSemanticUnitLeafValue() {
        String expectedSql = paginateSql("""
                SELECT fs.order_id AS orderId,
                       (fs.sales_amount / 100.0) + 10 AS salesAmountPlusTen
                FROM fact_sales fs
                ORDER BY fs.order_id ASC, fs.sales_amount / 100.0 ASC
                """, 20);
        List<Map<String, Object>> expectedRows = executeQuery(expectedSql);

        DbQueryRequestDef request = new DbQueryRequestDef();
        request.setQueryModel(QUERY_MODEL);
        request.setColumns(List.of("orderId", "salesAmountYuan", "salesAmountPlusTen"));
        request.setCalculatedFields(List.of(
                new CalculatedFieldDef("salesAmountPlusTen", "salesAmountYuan + 10")
        ));
        request.setOrderBy(List.of(
                order("orderId", "ASC"),
                order("salesAmountYuan", "ASC")
        ));

        List<Map<String, Object>> actualRows = queryRows(request, null);

        assertRowsMatch(expectedRows, actualRows, "orderId", "salesAmountPlusTen");
    }

    @Test
    @DisplayName("ratio 表达式引用两个 semanticScaleFactor 字段时不会二次换算")
    void ratioCalculatedField_doesNotDoubleConvert() {
        String expectedSql = paginateSql("""
                SELECT fs.order_id AS orderId,
                       (dp.unit_price / 100.0) / (fs.sales_amount / 100.0) AS unitToSalesRatio
                FROM fact_sales fs
                JOIN dim_product dp ON fs.product_key = dp.product_key
                ORDER BY fs.order_id ASC, dp.unit_price / 100.0 ASC, fs.sales_amount / 100.0 ASC
                """, 20);
        List<Map<String, Object>> expectedRows = executeQuery(expectedSql);

        DbQueryRequestDef request = new DbQueryRequestDef();
        request.setQueryModel(QUERY_MODEL);
        request.setColumns(List.of("orderId", "product$unitPriceYuan", "salesAmountYuan", "unitToSalesRatio"));
        request.setCalculatedFields(List.of(
                new CalculatedFieldDef("unitToSalesRatio", "product$unitPriceYuan / salesAmountYuan")
        ));
        request.setOrderBy(List.of(
                order("orderId", "ASC"),
                order("product$unitPriceYuan", "ASC"),
                order("salesAmountYuan", "ASC")
        ));

        List<Map<String, Object>> actualRows = queryRows(request, null);

        assertRowsMatch(expectedRows, actualRows, "orderId", "unitToSalesRatio");
    }

    @Test
    @DisplayName("pivot.metrics 使用 semanticScaleFactor 后的语义单位聚合")
    void pivotMetric_usesSemanticScaledMeasure() {
        String category = quoteIdentifier("product$categoryName");
        String amount = quoteIdentifier("salesAmountYuan");
        List<Map<String, Object>> expectedRows = executeQuery("""
                SELECT dp.category_name AS %s,
                       SUM(fs.sales_amount / 100.0) AS %s
                FROM fact_sales fs
                JOIN dim_product dp ON fs.product_key = dp.product_key
                GROUP BY dp.category_name
                """.formatted(category, amount));

        PivotRequest pivot = new PivotRequest();
        pivot.setRows(List.of(axis("product$categoryName")));
        pivot.setMetrics(List.of("salesAmountYuan"));
        pivot.setOutputFormat("flat");

        SemanticQueryRequest request = new SemanticQueryRequest();
        request.setPivot(pivot);

        List<Map<String, Object>> actualRows = executeSemantic(request).getItems();

        assertCanonicalRowsEqual(expectedRows, actualRows);
    }

    @Test
    @DisplayName("timeWindow.targetMetrics 使用 semanticScaleFactor 后的语义单位窗口聚合")
    void timeWindowTargetMetric_usesSemanticScaledMeasure() {
        if (!supportsWindowFunctions()) {
            return;
        }

        SemanticQueryRequest request = new SemanticQueryRequest();
        request.setColumns(List.of("salesDate$id", "salesAmountYuan", "salesAmountYuan__rolling_7d"));
        request.setGroupBy(List.of(semanticGroup("salesDate$id")));
        request.setTimeWindow(Map.of(
                "field", "salesDate$id",
                "grain", "day",
                "comparison", "rolling_7d",
                "targetMetrics", List.of("salesAmountYuan")
        ));

        String salesDateId = quoteIdentifier("salesDate$id");
        String amount = quoteIdentifier("salesAmountYuan");
        String rolling7d = quoteIdentifier("salesAmountYuan__rolling_7d");
        List<Map<String, Object>> expectedRows = executeQuery("""
                WITH daily AS (
                    SELECT fs.date_key AS %s,
                           SUM(fs.sales_amount / 100.0) AS %s
                    FROM fact_sales fs
                    GROUP BY fs.date_key
                )
                SELECT %s,
                       %s,
                       SUM(%s) OVER (
                           ORDER BY %s
                           ROWS BETWEEN 6 PRECEDING AND CURRENT ROW
                       ) AS %s
                FROM daily
                """.formatted(
                salesDateId, amount,
                salesDateId, amount, amount, salesDateId, rolling7d));

        List<Map<String, Object>> actualRows = executeSemantic(request).getItems();

        assertCanonicalRowsEqual(expectedRows, actualRows);
    }

    @Test
    @DisplayName("outputFormatting 只回传显示元数据且不改写 raw items 值")
    void outputFormatting_attachesDisplayMetadataWithoutMutatingRawValues() {
        SemanticQueryRequest request = new SemanticQueryRequest();
        request.setColumns(List.of("orderId", "salesAmountYuan"));
        request.setOutputFormatting(List.of(decimalFormat("salesAmountYuan", 2)));
        request.setLimit(5);

        SemanticQueryResponse response = executeSemantic(request);

        SemanticQueryResponse.SchemaInfo.ColumnDef amountColumn = findSchemaColumn(response, "salesAmountYuan");
        assertNotNull(amountColumn.getDisplayFormat(), "salesAmountYuan 应带 displayFormat 元数据");
        assertEquals("decimal", amountColumn.getDisplayFormat().getKind());
        assertEquals(2, amountColumn.getDisplayFormat().getScale());
        assertEquals("HALF_UP", amountColumn.getDisplayFormat().getMode());
        assertEquals("display_only", amountColumn.getDisplayFormat().getScope());

        Object rawValue = response.getItems().get(0).get("salesAmountYuan");
        assertTrue(rawValue instanceof Number,
                "outputFormatting 不应把 raw item 数值格式化成字符串，实际: " + rawValue);
    }

    @Test
    @DisplayName("outputFormatting 引用非最终输出字段时 fail-closed")
    void outputFormatting_unknownOutputFieldRejected() {
        SemanticQueryRequest request = new SemanticQueryRequest();
        request.setColumns(List.of("salesAmountYuan"));
        request.setOutputFormatting(List.of(decimalFormat("roundedSalesAmount", 2)));
        request.setLimit(5);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> semanticQueryServiceV3.queryModel(
                        QUERY_MODEL, request, "execute", SemanticRequestContext.empty()));
        assertTrue(ex.getMessage().contains("OUTPUT_FORMATTING_FIELD_NOT_IN_OUTPUT_SCHEMA"),
                "异常应包含 outputFormatting 字段闭环校验错误，实际: " + ex.getMessage());
    }

    @Test
    @DisplayName("deniedColumns 命中底层物理列时拒绝 semanticScaleFactor 字段")
    void deniedPhysicalColumn_rejectsSemanticScaledMeasure() {
        DbQueryRequestDef request = new DbQueryRequestDef();
        request.setQueryModel(QUERY_MODEL);
        request.setColumns(List.of("salesAmountYuan"));

        List<DeniedPhysicalColumn> deniedColumns = List.of(
                new DeniedPhysicalColumn(null, "fact_sales", "sales_amount")
        );

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> queryRows(request, deniedColumns));
        assertTrue(ex.getMessage().contains("salesAmountYuan"),
                "异常消息应包含被拒绝的 QM 字段名，实际: " + ex.getMessage());
    }

    @Test
    @DisplayName("fieldAccess 仍按 semanticScaleFactor 字段名拒绝未授权访问")
    void fieldAccess_rejectsSemanticScaledFieldName() {
        DbQueryRequestDef request = new DbQueryRequestDef();
        request.setQueryModel(QUERY_MODEL);
        request.setColumns(List.of("orderId", "salesAmountYuan"));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> queryRows(request, null, Set.of("orderId")));
        assertTrue(ex.getMessage().contains("salesAmountYuan"),
                "异常消息应包含被拒绝的 QM 字段名，实际: " + ex.getMessage());
    }

    @Test
    @DisplayName("formulaDef / dialectFormulaDef 结果支持 semanticScaleFactor")
    void semanticScaleWithFormula_queryDataMatchesNativeSql() {
        TableModel model = tableModelLoaderManager.load(FORMULA_TABLE_MODEL);
        assertNotNull(model.findJdbcPropertyByName("salesAmountFormulaLeafYuan"));
        assertNotNull(model.findJdbcMeasureByName("salesAmountFormulaYuan"));
        assertNotNull(model.findJdbcMeasureByName("salesAmountBuilderYuan"));
        assertNotNull(model.findJdbcMeasureByName("taxDialectFormulaYuan"));

        String expectedSql = paginateSql("""
                SELECT fs.order_id AS orderId,
                       SUM((fs.sales_amount + 0) / 100.0) AS salesAmountFormulaYuan,
                       SUM((fs.sales_amount + 1) / 100.0) AS salesAmountBuilderYuan,
                       SUM((fs.tax_amount + 0) / 100.0) AS taxDialectFormulaYuan
                FROM fact_sales fs
                GROUP BY fs.order_id
                ORDER BY orderId ASC
                """, 20);
        List<Map<String, Object>> expectedRows = executeQuery(expectedSql);

        DbQueryRequestDef request = new DbQueryRequestDef();
        request.setQueryModel(FORMULA_QUERY_MODEL);
        request.setColumns(List.of(
                "orderId",
                "salesAmountFormulaYuan",
                "salesAmountBuilderYuan",
                "taxDialectFormulaYuan"
        ));
        request.setGroupBy(List.of(group("orderId")));
        request.setOrderBy(List.of(order("orderId", "ASC")));

        List<Map<String, Object>> actualRows = queryRows(request, null);

        assertEquals(expectedRows.size(), actualRows.size());
        for (int i = 0; i < expectedRows.size(); i++) {
            assertEquals(String.valueOf(expectedRows.get(i).get("orderId")),
                    String.valueOf(actualRows.get(i).get("orderId")));
            assertDecimalEquals(expectedRows.get(i).get("salesAmountFormulaYuan"),
                    actualRows.get(i).get("salesAmountFormulaYuan"));
            assertDecimalEquals(expectedRows.get(i).get("salesAmountBuilderYuan"),
                    actualRows.get(i).get("salesAmountBuilderYuan"));
            assertDecimalEquals(expectedRows.get(i).get("taxDialectFormulaYuan"),
                    actualRows.get(i).get("taxDialectFormulaYuan"));
        }
    }

    @Test
    @DisplayName("属性 formulaDef.value 结果支持 semanticScaleFactor")
    void semanticScaleWithFormulaProperty_queryDataMatchesNativeSql() {
        String expectedSql = paginateSql("""
                SELECT fs.order_id AS orderId,
                       (fs.sales_amount + 2) / 100.0 AS salesAmountFormulaLeafYuan
                FROM fact_sales fs
                ORDER BY fs.order_id ASC, salesAmountFormulaLeafYuan ASC
                """, 20);
        List<Map<String, Object>> expectedRows = executeQuery(expectedSql);
        assertFalse(expectedRows.isEmpty(), "formula property semantic-scale smoke should return rows");

        DbQueryRequestDef request = new DbQueryRequestDef();
        request.setQueryModel(FORMULA_QUERY_MODEL);
        request.setColumns(List.of("orderId", "salesAmountFormulaLeafYuan"));
        request.setOrderBy(List.of(
                order("orderId", "ASC"),
                order("salesAmountFormulaLeafYuan", "ASC")
        ));

        List<Map<String, Object>> actualRows = queryRows(request, null);

        assertRowsMatch(expectedRows, actualRows, "orderId", "salesAmountFormulaLeafYuan");
    }

    @Test
    @DisplayName("禁用 namespace 时 formulaDef / dialectFormulaDef 查询保留物理公式结果")
    void disabledNamespace_formulaQueryUsesPhysicalFormulaValues() {
        String namespace = registerPhysicalNamespace();
        String expectedSql = paginateSql("""
                SELECT fs.order_id AS orderId,
                       SUM(fs.sales_amount + 0) AS salesAmountFormulaYuan,
                       SUM(fs.sales_amount + 1) AS salesAmountBuilderYuan,
                       SUM(fs.tax_amount + 0) AS taxDialectFormulaYuan
                FROM fact_sales fs
                GROUP BY fs.order_id
                ORDER BY orderId ASC
                """, 20);
        List<Map<String, Object>> expectedRows = executeQuery(expectedSql);
        assertFalse(expectedRows.isEmpty(), "formula physical namespace smoke should return rows");

        DbQueryRequestDef request = new DbQueryRequestDef();
        request.setQueryModel(FORMULA_QUERY_MODEL);
        request.setColumns(List.of(
                "orderId",
                "salesAmountFormulaYuan",
                "salesAmountBuilderYuan",
                "taxDialectFormulaYuan"
        ));
        request.setGroupBy(List.of(group("orderId")));
        request.setOrderBy(List.of(order("orderId", "ASC")));

        List<Map<String, Object>> actualRows = queryRows(request, null, null, namespace);

        assertRowsMatch(expectedRows, actualRows, "orderId",
                "salesAmountFormulaYuan", "salesAmountBuilderYuan", "taxDialectFormulaYuan");
    }

    @Test
    @DisplayName("DSL 入口启用/关闭 semanticScaleFactor 时 formulaDef 结果与真实 SQL 一致")
    void semanticDslFormulaSemanticScaleToggle_matchesNativeSql() {
        String semanticSql = paginateSql("""
                SELECT fs.order_id AS orderId,
                       SUM((fs.sales_amount + 0) / 100.0) AS salesAmountFormulaYuan,
                       SUM((fs.sales_amount + 1) / 100.0) AS salesAmountBuilderYuan,
                       SUM((fs.tax_amount + 0) / 100.0) AS taxDialectFormulaYuan
                FROM fact_sales fs
                GROUP BY fs.order_id
                ORDER BY orderId ASC
                """, 20);
        List<Map<String, Object>> semanticExpectedRows = executeQuery(semanticSql);

        SemanticQueryRequest semanticRequest = semanticFormulaRequest();
        List<Map<String, Object>> semanticActualRows = executeSemantic(FORMULA_QUERY_MODEL,
                semanticRequest, SemanticRequestContext.empty()).getItems();

        assertRowsMatch(semanticExpectedRows, semanticActualRows, "orderId",
                "salesAmountFormulaYuan", "salesAmountBuilderYuan", "taxDialectFormulaYuan");

        String namespace = registerPhysicalNamespace();
        String physicalSql = paginateSql("""
                SELECT fs.order_id AS orderId,
                       SUM(fs.sales_amount + 0) AS salesAmountFormulaYuan,
                       SUM(fs.sales_amount + 1) AS salesAmountBuilderYuan,
                       SUM(fs.tax_amount + 0) AS taxDialectFormulaYuan
                FROM fact_sales fs
                GROUP BY fs.order_id
                ORDER BY orderId ASC
                """, 20);
        List<Map<String, Object>> physicalExpectedRows = executeQuery(physicalSql);

        List<Map<String, Object>> physicalActualRows = executeSemantic(FORMULA_QUERY_MODEL,
                semanticFormulaRequest(), SemanticRequestContext.ofNamespace(namespace)).getItems();

        assertRowsMatch(physicalExpectedRows, physicalActualRows, "orderId",
                "salesAmountFormulaYuan", "salesAmountBuilderYuan", "taxDialectFormulaYuan");
    }

    @Test
    @DisplayName("formula-only 度量在当前方言无可用公式时拒绝加载")
    void formulaOnlyMeasureWithoutResolvedDialect_rejectedOnModelLoad() {
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> tableModelLoaderManager.load("FactSalesSemanticScaleFormulaUnsupportedDialectInvalidModel"));
        assertTrue(ex.getMessage().contains("列名") || ex.getMessage().contains("column"),
                "异常消息应说明无可用列或公式，实际: " + ex.getMessage());
    }

    @Test
    @DisplayName("formulaDef 属性缺少 carrier column 时提示具体字段")
    void formulaPropertyMissingColumn_reportsFieldPathAndCarrierColumnRule() {
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> tableModelLoaderManager.load("FactSalesFormulaPropertyMissingColumnInvalidModel"));

        assertTrue(ex.getMessage().contains(
                        "FactSalesFormulaPropertyMissingColumnInvalidModel.salesAmountFormulaLeafYuan column不能为空"),
                "异常消息应包含模型与字段路径，实际: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("formulaDef/dialectFormulaDef"),
                "异常消息应说明公式字段规则，实际: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("carrier column"),
                "异常消息应说明 carrier column 规则，实际: " + ex.getMessage());
    }

    @Test
    @DisplayName("semanticScaleFactor 的 column 不能写 SQL 表达式")
    void semanticScaleWithSqlExpressionColumn_rejectedOnModelLoad() {
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> tableModelLoaderManager.load("FactSalesSemanticScaleSqlExpressionInvalidModel"));
        assertTrue(ex.getMessage().contains("column"));
    }

    private OrderRequestDef order(String field, String dir) {
        OrderRequestDef order = new OrderRequestDef();
        order.setField(field);
        order.setDir(dir);
        return order;
    }

    private GroupRequestDef group(String field) {
        GroupRequestDef group = new GroupRequestDef();
        group.setField(field);
        return group;
    }

    private AxisField axis(String field) {
        AxisField axis = new AxisField();
        axis.setField(field);
        return axis;
    }

    private SemanticQueryRequest.OutputFormattingItem decimalFormat(String field, int scale) {
        SemanticQueryRequest.OutputFormattingItem item = new SemanticQueryRequest.OutputFormattingItem();
        item.setField(field);
        item.setKind("decimal");
        item.setScale(scale);
        item.setMode("HALF_UP");
        item.setScope("display_only");
        return item;
    }

    private SemanticQueryResponse.SchemaInfo.ColumnDef findSchemaColumn(SemanticQueryResponse response, String name) {
        assertNotNull(response.getSchema(), "响应应包含 schema");
        assertNotNull(response.getSchema().getColumns(), "schema 应包含 columns");
        return response.getSchema().getColumns().stream()
                .filter(column -> name.equals(column.getName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("schema column not found: " + name));
    }

    private SemanticQueryRequest.GroupByItem semanticGroup(String field) {
        SemanticQueryRequest.GroupByItem group = new SemanticQueryRequest.GroupByItem();
        group.setField(field);
        return group;
    }

    private SemanticQueryRequest.OrderItem semanticOrder(String field, String dir) {
        SemanticQueryRequest.OrderItem order = new SemanticQueryRequest.OrderItem();
        order.setField(field);
        order.setDir(dir);
        return order;
    }

    private SemanticQueryRequest semanticFormulaRequest() {
        SemanticQueryRequest request = new SemanticQueryRequest();
        request.setColumns(List.of(
                "orderId",
                "salesAmountFormulaYuan",
                "salesAmountBuilderYuan",
                "taxDialectFormulaYuan"
        ));
        request.setGroupBy(List.of(semanticGroup("orderId")));
        request.setOrderBy(List.of(semanticOrder("orderId", "ASC")));
        request.setLimit(20);
        return request;
    }

    private SemanticQueryResponse executeSemantic(SemanticQueryRequest request) {
        return executeSemantic(QUERY_MODEL, request, SemanticRequestContext.empty());
    }

    private SemanticQueryResponse executeSemantic(String model, SemanticQueryRequest request,
                                                  SemanticRequestContext context) {
        SemanticQueryResponse response = semanticQueryServiceV3.queryModel(
                model, request, "execute", context);
        assertNotNull(response);
        assertNotNull(response.getItems());
        assertFalse(response.getItems().isEmpty(), "semantic query should return rows");
        return response;
    }

    private List<Map<String, Object>> queryRows(DbQueryRequestDef request,
                                                List<DeniedPhysicalColumn> deniedColumns) {
        return queryRows(request, deniedColumns, null);
    }

    private List<Map<String, Object>> queryRows(DbQueryRequestDef request,
                                                List<DeniedPhysicalColumn> deniedColumns,
                                                Set<String> fieldAccess) {
        return queryRows(request, deniedColumns, fieldAccess, null);
    }

    private List<Map<String, Object>> queryRows(DbQueryRequestDef request,
                                                List<DeniedPhysicalColumn> deniedColumns,
                                                Set<String> fieldAccess,
                                                String namespace) {
        ModelResultContext ctx = new ModelResultContext();
        ctx.setRequest(PagingRequest.buildPagingRequest(request, 20));
        ctx.setDeniedColumns(deniedColumns);
        ctx.setFieldAccess(fieldAccess);
        ctx.setNamespace(namespace);
        DbQueryResult dbResult = queryFacade.queryModelResult(ctx);
        PagingResultImpl result = dbResult.getPagingResult();
        return castItems(result);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> castItems(PagingResultImpl result) {
        return (List<Map<String, Object>>) result.getItems();
    }

    private void assertDecimalEquals(Object expected, Object actual) {
        assertEquals(0, toBigDecimal(expected).compareTo(toBigDecimal(actual)),
                "expected=" + expected + ", actual=" + actual);
    }

    private void assertRowsMatch(List<Map<String, Object>> expectedRows,
                                 List<Map<String, Object>> actualRows,
                                 String idField,
                                 String... valueFields) {
        assertEquals(expectedRows.size(), actualRows.size());
        for (int i = 0; i < expectedRows.size(); i++) {
            assertEquals(String.valueOf(expectedRows.get(i).get(idField)),
                    String.valueOf(actualRows.get(i).get(idField)));
            for (String valueField : valueFields) {
                assertDecimalEquals(expectedRows.get(i).get(valueField), actualRows.get(i).get(valueField));
            }
        }
    }

    private void assertCanonicalRowsEqual(List<Map<String, Object>> expectedRows,
                                          List<Map<String, Object>> actualRows) {
        List<Map<String, String>> expected = canonicalRows(expectedRows);
        List<Map<String, String>> actual = canonicalRows(actualRows);
        assertEquals(expected, actual);
    }

    private static List<Map<String, String>> canonicalRows(List<Map<String, Object>> rows) {
        List<Map<String, String>> canonical = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, String> normalized = new LinkedHashMap<>();
            row.entrySet().stream()
                    .filter(entry -> !entry.getKey().startsWith("_"))
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> normalized.put(entry.getKey(), canonicalValue(entry.getValue())));
            canonical.add(normalized);
        }
        canonical.sort(Comparator.comparing(Map::toString));
        return canonical;
    }

    private static String canonicalValue(Object value) {
        if (value == null) {
            return "<null>";
        }
        if (value instanceof Number) {
            return new BigDecimal(value.toString())
                    .setScale(6, RoundingMode.HALF_UP)
                    .stripTrailingZeros()
                    .toPlainString();
        }
        return value.toString();
    }

    private String quoteIdentifier(String identifier) {
        String dialect = getDialectKey();
        if (dialect.contains("mysql")) {
            return "`" + identifier + "`";
        }
        if (dialect.contains("sqlserver")) {
            return "[" + identifier + "]";
        }
        return "\"" + identifier + "\"";
    }

    private BigDecimal toBigDecimal(Object value) {
        assertNotNull(value);
        return new BigDecimal(String.valueOf(value));
    }

    private String registerPhysicalNamespace() {
        String namespace = "semantic-scale-physical-" + System.nanoTime();
        DatasetProperties.SemanticScaleConfig config = datasetProperties.getSemanticScale();
        config.setDefaultEnabled(true);
        List<String> disabledNamespaces = config.getDisabledNamespaces() == null
                ? new ArrayList<>()
                : new ArrayList<>(config.getDisabledNamespaces());
        disabledNamespaces.add(namespace);
        config.setDisabledNamespaces(disabledNamespaces);

        String bundleName = "semantic-scale-test-" + System.nanoTime();
        assertTrue(systemBundlesContext.addExternalBundle(bundleName, namespace, ecommerceBundlePath(), false));
        tableModelLoaderManager.clearByNamespace(namespace);
        queryModelLoader.clearByNamespace(namespace);
        return namespace;
    }

    private String ecommerceBundlePath() {
        List<Path> candidates = List.of(
                Paths.get("foggy-dataset-demo", "src", "main", "resources", "foggy", "templates", "ecommerce"),
                Paths.get("..", "foggy-dataset-demo", "src", "main", "resources", "foggy", "templates", "ecommerce"),
                Paths.get("foggy-dataset-model", "src", "test", "resources", "foggy", "templates", "ecommerce"),
                Paths.get("src", "test", "resources", "foggy", "templates", "ecommerce")
        );
        for (Path candidate : candidates) {
            if (hasSemanticScaleFixture(candidate)) {
                return candidate.toAbsolutePath().normalize().toString();
            }
        }
        fail("ecommerce test bundle path should contain semantic scale fixtures: " + candidates);
        return "";
    }

    private boolean hasSemanticScaleFixture(Path path) {
        return Files.isDirectory(path)
                && Files.isRegularFile(path.resolve("model").resolve(TABLE_MODEL + ".tm"))
                && Files.isRegularFile(path.resolve("query").resolve(QUERY_MODEL + ".qm"));
    }
}
