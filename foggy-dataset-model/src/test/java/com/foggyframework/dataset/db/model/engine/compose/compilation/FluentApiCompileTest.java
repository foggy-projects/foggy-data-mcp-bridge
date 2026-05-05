package com.foggyframework.dataset.db.model.engine.compose.compilation;

import com.foggyframework.dataset.db.model.engine.compose.ComposedSql;
import com.foggyframework.dataset.db.model.engine.compose.plan.*;
import com.foggyframework.dataset.db.model.engine.compose.security.ModelBinding;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * E2E integration tests for the OO fluent API through the SQL compiler.
 *
 * <p>These tests exercise the full chain:
 * {@code Query.from → fluent chain → ComposeSqlCompiler → SQL output}.</p>
 *
 * <p>Uses the same {@link CompileTestHelpers.FakeSemanticService} as the M6
 * compilation tests — no DB required.</p>
 *
 * @since 8.2.0.beta
 */
@DisplayName("Fluent API → SQL Compiler E2E")
class FluentApiCompileTest {

    private CompileTestHelpers.FakeSemanticService semanticService;

    @BeforeEach
    void setUp() {
        semanticService = new CompileTestHelpers.FakeSemanticService();
    }

    private ComposedSql compile(QueryPlan plan, String dialect) {
        // Use a simple binding for all models referenced in tests.
        Map<String, ModelBinding> bindings = Map.of(
                "SaleOrderQM", CompileTestHelpers.emptyBinding(),
                "CustomerQM", CompileTestHelpers.emptyBinding(),
                "InvoiceQM", CompileTestHelpers.emptyBinding()
        );
        return ComposeSqlCompiler.compilePlanToSql(plan,
                CompileTestHelpers.context(CompileTestHelpers.resolverFor(bindings)),
                ComposeSqlCompiler.CompileOptions.builder()
                        .semanticService(semanticService)
                        .bindings(bindings)
                        .dialect(dialect)
                        .build());
    }

    // ==================================================================
    // §3.1 — Two-stage aggregation (CTE manual reference scenario)
    // ==================================================================

    @Nested
    @DisplayName("§3.1 Two-stage aggregation")
    class TwoStageAggregation {

        @Test
        @DisplayName("groupBy → select → orderBy generates correct outer SQL (mysql8/CTE)")
        void twoStageAggregation_mysql8() {
            semanticService.stub("SaleOrderQM",
                    "SELECT partner_id, amount_total FROM sale_order WHERE state='done'",
                    /* no params */new Object[0]);

            // Build the fluent chain
            BaseModelPlan sales = BaseModelPlan.builder()
                    .model("SaleOrderQM")
                    .columns(List.of("partnerId", "amountTotal"))
                    .build();

            PlanColumnRef partnerId = new PlanColumnRef(sales, "partnerId");
            PlanColumnRef amountTotal = new PlanColumnRef(sales, "amountTotal");

            // Stage 1: groupBy + select (produces SUM(amountTotal) AS totalAmount)
            DerivedQueryPlan grouped = sales
                    .fluentGroupBy(partnerId)
                    .fluentSelect(partnerId, amountTotal.sum().as("totalAmount"));

            // Stage 2: orderBy + limit
            DerivedQueryPlan result = grouped
                    .fluentOrderBy("-totalAmount")
                    .fluentLimit(20);

            ComposedSql sql = compile(result, "mysql8");
            assertNotNull(sql);
            assertNotNull(sql.getSql());
            // The outermost SQL should contain ORDER BY and LIMIT
            String sqlStr = sql.getSql().toUpperCase();
            assertTrue(sqlStr.contains("ORDER BY"), "Should contain ORDER BY");
            assertTrue(sqlStr.contains("LIMIT"), "Should contain LIMIT 20");
            assertTrue(sql.getSql().contains("ORDER BY totalAmount DESC"),
                    "Shorthand '-totalAmount' should render as ORDER BY totalAmount DESC. Got: "
                            + sql.getSql());
        }

        @Test
        @DisplayName("outer CTE orderBy +field renders ASC instead of raw +field")
        void twoStageAggregation_orderByPlusShorthand_mysql8() {
            semanticService.stub("SaleOrderQM",
                    "SELECT partner_id, amount_total FROM sale_order WHERE state='done'");

            BaseModelPlan sales = BaseModelPlan.builder()
                    .model("SaleOrderQM")
                    .columns(List.of("partnerId", "amountTotal"))
                    .build();

            PlanColumnRef partnerId = new PlanColumnRef(sales, "partnerId");
            PlanColumnRef amountTotal = new PlanColumnRef(sales, "amountTotal");

            DerivedQueryPlan grouped = sales
                    .fluentGroupBy(partnerId)
                    .fluentSelect(partnerId, amountTotal.sum().as("totalAmount"));

            DerivedQueryPlan result = grouped
                    .fluentOrderBy("+totalAmount")
                    .fluentLimit(20);

            ComposedSql sql = compile(result, "mysql8");
            assertNotNull(sql);
            assertTrue(sql.getSql().contains("ORDER BY totalAmount ASC"),
                    "Shorthand '+totalAmount' should render as ORDER BY totalAmount ASC. Got: "
                            + sql.getSql());
            assertFalse(sql.getSql().contains("+totalAmount"));
        }

