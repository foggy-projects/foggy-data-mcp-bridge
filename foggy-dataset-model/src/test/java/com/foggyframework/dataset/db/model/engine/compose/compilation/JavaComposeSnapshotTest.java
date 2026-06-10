package com.foggyframework.dataset.db.model.engine.compose.compilation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.foggyframework.dataset.db.model.engine.compose.ComposedSql;
import com.foggyframework.dataset.db.model.engine.compose.SqlGenerationResult;
import com.foggyframework.dataset.db.model.engine.compose.context.ComposeQueryContext;
import com.foggyframework.dataset.db.model.engine.compose.context.Principal;
import com.foggyframework.dataset.db.model.engine.compose.plan.BaseModelPlan;
import com.foggyframework.dataset.db.model.engine.compose.plan.DerivedQueryPlan;
import com.foggyframework.dataset.db.model.engine.compose.plan.JoinOn;
import com.foggyframework.dataset.db.model.engine.compose.plan.JoinPlan;
import com.foggyframework.dataset.db.model.engine.compose.plan.PlanAliasSupport;
import com.foggyframework.dataset.db.model.engine.compose.plan.QueryPlan;
import com.foggyframework.dataset.db.model.engine.compose.plan.UnionPlan;
import com.foggyframework.dataset.db.model.engine.compose.security.AuthorityRequest;
import com.foggyframework.dataset.db.model.engine.compose.security.AuthorityResolution;
import com.foggyframework.dataset.db.model.engine.compose.security.AuthorityResolver;
import com.foggyframework.dataset.db.model.engine.compose.security.ModelBinding;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticQueryRequest;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticQueryResponse;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticRequestContext;
import com.foggyframework.dataset.db.model.semantic.service.SemanticQueryServiceV3;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Java-side producer for Python P0-3 compose-query neutral snapshot replay.
 *
 * <p>The JSON plan shape is intentionally engine-neutral and mirrors the
 * Python replay harness in
 * {@code tests/integration/test_java_compose_snapshot_parity.py}. This test
 * validates each snapshot case against Java's compiler before writing the
 * shared fixture into the Python repository.</p>
 */
@DisplayName("JavaComposeSnapshotTest · Python alignment P0-3")
class JavaComposeSnapshotTest {

    @Test
    @DisplayName("writes java_compose_snapshot_parity.json for Python replay")
    void shouldProduceComposeSnapshot() throws Exception {
        List<Map<String, Object>> cases = cases();
        for (Map<String, Object> c : cases) {
            assertJavaContract(c);
        }

        Map<String, Object> snapshot = ordered();
        snapshot.put("schemaVersion", 1);
        snapshot.put("feature", "composeQuery");
        snapshot.put("source", "JavaComposeSnapshotTest");
        snapshot.put("cases", cases);

        ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        Path pythonTarget = Path.of(
                "..",
                "..",
                "foggy-data-mcp-bridge-python",
                "tests",
                "fixtures",
                "java_compose_snapshot_parity.json"
        ).normalize();
        Files.createDirectories(pythonTarget.getParent());
        mapper.writeValue(pythonTarget.toFile(), snapshot);

        Path localCopy = Path.of("target", "parity", "java_compose_snapshot_parity.json");
        Files.createDirectories(localCopy.getParent());
        mapper.writeValue(localCopy.toFile(), snapshot);
        assertTrue(Files.exists(pythonTarget),
                "snapshot was not written: " + pythonTarget.toAbsolutePath());
    }

