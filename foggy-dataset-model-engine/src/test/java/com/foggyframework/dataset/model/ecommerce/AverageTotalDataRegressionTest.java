package com.foggyframework.dataset.model.ecommerce;

import com.foggyframework.dataset.client.domain.PagingRequest;
import com.foggyframework.dataset.model.PagingResultImpl;
import com.foggyframework.dataset.model.def.query.request.CalculatedFieldDef;
import com.foggyframework.dataset.model.def.query.request.DbQueryRequestDef;
import com.foggyframework.dataset.model.def.query.request.GroupRequestDef;
import com.foggyframework.dataset.model.def.query.request.OrderRequestDef;
import com.foggyframework.dataset.model.def.query.request.PostAggregateCalculationDef;
import com.foggyframework.dataset.model.def.query.request.SliceRequestDef;
import com.foggyframework.dataset.model.def.query.request.WindowOrderDef;
import com.foggyframework.dataset.model.engine.JdbcModelQueryEngine;
import com.foggyframework.dataset.model.engine.pivot.transport.DomainTransportField;
import com.foggyframework.dataset.model.engine.pivot.transport.DomainTransportPlan;
import com.foggyframework.dataset.model.engine.pivot.transport.DomainTransportTuple;
import com.foggyframework.dataset.model.engine.query.DbQueryResult;
import com.foggyframework.dataset.model.engine.stage.result.ResultStagePlan;
import com.foggyframework.dataset.model.engine.stage.result.ResultStagePreparation;
import com.foggyframework.dataset.model.impl.measure.DbMeasureSupport;
import com.foggyframework.dataset.model.plugins.result_set_filter.ModelResultContext;
import com.foggyframework.dataset.model.service.AdvancedQueryFacade;
import com.foggyframework.dataset.model.spi.DbAggregation;
import com.foggyframework.dataset.model.spi.DbMeasure;
import com.foggyframework.dataset.model.spi.JdbcQueryModel;
import com.foggyframework.dataset.model.spi.TableModel;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Grouped {@code totalData} regression tests for algebraic AVG rollups.
 *
 * <p>The fixture deliberately has category groups of sizes 1, 4 and 5, so an
 * unweighted {@code AVG(group_avg)} cannot accidentally equal the fact-level
 * average. Existing measures are changed only for the duration of a
 * synchronous facade call and restored in {@code finally}.</p>
 */
@DisplayName("totalData AVG 全范围回归测试")
class AverageTotalDataRegressionTest extends EcommerceTestSupport {

    private static final BigDecimal EPSILON = new BigDecimal("0.000001");

    @Resource
    private AdvancedQueryFacade queryFacade;

    @Test
    @DisplayName("预定义 AVG：总计应是事实行总体平均，分组结果保持不变")
    void predefinedAverageShouldBeReaggregatedFromFactsAndKeepGroupedRows() {
        DbQueryRequestDef request = groupedRequest("product$categoryName");
        request.setColumns(List.of(
                "product$categoryName",
                "unitPrice"
        ));

        DbQueryResult queryResult = queryWithAverageMeasure(request, 0, 100, "unitPrice");
        PagingResultImpl<?> result = queryResult.getPagingResult();
        Map<String, Object> totalData = totalData(result);

        assertDecimalEquals(nativeDecimal("select avg(unit_price) from fact_sales"),
                totalData.get("unitPrice"), "AVG 总计必须来自完整事实范围");

        Map<String, BigDecimal> expectedGroups = new HashMap<>();
        jdbcTemplate.query("""
                        select p.category_name, avg(fs.unit_price)
                        from fact_sales fs
                        join dim_product p on p.product_key = fs.product_key
                        group by p.category_name
                        """,
                rs -> {
                    expectedGroups.put(rs.getString(1), decimal(rs.getObject(2)));
                });
        assertEquals(expectedGroups.size(), result.getItems().size(), "分组数量必须保持不变");
        for (Object item : result.getItems()) {
            Map<String, Object> row = row(item);
            String category = String.valueOf(row.get("product$categoryName"));
            assertDecimalEquals(expectedGroups.get(category), row.get("unitPrice"),
                    "维度分组 AVG 结果不得被总计修复改写");
        }

        JdbcModelQueryEngine engine = (JdbcModelQueryEngine) queryResult.getQueryEngine();
        String aggSql = engine.getAggSql().replaceAll("\\s+", " ").toLowerCase();
        assertFalse(aggSql.matches("(?s).*avg\\s*\\(\\s*tx\\..*unitprice.*"),
                "totalData SQL 不得继续生成 AVG(tx.分组平均值)");
    }

