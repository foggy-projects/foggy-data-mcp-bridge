package com.foggyframework.dataset.db.model.engine.join;

import com.foggyframework.dataset.db.model.spi.QueryObject;
import com.foggyframework.fsscript.exp.FsscriptFunction;
import jakarta.persistence.criteria.JoinType;
import lombok.Builder;
import lombok.Data;

/**
 * JOIN 边，表示两个 QueryObject 之间的关联关系
 * <p>
 * 在 JoinGraph 中，每条边表示一个可能的 LEFT JOIN 关系：
 * <pre>
 *   from (LEFT) ---[foreignKey/onBuilder]---> to (RIGHT)
 * </pre>
 * </p>
 *
 * <h3>设计说明</h3>
 * <ul>
 *   <li>边是有向的：from -> to</li>
 *   <li>边可以有多种 ON 条件来源：foreignKey、onBuilder、预计算的 onCondition</li>
 *   <li>边的 joinType 默认为 LEFT JOIN</li>
 * </ul>
 */
@Data
@Builder
public class JoinEdge {
    /**
     * LEFT 表（源头）
     */
    private final QueryObject from;

    /**
     * RIGHT 表（目标）
     */
    private final QueryObject to;

    /**
     * 外键字段名（在 from 表上）
     * <p>ON 条件格式: from.foreignKey = to.primaryKey</p>
     */
    private final String foreignKey;

    /**
     * 动态 ON 条件构建器
     * <p>如果设置，优先使用 onBuilder 生成 ON 条件</p>
     */
    private final FsscriptFunction onBuilder;

    /**
     * JOIN 类型（LEFT, INNER, RIGHT）
     */
    @Builder.Default
    private final JoinType joinType = JoinType.LEFT;

    /**
     * 预计算的 ON 条件（缓存）
     * <p>延迟计算后缓存，避免重复计算</p>
     */
    private String onCondition;

    /**
     * 判断是否有动态 ON 条件
     */
    public boolean hasOnBuilder() {
        return onBuilder != null;
    }

    /**
     * 判断是否已有预计算的 ON 条件
     */
    public boolean hasOnCondition() {
        return onCondition != null;
    }

    /**
     * 获取 JOIN 类型字符串
     */
    public String getJoinTypeString() {
        switch (joinType == null ? JoinType.LEFT : joinType) {
            case RIGHT:
                return " right join ";
            case INNER:
                return " inner join ";
            case LEFT:
            default:
                return " left join ";
        }
    }

    /**
     * 获取边的唯一标识
     * <p>用于去重和检测重复边</p>
     */
    public String getEdgeKey() {
        return from.getAlias() + "->" + to.getAlias();
    }

    @Override
    public String toString() {
        return String.format("JoinEdge{%s -[%s]-> %s}",
                from.getAlias(),
                foreignKey != null ? foreignKey : "onBuilder",
                to.getAlias());
    }
}
