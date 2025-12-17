package com.foggyframework.benchmark.spider2.generator;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Spider2 JM/QM 模型生成器
 *
 * 从 Spider2 元数据生成 foggy-data-mcp-bridge 的 JM 和 QM 模型文件
 * 每个表生成独立的 .jm 文件，每个事实表生成独立的 .qm 文件
 *
 * 使用方法：直接运行 main 方法
 */
public class Spider2ModelGenerator {

    // 默认路径，可通过环境变量 SPIDER2_BASE_PATH 覆盖
    private static final String SPIDER2_METADATA_PATH = System.getenv().getOrDefault("SPIDER2_BASE_PATH", "./spider2-data") + "/spider2-lite/resource/databases/sqlite";
    private static final String OUTPUT_PATH = "foggy-data-mcp-bridge/foggy-benchmark-spider2/src/main/resources/foggy/templates";

    private final ObjectMapper objectMapper;
    private final Path metadataPath;
    private final Path outputPath;

    public Spider2ModelGenerator() {
        this(Paths.get(SPIDER2_METADATA_PATH), Paths.get(OUTPUT_PATH));
    }

    public Spider2ModelGenerator(Path metadataPath, Path outputPath) {
        this.metadataPath = metadataPath;
        this.outputPath = outputPath;
        // 配置 ObjectMapper 允许 NaN 值（Spider2 数据中有些字段为 NaN）
        this.objectMapper = JsonMapper.builder()
                .enable(JsonReadFeature.ALLOW_NON_NUMERIC_NUMBERS)
                .build();
    }

    public static void main(String[] args) throws IOException {
        System.out.println("===========================================");
        System.out.println("  Spider2 JM/QM Model Generator");
        System.out.println("===========================================\n");

        Spider2ModelGenerator generator = new Spider2ModelGenerator();
        generator.generateAll();
    }

    /**
     * 生成所有数据库的 JM/QM 模型
     */
    public void generateAll() throws IOException {
        System.out.println("Starting Spider2 Model Generation...");
        System.out.println("Metadata path: " + metadataPath);
        System.out.println("Output path: " + outputPath);

        // 确保输出目录存在
        Files.createDirectories(outputPath);

        // 列出所有数据库目录
        List<String> databases;
        try (Stream<Path> paths = Files.list(metadataPath)) {
            databases = paths
                    .filter(Files::isDirectory)
                    .map(p -> p.getFileName().toString())
                    .sorted()
                    .collect(Collectors.toList());
        }

        System.out.println("Found " + databases.size() + " databases: " + databases);

        int successCount = 0;
        int failCount = 0;

        for (String dbName : databases) {
            try {
                generateDatabaseModels(dbName);
                successCount++;
            } catch (Exception e) {
                System.err.println("Failed to generate models for " + dbName + ": " + e.getMessage());
                e.printStackTrace();
                failCount++;
            }
        }

        System.out.println("\n========================================");
        System.out.println("Generation complete! Success: " + successCount + ", Failed: " + failCount);
        System.out.println("Output directory: " + outputPath.toAbsolutePath());
    }

