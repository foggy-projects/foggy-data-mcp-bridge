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
}
```

**从 Java 类提取信息**：
- `@Document(collection = "xxx")` → `tableName`
- `@Id` 字段 → `idColumn`
- 字段名 → `column` 和 `name`
- Java 类型 → TM 类型（见类型映射规则）

## 执行流程

### 1. 解析 Java Document 类

提取关键信息：
- 集合名称（`@Document(collection="...")`）
- 主键字段（`@Id` 注解）
- 字段列表及类型

### 2. 应用 TM 语法规则

使用 `tm-syntax-reference` 技能中的规则：

- **类型映射**：`String` → `STRING`、`BigDecimal` → `MONEY`、`LocalDateTime` → `DATETIME`
- **Name 字段简化**：
  - 单层驼峰字段：`merchantCode` → 省略 name
  - 嵌套字段自动拼接：`data.orderCount` → 省略 name（自动转为 `dataOrderCount`）
  - `_id` 字段：必须指定 `name: 'id'`
- **Measures 设计**：不为同一字段创建多个聚合版本

**详细规则见**：`tm-syntax-reference` 技能

### 3. 生成 TM 文件

**文件路径**（用户未指定时）：
```
src/main/resources/foggy/templates/model/mongo/{模型名称}Model.tm
```

**文件结构**：
```javascript
/**
 * {模型描述}
 * @description MongoDB 文档模型 - {详细描述}
 */
import { mcpMongoTemplate } from './mongoTemplate.fsscript';

export const model = {
    name: 'McpAuditLogModel',
    caption: 'MCP工具调用日志',
    tableName: 'mcp_tool_audit_log',
    idColumn: '_id',
    type: 'mongo',                  // ✅ 必须指定
    mongoTemplate: mcpMongoTemplate, // ✅ 必须配置

    // MongoDB 模型没有维度（不做 join）

    properties: [
        {
            column: '_id',
            name: 'id',              // ✅ 必须指定
            caption: '日志ID',
            type: 'STRING'
        },
        {
            column: 'traceId',       // ✅ 省略 name
            caption: 'AI会话ID',
            type: 'STRING'
        }
    ],

    measures: [
        {
            column: 'durationMs',
            caption: '耗时(ms)',
            type: 'LONG',
            aggregation: 'avg'
        }
    ]
};
```

### 4. 验证输出

对照检查清单：
- [ ] `type: 'mongo'` 已设置
- [ ] `mongoTemplate` 已指定并有对应 import
- [ ] `idColumn` 已设置（通常为 `_id`）
- [ ] **没有** dimensions 定义（MongoDB 不支持）
- [ ] 所有字段都在 properties 中定义
- [ ] 数值聚合字段在 measures 中定义
- [ ] 所有字段都有 caption
- [ ] `_id` 字段指定了 `name: 'id'`

## MongoDB 专属规则

### 1. 不支持 Dimensions/JOIN

```javascript
dimensions: []  // ❌ MongoDB 模型不支持维度

// 所有字段定义在 properties 中
properties: [
    { column: 'customerId', caption: '客户ID', type: 'STRING' },
    { column: 'customerName', caption: '客户名称', type: 'STRING' }
]
```

### 2. Name 字段规则

**嵌套字段自动拼接**：
```javascript
properties: [
    {
        column: 'data.orderCount',  // ✅ 省略 name，自动转为 dataOrderCount
        caption: '订单数',
        type: 'INTEGER'
    },
    {
        column: 'location.lng',     // ✅ 省略 name，自动转为 locationLng
        caption: '经度',
        type: 'NUMBER'
    }
]
```

**_id 字段必须指定 name**：
```javascript
{
    column: '_id',
    name: 'id',                     // ✅ 必须指定
    caption: '文档ID',
    type: 'STRING'
}
```

### 3. 必须配置 mongoTemplate

MongoDB 模型必须指定 `mongoTemplate`，引用 Spring Bean。

## MongoTemplate 引用配置

### 创建 mongoTemplate.fsscript

**文件路径**: `src/main/resources/foggy/templates/mongoTemplate.fsscript`

**正确示例** ✅:
```javascript
/**
 * MongoDB Template 配置
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

确保配置文件中已正确配置 MongoDB 连接：

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

## 完整示例

```javascript
/**
 * MCP 工具调用审计日志模型
 * @description MongoDB 文档模型 - 用于记录和查询 MCP 工具调用日志
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

    properties: [
        {
            column: '_id',
            name: 'id',
            caption: '日志ID',
            type: 'STRING'
        },
        {
            column: 'traceId',
            caption: 'AI会话ID',
            type: 'STRING',
            description: '一次完整AI执行的唯一标识'
        },
        {
            column: 'toolName',
            caption: '工具名称',
            type: 'STRING',
            dictRef: dicts.tool_name
        },
        {
            column: 'timestamp',
            caption: '调用时间',
            type: 'DATETIME'
        },
        {
            column: 'success',
            caption: '是否成功',
            type: 'BOOL'
        },
        {
            column: 'errorMessage',
            caption: '错误信息',
            type: 'STRING'
        }
    ],

    measures: [
        {
            column: 'durationMs',
            caption: '耗时(ms)',
            type: 'LONG',
            aggregation: 'avg'
        }
    ]
};
```

## 决策规则

- 如用户提供 Java Document 类 → 解析注解和字段
- 如用户提供 JSON 示例 → 推断字段类型
- 如用户要求添加维度 → 提示 MongoDB 模型不支持维度，建议应用层处理
- 如用户要求 JOIN 多个集合 → 提示 MongoDB 模型不支持 JOIN
- 如用户未指定 mongoTemplate → 询问使用哪个 MongoTemplate Bean

## 约束条件

- MongoDB TM **不支持维度**（dimensions 必须为空或不定义）
- MongoDB TM **不支持 JOIN**（只能查询单个集合）
- 必须指定 `type: 'mongo'`
- 必须指定 `mongoTemplate`
- 集合名使用 `tableName` 字段
- `_id` 字段必须指定 `name: 'id'`

## 参考文档

详细语法规则、类型映射、高级特性请参考：
- **核心语法**：`tm-syntax-reference` 技能
- **完整手册**：[TM 语法手册](https://foggy-projects.github.io/foggy-data-mcp-bridge/zh/dataset-model/tm-qm/tm-syntax.html)
