package com.foggyframework.dataset.db.model.def.dict;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DbDictionaryDiscoveryDefTest {

    @Test
    @DisplayName("dictionaryDiscovery 默认关闭并提供保守默认值")
    void defaultConfig_isDisabledWithConservativeDefaults() {
        DbDictionaryDiscoveryDef def = new DbDictionaryDiscoveryDef();

        assertFalse(def.isEnabled());
        assertEquals(DbDictionaryDiscoveryDef.STRATEGY_GROUP_BY, def.getEffectiveStrategy());
        assertEquals(DbDictionaryDiscoveryDef.DEFAULT_MAX_VALUES, def.getEffectiveMaxValues());
        assertEquals(DbDictionaryDiscoveryDef.DEFAULT_REFRESH_TTL_SECONDS, def.getEffectiveRefreshTtlSeconds());
        assertTrue(def.isExposeToLlm());
        assertFalse(def.isSensitive());
        assertFalse(def.isLlmVisible());
    }

    @Test
    @DisplayName("合法配置可通过校验并暴露给 LLM metadata")
    void validConfig_isLlmVisible() {
        DbDictionaryDiscoveryDef def = new DbDictionaryDiscoveryDef();
        def.setEnabled(true);
        def.setStrategy(DbDictionaryDiscoveryDef.STRATEGY_GROUP_BY);
        def.setMaxValues(20);
        def.setRefreshTtlSeconds(3600L);
        def.setExposeToLlm(true);
        def.setSensitive(false);

        DbDictionaryDiscoveryDef.AliasDef alias = new DbDictionaryDiscoveryDef.AliasDef();
        alias.setValues(List.of("PENDING", "CONFIRMED", "PROCESSING"));
        alias.setDescription("打开订单");
        def.setAliases(Map.of("open_order", alias));

        assertDoesNotThrow(() -> def.validate("property FactOrderModel.orderStatus"));
        assertTrue(def.isLlmVisible());
    }

    @Test
    @DisplayName("敏感字段即使开启 discovery 也不暴露给 LLM metadata")
    void sensitiveConfig_isNotLlmVisible() {
        DbDictionaryDiscoveryDef def = new DbDictionaryDiscoveryDef();
        def.setEnabled(true);
        def.setSensitive(true);

        assertDoesNotThrow(() -> def.validate("property SecretModel.status"));
        assertFalse(def.isLlmVisible());
    }

    @Test
    @DisplayName("非法 strategy fail-closed")
    void invalidStrategy_failsClosed() {
        DbDictionaryDiscoveryDef def = new DbDictionaryDiscoveryDef();
        def.setEnabled(true);
        def.setStrategy("sample");

        assertThrows(RuntimeException.class, () -> def.validate("property FactOrderModel.orderStatus"));
    }

    @Test
    @DisplayName("非法 maxValues fail-closed")
    void invalidMaxValues_failsClosed() {
        DbDictionaryDiscoveryDef tooSmall = new DbDictionaryDiscoveryDef();
        tooSmall.setEnabled(true);
        tooSmall.setMaxValues(0);

        DbDictionaryDiscoveryDef tooLarge = new DbDictionaryDiscoveryDef();
        tooLarge.setEnabled(true);
        tooLarge.setMaxValues(DbDictionaryDiscoveryDef.MAX_ALLOWED_VALUES + 1);

        assertThrows(RuntimeException.class,
                () -> tooSmall.validate("property FactOrderModel.orderStatus"));
        assertThrows(RuntimeException.class,
                () -> tooLarge.validate("property FactOrderModel.orderStatus"));
    }

    @Test
    @DisplayName("负数 TTL fail-closed")
    void negativeTtl_failsClosed() {
        DbDictionaryDiscoveryDef def = new DbDictionaryDiscoveryDef();
        def.setEnabled(true);
        def.setRefreshTtlSeconds(-1L);

        assertThrows(RuntimeException.class,
                () -> def.validate("property FactOrderModel.orderStatus"));
    }

    @Test
    @DisplayName("空别名值 fail-closed")
    void emptyAliasValues_failsClosed() {
        DbDictionaryDiscoveryDef def = new DbDictionaryDiscoveryDef();
        def.setEnabled(true);

        DbDictionaryDiscoveryDef.AliasDef alias = new DbDictionaryDiscoveryDef.AliasDef();
        alias.setValues(List.of());
        def.setAliases(Map.of("open_order", alias));

        assertThrows(RuntimeException.class, () -> def.validate("property FactOrderModel.orderStatus"));
    }
}
