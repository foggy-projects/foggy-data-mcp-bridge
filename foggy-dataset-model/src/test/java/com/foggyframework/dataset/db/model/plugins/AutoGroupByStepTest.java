package com.foggyframework.dataset.db.model.plugins;

import com.foggyframework.dataset.client.domain.PagingRequest;
import com.foggyframework.dataset.db.model.def.query.request.CalculatedFieldDef;
import com.foggyframework.dataset.db.model.def.query.request.GroupRequestDef;
import com.foggyframework.dataset.db.model.def.query.request.DbQueryRequestDef;
import com.foggyframework.dataset.db.model.def.query.request.OrderRequestDef;
import com.foggyframework.dataset.db.model.plugins.result_set_filter.AutoGroupByStep;
import com.foggyframework.dataset.db.model.plugins.result_set_filter.InlineExpressionPreprocessStep;
import com.foggyframework.dataset.db.model.plugins.result_set_filter.ModelResultContext;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AutoGroupByStep 单元测试
 * <p>
 * 注意：AutoGroupByStep 依赖 InlineExpressionPreprocessStep 先执行，
 * 以解析内联表达式并设置 CalculatedFieldDef.agg 字段。
 * </p>
 * <p>
 * autoGroupBy 参数已废弃，系统始终自动处理 groupBy。
 * </p>
 */
@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("AutoGroupByStep 测试")
class AutoGroupByStepTest {

    private InlineExpressionPreprocessStep inlineExpressionPreprocessStep;
    private AutoGroupByStep autoGroupByStep;

    @BeforeEach
    void setUp() {
        inlineExpressionPreprocessStep = new InlineExpressionPreprocessStep();
        autoGroupByStep = new AutoGroupByStep();
    }

    /**
     * 执行预处理和 AutoGroupBy 步骤
     */
    private void executeSteps(ModelResultContext ctx) {
        inlineExpressionPreprocessStep.beforeQuery(ctx);
        autoGroupByStep.beforeQuery(ctx);
    }

    @Test
    @Order(1)
    @DisplayName("autoGroupBy 参数已废弃 - 始终启用自动处理")
    void testAutoGroupByAlwaysEnabled() {
        // 即使设置 autoGroupBy=false，系统仍会自动处理
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setAutoGroupBy(false);  // 设置为 false 不再生效
        queryRequest.setColumns(Arrays.asList(
            "product$categoryName",
            "sum(salesAmount) as totalSales"
        ));

        ModelResultContext ctx = createContext(queryRequest);
        executeSteps(ctx);

        // groupBy 应该被自动处理（因为 isAutoGroupBy() 始终返回 true）
        List<GroupRequestDef> groupBy = queryRequest.getGroupBy();
        assertNotNull(groupBy, "groupBy 应被自动处理");
        assertEquals(2, groupBy.size(), "应有2个 groupBy 字段");

        log.info("autoGroupBy=false 仍触发自动处理: {}", groupBy);
    }

    @Test
    @Order(2)
    @DisplayName("检测内联聚合表达式 - sum(salesAmount) as totalSales")
    void testDetectInlineAggregateExpression() {
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setAutoGroupBy(true);
        queryRequest.setColumns(Arrays.asList(
            "product$categoryName",
            "sum(salesAmount) as totalSales"
        ));

        ModelResultContext ctx = createContext(queryRequest);
        executeSteps(ctx);

        List<GroupRequestDef> groupBy = queryRequest.getGroupBy();
        assertNotNull(groupBy);
        assertEquals(2, groupBy.size());

        // 第一个：非聚合列
        assertEquals("product$categoryName", groupBy.get(0).getField());
        assertNull(groupBy.get(0).getAgg());

        // 第二个：聚合列
        assertEquals("totalSales", groupBy.get(1).getField());
        assertEquals("SUM", groupBy.get(1).getAgg());

        log.info("自动生成的 groupBy: {}", groupBy);
    }

    @Test
    @Order(3)
    @DisplayName("混合场景 - 多个列和聚合")
    void testMixedColumns() {
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setAutoGroupBy(true);
        queryRequest.setColumns(Arrays.asList(
            "product$categoryName",
            "date",
            "sum(salesAmount) as salesAmount2",
            "orderCount"
        ));

        // 用户已指定部分 groupBy
        List<GroupRequestDef> existingGroupBy = new ArrayList<>();
        GroupRequestDef g = new GroupRequestDef();
        g.setField("product$categoryName");
        existingGroupBy.add(g);
        queryRequest.setGroupBy(existingGroupBy);

        ModelResultContext ctx = createContext(queryRequest);
        executeSteps(ctx);

        List<GroupRequestDef> groupBy = queryRequest.getGroupBy();
        assertNotNull(groupBy);
        assertEquals(4, groupBy.size());

        // 验证各列（按 columns 顺序）
        assertEquals("product$categoryName", groupBy.get(0).getField());
        assertNull(groupBy.get(0).getAgg());

        assertEquals("date", groupBy.get(1).getField());
        assertNull(groupBy.get(1).getAgg());

        assertEquals("salesAmount2", groupBy.get(2).getField());  // 内联表达式转换后的别名
        assertEquals("SUM", groupBy.get(2).getAgg());

        assertEquals("orderCount", groupBy.get(3).getField());
        assertNull(groupBy.get(3).getAgg());

        log.info("混合场景 groupBy: {}", groupBy);
    }

