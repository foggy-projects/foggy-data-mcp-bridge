package com.foggyframework.benchmark.spider2.loader;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foggyframework.benchmark.spider2.config.Spider2DataSourceConfig;
import com.foggyframework.benchmark.spider2.config.Spider2Properties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Spider2 数据库结构探测器
 *
 * 探测 SQLite 数据库结构，结合 Spider2 元数据生成 JM/QM 模型
 */
@Slf4j
@Component
public class Spider2DatabaseInspector {

    private final Spider2DataSourceConfig dataSourceConfig;
    private final Spider2Properties properties;
    private final ObjectMapper objectMapper;

    public Spider2DatabaseInspector(Spider2DataSourceConfig dataSourceConfig,
                                    Spider2Properties properties,
                                    ObjectMapper objectMapper) {
        this.dataSourceConfig = dataSourceConfig;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /**
     * 探测数据库结构（结合 Spider2 JSON 元数据）
     */
    public DatabaseSchema inspectSchema(String databaseName) {
        if (!dataSourceConfig.isDatabaseAvailable(databaseName)) {
            throw new IllegalArgumentException("Database not available: " + databaseName);
        }

        // 尝试从 JSON 元数据加载
        DatabaseSchema metadataSchema = loadFromMetadata(databaseName);
        if (metadataSchema != null) {
            log.info("Loaded schema from metadata for database: {}", databaseName);
            return metadataSchema;
        }

        // 回退到直接探测数据库
        log.info("Metadata not found, inspecting database directly: {}", databaseName);
        return inspectSchemaFromDatabase(databaseName);
    }

    /**
     * 从 Spider2 JSON 元数据加载数据库结构
     */
    public DatabaseSchema loadFromMetadata(String databaseName) {
        Path metadataDir = Paths.get(properties.getMetadataBasePath(), databaseName);
        if (!Files.isDirectory(metadataDir)) {
            log.debug("Metadata directory not found: {}", metadataDir);
            return null;
        }

        List<TableSchema> tables = new ArrayList<>();

        try (Stream<Path> paths = Files.list(metadataDir)) {
            List<Path> jsonFiles = paths
                    .filter(p -> p.toString().endsWith(".json"))
                    .toList();

            for (Path jsonFile : jsonFiles) {
                try {
                    TableMetadata metadata = objectMapper.readValue(jsonFile.toFile(), TableMetadata.class);
                    TableSchema tableSchema = convertMetadataToSchema(metadata);
                    tables.add(tableSchema);
                    log.debug("Loaded table metadata: {}", metadata.getTableName());
                } catch (IOException e) {
                    log.warn("Failed to parse metadata file {}: {}", jsonFile, e.getMessage());
                }
            }
        } catch (IOException e) {
            log.warn("Failed to list metadata files in {}: {}", metadataDir, e.getMessage());
            return null;
        }

        if (tables.isEmpty()) {
            return null;
        }

        return DatabaseSchema.builder()
                .databaseName(databaseName)
                .tables(tables)
                .build();
    }

    /**
     * 将 Spider2 表元数据转换为内部 schema 格式
     */
    private TableSchema convertMetadataToSchema(TableMetadata metadata) {
        List<ColumnSchema> columns = new ArrayList<>();

        for (int i = 0; i < metadata.getColumnNames().size(); i++) {
            String name = metadata.getColumnNames().get(i);
            String type = i < metadata.getColumnTypes().size() ? metadata.getColumnTypes().get(i) : "TEXT";
            String description = "";
            if (metadata.getDescription() != null && i < metadata.getDescription().size()) {
                description = metadata.getDescription().get(i);
            }

            columns.add(ColumnSchema.builder()
                    .name(name)
                    .type(type)
                    .description(description)
                    .notNull(false)
                    .primaryKey(i == 0 && name.toLowerCase().endsWith("_id"))
                    .build());
        }

        // 推断主键
        String primaryKey = inferPrimaryKey(columns, metadata.getTableName());

        return TableSchema.builder()
                .tableName(metadata.getTableName())
                .tableFullName(metadata.getTableFullName())
                .columns(columns)
                .primaryKey(primaryKey)
                .sampleRows(metadata.getSampleRows())
                .rowCount(metadata.getSampleRows() != null ? metadata.getSampleRows().size() : 0)
                .build();
    }

    /**
     * 推断主键
     */
    private String inferPrimaryKey(List<ColumnSchema> columns, String tableName) {
        // 优先查找 {table}_id 或 {table}Id
        String expectedPk = tableName.toLowerCase() + "_id";
        String expectedPk2 = tableName.toLowerCase() + "id";

        for (ColumnSchema col : columns) {
            String colLower = col.getName().toLowerCase();
            if (colLower.equals(expectedPk) || colLower.equals(expectedPk2)) {
                return col.getName();
            }
        }

        // 查找以 _id 结尾的第一列
        for (ColumnSchema col : columns) {
            if (col.getName().toLowerCase().endsWith("_id")) {
                return col.getName();
            }
        }

        // 查找名为 id 的列
        for (ColumnSchema col : columns) {
            if (col.getName().equalsIgnoreCase("id")) {
                return col.getName();
            }
        }

        // 返回第一列
        return columns.isEmpty() ? null : columns.get(0).getName();
    }

    /**
     * 直接从数据库探测结构（回退方案）
     */
    public DatabaseSchema inspectSchemaFromDatabase(String databaseName) {
        JdbcTemplate jdbcTemplate = dataSourceConfig.getJdbcTemplate(databaseName);

        List<String> tableNames = getTables(jdbcTemplate);
        log.info("Database {} has {} tables: {}", databaseName, tableNames.size(), tableNames);

        List<TableSchema> tableSchemas = new ArrayList<>();
        for (String tableName : tableNames) {
            TableSchema tableSchema = inspectTableFromDatabase(jdbcTemplate, tableName);
            tableSchemas.add(tableSchema);
        }

        return DatabaseSchema.builder()
                .databaseName(databaseName)
                .tables(tableSchemas)
                .build();
    }

    /**
     * 获取所有表名
     */
    private List<String> getTables(JdbcTemplate jdbcTemplate) {
        String sql = "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' ORDER BY name";
        return jdbcTemplate.queryForList(sql, String.class);
    }

    /**
     * 从数据库探测表结构
     */
    private TableSchema inspectTableFromDatabase(JdbcTemplate jdbcTemplate, String tableName) {
        String sql = "PRAGMA table_info('" + tableName + "')";
        List<Map<String, Object>> columns = jdbcTemplate.queryForList(sql);

        List<ColumnSchema> columnSchemas = new ArrayList<>();
        String primaryKey = null;

        for (Map<String, Object> column : columns) {
            String name = (String) column.get("name");
            String type = (String) column.get("type");
            boolean notNull = ((Number) column.get("notnull")).intValue() == 1;
            boolean pk = ((Number) column.get("pk")).intValue() == 1;

            if (pk) {
                primaryKey = name;
            }

            columnSchemas.add(ColumnSchema.builder()
                    .name(name)
                    .type(type)
                    .description("")
                    .notNull(notNull)
                    .primaryKey(pk)
                    .build());
        }

        long rowCount = getRowCount(jdbcTemplate, tableName);

        // 获取样本数据
        List<Map<String, Object>> sampleRows = getSampleRows(jdbcTemplate, tableName, 5);

        return TableSchema.builder()
                .tableName(tableName)
                .tableFullName(tableName)
                .columns(columnSchemas)
                .primaryKey(primaryKey)
                .rowCount(rowCount)
                .sampleRows(sampleRows)
                .build();
    }

    /**
     * 获取表行数
     */
    private long getRowCount(JdbcTemplate jdbcTemplate, String tableName) {
        try {
            String sql = "SELECT COUNT(*) FROM \"" + tableName + "\"";
            Long count = jdbcTemplate.queryForObject(sql, Long.class);
            return count != null ? count : 0;
        } catch (Exception e) {
            log.warn("Failed to get row count for {}: {}", tableName, e.getMessage());
            return -1;
        }
    }

    /**
     * 获取样本数据
     */
    private List<Map<String, Object>> getSampleRows(JdbcTemplate jdbcTemplate, String tableName, int limit) {
        try {
            String sql = "SELECT * FROM \"" + tableName + "\" LIMIT " + limit;
            return jdbcTemplate.queryForList(sql);
        } catch (Exception e) {
            log.warn("Failed to get sample rows for {}: {}", tableName, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 生成 JM 模型（利用元数据中的描述和样本）
     */
    public String generateJmModel(DatabaseSchema schema) {
        StringBuilder sb = new StringBuilder();
        sb.append("/**\n");
        sb.append(" * ").append(schema.getDatabaseName()).append(" 数据模型\n");
        sb.append(" * \n");
        sb.append(" * 基于 Spider2 元数据自动生成\n");
        sb.append(" */\n\n");

        for (TableSchema table : schema.getTables()) {
            sb.append("// === ").append(table.getTableName()).append(" ===\n");

            // 添加表注释（根据样本数据推断业务含义）
            String tableComment = inferTableComment(table);
            if (!tableComment.isEmpty()) {
                sb.append("// ").append(tableComment).append("\n");
            }

            sb.append("export const ").append(toPascalCase(table.getTableName())).append("Model = {\n");
            sb.append("    name: '").append(toPascalCase(table.getTableName())).append("Model',\n");
            sb.append("    caption: '").append(inferCaption(table.getTableName())).append("',\n");
            sb.append("    tableName: '").append(table.getTableName()).append("',\n");

            if (table.getPrimaryKey() != null) {
                sb.append("    idColumn: '").append(table.getPrimaryKey()).append("',\n");
            }

            sb.append("\n    properties: [\n");
            for (ColumnSchema column : table.getColumns()) {
                sb.append("        {\n");
                sb.append("            column: '").append(column.getName()).append("',\n");
                sb.append("            caption: '").append(inferColumnCaption(column)).append("',\n");
                sb.append("            type: '").append(mapSqliteType(column.getType())).append("'");

                // 添加描述（如果有）
                if (column.getDescription() != null && !column.getDescription().isEmpty()) {
                    sb.append(",\n            description: '").append(escapeString(column.getDescription())).append("'");
                }

                sb.append("\n        },\n");
            }
            sb.append("    ]\n");
            sb.append("};\n\n");
        }

        return sb.toString();
    }

    /**
     * 根据样本数据推断表的业务含义
     */
    private String inferTableComment(TableSchema table) {
        if (table.getSampleRows() == null || table.getSampleRows().isEmpty()) {
            return "";
        }

        String tableName = table.getTableName().toLowerCase();

        // 常见表名模式
        if (tableName.contains("customer")) return "客户信息表";
        if (tableName.contains("order") && !tableName.contains("item")) return "订单主表";
        if (tableName.contains("order_item") || tableName.contains("orderitem")) return "订单明细表";
        if (tableName.contains("product")) return "产品/商品表";
        if (tableName.contains("seller") || tableName.contains("vendor")) return "卖家/供应商表";
        if (tableName.contains("payment")) return "支付信息表";
        if (tableName.contains("review")) return "评价/评论表";
        if (tableName.contains("user")) return "用户表";
        if (tableName.contains("category")) return "分类表";
        if (tableName.contains("geo") || tableName.contains("location")) return "地理位置表";

        return "";
    }

    /**
     * 推断表标题
     */
    private String inferCaption(String tableName) {
        // 将下划线转为空格，首字母大写
        String[] parts = tableName.split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (sb.length() > 0) sb.append(" ");
            sb.append(Character.toUpperCase(part.charAt(0)));
            sb.append(part.substring(1).toLowerCase());
        }
        return sb.toString();
    }

    /**
     * 推断列标题
     */
    private String inferColumnCaption(ColumnSchema column) {
        // 如果有描述，使用描述
        if (column.getDescription() != null && !column.getDescription().isEmpty()) {
            // 取描述的前 30 字符作为标题
            String desc = column.getDescription();
            if (desc.length() > 30) {
                desc = desc.substring(0, 30) + "...";
            }
            return desc;
        }

        // 否则从列名推断
        return inferCaption(column.getName());
    }

    /**
     * SQLite 类型映射
     */
    private String mapSqliteType(String sqliteType) {
        if (sqliteType == null) return "STRING";
        String upper = sqliteType.toUpperCase();
        if (upper.contains("INT")) return "INTEGER";
        if (upper.contains("REAL") || upper.contains("FLOAT") || upper.contains("DOUBLE")) return "DECIMAL";
        if (upper.contains("BLOB")) return "BLOB";
        if (upper.contains("DATE") && !upper.contains("TIME")) return "DATE";
        if (upper.contains("TIME")) return "DATETIME";
        return "STRING";
    }

    private String toPascalCase(String name) {
        StringBuilder sb = new StringBuilder();
        boolean capitalizeNext = true;
        for (char c : name.toCharArray()) {
            if (c == '_') {
                capitalizeNext = true;
            } else if (capitalizeNext) {
                sb.append(Character.toUpperCase(c));
                capitalizeNext = false;
            } else {
                sb.append(Character.toLowerCase(c));
            }
        }
        return sb.toString();
    }

    private String escapeString(String s) {
        return s.replace("'", "\\'").replace("\n", " ");
    }

    // ========== 内部数据结构 ==========

    /**
     * Spider2 表元数据 JSON 格式
     */
    @Data
    @NoArgsConstructor
    public static class TableMetadata {
        @JsonProperty("table_name")
        private String tableName;

        @JsonProperty("table_fullname")
        private String tableFullName;

        @JsonProperty("column_names")
        private List<String> columnNames;

        @JsonProperty("column_types")
        private List<String> columnTypes;

        @JsonProperty("description")
        private List<String> description;

        @JsonProperty("sample_rows")
        private List<Map<String, Object>> sampleRows;
    }

    /**
     * 数据库结构
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DatabaseSchema {
        private String databaseName;
        private List<TableSchema> tables;
    }

    /**
     * 表结构
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TableSchema {
        private String tableName;
        private String tableFullName;
        private List<ColumnSchema> columns;
        private String primaryKey;
        private long rowCount;
        private List<Map<String, Object>> sampleRows;
    }

    /**
     * 列结构
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ColumnSchema {
        private String name;
        private String type;
        private String description;
        private boolean notNull;
        private boolean primaryKey;
    }
}
