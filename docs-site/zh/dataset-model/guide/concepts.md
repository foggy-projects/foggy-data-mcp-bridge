# 核心概念

本文档介绍 Foggy Dataset Model 的核心概念，帮助你理解 TM、QM 和 DSL 查询的关系。

## 语义层（Semantic Layer）

Foggy Dataset Model 是一个**嵌入式语义层框架**，它在数据库和应用层之间提供一个抽象层：

```
┌─────────────────────────────────────────────────────────┐
│                     应用层 / 前端                        │
│                  (使用 JSON DSL 查询)                    │
└─────────────────────────────────────────────────────────┘
                           ↓ DSL 查询
┌─────────────────────────────────────────────────────────┐
│                    Foggy Dataset Model                   │
│  ┌─────────────┐   ┌─────────────┐   ┌─────────────┐   │
│  │     TM      │ → │     QM      │ → │  Query API  │   │
│  │ (表格模型)   │   │ (查询模型)  │   │ (查询引擎)   │   │
│  └─────────────┘   └─────────────┘   └─────────────┘   │
└─────────────────────────────────────────────────────────┘
                           ↓ SQL
┌─────────────────────────────────────────────────────────┐
│                       数据库                             │
│         MySQL / PostgreSQL / SQL Server / mongo         │
└─────────────────────────────────────────────────────────┘
```

**核心价值**：
- 前端无需知道数据库结构，只需按业务语义查询
- 自动处理多表 JOIN，简化复杂查询
- 统一权限控制，保障数据安全
- 屏蔽数据库差异，支持多数据库

---

## TM - 表格模型（Table Model）

TM 是数据模型的定义文件（`.tm`），用于描述数据库表的结构和关联关系。

### TM 的作用

1. **表结构映射**：将数据库表映射为业务模型
2. **维度定义**：定义与其他表的关联（自动 JOIN）
3. **度量定义**：定义可聚合的数值字段
4. **属性定义**：定义表中的普通字段

### TM 的核心概念

#### 事实表（Fact Table）

事实表是业务的核心数据表，存储业务事件记录，如订单、销售、交易等。

```javascript
// FactOrderModel.tm
export const model = {
    name: 'FactOrderModel',
    tableName: 'fact_order',      // 事实表
    dimensions: [...],            // 关联的维度
    measures: [...]               // 可聚合的度量
};
```

#### 维度表（Dimension Table）

维度表存储业务实体的描述信息，如客户、商品、时间等。

```javascript
// DimCustomerModel.tm
export const model = {
    name: 'DimCustomerModel',
    tableName: 'dim_customer',    // 维度表
    properties: [...]             // 维度属性
};
```

#### 维度（Dimension）

维度定义事实表与维度表的关联关系，查询时自动生成 JOIN。

```javascript
dimensions: [
    {
        name: 'customer',
        tableName: 'dim_customer',
        foreignKey: 'customer_id',    // 事实表外键
        primaryKey: 'customer_id',    // 维度表主键
        captionColumn: 'customer_name'
    }
]
```

#### 度量（Measure）

度量是可聚合的数值字段，如销售金额、订单数量等。
注意，虽然这里配置聚合方式为sum，但DSL查询时，还是要加入groupBy或sum函数才可以

```javascript
measures: [
    {
        column: 'amount',
        caption: '销售金额',
        type: 'MONEY',
        aggregation: 'sum'    // 默认求和聚合
    }
]
```

#### 属性（Property）

属性是表中的普通字段，不参与聚合。

```javascript
properties: [
    {
        column: 'order_id',
        caption: '订单ID',
        type: 'STRING'
    }
]
```

---

## QM - 查询模型（Query Model）

QM 是查询视图的定义文件（`.qm`），基于一个或多个 TM 定义可查询的字段和权限。

### QM 的作用

1. **字段暴露**：控制哪些字段可以被查询
2. **权限控制**：控制可以查询的数据
3. **UI 配置**：定义字段的显示方式
4. **默认排序**：定义查询的默认排序

### QM 的核心概念

#### 列组（Column Groups）

将可查询的字段分组，便于 UI 展示。

```javascript
columnGroups: [
    {
        caption: '订单信息',
        items: [
            { name: 'orderId' },
            { name: 'orderStatus' }
        ]
    },
    {
        caption: '客户信息',
        items: [
            { name: 'customer$caption' },   // 维度显示值
            { name: 'customer$customerType' }
        ]
    }
]
```

## DSL 查询

