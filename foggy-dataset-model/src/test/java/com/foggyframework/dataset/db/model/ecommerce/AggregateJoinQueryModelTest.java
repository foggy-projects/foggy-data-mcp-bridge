package com.foggyframework.dataset.db.model.ecommerce;

import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.dataset.client.domain.PagingRequest;
import com.foggyframework.dataset.db.model.def.query.request.DbQueryRequestDef;
import com.foggyframework.dataset.db.model.def.query.request.SliceRequestDef;
import com.foggyframework.dataset.db.model.engine.JdbcModelQueryEngine;
import com.foggyframework.dataset.db.model.engine.formula.SqlFormulaService;
import com.foggyframework.dataset.db.model.engine.query.DbQueryResult;
import com.foggyframework.dataset.db.model.impl.model.AggregateJoinTableModel;
import com.foggyframework.dataset.db.model.impl.model.AggregateRelationOutputColumn;
import com.foggyframework.dataset.db.model.impl.model.AggregateRelationQueryObject;
import com.foggyframework.dataset.db.model.plugins.result_set_filter.ModelResultContext;
import com.foggyframework.dataset.db.model.proxy.AggregateJoinBuilder;
import com.foggyframework.dataset.db.model.proxy.TableModelProxy;
import com.foggyframework.dataset.db.model.service.QueryFacade;
import com.foggyframework.dataset.db.model.spi.DbColumn;
import com.foggyframework.dataset.db.model.spi.JdbcQueryModel;
import com.foggyframework.dataset.db.model.spi.TableModel;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 查询引擎测试 - aggregate join。
 */
@Slf4j
@DisplayName("查询引擎测试 - aggregate join")
class AggregateJoinQueryModelTest extends EcommerceTestSupport {

    @Resource
    private SqlFormulaService sqlFormulaService;

    @Resource
    private SystemBundlesContext systemBundlesContext;

    @Resource
    private QueryFacade queryFacade;

    @Test
    @DisplayName("aggregate join 应生成右侧聚合子查询")
    void aggregateJoinShouldRenderRightSideAggregateSubquery() {
        JdbcModelQueryEngine queryEngine = buildOrderSalesAggregateJoinQuery();

        String sql = queryEngine.getSql();
        assertNotNull(sql, "SQL生成失败");

        String normalizedSql = sql.toLowerCase();
        assertTrue(normalizedSql.contains("left join"), "SQL应包含 LEFT JOIN");
        assertTrue(normalizedSql.contains("(select"), "aggregate join 右侧应是内联聚合子查询");
        assertTrue(normalizedSql.contains("sum("), "右侧子查询应包含 SUM 聚合");
        assertTrue(normalizedSql.contains("count(*)"), "右侧子查询应包含 COUNT 聚合");
        assertTrue(normalizedSql.contains("count(distinct"), "右侧子查询应包含 COUNT DISTINCT 聚合");
        assertTrue(normalizedSql.contains("group by"), "右侧子查询应包含 GROUP BY");
        assertTrue(normalizedSql.contains("fact_sales"), "右侧子查询应读取销售明细表");
        assertTrue(sql.contains("agg_src.order_status = 'COMPLETED'"), "右侧固定 slice 应在聚合前下推");
        assertTrue(sql.contains("order_id"), "JOIN ON 应使用订单物理列");
        assertFalse(sql.contains(".salesAmount"), "SQL 不应直接使用语义字段 salesAmount");

        printSql(sql, "订单-销售明细 aggregate join SQL");
    }

    @Test
    @DisplayName("aggregate relation 应按 TM 默认聚合方式生成右侧聚合子查询")
    void aggregateRelationShouldRenderDefaultMeasureAggregation() {
        JdbcModelQueryEngine queryEngine = buildOrderSalesAggregateRelationQuery();

        String sql = queryEngine.getSql();
        assertNotNull(sql, "SQL生成失败");

        String normalizedSql = sql.toLowerCase();
        assertTrue(normalizedSql.contains("left join"), "SQL应包含 LEFT JOIN");
        assertTrue(normalizedSql.contains("(select"), "aggregate relation 右侧应是内联聚合子查询");
        assertTrue(sql.contains("sum(agg_src.sales_amount) salesAmount"), "salesAmount 应按 TM 默认 SUM 聚合");
        assertTrue(sql.contains("count(distinct agg_src.customer_key) uniqueCustomers"), "COUNT_DISTINCT measure 应按 TM 聚合元数据渲染");
        assertTrue(sql.contains("agg_src.order_status = 'COMPLETED'"), "右侧 fixed slice 应在聚合前下推");
        assertTrue(normalizedSql.contains("group by"), "右侧子查询应包含 GROUP BY");
        assertTrue(sql.contains("fsByOrder"), "aggregate relation 应保留模型作者声明的 relation alias");

        printSql(sql, "订单-销售明细 aggregate relation SQL");
    }

