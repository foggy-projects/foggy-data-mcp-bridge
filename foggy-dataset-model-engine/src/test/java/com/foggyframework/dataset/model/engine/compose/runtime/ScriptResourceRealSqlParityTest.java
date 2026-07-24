package com.foggyframework.dataset.model.engine.compose.runtime;

import com.foggyframework.dataset.model.ecommerce.EcommerceTestSupport;
import com.foggyframework.dataset.model.engine.compose.context.ComposeQueryContext;
import com.foggyframework.dataset.model.engine.compose.context.Principal;
import com.foggyframework.dataset.model.engine.compose.security.AuthorityResolution;
import com.foggyframework.dataset.model.engine.compose.security.ModelBinding;
import com.foggyframework.dataset.model.semantic.service.SemanticQueryServiceV3;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

@DisplayName("Script resource real-SQL parity")
class ScriptResourceRealSqlParityTest extends EcommerceTestSupport {

    @Resource
    private SemanticQueryServiceV3 semanticQueryServiceV3;

    @Test
    @DisplayName("derived resource script executes and matches hand-written SQL")
    void derivedResourceScriptMatchesSql() throws IOException {
        List<Map<String, Object>> actual = executeScriptPlan(
                "real_sql_derived_query_scenario.js", "high_value_categories");
        List<Map<String, Object>> expected = executeQuery("""
                SELECT p.category_name AS %s,
                       SUM(fs.sales_amount) AS %s
                FROM fact_sales fs
                LEFT JOIN dim_product p ON fs.product_key = p.product_key
                GROUP BY p.category_name
                HAVING SUM(fs.sales_amount) > 1000
                """.formatted(q("product$categoryName"), q("salesAmount")));

        assertRowsEqual(expected, actual);
    }

    @Test
    @DisplayName("join resource script executes and matches hand-written SQL")
    void joinResourceScriptMatchesSql() throws IOException {
        List<Map<String, Object>> actual = executeScriptPlan(
                "real_sql_join_scenario.js", "sales_return_by_product");
        List<Map<String, Object>> expected = executeQuery("""
                SELECT %s,
                       %s,
                       %s
                FROM (
                    SELECT fs.product_key AS %s,
                           SUM(fs.sales_amount) AS %s
                    FROM fact_sales fs
                    GROUP BY fs.product_key
                ) sales
                INNER JOIN (
                    SELECT fr.product_key AS %s,
                           SUM(fr.return_amount) AS %s
                    FROM fact_return fr
                    GROUP BY fr.product_key
                ) ret ON %s = %s
                """.formatted(
                ref("sales", "product$id"),
                ref("sales", "salesAmount"),
                ref("ret", "returnAmount"),
                q("product$id"),
                q("salesAmount"),
                q("product$id"),
                q("returnAmount"),
                ref("sales", "product$id"),
                ref("ret", "product$id")));

        assertRowsEqual(expected, actual, false);
    }

    @Test
    @DisplayName("union resource script executes and matches hand-written SQL")
    void unionResourceScriptMatchesSql() throws IOException {
        List<Map<String, Object>> actual = executeScriptPlan(
                "real_sql_union_scenario.js", "product_amount_union");
        List<Map<String, Object>> expected = executeQuery("""
                SELECT fs.product_key AS %s,
                       SUM(fs.sales_amount) AS %s
                FROM fact_sales fs
                GROUP BY fs.product_key
                UNION ALL
                SELECT fr.product_key AS %s,
                       SUM(fr.return_amount) AS %s
                FROM fact_return fr
                GROUP BY fr.product_key
                """.formatted(
                q("product$id"),
                q("salesAmount"),
                q("product$id"),
                q("salesAmount")));

        assertRowsEqual(expected, actual);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> executeScriptPlan(String scriptName, String planName)
            throws IOException {
        String script = Files.readString(Path.of("src/test/resources/scripts", scriptName));
        ScriptRuntime.ScriptResult result = ScriptRuntime.runScript(
                script, composeContext(), semanticQueryServiceV3, composeDialect(), false);
        Map<String, Object> value = assertInstanceOf(Map.class, result.value());
        Map<String, Object> plans = assertInstanceOf(Map.class, value.get("plans"));
        Object planResult = plans.get(planName);
        return (List<Map<String, Object>>) assertInstanceOf(List.class, planResult);
    }

    private ComposeQueryContext composeContext() {
        return ComposeQueryContext.builder()
                .principal(Principal.builder()
                        .userId("script-resource-parity-test")
                        .tenantId("test")
                        .roles(List.of("tester"))
                        .build())
                .namespace(null)
                .traceId("script-resource-real-sql-parity")
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
            return supportsWindowFunctions() ? "mysql8" : "mysql";
        }
        return dialect;
    }

    private String q(String identifier) {
        String dialect = getDialectKey();
        if (dialect.contains("mysql")) {
            return "`" + identifier + "`";
        }
        if (dialect.contains("sqlserver")) {
            return "[" + identifier + "]";
        }
        return "\"" + identifier + "\"";
    }

    private String ref(String alias, String identifier) {
        return alias + "." + q(identifier);
    }

    private static void assertRowsEqual(List<Map<String, Object>> expected,
                                        List<Map<String, Object>> actual) {
        assertRowsEqual(expected, actual, true);
    }

    private static void assertRowsEqual(List<Map<String, Object>> expected,
                                        List<Map<String, Object>> actual,
                                        boolean requireNonEmpty) {
        if (!requireNonEmpty) {
            assertEquals(canonicalRows(expected), canonicalRows(actual));
            return;
        }
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
