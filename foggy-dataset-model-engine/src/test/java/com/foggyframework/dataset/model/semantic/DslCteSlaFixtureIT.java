package com.foggyframework.dataset.model.semantic;

import com.foggyframework.dataset.model.ecommerce.EcommerceTestSupport;
import com.foggyframework.dataset.model.engine.compose.SqlGenerationResult;
import com.foggyframework.dataset.model.semantic.domain.SemanticQueryRequest;
import com.foggyframework.dataset.model.semantic.domain.SemanticQueryResponse;
import com.foggyframework.dataset.model.semantic.domain.SemanticRequestContext;
import com.foggyframework.dataset.model.semantic.service.SemanticQueryServiceV3;
import com.foggyframework.dataset.model.spi.JdbcQueryModel;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.BeforeEach;
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

class DslCteSlaFixtureIT extends EcommerceTestSupport {

    private static final BigDecimal RATIO_TOLERANCE = BigDecimal.valueOf(0.00001);

    @Resource
    private SemanticQueryServiceV3 semanticQueryServiceV3;

    @BeforeEach
    void resetFixture() {
        resetServiceTicketFixture();
    }

    @Test
    @DisplayName("ServiceTicket TM/QM loads for SLA DSL_CTE fixture")
    void serviceTicketModelLoads() {
        JdbcQueryModel queryModel = getQueryModel("ServiceTicketQueryModel");

        assertNotNull(queryModel);
        assertEquals("ServiceTicketQueryModel", queryModel.getName());
        assertNotNull(queryModel.getColumnGroups());
        assertEquals(3, queryModel.getColumnGroups().size());
        assertEquals(10L, getTableCount("service_ticket"));
    }

    @Test
    @DisplayName("ServiceTicket SLA manual SQL establishes SQLite parity baseline")
    void serviceTicketSlaManualSqlBaseline() {
        String firstResponseHours = hoursBetweenSql("st.created_at", "st.first_response_at");
        List<Map<String, Object>> rows = executeQuery("""
                SELECT dt.team_name AS teamName,
                       COUNT(*) AS ticketCount,
                       SUM(CASE
                               WHEN st.first_response_at IS NOT NULL
                                    AND %s <= 48.0
                               THEN 1 ELSE 0
                           END) AS slaHitCount,
                       1.0 * SUM(CASE
                               WHEN st.first_response_at IS NOT NULL
                                    AND %s <= 48.0
                               THEN 1 ELSE 0
                           END) / NULLIF(COUNT(*), 0) AS slaAchievementRate
                FROM service_ticket st
                LEFT JOIN dim_team dt ON st.team_id = dt.team_id
                WHERE st.created_at >= '2026-05-01 00:00:00'
                  AND st.created_at < '2026-06-01 00:00:00'
                GROUP BY dt.team_name
                ORDER BY teamName
                """.formatted(firstResponseHours, firstResponseHours));

        assertEquals(3, rows.size());
        assertSlaRow(rows.get(0), "华东区", 2, 1, 0.5);
        assertSlaRow(rows.get(1), "技术部", 4, 2, 0.5);
        assertSlaRow(rows.get(2), "销售部", 3, 2, 2.0 / 3.0);
    }

    @Test
    @DisplayName("DSL_CTE minimal SLA row-level bridge executes and matches manual baseline")
    void minimalSlaRowLevelBridgeSqlMatchesManualBaseline() {
        SemanticQueryRequest request = dslCtePlan(minimalRowLevelSlaPlan());
        request.setHints(Map.of("dslCteCompileToDsl", true));

        SqlGenerationResult generated = semanticQueryServiceV3.generateSql(
                "ServiceTicketQueryModel", request, SemanticRequestContext.empty());

        assertNotNull(generated);
        assertNotNull(generated.getSql());
        assertSlaDateDiffMatchesDialect(generated.getSql());
        assertTrue(generated.getSql().contains("slaHitCount"), generated.getSql());

        List<Map<String, Object>> rows = new ArrayList<>(jdbcTemplate.queryForList(
                generated.getSql(), generated.getParams().toArray(new Object[0])));
        rows.sort(Comparator.comparing(row -> String.valueOf(value(row, "team$caption", "teamName"))));

        assertEquals(3, rows.size());
        assertSlaAggregateRow(rows.get(0), "华东区", 2, 1);
        assertSlaAggregateRow(rows.get(1), "技术部", 4, 2);
        assertSlaAggregateRow(rows.get(2), "销售部", 3, 2);
    }

    @Test
    @DisplayName("DSL_CTE minimal SLA rate postSlice bridge executes and matches manual baseline")
    void minimalSlaRatePostSliceBridgeSqlMatchesManualBaseline() {
        assumeCommonTableExpressionsSupported();

        SemanticQueryRequest request = dslCtePlan(minimalSlaRatePostSlicePlan());
        request.setHints(Map.of("dslCteCompileToDsl", true));

        SqlGenerationResult generated = semanticQueryServiceV3.generateSql(
                "ServiceTicketQueryModel", request, SemanticRequestContext.empty());

        assertNotNull(generated);
        assertNotNull(generated.getSql());
        assertTrue(generated.getSql().contains("dsl_cte_metric_ratio"), generated.getSql());
        assertTrue(generated.getSql().contains("slaAchievementRate"), generated.getSql());

        List<Map<String, Object>> rows = new ArrayList<>(jdbcTemplate.queryForList(
                generated.getSql(), generated.getParams().toArray(new Object[0])));
        rows.sort(Comparator.comparing(row -> String.valueOf(value(row, "team$caption", "teamName"))));

        assertEquals(3, rows.size());
        assertSlaRateRow(rows.get(0), "华东区", 2, 0.5);
        assertSlaRateRow(rows.get(1), "技术部", 4, 0.5);
        assertSlaRateRow(rows.get(2), "销售部", 3, 2.0 / 3.0);
    }

    @Test
    @DisplayName("DSL_CTE execute mode with bridge hint returns structured SLA rows")
    void dslCteExecuteModeWithBridgeHintReturnsRows() {
        assumeCommonTableExpressionsSupported();

        SemanticQueryRequest request = dslCtePlan(minimalSlaRatePostSlicePlan());
        request.setHints(Map.of("dslCteCompileToDsl", true));

        SemanticQueryResponse response = semanticQueryServiceV3.queryModel(
                "ServiceTicketQueryModel", request, "execute", SemanticRequestContext.empty());

        List<Map<String, Object>> rows = new ArrayList<>(response.getItems());
        rows.sort(Comparator.comparing(row -> String.valueOf(value(row, "team$caption", "teamName"))));

        assertEquals(3, rows.size());
        assertSlaRateRow(rows.get(0), "华东区", 2, 0.5);
        assertSlaRateRow(rows.get(1), "技术部", 4, 0.5);
        assertSlaRateRow(rows.get(2), "销售部", 3, 2.0 / 3.0);
    }