    /**
     * 生成单个数据库的模型（每个表一个文件）
     */
    public void generateDatabaseModels(String dbName) throws IOException {
        System.out.println("\n========== Processing: " + dbName + " ==========");

        Path dbMetadataDir = metadataPath.resolve(dbName);
        if (!Files.isDirectory(dbMetadataDir)) {
            throw new IllegalArgumentException("Metadata directory not found: " + dbMetadataDir);
        }

        // 加载所有表元数据
        List<TableMetadata> tables = loadTableMetadata(dbMetadataDir);
        if (tables.isEmpty()) {
            System.out.println("No tables found for database: " + dbName);
            return;
        }

        System.out.println("Loaded " + tables.size() + " tables");

        // 分析表关系
        DatabaseAnalysis analysis = analyzeDatabase(dbName, tables);

        // 创建数据库输出目录
        Path dbOutputDir = outputPath.resolve(toSnakeCase(dbName));
        Files.createDirectories(dbOutputDir);

        int jmCount = 0;
        int qmCount = 0;

        // 为每个维度表生成独立的 JM 文件
        for (TableAnalysis dimTable : analysis.getDimensionTables()) {
            String jmContent = generateSingleJmFile(dimTable, false, analysis);
            String fileName = toPascalCase(dimTable.getMetadata().getTableName()) + "Model.jm";
            Path jmPath = dbOutputDir.resolve(fileName);
            Files.writeString(jmPath, jmContent, StandardCharsets.UTF_8);
            jmCount++;
        }

        // 为每个事实表生成独立的 JM 文件和 QM 文件
        for (TableAnalysis factTable : analysis.getFactTables()) {
            // 生成 JM
            String jmContent = generateSingleJmFile(factTable, true, analysis);
            String jmFileName = toPascalCase(factTable.getMetadata().getTableName()) + "Model.jm";
            Path jmPath = dbOutputDir.resolve(jmFileName);
            Files.writeString(jmPath, jmContent, StandardCharsets.UTF_8);
            jmCount++;

            // 生成 QM
            String qmContent = generateSingleQmFile(factTable, analysis);
            String qmFileName = toPascalCase(factTable.getMetadata().getTableName()) + "QueryModel.qm";
            Path qmPath = dbOutputDir.resolve(qmFileName);
            Files.writeString(qmPath, qmContent, StandardCharsets.UTF_8);
            qmCount++;
        }

        System.out.println("Generated " + jmCount + " JM files, " + qmCount + " QM files");
    }

    /**
     * 加载表元数据
     */
    private List<TableMetadata> loadTableMetadata(Path dbDir) throws IOException {
        List<TableMetadata> tables = new ArrayList<>();

        try (Stream<Path> paths = Files.list(dbDir)) {
            List<Path> jsonFiles = paths
                    .filter(p -> p.toString().endsWith(".json"))
                    .collect(Collectors.toList());

            for (Path jsonFile : jsonFiles) {
                try {
                    TableMetadata metadata = objectMapper.readValue(jsonFile.toFile(), TableMetadata.class);
                    tables.add(metadata);
                } catch (Exception e) {
                    System.err.println("Failed to parse " + jsonFile.getFileName() + ": " + e.getMessage());
                }
            }
        }

        return tables;
    }

