# 工具提示词对比分析与配置方案建议

## 提示词对比结果

### 1. NaturalLanguageQueryTool (dataset_nl.query)

**原Python配置** (415行详细文档):
- ✅ 详细的功能说明（核心功能、可视化支持、响应格式）
- ✅ 完整的参数说明（query、session_id、cursor、format、hints、stream）
- ✅ 丰富的使用示例（趋势分析、分类统计、Excel导出、占比分析等）
- ✅ 返回值详细说明（exports字段结构）
- ✅ AI规范兼容性说明
- ✅ 智能特性说明（意图识别、多步骤分析、图表自动生成）
- ✅ 性能优化建议
- ✅ 错误处理指南
- ✅ 最佳实践

**当前Java实现**:
```java
"智能自然语言查询接口。" +
"接收自然语言查询，自动分析意图、选择模型、构建查询、执行并返回结果。" +
"支持自动图表生成和Excel导出。" +
"返回标准化exports字段：charts[]和excel[]数组。"
```

**差异分析**: ❌ 严重简化，丢失了约95%的重要信息

---

### 2. MetadataTool (dataset.get_metadata)

**原Python配置** (30行文档):
- ✅ 主要功能说明
- ✅ 使用场景说明
- ✅ 返回内容说明
- ✅ 内部实现说明

**当前Java实现**:
```java
"获取用户级元数据包（小而精），用于模型发现与常见字段选择。" +
"返回可用的数据模型列表、常用字段和字典信息。" +
"仅在需要时调用（首次/缓存失效）。"
```

**差异分析**: ⚠️ 基本信息保留，但缺少详细的返回内容结构和使用场景

---

### 3. QueryModelTool (dataset.query_model_v2)

**原Python配置** (324行超详细文档):
- ✅ 核心概念与名词解释（数据模型、维度、属性、度量）
- ✅ 详细的工具描述
- ✅ 查询参数详解（columns、slice、orderBy、groupBy、分页）
- ✅ 大量查询示例（基础查询、高性能查询、空值查询、复合条件、区间查询、OR逻辑）
- ✅ 语法规则说明
- ✅ 性能优化建议
- ✅ 错误处理与调试指南
- ✅ 最佳实践

**当前Java实现**:
```java
"按列/过滤/分组/排序/分页执行查询。" +
"服务端会将 $caption 条件归一化为 $id。" +
"支持的操作：选择列、过滤条件、分组聚合、排序、分页。" +
"展示需要文本时在 columns 带 $caption；过滤/分组由服务端归一化。"
```

**差异分析**: ❌ 极度简化，丢失了约98%的关键信息

---

### 4. DescriptionModelTool (dataset.description_model_internal)

**原Python配置** (49行文档):
- ✅ 详细功能说明
- ✅ 使用场景说明
- ✅ 返回内容结构示例
- ✅ 特殊说明（维度字段、字典字段、度量字段）
- ✅ 内部实现说明

**当前Java实现**:
```java
"获取指定模型的全量字段与字典映射。" +
"当字段校验失败或需要字典映射/聚合能力时再调用。" +
"返回模型的完整字段定义、数据类型、字典枚举等信息。"
```

**差异分析**: ⚠️ 基本功能说明保留，但缺少返回结构和特殊说明

---

### 5. ChartTool (chart.generate)

**原Python配置** (141行文档):
- ✅ 工具描述
- ✅ 适用场景
- ✅ 详细示例（PIE图、LINE图）
- ✅ 参数说明（data、chart、returnFormat）
- ✅ 返回值说明（URL格式、Base64格式）
- ✅ 图表类型和高级配置
- ✅ 性能优化
- ✅ 错误处理
- ✅ 与其他工具配合
- ✅ 注意事项

**当前Java实现**:
```java
"生成数据可视化图表。支持线图(line)、柱图(bar)、饼图(pie)、散点图(scatter)等类型。" +
"根据数据自动选择最佳的图表类型，或按指定类型生成。" +
"返回图表的 URL 地址。"
```

