package com.foggyframework.dataset.db.model.engine.compose.sandbox;

import com.foggyframework.dataset.db.model.engine.compose.context.ComposeQueryContext;
import com.foggyframework.dataset.db.model.engine.compose.context.Principal;
import com.foggyframework.dataset.db.model.engine.compose.security.AuthorityResolution;
import com.foggyframework.dataset.db.model.engine.compose.security.AuthorityResolver;
import com.foggyframework.dataset.db.model.engine.compose.security.ModelBinding;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("M9: Sandbox Layer A Tests")
class SandboxLayerATest extends ComposeSandboxTestSupport {

    private static ComposeQueryContext dummyCtx() {
        return contextMockWithParams(Map.of());
    }

    private static ComposeQueryContext contextMockWithParams(Map<String, Object> params) {
        AuthorityResolver resolver = request -> {
            Map<String, ModelBinding> bindings = new LinkedHashMap<>();
            for (String name : request.modelNames()) {
                bindings.put(name, ModelBinding.builder().build());
            }
            return AuthorityResolution.builder().bindings(bindings).build();
        };
        return ComposeQueryContext.builder()
                .principal(Principal.builder()
                        .userId("u1").tenantId("t1").roles(List.of("analyst")).build())
                .namespace("ns1")
                .traceId("trace-1")
                .authorityResolver(resolver)
                .params(params)
                .build();
    }

    private SandboxRunner newRunner() {
        return SandboxRunner.forScript(dummyCtx());
    }

    @Test
    void a01_evalBasicShouldBeDenied() {
        String script = "eval(\"from({model: 'X'})\")";
        assertSandboxViolation(
            () -> newRunner().run(script),
            ComposeSandboxErrorCodes.LAYER_A_EVAL_DENIED, "A", "eval-denied");
    }

    @Test
    void a02_functionConstructorShouldBeDenied() {
        String script = "new Function(\"return from({model:'X'})\")()";
        assertSandboxViolation(
            () -> newRunner().run(script),
            ComposeSandboxErrorCodes.LAYER_A_EVAL_DENIED, "A", "eval-denied");
    }

    @Test
    void a03_asyncFetchShouldBeDenied() {
        String script = "await fetch('http://evil.example/')";
        ComposeSandboxViolationException ex = assertThrows(
            ComposeSandboxViolationException.class, () -> newRunner().run(script));
        assertTrue(
            Set.of(ComposeSandboxErrorCodes.LAYER_A_ASYNC_DENIED, ComposeSandboxErrorCodes.LAYER_A_NETWORK_DENIED)
                .contains(ex.code()),
            "A-03 must trigger either async-denied or network-denied, got: " + ex.code());
        assertEquals("A", ex.layer());
    }

    @Test
    void a04_globalReflectShouldBeDenied() {
        String script = "Object.getPrototypeOf(from)";
        assertSandboxViolation(
            () -> newRunner().run(script),
            ComposeSandboxErrorCodes.LAYER_A_GLOBAL_DENIED, "A", "global-denied");
    }

    @Test
    void a05_dateNowShouldBeDenied() {
        String script = "from({model:'X', slice:[{field:'t', op:'>', value: Date.now()}]})";
        assertSandboxViolation(
            () -> newRunner().run(script),
            ComposeSandboxErrorCodes.LAYER_A_TIME_DENIED, "A", "time-denied");
    }

    @Test
    void a06_securityParamInjectionShouldBeDenied() {
        String script = "from({model:'X', authorization:'Bearer hack'})";
        assertSandboxViolation(
            () -> newRunner().run(script),
            ComposeSandboxErrorCodes.LAYER_A_SECURITY_PARAM, "A", "security-param-denied");
    }

    @Test
    void a07_securityParamInDerivedShouldBeDenied() {
        String script = 
            "const base = from({model:'X', columns:['id']});\n" +
            "base.query({columns:['id'], systemSlice:[{field:'orgId', op:'=', value:'other-org'}]});";
        assertSandboxViolation(
            () -> newRunner().run(script),
            ComposeSandboxErrorCodes.LAYER_A_SECURITY_PARAM, "A", "security-param-denied");
    }

    @Test
    void a08_contextAccessShouldBeDenied() {
        String script = "const p = __context__.principal";
        assertSandboxViolation(
            () -> newRunner().run(script),
            ComposeSandboxErrorCodes.LAYER_A_CONTEXT_ACCESS, "A", "context-access-denied");
    }

    @Test
    void a09_moduleImportShouldBeDenied() {
        String script = "const fs = require('fs')";
        assertSandboxViolation(
            () -> newRunner().run(script),
            ComposeSandboxErrorCodes.LAYER_A_IO_DENIED, "A", "io-denied");
    }

    @Test
    void a10_legalBusinessParamShouldBeAccepted() {
        SandboxRunner runner = SandboxRunner.forScript(
            contextMockWithParams(Map.of("orgId", "org001")));
        String script = "from({model:'X', columns:['id'], slice:[{field:'orgId', op:'=', value: params.orgId}]})";
        assertDoesNotThrow(() -> runner.run(script));
    }
}
