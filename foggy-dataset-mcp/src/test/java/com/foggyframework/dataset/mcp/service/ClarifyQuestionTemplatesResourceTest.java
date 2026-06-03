package com.foggyframework.dataset.mcp.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Clarify question templates resource")
class ClarifyQuestionTemplatesResourceTest {

    private static final String RESOURCE_PATH = "routing/clarify-question-templates.json";
    private static final String FIXTURE_PATH = "ai-test-cases/clarify-routing-tests.json";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("资源应可从 classpath 加载且结构完整")
    void resource_shouldLoadFromClasspathAndHaveValidShape() throws Exception {
        JsonNode templates = readTemplates();

        assertTrue(templates.isArray(), "clarify templates root must be an array");
        assertTrue(templates.size() >= 20, "clarify templates should keep scenario coverage");

        Set<String> templateSignatures = new HashSet<>();
        Set<String> ownerRules = new HashSet<>();
        Set<String> domains = new LinkedHashSet<>();
        for (int i = 0; i < templates.size(); i++) {
            JsonNode template = templates.get(i);
            JsonNode ruleSignals = template.path("ruleSignals");
            JsonNode missingSlots = template.path("missingSlots");
            JsonNode keywordGroups = template.path("keywordGroups");
            JsonNode keywords = template.path("keywords");
            JsonNode questions = template.path("questions");
            String domain = requireNonBlankText(template, "domain", i);
            requireNonBlankText(template, "riskType", i);
            String ownerRule = requireNonBlankText(template, "ownerRule", i);

            assertTrue(ruleSignals.isMissingNode() || ruleSignals.isArray(), "ruleSignals must be an array at index " + i);
            assertTrue(missingSlots.isArray(), "missingSlots must be an array at index " + i);
            assertTrue(keywordGroups.isMissingNode() || keywordGroups.isArray(),
                    "keywordGroups must be an array at index " + i);
            assertTrue(keywords.isMissingNode() || keywords.isArray(), "keywords must be an array at index " + i);
            assertTrue(questions.isArray(), "questions must be an array at index " + i);
            assertTrue(ruleSignals.size() > 0 || keywordGroups.size() > 0 || keywords.size() > 0,
                    "template must have a matcher at index " + i);
            assertTrue(missingSlots.size() > 0, "missingSlots must not be empty at index " + i);
            assertTrue(questions.size() > 0, "questions must not be empty at index " + i);
            assertTrue(ownerRules.add(ownerRule), "duplicate clarify ownerRule: " + ownerRule);

            domains.add(domain);
            assertTextArrayHasOnlyNonBlankValues(ruleSignals, "ruleSignals", i);
            assertTextArrayHasOnlyNonBlankValues(missingSlots, "missingSlots", i);
            assertKeywordGroupsHaveOnlyNonBlankValues(keywordGroups, i);
            assertTextArrayHasOnlyNonBlankValues(keywords, "keywords", i);
            assertTextArrayHasOnlyNonBlankValues(questions, "questions", i);
            assertTrue(templateSignatures.add(template.toString()), "duplicate clarify template at index " + i);
        }
        assertTrue(domains.size() >= 10, "clarify templates should keep cross-domain metadata coverage");
    }

    @Test
    @DisplayName("资源应保留关键校准规则的澄清模板")
    void resource_shouldKeepCriticalRuleSignalCoverage() throws Exception {
        JsonNode templates = readTemplates();
        Set<String> ruleSignals = new LinkedHashSet<>();

        for (JsonNode template : templates) {
            for (JsonNode ruleSignal : template.path("ruleSignals")) {
                ruleSignals.add(ruleSignal.asText());
            }
        }

        assertTrue(ruleSignals.contains("subscription_renewal_risk_boundary"));
        assertTrue(ruleSignals.contains("marketing_attribution_boundary"));
        assertTrue(ruleSignals.contains("manufacturing_yield_boundary"));
        assertTrue(ruleSignals.contains("unbounded_memory_governance"));
        assertTrue(ruleSignals.contains("sales_target_version_guard"));
        assertTrue(ruleSignals.contains("budget_or_target_ambiguity"));
    }