    @Test
    @DisplayName("DSL_CTE execute mode returns SLA rate with unresponded cutoff count")
    void dslCteExecuteModeReturnsSlaRateWithUnrespondedCutoffCount() {
        assumeCommonTableExpressionsSupported();

        SemanticQueryRequest request = dslCtePlan(unrespondedCutoffSlaRatePlan());
        request.setHints(Map.of("dslCteCompileToDsl", true));

        SemanticQueryResponse response = semanticQueryServiceV3.queryModel(
                "ServiceTicketQueryModel", request, "execute", SemanticRequestContext.empty());

        List<Map<String, Object>> rows = new ArrayList<>(response.getItems());
        rows.sort(Comparator.comparing(row -> String.valueOf(value(row, "team$caption", "teamName"))));

        assertEquals(3, rows.size());
        assertSlaRateAndUnrespondedRow(rows.get(0), "华东区", 2, 0.5, 1);
        assertSlaRateAndUnrespondedRow(rows.get(1), "技术部", 4, 0.5, 1);
        assertSlaRateAndUnrespondedRow(rows.get(2), "销售部", 3, 2.0 / 3.0, 0);
    }

    @Test
    @DisplayName("DSL_CTE conditional aggregate difference bridge executes and matches manual baseline")
    void conditionalAggregateDifferenceBridgeSqlMatchesManualBaseline() {
        assumeCommonTableExpressionsSupported();

        SemanticQueryRequest request = dslCtePlan(unresolvedTicketLeaderboardPlan());
        request.setHints(Map.of("dslCteCompileToDsl", true));

        SemanticQueryResponse response = semanticQueryServiceV3.validateQuery(
                "ServiceTicketQueryModel", request, SemanticRequestContext.empty());
        Map<String, Object> aggregateContract = stageContract(response, "team_ticket_status", "aggregate_contract");
        assertEquals("conditional_numerator_aggregation", aggregateContract.get("kind"));
        assertEquals(true, aggregateContract.get("bridge_signed"));

        @SuppressWarnings("unchecked")
        Map<String, Object> resultStageBridge = (Map<String, Object>) response.getExecution()
                .getDslCteValidation().get("dsl_result_stage_metric_ratio");
        assertEquals("relation_metric_arithmetic", resultStageBridge.get("kind"));

        SqlGenerationResult generated = semanticQueryServiceV3.generateSql(
                "ServiceTicketQueryModel", request, SemanticRequestContext.empty());

        assertNotNull(generated);
        assertNotNull(generated.getSql());
        assertTrue(generated.getSql().contains("dsl_cte_metric_ratio"), generated.getSql());
        assertTrue(generated.getSql().contains("is_not_null")
                || generated.getSql().toLowerCase().contains(" is not null"), generated.getSql());
        assertTrue(generated.getSql().contains("\"ticketCount\" - \"resolvedTicketCount\" AS \"unresolvedTicketCount\""),
                generated.getSql());
        assertTrue(generated.getSql().contains(
                "ORDER BY \"unresolvedTicketCount\" DESC, \"team$caption\" ASC"), generated.getSql());

        List<Map<String, Object>> manualRows = unresolvedTicketLeaderboardManualRows();
        List<Map<String, Object>> rows = new ArrayList<>(jdbcTemplate.queryForList(
                generated.getSql(), generated.getParams().toArray(new Object[0])));

        assertEquals(manualRows.size(), rows.size());
        for (int i = 0; i < manualRows.size(); i++) {
            assertGeneratedUnresolvedTicketRowMatchesManual(rows.get(i), manualRows.get(i));
        }
    }

    @Test
    @DisplayName("DSL_CTE priority-aware SLA rate bridge executes and matches manual baseline")
    void priorityAwareSlaRatePostSliceBridgeSqlMatchesManualBaseline() {
        assumeCommonTableExpressionsSupported();

        List<Map<String, Object>> manualRows = priorityAwareManualRows(0.85);

        SemanticQueryRequest request = dslCtePlan(priorityAwareSlaRatePostSlicePlan());
        request.setHints(Map.of("dslCteCompileToDsl", true));

        SqlGenerationResult generated = semanticQueryServiceV3.generateSql(
                "ServiceTicketQueryModel", request, SemanticRequestContext.empty());

        assertNotNull(generated);
        assertNotNull(generated.getSql());
        assertTrue(generated.getSql().contains("dsl_cte_metric_ratio"), generated.getSql());
        assertTrue(generated.getSql().contains("priority"), generated.getSql());

        List<Map<String, Object>> rows = new ArrayList<>(jdbcTemplate.queryForList(
                generated.getSql(), generated.getParams().toArray(new Object[0])));
        rows.sort(Comparator.comparing(row -> String.valueOf(value(row, "team$caption", "teamName"))));

        assertEquals(manualRows.size(), rows.size());
        for (int i = 0; i < manualRows.size(); i++) {
            assertGeneratedSlaRateRowMatchesManual(rows.get(i), manualRows.get(i));
        }
    }

    @Test
    @DisplayName("DSL_CTE priority-aware resolution SLA rate bridge executes and matches manual baseline")
    void priorityAwareResolutionSlaRatePostSliceBridgeSqlMatchesManualBaseline() {
        assumeCommonTableExpressionsSupported();

        List<Map<String, Object>> manualRows = priorityAwareResolutionManualRows(0.90);

        SemanticQueryRequest request = dslCtePlan(priorityAwareResolutionSlaRatePostSlicePlan());
        request.setHints(Map.of("dslCteCompileToDsl", true));

        SqlGenerationResult generated = semanticQueryServiceV3.generateSql(
                "ServiceTicketQueryModel", request, SemanticRequestContext.empty());

        assertNotNull(generated);
        assertNotNull(generated.getSql());
        assertTrue(generated.getSql().contains("dsl_cte_metric_ratio"), generated.getSql());
        assertTrue(generated.getSql().contains("resolved_at"), generated.getSql());

        List<Map<String, Object>> rows = new ArrayList<>(jdbcTemplate.queryForList(
                generated.getSql(), generated.getParams().toArray(new Object[0])));
        rows.sort(Comparator.comparing(row -> String.valueOf(value(row, "team$caption", "teamName"))));

        assertEquals(manualRows.size(), rows.size());
        for (int i = 0; i < manualRows.size(); i++) {
            assertGeneratedSlaRateRowMatchesManual(rows.get(i), manualRows.get(i));
        }
    }

    @Test
    @DisplayName("DSL_CTE dual first-response and resolution SLA rates execute and match manual baseline")
    void priorityAwareDualSlaRateBridgeSqlMatchesManualBaseline() {
        assumeCommonTableExpressionsSupported();

        List<Map<String, Object>> manualRows = priorityAwareDualSlaManualRows(0.90);

        SemanticQueryRequest request = dslCtePlan(priorityAwareDualSlaRatePlan());
        request.setHints(Map.of("dslCteCompileToDsl", true));

        SqlGenerationResult generated = semanticQueryServiceV3.generateSql(
                "ServiceTicketQueryModel", request, SemanticRequestContext.empty());

        assertNotNull(generated);
        assertNotNull(generated.getSql());
        assertTrue(generated.getSql().contains("firstResponseSlaRate"), generated.getSql());
        assertTrue(generated.getSql().contains("resolutionSlaRate"), generated.getSql());

        List<Map<String, Object>> rows = new ArrayList<>(jdbcTemplate.queryForList(
                generated.getSql(), generated.getParams().toArray(new Object[0])));
        rows.sort(Comparator.comparing(row -> String.valueOf(value(row, "team$caption", "teamName"))));

        assertEquals(manualRows.size(), rows.size());
        for (int i = 0; i < manualRows.size(); i++) {
            assertGeneratedDualSlaRateRowMatchesManual(rows.get(i), manualRows.get(i));
        }
    }

