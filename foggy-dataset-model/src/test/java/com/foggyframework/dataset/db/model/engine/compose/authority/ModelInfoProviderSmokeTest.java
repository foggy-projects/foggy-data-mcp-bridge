package com.foggyframework.dataset.db.model.engine.compose.authority;

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
}
