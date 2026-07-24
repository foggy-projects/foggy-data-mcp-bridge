package com.foggyframework.dataset.model.engine.compose;

import lombok.Getter;

import java.util.List;

/**
 * CTE 单元 -- 一个 QM 生成的 SQL 片段
 *
 * <p>对应一个 {@code WITH alias AS (sql)} 子句，或子查询 {@code (sql) AS alias}。</p>
 *
 * @author Foggy Framework
 * @since 8.2.0
 */
@Getter
public class CteUnit {

    /**
     * CTE 别名（如 cte_0, cte_1）
     */
    private final String alias;

    /**
     * QM 生成的完整 SQL
     */
    private final String sql;

    /**
     * SQL 绑定参数
     */
    private final List<Object> params;

    /**
     * 外层 SELECT 需要引用的列名列表（可选）。
     *
     * <p><b>Cross-repo drift note（8.2.0.beta）</b>：Python 端
     * {@code CteComposer.compose(units, join_specs, use_cte, select_columns=...)}
     * 在 compose 顶层接收一份"先 join 再统一裁列"的列表；Java 没有顶层 {@code selectColumns}
     * 参数，能力被下沉到本字段——每个 unit 自带一份外层 SELECT 列，组合时按
     * {@code unit.alias.col} 逐一展开。</p>
     *
     * <p>所以这是 Java 侧"外层 SELECT 重写"的<b>唯一</b>入口；想表达 Python 顶层
     * {@code select_columns} 的语义，必须把列分配到对应 unit 上，<b>或</b>走
     * {@link com.foggyframework.dataset.model.engine.compose.compilation.ComposePlanner}
     * 的 M6 SQL assembly 自管路径（join 场景必走）。</p>
     */
    private final List<String> selectColumns;

    public CteUnit(String alias, String sql, List<Object> params, List<String> selectColumns) {
        this.alias = alias;
        this.sql = sql;
        this.params = params;
        this.selectColumns = selectColumns;
    }
}
