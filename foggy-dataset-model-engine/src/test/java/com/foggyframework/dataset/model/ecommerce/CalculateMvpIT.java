package com.foggyframework.dataset.model.ecommerce;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.dataset.client.domain.PagingRequest;
import com.foggyframework.dataset.db.dialect.FDialect;
import com.foggyframework.dataset.model.def.query.request.CalculatedFieldDef;
import com.foggyframework.dataset.model.def.query.request.DbQueryRequestDef;
import com.foggyframework.dataset.model.def.query.request.GroupRequestDef;
import com.foggyframework.dataset.model.def.query.request.OrderRequestDef;
import com.foggyframework.dataset.model.def.query.request.SliceRequestDef;
import com.foggyframework.dataset.model.engine.JdbcModelQueryEngine;
import com.foggyframework.dataset.model.engine.expression.CalculateQueryContext;
import com.foggyframework.dataset.model.engine.expression.SqlCalculatedFieldProcessor;
import com.foggyframework.dataset.model.engine.formula.SqlFormulaService;
import com.foggyframework.dataset.model.plugins.result_set_filter.ModelResultContext;
import com.foggyframework.dataset.model.semantic.domain.DeniedPhysicalColumn;
import com.foggyframework.dataset.model.service.AdvancedQueryFacade;
import com.foggyframework.dataset.model.spi.support.CalculatedDbColumn;
import com.foggyframework.dataset.model.spi.JdbcQueryModel;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("CALCULATE / REMOVE 受限 MVP 集成测试")
class CalculateMvpIT extends EcommerceTestSupport {

    @Resource
    private SqlFormulaService sqlFormulaService;

    @Resource
    private SystemBundlesContext systemBundlesContext;

    @Resource
    private AdvancedQueryFacade queryFacade;

    @Test
    @DisplayName("CALCULATE(SUM(metric), REMOVE(dim)) 下推为分组窗口总计并与原生 SQL 一致")
    void calculateRemoveAllGroupProducesTotalRatio() {
        assumeGroupedAggregateWindowSupported();
        String nativeSql = """
            SELECT
                dc.customer_type,
                SUM(fs.sales_amount) AS sales_amount,
                SUM(fs.sales_amount) / NULLIF(SUM(SUM(fs.sales_amount)) OVER (), 0) AS total_share
            FROM fact_sales fs
            LEFT JOIN dim_customer dc ON fs.customer_key = dc.customer_key
            GROUP BY dc.customer_type
            ORDER BY dc.customer_type
            """;
        List<Map<String, Object>> nativeResults = executeQuery(nativeSql);

        DbQueryRequestDef request = baseRequest(
                List.of("customer$customerType", "salesAmount", "totalShare"),
                List.of("customer$customerType"),
                List.of(new CalculatedFieldDef(
                        "totalShare",
                        "总占比",
                        "SUM(salesAmount) / NULLIF(CALCULATE(SUM(salesAmount), REMOVE(customer$customerType)), 0)"
                ))
        );

        JdbcModelQueryEngine engine = analyze(request);
        String sql = engine.getSql();
        assertTrue(sql.toUpperCase().contains("OVER ()"), "REMOVE 唯一分组维度时应生成全局窗口: " + sql);
        assertTrue(sql.toUpperCase().contains("NULLIF"), "比率分母应保留 NULLIF 防零除: " + sql);

        List<Map<String, Object>> modelResults =
                jdbcTemplate.queryForList(sql, engine.getValues().toArray());

        assertEquals(nativeResults.size(), modelResults.size(), "分组数量应一致");
        for (int i = 0; i < nativeResults.size(); i++) {
            Map<String, Object> nativeRow = nativeResults.get(i);
            Map<String, Object> modelRow = modelResults.get(i);
            assertEquals(nativeRow.get("customer_type"), modelRow.get("customer$customerType"));
            assertDecimalEquals(nativeRow.get("sales_amount"), modelRow.get("salesAmount"));
            assertDecimalEquals(nativeRow.get("total_share"), modelRow.get("totalShare"));
        }
    }

