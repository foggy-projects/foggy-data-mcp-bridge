package com.foggyframework.analytics.console.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foggyframework.analytics.console.catalog.AnalyticsConsoleCatalogException;
import com.foggyframework.analytics.console.security.AnalyticsConsoleRole;
import com.foggyframework.analytics.console.security.AnalyticsConsoleSubject;
import com.foggyframework.analytics.console.security.AnalyticsConsoleSubjectResolver;
import com.foggyframework.analytics.function.fap.FapAnalyticsQuestionFunctionCatalog;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Read-only handoff material for an FAP host-owned append-only publisher. */
@RestController
@RequestMapping("/analytics-console/api/v1/integrations/fap")
public final class AnalyticsConsoleFapPublicationController {

    private static final String ROOT = "fap/analytics-question-answering/";
    private static final List<String> DOCUMENTS = List.of(
            "SKILL.md",
            "references/query-model-dsl.md",
            "references/compose-script.md");

    private final AnalyticsConsoleSubjectResolver subjects;
    private final ObjectMapper json;

    public AnalyticsConsoleFapPublicationController(
            AnalyticsConsoleSubjectResolver subjects,
            ObjectMapper json) {
        this.subjects = subjects;
        this.json = json;
    }

    @GetMapping("/question-publication")
    public AnalyticsConsoleEnvelope<PublicationBundle> questionPublication(
            HttpServletRequest request) {
        AnalyticsConsoleSubject subject = subjects.resolve(request);
        if (!subject.hasRole(AnalyticsConsoleRole.ADMIN)) {
            throw new AnalyticsConsoleCatalogException(
                    "ANALYTICS_CONSOLE_FAP_PUBLICATION_FORBIDDEN",
                    "Analytics Console administrator role is required");
        }
        PublicationBundle bundle = new PublicationBundle(
                "foggy.analytics.question-host-sync-bundle.v1",
                "HOST_MANAGED_EXPLICIT",
                false,
                new ProviderCallback(
                        "fap.service-provider.v1alpha1",
                        "POST",
                        "/analytics-console/internal/fap/functions:invoke"),
                resourceJson("skill-metadata.json"),
                DOCUMENTS.stream().map(this::document).toList(),
                resourceJson("function-schema-delivery.json"),
                resourceJson("host-publication-manifest.json"),
                FapAnalyticsQuestionFunctionCatalog.publicationValues());
        return AnalyticsConsoleEnvelope.ok(bundle, "console-" + UUID.randomUUID());
    }

    private Document document(String path) {
        return new Document(path, resourceText(path));
    }

    private JsonNode resourceJson(String path) {
        try {
            return json.readTree(resourceText(path));
        } catch (IOException error) {
            throw new IllegalStateException(
                    "Cannot parse Analytics question publication resource " + path,
                    error);
        }
    }

    private static String resourceText(String path) {
        String name = ROOT + path;
        try (InputStream stream = AnalyticsConsoleFapPublicationController.class
                .getClassLoader().getResourceAsStream(name)) {
            if (stream == null) {
                throw new IllegalStateException(
                        "Missing Analytics question publication resource " + name);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException error) {
            throw new IllegalStateException(
                    "Cannot read Analytics question publication resource " + name,
                    error);
        }
    }

    public record PublicationBundle(
            String contractVersion,
            String publicationMode,
            boolean mutationPerformed,
            ProviderCallback providerCallback,
            JsonNode skillMetadata,
            List<Document> skillDocuments,
            JsonNode functionSchemaDelivery,
            JsonNode hostPublicationManifest,
            List<Map<String, Object>> functions) {
    }

    public record ProviderCallback(String contractVersion, String method, String path) {
    }

    public record Document(String path, String content) {
    }
}