    /**
     * 分析数据库结构，识别事实表和维度表
     */
    private DatabaseAnalysis analyzeDatabase(String dbName, List<TableMetadata> tables) {
        DatabaseAnalysis analysis = new DatabaseAnalysis();
        analysis.setDatabaseName(dbName);

        // 收集所有可能的主键列
        Map<String, String> tablePrimaryKeys = new HashMap<>();
        for (TableMetadata table : tables) {
            String pk = inferPrimaryKey(table);
            if (pk != null) {
                tablePrimaryKeys.put(table.getTableName(), pk);
            }
        }
        analysis.setTablePrimaryKeys(tablePrimaryKeys);

        // 构建表名到元数据的映射
        Map<String, TableMetadata> tableMap = new HashMap<>();
        for (TableMetadata table : tables) {
            tableMap.put(table.getTableName(), table);
        }
        analysis.setTableMap(tableMap);

        // 分析每个表，识别外键关系
        for (TableMetadata table : tables) {
            TableAnalysis tableAnalysis = new TableAnalysis();
            tableAnalysis.setMetadata(table);
            tableAnalysis.setPrimaryKey(tablePrimaryKeys.get(table.getTableName()));

            // 检测外键（以 _id、_key、id、key 结尾且对应其他表的主键）
            List<ForeignKeyRelation> foreignKeys = new ArrayList<>();
            for (String colName : table.getColumnNames()) {
                String colLower = colName.toLowerCase();
                // 支持 salesperson_id、salespersonid、territory_key、territorykey 等命名
                boolean isForeignKeyCandidate = (colLower.endsWith("_id") || colLower.endsWith("_key") ||
                        colLower.endsWith("id") || colLower.endsWith("key"))
                        && !colName.equals(tableAnalysis.getPrimaryKey());

                if (isForeignKeyCandidate) {
                    // 尝试找到对应的表
                    // 移除后缀：_id, _key, id, key
                    String possibleTable = colLower.replaceAll("(_id|_key|id|key)$", "");
                    // 如果移除后为空或太短，跳过
                    if (possibleTable.isEmpty() || possibleTable.length() < 2) {
                        continue;
                    }

                    String possibleTablePlural = possibleTable + "s";

                    for (TableMetadata otherTable : tables) {
                        String otherName = otherTable.getTableName().toLowerCase();
                        if (otherName.equals(possibleTable) || otherName.equals(possibleTablePlural) ||
                            otherName.endsWith("_" + possibleTable) || otherName.endsWith("_" + possibleTablePlural)) {
                            ForeignKeyRelation fk = new ForeignKeyRelation();
                            fk.setForeignKeyColumn(colName);
                            fk.setReferencedTable(otherTable.getTableName());
                            fk.setReferencedColumn(tablePrimaryKeys.get(otherTable.getTableName()));
                            foreignKeys.add(fk);
                            break;
                        }
                    }
                }
            }
            tableAnalysis.setForeignKeys(foreignKeys);

            // 判断是事实表还是维度表（需要同时检查列名和类型）
            boolean hasMeasures = false;
            for (int i = 0; i < table.getColumnNames().size(); i++) {
                String colName = table.getColumnNames().get(i);
                String colType = i < table.getColumnTypes().size() ? table.getColumnTypes().get(i) : "TEXT";
                if (isMeasureColumn(colName, colType)) {
                    hasMeasures = true;
                    break;
                }
            }

            if (foreignKeys.size() >= 2 || hasMeasures) {
                tableAnalysis.setTableType(TableType.FACT);
                analysis.getFactTables().add(tableAnalysis);
            } else {
                tableAnalysis.setTableType(TableType.DIMENSION);
                analysis.getDimensionTables().add(tableAnalysis);
            }
        }

        // 如果没有识别出事实表，选择外键最多的表作为事实表
        if (analysis.getFactTables().isEmpty() && !analysis.getDimensionTables().isEmpty()) {
            analysis.getDimensionTables().sort((a, b) ->
                    b.getForeignKeys().size() - a.getForeignKeys().size());
            TableAnalysis mainTable = analysis.getDimensionTables().remove(0);
            mainTable.setTableType(TableType.FACT);
            analysis.getFactTables().add(mainTable);
        }

        System.out.println("Analysis - Fact tables: " + analysis.getFactTables().size() +
                ", Dimension tables: " + analysis.getDimensionTables().size());

        return analysis;
    }