    @Test
    @DisplayName("SUM、COUNT、MIN、MAX 的 totalData 不得受 AVG 修复影响")
    void otherAggregateTotalsShouldRemainUnchanged() {
        assertDecimalEquals(nativeDecimal("select sum(sales_amount) from fact_sales"),
                predefinedAggregateTotal("salesAmount", DbAggregation.SUM),
                "SUM 总计不得受影响");
        assertDecimalEquals(nativeDecimal("select count(unit_cost) from fact_sales"),
                predefinedAggregateTotal("unitCost", DbAggregation.COUNT),
                "COUNT 总计不得受影响");
        assertDecimalEquals(nativeDecimal("select min(unit_cost) from fact_sales"),
                predefinedAggregateTotal("unitCost", DbAggregation.MIN),
                "MIN 总计不得受影响");
        assertDecimalEquals(nativeDecimal("select max(unit_cost) from fact_sales"),
                predefinedAggregateTotal("unitCost", DbAggregation.MAX),
                "MAX 总计不得受影响");
    }

    @Test
    @DisplayName("非优化 SQL 路径：AVG 总计同样应按事实行加权")
    void nonOptimizedAggregateSqlShouldUseTheSameAverageSemantics() {
        DbQueryRequestDef request = groupedRequest("product$categoryName");
        request.setColumns(List.of("product$categoryName", "unitPrice"));
        request.setOptimizeAggSql(false);

        PagingResultImpl<?> result = queryWithAverageMeasure(request, 0, 100, "unitPrice")
                .getPagingResult();

        assertDecimalEquals(nativeDecimal("select avg(unit_price) from fact_sales"),
                totalData(result).get("unitPrice"),
                "优化开关不得改变 AVG 总计语义");
    }

    @Test
    @DisplayName("分页、start/limit 与 orderBy 不得缩小 totalData 统计范围")
    void pagingAndOrderingShouldNotAffectAverageTotalScope() {
        DbQueryRequestDef request = groupedRequest("product$categoryName");
        request.setColumns(List.of("product$categoryName", "unitPrice"));
        request.setOrderBy(List.of(order("unitPrice", "desc")));

        PagingResultImpl<?> paged = queryWithAverageMeasure(request, 1, 1, "unitPrice")
                .getPagingResult();

        assertEquals(1, paged.getItems().size(), "分页结果只应返回一个分组");
        assertEquals(3, ((Number) paged.getTotal()).intValue(), "total 应统计完整分组范围");
        assertDecimalEquals(nativeDecimal("select avg(unit_price) from fact_sales"),
                totalData(paged).get("unitPrice"),
                "totalData 不得只统计分页后的分组");
    }

    @Test
    @DisplayName("WHERE 过滤：AVG 总计应基于完整过滤结果")
    void filteredAverageShouldUseAllMatchingFacts() {
        DbQueryRequestDef request = groupedRequest("product$categoryName");
        request.setColumns(List.of("product$categoryName", "unitPrice"));
        request.setSlice(List.of(new SliceRequestDef("orderStatus", "=", "COMPLETED")));

        PagingResultImpl<?> result = queryWithAverageMeasure(request, 0, 100, "unitPrice")
                .getPagingResult();

        assertDecimalEquals(nativeDecimal(
                        "select avg(unit_price) from fact_sales where order_status = 'COMPLETED'"),
                totalData(result).get("unitPrice"),
                "AVG 总计必须保留 WHERE 过滤范围");
    }

    @Test
    @DisplayName("HAVING 过滤：AVG 总计应按通过 HAVING 的分组样本量加权")
    void havingAverageShouldWeightOnlySurvivingGroups() {
        DbQueryRequestDef request = groupedRequest("product$categoryName");
        request.setColumns(List.of(
                "product$categoryName",
                "avg(unitPrice) as averageUnitPrice"
        ));
        request.setHaving(List.of(new SliceRequestDef("averageUnitPrice", ">", 1000)));

        PagingResultImpl<?> result = queryWithAverageMeasure(request, 0, 100, "unitPrice")
                .getPagingResult();
        BigDecimal expected = nativeDecimal("""
                select sum(group_sum) * 1.0 / sum(group_count)
                from (
                    select sum(fs.unit_price) as group_sum,
                           count(fs.unit_price) as group_count
                    from fact_sales fs
                    join dim_product p on p.product_key = fs.product_key
                    group by p.category_name
                    having avg(fs.unit_price) > 1000
                ) surviving_groups
                """);

        assertDecimalEquals(expected, totalData(result).get("averageUnitPrice"),
                "HAVING 后总计必须保留分组集合并按事实样本量加权");
    }