    @Test
    @DisplayName("CALCULATE 占比允许引用前序聚合别名")
    void calculateRatioCanReferencePriorAggregateAlias() {
        assumeGroupedAggregateWindowSupported();
        CalculatedFieldDef totalAmount = new CalculatedFieldDef(
                "totalAmount",
                "总金额",
                "salesAmount"
        );
        totalAmount.setAgg("SUM");

        DbQueryRequestDef request = baseRequest(
                List.of("customer$customerType", "totalAmount", "totalShare"),
                List.of("customer$customerType"),
                List.of(
                        totalAmount,
                        new CalculatedFieldDef(
                                "totalShare",
                                "总占比",
                                "totalAmount / NULLIF(CALCULATE(SUM(totalAmount), REMOVE(customer$customerType)), 0)"
                        )
                )
        );

        JdbcModelQueryEngine engine = analyze(request);
        String sql = engine.getSql();
        assertTrue(sql.toUpperCase().contains("OVER ()"), "别名占比分母应生成全局窗口: " + sql);
        assertTrue(sql.contains("totalShare"), "应投影 totalShare 别名: " + sql);
        String compactSql = sql.replaceAll("\\s+", " ");
        assertTrue(compactSql.contains("SUM(t1.sales_amount) / NULLIF"),
                "裸 totalAmount 应在分组公式中解析为 SUM(t1.sales_amount): " + sql);
        assertTrue(compactSql.contains("SUM(SUM(t1.sales_amount)) OVER ()"),
                "SUM(totalAmount) 应在 CALCULATE 中解析为窗口聚合总计: " + sql);
        String nativeSql = """
            SELECT
                dc.customer_type,
                SUM(fs.sales_amount) AS total_amount,
                SUM(fs.sales_amount) / NULLIF(SUM(SUM(fs.sales_amount)) OVER (), 0) AS total_share
            FROM fact_sales fs
            LEFT JOIN dim_customer dc ON fs.customer_key = dc.customer_key
            GROUP BY dc.customer_type
            ORDER BY dc.customer_type
            """;
        List<Map<String, Object>> nativeResults = executeQuery(nativeSql);

        List<Map<String, Object>> modelResults =
                jdbcTemplate.queryForList(sql, engine.getValues().toArray());

        assertEquals(nativeResults.size(), modelResults.size(), "分组数量应一致");
        for (int i = 0; i < nativeResults.size(); i++) {
            Map<String, Object> nativeRow = nativeResults.get(i);
            Map<String, Object> modelRow = modelResults.get(i);
            assertEquals(nativeRow.get("customer_type"), modelRow.get("customer$customerType"));
            assertDecimalEquals(nativeRow.get("total_amount"), modelRow.get("totalAmount"));
            assertDecimalEquals(nativeRow.get("total_share"), modelRow.get("totalShare"));
        }
    }

    @Test
    @DisplayName("后聚合公式引用前序聚合别名时生成分组聚合 SQL")
    void postAggregateFormulaReferenceToPriorAliasLowersToGroupedAggregateSql() {
        JdbcQueryModel queryModel = getQueryModel("FactSalesQueryModel");
        SqlCalculatedFieldProcessor processor =
                new SqlCalculatedFieldProcessor(queryModel, queryModel.getDialect());
        processor.setGroupedQuery(true);
        processor.setCalculateQueryContext(new CalculateQueryContext(
                List.of("customer$customerType"),
                Set.of(),
                true,
                false
        ));

        CalculatedFieldDef totalAmount = new CalculatedFieldDef(
                "totalAmount",
                "总金额",
                "salesAmount"
        );
        totalAmount.setAgg("SUM");

        List<CalculatedDbColumn> calculatedColumns = processor.processCalculatedFields(
                List.of(
                        totalAmount,
                        new CalculatedFieldDef(
                                "totalShare",
                                "总占比",
                                "totalAmount / NULLIF(CALCULATE(SUM(totalAmount), REMOVE(customer$customerType)), 0)"
                        )
                ),
                appCtx
        );

        CalculatedDbColumn totalShare = calculatedColumns.stream()
                .filter(column -> "totalShare".equals(column.getName()))
                .findFirst()
                .orElseThrow();
        String sql = totalShare.getDeclare();
        String compactSql = sql.replaceAll("\\s+", " ");
        assertTrue(compactSql.contains("SUM(t1.sales_amount) / NULLIF"),
                "裸 totalAmount 应解析为分组聚合表达式: " + sql);
        assertTrue(compactSql.contains("SUM(SUM(t1.sales_amount)) OVER ()"),
                "CALCULATE(SUM(totalAmount)) 应解析为分组窗口总计: " + sql);
    }

