package com.foggyframework.dataset.db.model.ecommerce;

import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.dataset.client.domain.PagingRequest;
import com.foggyframework.dataset.db.model.def.query.request.CalculatedFieldDef;
import com.foggyframework.dataset.db.model.def.query.request.CondRequestDef;
import com.foggyframework.dataset.db.model.def.query.request.DbQueryRequestDef;
import com.foggyframework.dataset.db.model.def.query.request.OrderRequestDef;
import com.foggyframework.dataset.db.model.def.query.request.SliceRequestDef;
import com.foggyframework.dataset.db.model.engine.JdbcModelQueryEngine;
import com.foggyframework.dataset.db.model.engine.formula.SqlFormulaService;
import com.foggyframework.dataset.db.model.engine.query.DbQueryResult;
import com.foggyframework.dataset.db.model.impl.model.AggregateJoinTableModel;
import com.foggyframework.dataset.db.model.impl.model.AggregateRelationDiagnostic;
import com.foggyframework.dataset.db.model.impl.model.AggregateRelationOutputColumn;
import com.foggyframework.dataset.db.model.impl.model.AggregateRelationQueryObject;
import com.foggyframework.dataset.db.model.plugins.result_set_filter.ModelResultContext;
import com.foggyframework.dataset.db.model.plugins.result_set_filter.AggregateMemberFilterPlanner;
import com.foggyframework.dataset.db.model.plugins.result_set_filter.AggregateMemberFilterRewriteStep;
import com.foggyframework.dataset.db.model.proxy.AggregateJoinBuilder;
import com.foggyframework.dataset.db.model.proxy.TableModelProxy;
import com.foggyframework.dataset.db.model.semantic.domain.DeniedPhysicalColumn;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticQueryRequest;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticQueryResponse;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticRequestContext;
import com.foggyframework.dataset.db.model.semantic.service.SemanticQueryServiceV3;
import com.foggyframework.dataset.db.model.service.QueryFacade;
import com.foggyframework.dataset.db.model.spi.DbColumn;
import com.foggyframework.dataset.db.model.spi.DbColumnType;
import com.foggyframework.dataset.db.model.spi.DbQueryColumn;
import com.foggyframework.dataset.db.model.spi.JdbcQueryModel;
import com.foggyframework.dataset.db.model.spi.QueryObject;
import com.foggyframework.dataset.db.model.spi.TableModel;
import com.foggyframework.dataset.model.PagingResultImpl;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.IdentityHashMap;
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

    @Resource
    private SemanticQueryServiceV3 semanticQueryService;

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
        assertFalse(normalizedSql.contains("count(distinct"), "未请求的 COUNT DISTINCT 聚合不应进入 RHS SELECT");
        assertTrue(normalizedSql.contains("group by"), "右侧子查询应包含 GROUP BY");
        assertTrue(normalizedSql.contains("fact_sales"), "右侧子查询应读取销售明细表");
        assertTrue(sql.contains("agg_src.order_status = ?"), "右侧固定 slice 应在聚合前下推并使用参数绑定");
        assertTrue(queryEngine.getValues().contains("COMPLETED"), "右侧固定 slice 参数应进入查询参数列表");
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
        assertFalse(sql.contains("sum(agg_src.quantity) quantity"), "未请求的 aggregate relation measure 不应进入 RHS SELECT");
        assertFalse(sql.contains("sum(agg_src.unit_price) unitPrice"), "未请求的 aggregate relation measure 不应进入 RHS SELECT");
        assertTrue(sql.contains("agg_src.order_status = ?"), "右侧 fixed slice 应在聚合前下推并使用参数绑定");
        assertTrue(queryEngine.getValues().contains("COMPLETED"), "右侧 fixed slice 参数应进入查询参数列表");
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
        assertTrue(normalizedSql.contains("having sum(agg_src.sales_amount) > ?"),
                "右侧聚合子查询应包含 measure HAVING 下推");
        assertTrue(normalizedSql.contains("fsByOrder.salesAmount >?"),
                "外层 WHERE 应保留 aggregate relation measure 条件以保持 LEFT 语义");
        assertTrue(queryEngine.getValues().contains("COMPLETED"), "右侧 fixed slice 参数应进入查询参数列表");
        assertTrue(countBigDecimalValues(queryEngine.getValues(), BigDecimal.ZERO) >= 2,
                "RHS HAVING 与外层 WHERE 都应使用参数化绑定");
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
            assertTrue(normalizedBody.contains("where agg_src.order_status = ? and agg_src.order_id = ?"),
                    "aggregate relation group key 条件应进入右侧聚合前 WHERE");
            assertEquals(List.of("COMPLETED", orderId), ((DbColumn) groupKey).getQueryObject().getBodyParameters(),
                    "aggregate relation body 参数应按 RHS SQL 占位符顺序输出");
            assertFalse(normalizedBody.contains("having"), "group key 条件不应进入 HAVING");
        } finally {
            ((AggregateRelationQueryObject) ((DbColumn) groupKey).getQueryObject()).clearAggregateRelationPushdowns();
        }
    }

    @Test
    @DisplayName("aggregate relation pushdown 拒绝路径应记录稳定 reason code")
    void aggregateRelationPushdownRefusalShouldRecordReasonCodes() {
        JdbcQueryModel queryModel = getQueryModel("OrderSalesAggregateRelationQueryModel");
        assertNotNull(queryModel, "查询模型加载失败");
        DbColumn salesAmountColumn = queryModel.findJdbcColumnForCond("salesAmount", true, true);
        assertTrue(salesAmountColumn instanceof AggregateRelationOutputColumn,
                "salesAmount 应来自 aggregate relation 输出列");

        AggregateRelationOutputColumn salesAmount = (AggregateRelationOutputColumn) salesAmountColumn;
        AggregateRelationQueryObject queryObject = resolveAggregateRelationQueryObject(salesAmountColumn.getQueryObject());
        assertNotNull(queryObject, "aggregate relation query object 应可解析");

        try {
            assertFalse(salesAmount.pushAggregateRelationCondition("regexp", "10"),
                    "unsupported operator 不应下推");
            assertFalse(salesAmount.pushAggregateRelationCondition("in", List.of()),
                    "empty IN 不应下推");
            assertFalse(salesAmount.pushAggregateRelationCondition("[]", List.of(BigDecimal.ZERO)),
                    "invalid range 不应下推");

            List<AggregateRelationDiagnostic> diagnostics = queryObject.getAggregateRelationDiagnostics();
            assertTrue(diagnostics.stream().anyMatch(diagnostic ->
                            "refused".equals(diagnostic.decision())
                                    && AggregateRelationQueryObject.REASON_UNSUPPORTED_OPERATOR.equals(diagnostic.reasonCode())
                                    && "salesAmount".equals(diagnostic.field())),
                    "unsupported operator 应记录拒绝原因");
            assertTrue(diagnostics.stream().anyMatch(diagnostic ->
                            "refused".equals(diagnostic.decision())
                                    && AggregateRelationQueryObject.REASON_EMPTY_IN_VALUES.equals(diagnostic.reasonCode())
                                    && "salesAmount".equals(diagnostic.field())),
                    "empty IN 应记录拒绝原因");
            assertTrue(diagnostics.stream().anyMatch(diagnostic ->
                            "refused".equals(diagnostic.decision())
                                    && AggregateRelationQueryObject.REASON_INVALID_RANGE_VALUE.equals(diagnostic.reasonCode())
                                    && "salesAmount".equals(diagnostic.field())),
                    "invalid range 应记录拒绝原因");
        } finally {
            queryObject.clearAggregateRelationPushdowns();
        }
    }

    @Test
    @DisplayName("aggregate relation 应暴露 GROUP_CONCAT 字符串 measure 并支持 like slice")
    void aggregateRelationGroupConcatMeasureShouldRenderAndFilterByLike() {
        JdbcQueryModel queryModel = getQueryModel("OrderSalesAggregateRelationStringAggQueryModel");
        assertNotNull(queryModel, "查询模型加载失败");
        DbColumn paymentMethodListColumn = queryModel.findJdbcColumnForCond("paymentMethodList", true, true);
        assertNotNull(paymentMethodListColumn, "GROUP_CONCAT 字符串 measure 应注册为可筛选列");
        assertTrue(paymentMethodListColumn instanceof AggregateRelationOutputColumn,
                "paymentMethodList 应来自 aggregate relation 输出列");
        assertEquals(DbColumnType.STRING, paymentMethodListColumn.getType(),
                "GROUP_CONCAT 输出应暴露为 STRING");
        assertEquals("GROUP_CONCAT", paymentMethodListColumn.getAggregation().name(),
                "aggregate relation 输出列应保留 GROUP_CONCAT 聚合元数据");

        JdbcModelQueryEngine queryEngine = buildOrderSalesAggregateRelationStringAggQuery("WECHAT");

        String normalizedSql = normalizeSql(queryEngine.getSql()).toLowerCase();
        String aggregateExpression = expectedPaymentMethodListAggregateSql("agg_src.payment_method");
        assertTrue(normalizedSql.contains(aggregateExpression + " paymentmethodlist"),
                "SELECT 子查询应按当前方言渲染字符串聚合输出");
        assertTrue(normalizedSql.contains("having " + aggregateExpression + " like ?"),
                "字符串聚合字段的 like slice 应下推到 RHS HAVING");
        assertTrue(normalizedSql.contains("fsstringbyorder.paymentmethodlist like ?"),
                "外层 WHERE 仍应保留标准 slice 过滤");
        assertTrue(queryEngine.getValues().contains("COMPLETED"),
                "RHS 固定过滤参数应进入查询参数");
        assertTrue(queryEngine.getValues().stream().filter("%WECHAT%"::equals).count() >= 2,
                "like 参数应同时用于 RHS HAVING 和外层 WHERE");

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                queryEngine.getSql(),
                queryEngine.getValues().toArray());
        assertFalse(rows.isEmpty(), "测试数据应返回 WECHAT 支付方式订单");
        for (Map<String, Object> row : rows) {
            Object value = valueIgnoreCase(row, "paymentMethodList");
            assertNotNull(value, "查询结果应包含 paymentMethodList");
            assertTrue(String.valueOf(value).contains("WECHAT"),
                    "like slice 后返回的字符串聚合值应包含 WECHAT");
        }
    }

    @Test
    @DisplayName("aggregate relation 显式 measure 应暴露声明 alias 并替代默认 measure")
    void aggregateRelationExplicitMeasuresShouldExposeAliases() {
        JdbcQueryModel queryModel = getQueryModel("OrderSalesAggregateRelationExplicitMeasureQueryModel");
        assertNotNull(queryModel, "查询模型加载失败");

        DbColumn salesLineCountColumn = queryModel.findJdbcColumnForCond("salesLineCount", true, true);
        assertNotNull(salesLineCountColumn, "显式 count alias 应注册为可筛选列");
        assertTrue(salesLineCountColumn instanceof AggregateRelationOutputColumn,
                "salesLineCount 应来自 aggregate relation 输出列");
        assertEquals(DbColumnType.BIGINT, salesLineCountColumn.getType(),
                "COUNT 输出应暴露为 BIGINT");
        assertEquals("COUNT", salesLineCountColumn.getAggregation().name(),
                "显式 count alias 应保留 COUNT 聚合元数据");

        DbColumn paymentMethodListColumn = queryModel.findJdbcColumnForCond("paymentMethodList", true, true);
        assertNotNull(paymentMethodListColumn, "显式 groupConcat alias 应注册为可筛选列");
        assertTrue(paymentMethodListColumn instanceof AggregateRelationOutputColumn,
                "paymentMethodList 应来自 aggregate relation 输出列");
        assertEquals(DbColumnType.STRING, paymentMethodListColumn.getType(),
                "GROUP_CONCAT 输出应暴露为 STRING");
        assertEquals("GROUP_CONCAT", paymentMethodListColumn.getAggregation().name(),
                "显式 groupConcat alias 应保留 GROUP_CONCAT 聚合元数据");

        JdbcModelQueryEngine queryEngine = buildOrderSalesAggregateRelationExplicitMeasureQuery();
        String normalizedSql = normalizeSql(queryEngine.getSql()).toLowerCase();
        String aggregateExpression = expectedPaymentMethodListAggregateSql("agg_src.payment_method");
        assertTrue(normalizedSql.contains("count(*) saleslinecount"),
                "显式 count alias 应进入 RHS SELECT");
        assertTrue(normalizedSql.contains(aggregateExpression + " paymentmethodlist"),
                "显式 GROUP_CONCAT alias 应进入 RHS SELECT");
        assertFalse(normalizedSql.contains("sum(agg_src.sales_amount) salesamount"),
                "显式声明 measure 后不应继续暴露 TM 默认 measure");
        assertTrue(normalizedSql.contains("fsexplicitbyorder"),
                "aggregate relation 应保留显式 relation alias");
        assertTrue(queryEngine.getValues().contains("COMPLETED"),
                "RHS 固定过滤参数应进入查询参数");
    }

    @Test
    @DisplayName("aggregate relation 显式 GROUP_CONCAT like slice 应保留聚合字符串过滤")
    void aggregateRelationExplicitGroupConcatLikeShouldStayAggregateString() {
        JdbcModelQueryEngine queryEngine = buildOrderSalesAggregateRelationExplicitMeasureQuery("like", "WECHAT");

        String normalizedSql = normalizeSql(queryEngine.getSql()).toLowerCase();
        String aggregateExpression = expectedPaymentMethodListAggregateSql("agg_src.payment_method");
        assertTrue(normalizedSql.contains("having " + aggregateExpression + " like ?"),
                "显式 GROUP_CONCAT 的 like slice 应下推到 RHS HAVING");
        assertTrue(normalizedSql.contains("fsexplicitbyorder.paymentmethodlist like ?"),
                "显式 GROUP_CONCAT 的 like slice 应保留外层聚合字符串过滤");
        assertTrue(queryEngine.getValues().stream().filter("%WECHAT%"::equals).count() >= 2,
                "like 参数应同时用于 RHS HAVING 和外层 WHERE");
    }

    @Test
    @DisplayName("aggregate relation 显式 GROUP_CONCAT 等值 slice 应按源成员 EXISTS 过滤")
    void aggregateRelationExplicitGroupConcatEqualsShouldRewriteToMemberExists() {
        PaymentMemberFixture fixture = findPaymentMemberFixture();
        JdbcModelQueryEngine queryEngine = buildOrderSalesAggregateRelationExplicitMeasureQuery("=", fixture.member());

        String normalizedSql = normalizeSql(queryEngine.getSql()).toLowerCase();
        assertTrue(normalizedSql.contains("exists (select 1 from fact_sales agg_src"),
                "显式 GROUP_CONCAT 等值 slice 应改写为 RHS 成员相关 EXISTS");
        assertTrue(normalizedSql.contains("agg_src.order_status = ?"),
                "EXISTS 应继承 aggregate relation 固定过滤");
        assertTrue(normalizedSql.contains("agg_src.payment_method = ?"),
                "EXISTS 应把 alias 等值条件落到源字段");
        assertFalse(normalizedSql.contains("fsexplicitbyorder.paymentmethodlist = ?"),
                "外层 WHERE 不应再按聚合字符串做等值过滤");
        assertTrue(queryEngine.getValues().contains(fixture.member()),
                "源字段等值参数应进入查询参数列表");

        List<AggregateRelationDiagnostic> diagnostics = aggregateRelationDiagnostics(queryEngine);
        assertTrue(diagnostics.stream().anyMatch(diagnostic ->
                        "rewritten".equals(diagnostic.decision())
                                && "member".equals(diagnostic.target())
                                && "paymentMethodList".equals(diagnostic.field())
                                && diagnostic.expression().contains("exists")),
                "成员过滤改写应记录 rewritten 诊断");
    }

    @Test
    @DisplayName("aggregate relation 显式 GROUP_CONCAT IN slice 应按源成员 EXISTS 过滤")
    void aggregateRelationExplicitGroupConcatInShouldRewriteToMemberExists() {
        PaymentMemberFixture fixture = findPaymentMemberFixture();
        List<String> memberValues = findCompletedPaymentMethods(fixture.member(), 2);
        assertTrue(memberValues.contains(fixture.member()), "IN 测试值应包含动态样本订单中的支付方式");

        JdbcModelQueryEngine queryEngine = buildOrderSalesAggregateRelationExplicitMeasureQuery(
                "in",
                memberValues);

        String normalizedSql = normalizeSql(queryEngine.getSql()).toLowerCase();
        assertTrue(normalizedSql.contains("exists (select 1 from fact_sales agg_src"),
                "显式 GROUP_CONCAT IN slice 应改写为 RHS 成员相关 EXISTS");
        assertTrue(normalizedSql.contains("agg_src.payment_method in (" + placeholders(memberValues.size()) + ")"),
                "EXISTS 应把 alias IN 条件落到源字段并保持参数化");
        assertFalse(normalizedSql.contains("fsexplicitbyorder.paymentmethodlist in"),
                "外层 WHERE 不应再按聚合字符串做 IN 过滤");
        for (String memberValue : memberValues) {
            assertTrue(queryEngine.getValues().contains(memberValue),
                    "源字段 IN 参数应进入查询参数列表");
        }

        List<AggregateRelationDiagnostic> diagnostics = aggregateRelationDiagnostics(queryEngine);
        assertTrue(diagnostics.stream().anyMatch(diagnostic ->
                        "rewritten".equals(diagnostic.decision())
                                && "member".equals(diagnostic.target())
                                && "paymentMethodList".equals(diagnostic.field())
                                && diagnostic.expression().contains(" in ")),
                "成员 IN 过滤改写应记录 rewritten 诊断");
    }

    @Test
    @DisplayName("aggregate relation GROUP_CONCAT 等值 slice 应按源成员 EXISTS 过滤")
    void aggregateRelationGroupConcatMeasureEqualsShouldRewriteToMemberExists() {
        PaymentMemberFixture fixture = findPaymentMemberFixture();
        JdbcModelQueryEngine queryEngine = buildOrderSalesAggregateRelationStringAggQuery("=", fixture.member());

        String normalizedSql = normalizeSql(queryEngine.getSql()).toLowerCase();
        assertTrue(normalizedSql.contains("exists (select 1 from fact_sales agg_src"),
                "GROUP_CONCAT 等值 slice 应改写为 RHS 成员相关 EXISTS");
        assertTrue(normalizedSql.contains("agg_src.order_status = ?"),
                "EXISTS 应继承 aggregate relation 固定过滤");
        assertTrue(normalizedSql.contains("agg_src.payment_method = ?"),
                "EXISTS 应把 alias 等值条件落到源字段");
        assertFalse(normalizedSql.contains("fsstringbyorder.paymentmethodlist = ?"),
                "外层 WHERE 不应再按聚合字符串做等值过滤，否则会漏掉多成员列表");
        assertEquals(2, queryEngine.getValues().stream().filter("COMPLETED"::equals).count(),
                "RHS 聚合子查询和 EXISTS 都应绑定固定过滤参数");
        assertTrue(queryEngine.getValues().contains(fixture.member()),
                "源字段等值参数应进入查询参数列表");

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                queryEngine.getSql(),
                queryEngine.getValues().toArray());
        assertFalse(rows.isEmpty(), "测试数据应返回指定支付方式订单");
        Map<String, Object> matchedOrder = rows.stream()
                .filter(row -> fixture.orderId().equals(String.valueOf(valueIgnoreCase(row, "orderId"))))
                .findFirst()
                .orElseThrow();
        assertEquals(sortedPaymentMembers(fixture.expectedMembers()),
                sortedPaymentMembers(valueIgnoreCase(matchedOrder, "paymentMethodList")),
                "成员等值过滤不应把返回的 GROUP_CONCAT 列表收窄成单个源成员");

        List<AggregateRelationDiagnostic> diagnostics = aggregateRelationDiagnostics(queryEngine);
        assertTrue(diagnostics.stream().anyMatch(diagnostic ->
                        "rewritten".equals(diagnostic.decision())
                                && "member".equals(diagnostic.target())
                                && "paymentMethodList".equals(diagnostic.field())
                                && diagnostic.expression().contains("exists")),
                "成员过滤改写应记录 rewritten 诊断");
    }

    @Test
    @DisplayName("aggregate relation GROUP_CONCAT IN slice 应按源成员 EXISTS 过滤")
    void aggregateRelationGroupConcatMeasureInShouldRewriteToMemberExists() {
        PaymentMemberFixture fixture = findPaymentMemberFixture();
        List<String> memberValues = findCompletedPaymentMethods(fixture.member(), 2);
        assertTrue(memberValues.contains(fixture.member()), "IN 测试值应包含动态样本订单中的支付方式");

        JdbcModelQueryEngine queryEngine = buildOrderSalesAggregateRelationStringAggQuery(
                "in",
                memberValues);

        String normalizedSql = normalizeSql(queryEngine.getSql()).toLowerCase();
        assertTrue(normalizedSql.contains("exists (select 1 from fact_sales agg_src"),
                "GROUP_CONCAT IN slice 应改写为 RHS 成员相关 EXISTS");
        assertTrue(normalizedSql.contains("agg_src.payment_method in (" + placeholders(memberValues.size()) + ")"),
                "EXISTS 应把 alias IN 条件落到源字段并保持参数化");
        assertFalse(normalizedSql.contains("fsstringbyorder.paymentmethodlist in"),
                "外层 WHERE 不应再按聚合字符串做 IN 过滤");
        for (String memberValue : memberValues) {
            assertTrue(queryEngine.getValues().contains(memberValue),
                    "源字段 IN 参数应进入查询参数列表");
        }

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                queryEngine.getSql(),
                queryEngine.getValues().toArray());
        assertFalse(rows.isEmpty(), "测试数据应返回指定支付方式订单");
        assertTrue(rows.stream().anyMatch(row -> fixture.orderId().equals(String.valueOf(valueIgnoreCase(row, "orderId")))),
                "成员 IN 过滤应返回包含样本支付方式成员的订单");

        List<AggregateRelationDiagnostic> diagnostics = aggregateRelationDiagnostics(queryEngine);
        assertTrue(diagnostics.stream().anyMatch(diagnostic ->
                        "rewritten".equals(diagnostic.decision())
                                && "member".equals(diagnostic.target())
                                && "paymentMethodList".equals(diagnostic.field())
                                && diagnostic.expression().contains(" in ")),
                "成员 IN 过滤改写应记录 rewritten 诊断");
    }

    @Test
    @DisplayName("aggregate relation GROUP_CONCAT 负向 slice 不应按源成员改写")
    void aggregateRelationGroupConcatNegativeOpsShouldNotRewriteToMemberExists() {
        JdbcModelQueryEngine notEqualsQuery = buildOrderSalesAggregateRelationStringAggQuery("!=", "ALIPAY");
        String notEqualsSql = normalizeSql(notEqualsQuery.getSql()).toLowerCase();
        assertFalse(notEqualsSql.contains("exists (select 1 from fact_sales agg_src"),
                "GROUP_CONCAT != slice 不应改写为成员 EXISTS");
        assertTrue(notEqualsSql.contains("fsstringbyorder.paymentmethodlist != ?")
                        || notEqualsSql.contains("fsstringbyorder.paymentmethodlist !=?")
                        || notEqualsSql.contains("fsstringbyorder.paymentmethodlist <> ?")
                        || notEqualsSql.contains("fsstringbyorder.paymentmethodlist <>?"),
                "GROUP_CONCAT != slice 应保留聚合字符串外层过滤语义");
        assertTrue(aggregateRelationDiagnostics(notEqualsQuery).stream().noneMatch(diagnostic ->
                        "rewritten".equals(diagnostic.decision())
                                && "member".equals(diagnostic.target())
                                && "paymentMethodList".equals(diagnostic.field())),
                "负向 op 不应记录成员改写诊断");

        JdbcModelQueryEngine notInQuery = buildOrderSalesAggregateRelationStringAggQuery(
                "not in",
                List.of("ALIPAY", "WECHAT"));
        String notInSql = normalizeSql(notInQuery.getSql()).toLowerCase();
        assertFalse(notInSql.contains("exists (select 1 from fact_sales agg_src"),
                "GROUP_CONCAT not in slice 不应改写为成员 EXISTS");
        assertTrue(notInSql.contains("fsstringbyorder.paymentmethodlist not in"),
                "GROUP_CONCAT not in slice 应保留聚合字符串外层过滤语义");
        assertTrue(aggregateRelationDiagnostics(notInQuery).stream().noneMatch(diagnostic ->
                        "rewritten".equals(diagnostic.decision())
                                && "member".equals(diagnostic.target())
                                && "paymentMethodList".equals(diagnostic.field())),
                "not in 不应记录成员改写诊断");
    }

    @Test
    @DisplayName("aggregate relation GROUP_CONCAT 成员过滤 pipeline step 应预先写入 context 标记")
    void aggregateMemberFilterRewriteStepShouldPlanBeforeEngineFallback() {
        JdbcQueryModel queryModel = getQueryModel("OrderSalesAggregateRelationStringAggQueryModel");
        assertNotNull(queryModel, "查询模型加载失败");

        DbQueryRequestDef queryRequest = buildOrderSalesAggregateRelationStringAggRequest(
                List.of(slice("paymentMethodList", "=", "ALIPAY")));
        ModelResultContext context = buildQueryFacadeContext(queryRequest);
        context.setQueryModel(queryModel);

        new AggregateMemberFilterRewriteStep().beforeQuery(context);

        Object plansValue = context.getExtData().get(AggregateMemberFilterPlanner.EXT_DATA_KEY);
        assertTrue(plansValue instanceof IdentityHashMap<?, ?> plans && plans.size() == 1,
                "pipeline step 应在 SQL 引擎兜底前为 GROUP_CONCAT 成员过滤写入计划标记");
    }

    @Test
    @DisplayName("QueryFacade 应贯通 GROUP_CONCAT 成员过滤改写")
    void aggregateRelationGroupConcatMemberFilterShouldWorkThroughQueryFacade() {
        DbQueryRequestDef queryRequest = buildOrderSalesAggregateRelationStringAggRequest(
                List.of(slice("paymentMethodList", "=", "ALIPAY")));
        ModelResultContext context = buildQueryFacadeContext(queryRequest);

        DbQueryResult result = queryFacade.queryModelResult(context);
        JdbcModelQueryEngine queryEngine = (JdbcModelQueryEngine) result.getQueryEngine();
        String normalizedSql = normalizeSql(queryEngine.getSql()).toLowerCase();

        assertTrue(normalizedSql.contains("exists (select 1 from fact_sales agg_src"),
                "QueryFacade 路径应生成成员 EXISTS 改写 SQL");
        assertFalse(normalizedSql.contains("fsstringbyorder.paymentmethodlist = ?"),
                "QueryFacade 路径不应保留聚合字符串等值过滤");
        assertTrue(context.getExtData().get(AggregateMemberFilterPlanner.EXT_DATA_KEY)
                        instanceof IdentityHashMap<?, ?> plans && !plans.isEmpty(),
                "QueryFacade pipeline 应留下成员过滤计划标记");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) result.getPagingResult().getItems();
        assertFalse(rows.isEmpty(), "QueryFacade 路径应返回 ALIPAY 成员匹配订单");
    }

    @Test
    @DisplayName("aggregate relation GROUP_CONCAT 等值 slice 位于 OR 时不应成员改写")
    void aggregateRelationGroupConcatEqualsInsideOrShouldStayOuterOnly() {
        JdbcModelQueryEngine queryEngine = buildOrderSalesAggregateRelationStringAggQuery(List.of(
                SliceRequestDef.or(List.of(
                        condition("paymentMethodList", "=", "ALIPAY"),
                        condition("orderId", "=", "ORD20240101000002")))));

        String normalizedSql = normalizeSql(queryEngine.getSql()).toLowerCase();
        assertFalse(normalizedSql.contains("exists (select 1 from fact_sales agg_src"),
                "OR 条件内的 GROUP_CONCAT 等值 slice 不应做成员 EXISTS 改写");
        assertTrue(normalizedSql.contains("fsstringbyorder.paymentmethodlist = ?")
                        || normalizedSql.contains("fsstringbyorder.paymentmethodlist =?"),
                "OR 条件应保留外层标准 slice 语义");

        List<AggregateRelationDiagnostic> diagnostics = aggregateRelationDiagnostics(queryEngine);
        assertTrue(diagnostics.stream().anyMatch(diagnostic ->
                        "retained".equals(diagnostic.decision())
                                && AggregateRelationQueryObject.REASON_OR_CONDITION_OUTER_ONLY.equals(diagnostic.reasonCode())
                                && "paymentMethodList".equals(diagnostic.field())),
                "OR 条件内的 GROUP_CONCAT slice 应记录 outer-only 诊断");
    }

    @Test
    @DisplayName("aggregate relation GROUP_CONCAT 等值 slice 位于 OR 子 AND 时不应成员改写")
    void aggregateRelationGroupConcatEqualsInsideNestedAndUnderOrShouldStayOuterOnly() {
        JdbcModelQueryEngine queryEngine = buildOrderSalesAggregateRelationStringAggQuery(List.of(
                SliceRequestDef.or(List.of(
                        SliceRequestDef.and(List.of(
                                condition("paymentMethodList", "=", "ALIPAY"),
                                condition("orderId", "=", "ORD20240101000001"))),
                        condition("orderId", "=", "ORD20240101000002")))));

        String normalizedSql = normalizeSql(queryEngine.getSql()).toLowerCase();
        String aggregateExpression = expectedPaymentMethodListAggregateSql("agg_src.payment_method");
        assertFalse(normalizedSql.contains("exists (select 1 from fact_sales agg_src"),
                "OR 子 AND 内的 GROUP_CONCAT 等值 slice 也不应做成员 EXISTS 改写");
        assertFalse(normalizedSql.contains("having " + aggregateExpression + " = ?"),
                "OR 子 AND 内的 GROUP_CONCAT 等值 slice 不应下推到 RHS HAVING");
        assertTrue(normalizedSql.contains("fsstringbyorder.paymentmethodlist = ?")
                        || normalizedSql.contains("fsstringbyorder.paymentmethodlist =?"),
                "OR 子 AND 条件应保留外层标准 slice 语义");
    }

    @Test
    @DisplayName("aggregate relation group key alias 请求条件应复制到右侧 WHERE")
    void aggregateRelationGroupKeyAliasSliceShouldPushWhereThroughRequest() {
        String orderId = findOrderIdWithCompletedSales();
        JdbcQueryModel queryModel = getQueryModel("OrderSalesAggregateRelationGroupKeyAliasQueryModel");
        assertNotNull(queryModel, "查询模型加载失败");

        DbQueryColumn salesOrderIdColumn = queryModel.findJdbcQueryColumnByName("salesOrderId", true);
        assertTrue(salesOrderIdColumn.getSelectColumn() instanceof AggregateRelationOutputColumn,
                "salesOrderId 应是 aggregate relation group key 的 QM alias");

        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);
        DbQueryRequestDef queryRequest = buildOrderSalesAggregateRelationGroupKeyAliasRequest();
        queryRequest.setSlice(List.of(slice("salesOrderId", "=", orderId)));

        queryEngine.analysisQueryRequest(systemBundlesContext, queryRequest);

        String normalizedSql = normalizeSql(queryEngine.getSql());
        assertTrue(normalizedSql.contains("agg_src.order_id = ?"),
                "请求侧 aggregate relation group key alias 条件应复制到 RHS 聚合前 WHERE");
        assertTrue(normalizedSql.contains("fsByOrder.orderId =?") || normalizedSql.contains("fsByOrder.orderId = ?"),
                "外层 WHERE 应保留 group key alias 条件以保持 LEFT JOIN 语义");
        assertTrue(normalizedSql.contains("fsByOrder.orderId \"salesOrderId\""),
                "RHS group key 应按 QM alias 返回，避免与左侧 orderId 冲突");
        assertEquals(List.of("COMPLETED", orderId, orderId), queryEngine.getValues(),
                "RHS fixed filter、RHS group key pushdown、outer WHERE 参数顺序应稳定");

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                queryEngine.getSql(),
                queryEngine.getValues().toArray());
        assertEquals(1, rows.size(), "aggregate relation group key alias slice 应返回一行真实数据");
        assertEquals(orderId, rows.get(0).get("orderId"));
        assertEquals(orderId, rows.get(0).get("salesOrderId"));
    }

    @Test
    @DisplayName("左侧 join key slice 应复制到 aggregate relation 右侧 WHERE")
    void aggregateRelationLeftJoinKeySliceShouldPushRightWhere() {
        String orderId = findOrderIdWithCompletedSales();
        JdbcModelQueryEngine queryEngine = buildOrderSalesAggregateRelationQuery(orderId);

        String sql = queryEngine.getSql();
        assertTrue(sql.contains("agg_src.order_id = ?"),
                "左侧 join key 条件应复制到右侧聚合前 WHERE，限制 RHS key domain");
        assertTrue(queryEngine.getValues().contains("COMPLETED"), "右侧 fixed slice 参数应进入查询参数列表");
        assertTrue(queryEngine.getValues().contains(orderId), "外层 WHERE 仍应保留参数化 join key 条件");
    }

    @Test
    @DisplayName("aggregate relation OR join key slice 不应复制到右侧 WHERE")
    void aggregateRelationOrJoinKeySliceShouldStayOuterOnly() {
        String matchedOrderId = findOrderIdWithCompletedSales();
        String unmatchedOrderId = findOrderIdWithoutCompletedSales();
        JdbcModelQueryEngine queryEngine = buildOrderSalesAggregateRelationQuery(
                null,
                List.of(SliceRequestDef.or(List.of(
                        condition("orderId", "=", matchedOrderId),
                        condition("orderId", "=", unmatchedOrderId)))));

        String normalizedSql = normalizeSql(queryEngine.getSql());
        assertFalse(normalizedSql.contains("agg_src.order_id = ?"),
                "OR join key slice 不应复制到 RHS 聚合前 WHERE，避免收窄 LEFT JOIN 右侧 key domain");
        assertTrue(normalizedSql.contains("t1.order_id =?") || normalizedSql.contains("t1.order_id = ?"),
                "OR join key slice 应保留在外层 WHERE");
        assertTrue(normalizedSql.toLowerCase().contains(" or "),
                "外层 WHERE 应保留 OR 连接语义");

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                queryEngine.getSql(),
                queryEngine.getValues().toArray());
        assertEquals(2, rows.size(), "OR join key slice 应返回两个左侧订单");
        assertTrue(rows.stream().anyMatch(row -> matchedOrderId.equals(row.get("orderId"))));
        assertTrue(rows.stream().anyMatch(row -> unmatchedOrderId.equals(row.get("orderId"))));
    }

    @Test
    @DisplayName("aggregate relation OR measure slice 不应复制到右侧 HAVING")
    void aggregateRelationOrMeasureSliceShouldStayOuterOnly() {
        JdbcModelQueryEngine queryEngine = buildOrderSalesAggregateRelationQuery(
                null,
                List.of(SliceRequestDef.or(List.of(
                        condition("salesAmount", ">", BigDecimal.ZERO),
                        condition("uniqueCustomers", ">", 0)))));

        String normalizedSql = normalizeSql(queryEngine.getSql());
        assertFalse(normalizedSql.contains("having sum(agg_src.sales_amount) > ?"),
                "OR measure slice 不应复制为 RHS HAVING");
        assertFalse(normalizedSql.contains("having count(distinct agg_src.customer_key) > ?"),
                "OR measure slice 不应复制为 RHS HAVING");
        assertTrue(normalizedSql.contains("fsByOrder.salesAmount >?") || normalizedSql.contains("fsByOrder.salesAmount > ?"),
                "OR measure slice 应保留在外层 WHERE");
        assertTrue(normalizedSql.contains("fsByOrder.uniqueCustomers >?") || normalizedSql.contains("fsByOrder.uniqueCustomers > ?"),
                "OR measure slice 应保留在外层 WHERE");
        assertTrue(normalizedSql.toLowerCase().contains(" or "),
                "外层 WHERE 应保留 OR 连接语义");

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                queryEngine.getSql(),
                queryEngine.getValues().toArray());
        assertFalse(rows.isEmpty(), "OR measure slice 应返回有右侧聚合结果的订单");
    }

    @Test
    @DisplayName("aggregate relation mixed OR slice 不应复制到右侧 WHERE 或 HAVING")
    void aggregateRelationMixedOrSliceShouldStayOuterOnly() {
        String orderId = findOrderIdWithCompletedSales();
        JdbcModelQueryEngine queryEngine = buildOrderSalesAggregateRelationQuery(
                null,
                List.of(SliceRequestDef.or(List.of(
                        condition("orderId", "=", orderId),
                        condition("salesAmount", ">", BigDecimal.ZERO)))));

        String normalizedSql = normalizeSql(queryEngine.getSql());
        assertFalse(normalizedSql.contains("agg_src.order_id = ?"),
                "mixed OR 中的 join key slice 不应复制到 RHS WHERE");
        assertFalse(normalizedSql.contains("having sum(agg_src.sales_amount) > ?"),
                "mixed OR 中的 measure slice 不应复制到 RHS HAVING");
        assertTrue(normalizedSql.contains("t1.order_id =?") || normalizedSql.contains("t1.order_id = ?"),
                "mixed OR join key slice 应保留在外层 WHERE");
        assertTrue(normalizedSql.contains("fsByOrder.salesAmount >?") || normalizedSql.contains("fsByOrder.salesAmount > ?"),
                "mixed OR measure slice 应保留在外层 WHERE");
        assertTrue(normalizedSql.toLowerCase().contains(" or "),
                "mixed OR 应保留外层 OR 连接语义");
        List<AggregateRelationDiagnostic> diagnostics = aggregateRelationDiagnostics(queryEngine);
        assertTrue(diagnostics.stream().anyMatch(diagnostic ->
                        "retained".equals(diagnostic.decision())
                                && AggregateRelationQueryObject.REASON_OR_CONDITION_OUTER_ONLY.equals(diagnostic.reasonCode())
                                && "orderId".equals(diagnostic.field())),
                "mixed OR 中的 join-key slice 应记录为 outer-only");
        assertTrue(diagnostics.stream().anyMatch(diagnostic ->
                        "retained".equals(diagnostic.decision())
                                && AggregateRelationQueryObject.REASON_OR_CONDITION_OUTER_ONLY.equals(diagnostic.reasonCode())
                                && "salesAmount".equals(diagnostic.field())),
                "mixed OR 中的 measure slice 应记录为 outer-only");

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                queryEngine.getSql(),
                queryEngine.getValues().toArray());
        assertFalse(rows.isEmpty(), "mixed OR slice 应可真实执行");
    }

    @Test
    @DisplayName("aggregate relation AND in/range slice 应复制到右侧并保留外层 WHERE")
    void aggregateRelationAndInRangeSlicesShouldPushRightFilters() {
        String matchedOrderId = findOrderIdWithCompletedSales();
        String unmatchedOrderId = findOrderIdWithoutCompletedSales();
        JdbcModelQueryEngine queryEngine = buildOrderSalesAggregateRelationQuery(
                null,
                List.of(
                        slice("orderId", "in", List.of(matchedOrderId, unmatchedOrderId)),
                        slice("salesAmount", "[]", List.of(BigDecimal.ZERO, new BigDecimal("999999999")))));

        String normalizedSql = normalizeSql(queryEngine.getSql());
        assertTrue(normalizedSql.contains("agg_src.order_id in (?, ?)"),
                "AND join-key IN slice 应复制到 RHS WHERE 并使用参数绑定");
        assertTrue(normalizedSql.contains("having sum(agg_src.sales_amount) >= ? and sum(agg_src.sales_amount) <= ?"),
                "AND measure range slice 应复制到 RHS HAVING 并使用参数绑定");
        assertTrue(normalizedSql.contains("t1.order_id in (?, ?)") || normalizedSql.contains("t1.order_id in (?,?)"),
                "外层 WHERE 应保留 join-key IN slice");
        assertTrue(normalizedSql.contains("fsByOrder.salesAmount >=?") || normalizedSql.contains("fsByOrder.salesAmount >= ?"),
                "外层 WHERE 应保留 measure range 下界");
        assertTrue(normalizedSql.contains("fsByOrder.salesAmount <=?") || normalizedSql.contains("fsByOrder.salesAmount <= ?"),
                "外层 WHERE 应保留 measure range 上界");
        assertTrue(queryEngine.getValues().contains(matchedOrderId), "IN 参数应进入查询参数列表");
        assertTrue(queryEngine.getValues().contains(unmatchedOrderId), "IN 参数应进入查询参数列表");
        assertTrue(countBigDecimalValues(queryEngine.getValues(), BigDecimal.ZERO) >= 2,
                "RHS HAVING 与外层 WHERE 都应绑定 range 下界");
        List<AggregateRelationDiagnostic> diagnostics = aggregateRelationDiagnostics(queryEngine);
        assertTrue(diagnostics.stream().anyMatch(diagnostic ->
                        "pushed".equals(diagnostic.decision())
                                && "where".equals(diagnostic.target())
                                && "orderId".equals(diagnostic.field())
                                && diagnostic.expression().contains("agg_src.order_id in")),
                "AND join-key IN slice 应记录 RHS WHERE pushdown 诊断");
        assertTrue(diagnostics.stream().anyMatch(diagnostic ->
                        "pushed".equals(diagnostic.decision())
                                && "having".equals(diagnostic.target())
                                && "salesAmount".equals(diagnostic.field())
                                && diagnostic.expression().contains("sum(agg_src.sales_amount) >=")),
                "AND measure range slice 应记录 RHS HAVING pushdown 诊断");

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                queryEngine.getSql(),
                queryEngine.getValues().toArray());
        assertEquals(1, rows.size(), "measure range 保留外层 WHERE 后仅有 RHS 聚合匹配的订单返回");
        assertEquals(matchedOrderId, rows.get(0).get("orderId"));
    }

    @Test
    @DisplayName("semantic response debug.extra 应暴露 aggregate relation pushdown diagnostics")
    void semanticResponseShouldExposeAggregateRelationDiagnostics() {
        String matchedOrderId = findOrderIdWithCompletedSales();
        String unmatchedOrderId = findOrderIdWithoutCompletedSales();

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

        assertNotNull(response.getDebug(), "semantic response 应包含 debug 信息");
        assertNotNull(response.getDebug().getExtra(), "semantic response debug.extra 应包含执行证据");
        Object rawDiagnostics = response.getDebug().getExtra().get("aggregateRelationDiagnostics");
        assertTrue(rawDiagnostics instanceof List<?>, "debug.extra 应暴露 aggregateRelationDiagnostics 列表");

        @SuppressWarnings("unchecked")
        List<AggregateRelationDiagnostic> diagnostics = (List<AggregateRelationDiagnostic>) rawDiagnostics;
        assertTrue(diagnostics.stream().anyMatch(diagnostic ->
                        "pushed".equals(diagnostic.decision())
                                && "where".equals(diagnostic.target())
                                && "orderId".equals(diagnostic.field())),
                "semantic debug.extra 应包含 RHS WHERE pushdown 诊断");
        assertTrue(diagnostics.stream().anyMatch(diagnostic ->
                        "pushed".equals(diagnostic.decision())
                                && "having".equals(diagnostic.target())
                                && "salesAmount".equals(diagnostic.field())),
                "semantic debug.extra 应包含 RHS HAVING pushdown 诊断");
    }

    @Test
    @DisplayName("semantic response debug.extra 应暴露 aggregate relation retained/refused diagnostics")
    void semanticResponseShouldExposeRetainedAndRefusedAggregateRelationDiagnostics() {
        String orderId = findOrderIdWithCompletedSales();

        SemanticQueryRequest request = new SemanticQueryRequest();
        request.setColumns(Arrays.asList("orderId", "salesAmount", "uniqueCustomers"));
        request.setSlice(List.of(
                semanticOr(
                        semanticSlice("orderId", "=", orderId),
                        semanticSlice("salesAmount", ">", BigDecimal.ZERO)),
                semanticSlice("salesAmount", "[]", Arrays.asList(null, null))));
        request.setLimit(100);

        SemanticQueryResponse response = semanticQueryService.queryModel(
                "OrderSalesAggregateRelationQueryModel",
                request,
                "execute",
                SemanticRequestContext.empty());

        List<AggregateRelationDiagnostic> diagnostics = semanticAggregateRelationDiagnostics(response);
        assertTrue(diagnostics.stream().anyMatch(diagnostic ->
                        "retained".equals(diagnostic.decision())
                                && AggregateRelationQueryObject.REASON_OR_CONDITION_OUTER_ONLY.equals(diagnostic.reasonCode())
                                && "orderId".equals(diagnostic.field())),
                "semantic debug.extra 应包含 OR join-key retained 诊断");
        assertTrue(diagnostics.stream().anyMatch(diagnostic ->
                        "retained".equals(diagnostic.decision())
                                && AggregateRelationQueryObject.REASON_OR_CONDITION_OUTER_ONLY.equals(diagnostic.reasonCode())
                                && "salesAmount".equals(diagnostic.field())),
                "semantic debug.extra 应包含 OR measure retained 诊断");
        assertTrue(diagnostics.stream().anyMatch(diagnostic ->
                        "refused".equals(diagnostic.decision())
                                && AggregateRelationQueryObject.REASON_INVALID_RANGE_VALUE.equals(diagnostic.reasonCode())
                                && "salesAmount".equals(diagnostic.field())
                                && "[]".equals(diagnostic.op())),
                "semantic debug.extra 应包含 invalid range refused 诊断");
    }

    @Test
    @DisplayName("TMS-style aggregate relation 应支持主单+站点双 key 粒度")
    void tmsStyleAggregateRelationShouldPushCompositeKeyFilters() {
        Map<String, Object> fixture = findOrderStoreWithCompletedSales();
        String orderId = String.valueOf(fixture.get("orderId"));
        Object storeKey = fixture.get("storeKey");

        JdbcQueryModel queryModel = getQueryModel("TmsStyleOrderStoreSalesAggregateRelationQueryModel");
        assertNotNull(queryModel, "查询模型加载失败");

        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("TmsStyleOrderStoreSalesAggregateRelationQueryModel");
        queryRequest.setColumns(Arrays.asList("orderId", "amount", "salesAmount", "quantity", "uniqueCustomers"));
        queryRequest.setSlice(List.of(
                slice("orderId", "=", orderId),
                slice("store$id", "=", storeKey),
                slice("salesAmount", ">", BigDecimal.ZERO)));

        queryEngine.analysisQueryRequest(systemBundlesContext, queryRequest);

        String normalizedSql = normalizeSql(queryEngine.getSql());
        assertTrue(normalizedSql.contains("salesByOrderStore"),
                "TMS-style aggregate relation alias 应正常渲染");
        assertTrue(normalizedSql.contains("group by agg_src.order_id, agg_src.store_key")
                        || normalizedSql.contains("group by agg_src.store_key, agg_src.order_id"),
                "RHS groupBy 应覆盖订单号与站点双 key 粒度");
        assertTrue(normalizedSql.contains("agg_src.order_id = ?"),
                "订单号 slice 应复制到 RHS 聚合前 WHERE");
        assertTrue(normalizedSql.contains("agg_src.store_key = ?"),
                "站点 slice 应复制到 RHS 聚合前 WHERE");
        assertTrue(normalizedSql.contains("having sum(agg_src.sales_amount) > ?"),
                "聚合金额 slice 应复制到 RHS HAVING");

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                queryEngine.getSql(),
                queryEngine.getValues().toArray());
        assertEquals(1, rows.size(), "订单号 + 站点双 key 限定后应返回一行真实数据");

        BigDecimal nativeSalesAmount = jdbcTemplate.queryForObject("""
                select sum(fs.sales_amount)
                from fact_sales fs
                where fs.order_id = ?
                  and fs.store_key = ?
                  and fs.order_status = 'COMPLETED'
                """, BigDecimal.class, orderId, storeKey);
        assertEquals(0, money(nativeSalesAmount).compareTo(money(rows.get(0).get("salesAmount"))),
                "TMS-style 双 key RHS 聚合结果应与原生聚合一致");
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
    @DisplayName("aggregate relation 预定义计算字段未受限时应正常执行")
    void aggregateRelationPredefinedCalculatedFieldShouldExecuteWhenAllowed() {
        String orderId = findOrderIdWithCompletedSales();
        DbQueryRequestDef queryRequest = buildOrderSalesAggregateRelationRequest();
        queryRequest.setColumns(List.of("orderId", "salesAmount", "salesAmountPredefinedTax"));
        queryRequest.setSlice(List.of(slice("orderId", "=", orderId)));

        DbQueryResult result = queryFacade.queryModelResult(buildQueryFacadeContext(queryRequest));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) result.getPagingResult().getItems();
        assertEquals(1, rows.size(), "指定订单应只返回一行");

        Map<String, Object> row = rows.get(0);
        BigDecimal nativeSalesAmount = jdbcTemplate.queryForObject(
                "select sum(sales_amount) from fact_sales where order_id = ? and order_status = 'COMPLETED'",
                BigDecimal.class,
                orderId);
        BigDecimal expectedTaxAmount = nativeSalesAmount.multiply(new BigDecimal("1.1"));

        assertEquals(orderId, row.get("orderId"));
        assertEquals(0, money(nativeSalesAmount).compareTo(money(row.get("salesAmount"))),
                "预定义计算字段查询中仍应保留原始聚合销售金额");
        assertEquals(0, money(expectedTaxAmount).compareTo(money(row.get("salesAmountPredefinedTax"))),
                "QM 预定义计算字段应按 aggregate relation 输出字段执行公式");
    }

    @Test
    @DisplayName("aggregate relation 输出字段用于 orderBy 时应保留 RHS projection")
    void aggregateRelationMeasureOrderByShouldRetainProjection() {
        JdbcQueryModel queryModel = getQueryModel("OrderSalesAggregateRelationQueryModel");
        assertNotNull(queryModel, "查询模型加载失败");

        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);
        DbQueryRequestDef queryRequest = buildOrderSalesAggregateRelationRequest();
        OrderRequestDef order = new OrderRequestDef();
        order.setField("salesAmount");
        order.setDir("desc");
        queryRequest.setOrderBy(List.of(order));

        queryEngine.analysisQueryRequest(systemBundlesContext, queryRequest);

        String normalizedSql = normalizeSql(queryEngine.getSql());
        assertTrue(normalizedSql.contains("sum(agg_src.sales_amount) salesAmount"),
                "orderBy 引用 aggregate relation measure 时 RHS SELECT 不应裁掉该 measure");
        assertTrue(normalizedSql.toLowerCase().contains("order by"),
                "aggregate relation measure orderBy 应渲染外层 ORDER BY");
        assertTrue(normalizedSql.contains("fsByOrder.salesAmount"),
                "orderBy 应引用 aggregate relation 输出 alias，而不是直接引用 RHS 源列");

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                queryEngine.getSql(),
                queryEngine.getValues().toArray());
        assertFalse(rows.isEmpty(), "aggregate relation measure orderBy 查询应可真实执行");
    }

    @Test
    @DisplayName("aggregate relation returnTotal 应执行 total 查询并保持聚合关系")
    void aggregateRelationReturnTotalShouldKeepAggregateRelationQuery() {
        String orderId = findOrderIdWithCompletedSales();
        DbQueryRequestDef queryRequest = buildOrderSalesAggregateRelationRequest();
        queryRequest.setReturnTotal(true);
        queryRequest.setSlice(List.of(slice("orderId", "=", orderId)));

        ModelResultContext context = buildQueryFacadeContext(queryRequest);
        DbQueryResult result = queryFacade.queryModelResult(context);
        PagingResultImpl<?> pagingResult = result.getPagingResult();
        JdbcModelQueryEngine queryEngine = (JdbcModelQueryEngine) result.getQueryEngine();

        assertEquals(1, pagingResult.getTotal(), "returnTotal 应返回过滤后的总行数");
        assertTrue(pagingResult.getTotalData() instanceof Map, "returnTotal 应返回 totalData");
        @SuppressWarnings("unchecked")
        Map<String, Object> totalData = (Map<String, Object>) pagingResult.getTotalData();
        assertEquals(1, ((Number) totalData.get("total")).intValue(),
                "totalData.total 应与分页 total 保持一致");
        assertTrue(normalizeSql(queryEngine.getAggSql()).contains("fsByOrder"),
                "returnTotal 的 total SQL 应保留 aggregate relation 外层 alias");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) pagingResult.getItems();
        assertEquals(1, rows.size(), "returnTotal 不应影响明细查询结果");
        assertEquals(orderId, rows.get(0).get("orderId"));
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
        assertTrue(normalizeSql(queryEngine.getSql()).contains("having sum(agg_src.sales_amount) > ?"),
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

        assertTrue(sql.contains("agg_src.order_id = ?"),
                "system_slice 中的左侧 join key 应复制到右侧聚合前 WHERE");
        assertTrue(queryEngine.getValues().contains("COMPLETED"),
                "右侧 fixed slice 参数应进入查询参数列表");
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
    @DisplayName("aggregate relation 输出字段应遵守源物理列 deniedColumns")
    void aggregateRelationOutputFieldShouldFailClosedWhenDeniedPhysicalSourceColumn() {
        String orderId = findOrderIdWithCompletedSales();
        DbQueryRequestDef queryRequest = buildOrderSalesAggregateRelationRequest();
        queryRequest.setSlice(List.of(slice("orderId", "=", orderId)));

        ModelResultContext context = buildQueryFacadeContext(queryRequest);
        context.setDeniedColumns(List.of(new DeniedPhysicalColumn(null, "fact_sales", "sales_amount")));

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> queryFacade.queryModelResult(context));
        assertTrue(exception.getMessage().contains("salesAmount"),
                "deny RHS 源物理列 fact_sales.sales_amount 时应指出被拒绝的 aggregate relation 输出字段");
    }

    @Test
    @DisplayName("aggregate relation 未引用源物理列被 deniedColumns 命中时应放行")
    void aggregateRelationDeniedPhysicalUnreferencedSourceColumnShouldPass() {
        String orderId = findOrderIdWithCompletedSales();
        DbQueryRequestDef queryRequest = buildOrderSalesAggregateRelationRequest();
        queryRequest.setSlice(List.of(slice("orderId", "=", orderId)));

        ModelResultContext context = buildQueryFacadeContext(queryRequest);
        context.setDeniedColumns(List.of(new DeniedPhysicalColumn(null, "fact_sales", "profit_amount")));

        DbQueryResult result = queryFacade.queryModelResult(context);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) result.getPagingResult().getItems();
        assertEquals(1, rows.size(), "deny 未参与 RHS 聚合输出的源物理列时查询应正常返回");
        assertEquals(orderId, rows.get(0).get("orderId"));
    }

    @Test
    @DisplayName("aggregate relation 动态计算字段应遵守源物理列 deniedColumns")
    void aggregateRelationCalculatedFieldShouldFailClosedWhenDeniedPhysicalSourceColumn() {
        String orderId = findOrderIdWithCompletedSales();
        DbQueryRequestDef queryRequest = buildOrderSalesAggregateRelationRequest();
        queryRequest.setColumns(List.of("orderId", "amount", "salesAmountWithTax"));
        queryRequest.setCalculatedFields(List.of(new CalculatedFieldDef(
                "salesAmountWithTax",
                "含税销售金额",
                "salesAmount * 1.1")));
        queryRequest.setSlice(List.of(slice("orderId", "=", orderId)));

        ModelResultContext context = buildQueryFacadeContext(queryRequest);
        context.setDeniedColumns(List.of(new DeniedPhysicalColumn(null, "fact_sales", "sales_amount")));

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> queryFacade.queryModelResult(context));
        assertTrue(exception.getMessage().contains("salesAmount"),
                "deny RHS 源物理列 fact_sales.sales_amount 时，依赖该输出字段的动态计算字段应失败关闭");
    }

    @Test
    @DisplayName("aggregate relation 链式动态计算字段应传递遵守源物理列 deniedColumns")
    void aggregateRelationCalculatedFieldChainShouldFailClosedWhenDeniedPhysicalSourceColumn() {
        String orderId = findOrderIdWithCompletedSales();
        DbQueryRequestDef queryRequest = buildOrderSalesAggregateRelationRequest();
        queryRequest.setColumns(List.of("orderId", "amount", "salesAmountScore"));
        queryRequest.setCalculatedFields(List.of(
                new CalculatedFieldDef("salesAmountWithTax", "含税销售金额", "salesAmount * 1.1"),
                new CalculatedFieldDef("salesAmountScore", "销售金额评分", "salesAmountWithTax + 1")));
        queryRequest.setSlice(List.of(slice("orderId", "=", orderId)));

        ModelResultContext context = buildQueryFacadeContext(queryRequest);
        context.setDeniedColumns(List.of(new DeniedPhysicalColumn(null, "fact_sales", "sales_amount")));

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> queryFacade.queryModelResult(context));
        assertTrue(exception.getMessage().contains("salesAmount"),
                "deny RHS 源物理列 fact_sales.sales_amount 时，链式动态计算字段依赖应展开到被拒绝输出字段");
    }

    @Test
    @DisplayName("aggregate relation 预定义计算字段应遵守源物理列 deniedColumns")
    void aggregateRelationPredefinedCalculatedFieldShouldFailClosedWhenDeniedPhysicalSourceColumn() {
        String orderId = findOrderIdWithCompletedSales();
        DbQueryRequestDef queryRequest = buildOrderSalesAggregateRelationRequest();
        queryRequest.setColumns(List.of("orderId", "amount", "salesAmountPredefinedTax"));
        queryRequest.setSlice(List.of(slice("orderId", "=", orderId)));

        ModelResultContext context = buildQueryFacadeContext(queryRequest);
        context.setDeniedColumns(List.of(new DeniedPhysicalColumn(null, "fact_sales", "sales_amount")));

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> queryFacade.queryModelResult(context));
        assertTrue(exception.getMessage().contains("salesAmount"),
                "deny RHS 源物理列 fact_sales.sales_amount 时，QM 预定义计算字段依赖应展开到被拒绝输出字段");
    }

    @Test
    @DisplayName("aggregate relation system_slice 可引用未开放给用户的输出字段")
    void aggregateRelationSystemSliceShouldBypassUserFieldAccessForGuardFields() {
        String orderId = findOrderIdWithCompletedSales();
        DbQueryRequestDef queryRequest = buildOrderSalesAggregateRelationRequest();
        queryRequest.setColumns(List.of("orderId", "amount"));
        queryRequest.setSlice(List.of(slice("orderId", "=", orderId)));

        ModelResultContext context = buildQueryFacadeContext(queryRequest);
        context.setFieldAccess(Set.of("orderId", "amount"));
        context.setSystemSlice(List.of(slice("salesAmount", ">", BigDecimal.ZERO)));

        DbQueryResult result = queryFacade.queryModelResult(context);
        JdbcModelQueryEngine queryEngine = (JdbcModelQueryEngine) result.getQueryEngine();
        String sql = normalizeSql(queryEngine.getSql());
        assertTrue(sql.contains("having sum(agg_src.sales_amount) > ?"),
                "system_slice 中的 aggregate relation measure 应保留 RHS HAVING 下推");
        assertTrue(countBigDecimalValues(queryEngine.getValues(), BigDecimal.ZERO) >= 2,
                "RHS HAVING 与外层 WHERE 都应使用参数化绑定");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) result.getPagingResult().getItems();
        assertEquals(1, rows.size(), "system_slice guard 字段不在 fieldAccess 中时仍应作为系统过滤生效");
        assertEquals(orderId, rows.get(0).get("orderId"));
        assertFalse(rows.get(0).containsKey("salesAmount"),
                "system_slice guard 字段不应因为参与过滤而泄露到返回列");
    }

    @Test
    @DisplayName("aggregate relation RHS 运行期 filter 应读取 ModelResultContext.extData")
    void aggregateRelationRuntimeFilterShouldReadContextExtData() {
        String orderId = findOrderIdWithCompletedSales();
        JdbcModelQueryEngine queryEngine = buildOrderSalesAggregateRelationRuntimeFilterQuery(
                Map.of("orderId", orderId),
                orderId);

        String sql = queryEngine.getSql();
        assertTrue(sql.contains("agg_src.order_id = ?"),
                "RHS 运行期 filter 应在聚合前 WHERE 渲染为参数化条件");
        assertTrue(queryEngine.getValues().contains(orderId),
                "RHS 运行期 filter 参数应进入查询参数列表");
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
        assertTrue(normalizedSql.contains("agg_src.payment_method = ?"),
                "RHS runtime filter 应保留在聚合前 WHERE");
        assertTrue(queryEngine.getValues().contains("CREDIT_CARD"),
                "RHS runtime filter 参数应进入查询参数列表");
        assertFalse(normalizedSql.contains("agg_src.payment_method is null"),
                "外层 RHS alias is null slice 不应复制到 RHS 聚合子查询内部");
        assertTrue(normalizedSql.contains("fsByPaymentMethod.paymentMethod is null"),
                "RHS alias is null slice 应只渲染在外层 WHERE");
        List<AggregateRelationDiagnostic> diagnostics = aggregateRelationDiagnostics(queryEngine);
        assertTrue(diagnostics.stream().anyMatch(diagnostic ->
                        "retained".equals(diagnostic.decision())
                                && AggregateRelationQueryObject.REASON_NULL_CHECK_OUTER_ONLY.equals(diagnostic.reasonCode())
                                && "paymentMethod".equals(diagnostic.field())
                                && "is null".equals(diagnostic.op())),
                "RHS alias is null slice 应记录为 outer-only");
    }

    @Test
    @DisplayName("O616: aggregate relation RHS 输出字段 is not null slice 不应下推到 RHS 子查询")
    void aggregateRelationOutputNotNullSliceShouldStayOuterWhere() {
        JdbcModelQueryEngine queryEngine = buildOrderSalesAggregateRelationNullSlicePushdownProbeQuery("is not null");

        String normalizedSql = normalizeSql(queryEngine.getSql());
        assertTrue(normalizedSql.contains("left join (select"),
                "slice 引用 RHS alias 时应保留 LEFT JOIN");
        assertTrue(normalizedSql.contains("agg_src.payment_method = ?"),
                "RHS runtime filter 应保留在聚合前 WHERE");
        assertTrue(queryEngine.getValues().contains("CREDIT_CARD"),
                "RHS runtime filter 参数应进入查询参数列表");
        assertFalse(normalizedSql.contains("agg_src.payment_method is not null"),
                "外层 RHS alias is not null slice 不应复制到 RHS 聚合子查询内部");
        assertTrue(normalizedSql.contains("fsByPaymentMethod.paymentMethod is not null"),
                "RHS alias is not null slice 应只渲染在外层 WHERE");
        List<AggregateRelationDiagnostic> diagnostics = aggregateRelationDiagnostics(queryEngine);
        assertTrue(diagnostics.stream().anyMatch(diagnostic ->
                        "retained".equals(diagnostic.decision())
                                && AggregateRelationQueryObject.REASON_NULL_CHECK_OUTER_ONLY.equals(diagnostic.reasonCode())
                                && "paymentMethod".equals(diagnostic.field())
                                && "is not null".equals(diagnostic.op())),
                "RHS alias is not null slice 应记录为 outer-only");
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
        assertTrue(sql.contains("agg_src.order_id = ?"),
                "accessBuilder 追加的左侧 join key 守卫应复制到 RHS 聚合前 WHERE");
        assertTrue(queryEngine.getValues().contains("COMPLETED"),
                "右侧 fixed slice 参数应进入查询参数列表");
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
    @DisplayName("aggregate relation raw SQL accessBuilder 不应猜测下推到右侧 WHERE")
    void aggregateRelationRawSqlAccessBuilderShouldStayOuterOnly() {
        String orderId = "ORD20240101000001";
        JdbcModelQueryEngine queryEngine = buildOrderSalesAggregateRelationRawAccessQuery();

        String normalizedSql = normalizeSql(queryEngine.getSql());
        assertTrue(normalizedSql.contains("t1.order_id = ?"),
                "raw SQL accessBuilder 条件应保留在外层 WHERE");
        assertFalse(normalizedSql.contains("agg_src.order_id = ?"),
                "raw SQL accessBuilder 条件不应被复制为 RHS 聚合前 WHERE 参数条件");
        assertTrue(normalizedSql.contains("sum(agg_src.quantity) quantity"),
                "raw SQL accessBuilder 存在时应退回全量 RHS projection，避免误裁未知 raw SQL 引用");
        assertTrue(queryEngine.getValues().contains(orderId),
                "外层 WHERE 应保留 raw SQL accessBuilder 参数化条件");

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                queryEngine.getSql(),
                queryEngine.getValues().toArray());
        assertEquals(1, rows.size(), "raw SQL accessBuilder 限定订单后应返回一行真实数据");
        assertEquals(orderId, rows.get(0).get("orderId"));
    }

    @Test
    @DisplayName("aggregate relation grouped accessBuilder 应支持字段引用 OR 条件")
    void aggregateRelationGroupedAccessBuilderFieldRefOrShouldRenderParameterizedGroup() {
        String orderId = "ORD20240101000001";
        JdbcModelQueryEngine queryEngine = buildOrderSalesAggregateRelationGroupedAccessQuery();

        String normalizedSql = normalizeSql(queryEngine.getSql());
        assertTrue(normalizedSql.contains("1=0 or t1.order_id = ? or t1.order_status = ?"),
                "字段引用 OR 分组应生成外层参数化条件");
        assertFalse(normalizedSql.contains("agg_src.order_id = ?"),
                "OR accessBuilder 条件应保持 outer-only，不应猜测复制到 RHS 聚合前 WHERE");
        assertTrue(queryEngine.getValues().contains(orderId),
                "字段引用 OR 分组应保留 orderId 参数");
        assertTrue(queryEngine.getValues().contains("CANCELLED"),
                "字段引用 OR 分组应保留 orderStatus 参数");

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                queryEngine.getSql(),
                queryEngine.getValues().toArray());
        assertTrue(rows.stream().anyMatch(row -> orderId.equals(row.get("orderId"))),
                "字段引用 OR 分组应返回目标订单");
        assertTrue(rows.stream().anyMatch(row -> "CANCELLED".equals(row.get("orderStatus"))),
                "字段引用 OR 分组应返回取消状态订单");
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
        assertTrue(normalizedSql.contains("agg_src.store_key = ?"),
                "accessBuilder 中的显式 tenant join key 应复制到 RHS 聚合前 WHERE");
        assertTrue(normalizedSql.contains("group by agg_src.store_key"),
                "RHS groupBy 应保留显式 tenant join key，避免跨租户聚合后再关联");
        assertTrue(queryEngine.getValues().contains(20240101),
                "tenant guard 参数应进入查询参数列表");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) result.getPagingResult().getItems();
        assertEquals(1, rows.size(), "O615 orderNo slice payload 形态应返回一行真实数据");
        assertEquals(orderNo, rows.get(0).get("orderNo"));
    }

    @Test
    @DisplayName("O615 probe: 显式 tenant guard 可绕过用户字段白名单且不泄露")
    void aggregateRelationO615TenantGuardShouldBypassFieldAccessWithoutLeaking() {
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
        queryRequest.setColumns(Arrays.asList("orderNo", "srcId", "useType", "number", "plannedPieceCount"));
        queryRequest.setSlice(List.of(
                slice("orderNo", "=", orderNo),
                slice("srcId", "=", srcId),
                slice("useType", "=", useType),
                slice("number", ">", 0)));

        ModelResultContext context = buildQueryFacadeContext(queryRequest);
        context.setFieldAccess(Set.of("orderNo", "srcId", "useType", "number", "plannedPieceCount"));
        context.setSystemSlice(List.of(slice("tenantId", "=", 20240101)));

        DbQueryResult result = queryFacade.queryModelResult(context);
        JdbcModelQueryEngine queryEngine = (JdbcModelQueryEngine) result.getQueryEngine();
        String normalizedSql = normalizeSql(queryEngine.getSql());

        assertTrue(normalizedSql.contains("agg_src.store_key = ?"),
                "system_slice 中的显式 tenant join key 应复制到 RHS 聚合前 WHERE");
        assertTrue(normalizedSql.contains("plannedByOrder"),
                "请求 aggregate relation 输出时应保留计划占用聚合关系");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) result.getPagingResult().getItems();
        assertEquals(1, rows.size(), "tenant guard 不在 fieldAccess 中时仍应作为系统过滤生效");
        assertEquals(orderNo, rows.get(0).get("orderNo"));
        assertFalse(rows.get(0).containsKey("tenantId"),
                "tenant guard 不应因为参与过滤而泄露到返回列");
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
        assertTrue(normalizedSql.contains("status = ?") || normalizedSql.contains("status=?"),
                "RHS 维度字段固定过滤应进入聚合前过滤");
        assertTrue(queryEngine.getValues().contains("ACTIVE"),
                "RHS 维度字段固定过滤参数应进入查询参数列表");

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
        assertTrue(normalizedSql.contains("status = ?") || normalizedSql.contains("status=?"),
                "RHS 内部维表过滤应进入聚合前过滤");
        assertTrue(queryEngine.getValues().contains("ACTIVE"),
                "RHS 内部维表过滤参数应进入查询参数列表");

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
        assertTrue(sql.contains("category_id = ?") || sql.contains("category_id=?"),
                "RHS fixed filter 应使用右侧维表物理列表达式");
        assertTrue(queryEngine.getValues().contains("CAT001"),
                "RHS fixed filter 参数应进入查询参数列表");

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
        assertTrue(sql.contains("agg_src.order_id = ?"),
                "左侧 join key 条件应进入 RHS WHERE，降低右侧聚合 key domain");
        assertTrue(normalizedSql.contains("having sum(agg_src.sales_amount) > ?"),
                "aggregate measure 条件应进入 RHS HAVING");
        assertTrue(queryEngine.getValues().contains(orderId),
                "RHS join key 参数应进入查询参数列表");

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

    private List<AggregateRelationDiagnostic> aggregateRelationDiagnostics(JdbcModelQueryEngine queryEngine) {
        assertNotNull(queryEngine.getJdbcQueryModel(), "查询引擎应保留 JDBC QueryModel");
        return queryEngine.getJdbcQueryModel().getJdbcModelList().stream()
                .map(TableModel::getQueryObject)
                .map(this::resolveAggregateRelationQueryObject)
                .filter(AggregateRelationQueryObject.class::isInstance)
                .findFirst()
                .map(AggregateRelationQueryObject::getAggregateRelationDiagnostics)
                .orElseThrow();
    }

    private AggregateRelationQueryObject resolveAggregateRelationQueryObject(QueryObject queryObject) {
        if (queryObject instanceof AggregateRelationQueryObject aggregateRelationQueryObject) {
            return aggregateRelationQueryObject;
        }
        return queryObject == null ? null : queryObject.getDecorate(AggregateRelationQueryObject.class);
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

    private JdbcModelQueryEngine buildOrderSalesAggregateRelationRawAccessQuery() {
        JdbcQueryModel queryModel = getQueryModel("OrderSalesAggregateRelationRawAccessQueryModel");
        assertNotNull(queryModel, "查询模型加载失败");

        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);

        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("OrderSalesAggregateRelationRawAccessQueryModel");
        queryRequest.setColumns(Arrays.asList("orderId", "amount", "salesAmount", "uniqueCustomers"));

        queryEngine.analysisQueryRequest(systemBundlesContext, queryRequest);
        return queryEngine;
    }

    private JdbcModelQueryEngine buildOrderSalesAggregateRelationGroupedAccessQuery() {
        JdbcQueryModel queryModel = getQueryModel("OrderSalesAggregateRelationGroupedAccessQueryModel");
        assertNotNull(queryModel, "查询模型加载失败");

        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);

        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("OrderSalesAggregateRelationGroupedAccessQueryModel");
        queryRequest.setColumns(Arrays.asList("orderId", "orderStatus", "amount", "salesAmount", "uniqueCustomers"));

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

    private JdbcModelQueryEngine buildOrderSalesAggregateRelationStringAggQuery(String paymentMethodLike) {
        return buildOrderSalesAggregateRelationStringAggQuery("like", paymentMethodLike);
    }

    private JdbcModelQueryEngine buildOrderSalesAggregateRelationStringAggQuery(String op, Object value) {
        return buildOrderSalesAggregateRelationStringAggQuery(List.of(slice("paymentMethodList", op, value)));
    }

    private JdbcModelQueryEngine buildOrderSalesAggregateRelationStringAggQuery(List<SliceRequestDef> slices) {
        JdbcQueryModel queryModel = getQueryModel("OrderSalesAggregateRelationStringAggQueryModel");
        assertNotNull(queryModel, "查询模型加载失败");

        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);

        DbQueryRequestDef queryRequest = buildOrderSalesAggregateRelationStringAggRequest(slices);

        queryEngine.analysisQueryRequest(systemBundlesContext, queryRequest);
        return queryEngine;
    }

    private DbQueryRequestDef buildOrderSalesAggregateRelationStringAggRequest(List<SliceRequestDef> slices) {
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("OrderSalesAggregateRelationStringAggQueryModel");
        queryRequest.setColumns(Arrays.asList("orderId", "paymentMethodList"));
        queryRequest.setSlice(slices);
        return queryRequest;
    }

    private JdbcModelQueryEngine buildOrderSalesAggregateRelationExplicitMeasureQuery() {
        return buildOrderSalesAggregateRelationExplicitMeasureQuery(List.of());
    }

    private JdbcModelQueryEngine buildOrderSalesAggregateRelationExplicitMeasureQuery(String op, Object value) {
        return buildOrderSalesAggregateRelationExplicitMeasureQuery(List.of(slice("paymentMethodList", op, value)));
    }

    private JdbcModelQueryEngine buildOrderSalesAggregateRelationExplicitMeasureQuery(List<SliceRequestDef> slices) {
        JdbcQueryModel queryModel = getQueryModel("OrderSalesAggregateRelationExplicitMeasureQueryModel");
        assertNotNull(queryModel, "查询模型加载失败");

        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);
        DbQueryRequestDef queryRequest = buildOrderSalesAggregateRelationExplicitMeasureRequest(slices);

        queryEngine.analysisQueryRequest(systemBundlesContext, queryRequest);
        return queryEngine;
    }

    private DbQueryRequestDef buildOrderSalesAggregateRelationExplicitMeasureRequest(List<SliceRequestDef> slices) {
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("OrderSalesAggregateRelationExplicitMeasureQueryModel");
        queryRequest.setColumns(Arrays.asList("orderId", "salesLineCount", "paymentMethodList"));
        if (slices != null && !slices.isEmpty()) {
            queryRequest.setSlice(slices);
        }
        return queryRequest;
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

    private DbQueryRequestDef buildOrderSalesAggregateRelationGroupKeyAliasRequest() {
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("OrderSalesAggregateRelationGroupKeyAliasQueryModel");
        queryRequest.setColumns(Arrays.asList("orderId", "amount", "salesOrderId", "salesAmount", "uniqueCustomers"));
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

    private PaymentMemberFixture findPaymentMemberFixture() {
        List<String> orderIds = jdbcTemplate.queryForList("""
                select fs.order_id
                from fact_sales fs
                where fs.order_status = 'COMPLETED'
                  and fs.payment_method is not null
                group by fs.order_id
                having count(*) > 1
                order by fs.order_id
                limit 1
                """, String.class);
        if (orderIds.isEmpty()) {
            orderIds = jdbcTemplate.queryForList("""
                    select fs.order_id
                    from fact_sales fs
                    where fs.order_status = 'COMPLETED'
                      and fs.payment_method is not null
                    order by fs.order_id
                    limit 1
                    """, String.class);
        }
        assertFalse(orderIds.isEmpty(), "测试数据应至少包含一个有 COMPLETED 支付方式明细的订单");

        String orderId = orderIds.get(0);
        List<String> members = completedPaymentMethodsForOrder(orderId);
        assertFalse(members.isEmpty(), "样本订单应至少包含一个支付方式成员");
        return new PaymentMemberFixture(orderId, members.get(0), members);
    }

    private List<String> completedPaymentMethodsForOrder(String orderId) {
        return jdbcTemplate.queryForList("""
                select fs.payment_method
                from fact_sales fs
                where fs.order_status = 'COMPLETED'
                  and fs.payment_method is not null
                  and fs.order_id = ?
                order by fs.sales_key
                """, String.class, orderId);
    }

    private List<String> findCompletedPaymentMethods(String requiredMember, int limit) {
        List<String> members = new ArrayList<>();
        members.add(requiredMember);

        List<String> candidates = jdbcTemplate.queryForList("""
                select fs.payment_method
                from fact_sales fs
                where fs.order_status = 'COMPLETED'
                  and fs.payment_method is not null
                group by fs.payment_method
                order by fs.payment_method
                """, String.class);
        for (String candidate : candidates) {
            if (!requiredMember.equals(candidate)) {
                members.add(candidate);
            }
            if (members.size() >= limit) {
                break;
            }
        }
        return members;
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
        assertFalse(fixture.isEmpty(), "测试数据应至少包含一个订单+站点粒度的 COMPLETED 销售明细");
        return fixture;
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

    private String placeholders(int count) {
        List<String> placeholders = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            placeholders.add("?");
        }
        return String.join(", ", placeholders);
    }

    private SemanticQueryRequest.SliceItem semanticSlice(String field, String op, Object value) {
        SemanticQueryRequest.SliceItem slice = new SemanticQueryRequest.SliceItem();
        slice.setField(field);
        slice.setOp(op);
        slice.setValue(value);
        return slice;
    }

    private SemanticQueryRequest.SliceItem semanticOr(SemanticQueryRequest.SliceItem... children) {
        SemanticQueryRequest.SliceItem slice = new SemanticQueryRequest.SliceItem();
        slice.setOr(Arrays.asList(children));
        return slice;
    }

    @SuppressWarnings("unchecked")
    private List<AggregateRelationDiagnostic> semanticAggregateRelationDiagnostics(SemanticQueryResponse response) {
        assertNotNull(response.getDebug(), "semantic response 应包含 debug 信息");
        assertNotNull(response.getDebug().getExtra(), "semantic response debug.extra 应包含执行证据");
        Object rawDiagnostics = response.getDebug().getExtra().get("aggregateRelationDiagnostics");
        assertTrue(rawDiagnostics instanceof List<?>, "debug.extra 应暴露 aggregateRelationDiagnostics 列表");
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
        return sql.replace('`', '"').replaceAll("\\s+", " ").trim();
    }

    private String expectedPaymentMethodListAggregateSql(String columnRef) {
        return switch (getDialectKey()) {
            case "postgresql" -> "string_agg(" + columnRef + "::text, ',')";
            case "sqlserver" -> "string_agg(" + columnRef + ", ',')";
            case "sqlite" -> "group_concat(" + columnRef + ", ',')";
            default -> "group_concat(" + columnRef + " separator ',')";
        };
    }

    private Object valueIgnoreCase(Map<String, Object> row, String key) {
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(key)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private List<String> sortedPaymentMembers(List<String> members) {
        List<String> sorted = new ArrayList<>();
        for (String member : members) {
            if (member != null && !member.isBlank()) {
                sorted.add(member.trim());
            }
        }
        sorted.sort(String::compareTo);
        return sorted;
    }

    private List<String> sortedPaymentMembers(Object membersValue) {
        if (membersValue == null) {
            return List.of();
        }
        return sortedPaymentMembers(Arrays.asList(String.valueOf(membersValue).split(",")));
    }

    private record PaymentMemberFixture(String orderId, String member, List<String> expectedMembers) {
    }

    private long countBigDecimalValues(List<Object> values, BigDecimal expected) {
        return values.stream()
                .filter(this::isNumericValue)
                .filter(value -> new BigDecimal(String.valueOf(value)).compareTo(expected) == 0)
                .count();
    }

    private boolean isNumericValue(Object value) {
        if (value instanceof Number) {
            return true;
        }
        if (value instanceof CharSequence text) {
            return text.toString().matches("-?\\d+(\\.\\d+)?");
        }
        return false;
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
