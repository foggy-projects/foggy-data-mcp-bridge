package com.foggyframework.dataset.db.model.engine.compose.runtime;

import com.foggyframework.dataset.db.model.engine.compose.ComposedSql;
import com.foggyframework.dataset.db.model.engine.compose.SqlGenerationResult;
import com.foggyframework.dataset.db.model.engine.compose.context.ComposeQueryContext;
import com.foggyframework.dataset.db.model.engine.compose.context.Principal;
import com.foggyframework.dataset.db.model.engine.compose.sandbox.ComposeSandboxViolationException;
import com.foggyframework.dataset.db.model.engine.compose.security.AuthorityResolution;
import com.foggyframework.dataset.db.model.engine.compose.security.AuthorityResolver;
import com.foggyframework.dataset.db.model.engine.compose.security.ModelBinding;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticQueryRequest;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticQueryResponse;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticRequestContext;
import com.foggyframework.dataset.db.model.semantic.service.SemanticQueryServiceV3;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("ComposeScriptService · restricted compose service boundary")
class ComposeScriptServiceTest {

    private static final String PLAN_SCRIPT = """
            let base = dsl({model: 'SaleOrderQM', columns: ['amount']});
            return { plans: { summary: base }, metadata: { title: 'orders' } };
            """;

    private static final String EMPTY_WRAPPER_PLAN_SCRIPT = """
            let base = dsl({model: 'SaleOrderQM', columns: ['amount']});
            let wrapped = base.query({columns: []});
            return { plans: { summary: wrapped }, metadata: { title: 'orders' } };
            """;

    private static final String JOIN_ON_MAP_ARRAY_SCRIPT = """
            const prior = dsl({
              model: 'wwi_sales_analysis',
              columns: [
                'customer$id as customer_id',
                'customer$caption as customer_name',
                'sum(totalIncludingTax) as revenue_2015'
              ],
              groupBy: ['customer$id', 'customer$caption']
            });

            const current = dsl({
              model: 'wwi_sales_analysis',
              columns: [
                'customer$id as customer_id_2016',
                'customer$caption as customer_name_2016',
                'sum(totalIncludingTax) as revenue_2016'
              ],
              groupBy: ['customer$id', 'customer$caption']
            });

            const joined = prior.join(current, 'inner', [
              { left: 'customer_id', op: '=', right: 'customer_id_2016' }
            ]);

            const result = joined.query({
              columns: [
                'customer_id',
                'customer_name',
                'revenue_2015',
                'revenue_2016',
                'revenue_2016 - revenue_2015 as delta'
              ],
              orderBy: [{ field: 'delta', dir: 'ASC' }],
              limit: 10
            });

            return { plans: result };
            """;

    private static final String JOIN_ON_STRING_SCRIPT = """
            const prior = dsl({
              model: 'wwi_sales_analysis',
              columns: ['customer$id as customer_id'],
              groupBy: ['customer$id']
            });
            const current = dsl({
              model: 'wwi_sales_analysis',
              columns: ['customer$id as customer_id_2016'],
              groupBy: ['customer$id']
            });
            return { plans: prior.join(current, 'inner', 'customer_id = customer_id_2016') };
            """;

    private static final String JOIN_ON_STRING_ARRAY_SCRIPT = """
            const prior = dsl({
              model: 'wwi_sales_analysis',
              columns: ['customer$id as customer_id'],
              groupBy: ['customer$id']
            });
            const current = dsl({
              model: 'wwi_sales_analysis',
              columns: ['customer$id as customer_id_2016'],
              groupBy: ['customer$id']
            });
            return { plans: prior.join(current, 'inner', ['customer_id = customer_id_2016']) };
            """;

    @Test
    @DisplayName("validate runs through restricted preview path without executing SQL")
    void validate_usesPreviewPathWithoutSqlExecution() {
        CountingSemanticService semanticService = new CountingSemanticService();

        ComposeScriptService.ComposeScriptResult result = ComposeScriptService.validate(
                PLAN_SCRIPT, ctx(), semanticService, "mysql");

        assertEquals(ComposeScriptService.Mode.VALIDATE, result.mode());
        assertTrue(result.valid());
        assertFalse(result.executed());
        assertEquals(1, semanticService.generateSqlCount.get());
        assertEquals(0, semanticService.executeSqlCount.get());
    }