        @Test
        @DisplayName("groupBy → select → where (derived WHERE = outer filter)")
        void derivedWhere() {
            semanticService.stub("SaleOrderQM",
                    "SELECT partner_id, amount_total FROM sale_order");

            BaseModelPlan sales = BaseModelPlan.builder()
                    .model("SaleOrderQM")
                    .columns(List.of("partnerId", "amountTotal"))
                    .build();

            PlanColumnRef partnerId = new PlanColumnRef(sales, "partnerId");
            PlanColumnRef amountTotal = new PlanColumnRef(sales, "amountTotal");

            DerivedQueryPlan grouped = sales
                    .fluentGroupBy(partnerId)
                    .fluentSelect(partnerId, amountTotal.sum().as("totalAmount"));

            // Derived WHERE = filter on the aggregated output
            DerivedQueryPlan filtered = grouped
                    .fluentWhere(List.of(Map.of("field", "totalAmount", "op", ">", "value", 100000)));

            ComposedSql sql = compile(filtered, "mysql8");
            assertNotNull(sql);
            String sqlStr = sql.getSql().toUpperCase();
            assertTrue(sqlStr.contains("WHERE"), "Should contain WHERE clause for derived filter");
            // The WHERE should be on the outer query, not inside the inner CTE
        }
    }

    // ==================================================================
    // §4 — JOIN with fluent API
    // ==================================================================

    @Nested
    @DisplayName("§4 Fluent JOIN compilation")
    class FluentJoinCompilation {

        @Test
        @DisplayName("leftJoin → on produces correct JOIN SQL (mysql8/CTE)")
        void leftJoinCompilation_mysql8() {
            semanticService
                    .stub("CustomerQM", "SELECT id, name FROM res_partner")
                    .stub("SaleOrderQM", "SELECT partner_id, amount_total FROM sale_order");

            BaseModelPlan customers = CompileTestHelpers.base("CustomerQM", "id", "name");
            BaseModelPlan orders = CompileTestHelpers.base("SaleOrderQM", "partnerId", "amountTotal");

            PlanColumnRef custId = new PlanColumnRef(customers, "id");
            PlanColumnRef ordPartnerId = new PlanColumnRef(orders, "partnerId");

            JoinPlan joined = customers.leftJoin(orders)
                    .on(custId, ordPartnerId);

            ComposedSql sql = compile(joined, "mysql8");
            assertNotNull(sql);
            String sqlStr = sql.getSql().toUpperCase();
            assertTrue(sqlStr.contains("LEFT JOIN"), "Should contain LEFT JOIN");
            assertTrue(sqlStr.contains(" ON "), "Should contain ON clause");
        }

        @Test
        @DisplayName("leftJoin → on → and produces compound ON (mysql/subquery)")
        void compoundJoinCondition_subquery() {
            semanticService
                    .stub("CustomerQM", "SELECT id, name, company_id FROM res_partner")
                    .stub("SaleOrderQM", "SELECT partner_id, amount_total, company_id FROM sale_order");

            BaseModelPlan customers = CompileTestHelpers.base("CustomerQM", "id", "name", "companyId");
            BaseModelPlan orders = CompileTestHelpers.base("SaleOrderQM", "partnerId", "amountTotal", "companyId");

            PlanColumnRef custId = new PlanColumnRef(customers, "id");
            PlanColumnRef ordPartnerId = new PlanColumnRef(orders, "partnerId");
            PlanColumnRef custCompany = new PlanColumnRef(customers, "companyId");
            PlanColumnRef ordCompany = new PlanColumnRef(orders, "companyId");

            JoinPlan joined = customers.innerJoin(orders)
                    .on(custId, ordPartnerId)
                    .and(custCompany, ordCompany);

            ComposedSql sql = compile(joined, "mysql");
            assertNotNull(sql);
            String sqlStr = sql.getSql().toUpperCase();
            assertTrue(sqlStr.contains("INNER JOIN"), "Should contain INNER JOIN");
            assertTrue(sqlStr.contains(" AND "), "Should contain AND for compound condition");
        }
    }

    // ==================================================================
    // §3 — Intermediate stages (empty columns)
    // ==================================================================

    @Nested
    @DisplayName("Intermediate fluent stages")
    class IntermediateStages {