    @Test
    @DisplayName("DSL_CTE combined first-response and resolution SLA rate executes and matches manual baseline")
    void priorityAwareCombinedSlaRateBridgeSqlMatchesManualBaseline() {
        assumeCommonTableExpressionsSupported();

        List<Map<String, Object>> manualRows = priorityAwareCombinedSlaManualRows(0.85);

        SemanticQueryRequest request = dslCtePlan(priorityAwareCombinedSlaRatePlan());
        request.setHints(Map.of("dslCteCompileToDsl", true));

        SqlGenerationResult generated = semanticQueryServiceV3.generateSql(
                "ServiceTicketQueryModel", request, SemanticRequestContext.empty());

        assertNotNull(generated);
        assertNotNull(generated.getSql());
        assertTrue(generated.getSql().contains("combinedSlaRate"), generated.getSql());

        List<Map<String, Object>> rows = new ArrayList<>(jdbcTemplate.queryForList(
                generated.getSql(), generated.getParams().toArray(new Object[0])));
        rows.sort(Comparator.comparing(row -> String.valueOf(value(row, "team$caption", "teamName"))));

        assertEquals(manualRows.size(), rows.size());
        for (int i = 0; i < manualRows.size(); i++) {
            assertGeneratedCombinedSlaRateRowMatchesManual(rows.get(i), manualRows.get(i));
        }
    }

    @Test
    @DisplayName("DSL_CTE priority-aware SLA rate bridge supports team and priority grouping")
    void priorityAwareSlaRateByTeamPriorityPostSliceBridgeSqlMatchesManualBaseline() {
        assumeCommonTableExpressionsSupported();

        List<Map<String, Object>> manualRows = priorityAwareByTeamPriorityManualRows(0.85);

        SemanticQueryRequest request = dslCtePlan(priorityAwareSlaRateByTeamPriorityPostSlicePlan());
        request.setHints(Map.of("dslCteCompileToDsl", true));

        SqlGenerationResult generated = semanticQueryServiceV3.generateSql(
                "ServiceTicketQueryModel", request, SemanticRequestContext.empty());

        assertNotNull(generated);
        assertNotNull(generated.getSql());
        assertTrue(generated.getSql().contains("\"priority\""), generated.getSql());
        assertTrue(generated.getSql().contains("ORDER BY \"team$caption\" ASC, \"priority\" ASC"), generated.getSql());

        List<Map<String, Object>> rows = new ArrayList<>(jdbcTemplate.queryForList(
                generated.getSql(), generated.getParams().toArray(new Object[0])));
        rows.sort(Comparator
                .comparing((Map<String, Object> row) -> String.valueOf(value(row, "team$caption", "teamName")))
                .thenComparing(row -> String.valueOf(value(row, "priority"))));

        assertEquals(manualRows.size(), rows.size());
        for (int i = 0; i < manualRows.size(); i++) {
            assertGeneratedSlaRateByPriorityRowMatchesManual(rows.get(i), manualRows.get(i));
        }
    }

    @Test
    @DisplayName("DSL_CTE P1 first-response overdue leaderboard executes and matches manual baseline")
    void p1FirstResponseOverdueLeaderboardBridgeSqlMatchesManualBaseline() {
        assumeCommonTableExpressionsSupported();

        List<Map<String, Object>> manualRows = p1FirstResponseOverdueLeaderboardManualRows(5);

        SemanticQueryRequest request = dslCtePlan(p1FirstResponseOverdueLeaderboardPlan());
        request.setHints(Map.of("dslCteCompileToDsl", true));

        SqlGenerationResult generated = semanticQueryServiceV3.generateSql(
                "ServiceTicketQueryModel", request, SemanticRequestContext.empty());

        assertNotNull(generated);
        assertNotNull(generated.getSql());
        assertTrue(generated.getSql().contains("overdueTicketCount"), generated.getSql());
        assertTrue(generated.getSql().toUpperCase().contains("ORDER BY"), generated.getSql());
        assertTrue(generated.getSql().toUpperCase().contains("LIMIT"), generated.getSql());

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                generated.getSql(), generated.getParams().toArray(new Object[0]));

