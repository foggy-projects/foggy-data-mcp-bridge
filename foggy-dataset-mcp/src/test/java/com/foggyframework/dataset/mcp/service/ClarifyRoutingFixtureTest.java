package com.foggyframework.dataset.mcp.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foggyframework.dataset.mcp.config.McpProperties;
import com.foggyframework.dataset.mcp.schema.DatasetNLQueryRequest;
import com.foggyframework.dataset.mcp.schema.DatasetNLQueryResponse;
import com.foggyframework.dataset.mcp.service.routing.RoutingCalibrationActionResolver;
import com.foggyframework.dataset.mcp.spi.DatasetAccessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Clarify routing fixture execution")
class ClarifyRoutingFixtureTest {

    private static final String FIXTURE_PATH = "ai-test-cases/clarify-routing-tests.json";

    @Mock
    private ChatClient.Builder chatClientBuilder;

    @Mock
    private DatasetAccessor datasetAccessor;

    @Mock
    private McpProperties mcpProperties;

    @Mock
    private McpToolDispatcher mcpToolDispatcher;

    @Mock
    private McpToolCallbackFactory toolCallbackFactory;

    private QueryExpertService queryExpertService;

    @BeforeEach
    void setUp() {
        when(datasetAccessor.getAccessMode()).thenReturn("mock");
        queryExpertService = new QueryExpertService(
                chatClientBuilder,
                datasetAccessor,
                mcpProperties,
                new ObjectMapper(),
                mcpToolDispatcher,
                toolCallbackFactory,
                new RoutingCalibrationActionResolver()
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("clarifyRoutingCases")
    @DisplayName("fixture 中的 CLARIFY 样本应生成场景化澄清问题")
    @SuppressWarnings("unchecked")
    void fixtureClarifyCase_shouldReturnScenarioAwareQuestions(ClarifyRoutingCase testCase) {
        DatasetNLQueryResponse response = executeClarifyCase(testCase);

        assertEquals("clarify", response.getType());
        assertEquals("ROUTING_TERMINAL_CLARIFY", response.getCode());
        Map<String, Object> detail = (Map<String, Object>) response.getDetail();
        assertEquals("CLARIFY", detail.get("terminal_route"));
        assertEquals(false, detail.get("query_model_execution_allowed"));
        Map<String, Object> routing = (Map<String, Object>) response.getDebug().get("routing_calibration");
        assertEquals("TERMINAL_ROUTE", routing.get("action"));
        assertEquals("CLARIFY", routing.get("calibrated_route"));
        assertQuestionTextContains(response, testCase.questionTerms());
        assertMissingSlotsContain(response, testCase.missingSlots());
        assertStructuredMissingSlotDetails(detail, testCase.missingSlots());
        assertTemplateMatches(detail, testCase.ownerRules(), testCase.riskTypes(), testCase.domains());
        verifyNoInteractions(chatClientBuilder, mcpToolDispatcher, toolCallbackFactory);
    }

    @Test
    @DisplayName("fixture coverage summary 应输出 template match 可观测性基线")
    @SuppressWarnings("unchecked")
    void fixtureCoverageSummary_shouldExposeTemplateMatchMetadata() throws Exception {
        List<ClarifyRoutingCase> cases = clarifyRoutingCases()
                .map(Arguments::get)
                .map(values -> (ClarifyRoutingCase) values[0])
                .toList();

        List<Map<String, Object>> caseSummaries = new ArrayList<>();
        Set<String> domains = new LinkedHashSet<>();
        Set<String> riskTypes = new LinkedHashSet<>();
        Set<String> ownerRules = new LinkedHashSet<>();
        Set<String> expectedOwnerRules = new LinkedHashSet<>();

        for (ClarifyRoutingCase testCase : cases) {
            DatasetNLQueryResponse response = executeClarifyCase(testCase);
            Map<String, Object> detail = (Map<String, Object>) response.getDetail();

            Set<String> caseDomains = templateMatchValues(detail, "domain");
            Set<String> caseRiskTypes = templateMatchValues(detail, "riskType");
            Set<String> caseOwnerRules = templateMatchValues(detail, "ownerRule");
            domains.addAll(caseDomains);
            riskTypes.addAll(caseRiskTypes);
            ownerRules.addAll(caseOwnerRules);
            expectedOwnerRules.addAll(testCase.ownerRules());

            Map<String, Object> caseSummary = new LinkedHashMap<>();
            caseSummary.put("id", testCase.id());
            caseSummary.put("domains", List.copyOf(caseDomains));
            caseSummary.put("riskTypes", List.copyOf(caseRiskTypes));
            caseSummary.put("ownerRules", List.copyOf(caseOwnerRules));
            caseSummary.put("missingSlots", detail.get("clarify_missing_slots"));
            caseSummaries.add(caseSummary);
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("source", FIXTURE_PATH);
        summary.put("caseCount", cases.size());
        summary.put("domainCount", domains.size());
        summary.put("riskTypeCount", riskTypes.size());
        summary.put("ownerRuleCount", ownerRules.size());
        summary.put("domains", List.copyOf(domains));
        summary.put("riskTypes", List.copyOf(riskTypes));
        summary.put("ownerRules", List.copyOf(ownerRules));
        summary.put("cases", caseSummaries);

        Path output = Path.of("target", "clarify-routing-fixture-summary.json");
        Files.createDirectories(output.getParent());
        new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(output.toFile(), summary);

        assertEquals(15, cases.size(), "clarify routing fixture case count should stay explicit");
        assertTrue(domains.size() >= 10, "summary should keep broad domain coverage");
        assertTrue(riskTypes.size() >= 8, "summary should keep broad risk-type coverage");
        assertTrue(ownerRules.containsAll(expectedOwnerRules),
                "summary should include every fixture-declared owner rule");
        assertTrue(ownerRules.size() >= cases.size(),
                "summary should keep at least one matched owner rule per fixture case");
    }

    private static Stream<Arguments> clarifyRoutingCases() throws Exception {
        JsonNode testCases = readFixture().path("testCases");
        List<Arguments> arguments = new ArrayList<>();
        for (JsonNode testCase : testCases) {
            assertEquals("CLARIFY", testCase.path("expected_route").asText());
            arguments.add(Arguments.of(new ClarifyRoutingCase(
                    testCase.path("id").asText(),
                    testCase.path("question").asText(),
                    textValues(testCase.path("expected_rule_signals")),
                    textValues(testCase.path("expected_template_owner_rules")),
                    textValues(testCase.path("expected_template_risk_types")),
                    textValues(testCase.path("expected_template_domains")),
                    textValues(testCase.path("expected_missing_slots")),
                    textValues(testCase.path("expected_question_terms"))
            )));
        }
        return arguments.stream();
    }

    private static JsonNode readFixture() throws Exception {
        try (InputStream inputStream = ClarifyRoutingFixtureTest.class.getClassLoader()
                .getResourceAsStream(FIXTURE_PATH)) {
            if (inputStream == null) {
                throw new IllegalStateException("resource not found: " + FIXTURE_PATH);
            }
            return new ObjectMapper().readTree(inputStream);
        }
    }

    private DatasetNLQueryResponse executeClarifyCase(ClarifyRoutingCase testCase) {
        DatasetNLQueryRequest request = DatasetNLQueryRequest.builder()
                .query(testCase.question())
                .hints(DatasetNLQueryRequest.QueryHints.builder()
                        .extra(Map.of(
                                "routing_calibration_guard", Map.of(
                                        "raw_route", "CLARIFY",
                                        "calibrated_route", "CLARIFY",
                                        "raw_risks", List.of("needs_business_rule", "needs_metric_definition", "needs_time_range"),
                                        "calibrated_risks", List.of("needs_business_rule", "needs_metric_definition", "needs_time_range"),
                                        "applied_rules", testCase.ruleSignals(),
                                        "execution_allowed", true
                                )
                        ))
                        .build())
                .build();

        return queryExpertService.processQuery(request, "trace-" + testCase.id(), null);
    }

    private static List<String> textValues(JsonNode array) {
        List<String> values = new ArrayList<>();
        for (JsonNode value : array) {
            values.add(value.asText());
        }
        return List.copyOf(values);
    }

    private static void assertQuestionTextContains(DatasetNLQueryResponse response, List<String> expectedTerms) {
        assertNotNull(response.getQuestions());
        String text = String.join("\n", response.getQuestions());
        for (String expectedTerm : expectedTerms) {
            assertTrue(text.contains(expectedTerm), "expected clarify questions to contain: "
                    + expectedTerm + ", actual: " + text);
        }
    }

    private static void assertMissingSlotsContain(DatasetNLQueryResponse response, List<String> expectedSlots) {
        assertInstanceOf(List.class, response.getMissing(), "clarify response missing must be a slot list");
        List<?> missing = (List<?>) response.getMissing();
        Set<String> actualSlots = new LinkedHashSet<>();
        for (Object value : missing) {
            actualSlots.add(String.valueOf(value));
        }
        for (String expectedSlot : expectedSlots) {
            assertTrue(actualSlots.contains(expectedSlot), "expected missing slots to contain: "
                    + expectedSlot + ", actual: " + actualSlots);
        }
    }

    @SuppressWarnings("unchecked")
    private static void assertStructuredMissingSlotDetails(Map<String, Object> detail, List<String> expectedSlots) {
        assertInstanceOf(List.class, detail.get("clarify_missing_slots"),
                "clarify detail must expose compatible missing slot names");
        assertInstanceOf(List.class, detail.get("clarify_missing_slot_details"),
                "clarify detail must expose structured missing slot details");

        Set<String> detailSlots = new LinkedHashSet<>();
        for (Object value : (List<?>) detail.get("clarify_missing_slots")) {
            detailSlots.add(String.valueOf(value));
        }

        Set<String> structuredSlots = new LinkedHashSet<>();
        for (Object value : (List<?>) detail.get("clarify_missing_slot_details")) {
            assertInstanceOf(Map.class, value, "structured missing slot detail must be a map");
            Map<String, Object> slotDetail = (Map<String, Object>) value;
            assertNotNull(slotDetail.get("slot"), "structured missing slot must have slot");
            assertNotNull(slotDetail.get("type"), "structured missing slot must have type");
            assertNotNull(slotDetail.get("source"), "structured missing slot must have source");
            String slot = String.valueOf(slotDetail.get("slot"));
            structuredSlots.add(slot);
            assertFalse(slot.isBlank(), "structured missing slot must have slot");
            assertFalse(String.valueOf(slotDetail.get("type")).isBlank(), "structured missing slot must have type");
            assertFalse(String.valueOf(slotDetail.get("source")).isBlank(), "structured missing slot must have source");
            assertInstanceOf(Boolean.class, slotDetail.get("required"), "structured missing slot must have required flag");
        }

        assertEquals(detailSlots, structuredSlots, "compatible and structured missing slots should stay aligned");
        for (String expectedSlot : expectedSlots) {
            assertTrue(structuredSlots.contains(expectedSlot), "expected structured missing slots to contain: "
                    + expectedSlot + ", actual: " + structuredSlots);
        }
    }

    @SuppressWarnings("unchecked")
    private static void assertTemplateMatches(Map<String, Object> detail, List<String> expectedOwnerRules,
                                              List<String> expectedRiskTypes, List<String> expectedDomains) {
        assertInstanceOf(List.class, detail.get("clarify_template_matches"),
                "clarify detail must expose matched template metadata");
        Set<String> ownerRules = new LinkedHashSet<>();
        Set<String> riskTypes = new LinkedHashSet<>();
        Set<String> domains = new LinkedHashSet<>();
        for (Object value : (List<?>) detail.get("clarify_template_matches")) {
            assertInstanceOf(Map.class, value, "template match detail must be a map");
            Map<String, Object> match = (Map<String, Object>) value;
            assertNotNull(match.get("ownerRule"), "template match must expose ownerRule");
            assertNotNull(match.get("riskType"), "template match must expose riskType");
            assertNotNull(match.get("domain"), "template match must expose domain");
            ownerRules.add(String.valueOf(match.get("ownerRule")));
            riskTypes.add(String.valueOf(match.get("riskType")));
            domains.add(String.valueOf(match.get("domain")));
        }
        assertValuesContain(ownerRules, expectedOwnerRules, "ownerRule");
        assertValuesContain(riskTypes, expectedRiskTypes, "riskType");
        assertValuesContain(domains, expectedDomains, "domain");
    }

    @SuppressWarnings("unchecked")
    private static Set<String> templateMatchValues(Map<String, Object> detail, String fieldName) {
        assertInstanceOf(List.class, detail.get("clarify_template_matches"),
                "clarify detail must expose matched template metadata");
        Set<String> values = new LinkedHashSet<>();
        for (Object value : (List<?>) detail.get("clarify_template_matches")) {
            assertInstanceOf(Map.class, value, "template match detail must be a map");
            Object fieldValue = ((Map<String, Object>) value).get(fieldName);
            assertNotNull(fieldValue, "template match must expose " + fieldName);
            values.add(String.valueOf(fieldValue));
        }
        return values;
    }

    private static void assertValuesContain(Set<String> actualValues, List<String> expectedValues, String valueName) {
        assertFalse(expectedValues.isEmpty(), "fixture must expect at least one " + valueName);
        for (String expectedValue : expectedValues) {
            assertTrue(actualValues.contains(expectedValue), "expected template " + valueName + " to contain: "
                    + expectedValue + ", actual: " + actualValues);
        }
    }

    private record ClarifyRoutingCase(
            String id,
            String question,
            List<String> ruleSignals,
            List<String> ownerRules,
            List<String> riskTypes,
            List<String> domains,
            List<String> missingSlots,
            List<String> questionTerms
    ) {
        @Override
        public String toString() {
            return id;
        }
    }
}
