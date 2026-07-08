package com.foggyframework.runtime.api.service;

import com.foggyframework.runtime.api.dto.ComposeRequest;
import com.foggyframework.runtime.api.dto.FsscriptRequest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RuntimeComposeInvocationTest {

    @Test
    void shouldPreserveComposeRequestFields() {
        Map<String, String> headers = Map.of("X-Trace-Id", "trace-header");
        ComposeRequest request = new ComposeRequest(
                "return { plans: [] };",
                null,
                null,
                "body-ns",
                "trace-body"
        );

        RuntimeComposeInvocation invocation = RuntimeComposeInvocation.fromComposeRequest(
                request,
                "header-ns",
                "Bearer test",
                headers
        );

        assertThat(invocation.script()).isEqualTo("return { plans: [] };");
        assertThat(invocation.params()).isNull();
        assertThat(invocation.options()).isNull();
        assertThat(invocation.namespace()).isEqualTo("body-ns");
        assertThat(invocation.traceId()).isEqualTo("trace-body");
        assertThat(invocation.headerNamespace()).isEqualTo("header-ns");
        assertThat(invocation.authorization()).isEqualTo("Bearer test");
        assertThat(invocation.headers()).isSameAs(headers);
    }

    @Test
    void shouldParseFsscriptCteStringRequest() {
        FsscriptRequest request = new FsscriptRequest(
                "return foggy.cte.preview('...');",
                Map.of(),
                Map.of("diagnostics", "normal"),
                Map.of("cteBridge", true),
                "fsscript-ns",
                "trace-1"
        );

        RuntimeComposeInvocation invocation = RuntimeComposeInvocation.fromFsscriptCteArgs(
                request,
                "header-ns",
                "Bearer test",
                Map.of(),
                new Object[]{"return { plans: [] };"},
                "compose.preview"
        );

        assertThat(invocation.script()).isEqualTo("return { plans: [] };");
        assertThat(invocation.params()).isEmpty();
        assertThat(invocation.options()).containsEntry("diagnostics", "normal");
        assertThat(invocation.namespace()).isEqualTo("fsscript-ns");
        assertThat(invocation.traceId()).isEqualTo("trace-1");
        assertThat(invocation.headerNamespace()).isEqualTo("header-ns");
    }

    @Test
    void shouldMergeFsscriptAndNestedComposeOptionsWithNestedOverride() {
        FsscriptRequest request = new FsscriptRequest(
                "return foggy.cte.preview({});",
                Map.of(),
                Map.of("dialect", "mysql", "diagnostics", "normal"),
                Map.of("cteBridge", true),
                "fsscript-ns",
                "trace-1"
        );

        RuntimeComposeInvocation invocation = RuntimeComposeInvocation.fromFsscriptCteArgs(
                request,
                null,
                null,
                Map.of(),
                new Object[]{
                        Map.of(
                                "script", "return { plans: [] };",
                                "params", Map.of("customerId", 42),
                                "options", Map.of("dialect", "sqlserver")
                        )
                },
                "compose.preview"
        );

        assertThat(invocation.script()).isEqualTo("return { plans: [] };");
        assertThat(invocation.params()).containsEntry("customerId", 42);
        assertThat(invocation.options())
                .containsEntry("dialect", "sqlserver")
                .containsEntry("diagnostics", "normal");
    }

    @Test
    void shouldRejectInvalidFsscriptCteArgumentShape() {
        assertThatThrownBy(() -> RuntimeComposeInvocation.fromFsscriptCteArgs(
                null,
                null,
                null,
                Map.of(),
                new Object[]{List.of("invalid")},
                "compose.validate"
        )).isInstanceOfSatisfying(RuntimeComposeException.class, e -> {
            assertThat(e.toRuntimeError().code()).isEqualTo("COMPOSE_SCRIPT_INVALID");
            assertThat(e.toRuntimeError().phase()).isEqualTo("compose.validate");
            assertThat(e.toRuntimeError().safeToAutoRepair()).isTrue();
        });
    }
}