        assertEquals(manualRows.size(), rows.size());
        for (int i = 0; i < manualRows.size(); i++) {
            assertGeneratedP1OverdueRowMatchesManual(rows.get(i), manualRows.get(i));
        }
    }

    @Test
    @DisplayName("DSL_CTE minimal SLA rate postSlice can filter low-achievement teams")
    void minimalSlaRatePostSliceBridgeFiltersLowAchievementTeams() {
        assumeCommonTableExpressionsSupported();

        Map<String, Object> plan = minimalSlaRatePostSlicePlan();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> stages = (List<Map<String, Object>>) plan.get("stages");
        stages.get(3).put("filters", List.of(m("field", "slaAchievementRate", "op", "<", "value", 0.6)));

        SemanticQueryRequest request = dslCtePlan(plan);
        request.setHints(Map.of("dslCteCompileToDsl", true));

        SqlGenerationResult generated = semanticQueryServiceV3.generateSql(
                "ServiceTicketQueryModel", request, SemanticRequestContext.empty());
        List<Map<String, Object>> rows = new ArrayList<>(jdbcTemplate.queryForList(
                generated.getSql(), generated.getParams().toArray(new Object[0])));
        rows.sort(Comparator.comparing(row -> String.valueOf(value(row, "team$caption", "teamName"))));

        assertEquals(2, rows.size());
        assertSlaRateRow(rows.get(0), "华东区", 2, 0.5);
        assertSlaRateRow(rows.get(1), "技术部", 4, 0.5);
    }

    private String hoursBetweenSql(String startExpr, String endExpr) {
        return switch (getDialectKey()) {
            case "mysql" -> "(TIMESTAMPDIFF(SECOND, " + startExpr + ", " + endExpr + ") / 3600.0)";
            case "postgresql" -> "(EXTRACT(EPOCH FROM (" + endExpr + " - " + startExpr + ")) / 3600.0)";
            case "sqlserver" -> "(DATEDIFF(second, " + startExpr + ", " + endExpr + ") / 3600.0)";
            default -> "((julianday(" + endExpr + ") - julianday(" + startExpr + ")) * 24.0)";
        };
    }

    private void assertSlaDateDiffMatchesDialect(String sql) {
        switch (getDialectKey()) {
            case "mysql" -> assertTrue(sql.contains("TIMESTAMPDIFF"), sql);
            case "postgresql" -> assertTrue(sql.contains("EXTRACT(EPOCH"), sql);
            default -> assertTrue(sql.contains("julianday"), sql);
        }
    }

    private List<Map<String, Object>> priorityAwareManualRows(double lowRateThreshold) {
        String firstResponseHours = hoursBetweenSql("st.created_at", "st.first_response_at");
        List<Map<String, Object>> rows = new ArrayList<>(jdbcTemplate.queryForList("""
                SELECT *
                FROM (
                    SELECT dt.team_name AS teamName,
                           COUNT(*) AS ticketCount,
                           SUM(CASE
                                   WHEN st.first_response_at IS NOT NULL
                                        AND %1$s <=
                                            CASE
                                                WHEN st.priority = 'P1' THEN 4.0
                                                WHEN st.priority = 'P2' THEN 24.0
                                                WHEN st.priority = 'P3' THEN 48.0
                                                ELSE NULL
                                            END
                                   THEN 1 ELSE 0
                               END) AS slaHitCount,
                           1.0 * SUM(CASE
                                   WHEN st.first_response_at IS NOT NULL
                                        AND %1$s <=
                                            CASE
                                                WHEN st.priority = 'P1' THEN 4.0
                                                WHEN st.priority = 'P2' THEN 24.0
                                                WHEN st.priority = 'P3' THEN 48.0
                                                ELSE NULL
                                            END
                                   THEN 1 ELSE 0
                               END) / NULLIF(COUNT(*), 0) AS slaAchievementRate
                    FROM service_ticket st
                    LEFT JOIN dim_team dt ON st.team_id = dt.team_id
                    WHERE st.created_at >= '2026-05-01 00:00:00'
                      AND st.created_at < '2026-06-01 00:00:00'
                    GROUP BY dt.team_name
                ) priority_sla
                WHERE slaAchievementRate < ?
                ORDER BY teamName
                """.formatted(firstResponseHours), lowRateThreshold));
        rows.sort(Comparator.comparing(row -> String.valueOf(row.get("teamName"))));
        return rows;
    }

    private List<Map<String, Object>> priorityAwareResolutionManualRows(double lowRateThreshold) {
        String resolutionHours = hoursBetweenSql("st.created_at", "st.resolved_at");
        List<Map<String, Object>> rows = new ArrayList<>(jdbcTemplate.queryForList("""
                SELECT *
                FROM (
                    SELECT dt.team_name AS teamName,
                           COUNT(*) AS ticketCount,
                           SUM(CASE
                                   WHEN st.resolved_at IS NOT NULL
                                        AND %1$s <=
                                            CASE
                                                WHEN st.priority = 'P1' THEN 8.0
                                                WHEN st.priority = 'P2' THEN 48.0
                                                WHEN st.priority = 'P3' THEN 72.0
                                                ELSE NULL
                                            END
                                   THEN 1 ELSE 0
                               END) AS slaHitCount,
                           1.0 * SUM(CASE
                                   WHEN st.resolved_at IS NOT NULL
                                        AND %1$s <=
                                            CASE
                                                WHEN st.priority = 'P1' THEN 8.0
                                                WHEN st.priority = 'P2' THEN 48.0
                                                WHEN st.priority = 'P3' THEN 72.0
                                                ELSE NULL
                                            END
                                   THEN 1 ELSE 0
                               END) / NULLIF(COUNT(*), 0) AS slaAchievementRate
                    FROM service_ticket st
                    LEFT JOIN dim_team dt ON st.team_id = dt.team_id
                    WHERE st.created_at >= '2026-05-01 00:00:00'
                      AND st.created_at < '2026-06-01 00:00:00'
                    GROUP BY dt.team_name
                ) priority_resolution_sla
                WHERE slaAchievementRate < ?
                ORDER BY teamName
                """.formatted(resolutionHours), lowRateThreshold));
        rows.sort(Comparator.comparing(row -> String.valueOf(row.get("teamName"))));
        return rows;
    }

    private List<Map<String, Object>> priorityAwareDualSlaManualRows(double lowRateThreshold) {
        String firstResponseHours = hoursBetweenSql("st.created_at", "st.first_response_at");
        String resolutionHours = hoursBetweenSql("st.created_at", "st.resolved_at");
        List<Map<String, Object>> rows = new ArrayList<>(jdbcTemplate.queryForList("""
                SELECT *
                FROM (
                    SELECT dt.team_name AS teamName,
                           COUNT(*) AS ticketCount,
                           1.0 * SUM(CASE
                                   WHEN st.first_response_at IS NOT NULL
                                        AND %1$s <=
                                            CASE
                                                WHEN st.priority = 'P1' THEN 4.0
                                                WHEN st.priority = 'P2' THEN 24.0
                                                WHEN st.priority = 'P3' THEN 48.0
                                                ELSE NULL
                                            END
                                   THEN 1 ELSE 0
                               END) / NULLIF(COUNT(*), 0) AS firstResponseSlaRate,
                           1.0 * SUM(CASE
                                   WHEN st.resolved_at IS NOT NULL
                                        AND %2$s <=
                                            CASE
                                                WHEN st.priority = 'P1' THEN 8.0
                                                WHEN st.priority = 'P2' THEN 48.0
                                                WHEN st.priority = 'P3' THEN 72.0
                                                ELSE NULL
                                            END
                                   THEN 1 ELSE 0
                               END) / NULLIF(COUNT(*), 0) AS resolutionSlaRate
                    FROM service_ticket st
                    LEFT JOIN dim_team dt ON st.team_id = dt.team_id
                    WHERE st.created_at >= '2026-05-01 00:00:00'
                      AND st.created_at < '2026-06-01 00:00:00'
                    GROUP BY dt.team_name
                ) priority_dual_sla
                WHERE resolutionSlaRate < ?
                ORDER BY teamName
                """.formatted(firstResponseHours, resolutionHours), lowRateThreshold));
        rows.sort(Comparator.comparing(row -> String.valueOf(row.get("teamName"))));
        return rows;
    }

    private List<Map<String, Object>> priorityAwareCombinedSlaManualRows(double lowRateThreshold) {
        String firstResponseHours = hoursBetweenSql("st.created_at", "st.first_response_at");
        String resolutionHours = hoursBetweenSql("st.created_at", "st.resolved_at");
        List<Map<String, Object>> rows = new ArrayList<>(jdbcTemplate.queryForList("""
                SELECT *
                FROM (
                    SELECT dt.team_name AS teamName,
                           COUNT(*) AS ticketCount,
                           1.0 * SUM(CASE
                                   WHEN st.first_response_at IS NOT NULL
                                        AND %1$s <=
                                            CASE
                                                WHEN st.priority = 'P1' THEN 4.0
                                                WHEN st.priority = 'P2' THEN 24.0
                                                WHEN st.priority = 'P3' THEN 48.0
                                                ELSE NULL
                                            END
                                        AND st.resolved_at IS NOT NULL
                                        AND %2$s <=
                                            CASE
                                                WHEN st.priority = 'P1' THEN 8.0
                                                WHEN st.priority = 'P2' THEN 48.0
                                                WHEN st.priority = 'P3' THEN 72.0
                                                ELSE NULL
                                            END
                                   THEN 1 ELSE 0
                               END) / NULLIF(COUNT(*), 0) AS combinedSlaRate
                    FROM service_ticket st
                    LEFT JOIN dim_team dt ON st.team_id = dt.team_id
                    WHERE st.created_at >= '2026-05-01 00:00:00'
                      AND st.created_at < '2026-06-01 00:00:00'
                    GROUP BY dt.team_name
                ) priority_combined_sla
                WHERE combinedSlaRate < ?
                ORDER BY teamName
                """.formatted(firstResponseHours, resolutionHours), lowRateThreshold));
        rows.sort(Comparator.comparing(row -> String.valueOf(row.get("teamName"))));
        return rows;
    }

    private List<Map<String, Object>> priorityAwareByTeamPriorityManualRows(double lowRateThreshold) {
        String firstResponseHours = hoursBetweenSql("st.created_at", "st.first_response_at");
        List<Map<String, Object>> rows = new ArrayList<>(jdbcTemplate.queryForList("""
                SELECT *
                FROM (
                    SELECT dt.team_name AS teamName,
                           st.priority AS priority,
                           COUNT(*) AS ticketCount,
                           SUM(CASE
                                   WHEN st.first_response_at IS NOT NULL
                                        AND %1$s <=
                                            CASE
                                                WHEN st.priority = 'P1' THEN 4.0
                                                WHEN st.priority = 'P2' THEN 24.0
                                                WHEN st.priority = 'P3' THEN 48.0
                                                ELSE NULL
                                            END
                                   THEN 1 ELSE 0
                               END) AS slaHitCount,
                           1.0 * SUM(CASE
                                   WHEN st.first_response_at IS NOT NULL
                                        AND %1$s <=
                                            CASE
                                                WHEN st.priority = 'P1' THEN 4.0
                                                WHEN st.priority = 'P2' THEN 24.0
                                                WHEN st.priority = 'P3' THEN 48.0
                                                ELSE NULL
                                            END
                                   THEN 1 ELSE 0
                               END) / NULLIF(COUNT(*), 0) AS slaAchievementRate
                    FROM service_ticket st
                    LEFT JOIN dim_team dt ON st.team_id = dt.team_id
                    WHERE st.created_at >= '2026-05-01 00:00:00'
                      AND st.created_at < '2026-06-01 00:00:00'
                    GROUP BY dt.team_name, st.priority
                ) priority_team_sla
                WHERE slaAchievementRate < ?
                ORDER BY teamName, priority
                """.formatted(firstResponseHours), lowRateThreshold));
        rows.sort(Comparator
                .comparing((Map<String, Object> row) -> String.valueOf(row.get("teamName")))
                .thenComparing(row -> String.valueOf(row.get("priority"))));
        return rows;
    }

    private List<Map<String, Object>> p1FirstResponseOverdueLeaderboardManualRows(int limit) {
        String firstResponseHours = hoursBetweenSql("st.created_at", "st.first_response_at");
        return jdbcTemplate.queryForList("""
                SELECT dt.team_name AS teamName,
                       COUNT(*) AS ticketCount,
                       SUM(CASE
                               WHEN st.first_response_at IS NULL
                                    OR %s > 4.0
                               THEN 1 ELSE 0
                           END) AS overdueTicketCount
                FROM service_ticket st
                LEFT JOIN dim_team dt ON st.team_id = dt.team_id
                WHERE st.created_at >= '2026-05-01 00:00:00'
                  AND st.created_at < '2026-06-01 00:00:00'
                  AND st.priority = 'P1'
                GROUP BY dt.team_name
                ORDER BY overdueTicketCount DESC, teamName ASC
                LIMIT ?
                """.formatted(firstResponseHours), limit);
    }

    private static void assertSlaRow(Map<String, Object> row, String teamName, int ticketCount,
                                     int slaHitCount, double slaAchievementRate) {
        assertEquals(teamName, row.get("teamName"));
        assertEquals(ticketCount, ((Number) row.get("ticketCount")).intValue());
        assertEquals(slaHitCount, ((Number) row.get("slaHitCount")).intValue());
        assertTrue(BigDecimal.valueOf(((Number) row.get("slaAchievementRate")).doubleValue())
                .subtract(BigDecimal.valueOf(slaAchievementRate)).abs()
                .compareTo(RATIO_TOLERANCE) <= 0);
    }

    private static void assertGeneratedSlaRateRowMatchesManual(Map<String, Object> generated,
                                                               Map<String, Object> manual) {
        assertEquals(manual.get("teamName"), value(generated, "team$caption", "teamName"));
        assertEquals(((Number) manual.get("ticketCount")).intValue(),
                ((Number) value(generated, "ticketCount")).intValue());
        assertEquals(((Number) manual.get("slaHitCount")).intValue(),
                ((Number) value(generated, "slaHitCount")).intValue());
        assertTrue(BigDecimal.valueOf(((Number) value(generated, "slaAchievementRate")).doubleValue())
                .subtract(BigDecimal.valueOf(((Number) manual.get("slaAchievementRate")).doubleValue())).abs()
                .compareTo(RATIO_TOLERANCE) <= 0);
    }

    private static void assertGeneratedSlaRateByPriorityRowMatchesManual(Map<String, Object> generated,
                                                                         Map<String, Object> manual) {
        assertEquals(manual.get("teamName"), value(generated, "team$caption", "teamName"));
        assertEquals(manual.get("priority"), value(generated, "priority"));
        assertEquals(((Number) manual.get("ticketCount")).intValue(),
                ((Number) value(generated, "ticketCount")).intValue());
        assertEquals(((Number) manual.get("slaHitCount")).intValue(),
                ((Number) value(generated, "slaHitCount")).intValue());
        assertTrue(BigDecimal.valueOf(((Number) value(generated, "slaAchievementRate")).doubleValue())
                .subtract(BigDecimal.valueOf(((Number) manual.get("slaAchievementRate")).doubleValue())).abs()
                .compareTo(RATIO_TOLERANCE) <= 0);
    }

    private static void assertGeneratedDualSlaRateRowMatchesManual(Map<String, Object> generated,
                                                                   Map<String, Object> manual) {
        assertEquals(manual.get("teamName"), value(generated, "team$caption", "teamName"));
        assertEquals(((Number) manual.get("ticketCount")).intValue(),
                ((Number) value(generated, "ticketCount")).intValue());
        assertTrue(BigDecimal.valueOf(((Number) value(generated, "firstResponseSlaRate")).doubleValue())
                .subtract(BigDecimal.valueOf(((Number) manual.get("firstResponseSlaRate")).doubleValue())).abs()
                .compareTo(RATIO_TOLERANCE) <= 0);
        assertTrue(BigDecimal.valueOf(((Number) value(generated, "resolutionSlaRate")).doubleValue())
                .subtract(BigDecimal.valueOf(((Number) manual.get("resolutionSlaRate")).doubleValue())).abs()
                .compareTo(RATIO_TOLERANCE) <= 0);
    }

    private static void assertGeneratedCombinedSlaRateRowMatchesManual(Map<String, Object> generated,
                                                                       Map<String, Object> manual) {
        assertEquals(manual.get("teamName"), value(generated, "team$caption", "teamName"));
        assertEquals(((Number) manual.get("ticketCount")).intValue(),
                ((Number) value(generated, "ticketCount")).intValue());
        assertTrue(BigDecimal.valueOf(((Number) value(generated, "combinedSlaRate")).doubleValue())
                .subtract(BigDecimal.valueOf(((Number) manual.get("combinedSlaRate")).doubleValue())).abs()
                .compareTo(RATIO_TOLERANCE) <= 0);
    }

    private static void assertGeneratedP1OverdueRowMatchesManual(Map<String, Object> generated,
                                                                 Map<String, Object> manual) {
        assertEquals(manual.get("teamName"), value(generated, "team$caption", "teamName"));
        assertEquals(((Number) manual.get("ticketCount")).intValue(),
                ((Number) value(generated, "ticketCount")).intValue());
        assertEquals(((Number) manual.get("overdueTicketCount")).intValue(),
                ((Number) value(generated, "overdueTicketCount")).intValue());
    }

    private static void assertSlaAggregateRow(Map<String, Object> row, String teamName, int ticketCount,
                                              int slaHitCount) {
        assertEquals(teamName, value(row, "team$caption", "teamName"));
        assertEquals(ticketCount, ((Number) value(row, "ticketCount")).intValue());
        assertEquals(slaHitCount, ((Number) value(row, "slaHitCount")).intValue());
    }

    private static void assertSlaRateRow(Map<String, Object> row, String teamName, int ticketCount,
                                         double slaAchievementRate) {
        assertEquals(teamName, value(row, "team$caption", "teamName"));
        assertEquals(ticketCount, ((Number) value(row, "ticketCount")).intValue());
        assertTrue(BigDecimal.valueOf(((Number) value(row, "slaAchievementRate")).doubleValue())
                .subtract(BigDecimal.valueOf(slaAchievementRate)).abs()
                .compareTo(RATIO_TOLERANCE) <= 0);
    }

    private static void assertSlaRateAndUnrespondedRow(Map<String, Object> row, String teamName, int ticketCount,
                                                       double slaAchievementRate, int overdueUnrespondedCount) {
        assertSlaRateRow(row, teamName, ticketCount, slaAchievementRate);
        assertEquals(overdueUnrespondedCount, ((Number) value(row, "overdueUnrespondedCount")).intValue());
    }

    private static Object value(Map<String, Object> row, String... keys) {
        for (String key : keys) {
            if (row.containsKey(key)) {
                return row.get(key);
            }
        }
        throw new AssertionError("Missing keys " + List.of(keys) + " in " + row);
    }

    private static Map<String, Object> stageContract(SemanticQueryResponse response,
                                                     String stageName,
                                                     String contractKey) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> stages = (List<Map<String, Object>>) response.getExecution()
                .getDslCteValidation().get("stages");
        return stages.stream()
                .filter(stage -> stageName.equals(stage.get("name")))
                .findFirst()
                .map(stage -> {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> contract = (Map<String, Object>) stage.get(contractKey);
                    return contract;
                })
                .orElseThrow(() -> new AssertionError("Missing contract " + contractKey + " for stage " + stageName));
    }

    private static SemanticQueryRequest dslCtePlan(Map<String, Object> ctePlan) {
        SemanticQueryRequest request = new SemanticQueryRequest();
        request.setRoute("DSL_CTE");
        request.setExecutablePlan(m("cte_plan", ctePlan));
        return request;
    }

    private List<Map<String, Object>> unresolvedTicketLeaderboardManualRows() {
        return jdbcTemplate.queryForList("""
                SELECT *
                FROM (
                    SELECT dt.team_name AS teamName,
                           COUNT(*) AS ticketCount,
                           SUM(CASE WHEN st.resolved_at IS NOT NULL THEN 1 ELSE 0 END) AS resolvedTicketCount,
                           COUNT(*) - SUM(CASE WHEN st.resolved_at IS NOT NULL THEN 1 ELSE 0 END) AS unresolvedTicketCount
                    FROM service_ticket st
                    LEFT JOIN dim_team dt ON st.team_id = dt.team_id
                    GROUP BY dt.team_name
                ) unresolved_tickets
                WHERE unresolvedTicketCount > 0
                ORDER BY unresolvedTicketCount DESC, teamName ASC
                LIMIT 5
                """);
    }

    private static void assertGeneratedUnresolvedTicketRowMatchesManual(Map<String, Object> generated,
                                                                        Map<String, Object> manual) {
        assertEquals(manual.get("teamName"), value(generated, "team$caption", "teamName"));
        assertEquals(((Number) manual.get("ticketCount")).intValue(),
                ((Number) value(generated, "ticketCount")).intValue());
        assertEquals(((Number) manual.get("resolvedTicketCount")).intValue(),
                ((Number) value(generated, "resolvedTicketCount")).intValue());
        assertEquals(((Number) manual.get("unresolvedTicketCount")).intValue(),
                ((Number) value(generated, "unresolvedTicketCount")).intValue());
    }

    private static Map<String, Object> unresolvedTicketLeaderboardPlan() {
        return m(
                "stages", List.of(
                        stage("team_ticket_status", "aggregate",
                                "input", m("model", "ServiceTicketQueryModel"),
                                "groupBy", List.of("team$caption"),
                                "metrics", List.of(
                                        m("name", "ticketCount", "expr", "count(ticketId)"),
                                        m("name", "resolvedTicketCount", "expr",
                                                "sum(case when resolvedAt is not null then 1 else 0 end)"))),
                        stage("team_ticket_gap", "derive",
                                "inputs", List.of("team_ticket_status"),
                                "derived", List.of(
                                        m("name", "unresolvedTicketCount",
                                                "expr", "ticketCount - resolvedTicketCount"))),
                        stage("teams_with_unresolved_tickets", "postSlice",
                                "inputs", List.of("team_ticket_gap"),
                                "filters", List.of(
                                        m("field", "unresolvedTicketCount", "op", ">", "value", 0))),
                        stage("unresolved_ticket_leaderboard", "orderBy",
                                "inputs", List.of("teams_with_unresolved_tickets"),
                                "orderBy", List.of(
                                        m("field", "unresolvedTicketCount", "dir", "DESC"),
                                        m("field", "team$caption", "dir", "ASC")),
                                "limit", 5)
                ),
                "output", List.of("team$caption", "ticketCount", "resolvedTicketCount", "unresolvedTicketCount")
        );
    }

    private static Map<String, Object> minimalRowLevelSlaPlan() {
        return m(
                "stages", List.of(
                        stage("ticket_scope", "derive",
                                "input", m("model", "ServiceTicketQueryModel"),
                                "filters", List.of(
                                        m("field", "createdAt", "op", ">=", "value", "2026-05-01 00:00:00"),
                                        m("field", "createdAt", "op", "<", "value", "2026-06-01 00:00:00")),
                                "derived", List.of(
                                        m("name", "firstResponseHours", "expr", "hours_between(createdAt, firstResponseAt)"),
                                        m("name", "slaHit", "expr",
                                                "firstResponseAt is not null and firstResponseHours <= 48"))),
                        stage("team_sla", "aggregate",
                                "inputs", List.of("ticket_scope"),
                                "groupBy", List.of("team$caption"),
                                "metrics", List.of(
                                        m("name", "ticketCount", "expr", "count(*)"),
                                        m("name", "slaHitCount", "expr", "sum(slaHit)")))
                ),
                "output", List.of("team$caption", "ticketCount", "slaHitCount")
        );
    }

    private static Map<String, Object> minimalSlaRatePostSlicePlan() {
        return m(
                "stages", List.of(
                        stage("ticket_scope", "derive",
                                "input", m("model", "ServiceTicketQueryModel"),
                                "filters", List.of(
                                        m("field", "createdAt", "op", ">=", "value", "2026-05-01 00:00:00"),
                                        m("field", "createdAt", "op", "<", "value", "2026-06-01 00:00:00")),
                                "derived", List.of(
                                        m("name", "firstResponseHours", "expr", "hours_between(createdAt, firstResponseAt)"),
                                        m("name", "slaHit", "expr",
                                                "firstResponseAt is not null and firstResponseHours <= 48"))),
                        stage("team_sla", "aggregate",
                                "inputs", List.of("ticket_scope"),
                                "groupBy", List.of("team$caption"),
                                "metrics", List.of(
                                        m("name", "ticketCount", "expr", "count(*)"),
                                        m("name", "slaHitCount", "expr", "sum(slaHit)"))),
                        stage("team_sla_rate", "derive",
                                "inputs", List.of("team_sla"),
                                "derived", List.of(
                                        m("name", "slaAchievementRate", "expr", "slaHitCount / ticketCount"))),
                        stage("low_sla_teams", "postSlice",
                                "inputs", List.of("team_sla_rate"),
                                "filters", List.of(
                                        m("field", "slaAchievementRate", "op", "<", "value", 0.85)))
                ),
                "output", List.of("team$caption", "ticketCount", "slaAchievementRate")
        );
    }

    private static Map<String, Object> unrespondedCutoffSlaRatePlan() {
        return m(
                "stages", List.of(
                        stage("ticket_scope", "derive",
                                "input", m("model", "ServiceTicketQueryModel"),
                                "filters", List.of(
                                        m("field", "createdAt", "op", ">=", "value", "2026-05-01 00:00:00"),
                                        m("field", "createdAt", "op", "<", "value", "2026-06-01 00:00:00")),
                                "derived", List.of(
                                        m("name", "firstResponseHours", "expr", "hours_between(createdAt, firstResponseAt)"),
                                        m("name", "slaHit", "expr",
                                                "firstResponseAt is not null and firstResponseHours <= 48"),
                                        m("name", "overdueUnresponded", "expr",
                                                "firstResponseAt is null and createdAt < '2026-05-30 00:00:00'"))),
                        stage("team_sla", "aggregate",
                                "inputs", List.of("ticket_scope"),
                                "groupBy", List.of("team$caption"),
                                "metrics", List.of(
                                        m("name", "ticketCount", "expr", "count(*)"),
                                        m("name", "slaHitCount", "expr", "sum(slaHit)"),
                                        m("name", "overdueUnrespondedCount", "expr",
                                                "sum(case when overdueUnresponded then 1 else 0 end)"))),
                        stage("team_sla_rate", "derive",
                                "inputs", List.of("team_sla"),
                                "derived", List.of(
                                        m("name", "slaAchievementRate", "expr", "slaHitCount / ticketCount")))
                ),
                "output", List.of("team$caption", "ticketCount", "slaAchievementRate", "overdueUnrespondedCount")
        );
    }

    private static Map<String, Object> priorityAwareSlaRatePostSlicePlan() {
        return m(
                "stages", List.of(
                        stage("ticket_scope", "derive",
                                "input", m("model", "ServiceTicketQueryModel"),
                                "filters", List.of(
                                        m("field", "createdAt", "op", ">=", "value", "2026-05-01 00:00:00"),
                                        m("field", "createdAt", "op", "<", "value", "2026-06-01 00:00:00")),
                                "derived", List.of(
                                        m("name", "firstResponseHours", "expr", "hours_between(createdAt, firstResponseAt)"),
                                        m("name", "slaThresholdHours", "expr",
                                                "priority_threshold(priority, P1=4, P2=24, P3=48)"),
                                        m("name", "slaHit", "expr",
                                                "firstResponseAt is not null and firstResponseHours <= slaThresholdHours"))),
                        stage("team_sla", "aggregate",
                                "inputs", List.of("ticket_scope"),
                                "groupBy", List.of("team$caption"),
                                "metrics", List.of(
                                        m("name", "ticketCount", "expr", "count(*)"),
                                        m("name", "slaHitCount", "expr", "sum(slaHit)"))),
                        stage("team_sla_rate", "derive",
                                "inputs", List.of("team_sla"),
                                "derived", List.of(
                                        m("name", "slaAchievementRate", "expr", "slaHitCount / ticketCount"))),
                        stage("low_sla_teams", "postSlice",
                                "inputs", List.of("team_sla_rate"),
                                "filters", List.of(
                                        m("field", "slaAchievementRate", "op", "<", "value", 0.85)))
                ),
                "output", List.of("team$caption", "ticketCount", "slaHitCount", "slaAchievementRate")
        );
    }

    private static Map<String, Object> priorityAwareSlaRateByTeamPriorityPostSlicePlan() {
        return m(
                "stages", List.of(
                        stage("ticket_scope", "derive",
                                "input", m("model", "ServiceTicketQueryModel"),
                                "filters", List.of(
                                        m("field", "createdAt", "op", ">=", "value", "2026-05-01 00:00:00"),
                                        m("field", "createdAt", "op", "<", "value", "2026-06-01 00:00:00")),
                                "derived", List.of(
                                        m("name", "firstResponseHours", "expr", "hours_between(createdAt, firstResponseAt)"),
                                        m("name", "slaThresholdHours", "expr",
                                                "priority_threshold(priority, P1=4, P2=24, P3=48)"),
                                        m("name", "slaHit", "expr",
                                                "firstResponseAt is not null and firstResponseHours <= slaThresholdHours"))),
                        stage("team_priority_sla", "aggregate",
                                "inputs", List.of("ticket_scope"),
                                "groupBy", List.of("team$caption", "priority"),
                                "metrics", List.of(
                                        m("name", "ticketCount", "expr", "count(*)"),
                                        m("name", "slaHitCount", "expr", "sum(slaHit)"))),
                        stage("team_priority_sla_rate", "derive",
                                "inputs", List.of("team_priority_sla"),
                                "derived", List.of(
                                        m("name", "slaAchievementRate", "expr", "slaHitCount / ticketCount"))),
                        stage("low_sla_team_priorities", "postSlice",
                                "inputs", List.of("team_priority_sla_rate"),
                                "filters", List.of(
                                        m("field", "slaAchievementRate", "op", "<", "value", 0.85)))
                ),
                "output", List.of("team$caption", "priority", "ticketCount", "slaHitCount", "slaAchievementRate")
        );
    }

    private static Map<String, Object> priorityAwareResolutionSlaRatePostSlicePlan() {
        return m(
                "stages", List.of(
                        stage("ticket_scope", "derive",
                                "input", m("model", "ServiceTicketQueryModel"),
                                "filters", List.of(
                                        m("field", "createdAt", "op", ">=", "value", "2026-05-01 00:00:00"),
                                        m("field", "createdAt", "op", "<", "value", "2026-06-01 00:00:00")),
                                "derived", List.of(
                                        m("name", "resolutionHours", "expr", "hours_between(createdAt, resolvedAt)"),
                                        m("name", "slaThresholdHours", "expr",
                                                "priority_threshold(priority, P1=8, P2=48, P3=72)"),
                                        m("name", "slaHit", "expr",
                                                "resolvedAt is not null and resolutionHours <= slaThresholdHours"))),
                        stage("team_resolution_sla", "aggregate",
                                "inputs", List.of("ticket_scope"),
                                "groupBy", List.of("team$caption"),
                                "metrics", List.of(
                                        m("name", "ticketCount", "expr", "count(*)"),
                                        m("name", "slaHitCount", "expr", "sum(slaHit)"))),
                        stage("team_resolution_sla_rate", "derive",
                                "inputs", List.of("team_resolution_sla"),
                                "derived", List.of(
                                        m("name", "slaAchievementRate", "expr", "slaHitCount / ticketCount"))),
                        stage("low_resolution_sla_teams", "postSlice",
                                "inputs", List.of("team_resolution_sla_rate"),
                                "filters", List.of(
                                        m("field", "slaAchievementRate", "op", "<", "value", 0.90)))
                ),
                "output", List.of("team$caption", "ticketCount", "slaHitCount", "slaAchievementRate")
        );
    }

    private static Map<String, Object> priorityAwareDualSlaRatePlan() {
        return m(
                "stages", List.of(
                        stage("ticket_scope", "derive",
                                "input", m("model", "ServiceTicketQueryModel"),
                                "filters", List.of(
                                        m("field", "createdAt", "op", ">=", "value", "2026-05-01 00:00:00"),
                                        m("field", "createdAt", "op", "<", "value", "2026-06-01 00:00:00")),
                                "derived", List.of(
                                        m("name", "firstResponseHours", "expr",
                                                "hours_between(createdAt, firstResponseAt)"),
                                        m("name", "firstResponseThresholdHours", "expr",
                                                "priority_threshold(priority, P1=4, P2=24, P3=48)"),
                                        m("name", "firstResponseSlaHit", "expr",
                                                "firstResponseAt is not null and firstResponseHours <= firstResponseThresholdHours"),
                                        m("name", "resolutionHours", "expr",
                                                "hours_between(createdAt, resolvedAt)"),
                                        m("name", "resolutionThresholdHours", "expr",
                                                "priority_threshold(priority, P1=8, P2=48, P3=72)"),
                                        m("name", "resolutionSlaHit", "expr",
                                                "resolvedAt is not null and resolutionHours <= resolutionThresholdHours"))),
                        stage("team_dual_sla", "aggregate",
                                "inputs", List.of("ticket_scope"),
                                "groupBy", List.of("team$caption"),
                                "metrics", List.of(
                                        m("name", "ticketCount", "expr", "count(*)"),
                                        m("name", "firstResponseSlaHitCount", "expr", "sum(firstResponseSlaHit)"),
                                        m("name", "resolutionSlaHitCount", "expr", "sum(resolutionSlaHit)"))),
                        stage("team_dual_sla_rate", "derive",
                                "inputs", List.of("team_dual_sla"),
                                "derived", List.of(
                                        m("name", "firstResponseSlaRate",
                                                "expr", "firstResponseSlaHitCount / ticketCount"),
                                        m("name", "resolutionSlaRate",
                                                "expr", "resolutionSlaHitCount / ticketCount"))),
                        stage("low_dual_sla_teams", "postSlice",
                                "inputs", List.of("team_dual_sla_rate"),
                                "filters", List.of(
                                        m("field", "resolutionSlaRate", "op", "<", "value", 0.90)))
                ),
                "output", List.of("team$caption", "ticketCount", "firstResponseSlaRate", "resolutionSlaRate")
        );
    }

    private static Map<String, Object> priorityAwareCombinedSlaRatePlan() {
        return m(
                "stages", List.of(
                        stage("ticket_scope", "derive",
                                "input", m("model", "ServiceTicketQueryModel"),
                                "filters", List.of(
                                        m("field", "createdAt", "op", ">=", "value", "2026-05-01 00:00:00"),
                                        m("field", "createdAt", "op", "<", "value", "2026-06-01 00:00:00")),
                                "derived", List.of(
                                        m("name", "firstResponseHours", "expr",
                                                "hours_between(createdAt, firstResponseAt)"),
                                        m("name", "firstResponseThresholdHours", "expr",
                                                "priority_threshold(priority, P1=4, P2=24, P3=48)"),
                                        m("name", "firstResponseSlaHit", "expr",
                                                "firstResponseAt is not null and firstResponseHours <= firstResponseThresholdHours"),
                                        m("name", "resolutionHours", "expr",
                                                "hours_between(createdAt, resolvedAt)"),
                                        m("name", "resolutionThresholdHours", "expr",
                                                "priority_threshold(priority, P1=8, P2=48, P3=72)"),
                                        m("name", "resolutionSlaHit", "expr",
                                                "resolvedAt is not null and resolutionHours <= resolutionThresholdHours"),
                                        m("name", "combinedSlaHit", "expr",
                                                "firstResponseSlaHit = 1 and resolutionSlaHit = 1"))),
                        stage("team_combined_sla", "aggregate",
                                "inputs", List.of("ticket_scope"),
                                "groupBy", List.of("team$caption"),
                                "metrics", List.of(
                                        m("name", "ticketCount", "expr", "count(*)"),
                                        m("name", "combinedSlaHitCount", "expr", "sum(combinedSlaHit)"))),
                        stage("team_combined_sla_rate", "derive",
                                "inputs", List.of("team_combined_sla"),
                                "derived", List.of(
                                        m("name", "combinedSlaRate", "expr", "combinedSlaHitCount / ticketCount"))),
                        stage("low_combined_sla_teams", "postSlice",
                                "inputs", List.of("team_combined_sla_rate"),
                                "filters", List.of(
                                        m("field", "combinedSlaRate", "op", "<", "value", 0.85)))
                ),
                "output", List.of("team$caption", "ticketCount", "combinedSlaRate")
        );
    }

    private static Map<String, Object> p1FirstResponseOverdueLeaderboardPlan() {
        Map<String, Object> result = m(
                "stages", List.of(
                        stage("p1_ticket_scope", "derive",
                                "input", m("model", "ServiceTicketQueryModel"),
                                "filters", List.of(
                                        m("field", "createdAt", "op", ">=", "value", "2026-05-01 00:00:00"),
                                        m("field", "createdAt", "op", "<", "value", "2026-06-01 00:00:00"),
                                        m("field", "priority", "op", "=", "value", "P1")),
                                "derived", List.of(
                                        m("name", "firstResponseHours", "expr", "hours_between(createdAt, firstResponseAt)"),
                                        m("name", "slaOverdue", "expr",
                                                "firstResponseAt is null or firstResponseHours > 4"))),
                        stage("team_p1_overdue", "aggregate",
                                "inputs", List.of("p1_ticket_scope"),
                                "groupBy", List.of("team$caption"),
                                "metrics", List.of(
                                        m("name", "ticketCount", "expr", "count(*)"),
                                        m("name", "overdueTicketCount", "expr", "sum(slaOverdue)")))
                ),
                "output", List.of("team$caption", "ticketCount", "overdueTicketCount")
        );
        result.put("orderBy", List.of(
                m("field", "overdueTicketCount", "dir", "DESC"),
                m("field", "team$caption", "dir", "ASC")));
        result.put("limit", 5);
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
