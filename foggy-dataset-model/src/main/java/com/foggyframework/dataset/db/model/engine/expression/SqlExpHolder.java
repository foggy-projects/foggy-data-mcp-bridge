package com.foggyframework.dataset.db.model.engine.expression;

import com.foggyframework.fsscript.parser.spi.Exp;

/**
 * SQL 表达式持有者接口
 * <p>
 * 用于标识包装了内部 SQL 表达式的 AST 节点，
 * 便于在遍历 AST 时提取真正的 SQL 表达式进行分析。
 * </p>
 *
 * @author Foggy
 * @since 1.0
 */
public interface SqlExpHolder {

    /**
     * 获取内部的 SQL 表达式
     *
     * @return 内部的 Exp，可能是 SqlFunctionExp、SqlBinaryExp 等
     */
    Exp getInnerSqlExp();
}