    @Test
    @DisplayName("无 groupBy：AVG 总计仍等于原始明细总体 AVG")
    void noGroupByAverageShouldRemainCorrect() {
        DbQueryRequestDef request = baseRequest();
        request.setColumns(List.of("unitPrice"));

        PagingResultImpl<?> result = queryWithAverageMeasure(request, 0, 100, "unitPrice")
                .getPagingResult();

        assertDecimalEquals(nativeDecimal("select avg(unit_price) from fact_sales"),
                totalData(result).get("unitPrice"),
                "无分组场景不得回退");
    }

    @Test
    @DisplayName("多层分组：AVG 总计不得按最细分组再次平均")
    void multiDimensionGroupingShouldReaggregateAverageFromFacts() {
        DbQueryRequestDef request = groupedRequest("product$categoryName", "paymentMethod");
        request.setColumns(List.of("product$categoryName", "paymentMethod", "unitPrice"));

        PagingResultImpl<?> result = queryWithAverageMeasure(request, 0, 100, "unitPrice")
                .getPagingResult();

        assertDecimalEquals(nativeDecimal("select avg(unit_price) from fact_sales"),
                totalData(result).get("unitPrice"),
                "多维分组不得对各组 AVG 做无权重平均");
    }

    @Test
    @DisplayName("NULL 语义：加权分母必须是 COUNT(AVG 原始表达式)，不能是 COUNT(*)")
    void averageStateShouldUseNonNullExpressionCount() {
        BigDecimal original = nativeDecimal("select unit_cost from fact_sales where sales_key = 1");
        jdbcTemplate.update("update fact_sales set unit_cost = null where sales_key = 1");
        try {
            DbQueryRequestDef request = groupedRequest("product$categoryName");
            request.setColumns(List.of("product$categoryName", "unitCost"));

            PagingResultImpl<?> result = queryWithAverageMeasure(request, 0, 100, "unitCost")
                    .getPagingResult();

            assertDecimalEquals(nativeDecimal("select avg(unit_cost) from fact_sales"),
                    totalData(result).get("unitCost"),
                    "AVG 状态分母必须排除原始表达式为 NULL 的事实行");
        } finally {
            jdbcTemplate.update("update fact_sales set unit_cost = ? where sales_key = 1", original);
        }
    }

    @Test
    @DisplayName("计算型 AVG：AVG(expr) 的 totalData 应在事实范围重新聚合")
    void calculatedAverageShouldBeReaggregatedFromItsSourceExpression() {
        DbQueryRequestDef request = groupedRequest("product$categoryName");
        CalculatedFieldDef averageSales = new CalculatedFieldDef(
                "averageSalesAmount", "平均销售金额", "AVG(salesAmount)");
        averageSales.setAgg("AVG");
        request.setCalculatedFields(List.of(averageSales));
        request.setColumns(List.of("product$categoryName", "averageSalesAmount"));

        PagingResultImpl<?> result = queryWithAverageMeasure(request, 0, 100, "unitPrice")
                .getPagingResult();

        assertDecimalEquals(nativeDecimal("select avg(sales_amount) from fact_sales"),
                totalData(result).get("averageSalesAmount"),
                "计算字段 AVG 总计不得为空或再次平均");
    }

    @Test
    @DisplayName("计算型平均：SUM/COUNT 比率的 totalData 应由总体分子分母重算")
    void calculatedRatioAverageShouldBeRecomputedFromGrandTotals() {
        DbQueryRequestDef request = groupedRequest("product$categoryName");
        CalculatedFieldDef totalSales = calculated("totalSales", "SUM(salesAmount)", "SUM");
        CalculatedFieldDef rowCount = calculated("rowCount", "COUNT(orderId)", "COUNT");
        CalculatedFieldDef averageSales = calculated("averageSales", "totalSales / rowCount", null);
        request.setCalculatedFields(List.of(totalSales, rowCount, averageSales));
        request.setColumns(List.of(
                "product$categoryName", "totalSales", "rowCount", "averageSales"));

        PagingResultImpl<?> result = queryWithAverageMeasure(request, 0, 100, "unitPrice")
                .getPagingResult();
        BigDecimal expected = nativeDecimal(
                "select sum(sales_amount) * 1.0 / count(order_id) from fact_sales");

        assertDecimalEquals(expected, totalData(result).get("averageSales"),
                "计算型平均必须使用总体 SUM / 总体 COUNT");
    }

