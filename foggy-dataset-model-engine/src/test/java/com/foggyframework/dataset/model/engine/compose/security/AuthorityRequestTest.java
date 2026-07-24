package com.foggyframework.dataset.model.engine.compose.security;

import com.foggyframework.dataset.model.engine.compose.context.Principal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * M1 AuthorityRequest / ModelQuery 批量契约 — 跨仓对齐 Python test_authority_request.py。
 */
@DisplayName("M1 AuthorityRequest")
class AuthorityRequestTest {

    private static Principal principal() {
        return Principal.builder().userId("u001").tenantId("t001").build();
    }

    // ------------------------------------------------------------------
    // ModelQuery invariants
    // ------------------------------------------------------------------

    @Test
    @DisplayName("ModelQuery.model 必填且非空")
    void modelRequiredNonBlank() {
        assertThrows(IllegalArgumentException.class,
                () -> ModelQuery.builder().model("").tables(List.of("t1")).build());
        assertThrows(IllegalArgumentException.class,
                () -> ModelQuery.builder().model(null).tables(Collections.emptyList()).build());
    }

    @Test
    @DisplayName("ModelQuery.tables 不得为 null；空 list 合法")
    void tablesMustNotBeNullEmptyLegal() {
        assertThrows(IllegalArgumentException.class,
                () -> ModelQuery.builder().model("X").tables(null).build());

        ModelQuery m = ModelQuery.builder()
                .model("SaleOrderQM")
                .tables(Collections.emptyList())
                .build();
        assertNotNull(m.tables());
        assertEquals(0, m.tables().size());
    }

    @Test
    @DisplayName("ModelQuery 正常构造：tables 不可变")
    void modelQueryNormalConstruction() {
        ModelQuery m = ModelQuery.builder()
                .model("SaleOrderQM")
                .tables(List.of("sale_order", "sale_order_line"))
                .build();
        assertEquals("SaleOrderQM", m.model());
        assertEquals(List.of("sale_order", "sale_order_line"), m.tables());
        assertThrows(UnsupportedOperationException.class,
                () -> m.tables().add("x"));
    }

    // ------------------------------------------------------------------
    // AuthorityRequest batch contract
    // ------------------------------------------------------------------

    @Test
    @DisplayName("models 必须非空：单模型也走 size-1 list")
    void modelsMustBeNonEmpty() {
        assertThrows(IllegalArgumentException.class,
                () -> AuthorityRequest.builder()
                        .principal(principal())
                        .namespace("odoo")
                        .models(Collections.emptyList())
                        .build());
        assertThrows(IllegalArgumentException.class,
                () -> AuthorityRequest.builder()
                        .principal(principal())
                        .namespace("odoo")
                        .models(null)
                        .build());
    }

    @Test
    @DisplayName("单模型请求使用 size-1 list")
    void singleModelSize1List() {
        AuthorityRequest req = AuthorityRequest.builder()
                .principal(principal())
                .namespace("odoo")
                .models(List.of(ModelQuery.builder()
                        .model("SaleOrderQM")
                        .tables(List.of("sale_order"))
                        .build()))
                .build();
        assertEquals(1, req.models().size());
        assertEquals(List.of("SaleOrderQM"), req.modelNames());
    }

    @Test
    @DisplayName("多模型请求保留顺序")
    void multiModelPreservesOrder() {
        AuthorityRequest req = AuthorityRequest.builder()
                .principal(principal())
                .namespace("odoo")
                .models(List.of(
                        ModelQuery.builder().model("SaleOrderQM")
                                .tables(List.of("sale_order")).build(),
                        ModelQuery.builder().model("CrmLeadQM")
                                .tables(List.of("crm_lead")).build(),
                        ModelQuery.builder().model("ResPartnerQM")
                                .tables(List.of("res_partner")).build()))
                .build();
        assertEquals(List.of("SaleOrderQM", "CrmLeadQM", "ResPartnerQM"),
                req.modelNames());
    }

    @Test
    @DisplayName("namespace null/empty fallback 到默认空字符串（A1：默认 namespace 语义）")
    void namespaceFallsBackToEmptyOnNullOrBlank() {
        // 8.3.0.beta P3 决策 A1：null/empty namespace 视为默认/匿名 namespace，
        // 与 v1.3 SemanticRequestContext 默认行为对齐。下游若需限制非空必须自行校验。
        AuthorityRequest emptyReq = AuthorityRequest.builder()
                .principal(principal())
                .namespace("")
                .models(List.of(ModelQuery.builder()
                        .model("X").tables(Collections.emptyList()).build()))
                .build();
        assertEquals("", emptyReq.namespace());

        AuthorityRequest nullReq = AuthorityRequest.builder()
                .principal(principal())
                .namespace(null)
                .models(List.of(ModelQuery.builder()
                        .model("X").tables(Collections.emptyList()).build()))
                .build();
        assertEquals("", nullReq.namespace());
    }

    @Test
    @DisplayName("principal 必填")
    void principalRequired() {
        assertThrows(NullPointerException.class,
                () -> AuthorityRequest.builder()
                        .principal(null)
                        .namespace("odoo")
                        .models(List.of(ModelQuery.builder()
                                .model("X").tables(Collections.emptyList()).build()))
                        .build());
    }
}
