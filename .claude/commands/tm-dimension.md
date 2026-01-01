# TM Dimension Configuration Helper

Help users configure dimensions in TM (Table Model) files, including basic dimensions, nested dimensions (snowflake schema), parent-child dimensions (hierarchy), and dimension reuse patterns.

## Dimension Types

### 1. Basic Dimension
Direct relationship between fact table and dimension table.

```javascript
{
    name: 'customer',              // Dimension name for query reference
    caption: 'Customer',           // Display name
    description: 'Customer info',  // AI context

    tableName: 'dim_customer',     // Dimension table
    foreignKey: 'customer_key',    // Column in FACT table
    primaryKey: 'customer_key',    // Column in DIMENSION table
    captionColumn: 'customer_name', // Display column

    properties: [
        { column: 'customer_id', caption: 'Customer ID' },
        { column: 'customer_type', caption: 'Customer Type' }
    ]
}
```

### 2. Nested Dimension (Snowflake Schema)
Multi-level dimension hierarchy (e.g., Product → Category → Category Group).

**CRITICAL**: In nested dimensions, `foreignKey` refers to the column in the **PARENT** table, not the fact table!

```javascript
{
    name: 'product',
    tableName: 'dim_product',
    foreignKey: 'product_key',      // In FACT table
    primaryKey: 'product_key',
    captionColumn: 'product_name',
    caption: 'Product',

    dimensions: [                    // Nested sub-dimensions
        {
            name: 'category',
            alias: 'productCategory', // Simplifies QM access
            tableName: 'dim_category',
            foreignKey: 'category_key', // In dim_product (PARENT), NOT fact table!
            primaryKey: 'category_key',
            captionColumn: 'category_name',
            caption: 'Category',

            dimensions: [             // Can continue nesting
                {
                    name: 'group',
                    alias: 'categoryGroup',
                    tableName: 'dim_category_group',
                    foreignKey: 'group_key', // In dim_category (PARENT)
                    primaryKey: 'group_key',
                    captionColumn: 'group_name',
                    caption: 'Category Group'
                }
            ]
        }
    ]
}
```

**Generated SQL**:
```sql
FROM fact_sales f
LEFT JOIN dim_product p ON f.product_key = p.product_key
LEFT JOIN dim_category c ON p.category_key = c.category_key
LEFT JOIN dim_category_group g ON c.group_key = g.group_key
```

### 3. Parent-Child Dimension (Hierarchy)
Tree-structured data using closure tables (e.g., organization hierarchy).

```javascript
{
    name: 'team',
    tableName: 'dim_team',
    foreignKey: 'team_id',
    primaryKey: 'team_id',
    captionColumn: 'team_name',
    caption: 'Team',

    // Parent-child specific fields
    closureTableName: 'team_closure',  // REQUIRED
    parentKey: 'parent_id',            // Ancestor column in closure table
    childKey: 'team_id',               // Descendant column in closure table

    properties: [
        { column: 'team_id', caption: 'Team ID', type: 'STRING' },
        { column: 'team_name', caption: 'Team Name', type: 'STRING' },
        { column: 'parent_id', caption: 'Parent Team', type: 'STRING' },
        { column: 'team_level', caption: 'Level', type: 'INTEGER' }
    ]
}
```

**Closure Table Structure**:
```sql
CREATE TABLE team_closure (
    parent_id VARCHAR(50),  -- Ancestor
    child_id  VARCHAR(50),  -- Descendant
    depth     INT,          -- 0 for self
    PRIMARY KEY (parent_id, child_id)
);
```

## Dimension Reuse Patterns

### Creating Dimension Builders

Store reusable dimension configurations as factory functions:

```javascript
// dimensions/common-dims.fsscript

/**
 * Build Date Dimension
 * @param {object} options - Configuration options
 */
export function buildDateDim(options = {}) {
    const {
        name = 'salesDate',
        foreignKey = 'date_key',
        caption = 'Date',
        description = 'Business date'
    } = options;

    return {
        name,
        tableName: 'dim_date',
        foreignKey,
        primaryKey: 'date_key',
        captionColumn: 'full_date',
        caption,
        description,
        keyDescription: 'Date key, format yyyyMMdd',
        type: 'DATETIME',

        properties: [
            { column: 'year', caption: 'Year', type: 'INTEGER' },
            { column: 'quarter', caption: 'Quarter', type: 'INTEGER' },
            { column: 'month', caption: 'Month', type: 'INTEGER' },
            { column: 'month_name', caption: 'Month Name', type: 'STRING' },
            { column: 'day_of_week', caption: 'Day of Week', type: 'INTEGER' },
            { column: 'is_weekend', caption: 'Is Weekend', type: 'BOOL' }
        ]
    };
}

/**
 * Build Customer Dimension
 */
export function buildCustomerDim(options = {}) {
    const {
        name = 'customer',
        foreignKey = 'customer_key',
        caption = 'Customer'
    } = options;

    return {
        name,
        tableName: 'dim_customer',
        foreignKey,
        primaryKey: 'customer_key',
        captionColumn: 'customer_name',
        caption,

        properties: [
            { column: 'customer_id', caption: 'Customer ID' },
            { column: 'customer_type', caption: 'Customer Type' },
            { column: 'province', caption: 'Province' },
            { column: 'city', caption: 'City' }
        ]
    };
}
```