    /**
     * 生成单个表的 JM 文件内容
     */
    private String generateSingleJmFile(TableAnalysis table, boolean isFact, DatabaseAnalysis analysis) {
        StringBuilder sb = new StringBuilder();
        TableMetadata meta = table.getMetadata();
        String modelName = toPascalCase(meta.getTableName()) + "Model";
        String dataSourceName = toCamelCase(analysis.getDatabaseName()) + "DataSource";

        // 数据源导入
        sb.append("import '@").append(dataSourceName).append("';\n");

        // 文件头注释
        sb.append("/**\n");
        sb.append(" * ").append(meta.getTableName()).append(" ").append(isFact ? "事实表" : "维度表").append("模型\n");
        sb.append(" * \n");
        sb.append(" * 数据库: ").append(analysis.getDatabaseName()).append("\n");
        sb.append(" * 数据源: ").append(dataSourceName).append("\n");
        sb.append(" * 基于 Spider2 元数据自动生成\n");
        sb.append(" */\n\n");

        sb.append("export const model = {\n");
        sb.append("    name: '").append(modelName).append("',\n");
        sb.append("    caption: '").append(inferTableCaption(meta.getTableName())).append("',\n");
        sb.append("    tableName: '").append(meta.getTableName()).append("',\n");
        sb.append("    dataSource: ").append(dataSourceName).append(",\n");

        if (table.getPrimaryKey() != null) {
            sb.append("    idColumn: '").append(table.getPrimaryKey()).append("',\n");
        }

        // 维度引用（仅事实表，内联维度表属性）
        if (isFact && !table.getForeignKeys().isEmpty()) {
            sb.append("\n    dimensions: [\n");
            for (ForeignKeyRelation fk : table.getForeignKeys()) {
                sb.append("        {\n");
                sb.append("            name: '").append(toCamelCase(fk.getReferencedTable())).append("',\n");
                sb.append("            tableName: '").append(fk.getReferencedTable()).append("',\n");
                sb.append("            foreignKey: '").append(fk.getForeignKeyColumn()).append("',\n");
                sb.append("            primaryKey: '").append(fk.getReferencedColumn() != null ? fk.getReferencedColumn() : fk.getForeignKeyColumn()).append("',\n");

                // 找到维度表的 caption 列
                TableMetadata dimMeta = analysis.getTableMap().get(fk.getReferencedTable());
                if (dimMeta != null) {
                    String captionCol = inferCaptionColumn(dimMeta);
                    if (captionCol != null) {
                        sb.append("            captionColumn: '").append(captionCol).append("',\n");
                    }
                }

                sb.append("            caption: '").append(inferTableCaption(fk.getReferencedTable())).append("',\n");

                // 内联维度表的属性
                if (dimMeta != null) {
                    sb.append("            properties: [\n");
                    for (int i = 0; i < dimMeta.getColumnNames().size(); i++) {
                        String colName = dimMeta.getColumnNames().get(i);
                        String colType = i < dimMeta.getColumnTypes().size() ? dimMeta.getColumnTypes().get(i) : "TEXT";
                        sb.append("                { column: '").append(colName).append("'");
                        sb.append(", caption: '").append(inferColumnCaption(colName, "")).append("'");
                        sb.append(", type: '").append(mapToJmType(colType)).append("' },\n");
                    }
                    sb.append("            ]\n");
                }
                sb.append("        },\n");
            }
            sb.append("    ],\n");
        }

        // 收集外键列名（用于排除）
        Set<String> foreignKeyColumns = new HashSet<>();
        if (isFact) {
            for (ForeignKeyRelation fk : table.getForeignKeys()) {
                foreignKeyColumns.add(fk.getForeignKeyColumn());
            }
        }

        // 属性定义
        sb.append("\n    properties: [\n");
        List<String> measureColumns = new ArrayList<>();

        for (int i = 0; i < meta.getColumnNames().size(); i++) {
            String colName = meta.getColumnNames().get(i);
            String colType = i < meta.getColumnTypes().size() ? meta.getColumnTypes().get(i) : "TEXT";
            String description = "";
            if (meta.getDescription() != null && i < meta.getDescription().size()) {
                description = meta.getDescription().get(i);
                if (description == null) description = "";
            }

            // 跳过外键字段（已在 dimensions 中定义）
            if (foreignKeyColumns.contains(colName)) {
                continue;
            }

            // 识别度量字段（仅事实表）
            boolean isMeasure = isFact && isMeasureColumn(colName, colType);
            if (isMeasure) {
                measureColumns.add(colName);
                continue;
            }

            sb.append("        { column: '").append(colName).append("'");
            sb.append(", caption: '").append(inferColumnCaption(colName, description)).append("'");
            sb.append(", type: '").append(mapToJmType(colType)).append("'");
            sb.append(" },\n");
        }
        sb.append("    ],\n");

        // 度量定义（仅事实表）
        if (isFact && !measureColumns.isEmpty()) {
            sb.append("\n    measures: [\n");
            for (String colName : measureColumns) {
                int idx = meta.getColumnNames().indexOf(colName);
                String colType = idx >= 0 && idx < meta.getColumnTypes().size() ? meta.getColumnTypes().get(idx) : "REAL";

                sb.append("        { column: '").append(colName).append("'");
                sb.append(", caption: '").append(inferColumnCaption(colName, "")).append("'");
                sb.append(", type: '").append(mapToJmType(colType)).append("'");
                sb.append(", aggregation: '").append(inferAggregation(colName)).append("' },\n");
            }
            sb.append("    ]\n");
        }

        sb.append("};\n");

        return sb.toString();
    }

