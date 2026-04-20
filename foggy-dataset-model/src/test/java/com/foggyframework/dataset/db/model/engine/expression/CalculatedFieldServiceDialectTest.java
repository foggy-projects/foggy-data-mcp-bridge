package com.foggyframework.dataset.db.model.engine.expression;

import com.foggyframework.fsscript.parser.FsscriptDialect;
import com.foggyframework.fsscript.parser.spi.Exp;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 验证 {@link CalculatedFieldService#compileExpression(String, FsscriptDialect)} 新 API
 * 与旧 {@link CalculatedFieldService#compileExpression(String)} 行为一致（旧签名默认
 * 走 {@link FsscriptDialect#SQL_EXPRESSION}）。
 *
 * @since 8.1.11.beta
 */
@DisplayName("CalculatedFieldService · 方言重载")
class CalculatedFieldServiceDialectTest {

    @Test
    @DisplayName("旧签名 — if(a, 1, 0) 走 SQL_EXPRESSION 方言，应编译通过")
    void legacySignatureAcceptsIfFunction() {
        Exp exp = CalculatedFieldService.compileExpression("if(a > 0, 1, 0)");
        assertNotNull(exp);
    }

    @Test
    @DisplayName("新签名 + SQL_EXPRESSION — if(a, 1, 0) 编译通过")
    void newSignatureWithSqlExpressionDialect() {
        Exp exp = CalculatedFieldService.compileExpression(
                "if(a > 0, 1, 0)", FsscriptDialect.SQL_EXPRESSION);
        assertNotNull(exp);
    }

    @Test
    @DisplayName("新签名 + DEFAULT — if(a, 1, 0) 应抛异常（保留字冲突）")
    void newSignatureWithDefaultDialectRejectsIfFunction() {
        assertThrows(RuntimeException.class, () ->
                CalculatedFieldService.compileExpression(
                        "if(a > 0, 1, 0)", FsscriptDialect.DEFAULT)
        );
    }

    @Test
    @DisplayName("新签名 + null 方言 — 等价 DEFAULT，if(...) 应抛异常")
    void newSignatureNullDialectEqualsDefault() {
        assertThrows(RuntimeException.class, () ->
                CalculatedFieldService.compileExpression("if(a > 0, 1, 0)", null)
        );
    }

    @Test
    @DisplayName("旧签名 + 字符串内 if( — 不被误替换（需与迁移前行为一致）")
    void legacySignaturePreservesStringLiteralIf() {
        // 字符串 'has if(' 内部的 if( 不应被替换；整条表达式能正常编译
        Exp exp = CalculatedFieldService.compileExpression(
                "if(s == 'has if(', 1, 0)");
        assertNotNull(exp);
    }
}
