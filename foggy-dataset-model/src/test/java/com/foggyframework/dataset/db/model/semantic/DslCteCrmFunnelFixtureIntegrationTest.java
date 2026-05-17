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
