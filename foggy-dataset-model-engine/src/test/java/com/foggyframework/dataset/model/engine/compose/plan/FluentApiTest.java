package com.foggyframework.dataset.model.engine.compose.plan;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the OO fluent API classes: PlanColumnRef, AggregateColumn,
 * ProjectedColumn, ComposeJoinBuilder, and the fluent methods on QueryPlan.
 *
 * <p>These are pure AST/structure tests — no DB, no sandbox, no compiler.</p>
 *
 * @since 8.2.0.beta
 */
@DisplayName("Fluent API (OO Chain)")
class FluentApiTest {

    // ---- Helpers ----

    private static BaseModelPlan basePlan(String model) {
        return BaseModelPlan.builder().model(model).columns(List.of("id")).build();
    }

    private static List<String> toExprList(List<Object> cols) {
        return cols.stream().map(c -> {
            if (c instanceof ProjectedColumn pc) return pc.toColumnExpr();
            if (c instanceof PlanColumnRef ref) return ref.toColumnExpr();
            if (c instanceof AggregateColumn agg) return agg.toColumnExpr();
            if (c instanceof WindowColumn win) return win.toColumnExpr();
            return c.toString();
        }).toList();
    }

    // ==================================================================
    // PlanColumnRef
    // ==================================================================

    @Nested
    @DisplayName("PlanColumnRef")
    class PlanColumnRefTests {

        @Test
        @DisplayName("basic creation and accessors")
        void basicCreation() {
            QueryPlan plan = basePlan("SaleOrderQM");
            PlanColumnRef ref = new PlanColumnRef(plan, "partnerId");
            assertEquals("partnerId", ref.name());
            assertSame(plan, ref.plan());
            assertEquals("partnerId", ref.toColumnExpr());
        }

        @Test
        @DisplayName("as() returns ProjectedColumn")
        void asReturnsProjectedColumn() {
            PlanColumnRef ref = new PlanColumnRef(basePlan("QM"), "amount");
            ProjectedColumn pc = ref.as("totalAmount", "总金额");
            assertEquals("totalAmount", pc.alias());
            assertEquals("总金额", pc.caption());
            assertEquals("amount$总金额 AS totalAmount", pc.toColumnExpr());
        }

        @Test
        @DisplayName("as() without caption")
        void asWithoutCaption() {
            PlanColumnRef ref = new PlanColumnRef(basePlan("QM"), "name");
            ProjectedColumn pc = ref.as("customerName");
            assertNull(pc.caption());
            assertEquals("name AS customerName", pc.toColumnExpr());
        }

        @Test
        @DisplayName("sum/count/avg/max/min return AggregateColumn")
        void aggregationMethods() {
            PlanColumnRef ref = new PlanColumnRef(basePlan("QM"), "amountTotal");
            assertEquals("SUM(amountTotal)", ref.sum().toColumnExpr());
            assertEquals("COUNT(amountTotal)", ref.count().toColumnExpr());
            assertEquals("AVG(amountTotal)", ref.avg().toColumnExpr());
            assertEquals("MAX(amountTotal)", ref.max().toColumnExpr());
            assertEquals("MIN(amountTotal)", ref.min().toColumnExpr());
        }

        @Test
        @DisplayName("PropertyFunction invoke dispatches correctly")
        void propertyFunctionInvoke() {
            PlanColumnRef ref = new PlanColumnRef(basePlan("QM"), "amount");
            Object result = ref.invoke(null, "sum", null);
            assertInstanceOf(AggregateColumn.class, result);
            assertEquals("SUM", ((AggregateColumn) result).func());
        }
    }

    // ==================================================================
    // AggregateColumn
    // ==================================================================

    @Nested
    @DisplayName("AggregateColumn")
    class AggregateColumnTests {

        @Test
        @DisplayName("chained as() produces correct expression")
        void chainedAs() {
            PlanColumnRef ref = new PlanColumnRef(basePlan("QM"), "amountTotal");
            ProjectedColumn pc = ref.sum().as("totalAmount", "总金额");
            assertEquals("SUM(amountTotal)$总金额 AS totalAmount", pc.toColumnExpr());
        }