    @Test
    @DisplayName("preview returns ComposedSql and does not execute SQL")
    void preview_returnsComposedSqlWithoutSqlExecution() {
        CountingSemanticService semanticService = new CountingSemanticService();

        ComposeScriptService.ComposeScriptResult result = ComposeScriptService.preview(
                PLAN_SCRIPT, ctx(), semanticService, "mysql");

        assertEquals(ComposeScriptService.Mode.PREVIEW, result.mode());
        assertFalse(result.executed());
        assertEquals(0, semanticService.executeSqlCount.get());

        @SuppressWarnings("unchecked")
        Map<String, Object> value = (Map<String, Object>) result.value();
        @SuppressWarnings("unchecked")
        Map<String, Object> plans = (Map<String, Object>) value.get("plans");
        ComposedSql sql = assertInstanceOf(ComposedSql.class, plans.get("summary"));
        assertTrue(sql.getSql().contains("SELECT amount FROM sale_order"));
    }

    @Test
    @DisplayName("preview normalizePlan=true removes empty script wrapper with SQL/params equivalence")
    void previewNormalizePlanOptInRemovesEmptyScriptWrapper() {
        CountingSemanticService baseService = new CountingSemanticService();
        ComposeScriptService.ComposeScriptResult baseResult = ComposeScriptService.preview(
                PLAN_SCRIPT, ctx(), baseService, "sqlite");

        CountingSemanticService normalizedService = new CountingSemanticService();
        ComposeScriptService.ComposeScriptResult normalizedResult = ComposeScriptService.run(
                ComposeScriptService.ComposeScriptRequest.builder()
                        .mode(ComposeScriptService.Mode.PREVIEW)
                        .script(EMPTY_WRAPPER_PLAN_SCRIPT)
                        .ctx(ctx())
                        .semanticService(normalizedService)
                        .dialect("sqlite")
                        .normalizePlan(true)
                        .build());

        ComposedSql baseSql = summarySql(baseResult);
        ComposedSql normalizedSql = summarySql(normalizedResult);
        assertEquals(baseSql.getSql(), normalizedSql.getSql());
        assertEquals(baseSql.getParams(), normalizedSql.getParams());
        assertEquals(1, normalizedService.generateSqlCount.get());
        assertEquals(0, normalizedService.executeSqlCount.get());
    }

    @Test
    @DisplayName("validate accepts documented join on Map-array shape with object orderBy")
    void validateAcceptsJoinOnMapArrayShape() {
        CountingSemanticService semanticService = new CountingSemanticService();

        ComposeScriptService.ComposeScriptResult result = ComposeScriptService.validate(
                JOIN_ON_MAP_ARRAY_SCRIPT, ctx(), semanticService, "sqlite");

        assertEquals(ComposeScriptService.Mode.VALIDATE, result.mode());
        assertTrue(result.valid());
        assertFalse(result.executed());
        @SuppressWarnings("unchecked")
        Map<String, Object> value = (Map<String, Object>) result.value();
        ComposedSql sql = assertInstanceOf(ComposedSql.class, value.get("plans"));
        assertTrue(sql.getSql().contains("JOIN"));
        assertTrue(sql.getSql().contains("customer_id"));
        assertTrue(sql.getSql().contains("ORDER BY"));
        assertTrue(sql.getSql().contains("delta"));
        assertEquals(2, semanticService.generateSqlCount.get());
        assertEquals(0, semanticService.executeSqlCount.get());
    }

    @Test
    @DisplayName("validate rejects join on string with schema message")
    void validateRejectsJoinOnStringWithSchemaMessage() {
        CountingSemanticService semanticService = new CountingSemanticService();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> ComposeScriptService.validate(JOIN_ON_STRING_SCRIPT, ctx(), semanticService, "sqlite"));

