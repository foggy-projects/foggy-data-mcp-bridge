package com.foggyframework.dataset.db.model.parity;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.dataset.client.domain.PagingRequest;
import com.foggyframework.dataset.db.model.def.query.request.CalculatedFieldDef;
import com.foggyframework.dataset.db.model.def.query.request.CondRequestDef;
import com.foggyframework.dataset.db.model.def.query.request.DbQueryRequestDef;
import com.foggyframework.dataset.db.model.def.query.request.OrderRequestDef;
import com.foggyframework.dataset.db.model.def.query.request.SliceRequestDef;
import com.foggyframework.dataset.db.model.ecommerce.EcommerceTestSupport;
import com.foggyframework.dataset.db.model.engine.JdbcModelQueryEngine;
import com.foggyframework.dataset.db.model.engine.formula.SqlFormulaService;
import com.foggyframework.dataset.db.model.engine.query.DbQueryResult;
import com.foggyframework.dataset.db.model.impl.model.AggregateJoinTableModel;
import com.foggyframework.dataset.db.model.impl.model.AggregateRelationDiagnostic;
import com.foggyframework.dataset.db.model.impl.model.AggregateRelationOutputColumn;
import com.foggyframework.dataset.db.model.impl.model.AggregateRelationQueryObject;
import com.foggyframework.dataset.db.model.plugins.result_set_filter.ModelResultContext;
import com.foggyframework.dataset.db.model.proxy.AggregateJoinBuilder;
import com.foggyframework.dataset.db.model.proxy.TableModelProxy;
import com.foggyframework.dataset.db.model.semantic.domain.DeniedPhysicalColumn;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticQueryRequest;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticQueryResponse;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticRequestContext;
import com.foggyframework.dataset.db.model.semantic.service.SemanticQueryServiceV3;
import com.foggyframework.dataset.db.model.service.QueryFacade;
import com.foggyframework.dataset.db.model.spi.DbColumn;
import com.foggyframework.dataset.db.model.spi.DbQueryColumn;
import com.foggyframework.dataset.db.model.spi.JdbcQueryModel;
import com.foggyframework.dataset.db.model.spi.QueryObject;
import com.foggyframework.dataset.db.model.spi.TableModel;
import com.foggyframework.dataset.model.PagingResultImpl;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * QueryModel aggregate join neutral snapshot producer for Python parity.
 *
 * <p>Produces {@code target/parity/_querymodel_aggregate_join_snapshot.json}.
 * The checked-in Python contract fixture defines the expected envelope; this
 * test exports the Java evidence that Python should replay before implementing
 * aggregate relation SQL lowering.</p>
 */
@DisplayName("JavaQueryModelAggregateJoinSnapshotTest")
class JavaQueryModelAggregateJoinSnapshotTest extends EcommerceTestSupport {

    @Resource
    private SqlFormulaService sqlFormulaService;

    @Resource
    private SystemBundlesContext systemBundlesContext;

    @Resource
    private QueryFacade queryFacade;

    @Resource
    private SemanticQueryServiceV3 semanticQueryService;