    /**
     * 生成单个事实表的 QM 文件内容
     */
    private String generateSingleQmFile(TableAnalysis factTable, DatabaseAnalysis analysis) {
        StringBuilder sb = new StringBuilder();
        TableMetadata meta = factTable.getMetadata();
        String modelName = toPascalCase(meta.getTableName()) + "Model";
        String qmName = toPascalCase(meta.getTableName()) + "QueryModel";

        // 文件头注释
        sb.append("/**\n");
        sb.append(" * ").append(meta.getTableName()).append(" 查询模型\n");
        sb.append(" * \n");
        sb.append(" * 数据库: ").append(analysis.getDatabaseName()).append("\n");
        sb.append(" * 基于 Spider2 元数据自动生成\n");
        sb.append(" */\n\n");

        sb.append("export const queryModel = {\n");
        sb.append("    name: '").append(qmName).append("',\n");
        sb.append("    model: '").append(modelName).append("',\n");
        sb.append("\n");

        // 构建外键列到维度名的映射
        Map<String, String> fkToDimName = new HashMap<>();
        for (ForeignKeyRelation fk : factTable.getForeignKeys()) {
            fkToDimName.put(fk.getForeignKeyColumn(), toCamelCase(fk.getReferencedTable()));
        }

        sb.append("    columnGroups: [\n");

        // 基本信息组
        sb.append("        {\n");
        sb.append("            caption: '基本信息',\n");
        sb.append("            items: [\n");
        for (int i = 0; i < meta.getColumnNames().size(); i++) {
            String colName = meta.getColumnNames().get(i);
            String colType = i < meta.getColumnTypes().size() ? meta.getColumnTypes().get(i) : "TEXT";
            if (!isMeasureColumn(colName, colType)) {
                // 如果是外键列，使用维度名$id的格式（约定：外键统一用 $id 后缀）
                String dimName = fkToDimName.get(colName);
                if (dimName != null) {
                    sb.append("                { name: '").append(dimName).append("$id' },\n");
                } else {
                    sb.append("                { name: '").append(colName).append("' },\n");
                }
            }
        }
        sb.append("            ]\n");
        sb.append("        },\n");

        // 维度组（维度表的其他属性，使用 $caption 显示名称）
        if (!factTable.getForeignKeys().isEmpty()) {
            sb.append("        {\n");
            sb.append("            caption: '关联维度',\n");
            sb.append("            items: [\n");
            for (ForeignKeyRelation fk : factTable.getForeignKeys()) {
                // 维度引用使用 camelCase 维度名 + $caption
                String dimName = toCamelCase(fk.getReferencedTable());
                sb.append("                { name: '").append(dimName).append("$caption' },\n");
            }
            sb.append("            ]\n");
            sb.append("        },\n");
        }

        // 度量组
        List<String> measures = new ArrayList<>();
        for (int i = 0; i < meta.getColumnNames().size(); i++) {
            String colName = meta.getColumnNames().get(i);
            String colType = i < meta.getColumnTypes().size() ? meta.getColumnTypes().get(i) : "TEXT";
            if (isMeasureColumn(colName, colType)) {
                measures.add(colName);
            }
        }
        if (!measures.isEmpty()) {
            sb.append("        {\n");
            sb.append("            caption: '度量指标',\n");
            sb.append("            items: [\n");
            for (String colName : measures) {
                // QM 中的 name 使用原始列名（与 JM 中的 column 保持一致）
                sb.append("                { name: '").append(colName).append("' },\n");
            }
            sb.append("            ]\n");
            sb.append("        }\n");
        }

        sb.append("    ],\n\n");

        sb.append("    orders: [],\n");
        sb.append("    accesses: []\n");
        sb.append("};\n");

        return sb.toString();
    }

    // ========== 辅助方法 ==========

