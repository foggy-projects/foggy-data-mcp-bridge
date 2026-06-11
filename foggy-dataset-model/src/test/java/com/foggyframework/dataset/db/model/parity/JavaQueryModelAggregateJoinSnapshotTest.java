package com.foggyframework.dataset.db.model.parity;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.dataset.client.domain.PagingRequest;
import com.foggyframework.dataset.db.model.def.query.request.CalculatedFieldDef;
import com.foggyframework.dataset.db.model.def.query.request.CondRequestDef;
import com.foggyframework.dataset.db.model.def.query.request.DbQueryRequestDef;
import com.foggyframework.dataset.db.model.def.query.request.SliceRequestDef;
import com.foggyframework.dataset.db.model.ecommerce.EcommerceTestSupport;
import com.foggyframework.dataset.db.model.engine.JdbcModelQueryEngine;
import com.foggyframework.dataset.db.model.engine.formula.SqlFormulaService;
import com.foggyframework.dataset.db.model.impl.model.AggregateJoinTableModel;
import com.foggyframework.dataset.db.model.impl.model.AggregateRelationDiagnostic;
import com.foggyframework.dataset.db.model.impl.model.AggregateRelationOutputColumn;
import com.foggyframework.dataset.db.model.impl.model.AggregateRelationQueryObject;
import com.foggyframework.dataset.db.model.plugins.result_set_filter.ModelResultContext;
import com.foggyframework.dataset.db.model.proxy.AggregateJoinBuilder;
import com.foggyframework.dataset.db.model.proxy.TableModelProxy;
import com.foggyframework.dataset.db.model.semantic.domain.DeniedPhysicalColumn;
import com.foggyframework.dataset.db.model.service.QueryFacade;
import com.foggyframework.dataset.db.model.spi.DbColumn;
import com.foggyframework.dataset.db.model.spi.DbQueryColumn;
import com.foggyframework.dataset.db.model.spi.JdbcQueryModel;
import com.foggyframework.dataset.db.model.spi.QueryObject;
import com.foggyframework.dataset.db.model.spi.TableModel;
import jakarta.annotation.Resource;
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

    @Test
    @DisplayName("produces _querymodel_aggregate_join_snapshot.json")
    void shouldProduceSnapshot() throws Exception {
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
                metadataLineageCase());

        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("schemaVersion", 1);
        snapshot.put("feature", "queryModelAggregateJoin");
        snapshot.put("source", "JavaQueryModelAggregateJoinSnapshotTest");
        snapshot.put("contractVersion", "querymodel-aggregate-join-1");
        snapshot.put("generatedAt", new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").format(new Date()));
        snapshot.put("dialect", getDialectKey());
        snapshot.put("cases", cases);

        ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        Path target = Path.of("target", "parity", "_querymodel_aggregate_join_snapshot.json");
        Files.createDirectories(target.getParent());
        mapper.writeValue(target.toFile(), snapshot);

        assertTrue(Files.exists(target), "snapshot file must be written");
        assertEquals(10, cases.size(), "expected aggregate join contract case count");
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

    private SliceRequestDef slice(String field, String op, Object value) {
        SliceRequestDef slice = new SliceRequestDef();
        slice.setField(field);
        slice.setOp(op);
        slice.setValue(value);
        return slice;
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
