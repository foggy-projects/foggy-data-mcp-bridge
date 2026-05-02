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
 * Compose Query 方言（8.2.0.beta）：允许 {@code from(...)} 作为普通函数调用进入 AST，
 * 与 JS 宿主的 {@code from({model: 'X'})} 形态对齐。
 * <p>
 * FSScript 基线把 {@code from} 作为保留字（ES Module 风格 {@code import ... from '...'}
 * 的语法位置）。Compose Query 脚本不走 import 语法，但需要 {@code from(} 作为顶层入口
 * 函数调用；本方言在 Scanner 层精细降级：
 * </p>
 * <ul>
 *   <li>只对 {@code (keyword = FROM, nextChar = '(')} 的二字节序列返回 true</li>
 *   <li>对其他 keyword（{@code IF / RETURN / CONST / FOR / WHILE} 等）保持默认</li>
 *   <li>对 {@code FROM} 后跟空格 / 换行 / 其他字符的组合不降级（保留未来 import 语法可能）</li>
 * </ul>
 * <p>
 * Python 对等实现见 {@code foggy.fsscript.parser.dialect.COMPOSE_QUERY_DIALECT}。
 * Python 侧采用"整体移除 {@code from} 保留字"的粒度，Java 侧走 Scanner 钩子做
 * 二字节精细降级；两种路径功能等价：都让 {@code from(...)} 被识别为 FunctionCall。
 * </p>
 * <p>
 * 实现为无状态单例，线程安全。
 * </p>
 *
 * @since 8.2.0.beta
 */
public final class ComposeQueryDialect extends FsscriptDialect {

    public static final ComposeQueryDialect INSTANCE = new ComposeQueryDialect();

    private ComposeQueryDialect() { /* singleton */ }

    @Override
    public String getName() {
        return "compose-query";
    }

    @Override
    public boolean isKeywordAsIdentifier(int keywordSymbol, int nextChar) {
        return keywordSymbol == ExpSymbols.FROM && nextChar == '(';
    }

    // normalize(source) 继承基类默认实现 —— 返回原串。方言语义完全由 Scanner 钩子承担。
}
