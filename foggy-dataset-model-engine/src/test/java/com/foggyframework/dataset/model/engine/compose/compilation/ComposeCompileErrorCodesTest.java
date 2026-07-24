package com.foggyframework.dataset.model.engine.compose.compilation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ComposeCompileErrorCodes} · M6 错误码契约断言。
 *
 * <p>与 Python 仓 {@code tests/compose/compilation/test_error_codes.py}
 * 逐字对齐：4 codes + NAMESPACE + ALL_CODES 大小 + 2 phase + 构造期校验。</p>
 */
class ComposeCompileErrorCodesTest {

    @Test
    @DisplayName("NAMESPACE 字面量等于 'compose-compile-error'")
    void namespaceLiteralIsStable() {
        assertEquals("compose-compile-error", ComposeCompileErrorCodes.NAMESPACE);
    }

    @Test
    @DisplayName("4 个错误码字面量与 Python 字面对齐")
    void errorCodeLiteralsMatchPython() {
        assertEquals("compose-compile-error/unsupported-plan-shape",
                ComposeCompileErrorCodes.UNSUPPORTED_PLAN_SHAPE);
        assertEquals("compose-compile-error/cross-datasource-rejected",
                ComposeCompileErrorCodes.CROSS_DATASOURCE_REJECTED);
        assertEquals("compose-compile-error/missing-binding",
                ComposeCompileErrorCodes.MISSING_BINDING);
        assertEquals("compose-compile-error/per-base-compile-failed",
                ComposeCompileErrorCodes.PER_BASE_COMPILE_FAILED);
    }

    @Test
    @DisplayName("所有错误码以 NAMESPACE 为前缀")
    void allCodesStartWithNamespace() {
        for (String code : ComposeCompileErrorCodes.ALL_CODES) {
            assertTrue(code.startsWith(ComposeCompileErrorCodes.NAMESPACE + "/"),
                    "code " + code + " missing NAMESPACE prefix");
        }
    }

    @Test
    @DisplayName("ALL_CODES 大小严格等于 14（NAMESPACE 不纳入）")
    void allCodesSizeIsFourteen() {
        assertEquals(14, ComposeCompileErrorCodes.ALL_CODES.size());
    }

    @Test
    @DisplayName("ALL_CODES 包含全部 4 条已注册 code")
    void allCodesContainsEveryRegisteredConstant() {
        assertTrue(ComposeCompileErrorCodes.ALL_CODES.contains(
                ComposeCompileErrorCodes.UNSUPPORTED_PLAN_SHAPE));
        assertTrue(ComposeCompileErrorCodes.ALL_CODES.contains(
                ComposeCompileErrorCodes.CROSS_DATASOURCE_REJECTED));
        assertTrue(ComposeCompileErrorCodes.ALL_CODES.contains(
                ComposeCompileErrorCodes.MISSING_BINDING));
        assertTrue(ComposeCompileErrorCodes.ALL_CODES.contains(
                ComposeCompileErrorCodes.PER_BASE_COMPILE_FAILED));
        assertTrue(ComposeCompileErrorCodes.ALL_CODES.contains(
                ComposeCompileErrorCodes.RELATION_WRAP_UNSUPPORTED));
        assertTrue(ComposeCompileErrorCodes.ALL_CODES.contains(
                ComposeCompileErrorCodes.RELATION_CTE_HOIST_UNSUPPORTED));
        assertTrue(ComposeCompileErrorCodes.ALL_CODES.contains(
                ComposeCompileErrorCodes.RELATION_DATASOURCE_MISMATCH));
        // S7d codes
        assertTrue(ComposeCompileErrorCodes.ALL_CODES.contains(
                ComposeCompileErrorCodes.RELATION_COLUMN_NOT_FOUND));
        assertTrue(ComposeCompileErrorCodes.ALL_CODES.contains(
                ComposeCompileErrorCodes.RELATION_COLUMN_NOT_READABLE));
        assertTrue(ComposeCompileErrorCodes.ALL_CODES.contains(
                ComposeCompileErrorCodes.RELATION_COLUMN_NOT_ORDERABLE));
        assertTrue(ComposeCompileErrorCodes.ALL_CODES.contains(
                ComposeCompileErrorCodes.RELATION_COLUMN_NOT_AGGREGATABLE));
        assertTrue(ComposeCompileErrorCodes.ALL_CODES.contains(
                ComposeCompileErrorCodes.RELATION_OUTER_AGGREGATE_NOT_SUPPORTED));
        assertTrue(ComposeCompileErrorCodes.ALL_CODES.contains(
                ComposeCompileErrorCodes.RELATION_OUTER_WINDOW_NOT_SUPPORTED));
        // S7f codes
        assertTrue(ComposeCompileErrorCodes.ALL_CODES.contains(
                ComposeCompileErrorCodes.RELATION_COLUMN_NOT_WINDOWABLE));
    }

