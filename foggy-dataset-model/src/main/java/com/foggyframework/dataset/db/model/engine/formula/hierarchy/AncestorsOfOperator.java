package com.foggyframework.dataset.db.model.engine.formula.hierarchy;

import com.foggyframework.dataset.db.model.engine.query.JdbcQuery;

/**
 * ancestorsOf 操作符 - 查询所有祖先（不含自身）
 *
 * <p>祖先方向查询，生成条件：distance > 0（默认）或 distance BETWEEN 1 AND maxDepth
 *
 * <p>示例：
 * <pre>
 * { "field": "company$id", "op": "ancestorsOf", "value": 3 }
 * → JOIN closure ON fact.company_id = closure.parent_id
 *   WHERE closure.company_id = 3 AND closure.distance > 0
 * → 返回公司 3 的所有上级公司（不含公司 3 自身）
 *
 * { "field": "company$id", "op": "ancestorsOf", "value": 3, "maxDepth": 1 }
 * → WHERE closure.company_id = 3 AND closure.distance BETWEEN 1 AND 1
 * → 仅返回公司 3 的直接上级
 * </pre>
 */
public class AncestorsOfOperator implements HierarchyOperator {

    @Override
    public String[] getNameList() {
        return new String[]{"ancestorsOf", "ancestors_of"};
    }

    @Override
    public void buildDistanceCondition(JdbcQuery.JdbcListCond listCond, String closureAlias, Integer maxDepth) {
        String distanceColumn = closureAlias + ".distance";

        if (maxDepth != null) {
            // 限制深度：distance BETWEEN 1 AND maxDepth
            listCond.and(distanceColumn + " BETWEEN 1 AND " + maxDepth);
        } else {
            // 默认模式：所有祖先（不含自身）
            listCond.and(distanceColumn + " > 0");
        }
    }

    @Override
    public boolean isAncestorDirection() {
        return true;
    }
}
