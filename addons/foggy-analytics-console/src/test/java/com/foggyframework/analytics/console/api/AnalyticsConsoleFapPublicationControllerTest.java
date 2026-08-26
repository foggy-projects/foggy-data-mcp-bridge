package com.foggyframework.analytics.console.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foggyframework.analytics.console.catalog.AnalyticsConsoleCatalogException;
import com.foggyframework.analytics.console.security.AnalyticsConsoleRole;
import com.foggyframework.analytics.console.security.AnalyticsConsoleSubject;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class AnalyticsConsoleFapPublicationControllerTest {

    @Test
    void adminCanExportCompleteReadOnlyQuestionPublicationBundle() {
        AnalyticsConsoleFapPublicationController controller = controller(
                Set.of(AnalyticsConsoleRole.ADMIN));

        var response = controller.questionPublication(mock(HttpServletRequest.class));
        var bundle = response.data();

        assertThat(response.success()).isTrue();
        assertThat(bundle.contractVersion())
                .isEqualTo("foggy.analytics.question-host-sync-bundle.v1");
        assertThat(bundle.publicationMode()).isEqualTo("HOST_MANAGED_EXPLICIT");
        assertThat(bundle.mutationPerformed()).isFalse();
        assertThat(bundle.providerCallback().path())
                .isEqualTo("/analytics-console/internal/fap/functions:invoke");
        assertThat(bundle.skillMetadata().path("revision").asInt()).isEqualTo(7);
        assertThat(bundle.skillDocuments())
                .extracting(AnalyticsConsoleFapPublicationController.Document::path)
                .containsExactly(
                        "SKILL.md",
                        "references/query-model-dsl.md",
                        "references/compose-script.md");
        assertThat(bundle.functions())
                .extracting(value -> value.get("functionRef"))
                .containsExactly(
                        "foggy.analytics.model-dependencies.list@v3",
                        "foggy.analytics.semantic-models.describe@v2",
                        "foggy.analytics.semantic-queries.execute@v2",
                        "foggy.analytics.query-model.run@v2",
                        "foggy.analytics.compose.run@v1");
    }

    @Test
    void nonAdminCannotExportQuestionPublicationBundle() {
        AnalyticsConsoleFapPublicationController controller = controller(
                Set.of(AnalyticsConsoleRole.DESIGNER));

        assertThatThrownBy(() -> controller.questionPublication(
                mock(HttpServletRequest.class)))
                .isInstanceOf(AnalyticsConsoleCatalogException.class)
                .hasMessage("Analytics Console administrator role is required");
    }

    private static AnalyticsConsoleFapPublicationController controller(
            Set<AnalyticsConsoleRole> roles) {
        AnalyticsConsoleSubject subject = new AnalyticsConsoleSubject(
                "subject-1",
                "Test subject",
                roles,
                "test",
                "authority-1");
        return new AnalyticsConsoleFapPublicationController(
                ignored -> subject,
                new ObjectMapper());
    }
}