    @Test
    @Order(4)
    @DisplayName("多种聚合函数 - AVG, COUNT, MAX, MIN")
    void testVariousAggFunctions() {
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setAutoGroupBy(true);
        queryRequest.setColumns(Arrays.asList(
            "product$categoryName",
            "avg(unitPrice) as avgPrice",
            "count(orderId) as orderCount",
            "max(salesAmount) as maxSales",
            "min(salesAmount) as minSales"
        ));

        ModelResultContext ctx = createContext(queryRequest);
        executeSteps(ctx);

        List<GroupRequestDef> groupBy = queryRequest.getGroupBy();
        assertNotNull(groupBy);
        assertEquals(5, groupBy.size());

        // 验证聚合类型
        assertEquals("AVG", groupBy.get(1).getAgg());
        assertEquals("COUNT", groupBy.get(2).getAgg());
        assertEquals("MAX", groupBy.get(3).getAgg());
        assertEquals("MIN", groupBy.get(4).getAgg());

        log.info("多聚合函数 groupBy: {}", groupBy);
    }

    @Test
    @Order(5)
    @DisplayName("没有聚合表达式时不处理")
    void testNoAggregation() {
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setAutoGroupBy(true);
        queryRequest.setColumns(Arrays.asList(
            "product$categoryName",
            "salesAmount",
            "orderCount"
        ));

        ModelResultContext ctx = createContext(queryRequest);
        executeSteps(ctx);

        // 没有聚合表达式，groupBy 保持为空
        assertNull(queryRequest.getGroupBy());
    }

    @Test
    @Order(6)
    @DisplayName("calculatedField 带 agg 属性")
    void testCalculatedFieldWithAgg() {
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setAutoGroupBy(true);

        // 定义带 agg 的计算字段
        List<CalculatedFieldDef> calcFields = new ArrayList<>();
        CalculatedFieldDef calcField = new CalculatedFieldDef();
        calcField.setName("totalSales");
        calcField.setExpression("salesAmount");  // 表达式不含聚合函数
        calcField.setAgg("SUM");  // 但通过 agg 属性指定聚合
        calcFields.add(calcField);
        queryRequest.setCalculatedFields(calcFields);

        queryRequest.setColumns(Arrays.asList(
            "product$categoryName",
            "totalSales"
        ));

        ModelResultContext ctx = createContext(queryRequest);
        executeSteps(ctx);

        List<GroupRequestDef> groupBy = queryRequest.getGroupBy();
        assertNotNull(groupBy);
        assertEquals(2, groupBy.size());

        assertEquals("product$categoryName", groupBy.get(0).getField());
        assertNull(groupBy.get(0).getAgg());

        assertEquals("totalSales", groupBy.get(1).getField());
        assertEquals("SUM", groupBy.get(1).getAgg());

        log.info("calculatedField带agg groupBy: {}", groupBy);
    }

    @Test
    @Order(7)
    @DisplayName("不重复添加已存在的 groupBy 字段")
    void testNoDuplicateGroupBy() {
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setAutoGroupBy(true);
        queryRequest.setColumns(Arrays.asList(
            "product$categoryName",
            "date",
            "sum(salesAmount) as totalSales"
        ));

        // 用户已指定所有非聚合列
        List<GroupRequestDef> existingGroupBy = new ArrayList<>();
        GroupRequestDef g1 = new GroupRequestDef();
        g1.setField("product$categoryName");
        existingGroupBy.add(g1);
        GroupRequestDef g2 = new GroupRequestDef();
        g2.setField("date");
        existingGroupBy.add(g2);
        queryRequest.setGroupBy(existingGroupBy);

        ModelResultContext ctx = createContext(queryRequest);
        executeSteps(ctx);

        List<GroupRequestDef> groupBy = queryRequest.getGroupBy();
        assertNotNull(groupBy);
        assertEquals(3, groupBy.size());  // 原有2个 + 1个聚合

        // 验证没有重复
        long distinctCount = groupBy.stream().map(GroupRequestDef::getField).distinct().count();
        assertEquals(3, distinctCount);

        log.info("无重复 groupBy: {}", groupBy);
    }

