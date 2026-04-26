package com.foggyframework.dataset.db.model.engine.compose.runtime;

import com.foggyframework.dataset.db.model.ecommerce.EcommerceTestSupport;
import com.foggyframework.dataset.db.model.engine.compose.context.ComposeQueryContext;
import com.foggyframework.dataset.db.model.engine.compose.context.Principal;
import com.foggyframework.dataset.db.model.engine.compose.plan.BaseModelPlan;
import com.foggyframework.dataset.db.model.engine.compose.plan.DerivedQueryPlan;
import com.foggyframework.dataset.db.model.engine.compose.plan.JoinOn;
import com.foggyframework.dataset.db.model.engine.compose.plan.JoinPlan;
import com.foggyframework.dataset.db.model.engine.compose.plan.JoinType;
import com.foggyframework.dataset.db.model.engine.compose.plan.QueryPlan;
import com.foggyframework.dataset.db.model.engine.compose.plan.UnionPlan;
import com.foggyframework.dataset.db.model.engine.compose.security.AuthorityResolution;
import com.foggyframework.dataset.db.model.engine.compose.security.ModelBinding;
import com.foggyframework.dataset.db.model.semantic.service.SemanticQueryServiceV3;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

@DisplayName("Compose runtime real-SQL parity")
class ComposeRealSqlParityTest extends EcommerceTestSupport {

    private static final String SALES_MODEL = "FactSalesQueryModel";
    private static final String RETURN_MODEL = "FactReturnQueryModel";

    @Resource
    private SemanticQueryServiceV3 semanticQueryServiceV3;

    @Test
    @DisplayName("derived aggregate + outer filter matches hand-written SQL")
    void derivedAggregateFilterMatchesSql() {
        BaseModelPlan salesByCategory = BaseModelPlan.builder()
                .model(SALES_MODEL)
                .columns(List.of("product$categoryName", "salesAmount"))
                .groupBy(List.of("product$categoryName"))
                .build();

        DerivedQueryPlan filtered = DerivedQueryPlan.builder()
                .source(salesByCategory)
                .columns(List.of("product$categoryName", "salesAmount"))
                .slice(List.<Object>of(Map.of("field", "salesAmount", "op", ">", "value", 1000)))
                .build();

        List<Map<String, Object>> actual = executePlan(filtered);
        List<Map<String, Object>> expected = executeQuery("""
                SELECT p.category_name AS "product$categoryName",
                       SUM(fs.sales_amount) AS "salesAmount"
                FROM fact_sales fs
                LEFT JOIN dim_product p ON fs.product_key = p.product_key
                GROUP BY p.category_name
                HAVING SUM(fs.sales_amount) > 1000
                """);

        assertRowsEqual(expected, actual);
    }

    @Test
    @DisplayName("cross-model join aggregate matches hand-written SQL")
    void joinAggregateMatchesSql() {
        BaseModelPlan salesByProduct = BaseModelPlan.builder()
                .model(SALES_MODEL)
                .columns(List.of("product$id", "salesAmount"))
                .groupBy(List.of("product$id"))
                .build();

        BaseModelPlan returnsByProduct = BaseModelPlan.builder()
                .model(RETURN_MODEL)
                .columns(List.of("product$id", "returnAmount"))
                .groupBy(List.of("product$id"))
                .build();

        QueryPlan joined = DerivedQueryPlan.builder()
                .source(JoinPlan.builder()
                        .left(salesByProduct)
                        .right(returnsByProduct)
                        .type(JoinType.INNER)
                        .on(List.of(JoinOn.of("product$id", "=", "product$id")))
                        .build())
                .columns(List.of("product$id", "salesAmount", "returnAmount"))
                .build();

        List<Map<String, Object>> actual = executePlan(joined);
        List<Map<String, Object>> expected = executeQuery("""
                WITH sales AS (
                    SELECT fs.product_key AS "product$id",
                           SUM(fs.sales_amount) AS "salesAmount"
                    FROM fact_sales fs
                    GROUP BY fs.product_key
                ),
                returns AS (
                    SELECT fr.product_key AS "product$id",
                           SUM(fr.return_amount) AS "returnAmount"
                    FROM fact_return fr
                    GROUP BY fr.product_key
                )
                SELECT sales."product$id",
                       sales."salesAmount",
                       returns."returnAmount"
                FROM sales
                INNER JOIN returns ON sales."product$id" = returns."product$id"
                """);

        assertRowsEqual(expected, actual);
    }

