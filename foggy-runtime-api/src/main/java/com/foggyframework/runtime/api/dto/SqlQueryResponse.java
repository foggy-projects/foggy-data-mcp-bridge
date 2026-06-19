package com.foggyframework.runtime.api.dto;

import java.util.List;
import java.util.Map;

public record SqlQueryResponse(
        String dataSource,
        String sql,
        Integer maxRows,
        Boolean truncated,
        List<SqlColumnInfo> columns,
        List<Map<String, Object>> rows,
        Integer rowCount,
        List<String> warnings
) {
}
