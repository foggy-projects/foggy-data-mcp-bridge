package com.foggyframework.dataset.db.model.engine.formula.hierarchy;

import com.foggyframework.dataset.db.model.engine.query.JdbcQuery;

/**
 * descendantsOf 操作符 - 查询所有后代（不含自身）
 *
 * <p>生成条件：distance > 0（默认）或 distance BETWEEN 1 AND maxDepth
 *
 * <p>示例：
 * <pre>
 * { "field": "team$id", "op": "descendantsOf", "value": "T001" }
 * → WHERE closure.parent_id = 'T001' AND closure.distance > 0
 *
 * { "field": "team$id", "op": "descendantsOf", "value": "T001", "maxDepth": 2 }
 * → WHERE closure.parent_id = 'T001' AND closure.distance BETWEEN 1 AND 2
 * </pre>
 */
public class DescendantsOfOperator implements HierarchyOperator {

    @Override
    public String[] getNameList() {
        return new String[]{"descendantsOf", "descendants_of"};
    }

    @Override
    public void buildDistanceCondition(JdbcQuery.JdbcListCond listCond, String closureAlias, Integer maxDepth) {
        String distanceColumn = closureAlias + ".distance";

        if (maxDepth != null) {
            // 限制深度：distance BETWEEN 1 AND maxDepth
            listCond.and(distanceColumn + " BETWEEN 1 AND " + maxDepth);
        } else {
            // 默认模式：所有后代（不含自身）
            listCond.and(distanceColumn + " > 0");
        }
    }
}
