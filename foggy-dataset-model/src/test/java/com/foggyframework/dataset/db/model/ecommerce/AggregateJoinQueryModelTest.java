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
import com.foggyframework.dataset.db.model.spi.DbColumnType;
import com.foggyframework.dataset.db.model.spi.DbQueryColumn;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
        assertEquals("销售金额", salesAmountColumn.getCaption(), "aggregate measure 应继承 TM measure caption");
        assertEquals(DbColumnType.MONEY, salesAmountColumn.getType(), "SUM 金额字段应继承 TM measure type");
        assertEquals("agg_src.sales_amount", salesAmount.getAggregateRelationSourceExpression());
        assertEquals("sum(agg_src.sales_amount)", salesAmount.getAggregateRelationAggregateExpression());

        DbColumn uniqueCustomersColumn = queryModel.findJdbcColumnForCond("uniqueCustomers", true, true);
        assertTrue(uniqueCustomersColumn instanceof AggregateRelationOutputColumn,
                "uniqueCustomers 应来自 aggregate relation 输出列");
        assertEquals("独立客户数", uniqueCustomersColumn.getCaption(),
                "COUNT DISTINCT 输出列应继承 TM measure caption");
        assertEquals(DbColumnType.BIGINT, uniqueCustomersColumn.getType(),
                "COUNT DISTINCT 输出列运行态类型应为 BIGINT");
        @SuppressWarnings("unchecked")
        Map<String, Object> uniqueCustomersExtData = (Map<String, Object>) uniqueCustomersColumn.getExtData();
        @SuppressWarnings("unchecked")
        Map<String, Object> aggregateRelation = (Map<String, Object>) uniqueCustomersExtData.get("aggregateRelation");
        assertEquals("COUNT_DISTINCT", aggregateRelation.get("aggregation"));
        assertEquals("独立客户数", aggregateRelation.get("sourceCaption"));

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
    @DisplayName("aggregate relation 输出字段应遵守 fieldAccess 白名单")
    void aggregateRelationOutputFieldShouldRespectFieldAccessAllowList() {
        String orderId = findOrderIdWithCompletedSales();
        DbQueryRequestDef queryRequest = buildOrderSalesAggregateRelationRequest();
        queryRequest.setSlice(List.of(slice("orderId", "=", orderId)));

        ModelResultContext context = buildQueryFacadeContext(queryRequest);
        context.setFieldAccess(Set.of("orderId", "amount", "salesAmount", "uniqueCustomers"));

        DbQueryResult result = queryFacade.queryModelResult(context);
        JdbcModelQueryEngine queryEngine = (JdbcModelQueryEngine) result.getQueryEngine();
        assertTrue(queryEngine.getSql().contains("fsByOrder.salesAmount"),
                "fieldAccess 允许的 aggregate relation 输出字段应正常参与查询");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) result.getPagingResult().getItems();
        assertEquals(1, rows.size(), "fieldAccess 允许全部请求字段时应返回真实数据");
        assertEquals(orderId, rows.get(0).get("orderId"));
    }

    @Test
    @DisplayName("aggregate relation 输出字段缺少 fieldAccess 时应拒绝")
    void aggregateRelationOutputFieldShouldFailClosedWhenMissingFromFieldAccess() {
        String orderId = findOrderIdWithCompletedSales();
        DbQueryRequestDef queryRequest = buildOrderSalesAggregateRelationRequest();
        queryRequest.setSlice(List.of(slice("orderId", "=", orderId)));

        ModelResultContext context = buildQueryFacadeContext(queryRequest);
        context.setFieldAccess(Set.of("orderId", "amount", "uniqueCustomers"));

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> queryFacade.queryModelResult(context));
        assertTrue(exception.getMessage().contains("salesAmount"),
                "缺少 aggregate relation 输出字段权限时应指出被拒绝字段");
    }

    @Test
    @DisplayName("aggregate relation RHS 运行期 filter 应读取 ModelResultContext.extData")
    void aggregateRelationRuntimeFilterShouldReadContextExtData() {
        String orderId = findOrderIdWithCompletedSales();
        JdbcModelQueryEngine queryEngine = buildOrderSalesAggregateRelationRuntimeFilterQuery(
                Map.of("orderId", orderId),
                orderId);

        String sql = queryEngine.getSql();
        assertTrue(sql.contains("agg_src.order_id = '" + orderId + "'"),
                "RHS 运行期 filter 应在聚合前 WHERE 渲染为受控字面量");
        assertFalse(sql.contains("ctx.extData"), "SQL 不应泄漏运行期函数源码");

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                sql,
                queryEngine.getValues().toArray());
        assertEquals(1, rows.size(), "指定订单应只返回一行");

        BigDecimal nativeSalesAmount = jdbcTemplate.queryForObject(
                "select sum(sales_amount) from fact_sales where order_id = ? and order_status = 'COMPLETED'",
                BigDecimal.class,
                orderId);
        assertEquals(0, money(nativeSalesAmount).compareTo(money(rows.get(0).get("salesAmount"))),
                "RHS 运行期 filter 后的聚合结果应与原生查询一致");
    }

    @Test
    @DisplayName("O616: aggregate relation RHS 输出字段 is null slice 不应下推到 RHS 子查询")
    void aggregateRelationOutputNullSliceShouldStayOuterWhere() {
        JdbcModelQueryEngine queryEngine = buildOrderSalesAggregateRelationNullSlicePushdownProbeQuery("is null");

        String sql = queryEngine.getSql();
        String normalizedSql = normalizeSql(sql);
        assertTrue(normalizedSql.contains("left join (select"),
                "summary columns 不引用 RHS 字段时，slice 引用 RHS alias 仍应保留 LEFT JOIN");
        assertTrue(normalizedSql.contains("fsByPaymentMethod"),
                "aggregate relation alias 应保留在外层 SQL 中");
        assertTrue(normalizedSql.contains("agg_src.payment_method = 'CREDIT_CARD'"),
                "RHS runtime filter 应保留在聚合前 WHERE");
        assertFalse(normalizedSql.contains("agg_src.payment_method is null"),
                "外层 RHS alias is null slice 不应复制到 RHS 聚合子查询内部");
        assertTrue(normalizedSql.contains("fsByPaymentMethod.paymentMethod is null"),
                "RHS alias is null slice 应只渲染在外层 WHERE");
    }

    @Test
    @DisplayName("O616: aggregate relation RHS 输出字段 is not null slice 不应下推到 RHS 子查询")
    void aggregateRelationOutputNotNullSliceShouldStayOuterWhere() {
        JdbcModelQueryEngine queryEngine = buildOrderSalesAggregateRelationNullSlicePushdownProbeQuery("is not null");

        String normalizedSql = normalizeSql(queryEngine.getSql());
        assertTrue(normalizedSql.contains("left join (select"),
                "slice 引用 RHS alias 时应保留 LEFT JOIN");
        assertTrue(normalizedSql.contains("agg_src.payment_method = 'CREDIT_CARD'"),
                "RHS runtime filter 应保留在聚合前 WHERE");
        assertFalse(normalizedSql.contains("agg_src.payment_method is not null"),
                "外层 RHS alias is not null slice 不应复制到 RHS 聚合子查询内部");
        assertTrue(normalizedSql.contains("fsByPaymentMethod.paymentMethod is not null"),
                "RHS alias is not null slice 应只渲染在外层 WHERE");
    }

    @Test
    @DisplayName("aggregate relation RHS 运行期 filter 缺值应失败关闭")
    void aggregateRelationRuntimeFilterShouldFailClosedWhenMissing() {
        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> buildOrderSalesAggregateRelationRuntimeFilterQuery(null, null));

        assertTrue(exception.getMessage().contains("runtime filter"),
                "缺少 extData 值时应拒绝生成 SQL");
    }

    @Test
    @DisplayName("aggregate relation RHS 运行期 filter 应拒绝非法字符")
    void aggregateRelationRuntimeFilterShouldRejectUnsafeCharacters() {
        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> buildOrderSalesAggregateRelationRuntimeFilterQuery(
                        Map.of("orderId", "ORD001' OR '1'='1"),
                        null));

        assertTrue(exception.getMessage().contains("runtime filter"),
                "非法字符应被安全校验拦截");
    }

    @Test
    @DisplayName("aggregate relation accessBuilder 字段引用条件应复制到右侧 WHERE")
    void aggregateRelationAccessBuilderFieldRefShouldPushRightWhere() {
        String orderId = "ORD20240101000001";
        JdbcModelQueryEngine queryEngine = buildOrderSalesAggregateRelationAccessQuery();

        String sql = queryEngine.getSql();
        assertTrue(sql.contains("agg_src.order_id = '" + orderId + "'"),
                "accessBuilder 追加的左侧 join key 守卫应复制到 RHS 聚合前 WHERE");
        assertTrue(queryEngine.getValues().contains(orderId),
                "外层 WHERE 仍应保留 accessBuilder 参数化条件");

        JdbcQueryModel queryModel = getQueryModel("OrderSalesAggregateRelationAccessQueryModel");
        DbQueryColumn salesAmountColumn = queryModel.findJdbcQueryColumnByName("salesAmount", true);
        assertEquals("销售金额", salesAmountColumn.getCaption(),
                "未显式声明 caption 的 aggregate 字段应继承 TM measure caption");
        assertEquals(DbColumnType.MONEY, salesAmountColumn.getType(),
                "frontend/schema 可通过 QueryColumn 读取 aggregate measure 类型");

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                sql,
                queryEngine.getValues().toArray());
        assertEquals(1, rows.size(), "accessBuilder 限定订单后应返回一行真实数据");
        assertEquals(orderId, rows.get(0).get("orderId"));
    }

    @Test
    @DisplayName("aggregate relation ON 左键应支持已 join 维度字段")
    void aggregateRelationOnLeftKeyShouldSupportJoinedDimensionField() {
        String orderId = findOrderIdWithActiveStore();
        JdbcModelQueryEngine queryEngine = buildOrderStoreAggregateRelationDimensionKeyQuery(orderId);

        String sql = queryEngine.getSql();
        String normalizedSql = normalizeSql(sql);
        assertTrue(normalizedSql.contains("left join dim_store"),
                "ON 左侧维度字段应触发维表 JOIN，而不是直接拼 root alias.fieldAlias");
        assertFalse(sql.contains("store$storeId"),
                "aggregate relation ON 不应把维度字段别名当成根表物理列渲染");
        assertTrue(sql.contains("store_id = storeAggByBusinessId.storeId")
                        || sql.contains("store_id=storeAggByBusinessId.storeId"),
                "aggregate relation ON 左侧应使用已 join 维表的物理列表达式");

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                sql,
                queryEngine.getValues().toArray());
        assertEquals(1, rows.size(), "指定订单应返回一行真实数据");

        BigDecimal nativeArea = jdbcTemplate.queryForObject("""
                select ds.area_sqm
                from fact_order fo
                join dim_store ds on fo.store_key = ds.store_key
                where fo.order_id = ?
                  and ds.status = 'ACTIVE'
                """, BigDecimal.class, orderId);
        assertEquals(0, money(nativeArea).compareTo(money(rows.get(0).get("areaSqm"))),
                "维度字段 ON 连接到的 RHS 聚合结果应与原生查询一致");
    }

    @Test
    @DisplayName("aggregate relation ON 左键维度字段被 slice 使用时应可解析 join path")
    void aggregateRelationOnLeftDimensionKeySliceShouldResolveJoinPath() {
        Map<String, Object> activeStoreOrder = jdbcTemplate.queryForMap("""
                select fo.order_id orderId, ds.store_id storeId
                from fact_order fo
                join dim_store ds on fo.store_key = ds.store_key
                where ds.status = 'ACTIVE'
                order by fo.order_id
                limit 1
                """);
        String orderId = String.valueOf(activeStoreOrder.get("orderId"));
        String storeId = String.valueOf(activeStoreOrder.get("storeId"));

        JdbcQueryModel queryModel = getQueryModel("OrderStoreAggregateRelationDimensionKeyQueryModel");
        assertNotNull(queryModel, "查询模型加载失败");

        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("OrderStoreAggregateRelationDimensionKeyQueryModel");
        queryRequest.setColumns(Arrays.asList("orderId", "amount", "areaSqm"));
        queryRequest.setSlice(List.of(
                slice("orderId", "=", orderId),
                slice("store$storeId", "=", storeId)));

        queryEngine.analysisQueryRequest(systemBundlesContext, queryRequest);

        String sql = queryEngine.getSql();
        String normalizedSql = normalizeSql(sql);
        assertTrue(normalizedSql.contains("left join dim_store"),
                "slice 中的左侧维度字段应能复用维表 JOIN");
        assertTrue(normalizedSql.matches("(?s).*d\\d+\\.store_id\\s*=\\?.*"),
                "外层 WHERE 应使用维表物理字段表达式");

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                sql,
                queryEngine.getValues().toArray());
        assertEquals(1, rows.size(), "订单与门店双条件限定后应返回一行真实数据");
        assertEquals(orderId, rows.get(0).get("orderId"));
    }

    @Test
    @DisplayName("aggregate relation ON 左键维度路径与显式 join 公开字段并存时应可解析")
    void aggregateRelationDimensionPathShouldCoexistWithExplicitJoinSlice() {
        Map<String, Object> activeStoreOrder = jdbcTemplate.queryForMap("""
                select fo.order_id orderId, ds.store_id storeId
                from fact_order fo
                join dim_store ds on fo.store_key = ds.store_key
                where ds.status = 'ACTIVE'
                order by fo.order_id
                limit 1
                """);
        String orderId = String.valueOf(activeStoreOrder.get("orderId"));
        String storeId = String.valueOf(activeStoreOrder.get("storeId"));

        JdbcQueryModel queryModel = getQueryModel("OrderStoreAggregateRelationDualPathQueryModel");
        assertNotNull(queryModel, "查询模型加载失败");

        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("OrderStoreAggregateRelationDualPathQueryModel");
        queryRequest.setColumns(Arrays.asList("orderId", "storeId", "amount", "areaSqm"));
        queryRequest.setSlice(List.of(
                slice("orderId", "=", orderId),
                slice("storeId", "=", storeId)));

        queryEngine.analysisQueryRequest(systemBundlesContext, queryRequest);

        String sql = queryEngine.getSql();
        String normalizedSql = normalizeSql(sql);
        assertTrue(normalizedSql.contains("left join dim_store"),
                "显式 join 与维度路径 JOIN 应能共同参与 query graph");
        assertTrue(normalizedSql.contains("storeAggByBusinessId"),
                "aggregate relation 应正常渲染");

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                sql,
                queryEngine.getValues().toArray());
        assertEquals(1, rows.size(), "订单与显式门店字段限定后应返回一行真实数据");
        assertEquals(orderId, rows.get(0).get("orderId"));
        assertEquals(storeId, rows.get(0).get("storeId"));
    }

    @Test
    @DisplayName("aggregate relation 双路径模型无 columns 请求应可解析")
    void aggregateRelationDualPathNoColumnsRequestShouldResolveJoinPath() {
        Map<String, Object> activeStoreOrder = jdbcTemplate.queryForMap("""
                select fo.order_id orderId, ds.store_id storeId
                from fact_order fo
                join dim_store ds on fo.store_key = ds.store_key
                where ds.status = 'ACTIVE'
                order by fo.order_id
                limit 1
                """);
        String orderId = String.valueOf(activeStoreOrder.get("orderId"));
        String storeId = String.valueOf(activeStoreOrder.get("storeId"));

        JdbcQueryModel queryModel = getQueryModel("OrderStoreAggregateRelationDualPathQueryModel");
        assertNotNull(queryModel, "查询模型加载失败");

        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("OrderStoreAggregateRelationDualPathQueryModel");
        queryRequest.setSlice(List.of(
                slice("orderId", "=", orderId),
                slice("storeId", "=", storeId)));

        queryEngine.analysisQueryRequest(systemBundlesContext, queryRequest);

        String sql = queryEngine.getSql();
        assertTrue(sql.contains("storeAggByBusinessId"),
                "无 columns 请求仍应保留默认列集中的 aggregate relation");
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                sql,
                queryEngine.getValues().toArray());
        assertEquals(1, rows.size(), "无 columns 请求应返回一行真实数据");
    }

    @Test
    @DisplayName("O615 probe: 三键 aggregate relation 双路径模型无 columns 和 access 应可解析")
    void aggregateRelationO615ProbeNoColumnsWithAccessShouldResolveJoinPath() {
        Map<String, Object> stockOrder = jdbcTemplate.queryForMap("""
                select fo.order_id orderId, ds.store_id srcId, ds.store_type useType
                from fact_order fo
                join dim_store ds on fo.store_key = ds.store_key
                where fo.date_key = 20240101
                  and fo.total_quantity > 0
                order by fo.order_id
                limit 1
                """);
        String orderId = String.valueOf(stockOrder.get("orderId"));
        String srcId = String.valueOf(stockOrder.get("srcId"));
        String useType = String.valueOf(stockOrder.get("useType"));

        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("OrderStationStockProjectionO615ProbeQueryModel");
        queryRequest.setSlice(List.of(
                slice("orderId", "=", orderId),
                slice("srcId", "=", srcId),
                slice("useType", "=", useType),
                slice("number", ">", 0)));

        ModelResultContext context = buildQueryFacadeContext(queryRequest);
        DbQueryResult result = queryFacade.queryModelResult(context);
        JdbcModelQueryEngine queryEngine = (JdbcModelQueryEngine) result.getQueryEngine();
        String sql = queryEngine.getSql();
        String normalizedSql = normalizeSql(sql);

        assertTrue(normalizedSql.contains("plannedByOrder"),
                "三键 aggregate relation 应正常渲染");
        assertTrue(normalizedSql.contains("left join dim_store"),
                "显式库存仓 join 与 stockHouse 维度路径 join 应都能进入 query graph");
        assertTrue(normalizedSql.contains("where"),
                "slice 与 access 应正常生成 WHERE");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) result.getPagingResult().getItems();
        assertEquals(1, rows.size(), "O615 no-columns payload 形态应返回一行真实数据");
        assertEquals(orderId, rows.get(0).get("orderId"));
    }

    @Test
    @DisplayName("O615 probe: orderNo 来自显式 join 时无 columns 和 access 应可解析")
    void aggregateRelationO615ProbeExpressJoinNoColumnsShouldResolveJoinPath() {
        Map<String, Object> stockOrder = jdbcTemplate.queryForMap("""
                select fo.order_id orderNo, ds.store_id srcId, ds.store_type useType
                from fact_order fo
                join dim_store ds on fo.store_key = ds.store_key
                where fo.date_key = 20240101
                  and fo.total_quantity > 0
                order by fo.order_id
                limit 1
                """);
        String orderNo = String.valueOf(stockOrder.get("orderNo"));
        String srcId = String.valueOf(stockOrder.get("srcId"));
        String useType = String.valueOf(stockOrder.get("useType"));

        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("OrderStationStockProjectionO615ExpressJoinProbeQueryModel");
        queryRequest.setSlice(List.of(
                slice("orderNo", "=", orderNo),
                slice("srcId", "=", srcId),
                slice("useType", "=", useType),
                slice("number", ">", 0)));

        ModelResultContext context = buildQueryFacadeContext(queryRequest);
        DbQueryResult result = queryFacade.queryModelResult(context);
        JdbcModelQueryEngine queryEngine = (JdbcModelQueryEngine) result.getQueryEngine();
        String normalizedSql = normalizeSql(queryEngine.getSql());

        assertTrue(normalizedSql.contains("plannedByOrder"),
                "三键 aggregate relation 应正常渲染");
        assertTrue(normalizedSql.contains("left join fact_order"),
                "显式运单 join 应进入 query graph");
        assertTrue(normalizedSql.contains("left join dim_store"),
                "显式库存仓 join 与 stockHouse 维度路径 join 应都能进入 query graph");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) result.getPagingResult().getItems();
        assertEquals(1, rows.size(), "O615 orderNo slice payload 形态应返回一行真实数据");
        assertEquals(orderNo, rows.get(0).get("orderNo"));
    }

    @Test
    @DisplayName("O615 probe: 显式 join RHS 维度 $id slice 应可解析")
    void aggregateRelationO615ProbeExpressJoinDimensionIdSliceShouldResolveJoinPath() {
        Map<String, Object> stockOrder = jdbcTemplate.queryForMap("""
                select fo.order_id orderNo, ds.store_id srcId, ds.store_type useType, ds.store_key destinationServiceAreaId
                from fact_order fo
                join dim_store ds on fo.store_key = ds.store_key
                where fo.date_key = 20240101
                  and fo.total_quantity > 0
                order by fo.order_id
                limit 1
                """);
        String orderNo = String.valueOf(stockOrder.get("orderNo"));
        String srcId = String.valueOf(stockOrder.get("srcId"));
        String useType = String.valueOf(stockOrder.get("useType"));
        Object destinationServiceAreaId = stockOrder.get("destinationServiceAreaId");

        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("OrderStationStockProjectionO615ExpressJoinProbeQueryModel");
        queryRequest.setColumns(Arrays.asList("orderNo", "srcId", "useType", "number"));
        queryRequest.setSlice(List.of(
                slice("orderNo", "=", orderNo),
                slice("srcId", "=", srcId),
                slice("useType", "=", useType),
                slice("number", ">", 0),
                slice("destinationServiceArea$id", "in", List.of(destinationServiceAreaId))));

        ModelResultContext context = buildQueryFacadeContext(queryRequest);
        DbQueryResult result = queryFacade.queryModelResult(context);
        JdbcModelQueryEngine queryEngine = (JdbcModelQueryEngine) result.getQueryEngine();
        String normalizedSql = normalizeSql(queryEngine.getSql());

        assertTrue(normalizedSql.contains("left join fact_order"),
                "显式运单 join 应进入 query graph");
        assertTrue(normalizedSql.contains("destinationServiceArea") || normalizedSql.contains("store_key"),
                "RHS 维度 $id slice 应渲染为可达字段，不应引用 graph 外 alias");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) result.getPagingResult().getItems();
        assertEquals(1, rows.size(), "O615 destinationServiceArea$id slice payload 形态应返回一行真实数据");
        assertEquals(orderNo, rows.get(0).get("orderNo"));
    }

    @Test
    @DisplayName("O615 probe: RHS 维度过滤叠加 orderNo slice 时应可解析")
    void aggregateRelationO615ProbeRhsDimensionFilterShouldResolveJoinPath() {
        Map<String, Object> stockOrder = jdbcTemplate.queryForMap("""
                select fo.order_id orderNo, ds.store_id srcId, ds.store_type useType
                from fact_order fo
                join dim_store ds on fo.store_key = ds.store_key
                where fo.date_key = 20240101
                  and fo.total_quantity > 0
                order by fo.order_id
                limit 1
                """);
        String orderNo = String.valueOf(stockOrder.get("orderNo"));
        String srcId = String.valueOf(stockOrder.get("srcId"));
        String useType = String.valueOf(stockOrder.get("useType"));

        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("OrderStationStockProjectionO615RhsDimensionProbeQueryModel");
        queryRequest.setSlice(List.of(
                slice("orderNo", "=", orderNo),
                slice("srcId", "=", srcId),
                slice("useType", "=", useType),
                slice("number", ">", 0)));

        ModelResultContext context = buildQueryFacadeContext(queryRequest);
        DbQueryResult result = queryFacade.queryModelResult(context);
        JdbcModelQueryEngine queryEngine = (JdbcModelQueryEngine) result.getQueryEngine();
        String sql = queryEngine.getSql();
        String normalizedSql = normalizeSql(sql);

        assertTrue(normalizedSql.contains("plannedByOrder"),
                "三键 aggregate relation 应正常渲染");
        assertTrue(normalizedSql.contains("agg_src"),
                "RHS 聚合子查询应正常渲染");
        assertTrue(normalizedSql.contains("status = 'ACTIVE'") || normalizedSql.contains("status='ACTIVE'"),
                "RHS 维度字段固定过滤应进入聚合前过滤");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) result.getPagingResult().getItems();
        assertEquals(1, rows.size(), "O615 RHS 维度过滤 payload 形态应返回一行真实数据");
        assertEquals(orderNo, rows.get(0).get("orderNo"));
    }

    @Test
    @DisplayName("O615 probe: RHS 聚合源内部维表 join 叠加 orderNo slice 时应可解析")
    void aggregateRelationO615ProbeRhsJoinDimensionFilterShouldResolveJoinPath() {
        Map<String, Object> stockOrder = jdbcTemplate.queryForMap("""
                select fo.order_id orderNo, ds.store_id srcId, ds.store_type useType
                from fact_order fo
                join dim_store ds on fo.store_key = ds.store_key
                where fo.date_key = 20240101
                  and fo.total_quantity > 0
                order by fo.order_id
                limit 1
                """);
        String orderNo = String.valueOf(stockOrder.get("orderNo"));
        String srcId = String.valueOf(stockOrder.get("srcId"));
        String useType = String.valueOf(stockOrder.get("useType"));

        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("OrderStationStockProjectionO615RhsJoinDimensionProbeQueryModel");
        queryRequest.setSlice(List.of(
                slice("orderNo", "=", orderNo),
                slice("srcId", "=", srcId),
                slice("useType", "=", useType),
                slice("number", ">", 0)));

        ModelResultContext context = buildQueryFacadeContext(queryRequest);
        DbQueryResult result = queryFacade.queryModelResult(context);
        JdbcModelQueryEngine queryEngine = (JdbcModelQueryEngine) result.getQueryEngine();
        String normalizedSql = normalizeSql(queryEngine.getSql());

        assertTrue(normalizedSql.contains("plannedByOrder"),
                "三键 aggregate relation 应正常渲染");
        assertTrue(normalizedSql.contains("left join dim_store"),
                "主查询和 RHS 子查询涉及的维表 join 均应可解析");
        assertTrue(normalizedSql.contains("status = 'ACTIVE'") || normalizedSql.contains("status='ACTIVE'"),
                "RHS 内部维表过滤应进入聚合前过滤");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) result.getPagingResult().getItems();
        assertEquals(1, rows.size(), "O615 RHS 内部维表 join payload 形态应返回一行真实数据");
        assertEquals(orderNo, rows.get(0).get("orderNo"));
    }

    @Test
    @DisplayName("aggregate relation ON 左键应支持嵌套维度路径")
    void aggregateRelationOnLeftKeyShouldSupportNestedDimensionPath() {
        JdbcModelQueryEngine queryEngine = buildSalesNestedCategoryAggregateRelationDimensionPathQuery();

        String sql = queryEngine.getSql();
        String normalizedSql = normalizeSql(sql).toLowerCase();
        int productJoin = normalizedSql.indexOf("left join dim_product_nested");
        int categoryJoin = normalizedSql.indexOf("left join dim_category_nested");
        int aggregateJoin = normalizedSql.indexOf("left join (select");
        assertTrue(productJoin > 0, "ON 左侧嵌套路径应先触发一级商品维表 JOIN");
        assertTrue(categoryJoin > productJoin, "ON 左侧嵌套路径应继续触发二级品类维表 JOIN");
        assertTrue(aggregateJoin > categoryJoin, "维度路径依赖 JOIN 应先于 aggregate derived table 生成");
        assertFalse(sql.contains("product.category$categoryId"),
                "aggregate relation ON 不应把嵌套路径表达式直接渲染进 SQL");
        assertFalse(sql.contains("product_category$categoryId"),
                "aggregate relation ON 不应把嵌套路径别名当成根表物理列渲染");
        assertTrue(sql.contains("category_id = categoryAggByBusinessId.categoryId")
                        || sql.contains("category_id=categoryAggByBusinessId.categoryId"),
                "aggregate relation ON 左侧应使用二级品类维表的物理列表达式");

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                sql,
                queryEngine.getValues().toArray());
        assertFalse(rows.isEmpty(), "嵌套维度路径 aggregate relation 应返回真实数据");

        Map<String, Integer> nativeCategoryLevels = nativeCategoryLevelsByProductId();
        for (Map<String, Object> row : rows) {
            String productId = String.valueOf(row.get("product$productId"));
            Integer expected = nativeCategoryLevels.get(productId);
            assertNotNull(expected, "原生查询应能按商品ID找到品类层级：" + productId);
            assertEquals(expected.intValue(), ((Number) row.get("categoryLevel")).intValue(),
                    "嵌套维度路径 ON 连接到的 RHS 聚合结果应与原生查询一致");
        }
    }

    @Test
    @DisplayName("aggregate relation RHS 固定条件应支持右侧维度字段")
    void aggregateRelationRhsFixedFilterShouldSupportRightDimensionField() {
        String orderId = findOrderIdWithCompletedElectronicsSales();
        JdbcModelQueryEngine queryEngine = buildOrderSalesAggregateRelationRhsDimensionFilterQuery(orderId);

        String sql = queryEngine.getSql();
        String normalizedSql = normalizeSql(sql).toLowerCase();
        assertTrue(normalizedSql.contains("from fact_sales agg_src left join dim_product"),
                "RHS 维度字段 fixed filter 应在 aggregate derived table 内补齐右侧维表 JOIN");
        assertFalse(sql.contains("agg_src.category_id"),
                "RHS 维度字段不应被错误渲染为 RHS 根表物理列");
        assertTrue(sql.contains("category_id = 'CAT001'") || sql.contains("category_id='CAT001'"),
                "RHS fixed filter 应使用右侧维表物理列表达式");

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                sql,
                queryEngine.getValues().toArray());
        assertEquals(1, rows.size(), "指定订单应返回一行真实数据");

        BigDecimal nativeSalesAmount = jdbcTemplate.queryForObject("""
                select sum(fs.sales_amount)
                from fact_sales fs
                join dim_product dp on fs.product_key = dp.product_key
                where fs.order_id = ?
                  and fs.order_status = 'COMPLETED'
                  and dp.category_id = 'CAT001'
                """, BigDecimal.class, orderId);
        assertEquals(0, money(nativeSalesAmount).compareTo(money(rows.get(0).get("salesAmount"))),
                "RHS 维度字段 fixed filter 后的聚合结果应与原生查询一致");
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

    private JdbcModelQueryEngine buildOrderSalesAggregateRelationAccessQuery() {
        JdbcQueryModel queryModel = getQueryModel("OrderSalesAggregateRelationAccessQueryModel");
        assertNotNull(queryModel, "查询模型加载失败");

        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);

        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("OrderSalesAggregateRelationAccessQueryModel");
        queryRequest.setColumns(Arrays.asList("orderId", "amount", "salesAmount", "uniqueCustomers"));

        queryEngine.analysisQueryRequest(systemBundlesContext, queryRequest);
        return queryEngine;
    }

    private JdbcModelQueryEngine buildOrderSalesAggregateRelationRuntimeFilterQuery(
            Map<String, Object> extData,
            String outerOrderId) {
        JdbcQueryModel queryModel = getQueryModel("OrderSalesAggregateRelationRuntimeFilterQueryModel");
        assertNotNull(queryModel, "查询模型加载失败");

        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);

        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("OrderSalesAggregateRelationRuntimeFilterQueryModel");
        queryRequest.setColumns(Arrays.asList("orderId", "amount", "salesAmount", "uniqueCustomers"));
        queryRequest.setExtData(extData);
        if (outerOrderId != null) {
            queryRequest.setSlice(List.of(slice("orderId", "=", outerOrderId)));
        }

        queryEngine.analysisQueryRequest(systemBundlesContext, queryRequest);
        return queryEngine;
    }

    private JdbcModelQueryEngine buildOrderSalesAggregateRelationNullSlicePushdownProbeQuery(String op) {
        JdbcQueryModel queryModel = getQueryModel("OrderSalesAggregateRelationNullSlicePushdownProbeQueryModel");
        assertNotNull(queryModel, "查询模型加载失败");

        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);

        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("OrderSalesAggregateRelationNullSlicePushdownProbeQueryModel");
        queryRequest.setColumns(Arrays.asList(
                "count(orderId) as candidateCount",
                "sum(amount) as totalAmount"));
        queryRequest.setExtData(Map.of("paymentMethod", "CREDIT_CARD"));
        queryRequest.setSlice(List.of(slice("paymentMethod", op, null)));

        queryEngine.analysisQueryRequest(systemBundlesContext, queryRequest);
        return queryEngine;
    }

    private JdbcModelQueryEngine buildOrderStoreAggregateRelationDimensionKeyQuery(String orderId) {
        JdbcQueryModel queryModel = getQueryModel("OrderStoreAggregateRelationDimensionKeyQueryModel");
        assertNotNull(queryModel, "查询模型加载失败");

        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);

        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("OrderStoreAggregateRelationDimensionKeyQueryModel");
        queryRequest.setColumns(Arrays.asList("orderId", "amount", "areaSqm"));
        queryRequest.setSlice(List.of(slice("orderId", "=", orderId)));

        queryEngine.analysisQueryRequest(systemBundlesContext, queryRequest);
        return queryEngine;
    }

    private JdbcModelQueryEngine buildSalesNestedCategoryAggregateRelationDimensionPathQuery() {
        JdbcQueryModel queryModel = getQueryModel("SalesNestedCategoryAggregateRelationDimensionPathQueryModel");
        assertNotNull(queryModel, "查询模型加载失败");

        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);

        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("SalesNestedCategoryAggregateRelationDimensionPathQueryModel");
        queryRequest.setColumns(Arrays.asList("product$productId", "salesAmount", "categoryLevel"));

        queryEngine.analysisQueryRequest(systemBundlesContext, queryRequest);
        return queryEngine;
    }

    private JdbcModelQueryEngine buildOrderSalesAggregateRelationRhsDimensionFilterQuery(String orderId) {
        JdbcQueryModel queryModel = getQueryModel("OrderSalesAggregateRelationRhsDimensionFilterQueryModel");
        assertNotNull(queryModel, "查询模型加载失败");

        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);

        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("OrderSalesAggregateRelationRhsDimensionFilterQueryModel");
        queryRequest.setColumns(Arrays.asList("orderId", "amount", "salesAmount"));
        queryRequest.setSlice(List.of(slice("orderId", "=", orderId)));

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
        assertFalse(orderIds.isEmpty(), "测试数据应至少包含一个有订单头的 COMPLETED 数码品类销售订单");
        return orderIds.get(0);
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
        assertFalse(orderIds.isEmpty(), "测试数据应至少包含一个 ACTIVE 门店订单");
        return orderIds.get(0);
    }

    private Map<String, Integer> nativeCategoryLevelsByProductId() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                select dp.product_id productId, dc.category_level categoryLevel
                from dim_product_nested dp
                join dim_category_nested dc on dp.category_key = dc.category_key
                where dc.status = 'ACTIVE'
                """);
        assertFalse(rows.isEmpty(), "测试数据应至少包含 ACTIVE 品类关联商品");

        Map<String, Integer> result = new HashMap<>();
        for (Map<String, Object> row : rows) {
            result.put(String.valueOf(row.get("productId")), ((Number) row.get("categoryLevel")).intValue());
        }
        return result;
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
