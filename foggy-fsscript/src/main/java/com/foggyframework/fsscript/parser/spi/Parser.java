/*******************************************************************************
 * This software is subject to the terms of the GNU LESSER GENERAL PUBLIC LICENSE
 * Agreement, available at the following URL:
 * http://www.gnu.org/licenses/lgpl.html
 * Copyright (c) 2012, 2013  Foggy.
 * All rights reserved. 
 * You must accept the terms of that agreement to use this software.
 *******************************************************************************/
package com.foggyframework.fsscript.parser.spi;

import com.foggyframework.fsscript.parser.FsscriptDialect;

public interface Parser {

    /**
     * "awerwer,asdferwe--wre"+${ee-2}+"werw"
     *
     * @param str
     * @return
     * @throws Exception
     */
    Exp compile(FsscriptClosureDefinition fcDefinition, String str) throws CompileException;

    /**
     * ds.select('s')
     *
     * @param str
     * @return
     * @throws Exception
     */
    Exp compileEl(FsscriptClosureDefinition fcDefinition, String str) throws CompileException;

    /**
     * 带方言的 EL 编译入口。
     * <p>
     * v1.4 起方言机制升级为 Scanner 层 keyword 降级（实装在
     * {@code ElExpScanner.next_token()}）。方言通过
     * {@link FsscriptDialect#isKeywordAsIdentifier(int, int)} 钩子让 scanner 决定
     * 在词法层把某些保留字（如 SQL 方言下的 {@code IF + '('}）当成 IDENTIFIER 处理。
     * </p>
     * <p>
     * 默认实现是兜底：对没升级到 scanner-level 方言的 Parser 实现，{@code dialect} 参数
     * 被忽略（旧行为 == {@link FsscriptDialect#normalize(String)} noop）。
     * 生产实现（例如 {@code FoggyParserFactory.FoggyParserX}）应覆写此方法，把 dialect
     * 通过 {@code ExpParser.setDialect} 传到 scanner。
     * </p>
     *
     * @param fcDefinition 闭包定义（可为 null）
     * @param str          原始 FSScript 表达式
     * @param dialect      方言；传 null 等价于 {@link FsscriptDialect#DEFAULT}
     * @return 编译后的 AST
     * @throws CompileException 语法错误
     * @since 8.1.11.beta
     */
    default Exp compileEl(FsscriptClosureDefinition fcDefinition, String str, FsscriptDialect dialect)
            throws CompileException {
        String normalized = (dialect == null ? str : dialect.normalize(str));
        return compileEl(fcDefinition, normalized);
    }


}