**差异分析**: ❌ 严重简化，丢失了约95%的重要信息

---

### 6. ExportWithChartTool (dataset.export_with_chart)

**原Python配置** (类似generate_chart的详细文档)

**当前Java实现**:
```java
"执行数据查询并自动生成可视化图表。" +
"支持自动选择图表类型，或指定具体类型。" +
"返回查询结果和图表 URL。"
```

**差异分析**: ❌ 严重简化，丢失了大量关键信息

---

## 配置方案建议

### 方案对比

| 配置方式 | 优点 | 缺点 | 适用场景 |
|---------|------|------|----------|
| **Markdown文件** | • 丰富的格式化支持<br>• 可包含代码示例<br>• 易于阅读和编辑<br>• 支持表格、列表等复杂格式 | • 需要解析器<br>• 结构不够严格<br>• 难以编程式访问 | 复杂工具描述、文档生成 |
| **YAML配置** | • 结构化清晰<br>• 易于解析<br>• 支持多语言<br>• 版本控制友好<br>• 易于编程访问 | • 格式化能力有限<br>• 不支持复杂示例<br>• 可读性不如Markdown | 简单配置、参数定义 |
| **JSON配置** | • 标准格式<br>• 解析速度快<br>• JavaScript原生支持 | • 不支持注释<br>• 可读性较差<br>• 格式化能力有限 | API定义、数据交换 |
| **数据库存储** | • 动态更新<br>• 支持版本管理<br>• 多租户支持<br>• 权限控制 | • 增加复杂度<br>• 需要额外维护<br>• 启动依赖 | 企业级应用、多租户 |

### 推荐方案：混合配置架构

我推荐采用 **YAML + Markdown 混合配置方案**：

#### 架构设计

```
config/
└── tools/
    ├── tool-configs.yml                  # 主配置文件（结构化数据）
    ├── descriptions/                     # 详细描述目录（Markdown）
    │   ├── dataset_nl_query.md
    │   ├── get_metadata.md
    │   ├── query_model_v2.md
    │   ├── description_model_internal.md
    │   ├── generate_chart.md
    │   └── export_with_chart.md
    └── schemas/                          # 参数Schema定义（JSON Schema）
        ├── dataset_nl_query_schema.json
        ├── query_model_v2_schema.json
        └── ...
```

#### tool-configs.yml 结构示例

```yaml
tools:
  - name: "dataset_nl.query"
    displayName: "智能自然语言查询"
    category: NATURAL_LANGUAGE
    version: "2.0"

    # 简短描述（用于工具列表）
    summary: "使用自然语言查询数据集，支持自动生成图表和Excel导出"

    # 详细描述文件路径
    descriptionFile: "descriptions/dataset_nl_query.md"

    # 参数Schema文件路径
    schemaFile: "schemas/dataset_nl_query_schema.json"

    # 快速参考（核心参数）
    parameters:
      - name: "query"
        type: "string"
        required: true
        description: "自然语言查询内容"
        example: "查询最近一周的订单趋势"

      - name: "session_id"
        type: "string"
        required: false
        description: "会话ID，用于保持上下文"

      - name: "cursor"
        type: "string"
        required: false
        description: "分页游标"

    # 返回值简要说明
    returns:
      type: "object"
      summary: "查询结果包含数据、图表和Excel导出"
      exportsField: true

    # 使用场景标签
    tags:
      - "自然语言"
      - "智能查询"
      - "图表生成"
      - "数据导出"

    # 性能提示
    performance:
      averageResponseTime: "20-60s"
      maxDataPoints: 10000
      paginationRecommended: true

  - name: "dataset.query_model_v2"
    displayName: "模型结构化查询"
    category: QUERY
    version: "2.0"

    summary: "执行指定模型的数据查询，支持复杂查询条件、分页、排序、分组聚合等"

    descriptionFile: "descriptions/query_model_v2.md"
    schemaFile: "schemas/query_model_v2_schema.json"

    parameters:
      - name: "model"
        type: "string"
        required: true
        description: "模型名称"
        example: "TmsCustomerModel"

      - name: "payload"
        type: "object"
        required: true
        description: "查询参数对象"
        properties:
          columns: "选择的列"
          slice: "过滤条件"
          orderBy: "排序规则"
          groupBy: "分组聚合"
          limit: "分页大小"
          totalColumn: "是否返回总数"

    tags:
      - "结构化查询"
      - "数据分析"
      - "聚合统计"

    performance:
      averageResponseTime: "1-5s"
      totalColumnPerformance: "设置totalColumn=false可显著提升性能"

# 全局配置
global:
  descriptionLanguage: "zh-CN"
  defaultEncoding: "UTF-8"
  cacheEnabled: true
  cacheTTL: 3600  # 秒
```