    @Test
    @DisplayName("多阶段查询：后聚合包装存在时 AVG 状态仍应贯穿到 totalData")
    void postAggregateStageShouldPreserveAverageStateForTotalData() {
        DbQueryRequestDef request = groupedRequest("product$categoryName");
        request.setColumns(List.of(
                "product$categoryName",
                "avg(unitPrice) as averageUnitPrice",
                "sum(salesAmount) as totalSales"
        ));
        request.setPostAggregateCalculations(List.of(new PostAggregateCalculationDef(
                "salesShare", "ratioToTotal", "totalSales", "grandTotal", "ratio")));

        PagingResultImpl<?> result = queryWithAverageMeasure(request, 0, 100, "unitPrice")
                .getPagingResult();

        assertFalse(result.getItems().isEmpty(), "多阶段查询应返回分组结果");
        assertTrue(row(result.getItems().get(0)).containsKey("salesShare"),
                "后聚合字段应保持可用");
        assertDecimalEquals(nativeDecimal("select avg(unit_price) from fact_sales"),
                totalData(result).get("averageUnitPrice"),
                "最终阶段包装不得丢失或错误汇总 AVG 状态");
    }

    @Test
    @DisplayName("postAggregate + postSlice：AVG 总计只合并幸存分组状态")
    void postAggregatePostSliceShouldMergeOnlySurvivingAverageStates() {
        DbQueryRequestDef request = groupedRequest("product$categoryName");
        request.setColumns(List.of(
                "product$categoryName",
                "avg(unitPrice) as averageUnitPrice",
                "sum(salesAmount) as totalSales",
                "salesShare"
        ));
        request.setPostAggregateCalculations(List.of(new PostAggregateCalculationDef(
                "salesShare", "ratioToTotal", "totalSales", "grandTotal", "ratio")));
        request.setPostSlice(List.of(new SliceRequestDef("salesShare", ">", 0.2)));

        DbQueryResult queryResult = queryWithAverageMeasure(request, 0, 100, "unitPrice");
        PagingResultImpl<?> result = queryResult.getPagingResult();
        BigDecimal expected = nativeDecimal("""
                with grouped as (
                    select p.category_name,
                           sum(fs.unit_price) as avg_sum,
                           count(fs.unit_price) as avg_count,
                           sum(fs.sales_amount) as total_sales
                    from fact_sales fs
                    join dim_product p on p.product_key = fs.product_key
                    group by p.category_name
                ), scored as (
                    select grouped.*,
                           total_sales * 1.0 / sum(total_sales) over () as sales_share
                    from grouped
                )
                select sum(avg_sum) * 1.0 / sum(avg_count)
                from scored
                where sales_share > 0.2
                """);

        assertDecimalEquals(expected, totalData(result).get("averageUnitPrice"),
                "postSlice 后 AVG 总计必须只合并幸存分组的 SUM/COUNT 状态");
        assertSharedResultStageTotalSql(queryResult, "post_stage");
    }

