# Introduction

Foggy Dataset Model is an **embedded semantic layer framework** that implements declarative data modeling using FSScript (JavaScript-like syntax), deeply integrates with Spring, and allows frontend or upstream systems to safely access databases using DSL (JSON format).

## Core Features

### TM (Table Model) - Table Model

Maps database tables to semantic data models, defining dimensions, measures, and properties:

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
        caption: 'Order ID'
    },{
        column: 'date_key',
        caption: 'Order Date'
    }],

    measures: [
        { column: 'amount', caption: 'Order Amount', aggregation: 'sum' }
    ]
};
```

### QM (Query Model) - Query Model

Defines query views based on TM, controlling model visible fields, etc.:

```javascript
export const queryModel = {
    name: 'FactOrderQueryModel',
    model: 'FactOrderModel',

    columnGroups: [
        {
            caption: 'Order Info',
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

### DSL Query

Query data using JSON format DSL without writing SQL:

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

## Core Advantages

| Feature | Description |
|---------|-------------|
| **Auto JOIN** | Automatically generates JOIN statements based on dimension definitions |
| **Secure Query** | DSL parameterized queries prevent SQL injection |
| **Multi-Database Support** | MySQL, PostgreSQL, SQL Server, SQLite, MongoDB |
| **Declarative Modeling** | Uses FSScript syntax, concise and maintainable |

## Architecture Overview

```
┌─────────────────────────────────────────────────────────┐
│                  Frontend / Upstream System              │
│                     (JSON DSL Query)                     │
└─────────────────────────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────┐
│                   Foggy Dataset Model                    │
│                                                          │
│   ┌──────────┐    ┌──────────┐    ┌──────────────────┐  │
│   │   TM     │ →  │   QM     │ →  │   Query Engine   │  │
│   │Table Model│    │Query Model│    │                  │  │
│   └──────────┘    └──────────┘    └──────────────────┘  │
└─────────────────────────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────┐
│                        Database                          │
│         MySQL / PostgreSQL / SQL Server / MongoDB        │
└─────────────────────────────────────────────────────────┘
```

## Use Cases

- **Reporting Systems**: Multi-dimensional queries, group summaries, KPI statistics
- **Data Analysis Platforms**: Flexible dynamic query conditions
- **BI Tool Backends**: Semantic layer abstraction, hiding database details
- **Microservice Data Gateways**: Unified data query interface

## Quick Links

- [Quick Start](./quick-start.md) - Create TM/QM and use DSL queries in 10 minutes
- [Core Concepts](./concepts.md) - Understand the relationship between TM, QM, and DSL
- [TM Syntax Manual](../tm-qm/tm-syntax.md) - Complete TM definition syntax
- [QM Syntax Manual](../tm-qm/qm-syntax.md) - Complete QM definition syntax
- [DSL Query API](../api/query-api.md) - Complete DSL query reference