    @Test
    @DisplayName("REMOVE 单个维度时保留剩余 groupBy 作为 PARTITION BY")
    void calculateRemoveOneGroupKeepsRemainingPartition() {
        assumeGroupedAggregateWindowSupported();
        String nativeSql = """
            SELECT
                dc.customer_type,
                dp.category_name,
                SUM(fs.sales_amount) AS sales_amount,
                SUM(fs.sales_amount) / NULLIF(
                    SUM(SUM(fs.sales_amount)) OVER (PARTITION BY dc.customer_type),
                    0
                ) AS type_share
            FROM fact_sales fs
            LEFT JOIN dim_customer dc ON fs.customer_key = dc.customer_key
            LEFT JOIN dim_product dp ON fs.product_key = dp.product_key
            GROUP BY dc.customer_type, dp.category_name
            ORDER BY dc.customer_type, dp.category_name
            """;
        List<Map<String, Object>> nativeResults = executeQuery(nativeSql);

        DbQueryRequestDef request = baseRequest(
                List.of("customer$customerType", "product$categoryName", "salesAmount", "typeShare"),
                List.of("customer$customerType", "product$categoryName"),
                List.of(new CalculatedFieldDef(
                        "typeShare",
                        "类型内占比",
                        "SUM(salesAmount) / NULLIF(CALCULATE(SUM(salesAmount), REMOVE(product$categoryName)), 0)"
                ))
        );

        JdbcModelQueryEngine engine = analyze(request);
        String sql = engine.getSql();
        assertTrue(sql.toUpperCase().contains("PARTITION BY"), "应按剩余 groupBy 维度生成窗口分区: " + sql);

        List<Map<String, Object>> modelResults =
                jdbcTemplate.queryForList(sql, engine.getValues().toArray());

        assertEquals(nativeResults.size(), modelResults.size(), "分组数量应一致");
        for (int i = 0; i < nativeResults.size(); i++) {
            Map<String, Object> nativeRow = nativeResults.get(i);
            Map<String, Object> modelRow = modelResults.get(i);
            assertEquals(nativeRow.get("customer_type"), modelRow.get("customer$customerType"));
            assertEquals(nativeRow.get("category_name"), modelRow.get("product$categoryName"));
            assertDecimalEquals(nativeRow.get("sales_amount"), modelRow.get("salesAmount"));
            assertDecimalEquals(nativeRow.get("type_share"), modelRow.get("typeShare"));
        }
    }

    @Test
    @DisplayName("CALCULATE 占比允许外层 ROUND 标量包装")
    void calculateRatioAllowsScalarRoundWrapper() {
        assumeGroupedAggregateWindowSupported();
        DbQueryRequestDef request = baseRequest(
                List.of("customer$customerType", "roundedShare"),
                List.of("customer$customerType"),
                List.of(new CalculatedFieldDef(
                        "roundedShare",
                        "四位占比",
                        "ROUND(SUM(salesAmount) / NULLIF(CALCULATE(SUM(salesAmount), REMOVE(customer$customerType)), 0), 4)"
                ))
        );

        JdbcModelQueryEngine engine = analyze(request);
        String sql = engine.getSql();
        assertTrue(sql.toUpperCase().contains("ROUND("), "应保留外层 ROUND 标量包装: " + sql);
        assertTrue(sql.toUpperCase().contains("OVER ()"), "ROUND 内部应仍生成全局窗口: " + sql);
        assertTrue(jdbcTemplate.queryForList(sql, engine.getValues().toArray()).size() > 0);
    }