    @Test
    @DisplayName("window + postSlice：AVG 状态应贯穿排名结果过滤")
    void windowPostSliceShouldPreserveAverageStates() {
        DbQueryRequestDef request = groupedRequest("product$categoryName");
        CalculatedFieldDef rank = calculated("averagePriceRank", "RANK()", null);
        rank.setWindowOrderBy(List.of(new WindowOrderDef("averageUnitPrice", "desc")));
        request.setCalculatedFields(List.of(rank));
        request.setColumns(List.of(
                "product$categoryName",
                "avg(unitPrice) as averageUnitPrice",
                "sum(salesAmount) as totalSales",
                "averagePriceRank"
        ));
        request.setPostSlice(List.of(new SliceRequestDef("averagePriceRank", "<=", 2)));

        DbQueryResult queryResult = queryWithAverageMeasure(request, 0, 100, "unitPrice");
        PagingResultImpl<?> result = queryResult.getPagingResult();
        BigDecimal expected = nativeDecimal("""
                with grouped as (
                    select p.category_name,
                           sum(fs.unit_price) as avg_sum,
                           count(fs.unit_price) as avg_count,
                           sum(fs.sales_amount) as total_sales
                    from fact_sales fs
                    join dim_product p on p.product_key = fs.product_key
                    group by p.category_name
                ), ranked as (
                    select grouped.*,
                           rank() over (order by avg_sum * 1.0 / avg_count desc) as sales_rank
                    from grouped
                )
                select sum(avg_sum) * 1.0 / sum(avg_count)
                from ranked
                where sales_rank <= 2
                """);

        assertDecimalEquals(expected, totalData(result).get("averageUnitPrice"),
                "window postSlice 后 AVG 总计必须保留幸存组状态");
        assertSharedResultStageTotalSql(queryResult, "__POST_RESULT_STAGE__");
    }

    @Test
    @DisplayName("SQLite 组合：domain CTE + AVG state + window/postSlice 必须执行并保持参数拓扑")
    void domainCteAverageWindowPostSliceShouldExecuteWithWeightedTotal() {
        List<String> categories = jdbcTemplate.queryForList(
                "select distinct category_name from dim_product order by category_name",
                String.class);
        assertTrue(categories.size() >= 3, "夹具至少需要三个不同样本量的分组");
        DomainTransportPlan domain = DomainTransportPlan.builder()
                .relationName("_avg_domain")
                .fields(List.of(new DomainTransportField("product$categoryName")))
                .tuples(categories.stream()
                        .map(category -> new DomainTransportTuple(List.of(category)))
                        .toList())
                .build();

        DbQueryRequestDef request = groupedRequest("product$categoryName");
        CalculatedFieldDef rank = calculated("averagePriceRank", "RANK()", null);
        rank.setWindowOrderBy(List.of(new WindowOrderDef("averageUnitPrice", "desc")));
        request.setCalculatedFields(List.of(rank));
        request.setColumns(List.of(
                "product$categoryName",
                "avg(unitPrice) as averageUnitPrice",
                "averagePriceRank"
        ));
        request.setPostSlice(List.of(new SliceRequestDef("averagePriceRank", "<=", 2)));

        DbQueryResult queryResult = queryWithAverageMeasureAndDomain(
                request, 0, 100, "unitPrice", domain);
        PagingResultImpl<?> result = queryResult.getPagingResult();
        String placeholders = String.join(", ",
                Collections.nCopies(categories.size(), "?"));
        BigDecimal expected = nativeDecimal("""
                with grouped as (
                    select p.category_name,
                           sum(fs.unit_price) as avg_sum,
                           count(fs.unit_price) as avg_count
                    from fact_sales fs
                    join dim_product p on p.product_key = fs.product_key
                    where p.category_name in (%s)
                    group by p.category_name
                ), ranked as (
                    select grouped.*,
                           rank() over (order by avg_sum * 1.0 / avg_count desc) as price_rank
                    from grouped
                )
                select sum(avg_sum) * 1.0 / sum(avg_count)
                from ranked
                where price_rank <= 2
                """.formatted(placeholders), categories.toArray());

        assertDecimalEquals(expected, totalData(result).get("averageUnitPrice"),
                "domain 过滤后的幸存组必须按 SUM/COUNT 状态加权");
        JdbcModelQueryEngine engine = (JdbcModelQueryEngine) queryResult.getQueryEngine();
        String totalSql = engine.getAggSql();
        assertTrue(totalSql.startsWith("WITH _avg_domain AS"), totalSql);
        assertTrue(totalSql.contains(",\nstage1 AS"), totalSql);
        assertEquals(totalSql.toUpperCase().indexOf("WITH "),
                totalSql.toUpperCase().lastIndexOf("WITH "), totalSql);
        assertEquals(categories, engine.getAggValues().subList(0, categories.size()));
    }

