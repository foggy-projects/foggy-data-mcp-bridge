/*******************************************************************************
 * This software is subject to the terms of the GNU LESSER GENERAL PUBLIC LICENSE
 * Agreement, available at the following URL:
 * http://www.gnu.org/licenses/lgpl.html
 * Copyright (c) 2012, 2013  Foggy.
 * All rights reserved.
 * You must accept the terms of that agreement to use this software.
 *******************************************************************************/
package com.foggyframework.fsscript.parser;

/**
 * SQL 表达式方言：允许 {@code if(c, a, b)} 作为函数调用语法（FSScript 默认情况下 {@code if}
 * 是控制流保留字，函数式 {@code if(...)} 会被 parser 拒绝）。
 * <p>
 * <b>v1.4 起实装升级为 Scanner 层方言化</b>：{@link ElExpScanner} 在 identifier 查表
 * 命中 {@code IF} 且下一个字符为 {@code '('} 时调用 {@link #isKeywordAsIdentifier(int, int)}，
 * 此方言返回 true，scanner 直接发 {@code ID} token 而不是 {@code IF} token。下游 parser 把
 * {@code if(...)} 当成普通函数调用处理，AST 函数名字面量为小写 {@code if}，与 Python r5 对齐。
 * </p>
 * <p>
 * 历史字符状态机 {@code if( → IIF(} 字符串预处理已退化为 noop。此前 v1.3 及更早版本里
 * {@code CalculatedFieldService.compileExpression} 依赖该字符串改写产出 {@code IIF} 函数名，
 * 升级后 AST 直接是 {@code if}，下游 {@code SqlFunctionExp} 等走 {@code .toUpperCase()}
 * 链路自动兼容。
 * </p>
 * <p>
 * 实现为无状态单例，线程安全。
 * </p>
 *
 * @since 8.1.11.beta
 */
public final class SqlExpressionDialect extends FsscriptDialect {

    @Override
    public String getName() {
        return "sql-expression";
    }

    @Override
    public boolean isKeywordAsIdentifier(int keywordSymbol, int nextChar) {
        return keywordSymbol == ExpSymbols.IF && nextChar == '(';
    }

    @Override
    public int mapIdentifierToken(String identifier, int nextChar) {
        if ("AND".equalsIgnoreCase(identifier)) {
            return ExpSymbols.AA;
        }
        return super.mapIdentifierToken(identifier, nextChar);
    }

    // normalize(source) 继承基类默认实现 —— 返回原串。v1.4 起字符串预处理链路已下线。
}