    @Test
    @DisplayName("CALCULATE 比率分母必须使用 NULLIF(CALCULATE(...), 0)")
    void calculateRatioRequiresNullif() {
        DbQueryRequestDef request = baseRequest(
                List.of("customer$customerType", "badShare"),
                List.of("customer$customerType"),
                List.of(new CalculatedFieldDef(
                        "badShare",
                        "错误占比",
                        "SUM(salesAmount) / CALCULATE(SUM(salesAmount), REMOVE(customer$customerType))"
                ))
        );

        RuntimeException ex = assertThrows(RuntimeException.class, () -> analyze(request));
        assertExceptionContains(ex, "CALCULATE_RATIO_REQUIRES_NULLIF");
    }

    @Test
    @DisplayName("REMOVE 只能移除当前 groupBy 中存在的维度")
    void calculateRemoveRequiresCurrentGroupByField() {
        assumeGroupedAggregateWindowSupported();
        DbQueryRequestDef request = baseRequest(
                List.of("customer$customerType", "badShare"),
                List.of("customer$customerType"),
                List.of(new CalculatedFieldDef(
                        "badShare",
                        "错误占比",
                        "SUM(salesAmount) / NULLIF(CALCULATE(SUM(salesAmount), REMOVE(product$categoryName)), 0)"
                ))
        );

        RuntimeException ex = assertThrows(RuntimeException.class, () -> analyze(request));
        assertExceptionContains(ex, "CALCULATE_REMOVE_FIELD_NOT_GROUPED");
    }

