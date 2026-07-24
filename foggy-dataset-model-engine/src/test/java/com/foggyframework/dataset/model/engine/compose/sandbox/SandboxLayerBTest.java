package com.foggyframework.dataset.model.engine.compose.sandbox;

import com.foggyframework.dataset.model.engine.compose.context.ComposeQueryContext;
import com.foggyframework.dataset.model.engine.compose.context.Principal;
import com.foggyframework.dataset.model.engine.compose.security.AuthorityResolution;
import com.foggyframework.dataset.model.engine.compose.security.AuthorityResolver;
import com.foggyframework.dataset.model.engine.compose.security.ModelBinding;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("M9: Sandbox Layer B Tests")
class SandboxLayerBTest extends ComposeSandboxTestSupport {

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
    void b01_hexFunctionShouldBeDenied() {
        String script = "from({model:'X', columns: ['CHAR(0x41) as x']})";
        assertSandboxViolation(
            () -> newRunner().run(script),
            ComposeSandboxErrorCodes.LAYER_B_FUNCTION_DENIED, "B", "function-denied");
    }

    @Test
    void b02_sleepFunctionShouldBeDenied() {
        String script = "from({model:'X', columns: ['SLEEP(5) as x']})";
        assertSandboxViolation(
            () -> newRunner().run(script),
            ComposeSandboxErrorCodes.LAYER_B_FUNCTION_DENIED, "B", "function-denied");
    }

    @Test
    void b03_unionSelectInjectionShouldBeNeutralized() {
        String script = "from({model:'X', slice:[{field:'name', op:'=', value:\"a' UNION SELECT 1,2,3--\"}]})";
        try {
            String sql = newRunner().runToSql(script);
            // 参数化路径：SQL 必须使用 ? 占位符，原始字符串不得出现在 SQL 文本中
            assertFalse(sql.contains("UNION SELECT"), "Raw injection payload leaked into SQL: " + sql);
        } catch (ComposeSandboxViolationException ex) {
            // 拦截路径：错误码必须是 injection-suspected
            assertEquals(ComposeSandboxErrorCodes.LAYER_B_INJECTION_SUSPECTED, ex.code());
        }
    }

    @Test
    void b04_derivedRawSqlShouldBeDenied() {
        String script = 
            "const base = from({model:'X', columns:['id']});\n" +
            "base.query({columns:['RAW_SQL(\"DROP TABLE x\")']});";
        // May throw either LAYER_B_DERIVED_FN_DENIED or LAYER_B_FUNCTION_DENIED depending on implementation
        ComposeSandboxViolationException ex = assertThrows(
            ComposeSandboxViolationException.class, () -> newRunner().run(script));
        assertTrue(
            List.of(ComposeSandboxErrorCodes.LAYER_B_DERIVED_FN_DENIED, ComposeSandboxErrorCodes.LAYER_B_FUNCTION_DENIED)
                .contains(ex.code()),
            "B-04 must trigger derived-plan-function-denied or function-denied, got: " + ex.code());
        assertEquals("B", ex.layer());
    }

    @Test
    void b05_allowedDateDiffShouldBeAccepted() {
        String script = "from({model:'X', columns: ['DATE_DIFF(create_date, write_date) as days']})";
        assertDoesNotThrow(() -> newRunner().run(script));
    }

    @Test
    void b06_allowedIifSumShouldBeAccepted() {
        String script = "from({model:'X', columns: ['SUM(IIF(state == 1, 1, 0)) as openCount'], groupBy: ['id']})";
        assertDoesNotThrow(() -> newRunner().run(script));
    }

    @Test
    void b07_loadFileFunctionShouldBeDenied() {
        String script = "from({model:'X', columns: ['LOAD_FILE(\"/etc/passwd\") as x']})";
        assertSandboxViolation(
            () -> newRunner().run(script),
            ComposeSandboxErrorCodes.LAYER_B_FUNCTION_DENIED, "B", "function-denied");
    }
}
