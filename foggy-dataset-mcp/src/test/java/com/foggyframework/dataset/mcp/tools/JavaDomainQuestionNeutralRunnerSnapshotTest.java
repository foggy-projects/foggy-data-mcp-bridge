package com.foggyframework.dataset.mcp.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Java-side producer for Python P0-31 domain/question neutral runner replay.
 *
 * <p>The fixture intentionally exports normalized MCP tool arguments instead
 * of LLM transcripts. Python can replay this boundary without Odoo models or a
 * live model provider.</p>
 */
@DisplayName("JavaDomainQuestionNeutralRunnerSnapshotTest · Python alignment P0-31/P0-41/P0-47")
class JavaDomainQuestionNeutralRunnerSnapshotTest {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    @Test
    @DisplayName("writes java_domain_question_neutral_runner_parity.json for Python replay")
    void shouldProduceDomainQuestionNeutralRunnerSnapshot() throws Exception {
        Map<String, Object> snapshot = ordered();
        snapshot.put("schemaVersion", 1);
        snapshot.put("feature", "domainQuestionNeutralRunner");
        snapshot.put("source", "JavaDomainQuestionNeutralRunnerSnapshotTest");
        snapshot.put("contract", "normalized-tool-arguments-v1");
        snapshot.put("cases", cases());

        for (Map<String, Object> c : cases()) {
            assertJavaFixtureContract(c);
        }

        Path pythonTarget = pythonFixturePath();
        Files.createDirectories(pythonTarget.getParent());
        MAPPER.writeValue(pythonTarget.toFile(), snapshot);

        Path localCopy = Path.of("target", "parity",
                "java_domain_question_neutral_runner_parity.json");
        Files.createDirectories(localCopy.getParent());
        MAPPER.writeValue(localCopy.toFile(), snapshot);
        assertTrue(Files.exists(pythonTarget),
                "snapshot was not written: " + pythonTarget.toAbsolutePath());
    }

    private static List<Map<String, Object>> cases() {
        return List.of(
                caseDef(
                        "sales-by-status-current-quarter",
                        "sales by status this quarter",
                        expectedQueryModel(
                                "FactSalesModel",
                                "execute",
                                payload(
                                        List.of("salesAmount"),
                                        List.of("orderStatus"),
                                        List.of(Map.of("field", "salesAmount", "order", "desc")),
                                        Map.of("field", "orderDate", "preset", "currentQuarter"),
                                        List.of(),
                                        List.of(),
                                        10
                                ),
                                List.of("GROUP BY", "orderStatus", "salesAmount"),
                                List.of("orderStatus", "salesAmount"),
                                List.of("timeWindow:currentQuarter"),
                                null
                        )
                ),
                caseDef(
                        "gross-margin-by-month-calculated",
                        "gross margin by month for closed orders",
                        expectedQueryModel(
                                "FactSalesModel",
                                "execute",
                                payload(
                                        List.of("grossMargin"),
                                        List.of("orderMonth"),
                                        List.of(),
                                        null,
                                        List.of(Map.of(
                                                "name", "grossMargin",
                                                "expression", "salesAmount - costAmount"
                                        )),
                                        List.of(Map.of("field", "orderStatus", "op", "eq", "value", "closed")),
                                        20
                                ),
                                List.of("grossMargin", "orderMonth", "salesAmount - costAmount"),
                                List.of("orderMonth", "grossMargin"),
                                List.of("calculatedFields:grossMargin"),
                                null
                        )
                ),
                caseDef(
                        "denied-customer-email-fail-closed",
                        "show customer emails for recent sales",
                        expectedQueryModel(
                                "FactSalesModel",
                                "validate",
                                payload(
                                        List.of("customerEmail"),
                                        List.of(),
                                        List.of(),
                                        null,
                                        List.of(),
                                        List.of(),
                                        50,
                                        List.of(Map.of(
                                                "table", "fact_sales",
                                                "column", "customer_email",
                                                "field", "customerEmail"
                                        ))
                                ),
                                List.of(),
                                List.of(),
                                List.of("deniedColumns:customerEmail"),
                                "governance/denied-field"
                        )
                ),
                caseDef(
                        "pivot-time-window-mutual-exclusion-unsupported",
                        "pivot sales by status and also compare this quarter year over year",
                        expectedQueryModel(
                                "FactSalesModel",
                                "validate",
                                with(
                                        payload(
                                                List.of(),
                                                List.of(),
                                                List.of(),
                                                Map.of("field", "orderDate", "preset", "currentQuarter"),
                                                List.of(),
                                                List.of(),
                                                50
                                        ),
                                        Map.of(
                                                "pivot", Map.of(
                                                        "rows", List.of("orderStatus"),
                                                        "columns", List.of("orderMonth"),
                                                        "metrics", List.of("salesAmount"),
                                                        "outputFormat", "grid"
                                                )
                                        )
                                ),
                                List.of(),
                                List.of(),
                                List.of("unsupported:pivot+timeWindow"),
                                "domain-question/unsupported-construct",
                                List.of("pivot+timeWindow")
                        )
                ),
                caseDef(
                        "hidden-axis-function-calculated-field-unsupported",
                        "show sales ratio against the first pivot column",
                        expectedQueryModel(
                                "FactSalesModel",
                                "validate",
                                payload(
                                        List.of("axisRatio"),
                                        List.of("orderStatus"),
                                        List.of(),
                                        null,
                                        List.of(Map.of(
                                                "name", "axisRatio",
                                                "expression", "CELL_AT('columns', 'first')"
                                        )),
                                        List.of(),
                                        50
                                ),
                                List.of(),
                                List.of(),
                                List.of("unsupported:hidden-axis-function"),
                                "domain-question/unsupported-construct",
                                List.of("CELL_AT")
                        )
                ),
                caseDef(
                        "cross-model-join-needs-compose-script-unsupported",
                        "join sales with support tickets by customer",
                        expectedQueryModel(
                                "FactSalesModel",
                                "validate",
                                with(
                                        payload(
                                                List.of("salesAmount"),
                                                List.of("customerId"),
                                                List.of(),
                                                null,
                                                List.of(),
                                                List.of(),
                                                50
                                        ),
                                        Map.of(
                                                "hints", Map.of(
                                                        "requestedConstruct", "crossModelJoin",
                                                        "recommendedTool", "dataset.compose_script"
                                                )
                                        )
                                ),
                                List.of(),
                                List.of(),
                                List.of("unsupported:cross-model-join"),
                                "domain-question/unsupported-construct",
                                List.of("crossModelJoin")
                        )
                )
        );
    }