    @Test
    @DisplayName("aggregate relation 输出列应暴露 group key 与 measure 元数据")
    void aggregateRelationColumnsShouldExposeOutputMetadata() {
        JdbcQueryModel queryModel = getQueryModel("OrderSalesAggregateRelationQueryModel");
        assertNotNull(queryModel, "查询模型加载失败");

        DbColumn salesAmountColumn = queryModel.findJdbcColumnForCond("salesAmount", true, true);
        assertTrue(salesAmountColumn instanceof AggregateRelationOutputColumn, "salesAmount 应来自 aggregate relation 输出列");
        AggregateRelationOutputColumn salesAmount = (AggregateRelationOutputColumn) salesAmountColumn;
        assertTrue(salesAmount.isAggregateRelationMeasure(), "salesAmount 应标记为 aggregate measure");
        assertFalse(salesAmount.isAggregateRelationGroupKey(), "salesAmount 不应标记为 group key");
        assertEquals("agg_src.sales_amount", salesAmount.getAggregateRelationSourceExpression());
        assertEquals("sum(agg_src.sales_amount)", salesAmount.getAggregateRelationAggregateExpression());

        AggregateRelationOutputColumn groupKey = findAggregateRelationGroupKey(queryModel);
        assertEquals("agg_src.order_id", groupKey.getAggregateRelationSourceExpression());
        assertEquals(null, groupKey.getAggregateRelationAggregateExpression());
    }

    @Test
    @DisplayName("aggregate relation measure slice 应复制到右侧 HAVING 并保留外层 WHERE")
    void aggregateRelationMeasureSliceShouldPushHavingAndKeepOuterWhere() {
        JdbcModelQueryEngine queryEngine = buildOrderSalesAggregateRelationQuery(
                null,
                List.of(slice("salesAmount", ">", BigDecimal.ZERO)));

        String sql = queryEngine.getSql();
        String normalizedSql = normalizeSql(sql);
        assertTrue(normalizedSql.contains("having sum(agg_src.sales_amount) > 0"),
                "右侧聚合子查询应包含 measure HAVING 下推");
        assertTrue(normalizedSql.contains("fsByOrder.salesAmount >?"),
                "外层 WHERE 应保留 aggregate relation measure 条件以保持 LEFT 语义");
        assertEquals(1, queryEngine.getValues().size(), "外层条件仍应使用参数化绑定");
        assertEquals(0, new BigDecimal(String.valueOf(queryEngine.getValues().get(0))).compareTo(BigDecimal.ZERO));
    }

    @Test
    @DisplayName("aggregate relation group key 条件应复制到右侧 WHERE")
    void aggregateRelationGroupKeyConditionShouldPushWhere() {
        String orderId = findOrderIdWithCompletedSales();
        AggregateRelationOutputColumn groupKey = findAggregateRelationGroupKey(
                getQueryModel("OrderSalesAggregateRelationQueryModel"));

        try {
            assertTrue(groupKey.pushAggregateRelationCondition("=", orderId));

            String body = ((DbColumn) groupKey).getQueryObject().getBody();
            String normalizedBody = normalizeSql(body);
            assertTrue(normalizedBody.contains("where agg_src.order_status = 'COMPLETED' and agg_src.order_id = '" + orderId + "'"),
                    "aggregate relation group key 条件应进入右侧聚合前 WHERE");
            assertFalse(normalizedBody.contains("having"), "group key 条件不应进入 HAVING");
        } finally {
            ((AggregateRelationQueryObject) ((DbColumn) groupKey).getQueryObject()).clearAggregateRelationPushdowns();
        }
    }

    @Test
    @DisplayName("左侧 join key slice 应复制到 aggregate relation 右侧 WHERE")
    void aggregateRelationLeftJoinKeySliceShouldPushRightWhere() {
        String orderId = findOrderIdWithCompletedSales();
        JdbcModelQueryEngine queryEngine = buildOrderSalesAggregateRelationQuery(orderId);

        String sql = queryEngine.getSql();
        assertTrue(sql.contains("agg_src.order_id = '" + orderId + "'"),
                "左侧 join key 条件应复制到右侧聚合前 WHERE，限制 RHS key domain");
        assertTrue(queryEngine.getValues().contains(orderId), "外层 WHERE 仍应保留参数化 join key 条件");
    }

