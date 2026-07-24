package com.foggyframework.runtime.api.service;

import com.foggyframework.dataset.model.semantic.port.ComposeCaller;
import com.foggyframework.dataset.model.semantic.port.ComposeExecutionException;
import com.foggyframework.dataset.model.semantic.port.ComposeExecutionPort;
import com.foggyframework.dataset.model.semantic.port.ComposeExecutionRequest;
import com.foggyframework.dataset.model.semantic.port.ComposeExecutionResult;
import com.foggyframework.dataset.model.semantic.port.ComposeOperation;
import com.foggyframework.runtime.api.service.RuntimeComposeContextFactory.RuntimeComposeContext;
import com.foggyframework.runtime.api.service.RuntimeComposeDialectResolver.ResolvedDialect;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

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

    private final ComposeExecutionPort composeExecutionPort = mock(ComposeExecutionPort.class);
    private final RuntimeComposeContextFactory contextFactory = mock(RuntimeComposeContextFactory.class);
    private final RuntimeComposeRunner runner = new RuntimeComposeRunner(composeExecutionPort, contextFactory);

    @Test
    void shouldRejectBlankScriptBeforeCreatingContext() {
        RuntimeComposeInvocation invocation = new RuntimeComposeInvocation(
                " ", Map.of(), Map.of(), null, null, null, null, Map.of());

        assertThatThrownBy(() -> runner.run(ComposeOperation.VALIDATE, "compose.validate", invocation))
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
        verify(composeExecutionPort, never()).execute(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldReturnComposeResponseWithDiagnostics() {
        Map<String, Object> params = Map.of("customerId", 42);
        Map<String, Object> options = Map.of("dialect", "sqlserver");
        Map<String, String> headers = Map.of("X-Trace-Id", "trace-header");
        RuntimeComposeInvocation invocation = new RuntimeComposeInvocation(
                "return { plans: [] };", params, options, "body-ns", "trace-body",
                "header-ns", "Bearer test", headers);
        when(contextFactory.create(
                eq("body-ns"), eq("trace-body"), eq(params), eq(options), eq("header-ns"),
                eq("Bearer test"), eq(headers)))
                .thenReturn(context("resolved-ns", "trace-body", params, "sqlserver", "request-options"));
        when(composeExecutionPort.execute(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new ComposeExecutionResult(
                        ComposeOperation.VALIDATE, true, false,
                        Map.of("plans", List.of()), "SELECT 1", List.of(), List.of()));

        RuntimeComposeRunner.RuntimeComposeRunResult result =
                runner.run(ComposeOperation.VALIDATE, "compose.validate", invocation);

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

        ArgumentCaptor<ComposeExecutionRequest> requestCaptor =
                ArgumentCaptor.forClass(ComposeExecutionRequest.class);
        verify(composeExecutionPort).execute(requestCaptor.capture());
        assertThat(requestCaptor.getValue().namespace()).isEqualTo("resolved-ns");
        assertThat(requestCaptor.getValue().dialect()).isEqualTo("sqlserver");
        assertThat(requestCaptor.getValue().caller().authorizationHint()).isEqualTo("Bearer test");
    }

    @Test
    void shouldMapRuntimeFailureAfterDialectResolution() {
        RuntimeComposeInvocation invocation = new RuntimeComposeInvocation(
                "return { plans: [] };", Map.of(), Map.of(), null, null, null, null, Map.of());
        when(contextFactory.create(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()))
                .thenThrow(new IllegalStateException("dialect lookup failed"));

        assertThatThrownBy(() -> runner.run(ComposeOperation.PREVIEW, "compose.preview", invocation))
                .isInstanceOfSatisfying(RuntimeComposeException.class, e -> {
                    assertThat(e.toRuntimeError().code()).isEqualTo("COMPOSE_COMPILE_FAILED");
                    assertThat(e.toRuntimeError().phase()).isEqualTo("compose.preview");
                    assertThat(e.toRuntimeError().message()).isEqualTo("dialect lookup failed");
                    assertThat(e.toRuntimeError().safeToAutoRepair()).isFalse();
                    assertThat(e.diagnostics()).isEqualTo(
                            com.foggyframework.runtime.api.dto.RuntimeDiagnostics.empty());
                });
    }

    @Test
    void shouldMapSandboxFailureWithoutLeakingEngineExceptionType() {
        RuntimeComposeInvocation invocation = new RuntimeComposeInvocation(
                "import java.lang.System;", Map.of(), Map.of(), null, null, null, null, Map.of());
        RuntimeComposeContext context = context("resolved-ns", null, Map.of(), "mysql", "default");
        when(contextFactory.create(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()))
                .thenReturn(context);
        when(composeExecutionPort.execute(org.mockito.ArgumentMatchers.any()))
                .thenThrow(new ComposeExecutionException(
                        ComposeExecutionException.Kind.SANDBOX,
                        "compose-sandbox/A/import-denied", "parse", "import denied",
                        null, null, null));

        assertThatThrownBy(() -> runner.run(ComposeOperation.VALIDATE, "compose.validate", invocation))
                .isInstanceOfSatisfying(RuntimeComposeException.class, e -> {
                    assertThat(e.toRuntimeError().code()).isEqualTo("COMPOSE_SANDBOX_VIOLATION");
                    assertThat(e.toRuntimeError().safeToAutoRepair()).isFalse();
                    assertThat(e.diagnostics().attributes()).containsEntry("namespace", "resolved-ns");
                });
    }

    private static RuntimeComposeContext context(
            String namespace,
            String traceId,
            Map<String, Object> params,
            String dialect,
            String dialectSource
    ) {
        ComposeCaller caller = new ComposeCaller(
                "runtime-api-test", null, List.of(), null, "Bearer test", null);
        ResolvedDialect resolvedDialect = new ResolvedDialect(
                dialect, dialectSource, null, "unbound", null, "mysql");
        return new RuntimeComposeContext(namespace, traceId, params, caller, resolvedDialect);
    }
}