    private static void assertJavaContract(Map<String, Object> c) {
        @SuppressWarnings("unchecked")
        Map<String, Object> expected = (Map<String, Object>) c.get("expected");
        String expectedError = (String) expected.get("errorCode");
        if (expectedError != null) {
            try {
                QueryPlan plan = planFrom(c.get("plan"));
                compile(plan, (String) c.get("dialect"));
                throw new AssertionError("Expected Java compose case to fail: " + c.get("id"));
            } catch (RuntimeException ex) {
                assertTrue(ex.getMessage().toLowerCase(Locale.ROOT)
                                .contains(expectedError.toLowerCase(Locale.ROOT)),
                        "Expected error marker '" + expectedError + "' in: " + ex.getMessage());
            }
            return;
        }

        QueryPlan plan = planFrom(c.get("plan"));
        ComposedSql sql = compile(plan, (String) c.get("dialect"));
        @SuppressWarnings("unchecked")
        List<String> markers = (List<String>) expected.getOrDefault("sqlMarkers", List.of());
        for (String marker : markers) {
            assertTrue(sql.getSql().contains(marker),
                    "[" + c.get("id") + "] SQL marker missing: " + marker + "\n" + sql.getSql());
        }
        @SuppressWarnings("unchecked")
        List<String> forbidden = (List<String>) expected.getOrDefault("forbiddenSqlMarkers", List.of());
        for (String marker : forbidden) {
            assertFalse(sql.getSql().contains(marker),
                    "[" + c.get("id") + "] forbidden SQL marker present: " + marker + "\n" + sql.getSql());
        }
        @SuppressWarnings("unchecked")
        List<Object> params = (List<Object>) expected.get("params");
        if (params != null) {
            assertTrue(sql.getParams().equals(params),
                    "[" + c.get("id") + "] params mismatch, expected " + params + ", got "
                            + sql.getParams());
        }
    }

    private static ComposedSql compile(QueryPlan plan, String dialect) {
        return ComposeSqlCompiler.compilePlanToSql(
                plan,
                context(resolverFor(bindings())),
                ComposeSqlCompiler.CompileOptions.builder()
                        .semanticService(semanticService())
                        .bindings(bindings())
                        .dialect(dialect)
                        .build());
    }

    private static FakeSemanticService semanticService() {
        return new FakeSemanticService()
                .stub("FactSalesModel",
                        "SELECT order_status AS `orderStatus$caption`, sales_amount AS salesAmount "
                                + "FROM fact_sales")
                .stub("FactOrderModel",
                        "SELECT order_status AS `orderStatus$caption`, total_amount AS totalAmount "
                                + "FROM fact_order")
                .stub("FactPaymentModel",
                        "SELECT pay_method AS `payMethod$caption`, pay_amount AS payAmount "
                                + "FROM fact_payment");
    }

    private static Map<String, ModelBinding> bindings() {
        return Map.of(
                "FactSalesModel", ModelBinding.builder().build(),
                "FactOrderModel", ModelBinding.builder().build(),
                "FactPaymentModel", ModelBinding.builder().build());
    }

    private static ComposeQueryContext context(AuthorityResolver resolver) {
        return ComposeQueryContext.builder()
                .principal(Principal.builder()
                        .userId("snapshot-user")
                        .tenantId("demo")
                        .roles(List.of("analyst"))
                        .build())
                .namespace("demo")
                .traceId("java-compose-snapshot")
                .authorityResolver(resolver)
                .build();
    }

    private static AuthorityResolver resolverFor(Map<String, ModelBinding> bindings) {
        return new AuthorityResolver() {
            @Override
            public AuthorityResolution resolve(AuthorityRequest request) {
                Map<String, ModelBinding> matching = new LinkedHashMap<>();
                for (String name : request.modelNames()) {
                    ModelBinding b = bindings.get(name);
                    if (b != null) {
                        matching.put(name, b);
                    }
                }
                return AuthorityResolution.builder().bindings(matching).build();
            }
        };
    }

    private static final class FakeSemanticService implements SemanticQueryServiceV3 {
        private final Map<String, String> sqlByModel = new LinkedHashMap<>();

        FakeSemanticService stub(String model, String sql) {
            sqlByModel.put(model, sql);
            return this;
        }

        @Override
        public SqlGenerationResult generateSql(String model, SemanticQueryRequest request,
                                               SemanticRequestContext context) {
            String sql = sqlByModel.get(model);
            if (sql == null) {
                throw new RuntimeException("Model not found: " + model);
            }
            return new SqlGenerationResult(sql, List.of(), null, List.of());
        }