    @Test
    @DisplayName("aggregate join 查询结果应等于原生订单明细聚合")
    void aggregateJoinResultShouldMatchNativeAggregate() {
        String orderId = findOrderIdWithCompletedSales();
        JdbcModelQueryEngine queryEngine = buildOrderSalesAggregateJoinQuery(orderId);

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                queryEngine.getSql(),
                queryEngine.getValues().toArray());
        assertEquals(1, rows.size(), "指定订单应只返回一行");

        Map<String, Object> row = rows.get(0);
        BigDecimal nativeSalesAmount = jdbcTemplate.queryForObject(
                "select sum(sales_amount) from fact_sales where order_id = ? and order_status = 'COMPLETED'",
                BigDecimal.class,
                orderId);
        Long nativeLineCount = jdbcTemplate.queryForObject(
                "select count(*) from fact_sales where order_id = ? and order_status = 'COMPLETED'",
                Long.class,
                orderId);

        assertEquals(orderId, row.get("orderId"));
        assertEquals(0, money(nativeSalesAmount).compareTo(money(row.get("salesAggAmount"))), "聚合销售金额应一致");
        assertEquals(nativeLineCount.longValue(), ((Number) row.get("salesLineCount")).longValue(), "销售明细行数应一致");
    }

    @Test
    @DisplayName("aggregate relation 查询结果应等于原生订单明细聚合")
    void aggregateRelationResultShouldMatchNativeAggregate() {
        String orderId = findOrderIdWithCompletedSales();
        JdbcModelQueryEngine queryEngine = buildOrderSalesAggregateRelationQuery(orderId);

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                queryEngine.getSql(),
                queryEngine.getValues().toArray());
        assertEquals(1, rows.size(), "指定订单应只返回一行");

        Map<String, Object> row = rows.get(0);
        BigDecimal nativeSalesAmount = jdbcTemplate.queryForObject(
                "select sum(sales_amount) from fact_sales where order_id = ? and order_status = 'COMPLETED'",
                BigDecimal.class,
                orderId);
        Long nativeUniqueCustomers = jdbcTemplate.queryForObject(
                "select count(distinct customer_key) from fact_sales where order_id = ? and order_status = 'COMPLETED'",
                Long.class,
                orderId);

        assertEquals(orderId, row.get("orderId"));
        assertEquals(0, money(nativeSalesAmount).compareTo(money(row.get("salesAmount"))), "默认聚合销售金额应一致");
        assertEquals(nativeUniqueCustomers.longValue(), ((Number) row.get("uniqueCustomers")).longValue(), "默认去重客户数应一致");
    }

    @Test
    @DisplayName("aggregate join 右侧 fixed slice 无匹配时应保留左侧行")
    void aggregateJoinNoRightMatchShouldKeepLeftRow() {
        String orderId = findOrderIdWithoutCompletedSales();
        JdbcModelQueryEngine queryEngine = buildOrderSalesAggregateJoinQuery(orderId);

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                queryEngine.getSql(),
                queryEngine.getValues().toArray());
        assertEquals(1, rows.size(), "LEFT aggregate join 无右侧聚合结果时仍应返回左侧订单");

        Map<String, Object> row = rows.get(0);
        assertEquals(orderId, row.get("orderId"));
        assertEquals(null, row.get("salesAggAmount"), "右侧 fixed slice 无匹配时聚合金额应为 null");
        assertEquals(null, row.get("salesLineCount"), "右侧 fixed slice 无匹配时行数应为 null");
    }

    @Test
    @DisplayName("aggregate relation 右侧 fixed slice 无匹配时应保留左侧行")
    void aggregateRelationNoRightMatchShouldKeepLeftRow() {
        String orderId = findOrderIdWithoutCompletedSales();
        JdbcModelQueryEngine queryEngine = buildOrderSalesAggregateRelationQuery(orderId);

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                queryEngine.getSql(),
                queryEngine.getValues().toArray());
        assertEquals(1, rows.size(), "LEFT aggregate relation 无右侧聚合结果时仍应返回左侧订单");

        Map<String, Object> row = rows.get(0);
        assertEquals(orderId, row.get("orderId"));
        assertEquals(null, row.get("salesAmount"), "右侧 fixed slice 无匹配时聚合金额应为 null");
        assertEquals(null, row.get("uniqueCustomers"), "右侧 fixed slice 无匹配时去重客户数应为 null");
    }

    @Test
    @DisplayName("aggregate relation measure slice 无右侧匹配时应按外层 WHERE 过滤")
    void aggregateRelationMeasureSliceNoRightMatchShouldKeepOuterWhereSemantics() {
        String orderId = findOrderIdWithoutCompletedSales();
        JdbcModelQueryEngine queryEngine = buildOrderSalesAggregateRelationQuery(
                orderId,
                List.of(slice("salesAmount", ">", BigDecimal.ZERO)));

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                queryEngine.getSql(),
                queryEngine.getValues().toArray());
        assertEquals(0, rows.size(), "aggregate measure slice 保留外层 WHERE 后，无右侧聚合结果的 LEFT 行应被过滤");
        assertTrue(normalizeSql(queryEngine.getSql()).contains("having sum(agg_src.sales_amount) > 0"),
                "右侧 HAVING 下推不应替代外层 WHERE");
    }

    @Test
    @DisplayName("aggregate relation system_slice 应经 QueryFacade 合并并下推到右侧聚合")
    void aggregateRelationSystemSliceShouldMergeAndPushRightWhereThroughQueryFacade() {
        String orderId = findOrderIdWithCompletedSales();
        DbQueryRequestDef queryRequest = buildOrderSalesAggregateRelationRequest();
        ModelResultContext context = buildQueryFacadeContext(queryRequest);
        context.setSystemSlice(List.of(slice("orderId", "=", orderId)));

        DbQueryResult result = queryFacade.queryModelResult(context);
        JdbcModelQueryEngine queryEngine = (JdbcModelQueryEngine) result.getQueryEngine();
        String sql = queryEngine.getSql();

        assertTrue(sql.contains("agg_src.order_id = '" + orderId + "'"),
                "system_slice 中的左侧 join key 应复制到右侧聚合前 WHERE");
        assertTrue(queryEngine.getValues().contains(orderId),
                "外层 WHERE 仍应保留 system_slice 参数化条件");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) result.getPagingResult().getItems();
        assertEquals(1, rows.size(), "system_slice 限定订单后应返回一行真实数据");

        Map<String, Object> row = rows.get(0);
        BigDecimal nativeSalesAmount = jdbcTemplate.queryForObject(
                "select sum(sales_amount) from fact_sales where order_id = ? and order_status = 'COMPLETED'",
                BigDecimal.class,
                orderId);

        assertEquals(orderId, row.get("orderId"));
        assertEquals(0, money(nativeSalesAmount).compareTo(money(row.get("salesAmount"))),
                "QueryFacade 完整生命周期下 RHS 聚合金额应与原生聚合一致");
    }

    @Test
    @DisplayName("aggregate relation 应在当前数据库执行 EXPLAIN 并保留 RHS 过滤证据")
    void aggregateRelationShouldRunExplainWithPushedRightSideFilters() {
        String orderId = findOrderIdWithCompletedSales();
        JdbcModelQueryEngine queryEngine = buildOrderSalesAggregateRelationQuery(
                orderId,
                List.of(slice("salesAmount", ">", BigDecimal.ZERO)));

        String sql = queryEngine.getSql();
        String normalizedSql = normalizeSql(sql);
        assertTrue(sql.contains("agg_src.order_id = '" + orderId + "'"),
                "左侧 join key 条件应进入 RHS WHERE，降低右侧聚合 key domain");
        assertTrue(normalizedSql.contains("having sum(agg_src.sales_amount) > 0"),
                "aggregate measure 条件应进入 RHS HAVING");

        List<Map<String, Object>> planRows = explainQueryPlan(sql, queryEngine.getValues());
        assertFalse(planRows.isEmpty(), "当前数据库应返回 EXPLAIN 执行计划");
        log.info("aggregate relation EXPLAIN [{}]: {}", getDialectKey(), planRows);

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                sql,
                queryEngine.getValues().toArray());
        assertEquals(1, rows.size(), "EXPLAIN 覆盖的 SQL 应能在真实数据上执行");
        assertEquals(orderId, rows.get(0).get("orderId"));
    }

    @Test
    @DisplayName("aggregate join 应拒绝 groupBy 未覆盖右侧 join key")
    void aggregateJoinShouldRejectJoinKeyMissingFromGroupBy() {
        TableModelProxy fo = new TableModelProxy("FactOrderModel");
        TableModelProxy fs = new TableModelProxy("FactSalesModel");
        AggregateJoinBuilder builder = (AggregateJoinBuilder) fo.invoke(null, "leftJoinAggregate", new Object[]{fs});
        builder.invoke(null, "groupBy", new Object[]{fs.getProperty("orderLineNo")});
        builder.invoke(null, "sum", new Object[]{fs.getProperty("salesAmount"), "salesAggAmount"});
        builder.invoke(null, "on", new Object[]{fo.getProperty("orderId"), fs.getProperty("orderId")});

        TableModel salesModel = tableModelLoaderManager.load("FactSalesModel");
        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> AggregateJoinTableModel.from(salesModel, builder));
        assertTrue(exception.getMessage().contains("groupBy"), "错误信息应指向 groupBy 语义约束");
    }

    private JdbcModelQueryEngine buildOrderSalesAggregateJoinQuery() {
        return buildOrderSalesAggregateJoinQuery(null);
    }

    private AggregateRelationOutputColumn findAggregateRelationGroupKey(JdbcQueryModel queryModel) {
        assertNotNull(queryModel, "查询模型加载失败");
        return queryModel.getJdbcModelList().stream()
                .flatMap(model -> model.getVisibleSelectColumns().stream())
                .filter(AggregateRelationOutputColumn.class::isInstance)
                .map(AggregateRelationOutputColumn.class::cast)
                .filter(AggregateRelationOutputColumn::isAggregateRelationGroupKey)
                .findFirst()
                .orElseThrow();
    }

    private JdbcModelQueryEngine buildOrderSalesAggregateJoinQuery(String orderId) {
        JdbcQueryModel queryModel = getQueryModel("OrderSalesAggregateJoinQueryModel");
        assertNotNull(queryModel, "查询模型加载失败");

        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);

        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("OrderSalesAggregateJoinQueryModel");
        queryRequest.setColumns(Arrays.asList("orderId", "amount", "salesAggAmount", "salesLineCount"));

        if (orderId != null) {
            SliceRequestDef slice = new SliceRequestDef();
            slice.setField("orderId");
            slice.setOp("=");
            slice.setValue(orderId);
            queryRequest.setSlice(List.of(slice));
        }

        queryEngine.analysisQueryRequest(systemBundlesContext, queryRequest);
        return queryEngine;
    }

    private JdbcModelQueryEngine buildOrderSalesAggregateRelationQuery() {
        return buildOrderSalesAggregateRelationQuery(null);
    }

    private JdbcModelQueryEngine buildOrderSalesAggregateRelationQuery(String orderId) {
        return buildOrderSalesAggregateRelationQuery(orderId, null);
    }

    private JdbcModelQueryEngine buildOrderSalesAggregateRelationQuery(String orderId, List<SliceRequestDef> extraSlices) {
        JdbcQueryModel queryModel = getQueryModel("OrderSalesAggregateRelationQueryModel");
        assertNotNull(queryModel, "查询模型加载失败");

        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);

        DbQueryRequestDef queryRequest = buildOrderSalesAggregateRelationRequest();

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

        queryEngine.analysisQueryRequest(systemBundlesContext, queryRequest);
        return queryEngine;
    }

    private DbQueryRequestDef buildOrderSalesAggregateRelationRequest() {
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("OrderSalesAggregateRelationQueryModel");
        queryRequest.setColumns(Arrays.asList("orderId", "amount", "salesAmount", "uniqueCustomers"));
        return queryRequest;
    }

    private ModelResultContext buildQueryFacadeContext(DbQueryRequestDef queryRequest) {
        ModelResultContext context = new ModelResultContext();
        context.setRequest(PagingRequest.buildPagingRequest(queryRequest, 100));
        return context;
    }

    private List<Map<String, Object>> explainQueryPlan(String sql, List<Object> values) {
        String dialect = getDialectKey();
        String explainSql;
        if ("sqlite".equals(dialect)) {
            explainSql = "EXPLAIN QUERY PLAN " + sql;
        } else if ("postgresql".equals(dialect) || "mysql".equals(dialect)) {
            explainSql = "EXPLAIN " + sql;
        } else {
            explainSql = "EXPLAIN " + sql;
        }
        return jdbcTemplate.queryForList(explainSql, values.toArray());
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
        assertFalse(orderIds.isEmpty(), "测试数据应至少包含一个有 COMPLETED 销售明细的订单");
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
        assertFalse(orderIds.isEmpty(), "测试数据应至少包含一个无 COMPLETED 销售明细的左侧订单");
        return orderIds.get(0);
    }

    private SliceRequestDef slice(String field, String op, Object value) {
        SliceRequestDef slice = new SliceRequestDef();
        slice.setField(field);
        slice.setOp(op);
        slice.setValue(value);
        return slice;
    }

    private String normalizeSql(String sql) {
        return sql.replaceAll("\\s+", " ").trim();
    }

    private BigDecimal money(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal bigDecimal) {
            return bigDecimal.setScale(2, RoundingMode.HALF_UP);
        }
        return new BigDecimal(String.valueOf(value)).setScale(2, RoundingMode.HALF_UP);
    }
}
