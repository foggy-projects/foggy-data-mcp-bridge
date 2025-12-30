package com.foggyframework.dataset.db.model.engine.formula.hierarchy;

import com.foggyframework.dataset.db.model.engine.query.JdbcQuery;

/**
 * childrenOf 操作符 - 查询直接子节点
 *
 * <p>生成条件：distance = 1（默认）或 distance BETWEEN 1 AND maxDepth
 *
 * <p>示例：
 * <pre>
 * { "field": "team$id", "op": "childrenOf", "value": "T001" }
 * → WHERE closure.parent_id = 'T001' AND closure.distance = 1
 *
 * { "field": "team$id", "op": "childrenOf", "value": "T001", "maxDepth": 2 }
 * → WHERE closure.parent_id = 'T001' AND closure.distance BETWEEN 1 AND 2
 * </pre>
 */
public class ChildrenOfOperator implements HierarchyOperator {

    @Override
    public String[] getNameList() {
        return new String[]{"childrenOf", "children_of"};
    }

    @Override
    public void buildDistanceCondition(JdbcQuery.JdbcListCond listCond, String closureAlias, Integer maxDepth) {
        String distanceColumn = closureAlias + ".distance";

        if (maxDepth != null && maxDepth > 1) {
            // 扩展模式：distance BETWEEN 1 AND maxDepth
            listCond.and(distanceColumn + " BETWEEN 1 AND " + maxDepth);
        } else {
            // 默认模式：只查直接子节点
            listCond.and(distanceColumn + " = 1");
        }
    }
}
