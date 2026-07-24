package com.foggyframework.dataset.model.engine.compose.authority;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * M5 {@link ModelInfoProvider} interface + {@link NullModelInfoProvider} smoke.
 *
 * <p>Java equivalent of Python's {@code @runtime_checkable Protocol} test
 * ({@code tests/compose/authority/test_public_api.py}). Java interfaces are
 * always structurally enforced at compile time; we verify structural
 * presence (interface implemented, method returns the right shape).</p>
 */
@DisplayName("M5 ModelInfoProvider")
class ModelInfoProviderSmokeTest {

    @Test
    @DisplayName("NullModelInfoProvider 实现 ModelInfoProvider interface")
    void nullProviderImplementsInterface() {
        assertTrue(ModelInfoProvider.class.isAssignableFrom(NullModelInfoProvider.class),
                "NullModelInfoProvider 必须 implements ModelInfoProvider");
    }

    @Test
    @DisplayName("NullModelInfoProvider 对任意参数返回 Optional.of(empty list)")
    void nullProviderReturnsEmptyOptional() {
        NullModelInfoProvider provider = new NullModelInfoProvider();
        Optional<List<String>> result = provider.getTablesForModel("SaleOrderQM", "odoo");
        assertTrue(result.isPresent(), "NullModelInfoProvider 返回 Optional.of(...)，不返回 empty");
        assertEquals(List.of(), result.get());
    }

    @Test
    @DisplayName("NullModelInfoProvider 对 null namespace 仍安全")
    void nullProviderHandlesNullNamespace() {
        NullModelInfoProvider provider = new NullModelInfoProvider();
        Optional<List<String>> result = provider.getTablesForModel("AnyModelQM", null);
        assertTrue(result.isPresent());
        assertEquals(List.of(), result.get());
    }

    // ------------------------------------------------------------------
    // F-7 · getDatasourceId
    // ------------------------------------------------------------------

    @Test
    @DisplayName("F-7 · default getDatasourceId 返回 Optional.empty()")
    void defaultGetDatasourceIdReturnsEmpty() {
        // Use a lambda to construct a provider — proves @FunctionalInterface
        // is still valid after adding the default method.
        ModelInfoProvider lambda = (name, ns) -> Optional.of(List.of("table1"));
        assertTrue(lambda.getDatasourceId("any", "ns").isEmpty(),
                "default getDatasourceId must return Optional.empty()");
    }

    @Test
    @DisplayName("F-7 · NullModelInfoProvider.getDatasourceId 返回 Optional.empty()")
    void nullProviderGetDatasourceIdReturnsEmpty() {
        NullModelInfoProvider provider = new NullModelInfoProvider();
        assertTrue(provider.getDatasourceId("SaleOrderQM", "odoo").isEmpty());
        assertTrue(provider.getDatasourceId("AnyQM", null).isEmpty());
    }
}
