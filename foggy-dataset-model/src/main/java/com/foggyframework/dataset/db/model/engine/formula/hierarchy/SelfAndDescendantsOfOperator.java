package com.foggyframework.dataset.db.model.engine.formula.hierarchy;

import com.foggyframework.dataset.db.model.engine.query.JdbcQuery;

/**
 * selfAndDescendantsOf 操作符 - 查询自身及所有后代
 *
 * <p>不添加 distance 条件，包含自身（distance >= 0）
 *
 * <p>等同于使用 team$hierarchy$id 视角
 *
 * <p>示例：
 * <pre>
 * { "field": "team$id", "op": "selfAndDescendantsOf", "value": "T001" }
 * → WHERE closure.parent_id = 'T001'
 * </pre>
 */
public class SelfAndDescendantsOfOperator implements HierarchyOperator {

    @Override
    public String[] getNameList() {
        return new String[]{"selfAndDescendantsOf", "self_and_descendants_of"};
    }

    @Override
    public void buildDistanceCondition(JdbcQuery.JdbcListCond listCond, String closureAlias, Integer maxDepth) {
        // selfAndDescendantsOf 不添加 distance 条件，包含自身（distance >= 0）
        // 如果指定了 maxDepth，则限制为 distance <= maxDepth
        if (maxDepth != null) {
            String distanceColumn = closureAlias + ".distance";
            listCond.and(distanceColumn + " <= " + maxDepth);
        }
    }
}
