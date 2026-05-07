package com.foggyframework.dataset.db.model.engine.compose.compilation;

import com.foggyframework.dataset.db.model.engine.compose.ComposedSql;
import com.foggyframework.dataset.db.model.engine.compose.SqlGenerationResult;
import com.foggyframework.dataset.db.model.engine.compose.plan.BaseModelPlan;
import com.foggyframework.dataset.db.model.engine.compose.plan.JoinOn;
import com.foggyframework.dataset.db.model.engine.compose.plan.JoinType;
import com.foggyframework.dataset.db.model.engine.compose.plan.QueryPlan;
import com.foggyframework.dataset.db.model.engine.compose.security.ModelBinding;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Validates ComposePlanner's handling of flattened CTE stages during SQL assembly.
 */
@DisplayName("ComposePlanner CTE Flattening Test")
class ComposePlannerCteWrapTest {

    private ComposedSql compile(QueryPlan plan, CompileTestHelpers.FakeSemanticService svc,
                                Map<String, ModelBinding> bindings, String dialect) {
        return ComposeSqlCompiler.compilePlanToSql(
                plan,
                CompileTestHelpers.context(CompileTestHelpers.resolverFor(bindings)),
                ComposeSqlCompiler.CompileOptions.builder()
                        .semanticService(svc)
                        .bindings(bindings)
                        .dialect(dialect)
                        .build());
    }

    @Test
    @DisplayName("Single base model with multi-stage CTE (e.g., from Window CFs) flattens to sibling CTEs")
    void testSingleBaseModelCteFlattening() {
        CompileTestHelpers.FakeSemanticService svc = new CompileTestHelpers.FakeSemanticService();
        
        // Mock a structured CTE result from the Semantic Query Engine
        List<SqlGenerationResult.CteStage> stages = List.of(
            new SqlGenerationResult.CteStage("stage1", "SELECT id, category FROM products", java.util.Collections.emptyList())
        );
        String outerSql = "SELECT id, category, RANK() OVER(...) as rnk FROM stage1";
        
        svc.stubWithCtes("WindowModel", outerSql, stages);
        Map<String, ModelBinding> bindings = Map.of("WindowModel", CompileTestHelpers.emptyBinding());

        BaseModelPlan plan = CompileTestHelpers.base("WindowModel", "id", "category", "rnk");
        ComposedSql composed = compile(plan, svc, bindings, "postgres");
        
        String sql = composed.getSql();
        System.out.println("Single Base CTE SQL:\n" + sql);

        // Verification:
        // 1. Prerequisite CTE should be present as a sibling WITH clause.
        // 2. The main body CTE (cte_0) should read from the prerequisite CTE.
        assertTrue(sql.contains("WITH cte_0_stage1 AS (SELECT id, category FROM products)"), "Should contain prerequisite CTE block");
        assertTrue(sql.contains(",\ncte_0 AS (SELECT id, category, RANK() OVER(...) as rnk FROM cte_0_stage1)"), "cte_0 should be a sibling of stage1");
        assertTrue(sql.contains("SELECT cte_0.\"id\", cte_0.\"category\", cte_0.\"rnk\"") || sql.contains("SELECT cte_0.id, cte_0.category, cte_0.rnk"), "Outer query reads from cte_0");
        assertTrue(sql.contains("FROM cte_0"), "Outer query reads from cte_0");
        assertFalse(sql.matches("(?s).*WITH.*WITH.*"), "Nested WITH clauses are illegal");
    }

    @Test
    @DisplayName("Join with multi-stage CTE anchors correctly hoists prerequisite CTEs to the top")
    void testJoinWithMultiStageCte() {
        CompileTestHelpers.FakeSemanticService svc = new CompileTestHelpers.FakeSemanticService();
        
        // Left side: A normal single-stage base model
        svc.stub("NormalModel", "SELECT id, name FROM tableA");
        
        // Right side: A multi-stage base model (e.g., Window CFs)
        List<SqlGenerationResult.CteStage> stages = List.of(
            new SqlGenerationResult.CteStage("stage1_b", "SELECT id, amount FROM tableB", java.util.Collections.emptyList())
        );
        String outerSql = "SELECT id, amount, SUM(amount) OVER(...) as total FROM stage1_b";
        svc.stubWithCtes("WindowModelB", outerSql, stages);

        Map<String, ModelBinding> bindings = Map.of(
                "NormalModel", CompileTestHelpers.emptyBinding(),
                "WindowModelB", CompileTestHelpers.emptyBinding()
        );

        QueryPlan plan = CompileTestHelpers.base("NormalModel", "id", "name").join(
                CompileTestHelpers.base("WindowModelB", "id", "amount", "total"), 
                JoinType.INNER,
                List.of(JoinOn.of("id", "=", "id")));

        ComposedSql composed = compile(plan, svc, bindings, "postgres");
        
        String sql = composed.getSql();
        System.out.println("Join CTE SQL:\n" + sql);

        // Verification:
        // 1. The top-level WITH clause must start with the prerequisite CTE.
        assertTrue(sql.startsWith("WITH cte_1_stage1_b AS (SELECT id, amount FROM tableB),\ncte_0 AS (SELECT id, name FROM tableA)"), "Prerequisite CTEs must be hoisted before join CTEs");
        assertTrue(sql.contains(",\ncte_1 AS (SELECT id, amount, SUM(amount) OVER(...) as total FROM cte_1_stage1_b)"), "cte_1 should be a sibling of stage1_b");
        assertFalse(sql.matches("(?s).*WITH.*WITH.*"), "Nested WITH clauses are illegal");
    }
}
