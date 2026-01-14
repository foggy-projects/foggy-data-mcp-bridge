package com.foggyframework.dataset.mcp.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.sql.DataSource;
import java.sql.*;
import java.util.*;

/**
 * 开发者工具控制器 - 提供表结构查询 API
 * <p>
 * 用于本地开发时快速获取数据库表结构，辅助 TM/QM 文件编写。
 * 默认启用，可通过配置 foggy.dev-tools.enabled=false 禁用。
 * </p>
 *
 * <h3>API 端点:</h3>
 * <ul>
 *   <li>GET /dev/tables - 列出所有表</li>
 *   <li>GET /dev/tables/{tableName} - 获取表详细结构</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/dev")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "foggy.dev-tools.enabled", havingValue = "true", matchIfMissing = true)
public class DevToolsController {

    private final DataSource dataSource;
    private final ApplicationContext applicationContext;

    /**
     * 列出所有表
     *
     * @param schema      数据库 schema（可选，默认使用当前连接的 schema）
     * @param dataSourceName 数据源 Bean 名称（可选，默认 defaultDataSource）
     * @return 表列表
     */
    @GetMapping("/tables")
    public ResponseEntity<Map<String, Object>> listTables(
            @RequestParam(required = false) String schema,
            @RequestParam(required = false, name = "datasource", defaultValue = "defaultDataSource") String dataSourceName) {

        try {
            DataSource targetDataSource = resolveDataSource(dataSourceName);

            try (Connection conn = targetDataSource.getConnection()) {
                DatabaseMetaData meta = conn.getMetaData();

                if (schema == null || schema.isEmpty()) {
                    schema = conn.getCatalog();
                    if (schema == null || schema.isEmpty()) {
                        schema = conn.getSchema();
                    }
                }

                String catalog = conn.getCatalog();
                List<Map<String, Object>> tables = new ArrayList<>();

                try (ResultSet rs = meta.getTables(catalog, schema, null, new String[]{"TABLE", "VIEW"})) {
                    while (rs.next()) {
                        Map<String, Object> table = new LinkedHashMap<>();
                        table.put("name", rs.getString("TABLE_NAME"));
                        table.put("type", rs.getString("TABLE_TYPE"));
                        String remarks = rs.getString("REMARKS");
                        if (remarks != null && !remarks.isEmpty()) {
                            table.put("comment", remarks);
                        }
                        tables.add(table);
                    }
                }

                Map<String, Object> result = new LinkedHashMap<>();
                result.put("database", meta.getDatabaseProductName());
                result.put("schema", schema);
                result.put("catalog", catalog);
                result.put("count", tables.size());
                result.put("tables", tables);

                return ResponseEntity.ok(result);
            }
        } catch (SQLException e) {
            log.error("Failed to list tables", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", true,
                    "message", "Failed to list tables: " + e.getMessage()
            ));
        }
    }

    /**
     * 获取表详细结构（用于 TM 文件生成）
     *
     * @param tableName       表名
     * @param schema          数据库 schema（可选）
     * @param dataSourceName  数据源 Bean 名称（可选，默认 defaultDataSource）
     * @param includeIndexes  是否包含索引信息（默认 false）
     * @param includeForeignKeys 是否包含外键信息（默认 true）
     * @param includeSampleData 是否包含一条示例数据（默认 true）
     * @return 表结构详情，包含 TM 生成建议和示例数据
     */
    @GetMapping("/tables/{tableName}")
    public ResponseEntity<Map<String, Object>> inspectTable(
            @PathVariable String tableName,
            @RequestParam(required = false) String schema,
            @RequestParam(required = false, name = "datasource", defaultValue = "defaultDataSource") String dataSourceName,
            @RequestParam(required = false, defaultValue = "false") boolean includeIndexes,
            @RequestParam(required = false, defaultValue = "true") boolean includeForeignKeys,
            @RequestParam(required = false, defaultValue = "true") boolean includeSampleData) {

        log.info("Inspecting table: {}, schema: {}", tableName, schema);

        try {
            DataSource targetDataSource = resolveDataSource(dataSourceName);
            Map<String, Object> result = inspectTableInternal(
                    targetDataSource, tableName, schema, includeIndexes, includeForeignKeys, includeSampleData);
            return ResponseEntity.ok(result);
        } catch (SQLException e) {
            log.error("Failed to inspect table: {}", tableName, e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", true,
                    "message", "Failed to inspect table: " + e.getMessage(),
                    "sql_state", e.getSQLState()
            ));
        }
    }

    /**
     * 通过 Bean 名称解析数据源
     *
     * @param dataSourceName 数据源 Bean 名称
     * @return DataSource 实例
     */
    private DataSource resolveDataSource(String dataSourceName) {
        try {
            return applicationContext.getBean(dataSourceName, DataSource.class);
        } catch (Exception e) {
            // fallback 到注入的主数据源
            log.warn("DataSource '{}' not found, fallback to primary dataSource", dataSourceName);
            return dataSource;
        }
    }

    private Map<String, Object> inspectTableInternal(DataSource targetDataSource, String tableName,
                                                      String schema, boolean includeIndexes,
                                                      boolean includeForeignKeys, boolean includeSampleData) throws SQLException {
        Map<String, Object> result = new LinkedHashMap<>();

        try (Connection conn = targetDataSource.getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();

            if (schema == null || schema.isEmpty()) {
                schema = conn.getCatalog();
                if (schema == null || schema.isEmpty()) {
                    schema = conn.getSchema();
                }
            }

            String catalog = conn.getCatalog();

            result.put("table_name", tableName);
            result.put("schema", schema);
            result.put("catalog", catalog);
            result.put("database_product", meta.getDatabaseProductName());

            // 表注释
            try (ResultSet tables = meta.getTables(catalog, schema, tableName, new String[]{"TABLE", "VIEW"})) {
                if (tables.next()) {
                    result.put("table_type", tables.getString("TABLE_TYPE"));
                    String remarks = tables.getString("REMARKS");
                    if (remarks != null && !remarks.isEmpty()) {
                        result.put("comment", remarks);
                    }
                }
            }

            // 主键
            Set<String> primaryKeys = new HashSet<>();
            Map<String, Object> pkInfo = new LinkedHashMap<>();
            try (ResultSet pk = meta.getPrimaryKeys(catalog, schema, tableName)) {
                List<String> pkColumns = new ArrayList<>();
                while (pk.next()) {
                    String colName = pk.getString("COLUMN_NAME");
                    pkColumns.add(colName);
                    primaryKeys.add(colName.toLowerCase());
                    if (pkInfo.isEmpty()) {
                        pkInfo.put("name", pk.getString("PK_NAME"));
                    }
                }
                pkInfo.put("columns", pkColumns);
            }
            result.put("primary_key", pkInfo);

            // 外键
            Map<String, Map<String, Object>> foreignKeys = new LinkedHashMap<>();
            if (includeForeignKeys) {
                try (ResultSet fk = meta.getImportedKeys(catalog, schema, tableName)) {
                    while (fk.next()) {
                        String fkColumn = fk.getString("FKCOLUMN_NAME");
                        Map<String, Object> fkInfo = new LinkedHashMap<>();
                        fkInfo.put("name", fk.getString("FK_NAME"));
                        fkInfo.put("column", fkColumn);
                        fkInfo.put("references_table", fk.getString("PKTABLE_NAME"));
                        fkInfo.put("references_column", fk.getString("PKCOLUMN_NAME"));
                        fkInfo.put("suggested_dimension_name", suggestDimensionName(fkColumn, fk.getString("PKTABLE_NAME")));
                        foreignKeys.put(fkColumn.toLowerCase(), fkInfo);
                    }
                }
                result.put("foreign_keys", new ArrayList<>(foreignKeys.values()));
            }

            // 列信息
            List<Map<String, Object>> columns = new ArrayList<>();
            try (ResultSet cols = meta.getColumns(catalog, schema, tableName, null)) {
                while (cols.next()) {
                    Map<String, Object> col = new LinkedHashMap<>();
                    String colName = cols.getString("COLUMN_NAME");
                    int jdbcType = cols.getInt("DATA_TYPE");
                    String typeName = cols.getString("TYPE_NAME");
                    int size = cols.getInt("COLUMN_SIZE");
                    int scale = cols.getInt("DECIMAL_DIGITS");
                    boolean nullable = cols.getInt("NULLABLE") == DatabaseMetaData.columnNullable;
                    String defaultValue = cols.getString("COLUMN_DEF");
                    String remarks = cols.getString("REMARKS");
                    String autoIncrement = cols.getString("IS_AUTOINCREMENT");

                    col.put("name", colName);
                    col.put("sql_type", typeName);
                    col.put("jdbc_type", jdbcType);
                    col.put("tm_type", mapToTmType(jdbcType, typeName, colName));
                    col.put("length", size);
                    if (scale > 0) {
                        col.put("precision", size);
                        col.put("scale", scale);
                    }
                    col.put("nullable", nullable);
                    col.put("auto_increment", "YES".equals(autoIncrement));
                    if (defaultValue != null) {
                        col.put("default_value", defaultValue);
                    }
                    if (remarks != null && !remarks.isEmpty()) {
                        col.put("comment", remarks);
                    }

                    boolean isPk = primaryKeys.contains(colName.toLowerCase());
                    boolean isFk = foreignKeys.containsKey(colName.toLowerCase());
                    col.put("is_primary_key", isPk);
                    col.put("is_foreign_key", isFk);

                    if (isFk) {
                        col.put("references", Map.of(
                                "table", foreignKeys.get(colName.toLowerCase()).get("references_table"),
                                "column", foreignKeys.get(colName.toLowerCase()).get("references_column")
                        ));
                    }

                    col.put("suggested_role", suggestRole(colName, jdbcType, typeName, isPk, isFk));
                    String aggregation = suggestAggregation(colName, jdbcType);
                    if (aggregation != null) {
                        col.put("suggested_aggregation", aggregation);
                    }

                    columns.add(col);
                }
            }
            result.put("columns", columns);

            // 索引
            if (includeIndexes) {
                List<Map<String, Object>> indexes = new ArrayList<>();
                try (ResultSet idx = meta.getIndexInfo(catalog, schema, tableName, false, false)) {
                    Map<String, List<String>> indexColumns = new LinkedHashMap<>();
                    Map<String, Boolean> indexUnique = new HashMap<>();
                    while (idx.next()) {
                        String idxName = idx.getString("INDEX_NAME");
                        if (idxName == null) continue;
                        String colName = idx.getString("COLUMN_NAME");
                        boolean unique = !idx.getBoolean("NON_UNIQUE");

                        indexColumns.computeIfAbsent(idxName, k -> new ArrayList<>()).add(colName);
                        indexUnique.put(idxName, unique);
                    }
                    for (Map.Entry<String, List<String>> entry : indexColumns.entrySet()) {
                        indexes.add(Map.of(
                                "name", entry.getKey(),
                                "columns", entry.getValue(),
                                "unique", indexUnique.get(entry.getKey())
                        ));
                    }
                }
                result.put("indexes", indexes);
            }

            // 推断模型类型和名称
            String modelType = inferModelType(tableName, foreignKeys.size(), columns);
            result.put("suggested_model_type", modelType);
            result.put("suggested_model_name", suggestModelName(tableName, modelType));

            // 读取一条示例数据
            if (includeSampleData) {
                try {
                    Map<String, Object> sampleData = fetchSampleData(conn, tableName, schema, columns);
                    result.put("sample_data", sampleData);
                } catch (SQLException e) {
                    log.warn("Failed to fetch sample data for table: {}, error: {}", tableName, e.getMessage());
                    result.put("sample_data_error", "Failed to fetch sample data: " + e.getMessage());
                }
            }

            // 生成 TM 模板预览
            result.put("tm_template", generateTmTemplate(result, modelType));
        }

        return result;
    }

    private String mapToTmType(int jdbcType, String typeName, String columnName) {
        String lowerName = columnName.toLowerCase();
        if (lowerName.contains("amount") || lowerName.contains("price") ||
                lowerName.contains("cost") || lowerName.contains("total") ||
                lowerName.contains("money") || lowerName.contains("fee")) {
            return "MONEY";
        }

        switch (jdbcType) {
            case Types.BIGINT:
                return "BIGINT";
            case Types.INTEGER:
            case Types.SMALLINT:
            case Types.TINYINT:
                return "INTEGER";
            case Types.DECIMAL:
            case Types.NUMERIC:
            case Types.FLOAT:
            case Types.DOUBLE:
            case Types.REAL:
                return "MONEY";
            case Types.VARCHAR:
            case Types.CHAR:
            case Types.LONGVARCHAR:
            case Types.NVARCHAR:
            case Types.NCHAR:
            case Types.CLOB:
                return "STRING";
            case Types.DATE:
                return "DAY";
            case Types.TIMESTAMP:
            case Types.TIMESTAMP_WITH_TIMEZONE:
            case Types.TIME:
            case Types.TIME_WITH_TIMEZONE:
                return "DATETIME";
            case Types.BOOLEAN:
            case Types.BIT:
                return "BOOL";
            default:
                return "STRING";
        }
    }

    private String suggestRole(String colName, int jdbcType, String typeName, boolean isPk, boolean isFk) {
        String lower = colName.toLowerCase();

        if (isFk) return "dimension";
        if (isPk) return "property";

        if (lower.contains("amount") || lower.contains("price") || lower.contains("cost") ||
                lower.contains("total") || lower.contains("sum") || lower.contains("fee")) {
            return "measure";
        }
        if ((lower.contains("qty") || lower.contains("quantity") || lower.contains("count")) &&
                (jdbcType == Types.INTEGER || jdbcType == Types.BIGINT || jdbcType == Types.SMALLINT)) {
            return "measure";
        }

        return "property";
    }

    private String suggestAggregation(String colName, int jdbcType) {
        String lower = colName.toLowerCase();

        if (lower.contains("amount") || lower.contains("total") || lower.contains("sum") ||
                lower.contains("cost") || lower.contains("fee") || lower.contains("price")) {
            return "sum";
        }
        if (lower.contains("qty") || lower.contains("quantity") || lower.contains("count")) {
            return "sum";
        }
        if (lower.contains("rate") || lower.contains("ratio") || lower.contains("avg") || lower.contains("average")) {
            return "avg";
        }

        return null;
    }

    private String suggestDimensionName(String fkColumn, String refTable) {
        String lower = fkColumn.toLowerCase();

        if (lower.endsWith("_key") || lower.endsWith("_id")) {
            String base = lower.substring(0, lower.lastIndexOf('_'));
            if (base.equals("date")) {
                return "salesDate";
            }
            return toCamelCase(base);
        }

        String tableLower = refTable.toLowerCase();
        if (tableLower.startsWith("dim_")) {
            return toCamelCase(tableLower.substring(4));
        }

        return toCamelCase(refTable);
    }

    private String toCamelCase(String snakeCase) {
        StringBuilder result = new StringBuilder();
        boolean capitalizeNext = false;
        for (char c : snakeCase.toCharArray()) {
            if (c == '_') {
                capitalizeNext = true;
            } else if (capitalizeNext) {
                result.append(Character.toUpperCase(c));
                capitalizeNext = false;
            } else {
                result.append(Character.toLowerCase(c));
            }
        }
        return result.toString();
    }

    private String inferModelType(String tableName, int fkCount, List<Map<String, Object>> columns) {
        String lower = tableName.toLowerCase();

        if (lower.startsWith("fact_") || lower.startsWith("fct_")) {
            return "fact";
        }
        if (lower.startsWith("dim_") || lower.startsWith("dimension_")) {
            return "dimension";
        }

        long measureCount = columns.stream()
                .filter(c -> "measure".equals(c.get("suggested_role")))
                .count();

        if (fkCount >= 2 && measureCount >= 1) {
            return "fact";
        }
        if (fkCount == 0 && measureCount == 0) {
            return "dimension";
        }

        return "fact";
    }

    private String suggestModelName(String tableName, String modelType) {
        String base = tableName;
        String lower = tableName.toLowerCase();

        if (lower.startsWith("fact_") || lower.startsWith("fct_")) {
            base = tableName.substring(tableName.indexOf('_') + 1);
        } else if (lower.startsWith("dim_")) {
            base = tableName.substring(4);
        }

        StringBuilder pascal = new StringBuilder();
        boolean capitalizeNext = true;
        for (char c : base.toCharArray()) {
            if (c == '_') {
                capitalizeNext = true;
            } else if (capitalizeNext) {
                pascal.append(Character.toUpperCase(c));
                capitalizeNext = false;
            } else {
                pascal.append(c);
            }
        }

        String prefix = "fact".equals(modelType) ? "Fact" : "Dim";
        return prefix + pascal + "Model";
    }

    @SuppressWarnings("unchecked")
    private String generateTmTemplate(Map<String, Object> tableInfo, String modelType) {
        StringBuilder sb = new StringBuilder();
        String modelName = (String) tableInfo.get("suggested_model_name");
        String tableName = (String) tableInfo.get("table_name");
        List<Map<String, Object>> columns = (List<Map<String, Object>>) tableInfo.get("columns");
        List<Map<String, Object>> foreignKeys = (List<Map<String, Object>>) tableInfo.getOrDefault("foreign_keys", List.of());

        sb.append("/**\n");
        sb.append(" * ").append(modelName).append("\n");
        sb.append(" * @description Auto-generated from database table: ").append(tableName).append("\n");
        sb.append(" */\n\n");

        boolean hasDateDim = foreignKeys.stream().anyMatch(fk ->
                ((String) fk.get("references_table")).toLowerCase().contains("date"));
        boolean hasCustomerDim = foreignKeys.stream().anyMatch(fk ->
                ((String) fk.get("references_table")).toLowerCase().contains("customer"));

        if (hasDateDim || hasCustomerDim) {
            sb.append("// Consider using dimension builders for reuse:\n");
            sb.append("// import { buildDateDim, buildCustomerDim } from '../dimensions/common-dims.fsscript';\n\n");
        }

        sb.append("export const model = {\n");
        sb.append("    name: '").append(modelName).append("',\n");
        sb.append("    caption: '").append(tableName).append("',\n");
        sb.append("    tableName: '").append(tableName).append("',\n");

        Map<String, Object> pk = (Map<String, Object>) tableInfo.get("primary_key");
        if (pk != null && pk.get("columns") != null) {
            List<String> pkCols = (List<String>) pk.get("columns");
            if (!pkCols.isEmpty()) {
                sb.append("    idColumn: '").append(pkCols.get(0)).append("',\n");
            }
        }

        sb.append("\n    dimensions: [\n");
        for (Map<String, Object> fk : foreignKeys) {
            sb.append("        {\n");
            sb.append("            name: '").append(fk.get("suggested_dimension_name")).append("',\n");
            sb.append("            tableName: '").append(fk.get("references_table")).append("',\n");
            sb.append("            foreignKey: '").append(fk.get("column")).append("',\n");
            sb.append("            primaryKey: '").append(fk.get("references_column")).append("',\n");
            sb.append("            caption: '").append(fk.get("suggested_dimension_name")).append("',\n");
            sb.append("            properties: [\n");
            sb.append("                // TODO: Add dimension properties\n");
            sb.append("            ]\n");
            sb.append("        },\n");
        }
        sb.append("    ],\n");

        sb.append("\n    properties: [\n");
        for (Map<String, Object> col : columns) {
            if ("property".equals(col.get("suggested_role"))) {
                sb.append("        {\n");
                sb.append("            column: '").append(col.get("name")).append("',\n");
                sb.append("            caption: '").append(col.get("name")).append("',\n");
                sb.append("            type: '").append(col.get("tm_type")).append("'\n");
                sb.append("        },\n");
            }
        }
        sb.append("    ],\n");

        sb.append("\n    measures: [\n");
        for (Map<String, Object> col : columns) {
            if ("measure".equals(col.get("suggested_role"))) {
                sb.append("        {\n");
                sb.append("            column: '").append(col.get("name")).append("',\n");
                sb.append("            caption: '").append(col.get("name")).append("',\n");
                sb.append("            type: '").append(col.get("tm_type")).append("',\n");
                String agg = (String) col.get("suggested_aggregation");
                sb.append("            aggregation: '").append(agg != null ? agg : "sum").append("'\n");
                sb.append("        },\n");
            }
        }
        sb.append("    ]\n");

        sb.append("};\n");

        return sb.toString();
    }

    /**
     * 读取表的一条示例数据
     *
     * @param conn    数据库连接
     * @param tableName 表名
     * @param schema  数据库 schema
     * @param columns 表的列信息
     * @return 包含一条示例数据的 Map，key 为列名，value 为数据值
     * @throws SQLException 如果查询失败
     */
    private Map<String, Object> fetchSampleData(Connection conn, String tableName, String schema,
                                                List<Map<String, Object>> columns) throws SQLException {
        Map<String, Object> sampleData = new LinkedHashMap<>();
        
        // 构建查询 SQL，使用 LIMIT 1 确保只返回一条记录
        String sql = buildSelectQuery(tableName, schema, columns);
        
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    // 遍历所有列，获取数据值
                    for (Map<String, Object> column : columns) {
                        String columnName = (String) column.get("name");
                        try {
                            Object value = rs.getObject(columnName);
                            
                            // 处理特殊类型的数据
                            if (value != null) {
                                int jdbcType = (int) column.get("jdbc_type");
                                String sqlType = (String) column.get("sql_type");
                                value = formatValueForDisplay(value, jdbcType, sqlType);
                            }
                            
                            sampleData.put(columnName, value);
                        } catch (SQLException e) {
                            // 如果某列无法读取，记录错误但继续处理其他列
                            log.debug("Failed to read column {}: {}", columnName, e.getMessage());
                            sampleData.put(columnName, "[读取失败: " + e.getMessage() + "]");
                        }
                    }
                } else {
                    // 表为空
                    sampleData.put("message", "表为空，无数据");
                }
            }
        }
        
        return sampleData;
    }

    /**
     * 构建 SELECT 查询语句
     */
    private String buildSelectQuery(String tableName, String schema, List<Map<String, Object>> columns) {
        StringBuilder sql = new StringBuilder("SELECT ");
        
        // 构建列名列表
        List<String> columnNames = new ArrayList<>();
        for (Map<String, Object> column : columns) {
            String columnName = (String) column.get("name");
            columnNames.add(escapeColumnName(columnName));
        }
        sql.append(String.join(", ", columnNames));
        
        // 构建表名（包含 schema）
        sql.append(" FROM ");
        if (schema != null && !schema.isEmpty()) {
            sql.append(escapeIdentifier(schema)).append(".");
        }
        sql.append(escapeIdentifier(tableName));
        
        // 添加 LIMIT 1 限制
        sql.append(" LIMIT 1");
        
        return sql.toString();
    }

    /**
     * 转义列名（处理包含特殊字符的列名）
     */
    private String escapeColumnName(String columnName) {
        // 如果列名包含空格、特殊字符或关键字，需要转义
        if (columnName.matches(".*[ \\-\\+\\*\\/\\=\\(\\)\\[\\]{}<>!@#$%^&|`~].*")) {
            return "\"" + columnName.replace("\"", "\"\"") + "\"";
        }
        return columnName;
    }

    /**
     * 转义标识符（表名、schema名）
     */
    private String escapeIdentifier(String identifier) {
        // 简单的转义逻辑，可以根据具体数据库调整
        if (identifier.matches(".*[ \\-\\+\\*\\/\\=\\(\\)\\[\\]{}<>!@#$%^&|`~].*")) {
            return "\"" + identifier.replace("\"", "\"\"") + "\"";
        }
        return identifier;
    }

    /**
     * 格式化数据值以便显示
     */
    private Object formatValueForDisplay(Object value, int jdbcType, String sqlType) {
        if (value == null) {
            return null;
        }
        
        // 处理大文本字段，截断过长的内容
        if (value instanceof String) {
            String strValue = (String) value;
            if (strValue.length() > 100) {
                return strValue.substring(0, 100) + "... [截断，原长: " + strValue.length() + " 字符]";
            }
        }
        
        // 处理二进制数据
        if (value instanceof byte[]) {
            byte[] bytes = (byte[]) value;
            if (bytes.length > 50) {
                return "[二进制数据，长度: " + bytes.length + " 字节]";
            } else {
                return "[二进制数据: " + bytes.length + " 字节]";
            }
        }
        
        // 处理日期时间类型
        if (value instanceof java.sql.Date || value instanceof java.sql.Timestamp || 
            value instanceof java.sql.Time) {
            return value.toString();
        }
        
        return value;
    }
}