    @Test
    @Order(8)
    @DisplayName("大小写不敏感 - SUM/sum/Sum")
    void testCaseInsensitive() {
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setAutoGroupBy(true);
        queryRequest.setColumns(Arrays.asList(
            "product$categoryName",
            "SUM(salesAmount) as total1",
            "sum(quantity) as total2",
            "Sum(profit) as total3"
        ));

        ModelResultContext ctx = createContext(queryRequest);
        executeSteps(ctx);

        List<GroupRequestDef> groupBy = queryRequest.getGroupBy();
        assertNotNull(groupBy);

        // 所有聚合都应该被识别
        long aggCount = groupBy.stream()
            .filter(g -> g.getAgg() != null)
            .count();
        assertEquals(3, aggCount);

        // agg 应该统一为大写
        groupBy.stream()
            .filter(g -> g.getAgg() != null)
            .forEach(g -> assertEquals("SUM", g.getAgg()));

        log.info("大小写不敏感 groupBy: {}", groupBy);
    }

    @Test
    @Order(9)
    @DisplayName("orderBy 字段不在 SELECT 中时警告并忽略")
    void testOrderByNotInSelect_WarnAndRemove() {
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setAutoGroupBy(true);
        queryRequest.setColumns(Arrays.asList(
            "product$categoryName",
            "sum(salesAmount) as totalSales"
        ));

        // 设置 orderBy，其中 salesDate 不在 columns 中
        List<OrderRequestDef> orderBy = new ArrayList<>();
        OrderRequestDef o1 = new OrderRequestDef();
        o1.setField("product$categoryName");
        o1.setOrder("ASC");
        orderBy.add(o1);
        OrderRequestDef o2 = new OrderRequestDef();
        o2.setField("salesDate");  // 不在 SELECT 中
        o2.setOrder("DESC");
        orderBy.add(o2);
        queryRequest.setOrderBy(orderBy);

        ModelResultContext ctx = createContext(queryRequest);
        executeSteps(ctx);

        // orderBy 应该只保留 product$categoryName
        List<OrderRequestDef> resultOrderBy = queryRequest.getOrderBy();
        assertNotNull(resultOrderBy);
        assertEquals(1, resultOrderBy.size());
        assertEquals("product$categoryName", resultOrderBy.get(0).getField());

        log.info("清理后的 orderBy: {}", resultOrderBy);
    }

    @Test
    @Order(10)
    @DisplayName("orderBy 字段全部不在 SELECT 中时设为 null")
    void testOrderByAllInvalid_SetNull() {
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setAutoGroupBy(true);
        queryRequest.setColumns(Arrays.asList(
            "product$categoryName",
            "sum(salesAmount) as totalSales"
        ));

        // 设置 orderBy，所有字段都不在 columns 中
        List<OrderRequestDef> orderBy = new ArrayList<>();
        OrderRequestDef o1 = new OrderRequestDef();
        o1.setField("salesDate");
        orderBy.add(o1);
        OrderRequestDef o2 = new OrderRequestDef();
        o2.setField("customerId");
        orderBy.add(o2);
        queryRequest.setOrderBy(orderBy);

        ModelResultContext ctx = createContext(queryRequest);
        executeSteps(ctx);

        // orderBy 应该被设为 null
        assertNull(queryRequest.getOrderBy());

        log.info("所有 orderBy 字段无效，已清空");
    }

    @Test
    @Order(11)
    @DisplayName("orderBy 字段全部在 SELECT 中时保留")
    void testOrderByAllValid_Keep() {
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setAutoGroupBy(true);
        queryRequest.setColumns(Arrays.asList(
            "product$categoryName",
            "sum(salesAmount) as totalSales"
        ));

        // 设置 orderBy，所有字段都在 columns 中
        List<OrderRequestDef> orderBy = new ArrayList<>();
        OrderRequestDef o1 = new OrderRequestDef();
        o1.setField("product$categoryName");
        o1.setOrder("ASC");
        orderBy.add(o1);
        OrderRequestDef o2 = new OrderRequestDef();
        o2.setField("totalSales");
        o2.setOrder("DESC");
        orderBy.add(o2);
        queryRequest.setOrderBy(orderBy);

        ModelResultContext ctx = createContext(queryRequest);
        executeSteps(ctx);

        // orderBy 应该保持不变
        List<OrderRequestDef> resultOrderBy = queryRequest.getOrderBy();
        assertNotNull(resultOrderBy);
        assertEquals(2, resultOrderBy.size());
        assertEquals("product$categoryName", resultOrderBy.get(0).getField());
        assertEquals("totalSales", resultOrderBy.get(1).getField());

        log.info("有效的 orderBy 保持不变: {}", resultOrderBy);
    }

    /**
     * 创建测试用的 ModelResultContext
     */
    private ModelResultContext createContext(DbQueryRequestDef queryRequest) {
        PagingRequest<DbQueryRequestDef> pagingRequest = new PagingRequest<>();
        pagingRequest.setParam(queryRequest);

        ModelResultContext ctx = new ModelResultContext();
        ctx.setRequest(pagingRequest);
        return ctx;
    }
}