        assertTrue(ex.getMessage().contains("join.on must be an array of {left, op, right}"));
        assertFalse(ex.getMessage().contains("cannot be cast"));
        assertEquals(0, semanticService.generateSqlCount.get());
        assertEquals(0, semanticService.executeSqlCount.get());
    }

    @Test
    @DisplayName("validate rejects join on string array without internal class names")
    void validateRejectsJoinOnStringArrayWithSchemaMessage() {
        CountingSemanticService semanticService = new CountingSemanticService();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> ComposeScriptService.validate(JOIN_ON_STRING_ARRAY_SCRIPT, ctx(), semanticService, "sqlite"));

        assertTrue(ex.getMessage().contains("join.on[0] must be an object with left, op, and right"));
        assertFalse(ex.getMessage().contains("JoinPlan"));
        assertFalse(ex.getMessage().contains("cannot be cast"));
        assertEquals(0, semanticService.generateSqlCount.get());
        assertEquals(0, semanticService.executeSqlCount.get());
    }

    @Test
    @DisplayName("execute runs compiled CTE SQL through SemanticQueryServiceV3.executeSql")
    void execute_runsSqlExecution() {
        CountingSemanticService semanticService = new CountingSemanticService();

        ComposeScriptService.ComposeScriptResult result = ComposeScriptService.execute(
                PLAN_SCRIPT, ctx(), semanticService, "mysql");

        assertEquals(ComposeScriptService.Mode.EXECUTE, result.mode());
        assertTrue(result.executed());
        assertEquals(1, semanticService.generateSqlCount.get());
        assertEquals(1, semanticService.executeSqlCount.get());

        @SuppressWarnings("unchecked")
        Map<String, Object> value = (Map<String, Object>) result.value();
        @SuppressWarnings("unchecked")
        Map<String, Object> plans = (Map<String, Object>) value.get("plans");
        assertEquals(List.of(Map.of("amount", 100)), plans.get("summary"));
    }

    @Test
    @DisplayName("sandbox violations fail before SQL generation or execution")
    void sandboxViolation_failsClosedBeforeSql() {
        CountingSemanticService semanticService = new CountingSemanticService();

        assertThrows(ComposeSandboxViolationException.class, () -> ComposeScriptService.preview(
                "return eval('1');", ctx(), semanticService, "mysql"));
        assertEquals(0, semanticService.generateSqlCount.get());
        assertEquals(0, semanticService.executeSqlCount.get());
    }

    @Test
    @DisplayName("blank scripts are rejected at service boundary")
    void blankScript_rejectedAtBoundary() {
        assertThrows(IllegalArgumentException.class, () -> ComposeScriptService.execute(
                "  ", ctx(), new CountingSemanticService(), "mysql"));
    }

    private static ComposeQueryContext ctx() {
        AuthorityResolver resolver = request -> {
            Map<String, ModelBinding> bindings = new LinkedHashMap<>();
            for (String name : request.modelNames()) {
                bindings.put(name, ModelBinding.builder().build());
            }
            return AuthorityResolution.builder().bindings(bindings).build();
        };
        return ComposeQueryContext.builder()
                .principal(Principal.builder()
                        .userId("u1").tenantId("t1").roles(List.of("analyst")).build())
                .namespace("ns1")
                .traceId("trace-1")
                .authorityResolver(resolver)
                .build();
    }

    private static ComposedSql summarySql(ComposeScriptService.ComposeScriptResult result) {
        @SuppressWarnings("unchecked")
        Map<String, Object> value = (Map<String, Object>) result.value();
        @SuppressWarnings("unchecked")
        Map<String, Object> plans = (Map<String, Object>) value.get("plans");
        return assertInstanceOf(ComposedSql.class, plans.get("summary"));
    }

    private static final class CountingSemanticService implements SemanticQueryServiceV3 {
        private final AtomicInteger generateSqlCount = new AtomicInteger();
        private final AtomicInteger executeSqlCount = new AtomicInteger();

        @Override
        public SemanticQueryResponse queryModel(
                String model,
                SemanticQueryRequest req,
                String mode,
                SemanticRequestContext ctx) {
            throw new UnsupportedOperationException();
        }

        @Override
        public SemanticQueryResponse validateQuery(
                String model,
                SemanticQueryRequest req,
                SemanticRequestContext ctx) {
            throw new UnsupportedOperationException();
        }

        @Override
        public SqlGenerationResult generateSql(
                String model,
                SemanticQueryRequest req,
            SemanticRequestContext ctx) {
            generateSqlCount.incrementAndGet();
            return new SqlGenerationResult(
                    "SELECT amount FROM sale_order WHERE status = ?",
                    List.of("paid"),
                    null);
        }

        @Override
        public List<Map<String, Object>> executeSql(String sql, List<Object> params, String routeModel) {
            executeSqlCount.incrementAndGet();
            return List.of(Map.of("amount", 100));
        }
    }
}
