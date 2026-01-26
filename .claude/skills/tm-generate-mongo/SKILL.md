---
name: tm-generate-mongo
description: 根据 MongoDB 集合信息生成 TM（表模型）文件。当用户需要为 MongoDB 集合创建数据模型、生成 mongo 类型的 .tm 文件时使用。
---

# MongoDB TM 生成器

根据用户输入为 Foggy Dataset Model 系统生成 MongoDB 类型的 TM（表模型）文件。

## 使用场景

当用户需要以下操作时使用本技能：
- 为 MongoDB 集合创建 TM 文件
- 将 MongoDB 文档结构转换为数据模型
- 创建 `type: 'mongo'` 的表模型定义

## 与 JDBC TM 的关键差异

| 特性 | MongoDB TM | JDBC TM |
|------|-----------|---------|
| type | 必须指定 `mongo` | 默认 `jdbc` |
| 维度 | **不支持** | 支持 |
| JOIN | **不支持** | 支持 |
| mongoTemplate | 必须指定 | 不需要 |
| idColumn | 通常为 `_id` | 代理键 |

## 输入类型

用户可能提供以下类型的输入：

1. **Java Document 类**：Spring Data MongoDB 的 `@Document` 注解类（推荐）
2. **集合描述**：集合及其字段的自然语言描述
3. **样本文档**：MongoDB 文档的 JSON 示例

### Java Document 类示例

```java
@Document(collection = "mcp_tool_audit_log")
public class McpAuditLog {
    @Id
    private String id;

    private String traceId;
    private String toolName;
    private LocalDateTime timestamp;
    private Long durationMs;
    private Boolean success;
    private String errorMessage;

    // getters/setters
}
```

从 Java 类提取信息：
- `@Document(collection = "xxx")` → `tableName`
- `@Id` 字段 → `idColumn`
- 字段名 → `column` 和 `name`
- Java 类型 → TM 类型（见类型映射规则）

## 输出要求

### 文件存放路径

**默认路径**（用户未指定时）：
```
src/main/resources/foggy/templates/model/mongo/{模型名称}Model.tm
```

**目录结构说明**：
```
src/main/resources/foggy/templates/
├── model/                    # TM 表模型目录
│   ├── mongo/               # MongoDB 模型
│   │   └── {Name}Model.tm
│   └── jdbc/                # JDBC 模型（可选分类）
│       └── {Name}Model.tm
├── query/                   # QM 查询模型目录（后续）
└── dicts.fsscript          # 字典定义
```

如果用户指定了其他路径，按用户指定的路径生成。

### 文件内容结构

```javascript
/**
 * {模型描述}
 *
 * @description MongoDB 文档模型 - {详细描述}
 */
import { mcpMongoTemplate } from './mongoTemplate.fsscript';

export const model = {
    name: '{模型名称}Model',
    caption: '{显示名称}',
    tableName: '{collection_name}',
    idColumn: '_id',
    type: 'mongo',
    mongoTemplate: mcpMongoTemplate,

    // MongoDB 模型没有维度（不做 join）
    // 所有字段都定义在 properties 中

    properties: [
        // 所有字段定义
    ],

    measures: [
        // 可聚合的数值字段
    ]
};
```

**注意**: 需要先创建 `mongoTemplate.fsscript` 文件（详见下方 "MongoTemplate 引用配置" 章节）。

**文件名**：`{模型名称}Model.tm`，与 `model.name` 相同。

## 类型映射规则

### Java 类型 → TM 类型

| Java 类型 | TM 类型 | 说明 |
|----------|---------|------|
| String | `STRING` | 文本、ID |
| Integer, int | `INTEGER` | 整数 |
| Long, long | `LONG` | 长整数 |
| BigDecimal | `MONEY` | 金额、精确小数 |
| Double, Float | `NUMBER` | 浮点数 |
| Boolean, boolean | `BOOL` | 布尔值 |
| LocalDateTime, Date | `DATETIME` | 时间戳 |
| LocalDate | `DAY` | 仅日期 |

### MongoDB 原生类型 → TM 类型

| MongoDB 类型 | TM 类型 | 使用场景 |
|-------------|---------|----------|
| String | `STRING` | 文本、ID |
| ObjectId | `STRING` | _id 字段 |
| Number (整数) | `INTEGER` / `LONG` | 计数、整数 |
| Number (浮点) | `NUMBER` / `MONEY` | 金额、小数 |
| Boolean | `BOOL` | 是/否标志 |
| Date | `DATETIME` | 时间戳 |

## 命名规范

- **模型名称**：PascalCase，以 `Model` 为后缀（如 `AuditLogModel`）
- **属性名称**：camelCase（如 `userId`、`createTime`）
- **集合名称**：snake_case（如 `mcp_tool_audit_log`）

## MongoTemplate 引用配置

### 创建引用文件

**文件路径**: `src/main/resources/foggy/templates/mongoTemplate.fsscript`

**正确示例** ✅:
```javascript
/**
 * MongoDB Template 配置
 *
 * @description 引用 Spring 容器中的默认 MongoTemplate Bean
 */
import '@mongoTemplate'

// 引用默认 mongoTemplate Bean
export const mcpMongoTemplate = mongoTemplate;
```

**错误示例** ❌:
```javascript
// ❌ 不要使用 beanRef() 函数
export const mcpMongoTemplate = beanRef('mongoTemplate');
```

