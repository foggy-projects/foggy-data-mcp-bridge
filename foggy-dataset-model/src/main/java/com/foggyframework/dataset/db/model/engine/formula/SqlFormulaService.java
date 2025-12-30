package com.foggyframework.dataset.db.model.engine.formula;

import com.foggyframework.dataset.db.model.engine.query.JdbcQuery;
import com.foggyframework.dataset.db.model.spi.DbColumn;

public interface SqlFormulaService {
    void buildAndAddToJdbcCond(JdbcQuery.JdbcListCond listCond, String type, DbColumn sqlColumn, String alias, Object value, int link);

    /**
     * 检查是否支持指定的操作符
     *
     * @param operator 操作符名称（如 "=", "in", "like" 等）
     * @return true 如果支持该操作符，否则返回 false
     */
    boolean supports(String operator);
}
