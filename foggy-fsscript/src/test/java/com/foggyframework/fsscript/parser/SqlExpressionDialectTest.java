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
 * v1.4 起字符状态机下线，{@code normalize} 退化为 noop；方言语义改由
 * {@link FsscriptDialect#isKeywordAsIdentifier(int, int)} 钩子在 scanner 层实现。
 * 因此本测试不再断言字符串改写结果，转而：
 * </p>
 * <ul>
 *   <li>断言 normalize 对所有输入都返回原串（noop 不变性）</li>
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

    // ---- normalize noop 不变性 ----

    @Test
    @DisplayName("normalize — null / empty / 任意内容 都返回原串")
    void normalizeIsNoop() {
        assertNull(dialect.normalize(null));
        assertEquals("", dialect.normalize(""));

        // 历史字符状态机会改写的形态，现在全部保持原串
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
                    "normalize 应为 noop，但对输入 '" + src + "' 做了改写");
        }
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
    @DisplayName("DEFAULT 方言 — 所有 keyword 都不降级")
    void defaultDialectDoesNotDowngradeAnyKeyword() {
        FsscriptDialect def = FsscriptDialect.DEFAULT;
        assertFalse(def.isKeywordAsIdentifier(ExpSymbols.IF, '('));
        assertFalse(def.isKeywordAsIdentifier(ExpSymbols.FOR, '('));
        assertFalse(def.isKeywordAsIdentifier(ExpSymbols.ELSE, ' '));
    }
}
