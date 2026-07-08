package com.foggyframework.runtime.api.service;

import com.foggyframework.dataset.db.model.engine.compose.context.ComposeQueryContext;
import com.foggyframework.dataset.db.model.engine.compose.context.Principal;
import com.foggyframework.dataset.db.model.engine.compose.runtime.ComposeScriptService;
import com.foggyframework.dataset.db.model.engine.compose.security.AuthorityResolution;
import com.foggyframework.dataset.db.model.semantic.service.SemanticQueryServiceV3;
import com.foggyframework.runtime.api.service.RuntimeComposeContextFactory.RuntimeComposeContext;
import com.foggyframework.runtime.api.service.RuntimeComposeDialectResolver.ResolvedDialect;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RuntimeComposeRunnerTest {

    private final SemanticQueryServiceV3 semanticQueryServiceV3 = mock(SemanticQueryServiceV3.class);
    private final RuntimeComposeContextFactory contextFactory = mock(RuntimeComposeContextFactory.class);
    private final RuntimeComposeRunner runner = new RuntimeComposeRunner(semanticQueryServiceV3, contextFactory);

    @Test
    void shouldRejectBlankScriptBeforeCreatingContext() {
        RuntimeComposeInvocation invocation = new RuntimeComposeInvocation(
                " ",
                Map.of(),
                Map.of(),
                null,
                null,
                null,
                null,
                Map.of()
        );

        assertThatThrownBy(() -> runner.run(ComposeScriptService.Mode.VALIDATE, "compose.validate", invocation))
                .isInstanceOfSatisfying(RuntimeComposeException.class, e -> {
                    assertThat(e.toRuntimeError().code()).isEqualTo("COMPOSE_SCRIPT_INVALID");
                    assertThat(e.toRuntimeError().phase()).isEqualTo("compose.validate");
                    assertThat(e.toRuntimeError().safeToAutoRepair()).isTrue();
                });
        verify(contextFactory, never()).create(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldReturnComposeResponseWithDiagnostics() {
        Map<String, Object> params = Map.of("customerId", 42);
        Map<String, Object> options = Map.of("dialect", "sqlserver");
        Map<String, String> headers = Map.of("X-Trace-Id", "trace-header");
        RuntimeComposeInvocation invocation = new RuntimeComposeInvocation(
                "return { plans: [] };",
                params,
                options,
                "body-ns",
                "trace-body",
                "header-ns",
                "Bearer test",
                headers
        );
        when(contextFactory.create(
                eq("body-ns"),
                eq("trace-body"),
                eq(params),
                eq(options),
                eq("header-ns"),
                eq("Bearer test"),
                eq(headers)
        )).thenReturn(context("resolved-ns", "trace-body", params, "sqlserver", "request-options"));

        RuntimeComposeRunner.RuntimeComposeRunResult result =
                runner.run(ComposeScriptService.Mode.VALIDATE, "compose.validate", invocation);

        assertThat(result.response().valid()).isTrue();
        assertThat(result.response().scriptKind()).isEqualTo("compose");
        assertThat(result.response().mode()).isEqualTo("validate");
        assertThat(result.response().diagnostics())
                .containsEntry("namespace", "resolved-ns")
                .containsEntry("resolvedDialect", "sqlserver")
                .containsEntry("dialectSource", "request-options");
        assertThat(result.diagnostics().attributes())
                .containsEntry("namespace", "resolved-ns")
                .containsEntry("resolvedDialect", "sqlserver");
    }

    @Test
    void shouldMapRuntimeFailureAfterDialectResolution() {
        RuntimeComposeInvocation invocation = new RuntimeComposeInvocation(
                "return { plans: [] };",
                Map.of(),
                Map.of(),
                null,
                null,
                null,
                null,
                Map.of()
        );
        when(contextFactory.create(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        )).thenThrow(new IllegalStateException("dialect lookup failed"));

        assertThatThrownBy(() -> runner.run(ComposeScriptService.Mode.PREVIEW, "compose.preview", invocation))
                .isInstanceOfSatisfying(RuntimeComposeException.class, e -> {
                    assertThat(e.toRuntimeError().code()).isEqualTo("COMPOSE_COMPILE_FAILED");
                    assertThat(e.toRuntimeError().phase()).isEqualTo("compose.preview");
                    assertThat(e.toRuntimeError().message()).isEqualTo("dialect lookup failed");
                    assertThat(e.toRuntimeError().safeToAutoRepair()).isFalse();
                    assertThat(e.diagnostics()).isEqualTo(com.foggyframework.runtime.api.dto.RuntimeDiagnostics.empty());
                });
    }

    private static RuntimeComposeContext context(
            String namespace,
            String traceId,
            Map<String, Object> params,
            String dialect,
            String dialectSource
    ) {
        ComposeQueryContext queryContext = ComposeQueryContext.builder()
                .principal(Principal.builder().userId("runtime-api-test").roles(List.of()).build())
                .namespace(namespace)
                .traceId(traceId)
                .params(params)
                .authorityResolver(request -> AuthorityResolution.builder().bindings(Map.of()).build())
                .build();
        ResolvedDialect resolvedDialect = new ResolvedDialect(
                dialect,
                dialectSource,
                null,
                "unbound",
                null,
                "mysql"
        );
        return new RuntimeComposeContext(queryContext, resolvedDialect);
    }
}