        @Override
        public List<Map<String, Object>> executeSql(String sql, List<Object> params,
                                                    String routeModel) {
            return List.of();
        }

        @Override
        public SemanticQueryResponse queryModel(String model, SemanticQueryRequest request,
                                                String mode, SemanticRequestContext context) {
            throw new UnsupportedOperationException("queryModel is not used by snapshot compile tests");
        }

        @Override
        public SemanticQueryResponse validateQuery(String model, SemanticQueryRequest request,
                                                   SemanticRequestContext context) {
            throw new UnsupportedOperationException("validateQuery is not used by snapshot compile tests");
        }
    }

    private static QueryPlan planFrom(Object raw) {
        return planFrom(raw, new LinkedHashMap<>());
    }

    private static QueryPlan planFrom(Object raw, Map<String, QueryPlan> reuseCache) {
        @SuppressWarnings("unchecked")
        Map<String, Object> node = (Map<String, Object>) raw;
        String reuseKey = (String) node.get("reuseKey");
        if (reuseKey != null && reuseCache.containsKey(reuseKey)) {
            QueryPlan reused = reuseCache.get(reuseKey);
            for (String alias : stringList(node.get("aliases"))) {
                PlanAliasSupport.bindAlias(reused, alias);
            }
            return reused;
        }
        String type = (String) node.get("type");
        QueryPlan plan;
        switch (type) {
            case "base":
                plan = BaseModelPlan.builder()
                        .model((String) node.get("model"))
                        .columns(list(node.get("columns")))
                        .slice(list(node.get("slice")))
                        .groupBy(stringList(node.get("groupBy")))
                        .orderBy(stringList(node.get("orderBy")))
                        .limit(integer(node.get("limit")))
                        .start(integer(node.get("start")))
                        .distinct(Boolean.TRUE.equals(node.get("distinct")))
                        .build();
                break;
            case "derived":
                plan = DerivedQueryPlan.builder()
                        .source(planFrom(node.get("source"), reuseCache))
                        .columns(list(node.get("columns")))
                        .slice(list(node.get("slice")))
                        .groupBy(stringList(node.get("groupBy")))
                        .orderBy(stringList(node.get("orderBy")))
                        .limit(integer(node.get("limit")))
                        .start(integer(node.get("start")))
                        .distinct(Boolean.TRUE.equals(node.get("distinct")))
                        .build();
                break;
            case "union":
                plan = UnionPlan.builder()
                        .left(planFrom(node.get("left"), reuseCache))
                        .right(planFrom(node.get("right"), reuseCache))
                        .all(Boolean.TRUE.equals(node.get("all")))
                        .build();
                break;
            case "join":
                plan = JoinPlan.builder()
                        .left(planFrom(node.get("left"), reuseCache))
                        .right(planFrom(node.get("right"), reuseCache))
                        .type((String) node.get("joinType"))
                        .on(joinOnList(node.get("on")))
                        .build();
                break;
            default:
                throw new IllegalArgumentException("Unknown snapshot plan type: " + type);
        }
        for (String alias : stringList(node.get("aliases"))) {
            PlanAliasSupport.bindAlias(plan, alias);
        }
        if (reuseKey != null) {
            reuseCache.put(reuseKey, plan);
        }
        return plan;
    }

    private static List<JoinOn> joinOnList(Object raw) {
        List<JoinOn> out = new ArrayList<>();
        for (Object item : list(raw)) {
            @SuppressWarnings("unchecked")
            Map<String, Object> m = (Map<String, Object>) item;
            out.add(JoinOn.of((String) m.get("left"),
                    (String) m.getOrDefault("op", "="),
                    (String) m.get("right")));
        }
        return out;
    }

    private static List<Object> list(Object raw) {
        if (raw == null) {
            return List.of();
        }
        @SuppressWarnings("unchecked")
        List<Object> out = (List<Object>) raw;
        return out;
    }