    @Test
    @DisplayName("ALL_CODES 不包含 NAMESPACE 前缀本身")
    void allCodesDoesNotContainBareNamespace() {
        assertFalse(ComposeCompileErrorCodes.ALL_CODES.contains(
                ComposeCompileErrorCodes.NAMESPACE));
    }

    @Test
    @DisplayName("isValidCode 对 4 条 code 均返回 true")
    void isValidCodeTrueForEveryRegisteredConstant() {
        assertTrue(ComposeCompileErrorCodes.isValidCode(
                ComposeCompileErrorCodes.UNSUPPORTED_PLAN_SHAPE));
        assertTrue(ComposeCompileErrorCodes.isValidCode(
                ComposeCompileErrorCodes.CROSS_DATASOURCE_REJECTED));
        assertTrue(ComposeCompileErrorCodes.isValidCode(
                ComposeCompileErrorCodes.MISSING_BINDING));
        assertTrue(ComposeCompileErrorCodes.isValidCode(
                ComposeCompileErrorCodes.PER_BASE_COMPILE_FAILED));
        assertTrue(ComposeCompileErrorCodes.isValidCode(
                ComposeCompileErrorCodes.RELATION_WRAP_UNSUPPORTED));
        assertTrue(ComposeCompileErrorCodes.isValidCode(
                ComposeCompileErrorCodes.RELATION_CTE_HOIST_UNSUPPORTED));
        assertTrue(ComposeCompileErrorCodes.isValidCode(
                ComposeCompileErrorCodes.RELATION_DATASOURCE_MISMATCH));
        // S7d codes
        assertTrue(ComposeCompileErrorCodes.isValidCode(
                ComposeCompileErrorCodes.RELATION_COLUMN_NOT_FOUND));
        assertTrue(ComposeCompileErrorCodes.isValidCode(
                ComposeCompileErrorCodes.RELATION_COLUMN_NOT_READABLE));
        assertTrue(ComposeCompileErrorCodes.isValidCode(
                ComposeCompileErrorCodes.RELATION_COLUMN_NOT_ORDERABLE));
        assertTrue(ComposeCompileErrorCodes.isValidCode(
                ComposeCompileErrorCodes.RELATION_COLUMN_NOT_AGGREGATABLE));
        assertTrue(ComposeCompileErrorCodes.isValidCode(
                ComposeCompileErrorCodes.RELATION_OUTER_AGGREGATE_NOT_SUPPORTED));
        assertTrue(ComposeCompileErrorCodes.isValidCode(
                ComposeCompileErrorCodes.RELATION_OUTER_WINDOW_NOT_SUPPORTED));
        // S7f codes
        assertTrue(ComposeCompileErrorCodes.isValidCode(
                ComposeCompileErrorCodes.RELATION_COLUMN_NOT_WINDOWABLE));
    }

