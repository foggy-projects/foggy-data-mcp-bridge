# Core Concepts

This document introduces the core concepts of Foggy Dataset Model, helping you understand the relationship between TM, QM, and DSL queries.

## Semantic Layer

Foggy Dataset Model is an **embedded semantic layer framework** that provides an abstraction layer between the database and application layer:

```
┌─────────────────────────────────────────────────────────┐
│                  Application Layer / Frontend            │
│                   (Using JSON DSL Query)                 │
└─────────────────────────────────────────────────────────┘
                           ↓ DSL Query
┌─────────────────────────────────────────────────────────┐
│                    Foggy Dataset Model                   │
│  ┌─────────────┐   ┌─────────────┐   ┌─────────────┐   │
│  │     TM      │ → │     QM      │ → │  Query API  │   │
│  │(Table Model)│   │(Query Model)│   │(Query Engine)│   │
│  └─────────────┘   └─────────────┘   └─────────────┘   │
└─────────────────────────────────────────────────────────┘
                           ↓ SQL
┌─────────────────────────────────────────────────────────┐
│                        Database                          │
│         MySQL / PostgreSQL / SQL Server / MongoDB        │
└─────────────────────────────────────────────────────────┘
```

**Core Value**:
- Frontend doesn't need to know database structure, only query by business semantics
- Automatic multi-table JOIN handling, simplifying complex queries
- Unified permission control, ensuring data security
- Hiding database differences, supporting multiple databases

---

## TM - Table Model

TM is the data model definition file (`.tm`), used to describe the structure and relationships of database tables.

### TM Purpose

1. **Table Structure Mapping**: Map database tables to business models
2. **Dimension Definition**: Define relationships with other tables (auto JOIN)
3. **Measure Definition**: Define aggregatable numeric fields
4. **Property Definition**: Define regular fields in tables

### TM Core Concepts

#### Fact Table

Fact tables are the core data tables of business, storing business event records such as orders, sales, transactions, etc.

```javascript
// FactOrderModel.tm
export const model = {
    name: 'FactOrderModel',
    tableName: 'fact_order',      // Fact table
    dimensions: [...],            // Related dimensions
    measures: [...]               // Aggregatable measures
};
```

#### Dimension Table

Dimension tables store descriptive information about business entities, such as customers, products, dates, etc.

```javascript
// DimCustomerModel.tm
export const model = {
    name: 'DimCustomerModel',
    tableName: 'dim_customer',    // Dimension table
    properties: [...]             // Dimension properties
};
```

#### Dimension

Dimensions define the relationship between fact tables and dimension tables, automatically generating JOIN during queries.

```javascript
dimensions: [
    {
        name: 'customer',
        tableName: 'dim_customer',
        foreignKey: 'customer_id',    // Fact table foreign key
        primaryKey: 'customer_id',    // Dimension table primary key
        captionColumn: 'customer_name'
    }
]
```

#### Measure

Measures are aggregatable numeric fields, such as sales amount, order quantity, etc.
Note: Although aggregation method is configured as sum here, you still need to add groupBy or sum function in DSL queries.

```javascript
measures: [
    {
        column: 'amount',
        caption: 'Sales Amount',
        type: 'MONEY',
        aggregation: 'sum'    // Default sum aggregation
    }
]
```

#### Property

Properties are regular fields in tables that don't participate in aggregation.

```javascript
properties: [
    {
        column: 'order_id',
        caption: 'Order ID',
        type: 'STRING'
    }
]
```

---

## QM - Query Model

QM is the query view definition file (`.qm`), defining queryable fields and permissions based on one or more TMs.

### QM Purpose

1. **Field Exposure**: Control which fields can be queried
2. **Permission Control**: Control queryable data
3. **UI Configuration**: Define field display methods

---

## Model Types

### Star Schema

Fact tables directly relate to dimension tables, automatically generating one-level JOIN during queries.

```
                    ┌─────────────┐
                    │ dim_customer│
                    └──────┬──────┘
                           │
┌─────────────┐    ┌───────┴───────┐    ┌─────────────┐
│ dim_product │────│  fact_order   │────│  dim_date   │
└─────────────┘    └───────────────┘    └─────────────┘
```

### Snowflake Schema

Dimension tables can further relate to sub-dimension tables, forming multi-level structures.

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

### Parent-Child Dimension

Used for handling hierarchical structure data, such as organizational structure, product categories, etc. Implements efficient hierarchical queries through closure tables.

---

## Data Flow

```
┌────────────────────────────────────────────────────────────────┐
│  ┌──────────────┐    ┌──────────────┐    ┌──────────────┐     │
│  │ FactOrder.tm │    │ DimCustomer.tm│    │ DimProduct.tm│     │
│  └──────────────┘    └──────────────┘    └──────────────┘     │
│         ↓                   ↓                   ↓              │
│         └───────────────────┴───────────────────┘              │
│                            ↓                                   │
│                    ┌──────────────┐                           │
│                    │   QM (Query)  │                           │
│                    │ OrderQuery.qm│                           │
│                    └──────────────┘                           │
│                            ↓                                   │
│                    ┌──────────────┐                           │
│                    │  DSL Request  │                           │
│                    │    (JSON)     │                           │
│                    └──────────────┘                           │
└────────────────────────────────────────────────────────────────┘
```

1. **TM defines data structure**: Tables, fields, relationships
2. **QM defines query view**: Based on TM, defines queryable content and permissions
3. **DSL initiates query**: Based on QM, uses JSON format to query data

---

## Next Steps

- [Quick Start](./quick-start.md) - Create your first TM/QM and use DSL queries
- [TM Syntax Manual](../tm-qm/tm-syntax.md) - Complete TM definition syntax
- [QM Syntax Manual](../tm-qm/qm-syntax.md) - Complete QM definition syntax
- [DSL Query API](../api/query-api.md) - Complete DSL query syntax
