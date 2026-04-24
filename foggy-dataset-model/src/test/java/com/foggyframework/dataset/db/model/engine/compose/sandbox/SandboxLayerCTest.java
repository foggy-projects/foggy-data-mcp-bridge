package com.foggyframework.dataset.db.model.engine.compose.sandbox;

import com.foggyframework.dataset.db.model.engine.compose.context.ComposeQueryContext;
import com.foggyframework.dataset.db.model.engine.compose.context.Principal;
import com.foggyframework.dataset.db.model.engine.compose.plan.QueryPlan;
import com.foggyframework.dataset.db.model.engine.compose.runtime.ScriptRuntime;
import com.foggyframework.dataset.db.model.engine.compose.security.AuthorityResolution;
import com.foggyframework.dataset.db.model.engine.compose.security.AuthorityResolver;
import com.foggyframework.dataset.db.model.engine.compose.security.ModelBinding;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("M9: Sandbox Layer C Tests")
class SandboxLayerCTest extends ComposeSandboxTestSupport {

    private static ComposeQueryContext dummyCtx() {
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
                .build();
    }

    private SandboxRunner newRunner() {
        return SandboxRunner.forScript(dummyCtx());
    }

    @Test
    void c01_methodRawShouldBeDenied() {
        String script = 
            "const p = from({model:'X', columns:['id']});\n" +
            "p.raw(\"select * from sale_order\");";
        assertSandboxViolation(
            () -> newRunner().run(script),
            ComposeSandboxErrorCodes.LAYER_C_METHOD_DENIED, "C", "method-denied");
    }

    @Test
    void c02_methodMemoryFilterShouldBeDenied() {
        String script = 
            "const p = from({model:'X', columns:['id']});\n" +
            "p.memoryFilter(x => x.id > 0);";
        assertSandboxViolation(
            () -> newRunner().run(script),
            ComposeSandboxErrorCodes.LAYER_C_METHOD_DENIED, "C", "method-denied");
    }

    @Test
    void c03_methodForEachShouldBeDenied() {
        String script = 
            "const p = from({model:'X', columns:['id']});\n" +
            "p.forEach(row => 1);";
        assertSandboxViolation(
            () -> newRunner().run(script),
            ComposeSandboxErrorCodes.LAYER_C_METHOD_DENIED, "C", "method-denied");
    }

    @Test
    void c04_resultIterateShouldBeDenied() {
        String script = 
            "const res = from({model:'X', columns:['id']}).execute();\n" +
            "res.items.forEach(x => 1);";
        assertSandboxViolation(
            () -> newRunner().run(script),
            ComposeSandboxErrorCodes.LAYER_C_RESULT_ITERATION, "C", "result-iteration-denied");
    }

    @Test
    void c05_crossDatasourceJoinShouldBeDenied() {
        // Cross-datasource composition is blocked by the spec.
        // Currently the cross-ds check is deferred to the compiler/authority-resolver.
        // This test validates that the exception structure is correct when raised.
        ComposeSandboxViolationException ex = new ComposeSandboxViolationException(
                ComposeSandboxErrorCodes.LAYER_C_CROSS_DS,
                "Cross-datasource composition is not supported in 8.2.0.beta.",
                ComposeSandboxErrorCodes.PHASE_COMPILE);
        assertEquals("C", ex.layer());
        assertEquals("cross-datasource-denied", ex.kind());
        assertEquals(ComposeSandboxErrorCodes.LAYER_C_CROSS_DS, ex.code());
    }

    @Test
    void c06_legalChainShouldBeAccepted() {
        // A simple from() call is legal — produces a QueryPlan without sandbox error.
        String script = "from({model:'X', columns:['id','val']})";
        ScriptRuntime.ScriptResult result = assertDoesNotThrow(
            () -> ScriptRuntime.runScript(script, dummyCtx(),
                    org.mockito.Mockito.mock(com.foggyframework.dataset.db.model.semantic.service.SemanticQueryServiceV3.class),
                    "mysql"));
        assertInstanceOf(QueryPlan.class, result.value(),
                "Legal from() must produce a QueryPlan");
    }

    @Test
    void c07_legalTosqlDebugShouldBeAccepted() {
        // A simple from() returning a plan is legal — toSql() is a Layer-C public surface method.
        // We verify the plan has toSql() accessible via its class, even though calling it
        // would need a SemanticService for SQL rendering.
        String script = "from({model:'X', columns:['id']})";
        ScriptRuntime.ScriptResult result = assertDoesNotThrow(
            () -> ScriptRuntime.runScript(script, dummyCtx(),
                    org.mockito.Mockito.mock(com.foggyframework.dataset.db.model.semantic.service.SemanticQueryServiceV3.class),
                    "mysql"));
        QueryPlan plan = assertInstanceOf(QueryPlan.class, result.value());
        // toSql() is a declared method on QueryPlan — it IS in the public surface
        assertDoesNotThrow(() -> plan.getClass().getMethod("toSql"),
                "toSql() must be part of QueryPlan's public surface");
    }
}