        @Test
        @DisplayName("as() without caption")
        void asWithoutCaption() {
            PlanColumnRef ref = new PlanColumnRef(basePlan("QM"), "amountTotal");
            ProjectedColumn pc = ref.sum().as("totalAmount");
            assertEquals("SUM(amountTotal) AS totalAmount", pc.toColumnExpr());
        }
    }

    // ==================================================================
    // Window Functions (8.3.0.beta)
    // ==================================================================

    @Nested
    @DisplayName("Window Functions")
    class WindowFunctionTests {

        @Test
        @DisplayName("lag with offset returns WindowColumn")
        void lagWindowColumn() {
            PlanColumnRef ref = new PlanColumnRef(basePlan("QM"), "amountTotal");
            WindowColumn win = ref.lag(1).over(Map.of("partitionBy", List.of(ref), "orderBy", List.of("date")));
            assertEquals("LAG", win.func());
            assertEquals("LAG(amountTotal, 1) OVER (PARTITION BY amountTotal ORDER BY date ASC)", win.toColumnExpr());
            assertEquals(1, win.args().get(0));
            
            ProjectedColumn pc = win.as("prevAmount");
            assertEquals("LAG(amountTotal, 1) OVER (PARTITION BY amountTotal ORDER BY date ASC) AS prevAmount", pc.toColumnExpr());
        }

        @Test
        @DisplayName("lag() default offset is 1")
        void lagDefaultOffset() {
            PlanColumnRef ref = new PlanColumnRef(basePlan("QM"), "amount");
            WindowColumn win = ref.lag().over(Map.of("orderBy", List.of("date")));
            assertEquals("LAG(amount, 1) OVER (ORDER BY date ASC)", win.toColumnExpr());
        }

        @Test
        @DisplayName("lead with offset returns WindowColumn")
        void leadWindowColumn() {
            PlanColumnRef ref = new PlanColumnRef(basePlan("QM"), "amountTotal");
            WindowColumn win = ref.lead(2).over(Map.of("orderBy", List.of("date")));
            assertEquals("LEAD", win.func());
            assertEquals("LEAD(amountTotal, 2) OVER (ORDER BY date ASC)", win.toColumnExpr());
        }

        @Test
        @DisplayName("lead() default offset is 1")
        void leadDefaultOffset() {
            PlanColumnRef ref = new PlanColumnRef(basePlan("QM"), "amount");
            WindowColumn win = ref.lead().over(Map.of("orderBy", List.of("date")));
            assertEquals("LEAD(amount, 1) OVER (ORDER BY date ASC)", win.toColumnExpr());
        }

        @Test
        @DisplayName("rowNumber returns WindowColumn")
        void rowNumberWindowColumn() {
            QueryPlan plan = basePlan("QM");
            WindowColumn win = plan.rowNumber().over(Map.of("orderBy", List.of("-date")));
            assertEquals("ROW_NUMBER", win.func());
            assertEquals("ROW_NUMBER() OVER (ORDER BY date DESC)", win.toColumnExpr());
            
            ProjectedColumn pc = win.as("rn");
            assertEquals("ROW_NUMBER() OVER (ORDER BY date DESC) AS rn", pc.toColumnExpr());
        }

        @Test
        @DisplayName("rank returns WindowColumn")
        void rankWindowColumn() {
            QueryPlan plan = basePlan("QM");
            WindowColumn win = plan.rank().over(Map.of("orderBy", List.of("-score")));
            assertEquals("RANK", win.func());
            assertNull(win.ref());
            assertEquals("RANK() OVER (ORDER BY score DESC)", win.toColumnExpr());
        }

        @Test
        @DisplayName("denseRank returns WindowColumn")
        void denseRankWindowColumn() {
            QueryPlan plan = basePlan("QM");
            WindowColumn win = plan.denseRank().over(Map.of(
                    "partitionBy", List.of("category"),
                    "orderBy", List.of("-score")));
            assertEquals("DENSE_RANK", win.func());
            assertEquals("DENSE_RANK() OVER (PARTITION BY category ORDER BY score DESC)", win.toColumnExpr());
        }