    @Test
    @DisplayName("union all aggregate matches hand-written SQL")
    void unionAllAggregateMatchesSql() {
        BaseModelPlan salesByProduct = BaseModelPlan.builder()
                .model(SALES_MODEL)
                .columns(List.of("product$id", "salesAmount"))
                .groupBy(List.of("product$id"))
                .build();

        BaseModelPlan returnsByProduct = BaseModelPlan.builder()
                .model(RETURN_MODEL)
                .columns(List.of("product$id", "returnAmount"))
                .groupBy(List.of("product$id"))
                .build();

        UnionPlan union = UnionPlan.builder()
                .left(salesByProduct)
                .right(returnsByProduct)
                .all(true)
                .build();

        List<Map<String, Object>> actual = executePlan(union);
        List<Map<String, Object>> expected = executeQuery("""
                SELECT fs.product_key AS "product$id",
                       SUM(fs.sales_amount) AS "salesAmount"
                FROM fact_sales fs
                GROUP BY fs.product_key
                UNION ALL
                SELECT fr.product_key AS "product$id",
                       SUM(fr.return_amount) AS "salesAmount"
                FROM fact_return fr
                GROUP BY fr.product_key
                """);

        assertRowsEqual(expected, actual);
    }

    private List<Map<String, Object>> executePlan(QueryPlan plan) {
        return PlanExecution.executePlan(plan, composeContext(), semanticQueryServiceV3, composeDialect());
    }

    private ComposeQueryContext composeContext() {
        return ComposeQueryContext.builder()
                .principal(Principal.builder()
                        .userId("compose-parity-test")
                        .tenantId("test")
                        .roles(List.of("tester"))
                        .build())
                .namespace(null)
                .traceId("compose-real-sql-parity")
                .authorityResolver(request -> {
                    Map<String, ModelBinding> bindings = new LinkedHashMap<>();
                    for (String modelName : request.modelNames()) {
                        bindings.put(modelName, ModelBinding.builder().build());
                    }
                    return AuthorityResolution.builder().bindings(bindings).build();
                })
                .build();
    }

    private String composeDialect() {
        String dialect = getDialectKey();
        if (dialect.contains("postgres")) {
            return "postgres";
        }
        if (dialect.contains("sqlserver")) {
            return "mssql";
        }
        if (dialect.contains("mysql")) {
            return "mysql8";
        }
        return dialect;
    }

    private static void assertRowsEqual(List<Map<String, Object>> expected, List<Map<String, Object>> actual) {
        assertFalse(actual.isEmpty(), "actual result should not be empty");
        assertEquals(canonicalRows(expected), canonicalRows(actual));
    }

    private static List<Map<String, String>> canonicalRows(List<Map<String, Object>> rows) {
        List<Map<String, String>> canonical = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, String> normalized = new LinkedHashMap<>();
            row.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> normalized.put(entry.getKey(), canonicalValue(entry.getValue())));
            canonical.add(normalized);
        }
        canonical.sort(Comparator.comparing(Map::toString));
        return canonical;
    }

    private static String canonicalValue(Object value) {
        if (value == null) {
            return "<null>";
        }
        if (value instanceof Number) {
            return new BigDecimal(value.toString())
                    .setScale(6, RoundingMode.HALF_UP)
                    .stripTrailingZeros()
                    .toPlainString();
        }
        return value.toString();
    }
}
