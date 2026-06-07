package com.foggyframework.runtime.api.dto;

import java.util.List;

public record TableInspectResponse(
        String dataSource,
        String schema,
        String table,
        String tableType,
        List<TableColumnInfo> columns,
        TablePrimaryKeyInfo primaryKey,
        List<TableForeignKeyInfo> foreignKeys,
        List<TableIndexInfo> indexes
) {
}
