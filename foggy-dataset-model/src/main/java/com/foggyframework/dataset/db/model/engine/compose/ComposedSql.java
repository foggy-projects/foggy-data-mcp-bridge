package com.foggyframework.dataset.db.model.engine.compose;

import lombok.Getter;

import java.util.List;

/**
 * 组合 SQL 结果 -- CteComposer 拼接后的最终 SQL
 *
 * @author Foggy Framework
 * @since 8.2.0
 */
@Getter
public class ComposedSql {

    /**
     * 最终拼接的 SQL（CTE 或子查询格式）
     */
    private final String sql;

    /**
     * 合并后的绑定参数（按 CTE 单元顺序拼接）
     */
    private final List<Object> params;

    public ComposedSql(String sql, List<Object> params) {
        this.sql = sql;
        this.params = params;
    }
}
