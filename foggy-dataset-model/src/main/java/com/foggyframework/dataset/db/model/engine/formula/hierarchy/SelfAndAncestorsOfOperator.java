package com.foggyframework.dataset.db.model.engine.formula.hierarchy;

import com.foggyframework.dataset.db.model.engine.query.JdbcQuery;

/**
 * selfAndAncestorsOf 操作符 - 查询自身及所有祖先
 *
 * <p>祖先方向查询，不添加 distance 条件时包含自身（distance >= 0）
 *
 * <p>与 selfAndDescendantsOf 的区别：JOIN 方向相反
 * <ul>
 *   <li>selfAndDescendantsOf: JOIN closure.childKey, WHERE closure.parentKey = X</li>
 *   <li>selfAndAncestorsOf: JOIN closure.parentKey, WHERE closure.childKey = X</li>
 * </ul>
 *
 * <p>示例：
 * <pre>
 * { "field": "company$id", "op": "selfAndAncestorsOf", "value": 3 }
 * → JOIN closure ON fact.company_id = closure.parent_id
 *   WHERE closure.company_id = 3
 * → 返回公司 3 及其所有上级公司
 * </pre>
 */
public class SelfAndAncestorsOfOperator implements HierarchyOperator {

    @Override
    public String[] getNameList() {
        return new String[]{"selfAndAncestorsOf", "self_and_ancestors_of"};
    }

    @Override
    public void buildDistanceCondition(JdbcQuery.JdbcListCond listCond, String closureAlias, Integer maxDepth) {
        // selfAndAncestorsOf 不添加 distance 条件，包含自身（distance >= 0）
        // 如果指定了 maxDepth，则限制为 distance <= maxDepth
        if (maxDepth != null) {
            String distanceColumn = closureAlias + ".distance";
            listCond.and(distanceColumn + " <= " + maxDepth);
        }
    }

    @Override
    public boolean isAncestorDirection() {
        return true;
    }
}