    private static List<String> stringList(Object raw) {
        if (raw == null) {
            return List.of();
        }
        @SuppressWarnings("unchecked")
        List<String> out = (List<String>) raw;
        return out;
    }

    private static Integer integer(Object raw) {
        return raw instanceof Number n ? n.intValue() : null;
    }

    private static List<Map<String, Object>> cases() {
        return List.of(
                caseOf(
                        "base-mysql8",
                        "mysql8",
                        base("FactSalesModel",
                                List.of("orderStatus$caption", "salesAmount"),
                                null, null, null),
                        expected(List.of("WITH ", "cte_0 AS", "salesAmount"), List.of(), List.of())),
                caseOf(
                        "derived-filter-order-limit-mysql8",
                        "mysql8",
                        derived(
                                base("FactSalesModel",
                                        List.of("orderStatus$caption", "salesAmount"),
                                        null, null, null),
                                List.of("orderStatus$caption", "salesAmount"),
                                List.of(filter("orderStatus$caption", "=", "COMPLETED")),
                                List.of("-salesAmount"),
                                5,
                                null),
                        expected(List.of("WITH ", "WHERE", "ORDER BY", "LIMIT"), List.of(), List.of("COMPLETED"))),
                caseOf(
                        "union-all-sales-orders-mysql8",
                        "mysql8",
                        union(
                                base("FactSalesModel",
                                        List.of("orderStatus$caption AS bucket", "salesAmount AS amount"),
                                        null, null, null),
                                base("FactOrderModel",
                                        List.of("orderStatus$caption AS bucket", "totalAmount AS amount"),
                                        null, null, null),
                                true),
                        expected(List.of("UNION ALL", "order_status"), List.of(), List.of())),
                caseOf(
                        "qualified-source-alias-join-postgres",
                        "postgres",
                        derived(
                                join(
                                        base("FactSalesModel",
                                                List.of("orderStatus$caption", "salesAmount"),
                                                null, null, List.of("sales")),
                                        base("FactOrderModel",
                                                List.of("orderStatus$caption", "totalAmount"),
                                                null, null, List.of("orders")),
                                        "inner",
                                        List.of(joinOn("left.orderStatus$caption", "=", "right.orderStatus$caption"))),
                                List.of("sales.salesAmount", "orders.totalAmount"),
                                null,
                                List.of("-orders.totalAmount"),
                                null,
                                null),
                        expected(List.of("INNER JOIN", "salesAmount", "totalAmount", "ORDER BY"),
                                List.of("sales.salesAmount", "orders.totalAmount"),
                                List.of())),
                caseOf(
                        "qualified-source-alias-slice-order-postgres",
                        "postgres",
                        derived(
                                join(
                                        base("FactSalesModel",
                                                List.of("orderStatus$caption", "salesAmount"),
                                                null, null, List.of("sales")),
                                        base("FactOrderModel",
                                                List.of("orderStatus$caption", "totalAmount"),
                                                null, null, List.of("orders")),
                                        "inner",
                                        List.of(joinOn("left.orderStatus$caption", "=", "right.orderStatus$caption"))),
                                List.of("sales.salesAmount", "orders.totalAmount"),
                                List.of(filter("sales.salesAmount", ">", 0)),
                                List.of("-orders.totalAmount"),
                                null,
                                null),
                        expected(List.of("INNER JOIN", "WHERE", "ORDER BY", "salesAmount", "totalAmount"),
                                List.of("sales.salesAmount", "orders.totalAmount"),
                                List.of(0))),
                caseOf(
                        "qualified-source-alias-slice-order-mysql8",
                        "mysql8",
                        derived(
                                join(
                                        base("FactSalesModel",
                                                List.of("orderStatus$caption", "salesAmount"),
                                                null, null, List.of("sales")),
                                        base("FactOrderModel",
                                                List.of("orderStatus$caption", "totalAmount"),
                                                null, null, List.of("orders")),
                                        "inner",
                                        List.of(joinOn("left.orderStatus$caption", "=", "right.orderStatus$caption"))),
                                List.of("sales.salesAmount", "orders.totalAmount"),
                                List.of(filter("sales.salesAmount", ">", 0)),
                                List.of("-orders.totalAmount"),
                                null,
                                null),
                        expected(List.of("INNER JOIN", "WHERE", "ORDER BY", "salesAmount", "totalAmount"),
                                List.of("sales.salesAmount", "orders.totalAmount"),
                                List.of(0))),
                caseOf(
                        "qualified-source-alias-slice-order-sqlserver",
                        "sqlserver",
                        derived(
                                join(
                                        base("FactSalesModel",
                                                List.of("orderStatus$caption", "salesAmount"),
                                                null, null, List.of("sales")),
                                        base("FactOrderModel",
                                                List.of("orderStatus$caption", "totalAmount"),
                                                null, null, List.of("orders")),
                                        "inner",
                                        List.of(joinOn("left.orderStatus$caption", "=", "right.orderStatus$caption"))),
                                List.of("sales.salesAmount", "orders.totalAmount"),
                                List.of(filter("sales.salesAmount", ">", 0)),
                                List.of("-orders.totalAmount"),
                                null,
                                null),
                        expected(List.of("INNER JOIN", "WHERE", "ORDER BY", "salesAmount", "totalAmount"),
                                List.of("sales.salesAmount", "orders.totalAmount"),
                                List.of(0))),
                caseOf(
                        "inherited-source-alias-through-derived-postgres",
                        "postgres",
                        derived(
                                join(
                                        derived(
                                                base("FactSalesModel",
                                                        List.of("orderStatus$caption", "salesAmount"),
                                                        null, null, List.of("sales")),
                                                List.of("orderStatus$caption", "salesAmount"),
                                                List.of(filter("salesAmount", ">", 0)),
                                                null,
                                                null,
                                                null),
                                        base("FactOrderModel",
                                                List.of("orderStatus$caption", "totalAmount"),
                                                null, null, List.of("orders")),
                                        "left",
                                        List.of(joinOn("left.orderStatus$caption", "=", "right.orderStatus$caption"))),
                                List.of("sales.salesAmount", "orders.totalAmount"),
                                null,
                                List.of("-orders.totalAmount"),
                                null,
                                null),
                        expected(List.of("LEFT JOIN", "ORDER BY", "salesAmount", "totalAmount"),
                                List.of("sales.salesAmount", "orders.totalAmount"),
                                List.of(0))),
                caseOf(
                        "ambiguous-duplicate-source-alias-ref-refused",
                        "postgres",
                        derived(
                                join(
                                        base("FactSalesModel",
                                                List.of("orderStatus$caption", "salesAmount"),
                                                null, null, List.of("dup")),
                                        base("FactOrderModel",
                                                List.of("orderStatus$caption", "totalAmount"),
                                                null, null, List.of("dup")),
                                        "inner",
                                        List.of(joinOn("left.orderStatus$caption", "=", "right.orderStatus$caption"))),
                                List.of("dup.salesAmount"),
                                null,
                                null,
                                null,
                                null),
                        expectedError("ambiguous")),
                caseOf(
                        "source-alias-shadowed-by-projected-alias-refused",
                        "postgres",
                        derived(
                                base("FactSalesModel",
                                        List.of("orderStatus$caption", "salesAmount"),
                                        null, null, List.of("sales")),
                                List.of("salesAmount AS sales"),
                                null,
                                null,
                                null,
                                null),
                        expectedError("shadow")),
                caseOf(
                        "source-alias-dropped-column-refused",
                        "mysql8",
                        derived(
                                join(
                                        derived(
                                                base("FactSalesModel",
                                                        List.of("orderStatus$caption", "salesAmount"),
                                                        null, null, List.of("sales")),
                                                List.of("orderStatus$caption"),
                                                null,
                                                null,
                                                null,
                                                null),
                                        base("FactOrderModel",
                                                List.of("orderStatus$caption", "totalAmount"),
                                                null, null, List.of("orders")),
                                        "left",
                                        List.of(joinOn("left.orderStatus$caption", "=", "right.orderStatus$caption"))),
                                List.of("sales.salesAmount", "orders.totalAmount"),
                                null,
                                null,
                                null,
                                null),
                        expectedError("unknown field")),
                caseOf(
                        "union-branch-source-alias-ref-refused",
                        "postgres",
                        derived(
                                union(
                                        base("FactSalesModel",
                                                List.of("orderStatus$caption AS bucket", "salesAmount AS amount"),
                                                null, null, List.of("sales")),
                                        base("FactOrderModel",
                                                List.of("orderStatus$caption AS bucket", "totalAmount AS amount"),
                                                null, null, List.of("orders")),
                                        true),
                                List.of("sales.amount"),
                                null,
                                null,
                                null,
                                null),
                        expectedError("unknown field")),
                caseOf(
                        "union-result-alias-qualified-ref-postgres",
                        "postgres",
                        derived(
                                union(
                                        base("FactSalesModel",
                                                List.of("orderStatus$caption AS bucket", "salesAmount AS amount"),
                                                null, null, List.of("sales")),
                                        base("FactOrderModel",
                                                List.of("orderStatus$caption AS bucket", "totalAmount AS amount"),
                                                null, null, List.of("orders")),
                                        true,
                                        List.of("combined")),
                                List.of("combined.amount"),
                                List.of(filter("combined.amount", ">", 0)),
                                List.of("-combined.amount"),
                                null,
                                null),
                        expected(List.of("UNION ALL", "WHERE", "ORDER BY", "amount"),
                                List.of("combined.amount", "sales.amount", "orders.amount"),
                                List.of(0))),
                caseOf(
                        "stable-reused-base-qualified-ref-postgres",
                        "postgres",
                        derived(
                                join(
                                        derived(
                                                reuse(base("FactSalesModel",
                                                        List.of("orderStatus$caption", "salesAmount"),
                                                        null, null, null),
                                                        "sharedSales"),
                                                List.of(
                                                        "orderStatus$caption AS statusLeft",
                                                        "salesAmount AS amountLeft"),
                                                null,
                                                null,
                                                null,
                                                List.of("leftSales")),
                                        derived(
                                                reuse(base("FactSalesModel",
                                                        List.of("orderStatus$caption", "salesAmount"),
                                                        null, null, null),
                                                        "sharedSales"),
                                                List.of(
                                                        "orderStatus$caption AS statusRight",
                                                        "salesAmount AS amountRight"),
                                                null,
                                                null,
                                                null,
                                                List.of("rightSales")),
                                        "inner",
                                        List.of(joinOn("left.statusLeft", "=", "right.statusRight"))),
                                List.of("left.amountLeft", "right.amountRight"),
                                List.of(filter("left.amountLeft", ">", 0)),
                                List.of("-right.amountRight"),
                                null,
                                null),
                        expected(List.of("INNER JOIN", "WHERE", "ORDER BY", "amountLeft", "amountRight"),
                                List.of("left.amountLeft", "right.amountRight"),
                                List.of(0))),
                caseOf(
                        "sqlserver-union-result-alias-derived-fallback",
                        "sqlserver",
                        derived(
                                union(
                                        base("FactSalesModel",
                                                List.of("orderStatus$caption AS bucket", "salesAmount AS amount"),
                                                null, null, List.of("sales")),
                                        base("FactOrderModel",
                                                List.of("orderStatus$caption AS bucket", "totalAmount AS amount"),
                                                null, null, List.of("orders")),
                                        true,
                                        List.of("combined")),
                                List.of("combined.amount"),
                                List.of(filter("combined.amount", ">", 0)),
                                List.of("-combined.amount"),
                                null,
                                null),
                        expected(List.of("UNION ALL", "WHERE", "ORDER BY", "amount"),
                                List.of("FROM (WITH", "combined.amount", "sales.amount", "orders.amount"),
                                List.of(0))),
                caseOf(
                        "sqlserver-derived-chain-top-level-with",
                        "sqlserver",
                        derived(
                                derived(
                                        base("FactSalesModel",
                                                List.of("orderStatus$caption", "salesAmount"),
                                                null, null, null),
                                        List.of("orderStatus$caption", "salesAmount"),
                                        null,
                                        null,
                                        null,
                                        null),
                                List.of("orderStatus$caption"),
                                null,
                                null,
                                null,
                                null),
                        expected(List.of("SELECT", "FROM (", " AS t0"),
                                List.of("WITH ", "FROM (WITH"),
                                List.of()))
        );
    }