    private static Map<String, Object> caseDef(String id, String question,
                                               Map<String, Object> expected) {
        Map<String, Object> c = ordered();
        c.put("id", id);
        c.put("question", question);
        c.put("context", Map.of(
                "namespace", "demo",
                "principal", Map.of(
                        "userId", "snapshot-user",
                        "tenantId", "demo",
                        "roles", List.of("analyst")
                )
        ));
        c.put("expected", expected);
        return c;
    }

    private static Map<String, Object> expectedQueryModel(String model,
                                                          String mode,
                                                          Map<String, Object> payload,
                                                          List<String> sqlMarkers,
                                                          List<String> resultMarkers,
                                                          List<String> warnings,
                                                          String errorCode) {
        return expectedQueryModel(
                model,
                mode,
                payload,
                sqlMarkers,
                resultMarkers,
                warnings,
                errorCode,
                List.of()
        );
    }

    private static Map<String, Object> expectedQueryModel(String model,
                                                          String mode,
                                                          Map<String, Object> payload,
                                                          List<String> sqlMarkers,
                                                          List<String> resultMarkers,
                                                          List<String> warnings,
                                                          String errorCode,
                                                          List<String> unsupportedConstructs) {
        Map<String, Object> toolArguments = ordered();
        toolArguments.put("model", model);
        toolArguments.put("mode", mode);
        toolArguments.put("payload", payload);

        Map<String, Object> expected = ordered();
        expected.put("toolName", "dataset.query_model");
        expected.put("toolArguments", toolArguments);
        expected.put("sqlMarkers", sqlMarkers);
        expected.put("resultMarkers", resultMarkers);
        expected.put("warnings", warnings);
        expected.put("errorCode", errorCode);
        if (!unsupportedConstructs.isEmpty()) {
            expected.put("unsupportedConstructs", unsupportedConstructs);
        }
        expected.put("reports", List.of(reportMetadata(model, mode, warnings, errorCode, unsupportedConstructs)));
        expected.put("forbiddenMarkers", List.of("odoo", "res_partner", "res_users", "LLM"));
        return expected;
    }

