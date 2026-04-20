/*******************************************************************************
 * This software is subject to the terms of the GNU LESSER GENERAL PUBLIC LICENSE
 * Agreement, available at the following URL:
 * http://www.gnu.org/licenses/lgpl.html
 * Copyright (c) 2012, 2013  Foggy.
 * All rights reserved.
 * You must accept the terms of that agreement to use this software.
 *******************************************************************************/
package com.foggyframework.fsscript.parser;

import com.foggyframework.fsscript.parser.spi.CompileException;
import com.foggyframework.fsscript.parser.spi.Exp;
import com.foggyframework.fsscript.parser.spi.Parser;
import com.foggyframework.fsscript.parser.spi.ParserFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link FsscriptDialect} 抽象层 + 与 {@link Parser} 集成的用例。
 * <p>
 * 目标：
 * </p>
 * <ul>
 *   <li>{@link FsscriptDialect#DEFAULT} 返回原串；parser 遇到 {@code if(...)} 会报错（保留字冲突）</li>
 *   <li>{@link FsscriptDialect#SQL_EXPRESSION} 会把 {@code if(} 改写为 {@code IIF(}，parser 可接受</li>
 *   <li>新 compileEl(ctx, str, dialect) 与旧 compileEl(ctx, str) 行为一致（无方言场景）</li>
 * </ul>
 *
 * @since 8.1.11.beta
 */
@DisplayName("FsscriptDialect · 抽象层与 Parser 集成")
class FsscriptDialectTest {

    private final Parser parser = ParserFactory.newInstance().newExpParser();

    @Test
    @DisplayName("预定义方言元数据")
    void dialectNames() {
        assertEquals("default", FsscriptDialect.DEFAULT.getName());
        assertEquals("sql-expression", FsscriptDialect.SQL_EXPRESSION.getName());
    }

    @Test
    @DisplayName("DEFAULT — normalize 原样返回")
    void defaultDialectNormalizeIsIdentity() {
        String src = "if(x, 1, 0)";
        assertSame(src, FsscriptDialect.DEFAULT.normalize(src));
    }

    @Test
    @DisplayName("DEFAULT — parser 遇到 if(...) 保留字应编译失败")
    void defaultDialectParserRejectsIfFunction() {
        // if 是 FSScript 控制流保留字，函数式调用会被 parser 拒绝
        assertThrows(Exception.class, () -> parser.compileEl(null, "if(1 == 1, 2, 3)", FsscriptDialect.DEFAULT));
    }

    @Test
    @DisplayName("SQL_EXPRESSION — parser 接受 if(...) 函数式语法")
    void sqlExpressionDialectParserAcceptsIfFunction() throws CompileException {
        Exp exp = parser.compileEl(null, "if(1 == 1, 2, 3)", FsscriptDialect.SQL_EXPRESSION);
        assertNotNull(exp);
    }

    @Test
    @DisplayName("SQL_EXPRESSION — 嵌套 if( 可编译")
    void sqlExpressionDialectAcceptsNestedIf() throws CompileException {
        Exp exp = parser.compileEl(null, "if(a > 0, if(b > 0, 1, 2), 0)", FsscriptDialect.SQL_EXPRESSION);
        assertNotNull(exp);
    }

    @Test
    @DisplayName("三参重载传 null — 等价 DEFAULT（原样）")
    void nullDialectTreatedAsDefault() throws CompileException {
        // 非 if 语法走原路径
        Exp exp = parser.compileEl(null, "1 + 2", null);
        assertNotNull(exp);
    }

    @Test
    @DisplayName("二参 compileEl 与 三参 compileEl+DEFAULT 行为一致（非 if 表达式）")
    void twoArgAndThreeArgDefaultAreEquivalent() throws CompileException {
        Exp e1 = parser.compileEl(null, "a + b * 2");
        Exp e2 = parser.compileEl(null, "a + b * 2", FsscriptDialect.DEFAULT);
        assertNotNull(e1);
        assertNotNull(e2);
    }
}