#### Java实现架构

```java
// 1. 配置加载器
public class ToolConfigLoader {

    private final Map<String, ToolConfig> toolConfigs;
    private final MarkdownParser markdownParser;

    public ToolConfigLoader(String configPath) {
        // 加载 tool-configs.yml
        this.toolConfigs = loadYamlConfig(configPath);
        this.markdownParser = new MarkdownParser();
    }

    public ToolConfig getToolConfig(String toolName) {
        return toolConfigs.get(toolName);
    }

    public String getFullDescription(String toolName) {
        ToolConfig config = toolConfigs.get(toolName);
        if (config.getDescriptionFile() != null) {
            // 从Markdown文件加载完整描述
            return markdownParser.parse(config.getDescriptionFile());
        }
        return config.getSummary();
    }
}

// 2. 工具配置模型
@Data
public class ToolConfig {
    private String name;
    private String displayName;
    private ToolCategory category;
    private String version;
    private String summary;
    private String descriptionFile;
    private String schemaFile;
    private List<ParameterInfo> parameters;
    private ReturnInfo returns;
    private List<String> tags;
    private PerformanceInfo performance;
}

// 3. 工具基类更新
public interface McpTool {

    String getName();

    // 新增：获取工具配置
    default ToolConfig getConfig() {
        return ToolConfigRegistry.getInstance().getConfig(getName());
    }

    // 简短描述（用于列表显示）
    default String getDescription() {
        return getConfig().getSummary();
    }

    // 完整描述（用于详细展示或AI Prompt）
    default String getFullDescription() {
        return ToolConfigRegistry.getInstance().getFullDescription(getName());
    }

    // 参数Schema
    default Map<String, Object> getInputSchema() {
        return getConfig().getJsonSchema();
    }

    Set<ToolCategory> getCategories();

    Object execute(Map<String, Object> arguments, String traceId);
}

// 4. 全局配置注册表
@Component
public class ToolConfigRegistry {

    private static ToolConfigRegistry instance;
    private final ToolConfigLoader loader;

    public ToolConfigRegistry(@Value("${mcp.tools.config-path}") String configPath) {
        this.loader = new ToolConfigLoader(configPath);
        instance = this;
    }

    public static ToolConfigRegistry getInstance() {
        return instance;
    }

    public ToolConfig getConfig(String toolName) {
        return loader.getToolConfig(toolName);
    }

    public String getFullDescription(String toolName) {
        return loader.getFullDescription(toolName);
    }
}
```

#### Markdown描述文件模板