### 引用语法说明

| 语法 | 说明 | 示例 |
|------|------|------|
| `import '@beanName'` | 导入 Spring Bean | `import '@mongoTemplate'` |
| 直接使用变量名 | 引用导入的 Bean | `export const myTemplate = mongoTemplate` |
| 自定义 Bean 名称 | 引用自定义的 MongoTemplate | `import '@customMongoTemplate'` |

### 在 TM 模型中使用

```javascript
import { mcpMongoTemplate } from './mongoTemplate.fsscript';

export const model = {
    name: 'VehicleStatusModel',
    caption: '车辆状态',
    tableName: 'vehicle_status',
    type: 'mongo',
    mongoTemplate: mcpMongoTemplate,  // ⬅️ 引用配置
    // ...
};
```

### Spring Boot 配置

确保 Spring Boot 配置文件中已正确配置 MongoDB 连接：

```yaml
spring:
  data:
    mongodb:
      host: localhost
      port: 27017
      database: your_database
      # username: user
      # password: pass
```

## 属性 vs 度量检测

**属性特征**（放入 properties）：
- 字符串/文本类型
- 日期/布尔类型
- 标识符字段（如 `_id`、`userId`）
- 状态、类型、类别字段

**度量特征**（放入 measures）：
- 数值类型且可聚合
- 字段名包含：`amount`、`count`、`total`、`duration`、`price`、`cost`
- 需要指定聚合方式：`sum`、`avg`、`count`、`max`、`min`

## 字典引用

对于枚举类型字段，可添加 dictRef：

```javascript
{
    column: 'status',
    name: 'status',
    caption: '状态',
    type: 'STRING',
    dictRef: dicts.status_dict
}
```

需要在 dicts.fsscript 中定义对应字典。

## 完整示例

对于 MCP 工具调用日志集合：

```javascript
/**
 * MCP 工具调用审计日志模型
 *
 * @description MongoDB 文档模型示例 - 用于记录和查询 MCP 工具调用日志
 */
import { mcpMongoTemplate } from './mongoTemplate.fsscript';
import { dicts } from '../dicts.fsscript';

export const model = {
    name: 'McpAuditLogModel',
    caption: 'MCP工具调用日志',
    tableName: 'mcp_tool_audit_log',
    idColumn: '_id',
    type: 'mongo',
    mongoTemplate: mcpMongoTemplate,

    // MongoDB 模型没有维度（不做 join）

    properties: [
        {
            column: '_id',
            name: 'id',
            caption: '日志ID',
            type: 'STRING'
        },
        {
            column: 'traceId',
            name: 'traceId',
            caption: 'AI会话ID',
            type: 'STRING',
            description: '一次完整AI执行的唯一标识'
        },
        {
            column: 'toolName',
            name: 'toolName',
            caption: '工具名称',
            type: 'STRING',
            dictRef: dicts.tool_name
        },
        {
            column: 'timestamp',
            name: 'timestamp',
            caption: '调用时间',
            type: 'DATETIME'
        },
        {
            column: 'durationMs',
            name: 'durationMs',
            caption: '执行耗时(ms)',
            type: 'LONG'
        },
        {
            column: 'success',
            name: 'success',
            caption: '是否成功',
            type: 'BOOL'
        },
        {
            column: 'errorMessage',
            name: 'errorMessage',
            caption: '错误信息',
            type: 'STRING'
        }
    ],

    measures: [
        {
            column: 'durationMs',
            name: 'avgDuration',
            caption: '平均耗时',
            type: 'LONG',
            aggregation: 'avg'
        },
        {
            column: 'durationMs',
            name: 'maxDuration',
            caption: '最大耗时',
            type: 'LONG',
            aggregation: 'max'
        }
    ]
};
```

## 输出前检查清单

- [ ] `type: 'mongo'` 已设置
- [ ] `mongoTemplate` 已指定并有对应 import
- [ ] `idColumn` 已设置（通常为 `_id`）
- [ ] **没有** dimensions 定义（MongoDB 不支持）
- [ ] 所有字段都在 properties 中定义
- [ ] 数值聚合字段在 measures 中定义
- [ ] 所有字段都有 caption
- [ ] 枚举字段建议添加 dictRef

## 操作步骤

1. **分析用户输入**：确定是集合描述、样本文档还是集合名
2. **确认 mongoTemplate**：询问用户使用哪个 MongoTemplate（如有多个）
3. **识别字段**：区分属性和度量
4. **生成 TM 文件**：按照模板结构输出完整的 .tm 文件
5. **验证输出**：对照检查清单确保完整性

## 约束条件

- MongoDB TM **不支持维度**（dimensions 必须为空或不定义）
- MongoDB TM **不支持 JOIN**（只能查询单个集合）
- 必须指定 `type: 'mongo'`
- 必须指定 `mongoTemplate`
- 集合名使用 `tableName` 字段

## 决策规则

- 如果用户要求添加维度 → 提示 MongoDB 模型不支持维度，建议使用 JDBC 模型或在应用层处理
- 如果用户要求 JOIN 多个集合 → 提示 MongoDB 模型不支持 JOIN
- 如果用户未指定 mongoTemplate → 询问使用哪个 MongoTemplate Bean
- 如果字段既是属性又需要聚合 → 在 properties 和 measures 中都定义（使用不同的 name）
