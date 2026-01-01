package com.foggyframework.dataset.mcp.tools;

import com.foggyframework.dataset.db.dialect.FDialect;
import com.foggyframework.dataset.mcp.enums.ToolCategory;
import com.foggyframework.dataset.utils.DbUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.*;
import java.util.*;

/**
 * 表结构检查工具 - 从数据库获取表元数据用于 TM 生成
 *
 * 对应 MCP 工具名: dataset.inspect_table
 *
 * 该工具直接访问数据库元数据，帮助 AI 生成 TM/QM 文件
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnBean(DataSource.class)
public class TableInspectionTool implements McpTool {

    private final DataSource dataSource;
    private final ApplicationContext applicationContext;

    @Override
    public String getName() {
        return "dataset.inspect_table";
    }

    @Override
    public Set<ToolCategory> getCategories() {
        return EnumSet.of(ToolCategory.ADMIN);
    }

    @Override
    public Object execute(Map<String, Object> arguments, String traceId, String authorization) {
        String tableName = (String) arguments.get("table_name");
        String schema = (String) arguments.get("schema");
        String dataSourceName = (String) arguments.get("data_source");
        String databaseType = (String) arguments.get("database_type");
        boolean includeIndexes = Boolean.TRUE.equals(arguments.get("include_indexes"));
        boolean includeForeignKeys = arguments.get("include_foreign_keys") == null
                || Boolean.TRUE.equals(arguments.get("include_foreign_keys"));

        // 默认数据库类型为 jdbc
        if (databaseType == null || databaseType.isEmpty()) {
            databaseType = "jdbc";
        }

        log.info("Inspecting table: {}, schema: {}, dataSource: {}, dbType: {}, traceId={}",
                tableName, schema, dataSourceName, databaseType, traceId);

        // 非 JDBC 类型暂不支持
        if (!"jdbc".equalsIgnoreCase(databaseType)) {
            return Map.of(
                    "error", true,
                    "message", "Unsupported database type: " + databaseType + ". Currently only 'jdbc' is supported.",
                    "supported_types", List.of("jdbc")
            );
        }

        try {
            DataSource targetDataSource = resolveDataSource(dataSourceName);
            return inspectTable(targetDataSource, tableName, schema, includeIndexes, includeForeignKeys);
        } catch (IllegalArgumentException e) {
            log.error("Failed to resolve data source: {}", dataSourceName, e);
            return Map.of(
                    "error", true,
                    "message", "Failed to resolve data source: " + e.getMessage()
            );
        } catch (SQLException e) {
            log.error("Failed to inspect table: {}", tableName, e);
            return Map.of(
                    "error", true,
                    "message", "Failed to inspect table: " + e.getMessage(),
                    "sql_state", e.getSQLState()
            );
        }
    }

    /**
     * 解析数据源
     *
     * @param dataSourceName 数据源 Bean 名称（可为空，为空时使用默认数据源）
     * @return 对应的 DataSource 实例
     */
    private DataSource resolveDataSource(String dataSourceName) {
        if (dataSourceName == null || dataSourceName.isEmpty()) {
            return dataSource;
        }

        try {
            return applicationContext.getBean(dataSourceName, DataSource.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("DataSource not found: " + dataSourceName, e);
        }
    }

    private Map<String, Object> inspectTable(DataSource targetDataSource, String tableName, String schema,
                                              boolean includeIndexes, boolean includeForeignKeys) throws SQLException {
        Map<String, Object> result = new LinkedHashMap<>();

        try (Connection conn = targetDataSource.getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();

            // 确定 schema
            if (schema == null || schema.isEmpty()) {
                schema = conn.getCatalog();
                if (schema == null || schema.isEmpty()) {
                    schema = conn.getSchema();
                }
            }

            String catalog = conn.getCatalog();
            FDialect dialect = detectDialect(meta.getDatabaseProductName());

            result.put("table_name", tableName);
            result.put("schema", schema);
            result.put("catalog", catalog);
            result.put("database_product", meta.getDatabaseProductName());

            // 获取表注释
            try (ResultSet tables = meta.getTables(catalog, schema, tableName, new String[]{"TABLE", "VIEW"})) {
                if (tables.next()) {
                    result.put("table_type", tables.getString("TABLE_TYPE"));
                    result.put("comment", tables.getString("REMARKS"));
                }
            }

            // 获取主键
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

            // 获取外键
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

            // 获取列信息
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

                    // 标记主键和外键
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

                    // 推断角色
                    col.put("suggested_role", suggestRole(colName, jdbcType, typeName, isPk, isFk));
                    String aggregation = suggestAggregation(colName, jdbcType);
                    if (aggregation != null) {
                        col.put("suggested_aggregation", aggregation);
                    }

                    columns.add(col);
                }
            }
            result.put("columns", columns);

            // 获取索引
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

            // 生成 TM 模板预览
            result.put("tm_template", generateTmTemplate(result, modelType));
        }

        return result;
    }

    private FDialect detectDialect(String productName) {
        if (productName == null) return FDialect.MYSQL_DIALECT;
        String lower = productName.toLowerCase();
        if (lower.contains("mysql") || lower.contains("mariadb")) {
            return FDialect.MYSQL_DIALECT;
        } else if (lower.contains("postgresql")) {
            return FDialect.POSTGRES_DIALECT;
        } else if (lower.contains("microsoft") || lower.contains("sql server")) {
            return FDialect.SQLSERVER_DIALECT;
        } else if (lower.contains("sqlite")) {
            return FDialect.SQLITE_DIALECT;
        }
        return FDialect.MYSQL_DIALECT;
    }

    private String mapToTmType(int jdbcType, String typeName, String columnName) {
        // 基于列名的特殊推断
        String lowerName = columnName.toLowerCase();
        if (lowerName.contains("amount") || lowerName.contains("price") ||
                lowerName.contains("cost") || lowerName.contains("total") ||
                lowerName.contains("money") || lowerName.contains("fee")) {
            return "MONEY";
        }

        // 基于 JDBC 类型映射
        switch (jdbcType) {
            case Types.BIGINT:
                return "BIGINT";
            case Types.INTEGER:
            case Types.SMALLINT:
            case Types.TINYINT:
                return "INTEGER";
            case Types.DECIMAL:
            case Types.NUMERIC:
                return "MONEY";
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
                return "DATETIME";
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

        if (isFk) {
            return "dimension";
        }
        if (isPk) {
            return "property";
        }

        // 度量判断
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
        if (lower.contains("qty") || lower.contains("quantity")) {
            return "sum";
        }
        if (lower.contains("count")) {
            return "sum";
        }
        if (lower.contains("rate") || lower.contains("ratio") || lower.contains("avg") || lower.contains("average")) {
            return "avg";
        }

        return null;
    }

    private String suggestDimensionName(String fkColumn, String refTable) {
        String lower = fkColumn.toLowerCase();

        // 从列名推断
        if (lower.endsWith("_key") || lower.endsWith("_id")) {
            String base = lower.substring(0, lower.lastIndexOf('_'));
            // 处理常见前缀，如 date_key -> salesDate
            if (base.equals("date")) {
                return "salesDate";
            }
            return toCamelCase(base);
        }

        // 从表名推断
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

        // 基于表名前缀
        if (lower.startsWith("fact_") || lower.startsWith("fct_")) {
            return "fact";
        }
        if (lower.startsWith("dim_") || lower.startsWith("dimension_")) {
            return "dimension";
        }

        // 基于外键数量和度量数量
        long measureCount = columns.stream()
                .filter(c -> "measure".equals(c.get("suggested_role")))
                .count();

        if (fkCount >= 2 && measureCount >= 1) {
            return "fact";
        }
        if (fkCount == 0 && measureCount == 0) {
            return "dimension";
        }

        return "fact"; // 默认事实表
    }

    private String suggestModelName(String tableName, String modelType) {
        String base = tableName;
        String lower = tableName.toLowerCase();

        // 移除前缀
        if (lower.startsWith("fact_") || lower.startsWith("fct_")) {
            base = tableName.substring(tableName.indexOf('_') + 1);
        } else if (lower.startsWith("dim_")) {
            base = tableName.substring(4);
        }

        // 转 PascalCase
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

        // 检查是否有通用维度可复用
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

        // 主键
        Map<String, Object> pk = (Map<String, Object>) tableInfo.get("primary_key");
        if (pk != null && pk.get("columns") != null) {
            List<String> pkCols = (List<String>) pk.get("columns");
            if (!pkCols.isEmpty()) {
                sb.append("    idColumn: '").append(pkCols.get(0)).append("',\n");
            }
        }

        // Dimensions
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

        // Properties
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

        // Measures
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
}
