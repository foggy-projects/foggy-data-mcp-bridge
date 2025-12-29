# 简介

Foggy Dataset Model 是一个**嵌入式语义层框架**，基于类 JavaScript 语法的 FSScript 实现声明式数据建模，深度集成 Spring，使得前端或上游可以使用 DSL（JSON 格式）安全地访问数据库。

## 核心特性

### TM (Table Model) - 表格模型

将数据库表映射为语义化的数据模型，定义维度、度量和属性：

```javascript
export const model = {
    name: 'FactOrderModel',
    tableName: 'fact_order',
    idColumn: 'order_id',
    dimensions: [
        {
            name: 'customer',
            tableName: 'dim_customer',
            foreignKey: 'customer_id',
            primaryKey: 'customer_id',
            captionColumn: 'customer_name'
        }
    ],
    properties: [{
        column: 'order_id',
        caption: '订单ID'
    },{
        column: 'date_key',
        caption: '下单时间'
    }],

    measures: [
        { column: 'amount', caption: '订单金额', aggregation: 'sum' }
    ]
};
```

### QM (Query Model) - 查询模型

基于 TM 定义查询视图，控制模型可见字段等：

```javascript
export const queryModel = {
    name: 'FactOrderQueryModel',
    model: 'FactOrderModel',

    columnGroups: [
        {
            caption: '订单信息',
            items: [
                { name: 'orderId' },
                { name: 'dateKey' },
                { name: 'customer$caption' },
                { name: 'amount' }
            ]
        }
    ]
};
```

### DSL 查询

使用 JSON 格式的 DSL 查询数据，无需编写 SQL：

```json
{
    "page": 1,
    "pageSize": 20,
    "param": {
        "columns": [ "customer$caption", "amount"],
        "slice": [
            { "field": "orderStatus", "op": "=", "value": "COMPLETED" }
        ],
        "groupBy": [
            { "field": "customer$customerType" }
        ]
    }
}
```

## 核心优势

| 特性 | 说明 |
|------|------|
| **自动 JOIN** | 根据维度定义自动生成 JOIN 语句 |
| **安全查询** | DSL 参数化查询，防止 SQL 注入 |
| **多数据库支持** | MySQL、PostgreSQL、SQL Server、SQLite、MongoDB |
| **声明式建模** | 使用 FSScript 语法，简洁易维护 |

## 架构概览

```
┌─────────────────────────────────────────────────────────┐
│                     前端 / 上游系统                       │
│                    (JSON DSL 查询)                       │
└─────────────────────────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────┐
│                   Foggy Dataset Model                    │
│                                                          │
│   ┌──────────┐    ┌──────────┐    ┌──────────────────┐  │
│   │   TM     │ →  │   QM     │ →  │   Query Engine   │  │
│   │ 表格模型  │    │ 查询模型  │    │     查询引擎     │  │
│   └──────────┘    └──────────┘    └──────────────────┘  │
└─────────────────────────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────┐
│                        数据库                            │
│         MySQL / PostgreSQL / SQL Server / MongoDB        │
└─────────────────────────────────────────────────────────┘
```

## 适用场景

- **报表系统**：多维度查询、分组汇总、KPI 统计
- **数据分析平台**：灵活的动态查询条件
- **BI 工具后端**：语义层抽象，屏蔽数据库细节
- **微服务数据网关**：统一的数据查询接口

## 快速链接

- [快速开始](./quick-start.md) - 10 分钟创建 TM/QM 并使用 DSL 查询
- [核心概念](./concepts.md) - 理解 TM、QM、DSL 的关系
- [TM 语法手册](../tm-qm/tm-syntax.md) - 完整的 TM 定义语法
- [QM 语法手册](../tm-qm/qm-syntax.md) - 完整的 QM 定义语法
- [DSL 查询 API](../api/query-api.md) - 完整的 DSL 查询参考
