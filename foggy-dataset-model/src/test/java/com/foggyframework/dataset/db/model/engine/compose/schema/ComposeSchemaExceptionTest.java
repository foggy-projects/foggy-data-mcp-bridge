package com.foggyframework.dataset.db.model.engine.compose.schema;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * M4 · ComposeSchemaException + ComposeSchemaErrorCodes contract test.
 *
 * <p>Mirrors Python {@code tests/compose/schema/test_schema_errors.py}.</p>
 */
@DisplayName("M4 ComposeSchemaException / ComposeSchemaErrorCodes")
class ComposeSchemaExceptionTest {

    /**
     * 跨仓冻结的 code 字符串集合。
     *
     * <p>M4 baseline 是 7 条；G10 PR2 追加两条
     * ({@code OUTPUT_SCHEMA_AMBIGUOUS_LOOKUP} +
     * {@code JOIN_AMBIGUOUS_COLUMN}) 以承载歧义列下游错误码 → 当前共 9 条。
     * Python 侧 {@code error_codes.py} 的 {@code ALL_CODES} 必须同步 9 条。</p>
     */
    private static final Set<String> EXPECTED_CODES = Set.of(
            "compose-schema-error/derived-query/unknown-field",
            "compose-schema-error/column-spec/malformed",
            "compose-schema-error/duplicate-output-column",
            "compose-schema-error/union/column-count-mismatch",
            "compose-schema-error/join/on-left-unknown-field",
            "compose-schema-error/join/on-right-unknown-field",
            "compose-schema-error/join/output-column-conflict",
            "compose-schema-error/output-schema/ambiguous-lookup",
            "compose-schema-error/join/ambiguous-column"
    );

    private static final Set<String> EXPECTED_PHASES = Set.of(
            "plan-build",
            "schema-derive"
    );

    @Test
    @DisplayName("ALL_CODES 与跨仓冻结的字符串集合逐字符一致")
    void allCodesMatchesExpected() {
        assertEquals(EXPECTED_CODES, ComposeSchemaErrorCodes.ALL_CODES);
        assertEquals(9, ComposeSchemaErrorCodes.ALL_CODES.size(),
                "M4 baseline 7 + G10 PR2 新增 2 条 = 9 条");
    }

    @Test
    @DisplayName("9 个常量值匹配 namespace/kind 形态")
    void eachConstantMatchesNamespaceForm() {
        assertEquals("compose-schema-error/derived-query/unknown-field",
                ComposeSchemaErrorCodes.DERIVED_QUERY_UNKNOWN_FIELD);
        assertEquals("compose-schema-error/column-spec/malformed",
                ComposeSchemaErrorCodes.COLUMN_SPEC_MALFORMED);
        assertEquals("compose-schema-error/duplicate-output-column",
                ComposeSchemaErrorCodes.DUPLICATE_OUTPUT_COLUMN);
        assertEquals("compose-schema-error/union/column-count-mismatch",
                ComposeSchemaErrorCodes.UNION_COLUMN_COUNT_MISMATCH);
        assertEquals("compose-schema-error/join/on-left-unknown-field",
                ComposeSchemaErrorCodes.JOIN_ON_LEFT_UNKNOWN_FIELD);
        assertEquals("compose-schema-error/join/on-right-unknown-field",
                ComposeSchemaErrorCodes.JOIN_ON_RIGHT_UNKNOWN_FIELD);
        assertEquals("compose-schema-error/join/output-column-conflict",
                ComposeSchemaErrorCodes.JOIN_OUTPUT_COLUMN_CONFLICT);
        assertEquals("compose-schema-error/output-schema/ambiguous-lookup",
                ComposeSchemaErrorCodes.OUTPUT_SCHEMA_AMBIGUOUS_LOOKUP);
        assertEquals("compose-schema-error/join/ambiguous-column",
                ComposeSchemaErrorCodes.JOIN_AMBIGUOUS_COLUMN);
    }

