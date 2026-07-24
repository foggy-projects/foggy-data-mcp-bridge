package com.foggyframework.dataset.db.model.engine.compose;

import com.foggyframework.dataset.db.model.ecommerce.EcommerceTestSupport;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticRequestContext;
import com.foggyframework.dataset.db.model.semantic.port.ComposeSemanticPlanningPort;
import com.foggyframework.dataset.db.model.semantic.port.SemanticQueryExecutionPort;
import com.foggyframework.dataset.db.model.semantic.service.SemanticQueryServiceV3;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

@DisplayName("ComposedDataSetResult integration")
class ComposedDataSetResultIT extends EcommerceTestSupport {

    private static final String SALES_MODEL = "FactSalesQueryModel";
    private static final String RETURN_MODEL = "FactReturnQueryModel";

    @Resource
    private SemanticQueryServiceV3 semanticQueryServiceV3;

    @Resource
    private DataSource dataSource;

    @Test
    @DisplayName("withJoin from real dsl results executes composed SQL and matches hand-written SQL")
    void withJoinFromRealDslResultsMatchesSql() {
        DataSetResult salesByProduct = dsl(Map.of(
                "model", SALES_MODEL,
                "columns", List.of("product$id", "salesAmount"),
                "groupBy", List.of("product$id")));
        DataSetResult returnsByProduct = dsl(Map.of(
                "model", RETURN_MODEL,
                "columns", List.of("product$id", "returnAmount"),
                "groupBy", List.of("product$id")));

        ComposedDataSetResult composed = salesByProduct.withJoin(
                returnsByProduct, "INNER", "product$id");
        DataSetResult firstExecution = composed.execute();
        DataSetResult secondExecution = composed.execute();

        List<Map<String, Object>> expected = executeQuery("""
                SELECT sales.%s,
                       sales.%s,
                       ret.%s
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
                ) ret ON sales.%s = ret.%s
                """.formatted(
                q("product$id"),
                q("salesAmount"),
                q("returnAmount"),
                q("product$id"),
                q("salesAmount"),
                q("product$id"),
                q("returnAmount"),
                q("product$id"),
                q("product$id")));

        assertSame(firstExecution, secondExecution, "execute() should return the cached result");
        assertRowsEqual(expected, firstExecution.toList(), false);
    }

    private DataSetResult dsl(Map<String, Object> params) {
        SemanticQueryExecutionPort executionPort = semanticQueryServiceV3::queryModel;
        ComposeSemanticPlanningPort planningPort = semanticQueryServiceV3::generateComposeSql;
        DslQueryFunction function = new DslQueryFunction(
                executionPort, planningPort, SemanticRequestContext.empty(), dataSource);
        return (DataSetResult) function.executeFunction(null, params);
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
