package com.foggyframework.dataset.model.preagg.ddl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 参数化 SQL
 * <p>
 * 包含 SQL 语句和对应的参数列表，防止 SQL 注入。
 * </p>
 *
 * @author foggy-framework
 * @since 8.2.0
 */
public class ParameterizedSql {

    private final String sql;
    private final List<Object> params;

    public ParameterizedSql(String sql, List<Object> params) {
        this.sql = sql;
        this.params = params != null ? new ArrayList<>(params) : Collections.emptyList();
    }

    public ParameterizedSql(String sql) {
        this(sql, Collections.emptyList());
    }

    public String getSql() {
        return sql;
    }

    public List<Object> getParams() {
        return params;
    }

    public Object[] getParamsArray() {
        return params.toArray();
    }

    @Override
    public String toString() {
        return "ParameterizedSql{sql='" + sql + "', params=" + params + '}';
    }
}
