package com.foggyframework.dataset.db.model.engine.compose;

import lombok.Getter;

import java.util.List;

/**
 * JOIN 规格 -- 描述两个 CTE 单元之间的 JOIN 关系
 *
 * @author Foggy Framework
 * @since 8.2.0
 */
@Getter
public class JoinSpec {

    /**
     * JOIN 类型（LEFT, INNER, RIGHT）
     */
    private final String joinType;

    /**
     * JOIN key -- 左侧 CTE 的列名
     */
    private final String leftKey;

    /**
     * JOIN key -- 右侧 CTE 的列名
     */
    private final String rightKey;

    /**
     * 左侧 CTE 需要 SELECT 的列
     */
    private final List<String> leftColumns;

    /**
     * 右侧 CTE 需要 SELECT 的列
     */
    private final List<String> rightColumns;

    public JoinSpec(String joinType, String leftKey, String rightKey,
                    List<String> leftColumns, List<String> rightColumns) {
        this.joinType = joinType;
        this.leftKey = leftKey;
        this.rightKey = rightKey;
        this.leftColumns = leftColumns;
        this.rightColumns = rightColumns;
    }

    /**
     * 创建 LEFT JOIN 规格的便捷方法
     */
    public static JoinSpec leftJoin(String leftKey, String rightKey,
                                    List<String> leftColumns, List<String> rightColumns) {
        return new JoinSpec("LEFT", leftKey, rightKey, leftColumns, rightColumns);
    }

    /**
     * 创建 INNER JOIN 规格的便捷方法
     */
    public static JoinSpec innerJoin(String leftKey, String rightKey,
                                     List<String> leftColumns, List<String> rightColumns) {
        return new JoinSpec("INNER", leftKey, rightKey, leftColumns, rightColumns);
    }
}
