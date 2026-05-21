package com.foggyframework.dataset.db.model.semantic;

import com.foggyframework.dataset.db.model.ecommerce.EcommerceTestSupport;
import com.foggyframework.dataset.db.model.engine.compose.SqlGenerationResult;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticQueryRequest;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticRequestContext;
import com.foggyframework.dataset.db.model.semantic.service.SemanticQueryServiceV3;
import com.foggyframework.dataset.db.model.spi.JdbcQueryModel;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DslCteCrmFunnelFixtureIntegrationTest extends EcommerceTestSupport {

    @Resource
    private SemanticQueryServiceV3 semanticQueryServiceV3;

    @Test
    @DisplayName("CrmLead TM/QM loads for CRM funnel DSL_CTE fixture")
    void crmLeadModelLoadsForFunnelFixture() {
        JdbcQueryModel queryModel = getQueryModel("CrmLead");

        assertNotNull(queryModel);
        assertEquals("CrmLead", queryModel.getName());
        assertNotNull(queryModel.getColumnGroups());
        assertEquals(2, queryModel.getColumnGroups().size());
        assertEquals(9L, getTableCount("crm_lead"));
    }

    @Test
    @DisplayName("CrmLead funnel manual SQL establishes SQLite parity baseline")
    void crmLeadFunnelManualSqlBaseline() {
        List<Map<String, Object>> rows = crmLeadFunnelManualRows();

        assertEquals(3, rows.size());
        assertCrmFunnelRow(rows.get(0), "APP", 3, 2, 2, 2.0 / 3.0);
        assertCrmFunnelRow(rows.get(1), "PHONE", 2, 1, 0, 0.0);
        assertCrmFunnelRow(rows.get(2), "WEB", 3, 2, 1, 1.0 / 3.0);
    }

    @Test
    @DisplayName("CrmLead converted order ids align to FactOrder business order ids")
    void crmLeadConvertedOrderFixtureAlignsToFactOrderIds() {
        Map<String, Object> row = jdbcTemplate.queryForMap("""
                SELECT COUNT(*) AS convertedOrderRefs,
                       SUM(CASE WHEN fo.order_id IS NOT NULL THEN 1 ELSE 0 END) AS matchedOrderRefs,
                       SUM(CASE WHEN fo.order_status = 'COMPLETED' THEN 1 ELSE 0 END) AS completedOrderRefs
                FROM crm_lead cl
                LEFT JOIN fact_order fo ON cl.converted_order_id = fo.order_id
                WHERE cl.converted_order_id IS NOT NULL
                """);

        assertEquals(4, ((Number) row.get("convertedOrderRefs")).intValue());
        assertEquals(4, ((Number) row.get("matchedOrderRefs")).intValue());
        assertEquals(4, ((Number) row.get("completedOrderRefs")).intValue());
    }

    @Test
    @DisplayName("CrmLead converted order fixture satisfies many-to-one runtime guard baseline")
    void crmLeadConvertedOrderFixtureSatisfiesRuntimeCardinalityGuardBaseline() {
        Map<String, Object> row = jdbcTemplate.queryForMap("""
                SELECT
                    (SELECT COUNT(*)
                     FROM (
                         SELECT order_id
                         FROM fact_order
                         WHERE order_status = 'COMPLETED'
                         GROUP BY order_id
                         HAVING COUNT(*) > 1
                     ) duplicate_right_keys) AS duplicateRightKeys,
                    (SELECT COUNT(*)
                     FROM crm_lead cl
                     LEFT JOIN fact_order fo
                            ON cl.converted_order_id = fo.order_id
                           AND fo.order_status = 'COMPLETED'
                     WHERE cl.converted_order_id IS NOT NULL
                       AND fo.order_id IS NULL) AS unmatchedConvertedKeys,
                    (SELECT COUNT(*)
                     FROM crm_lead
                     WHERE converted_order_id IS NULL) AS excludedNullLeftKeys
                """);

        assertEquals(0, ((Number) row.get("duplicateRightKeys")).intValue());
        assertEquals(0, ((Number) row.get("unmatchedConvertedKeys")).intValue());
        assertEquals(5, ((Number) row.get("excludedNullLeftKeys")).intValue());
    }

    @Test
    @DisplayName("CrmLead converted order fixture satisfies lead-created time attribution guard baseline")
    void crmLeadConvertedOrderFixtureSatisfiesRuntimeTimeAttributionGuardBaseline() {
        Map<String, Object> row = jdbcTemplate.queryForMap("""
                SELECT COUNT(*) AS matchedConvertedRows,
                       SUM(CASE WHEN cl.created_at IS NOT NULL THEN 1 ELSE 0 END) AS attributedRows,
                       SUM(CASE WHEN cl.created_at IS NULL THEN 1 ELSE 0 END) AS missingAttributionRows
                FROM crm_lead cl
                JOIN fact_order fo
                  ON cl.converted_order_id = fo.order_id
                 AND fo.order_status = 'COMPLETED'
                WHERE cl.converted_order_id IS NOT NULL
                """);

        assertEquals(4, ((Number) row.get("matchedConvertedRows")).intValue());
        assertEquals(4, ((Number) row.get("attributedRows")).intValue());
        assertEquals(0, ((Number) row.get("missingAttributionRows")).intValue());
    }

    @Test
    @DisplayName("DSL_CTE cross-model join_align bridge executes guarded SQL and matches manual baseline")
    void crossModelCrmOrderJoinAlignBridgeSqlMatchesManualBaseline() {
        List<Map<String, Object>> manualRows = crossModelCrmOrderJoinAlignManualRows();
        SemanticQueryRequest request = dslCtePlan(crossModelCrmOrderJoinAlignBridgePlan());
        request.setHints(Map.of("dslCteCompileToDsl", true));

        SqlGenerationResult generated = semanticQueryServiceV3.generateSql(
                "CrmLead", request, SemanticRequestContext.empty());

        assertNotNull(generated);
        assertNotNull(generated.getSql());
        assertTrue(generated.getSql().contains("dsl_cte_join_guard"), generated.getSql());
        assertTrue(generated.getSql().contains("dsl_cte_join_align"), generated.getSql());
        assertTrue(generated.getSql().contains("duplicateRightKeys"), generated.getSql());
        assertTrue(generated.getSql().contains("missingAttributionRows"), generated.getSql());

        List<Map<String, Object>> rows = new ArrayList<>(jdbcTemplate.queryForList(
                generated.getSql(), generated.getParams().toArray(new Object[0])));
        rows.sort(Comparator
                .comparing((Map<String, Object> row) -> String.valueOf(value(row, "leadSource")))
                .thenComparing(row -> String.valueOf(value(row, "convertedOrderId"))));

        assertEquals(manualRows.size(), rows.size());
        for (int i = 0; i < manualRows.size(); i++) {
            assertGeneratedCrossModelJoinAlignRowMatchesManual(rows.get(i), manualRows.get(i));
        }
    }

    @Test
    @DisplayName("DSL_CTE cross-model source-rate bridge executes guarded SQL and matches manual baseline")
    void crossModelCrmOrderSourceRateBridgeSqlMatchesManualBaseline() {
        List<Map<String, Object>> manualRows = crossModelCrmOrderSourceRateManualRows();
        SemanticQueryRequest request = dslCtePlan(crossModelCrmOrderSourceRateBridgePlan());
        request.setHints(Map.of("dslCteCompileToDsl", true));

        SqlGenerationResult generated = semanticQueryServiceV3.generateSql(
                "CrmLead", request, SemanticRequestContext.empty());

        assertNotNull(generated);
        assertNotNull(generated.getSql());
        assertTrue(generated.getSql().contains("dsl_cte_funnel_denominator"), generated.getSql());
        assertTrue(generated.getSql().contains("dsl_cte_join_guard"), generated.getSql());
        assertTrue(generated.getSql().contains("dsl_cte_funnel_matched"), generated.getSql());
        assertTrue(generated.getSql().contains("dsl_cte_funnel_rate"), generated.getSql());
        assertTrue(generated.getSql().contains("leadToOrderConversionRate"), generated.getSql());

        List<Map<String, Object>> rows = new ArrayList<>(jdbcTemplate.queryForList(
                generated.getSql(), generated.getParams().toArray(new Object[0])));
        rows.sort(Comparator.comparing(row -> String.valueOf(value(row, "leadSource"))));

        assertEquals(manualRows.size(), rows.size());
        for (int i = 0; i < manualRows.size(); i++) {
            assertGeneratedCrossModelSourceRateRowMatchesManual(rows.get(i), manualRows.get(i));
        }
    }

    @Test
    @DisplayName("DSL_CTE cross-model target-event window bridge executes guarded SQL and matches manual baseline")
    void crossModelCrmOrderTimeAttributionBridgeSqlMatchesManualBaseline() {
        insertTimeAttributionFixture();
        try {
            List<Map<String, Object>> manualRows = crossModelCrmOrderTimeAttributionManualRows();
            SemanticQueryRequest request = dslCtePlan(crossModelCrmOrderTimeAttributionBridgePlan());
            request.setHints(Map.of("dslCteCompileToDsl", true));

            SqlGenerationResult generated = semanticQueryServiceV3.generateSql(
                    "CrmLead", request, SemanticRequestContext.empty());

            assertNotNull(generated);
            assertNotNull(generated.getSql());
            assertTrue(generated.getSql().contains("dsl_cte_funnel_denominator"), generated.getSql());
            assertTrue(generated.getSql().contains("dsl_cte_funnel_window_matched"), generated.getSql());
            assertTrue(generated.getSql().contains("targetBeforeSourceRows"), generated.getSql());
            assertTrue(generated.getSql().contains("date(r.\"orderDate$caption\") < date(l.\"createdAt\", '+' || ? || ' days')"),
                    generated.getSql());
            assertEquals(List.of(
                    "2026-07-01 00:00:00",
                    "2026-08-01 00:00:00",
                    "2026-07-01 00:00:00",
                    "2026-08-01 00:00:00",
                    "COMPLETED",
                    30), generated.getParams());

            List<Map<String, Object>> rows = new ArrayList<>(jdbcTemplate.queryForList(
                    generated.getSql(), generated.getParams().toArray(new Object[0])));
            rows.sort(Comparator.comparing(row -> String.valueOf(value(row, "leadSource"))));

            assertEquals(manualRows.size(), rows.size());
            for (int i = 0; i < manualRows.size(); i++) {
                assertGeneratedCrossModelSourceRateRowMatchesManual(rows.get(i), manualRows.get(i));
            }
        } finally {
            deleteTimeAttributionFixture();
        }
    }

    @Test
    @DisplayName("DSL_CTE cross-model target-month attribution bridge executes guarded SQL and matches manual baseline")
    void crossModelCrmOrderTargetMonthAttributionBridgeSqlMatchesManualBaseline() {
        insertTimeAttributionFixture();
        try {
            List<Map<String, Object>> manualRows = crossModelCrmOrderTargetMonthAttributionManualRows();
            SemanticQueryRequest request = dslCtePlan(crossModelCrmOrderTargetMonthAttributionBridgePlan());
            request.setHints(Map.of("dslCteCompileToDsl", true));

            SqlGenerationResult generated = semanticQueryServiceV3.generateSql(
                    "CrmLead", request, SemanticRequestContext.empty());

            assertNotNull(generated);
            assertNotNull(generated.getSql());
            assertTrue(generated.getSql().contains("dsl_cte_funnel_denominator"), generated.getSql());
            assertTrue(generated.getSql().contains("dsl_cte_funnel_window_matched"), generated.getSql());
            assertTrue(generated.getSql().contains("\"orderDate$month\""), generated.getSql());
            assertTrue(generated.getSql().contains(
                    "JOIN dsl_cte_funnel_window_matched m ON d.\"leadSource\" = m.\"leadSource\""),
                    generated.getSql());
            assertTrue(generated.getSql().contains("ORDER BY \"leadSource\" ASC, \"orderDate$month\" ASC"),
                    generated.getSql());

            List<Map<String, Object>> rows = new ArrayList<>(jdbcTemplate.queryForList(
                    generated.getSql(), generated.getParams().toArray(new Object[0])));
            rows.sort(Comparator
                    .comparing((Map<String, Object> row) -> String.valueOf(value(row, "leadSource")))
                    .thenComparing(row -> ((Number) value(row, "orderDate$month")).intValue()));

            assertEquals(manualRows.size(), rows.size());
            for (int i = 0; i < manualRows.size(); i++) {
                assertGeneratedCrossModelTargetMonthAttributionRowMatchesManual(rows.get(i), manualRows.get(i));
            }
        } finally {
            deleteTimeAttributionFixture();
        }
    }

    @Test
    @DisplayName("DSL_CTE cross-model target year-month zero-fill calendar bridge executes guarded SQL")
    void crossModelCrmOrderTargetYearMonthZeroFillCalendarBridgeSqlMatchesManualBaseline() {
        insertTimeAttributionFixture();
        try {
            List<Map<String, Object>> manualRows =
                    crossModelCrmOrderTargetYearMonthZeroFillCalendarManualRows();
            SemanticQueryRequest request =
                    dslCtePlan(crossModelCrmOrderTargetYearMonthZeroFillCalendarBridgePlan());
            request.setHints(Map.of("dslCteCompileToDsl", true));

            SqlGenerationResult generated = semanticQueryServiceV3.generateSql(
                    "CrmLead", request, SemanticRequestContext.empty());

            assertNotNull(generated);
            assertNotNull(generated.getSql());
            assertTrue(generated.getSql().contains("dsl_cte_calendar_periods"), generated.getSql());
            assertTrue(generated.getSql().contains("dsl_cte_source_period_grid"), generated.getSql());
            assertTrue(generated.getSql().contains("LEFT JOIN dsl_cte_funnel_window_matched m"),
                    generated.getSql());
            assertTrue(generated.getSql().contains("COALESCE(m.\"matchedLeadCount\", 0) AS \"matchedLeadCount\""),
                    generated.getSql());

            List<Map<String, Object>> rows = new ArrayList<>(jdbcTemplate.queryForList(
                    generated.getSql(), generated.getParams().toArray(new Object[0])));
            rows.sort(targetYearMonthOrder());

            assertEquals(manualRows.size(), rows.size());
            for (int i = 0; i < manualRows.size(); i++) {
                assertGeneratedCrossModelTargetYearMonthAttributionRowMatchesManual(rows.get(i), manualRows.get(i));
            }
        } finally {
            deleteTimeAttributionFixture();
        }
    }

    @Test
    @DisplayName("DSL_CTE CRM lead funnel bridge executes and matches manual baseline")
    void crmLeadFunnelBridgeSqlMatchesManualBaseline() {
        List<Map<String, Object>> manualRows = crmLeadFunnelManualRows();
        SemanticQueryRequest request = dslCtePlan(crmLeadFunnelPlan());
        request.setHints(Map.of("dslCteCompileToDsl", true));

        SqlGenerationResult generated = semanticQueryServiceV3.generateSql(
                "CrmLead", request, SemanticRequestContext.empty());

        assertNotNull(generated);
        assertNotNull(generated.getSql());
        assertTrue(generated.getSql().contains("dsl_cte_metric_ratio"), generated.getSql());
        assertTrue(generated.getSql().contains("leadToOrderConversionRate"), generated.getSql());

        List<Map<String, Object>> rows = new ArrayList<>(jdbcTemplate.queryForList(
                generated.getSql(), generated.getParams().toArray(new Object[0])));
        rows.sort(Comparator.comparing(row -> String.valueOf(value(row, "leadSource"))));

        assertEquals(manualRows.size(), rows.size());
        for (int i = 0; i < manualRows.size(); i++) {
            assertGeneratedCrmFunnelRowMatchesManual(rows.get(i), manualRows.get(i));
        }
    }

    @Test
    @DisplayName("DSL_CTE CRM lead funnel drop-off bridge executes and matches manual baseline")
    void crmLeadFunnelDropOffBridgeSqlMatchesManualBaseline() {
        List<Map<String, Object>> manualRows = crmLeadFunnelDropOffManualRows();
        SemanticQueryRequest request = dslCtePlan(crmLeadFunnelDropOffPlan());
        request.setHints(Map.of("dslCteCompileToDsl", true));

        SqlGenerationResult generated = semanticQueryServiceV3.generateSql(
                "CrmLead", request, SemanticRequestContext.empty());

        assertNotNull(generated);
        assertNotNull(generated.getSql());
        assertTrue(generated.getSql().contains("opportunityDropOffCount"), generated.getSql());
        assertTrue(generated.getSql().contains("opportunityToOrderDropOffRate"), generated.getSql());

        List<Map<String, Object>> rows = new ArrayList<>(jdbcTemplate.queryForList(
                generated.getSql(), generated.getParams().toArray(new Object[0])));
        rows.sort(Comparator.comparing(row -> String.valueOf(value(row, "leadSource"))));

        assertEquals(manualRows.size(), rows.size());
        for (int i = 0; i < manualRows.size(); i++) {
            assertGeneratedCrmFunnelDropOffRowMatchesManual(rows.get(i), manualRows.get(i));
        }
    }

    @Test
    @DisplayName("DSL_CTE CRM lead funnel drop-off leaderboard executes and matches manual baseline")
    void crmLeadFunnelDropOffLeaderboardBridgeSqlMatchesManualBaseline() {
        List<Map<String, Object>> manualRows = crmLeadFunnelDropOffManualRows();
        manualRows.sort(Comparator.<Map<String, Object>>comparingDouble(row ->
                        ((Number) row.get("opportunityToOrderDropOffRate")).doubleValue())
                .reversed()
                .thenComparing(row -> String.valueOf(row.get("leadSource"))));
        manualRows = manualRows.subList(0, 2);

        SemanticQueryRequest request = dslCtePlan(crmLeadFunnelDropOffLeaderboardPlan());
        request.setHints(Map.of("dslCteCompileToDsl", true));

        SqlGenerationResult generated = semanticQueryServiceV3.generateSql(
                "CrmLead", request, SemanticRequestContext.empty());

        assertNotNull(generated);
        assertNotNull(generated.getSql());
        assertTrue(generated.getSql().contains(
                "ORDER BY \"opportunityToOrderDropOffRate\" DESC, \"leadSource\" ASC"), generated.getSql());
        assertTrue(generated.getSql().contains("LIMIT ?"), generated.getSql());

        List<Map<String, Object>> rows = new ArrayList<>(jdbcTemplate.queryForList(
                generated.getSql(), generated.getParams().toArray(new Object[0])));

        assertEquals(manualRows.size(), rows.size());
        for (int i = 0; i < manualRows.size(); i++) {
            assertGeneratedCrmFunnelDropOffRowMatchesManual(rows.get(i), manualRows.get(i));
        }
    }

    private List<Map<String, Object>> crmLeadFunnelManualRows() {
        List<Map<String, Object>> rows = new ArrayList<>(jdbcTemplate.queryForList("""
                SELECT lead_source AS leadSource,
                       COUNT(*) AS leadCount,
                       SUM(CASE WHEN converted_opportunity_id IS NOT NULL THEN 1 ELSE 0 END) AS convertedOpportunityCount,
                       SUM(CASE WHEN converted_order_id IS NOT NULL THEN 1 ELSE 0 END) AS convertedOrderCount,
                       1.0 * SUM(CASE WHEN converted_order_id IS NOT NULL THEN 1 ELSE 0 END)
                           / NULLIF(COUNT(*), 0) AS leadToOrderConversionRate
                FROM crm_lead
                WHERE created_at >= '2026-05-01 00:00:00'
                  AND created_at < '2026-06-01 00:00:00'
                GROUP BY lead_source
                ORDER BY leadSource
                """));
        rows.sort(Comparator.comparing(row -> String.valueOf(row.get("leadSource"))));
        return rows;
    }

    private List<Map<String, Object>> crmLeadFunnelDropOffManualRows() {
        List<Map<String, Object>> rows = new ArrayList<>(jdbcTemplate.queryForList("""
                SELECT lead_source AS leadSource,
                       COUNT(*) AS leadCount,
                       SUM(CASE WHEN converted_opportunity_id IS NOT NULL THEN 1 ELSE 0 END) AS convertedOpportunityCount,
                       SUM(CASE WHEN converted_order_id IS NOT NULL THEN 1 ELSE 0 END) AS convertedOrderCount,
                       SUM(CASE WHEN converted_opportunity_id IS NOT NULL THEN 1 ELSE 0 END)
                           - SUM(CASE WHEN converted_order_id IS NOT NULL THEN 1 ELSE 0 END) AS opportunityDropOffCount,
                       1.0 * (
                           SUM(CASE WHEN converted_opportunity_id IS NOT NULL THEN 1 ELSE 0 END)
                           - SUM(CASE WHEN converted_order_id IS NOT NULL THEN 1 ELSE 0 END)
                       ) / NULLIF(SUM(CASE WHEN converted_opportunity_id IS NOT NULL THEN 1 ELSE 0 END), 0)
                           AS opportunityToOrderDropOffRate
                FROM crm_lead
                WHERE created_at >= '2026-05-01 00:00:00'
                  AND created_at < '2026-06-01 00:00:00'
                GROUP BY lead_source
                ORDER BY leadSource
                """));
        rows.sort(Comparator.comparing(row -> String.valueOf(row.get("leadSource"))));
        return rows;
    }

    private List<Map<String, Object>> crossModelCrmOrderJoinAlignManualRows() {
        List<Map<String, Object>> rows = new ArrayList<>(jdbcTemplate.queryForList("""
                WITH lead_orders AS (
                    SELECT lead_source AS leadSource,
                           converted_order_id AS convertedOrderId,
                           created_at AS createdAt,
                           COUNT(lead_id) AS leadCount
                    FROM crm_lead
                    WHERE created_at >= '2026-05-01 00:00:00'
                      AND created_at < '2026-06-01 00:00:00'
                    GROUP BY lead_source, converted_order_id, created_at
                ),
                completed_orders AS (
                    SELECT order_id AS orderId,
                           COUNT(order_id) AS matchedOrderCount
                    FROM fact_order
                    WHERE order_status = 'COMPLETED'
                    GROUP BY order_id
                )
                SELECT l.leadSource AS leadSource,
                       l.convertedOrderId AS convertedOrderId,
                       l.leadCount AS leadCount,
                       r.orderId AS orderId,
                       r.matchedOrderCount AS matchedOrderCount
                FROM lead_orders l
                JOIN completed_orders r
                  ON l.convertedOrderId = r.orderId
                WHERE l.convertedOrderId IS NOT NULL
                ORDER BY leadSource, convertedOrderId
                """));
        rows.sort(Comparator
                .comparing((Map<String, Object> row) -> String.valueOf(row.get("leadSource")))
                .thenComparing(row -> String.valueOf(row.get("convertedOrderId"))));
        return rows;
    }

    private List<Map<String, Object>> crossModelCrmOrderSourceRateManualRows() {
        List<Map<String, Object>> rows = new ArrayList<>(jdbcTemplate.queryForList("""
                WITH source_total_leads AS (
                    SELECT lead_source AS leadSource,
                           COUNT(lead_id) AS totalLeadCount
                    FROM crm_lead
                    WHERE created_at >= '2026-05-01 00:00:00'
                      AND created_at < '2026-06-01 00:00:00'
                    GROUP BY lead_source
                ),
                lead_orders AS (
                    SELECT lead_source AS leadSource,
                           converted_order_id AS convertedOrderId,
                           created_at AS createdAt,
                           COUNT(lead_id) AS leadCount
                    FROM crm_lead
                    WHERE created_at >= '2026-05-01 00:00:00'
                      AND created_at < '2026-06-01 00:00:00'
                    GROUP BY lead_source, converted_order_id, created_at
                ),
                completed_orders AS (
                    SELECT order_id AS orderId,
                           COUNT(order_id) AS matchedOrderCount
                    FROM fact_order
                    WHERE order_status = 'COMPLETED'
                    GROUP BY order_id
                ),
                source_matched_orders AS (
                    SELECT l.leadSource AS leadSource,
                           SUM(l.leadCount) AS matchedLeadCount
                    FROM lead_orders l
                    JOIN completed_orders r
                      ON l.convertedOrderId = r.orderId
                    WHERE l.convertedOrderId IS NOT NULL
                    GROUP BY l.leadSource
                )
                SELECT d.leadSource AS leadSource,
                       d.totalLeadCount AS totalLeadCount,
                       COALESCE(m.matchedLeadCount, 0) AS matchedLeadCount,
                       1.0 * COALESCE(m.matchedLeadCount, 0)
                           / NULLIF(d.totalLeadCount, 0) AS leadToOrderConversionRate
                FROM source_total_leads d
                LEFT JOIN source_matched_orders m
                  ON d.leadSource = m.leadSource
                ORDER BY leadSource
                """));
        rows.sort(Comparator.comparing(row -> String.valueOf(row.get("leadSource"))));
        return rows;
    }

    private List<Map<String, Object>> crossModelCrmOrderTimeAttributionManualRows() {
        List<Map<String, Object>> rows = new ArrayList<>(jdbcTemplate.queryForList("""
                WITH source_total_leads AS (
                    SELECT lead_source AS leadSource,
                           COUNT(lead_id) AS totalLeadCount
                    FROM crm_lead
                    WHERE created_at >= '2026-07-01 00:00:00'
                      AND created_at < '2026-08-01 00:00:00'
                    GROUP BY lead_source
                ),
                lead_orders AS (
                    SELECT lead_source AS leadSource,
                           converted_order_id AS convertedOrderId,
                           created_at AS createdAt,
                           COUNT(lead_id) AS leadCount
                    FROM crm_lead
                    WHERE created_at >= '2026-07-01 00:00:00'
                      AND created_at < '2026-08-01 00:00:00'
                    GROUP BY lead_source, converted_order_id, created_at
                ),
                completed_orders AS (
                    SELECT fo.order_id AS orderId,
                           dd.full_date AS "orderDate$caption",
                           COUNT(fo.order_id) AS matchedOrderCount
                    FROM fact_order fo
                    JOIN dim_date dd ON fo.date_key = dd.date_key
                    WHERE fo.order_status = 'COMPLETED'
                    GROUP BY fo.order_id, dd.full_date
                ),
                source_matched_orders AS (
                    SELECT l.leadSource AS leadSource,
                           SUM(l.leadCount) AS matchedLeadCount
                    FROM lead_orders l
                    JOIN completed_orders r
                      ON l.convertedOrderId = r.orderId
                    WHERE l.convertedOrderId IS NOT NULL
                      AND date(r."orderDate$caption") >= date(l.createdAt)
                      AND date(r."orderDate$caption") < date(l.createdAt, '+30 days')
                    GROUP BY l.leadSource
                )
                SELECT d.leadSource AS leadSource,
                       d.totalLeadCount AS totalLeadCount,
                       COALESCE(m.matchedLeadCount, 0) AS matchedLeadCount,
                       1.0 * COALESCE(m.matchedLeadCount, 0)
                           / NULLIF(d.totalLeadCount, 0) AS leadToOrderConversionRate
                FROM source_total_leads d
                LEFT JOIN source_matched_orders m
                  ON d.leadSource = m.leadSource
                ORDER BY leadSource
                """));
        rows.sort(Comparator.comparing(row -> String.valueOf(row.get("leadSource"))));
        return rows;
    }

    private List<Map<String, Object>> crossModelCrmOrderTargetMonthAttributionManualRows() {
        List<Map<String, Object>> rows = new ArrayList<>(jdbcTemplate.queryForList("""
                WITH source_total_leads AS (
                    SELECT lead_source AS leadSource,
                           COUNT(lead_id) AS totalLeadCount
                    FROM crm_lead
                    WHERE created_at >= '2026-07-01 00:00:00'
                      AND created_at < '2026-08-01 00:00:00'
                    GROUP BY lead_source
                ),
                lead_orders AS (
                    SELECT lead_source AS leadSource,
                           converted_order_id AS convertedOrderId,
                           created_at AS createdAt,
                           COUNT(lead_id) AS leadCount
                    FROM crm_lead
                    WHERE created_at >= '2026-07-01 00:00:00'
                      AND created_at < '2026-08-01 00:00:00'
                    GROUP BY lead_source, converted_order_id, created_at
                ),
                completed_orders AS (
                    SELECT fo.order_id AS orderId,
                           dd.full_date AS "orderDate$caption",
                           dd.month AS "orderDate$month",
                           COUNT(fo.order_id) AS matchedOrderCount
                    FROM fact_order fo
                    JOIN dim_date dd ON fo.date_key = dd.date_key
                    WHERE fo.order_status = 'COMPLETED'
                    GROUP BY fo.order_id, dd.full_date, dd.month
                ),
                source_matched_orders AS (
                    SELECT l.leadSource AS leadSource,
                           r."orderDate$month" AS "orderDate$month",
                           SUM(l.leadCount) AS matchedLeadCount
                    FROM lead_orders l
                    JOIN completed_orders r
                      ON l.convertedOrderId = r.orderId
                    WHERE l.convertedOrderId IS NOT NULL
                      AND date(r."orderDate$caption") >= date(l.createdAt)
                      AND date(r."orderDate$caption") < date(l.createdAt, '+30 days')
                    GROUP BY l.leadSource, r."orderDate$month"
                )
                SELECT d.leadSource AS leadSource,
                       m."orderDate$month" AS "orderDate$month",
                       d.totalLeadCount AS totalLeadCount,
                       COALESCE(m.matchedLeadCount, 0) AS matchedLeadCount,
                       1.0 * COALESCE(m.matchedLeadCount, 0)
                           / NULLIF(d.totalLeadCount, 0) AS leadToOrderConversionRate
                FROM source_total_leads d
                JOIN source_matched_orders m
                  ON d.leadSource = m.leadSource
                ORDER BY leadSource, "orderDate$month"
                """));
        rows.sort(Comparator
                .comparing((Map<String, Object> row) -> String.valueOf(row.get("leadSource")))
                .thenComparing(row -> ((Number) row.get("orderDate$month")).intValue()));
        return rows;
    }

    private List<Map<String, Object>> crossModelCrmOrderTargetYearMonthZeroFillCalendarManualRows() {
        List<Map<String, Object>> rows = new ArrayList<>(jdbcTemplate.queryForList("""
                WITH source_total_leads AS (
                    SELECT lead_source AS leadSource,
                           COUNT(lead_id) AS totalLeadCount
                    FROM crm_lead
                    WHERE created_at >= '2026-07-01 00:00:00'
                      AND created_at < '2026-08-01 00:00:00'
                    GROUP BY lead_source
                ),
                lead_orders AS (
                    SELECT lead_source AS leadSource,
                           converted_order_id AS convertedOrderId,
                           created_at AS createdAt,
                           COUNT(lead_id) AS leadCount
                    FROM crm_lead
                    WHERE created_at >= '2026-07-01 00:00:00'
                      AND created_at < '2026-08-01 00:00:00'
                    GROUP BY lead_source, converted_order_id, created_at
                ),
                completed_orders AS (
                    SELECT fo.order_id AS orderId,
                           dd.full_date AS "orderDate$caption",
                           dd.year AS "orderDate$year",
                           dd.month AS "orderDate$month",
                           COUNT(fo.order_id) AS matchedOrderCount
                    FROM fact_order fo
                    JOIN dim_date dd ON fo.date_key = dd.date_key
                    WHERE fo.order_status = 'COMPLETED'
                    GROUP BY fo.order_id, dd.full_date, dd.year, dd.month
                ),
                source_matched_orders AS (
                    SELECT l.leadSource AS leadSource,
                           r."orderDate$year" AS "orderDate$year",
                           r."orderDate$month" AS "orderDate$month",
                           SUM(l.leadCount) AS matchedLeadCount
                    FROM lead_orders l
                    JOIN completed_orders r
                      ON l.convertedOrderId = r.orderId
                    WHERE l.convertedOrderId IS NOT NULL
                      AND date(r."orderDate$caption") >= date(l.createdAt)
                      AND date(r."orderDate$caption") < date(l.createdAt, '+30 days')
                    GROUP BY l.leadSource, r."orderDate$year", r."orderDate$month"
                ),
                calendar_periods AS (
                    SELECT 2026 AS "orderDate$year", 7 AS "orderDate$month"
                    UNION ALL
                    SELECT 2026 AS "orderDate$year", 8 AS "orderDate$month"
                    UNION ALL
                    SELECT 2026 AS "orderDate$year", 9 AS "orderDate$month"
                ),
                source_period_grid AS (
                    SELECT d.leadSource AS leadSource,
                           c."orderDate$year" AS "orderDate$year",
                           c."orderDate$month" AS "orderDate$month",
                           d.totalLeadCount AS totalLeadCount
                    FROM source_total_leads d
                    CROSS JOIN calendar_periods c
                )
                SELECT sp.leadSource AS leadSource,
                       sp."orderDate$year" AS "orderDate$year",
                       sp."orderDate$month" AS "orderDate$month",
                       sp.totalLeadCount AS totalLeadCount,
                       COALESCE(m.matchedLeadCount, 0) AS matchedLeadCount,
                       1.0 * COALESCE(m.matchedLeadCount, 0)
                           / NULLIF(sp.totalLeadCount, 0) AS leadToOrderConversionRate
                FROM source_period_grid sp
                LEFT JOIN source_matched_orders m
                  ON sp.leadSource = m.leadSource
                 AND sp."orderDate$year" = m."orderDate$year"
                 AND sp."orderDate$month" = m."orderDate$month"
                ORDER BY leadSource, "orderDate$year", "orderDate$month"
                """));
        rows.sort(targetYearMonthOrder());
        return rows;
    }

    private void insertTimeAttributionFixture() {
        deleteTimeAttributionFixture();
        jdbcTemplate.update("""
                INSERT INTO dim_date
                    (date_key, full_date, year, quarter, month, month_name, week_of_year, day_of_month,
                     day_of_week, day_name, is_weekend, is_holiday, fiscal_year, fiscal_quarter)
                VALUES
                    (20260702, '2026-07-02', 2026, 3, 7, '七月', 27, 2, 4, '星期四', 0, 0, 2026, 3),
                    (20260710, '2026-07-10', 2026, 3, 7, '七月', 28, 10, 5, '星期五', 0, 0, 2026, 3),
                    (20260810, '2026-08-10', 2026, 3, 8, '八月', 33, 10, 1, '星期一', 0, 0, 2026, 3),
                    (20260820, '2026-08-20', 2026, 3, 8, '八月', 34, 20, 4, '星期四', 0, 0, 2026, 3)
                """);
        jdbcTemplate.update("""
                INSERT INTO fact_order
                    (order_id, date_key, customer_key, store_key, channel_key, promotion_key, total_quantity,
                     total_amount, discount_amount, freight_amount, pay_amount, order_status, payment_status,
                     order_time)
                VALUES
                    ('P031-ORD-001', 20260702, 1, 1, 1, 1, 1, 100.00, 0, 0, 100.00,
                     'COMPLETED', 'PAID', '2026-07-02 10:00:00'),
                    ('P031-ORD-002', 20260820, 2, 2, 2, 1, 1, 200.00, 0, 0, 200.00,
                     'COMPLETED', 'PAID', '2026-08-20 10:00:00'),
                    ('P031-ORD-003', 20260710, 3, 3, 1, 1, 1, 300.00, 0, 0, 300.00,
                     'COMPLETED', 'PAID', '2026-07-10 10:00:00'),
                    ('P031-ORD-004', 20260810, 3, 3, 1, 1, 1, 400.00, 0, 0, 400.00,
                     'COMPLETED', 'PAID', '2026-08-10 10:00:00')
                """);
        jdbcTemplate.update("""
                INSERT INTO crm_lead (lead_id, created_at, lead_source, converted_opportunity_id, converted_order_id)
                VALUES
                    ('P031-CRM-001', '2026-07-01 09:00:00', 'WEB', 'P031-OPP-001', 'P031-ORD-001'),
                    ('P031-CRM-002', '2026-07-03 10:00:00', 'WEB', 'P031-OPP-002', 'P031-ORD-002'),
                    ('P031-CRM-003', '2026-07-04 11:00:00', 'APP', 'P031-OPP-003', 'P031-ORD-003'),
                    ('P031-CRM-004', '2026-07-05 12:00:00', 'APP', NULL, NULL),
                    ('P031-CRM-005', '2026-07-06 13:00:00', 'PHONE', NULL, NULL),
                    ('P031-CRM-006', '2026-07-25 13:00:00', 'APP', 'P031-OPP-006', 'P031-ORD-004')
                """);
    }

    private void deleteTimeAttributionFixture() {
        jdbcTemplate.update("DELETE FROM crm_lead WHERE lead_id LIKE 'P031-CRM-%'");
        jdbcTemplate.update("DELETE FROM fact_order WHERE order_id LIKE 'P031-ORD-%'");
        jdbcTemplate.update("DELETE FROM dim_date WHERE date_key IN (20260702, 20260710, 20260810, 20260820)");
    }

    private static void assertGeneratedCrmFunnelRowMatchesManual(Map<String, Object> generated,
                                                                 Map<String, Object> manual) {
        assertEquals(manual.get("leadSource"), value(generated, "leadSource"));
        assertEquals(((Number) manual.get("leadCount")).intValue(),
                ((Number) value(generated, "leadCount")).intValue());
        assertEquals(((Number) manual.get("convertedOpportunityCount")).intValue(),
                ((Number) value(generated, "convertedOpportunityCount")).intValue());
        assertEquals(((Number) manual.get("convertedOrderCount")).intValue(),
                ((Number) value(generated, "convertedOrderCount")).intValue());
        assertClose(((Number) manual.get("leadToOrderConversionRate")).doubleValue(),
                ((Number) value(generated, "leadToOrderConversionRate")).doubleValue());
    }

    private static void assertGeneratedCrmFunnelDropOffRowMatchesManual(Map<String, Object> generated,
                                                                        Map<String, Object> manual) {
        assertGeneratedCrmFunnelCountsMatchManual(generated, manual);
        assertEquals(((Number) manual.get("opportunityDropOffCount")).intValue(),
                ((Number) value(generated, "opportunityDropOffCount")).intValue());
        assertClose(((Number) manual.get("opportunityToOrderDropOffRate")).doubleValue(),
                ((Number) value(generated, "opportunityToOrderDropOffRate")).doubleValue());
    }

    private static void assertGeneratedCrossModelJoinAlignRowMatchesManual(Map<String, Object> generated,
                                                                           Map<String, Object> manual) {
        assertEquals(manual.get("leadSource"), value(generated, "leadSource"));
        assertEquals(manual.get("convertedOrderId"), value(generated, "convertedOrderId"));
        assertEquals(((Number) manual.get("leadCount")).intValue(),
                ((Number) value(generated, "leadCount")).intValue());
        assertEquals(manual.get("orderId"), value(generated, "orderId"));
        assertEquals(((Number) manual.get("matchedOrderCount")).intValue(),
                ((Number) value(generated, "matchedOrderCount")).intValue());
    }

    private static void assertGeneratedCrossModelSourceRateRowMatchesManual(Map<String, Object> generated,
                                                                            Map<String, Object> manual) {
        assertEquals(manual.get("leadSource"), value(generated, "leadSource"));
        assertEquals(((Number) manual.get("totalLeadCount")).intValue(),
                ((Number) value(generated, "totalLeadCount")).intValue());
        assertEquals(((Number) manual.get("matchedLeadCount")).intValue(),
                ((Number) value(generated, "matchedLeadCount")).intValue());
        assertClose(((Number) manual.get("leadToOrderConversionRate")).doubleValue(),
                ((Number) value(generated, "leadToOrderConversionRate")).doubleValue());
    }

    private static void assertGeneratedCrossModelTargetMonthAttributionRowMatchesManual(
            Map<String, Object> generated,
            Map<String, Object> manual) {
        assertGeneratedCrossModelSourceRateRowMatchesManual(generated, manual);
        assertEquals(((Number) manual.get("orderDate$month")).intValue(),
                ((Number) value(generated, "orderDate$month")).intValue());
    }

    private static void assertGeneratedCrossModelTargetYearMonthAttributionRowMatchesManual(
            Map<String, Object> generated,
            Map<String, Object> manual) {
        assertGeneratedCrossModelSourceRateRowMatchesManual(generated, manual);
        assertEquals(((Number) manual.get("orderDate$year")).intValue(),
                ((Number) value(generated, "orderDate$year")).intValue());
        assertEquals(((Number) manual.get("orderDate$month")).intValue(),
                ((Number) value(generated, "orderDate$month")).intValue());
    }

    private static void assertGeneratedCrmFunnelCountsMatchManual(Map<String, Object> generated,
                                                                  Map<String, Object> manual) {
        assertEquals(manual.get("leadSource"), value(generated, "leadSource"));
        assertEquals(((Number) manual.get("leadCount")).intValue(),
                ((Number) value(generated, "leadCount")).intValue());
        assertEquals(((Number) manual.get("convertedOpportunityCount")).intValue(),
                ((Number) value(generated, "convertedOpportunityCount")).intValue());
        assertEquals(((Number) manual.get("convertedOrderCount")).intValue(),
                ((Number) value(generated, "convertedOrderCount")).intValue());
    }

    private static void assertCrmFunnelRow(Map<String, Object> row, String leadSource, int leadCount,
                                           int convertedOpportunityCount, int convertedOrderCount,
                                           double leadToOrderConversionRate) {
        assertEquals(leadSource, row.get("leadSource"));
        assertEquals(leadCount, ((Number) row.get("leadCount")).intValue());
        assertEquals(convertedOpportunityCount, ((Number) row.get("convertedOpportunityCount")).intValue());
        assertEquals(convertedOrderCount, ((Number) row.get("convertedOrderCount")).intValue());
        assertClose(leadToOrderConversionRate, ((Number) row.get("leadToOrderConversionRate")).doubleValue());
    }

    private static void assertClose(double expected, double actual) {
        assertTrue(BigDecimal.valueOf(actual)
                .subtract(BigDecimal.valueOf(expected)).abs()
                .compareTo(BigDecimal.valueOf(0.000001)) <= 0);
    }

    private static Object value(Map<String, Object> row, String... keys) {
        for (String key : keys) {
            if (row.containsKey(key)) {
                return row.get(key);
            }
        }
        throw new AssertionError("Missing keys " + List.of(keys) + " in " + row);
    }

    private static Comparator<Map<String, Object>> targetYearMonthOrder() {
        return Comparator
                .comparing((Map<String, Object> row) -> String.valueOf(value(row, "leadSource")))
                .thenComparing(row -> ((Number) value(row, "orderDate$year")).intValue())
                .thenComparing(row -> ((Number) value(row, "orderDate$month")).intValue());
    }

    private static SemanticQueryRequest dslCtePlan(Map<String, Object> ctePlan) {
        SemanticQueryRequest request = new SemanticQueryRequest();
        request.setRoute("DSL_CTE");
        request.setExecutablePlan(m("cte_plan", ctePlan));
        return request;
    }

    private static Map<String, Object> crmLeadFunnelPlan() {
        return m(
                "stages", List.of(
                        stage("lead_scope", "derive",
                                "input", m("model", "CrmLead"),
                                "filters", List.of(
                                        m("field", "createdAt", "op", ">=", "value", "2026-05-01 00:00:00"),
                                        m("field", "createdAt", "op", "<", "value", "2026-06-01 00:00:00")),
                                "derived", List.of(
                                        m("name", "convertedOpportunity", "expr",
                                                "convertedOpportunityId is not null"),
                                        m("name", "convertedOrder", "expr",
                                                "convertedOrderId is not null"))),
                        stage("source_funnel", "aggregate",
                                "inputs", List.of("lead_scope"),
                                "groupBy", List.of("leadSource"),
                                "metrics", List.of(
                                        m("name", "leadCount", "expr", "count(*)"),
                                        m("name", "convertedOpportunityCount", "expr",
                                                "sum(case when convertedOpportunity then 1 else 0 end)"),
                                        m("name", "convertedOrderCount", "expr",
                                                "sum(case when convertedOrder then 1 else 0 end)"))),
                        stage("source_conversion_rate", "derive",
                                "inputs", List.of("source_funnel"),
                                "derived", List.of(
                                        m("name", "leadToOrderConversionRate",
                                                "expr", "convertedOrderCount / leadCount")))
                ),
                "output", List.of("leadSource", "leadCount", "convertedOpportunityCount",
                        "convertedOrderCount", "leadToOrderConversionRate")
        );
    }

    private static Map<String, Object> crmLeadFunnelDropOffPlan() {
        return m(
                "stages", List.of(
                        stage("lead_scope", "derive",
                                "input", m("model", "CrmLead"),
                                "filters", List.of(
                                        m("field", "createdAt", "op", ">=", "value", "2026-05-01 00:00:00"),
                                        m("field", "createdAt", "op", "<", "value", "2026-06-01 00:00:00")),
                                "derived", List.of(
                                        m("name", "convertedOpportunity", "expr",
                                                "convertedOpportunityId is not null"),
                                        m("name", "convertedOrder", "expr",
                                                "convertedOrderId is not null"))),
                        stage("source_funnel", "aggregate",
                                "inputs", List.of("lead_scope"),
                                "groupBy", List.of("leadSource"),
                                "metrics", List.of(
                                        m("name", "leadCount", "expr", "count(*)"),
                                        m("name", "convertedOpportunityCount", "expr",
                                                "sum(case when convertedOpportunity then 1 else 0 end)"),
                                        m("name", "convertedOrderCount", "expr",
                                                "sum(case when convertedOrder then 1 else 0 end)"))),
                        stage("source_drop_off", "derive",
                                "inputs", List.of("source_funnel"),
                                "derived", List.of(
                                        m("name", "opportunityDropOffCount",
                                                "expr", "convertedOpportunityCount - convertedOrderCount"),
                                        m("name", "opportunityToOrderDropOffRate",
                                                "expr", "(convertedOpportunityCount - convertedOrderCount) / convertedOpportunityCount")))
                ),
                "output", List.of("leadSource", "leadCount", "convertedOpportunityCount",
                        "convertedOrderCount", "opportunityDropOffCount", "opportunityToOrderDropOffRate")
        );
    }

    private static Map<String, Object> crmLeadFunnelDropOffLeaderboardPlan() {
        Map<String, Object> result = crmLeadFunnelDropOffPlan();
        result.put("orderBy", List.of(
                m("field", "opportunityToOrderDropOffRate", "dir", "DESC"),
                m("field", "leadSource", "dir", "ASC")));
        result.put("limit", 2);
        return result;
    }

    private static Map<String, Object> crossModelCrmOrderJoinAlignBridgePlan() {
        Map<String, Object> plan = m(
                "stages", List.of(
                        stage("lead_orders", "aggregate",
                                "input", m("model", "CrmLead"),
                                "filters", List.of(
                                        m("field", "createdAt", "op", ">=", "value", "2026-05-01 00:00:00"),
                                        m("field", "createdAt", "op", "<", "value", "2026-06-01 00:00:00")),
                                "groupBy", List.of("leadSource", "convertedOrderId", "createdAt"),
                                "metrics", List.of(m("name", "leadCount", "expr", "count(*)"))),
                        stage("completed_orders", "aggregate",
                                "input", m("model", "FactOrderQueryModel"),
                                "filters", List.of(m("field", "orderStatus", "op", "=", "value", "COMPLETED")),
                                "groupBy", List.of("orderId"),
                                "metrics", List.of(m("name", "matchedOrderCount", "expr", "count(*)"))),
                        stage("verified_order_align", "join_align",
                                "inputs", List.of("lead_orders", "completed_orders"),
                                "keys", List.of("convertedOrderId=orderId"),
                                "joinType", "declared_key_align",
                                "relationRef", "CrmLead.convertedOrderId -> FactOrderQueryModel.orderId",
                                "cardinality", "many_to_one",
                                "timeAttribution", m(
                                        "basis", "lead_created",
                                        "field", "createdAt",
                                        "sourceStage", "lead_orders"),
                                "relation", m(
                                        "left", m(
                                                "stage", "lead_orders",
                                                "model", "CrmLead",
                                                "field", "convertedOrderId"),
                                        "right", m(
                                                "stage", "completed_orders",
                                                "model", "FactOrderQueryModel",
                                                "field", "orderId")),
                                "runtimeGuard", m(
                                        "cardinality", m(
                                                "enforce", true,
                                                "policy", "fail_closed",
                                                "leftMultiplicity", "many",
                                                "rightMultiplicity", "one",
                                                "nullKeyPolicy", "exclude_unmatched"),
                                        "timeAttribution", m(
                                                "enforce", true,
                                                "policy", "fail_closed",
                                                "sourceStage", "lead_orders",
                                                "sourceField", "createdAt",
                                                "nullPolicy", "reject_null")),
                                "output", List.of(
                                        "leadSource", "convertedOrderId", "leadCount",
                                        "orderId", "matchedOrderCount"))
                ),
                "output", List.of("leadSource", "convertedOrderId", "leadCount", "orderId", "matchedOrderCount")
        );
        return plan;
    }

    private static Map<String, Object> crossModelCrmOrderSourceRateBridgePlan() {
        Map<String, Object> plan = crossModelCrmOrderJoinAlignBridgePlan();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> baseStages = (List<Map<String, Object>>) plan.get("stages");
        List<Map<String, Object>> stages = new ArrayList<>(baseStages);
        stages.add(stage("source_matched_orders", "aggregate",
                "inputs", List.of("verified_order_align"),
                "groupBy", List.of("leadSource"),
                "metrics", List.of(m("name", "matchedLeadCount", "expr", "sum(leadCount)"))));
        stages.add(stage("source_total_leads", "aggregate",
                "input", m("model", "CrmLead"),
                "filters", List.of(
                        m("field", "createdAt", "op", ">=", "value", "2026-05-01 00:00:00"),
                        m("field", "createdAt", "op", "<", "value", "2026-06-01 00:00:00")),
                "groupBy", List.of("leadSource"),
                "metrics", List.of(m("name", "totalLeadCount", "expr", "count(*)"))));
        stages.add(stage("source_order_rate", "derive",
                "inputs", List.of("source_total_leads", "source_matched_orders"),
                "derived", List.of(m("name", "leadToOrderConversionRate",
                        "expr", "matchedLeadCount / totalLeadCount"))));
        plan.put("stages", stages);
        plan.put("output", List.of("leadSource", "totalLeadCount", "matchedLeadCount",
                "leadToOrderConversionRate"));
        return plan;
    }

    private static Map<String, Object> crossModelCrmOrderTimeAttributionBridgePlan() {
        Map<String, Object> plan = crossModelCrmOrderSourceRateBridgePlan();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> stages = (List<Map<String, Object>>) plan.get("stages");
        stages.get(0).put("filters", List.of(
                m("field", "createdAt", "op", ">=", "value", "2026-07-01 00:00:00"),
                m("field", "createdAt", "op", "<", "value", "2026-08-01 00:00:00")));
        stages.get(1).put("groupBy", List.of("orderId", "orderDate$caption"));
        stages.get(2).put("timeAttribution", m(
                "basis", "source_cohort_target_event_window",
                "field", "createdAt",
                "sourceStage", "lead_orders",
                "targetStage", "completed_orders",
                "targetField", "orderDate$caption"));
        @SuppressWarnings("unchecked")
        Map<String, Object> runtimeGuard = (Map<String, Object>) stages.get(2).get("runtimeGuard");
        @SuppressWarnings("unchecked")
        Map<String, Object> runtimeTimeAttribution =
                (Map<String, Object>) runtimeGuard.get("timeAttribution");
        runtimeTimeAttribution.put("targetStage", "completed_orders");
        runtimeTimeAttribution.put("targetField", "orderDate$caption");
        runtimeTimeAttribution.put("order", "source_at_or_before_target");
        stages.get(2).put("output", List.of(
                "leadSource", "convertedOrderId", "createdAt", "leadCount",
                "orderId", "orderDate$caption", "matchedOrderCount"));
        stages.get(4).put("filters", List.of(
                m("field", "createdAt", "op", ">=", "value", "2026-07-01 00:00:00"),
                m("field", "createdAt", "op", "<", "value", "2026-08-01 00:00:00")));
        plan.put("timeAttributionContract", m(
                "kind", "source_cohort_target_event_window",
                "relationRef", "CrmLead.convertedOrderId -> FactOrderQueryModel.orderId",
                "source", m(
                        "stage", "lead_orders",
                        "model", "CrmLead",
                        "field", "createdAt"),
                "target", m(
                        "stage", "completed_orders",
                        "model", "FactOrderQueryModel",
                        "field", "orderDate$caption"),
                "window", m(
                        "unit", "day",
                        "size", 30,
                        "order", "source_at_or_before_target"),
                "groupBy", List.of("leadSource"),
                "denominator", "totalLeadCount",
                "numerator", "matchedLeadCount",
                "ratio", "leadToOrderConversionRate"));
        return plan;
    }

    private static Map<String, Object> crossModelCrmOrderTargetMonthAttributionBridgePlan() {
        Map<String, Object> plan = crossModelCrmOrderTimeAttributionBridgePlan();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> stages = (List<Map<String, Object>>) plan.get("stages");
        stages.get(1).put("groupBy", List.of("orderId", "orderDate$caption", "orderDate$month"));
        stages.get(2).put("output", List.of(
                "leadSource", "convertedOrderId", "createdAt", "leadCount",
                "orderId", "orderDate$caption", "orderDate$month", "matchedOrderCount"));
        stages.get(3).put("groupBy", List.of("leadSource", "orderDate$month"));
        plan.put("targetPeriod", m(
                "field", "FactOrderQueryModel.orderDate",
                "grain", "month",
                "calendar", "natural"));
        plan.put("outputGrain", m(
                "sourceFields", List.of("CrmLead.leadSource"),
                "targetPeriodFields", List.of("FactOrderQueryModel.orderDate$month")));
        plan.put("output", List.of("leadSource", "orderDate$month", "totalLeadCount",
                "matchedLeadCount", "leadToOrderConversionRate"));
        return plan;
    }

    private static Map<String, Object> crossModelCrmOrderTargetYearMonthAttributionBridgePlan() {
        Map<String, Object> plan = crossModelCrmOrderTargetMonthAttributionBridgePlan();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> stages = (List<Map<String, Object>>) plan.get("stages");
        stages.get(1).put("groupBy", List.of("orderId", "orderDate$caption",
                "orderDate$year", "orderDate$month"));
        stages.get(2).put("output", List.of(
                "leadSource", "convertedOrderId", "createdAt", "leadCount",
                "orderId", "orderDate$caption", "orderDate$year", "orderDate$month", "matchedOrderCount"));
        stages.get(3).put("groupBy", List.of("leadSource", "orderDate$year", "orderDate$month"));
        plan.put("targetPeriod", m(
                "field", "FactOrderQueryModel.orderDate",
                "grain", "year_month",
                "calendar", "natural"));
        plan.put("outputGrain", m(
                "sourceFields", List.of("CrmLead.leadSource"),
                "targetPeriodFields", List.of(
                        "FactOrderQueryModel.orderDate$year",
                        "FactOrderQueryModel.orderDate$month")));
        plan.put("output", List.of("leadSource", "orderDate$year", "orderDate$month", "totalLeadCount",
                "matchedLeadCount", "leadToOrderConversionRate"));
        return plan;
    }

    private static Map<String, Object> crossModelCrmOrderTargetYearMonthZeroFillCalendarBridgePlan() {
        Map<String, Object> plan = crossModelCrmOrderTargetYearMonthAttributionBridgePlan();
        plan.put("calendarScaffold", m(
                "field", "FactOrderQueryModel.orderDate",
                "grain", "year_month",
                "source", "natural_gregorian_year_month",
                "rangePolicy", "explicit",
                "range", m("from", "2026-07", "to", "2026-09"),
                "fillPolicy", "zero",
                "fillTarget", "matchedLeadCount",
                "denominatorScope", "fixed_per_source_group",
                "scaffoldScope", "source_groups_from_source_cohort"));
        return plan;
    }

    private static Map<String, Object> stage(String name, String type, Object... rest) {
        Map<String, Object> result = m(rest);
        result.put("name", name);
        result.put("type", type);
        return result;
    }

    private static Map<String, Object> m(Object... kv) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            result.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return result;
    }
}
