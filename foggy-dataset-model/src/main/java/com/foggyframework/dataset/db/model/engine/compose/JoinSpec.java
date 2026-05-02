package com.foggyframework.dataset.db.model.engine.compose;

import lombok.Getter;

import java.util.List;

/**
 * JOIN 规格 -- 描述两个 CTE 单元之间的 JOIN 关系。
 *
 * <h3>Cross-repo drift vs. Python（8.2.0.beta · MINOR DRIFT, intentional）</h3>
 *
 * <p>本类只表达<b>单列等值 JOIN</b>：{@code leftAlias.leftKey = rightAlias.rightKey}。
 * Python 端 {@code foggy.dataset_model.engine.compose.cte_composer.JoinSpec}
 * 同时携带一个 raw {@code on_condition} 字符串，可以直接渲染任意 ON 子句
 * （多列 / 不等值 / 函数表达式等）。Java 这边能力更窄。</p>
 *
 * <p>因此凡是需要"非单列等值"的 JOIN 场景，就<b>不能</b>用 {@link CteComposer}，必须改走
 * {@link com.foggyframework.dataset.db.model.engine.compose.compilation.ComposePlanner}
 * 的 M6 SQL assembly 路径——那里手工组装 SELECT / JOIN，不依赖本类的 ON 表达力。
 * M6 的 {@code SQL assembly helpers} 段注释里明确记录了这一旁路。</p>
 *
 * <p>未来若要把 API 完全对齐 Python，需要在本类增补 raw {@code onCondition} 字段，
 * 同时把 {@link CteComposer#appendJoinClause} 升级为"raw 优先 / equi-join 兜底"的两态实现。</p>
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
