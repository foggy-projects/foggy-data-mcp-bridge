# Java Data MCP Bridge

[中文文档](README.zh-CN.md)

**Embedded Semantic Layer Framework** - Provides declarative dimensional modeling, dynamic querying, and AI-driven data analysis capabilities for Java applications.

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-17+-green.svg)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-brightgreen.svg)](https://spring.io/projects/spring-boot)

## Overview

Foggy Framework is an embeddable data modeling and query framework designed for OLAP analytics scenarios. Through declarative **JM (Data Model)** and **QM (Query Model)** definitions, it automatically handles complex logic such as multi-table JOINs, aggregate calculations, and dimension filtering, and supports natural language queries by connecting to AI assistants via the MCP protocol.

### Core Features

- **Declarative Dimensional Modeling** - Define star/snowflake schemas using JavaScript syntax, auto-generate SQL
- **Nested Dimensions (Snowflake Schema)** - Support multi-level dimension associations with concise nested syntax
- **Parent-Child Dimensions (Hierarchical Data)** - Automatically handle hierarchical queries like organization structures using closure tables
- **Multi-Database Support** - MySQL, PostgreSQL, SQL Server, SQLite, MongoDB
- **MCP Protocol Integration** - Connect to AI assistants like Claude and ChatGPT for natural language data queries
- **Embeddable Design** - Integrate as a Spring Boot Starter with no additional operational overhead

## Module Structure

```
java-data-mcp-bridge/
├── foggy-core              # Core utility library
├── foggy-dataset           # Database foundation layer (dialects, connection pools)
├── foggy-dataset-model     # Core: Data model engine (JM/QM)
├── foggy-dataset-mcp       # MCP server (AI integration)
├── foggy-dataset-demo      # Demo project
├── foggy-fsscript          # Script engine (JM/QM file parsing)
├── foggy-fsscript-client   # Script engine client
└── foggy-bean-copy         # Bean copy utility
```

## Quick Start

### 1. Add Dependencies

```xml
<dependency>
    <groupId>com.foggysource</groupId>
    <artifactId>foggy-dataset-model</artifactId>
    <version>8.0.0-beta</version>
</dependency>

<!-- For MCP/AI features -->
<dependency>
    <groupId>com.foggysource</groupId>
    <artifactId>foggy-dataset-mcp</artifactId>
    <version>8.0.0-beta</version>
</dependency>
```

### 2. Define Data Model (JM)

Create `FactSalesModel.jm`:

```javascript
export const model = {
    name: 'FactSalesModel',
    caption: 'Sales Fact Table',
    tableName: 'fact_sales',
    idColumn: 'sales_key',

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
        }
    ],

    measures: [
        { column: 'quantity', caption: 'Sales Quantity', aggregation: 'sum' },
        { column: 'sales_amount', caption: 'Sales Amount', type: 'MONEY', aggregation: 'sum' }
    ]
};
```

### 3. Define Query Model (QM)

Create `FactSalesQueryModel.qm`:

```javascript
export const queryModel = {
    name: 'FactSalesQueryModel',
    model: 'FactSalesModel',

    columnGroups: [
        {
            caption: 'Product Dimensions',
            items: [
                { name: 'product$caption' },
                { name: 'product$brand' }
            ]
        },
        {
            caption: 'Measures',
            items: [
                { name: 'quantity' },
                { name: 'salesAmount' }
            ]
        }
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
    request.setColumns(Arrays.asList("product$caption", "salesAmount"));

    PagingRequest<JdbcQueryRequestDef> form = PagingRequest.buildPagingRequest(request, 20);
    PagingResultImpl result = jdbcService.queryModelData(form);

    // Auto-generated SQL:
    // SELECT p.product_name, SUM(f.sales_amount)
    // FROM fact_sales f
    // LEFT JOIN dim_product p ON f.product_key = p.product_key
    // GROUP BY p.product_name
    // LIMIT 20
}
```

## AI-Driven Data Queries

Connect to AI assistants via MCP protocol for natural language queries:

```
User: What are the top 5 brands by sales last month?

AI: Querying... [Auto-generates query request]

Results:
| Brand   | Sales Amount |
|---------|-------------|
| Brand A | $1,234,567  |
| Brand B | $987,654    |
| ...     | ...         |
```

See [foggy-dataset-mcp](foggy-dataset-mcp/) module for details.

## Supported Databases

| Database | Version | Status |
|----------|---------|--------|
| MySQL | 5.7+ | ✅ Full support |
| PostgreSQL | 12+ | ✅ Full support |
| SQL Server | 2012+ | ✅ Full support |
| SQLite | 3.30+ | ✅ Full support |
| MongoDB | 4.0+ | ✅ Basic support |

## Documentation

- [Quick Start](foggy-dataset-model/docs/quick-start.md)
- [JM/QM Syntax Manual](foggy-dataset-model/docs/JM-QM-Syntax-Manual.md)
- [API Reference](foggy-dataset-model/docs/API-Reference.md)
- [Multi-Database Adapter](foggy-dataset-model/docs/MULTI_DATABASE_ADAPTER.md)
- [Parent-Child Dimensions](foggy-dataset-model/docs/Parent-Child-Dimension.md)

## Use Cases

- **SaaS Product Reporting** - Generate analytical queries directly in the backend without introducing heavy BI tools
- **Enterprise Data Platform** - Unified data model layer supporting frontend self-service queries
- **Low-Code Platforms** - Configure data models declaratively, generate queries dynamically
- **AI Data Assistants** - Enable AI to understand and query business data via MCP protocol

## Requirements

- Java 17+
- Spring Boot 3.x
- Maven 3.6+

## Contributing

Issues and Pull Requests are welcome.

## License

[Apache License 2.0](LICENSE)
