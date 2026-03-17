package com.foggyframework.dataset.db.model.engine.compose;

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
     * 外层 SELECT 需要引用的列名列表（可选）
     */
    private final List<String> selectColumns;

    public CteUnit(String alias, String sql, List<Object> params, List<String> selectColumns) {
        this.alias = alias;
        this.sql = sql;
        this.params = params;
        this.selectColumns = selectColumns;
    }
}
