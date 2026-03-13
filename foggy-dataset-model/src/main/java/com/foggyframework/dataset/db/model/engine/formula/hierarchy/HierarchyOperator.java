package com.foggyframework.dataset.db.model.engine.formula.hierarchy;

import com.foggyframework.dataset.db.model.engine.query.JdbcQuery;
import com.foggyframework.dataset.db.model.impl.dimension.DbModelParentChildDimensionImpl;

/**
 * 父子维度层级操作符接口
 *
 * <p>用于处理父子维度的层级查询操作符。支持两个方向：
 * <ul>
 *   <li><b>后代方向</b>（默认）：childrenOf、descendantsOf、selfAndDescendantsOf
 *       — JOIN closure ON fact.FK = closure.childKey, WHERE closure.parentKey = value</li>
 *   <li><b>祖先方向</b>：ancestorsOf、selfAndAncestorsOf
 *       — JOIN closure ON fact.FK = closure.parentKey, WHERE closure.childKey = value</li>
 * </ul>
 *
 * <p>与 SqlFormula 不同，HierarchyOperator 可以访问完整的查询上下文，支持：
 * <ul>
 *   <li>Join 闭包表</li>
 *   <li>添加 distance 条件</li>
 *   <li>访问父子维度配置</li>
 * </ul>
 */
public interface HierarchyOperator {

    /**
     * 获取操作符名称列表
     *
     * @return 操作符名称数组（支持别名）
     */
    String[] getNameList();

    /**
     * 构建并添加 distance 条件
     *
     * @param listCond     条件列表
     * @param closureAlias 闭包表别名
     * @param maxDepth     最大深度（可选，用于限制查询范围）
     */
    void buildDistanceCondition(JdbcQuery.JdbcListCond listCond, String closureAlias, Integer maxDepth);

    /**
     * 是否为祖先方向查询
     *
     * <p>祖先方向操作符（ancestorsOf、selfAndAncestorsOf）使用反向 JOIN：
     * <ul>
     *   <li>JOIN: fact.FK = closure.parentKey（而非 childKey）</li>
     *   <li>WHERE: closure.childKey = value（而非 parentKey）</li>
     * </ul>
     *
     * @return true 表示祖先方向，false 表示后代方向（默认）
     */
    default boolean isAncestorDirection() {
        return false;
    }
}