DSL（Domain Specific Language）是用于查询数据的 JSON 格式语言。前端通过 DSL 向 QM 发起查询请求。

### DSL 查询流程

```
┌──────────────┐    JSON DSL    ┌──────────────┐    SQL    ┌──────────────┐
│    前端      │  ───────────→  │   Query API  │  ──────→  │    数据库    │
│  (DSL 查询)  │                │   (QM 引擎)  │           │             │
└──────────────┘                └──────────────┘           └──────────────┘
                                       ↓
                                 查询结果 (JSON)
```

### DSL 核心语法

#### 查询列（columns）

指定要查询的字段：

```json
{
    "columns": ["orderId", "customer$caption", "amount"]
}
```

#### 过滤条件（slice）

指定查询条件：

```json
{
    "slice": [
        { "field": "orderStatus", "op": "=", "value": "COMPLETED" },
        { "field": "amount", "op": ">=", "value": 100 }
    ]
}
```

#### 分组（groupBy）

按字段分组聚合：

```json
{
    "groupBy": [
        { "field": "customer$customerType" }
    ]
}
```

#### 排序（orderBy）

指定排序：

```json
{
    "orderBy": [
        { "field": "field", "order": "desc" }
    ]
}
```

---

## 字段引用格式

在 DSL 查询中，使用统一的字段引用格式：

| 格式 | 说明 | 示例 |
|------|------|------|
| `属性名` | 事实表属性 | `orderId`, `orderStatus` |
| `度量名` | 度量字段 | `amount`, `quantity` |
| `维度名$caption` | 维度显示值 | `customer$caption` |
| `维度名$id` | 维度 ID | `customer$id` |
| `维度名$属性名` | 维度属性 | `customer$customerType` |

**示例**：

```json
{
    "columns": [
        "orderId",              // 事实表属性
        "amount",          // 度量
        "customer$caption",     // 客户名称
        "customer$province",    // 客户省份
        "orderDate$year"        // 订单年份
    ]
}
```

---

## TM、QM、DSL 的关系

```
┌────────────────────────────────────────────────────────────────┐
│                          TM (表格模型)                          │
│  ┌──────────────┐    ┌──────────────┐    ┌──────────────┐     │
│  │ FactOrder.tm │    │ DimCustomer.tm│    │ DimProduct.tm│     │
│  └──────────────┘    └──────────────┘    └──────────────┘     │
│         ↓                   ↓                   ↓              │
│         └───────────────────┴───────────────────┘              │
│                            ↓                                   │
│                    ┌──────────────┐                           │
│                    │   QM (查询模型)│                           │
│                    │ OrderQuery.qm│                           │
│                    └──────────────┘                           │
│                            ↓                                   │
│                    ┌──────────────┐                           │
│                    │  DSL 查询请求 │                           │
│                    │   (JSON)     │                           │
│                    └──────────────┘                           │
└────────────────────────────────────────────────────────────────┘
```

1. **TM 定义数据结构**：表、字段、关联关系
2. **QM 定义查询视图**：基于 TM，定义可查询的内容和权限
3. **DSL 发起查询**：基于 QM，使用 JSON 格式查询数据

---

## 模型类型

### 星型模型（Star Schema）

事实表直接关联维度表，查询时自动生成一层 JOIN。

```
                    ┌─────────────┐
                    │ dim_customer│
                    └──────┬──────┘
                           │
┌─────────────┐    ┌───────┴───────┐    ┌─────────────┐
│ dim_product │────│  fact_order   │────│  dim_date   │
└─────────────┘    └───────────────┘    └─────────────┘
```

### 雪花模型（Snowflake Schema）

维度表可以继续关联子维度表，形成多层结构。

```
                    ┌─────────────┐
                    │ dim_category│
                    └──────┬──────┘
                           │
                    ┌──────┴──────┐
                    │ dim_product │
                    └──────┬──────┘
                           │
                    ┌──────┴──────┐
                    │ fact_order  │
                    └─────────────┘
```

### 父子维度（Parent-Child Dimension）

用于处理层级结构数据，如组织架构、商品分类等。通过闭包表实现高效的层级查询。

---

## 下一步

- [快速开始](./quick-start.md) - 创建第一个 TM/QM 并使用 DSL 查询
- [TM 语法手册](../tm-qm/tm-syntax.md) - 完整的 TM 定义语法
- [QM 语法手册](../tm-qm/qm-syntax.md) - 完整的 QM 定义语法
- [DSL 查询 API](../api/query-api.md) - 完整的 DSL 查询语法
