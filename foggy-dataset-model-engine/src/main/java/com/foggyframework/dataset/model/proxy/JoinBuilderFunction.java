package com.foggyframework.dataset.model.proxy;

import com.foggyframework.dataset.model.spi.DbColumn;
import com.foggyframework.dataset.model.spi.QueryModel;
import com.foggyframework.dataset.model.spi.QueryObject;
import com.foggyframework.fsscript.exp.FsscriptFunction;
import com.foggyframework.fsscript.parser.spi.Exp;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

    public String buildOnClause(QueryModel queryModel) {
        if (queryModel == null) {
            return onClause;
        }
        return joinBuilder.buildOnClause(queryModel);
    }

    /**
     * 获取 ON 条件中引用到的查询对象。
     *
     * <p>用于在渲染使用自定义 ON 条件的 JOIN 前，先补齐 ON 条件依赖的维表路径。
     * 例如 {@code fo.leftJoin(agg).on(fo.store$storeId, agg.storeId)} 需要先 JOIN store 维表，
     * 否则 ON 条件会引用一个尚未出现在 FROM/JOIN 列表中的别名。</p>
     *
     * @param queryModel 查询模型
     * @return 按 ON 条件出现顺序去重后的查询对象
     */
    public List<QueryObject> getReferencedQueryObjects(QueryModel queryModel) {
        if (queryModel == null) {
            return Collections.emptyList();
        }
        Map<String, QueryObject> queryObjects = new LinkedHashMap<>();
        for (JoinCondition condition : joinBuilder.getConditions()) {
            for (DbColumn column : condition.resolveReferencedColumns(queryModel)) {
                QueryObject queryObject = column.getQueryObject();
                if (queryObject != null) {
                    queryObjects.putIfAbsent(queryObject.getAlias(), queryObject);
                }
            }
        }
        return new ArrayList<>(queryObjects.values());
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