    private static Map<String, Object> caseOf(String id, String dialect,
                                              Map<String, Object> plan,
                                              Map<String, Object> expected) {
        Map<String, Object> out = ordered();
        out.put("id", id);
        out.put("dialect", dialect);
        out.put("plan", plan);
        out.put("expected", expected);
        return out;
    }

    private static Map<String, Object> base(String model, List<String> columns,
                                            List<Object> slice,
                                            List<String> orderBy,
                                            List<String> aliases) {
        Map<String, Object> out = ordered();
        out.put("type", "base");
        out.put("model", model);
        out.put("columns", columns);
        putIf(out, "slice", slice);
        putIf(out, "orderBy", orderBy);
        putIf(out, "aliases", aliases);
        return out;
    }

    private static Map<String, Object> reuse(Map<String, Object> plan, String reuseKey) {
        plan.put("reuseKey", reuseKey);
        return plan;
    }

    private static Map<String, Object> derived(Map<String, Object> source,
                                               List<String> columns,
                                               List<Object> slice,
                                               List<String> orderBy,
                                               Integer limit,
                                               List<String> aliases) {
        Map<String, Object> out = ordered();
        out.put("type", "derived");
        out.put("source", source);
        out.put("columns", columns);
        putIf(out, "slice", slice);
        putIf(out, "orderBy", orderBy);
        putIf(out, "limit", limit);
        putIf(out, "aliases", aliases);
        return out;
    }

