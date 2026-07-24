package com.foggyframework.dataset.model.engine.compose.sandbox;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ComposeSandboxViolationException} 单元测试。
 *
 * <p>对等 Python {@code tests/compose/sandbox/test_sandbox_error_codes.py}
 * 里针对 {@code ComposeSandboxViolationError} 的用例子集；Java 端验证：</p>
 * <ul>
 *   <li>合法构造保留 code / phase / scriptLocation，并正确派生 layer / kind</li>
 *   <li>非法 code / phase 构造期抛 {@link IllegalArgumentException}（fail-closed）</li>
 *   <li>cause 通过 3-arg 构造器传递给 {@link Throwable#getCause()}</li>
 *   <li>toString 包含 code / layer / kind / phase / message 诊断信息</li>
 * </ul>
 *
 * @since 8.2.0.beta
 */
@DisplayName("ComposeSandboxViolationException · 结构化异常")
class ComposeSandboxViolationExceptionTest {

    @Test
    @DisplayName("合法构造 — code/layer/kind/phase/message 保留 · layer/kind 派生正确")
    void happyPathKeepsAllFieldsAndDerivesLayerKind() {
        ComposeSandboxViolationException ex = new ComposeSandboxViolationException(
                ComposeSandboxErrorCodes.LAYER_A_EVAL_DENIED,
                "eval is not allowed in compose scripts",
                ComposeSandboxErrorCodes.PHASE_SCRIPT_PARSE
        );
        assertAll(
                () -> assertEquals(ComposeSandboxErrorCodes.LAYER_A_EVAL_DENIED,
                        ex.code()),
                () -> assertEquals("A", ex.layer()),
                () -> assertEquals("eval-denied", ex.kind()),
                () -> assertEquals(ComposeSandboxErrorCodes.PHASE_SCRIPT_PARSE,
                        ex.phase()),
                () -> assertEquals("eval is not allowed in compose scripts",
                        ex.getMessage()),
                () -> assertNull(ex.scriptLocation()),
                () -> assertNull(ex.getCause())
        );
    }

    @Test
    @DisplayName("合法构造 — 带 scriptLocation（line:column）")
    void happyPathWithScriptLocation() {
        ComposeSandboxViolationException ex = new ComposeSandboxViolationException(
                ComposeSandboxErrorCodes.LAYER_A_NETWORK_DENIED,
                "http.get is denied",
                ComposeSandboxErrorCodes.PHASE_SCRIPT_EVAL,
                "3:17"
        );
        assertEquals("3:17", ex.scriptLocation());
        assertEquals("A", ex.layer());
        assertEquals("network-denied", ex.kind());
    }

    @Test
    @DisplayName("合法构造 — 5-arg 构造器附加 cause")
    void fiveArgConstructorAttachesCause() {
        RuntimeException root = new RuntimeException("underlying parser error");
        ComposeSandboxViolationException ex = new ComposeSandboxViolationException(
                ComposeSandboxErrorCodes.LAYER_B_INJECTION_SUSPECTED,
                "expression contains a forbidden literal",
                ComposeSandboxErrorCodes.PHASE_COMPILE,
                "5:2",
                root
        );
        assertSame(root, ex.getCause());
        assertEquals("B", ex.layer());
        assertEquals("injection-suspected", ex.kind());
    }

    @Test
    @DisplayName("14 个 code × 7 个 phase 的笛卡尔积都可合法构造")
    void allCodesTimesAllPhasesConstructable() {
        for (String code : ComposeSandboxErrorCodes.ALL_CODES) {
            for (String phase : ComposeSandboxErrorCodes.VALID_PHASES) {
                ComposeSandboxViolationException ex = new ComposeSandboxViolationException(
                        code, "smoke", phase);
                assertNotNull(ex.layer());
                assertNotNull(ex.kind());
                assertEquals(code, ex.code());
                assertEquals(phase, ex.phase());
            }
        }
    }

    // ---- fail-closed 构造期校验 ----

    @Test
    @DisplayName("非法 code · 构造期抛 IllegalArgumentException（fail-closed）")
    void illegalCodeRejected() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> new ComposeSandboxViolationException(
                        "compose-sandbox-violation/D/oops",
                        "bogus",
                        ComposeSandboxErrorCodes.PHASE_SCRIPT_EVAL));
        assertTrue(e.getMessage().contains("ALL_CODES"),
                "错误消息应指向 ALL_CODES · 便于快速定位");
    }

    @Test
    @DisplayName("null code · 构造期拒绝")
    void nullCodeRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new ComposeSandboxViolationException(
                        null,
                        "no code",
                        ComposeSandboxErrorCodes.PHASE_COMPILE));
    }

    @Test
    @DisplayName("非法 phase · 构造期抛 IllegalArgumentException（fail-closed）")
    void illegalPhaseRejected() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> new ComposeSandboxViolationException(
                        ComposeSandboxErrorCodes.LAYER_A_EVAL_DENIED,
                        "bogus phase",
                        "no-such-phase"));
        assertTrue(e.getMessage().contains("VALID_PHASES"));
    }

    @Test
    @DisplayName("null phase · 构造期拒绝")
    void nullPhaseRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new ComposeSandboxViolationException(
                        ComposeSandboxErrorCodes.LAYER_A_EVAL_DENIED,
                        "no phase",
                        null));
    }

    // ---- toString 诊断 ----

    @Test
    @DisplayName("toString · 包含 code / layer / kind / phase / message")
    void toStringCarriesDiagnostics() {
        ComposeSandboxViolationException ex = new ComposeSandboxViolationException(
                ComposeSandboxErrorCodes.LAYER_C_METHOD_DENIED,
                "unsupported QueryPlan method",
                ComposeSandboxErrorCodes.PHASE_PLAN_BUILD,
                "7:3"
        );
        String s = ex.toString();
        assertAll(
                () -> assertTrue(s.contains("code=" + ComposeSandboxErrorCodes.LAYER_C_METHOD_DENIED),
                        "toString 应含 code · 实际：" + s),
                () -> assertTrue(s.contains("layer=C"), "toString 应含 layer=C · 实际：" + s),
                () -> assertTrue(s.contains("kind=method-denied"),
                        "toString 应含 kind · 实际：" + s),
                () -> assertTrue(s.contains("phase=plan-build"),
                        "toString 应含 phase · 实际：" + s),
                () -> assertTrue(s.contains("scriptLocation=7:3"),
                        "toString 应含 scriptLocation · 实际：" + s),
                () -> assertTrue(s.contains("unsupported QueryPlan method"),
                        "toString 应含 message · 实际：" + s)
        );
    }

    @Test
    @DisplayName("toString · 无 scriptLocation 时省略该字段")
    void toStringOmitsNullScriptLocation() {
        ComposeSandboxViolationException ex = new ComposeSandboxViolationException(
                ComposeSandboxErrorCodes.LAYER_B_FUNCTION_DENIED,
                "denied fn",
                ComposeSandboxErrorCodes.PHASE_COMPILE
        );
        String s = ex.toString();
        assertTrue(s.contains("kind=function-denied"));
        // scriptLocation 省略
        assertTrue(!s.contains("scriptLocation="),
                "scriptLocation 为空时 toString 不应出现 · 实际：" + s);
    }

    @Test
    @DisplayName("RuntimeException 语义 · 无需 try/catch 或 throws 声明")
    void isRuntimeException() {
        ComposeSandboxViolationException ex = new ComposeSandboxViolationException(
                ComposeSandboxErrorCodes.LAYER_A_IO_DENIED,
                "io fn denied",
                ComposeSandboxErrorCodes.PHASE_EXECUTE
        );
        assertTrue(ex instanceof RuntimeException);
    }
}
