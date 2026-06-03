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
 * FSScript 方言抽象。
 * <p>
 * 每个方言对外暴露一个 {@link #normalize(String)} 方法，负责在 parse 前对源码做
 * 必要的词法预处理，让某些 FSScript 保留字（如 {@code if} / {@code while} 等）
 * 可以作为函数名使用。
 * </p>
 * <p>
 * 由于 CUP 生成的 parser 不能运行时修改产生式，方言机制主要通过 scanner 钩子实现，
 * 必要时也可在 {@link #normalize(String)} 中做 parse 前的轻量词法归一化。
 * </p>
 * <p>
 * 使用方式：
 * </p>
 * <pre>
 *   FsscriptDialect dialect = FsscriptDialect.SQL_EXPRESSION;
 *   Exp exp = parser.compileEl(null, input, dialect);
 * </pre>
 * <p>
 * Thread-safe: 实现类必须是无状态的，可在多线程安全共享。
 * </p>
 *
 * @since 8.1.11.beta
 */
public abstract class FsscriptDialect {

    /** 方言名（用于日志 / 调试）。 */
    public abstract String getName();

    /**
     * 对源码做词法预处理。
     * <p>
     * Java 侧优先使用 Scanner 层 keyword 降级（见 {@link #isKeywordAsIdentifier(int, int)}）；
     * 对 CUP 语法中难以通过 token 映射覆盖的兼容形态，可在这里做最小化归一化。
     * </p>
     *
     * @param source 原始 FSScript 源码
     * @return 预处理后源码（v1.4 起默认即 source 本身）
     */
    public String normalize(String source) {
        return source;
    }

    /**
     * Scanner 层钩子：判断当前 keyword token 是否应被降级为 IDENTIFIER。
     * <p>
     * {@link ElExpScanner#next_token()} 在 identifier 查表命中保留字后调用此方法。
     * 如果返回 true，scanner 发 {@code ID} token 而不是原 keyword token，
     * 等价于"在本方言里这个 keyword 不是保留字"。
     * </p>
     * <p>
     * 典型用例：SQL 表达式方言允许 {@code if(c, a, b)} 作为函数调用，
     * 此方法在遇到 {@code IF} 且下一个字符是 {@code '('} 时返回 true。
     * </p>
     * <p>
     * 默认实现始终返回 false（不降级任何 keyword）。
     * </p>
     *
     * @param keywordSymbol {@link ExpSymbols} 里的 keyword id（例如 {@link ExpSymbols#IF}）
     * @param nextChar      identifier 紧跟的下一个字符（未 advance）
     * @return true 表示把此 keyword 降级为 IDENTIFIER
     * @since v1.4
     */
    public boolean isKeywordAsIdentifier(int keywordSymbol, int nextChar) {
        return false;
    }

    /**
     * Scanner 层钩子：允许方言把普通 identifier 映射成特定 token。
     * <p>
     * 该钩子只在 identifier 未命中全局 keyword 表时触发。默认仍返回
     * {@link ExpSymbols#ID}，保持 DEFAULT 方言和既有脚本语义不变。
     * </p>
     *
     * @param identifier 当前 identifier 原始文本
     * @param nextChar   identifier 紧跟的下一个字符（未 advance）
     * @return 要发给 parser 的 token id，默认 {@link ExpSymbols#ID}
     */
    public int mapIdentifierToken(String identifier, int nextChar) {
        return ExpSymbols.ID;
    }

    // ---- 预定义方言 ----

    /**
     * 默认方言：不做任何预处理，保留 FSScript 全部保留字语义。
     */
    public static final FsscriptDialect DEFAULT = new FsscriptDialect() {
        @Override public String getName() { return "default"; }
        // normalize / isKeywordAsIdentifier 均走基类默认实现：返回原串 / 不降级任何 keyword
    };

    /**
     * SQL 表达式方言：把 {@code if(} 重写为 {@code IIF(}（字符串字面量 / 标识符边界受保护）。
     * 用于 formula compiler / calculated field 等需要 {@code if(c, a, b)} 函数形态的场景。
     */
    public static final FsscriptDialect SQL_EXPRESSION = new SqlExpressionDialect();
}