        @Test
        @DisplayName("groupBy-only (no select) compiles with SELECT *")
        void groupByOnlySelectStar() {
            semanticService.stub("SaleOrderQM",
                    "SELECT partner_id, amount_total FROM sale_order");

            BaseModelPlan sales = CompileTestHelpers.base("SaleOrderQM", "partnerId", "amountTotal");
            PlanColumnRef partnerId = new PlanColumnRef(sales, "partnerId");

            // Only groupBy, no select — columns is empty → should emit SELECT *
            DerivedQueryPlan result = sales.fluentGroupBy(partnerId);

            ComposedSql sql = compile(result, "mysql8");
            assertNotNull(sql);
            String sqlStr = sql.getSql().toUpperCase();
            assertTrue(sqlStr.contains("GROUP BY"), "Should contain GROUP BY");
            assertTrue(sqlStr.contains("*"), "Empty columns should produce SELECT *");
        }

        @Test
        @DisplayName("where-only stage compiles with WHERE + SELECT *")
        void whereOnlyStage() {
            semanticService.stub("SaleOrderQM",
                    "SELECT partner_id, amount_total FROM sale_order");

            BaseModelPlan sales = CompileTestHelpers.base("SaleOrderQM", "partnerId", "amountTotal");
            DerivedQueryPlan filtered = sales
                    .fluentWhere(List.of(Map.of("field", "status", "op", "=", "value", "done")));

            ComposedSql sql = compile(filtered, "mysql8");
            assertNotNull(sql);
            String sqlStr = sql.getSql().toUpperCase();
            assertTrue(sqlStr.contains("WHERE"), "Should contain WHERE");
        }

        @Test
        @DisplayName("orderBy + limit without select")
        void orderByLimitWithoutSelect() {
            semanticService.stub("SaleOrderQM",
                    "SELECT partner_id, amount_total FROM sale_order");

            BaseModelPlan sales = CompileTestHelpers.base("SaleOrderQM", "partnerId", "amountTotal");
            DerivedQueryPlan result = sales
                    .fluentOrderBy("-amountTotal")
                    .fluentLimit(10);

            ComposedSql sql = compile(result, "mysql");
            assertNotNull(sql);
            String sqlStr = sql.getSql().toUpperCase();
            assertTrue(sqlStr.contains("ORDER BY"), "Should contain ORDER BY");
            assertTrue(sqlStr.contains("LIMIT"), "Should contain LIMIT");
        }
    }

    // ==================================================================
    // QueryFactory (Query.from entry point)
    // ==================================================================

    @Nested
    @DisplayName("QueryFactory → compile")
    class QueryFactoryCompile {

        @Test
        @DisplayName("QueryFactory.from creates compilable BaseModelPlan")
        void queryFactoryProducesCompilablePlan() {
            semanticService.stub("SaleOrderQM",
                    "SELECT partner_id, amount_total FROM sale_order");

            // Simulate Query.from("SaleOrderQM") — creates BaseModelPlan with empty columns
            Object plan = QueryFactory.INSTANCE.invoke(null, "from", new Object[]{"SaleOrderQM"});
            assertInstanceOf(BaseModelPlan.class, plan);

            // Add select to give it columns (otherwise the base model has empty columns)
            BaseModelPlan basePlan = (BaseModelPlan) plan;
            PlanColumnRef partnerId = new PlanColumnRef(basePlan, "partnerId");
            PlanColumnRef amountTotal = new PlanColumnRef(basePlan, "amountTotal");

            DerivedQueryPlan result = basePlan
                    .fluentSelect(partnerId, amountTotal.sum().as("totalAmount"));

            ComposedSql sql = compile(result, "mysql8");
            assertNotNull(sql);
            assertFalse(sql.getSql().isEmpty());
        }
    }

    // ==================================================================
    // ProjectedColumn expression rendering through compiler
    // ==================================================================

    @Nested
    @DisplayName("ProjectedColumn expressions in SQL")
    class ProjectedColumnExpressions {

        @Test
        @DisplayName("select with caption generates $caption AS alias")
        void captionInColumnExpression() {
            semanticService.stub("SaleOrderQM",
                    "SELECT partner_id, amount_total FROM sale_order");

            BaseModelPlan sales = CompileTestHelpers.base("SaleOrderQM", "partnerId", "amountTotal");
            PlanColumnRef amountTotal = new PlanColumnRef(sales, "amountTotal");

            DerivedQueryPlan result = sales
                    .fluentSelect(amountTotal.sum().as("totalAmount", "总金额"));

            ComposedSql sql = compile(result, "mysql8");
            assertNotNull(sql);
            // The column expression should contain the caption marker
            assertTrue(sql.getSql().contains("$总金额"),
                    "Should contain caption marker $总金额 in SELECT");
            assertTrue(sql.getSql().contains("AS totalAmount"),
                    "Should contain AS totalAmount alias");
        }
    }