    @Test
    @DisplayName("所有 code 都以 NAMESPACE/ 开头")
    void namespacePrefixOnEveryCode() {
        String prefix = ComposeSchemaErrorCodes.NAMESPACE + "/";
        for (String code : ComposeSchemaErrorCodes.ALL_CODES) {
            assertTrue(code.startsWith(prefix),
                    "code " + code + " missing namespace prefix " + prefix);
        }
    }

    @Test
    @DisplayName("VALID_PHASES 仅包含 plan-build + schema-derive")
    void validPhasesMatchesExpected() {
        assertEquals(EXPECTED_PHASES, ComposeSchemaErrorCodes.VALID_PHASES);
    }

    @Test
    @DisplayName("合法构造保留 code / phase / planPath / offendingField / message")
    void validConstructionRecordsFields() {
        ComposeSchemaException ex = new ComposeSchemaException(
                ComposeSchemaErrorCodes.DERIVED_QUERY_UNKNOWN_FIELD,
                "unknown field 'foo'",
                ComposeSchemaErrorCodes.PHASE_SCHEMA_DERIVE,
                "DerivedQueryPlan",
                "foo");
        assertEquals(ComposeSchemaErrorCodes.DERIVED_QUERY_UNKNOWN_FIELD, ex.code());
        assertEquals("schema-derive", ex.phase());
        assertEquals("DerivedQueryPlan", ex.planPath());
        assertEquals("foo", ex.offendingField());
        assertEquals("unknown field 'foo'", ex.getMessage());
    }

    @Test
    @DisplayName("两参构造默认 phase = schema-derive")
    void twoArgCtorDefaultsToSchemaDerive() {
        ComposeSchemaException ex = new ComposeSchemaException(
                ComposeSchemaErrorCodes.UNION_COLUMN_COUNT_MISMATCH,
                "count mismatch");
        assertEquals("schema-derive", ex.phase());
        assertNull(ex.planPath());
        assertNull(ex.offendingField());
    }

    @Test
    @DisplayName("非法 code 被拒绝")
    void invalidCodeRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new ComposeSchemaException(
                        "compose-schema-error/made-up",
                        "x",
                        ComposeSchemaErrorCodes.PHASE_SCHEMA_DERIVE,
                        null, null));
    }

    @Test
    @DisplayName("非法 phase 被拒绝")
    void invalidPhaseRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new ComposeSchemaException(
                        ComposeSchemaErrorCodes.UNION_COLUMN_COUNT_MISMATCH,
                        "x",
                        "runtime",
                        null, null));
    }

    @Test
    @DisplayName("cause 通过构造函数附加")
    void causeAttachedViaCtor() {
        RuntimeException original = new RuntimeException("underlying");
        ComposeSchemaException ex = new ComposeSchemaException(
                ComposeSchemaErrorCodes.COLUMN_SPEC_MALFORMED,
                "malformed",
                ComposeSchemaErrorCodes.PHASE_PLAN_BUILD,
                null, null,
                original);
        assertSame(original, ex.getCause());
    }

    @Test
    @DisplayName("toString 携带 code / phase / offendingField 便于 debug")
    void toStringHelpfulForDebug() {
        ComposeSchemaException ex = new ComposeSchemaException(
                ComposeSchemaErrorCodes.JOIN_OUTPUT_COLUMN_CONFLICT,
                "column conflict",
                ComposeSchemaErrorCodes.PHASE_SCHEMA_DERIVE,
                "JoinPlan",
                "partnerName");
        String s = ex.toString();
        assertTrue(s.contains(ComposeSchemaErrorCodes.JOIN_OUTPUT_COLUMN_CONFLICT));
        assertTrue(s.contains("JoinPlan"));
        assertTrue(s.contains("partnerName"));
    }

    @Test
    @DisplayName("7 个 code 都可成功构造 exception")
    void allSevenCodesAccepted() {
        for (String code : ComposeSchemaErrorCodes.ALL_CODES) {
            ComposeSchemaException ex = new ComposeSchemaException(code, "x");
            assertEquals(code, ex.code());
        }
    }
}
