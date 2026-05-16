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

class DslCteSlaFixtureIntegrationTest extends EcommerceTestSupport {

    @Resource
    private SemanticQueryServiceV3 semanticQueryServiceV3;

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
        List<Map<String, Object>> rows = executeQuery("""
                SELECT dt.team_name AS teamName,
                       COUNT(*) AS ticketCount,
                       SUM(CASE
                               WHEN st.first_response_at IS NOT NULL
                                    AND ((julianday(st.first_response_at) - julianday(st.created_at)) * 24.0) <= 48.0
                               THEN 1 ELSE 0
                           END) AS slaHitCount,
                       1.0 * SUM(CASE
                               WHEN st.first_response_at IS NOT NULL
                                    AND ((julianday(st.first_response_at) - julianday(st.created_at)) * 24.0) <= 48.0
                               THEN 1 ELSE 0
                           END) / NULLIF(COUNT(*), 0) AS slaAchievementRate
                FROM service_ticket st
                LEFT JOIN dim_team dt ON st.team_id = dt.team_id
                WHERE st.created_at >= '2026-05-01 00:00:00'
                  AND st.created_at < '2026-06-01 00:00:00'
                GROUP BY dt.team_name
                ORDER BY teamName
                """);

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
        assertTrue(generated.getSql().contains("julianday"), generated.getSql());
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
    @DisplayName("DSL_CTE priority-aware SLA rate bridge executes and matches manual baseline")
    void priorityAwareSlaRatePostSliceBridgeSqlMatchesManualBaseline() {
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
    @DisplayName("DSL_CTE priority-aware SLA rate bridge supports team and priority grouping")
    void priorityAwareSlaRateByTeamPriorityPostSliceBridgeSqlMatchesManualBaseline() {
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

    private List<Map<String, Object>> priorityAwareManualRows(double lowRateThreshold) {
        List<Map<String, Object>> rows = new ArrayList<>(jdbcTemplate.queryForList("""
                SELECT *
                FROM (
                    SELECT dt.team_name AS teamName,
                           COUNT(*) AS ticketCount,
                           SUM(CASE
                                   WHEN st.first_response_at IS NOT NULL
                                        AND ((julianday(st.first_response_at) - julianday(st.created_at)) * 24.0) <=
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
                                        AND ((julianday(st.first_response_at) - julianday(st.created_at)) * 24.0) <=
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
                )
                WHERE slaAchievementRate < ?
                ORDER BY teamName
                """, lowRateThreshold));
        rows.sort(Comparator.comparing(row -> String.valueOf(row.get("teamName"))));
        return rows;
    }

    private List<Map<String, Object>> priorityAwareResolutionManualRows(double lowRateThreshold) {
        List<Map<String, Object>> rows = new ArrayList<>(jdbcTemplate.queryForList("""
                SELECT *
                FROM (
                    SELECT dt.team_name AS teamName,
                           COUNT(*) AS ticketCount,
                           SUM(CASE
                                   WHEN st.resolved_at IS NOT NULL
                                        AND ((julianday(st.resolved_at) - julianday(st.created_at)) * 24.0) <=
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
                                        AND ((julianday(st.resolved_at) - julianday(st.created_at)) * 24.0) <=
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
                )
                WHERE slaAchievementRate < ?
                ORDER BY teamName
                """, lowRateThreshold));
        rows.sort(Comparator.comparing(row -> String.valueOf(row.get("teamName"))));
        return rows;
    }

    private List<Map<String, Object>> priorityAwareByTeamPriorityManualRows(double lowRateThreshold) {
        List<Map<String, Object>> rows = new ArrayList<>(jdbcTemplate.queryForList("""
                SELECT *
                FROM (
                    SELECT dt.team_name AS teamName,
                           st.priority AS priority,
                           COUNT(*) AS ticketCount,
                           SUM(CASE
                                   WHEN st.first_response_at IS NOT NULL
                                        AND ((julianday(st.first_response_at) - julianday(st.created_at)) * 24.0) <=
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
                                        AND ((julianday(st.first_response_at) - julianday(st.created_at)) * 24.0) <=
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
                )
                WHERE slaAchievementRate < ?
                ORDER BY teamName, priority
                """, lowRateThreshold));
        rows.sort(Comparator
                .comparing((Map<String, Object> row) -> String.valueOf(row.get("teamName")))
                .thenComparing(row -> String.valueOf(row.get("priority"))));
        return rows;
    }

    private List<Map<String, Object>> p1FirstResponseOverdueLeaderboardManualRows(int limit) {
        return jdbcTemplate.queryForList("""
                SELECT dt.team_name AS teamName,
                       COUNT(*) AS ticketCount,
                       SUM(CASE
                               WHEN st.first_response_at IS NULL
                                    OR ((julianday(st.first_response_at) - julianday(st.created_at)) * 24.0) > 4.0
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
                """, limit);
    }

    private static void assertSlaRow(Map<String, Object> row, String teamName, int ticketCount,
                                     int slaHitCount, double slaAchievementRate) {
        assertEquals(teamName, row.get("teamName"));
        assertEquals(ticketCount, ((Number) row.get("ticketCount")).intValue());
        assertEquals(slaHitCount, ((Number) row.get("slaHitCount")).intValue());
        assertTrue(BigDecimal.valueOf(((Number) row.get("slaAchievementRate")).doubleValue())
                .subtract(BigDecimal.valueOf(slaAchievementRate)).abs()
                .compareTo(BigDecimal.valueOf(0.000001)) <= 0);
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
                .compareTo(BigDecimal.valueOf(0.000001)) <= 0);
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
                .compareTo(BigDecimal.valueOf(0.000001)) <= 0);
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