    private String inferPrimaryKey(TableMetadata table) {
        String tableName = table.getTableName().toLowerCase();

        // 尝试 {table}_id 或 {table}_key
        for (String col : table.getColumnNames()) {
            String colLower = col.toLowerCase();
            if (colLower.equals(tableName + "_id") || colLower.equals(tableName + "id") ||
                colLower.equals(tableName + "_key") || colLower.equals(tableName + "key")) {
                return col;
            }
        }

        // 尝试第一个 *_id 或 *_key 列
        for (String col : table.getColumnNames()) {
            String colLower = col.toLowerCase();
            if (colLower.endsWith("_id") || colLower.endsWith("_key")) {
                return col;
            }
        }

        // 尝试 id 列
        for (String col : table.getColumnNames()) {
            if (col.equalsIgnoreCase("id")) {
                return col;
            }
        }

        return table.getColumnNames().isEmpty() ? null : table.getColumnNames().get(0);
    }

    private String inferCaptionColumn(TableMetadata table) {
        // 优先查找 name 相关列
        for (String col : table.getColumnNames()) {
            String colLower = col.toLowerCase();
            if (colLower.endsWith("_name") || colLower.equals("name") ||
                colLower.endsWith("_title") || colLower.equals("title") ||
                colLower.endsWith("_description") || colLower.equals("description")) {
                return col;
            }
        }
        // 返回第二列（通常第一列是ID）
        if (table.getColumnNames().size() > 1) {
            return table.getColumnNames().get(1);
        }
        return null;
    }

    private boolean isMeasureColumn(String colName, String colType) {
        // 字符串类型不可能是度量
        if (colType != null) {
            String upperType = colType.toUpperCase();
            if (upperType.contains("TEXT") || upperType.contains("VARCHAR") ||
                upperType.contains("CHAR") || upperType.contains("STRING")) {
                return false;
            }
        }

        // 排除明显的非度量字段
        String lower = colName.toLowerCase();
        // 排除 ID 和 key 类型字段（包括以 id 结尾的字段如 salesorderid）
        if (lower.endsWith("_id") || lower.endsWith("_key") || lower.endsWith("_code") ||
            lower.endsWith("_name") || lower.endsWith("_date") || lower.endsWith("_time") ||
            lower.endsWith("id") || lower.endsWith("key") ||  // 新增：排除以 id/key 结尾的字段
            lower.equals("id") || lower.equals("name") || lower.equals("code") ||
            lower.contains("status") || lower.contains("type") || lower.contains("flag") ||
            lower.contains("region") || lower.contains("country") || lower.contains("city") ||
            lower.contains("address") || lower.contains("phone") || lower.contains("email")) {
            return false;
        }

        // 只有数值类型且名称包含度量关键词才是度量
        boolean isNumericType = colType != null &&
            (colType.toUpperCase().contains("INT") ||
             colType.toUpperCase().contains("REAL") ||
             colType.toUpperCase().contains("FLOAT") ||
             colType.toUpperCase().contains("DOUBLE") ||
             colType.toUpperCase().contains("NUMERIC") ||
             colType.toUpperCase().contains("DECIMAL"));

        boolean hasMeasureKeyword = lower.contains("amount") || lower.contains("price") ||
               lower.contains("quantity") || lower.contains("total") ||
               lower.contains("_qty") || lower.contains("_num") ||
               lower.contains("revenue") || lower.contains("sales") ||
               lower.contains("cost") || lower.contains("profit") ||
               lower.contains("discount") || lower.contains("tax") ||
               lower.contains("freight") || lower.contains("fee") ||
               lower.contains("balance") || lower.contains("budget") ||
               lower.equals("qty") || lower.equals("num");

        return isNumericType && hasMeasureKeyword;
    }

    private String inferAggregation(String colName) {
        String lower = colName.toLowerCase();
        if (lower.contains("count") || lower.contains("qty") || lower.contains("quantity") || lower.contains("_num")) {
            return "sum";
        }
        if (lower.contains("avg") || lower.contains("average") || lower.contains("rate")) {
            return "avg";
        }
        if (lower.contains("max")) {
            return "max";
        }
        return "sum";
    }