```markdown
# {工具名称} - {显示名称}

## 概述
{工具的简要说明}

## 核心功能
- 功能点1
- 功能点2
- 功能点3

## 参数说明

### 参数名1 (必填/可选)
- **类型**: string/number/object/array
- **说明**: 参数的详细说明
- **示例**: `"示例值"`
- **默认值**: 默认值说明

### 参数名2 (必填/可选)
- **类型**: object
- **说明**: 复杂参数的说明
- **属性**:
  - `子属性1`: 说明
  - `子属性2`: 说明

## 使用示例

### 示例1: 基础用法
\`\`\`json
{
  "参数1": "值1",
  "参数2": "值2"
}
\`\`\`

**返回结果**:
\`\`\`json
{
  "result": "结果"
}
\`\`\`

### 示例2: 高级用法
\`\`\`json
{
  "参数1": "值1",
  "参数2": {
    "子参数1": "值"
  }
}
\`\`\`

## 返回值说明
- **字段1**: 说明
- **字段2**: 说明

## 性能优化建议
1. 建议1
2. 建议2

## 错误处理
- **错误码1**: 说明和解决方案
- **错误码2**: 说明和解决方案

## 最佳实践
1. 实践1
2. 实践2

## 注意事项
- 注意点1
- 注意点2
```

---

## 实施步骤

### 阶段1: 配置迁移（1-2天）

1. **创建YAML主配置**
   ```bash
   mkdir -p config/tools/descriptions
   mkdir -p config/tools/schemas
   touch config/tools/tool-configs.yml
   ```

2. **转换现有Markdown**
   - 保留现有的 `*.md` 文件
   - 将其移动到 `descriptions/` 目录
   - 提取结构化信息到 `tool-configs.yml`

3. **生成JSON Schema**
   - 从 `getInputSchema()` 方法提取
   - 保存为独立的JSON文件
   - 使用JSON Schema标准格式

### 阶段2: Java集成（2-3天）

1. **实现配置加载器**
   - `ToolConfigLoader.java`
   - `ToolConfig.java` 数据模型
   - YAML解析（使用 SnakeYAML）
   - Markdown解析（使用 CommonMark）

2. **更新工具基类**
   - 修改 `McpTool` 接口
   - 添加配置访问方法
   - 保持向后兼容

3. **更新现有工具实现**
   - 移除硬编码的描述
   - 使用配置加载器
   - 验证功能正常

### 阶段3: 验证与测试（1天）

1. **单元测试**
   - 配置加载测试
   - Markdown解析测试
   - Schema验证测试

2. **集成测试**
   - MCP工具调用测试
   - 描述返回验证
   - 性能测试

3. **文档更新**
   - 更新开发文档
   - 添加配置示例
   - 编写迁移指南

---

## 依赖项

### Maven依赖

```xml
<!-- YAML解析 -->
<dependency>
    <groupId>org.yaml</groupId>
    <artifactId>snakeyaml</artifactId>
    <version>2.0</version>
</dependency>

<!-- Markdown解析 -->
<dependency>
    <groupId>org.commonmark</groupId>
    <artifactId>commonmark</artifactId>
    <version>0.21.0</version>
</dependency>

<!-- JSON Schema验证 -->
<dependency>
    <groupId>com.networknt</groupId>
    <artifactId>json-schema-validator</artifactId>
    <version>1.0.87</version>
</dependency>
```

---

## 优势总结

1. **保留丰富内容**: Markdown文件保留所有原始文档的详细信息
2. **结构化访问**: YAML提供程序化访问配置的能力
3. **易于维护**: 配置和文档分离，各司其职
4. **版本控制友好**: 所有配置文件可以独立版本管理
5. **支持国际化**: 可以为不同语言提供不同的描述文件
6. **性能优化**: 可以缓存解析后的配置
7. **灵活扩展**: 可以根据需要添加新的配置字段
8. **向后兼容**: 不破坏现有实现

---

## 下一步行动

**建议立即开始**:

1. ✅ 创建 `tool-configs.yml` 模板
2. ✅ 将现有Markdown文件整理到 `descriptions/` 目录
3. ✅ 实现 `ToolConfigLoader` 加载器
4. ✅ 更新一个工具作为试点（如 MetadataTool）
5. ✅ 验证试点工具功能
6. ✅ 逐步迁移其他工具

**预估时间**: 总计 4-6 天完成完整迁移
