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
 * 历史字符状态机 {@code if( → IIF(} 字符串预处理已下线。此方言仅在 parse 前把字符串字面量外的
 * 独立 SQL 逻辑词 {@code and}/{@code or} 归一化为 FSScript 逻辑符 {@code &&}/{@code ||}，
 * 用于兼容 LLM 生成的 SQL 风格条件表达式。
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

    @Override
    public String normalize(String source) {
        if (source == null || source.isEmpty()) {
            return source;
        }
        StringBuilder out = new StringBuilder(source.length());
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        for (int i = 0; i < source.length();) {
            char ch = source.charAt(i);
            if (ch == '\'' && !inDoubleQuote) {
                if (!isEscaped(source, i)) {
                    inSingleQuote = !inSingleQuote;
                }
                out.append(ch);
                i++;
                continue;
            }
            if (ch == '"' && !inSingleQuote) {
                if (!isEscaped(source, i)) {
                    inDoubleQuote = !inDoubleQuote;
                }
                out.append(ch);
                i++;
                continue;
            }
            if (!inSingleQuote && !inDoubleQuote && isIdentifierStart(ch)) {
                int end = i + 1;
                while (end < source.length() && isIdentifierPart(source.charAt(end))) {
                    end++;
                }
                String word = source.substring(i, end);
                if ("and".equalsIgnoreCase(word)) {
                    out.append("&&");
                } else if ("or".equalsIgnoreCase(word)) {
                    out.append("||");
                } else {
                    out.append(word);
                }
                i = end;
                continue;
            }
            out.append(ch);
            i++;
        }
        return out.toString();
    }

    private static boolean isEscaped(String source, int index) {
        int slashCount = 0;
        for (int i = index - 1; i >= 0 && source.charAt(i) == '\\'; i--) {
            slashCount++;
        }
        return slashCount % 2 == 1;
    }

    private static boolean isIdentifierStart(char ch) {
        return Character.isLetter(ch) || ch == '_' || ch == '$';
    }

    private static boolean isIdentifierPart(char ch) {
        return Character.isLetterOrDigit(ch) || ch == '_' || ch == '$';
    }
}
