package com.foggyframework.dataset.db.model.engine.compose;

import com.foggyframework.dataset.db.model.engine.JdbcModelQueryEngine;
import lombok.Getter;

import java.util.List;

/**
 * SQL 生成结果 -- 仅截取 SQL 不执行
 *
 * <p>用于 CTE/子查询组合场景：将 QM 生成的完整 SQL（含 TM JOIN + 权限 slice）
 * 视为黑盒"视图"，供 {@link CteComposer} 拼接。</p>
 *
 * @author Foggy Framework
 * @since 8.2.0
 */
@Getter
public class SqlGenerationResult {

    /**
     * 生成的完整 SQL（不含分页）
     */
    private final String sql;

    /**
     * SQL 绑定参数（按占位符顺序）
     */
    private final List<Object> params;

    /**
     * 查询引擎实例（保留以便后续提取列信息等元数据）
     */
    private final JdbcModelQueryEngine queryEngine;

    public SqlGenerationResult(String sql, List<Object> params, JdbcModelQueryEngine queryEngine) {
        this.sql = sql;
        this.params = params;
        this.queryEngine = queryEngine;
    }
}
