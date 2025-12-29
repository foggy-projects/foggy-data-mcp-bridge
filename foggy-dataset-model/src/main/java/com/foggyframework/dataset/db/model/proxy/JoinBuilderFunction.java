package com.foggyframework.dataset.db.model.proxy;

import com.foggyframework.fsscript.exp.FsscriptFunction;
import com.foggyframework.fsscript.parser.spi.Exp;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;
import lombok.Getter;

import java.util.Collections;
import java.util.List;

/**
 * JoinBuilder 的 FsscriptFunction 适配器
 *
 * <p>将 {@link JoinBuilder} 转换为 {@link FsscriptFunction}，
 * 使其可以被现有的 JdbcModelDx 和 JoinGraph 机制使用。
 *
 * <p>当调用时，返回预计算的 ON 子句 SQL 字符串。
 *
 * @author Foggy Framework
 * @since 2.0
 */
@Getter
public class JoinBuilderFunction implements FsscriptFunction {

    /**
     * 原始的 JoinBuilder
     */
    private final JoinBuilder joinBuilder;

    /**
     * 预计算的 ON 子句 SQL
     */
    private final String onClause;

    /**
     * 创建适配器
     *
     * @param joinBuilder JOIN 构建器
     */
    public JoinBuilderFunction(JoinBuilder joinBuilder) {
        this.joinBuilder = joinBuilder;
        // 预计算 ON 子句
        this.onClause = joinBuilder.buildOnClause();
    }

    @Override
    public Object threadSafeAccept(Object t) {
        return onClause;
    }

    @Override
    public Object executeFunction(ExpEvaluator evaluator, Object... args) {
        return onClause;
    }

    @Override
    public List<Exp> getArgDefs() {
        return Collections.emptyList();
    }

    @Override
    public Object autoApply(ExpEvaluator ee) {
        return onClause;
    }

    @Override
    public Object apply(Object[] objects) {
        return onClause;
    }

    @Override
    public String toString() {
        return "JoinBuilderFunction{" + onClause + "}";
    }
}