        @Test
        @DisplayName("aggregate over returns WindowColumn")
        void aggregateOver() {
            PlanColumnRef ref = new PlanColumnRef(basePlan("QM"), "amountTotal");
            WindowColumn win = ref.sum().over(Map.of("partitionBy", List.of("partnerId")));
            assertEquals("SUM", win.func());
            assertEquals("SUM(amountTotal) OVER (PARTITION BY partnerId)", win.toColumnExpr());
            
            ProjectedColumn pc = win.as("runningTotal");
            assertEquals("SUM(amountTotal) OVER (PARTITION BY partnerId) AS runningTotal", pc.toColumnExpr());
        }

        @Test
        @DisplayName("avg() over with both partition and order")
        void avgOverBothPartitionAndOrder() {
            PlanColumnRef ref = new PlanColumnRef(basePlan("QM"), "price");
            WindowColumn win = ref.avg().over(Map.of(
                    "partitionBy", List.of("category"),
                    "orderBy", List.of("date", "-id")));
            assertEquals("AVG(price) OVER (PARTITION BY category ORDER BY date ASC, id DESC)", win.toColumnExpr());
        }

        @Test
        @DisplayName("WindowColumn.as with caption")
        void windowColumnWithCaption() {
            PlanColumnRef ref = new PlanColumnRef(basePlan("QM"), "amount");
            WindowColumn win = ref.sum().over(Map.of("partitionBy", List.of("pid")));
            ProjectedColumn pc = win.as("runTotal", "累计金额");
            assertEquals("SUM(amount) OVER (PARTITION BY pid)$累计金额 AS runTotal", pc.toColumnExpr());
        }

        @Test
        @DisplayName("OverClause.fromMap(null) returns empty OVER()")
        void overClauseFromNull() {
            PlanColumnRef ref = new PlanColumnRef(basePlan("QM"), "amount");
            WindowColumn win = ref.sum().over(null);
            assertEquals("SUM(amount) OVER ()", win.toColumnExpr());
        }

        @Test
        @DisplayName("WindowColumn.invoke dispatches as()")
        void windowColumnInvokeAs() {
            PlanColumnRef ref = new PlanColumnRef(basePlan("QM"), "amount");
            WindowColumn win = ref.lag(1).over(Map.of("orderBy", List.of("date")));
            Object result = win.invoke(null, "as", new Object[]{"prev"});
            assertInstanceOf(ProjectedColumn.class, result);
            assertEquals("prev", ((ProjectedColumn) result).alias());
        }

        @Test
        @DisplayName("WindowColumn.invoke rejects unknown method")
        void windowColumnInvokeUnknown() {
            PlanColumnRef ref = new PlanColumnRef(basePlan("QM"), "amount");
            WindowColumn win = ref.lag(1).over(Map.of("orderBy", List.of("date")));
            assertThrows(IllegalArgumentException.class, () -> win.invoke(null, "foo", null));
        }

        @Test
        @DisplayName("WindowColumnBuilder.invoke dispatches over()")
        void builderInvokeOver() {
            PlanColumnRef ref = new PlanColumnRef(basePlan("QM"), "amount");
            WindowColumnBuilder builder = ref.lag(1);
            Object result = builder.invoke(null, "over", new Object[]{Map.of("orderBy", List.of("date"))});
            assertInstanceOf(WindowColumn.class, result);
        }

        @Test
        @DisplayName("WindowColumnBuilder.invoke rejects non-Map arg")
        void builderInvokeOverBadArg() {
            PlanColumnRef ref = new PlanColumnRef(basePlan("QM"), "amount");
            WindowColumnBuilder builder = ref.lag(1);
            assertThrows(IllegalArgumentException.class, () ->
                    builder.invoke(null, "over", new Object[]{"not-a-map"}));
        }