    @Test
    @DisplayName("Clarify routing fixture 应引用现有模板关键词和规则信号")
    void fixture_shouldMapToClarifyTemplateCatalog() throws Exception {
        JsonNode templates = readTemplates();
        JsonNode fixture = readJson(FIXTURE_PATH);
        Set<String> catalogRuleSignals = collectTextValues(templates, "ruleSignals");
        Set<String> catalogKeywords = collectTextValues(templates, "keywords");
        Set<String> catalogMissingSlots = collectTextValues(templates, "missingSlots");
        Set<String> catalogQuestions = collectTextValues(templates, "questions");

        assertEquals(RESOURCE_PATH, fixture.path("source_catalog").asText());
        JsonNode testCases = fixture.path("testCases");
        assertTrue(testCases.isArray(), "clarify routing fixture testCases must be an array");
        assertTrue(testCases.size() >= 10, "clarify routing fixture should keep representative scenario coverage");

        Set<String> testCaseIds = new HashSet<>();
        for (int i = 0; i < testCases.size(); i++) {
            JsonNode testCase = testCases.get(i);
            String id = requireNonBlankText(testCase, "id", i);
            requireNonBlankText(testCase, "scenario", i);
            requireNonBlankText(testCase, "question", i);
            assertEquals("CLARIFY", testCase.path("expected_route").asText(), "expected_route at index " + i);
            assertTrue(testCaseIds.add(id), "duplicate clarify routing fixture id: " + id);

            JsonNode expectedRuleSignals = testCase.path("expected_rule_signals");
            JsonNode expectedTemplateKeywords = testCase.path("expected_template_keywords");
            JsonNode expectedMissingSlots = testCase.path("expected_missing_slots");
            JsonNode expectedQuestionTerms = testCase.path("expected_question_terms");
            assertTrue(expectedRuleSignals.isMissingNode() || expectedRuleSignals.isArray(),
                    "expected_rule_signals must be an array at index " + i);
            assertTrue(expectedTemplateKeywords.isArray(), "expected_template_keywords must be an array at index " + i);
            assertTrue(expectedMissingSlots.isArray(), "expected_missing_slots must be an array at index " + i);
            assertTrue(expectedQuestionTerms.isArray(), "expected_question_terms must be an array at index " + i);
            assertTrue(expectedRuleSignals.size() > 0 || expectedTemplateKeywords.size() > 0,
                    "fixture must reference rule signals or template keywords at index " + i);
            assertTrue(expectedMissingSlots.size() > 0, "expected_missing_slots must not be empty at index " + i);
            assertTrue(expectedQuestionTerms.size() > 0, "expected_question_terms must not be empty at index " + i);

            assertFixtureValuesExist(expectedRuleSignals, catalogRuleSignals, "rule signal", id, i);
            assertFixtureValuesExist(expectedTemplateKeywords, catalogKeywords, "template keyword", id, i);
            assertFixtureValuesExist(expectedMissingSlots, catalogMissingSlots, "missing slot", id, i);
            assertTextArrayHasOnlyNonBlankValues(expectedQuestionTerms, "expected_question_terms", i);
            assertFixtureTermsExistInCatalogQuestions(expectedQuestionTerms, catalogQuestions, id);
        }
    }

    private JsonNode readTemplates() throws Exception {
        return readJson(RESOURCE_PATH);
    }

    private JsonNode readJson(String resourcePath) throws Exception {
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            assertNotNull(inputStream, "resource not found: " + resourcePath);
            return objectMapper.readTree(inputStream);
        }
    }

    private void assertTextArrayHasOnlyNonBlankValues(JsonNode array, String fieldName, int templateIndex) {
        Iterator<JsonNode> elements = array.elements();
        int valueIndex = 0;
        while (elements.hasNext()) {
            JsonNode value = elements.next();
            assertTrue(value.isTextual(), fieldName + " value must be text at template " + templateIndex
                    + ", value " + valueIndex);
            assertFalse(value.asText().isBlank(), fieldName + " value must not be blank at template " + templateIndex
                    + ", value " + valueIndex);
            valueIndex++;
        }
    }

    private void assertKeywordGroupsHaveOnlyNonBlankValues(JsonNode keywordGroups, int templateIndex) {
        Iterator<JsonNode> groups = keywordGroups.elements();
        int groupIndex = 0;
        while (groups.hasNext()) {
            JsonNode group = groups.next();
            assertTrue(group.isArray(), "keywordGroups value must be an array at template " + templateIndex
                    + ", group " + groupIndex);
            assertTrue(group.size() > 0, "keywordGroups group must not be empty at template " + templateIndex
                    + ", group " + groupIndex);
            assertTextArrayHasOnlyNonBlankValues(group, "keywordGroups", templateIndex);
            groupIndex++;
        }
    }

    private Set<String> collectTextValues(JsonNode templates, String fieldName) {
        Set<String> values = new LinkedHashSet<>();
        for (JsonNode template : templates) {
            for (JsonNode value : template.path(fieldName)) {
                values.add(value.asText());
            }
        }
        return values;
    }

    private String requireNonBlankText(JsonNode node, String fieldName, int index) {
        JsonNode value = node.path(fieldName);
        assertTrue(value.isTextual(), fieldName + " must be text at index " + index);
        assertFalse(value.asText().isBlank(), fieldName + " must not be blank at index " + index);
        return value.asText();
    }

    private void assertFixtureValuesExist(JsonNode fixtureValues, Set<String> catalogValues, String valueName,
                                          String testCaseId, int testCaseIndex) {
        assertTextArrayHasOnlyNonBlankValues(fixtureValues, valueName, testCaseIndex);
        for (JsonNode value : fixtureValues) {
            assertTrue(catalogValues.contains(value.asText()), "unknown " + valueName + " '" + value.asText()
                    + "' in fixture " + testCaseId);
        }
    }

    private void assertFixtureTermsExistInCatalogQuestions(JsonNode expectedTerms, Set<String> catalogQuestions,
                                                           String testCaseId) {
        for (JsonNode expectedTerm : expectedTerms) {
            assertTrue(catalogQuestions.stream().anyMatch(question -> question.contains(expectedTerm.asText())),
                    "expected question term '" + expectedTerm.asText()
                            + "' is not present in clarify catalog questions for fixture " + testCaseId);
        }
    }
}