    private static Map<String, Object> union(Map<String, Object> left,
                                             Map<String, Object> right,
                                             boolean all) {
        return union(left, right, all, null);
    }

    private static Map<String, Object> union(Map<String, Object> left,
                                             Map<String, Object> right,
                                             boolean all,
                                             List<String> aliases) {
        Map<String, Object> out = ordered();
        out.put("type", "union");
        out.put("left", left);
        out.put("right", right);
        out.put("all", all);
        putIf(out, "aliases", aliases);
        return out;
    }

    private static Map<String, Object> join(Map<String, Object> left,
                                            Map<String, Object> right,
                                            String joinType,
                                            List<Map<String, Object>> on) {
        Map<String, Object> out = ordered();
        out.put("type", "join");
        out.put("left", left);
        out.put("right", right);
        out.put("joinType", joinType);
        out.put("on", on);
        return out;
    }

    private static Map<String, Object> filter(String field, String op, Object value) {
        Map<String, Object> out = ordered();
        out.put("field", field);
        out.put("op", op);
        out.put("value", value);
        return out;
    }

    private static Map<String, Object> joinOn(String left, String op, String right) {
        Map<String, Object> out = ordered();
        out.put("left", left);
        out.put("op", op);
        out.put("right", right);
        return out;
    }

    private static Map<String, Object> expected(List<String> markers,
                                                List<String> forbidden,
                                                List<Object> params) {
        Map<String, Object> out = ordered();
        out.put("sqlMarkers", markers);
        out.put("forbiddenSqlMarkers", forbidden);
        out.put("params", params);
        return out;
    }

    private static Map<String, Object> expectedError(String code) {
        Map<String, Object> out = ordered();
        out.put("errorCode", code);
        return out;
    }

    private static Map<String, Object> ordered() {
        return new LinkedHashMap<>();
    }

    private static void putIf(Map<String, Object> out, String key, Object value) {
        if (value != null) {
            out.put(key, value);
        }
    }
}
