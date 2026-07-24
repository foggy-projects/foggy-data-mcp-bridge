package com.foggyframework.dataset.model.plugins.result_set_filter;

import com.foggyframework.dataset.model.def.query.request.CalculatedFieldDef;
import com.foggyframework.dataset.model.def.query.request.DbQueryRequestDef;
import com.foggyframework.dataset.model.def.query.request.OrderRequestDef;
import com.foggyframework.dataset.model.ecommerce.EcommerceTestSupport;
import com.foggyframework.dataset.model.engine.compose.SqlGenerationResult;
import com.foggyframework.dataset.model.semantic.domain.SemanticQueryRequest;
import com.foggyframework.dataset.model.semantic.domain.SemanticQueryResponse;
import com.foggyframework.dataset.model.semantic.domain.SemanticRequestContext;
import com.foggyframework.dataset.model.semantic.service.SemanticQueryServiceV3;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Stage 5: Validate SchemaAwareFieldValidationStep accepts calculatedField
 * output aliases in request columns and orderBy while still rejecting
 * genuinely unknown fields.
 *
 * <p>Uses the real v1.3 {@link SemanticQueryServiceV3#generateSql} pipeline
 * against SQLite so all steps (TimeWindowInterceptor, InlineExpressionPreprocessStep,
 * SchemaAwareFieldValidationStep, etc.) execute in their real order.</p>
 *
 * @since 8.5.0.beta (Stage 5)
 */
@Slf4j
@DisplayName("SchemaAwareFieldValidationStep · calculatedField alias contract")
class SchemaAwareCalcFieldAliasTest extends EcommerceTestSupport {

    private static final String MODEL = "FactSalesQueryModel";

    @Resource
    private SemanticQueryServiceV3 semanticQueryServiceV3;

    // ------------------------------------------------------------------
    // Happy: calc alias in columns (non-timeWindow)
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Non-timeWindow: calc alias in columns")
    class NonTimeWindowCalcAlias {

        @Test
        @DisplayName("calc field output name accepted in columns")
        void calcAliasAcceptedInColumns() {
            SemanticQueryRequest req = new SemanticQueryRequest();
            req.setColumns(List.of("salesDate$year", "salesAmount", "salesGrowth"));
            req.setGroupBy(List.of(
                    new SemanticQueryRequest.GroupByItem("salesDate$year", null)));
            req.setCalculatedFields(List.of(
                    new CalculatedFieldDef("salesGrowth", "salesAmount * 1.1")));

            SqlGenerationResult result = semanticQueryServiceV3.generateSql(
                    MODEL, req, SemanticRequestContext.empty());
            assertNotNull(result, "generateSql should succeed");
            assertNotNull(result.getSql(), "SQL should be generated");
            log.info("non-TW calc alias SQL: {}", result.getSql());
        }

        @Test
        @DisplayName("calc field output name accepted in orderBy")
        void calcAliasAcceptedInOrderBy() {
            SemanticQueryRequest req = new SemanticQueryRequest();
            req.setColumns(List.of("salesDate$year", "salesAmount", "salesGrowth"));
            req.setGroupBy(List.of(
                    new SemanticQueryRequest.GroupByItem("salesDate$year", null)));
            req.setCalculatedFields(List.of(
                    new CalculatedFieldDef("salesGrowth", "salesAmount * 1.1")));
            SemanticQueryRequest.OrderItem orderItem = new SemanticQueryRequest.OrderItem();
            orderItem.setField("salesGrowth");
            orderItem.setDir("desc");
            req.setOrderBy(List.of(orderItem));

            SqlGenerationResult result = semanticQueryServiceV3.generateSql(
                    MODEL, req, SemanticRequestContext.empty());
            assertNotNull(result, "generateSql should succeed with calc alias in orderBy");
            assertNotNull(result.getSql());
        }
    }

    // ------------------------------------------------------------------
    // Happy: calc alias in columns (with timeWindow)
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("TimeWindow: calc alias in columns")
    class TimeWindowCalcAlias {

        @Test
        @DisplayName("growthPercent in columns with YoY timeWindow")
        void growthPercentAccepted() {
            if (!supportsWindowFunctions()) return;

            SemanticQueryRequest req = new SemanticQueryRequest();
            req.setColumns(List.of(
                    "salesDate$year", "salesDate$month",
                    "salesAmount", "salesAmount__prior",
                    "salesAmount__diff", "salesAmount__ratio",
                    "growthPercent"));
            req.setGroupBy(List.of(
                    new SemanticQueryRequest.GroupByItem("salesDate$year", null),
                    new SemanticQueryRequest.GroupByItem("salesDate$month", null)));
            req.setTimeWindow(Map.of(
                    "field", "salesDate$id",
                    "grain", "month",
                    "comparison", "yoy",
                    "range", "[)",
                    "value", List.of("2024-01-01", "2025-01-01"),
                    "targetMetrics", List.of("salesAmount")));
            req.setCalculatedFields(List.of(
                    new CalculatedFieldDef("growthPercent", "salesAmount__ratio * 100")));

            SqlGenerationResult result = semanticQueryServiceV3.generateSql(
                    MODEL, req, SemanticRequestContext.empty());
            assertNotNull(result);
            String sql = result.getSql();
            assertNotNull(sql);
            assertTrue(sql.contains("growthPercent"),
                    "SQL should project growthPercent: " + sql);
            log.info("YoY + growthPercent SQL: {}", sql);
        }

        @Test
        @DisplayName("rollingGap in columns with rolling_7d timeWindow")
        void rollingGapAccepted() {
            if (!supportsWindowFunctions()) return;

            SemanticQueryRequest req = new SemanticQueryRequest();
            req.setColumns(List.of(
                    "salesDate$id", "salesAmount",
                    "salesAmount__rolling_7d",
                    "rollingGap"));
            req.setGroupBy(List.of(
                    new SemanticQueryRequest.GroupByItem("salesDate$id", null)));
            req.setTimeWindow(Map.of(
                    "field", "salesDate$id",
                    "grain", "day",
                    "comparison", "rolling_7d",
                    "range", "[)",
                    "value", List.of("-1M", "now"),
                    "targetMetrics", List.of("salesAmount")));
            req.setCalculatedFields(List.of(
                    new CalculatedFieldDef("rollingGap", "salesAmount - salesAmount__rolling_7d")));

            SqlGenerationResult result = semanticQueryServiceV3.generateSql(
                    MODEL, req, SemanticRequestContext.empty());
            assertNotNull(result);
            String sql = result.getSql();
            assertNotNull(sql);
            assertTrue(sql.contains("rollingGap"),
                    "SQL should project rollingGap: " + sql);
            log.info("rolling_7d + rollingGap SQL: {}", sql);
        }

        @Test
        @DisplayName("growthPercent survives execute mode with YoY timeWindow")
        void growthPercentAcceptedInExecuteMode() {
            if (!supportsWindowFunctions()) return;

            SemanticQueryRequest req = new SemanticQueryRequest();
            req.setColumns(List.of(
                    "salesDate$year", "salesDate$month",
                    "salesAmount", "salesAmount__prior",
                    "salesAmount__diff", "salesAmount__ratio",
                    "growthPercent"));
            req.setGroupBy(List.of(
                    new SemanticQueryRequest.GroupByItem("salesDate$year", null),
                    new SemanticQueryRequest.GroupByItem("salesDate$month", null)));
            req.setTimeWindow(Map.of(
                    "field", "salesDate$id",
                    "grain", "month",
                    "comparison", "yoy",
                    "range", "[)",
                    "value", List.of("2024-01-01", "2025-01-01"),
                    "targetMetrics", List.of("salesAmount")));
            req.setCalculatedFields(List.of(
                    new CalculatedFieldDef("growthPercent", "salesAmount__ratio * 100")));
            req.setLimit(20);

            SemanticQueryResponse response = semanticQueryServiceV3.queryModel(
                    MODEL, req, "execute", SemanticRequestContext.empty());
            assertNotNull(response);
            assertNotNull(response.getItems());
            assertFalse(response.getItems().isEmpty(),
                    "execute mode should return rows for the seeded ecommerce dataset");
            assertTrue(response.getItems().get(0).containsKey("growthPercent"),
                    "execute mode should project growthPercent");
        }
    }

    // ------------------------------------------------------------------
    // Negative: unknown columns still rejected
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Negative: still-rejected cases")
    class StillRejected {

        @Test
        @DisplayName("unknown column rejected")
        void unknownColumnRejected() {
            SemanticQueryRequest req = new SemanticQueryRequest();
            req.setColumns(List.of("salesDate$year", "notExistField"));
            req.setGroupBy(List.of(
                    new SemanticQueryRequest.GroupByItem("salesDate$year", null)));

            Exception ex = assertThrows(Exception.class, () ->
                    semanticQueryServiceV3.generateSql(
                            MODEL, req, SemanticRequestContext.empty()));
            assertTrue(ex.getMessage().contains("notExistField"),
                    "error should reference the unknown field: " + ex.getMessage());
        }

        @Test
        @DisplayName("calc alias without matching calculatedField rejected")
        void calcAliasWithoutDefRejected() {
            SemanticQueryRequest req = new SemanticQueryRequest();
            // growthPercent in columns but NO calculatedFields definition
            req.setColumns(List.of("salesDate$year", "salesAmount", "growthPercent"));
            req.setGroupBy(List.of(
                    new SemanticQueryRequest.GroupByItem("salesDate$year", null)));

            Exception ex = assertThrows(Exception.class, () ->
                    semanticQueryServiceV3.generateSql(
                            MODEL, req, SemanticRequestContext.empty()));
            assertTrue(ex.getMessage().contains("growthPercent"),
                    "error should reference growthPercent: " + ex.getMessage());
        }
    }
}
