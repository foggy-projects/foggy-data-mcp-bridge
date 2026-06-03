package com.foggyframework.dataset.mcp.service.routing;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Classpath-backed catalog for routing clarify questions and structured missing slots.
 */
public final class ClarifyTemplateCatalog {

    private static final String RESOURCE_PATH = "routing/clarify-question-templates.json";

    private final List<ClarifyQuestionTemplate> templates;

    private ClarifyTemplateCatalog(List<ClarifyQuestionTemplate> templates) {
        if (templates == null || templates.isEmpty()) {
            throw new IllegalArgumentException("Clarify question templates must not be empty");
        }
        this.templates = List.copyOf(templates);
    }

    public static ClarifyTemplateCatalog loadDefault() {
        return load(ClarifyTemplateCatalog.class.getClassLoader(), new ObjectMapper());
    }

    static ClarifyTemplateCatalog load(ClassLoader classLoader, ObjectMapper objectMapper) {
        try (InputStream inputStream = classLoader.getResourceAsStream(RESOURCE_PATH)) {
            if (inputStream == null) {
                throw new IllegalStateException("Clarify question templates resource not found: " + RESOURCE_PATH);
            }
            List<ClarifyQuestionTemplate> templates = objectMapper.readValue(
                    inputStream,
                    new TypeReference<>() {
                    }
            );
            return new ClarifyTemplateCatalog(templates);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load clarify question templates: " + RESOURCE_PATH, e);
        }
    }

    public List<String> matchingQuestions(String query, Collection<String> rules) {
        String normalizedQuery = normalizeQuestionText(query);
        Set<String> normalizedRules = normalizedSignalSet(rules);
        LinkedHashSet<String> questions = new LinkedHashSet<>();
        for (ClarifyQuestionTemplate template : templates) {
            if (template.matches(normalizedQuery, normalizedRules)) {
                questions.addAll(template.questions());
            }
        }
        return List.copyOf(questions);
    }

    public List<MissingSlot> matchingMissingSlots(String query, Collection<String> rules) {
        String normalizedQuery = normalizeQuestionText(query);
        Set<String> normalizedRules = normalizedSignalSet(rules);
        LinkedHashSet<MissingSlot> missingSlots = new LinkedHashSet<>();
        for (ClarifyQuestionTemplate template : templates) {
            if (template.matches(normalizedQuery, normalizedRules)) {
                for (String missingSlot : template.missingSlots()) {
                    missingSlots.add(new MissingSlot(missingSlot, template.riskType(), template.ownerRule(), true));
                }
            }
        }
        return List.copyOf(missingSlots);
    }

    private static Set<String> normalizedSignalSet(Collection<String> values) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        if (values == null) {
            return result;
        }
        for (String value : values) {
            String normalized = normalizeQuestionText(value);
            if (!normalized.isBlank()) {
                result.add(normalized);
            }
        }
        return result;
    }

    private static boolean questionTextContainsAny(String text, Collection<String> needles) {
        if (text == null || text.isBlank()) {
            return false;
        }
        for (String needle : needles) {
            if (text.contains(normalizeQuestionText(needle))) {
                return true;
            }
        }
        return false;
    }

    private static boolean questionTextContainsAll(String text, Collection<String> needles) {
        if (text == null || text.isBlank() || needles == null || needles.isEmpty()) {
            return false;
        }
        for (String needle : needles) {
            if (!text.contains(normalizeQuestionText(needle))) {
                return false;
            }
        }
        return true;
    }

    private static String normalizeQuestionText(String value) {
        return safeString(value).toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
    }

    private static String safeString(String value) {
        return value == null ? "" : value.trim();
    }

    public record MissingSlot(
            String slot,
            String type,
            String source,
            boolean required
    ) {
    }

    private record ClarifyQuestionTemplate(
            String domain,
            String riskType,
            String ownerRule,
            List<String> missingSlots,
            List<String> ruleSignals,
            List<List<String>> keywordGroups,
            List<String> keywords,
            List<String> questions
    ) {
        private ClarifyQuestionTemplate {
            missingSlots = missingSlots == null ? List.of() : List.copyOf(missingSlots);
            ruleSignals = ruleSignals == null ? List.of() : List.copyOf(ruleSignals);
            keywordGroups = copyKeywordGroups(keywordGroups);
            keywords = keywords == null ? List.of() : List.copyOf(keywords);
            questions = questions == null ? List.of() : List.copyOf(questions);
        }

        boolean matches(String query, Set<String> rules) {
            for (String ruleSignal : ruleSignals) {
                if (rules.contains(normalizeQuestionText(ruleSignal))) {
                    return true;
                }
            }
            for (List<String> keywordGroup : keywordGroups) {
                if (questionTextContainsAll(query, keywordGroup)) {
                    return true;
                }
            }
            return questionTextContainsAny(query, keywords);
        }
    }

    private static List<List<String>> copyKeywordGroups(List<List<String>> keywordGroups) {
        if (keywordGroups == null) {
            return List.of();
        }
        List<List<String>> copied = new ArrayList<>(keywordGroups.size());
        for (List<String> keywordGroup : keywordGroups) {
            copied.add(keywordGroup == null ? List.of() : List.copyOf(keywordGroup));
        }
        return List.copyOf(copied);
    }
}
