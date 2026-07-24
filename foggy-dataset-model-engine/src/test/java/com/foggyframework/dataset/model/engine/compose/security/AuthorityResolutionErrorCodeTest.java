package com.foggyframework.dataset.model.engine.compose.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * M1 AuthorityResolutionException / AuthorityErrorCodes 跨仓 parity 测试。
 *
 * <p>跨语言不变量：Java {@link AuthorityErrorCodes} 与 Python
 * {@code error_codes.py} 必须暴露同样的 7 个 code 字符串，逐字符一致。</p>
 */
@DisplayName("M1 AuthorityResolution Error Codes")
class AuthorityResolutionErrorCodeTest {

    /** M1 契约冻结的 7 个 code —— 跨仓镜像。 */
    private static final Set<String> EXPECTED_CODES = Set.of(
            "compose-authority-resolve/resolver-not-available",
            "compose-authority-resolve/model-binding-missing",
            "compose-authority-resolve/model-not-mapped",
            "compose-authority-resolve/principal-mismatch",
            "compose-authority-resolve/upstream-failure",
            "compose-authority-resolve/invalid-response",
            "compose-authority-resolve/ir-rule-unmapped-field"
    );

    private static final Set<String> EXPECTED_PHASES = Set.of(
            "authority-resolve",
            "schema-derive",
            "compile",
            "execute",
            "script-parse",
            "script-eval",
            "plan-build"
    );

    // ------------------------------------------------------------------
    // Catalogue
    // ------------------------------------------------------------------

    @Test
    @DisplayName("ALL_CODES 集合匹配冻结的 7 个 code")
    void allCodesMatchesExpected() {
        assertEquals(EXPECTED_CODES, AuthorityErrorCodes.ALL_CODES);
    }

    @Test
    @DisplayName("每个 code 常量值符合 namespace/kind 形态")
    void eachConstantHasNamespacePrefix() {
        assertEquals("compose-authority-resolve/resolver-not-available",
                AuthorityErrorCodes.RESOLVER_NOT_AVAILABLE);
        assertEquals("compose-authority-resolve/model-binding-missing",
                AuthorityErrorCodes.MODEL_BINDING_MISSING);
        assertEquals("compose-authority-resolve/model-not-mapped",
                AuthorityErrorCodes.MODEL_NOT_MAPPED);
        assertEquals("compose-authority-resolve/principal-mismatch",
                AuthorityErrorCodes.PRINCIPAL_MISMATCH);
        assertEquals("compose-authority-resolve/upstream-failure",
                AuthorityErrorCodes.UPSTREAM_FAILURE);
        assertEquals("compose-authority-resolve/invalid-response",
                AuthorityErrorCodes.INVALID_RESPONSE);
        assertEquals("compose-authority-resolve/ir-rule-unmapped-field",
                AuthorityErrorCodes.IR_RULE_UNMAPPED_FIELD);
    }

    @Test
    @DisplayName("所有 code 都以 NAMESPACE/ 开头")
    void namespacePrefixOnEveryCode() {
        String prefix = AuthorityErrorCodes.NAMESPACE + "/";
        for (String code : AuthorityErrorCodes.ALL_CODES) {
            assertTrue(code.startsWith(prefix),
                    "code " + code + " missing namespace prefix " + prefix);
        }
    }

    @Test
    @DisplayName("VALID_PHASES 集合匹配冻结的 7 个 phase")
    void validPhasesMatchesExpected() {
        assertEquals(EXPECTED_PHASES, AuthorityErrorCodes.VALID_PHASES);
    }

    // ------------------------------------------------------------------
    // Exception construction
    // ------------------------------------------------------------------

    @Test
    @DisplayName("合法构造保留 code / modelInvolved / phase / message")
    void validConstructionRecordsFields() {
        AuthorityResolutionException ex = new AuthorityResolutionException(
                AuthorityErrorCodes.UPSTREAM_FAILURE,
                "upstream offline",
                "SaleOrderQM",
                AuthorityErrorCodes.PHASE_AUTHORITY_RESOLVE);
        assertEquals(AuthorityErrorCodes.UPSTREAM_FAILURE, ex.code());
        assertEquals("SaleOrderQM", ex.modelInvolved());
        assertEquals(AuthorityErrorCodes.PHASE_AUTHORITY_RESOLVE, ex.phase());
        assertEquals("upstream offline", ex.getMessage());
    }

    @Test
    @DisplayName("非法 code 被拒绝")
    void invalidCodeRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new AuthorityResolutionException(
                        "made-up/not-in-catalogue",
                        "x",
                        null,
                        AuthorityErrorCodes.PHASE_AUTHORITY_RESOLVE));
    }

    @Test
    @DisplayName("非法 phase 被拒绝")
    void invalidPhaseRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new AuthorityResolutionException(
                        AuthorityErrorCodes.UPSTREAM_FAILURE,
                        "x",
                        null,
                        "made-up-phase"));
    }

    @Test
    @DisplayName("cause 通过构造函数附加")
    void causeAttachedViaCtor() {
        RuntimeException original = new RuntimeException("network");
        AuthorityResolutionException ex = new AuthorityResolutionException(
                AuthorityErrorCodes.UPSTREAM_FAILURE,
                "http 503",
                null,
                AuthorityErrorCodes.PHASE_AUTHORITY_RESOLVE,
                original);
        assertSame(original, ex.getCause());
    }

    @Test
    @DisplayName("7 个 code 都可成功构造 exception")
    void allSevenCodesAccepted() {
        for (String code : AuthorityErrorCodes.ALL_CODES) {
            AuthorityResolutionException ex = new AuthorityResolutionException(
                    code, "x", null, AuthorityErrorCodes.PHASE_AUTHORITY_RESOLVE);
            assertEquals(code, ex.code());
        }
    }

    @Test
    @DisplayName("toString 携带 code / modelInvolved / phase 便于 debug")
    void toStringHelpfulForDebug() {
        AuthorityResolutionException ex = new AuthorityResolutionException(
                AuthorityErrorCodes.MODEL_NOT_MAPPED,
                "no mapping for SaleOrderQM",
                "SaleOrderQM",
                AuthorityErrorCodes.PHASE_AUTHORITY_RESOLVE);
        String s = ex.toString();
        assertTrue(s.contains(AuthorityErrorCodes.MODEL_NOT_MAPPED));
        assertTrue(s.contains("SaleOrderQM"));
    }
}