    @Test
    @DisplayName("isValidCode 对 null / 未注册字符串返回 false")
    void isValidCodeFalseForUnknownInputs() {
        assertFalse(ComposeCompileErrorCodes.isValidCode(null));
        assertFalse(ComposeCompileErrorCodes.isValidCode(""));
        assertFalse(ComposeCompileErrorCodes.isValidCode("compose-compile-error"));
        assertFalse(ComposeCompileErrorCodes.isValidCode("compose-compile-error/unknown-kind"));
        assertFalse(ComposeCompileErrorCodes.isValidCode("compose-sandbox-violation/layer-a/scan"));
    }

    @Test
    @DisplayName("VALID_PHASES 大小严格等于 3 且含 plan-lower + compile + relation-compile")
    void validPhasesHasThreePhases() {
        assertEquals(3, ComposeCompileErrorCodes.VALID_PHASES.size());
        assertTrue(ComposeCompileErrorCodes.VALID_PHASES.contains(
                ComposeCompileErrorCodes.PHASE_PLAN_LOWER));
        assertTrue(ComposeCompileErrorCodes.VALID_PHASES.contains(
                ComposeCompileErrorCodes.PHASE_COMPILE));
        assertTrue(ComposeCompileErrorCodes.VALID_PHASES.contains(
                ComposeCompileErrorCodes.PHASE_RELATION_COMPILE));
    }

    @Test
    @DisplayName("PHASE_* 字面量与 Python 字面对齐")
    void phaseLiteralsMatchPython() {
        assertEquals("plan-lower", ComposeCompileErrorCodes.PHASE_PLAN_LOWER);
        assertEquals("compile", ComposeCompileErrorCodes.PHASE_COMPILE);
        assertEquals("relation-compile", ComposeCompileErrorCodes.PHASE_RELATION_COMPILE);
    }

    @Test
    @DisplayName("isValidPhase 对 plan-lower / compile / relation-compile 返回 true")
    void isValidPhaseTrueForRegisteredPhases() {
        assertTrue(ComposeCompileErrorCodes.isValidPhase("plan-lower"));
        assertTrue(ComposeCompileErrorCodes.isValidPhase("compile"));
        assertTrue(ComposeCompileErrorCodes.isValidPhase("relation-compile"));
    }

    @Test
    @DisplayName("isValidPhase 对 null / 其他 phase 返回 false")
    void isValidPhaseFalseForOtherPhases() {
        assertFalse(ComposeCompileErrorCodes.isValidPhase(null));
        assertFalse(ComposeCompileErrorCodes.isValidPhase(""));
        assertFalse(ComposeCompileErrorCodes.isValidPhase("PLAN-LOWER"));
        assertFalse(ComposeCompileErrorCodes.isValidPhase("runtime"));
        assertFalse(ComposeCompileErrorCodes.isValidPhase("authority-resolve"));
    }

    @Test
    @DisplayName("ALL_CODES 不可变（add 抛异常）")
    void allCodesIsImmutable() {
        assertThrows(UnsupportedOperationException.class,
                () -> ComposeCompileErrorCodes.ALL_CODES.add("x"));
    }

    @Test
    @DisplayName("VALID_PHASES 不可变（add 抛异常）")
    void validPhasesIsImmutable() {
        assertThrows(UnsupportedOperationException.class,
                () -> ComposeCompileErrorCodes.VALID_PHASES.add("x"));
    }

    @Test
    @DisplayName("错误码字符串 NAMESPACE/* 与其他命名空间字符串互不交叠")
    void codesAreDisjointFromOtherNamespaces() {
        for (String code : ComposeCompileErrorCodes.ALL_CODES) {
            assertFalse(code.contains("compose-sandbox-violation"),
                    code + " 不应出现 sandbox-violation 片段");
            assertFalse(code.contains("compose-schema-error"),
                    code + " 不应出现 schema-error 片段");
            assertFalse(code.contains("compose-authority-resolve"),
                    code + " 不应出现 authority-resolve 片段");
        }
    }
}