        @Test
        @DisplayName("WindowColumnBuilder.invoke rejects unknown method")
        void builderInvokeUnknown() {
            PlanColumnRef ref = new PlanColumnRef(basePlan("QM"), "amount");
            WindowColumnBuilder builder = ref.lag(1);
            assertThrows(IllegalArgumentException.class, () ->
                    builder.invoke(null, "foo", null));
        }

        @Test
        @DisplayName("PlanColumnRef.invoke dispatches lag with Number")
        void planColumnRefInvokeLag() {
            PlanColumnRef ref = new PlanColumnRef(basePlan("QM"), "amount");
            Object result = ref.invoke(null, "lag", new Object[]{3});
            assertInstanceOf(WindowColumnBuilder.class, result);
        }

        @Test
        @DisplayName("PlanColumnRef.invoke dispatches lead without args")
        void planColumnRefInvokeLead() {
            PlanColumnRef ref = new PlanColumnRef(basePlan("QM"), "amount");
            Object result = ref.invoke(null, "lead", null);
            assertInstanceOf(WindowColumnBuilder.class, result);
        }

        @Test
        @DisplayName("AggregateColumn.invoke dispatches over()")
        void aggregateColumnInvokeOver() {
            PlanColumnRef ref = new PlanColumnRef(basePlan("QM"), "amount");
            AggregateColumn agg = ref.sum();
            Object result = agg.invoke(null, "over", new Object[]{Map.of("orderBy", List.of("date"))});
            assertInstanceOf(WindowColumn.class, result);
        }

        @Test
        @DisplayName("fluentSelect accepts WindowColumn in projection")
        void fluentSelectWithWindowColumn() {
            QueryPlan plan = basePlan("QM");
            PlanColumnRef ref = new PlanColumnRef(plan, "amount");
            ProjectedColumn pc = ref.sum().over(Map.of("partitionBy", List.of("pid"))).as("runTotal");
            DerivedQueryPlan result = plan.fluentSelect(pc);
            assertEquals(1, result.columns().size());
            ProjectedColumn pcRes = (ProjectedColumn) result.columns().get(0);
            assertTrue(pcRes.toColumnExpr().contains("OVER"));
        }
    }

    // ==================================================================
    // PropertyHolder: dynamic field access on QueryPlan
    // ==================================================================

    @Nested
    @DisplayName("PropertyHolder (field access)")
    class PropertyHolderTests {

        @Test
        @DisplayName("getProperty returns PlanColumnRef")
        void getPropertyReturnsPlanColumnRef() {
            QueryPlan plan = basePlan("SaleOrderQM");
            Object result = plan.getProperty("partnerId");
            assertInstanceOf(PlanColumnRef.class, result);
            PlanColumnRef ref = (PlanColumnRef) result;
            assertEquals("partnerId", ref.name());
            assertSame(plan, ref.plan());
        }

        @Test
        @DisplayName("getProperty for 'class' returns NO_MATCH")
        void classPropertySkipped() {
            QueryPlan plan = basePlan("QM");
            assertSame(com.foggyframework.fsscript.parser.spi.PropertyHolder.NO_MATCH,
                    plan.getProperty("class"));
        }
    }

    // ==================================================================
    // Fluent select / groupBy / where / orderBy / limit / offset
    // ==================================================================

    @Nested
    @DisplayName("Fluent builder methods")
    class FluentBuilderTests {

        @Test
        @DisplayName("fluentSelect with PlanColumnRef and ProjectedColumn")
        void fluentSelectMixed() {
            QueryPlan sales = basePlan("SaleOrderQM");
            PlanColumnRef partnerId = new PlanColumnRef(sales, "partnerId");
            ProjectedColumn totalAmount = new PlanColumnRef(sales, "amountTotal").sum().as("totalAmount");

            DerivedQueryPlan result = sales.fluentSelect(partnerId, totalAmount);
            assertEquals(2, result.columns().size());
            List<String> exprs = toExprList(result.columns());
            assertEquals("partnerId", exprs.get(0));
            assertEquals("SUM(amountTotal) AS totalAmount", exprs.get(1));
            assertSame(sales, result.source());
        }