    @Test
    @DisplayName("CALCULATE 不允许移除 systemSlice 覆盖字段")
    void calculateCannotRemoveSystemSliceField() {
        assumeGroupedAggregateWindowSupported();
        DbQueryRequestDef request = baseRequest(
                List.of("customer$customerType", "badShare"),
                List.of("customer$customerType"),
                List.of(new CalculatedFieldDef(
                        "badShare",
                        "错误占比",
                        "SUM(salesAmount) / NULLIF(CALCULATE(SUM(salesAmount), REMOVE(customer$customerType)), 0)"
                ))
        );

        ModelResultContext context = new ModelResultContext();
        context.setRequest(PagingRequest.buildPagingRequest(request, 100));
        context.setSystemSlice(List.of(new SliceRequestDef("customer$customerType", "=", "会员")));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> analyze(context));
        assertExceptionContains(ex, "CALCULATE_SYSTEM_SLICE_OVERRIDE_DENIED");
    }

    @Test
    @DisplayName("嵌套 CALCULATE 被结构分析器拒绝")
    void nestedCalculateIsRejected() {
        DbQueryRequestDef request = baseRequest(
                List.of("customer$customerType", "badShare"),
                List.of("customer$customerType"),
                List.of(new CalculatedFieldDef(
                        "badShare",
                        "错误占比",
                        "CALCULATE(SUM(salesAmount), REMOVE(customer$customerType)) + "
                                + "CALCULATE(CALCULATE(SUM(salesAmount), REMOVE(customer$customerType)), REMOVE(customer$customerType))"
                ))
        );

        RuntimeException ex = assertThrows(RuntimeException.class, () -> analyze(request));
        assertExceptionContains(ex, "CALCULATE_NESTED_UNSUPPORTED");
    }

    @Test
    @DisplayName("不支持分组聚合窗口能力时 CALCULATE 显式失败")
    void calculateFailsWhenGroupedAggregateWindowUnsupported() {
        JdbcQueryModel queryModel = getQueryModel("FactSalesQueryModel");
        SqlCalculatedFieldProcessor processor =
                new SqlCalculatedFieldProcessor(queryModel, queryModel.getDialect());
        processor.setCalculateQueryContext(new CalculateQueryContext(
                List.of("customer$customerType"),
                Set.of(),
                false,
                false
        ));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> processor.processCalculatedField(
                new CalculatedFieldDef(
                        "badShare",
                        "错误占比",
                        "CALCULATE(SUM(salesAmount), REMOVE(customer$customerType))"
                ),
                appCtx
        ));
        assertExceptionContains(ex, "CALCULATE_WINDOW_UNSUPPORTED");
    }

    @Test
    @DisplayName("MySQL 5.7 runtime capability 通过引擎显式 fail-closed")
    void calculateFailsClosedForRuntimeUnsupportedDatabase() throws Exception {
        JdbcQueryModel runtimeQueryModel = Mockito.spy(getQueryModel("FactSalesQueryModel"));
        DataSource mysql57DataSource = Mockito.mock(DataSource.class);
        Connection connection = Mockito.mock(Connection.class);
        DatabaseMetaData metadata = Mockito.mock(DatabaseMetaData.class);
        Mockito.when(mysql57DataSource.getConnection()).thenReturn(connection);
        Mockito.when(connection.getMetaData()).thenReturn(metadata);
        Mockito.when(metadata.getDatabaseProductName()).thenReturn("MySQL");
        Mockito.when(metadata.getDatabaseMajorVersion()).thenReturn(5);
        Mockito.doReturn(FDialect.MYSQL_DIALECT).when(runtimeQueryModel).getDialect();
        Mockito.doReturn(mysql57DataSource).when(runtimeQueryModel).getDataSource();

        DbQueryRequestDef request = baseRequest(
                List.of("customer$customerType", "totalShare"),
                List.of("customer$customerType"),
                List.of(new CalculatedFieldDef(
                        "totalShare",
                        "总占比",
                        "SUM(salesAmount) / NULLIF(CALCULATE(SUM(salesAmount), REMOVE(customer$customerType)), 0)"
                ))
        );

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> analyze(request, runtimeQueryModel));
        assertExceptionContains(ex, "CALCULATE_WINDOW_UNSUPPORTED");
    }

    @Test
    @DisplayName("CALCULATE 依赖隐藏度量时被 fieldAccess 拒绝")
    void calculateMetricDependencyDeniedByFieldAccess() {
        DbQueryRequestDef request = baseRequest(
                List.of("customer$customerType", "totalShare"),
                List.of("customer$customerType"),
                List.of(new CalculatedFieldDef(
                        "totalShare",
                        "总占比",
                        "SUM(salesAmount) / NULLIF(CALCULATE(SUM(salesAmount), REMOVE(customer$customerType)), 0)"
                ))
        );

        ModelResultContext context = new ModelResultContext();
        context.setRequest(PagingRequest.buildPagingRequest(request, 100));
        context.setFieldAccess(Set.of("customer", "totalShare"));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> queryFacade.queryModelResult(context));
        assertExceptionContains(ex, "salesAmount");
    }

    @Test
    @DisplayName("CALCULATE 依赖被 deniedColumns 映射的物理列时被拒绝")
    void calculateMetricDependencyDeniedByPhysicalColumn() {
        DbQueryRequestDef request = baseRequest(
                List.of("customer$customerType", "totalShare"),
                List.of("customer$customerType"),
                List.of(new CalculatedFieldDef(
                        "totalShare",
                        "总占比",
                        "SUM(salesAmount) / NULLIF(CALCULATE(SUM(salesAmount), REMOVE(customer$customerType)), 0)"
                ))
        );

        ModelResultContext context = new ModelResultContext();
        context.setRequest(PagingRequest.buildPagingRequest(request, 100));
        context.setDeniedColumns(List.of(
                new DeniedPhysicalColumn(null, "fact_sales", "sales_amount")
        ));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> queryFacade.queryModelResult(context));
        assertExceptionContains(ex, "salesAmount");
    }

    @Test
    @DisplayName("文档 parity catalog 可执行并覆盖 Java CALCULATE 行为")
    void parityCatalogCasesStayExecutable() throws Exception {
        JsonNode cases = new ObjectMapper().readTree(resolveParityCatalogPath().toFile()).get("cases");
        assertNotNull(cases, "parity catalog cases missing");

        for (JsonNode item : cases) {
            String id = item.get("id").asText();
            String expression = item.get("expression").asText();
            List<String> groupBy = stringList(item.path("groupBy"));
            String expectedError = item.path("expectError").isMissingNode()
                    || item.path("expectError").isNull()
                    ? null
                    : item.path("expectError").asText();

            if ("calculate_mysql_57_window_unsupported".equals(id)) {
                assertCatalogProcessorError(id, expression, groupBy, expectedError, false, false);
                continue;
            }
            if (item.path("timeWindowPostCalculatedFields").asBoolean(false)) {
                assertCatalogProcessorError(id, expression, groupBy, expectedError, true, true);
                continue;
            }
            if (!supportsWindowFunctions()) {
                continue;
            }

            DbQueryRequestDef request = baseRequest(
                    withCalculatedAlias(groupBy, "catalogCalc"),
                    groupBy,
                    List.of(new CalculatedFieldDef("catalogCalc", id, expression))
            );
            ModelResultContext context = new ModelResultContext();
            context.setRequest(PagingRequest.buildPagingRequest(request, 100));
            if (item.has("systemSliceFields")) {
                context.setSystemSlice(List.of(new SliceRequestDef("customer$customerType", "=", "会员")));
            }

            if (expectedError != null) {
                RuntimeException ex = assertThrows(RuntimeException.class,
                        () -> analyze(context),
                        id + " should fail with " + expectedError);
                assertExceptionContains(ex, expectedError);
                continue;
            }

            JdbcModelQueryEngine engine = analyze(context);
            String normalizedSql = normalizeSqlForCatalog(engine.getSql());
            for (JsonNode fragmentNode : item.path("expectSqlContains")) {
                String fragment = normalizeSqlForCatalog(fragmentNode.asText());
                assertTrue(normalizedSql.contains(fragment),
                        id + " SQL should contain catalog fragment <" + fragment + "> but was: "
                                + normalizedSql);
            }
        }
    }

    private JdbcModelQueryEngine analyze(DbQueryRequestDef request) {
        ModelResultContext context = new ModelResultContext();
        context.setRequest(PagingRequest.buildPagingRequest(request, 100));
        return analyze(context);
    }

    private JdbcModelQueryEngine analyze(DbQueryRequestDef request, JdbcQueryModel queryModel) {
        ModelResultContext context = new ModelResultContext();
        context.setRequest(PagingRequest.buildPagingRequest(request, 100));
        return analyze(context, queryModel);
    }

    private JdbcModelQueryEngine analyze(ModelResultContext context) {
        DbQueryRequestDef request = context.getRequest().getParam();
        JdbcQueryModel queryModel = getQueryModel(request.getQueryModel());
        assertNotNull(queryModel, "查询模型加载失败");
        return analyze(context, queryModel);
    }

    private JdbcModelQueryEngine analyze(ModelResultContext context, JdbcQueryModel queryModel) {
        JdbcModelQueryEngine engine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);
        engine.analysisQueryRequest(systemBundlesContext, context);
        return engine;
    }

    private DbQueryRequestDef baseRequest(List<String> columns,
                                          List<String> groupBy,
                                          List<CalculatedFieldDef> calculatedFields) {
        DbQueryRequestDef request = new DbQueryRequestDef();
        request.setQueryModel("FactSalesQueryModel");
        request.setColumns(new ArrayList<>(columns));
        request.setCalculatedFields(new ArrayList<>(calculatedFields));
        request.setGroupBy(new ArrayList<>(groupBy.stream().map(this::group).toList()));
        request.setOrderBy(new ArrayList<>(groupBy.stream().map(this::orderAsc).toList()));
        return request;
    }

    private GroupRequestDef group(String field) {
        GroupRequestDef group = new GroupRequestDef();
        group.setField(field);
        return group;
    }

    private OrderRequestDef orderAsc(String field) {
        OrderRequestDef order = new OrderRequestDef();
        order.setField(field);
        order.setDir("ASC");
        return order;
    }

    private void assertDecimalEquals(Object expected, Object actual) {
        BigDecimal left = toBigDecimal(expected);
        BigDecimal right = toBigDecimal(actual);
        assertTrue(left.subtract(right).abs().compareTo(new BigDecimal("0.000001")) <= 0,
                "expected <" + left + "> but was <" + right + ">");
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return new BigDecimal(number.toString());
        }
        return new BigDecimal(value.toString());
    }

    private void assumeGroupedAggregateWindowSupported() {
        Assumptions.assumeTrue(supportsWindowFunctions(),
                "Runtime database does not support grouped aggregate windows for restricted CALCULATE");
    }

    private void assertExceptionContains(Throwable throwable, String expected) {
        Throwable current = throwable;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && message.contains(expected)) {
                return;
            }
            current = current.getCause();
        }
        throw new AssertionError("Expected exception chain to contain " + expected, throwable);
    }

    private void assertCatalogProcessorError(String id,
                                             String expression,
                                             List<String> groupBy,
                                             String expectedError,
                                             boolean supportsGroupedAggregateWindow,
                                             boolean timeWindowPostCalculatedFields) {
        JdbcQueryModel queryModel = getQueryModel("FactSalesQueryModel");
        SqlCalculatedFieldProcessor processor =
                new SqlCalculatedFieldProcessor(queryModel, queryModel.getDialect());
        processor.setCalculateQueryContext(new CalculateQueryContext(
                groupBy,
                Set.of(),
                supportsGroupedAggregateWindow,
                timeWindowPostCalculatedFields
        ));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> processor.processCalculatedField(
                new CalculatedFieldDef("catalogCalc", id, expression),
                appCtx
        ));
        assertExceptionContains(ex, expectedError);
    }

    private Path resolveParityCatalogPath() {
        List<Path> candidates = List.of(
                Paths.get("docs", "v1.5.1", "P1-CALCULATE-restricted-mvp-parity-catalog.json"),
                Paths.get("..", "docs", "v1.5.1", "P1-CALCULATE-restricted-mvp-parity-catalog.json"),
                Paths.get("..", "..", "docs", "v1.5.1", "P1-CALCULATE-restricted-mvp-parity-catalog.json")
        );
        for (Path candidate : candidates) {
            if (Files.exists(candidate)) {
                return candidate;
            }
        }
        throw new AssertionError("Cannot locate P1-CALCULATE parity catalog from working directory");
    }

    private List<String> stringList(JsonNode arrayNode) {
        List<String> result = new ArrayList<>();
        if (arrayNode == null || !arrayNode.isArray()) {
            return result;
        }
        for (JsonNode node : arrayNode) {
            result.add(node.asText());
        }
        return result;
    }

    private List<String> withCalculatedAlias(List<String> groupBy, String alias) {
        List<String> columns = new ArrayList<>(groupBy);
        columns.add(alias);
        return columns;
    }

    private String normalizeSqlForCatalog(String sql) {
        return sql
                .replaceAll("\\b\\w+\\.sales_amount\\b", "metric.sales_amount")
                .replaceAll("\\b\\w+\\.customer_type\\b", "dim.customer_type")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
