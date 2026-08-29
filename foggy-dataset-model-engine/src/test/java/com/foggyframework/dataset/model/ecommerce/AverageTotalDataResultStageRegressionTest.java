package com.foggyframework.dataset.model.ecommerce;

import com.foggyframework.dataset.model.PagingResultImpl;
import com.foggyframework.dataset.model.def.query.request.CalculatedFieldDef;
import com.foggyframework.dataset.model.def.query.request.DbQueryRequestDef;
import com.foggyframework.dataset.model.def.query.request.PostAggregateCalculationDef;
import com.foggyframework.dataset.model.def.query.request.SliceRequestDef;
import com.foggyframework.dataset.model.def.query.request.WindowOrderDef;
import com.foggyframework.dataset.model.engine.JdbcModelQueryEngine;
import com.foggyframework.dataset.model.engine.compose.SqlGenerationResult;
import com.foggyframework.dataset.model.engine.pivot.transport.DomainTransportField;
import com.foggyframework.dataset.model.engine.pivot.transport.DomainTransportPlan;
import com.foggyframework.dataset.model.engine.pivot.transport.DomainTransportTuple;
import com.foggyframework.dataset.model.engine.query.DbQueryResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("totalData AVG 结果阶段回归测试")
class AverageTotalDataResultStageRegressionTest extends AverageTotalDataTestSupport {
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
    @SuppressWarnings("removal")
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
        assertEquals("_avg_domain", engine.getCteStages().get(0).alias());
        SqlGenerationResult.CteStage stage1 = engine.getCteStages().stream()
                .filter(stage -> "stage1".equals(stage.alias()))
                .findFirst()
                .orElseThrow();
        assertEquals("stage1", engine.getCteStage1Alias());
        assertEquals(stage1.sql(), engine.getCteStage1Sql());
        assertEquals(stage1.params(), engine.getCteStage1Params());
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

}
