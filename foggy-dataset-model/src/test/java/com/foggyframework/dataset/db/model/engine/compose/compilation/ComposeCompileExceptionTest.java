package com.foggyframework.dataset.db.model.engine.compose.compilation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ComposeCompileException} · 构造期校验 + code/phase/message 保留。
 */
class ComposeCompileExceptionTest {

    @Test
    @DisplayName("合法 code + phase 构造成功，属性可读")
    void validCodeAndPhaseConstructs() {
        ComposeCompileException ex = new ComposeCompileException(
                ComposeCompileErrorCodes.MISSING_BINDING,
                ComposeCompileErrorCodes.PHASE_PLAN_LOWER,
                "binding missing for QM 'x'");
        assertEquals(ComposeCompileErrorCodes.MISSING_BINDING, ex.code());
        assertEquals(ComposeCompileErrorCodes.PHASE_PLAN_LOWER, ex.phase());
        assertTrue(ex.getMessage().contains("[compose-compile-error/missing-binding]"));
        assertTrue(ex.getMessage().contains("(plan-lower)"));
        assertTrue(ex.getMessage().contains("binding missing for QM 'x'"));
    }

    @Test
    @DisplayName("cause 参数被保留到 getCause()")
    void causeIsPreserved() {
        RuntimeException root = new RuntimeException("v1.3 exploded");
        ComposeCompileException ex = new ComposeCompileException(
                ComposeCompileErrorCodes.PER_BASE_COMPILE_FAILED,
                ComposeCompileErrorCodes.PHASE_COMPILE,
                "wrapped",
                root);
        assertSame(root, ex.getCause());
    }

    @Test
    @DisplayName("不传 cause 的构造，getCause() 返回 null")
    void defaultCauseIsNull() {
        ComposeCompileException ex = new ComposeCompileException(
                ComposeCompileErrorCodes.UNSUPPORTED_PLAN_SHAPE,
                ComposeCompileErrorCodes.PHASE_PLAN_LOWER,
                "shape not supported");
        assertNull(ex.getCause());
    }

    @Test
    @DisplayName("未注册 code 抛 IllegalArgumentException")
    void unknownCodeRejected() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> new ComposeCompileException(
                        "compose-compile-error/totally-made-up",
                        ComposeCompileErrorCodes.PHASE_PLAN_LOWER,
                        "x"));
        assertTrue(ex.getMessage().contains("Invalid ComposeCompileException code"));
    }

    @Test
    @DisplayName("NAMESPACE 前缀本身不是合法 code")
    void bareNamespaceRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new ComposeCompileException(
                        ComposeCompileErrorCodes.NAMESPACE,
                        ComposeCompileErrorCodes.PHASE_PLAN_LOWER,
                        "x"));
    }

    @Test
    @DisplayName("null code 抛 IllegalArgumentException")
    void nullCodeRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new ComposeCompileException(
                        null,
                        ComposeCompileErrorCodes.PHASE_PLAN_LOWER,
                        "x"));
    }

    @Test
    @DisplayName("未注册 phase 抛 IllegalArgumentException")
    void unknownPhaseRejected() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> new ComposeCompileException(
                        ComposeCompileErrorCodes.MISSING_BINDING,
                        "runtime",
                        "x"));
        assertTrue(ex.getMessage().contains("Invalid ComposeCompileException phase"));
    }

    @Test
    @DisplayName("null phase 抛 IllegalArgumentException")
    void nullPhaseRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new ComposeCompileException(
                        ComposeCompileErrorCodes.MISSING_BINDING,
                        null,
                        "x"));
    }

    @Test
    @DisplayName("phase 大小写敏感：'PLAN-LOWER' 被拒")
    void phaseIsCaseSensitive() {
        assertThrows(IllegalArgumentException.class,
                () -> new ComposeCompileException(
                        ComposeCompileErrorCodes.MISSING_BINDING,
                        "PLAN-LOWER",
                        "x"));
    }

    @Test
    @DisplayName("所有 4 条 code 都能构造成功")
    void everyRegisteredCodeCanConstruct() {
        for (String code : ComposeCompileErrorCodes.ALL_CODES) {
            ComposeCompileException ex = new ComposeCompileException(
                    code, ComposeCompileErrorCodes.PHASE_COMPILE, "ok");
            assertEquals(code, ex.code());
        }
    }

    @Test
    @DisplayName("message 参数中包含特殊字符（换行、单引号）原样保留")
    void messagePreservesSpecialCharacters() {
        String msg = "line1\nline2 with 'quotes' and \"doubles\"";
        ComposeCompileException ex = new ComposeCompileException(
                ComposeCompileErrorCodes.MISSING_BINDING,
                ComposeCompileErrorCodes.PHASE_PLAN_LOWER,
                msg);
        assertTrue(ex.getMessage().contains("line1\nline2"));
        assertTrue(ex.getMessage().contains("'quotes'"));
    }

    @Test
    @DisplayName("是 RuntimeException 的子类（便于 unchecked 传播）")
    void isRuntimeExceptionSubclass() {
        ComposeCompileException ex = new ComposeCompileException(
                ComposeCompileErrorCodes.MISSING_BINDING,
                ComposeCompileErrorCodes.PHASE_PLAN_LOWER,
                "x");
        assertTrue(ex instanceof RuntimeException);
    }
}
