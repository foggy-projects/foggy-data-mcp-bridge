---
name: tm-syntax-reference
description: Foggy TM 语法参考手册。当需要了解 TM 类型映射、Name 字段规则、Measures 设计原则、维度定义语法时使用。
allowed-tools: Read
---

# TM 语法参考手册

TM（Table Model）语法核心规则，涵盖 80% 常见场景。高级特性见 references/ 目录。

## 1. 类型映射规则

### JDBC 数据库类型 → TM 类型

| 数据库类型 | TM 类型 | Java 类型 | 使用场景 |
|-----------|---------|-----------|----------|
| VARCHAR, TEXT, CHAR | `STRING` | String | 文本、编码、ID |
| INT, SMALLINT | `INTEGER` | Integer | 计数、小数字、枚举 |
| BIGINT | `BIGINT` / `LONG` | Long | 大数字、代理键 |
| DECIMAL, NUMERIC, MONEY | `MONEY` | BigDecimal | 金额、价格（精确小数） |
| DATE | `DAY` | Date | 仅日期（yyyy-MM-dd） |
| DATETIME, TIMESTAMP | `DATETIME` | Date | 时间戳 |
| BOOLEAN, TINYINT(1) | `BOOL` | Boolean | 是/否标志 |

### MongoDB 类型 → TM 类型

| MongoDB 类型 | TM 类型 | Java 类型 | 使用场景 |
|-------------|---------|-----------|----------|
| String | `STRING` | String | 文本、ID |
| ObjectId | `STRING` | String | _id 字段 |
| Number (整数) | `INTEGER` / `LONG` | Integer/Long | 计数、整数 |
| Number (浮点) | `NUMBER` / `MONEY` | Double/BigDecimal | 金额、小数 |
| Boolean | `BOOL` | Boolean | 是/否标志 |
| Date | `DATETIME` | Date | 时间戳 |

## 2. Name 字段简化规则 ⚠️

**核心原则**：name 和 column 自动转换一致时，省略 name 字段。

### JDBC 数据源（自动转驼峰）

**自动转换规则**：
- `order_count` → 自动转为 `orderCount`
- `customer_name` → 自动转为 `customerName`
- `created_at` → 自动转为 `createdAt`

**正确做法** ✅：
```javascript
properties: [
    {
        column: 'order_count',   // ✅ 省略 name，自动转为 orderCount
        caption: '订单数',
        type: 'INTEGER'
    }
]
```

**错误做法** ❌：
```javascript
properties: [
    {
        column: 'order_count',
        name: 'orderCount',      // ❌ 冗余，系统自动转换
        caption: '订单数',
        type: 'INTEGER'
    }
]
```

**何时需要 name**：
```javascript
properties: [
    {
        column: 'cnt',
        name: 'orderCount',      // ✅ 需要，因为 cnt 不会自动转为 orderCount
        caption: '订单总数',
        type: 'INTEGER'
    }
]
```

### MongoDB 数据源（嵌套字段自动拼接）

**自动转换规则**：
- `merchantCode` → 保持 `merchantCode`
- `data.orderCount` → 自动转为 `dataOrderCount`
- `location.coordinates` → 自动转为 `locationCoordinates`

**特殊字段** `_id`：
```javascript
{
    column: '_id',
    name: 'id',              // ✅ 必须指定
    caption: '文档ID',
    type: 'STRING'
}
```

### 决策规则

**省略 name 的场景**（推荐）：
- JDBC 蛇形命名自动转驼峰：`order_count` → 省略
- MongoDB 单层驼峰字段：`merchantCode` → 省略
- MongoDB 嵌套字段自动拼接：`data.orderCount` → 省略

**必须指定 name 的场景**：
1. MongoDB `_id` 映射为 `id`
2. 需要自定义名称：`column: 'cnt'` → `name: 'orderCount'`
3. 用户明确要求特定名称

## 3. Measures 设计原则 ⚠️

**核心理念**：同一字段支持明细查询和聚合查询两种模式。

### 错误做法（传统数仓）❌

```javascript
measures: [
    { column: 'routeCount', name: 'totalRouteCount', caption: '总路线数', type: 'INTEGER', aggregation: 'sum' },
    { column: 'routeCount', name: 'avgRouteCount', caption: '平均路线数', type: 'INTEGER', aggregation: 'avg' },
    { column: 'routeCount', name: 'maxRouteCount', caption: '最大路线数', type: 'INTEGER', aggregation: 'max' }
]
```

### 正确做法 ✅

```javascript
measures: [
    {
        column: 'routeCount',
        caption: '路线数',        // ✅ 不添加 total/avg 前缀
        type: 'INTEGER',
        aggregation: 'sum'       // ✅ 默认聚合方式
    }
]
```

**原因**：
- aggregation 仅作为默认聚合方式
- 查询时可动态覆盖（sum/avg/max/min/count）
- 避免为每种聚合方式预定义字段

### 常见度量聚合方式

| 业务场景 | 默认聚合 | 说明 |
|---------|---------|------|
| 金额（salesAmount, totalPrice） | sum | 求和 |
| 数量（quantity, count） | sum | 求和 |
| 百分比（batteryLevel, progress） | avg | 平均值 |
| 速度/频率（speed, frequency） | avg | 平均值 |
| 距离/时长（distance, duration） | sum | 求和 |