### Using Dimension Builders

```javascript
// model/FactSalesModel.tm
import { buildDateDim, buildCustomerDim } from '../dimensions/common-dims.fsscript';

export const model = {
    name: 'FactSalesModel',
    dimensions: [
        // Custom configuration
        buildDateDim({
            name: 'salesDate',
            caption: 'Sales Date'
        }),

        // Default configuration
        buildCustomerDim(),

        // Multiple date dimensions in same model
        buildDateDim({
            name: 'shipDate',
            foreignKey: 'ship_date_key',
            caption: 'Ship Date'
        })
    ]
};
```

### Extending Dimension Properties

Use spread operator to add properties to a builder:

```javascript
{
    ...buildCustomerDim({ caption: 'VIP Customer' }),
    properties: [
        ...buildCustomerDim().properties,
        { column: 'vip_level', caption: 'VIP Level' },
        { column: 'vip_points', caption: 'Points', type: 'INTEGER' }
    ]
}
```

### Reusable Nested Dimension

```javascript
// dimensions/product-hierarchy.fsscript
export function buildProductWithCategoryDim(options = {}) {
    return {
        name: options.name || 'product',
        tableName: 'dim_product',
        foreignKey: options.foreignKey || 'product_key',
        primaryKey: 'product_key',
        captionColumn: 'product_name',
        caption: options.caption || 'Product',

        properties: [...],

        dimensions: [
            {
                name: 'category',
                alias: 'productCategory',
                tableName: 'dim_category',
                foreignKey: 'category_key', // In dim_product
                primaryKey: 'category_key',
                captionColumn: 'category_name',
                caption: 'Category',

                dimensions: [
                    {
                        name: 'group',
                        alias: 'categoryGroup',
                        tableName: 'dim_category_group',
                        foreignKey: 'group_key', // In dim_category
                        primaryKey: 'group_key',
                        captionColumn: 'group_name',
                        caption: 'Category Group'
                    }
                ]
            }
        ]
    };
}
```

### Reusable Parent-Child Dimension

```javascript
// dimensions/hierarchy-dims.fsscript
export function buildOrgDim(options = {}) {
    const {
        name = 'team',
        tableName = 'dim_team',
        foreignKey = 'team_id',
        closureTableName = 'team_closure',
        caption = 'Team'
    } = options;

    return {
        name,
        tableName,
        foreignKey,
        primaryKey: 'team_id',
        captionColumn: 'team_name',
        caption,

        closureTableName,
        parentKey: 'parent_id',
        childKey: 'team_id',

        properties: [
            { column: 'team_id', caption: 'Team ID', type: 'STRING' },
            { column: 'team_name', caption: 'Team Name', type: 'STRING' },
            { column: 'parent_id', caption: 'Parent Team', type: 'STRING' },
            { column: 'team_level', caption: 'Level', type: 'INTEGER' }
        ]
    };
}
```

## Common Pitfalls

### 1. Nested Dimension foreignKey Confusion

**WRONG**: Using fact table column for nested dimension
```javascript
dimensions: [{
    name: 'category',
    foreignKey: 'category_key',  // WRONG if this is in fact_sales
}]
```

**CORRECT**: Using parent dimension table column
```javascript
// product dimension in fact table
{
    name: 'product',
    foreignKey: 'product_key',  // This IS in fact_sales
    dimensions: [{
        name: 'category',
        foreignKey: 'category_key',  // This is in dim_product (parent)
    }]
}
```

### 2. Missing Alias for Deep Nesting

Without alias, QM access becomes verbose:
```javascript
'product.category.group$caption'  // Long path
```

With alias:
```javascript
'categoryGroup$caption'  // Short and clear
```

### 3. Parent-Child Without Closure Table

Parent-child dimensions REQUIRE a closure table. If you only have a `parent_id` column in the dimension table, you need to generate the closure table first.

## Recommended Project Structure

```
templates/
├── dimensions/
│   ├── common-dims.fsscript       # Date, Customer, Product, Store
│   ├── hierarchy-dims.fsscript    # Org, Region (parent-child)
│   └── product-hierarchy.fsscript # Product with Category (snowflake)
├── model/
│   ├── FactSalesModel.tm
│   └── FactOrderModel.tm
├── query/
│   └── ...
└── dicts.fsscript
```

## Interactive Guidance

When helping users configure dimensions, ask:

1. **Relationship type**: Is this a simple dimension, snowflake (nested), or hierarchy (parent-child)?

2. **For nested dimensions**:
   - What is the hierarchy? (e.g., Product → Category → Group)
   - What are the foreign keys at each level?

3. **For parent-child dimensions**:
   - Do you have a closure table?
   - What are the parent_id and child_id columns?

4. **For dimension reuse**:
   - Is this dimension used in multiple fact tables?
   - Should we create a reusable builder function?

5. **Customization needs**:
   - Do you need different captions or descriptions per usage?
   - Do you need additional properties beyond the standard ones?

Now, based on the user's input, help configure the dimension.
