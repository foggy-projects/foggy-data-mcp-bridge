package com.foggyframework.dataset.db.model.parity;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.foggyframework.dataset.db.model.def.query.request.CalculatedFieldDef;
import com.foggyframework.dataset.db.model.ecommerce.EcommerceTestSupport;
import com.foggyframework.dataset.db.model.engine.compose.SqlGenerationResult;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticQueryRequest;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticRequestContext;
import com.foggyframework.dataset.db.model.semantic.service.SemanticQueryServiceV3;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TimeWindow SQL snapshot producer (Stage 3).
 *
 * <p>Mirrors the pattern of {@link FormulaParitySnapshotTest}: drives the
 * real Java {@link SemanticQueryServiceV3#generateSql} pipeline to produce
 * SQL for all happy cases in the shared timeWindow catalog, normalizes the
 * output and writes a JSON snapshot consumed by the Python golden diff harness at
 * {@code tests/integration/test_time_window_golden_diff.py}.</p>
 */
@Slf4j
@DisplayName("TimeWindowParitySnapshotTest · Stage 3 (Java side)")
class TimeWindowParitySnapshotTest extends EcommerceTestSupport {

    private static final String TEST_MODEL = "FactSalesQueryModel";
    private static final int EXPECTED_SNAPSHOT_COUNT = 9;

    @Resource
    private SemanticQueryServiceV3 semanticQueryServiceV3;

    @Test
    @DisplayName("produces _time_window_parity_snapshot.json for Python golden diff")
    void shouldProduceSnapshot() throws Exception {
        if (!supportsWindowFunctions()) {
            log.info("Skipping timeWindow snapshot — current DB does not support window functions");
            return;
        }

        List<Map<String, Object>> snapshots = new ArrayList<>();
        List<Map<String, Object>> generationErrors = new ArrayList<>();

        recordSnapshot("mom-month-happy", snapshots, generationErrors, this::buildMomMonthSnapshot);
        recordSnapshot("mtd-day-happy", snapshots, generationErrors, this::buildMtdDaySnapshot);
        recordSnapshot("rolling_30d-day-happy", snapshots, generationErrors, this::buildRolling30dSnapshot);
        recordSnapshot("rolling_7d-day-happy", snapshots, generationErrors, this::buildRolling7dSnapshot);
        recordSnapshot("rolling_7d-post-calc-gap-happy", snapshots, generationErrors, this::buildRolling7dPostCalcGapSnapshot);
        recordSnapshot("wow-week-happy", snapshots, generationErrors, this::buildWowWeekSnapshot);
        recordSnapshot("yoy-month-happy", snapshots, generationErrors, this::buildYoyMonthSnapshot);
        recordSnapshot("yoy-month-post-calc-growth-happy", snapshots, generationErrors, this::buildYoyPostCalcGrowthSnapshot);
        recordSnapshot("ytd-month-happy", snapshots, generationErrors, this::buildYtdMonthSnapshot);

        assertTrue(snapshots.size() + generationErrors.size() == EXPECTED_SNAPSHOT_COUNT,
                "expected exactly " + EXPECTED_SNAPSHOT_COUNT
                        + " timeWindow records, got success=" + snapshots.size()
                        + ", errors=" + generationErrors.size());
        assertTrue(snapshots.size() == EXPECTED_SNAPSHOT_COUNT,
                "expected exactly " + EXPECTED_SNAPSHOT_COUNT
                        + " timeWindow SQL snapshots, got " + snapshots.size()
                        + ", errors=" + generationErrors);
        assertTrue(generationErrors.isEmpty(),
                "expected no current Java generation errors, got " + generationErrors);

        // Assemble the snapshot JSON
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("schema_version", "1");
        snapshot.put("source", "TimeWindowParitySnapshotTest");
        snapshot.put("feature", "timeWindow");
        snapshot.put("snapshots", snapshots);
        snapshot.put("generation_errors", generationErrors);

        ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

        // Write to Python repo (cross-repo direct write) — best effort
        Path pythonTarget = Path.of(
                "..", "..",
                "foggy-data-mcp-bridge-python",
                "tests", "integration",
                "_time_window_parity_snapshot.json"
        ).normalize();
        if (Files.exists(pythonTarget.getParent())) {
            Files.createDirectories(pythonTarget.getParent());
            mapper.writeValue(pythonTarget.toFile(), snapshot);
            log.info("Wrote timeWindow snapshot to Python repo: {}", pythonTarget.toAbsolutePath());
        } else {
            log.warn("Python repo path not reachable: {} — skipping direct write", pythonTarget.toAbsolutePath());
        }

        // Write to Java local artifact (always)
        Path localCopy = Path.of("target", "parity", "_time_window_parity_snapshot.json");
        Files.createDirectories(localCopy.getParent());
        mapper.writeValue(localCopy.toFile(), snapshot);
        log.info("Wrote timeWindow snapshot to local: {}", localCopy.toAbsolutePath());

        assertTrue(Files.exists(localCopy), "local snapshot was not written: " + localCopy.toAbsolutePath());
    }

    // ------------------------------------------------------------------
    // Case builders
    // ------------------------------------------------------------------

    private void recordSnapshot(
            String name,
            List<Map<String, Object>> snapshots,
            List<Map<String, Object>> generationErrors,
            Supplier<Map<String, Object>> supplier) {
        try {
            snapshots.add(supplier.get());
        } catch (RuntimeException ex) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("name", name);
            row.put("dialect", "default");
            row.put("error_code", "GENERATE_SQL_FAILED");
            row.put("exception", ex.getClass().getSimpleName());
            row.put("message", ex.getMessage());
            generationErrors.add(row);
            log.warn("{} snapshot generation failed: {}", name, ex.getMessage());
        }
    }

    private Map<String, Object> buildMomMonthSnapshot() {
        return buildSnapshot(
                "mom-month-happy",
                List.of(
                        "salesDate$month", "salesDate$id", "salesAmount",
                        "salesAmount__prior", "salesAmount__diff", "salesAmount__ratio"),
                List.of("salesDate$month", "salesDate$id"),
                Map.of(
                        "field", "salesDate$id",
                        "grain", "month",
                        "comparison", "mom",
                        "range", "[)",
                        "value", List.of("2024-01-01", "2025-01-01"),
                        "targetMetrics", List.of("salesAmount")),
                List.of());
    }

    private Map<String, Object> buildMtdDaySnapshot() {
        return buildSnapshot(
                "mtd-day-happy",
                List.of(
                        "salesDate$year", "salesDate$month", "salesDate$id",
                        "salesAmount", "salesAmount__mtd"),
                List.of("salesDate$year", "salesDate$month", "salesDate$id"),
                Map.of(
                        "field", "salesDate$id",
                        "grain", "day",
                        "comparison", "mtd",
                        "range", "[)",
                        "value", List.of("2024-01-01", "now"),
                        "targetMetrics", List.of("salesAmount")),
                List.of());
    }

    private Map<String, Object> buildRolling30dSnapshot() {
        return buildSnapshot(
                "rolling_30d-day-happy",
                List.of("salesDate$id", "salesAmount", "salesAmount__rolling_30d"),
                List.of("salesDate$id"),
                Map.of(
                        "field", "salesDate$id",
                        "grain", "day",
                        "comparison", "rolling_30d",
                        "range", "[)",
                        "value", List.of("-60D", "now"),
                        "targetMetrics", List.of("salesAmount")),
                List.of());
    }

    private Map<String, Object> buildRolling7dSnapshot() {
        return buildSnapshot(
                "rolling_7d-day-happy",
                List.of("salesDate$id", "salesAmount", "salesAmount__rolling_7d"),
                List.of("salesDate$id"),
                Map.of(
                        "field", "salesDate$id",
                        "grain", "day",
                        "comparison", "rolling_7d",
                        "range", "[)",
                        "value", List.of("-30D", "now"),
                        "targetMetrics", List.of("salesAmount"),
                        "rollingAggregator", "avg"),
                List.of());
    }

    private Map<String, Object> buildRolling7dPostCalcGapSnapshot() {
        return buildSnapshot(
                "rolling_7d-post-calc-gap-happy",
                List.of(
                        "salesDate$id", "salesAmount",
                        "salesAmount__rolling_7d",
                        "rollingGap"),
                List.of("salesDate$id"),
                Map.of(
                        "field", "salesDate$id",
                        "grain", "day",
                        "comparison", "rolling_7d",
                        "range", "[)",
                        "value", List.of("-1M", "now"),
                        "targetMetrics", List.of("salesAmount")),
                List.of(new CalculatedFieldDef(
                        "rollingGap", "salesAmount - salesAmount__rolling_7d")));
    }

    private Map<String, Object> buildWowWeekSnapshot() {
        return buildSnapshot(
                "wow-week-happy",
                List.of(
                        "salesDate$week", "salesDate$dayOfWeek", "salesAmount",
                        "salesAmount__prior", "salesAmount__diff", "salesAmount__ratio"),
                List.of("salesDate$week", "salesDate$dayOfWeek"),
                Map.of(
                        "field", "salesDate$id",
                        "grain", "week",
                        "comparison", "wow",
                        "range", "[)",
                        "value", List.of("-2W", "now"),
                        "targetMetrics", List.of("salesAmount")),
                List.of());
    }

    private Map<String, Object> buildYoyMonthSnapshot() {
        return buildSnapshot(
                "yoy-month-happy",
                List.of(
                        "salesDate$year", "salesDate$month",
                        "salesAmount", "salesAmount__prior",
                        "salesAmount__diff", "salesAmount__ratio"),
                List.of("salesDate$year", "salesDate$month"),
                Map.of(
                        "field", "salesDate$id",
                        "grain", "month",
                        "comparison", "yoy",
                        "range", "[)",
                        "value", List.of("2024-01-01", "2025-01-01"),
                        "targetMetrics", List.of("salesAmount")),
                List.of());
    }

    private Map<String, Object> buildYoyPostCalcGrowthSnapshot() {
        return buildSnapshot(
                "yoy-month-post-calc-growth-happy",
                List.of(
                        "salesDate$year", "salesDate$month",
                        "salesAmount", "salesAmount__prior",
                        "salesAmount__diff", "salesAmount__ratio",
                        "growthPercent"),
                List.of("salesDate$year", "salesDate$month"),
                Map.of(
                        "field", "salesDate$id",
                        "grain", "month",
                        "comparison", "yoy",
                        "range", "[)",
                        "value", List.of("2024-01-01", "2025-01-01"),
                        "targetMetrics", List.of("salesAmount")),
                List.of(new CalculatedFieldDef(
                        "growthPercent", "salesAmount__ratio * 100")));
    }

    private Map<String, Object> buildYtdMonthSnapshot() {
        return buildSnapshot(
                "ytd-month-happy",
                List.of("salesDate$year", "salesDate$id", "salesAmount", "salesAmount__ytd"),
                List.of("salesDate$year", "salesDate$id"),
                Map.of(
                        "field", "salesDate$id",
                        "grain", "month",
                        "comparison", "ytd",
                        "range", "[)",
                        "value", List.of("2024-01-01", "now"),
                        "targetMetrics", List.of("salesAmount")),
                List.of());
    }

    private Map<String, Object> buildSnapshot(
            String name,
            List<String> columns,
            List<String> groupBy,
            Map<String, Object> timeWindow,
            List<CalculatedFieldDef> calculatedFields) {
        SemanticQueryRequest request = new SemanticQueryRequest();
        request.setColumns(columns);
        List<SemanticQueryRequest.GroupByItem> groupByItems = new ArrayList<>();
        for (String field : groupBy) {
            groupByItems.add(new SemanticQueryRequest.GroupByItem(field, null));
        }
        request.setGroupBy(groupByItems);
        request.setTimeWindow(timeWindow);
        request.setCalculatedFields(calculatedFields);

        SqlGenerationResult result = semanticQueryServiceV3.generateSql(
                TEST_MODEL, request, SemanticRequestContext.empty());
        assertNotNull(result, "generateSql returned null for " + name);
        assertNotNull(result.getSql(), "SQL is null for " + name);

        log.info("{} SQL:\n{}", name, result.getSql());

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("name", name);
        row.put("dialect", "default");
        row.put("sql_normalized", result.getSql());
        row.put("bind_params", result.getParams() != null ? result.getParams() : List.of());
        return row;
    }
}