## 4. 基本维度定义

### JDBC 维度（支持 JOIN）

```javascript
dimensions: [
    {
        name: 'customer',              // 维度名称
        caption: '客户',                // 显示名称
        description: '购买商品的客户',   // AI 描述

        tableName: 'dim_customer',     // 维度表名
        foreignKey: 'customer_key',    // 本表外键
        primaryKey: 'customer_key',    // 维度表主键
        captionColumn: 'customer_name', // 显示字段

        properties: [                   // 维度属性
            { column: 'customer_id', caption: '客户ID' },
            { column: 'province', caption: '省份' },
            { column: 'city', caption: '城市' }
        ]
    }
]
```

### MongoDB 模型（不支持维度）

```javascript
dimensions: []  // MongoDB 模型不支持 dimensions

// 所有字段定义在 properties 中
properties: [
    { column: 'customerId', caption: '客户ID', type: 'STRING' },
    { column: 'customerName', caption: '客户名称', type: 'STRING' }
]
```

## 5. 维度复用（推荐）

绝大部分维度需要复用，使用维度构建器：

```javascript
import { buildDateDim, buildCustomerDim, buildProductDim } from '../dimensions/common-dims.fsscript';

export const model = {
    name: 'FactSalesModel',
    // ...
    dimensions: [
        buildDateDim({ name: 'salesDate', caption: '销售日期' }),
        buildCustomerDim(),
        buildProductDim()
    ]
};
```

**创建维度构建器**见 `references/dimension-reuse.md`

## 6. 命名规范

### 文件命名

- 事实表：`Fact{业务名}Model.tm`（如 `FactSalesModel.tm`）
- 维度表：`Dim{业务名}Model.tm`（如 `DimCustomerModel.tm`）
- MongoDB 模型：`{业务名}Model.tm`（如 `AuditLogModel.tm`）

### 字段命名

| 位置 | 规范 | 示例 |
|------|------|------|
| 模型 name | PascalCase | `FactSalesModel`, `DimCustomerModel` |
| 字段 name | camelCase | `orderId`, `salesAmount`, `customerType` |
| 数据库 column | snake_case (JDBC) | `order_id`, `sales_amount` |
| MongoDB column | camelCase | `orderId`, `salesAmount` |

## 7. 基本模型结构

### JDBC 事实表模板

```javascript
export const model = {
    name: 'FactSalesModel',
    caption: '销售事实表',
    tableName: 'fact_sales',
    idColumn: 'sales_key',
    // type: 'jdbc' 可省略（默认）

    dimensions: [
        // 维度定义
    ],

    properties: [
        // 不可聚合字段
    ],

    measures: [
        // 可聚合数值字段
    ]
};
```

### MongoDB 模型模板

```javascript
import { mcpMongoTemplate } from './mongoTemplate.fsscript';

export const model = {
    name: 'AuditLogModel',
    caption: 'MCP审计日志',
    tableName: 'mcp_tool_audit_log',
    idColumn: '_id',
    type: 'mongo',                  // ✅ 必须指定
    mongoTemplate: mcpMongoTemplate, // ✅ 必须配置

    // dimensions: [],              // ❌ MongoDB 不支持

    properties: [
        { column: '_id', name: 'id', caption: 'ID', type: 'STRING' },
        // 其他字段
    ],

    measures: [
        // 可聚合字段
    ]
};
```

## 8. 高级特性扩展文档

当遇到以下场景时，使用 `Read` 工具读取对应扩展文档：

| 场景 | 扩展文档 |
|------|---------|
| 雪花模型（商品→品类→品类组） | `references/nested-dimensions.md` |
| 组织架构/树形结构 | `references/parent-child-dimensions.md` |
| JSON 提取/字符串拼接/复杂计算 | `references/calculated-fields.md` |
| 创建自定义维度构建器 | `references/dimension-reuse.md` |
| 向量检索模型（Milvus） | `references/vector-model.md` |

## 9. 常见检查清单

生成 TM 文件前检查：

- [ ] 模型名称遵循命名规范（Fact*/Dim* 前缀）
- [ ] 所有字段都有 caption
- [ ] 类型映射正确（金额用 MONEY，日期用 DAY/DATETIME）
- [ ] Name 字段按规则省略（避免冗余）
- [ ] Measures 不为同一字段创建多个聚合版本
- [ ] 已识别并配置维度（JDBC 模型）
- [ ] MongoDB 模型设置 type='mongo' 和 mongoTemplate
- [ ] 枚举字段建议添加 dictRef
- [ ] 重要字段提供 description（供 AI 使用）

## 决策规则

- 如需嵌套维度 → 读取 `references/nested-dimensions.md`
- 如需父子维度 → 读取 `references/parent-child-dimensions.md`
- 如需计算字段 → 读取 `references/calculated-fields.md`
- 如需创建维度构建器 → 读取 `references/dimension-reuse.md`
- 如需向量模型 → 读取 `references/vector-model.md`
