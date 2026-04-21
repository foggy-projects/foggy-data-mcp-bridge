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
 * {@link ComposeQueryDialect} 单元测试（8.2.0.beta Compose Query M3）。
 * <p>
 * 对等 Python {@code tests/compose/sandbox/test_compose_query_dialect.py}。
 * 方言单一职责：在 Scanner 层把 {@code (FROM, '(')} 二字节序列降级为 IDENTIFIER，
 * 使 {@code from(...)} 可作为普通函数调用进入 AST。
 * </p>
 *
 * @since 8.2.0.beta
 */
@DisplayName("ComposeQueryDialect · Scanner 钩子单元测试")
class ComposeQueryDialectTest {

    private final ComposeQueryDialect dialect = ComposeQueryDialect.INSTANCE;

    @Test
    @DisplayName("单例 — INSTANCE 多次引用返回同一对象")
    void singletonInstanceIsStable() {
        assertSame(ComposeQueryDialect.INSTANCE, ComposeQueryDialect.INSTANCE);
        assertSame(dialect, ComposeQueryDialect.INSTANCE);
    }

    @Test
    @DisplayName("方言元数据 — name = compose-query")
    void dialectName() {
        assertEquals("compose-query", dialect.getName());
    }

    @Test
    @DisplayName("钩子 — (FROM, '(') 降级为 IDENTIFIER（Compose Query 顶层入口）")
    void hookDowngradesFromBeforeLparen() {
        assertTrue(dialect.isKeywordAsIdentifier(ExpSymbols.FROM, '('));
    }

    @Test
    @DisplayName("钩子 — FROM 后跟非 '(' 保持保留字语义（预留 import 语法）")
    void hookDoesNotDowngradeFromForOtherContexts() {
        assertFalse(dialect.isKeywordAsIdentifier(ExpSymbols.FROM, ' '),
                "`import x from 'mod'` 语法位置不应被降级");
        assertFalse(dialect.isKeywordAsIdentifier(ExpSymbols.FROM, '\n'));
        assertFalse(dialect.isKeywordAsIdentifier(ExpSymbols.FROM, '"'));
        assertFalse(dialect.isKeywordAsIdentifier(ExpSymbols.FROM, '\''));
    }

    @Test
    @DisplayName("钩子 — 其他 keyword 保持默认（IF / ELSE / RETURN / CONST / FOR / WHILE / LET / IN）")
    void hookDoesNotDowngradeOtherKeywords() {
        assertFalse(dialect.isKeywordAsIdentifier(ExpSymbols.IF, '('),
                "compose-query 方言不处理 IF（由 SqlExpressionDialect 处理）");
        assertFalse(dialect.isKeywordAsIdentifier(ExpSymbols.ELSE, '('));
        assertFalse(dialect.isKeywordAsIdentifier(ExpSymbols.RETURN, '('));
        assertFalse(dialect.isKeywordAsIdentifier(ExpSymbols.CONST, '('));
        assertFalse(dialect.isKeywordAsIdentifier(ExpSymbols.FOR, '('));
        assertFalse(dialect.isKeywordAsIdentifier(ExpSymbols.WHILE, '('));
        assertFalse(dialect.isKeywordAsIdentifier(ExpSymbols.LET, '('));
        assertFalse(dialect.isKeywordAsIdentifier(ExpSymbols.IN, '('));
    }

    @Test
    @DisplayName("normalize — noop（所有源码保持原串）")
    void normalizeIsNoop() {
        assertNull(dialect.normalize(null));
        assertEquals("", dialect.normalize(""));
        String[] samples = {
                "from({model: 'X'})",
                "const x = from({model: 'X'})",
                "from({model: 'X'}).query({columns: ['a']})",
                "let y = from({model: 'A'}).join(from({model: 'B'}))",
                "'text containing from( inside a literal'",
        };
        for (String src : samples) {
            assertEquals(src, dialect.normalize(src),
                    "compose-query normalize 应为 noop，对输入 '" + src + "' 被改写");
        }
    }

    @Test
    @DisplayName("DEFAULT 方言 — (FROM, '(') 依然是保留字，不降级")
    void defaultDialectKeepsFromAsKeyword() {
        FsscriptDialect def = FsscriptDialect.DEFAULT;
        assertFalse(def.isKeywordAsIdentifier(ExpSymbols.FROM, '('));
        assertFalse(def.isKeywordAsIdentifier(ExpSymbols.FROM, ' '));
    }

    @Test
    @DisplayName("SQL_EXPRESSION 方言 — 不处理 FROM（职责分离）")
    void sqlExpressionDialectDoesNotDowngradeFrom() {
        FsscriptDialect sql = FsscriptDialect.SQL_EXPRESSION;
        assertFalse(sql.isKeywordAsIdentifier(ExpSymbols.FROM, '('),
                "SQL 表达式方言只降级 IF，FROM 留给 ComposeQueryDialect");
        assertFalse(sql.isKeywordAsIdentifier(ExpSymbols.FROM, ' '));
    }
}
