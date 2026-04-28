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

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TimeWindow post-scalar calculatedFields SQL snapshot producer (Stage 3).
 *
 * <p>Mirrors the pattern of {@link FormulaParitySnapshotTest}: drives the
 * real Java {@link SemanticQueryServiceV3#generateSql} pipeline to produce
 * SQL for two post-scalar calculatedFields happy cases, normalizes the output
 * and writes a JSON snapshot consumed by the Python golden diff harness at
 * {@code tests/integration/test_time_window_golden_diff.py}.</p>
 *
 * <p>Target cases (from Java 8.5.0 timeWindow fixtures):</p>
 * <ul>
 *   <li>{@code yoy-month-post-calc-growth-happy} — growthPercent = salesAmount__ratio * 100</li>
 *   <li>{@code rolling_7d-post-calc-gap-happy} — rollingGap = salesAmount - salesAmount__rolling_7d</li>
 * </ul>
 */
@Slf4j
@DisplayName("TimeWindowParitySnapshotTest · Stage 3 (Java side)")
class TimeWindowParitySnapshotTest extends EcommerceTestSupport {

    private static final String TEST_MODEL = "FactSalesQueryModel";

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

        // Case 1: yoy-month-post-calc-growth-happy
        snapshots.add(buildYoyPostCalcGrowthSnapshot());

        // Case 2: rolling_7d-post-calc-gap-happy
        snapshots.add(buildRolling7dPostCalcGapSnapshot());

        assertTrue(snapshots.size() == 2,
                "expected exactly 2 timeWindow snapshots, got " + snapshots.size());

        // Assemble the snapshot JSON
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("schema_version", "1");
        snapshot.put("source", "TimeWindowParitySnapshotTest");
        snapshot.put("feature", "timeWindow");
        snapshot.put("snapshots", snapshots);

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

    private Map<String, Object> buildYoyPostCalcGrowthSnapshot() {
        // Request shape mirrors java_time_window_parity_catalog.json
        // case "yoy-month-post-calc-growth-happy"
        //
        // Stage 5: request columns now include the post-calc output alias.
        // SchemaAwareFieldValidationStep is skipped when timeWindow is active
        // (isSkipQuery=true), and collectSchemaFields() adds request-level
        // calculatedFields.name to the valid set anyway.
        SemanticQueryRequest request = new SemanticQueryRequest();
        request.setColumns(List.of(
                "salesDate$year", "salesDate$month",
                "salesAmount", "salesAmount__prior",
                "salesAmount__diff", "salesAmount__ratio",
                "growthPercent"));
        request.setGroupBy(List.of(
                new SemanticQueryRequest.GroupByItem("salesDate$year", null),
                new SemanticQueryRequest.GroupByItem("salesDate$month", null)));
        request.setTimeWindow(Map.of(
                "field", "salesDate$id",
                "grain", "month",
                "comparison", "yoy",
                "range", "[)",
                "value", List.of("2024-01-01", "2025-01-01"),
                "targetMetrics", List.of("salesAmount")));
        request.setCalculatedFields(List.of(
                new CalculatedFieldDef("growthPercent", "salesAmount__ratio * 100")));

        SqlGenerationResult result = semanticQueryServiceV3.generateSql(
                TEST_MODEL, request, SemanticRequestContext.empty());
        assertNotNull(result, "generateSql returned null for yoy-month-post-calc-growth-happy");
        assertNotNull(result.getSql(), "SQL is null for yoy-month-post-calc-growth-happy");

        log.info("yoy-month-post-calc-growth-happy SQL:\n{}", result.getSql());

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("name", "yoy-month-post-calc-growth-happy");
        row.put("dialect", "default");
        row.put("sql_normalized", result.getSql());
        row.put("bind_params", result.getParams() != null ? result.getParams() : List.of());
        return row;
    }

    private Map<String, Object> buildRolling7dPostCalcGapSnapshot() {
        // Request shape mirrors java_time_window_parity_catalog.json
        // case "rolling_7d-post-calc-gap-happy"
        //
        // Stage 5: request columns now include the post-calc output alias.
        SemanticQueryRequest request = new SemanticQueryRequest();
        request.setColumns(List.of(
                "salesDate$id", "salesAmount",
                "salesAmount__rolling_7d",
                "rollingGap"));
        request.setGroupBy(List.of(
                new SemanticQueryRequest.GroupByItem("salesDate$id", null)));
        request.setTimeWindow(Map.of(
                "field", "salesDate$id",
                "grain", "day",
                "comparison", "rolling_7d",
                "range", "[)",
                "value", List.of("-1M", "now"),
                "targetMetrics", List.of("salesAmount")));
        request.setCalculatedFields(List.of(
                new CalculatedFieldDef("rollingGap", "salesAmount - salesAmount__rolling_7d")));

        SqlGenerationResult result = semanticQueryServiceV3.generateSql(
                TEST_MODEL, request, SemanticRequestContext.empty());
        assertNotNull(result, "generateSql returned null for rolling_7d-post-calc-gap-happy");
        assertNotNull(result.getSql(), "SQL is null for rolling_7d-post-calc-gap-happy");

        log.info("rolling_7d-post-calc-gap-happy SQL:\n{}", result.getSql());

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("name", "rolling_7d-post-calc-gap-happy");
        row.put("dialect", "default");
        row.put("sql_normalized", result.getSql());
        row.put("bind_params", result.getParams() != null ? result.getParams() : List.of());
        return row;
    }
}