    private String inferTableCaption(String tableName) {
        // 常见表名映射
        Map<String, String> knownTables = new HashMap<>();
        knownTables.put("customers", "客户");
        knownTables.put("customer", "客户");
        knownTables.put("orders", "订单");
        knownTables.put("order", "订单");
        knownTables.put("order_items", "订单明细");
        knownTables.put("products", "产品");
        knownTables.put("product", "产品");
        knownTables.put("sellers", "卖家");
        knownTables.put("seller", "卖家");
        knownTables.put("payments", "支付");
        knownTables.put("order_payments", "订单支付");
        knownTables.put("reviews", "评价");
        knownTables.put("order_reviews", "订单评价");
        knownTables.put("geolocation", "地理位置");
        knownTables.put("categories", "分类");
        knownTables.put("category", "分类");
        knownTables.put("users", "用户");
        knownTables.put("user", "用户");

        String lower = tableName.toLowerCase();
        if (knownTables.containsKey(lower)) {
            return knownTables.get(lower);
        }

        // 将下划线转为空格
        return Arrays.stream(tableName.split("_"))
                .map(s -> s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase())
                .reduce((a, b) -> a + " " + b)
                .orElse(tableName);
    }

    private String inferColumnCaption(String colName, String description) {
        if (description != null && !description.isEmpty() && description.length() < 50) {
            return description;
        }
        // 从列名推断
        return Arrays.stream(colName.split("_"))
                .map(s -> s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase())
                .reduce((a, b) -> a + " " + b)
                .orElse(colName);
    }

    private String mapToJmType(String sqliteType) {
        if (sqliteType == null) return "STRING";
        String upper = sqliteType.toUpperCase();
        if (upper.contains("INT")) return "INTEGER";
        if (upper.contains("REAL") || upper.contains("FLOAT") || upper.contains("DOUBLE")) return "DECIMAL";
        if (upper.contains("BLOB")) return "STRING";
        if (upper.contains("DATE") && !upper.contains("TIME")) return "DAY";
        if (upper.contains("TIME")) return "DATETIME";
        return "STRING";
    }

    private String toPascalCase(String name) {
        StringBuilder sb = new StringBuilder();
        boolean capitalizeNext = true;
        for (char c : name.toCharArray()) {
            if (c == '_' || c == '-') {
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

    private String toCamelCase(String name) {
        String pascal = toPascalCase(name);
        if (pascal.isEmpty()) return pascal;
        return Character.toLowerCase(pascal.charAt(0)) + pascal.substring(1);
    }

    private String toSnakeCase(String name) {
        return name.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase();
    }

    // ========== 数据类 ==========

    @Data
    @NoArgsConstructor
    public static class TableMetadata {
        @JsonProperty("table_name")
        private String tableName;

        @JsonProperty("table_fullname")
        private String tableFullName;

        @JsonProperty("column_names")
        private List<String> columnNames = new ArrayList<>();

        @JsonProperty("column_types")
        private List<String> columnTypes = new ArrayList<>();

        @JsonProperty("description")
        private List<String> description;

        @JsonProperty("sample_rows")
        private List<Map<String, Object>> sampleRows;
    }

    @Data
    static class DatabaseAnalysis {
        private String databaseName;
        private List<TableAnalysis> factTables = new ArrayList<>();
        private List<TableAnalysis> dimensionTables = new ArrayList<>();
        private Map<String, String> tablePrimaryKeys = new HashMap<>();
        private Map<String, TableMetadata> tableMap = new HashMap<>();
    }

    @Data
    static class TableAnalysis {
        private TableMetadata metadata;
        private String primaryKey;
        private List<ForeignKeyRelation> foreignKeys = new ArrayList<>();
        private TableType tableType;
    }

    @Data
    static class ForeignKeyRelation {
        private String foreignKeyColumn;
        private String referencedTable;
        private String referencedColumn;
    }

    enum TableType {
        FACT, DIMENSION
    }
}
