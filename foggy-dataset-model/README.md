# Foggy Dataset Model

[中文文档](README.zh-CN.md)

**Embedded Semantic Layer Engine** - Provides declarative dimensional modeling and dynamic query capabilities for Java applications.

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-17+-green.svg)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-brightgreen.svg)](https://spring.io/projects/spring-boot)

## Overview

Foggy Dataset Model is an embeddable data modeling and query engine designed for OLAP analytics scenarios. Through declarative **TM (Data Model)** and **QM (Query Model)** definitions, it automatically handles complex logic such as multi-table JOINs, aggregate calculations, and dimension filtering.

### Core Features

- **Declarative Dimensional Modeling** - Define star/snowflake schemas using JavaScript syntax, auto-generate SQL
- **Nested Dimensions (Snowflake Schema)** - Support multi-level dimension associations with concise nested syntax
- **Parent-Child Dimensions (Hierarchical Data)** - Automatically handle hierarchical queries like organization structures using closure tables
- **Multi-Database Support** - MySQL, PostgreSQL, SQL Server, SQLite, MongoDB
- **Model-Query Separation** - TM defines data structure, QM defines queryable fields with clear responsibilities
- **Embeddable Design** - Integrate as a Spring Boot Starter with no additional operational overhead

## Quick Start

### 1. Add Dependencies

```xml
<dependency>
    <groupId>com.foggysource</groupId>
    <artifactId>foggy-dataset-model</artifactId>
    <version>8.0.1-beta</version>
</dependency>
```

### 2. Define Data Model (TM)

Create file `FactSalesModel.tm`:

```javascript
export const model = {
    name: 'FactSalesModel',
    caption: 'Sales Fact Table',
    tableName: 'fact_sales',
    idColumn: 'sales_key',

    // Dimension definitions
    dimensions: [
        {
            name: 'product',
            tableName: 'dim_product',
            foreignKey: 'product_key',
            primaryKey: 'product_key',
            captionColumn: 'product_name',
            caption: 'Product',
            properties: [
                { column: 'brand', caption: 'Brand' },
                { column: 'category_name', caption: 'Category' }
            ]
        },
        {
            name: 'customer',
            tableName: 'dim_customer',
            foreignKey: 'customer_key',
            primaryKey: 'customer_key',
            captionColumn: 'customer_name',
            caption: 'Customer',
            properties: [
                { column: 'province', caption: 'Province' },
                { column: 'city', caption: 'City' }
            ]
        }
    ],

    // Measure definitions
    measures: [
        { column: 'quantity', caption: 'Sales Quantity', aggregation: 'sum' },
        { column: 'sales_amount', caption: 'Sales Amount', type: 'MONEY', aggregation: 'sum' },
        { column: 'profit_amount', caption: 'Profit', type: 'MONEY', aggregation: 'sum' }
    ]
};
```

### 3. Define Query Model (QM)

Create file `FactSalesQueryModel.qm`:

```javascript
export const queryModel = {
    name: 'FactSalesQueryModel',
    model: 'FactSalesModel',

    columnGroups: [
        {
            caption: 'Product Dimensions',
            items: [
                { name: 'product$caption' },
                { name: 'product$brand' },
                { name: 'product$categoryName' }
            ]
        },
        {
            caption: 'Customer Dimensions',
            items: [
                { name: 'customer$caption' },
                { name: 'customer$province' }
            ]
        },
        {
            caption: 'Measures',
            items: [
                { name: 'quantity' },
                { name: 'salesAmount' },
                { name: 'profitAmount' }
            ]
        }
    ],

    orders: [
        { name: 'salesAmount', order: 'desc' }
    ]
};
```

### 4. Execute Query

```java
@Autowired
private JdbcService jdbcService;

public void querySales() {
    JdbcQueryRequestDef request = new JdbcQueryRequestDef();
    request.setQueryModel("FactSalesQueryModel");
    request.setColumns(Arrays.asList(
        "product$caption",
        "customer$province",
        "salesAmount"
    ));

    // Filter by province
    SliceRequestDef slice = new SliceRequestDef();
    slice.setName("customer$province");
    slice.setType(CondType.EQ);
    slice.setValue("Guangdong");
    request.setSlice(Collections.singletonList(slice));

    // Group by product
    GroupRequestDef group = new GroupRequestDef();
    group.setName("product$caption");
    request.setGroupBy(Collections.singletonList(group));

    PagingRequest<JdbcQueryRequestDef> form = PagingRequest.buildPagingRequest(request, 20);
    PagingResultImpl result = jdbcService.queryModelData(form);

    // Auto-generated SQL:
    // SELECT p.product_name, SUM(f.sales_amount)
    // FROM fact_sales f
    // LEFT JOIN dim_product p ON f.product_key = p.product_key
    // LEFT JOIN dim_customer c ON f.customer_key = c.customer_key
    // WHERE c.province = 'Guangdong'
    // GROUP BY p.product_name
    // ORDER BY SUM(f.sales_amount) DESC
    // LIMIT 20
}
```

## Advanced Features

### Nested Dimensions (Snowflake Schema)

Support multi-level dimension associations where foreign keys point to parent dimension tables instead of fact tables:

```javascript
dimensions: [
    {
        name: 'product',
        tableName: 'dim_product',
        foreignKey: 'product_key',
        primaryKey: 'product_key',
        captionColumn: 'product_name',

        // Nested sub-dimensions
        dimensions: [
            {
                name: 'category',
                alias: 'productCategory',  // Use productCategory$xxx in QM
                tableName: 'dim_category',
                foreignKey: 'category_key', // On dim_product table
                primaryKey: 'category_key',
                captionColumn: 'category_name',

                // Continue nesting
                dimensions: [
                    {
                        name: 'group',
                        alias: 'categoryGroup',
                        tableName: 'dim_category_group',
                        foreignKey: 'group_key', // On dim_category table
                        primaryKey: 'group_key',
                        captionColumn: 'group_name'
                    }
                ]
            }
        ]
    }
]
```

Generated SQL JOIN chain:
```sql
FROM fact_sales f
LEFT JOIN dim_product p ON f.product_key = p.product_key
LEFT JOIN dim_category c ON p.category_key = c.category_key
LEFT JOIN dim_category_group g ON c.group_key = g.group_key
```

### Parent-Child Dimensions (Hierarchical Data)

Handle hierarchical structures like organization charts and region trees using closure tables:

```javascript
{
    name: 'team',
    tableName: 'dim_team',
    foreignKey: 'team_id',
    primaryKey: 'team_id',
    captionColumn: 'team_name',
    caption: 'Team',

    // Parent-child dimension configuration
    closureTableName: 'team_closure',
    parentKey: 'parent_id',
    childKey: 'team_id',

    properties: [
        { column: 'team_level', caption: 'Level' },
        { column: 'manager_name', caption: 'Manager' }
    ]
}
```

Automatically includes all descendant nodes when querying:
```java
// Filter data from "Sales Department 1" and all subordinate teams
slice.setName("team$id");
slice.setValue("TEAM_SALES_1");
```

### Multi-Database Support

Automatically adapts to different database dialects:

```yaml
# application.yml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/demo  # Or postgresql, sqlserver, sqlite
```

Supported databases:
- MySQL 5.7+
- PostgreSQL 12+
- SQL Server 2012+
- SQLite 3.30+
- MongoDB 4.0+

## Architecture Design

```
┌─────────────────────────────────────────────────────────┐
│                 Application Layer (Your App)             │
├─────────────────────────────────────────────────────────┤
│                 JdbcService (Query Entry)                │
├─────────────────────────────────────────────────────────┤
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────┐  │
│  │ TM Loader   │  │ QM Loader   │  │ SQL Generator   │  │
│  │ (JdbcModel) │  │(QueryModel) │  │ (JdbcQuery)     │  │
│  └─────────────┘  └─────────────┘  └─────────────────┘  │
├─────────────────────────────────────────────────────────┤
│              Database Dialect Layer (FDialect)           │
├──────────┬──────────┬──────────┬──────────┬─────────────┤
│  MySQL   │ PostgreSQL│ SQLServer│  SQLite  │  MongoDB   │
└──────────┴──────────┴──────────┴──────────┴─────────────┘
```

## Documentation

- [TM/QM Syntax Manual](docs/TM-QM-Syntax-Manual.md)
- [Quick Start Guide](docs/quick-start.md)
- [API Reference](docs/API-Reference.md)
- [Multi-Database Adapter](docs/MULTI_DATABASE_ADAPTER.md)
- [Parent-Child Dimensions](docs/Parent-Child-Dimension.md)

## Use Cases

- **SaaS Product Reporting** - Generate analytical queries directly in the backend without introducing heavy BI tools
- **Enterprise Data Platform** - Unified data model layer supporting frontend self-service queries
- **Low-Code Platforms** - Configure data models declaratively, generate queries dynamically
- **Data API Services** - Rapidly build data query interfaces

## Contributing

Issues and Pull Requests are welcome.

## License

[Apache License 2.0](../LICENSE)