        @Test
        @DisplayName("fluentSelect rejects duplicate aliases")
        void fluentSelectRejectsDuplicates() {
            QueryPlan sales = basePlan("QM");
            PlanColumnRef ref1 = new PlanColumnRef(sales, "name");
            PlanColumnRef ref2 = new PlanColumnRef(sales, "name");
            assertThrows(IllegalArgumentException.class, () -> sales.fluentSelect(ref1, ref2));
        }

        @Test
        @DisplayName("fluentGroupBy with PlanColumnRef")
        void fluentGroupBy() {
            QueryPlan sales = basePlan("QM");
            PlanColumnRef partnerId = new PlanColumnRef(sales, "partnerId");
            DerivedQueryPlan result = sales.fluentGroupBy(partnerId);
            assertEquals(List.of("partnerId"), result.groupBy());
        }

        @Test
        @DisplayName("fluentWhere with slice")
        void fluentWhere() {
            QueryPlan sales = basePlan("QM");
            List<Object> slice = List.of(Map.of("field", "status", "op", "=", "value", "done"));
            DerivedQueryPlan result = sales.fluentWhere(slice);
            assertEquals(1, result.slice().size());
        }

        @Test
        @DisplayName("fluentOrderBy with string aliases")
        void fluentOrderBy() {
            QueryPlan plan = basePlan("QM");
            DerivedQueryPlan result = plan.fluentOrderBy("-totalAmount", "name");
            assertEquals(List.of("-totalAmount", "name"), result.orderBy());
        }

        @Test
        @DisplayName("fluentLimit and fluentOffset")
        void fluentLimitOffset() {
            QueryPlan plan = basePlan("QM");
            DerivedQueryPlan limited = plan.fluentLimit(10);
            assertEquals(10, limited.limit());

            DerivedQueryPlan offset = plan.fluentOffset(20);
            assertEquals(20, offset.start());
        }
    }

    // ==================================================================
    // ComposeJoinBuilder and JoinPlan.and()
    // ==================================================================

    @Nested
    @DisplayName("Join Builder")
    class JoinBuilderTests {

        @Test
        @DisplayName("leftJoin → on() → JoinPlan")
        void leftJoinOn() {
            QueryPlan customers = basePlan("CustomerQM");
            QueryPlan orders = basePlan("OrderQM");

            PlanColumnRef custId = new PlanColumnRef(customers, "id");
            PlanColumnRef orderPartnerId = new PlanColumnRef(orders, "partnerId");

            ComposeJoinBuilder builder = customers.leftJoin(orders);
            JoinPlan joinPlan = builder.on(custId, orderPartnerId);

            assertSame(customers, joinPlan.left());
            assertSame(orders, joinPlan.right());
            assertEquals(JoinType.LEFT, joinPlan.type());
            assertEquals(1, joinPlan.on().size());
            assertEquals("id", joinPlan.on().get(0).left());
        }

        @Test
        @DisplayName("on() + and() produces 2 conditions")
        void onAndCompound() {
            QueryPlan customers = basePlan("CustomerQM");
            QueryPlan orders = basePlan("OrderQM");

            PlanColumnRef custId = new PlanColumnRef(customers, "id");
            PlanColumnRef orderPartnerId = new PlanColumnRef(orders, "partnerId");
            PlanColumnRef custCompany = new PlanColumnRef(customers, "companyId");
            PlanColumnRef orderCompany = new PlanColumnRef(orders, "companyId");

            JoinPlan joinPlan = customers.innerJoin(orders)
                    .on(custId, orderPartnerId)
                    .and(custCompany, orderCompany);

            assertEquals(2, joinPlan.on().size());
            assertEquals("id", joinPlan.on().get(0).left());
            assertEquals("companyId", joinPlan.on().get(1).left());
        }