    // ==================================================================
    // WindowFrame — frame clause rendering through compiler
    // ==================================================================

    @Nested
    @DisplayName("WindowFrame in WindowColumn")
    class WindowFrameCompilation {

        @Test
        @DisplayName("rolling window with frame renders ROWS BETWEEN N PRECEDING AND CURRENT ROW")
        void rollingWindowFrame() {
            semanticService.stub("SaleOrderQM",
                    "SELECT partner_id, amount_total FROM sale_order");

            BaseModelPlan sales = CompileTestHelpers.base("SaleOrderQM", "partnerId", "amountTotal");
            PlanColumnRef amountTotal = new PlanColumnRef(sales, "amountTotal");
            PlanColumnRef partnerId = new PlanColumnRef(sales, "partnerId");

            // Build a rolling 7-day window: SUM(amountTotal) OVER (ORDER BY partnerId ROWS BETWEEN 6 PRECEDING AND CURRENT ROW)
            OverClause over = new OverClause(
                    List.of(),
                    List.of("partnerId"),
                    WindowFrame.rollingRows(7));
            WindowColumn winCol = new WindowColumn("SUM", amountTotal, List.of(), over);

            // Verify the expression string
            String expr = winCol.toColumnExpr();
            assertTrue(expr.contains("ROWS BETWEEN 6 PRECEDING AND CURRENT ROW"),
                    "Should contain frame clause. Got: " + expr);
            assertTrue(expr.contains("ORDER BY partnerId ASC"),
                    "Should contain ORDER BY. Got: " + expr);

            // Verify through full compile chain
            DerivedQueryPlan result = sales.fluentSelect(
                    partnerId,
                    winCol.as("rolling7Sum"));

            ComposedSql sql = compile(result, "mysql8");
            assertNotNull(sql);
            String sqlStr = sql.getSql();
            assertTrue(sqlStr.contains("ROWS BETWEEN 6 PRECEDING AND CURRENT ROW"),
                    "Compiled SQL should contain frame clause. Got: " + sqlStr);
        }

        @Test
        @DisplayName("cumulative window with UNBOUNDED PRECEDING renders correctly")
        void cumulativeWindowFrame() {
            WindowFrame frame = WindowFrame.cumulativeRows();
            assertEquals("ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW", frame.toSql());

            // Verify through OverClause → WindowColumn
            OverClause over = new OverClause(List.of("category"), List.of("dateKey"), frame);
            WindowColumn winCol = new WindowColumn("SUM", new PlanColumnRef(null, "amount"), List.of(), over);
            String expr = winCol.toColumnExpr();
            assertTrue(expr.contains("PARTITION BY category"),
                    "Should contain PARTITION BY. Got: " + expr);
            assertTrue(expr.contains("ORDER BY dateKey ASC"),
                    "Should contain ORDER BY. Got: " + expr);
            assertTrue(expr.contains("ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW"),
                    "Should contain cumulative frame. Got: " + expr);
        }

        @Test
        @DisplayName("OverClause without frame renders OVER() without frame clause")
        void noFrameBackwardCompat() {
            OverClause over = new OverClause(List.of("category"), List.of("dateKey"));
            WindowColumn winCol = new WindowColumn("ROW_NUMBER", null, List.of(), over);
            String expr = winCol.toColumnExpr();
            assertFalse(expr.contains("ROWS"),
                    "No frame → no ROWS clause. Got: " + expr);
            assertFalse(expr.contains("BETWEEN"),
                    "No frame → no BETWEEN clause. Got: " + expr);
            assertTrue(expr.contains("PARTITION BY category"),
                    "Should still have partition. Got: " + expr);
        }

        @Test
        @DisplayName("WindowFrame.fromMapOrString parses string format")
        void parseStringFormat() {
            WindowFrame frame = WindowFrame.fromMapOrString("ROWS BETWEEN 29 PRECEDING AND CURRENT ROW");
            assertNotNull(frame);
            assertEquals(WindowFrame.Unit.ROWS, frame.unit());
            assertInstanceOf(WindowFrame.Bound.Preceding.class, frame.startBound());
            assertEquals(29, ((WindowFrame.Bound.Preceding) frame.startBound()).offset());
            assertInstanceOf(WindowFrame.Bound.CurrentRow.class, frame.endBound());
        }

        @Test
        @DisplayName("WindowFrame.fromMapOrString parses map format")
        void parseMapFormat() {
            WindowFrame frame = WindowFrame.fromMapOrString(
                    Map.of("unit", "rows", "start", -6, "end", 0));
            assertNotNull(frame);
            assertEquals("ROWS BETWEEN 6 PRECEDING AND CURRENT ROW", frame.toSql());
        }
    }
}
