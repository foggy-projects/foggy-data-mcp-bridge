package com.foggyframework.dataset.model.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("SemanticScaleSqlSupport 单元测试")
class SemanticScaleSqlSupportTest {

    @Test
    @DisplayName("未配置 semanticScaleFactor 时不改写声明也不触发校验")
    void noScale_returnsOriginalDeclareAndSkipsValidation() {
        assertFalse(SemanticScaleSqlSupport.hasScale(null));

        SemanticScaleSqlSupport.validate(null, "amount", true, "amountYuan");

        assertEquals("t.amount", SemanticScaleSqlSupport.scaledDeclare("t.amount", null));
    }

    @Test
    @DisplayName("整数和小数 scale factor 都生成 SQL 数值 literal")
    void scaledDeclare_formatsSqlLiteral() {
        assertTrue(SemanticScaleSqlSupport.hasScale(new BigDecimal("100")));

        assertEquals("((t.amount) / 100.0)",
                SemanticScaleSqlSupport.scaledDeclare("t.amount", new BigDecimal("100")));
        assertEquals("((t.amount) / 2.5)",
                SemanticScaleSqlSupport.scaledDeclare("t.amount", new BigDecimal("2.50")));
    }

    @Test
    @DisplayName("非法 semanticScaleFactor 配置 fail closed")
    void validate_rejectsInvalidScaleContract() {
        assertThrows(RuntimeException.class,
                () -> SemanticScaleSqlSupport.validate(BigDecimal.ZERO, "amount", false, "amountYuan"));
        assertThrows(RuntimeException.class,
                () -> SemanticScaleSqlSupport.validate(new BigDecimal("-1"), "amount", false, "amountYuan"));
        SemanticScaleSqlSupport.validate(BigDecimal.ONE, "amount", true, "amountYuan");
        SemanticScaleSqlSupport.validate(BigDecimal.ONE, "", true, "amountYuan");
        assertThrows(RuntimeException.class,
                () -> SemanticScaleSqlSupport.validate(BigDecimal.ONE, "sum(amount)", false, "amountYuan"));
        assertThrows(RuntimeException.class,
                () -> SemanticScaleSqlSupport.validate(BigDecimal.ONE, "", false, "amountYuan"));
    }

    @Test
    @DisplayName("公式 SQL alias 占位符替换覆盖常见写法")
    void formulaSqlSupport_appliesAliasPlaceholders() {
        assertFalse(FormulaSqlSupport.hasSql(""));
        assertNull(FormulaSqlSupport.applyAlias(null, "t1"));
        assertEquals("alias.sales_amount + 1",
                FormulaSqlSupport.applyAlias("alias.sales_amount + 1", ""));
        assertEquals("t1.sales_amount + t1.tax_amount + t1.discount_amount",
                FormulaSqlSupport.applyAlias(
                        "alias.sales_amount + ${alias}.tax_amount + {alias}.discount_amount", "t1"));
        assertEquals("sales_alias.sales_amount + t1.tax_amount",
                FormulaSqlSupport.applyAlias("sales_alias.sales_amount + alias.tax_amount", "t1"));
    }
}