        @Test
        @DisplayName("all join types available")
        void allJoinTypes() {
            QueryPlan a = basePlan("A");
            QueryPlan b = basePlan("B");
            assertInstanceOf(ComposeJoinBuilder.class, a.leftJoin(b));
            assertInstanceOf(ComposeJoinBuilder.class, a.innerJoin(b));
            assertInstanceOf(ComposeJoinBuilder.class, a.rightJoin(b));
            assertInstanceOf(ComposeJoinBuilder.class, a.fullJoin(b));
        }
    }

    // ==================================================================
    // Full chain: simulating CTE manual §3.1
    // ==================================================================

    @Nested
    @DisplayName("End-to-end chain (AST only)")
    class EndToEndTests {

        @Test
        @DisplayName("§3.1 two-stage aggregation builds correct AST")
        void twoStageAggregation() {
            // Stage 1: SaleOrder → groupBy(partnerId) → select(partnerId, sum(amountTotal) as totalAmount)
            BaseModelPlan sales = basePlan("OdooSaleOrderModel");
            PlanColumnRef partnerId = new PlanColumnRef(sales, "partnerId");
            PlanColumnRef amountTotal = new PlanColumnRef(sales, "amountTotal");

            DerivedQueryPlan orderSummary = sales
                    .fluentGroupBy(partnerId)
                    .fluentSelect(partnerId, amountTotal.sum().as("totalAmount"));

            assertEquals(List.of("partnerId", "SUM(amountTotal) AS totalAmount"),
                    toExprList(orderSummary.columns()));

            // Stage 2: where(totalAmount > 100000) → select(count(partnerId) as premiumCount)
            PlanColumnRef summaryPartnerId = new PlanColumnRef(orderSummary, "partnerId");
            DerivedQueryPlan result = orderSummary
                    .fluentWhere(List.of(Map.of("field", "totalAmount", "op", ">", "value", 100000)))
                    .fluentSelect(summaryPartnerId.count().as("premiumCustomerCount"));

            assertEquals(List.of("COUNT(partnerId) AS premiumCustomerCount"),
                    toExprList(result.columns()));
        }

        @Test
        @DisplayName("multi-table JOIN builds correct AST")
        void multiTableJoin() {
            BaseModelPlan customers = basePlan("CustomerQM");
            BaseModelPlan orders = basePlan("OrderQM");

            PlanColumnRef custId = new PlanColumnRef(customers, "id");
            PlanColumnRef ordPartnerId = new PlanColumnRef(orders, "partnerId");
            PlanColumnRef ordAmount = new PlanColumnRef(orders, "amountTotal");

            // Pre-aggregate orders
            DerivedQueryPlan groupedOrders = orders
                    .fluentGroupBy(ordPartnerId)
                    .fluentSelect(ordPartnerId, ordAmount.sum().as("totalSales"));

            // Join
            PlanColumnRef grpPartnerId = new PlanColumnRef(groupedOrders, "partnerId");
            JoinPlan joined = customers.leftJoin(groupedOrders)
                    .on(custId, grpPartnerId);

            assertSame(customers, joined.left());
            assertEquals(JoinType.LEFT, joined.type());
            assertEquals(1, joined.on().size());
        }
    }

    // ==================================================================
    // QueryFactory
    // ==================================================================

    @Nested
    @DisplayName("QueryFactory")
    class QueryFactoryTests {

        @Test
        @DisplayName("from() creates BaseModelPlan")
        void fromCreatesBaseModelPlan() {
            Object result = QueryFactory.INSTANCE.invoke(null, "from",
                    new Object[]{"OdooSaleOrderModel"});
            assertInstanceOf(BaseModelPlan.class, result);
            assertEquals("OdooSaleOrderModel", ((BaseModelPlan) result).model());
        }

        @Test
        @DisplayName("from() with null model throws")
        void fromNullModelThrows() {
            assertThrows(IllegalArgumentException.class, () ->
                    QueryFactory.INSTANCE.invoke(null, "from", new Object[]{null}));
        }

        @Test
        @DisplayName("unsupported method throws")
        void unsupportedMethodThrows() {
            assertThrows(IllegalArgumentException.class, () ->
                    QueryFactory.INSTANCE.invoke(null, "delete", new Object[]{}));
        }
    }
}