    private static Map<String, Object> reportMetadata(String model,
                                                      String mode,
                                                      List<String> warnings,
                                                      String errorCode,
                                                      List<String> unsupportedConstructs) {
        Map<String, Object> report = ordered();
        report.put("reportType", "neutral-runner-case-summary");
        report.put("toolName", "dataset.query_model");
        report.put("model", model);
        report.put("mode", mode);
        report.put("status", errorCode == null ? "ok" : "error");
        report.put("warningCount", warnings.size());
        report.put("errorCount", errorCode == null ? 0 : 1);
        report.put("warningMarkers", warnings);
        if (errorCode != null) {
            report.put("errorCode", errorCode);
        }
        if (!unsupportedConstructs.isEmpty()) {
            report.put("unsupportedConstructs", unsupportedConstructs);
        }
        return report;
    }

    private static Map<String, Object> payload(List<String> columns,
                                               List<String> groupBy,
                                               List<Map<String, Object>> orderBy,
                                               Map<String, Object> timeWindow,
                                               List<Map<String, Object>> calculatedFields,
                                               List<Map<String, Object>> slice,
                                               int limit) {
        return payload(columns, groupBy, orderBy, timeWindow, calculatedFields, slice, limit, List.of());
    }

    private static Map<String, Object> payload(List<String> columns,
                                               List<String> groupBy,
                                               List<Map<String, Object>> orderBy,
                                               Map<String, Object> timeWindow,
                                               List<Map<String, Object>> calculatedFields,
                                               List<Map<String, Object>> slice,
                                               int limit,
                                               List<Map<String, Object>> deniedColumns) {
        Map<String, Object> p = ordered();
        p.put("columns", columns);
        p.put("groupBy", groupBy);
        p.put("orderBy", orderBy);
        p.put("slice", slice);
        p.put("calculatedFields", calculatedFields);
        p.put("start", 0);
        p.put("limit", limit);
        p.put("returnTotal", false);
        if (timeWindow != null) {
            p.put("timeWindow", timeWindow);
        }
        if (!deniedColumns.isEmpty()) {
            p.put("deniedColumns", deniedColumns);
        }
        return p;
    }

    private static Map<String, Object> with(Map<String, Object> payload, Map<String, Object> extras) {
        payload.putAll(extras);
        return payload;
    }

    private static void assertJavaFixtureContract(Map<String, Object> c) {
        @SuppressWarnings("unchecked")
        Map<String, Object> expected = (Map<String, Object>) c.get("expected");
        assertEquals("dataset.query_model", expected.get("toolName"));

        @SuppressWarnings("unchecked")
        Map<String, Object> toolArguments = (Map<String, Object>) expected.get("toolArguments");
        assertEquals("FactSalesModel", toolArguments.get("model"));
        assertTrue(List.of("execute", "validate").contains(toolArguments.get("mode")));

        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) toolArguments.get("payload");
        assertTrue(payload.containsKey("columns"));
        assertTrue(payload.containsKey("start"));
        assertTrue(payload.containsKey("limit"));

        @SuppressWarnings("unchecked")
        List<String> warnings = (List<String>) expected.get("warnings");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> reports = (List<Map<String, Object>>) expected.get("reports");
        assertEquals(1, reports.size());
        Map<String, Object> report = reports.get(0);
        assertEquals("neutral-runner-case-summary", report.get("reportType"));
        assertEquals(expected.get("toolName"), report.get("toolName"));
        assertEquals(toolArguments.get("model"), report.get("model"));
        assertEquals(toolArguments.get("mode"), report.get("mode"));
        assertEquals(warnings.size(), report.get("warningCount"));
        assertEquals(expected.get("errorCode") == null ? 0 : 1, report.get("errorCount"));

        String serialized = toolArguments.toString();
        @SuppressWarnings("unchecked")
        List<String> forbiddenMarkers = (List<String>) expected.get("forbiddenMarkers");
        for (String marker : forbiddenMarkers) {
            assertFalse(serialized.contains(marker),
                    "neutral fixture leaked forbidden marker: " + marker);
        }
    }

    private static Path pythonFixturePath() {
        for (Path pythonRoot : List.of(
                Path.of("..", "foggy-data-mcp-bridge-python"),
                Path.of("..", "..", "foggy-data-mcp-bridge-python")
        )) {
            Path fixture = pythonRoot
                    .resolve("tests")
                    .resolve("fixtures")
                    .resolve("java_domain_question_neutral_runner_parity.json")
                    .normalize();
            if (Files.exists(pythonRoot.resolve("pyproject.toml").normalize())) {
                return fixture;
            }
        }
        throw new IllegalStateException("Unable to locate foggy-data-mcp-bridge-python from "
                + Path.of("").toAbsolutePath());
    }

    private static Map<String, Object> ordered() {
        return new LinkedHashMap<>();
    }
}
