package com.foggyframework.dataset.db.model.semantic.service;

import com.foggyframework.dataset.db.model.engine.compose.security.AuthorityResolver;
import com.foggyframework.dataset.db.model.semantic.port.ComposeCaller;
import com.foggyframework.dataset.db.model.semantic.port.ComposeExecutionException;
import com.foggyframework.dataset.db.model.semantic.port.ComposeExecutionRequest;
import com.foggyframework.dataset.db.model.semantic.port.ComposeExecutionResult;
import com.foggyframework.dataset.db.model.semantic.port.ComposeOperation;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DefaultComposeExecutionPortTest {

    private final SemanticQueryServiceV3 semanticQueryService = mock(SemanticQueryServiceV3.class);
    private final ObjectProvider<AuthorityResolver> authorityResolvers = authorityResolvers();
    private final DefaultComposeExecutionPort port =
            new DefaultComposeExecutionPort(semanticQueryService, authorityResolvers, "mysql");

    @Test
    void shouldExecuteThroughDtoOnlyBoundary() {
        ComposeExecutionResult result = port.execute(request(
                ComposeOperation.VALIDATE,
                "return { plans: [] };",
                Map.of("limit", 1)));

        assertThat(result.valid()).isTrue();
        assertThat(result.executed()).isFalse();
        assertThat(result.operation()).isEqualTo(ComposeOperation.VALIDATE);
        assertThat(result.value()).isEqualTo(Map.of("plans", List.of()));
    }

    @Test
    void shouldTranslateSandboxExceptionAtPortBoundary() {
        assertThatThrownBy(() -> port.execute(request(
                ComposeOperation.VALIDATE,
                "import java.lang.System; return { plans: [] };",
                Map.of())))
                .isInstanceOfSatisfying(ComposeExecutionException.class, e -> {
                    assertThat(e.kind()).isEqualTo(ComposeExecutionException.Kind.SANDBOX);
                    assertThat(e.code()).startsWith("compose-sandbox-violation/");
                });
    }

    private static ComposeExecutionRequest request(
            ComposeOperation operation,
            String script,
            Map<String, Object> params
    ) {
        return new ComposeExecutionRequest(
                operation,
                script,
                "test-ns",
                "trace-1",
                params,
                new ComposeCaller("tester", "tenant-1", List.of("analyst"),
                        null, "Bearer test", "policy-1"),
                "mysql");
    }

    @SuppressWarnings("unchecked")
    private static ObjectProvider<AuthorityResolver> authorityResolvers() {
        ObjectProvider<AuthorityResolver> provider = mock(ObjectProvider.class);
        when(provider.orderedStream()).thenReturn(Stream.empty());
        return provider;
    }
}