    @Test
    @DisplayName("produces _querymodel_aggregate_join_snapshot.json")
    void shouldProduceSnapshot() throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean("foggy.parity.snapshot"),
                "set -Dfoggy.parity.snapshot=true to export aggregate join parity snapshot");

        String matchedOrderId = findOrderIdWithCompletedSales();
        String unmatchedOrderId = findOrderIdWithoutCompletedSales();

        List<Map<String, Object>> cases = List.of(
                leftMeasureNonMultiplicationCase(matchedOrderId),
                sqlShapeCase(),
                missingRightKeyGroupByRefusalCase(),
                fixedRhsFilterCase(),
                runtimeExtDataFilterCase(matchedOrderId),
                runtimeExtDataMissingRefusalCase(),
                andPushdownDiagnosticsCase(matchedOrderId, unmatchedOrderId),
                orOuterOnlyDiagnosticsCase(matchedOrderId, unmatchedOrderId),
                deniedSourceColumnRefusalCase(matchedOrderId),
                fieldAccessAllowOutputCase(matchedOrderId),
                fieldAccessDenyOutputRefusalCase(matchedOrderId),
                systemSliceGuardBypassNoLeakCase(matchedOrderId),
                deniedSourceColumnUnreferencedPassCase(matchedOrderId),
                calculatedFieldDeniedSourceRefusalCase(matchedOrderId),
                calculatedFieldChainDeniedSourceRefusalCase(matchedOrderId),
                predefinedCalculatedFieldDeniedSourceRefusalCase(matchedOrderId),
                predefinedCalculatedFieldAllowedExecCase(matchedOrderId),
                rawSqlAccessBuilderOuterOnlyCase(),
                orderByAggregateOutputCase(),
                returnTotalAggregateRelationCase(matchedOrderId),
                nullCheckOuterOnlyCase("is null"),
                nullCheckOuterOnlyCase("is not null"),
                semanticDebugExtraDiagnosticsCase(matchedOrderId, unmatchedOrderId),
                compositeKeyAggregateRelationCase(),
                structuredAccessBuilderPushdownCase(),
                runtimeFilterUnsafeCharacterRefusalCase(),
                leftDimensionKeyCase(),
                rhsDimensionFixedFilterCase(),
                o615NoColumnsWithAccessCase(),
                o615ExplicitJoinNoColumnsCase(),
                o615TenantGuardNoLeakCase(),
                o615DimensionIdSliceCase(),
                o615RhsDimensionFilterCase(),
                o615RhsJoinDimensionFilterCase(),
                metadataLineageCase());

        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("schemaVersion", 1);
        snapshot.put("feature", "queryModelAggregateJoin");
        snapshot.put("source", "JavaQueryModelAggregateJoinSnapshotTest");
        snapshot.put("contractVersion", "querymodel-aggregate-join-4");
        snapshot.put("generatedAt", new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").format(new Date()));
        snapshot.put("dialect", getDialectKey());
        snapshot.put("cases", cases);

        ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        Path target = Path.of("target", "parity", "_querymodel_aggregate_join_snapshot.json");
        Files.createDirectories(target.getParent());
        mapper.writeValue(target.toFile(), snapshot);

        assertTrue(Files.exists(target), "snapshot file must be written");
        assertEquals(35, cases.size(), "expected aggregate join contract case count");
    }

    private Map<String, Object> leftMeasureNonMultiplicationCase(String orderId) {
        JdbcModelQueryEngine queryEngine = buildAggregateJoinQuery(orderId);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                queryEngine.getSql(),
                queryEngine.getValues().toArray());
        assertEquals(1, rows.size(), "aggregate join should keep the left row grain");

        BigDecimal nativeOrderAmount = jdbcTemplate.queryForObject(
                "select total_amount from fact_order where order_id = ?",
                BigDecimal.class,
                orderId);
        BigDecimal nativeSalesAmount = jdbcTemplate.queryForObject(
                "select sum(sales_amount) from fact_sales where order_id = ? and order_status = 'COMPLETED'",
                BigDecimal.class,
                orderId);
        Long nativeLineCount = jdbcTemplate.queryForObject(
                "select count(*) from fact_sales where order_id = ? and order_status = 'COMPLETED'",
                Long.class,
                orderId);

        Map<String, Object> expected = new LinkedHashMap<>();
        expected.put("rows", rows);
        expected.put("nativeOrderAmount", nativeOrderAmount);
        expected.put("nativeSalesAmount", nativeSalesAmount);
        expected.put("nativeLineCount", nativeLineCount);
        expected.put("leftMeasureNonMultiplicationField", "amount");
        expected.put("aggregateOutputFields", List.of("salesAggAmount", "salesLineCount"));
        expected.put("params", queryEngine.getValues());
        expected.put("sqlMarkers", List.of("left join", "(select", "sum(", "count(*)", "group by"));

        return caseMap(
                "aggregate-join-left-measure-not-multiplied",
                "result",
                "OrderSalesAggregateJoinQueryModel",
                requestMap("OrderSalesAggregateJoinQueryModel",
                        List.of("orderId", "amount", "salesAggAmount", "salesLineCount"),
                        List.of(sliceMap("orderId", "=", orderId))),
                expected);
    }

    private Map<String, Object> sqlShapeCase() {
        JdbcModelQueryEngine queryEngine = buildAggregateJoinQuery(null);
        String normalizedSql = normalizeSql(queryEngine.getSql()).toLowerCase();
        assertTrue(normalizedSql.contains("left join"), "aggregate join should render LEFT JOIN");
        assertTrue(normalizedSql.contains("(select"), "right side should be a derived aggregate query");
        assertFalse(normalizedSql.contains("count(distinct"), "unrequested aggregate should be pruned");

        Map<String, Object> expected = new LinkedHashMap<>();
        expected.put("sql", queryEngine.getSql());
        expected.put("normalizedSql", normalizedSql);
        expected.put("params", queryEngine.getValues());
        expected.put("sqlMarkers", List.of("left join", "(select", "sum(", "count(*)", "group by", "fact_sales"));
        expected.put("forbiddenSqlMarkers", List.of("count(distinct", ".salesAmount"));

        return caseMap(
                "aggregate-join-sql-shape-sqlite",
                "sql",
                "OrderSalesAggregateJoinQueryModel",
                requestMap("OrderSalesAggregateJoinQueryModel",
                        List.of("orderId", "amount", "salesAggAmount", "salesLineCount"),
                        List.of()),
                expected);
    }

    private Map<String, Object> missingRightKeyGroupByRefusalCase() {
        TableModelProxy fo = new TableModelProxy("FactOrderModel");
        TableModelProxy fs = new TableModelProxy("FactSalesModel");
        AggregateJoinBuilder builder = (AggregateJoinBuilder) fo.invoke(null, "leftJoinAggregate", new Object[]{fs});
        builder.invoke(null, "groupBy", new Object[]{fs.getProperty("orderLineNo")});
        builder.invoke(null, "sum", new Object[]{fs.getProperty("salesAmount"), "salesAggAmount"});
        builder.invoke(null, "on", new Object[]{fo.getProperty("orderId"), fs.getProperty("orderId")});

        TableModel salesModel = tableModelLoaderManager.load("FactSalesModel");
        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> AggregateJoinTableModel.from(salesModel, builder));
        assertTrue(exception.getMessage().contains("groupBy"), "error should point at groupBy");

        Map<String, Object> expected = new LinkedHashMap<>();
        expected.put("errorCode", "QUERYMODEL_AGGREGATE_JOIN_GROUPBY_MISSING_RIGHT_KEY");
        expected.put("message", exception.getMessage());
        expected.put("messageMarkers", List.of("groupBy", "orderId"));

        return caseMap(
                "aggregate-join-missing-right-key-groupby-refusal",
                "error",
                "OrderSalesAggregateJoinQueryModel",
                Map.of("invalidGroupBy", List.of("orderLineNo"), "joinKey", "orderId"),
                expected);
    }

    private Map<String, Object> fixedRhsFilterCase() {
        JdbcModelQueryEngine queryEngine = buildAggregateRelationQuery(null, null);
        String normalizedSql = normalizeSql(queryEngine.getSql());
        assertTrue(normalizedSql.contains("agg_src.order_status = ?"),
                "fixed RHS filter should be pushed before aggregation");

        Map<String, Object> expected = new LinkedHashMap<>();
        expected.put("sql", queryEngine.getSql());
        expected.put("normalizedSql", normalizedSql);
        expected.put("params", queryEngine.getValues());
        expected.put("sqlMarkers", List.of("fsByOrder", "agg_src.order_status = ?", "group by", "sum(agg_src.sales_amount)"));
        expected.put("forbiddenSqlMarkers", List.of("sum(agg_src.quantity) quantity"));
        expected.put("diagnostics", diagnostics(queryEngine));

        return caseMap(
                "aggregate-join-fixed-rhs-filter",
                "sql",
                "OrderSalesAggregateRelationQueryModel",
                requestMap("OrderSalesAggregateRelationQueryModel",
                        List.of("orderId", "amount", "salesAmount", "uniqueCustomers"),
                        List.of()),
                expected);
    }

    private Map<String, Object> runtimeExtDataFilterCase(String orderId) {
        JdbcModelQueryEngine queryEngine = buildRuntimeFilterQuery(Map.of("orderId", orderId), orderId);
        String normalizedSql = normalizeSql(queryEngine.getSql());
        assertTrue(normalizedSql.contains("agg_src.order_id = ?"),
                "runtime extData filter should render inside RHS WHERE");

        Map<String, Object> expected = new LinkedHashMap<>();
        expected.put("sql", queryEngine.getSql());
        expected.put("normalizedSql", normalizedSql);
        expected.put("params", queryEngine.getValues());
        expected.put("sqlMarkers", List.of("agg_src.order_id = ?", "agg_src.order_status = ?", "group by"));
        expected.put("forbiddenSqlMarkers", List.of("ctx.extData"));
        expected.put("rows", jdbcTemplate.queryForList(queryEngine.getSql(), queryEngine.getValues().toArray()));
        expected.put("diagnostics", diagnostics(queryEngine));

        return caseMap(
                "aggregate-join-runtime-extdata-filter",
                "sql",
                "OrderSalesAggregateRelationRuntimeFilterQueryModel",
                requestMap("OrderSalesAggregateRelationRuntimeFilterQueryModel",
                        List.of("orderId", "amount", "salesAmount", "uniqueCustomers"),
                        List.of(sliceMap("orderId", "=", orderId))),
                expected);
    }

    private Map<String, Object> runtimeExtDataMissingRefusalCase() {
        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> buildRuntimeFilterQuery(null, null));
        assertTrue(exception.getMessage().contains("runtime filter"));

        Map<String, Object> expected = new LinkedHashMap<>();
        expected.put("errorCode", "QUERYMODEL_AGGREGATE_JOIN_RUNTIME_FILTER_MISSING");
        expected.put("message", exception.getMessage());
        expected.put("messageMarkers", List.of("runtime filter"));

        return caseMap(
                "aggregate-join-runtime-extdata-missing-refusal",
                "error",
                "OrderSalesAggregateRelationRuntimeFilterQueryModel",
                requestMap("OrderSalesAggregateRelationRuntimeFilterQueryModel",
                        List.of("orderId", "amount", "salesAmount", "uniqueCustomers"),
                        List.of()),
                expected);
    }

    private Map<String, Object> andPushdownDiagnosticsCase(String matchedOrderId, String unmatchedOrderId) {
        JdbcModelQueryEngine queryEngine = buildAggregateRelationQuery(
                null,
                List.of(
                        slice("orderId", "in", List.of(matchedOrderId, unmatchedOrderId)),
                        slice("salesAmount", "[]", List.of(BigDecimal.ZERO, new BigDecimal("999999999")))));
        String normalizedSql = normalizeSql(queryEngine.getSql());
        assertTrue(normalizedSql.contains("agg_src.order_id in (?, ?)"));
        assertTrue(normalizedSql.contains("having sum(agg_src.sales_amount) >= ? and sum(agg_src.sales_amount) <= ?"));

        List<Map<String, Object>> diagnostics = diagnostics(queryEngine);
        assertTrue(diagnostics.stream().anyMatch(d -> "pushed".equals(d.get("decision"))
                && "where".equals(d.get("target")) && "orderId".equals(d.get("field"))));
        assertTrue(diagnostics.stream().anyMatch(d -> "pushed".equals(d.get("decision"))
                && "having".equals(d.get("target")) && "salesAmount".equals(d.get("field"))));

        Map<String, Object> expected = new LinkedHashMap<>();
        expected.put("sql", queryEngine.getSql());
        expected.put("normalizedSql", normalizedSql);
        expected.put("params", queryEngine.getValues());
        expected.put("sqlMarkers", List.of("agg_src.order_id in (?, ?)", "having sum(agg_src.sales_amount) >= ?"));
        expected.put("forbiddenSqlMarkers", List.of());
        expected.put("diagnostics", diagnostics);

        return caseMap(
                "aggregate-join-and-pushdown-diagnostics",
                "sql",
                "OrderSalesAggregateRelationQueryModel",
                requestMap("OrderSalesAggregateRelationQueryModel",
                        List.of("orderId", "amount", "salesAmount", "uniqueCustomers"),
                        List.of(
                                sliceMap("orderId", "in", List.of(matchedOrderId, unmatchedOrderId)),
                                sliceMap("salesAmount", "[]", List.of(BigDecimal.ZERO, new BigDecimal("999999999"))))),
                expected);
    }

    private Map<String, Object> orOuterOnlyDiagnosticsCase(String matchedOrderId, String unmatchedOrderId) {
        JdbcModelQueryEngine queryEngine = buildAggregateRelationQuery(
                null,
                List.of(SliceRequestDef.or(List.of(
                        condition("orderId", "=", matchedOrderId),
                        condition("orderId", "=", unmatchedOrderId)))));
        String normalizedSql = normalizeSql(queryEngine.getSql());
        assertFalse(normalizedSql.contains("agg_src.order_id = ?"),
                "OR join key predicates must stay outer-only");

        List<Map<String, Object>> diagnostics = diagnostics(queryEngine);
        assertTrue(diagnostics.stream().anyMatch(d -> "retained".equals(d.get("decision"))
                && "orderId".equals(d.get("field"))));

        Map<String, Object> expected = new LinkedHashMap<>();
        expected.put("sql", queryEngine.getSql());
        expected.put("normalizedSql", normalizedSql);
        expected.put("params", queryEngine.getValues());
        expected.put("sqlMarkers", List.of(" or ", "t1.order_id ="));
        expected.put("forbiddenSqlMarkers", List.of("agg_src.order_id = ?"));
        expected.put("diagnostics", diagnostics);

        return caseMap(
                "aggregate-join-or-outer-only-diagnostics",
                "sql",
                "OrderSalesAggregateRelationQueryModel",
                Map.of("queryModel", "OrderSalesAggregateRelationQueryModel", "sliceShape", "OR(orderId, orderId)"),
                expected);
    }

    private Map<String, Object> deniedSourceColumnRefusalCase(String orderId) {
        DbQueryRequestDef queryRequest = aggregateRelationRequest();
        queryRequest.setSlice(List.of(slice("orderId", "=", orderId)));

        ModelResultContext context = queryFacadeContext(queryRequest);
        context.setDeniedColumns(List.of(new DeniedPhysicalColumn(null, "fact_sales", "sales_amount")));

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> queryFacade.queryModelResult(context));
        assertTrue(exception.getMessage().contains("salesAmount"));

        DbQueryRequestDef calculatedFieldRequest = aggregateRelationRequest();
        calculatedFieldRequest.setColumns(List.of("orderId", "amount", "salesAmountWithTax"));
        calculatedFieldRequest.setCalculatedFields(List.of(new CalculatedFieldDef(
                "salesAmountWithTax",
                "taxed sales amount",
                "salesAmount * 1.1")));
        calculatedFieldRequest.setSlice(List.of(slice("orderId", "=", orderId)));

        ModelResultContext calculatedFieldContext = queryFacadeContext(calculatedFieldRequest);
        calculatedFieldContext.setDeniedColumns(List.of(new DeniedPhysicalColumn(null, "fact_sales", "sales_amount")));
        RuntimeException calculatedFieldException = assertThrows(RuntimeException.class,
                () -> queryFacade.queryModelResult(calculatedFieldContext));
        assertTrue(calculatedFieldException.getMessage().contains("salesAmount"));

        Map<String, Object> expected = new LinkedHashMap<>();
        expected.put("errorCode", "QUERYMODEL_AGGREGATE_JOIN_DENIED_SOURCE_COLUMN");
        expected.put("message", exception.getMessage());
        expected.put("calculatedFieldMessage", calculatedFieldException.getMessage());
        expected.put("messageMarkers", List.of("salesAmount", "fact_sales", "sales_amount"));

        return caseMap(
                "aggregate-join-denied-source-column-refusal",
                "error",
                "OrderSalesAggregateRelationQueryModel",
                requestMap("OrderSalesAggregateRelationQueryModel",
                        List.of("orderId", "amount", "salesAmount", "uniqueCustomers"),
                        List.of(sliceMap("orderId", "=", orderId))),
                expected);
    }

    private Map<String, Object> metadataLineageCase() {
        JdbcQueryModel queryModel = getQueryModel("OrderSalesAggregateRelationQueryModel");
        assertNotNull(queryModel, "query model should load");

        DbColumn salesAmount = queryModel.findJdbcColumnForCond("salesAmount", true, true);
        DbColumn uniqueCustomers = queryModel.findJdbcColumnForCond("uniqueCustomers", true, true);
        assertTrue(salesAmount instanceof AggregateRelationOutputColumn);
        assertTrue(uniqueCustomers instanceof AggregateRelationOutputColumn);

        DbQueryColumn salesAmountQueryColumn = queryModel.findJdbcQueryColumnByName("salesAmount", true);
        DbQueryColumn uniqueCustomersQueryColumn = queryModel.findJdbcQueryColumnByName("uniqueCustomers", true);

        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("salesAmount", aggregateColumnMetadata(salesAmount, salesAmountQueryColumn));
        fields.put("uniqueCustomers", aggregateColumnMetadata(uniqueCustomers, uniqueCustomersQueryColumn));

        Map<String, Object> expected = new LinkedHashMap<>();
        expected.put("fields", fields);
        expected.put("aggregateRelation", List.of(
                "aggregation",
                "sourceCaption",
                "sourceMeasure",
                "sourceAlias",
                "sourceExpression",
                "aggregateExpression",
                "sourceColumn"));

        return caseMap(
                "aggregate-join-metadata-lineage",
                "metadata",
                "OrderSalesAggregateRelationQueryModel",
                Map.of("queryModel", "OrderSalesAggregateRelationQueryModel", "fields", fields.keySet()),
                expected);
    }

    private Map<String, Object> fieldAccessAllowOutputCase(String orderId) {
        DbQueryRequestDef queryRequest = aggregateRelationRequest();
        queryRequest.setSlice(List.of(slice("orderId", "=", orderId)));

        ModelResultContext context = queryFacadeContext(queryRequest);
        context.setFieldAccess(Set.of("orderId", "amount", "salesAmount", "uniqueCustomers"));

        DbQueryResult result = queryFacade.queryModelResult(context);
        JdbcModelQueryEngine queryEngine = (JdbcModelQueryEngine) result.getQueryEngine();
        String normalizedSql = normalizeSql(queryEngine.getSql());
        assertTrue(normalizedSql.contains("fsByOrder.salesAmount"),
                "allowed aggregate relation output should participate in SQL");

        Map<String, Object> expected = new LinkedHashMap<>();
        expected.put("sql", queryEngine.getSql());
        expected.put("normalizedSql", normalizedSql);
        expected.put("params", queryEngine.getValues());
        expected.put("rows", rows(result));
        expected.put("sqlMarkers", List.of("fsByOrder.salesAmount", "fsByOrder.uniqueCustomers"));
        expected.put("forbiddenSqlMarkers", List.of());
        expected.put("fieldAccess", List.of("orderId", "amount", "salesAmount", "uniqueCustomers"));

        return caseMap(
                "aggregate-join-field-access-allow-output",
                "sql",
                "OrderSalesAggregateRelationQueryModel",
                requestMap("OrderSalesAggregateRelationQueryModel",
                        List.of("orderId", "amount", "salesAmount", "uniqueCustomers"),
                        List.of(sliceMap("orderId", "=", orderId))),
                expected);
    }

    private Map<String, Object> fieldAccessDenyOutputRefusalCase(String orderId) {
        DbQueryRequestDef queryRequest = aggregateRelationRequest();
        queryRequest.setSlice(List.of(slice("orderId", "=", orderId)));

        ModelResultContext context = queryFacadeContext(queryRequest);
        context.setFieldAccess(Set.of("orderId", "amount", "uniqueCustomers"));

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> queryFacade.queryModelResult(context));
        assertTrue(exception.getMessage().contains("salesAmount"));

        Map<String, Object> expected = new LinkedHashMap<>();
        expected.put("errorCode", "QUERYMODEL_AGGREGATE_JOIN_FIELD_ACCESS_DENIED");
        expected.put("message", exception.getMessage());
        expected.put("messageMarkers", List.of("salesAmount"));
        expected.put("fieldAccess", List.of("orderId", "amount", "uniqueCustomers"));

        return caseMap(
                "aggregate-join-field-access-deny-output-refusal",
                "error",
                "OrderSalesAggregateRelationQueryModel",
                requestMap("OrderSalesAggregateRelationQueryModel",
                        List.of("orderId", "amount", "salesAmount", "uniqueCustomers"),
                        List.of(sliceMap("orderId", "=", orderId))),
                expected);
    }

    private Map<String, Object> systemSliceGuardBypassNoLeakCase(String orderId) {
        DbQueryRequestDef queryRequest = aggregateRelationRequest();
        queryRequest.setColumns(List.of("orderId", "amount"));
        queryRequest.setSlice(List.of(slice("orderId", "=", orderId)));

        ModelResultContext context = queryFacadeContext(queryRequest);
        context.setFieldAccess(Set.of("orderId", "amount"));
        context.setSystemSlice(List.of(slice("salesAmount", ">", BigDecimal.ZERO)));

        DbQueryResult result = queryFacade.queryModelResult(context);
        JdbcModelQueryEngine queryEngine = (JdbcModelQueryEngine) result.getQueryEngine();
        String normalizedSql = normalizeSql(queryEngine.getSql());
        assertTrue(normalizedSql.contains("having sum(agg_src.sales_amount) > ?"));
        List<Map<String, Object>> rows = rows(result);
        assertTrue(rows.stream().noneMatch(row -> row.containsKey("salesAmount")),
                "system_slice guard field should not leak into result columns");

        Map<String, Object> expected = new LinkedHashMap<>();
        expected.put("sql", queryEngine.getSql());
        expected.put("normalizedSql", normalizedSql);
        expected.put("params", queryEngine.getValues());
        expected.put("rows", rows);
        expected.put("sqlMarkers", List.of("having sum(agg_src.sales_amount) > ?"));
        expected.put("forbiddenSqlMarkers", List.of());
        expected.put("rowsForbiddenFields", List.of("salesAmount", "uniqueCustomers"));
        expected.put("systemSlice", List.of(sliceMap("salesAmount", ">", BigDecimal.ZERO)));
        expected.put("fieldAccess", List.of("orderId", "amount"));

        return caseMap(
                "aggregate-join-system-slice-guard-bypass-no-leak",
                "sql",
                "OrderSalesAggregateRelationQueryModel",
                requestMap("OrderSalesAggregateRelationQueryModel",
                        List.of("orderId", "amount"),
                        List.of(sliceMap("orderId", "=", orderId))),
                expected);
    }

    private Map<String, Object> deniedSourceColumnUnreferencedPassCase(String orderId) {
        DbQueryRequestDef queryRequest = aggregateRelationRequest();
        queryRequest.setSlice(List.of(slice("orderId", "=", orderId)));

        ModelResultContext context = queryFacadeContext(queryRequest);
        context.setDeniedColumns(List.of(new DeniedPhysicalColumn(null, "fact_sales", "profit_amount")));

        DbQueryResult result = queryFacade.queryModelResult(context);
        JdbcModelQueryEngine queryEngine = (JdbcModelQueryEngine) result.getQueryEngine();

        Map<String, Object> expected = new LinkedHashMap<>();
        expected.put("sql", queryEngine.getSql());
        expected.put("normalizedSql", normalizeSql(queryEngine.getSql()));
        expected.put("params", queryEngine.getValues());
        expected.put("rows", rows(result));
        expected.put("sqlMarkers", List.of("left join", "fsByOrder.salesAmount"));
        expected.put("forbiddenSqlMarkers", List.of("profit_amount"));
        expected.put("deniedColumns", List.of(Map.of("table", "fact_sales", "column", "profit_amount")));

        return caseMap(
                "aggregate-join-denied-source-column-unreferenced-pass",
                "sql",
                "OrderSalesAggregateRelationQueryModel",
                requestMap("OrderSalesAggregateRelationQueryModel",
                        List.of("orderId", "amount", "salesAmount", "uniqueCustomers"),
                        List.of(sliceMap("orderId", "=", orderId))),
                expected);
    }

    private Map<String, Object> calculatedFieldDeniedSourceRefusalCase(String orderId) {
        DbQueryRequestDef queryRequest = aggregateRelationRequest();
        queryRequest.setColumns(List.of("orderId", "amount", "salesAmountWithTax"));
        queryRequest.setCalculatedFields(List.of(new CalculatedFieldDef(
                "salesAmountWithTax",
                "taxed sales amount",
                "salesAmount * 1.1")));
        queryRequest.setSlice(List.of(slice("orderId", "=", orderId)));

        RuntimeException exception = deniedSalesAmountException(queryRequest);

        Map<String, Object> expected = new LinkedHashMap<>();
        expected.put("errorCode", "QUERYMODEL_AGGREGATE_JOIN_DENIED_SOURCE_COLUMN");
        expected.put("message", exception.getMessage());
        expected.put("messageMarkers", List.of("salesAmount", "salesAmountWithTax"));
        expected.put("calculatedFields", List.of(Map.of(
                "name", "salesAmountWithTax",
                "expression", "salesAmount * 1.1")));

        return caseMap(
                "aggregate-join-calculated-field-denied-source-refusal",
                "error",
                "OrderSalesAggregateRelationQueryModel",
                requestMap("OrderSalesAggregateRelationQueryModel",
                        List.of("orderId", "amount", "salesAmountWithTax"),
                        List.of(sliceMap("orderId", "=", orderId))),
                expected);
    }

    private Map<String, Object> calculatedFieldChainDeniedSourceRefusalCase(String orderId) {
        DbQueryRequestDef queryRequest = aggregateRelationRequest();
        queryRequest.setColumns(List.of("orderId", "amount", "salesAmountScore"));
        queryRequest.setCalculatedFields(List.of(
                new CalculatedFieldDef("salesAmountWithTax", "taxed sales amount", "salesAmount * 1.1"),
                new CalculatedFieldDef("salesAmountScore", "sales amount score", "salesAmountWithTax + 1")));
        queryRequest.setSlice(List.of(slice("orderId", "=", orderId)));

        RuntimeException exception = deniedSalesAmountException(queryRequest);

        Map<String, Object> expected = new LinkedHashMap<>();
        expected.put("errorCode", "QUERYMODEL_AGGREGATE_JOIN_DENIED_SOURCE_COLUMN");
        expected.put("message", exception.getMessage());
        expected.put("messageMarkers", List.of("salesAmount", "salesAmountWithTax", "salesAmountScore"));
        expected.put("calculatedFields", List.of(
                Map.of("name", "salesAmountWithTax", "expression", "salesAmount * 1.1"),
                Map.of("name", "salesAmountScore", "expression", "salesAmountWithTax + 1")));

        return caseMap(
                "aggregate-join-calculated-field-chain-denied-source-refusal",
                "error",
                "OrderSalesAggregateRelationQueryModel",
                requestMap("OrderSalesAggregateRelationQueryModel",
                        List.of("orderId", "amount", "salesAmountScore"),
                        List.of(sliceMap("orderId", "=", orderId))),
                expected);
    }

    private Map<String, Object> predefinedCalculatedFieldDeniedSourceRefusalCase(String orderId) {
        DbQueryRequestDef queryRequest = aggregateRelationRequest();
        queryRequest.setColumns(new ArrayList<>(List.of("orderId", "amount", "salesAmountPredefinedTax")));
        queryRequest.setSlice(List.of(slice("orderId", "=", orderId)));

        RuntimeException exception = deniedSalesAmountException(queryRequest);

        Map<String, Object> expected = new LinkedHashMap<>();
        expected.put("errorCode", "QUERYMODEL_AGGREGATE_JOIN_DENIED_SOURCE_COLUMN");
        expected.put("message", exception.getMessage());
        expected.put("messageMarkers", List.of("salesAmount", "salesAmountPredefinedTax"));
        expected.put("predefinedCalculatedField", "salesAmountPredefinedTax");

        return caseMap(
                "aggregate-join-predefined-calculated-field-denied-source-refusal",
                "error",
                "OrderSalesAggregateRelationQueryModel",
                requestMap("OrderSalesAggregateRelationQueryModel",
                        List.of("orderId", "amount", "salesAmountPredefinedTax"),
                        List.of(sliceMap("orderId", "=", orderId))),
                expected);
    }

    private Map<String, Object> predefinedCalculatedFieldAllowedExecCase(String orderId) {
        DbQueryRequestDef queryRequest = aggregateRelationRequest();
        queryRequest.setColumns(new ArrayList<>(List.of("orderId", "amount", "salesAmountPredefinedTax")));
        queryRequest.setSlice(List.of(slice("orderId", "=", orderId)));

        DbQueryResult result = queryFacade.queryModelResult(queryFacadeContext(queryRequest));
        JdbcModelQueryEngine queryEngine = (JdbcModelQueryEngine) result.getQueryEngine();
        List<Map<String, Object>> rows = rows(result);
        assertFalse(rows.isEmpty(), "predefined calculated field should execute");
        assertTrue(rows.get(0).containsKey("salesAmountPredefinedTax"));

        Map<String, Object> expected = new LinkedHashMap<>();
        expected.put("sql", queryEngine.getSql());
        expected.put("normalizedSql", normalizeSql(queryEngine.getSql()));
        expected.put("params", queryEngine.getValues());
        expected.put("rows", rows);
        expected.put("sqlMarkers", List.of("salesAmountPredefinedTax"));
        expected.put("forbiddenSqlMarkers", List.of());
        expected.put("rowsRequiredFields", List.of("orderId", "amount", "salesAmountPredefinedTax"));

        return caseMap(
                "aggregate-join-predefined-calculated-field-allowed-exec",
                "sql",
                "OrderSalesAggregateRelationQueryModel",
                requestMap("OrderSalesAggregateRelationQueryModel",
                        List.of("orderId", "amount", "salesAmountPredefinedTax"),
                        List.of(sliceMap("orderId", "=", orderId))),
                expected);
    }

    private Map<String, Object> rawSqlAccessBuilderOuterOnlyCase() {
        JdbcModelQueryEngine queryEngine = buildRawAccessBuilderQuery();
        String normalizedSql = normalizeSql(queryEngine.getSql());
        assertTrue(normalizedSql.contains("t1.order_id = ?"));
        assertFalse(normalizedSql.contains("agg_src.order_id = ?"));
        assertTrue(normalizedSql.contains("sum(agg_src.quantity) quantity"));

        Map<String, Object> expected = new LinkedHashMap<>();
        expected.put("sql", queryEngine.getSql());
        expected.put("normalizedSql", normalizedSql);
        expected.put("params", queryEngine.getValues());
        expected.put("sqlMarkers", List.of("t1.order_id = ?", "sum(agg_src.quantity) quantity"));
        expected.put("forbiddenSqlMarkers", List.of("agg_src.order_id = ?"));
        expected.put("diagnostics", diagnostics(queryEngine));

        return caseMap(
                "aggregate-join-raw-sql-access-builder-outer-only",
                "sql",
                "OrderSalesAggregateRelationRawAccessQueryModel",
                requestMap("OrderSalesAggregateRelationRawAccessQueryModel",
                        List.of("orderId", "amount", "salesAmount", "uniqueCustomers"),
                        List.of()),
                expected);
    }

    private Map<String, Object> orderByAggregateOutputCase() {
        JdbcQueryModel queryModel = getQueryModel("OrderSalesAggregateRelationQueryModel");
        assertNotNull(queryModel, "query model should load");

        DbQueryRequestDef queryRequest = aggregateRelationRequest();
        OrderRequestDef order = new OrderRequestDef();
        order.setField("salesAmount");
        order.setDir("desc");
        queryRequest.setOrderBy(List.of(order));

        JdbcModelQueryEngine queryEngine = analyze(queryModel, queryRequest);
        String normalizedSql = normalizeSql(queryEngine.getSql());
        assertTrue(normalizedSql.contains("sum(agg_src.sales_amount) salesAmount"));
        assertTrue(normalizedSql.toLowerCase().contains("order by"));
        assertTrue(normalizedSql.contains("fsByOrder.salesAmount"));

        Map<String, Object> expected = new LinkedHashMap<>();
        expected.put("sql", queryEngine.getSql());
        expected.put("normalizedSql", normalizedSql);
        expected.put("params", queryEngine.getValues());
        expected.put("rows", jdbcTemplate.queryForList(queryEngine.getSql(), queryEngine.getValues().toArray()));
        expected.put("sqlMarkers", List.of(
                "fsByOrder",
                "sum(agg_src.sales_amount) salesAmount",
                "order by",
                "fsByOrder.salesAmount"));
        expected.put("forbiddenSqlMarkers", List.of("order by agg_src.sales_amount"));
        expected.put("orderBy", List.of(Map.of("field", "salesAmount", "dir", "desc")));
        expected.put("diagnostics", diagnostics(queryEngine));

        Map<String, Object> request = requestMap(
                "OrderSalesAggregateRelationQueryModel",
                List.of("orderId", "amount", "salesAmount", "uniqueCustomers"),
                List.of());
        request.put("orderBy", List.of(Map.of("field", "salesAmount", "dir", "desc")));

        return caseMap(
                "aggregate-join-orderby-aggregate-output",
                "sql",
                "OrderSalesAggregateRelationQueryModel",
                request,
                expected);
    }

    private Map<String, Object> returnTotalAggregateRelationCase(String orderId) {
        DbQueryRequestDef queryRequest = aggregateRelationRequest();
        queryRequest.setReturnTotal(true);
        queryRequest.setSlice(List.of(slice("orderId", "=", orderId)));

        DbQueryResult result = queryFacade.queryModelResult(queryFacadeContext(queryRequest));
        PagingResultImpl<?> pagingResult = result.getPagingResult();
        JdbcModelQueryEngine queryEngine = (JdbcModelQueryEngine) result.getQueryEngine();
        String normalizedSql = normalizeSql(queryEngine.getSql());
        String normalizedTotalSql = normalizeSql(queryEngine.getAggSql());

        assertEquals(1, ((Number) pagingResult.getTotal()).intValue());
        assertTrue(pagingResult.getTotalData() instanceof Map);
        assertTrue(normalizedTotalSql.contains("fsByOrder"));

        Map<String, Object> expected = new LinkedHashMap<>();
        expected.put("sql", queryEngine.getSql());
        expected.put("normalizedSql", normalizedSql);
        expected.put("totalSql", queryEngine.getAggSql());
        expected.put("normalizedTotalSql", normalizedTotalSql);
        expected.put("params", queryEngine.getValues());
        expected.put("rows", rows(result));
        expected.put("total", pagingResult.getTotal());
        expected.put("totalData", pagingResult.getTotalData());
        expected.put("sqlMarkers", List.of("fsByOrder", "left join", "(select", "group by"));
        expected.put("totalSqlMarkers", List.of("fsByOrder"));
        expected.put("forbiddenSqlMarkers", List.of());
        expected.put("returnTotal", true);

        Map<String, Object> request = requestMap(
                "OrderSalesAggregateRelationQueryModel",
                List.of("orderId", "amount", "salesAmount", "uniqueCustomers"),
                List.of(sliceMap("orderId", "=", orderId)));
        request.put("returnTotal", true);

        return caseMap(
                "aggregate-join-return-total",
                "sql",
                "OrderSalesAggregateRelationQueryModel",
                request,
                expected);
    }

    private Map<String, Object> nullCheckOuterOnlyCase(String op) {
        JdbcModelQueryEngine queryEngine = buildNullSlicePushdownProbeQuery(op);
        String normalizedSql = normalizeSql(queryEngine.getSql());
        assertTrue(normalizedSql.contains("left join (select"));
        assertTrue(normalizedSql.contains("fsByPaymentMethod"));
        assertTrue(normalizedSql.contains("agg_src.payment_method = ?"));
        assertFalse(normalizedSql.contains("agg_src.payment_method " + op));
        assertTrue(normalizedSql.contains("fsByPaymentMethod.paymentMethod " + op));

        List<Map<String, Object>> diagnostics = diagnostics(queryEngine);
        assertTrue(diagnostics.stream().anyMatch(d -> "retained".equals(d.get("decision"))
                && AggregateRelationQueryObject.REASON_NULL_CHECK_OUTER_ONLY.equals(d.get("reasonCode"))
                && "paymentMethod".equals(d.get("field"))
                && op.equals(d.get("op"))));

        Map<String, Object> expected = new LinkedHashMap<>();
        expected.put("sql", queryEngine.getSql());
        expected.put("normalizedSql", normalizedSql);
        expected.put("params", queryEngine.getValues());
        expected.put("sqlMarkers", List.of(
                "left join (select",
                "fsByPaymentMethod",
                "agg_src.payment_method = ?",
                "fsByPaymentMethod.paymentMethod " + op));
        expected.put("forbiddenSqlMarkers", List.of("agg_src.payment_method " + op));
        expected.put("diagnostics", diagnostics);

        return caseMap(
                "aggregate-join-null-check-outer-only-" + op.replace(" ", "-"),
                "sql",
                "OrderSalesAggregateRelationNullSlicePushdownProbeQueryModel",
                requestMap("OrderSalesAggregateRelationNullSlicePushdownProbeQueryModel",
                        List.of("count(orderId) as candidateCount", "sum(amount) as totalAmount"),
                        List.of(sliceMap("paymentMethod", op, null))),
                expected);
    }

    private Map<String, Object> semanticDebugExtraDiagnosticsCase(String matchedOrderId, String unmatchedOrderId) {
        SemanticQueryRequest request = new SemanticQueryRequest();
        request.setColumns(Arrays.asList("orderId", "amount", "salesAmount", "uniqueCustomers"));
        request.setSlice(List.of(
                semanticSlice("orderId", "in", List.of(matchedOrderId, unmatchedOrderId)),
                semanticSlice("salesAmount", "[]", List.of(BigDecimal.ZERO, new BigDecimal("999999999")))));
        request.setLimit(100);

        SemanticQueryResponse response = semanticQueryService.queryModel(
                "OrderSalesAggregateRelationQueryModel",
                request,
                "execute",
                SemanticRequestContext.empty());

        List<Map<String, Object>> diagnostics = semanticAggregateRelationDiagnostics(response).stream()
                .map(this::diagnosticMap)
                .toList();
        assertTrue(diagnostics.stream().anyMatch(d -> "pushed".equals(d.get("decision"))
                && "where".equals(d.get("target")) && "orderId".equals(d.get("field"))));
        assertTrue(diagnostics.stream().anyMatch(d -> "pushed".equals(d.get("decision"))
                && "having".equals(d.get("target")) && "salesAmount".equals(d.get("field"))));

        Map<String, Object> expected = new LinkedHashMap<>();
        expected.put("diagnostics", diagnostics);
        expected.put("debugExtraKeys", response.getDebug().getExtra().keySet());
        expected.put("requiredDecisions", List.of("pushed"));
        expected.put("requiredTargets", List.of("where", "having"));
        expected.put("requiredFields", List.of("orderId", "salesAmount"));

        return caseMap(
                "aggregate-join-semantic-debug-extra-diagnostics",
                "diagnostics",
                "OrderSalesAggregateRelationQueryModel",
                requestMap("OrderSalesAggregateRelationQueryModel",
                        List.of("orderId", "amount", "salesAmount", "uniqueCustomers"),
                        List.of(
                                sliceMap("orderId", "in", List.of(matchedOrderId, unmatchedOrderId)),
                                sliceMap("salesAmount", "[]", List.of(BigDecimal.ZERO, new BigDecimal("999999999"))))),
                expected);
    }

    private Map<String, Object> compositeKeyAggregateRelationCase() {
        Map<String, Object> fixture = findOrderStoreWithCompletedSales();
        String orderId = String.valueOf(fixture.get("orderId"));
        Object storeKey = fixture.get("storeKey");

        JdbcQueryModel queryModel = getQueryModel("TmsStyleOrderStoreSalesAggregateRelationQueryModel");
        assertNotNull(queryModel, "query model should load");

        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("TmsStyleOrderStoreSalesAggregateRelationQueryModel");
        queryRequest.setColumns(Arrays.asList("orderId", "amount", "salesAmount", "quantity", "uniqueCustomers"));
        queryRequest.setSlice(List.of(
                slice("orderId", "=", orderId),
                slice("store$id", "=", storeKey),
                slice("salesAmount", ">", BigDecimal.ZERO)));

        JdbcModelQueryEngine queryEngine = analyze(queryModel, queryRequest);
        String normalizedSql = normalizeSql(queryEngine.getSql());
        assertTrue(normalizedSql.contains("salesByOrderStore"));
        assertTrue(normalizedSql.contains("agg_src.order_id = ?"));
        assertTrue(normalizedSql.contains("agg_src.store_key = ?"));
        assertTrue(normalizedSql.contains("having sum(agg_src.sales_amount) > ?"));

        Map<String, Object> expected = new LinkedHashMap<>();
        expected.put("sql", queryEngine.getSql());
        expected.put("normalizedSql", normalizedSql);
        expected.put("params", queryEngine.getValues());
        expected.put("rows", jdbcTemplate.queryForList(queryEngine.getSql(), queryEngine.getValues().toArray()));
        expected.put("sqlMarkers", List.of(
                "salesByOrderStore",
                "agg_src.order_id = ?",
                "agg_src.store_key = ?",
                "having sum(agg_src.sales_amount) > ?",
                "group by"));
        expected.put("forbiddenSqlMarkers", List.of());
        expected.put("diagnostics", diagnostics(queryEngine));

        return caseMap(
                "aggregate-join-composite-key-pushdown",
                "sql",
                "TmsStyleOrderStoreSalesAggregateRelationQueryModel",
                requestMap("TmsStyleOrderStoreSalesAggregateRelationQueryModel",
                        List.of("orderId", "amount", "salesAmount", "quantity", "uniqueCustomers"),
                        List.of(
                                sliceMap("orderId", "=", orderId),
                                sliceMap("store$id", "=", storeKey),
                                sliceMap("salesAmount", ">", BigDecimal.ZERO))),
                expected);
    }

    private Map<String, Object> structuredAccessBuilderPushdownCase() {
        JdbcModelQueryEngine queryEngine = buildStructuredAccessBuilderQuery();
        String normalizedSql = normalizeSql(queryEngine.getSql());
        assertTrue(normalizedSql.contains("agg_src.order_id = ?"));
        assertTrue(queryEngine.getValues().contains("COMPLETED"));
        assertTrue(queryEngine.getValues().contains("ORD20240101000001"));

        Map<String, Object> expected = new LinkedHashMap<>();
        expected.put("sql", queryEngine.getSql());
        expected.put("normalizedSql", normalizedSql);
        expected.put("params", queryEngine.getValues());
        expected.put("rows", jdbcTemplate.queryForList(queryEngine.getSql(), queryEngine.getValues().toArray()));
        expected.put("sqlMarkers", List.of("agg_src.order_id = ?", "sum(agg_src.sales_amount)"));
        expected.put("forbiddenSqlMarkers", List.of("ctx.extData"));
        expected.put("diagnostics", diagnostics(queryEngine));

        return caseMap(
                "aggregate-join-structured-access-builder-pushdown",
                "sql",
                "OrderSalesAggregateRelationAccessQueryModel",
                requestMap("OrderSalesAggregateRelationAccessQueryModel",
                        List.of("orderId", "amount", "salesAmount", "uniqueCustomers"),
                        List.of()),
                expected);
    }

    private Map<String, Object> runtimeFilterUnsafeCharacterRefusalCase() {
        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> buildRuntimeFilterQuery(Map.of("orderId", "ORD001' OR '1'='1"), null));
        assertTrue(exception.getMessage().contains("runtime filter"));

        Map<String, Object> expected = new LinkedHashMap<>();
        expected.put("errorCode", "QUERYMODEL_AGGREGATE_JOIN_RUNTIME_FILTER_UNSAFE");
        expected.put("message", exception.getMessage());
        expected.put("messageMarkers", List.of("runtime filter"));
        expected.put("forbiddenMessageMarkers", List.of("ORD001' OR '1'='1"));

        return caseMap(
                "aggregate-join-runtime-filter-unsafe-refusal",
                "error",
                "OrderSalesAggregateRelationRuntimeFilterQueryModel",
                requestMap("OrderSalesAggregateRelationRuntimeFilterQueryModel",
                        List.of("orderId", "amount", "salesAmount", "uniqueCustomers"),
                        List.of()),
                expected);
    }

    private Map<String, Object> leftDimensionKeyCase() {
        String orderId = findOrderIdWithActiveStore();
        JdbcModelQueryEngine queryEngine = buildDimensionKeyQuery(orderId);
        String normalizedSql = normalizeSql(queryEngine.getSql());
        assertTrue(normalizedSql.contains("left join dim_store"));
        assertFalse(queryEngine.getSql().contains("store$storeId"));
        assertTrue(queryEngine.getSql().contains("store_id = storeAggByBusinessId.storeId")
                || queryEngine.getSql().contains("store_id=storeAggByBusinessId.storeId"));

        Map<String, Object> expected = new LinkedHashMap<>();
        expected.put("sql", queryEngine.getSql());
        expected.put("normalizedSql", normalizedSql);
        expected.put("params", queryEngine.getValues());
        expected.put("rows", jdbcTemplate.queryForList(queryEngine.getSql(), queryEngine.getValues().toArray()));
        expected.put("sqlMarkers", List.of("left join dim_store", "storeAggByBusinessId", "store_id = storeAggByBusinessId.storeId"));
        expected.put("forbiddenSqlMarkers", List.of("store$storeId"));
        expected.put("diagnostics", diagnostics(queryEngine));

        return caseMap(
                "aggregate-join-left-dimension-key",
                "sql",
                "OrderStoreAggregateRelationDimensionKeyQueryModel",
                requestMap("OrderStoreAggregateRelationDimensionKeyQueryModel",
                        List.of("orderId", "amount", "areaSqm"),
                        List.of(sliceMap("orderId", "=", orderId))),
                expected);
    }

    private Map<String, Object> rhsDimensionFixedFilterCase() {
        String orderId = findOrderIdWithCompletedElectronicsSales();
        JdbcModelQueryEngine queryEngine = buildRhsDimensionFilterQuery(orderId);
        String normalizedSql = normalizeSql(queryEngine.getSql());
        assertTrue(normalizedSql.toLowerCase().contains("from fact_sales agg_src left join dim_product"));
        assertFalse(queryEngine.getSql().contains("agg_src.category_id"));
        assertTrue(queryEngine.getSql().contains("category_id = ?")
                || queryEngine.getSql().contains("category_id=?"));
        assertTrue(queryEngine.getValues().contains("CAT001"));

        Map<String, Object> expected = new LinkedHashMap<>();
        expected.put("sql", queryEngine.getSql());
        expected.put("normalizedSql", normalizedSql);
        expected.put("params", queryEngine.getValues());
        expected.put("rows", jdbcTemplate.queryForList(queryEngine.getSql(), queryEngine.getValues().toArray()));
        expected.put("sqlMarkers", List.of("from fact_sales agg_src left join dim_product", "category_id = ?"));
        expected.put("forbiddenSqlMarkers", List.of("agg_src.category_id"));
        expected.put("diagnostics", diagnostics(queryEngine));

        return caseMap(
                "aggregate-join-rhs-dimension-fixed-filter",
                "sql",
                "OrderSalesAggregateRelationRhsDimensionFilterQueryModel",
                requestMap("OrderSalesAggregateRelationRhsDimensionFilterQueryModel",
                        List.of("orderId", "amount", "salesAmount"),
                        List.of(sliceMap("orderId", "=", orderId))),
                expected);
    }

    private Map<String, Object> o615NoColumnsWithAccessCase() {
        Map<String, Object> stockOrder = findO615StockOrder();
        String orderId = String.valueOf(stockOrder.get("orderId"));
        String srcId = String.valueOf(stockOrder.get("srcId"));
        String useType = String.valueOf(stockOrder.get("useType"));
        List<SliceRequestDef> slices = List.of(
                slice("orderId", "=", orderId),
                slice("srcId", "=", srcId),
                slice("useType", "=", useType),
                slice("number", ">", 0));

        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("OrderStationStockProjectionO615ProbeQueryModel");
        queryRequest.setSlice(slices);

        DbQueryResult result = queryFacade.queryModelResult(queryFacadeContext(queryRequest));
        JdbcModelQueryEngine queryEngine = (JdbcModelQueryEngine) result.getQueryEngine();
        String normalizedSql = normalizeSql(queryEngine.getSql());
        assertTrue(normalizedSql.contains("plannedByOrder"));
        assertTrue(normalizedSql.contains("left join dim_store"));
        assertTrue(normalizedSql.contains("where"));
        assertEquals(1, rows(result).size());
        assertEquals(orderId, rows(result).get(0).get("orderId"));

        Map<String, Object> request = requestMap("OrderStationStockProjectionO615ProbeQueryModel",
                List.of(),
                List.of(
                        sliceMap("orderId", "=", orderId),
                        sliceMap("srcId", "=", srcId),
                        sliceMap("useType", "=", useType),
                        sliceMap("number", ">", 0)));
        Map<String, Object> expected = o615Expected(queryEngine, result, request,
                List.of("plannedByOrder", "left join dim_store", "where", "group by"),
                List.of("internalAlias"),
                List.of("orderId"),
                List.of());
        expected.put("defaultProjection", true);

        return caseMap(
                "aggregate-join-o615-no-columns-with-access",
                "result",
                "OrderStationStockProjectionO615ProbeQueryModel",
                request,
                expected);
    }

    private Map<String, Object> o615ExplicitJoinNoColumnsCase() {
        Map<String, Object> stockOrder = findO615StockOrder();
        String orderNo = String.valueOf(stockOrder.get("orderNo"));
        String srcId = String.valueOf(stockOrder.get("srcId"));
        String useType = String.valueOf(stockOrder.get("useType"));
        List<SliceRequestDef> slices = List.of(
                slice("orderNo", "=", orderNo),
                slice("srcId", "=", srcId),
                slice("useType", "=", useType),
                slice("number", ">", 0));

        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("OrderStationStockProjectionO615ExpressJoinProbeQueryModel");
        queryRequest.setSlice(slices);

        DbQueryResult result = queryFacade.queryModelResult(queryFacadeContext(queryRequest));
        JdbcModelQueryEngine queryEngine = (JdbcModelQueryEngine) result.getQueryEngine();
        String normalizedSql = normalizeSql(queryEngine.getSql());
        assertTrue(normalizedSql.contains("plannedByOrder"));
        assertTrue(normalizedSql.contains("left join fact_order"));
        assertTrue(normalizedSql.contains("left join dim_store"));
        assertTrue(normalizedSql.contains("agg_src.store_key = ?"));
        assertTrue(normalizedSql.contains("group by agg_src.store_key"));
        assertTrue(queryEngine.getValues().contains(20240101));
        assertEquals(1, rows(result).size());
        assertEquals(orderNo, rows(result).get(0).get("orderNo"));

        Map<String, Object> request = requestMap("OrderStationStockProjectionO615ExpressJoinProbeQueryModel",
                List.of(),
                List.of(
                        sliceMap("orderNo", "=", orderNo),
                        sliceMap("srcId", "=", srcId),
                        sliceMap("useType", "=", useType),
                        sliceMap("number", ">", 0)));
        Map<String, Object> expected = o615Expected(queryEngine, result, request,
                List.of("plannedByOrder", "left join fact_order", "left join dim_store",
                        "agg_src.store_key = ?", "group by agg_src.store_key"),
                List.of("internalAlias"),
                List.of("orderNo"),
                List.of());
        expected.put("defaultProjection", true);

        return caseMap(
                "aggregate-join-o615-explicit-join-no-columns",
                "result",
                "OrderStationStockProjectionO615ExpressJoinProbeQueryModel",
                request,
                expected);
    }

    private Map<String, Object> o615TenantGuardNoLeakCase() {
        Map<String, Object> stockOrder = findO615StockOrder();
        String orderNo = String.valueOf(stockOrder.get("orderNo"));
        String srcId = String.valueOf(stockOrder.get("srcId"));
        String useType = String.valueOf(stockOrder.get("useType"));
        List<String> columns = List.of("orderNo", "srcId", "useType", "number", "plannedPieceCount");
        List<SliceRequestDef> slices = List.of(
                slice("orderNo", "=", orderNo),
                slice("srcId", "=", srcId),
                slice("useType", "=", useType),
                slice("number", ">", 0));

        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("OrderStationStockProjectionO615ExpressJoinProbeQueryModel");
        queryRequest.setColumns(columns);
        queryRequest.setSlice(slices);

        ModelResultContext context = queryFacadeContext(queryRequest);
        context.setFieldAccess(Set.of("orderNo", "srcId", "useType", "number", "plannedPieceCount"));
        context.setSystemSlice(List.of(slice("tenantId", "=", 20240101)));
        DbQueryResult result = queryFacade.queryModelResult(context);
        JdbcModelQueryEngine queryEngine = (JdbcModelQueryEngine) result.getQueryEngine();
        String normalizedSql = normalizeSql(queryEngine.getSql());
        assertTrue(normalizedSql.contains("agg_src.store_key = ?"));
        assertTrue(normalizedSql.contains("plannedByOrder"));
        assertEquals(1, rows(result).size());
        assertEquals(orderNo, rows(result).get(0).get("orderNo"));
        assertFalse(rows(result).get(0).containsKey("tenantId"));

        Map<String, Object> request = requestMap("OrderStationStockProjectionO615ExpressJoinProbeQueryModel",
                columns,
                List.of(
                        sliceMap("orderNo", "=", orderNo),
                        sliceMap("srcId", "=", srcId),
                        sliceMap("useType", "=", useType),
                        sliceMap("number", ">", 0)));
        Map<String, Object> expected = o615Expected(queryEngine, result, request,
                List.of("plannedByOrder", "agg_src.store_key = ?", "group by agg_src.store_key"),
                List.of("internalAlias"),
                List.of("orderNo", "srcId", "useType", "number", "plannedPieceCount"),
                List.of("tenantId"));
        expected.put("fieldAccess", columns);
        expected.put("systemSlice", List.of(sliceMap("tenantId", "=", 20240101)));

        return caseMap(
                "aggregate-join-o615-tenant-guard-no-leak",
                "sql",
                "OrderStationStockProjectionO615ExpressJoinProbeQueryModel",
                request,
                expected);
    }

    private Map<String, Object> o615DimensionIdSliceCase() {
        Map<String, Object> stockOrder = findO615StockOrder();
        String orderNo = String.valueOf(stockOrder.get("orderNo"));
        String srcId = String.valueOf(stockOrder.get("srcId"));
        String useType = String.valueOf(stockOrder.get("useType"));
        Object destinationServiceAreaId = stockOrder.get("destinationServiceAreaId");
        List<String> columns = List.of("orderNo", "srcId", "useType", "number");
        List<SliceRequestDef> slices = List.of(
                slice("orderNo", "=", orderNo),
                slice("srcId", "=", srcId),
                slice("useType", "=", useType),
                slice("number", ">", 0),
                slice("destinationServiceArea$id", "in", List.of(destinationServiceAreaId)));

        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("OrderStationStockProjectionO615ExpressJoinProbeQueryModel");
        queryRequest.setColumns(columns);
        queryRequest.setSlice(slices);

        DbQueryResult result = queryFacade.queryModelResult(queryFacadeContext(queryRequest));
        JdbcModelQueryEngine queryEngine = (JdbcModelQueryEngine) result.getQueryEngine();
        String normalizedSql = normalizeSql(queryEngine.getSql());
        assertTrue(normalizedSql.contains("left join fact_order"));
        assertTrue(normalizedSql.contains("destinationServiceArea") || normalizedSql.contains("store_key"));
        assertEquals(1, rows(result).size());
        assertEquals(orderNo, rows(result).get(0).get("orderNo"));

        Map<String, Object> request = requestMap("OrderStationStockProjectionO615ExpressJoinProbeQueryModel",
                columns,
                List.of(
                        sliceMap("orderNo", "=", orderNo),
                        sliceMap("srcId", "=", srcId),
                        sliceMap("useType", "=", useType),
                        sliceMap("number", ">", 0),
                        sliceMap("destinationServiceArea$id", "in", List.of(destinationServiceAreaId))));
        Map<String, Object> expected = o615Expected(queryEngine, result, request,
                List.of("left join fact_order", "store_key", "group by"),
                List.of("internalAlias"),
                List.of("orderNo", "srcId", "useType", "number"),
                List.of("tenantId"));
        expected.put("selectedDimensionId", destinationServiceAreaId);

        return caseMap(
                "aggregate-join-o615-dimension-id-slice",
                "result",
                "OrderStationStockProjectionO615ExpressJoinProbeQueryModel",
                request,
                expected);
    }

    private Map<String, Object> o615RhsDimensionFilterCase() {
        Map<String, Object> stockOrder = findO615StockOrder();
        String orderNo = String.valueOf(stockOrder.get("orderNo"));
        String srcId = String.valueOf(stockOrder.get("srcId"));
        String useType = String.valueOf(stockOrder.get("useType"));
        List<SliceRequestDef> slices = List.of(
                slice("orderNo", "=", orderNo),
                slice("srcId", "=", srcId),
                slice("useType", "=", useType),
                slice("number", ">", 0));

        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("OrderStationStockProjectionO615RhsDimensionProbeQueryModel");
        queryRequest.setSlice(slices);

        DbQueryResult result = queryFacade.queryModelResult(queryFacadeContext(queryRequest));
        JdbcModelQueryEngine queryEngine = (JdbcModelQueryEngine) result.getQueryEngine();
        String normalizedSql = normalizeSql(queryEngine.getSql());
        assertTrue(normalizedSql.contains("plannedByOrder"));
        assertTrue(normalizedSql.contains("agg_src"));
        assertTrue(normalizedSql.contains("status = ?") || normalizedSql.contains("status=?"));
        assertTrue(queryEngine.getValues().contains("ACTIVE"));
        assertEquals(1, rows(result).size());
        assertEquals(orderNo, rows(result).get(0).get("orderNo"));

        Map<String, Object> request = requestMap("OrderStationStockProjectionO615RhsDimensionProbeQueryModel",
                List.of(),
                List.of(
                        sliceMap("orderNo", "=", orderNo),
                        sliceMap("srcId", "=", srcId),
                        sliceMap("useType", "=", useType),
                        sliceMap("number", ">", 0)));
        Map<String, Object> expected = o615Expected(queryEngine, result, request,
                List.of("plannedByOrder", "agg_src", "status = ?", "group by"),
                List.of("internalAlias"),
                List.of("orderNo"),
                List.of());
        expected.put("rhsDimensionFilter", Map.of("planSheet$planStatus", "ACTIVE"));

        return caseMap(
                "aggregate-join-o615-rhs-dimension-filter",
                "result",
                "OrderStationStockProjectionO615RhsDimensionProbeQueryModel",
                request,
                expected);
    }

    private Map<String, Object> o615RhsJoinDimensionFilterCase() {
        Map<String, Object> stockOrder = findO615StockOrder();
        String orderNo = String.valueOf(stockOrder.get("orderNo"));
        String srcId = String.valueOf(stockOrder.get("srcId"));
        String useType = String.valueOf(stockOrder.get("useType"));
        List<SliceRequestDef> slices = List.of(
                slice("orderNo", "=", orderNo),
                slice("srcId", "=", srcId),
                slice("useType", "=", useType),
                slice("number", ">", 0));

        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("OrderStationStockProjectionO615RhsJoinDimensionProbeQueryModel");
        queryRequest.setSlice(slices);

        DbQueryResult result = queryFacade.queryModelResult(queryFacadeContext(queryRequest));
        JdbcModelQueryEngine queryEngine = (JdbcModelQueryEngine) result.getQueryEngine();
        String normalizedSql = normalizeSql(queryEngine.getSql());
        assertTrue(normalizedSql.contains("plannedByOrder"));
        assertTrue(normalizedSql.contains("left join dim_store"));
        assertTrue(normalizedSql.contains("status = ?") || normalizedSql.contains("status=?"));
        assertTrue(queryEngine.getValues().contains("ACTIVE"));
        assertEquals(1, rows(result).size());
        assertEquals(orderNo, rows(result).get(0).get("orderNo"));

        Map<String, Object> request = requestMap("OrderStationStockProjectionO615RhsJoinDimensionProbeQueryModel",
                List.of(),
                List.of(
                        sliceMap("orderNo", "=", orderNo),
                        sliceMap("srcId", "=", srcId),
                        sliceMap("useType", "=", useType),
                        sliceMap("number", ">", 0)));
        Map<String, Object> expected = o615Expected(queryEngine, result, request,
                List.of("plannedByOrder", "left join dim_store", "status = ?", "group by"),
                List.of("internalAlias"),
                List.of("orderNo"),
                List.of());
        expected.put("rhsInternalDimensionJoin", "planSheet");

        return caseMap(
                "aggregate-join-o615-rhs-join-dimension-filter",
                "result",
                "OrderStationStockProjectionO615RhsJoinDimensionProbeQueryModel",
                request,
                expected);
    }

    private Map<String, Object> o615Expected(
            JdbcModelQueryEngine queryEngine,
            DbQueryResult result,
            Map<String, Object> request,
            List<String> sqlMarkers,
            List<String> forbiddenSqlMarkers,
            List<String> rowsRequiredFields,
            List<String> rowsForbiddenFields) {
        Map<String, Object> expected = new LinkedHashMap<>();
        expected.put("normalizedRequest", request);
        expected.put("sql", queryEngine.getSql());
        expected.put("normalizedSql", normalizeSql(queryEngine.getSql()));
        expected.put("params", queryEngine.getValues());
        expected.put("rows", rows(result));
        expected.put("sqlMarkers", sqlMarkers);
        expected.put("forbiddenSqlMarkers", forbiddenSqlMarkers);
        expected.put("rowsRequiredFields", rowsRequiredFields);
        expected.put("rowsForbiddenFields", rowsForbiddenFields);
        return expected;
    }

    private JdbcModelQueryEngine buildAggregateJoinQuery(String orderId) {
        JdbcQueryModel queryModel = getQueryModel("OrderSalesAggregateJoinQueryModel");
        assertNotNull(queryModel, "query model should load");

        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("OrderSalesAggregateJoinQueryModel");
        queryRequest.setColumns(Arrays.asList("orderId", "amount", "salesAggAmount", "salesLineCount"));
        if (orderId != null) {
            queryRequest.setSlice(List.of(slice("orderId", "=", orderId)));
        }
        return analyze(queryModel, queryRequest);
    }

    private JdbcModelQueryEngine buildAggregateRelationQuery(String orderId, List<SliceRequestDef> extraSlices) {
        JdbcQueryModel queryModel = getQueryModel("OrderSalesAggregateRelationQueryModel");
        assertNotNull(queryModel, "query model should load");

        DbQueryRequestDef queryRequest = aggregateRelationRequest();
        List<SliceRequestDef> slices = new ArrayList<>();
        if (orderId != null) {
            slices.add(slice("orderId", "=", orderId));
        }
        if (extraSlices != null) {
            slices.addAll(extraSlices);
        }
        if (!slices.isEmpty()) {
            queryRequest.setSlice(slices);
        }
        return analyze(queryModel, queryRequest);
    }

    private JdbcModelQueryEngine buildRuntimeFilterQuery(Map<String, Object> extData, String outerOrderId) {
        JdbcQueryModel queryModel = getQueryModel("OrderSalesAggregateRelationRuntimeFilterQueryModel");
        assertNotNull(queryModel, "query model should load");

        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("OrderSalesAggregateRelationRuntimeFilterQueryModel");
        queryRequest.setColumns(Arrays.asList("orderId", "amount", "salesAmount", "uniqueCustomers"));
        queryRequest.setExtData(extData);
        if (outerOrderId != null) {
            queryRequest.setSlice(List.of(slice("orderId", "=", outerOrderId)));
        }
        return analyze(queryModel, queryRequest);
    }

    private JdbcModelQueryEngine buildRawAccessBuilderQuery() {
        JdbcQueryModel queryModel = getQueryModel("OrderSalesAggregateRelationRawAccessQueryModel");
        assertNotNull(queryModel, "query model should load");

        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("OrderSalesAggregateRelationRawAccessQueryModel");
        queryRequest.setColumns(Arrays.asList("orderId", "amount", "salesAmount", "uniqueCustomers"));
        return analyze(queryModel, queryRequest);
    }

    private JdbcModelQueryEngine buildNullSlicePushdownProbeQuery(String op) {
        JdbcQueryModel queryModel = getQueryModel("OrderSalesAggregateRelationNullSlicePushdownProbeQueryModel");
        assertNotNull(queryModel, "query model should load");

        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("OrderSalesAggregateRelationNullSlicePushdownProbeQueryModel");
        queryRequest.setColumns(Arrays.asList(
                "count(orderId) as candidateCount",
                "sum(amount) as totalAmount"));
        queryRequest.setExtData(Map.of("paymentMethod", "CREDIT_CARD"));
        queryRequest.setSlice(List.of(slice("paymentMethod", op, null)));
        return analyze(queryModel, queryRequest);
    }

    private JdbcModelQueryEngine buildStructuredAccessBuilderQuery() {
        JdbcQueryModel queryModel = getQueryModel("OrderSalesAggregateRelationAccessQueryModel");
        assertNotNull(queryModel, "query model should load");

        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("OrderSalesAggregateRelationAccessQueryModel");
        queryRequest.setColumns(Arrays.asList("orderId", "amount", "salesAmount", "uniqueCustomers"));
        return analyze(queryModel, queryRequest);
    }

    private JdbcModelQueryEngine buildDimensionKeyQuery(String orderId) {
        JdbcQueryModel queryModel = getQueryModel("OrderStoreAggregateRelationDimensionKeyQueryModel");
        assertNotNull(queryModel, "query model should load");

        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("OrderStoreAggregateRelationDimensionKeyQueryModel");
        queryRequest.setColumns(Arrays.asList("orderId", "amount", "areaSqm"));
        queryRequest.setSlice(List.of(slice("orderId", "=", orderId)));
        return analyze(queryModel, queryRequest);
    }

    private JdbcModelQueryEngine buildRhsDimensionFilterQuery(String orderId) {
        JdbcQueryModel queryModel = getQueryModel("OrderSalesAggregateRelationRhsDimensionFilterQueryModel");
        assertNotNull(queryModel, "query model should load");

        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("OrderSalesAggregateRelationRhsDimensionFilterQueryModel");
        queryRequest.setColumns(Arrays.asList("orderId", "amount", "salesAmount"));
        queryRequest.setSlice(List.of(slice("orderId", "=", orderId)));
        return analyze(queryModel, queryRequest);
    }

    private JdbcModelQueryEngine analyze(JdbcQueryModel queryModel, DbQueryRequestDef queryRequest) {
        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);
        queryEngine.analysisQueryRequest(systemBundlesContext, queryRequest);
        return queryEngine;
    }

    private DbQueryRequestDef aggregateRelationRequest() {
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("OrderSalesAggregateRelationQueryModel");
        queryRequest.setColumns(Arrays.asList("orderId", "amount", "salesAmount", "uniqueCustomers"));
        return queryRequest;
    }

    private ModelResultContext queryFacadeContext(DbQueryRequestDef queryRequest) {
        ModelResultContext context = new ModelResultContext();
        context.setRequest(PagingRequest.buildPagingRequest(queryRequest, 100));
        return context;
    }

    private RuntimeException deniedSalesAmountException(DbQueryRequestDef queryRequest) {
        ModelResultContext context = queryFacadeContext(queryRequest);
        context.setDeniedColumns(List.of(new DeniedPhysicalColumn(null, "fact_sales", "sales_amount")));
        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> queryFacade.queryModelResult(context));
        assertTrue(exception.getMessage().contains("salesAmount"));
        return exception;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> rows(DbQueryResult result) {
        return (List<Map<String, Object>>) result.getPagingResult().getItems();
    }

    private List<Map<String, Object>> diagnostics(JdbcModelQueryEngine queryEngine) {
        assertNotNull(queryEngine.getJdbcQueryModel(), "query engine should keep the JDBC QueryModel");
        AggregateRelationQueryObject queryObject = queryEngine.getJdbcQueryModel().getJdbcModelList().stream()
                .map(TableModel::getQueryObject)
                .map(this::resolveAggregateRelationQueryObject)
                .filter(AggregateRelationQueryObject.class::isInstance)
                .findFirst()
                .orElseThrow();
        return queryObject.getAggregateRelationDiagnostics().stream()
                .map(this::diagnosticMap)
                .toList();
    }

    private AggregateRelationQueryObject resolveAggregateRelationQueryObject(QueryObject queryObject) {
        if (queryObject instanceof AggregateRelationQueryObject aggregateRelationQueryObject) {
            return aggregateRelationQueryObject;
        }
        return queryObject == null ? null : queryObject.getDecorate(AggregateRelationQueryObject.class);
    }

    private Map<String, Object> diagnosticMap(AggregateRelationDiagnostic diagnostic) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("decision", diagnostic.decision());
        map.put("field", diagnostic.field());
        map.put("op", diagnostic.op());
        map.put("target", diagnostic.target());
        map.put("reasonCode", diagnostic.reasonCode());
        map.put("expression", diagnostic.expression());
        return map;
    }

    private Map<String, Object> aggregateColumnMetadata(DbColumn column, DbQueryColumn queryColumn) {
        AggregateRelationOutputColumn aggregateColumn = (AggregateRelationOutputColumn) column;
        @SuppressWarnings("unchecked")
        Map<String, Object> extData = (Map<String, Object>) column.getExtData();
        @SuppressWarnings("unchecked")
        Map<String, Object> aggregateRelation = (Map<String, Object>) extData.get("aggregateRelation");

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("caption", queryColumn.getCaption());
        metadata.put("type", queryColumn.getType());
        metadata.put("isGroupKey", aggregateColumn.isAggregateRelationGroupKey());
        metadata.put("isMeasure", aggregateColumn.isAggregateRelationMeasure());
        metadata.put("sourceExpression", aggregateColumn.getAggregateRelationSourceExpression());
        metadata.put("aggregateExpression", aggregateColumn.getAggregateRelationAggregateExpression());
        metadata.put("aggregateRelation", aggregateRelation);
        return metadata;
    }

    private String findOrderIdWithCompletedSales() {
        List<String> orderIds = jdbcTemplate.queryForList("""
                select fo.order_id
                from fact_order fo
                join fact_sales fs on fo.order_id = fs.order_id
                where fs.order_status = 'COMPLETED'
                group by fo.order_id
                having sum(fs.sales_amount) > 0
                order by fo.order_id
                limit 1
                """, String.class);
        assertFalse(orderIds.isEmpty(), "fixture should contain an order with completed sales");
        return orderIds.get(0);
    }

    private String findOrderIdWithoutCompletedSales() {
        List<String> orderIds = jdbcTemplate.queryForList("""
                select fo.order_id
                from fact_order fo
                where not exists (
                    select 1
                    from fact_sales fs
                    where fs.order_id = fo.order_id
                      and fs.order_status = 'COMPLETED'
                )
                order by fo.order_id
                limit 1
                """, String.class);
        assertFalse(orderIds.isEmpty(), "fixture should contain a left row without completed sales");
        return orderIds.get(0);
    }

    private Map<String, Object> findOrderStoreWithCompletedSales() {
        Map<String, Object> fixture = jdbcTemplate.queryForMap("""
                select fo.order_id orderId, fo.store_key storeKey
                from fact_order fo
                join fact_sales fs on fo.order_id = fs.order_id
                                  and fo.store_key = fs.store_key
                where fs.order_status = 'COMPLETED'
                group by fo.order_id, fo.store_key
                having sum(fs.sales_amount) > 0
                order by fo.order_id
                limit 1
                """);
        assertFalse(fixture.isEmpty(), "fixture should contain an order/store pair with completed sales");
        return fixture;
    }

    private String findOrderIdWithActiveStore() {
        List<String> orderIds = jdbcTemplate.queryForList("""
                select fo.order_id
                from fact_order fo
                join dim_store ds on fo.store_key = ds.store_key
                where ds.status = 'ACTIVE'
                order by fo.order_id
                limit 1
                """, String.class);
        assertFalse(orderIds.isEmpty(), "fixture should contain an order with an active store");
        return orderIds.get(0);
    }

    private String findOrderIdWithCompletedElectronicsSales() {
        List<String> orderIds = jdbcTemplate.queryForList("""
                select fo.order_id
                from fact_order fo
                join fact_sales fs on fo.order_id = fs.order_id
                join dim_product dp on fs.product_key = dp.product_key
                where fs.order_status = 'COMPLETED'
                  and dp.category_id = 'CAT001'
                group by fo.order_id
                having sum(fs.sales_amount) > 0
                order by fo.order_id
                limit 1
                """, String.class);
        assertFalse(orderIds.isEmpty(), "fixture should contain an order with completed electronics sales");
        return orderIds.get(0);
    }

    private Map<String, Object> findO615StockOrder() {
        Map<String, Object> stockOrder = jdbcTemplate.queryForMap("""
                select fo.order_id orderId,
                       fo.order_id orderNo,
                       ds.store_id srcId,
                       ds.store_type useType,
                       ds.store_key destinationServiceAreaId
                from fact_order fo
                join dim_store ds on fo.store_key = ds.store_key
                where fo.date_key = 20240101
                  and fo.total_quantity > 0
                order by fo.order_id
                limit 1
                """);
        assertFalse(stockOrder.isEmpty(), "fixture should contain an O615 stock order");
        return stockOrder;
    }

    private SliceRequestDef slice(String field, String op, Object value) {
        SliceRequestDef slice = new SliceRequestDef();
        slice.setField(field);
        slice.setOp(op);
        slice.setValue(value);
        return slice;
    }

    private SemanticQueryRequest.SliceItem semanticSlice(String field, String op, Object value) {
        SemanticQueryRequest.SliceItem slice = new SemanticQueryRequest.SliceItem();
        slice.setField(field);
        slice.setOp(op);
        slice.setValue(value);
        return slice;
    }

    @SuppressWarnings("unchecked")
    private List<AggregateRelationDiagnostic> semanticAggregateRelationDiagnostics(SemanticQueryResponse response) {
        assertNotNull(response.getDebug(), "semantic response should include debug evidence");
        assertNotNull(response.getDebug().getExtra(), "semantic response debug.extra should include evidence");
        Object rawDiagnostics = response.getDebug().getExtra().get("aggregateRelationDiagnostics");
        assertTrue(rawDiagnostics instanceof List<?>, "debug.extra should expose aggregateRelationDiagnostics");
        return (List<AggregateRelationDiagnostic>) rawDiagnostics;
    }

    private CondRequestDef condition(String field, String op, Object value) {
        CondRequestDef condition = new CondRequestDef();
        condition.setField(field);
        condition.setOp(op);
        condition.setValue(value);
        return condition;
    }

    private String normalizeSql(String sql) {
        return sql.replaceAll("\\s+", " ").trim();
    }

    private Map<String, Object> caseMap(
            String id,
            String type,
            String model,
            Map<String, Object> request,
            Map<String, Object> expected) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", id);
        map.put("type", type);
        map.put("model", model);
        map.put("request", request);
        map.put("expected", expected);
        return map;
    }

    private Map<String, Object> requestMap(String queryModel, List<String> columns, List<Map<String, Object>> slices) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("queryModel", queryModel);
        map.put("columns", columns);
        map.put("slice", slices);
        return map;
    }

    private Map<String, Object> sliceMap(String field, String op, Object value) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("field", field);
        map.put("op", op);
        map.put("value", value);
        return map;
    }
}
