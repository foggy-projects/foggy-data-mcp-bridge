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
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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

        DatasetNLQueryResponse response = queryExpertService.processQuery(request, "trace-" + testCase.id(), null);

        assertEquals("clarify", response.getType());
        assertEquals("ROUTING_TERMINAL_CLARIFY", response.getCode());
        Map<String, Object> detail = (Map<String, Object>) response.getDetail();
        assertEquals("CLARIFY", detail.get("terminal_route"));
        assertEquals(false, detail.get("query_model_execution_allowed"));
        Map<String, Object> routing = (Map<String, Object>) response.getDebug().get("routing_calibration");
        assertEquals("TERMINAL_ROUTE", routing.get("action"));
        assertEquals("CLARIFY", routing.get("calibrated_route"));
        assertQuestionTextContains(response, testCase.questionTerms());
        verifyNoInteractions(chatClientBuilder, mcpToolDispatcher, toolCallbackFactory);
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

    private record ClarifyRoutingCase(
            String id,
            String question,
            List<String> ruleSignals,
            List<String> questionTerms
    ) {
        @Override
        public String toString() {
            return id;
        }
    }
}