    @Test
    @DisplayName("window 隐藏输入：未公开选择的排序度量也必须投影到 totalData 结果阶段")
    void windowShouldMaterializeHiddenOrderingMeasureForTotalData() {
        DbQueryRequestDef request = groupedRequest("product$categoryName");
        CalculatedFieldDef rowNumber = calculated(
                "hiddenSalesRowNumber", "ROW_NUMBER()", null);
        rowNumber.setWindowOrderBy(List.of(new WindowOrderDef("salesAmount", "desc")));
        request.setCalculatedFields(List.of(rowNumber));
        request.setColumns(List.of(
                "product$categoryName",
                "avg(unitPrice) as averageUnitPrice",
                "hiddenSalesRowNumber"
        ));
        request.setPostSlice(List.of(new SliceRequestDef("hiddenSalesRowNumber", "<=", 2)));

        DbQueryResult queryResult = queryWithAverageMeasure(request, 0, 100, "unitPrice");
        PagingResultImpl<?> result = queryResult.getPagingResult();
        BigDecimal expected = nativeDecimal("""
                with grouped as (
                    select p.category_name,
                           sum(fs.unit_price) as avg_sum,
                           count(fs.unit_price) as avg_count,
                           sum(fs.sales_amount) as total_sales
                    from fact_sales fs
                    join dim_product p on p.product_key = fs.product_key
                    group by p.category_name
                ), ranked as (
                    select grouped.*,
                           row_number() over (order by total_sales desc) as sales_row_number
                    from grouped
                )
                select sum(avg_sum) * 1.0 / sum(avg_count)
                from ranked
                where sales_row_number <= 2
                """);

        assertDecimalEquals(expected, totalData(result).get("averageUnitPrice"),
                "totalData 必须补齐 window 引用但未公开返回的聚合度量");
        assertPreparedHiddenDependencySharedByMainAndTotal(queryResult, "salesAmount");
    }

    @Test
    @DisplayName("计算型平均：未公开选择的 SUM/COUNT 依赖也必须递归物化")
    void calculatedRatioShouldMaterializeHiddenAggregateDependencies() {
        DbQueryRequestDef request = groupedRequest("product$categoryName");
        CalculatedFieldDef totalSales = calculated("totalSales", "SUM(salesAmount)", "SUM");
        CalculatedFieldDef rowCount = calculated("rowCount", "COUNT(orderId)", "COUNT");
        CalculatedFieldDef averageSales = calculated("averageSales", "totalSales / rowCount", null);
        request.setCalculatedFields(List.of(totalSales, rowCount, averageSales));
        request.setColumns(List.of("product$categoryName", "averageSales"));

        PagingResultImpl<?> result = queryWithAverageMeasure(request, 0, 100, "unitPrice")
                .getPagingResult();

        assertDecimalEquals(nativeDecimal(
                        "select sum(sales_amount) * 1.0 / count(order_id) from fact_sales"),
                totalData(result).get("averageSales"),
                "totalData 必须递归解析并物化未公开的聚合依赖");
    }

    @Test
    @DisplayName("标量包装 AVG：ROUND/算术应在 merge 后重新求值")
    void scalarWrappedAverageShouldBeFinalizedAfterStateMerge() {
        DbQueryRequestDef request = groupedRequest("product$categoryName");
        CalculatedFieldDef wrapped = calculated(
                "adjustedAverage", "ROUND(AVG(unitPrice), 2) + 1", null);
        request.setCalculatedFields(List.of(wrapped));
        request.setColumns(List.of("product$categoryName", "adjustedAverage"));

        PagingResultImpl<?> result = queryWithAverageMeasure(request, 0, 100, "unitPrice")
                .getPagingResult();

        assertDecimalEquals(nativeDecimal(
                        "select round(avg(unit_price), 2) + 1 from fact_sales"),
                totalData(result).get("adjustedAverage"),
                "标量包装必须在 AVG state merge 后执行");
    }

    @Test
    @DisplayName("多聚合叶子：同一公开字段内的两个 AVG 必须独立绑定状态")
    void multipleAverageLeavesShouldBindByStableLeafIdentity() {
        DbQueryRequestDef request = groupedRequest("product$categoryName");
        CalculatedFieldDef combined = calculated(
                "combinedAverage", "AVG(unitPrice) + AVG(unitCost)", null);
        request.setCalculatedFields(List.of(combined));
        request.setColumns(List.of("product$categoryName", "combinedAverage"));

        PagingResultImpl<?> result = queryWithAverageMeasure(request, 0, 100, "unitPrice")
                .getPagingResult();

        assertDecimalEquals(nativeDecimal(
                        "select avg(unit_price) + avg(unit_cost) from fact_sales"),
                totalData(result).get("combinedAverage"),
                "每个 AVG AST 叶子必须通过独立 leafId 绑定各自 SUM/COUNT 状态");
    }

