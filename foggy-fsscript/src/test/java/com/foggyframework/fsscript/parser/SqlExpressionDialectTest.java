/*******************************************************************************
 * This software is subject to the terms of the GNU LESSER GENERAL PUBLIC LICENSE
 * Agreement, available at the following URL:
 * http://www.gnu.org/licenses/lgpl.html
 * Copyright (c) 2012, 2013  Foggy.
 * All rights reserved.
 * You must accept the terms of that agreement to use this software.
 *******************************************************************************/
package com.foggyframework.fsscript.parser;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link SqlExpressionDialect} 单元测试（v1.4 Scanner 方言化之后）。
 * <p>
 * v1.4 起 {@code if( → IIF(} 字符状态机下线；方言语义主要由
 * {@link FsscriptDialect#isKeywordAsIdentifier(int, int)} 钩子在 scanner 层实现。
 * {@code normalize} 仅保留 SQL 风格逻辑词兼容：
 * </p>
 * <ul>
 *   <li>断言 normalize 只改写字符串字面量外的独立 {@code and}/{@code or}</li>
 *   <li>断言钩子方法只在 {@code IF + '('} 组合下返回 true（其他 keyword / 其他分隔符不降级）</li>
 * </ul>
 * <p>
 * 端到端 AST 形态验证见 {@code ExpParserDialectTest}。
 * </p>
 *
 * @since v1.4
 */
@DisplayName("SqlExpressionDialect · Scanner 钩子单元测试")
class SqlExpressionDialectTest {

    private final FsscriptDialect dialect = FsscriptDialect.SQL_EXPRESSION;

    @Test
    @DisplayName("方言元数据 — name = sql-expression / 单例")
    void dialectMetadata() {
        assertEquals("sql-expression", dialect.getName());
        assertSame(FsscriptDialect.SQL_EXPRESSION, FsscriptDialect.SQL_EXPRESSION, "预定义常量应为单例");
    }

    // ---- normalize 仅做 SQL 逻辑词归一化 ----

    @Test
    @DisplayName("normalize — null / empty 原样返回")
    void normalizeNullAndEmpty() {
        assertNull(dialect.normalize(null));
        assertEquals("", dialect.normalize(""));
    }

    @Test
    @DisplayName("normalize — 不再改写 if( 函数名")
    void normalizeDoesNotRewriteIfFunction() {
        String[] samples = {
                "if(a, 1, 0)",                                // 起始
                "a + if(b, 1, 0)",                            // 运算符后
                "IF(a, b, c)",                                // 大写
                "sum(if(state == 'posted', amount, 0))",      // sum 内嵌
                "if(a, if(b, 1, 0), 0)",                      // 嵌套
                "gift(x)",                                    // 标识符前缀（gift 含 if）
                "ifnull(a, 0)",                               // 标识符后缀（ifnull 含 if）
                "_if_(x)",                                    // 下划线包裹
                "'text with if(x) inside'",                   // 字符串内
                "if(s == 'has if(', 1, 0)",                   // 外部 if( + 字符串参数
        };
        for (String src : samples) {
            assertEquals(src, dialect.normalize(src),
                    "normalize 不应再改写 if 函数名，输入: " + src);
        }
    }

    @Test
    @DisplayName("normalize — 字符串字面量外的独立 and/or 归一化为 &&/||")
    void normalizeSqlLogicalKeywordsOutsideStringLiterals() {
        assertEquals("if(a > 0 && b < 1 || c == 3, 1, 0)",
                dialect.normalize("if(a > 0 and b < 1 or c == 3, 1, 0)"));
        assertEquals("if(a > 0 && b < 1 || c == 3, 1, 0)",
                dialect.normalize("if(a > 0 AND b < 1 OR c == 3, 1, 0)"));
    }

    @Test
    @DisplayName("normalize — 不改写字符串字面量和普通标识符中的 and/or")
    void normalizePreservesStringLiteralsAndIdentifiers() {
        assertEquals("if(label == 'sales and service' orFlag == 1, brand, 0)",
                dialect.normalize("if(label == 'sales and service' orFlag == 1, brand, 0)"));
        assertEquals("if(label == \"sales or service\" and_flag == 1, order_amount, 0)",
                dialect.normalize("if(label == \"sales or service\" and_flag == 1, order_amount, 0)"));
    }

    // ---- isKeywordAsIdentifier 钩子语义 ----

    @Test
    @DisplayName("钩子 — IF + '(' 返回 true（函数调用场景）")
    void hookDowngradesIfBeforeLparen() {
        assertTrue(dialect.isKeywordAsIdentifier(ExpSymbols.IF, '('));
    }

    @Test
    @DisplayName("钩子 — IF 后跟非 '(' 不降级（保留 FSScript 控制流语义）")
    void hookDoesNotDowngradeIfForControlFlow() {
        assertFalse(dialect.isKeywordAsIdentifier(ExpSymbols.IF, ' '),
                "`if (cond)` 控制流语法不应被降级");
        assertFalse(dialect.isKeywordAsIdentifier(ExpSymbols.IF, '\n'));
        assertFalse(dialect.isKeywordAsIdentifier(ExpSymbols.IF, '{'));
    }

    @Test
    @DisplayName("钩子 — 其他 keyword 不降级（ELSE / FOR / WHILE / LET / ...）")
    void hookDoesNotDowngradeOtherKeywords() {
        assertFalse(dialect.isKeywordAsIdentifier(ExpSymbols.ELSE, '('));
        assertFalse(dialect.isKeywordAsIdentifier(ExpSymbols.FOR, '('));
        assertFalse(dialect.isKeywordAsIdentifier(ExpSymbols.WHILE, '('));
        assertFalse(dialect.isKeywordAsIdentifier(ExpSymbols.LET, '('));
        assertFalse(dialect.isKeywordAsIdentifier(ExpSymbols.RETURN, '('));
        assertFalse(dialect.isKeywordAsIdentifier(ExpSymbols.IN, '('));
    }

    @Test
    @DisplayName("钩子 — SQL_EXPRESSION 把 SQL 风格 and 映射为逻辑 &&")
    void hookMapsSqlAndToLogicalAnd() {
        assertEquals(ExpSymbols.AA, dialect.mapIdentifierToken("and", ' '));
        assertEquals(ExpSymbols.AA, dialect.mapIdentifierToken("AND", ' '));
        assertEquals(ExpSymbols.ID, dialect.mapIdentifierToken("and_flag", ' '));
    }

    @Test
    @DisplayName("DEFAULT 方言 — 所有 keyword 都不降级")
    void defaultDialectDoesNotDowngradeAnyKeyword() {
        FsscriptDialect def = FsscriptDialect.DEFAULT;
        assertFalse(def.isKeywordAsIdentifier(ExpSymbols.IF, '('));
        assertFalse(def.isKeywordAsIdentifier(ExpSymbols.FOR, '('));
        assertFalse(def.isKeywordAsIdentifier(ExpSymbols.ELSE, ' '));
        assertEquals(ExpSymbols.ID, def.mapIdentifierToken("and", ' '));
    }
}
