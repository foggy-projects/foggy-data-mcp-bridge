/*******************************************************************************
 * This software is subject to the terms of the GNU LESSER GENERAL PUBLIC LICENSE
 * Agreement, available at the following URL:
 * http://www.gnu.org/licenses/lgpl.html
 * Copyright (c) 2012, 2013  Foggy.
 * All rights reserved.
 * You must accept the terms of that agreement to use this software.
 *******************************************************************************/
package com.foggyframework.fsscript.parser;

import com.foggyframework.fsscript.exp.ExpFunCall;
import com.foggyframework.fsscript.exp.IdExp;
import com.foggyframework.fsscript.parser.spi.CompileException;
import com.foggyframework.fsscript.parser.spi.Exp;
import com.foggyframework.fsscript.parser.spi.Parser;
import com.foggyframework.fsscript.parser.spi.ParserFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Parser + Scanner 方言集成 · AST 级端到端测试（v1.4）。
 * <p>
 * 目标是证明 {@link SqlExpressionDialect} 在 scanner 层把 {@code IF + '('} 降级为 IDENTIFIER
 * token，parser 收到的是普通函数调用形态，AST 函数名字面量为小写 {@code if}（与 Python r5 一致）。
 * </p>
 * <p>
 * 关键对比：
 * </p>
 * <ul>
 *   <li>DEFAULT 方言：parser 遇到 {@code if(} 会抛语法错（{@code IF} 是保留字）</li>
 *   <li>SQL_EXPRESSION 方言：parser 把 {@code if(...)} 当普通函数调用，AST 函数名字面量 == {@code "if"}</li>
 *   <li>FSScript 控制流 {@code if (cond) { ... } else { ... }} 在 DEFAULT 方言下照常编译</li>
 *   <li>字符串字面量内的 {@code if(} 不受任何影响（scanner 的 string literal 路径天然保护）</li>
 * </ul>
 *
 * @since v1.4
 */
@DisplayName("ExpParser + FsscriptDialect · AST 级端到端")
class ExpParserDialectTest {

    private Parser newParser() {
        return ParserFactory.newInstance().newExpParser();
    }

    // ---- DEFAULT 方言：if 仍是保留字 ----

    @Test
    @DisplayName("DEFAULT — if(a, 1, 0) 编译失败（IF 保留字冲突）")
    void defaultRejectsIfAsFunction() {
        Parser parser = newParser();
        assertThrows(Exception.class,
                () -> parser.compileEl(null, "if(a > 0, 1, 0)", FsscriptDialect.DEFAULT),
                "DEFAULT 方言下 if(...) 应被 parser 拒绝");
    }

    @Test
    @DisplayName("DEFAULT — 控制流 if (cond) { ... } else { ... } 照常编译")
    void defaultPreservesControlFlowIf() throws CompileException {
        Parser parser = newParser();
        // FSScript 自身的控制流语法：if 后跟空格 + '('
        Exp exp = parser.compileEl(null, "if (a > 0) { 1 } else { 0 }", FsscriptDialect.DEFAULT);
        assertNotNull(exp, "控制流 if 语句应编译通过");
    }

    @Test
    @DisplayName("DEFAULT + 传 null — 等价 DEFAULT（二参 compileEl 兼容）")
    void defaultMatchesNoDialect() throws CompileException {
        Parser parser = newParser();
        // 非 if 表达式两种方式都应能编译
        Exp e1 = parser.compileEl(null, "a + b * 2");
        Exp e2 = parser.compileEl(null, "a + b * 2", FsscriptDialect.DEFAULT);
        assertNotNull(e1);
        assertNotNull(e2);
    }

    // ---- SQL_EXPRESSION 方言：if 函数调用 ----

    @Test
    @DisplayName("SQL_EXPRESSION — if(a, 1, 0) 编译通过")
    void sqlExpressionAcceptsIfFunction() throws CompileException {
        Parser parser = newParser();
        Exp exp = parser.compileEl(null, "if(a > 0, 1, 0)", FsscriptDialect.SQL_EXPRESSION);
        assertNotNull(exp);
    }

    @Test
    @DisplayName("SQL_EXPRESSION — AST 函数名字面量 == \"if\"（小写，不是 IIF）")
    void sqlExpressionProducesLowercaseIfInAst() throws CompileException {
        Parser parser = newParser();
        Exp exp = parser.compileEl(null, "if(a > 0, 1, 0)", FsscriptDialect.SQL_EXPRESSION);

        assertInstanceOf(ExpFunCall.class, exp,
                "顶层 AST 节点应为 ExpFunCall（函数调用）");
        ExpFunCall call = (ExpFunCall) exp;
        Exp funExp = call.getValue();
        assertInstanceOf(IdExp.class, funExp,
                "函数表达式应为 IdExp（标识符），证明 scanner 确实把 IF 降级为 ID token");
        IdExp funId = (IdExp) funExp;
        assertEquals("if", funId.getValue(),
                "AST 函数名字面量必须是小写 \"if\"；如果是 \"IIF\" 说明还在走旧的字符串预处理路径");
    }

    @Test
    @DisplayName("SQL_EXPRESSION — 嵌套 if 编译通过，内外层 AST 均为 ExpFunCall")
    void sqlExpressionAcceptsNestedIf() throws CompileException {
        Parser parser = newParser();
        Exp exp = parser.compileEl(null, "if(a > 0, if(b > 0, 1, 2), 0)", FsscriptDialect.SQL_EXPRESSION);
        assertInstanceOf(ExpFunCall.class, exp);
    }

    @Test
    @DisplayName("SQL_EXPRESSION — 字符串字面量内的 if( 不被打扰（scanner string 路径天然保护）")
    void sqlExpressionDoesNotTouchStringLiterals() throws CompileException {
        Parser parser = newParser();
        // 整条表达式里外层 if 是函数调用，内层字符串 'has if(' 原样保留，编译不报错
        Exp exp = parser.compileEl(null, "if(s == 'has if(', 1, 0)", FsscriptDialect.SQL_EXPRESSION);
        assertNotNull(exp);
    }

    // ---- 上下文敏感性：同一 scanner 切换方言 ----

    @Test
    @DisplayName("同一 parser 实例连续切换方言 — cache 无污染")
    void dialectSwitchHasNoCachePollution() throws CompileException {
        Parser parser = newParser();

        // 先用 SQL_EXPRESSION 编译成功
        Exp e1 = parser.compileEl(null, "if(a > 0, 1, 0)", FsscriptDialect.SQL_EXPRESSION);
        assertNotNull(e1);

        // 立刻切回 DEFAULT，同一表达式应该被拒
        assertThrows(Exception.class,
                () -> parser.compileEl(null, "if(a > 0, 1, 0)", FsscriptDialect.DEFAULT),
                "切换到 DEFAULT 后 if(...) 应被拒绝，说明 dialect 切换干净");

        // 再切回 SQL_EXPRESSION，应重新编译通过
        Exp e3 = parser.compileEl(null, "if(a > 0, 1, 0)", FsscriptDialect.SQL_EXPRESSION);
        assertNotNull(e3);
    }
}