    @Test
    @DisplayName("混合不可 merge 聚合：AVG 与 COUNT_DISTINCT 必须明确 fail closed")
    void mixedNonMergeableAggregateShouldBeRefused() {
        DbQueryRequestDef request = groupedRequest("product$categoryName");
        request.setColumns(List.of(
                "product$categoryName",
                "avg(unitPrice) as averageUnitPrice",
                "countd(customer$id) as uniqueCustomers"
        ));

        RuntimeException error = assertThrows(RuntimeException.class,
                () -> queryWithAverageMeasure(request, 0, 100, "unitPrice"));

        assertTrue(error.getMessage().contains("TOTAL_DATA_AGGREGATE_NOT_MERGEABLE"),
                error.getMessage());
    }

    @Test
    @DisplayName("单独不可 merge 聚合：COUNT_DISTINCT 的分组 totalData 必须 fail closed")
    void standaloneCountDistinctShouldBeRefused() {
        DbQueryRequestDef request = groupedRequest("product$categoryName");
        request.setColumns(List.of(
                "product$categoryName",
                "countd(customer$id) as uniqueCustomers"
        ));

        RuntimeException error = assertThrows(RuntimeException.class,
                () -> queryWithAverageMeasure(request, 0, 100, "unitPrice"));

        assertTrue(error.getMessage().contains("TOTAL_DATA_AGGREGATE_NOT_MERGEABLE"),
                error.getMessage());
    }

    @Test
    @DisplayName("内部状态别名：用户别名与 __foggy_ 保留空间冲突时必须 fail closed")
    void reservedInternalStateAliasShouldBeRefused() {
        DbQueryRequestDef request = groupedRequest("product$categoryName");
        request.setColumns(List.of(
                "product$categoryName",
                "avg(unitPrice) as __foggy_avg_sum_0"
        ));

        RuntimeException error = assertThrows(RuntimeException.class,
                () -> queryWithAverageMeasure(request, 0, 100, "unitPrice"));

        assertTrue(error.getMessage().contains("TOTAL_DATA_AGGREGATE_NOT_MERGEABLE"),
                error.getMessage());
    }

    private DbQueryRequestDef baseRequest() {
        DbQueryRequestDef request = new DbQueryRequestDef();
        request.setQueryModel("FactSalesQueryModel");
        request.setReturnTotal(true);
        return request;
    }

    private DbQueryRequestDef groupedRequest(String... fields) {
        DbQueryRequestDef request = baseRequest();
        request.setGroupBy(java.util.Arrays.stream(fields).map(this::group).toList());
        return request;
    }

    private GroupRequestDef group(String field) {
        GroupRequestDef group = new GroupRequestDef();
        group.setField(field);
        return group;
    }

    private OrderRequestDef order(String field, String direction) {
        OrderRequestDef order = new OrderRequestDef();
        order.setField(field);
        order.setDir(direction);
        return order;
    }

    private CalculatedFieldDef calculated(String name, String expression, String aggregation) {
        CalculatedFieldDef field = new CalculatedFieldDef(name, name, expression);
        field.setAgg(aggregation);
        return field;
    }

    private DbQueryResult queryWithAverageMeasure(DbQueryRequestDef request,
                                                  int start,
                                                  int limit,
                                                  String measureName) {
        return queryWithMeasureAggregation(
                request, start, limit, measureName, DbAggregation.AVG);
    }

    private DbQueryResult queryWithAverageMeasureAndDomain(
            DbQueryRequestDef request,
            int start,
            int limit,
            String measureName,
            DomainTransportPlan domain) {
        DbMeasureSupport measure = findMeasure(measureName);
        DbAggregation original = measure.getAggregation();
        measure.setAggregation(DbAggregation.AVG);
        try {
            PagingRequest<DbQueryRequestDef> paging = new PagingRequest<>(
                    start / Math.max(limit, 1) + 1, limit, start, limit, request);
            ModelResultContext context = new ModelResultContext();
            context.setRequest(paging);
            context.getExtData().put(DomainTransportPlan.EXT_DATA_KEY, List.of(domain));
            return queryFacade.queryModelResult(context);
        } finally {
            measure.setAggregation(original);
        }
    }

