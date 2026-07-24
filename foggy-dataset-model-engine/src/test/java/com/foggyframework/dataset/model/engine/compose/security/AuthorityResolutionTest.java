package com.foggyframework.dataset.model.engine.compose.security;

import com.foggyframework.dataset.model.semantic.domain.DeniedPhysicalColumn;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * M1 AuthorityResolution / ModelBinding 契约 — 跨仓对齐 Python test_authority_resolution.py。
 */
@DisplayName("M1 AuthorityResolution")
class AuthorityResolutionTest {

    // ------------------------------------------------------------------
    // ModelBinding field_access semantics
    // ------------------------------------------------------------------

    @Test
    @DisplayName("fieldAccess=null 合法 —— Odoo Pro 主路径依赖 deniedColumns")
    void fieldAccessNullIsLegal() {
        ModelBinding b = ModelBinding.builder()
                .fieldAccess(null)
                .build();
        assertNull(b.fieldAccess());
        assertTrue(b.deniedColumns().isEmpty());
        assertTrue(b.systemSlice().isEmpty());
    }

    @Test
    @DisplayName("fieldAccess=[] 与 null 语义不同：pathological 但合法 —— 全部拒绝")
    void fieldAccessEmptyListDistinctFromNull() {
        ModelBinding b = ModelBinding.builder()
                .fieldAccess(Collections.emptyList())
                .build();
        assertNotNull(b.fieldAccess());
        assertTrue(b.fieldAccess().isEmpty());
    }

    @Test
    @DisplayName("fieldAccess 白名单：只暴露指定字段")
    void fieldAccessWhitelist() {
        ModelBinding b = ModelBinding.builder()
                .fieldAccess(List.of("partner$id", "partner$caption"))
                .build();
        assertEquals(List.of("partner$id", "partner$caption"), b.fieldAccess());
    }

    // ------------------------------------------------------------------
    // ModelBinding collection non-null invariants
    // ------------------------------------------------------------------

    @Test
    @DisplayName("deniedColumns 不得为 null")
    void deniedColumnsMustNotBeNull() {
        assertThrows(IllegalArgumentException.class,
                () -> ModelBinding.builder().deniedColumns(null).build());
    }

    @Test
    @DisplayName("systemSlice 不得为 null")
    void systemSliceMustNotBeNull() {
        assertThrows(IllegalArgumentException.class,
                () -> ModelBinding.builder().systemSlice(null).build());
    }

    @Test
    @DisplayName("deniedColumns 复用 v1.3 DeniedPhysicalColumn")
    void deniedColumnsReuseV13Type() {
        ModelBinding b = ModelBinding.builder()
                .deniedColumns(Arrays.asList(
                        new DeniedPhysicalColumn(null, "sale_order", "internal_cost")))
                .build();
        assertEquals(1, b.deniedColumns().size());
        assertEquals("sale_order", b.deniedColumns().get(0).getTable());
        assertEquals("internal_cost", b.deniedColumns().get(0).getColumn());
    }

    // ------------------------------------------------------------------
    // AuthorityResolution contract
    // ------------------------------------------------------------------

    @Test
    @DisplayName("空 bindings 可以构造 —— key-set 对齐由调用方验证")
    void emptyBindingsConstructible() {
        AuthorityResolution r = AuthorityResolution.builder()
                .bindings(Map.of())
                .build();
        assertTrue(r.bindings().isEmpty());
    }

    @Test
    @DisplayName("bindings 以模型名为 key 存储多模型 binding")
    void bindingsKeyedByModelName() {
        Map<String, ModelBinding> map = new HashMap<>();
        map.put("SaleOrderQM", ModelBinding.builder().build());
        map.put("CrmLeadQM", ModelBinding.builder()
                .deniedColumns(Arrays.asList(
                        new DeniedPhysicalColumn(null, "crm_lead", "source_cost")))
                .build());

        AuthorityResolution r = AuthorityResolution.builder().bindings(map).build();
        assertEquals(java.util.Set.of("SaleOrderQM", "CrmLeadQM"),
                r.bindings().keySet());
        assertEquals("source_cost",
                r.bindings().get("CrmLeadQM").deniedColumns().get(0).getColumn());
    }

    @Test
    @DisplayName("bindings key 必须非空字符串")
    void bindingsKeysMustBeNonBlank() {
        Map<String, ModelBinding> map = new HashMap<>();
        map.put("", ModelBinding.builder().build());
        assertThrows(IllegalArgumentException.class,
                () -> AuthorityResolution.builder().bindings(map).build());
    }

    @Test
    @DisplayName("bindings value 必须非 null")
    void bindingsValuesMustNotBeNull() {
        Map<String, ModelBinding> map = new HashMap<>();
        map.put("SaleOrderQM", null);
        assertThrows(NullPointerException.class,
                () -> AuthorityResolution.builder().bindings(map).build());
    }

    @Test
    @DisplayName("bindings 本身不得为 null")
    void bindingsMustNotBeNull() {
        assertThrows(IllegalArgumentException.class,
                () -> AuthorityResolution.builder().bindings(null).build());
    }

    @Test
    @DisplayName("bindings 返回不可变视图")
    void bindingsUnmodifiableView() {
        AuthorityResolution r = AuthorityResolution.builder()
                .bindings(Map.of("X", ModelBinding.builder().build()))
                .build();
        assertThrows(UnsupportedOperationException.class,
                () -> r.bindings().put("Y", ModelBinding.builder().build()));
    }
}
