# Claude Skills User Guide

This guide explains how to use Claude Code's Slash Commands to quickly generate and configure TM/QM model files.

## What are Claude Skills?

Claude Skills are predefined prompt templates integrated into Claude Code as Slash Commands. When you type `/command-name`, Claude loads the corresponding specialized knowledge and rules to help you complete specific tasks more efficiently.

This project provides two TM/QM related Skills:

| Command | Function |
|---------|----------|
| `/tm-generate` | Generate complete TM files from DDL or table descriptions |
| `/tm-dimension` | Configure dimension relationships (basic, nested, parent-child) |

## Prerequisites

1. Install [Claude Code](https://claude.ai/code)
2. Run Claude Code in the project root directory
3. Ensure the skill files exist in `.claude/commands/` directory

## `/tm-generate` - TM File Generation

### Overview

Automatically generate TM (Table Model) files based on user input, supporting:
- DDL statement parsing
- Natural language table descriptions
- Database table names (combined with `dataset.inspect_table` tool)

### Usage

In Claude Code, type:

```
/tm-generate

Please generate a TM file for the following table:

CREATE TABLE fact_sales (
    sales_key BIGINT PRIMARY KEY,
    date_key INT NOT NULL,
    customer_key INT NOT NULL,
    product_key INT NOT NULL,
    order_id VARCHAR(50),
    quantity INT,
    unit_price DECIMAL(10,2),
    sales_amount DECIMAL(12,2)
);
```

### Smart Inference

`/tm-generate` automatically:

1. **Type Mapping**: Converts database types to TM types
   - `BIGINT` → `BIGINT`
   - `DECIMAL` → `MONEY`
   - `VARCHAR` → `STRING`
   - `DATE` → `DAY`

2. **Role Detection**:
   - Foreign key columns → dimension
   - Amount/quantity columns → measure
   - Other columns → property

3. **Dimension Reuse Suggestions**:
   - Identifies common dimensions (date, customer, product)
   - Suggests using dimension builder functions

### Output Example

```javascript
import { buildDateDim, buildCustomerDim, buildProductDim }
    from '../dimensions/common-dims.fsscript';

export const model = {
    name: 'FactSalesModel',
    caption: 'Sales Fact Table',
    tableName: 'fact_sales',
    idColumn: 'sales_key',

    dimensions: [
        buildDateDim({ name: 'salesDate', foreignKey: 'date_key' }),
        buildCustomerDim({ foreignKey: 'customer_key' }),
        buildProductDim({ foreignKey: 'product_key' })
    ],

    properties: [
        { column: 'sales_key', caption: 'Sales Key', type: 'BIGINT' },
        { column: 'order_id', caption: 'Order ID', type: 'STRING' }
    ],

    measures: [
        { column: 'quantity', caption: 'Quantity', type: 'INTEGER', aggregation: 'sum' },
        { column: 'unit_price', caption: 'Unit Price', type: 'MONEY', aggregation: 'avg' },
        { column: 'sales_amount', caption: 'Sales Amount', type: 'MONEY', aggregation: 'sum' }
    ]
};
```

### Combined with Database Inspection Tool

If your database table already exists, combine with `dataset.inspect_table`:

```
/tm-generate

Please inspect the fact_sales table in the database and generate a TM file.
```

Claude will first call `dataset.inspect_table` to get the table structure, then generate the TM file.

## `/tm-dimension` - Dimension Configuration Helper

### Overview

Helps configure various types of dimension relationships:
- **Basic Dimension**: Direct relationship between fact and dimension tables
- **Nested Dimension** (Snowflake): Multi-level dimension hierarchy
- **Parent-Child Dimension** (Hierarchy): Tree structure using closure tables

### Usage

```
/tm-dimension

I need to configure a product dimension where products link to categories,
and categories link to category groups.
```

### Basic Dimension Configuration

```javascript
{
    name: 'customer',
    caption: 'Customer',
    tableName: 'dim_customer',
    foreignKey: 'customer_key',    // FK in fact table
    primaryKey: 'customer_key',    // PK in dimension table
    captionColumn: 'customer_name',

    properties: [
        { column: 'customer_id', caption: 'Customer ID' },
        { column: 'customer_type', caption: 'Customer Type' }
    ]
}
```

### Nested Dimension Configuration (Snowflake)

**IMPORTANT**: In nested dimensions, `foreignKey` refers to the column in the **PARENT** table, not the fact table!

```javascript
{
    name: 'product',
    tableName: 'dim_product',
    foreignKey: 'product_key',      // Column in FACT table
    primaryKey: 'product_key',
    captionColumn: 'product_name',

    dimensions: [                    // Nested sub-dimensions
        {
            name: 'category',
            alias: 'productCategory',
            tableName: 'dim_category',
            foreignKey: 'category_key', // Column in dim_product!
            primaryKey: 'category_key',
            captionColumn: 'category_name'
        }
    ]
}
```

### Parent-Child Dimension Configuration (Hierarchy)

Requires a pre-created closure table:

```javascript
{
    name: 'team',
    tableName: 'dim_team',
    foreignKey: 'team_id',
    primaryKey: 'team_id',
    captionColumn: 'team_name',

    // Parent-child specific configuration
    closureTableName: 'team_closure',  // Required
    parentKey: 'parent_id',
    childKey: 'team_id'
}
```

### Dimension Reuse Patterns

`/tm-dimension` suggests extracting common dimensions as builder functions:

```javascript
// dimensions/common-dims.fsscript
export function buildDateDim(options = {}) {
    const { name = 'date', foreignKey = 'date_key' } = options;
    return {
        name,
        tableName: 'dim_date',
        foreignKey,
        primaryKey: 'date_key',
        properties: [
            { column: 'year', caption: 'Year' },
            { column: 'month', caption: 'Month' },
            { column: 'day_of_week', caption: 'Day of Week' }
        ]
    };
}
```

## Best Practices

### 1. Inspect Before Generate

For existing database tables, inspect the structure first:

```
First use inspect_table to check the fact_orders table,
then use /tm-generate to create the TM file
```

### 2. Prioritize Dimension Reuse

When multiple fact tables use the same dimension, prefer dimension builders:

```
/tm-dimension

Please create a reusable date dimension builder,
I have multiple fact tables that need it
```

### 3. Configure Complex Dimensions Step by Step

For nested or parent-child dimensions, configure in steps:

```
/tm-dimension

Step 1: Configure the basic product dimension structure
Step 2: Add the category nested dimension
Step 3: Add the category group second-level nesting
```

### 4. Provide Business Context

Providing business context results in better field naming and descriptions:

```
/tm-generate

This is an e-commerce order detail table for sales analysis:
- date_key: order date
- customer_key: ordering customer
- sales_amount: order amount (needs to be aggregated by date and customer)
```

## FAQ

### Q: Skill not loading?

Ensure `.claude/commands/tm-generate.md` and `.claude/commands/tm-dimension.md` files exist.

### Q: How to customize a Skill?

Edit the markdown files in the `.claude/commands/` directory directly.

### Q: Can I add more Skills?

Yes. Create a new `.md` file in `.claude/commands/` directory. The filename becomes the command name.

## Related Documentation

- [TM Syntax Reference](/en/dataset/jm-qm/jm-syntax)
- [QM Syntax Reference](/en/dataset/jm-qm/qm-syntax)
- [Dimension Reuse Best Practices](/en/dataset/jm-qm/jm-syntax#_7-dimension-reuse-best-practices)
- [Parent-Child Dimension Configuration](/en/dataset/jm-qm/parent-child)