    private DbQueryResult queryWithMeasureAggregation(DbQueryRequestDef request,
                                                      int start,
                                                      int limit,
                                                      String measureName,
                                                      DbAggregation aggregation) {
        DbMeasureSupport measure = findMeasure(measureName);
        DbAggregation original = measure.getAggregation();
        measure.setAggregation(aggregation);
        try {
            PagingRequest<DbQueryRequestDef> paging = new PagingRequest<>(
                    start / Math.max(limit, 1) + 1, limit, start, limit, request);
            return queryFacade.queryModelResult(paging);
        } finally {
            measure.setAggregation(original);
        }
    }

    private Object predefinedAggregateTotal(String measureName, DbAggregation aggregation) {
        DbQueryRequestDef request = groupedRequest("product$categoryName");
        request.setColumns(List.of("product$categoryName", measureName));
        PagingResultImpl<?> result = queryWithMeasureAggregation(
                request, 0, 100, measureName, aggregation).getPagingResult();
        return totalData(result).get(measureName);
    }

    private DbMeasureSupport findMeasure(String measureName) {
        JdbcQueryModel queryModel = getQueryModel("FactSalesQueryModel");
        for (TableModel tableModel : queryModel.getJdbcModelList()) {
            DbMeasure measure = tableModel.findJdbcMeasureByName(measureName);
            if (measure instanceof DbMeasureSupport support) {
                return support;
            }
        }
        throw new AssertionError("测试模型中未找到 measure: " + measureName);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> totalData(PagingResultImpl<?> result) {
        assertNotNull(result.getTotalData(), "returnTotal=true 时必须返回 totalData");
        assertTrue(result.getTotalData() instanceof Map, "totalData 必须是 Map");
        return (Map<String, Object>) result.getTotalData();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> row(Object item) {
        return (Map<String, Object>) item;
    }

    private BigDecimal nativeDecimal(String sql) {
        return decimal(jdbcTemplate.queryForObject(sql, Object.class));
    }

    private BigDecimal nativeDecimal(String sql, Object... args) {
        return decimal(jdbcTemplate.queryForObject(sql, Object.class, args));
    }

    private BigDecimal decimal(Object value) {
        assertNotNull(value, "数值不应为 null");
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        return new BigDecimal(String.valueOf(value));
    }

    private void assertSharedResultStageTotalSql(
            DbQueryResult queryResult,
            String expectedResultStageAlias) {
        JdbcModelQueryEngine engine = (JdbcModelQueryEngine) queryResult.getQueryEngine();
        String totalSql = engine.getAggSql();
        assertTrue(totalSql.contains("WITH stage1 AS"), totalSql);
        assertTrue(totalSql.contains(expectedResultStageAlias + " AS"), totalSql);
        assertFalse(totalSql.contains("__foggy_total_stage_"), totalSql);
        assertFalse(totalSql.contains("WITH __foggy_total_base AS"), totalSql);
    }

    private void assertPreparedHiddenDependencySharedByMainAndTotal(
            DbQueryResult queryResult,
            String alias) {
        JdbcModelQueryEngine engine = (JdbcModelQueryEngine) queryResult.getQueryEngine();
        ResultStagePreparation preparation = (ResultStagePreparation)
                ReflectionTestUtils.getField(engine, "resultStagePreparation");
        assertNotNull(preparation, "window 请求必须在 visitor 前生成 request preparation");
        ResultStagePreparation.Projection main = preparation.baseProjectionPlan().main().projections()
                .stream()
                .filter(projection -> projection.column().role()
                        == ResultStagePlan.ColumnRole.HIDDEN_DEPENDENCY)
                .filter(projection -> alias.equals(projection.column().alias()))
                .findFirst()
                .orElseThrow();
        ResultStagePreparation.Projection total = preparation.baseProjectionPlan().total().projections()
                .stream()
                .filter(projection -> projection.column().role()
                        == ResultStagePlan.ColumnRole.HIDDEN_DEPENDENCY)
                .filter(projection -> alias.equals(projection.column().alias()))
                .findFirst()
                .orElseThrow();
        assertSame(main.source(), total.source(),
                "MAIN/TOTAL hidden dependency 必须来自一次 prepare 的同一个物理列绑定");
    }

    private void assertDecimalEquals(BigDecimal expected, Object actual, String message) {
        BigDecimal actualDecimal = decimal(actual);
        assertTrue(expected.subtract(actualDecimal).abs().compareTo(EPSILON) <= 0,
                () -> message + ", expected=" + expected + ", actual=" + actualDecimal);
    }
}
