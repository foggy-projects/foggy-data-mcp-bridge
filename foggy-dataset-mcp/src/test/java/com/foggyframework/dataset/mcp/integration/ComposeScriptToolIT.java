package com.foggyframework.dataset.mcp.integration;

import com.foggyframework.dataset.model.engine.compose.context.ComposeQueryContext;
import com.foggyframework.dataset.model.engine.compose.context.Principal;
import com.foggyframework.dataset.model.engine.compose.runtime.ComposeRuntimeBundle;
import com.foggyframework.dataset.model.engine.compose.runtime.ComposeRuntimeHolder;
import com.foggyframework.dataset.model.engine.compose.security.AuthorityResolution;
import com.foggyframework.dataset.model.engine.compose.security.AuthorityResolver;
import com.foggyframework.dataset.model.engine.compose.security.ModelBinding;
import com.foggyframework.dataset.model.semantic.service.SemanticQueryServiceV3;
import com.foggyframework.dataset.mcp.tools.ComposeScriptTool;
import com.foggyframework.mcp.spi.ToolExecutionContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@DisplayName("ComposeScriptTool integration")
class ComposeScriptToolIT extends McpIntegrationTestSupport {

    @Autowired
    private ComposeScriptTool composeScriptTool;

    @Autowired
    private SemanticQueryServiceV3 semanticQueryServiceV3;

    @Test
    @DisplayName("dataset.compose_script is registered in integration context")
    void composeScriptToolRegistered() {
        assertNotNull(composeScriptTool);
        assertEquals("dataset.compose_script", composeScriptTool.getName());
        assertEquals(composeScriptTool, getTool("dataset.compose_script"));
    }

    @Test
    @DisplayName("embedded compose script executes real join query and matches hand-written SQL")
    void embeddedComposeScriptJoinMatchesSql() {
        Map<String, Object> result = executeWithEmbeddedBundle("""
                const salesByProduct = dsl({
                    model: "FactSalesQueryModel",
                    columns: ["product$id", "salesAmount"],
                    groupBy: ["product$id"]
                });

                const returnsByProduct = dsl({
                    model: "FactReturnQueryModel",
                    columns: ["product$id", "returnAmount"],
                    groupBy: ["product$id"]
                });

                const joined = salesByProduct.join(returnsByProduct, "inner", [
                    { left: "product$id", op: "=", right: "product$id" }
                ]);

                return {
                    plans: {
                        sales_return_by_product: dsl({
                            source: joined,
                            columns: ["product$id", "salesAmount", "returnAmount"]
                        })
                    }
                };
                """);

        assertEquals("success", result.get("status"));
        Map<?, ?> data = assertInstanceOf(Map.class, result.get("data"));
        Map<?, ?> value = assertInstanceOf(Map.class, data.get("value"));
        Map<?, ?> plans = assertInstanceOf(Map.class, value.get("plans"));
        assertEquals(Set.of("sales_return_by_product"), plans.keySet());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> actual = (List<Map<String, Object>>) assertInstanceOf(
                List.class, plans.get("sales_return_by_product"));

        List<Map<String, Object>> expected = executeQuery("""
                SELECT sales.`product$id`,
                       sales.`salesAmount`,
                       ret.`returnAmount`
                FROM (
                    SELECT fs.product_key AS `product$id`,
                           SUM(fs.sales_amount) AS `salesAmount`
                    FROM fact_sales fs
                    GROUP BY fs.product_key
                ) sales
                INNER JOIN (
                    SELECT fr.product_key AS `product$id`,
                           SUM(fr.return_amount) AS `returnAmount`
                    FROM fact_return fr
                    GROUP BY fr.product_key
                ) ret ON sales.`product$id` = ret.`product$id`
                """);

        assertFalse(expected.isEmpty(), "hand-written SQL oracle should not be empty");
        assertRowsEqual(expected, actual, true);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> executeWithEmbeddedBundle(String script) {
        AuthorityResolver resolver = resolver();
        ComposeQueryContext ctx = ComposeQueryContext.builder()
                .principal(Principal.builder()
                        .userId("compose-script-tool-integration-test")
                        .tenantId("test")
                        .roles(List.of("tester"))
                        .build())
                .namespace(null)
                .traceId(generateTraceId())
                .authorityResolver(resolver)
                .build();
        ComposeRuntimeBundle bundle = ComposeRuntimeBundle.builder()
                .ctx(ctx)
                .semanticService(semanticQueryServiceV3)
                .dialect("mysql")
                .build();
        ComposeRuntimeHolder.Token token = ComposeRuntimeHolder.setBundle(bundle);
        try {
            return (Map<String, Object>) composeScriptTool.execute(
                    Map.of("script", script),
                    ToolExecutionContext.of(generateTraceId(), null));
        } finally {
            ComposeRuntimeHolder.popBundle(token);
        }
    }

    private static AuthorityResolver resolver() {
        return request -> {
            Map<String, ModelBinding> bindings = new LinkedHashMap<>();
            for (String modelName : request.modelNames()) {
                bindings.put(modelName, ModelBinding.builder().build());
            }
            return AuthorityResolution.builder().bindings(bindings).build();
        };
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
