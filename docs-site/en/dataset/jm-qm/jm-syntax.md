# TM Syntax Manual

TM (Table Model) defines the structure and relationships of database tables. This document provides a complete TM syntax specification.

## 1. Basic Structure

TM files use JavaScript ES6 module syntax, exporting a `model` object:

```javascript
export const model = {
    name: 'FactSalesModel',      // Model name (required, unique identifier)
    caption: 'Sales Fact Table', // Display name
    description: 'Sales order details data', // Description
    tableName: 'fact_sales',     // Database table name (required)
    idColumn: 'sales_key',       // Primary key column

    dimensions: [...],           // Dimension definitions (relationships)
    properties: [...],           // Property definitions (table fields)
    measures: [...]              // Measure definitions (aggregatable fields)
};
```

### 1.1 Basic Model Fields

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `name` | string | Yes | Unique model identifier, referenced by QM |
| `caption` | string | No | Display name, recommended for AI integration |
| `description` | string | No | Detailed description, recommended for AI integration |
| `tableName` | string | Yes¹ | Database table name or MongoDB collection |
| `viewSql` | string | No¹ | View SQL, alternative to tableName |
| `schema` | string | No | Database schema (for cross-schema access) |
| `idColumn` | string | No | Primary key column name |
| `type` | string | No | Model type, default `jdbc` or `mongo` |
| `deprecated` | boolean | No | Mark as deprecated, default false |

> ¹ `tableName` and `viewSql` are mutually exclusive, `tableName` takes precedence

### 1.2 AI Enhancement Configuration

Add `ai` configuration to models, dimensions, properties, and measures for AI natural language queries:

```javascript
{
    name: 'salesAmount',
    caption: 'Sales Amount',
    type: 'MONEY',
    ai: {
        enabled: true,              // Enable AI analysis (default true)
        prompt: 'Customer actual payment amount', // Override description
        levels: [1, 2]              // Activation levels
    }
}
```

| Field | Type | Description |
|-------|------|-------------|
| `enabled` | boolean | Enable AI analysis, default true |
| `prompt` | string | Prompt text, replaces description if provided |
| `levels` | number[] | Activation levels, field can belong to multiple levels |

---

## 2. Dimension Definitions (dimensions)

Dimensions define relationships with other tables, automatically generating JOINs in queries.

### 2.1 Basic Dimension

```javascript
dimensions: [
    {
        name: 'customer',              // Dimension name (for query reference)
        caption: 'Customer',           // Display name
        description: 'Customer who purchased products', // Description

        tableName: 'dim_customer',     // Related dimension table
        foreignKey: 'customer_key',    // Foreign key in fact table
        primaryKey: 'customer_key',    // Primary key in dimension table
        captionColumn: 'customer_name', // Display column

        keyCaption: 'Customer Key',    // Primary key display name
        keyDescription: 'Customer surrogate key, auto-increment integer',

        // Dimension properties (queryable fields from dimension table)
        properties: [
            {
                column: 'customer_id',
                caption: 'Customer ID',
                description: 'Customer unique identifier'
            },
            {
                column: 'customer_type',
                caption: 'Customer Type',
                description: 'Customer type: Individual/Enterprise'
            },
            { column: 'province', caption: 'Province' },
            { column: 'city', caption: 'City' },
            { column: 'member_level', caption: 'Member Level' }
        ]
    }
]
```

### 2.2 Dimension Field Reference

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `name` | string | Yes | Dimension name, use `dimension$property` format in queries |
| `caption` | string | No | Display name |
| `description` | string | No | Detailed description |
| `tableName` | string | Yes¹ | Related dimension table name |
| `viewSql` | string | No¹ | Dimension view SQL, alternative to tableName |
| `schema` | string | No | Dimension table schema |
| `foreignKey` | string | Yes | Foreign key field in fact table |
| `primaryKey` | string | Yes | Primary key field in dimension table |
| `captionColumn` | string | No | Display field, used for `dimension$caption` |
| `keyCaption` | string | No | Primary key display name, default `${caption}Key` |
| `keyDescription` | string | No | Primary key description |
| `type` | string | No | Dimension type, e.g., `DATETIME` for time dimension |
| `properties` | array | No | Queryable properties from dimension table |
| `forceIndex` | string | No | Force specific index name |

> ¹ `tableName` and `viewSql` are mutually exclusive, `tableName` takes precedence

### 2.3 Nested Dimensions (Snowflake Schema)

Nested dimensions implement snowflake schema where dimension tables have hierarchical relationships.

```javascript
{
    // Level 1 dimension: Product (directly linked to fact table)
    name: 'product',
    tableName: 'dim_product',
    foreignKey: 'product_key',       // Foreign key in fact table
    primaryKey: 'product_key',
    captionColumn: 'product_name',
    caption: 'Product',

    properties: [
        { column: 'product_id', caption: 'Product ID' },
        { column: 'brand', caption: 'Brand' },
        { column: 'unit_price', caption: 'Unit Price', type: 'MONEY' }
    ],

    // Nested sub-dimension: Category (linked to product, not fact table)
    dimensions: [
        {
            name: 'category',
            alias: 'productCategory',   // Alias for simpler QM access
            tableName: 'dim_category',
            foreignKey: 'category_key', // Foreign key in parent table (dim_product)
            primaryKey: 'category_key',
            captionColumn: 'category_name',
            caption: 'Category',

            properties: [
                { column: 'category_id', caption: 'Category ID' },
                { column: 'category_level', caption: 'Category Level' }
            ],

            // Continue nesting: Category Group (linked to category)
            dimensions: [
                {
                    name: 'group',
                    alias: 'categoryGroup',
                    tableName: 'dim_category_group',
                    foreignKey: 'group_key',  // Foreign key in parent (dim_category)
                    primaryKey: 'group_key',
                    captionColumn: 'group_name',
                    caption: 'Category Group',

                    properties: [
                        { column: 'group_id', caption: 'Group ID' },
                        { column: 'group_type', caption: 'Group Type' }
                    ]
                }
            ]
        }
    ]
}
```

**Key Points for Nested Dimensions**:

| Field | Description |
|-------|-------------|
| `alias` | Dimension alias for simpler QM column access |
| `foreignKey` | **Important**: Nested dimension's foreignKey refers to parent dimension table |
| `dimensions` | Sub-dimension list, can continue nesting for multi-level structures |

**Accessing Nested Dimensions in QM**:

```javascript
// Method 1: Using alias (recommended)
columns: [
    'product$caption',           // Level 1 dimension
    'productCategory$caption',   // Level 2 dimension (via alias)
    'categoryGroup$caption'      // Level 3 dimension (via alias)
]

// Method 2: Using full path
columns: [
    'product$caption',
    'product.category$caption',
    'product.category.group$caption'
]
```

**Generated SQL JOIN**:

```sql
SELECT ...
FROM fact_sales f
LEFT JOIN dim_product p ON f.product_key = p.product_key
LEFT JOIN dim_category c ON p.category_key = c.category_key
LEFT JOIN dim_category_group g ON c.group_key = g.group_key
```

### 2.4 Parent-Child Dimensions (Hierarchy)

Parent-child dimensions handle tree-structured data (e.g., organization hierarchy, product categories) using closure tables for efficient queries.

```javascript
{
    name: 'team',
    tableName: 'dim_team',
    foreignKey: 'team_id',
    primaryKey: 'team_id',
    captionColumn: 'team_name',
    caption: 'Team',
    description: 'Sales team',
    keyDescription: 'Team ID, string format',

    // Parent-child configuration
    closureTableName: 'team_closure',  // Closure table name (required)
    parentKey: 'parent_id',            // Ancestor column in closure table (required)
    childKey: 'team_id',               // Descendant column in closure table (required)

    properties: [
        { column: 'team_id', caption: 'Team ID', type: 'STRING' },
        { column: 'team_name', caption: 'Team Name', type: 'STRING' },
        { column: 'parent_id', caption: 'Parent Team', type: 'STRING' },
        { column: 'team_level', caption: 'Level', type: 'INTEGER' },
        { column: 'manager_name', caption: 'Manager', type: 'STRING' }
    ]
}
```

**Parent-Child Specific Fields**:

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `closureTableName` | string | Yes | Closure table name |
| `closureTableSchema` | string | No | Closure table schema |
| `parentKey` | string | Yes | Ancestor column in closure table (e.g., `parent_id`) |
| `childKey` | string | Yes | Descendant column in closure table (e.g., `team_id`) |

**Closure Table Structure Example**:

```sql
CREATE TABLE team_closure (
    parent_id VARCHAR(50),  -- Ancestor node ID
    child_id  VARCHAR(50),  -- Descendant node ID
    depth     INT,          -- Hierarchy depth (0 for self)
    PRIMARY KEY (parent_id, child_id)
);
```

> See [Parent-Child Dimensions](./parent-child.md) for detailed documentation

---

## 3. Property Definitions (properties)

Properties define the table's own fields (non-aggregatable).

### 3.1 Basic Properties

```javascript
properties: [
    {
        column: 'order_id',        // Database column name (required)
        name: 'orderId',           // Property name (optional)
        caption: 'Order ID',       // Display name
        description: 'Order unique identifier', // Detailed description
        type: 'STRING'             // Data type
    },
    {
        column: 'order_status',
        caption: 'Order Status',
        type: 'STRING'
    },
    {
        column: 'created_at',
        caption: 'Created Time',
        type: 'DATETIME'
    }
]
```

### 3.2 Dictionary Reference Properties

Use `dictRef` to map database values to display labels:

```javascript
import { dicts } from '../dicts.fsscript';

properties: [
    {
        column: 'order_status',
        caption: 'Order Status',
        type: 'STRING',
        dictRef: dicts.order_status  // Reference dictionary
    },
    {
        column: 'payment_method',
        caption: 'Payment Method',
        type: 'STRING',
        dictRef: dicts.payment_method
    }
]
```

**Dictionary Definition Example** (dicts.fsscript):

```javascript
import { registerDict } from '@jdbcModelDictService';

export const dicts = {
    order_status: registerDict({
        id: 'order_status',
        caption: 'Order Status',
        items: [
            { value: 'PENDING', label: 'Pending' },
            { value: 'CONFIRMED', label: 'Confirmed' },
            { value: 'SHIPPED', label: 'Shipped' },
            { value: 'COMPLETED', label: 'Completed' },
            { value: 'CANCELLED', label: 'Cancelled' }
        ]
    }),

    payment_method: registerDict({
        id: 'payment_method',
        caption: 'Payment Method',
        items: [
            { value: '1', label: 'Prepaid' },
            { value: '2', label: 'Collect' },
            { value: '3', label: 'Cash on Delivery' }
        ]
    })
};
```

### 3.3 Calculated Properties

Use `formulaDef` to define calculated fields. Common scenarios include JSON field extraction, string concatenation:

```javascript
properties: [
    {
        column: 'send_addr_info',  // JSON type field
        name: 'sendStreet',
        caption: 'Shipping Street',
        description: 'Extract street from address JSON',
        type: 'STRING',
        formulaDef: {
            builder: (alias) => {
                return `${alias}.send_addr_info ->> '$.send_street'`;
            },
            description: 'Extract street field from shipping address'
        }
    },
    {
        column: 'customer_name',
        name: 'fullName',
        caption: 'Full Name',
        type: 'STRING',
        formulaDef: {
            builder: (alias) => {
                return `CONCAT(${alias}.first_name, ' ', ${alias}.last_name)`;
            },
            description: 'Concatenate first and last name'
        }
    }
]
```

### 3.4 Property Field Reference

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `column` | string | Yes | Database column name |
| `name` | string | No | Property name, defaults to camelCase of column |
| `alias` | string | No | Property alias |
| `caption` | string | No | Display name |
| `description` | string | No | Detailed description, helps AI inference |
| `type` | string | No | Data type (see [5. Data Types](#5-data-types)) |
| `format` | string | No | Format template (for dates, etc.) |
| `dictRef` | string | No | Dictionary reference for value-to-label mapping |
| `formulaDef` | object | No | Formula definition (see 3.5) |

### 3.5 Formula Definition (formulaDef)

| Field | Type | Description |
|-------|------|-------------|
| `builder` | function | SQL builder function, parameter `alias` is table alias |
| `value` | string | Formula expression (based on measure names) |
| `description` | string | Text description of formula |

> `builder` and `value` are mutually exclusive, `builder` is more flexible for direct SQL manipulation

---

## 4. Measure Definitions (measures)

Measures define aggregatable numeric fields.

### 4.1 Basic Measures

```javascript
measures: [
    {
        column: 'quantity',         // Database column name (required)
        name: 'salesQuantity',      // Measure name (optional)
        caption: 'Sales Quantity',  // Display name
        description: 'Product sales count', // Detailed description
        type: 'INTEGER',            // Data type
        aggregation: 'sum'          // Aggregation method (required)
    },
    {
        column: 'sales_amount',
        name: 'salesAmount',
        caption: 'Sales Amount',
        type: 'MONEY',
        aggregation: 'sum'
    },
    {
        column: 'unit_price',
        caption: 'Unit Price',
        type: 'MONEY',
        aggregation: 'avg'          // Average aggregation
    }
]
```

### 4.2 Calculated Measures

Use `formulaDef` to define calculated measures:

```javascript
measures: [
    {
        column: 'tax_amount',
        name: 'taxAmount2',
        caption: 'Tax Amount*2',
        description: 'For testing calculated fields',
        type: 'MONEY',
        formulaDef: {
            builder: (alias) => {
                return `${alias}.tax_amount + 1`;
            },
            description: 'Tax amount plus one'
        }
    }
]
```

### 4.3 COUNT Aggregation

Count not based on specific column:

```javascript
measures: [
    {
        name: 'recordCount',
        caption: 'Record Count',
        aggregation: 'count',
        type: 'INTEGER'
    }
]
```

### 4.4 Measure Field Reference

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `column` | string | No¹ | Database column name |
| `name` | string | No | Measure name, defaults to camelCase of column |
| `alias` | string | No | Measure alias |
| `caption` | string | No | Display name |
| `description` | string | No | Detailed description |
| `type` | string | No | Data type (see [5. Data Types](#5-data-types)) |
| `aggregation` | string | Yes | Aggregation method (see 4.5) |
| `formulaDef` | object | No | Formula definition (see 3.5) |

> ¹ `count` aggregation doesn't require column

### 4.5 Aggregation Methods

| Value | Description | Applicable Types |
|-------|-------------|------------------|
| `sum` | Sum | Numeric types |
| `avg` | Average | Numeric types |
| `count` | Count | All types |
| `max` | Maximum | Numeric/Date types |
| `min` | Minimum | Numeric/Date types |
| `none` | No aggregation | All types |

---

## 5. Data Types

### 5.1 Type List

| Type | Aliases | Description | Java Type | Use Case |
|------|---------|-------------|-----------|----------|
| `STRING` | `TEXT` | String | String | Text, codes |
| `INTEGER` | - | Integer | Integer | Counts, enums |
| `BIGINT` | `LONG` | Long integer | Long | Large number PKs |
| `MONEY` | `NUMBER`, `BigDecimal` | Decimal/Money | BigDecimal | Amounts, prices |
| `DATETIME` | - | Datetime | Date | Timestamps |
| `DAY` | `DATE` | Date | Date | Date (yyyy-MM-dd) |
| `BOOL` | `Boolean` | Boolean | Boolean | Yes/No flags |
| `DICT` | - | Dictionary | Integer | Dictionary codes |

### 5.2 Type Selection Guidelines

- **Amount fields**: Use `MONEY` to avoid floating-point precision issues
- **Primary keys**: Surrogate keys use `INTEGER` or `BIGINT`, business keys use `STRING`
- **Date fields**: Use `DATETIME` for timestamps, `DAY` for date-only
- **Enum fields**: Prefer `dictRef` + `STRING` over creating dimension tables

---

## 6. Complete Examples

### 6.1 Fact Table Model

```javascript
// FactSalesModel.tm
/**
 * Sales Fact Table Model Definition
 *
 * @description E-commerce test data - Sales fact table (order details)
 *              Includes date, product, customer, store, channel, promotion dimensions
 */
import { dicts } from '../dicts.fsscript';

export const model = {
    name: 'FactSalesModel',
    caption: 'Sales Fact Table',
    tableName: 'fact_sales',
    idColumn: 'sales_key',

    // Dimension definitions
    dimensions: [
        {
            name: 'salesDate',
            tableName: 'dim_date',
            foreignKey: 'date_key',
            primaryKey: 'date_key',
            captionColumn: 'full_date',
            caption: 'Sales Date',
            description: 'Date of order',
            keyDescription: 'Date key, format yyyyMMdd, e.g., 20240101',

            properties: [
                { column: 'year', caption: 'Year', description: 'Year of sale' },
                { column: 'quarter', caption: 'Quarter', description: 'Quarter (1-4)' },
                { column: 'month', caption: 'Month', description: 'Month (1-12)' },
                { column: 'month_name', caption: 'Month Name' },
                { column: 'day_of_week', caption: 'Day of Week' },
                { column: 'is_weekend', caption: 'Is Weekend' }
            ]
        },
        {
            name: 'product',
            tableName: 'dim_product',
            foreignKey: 'product_key',
            primaryKey: 'product_key',
            captionColumn: 'product_name',
            caption: 'Product',
            description: 'Product information',

            properties: [
                { column: 'product_id', caption: 'Product ID' },
                { column: 'category_name', caption: 'Category Name' },
                { column: 'sub_category_name', caption: 'Sub-category Name' },
                { column: 'brand', caption: 'Brand' },
                { column: 'unit_price', caption: 'Unit Price', type: 'MONEY' },
                { column: 'unit_cost', caption: 'Unit Cost', type: 'MONEY' }
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
                { column: 'customer_id', caption: 'Customer ID' },
                { column: 'customer_type', caption: 'Customer Type' },
                { column: 'gender', caption: 'Gender' },
                { column: 'age_group', caption: 'Age Group' },
                { column: 'province', caption: 'Province' },
                { column: 'city', caption: 'City' },
                { column: 'member_level', caption: 'Member Level' }
            ]
        }
    ],

    // Property definitions
    properties: [
        {
            column: 'sales_key',
            caption: 'Sales Key',
            type: 'BIGINT'
        },
        {
            column: 'order_id',
            caption: 'Order ID',
            type: 'STRING'
        },
        {
            column: 'order_line_no',
            caption: 'Order Line',
            type: 'INTEGER'
        },
        {
            column: 'order_status',
            caption: 'Order Status',
            type: 'STRING',
            dictRef: dicts.order_status
        },
        {
            column: 'payment_method',
            caption: 'Payment Method',
            type: 'STRING',
            dictRef: dicts.payment_method
        },
        {
            column: 'created_at',
            caption: 'Created Time',
            type: 'DATETIME'
        }
    ],

    // Measure definitions
    measures: [
        {
            column: 'quantity',
            caption: 'Quantity',
            type: 'INTEGER',
            aggregation: 'sum'
        },
        {
            column: 'sales_amount',
            name: 'salesAmount',
            caption: 'Sales Amount',
            type: 'MONEY',
            aggregation: 'sum'
        },
        {
            column: 'cost_amount',
            name: 'costAmount',
            caption: 'Cost Amount',
            type: 'MONEY',
            aggregation: 'sum'
        },
        {
            column: 'profit_amount',
            name: 'profitAmount',
            caption: 'Profit Amount',
            type: 'MONEY',
            aggregation: 'sum'
        }
    ]
};
```

### 6.2 Dimension Table Model

```javascript
// DimProductModel.tm
/**
 * Product Dimension Model Definition
 *
 * @description E-commerce test data - Product dimension table
 */
export const model = {
    name: 'DimProductModel',
    caption: 'Product Dimension',
    tableName: 'dim_product',
    idColumn: 'product_key',

    dimensions: [],  // Dimension tables typically don't reference other dimensions

    properties: [
        {
            column: 'product_key',
            caption: 'Product Key',
            type: 'INTEGER'
        },
        {
            column: 'product_id',
            caption: 'Product Business ID',
            type: 'STRING'
        },
        {
            column: 'product_name',
            caption: 'Product Name',
            type: 'STRING'
        },
        {
            column: 'category_id',
            caption: 'Category ID',
            type: 'STRING'
        },
        {
            column: 'category_name',
            caption: 'Category Name',
            type: 'STRING'
        },
        {
            column: 'sub_category_id',
            caption: 'Sub-category ID',
            type: 'STRING'
        },
        {
            column: 'sub_category_name',
            caption: 'Sub-category Name',
            type: 'STRING'
        },
        {
            column: 'brand',
            caption: 'Brand',
            type: 'STRING'
        },
        {
            column: 'unit_price',
            caption: 'Price',
            type: 'MONEY'
        },
        {
            column: 'unit_cost',
            caption: 'Cost',
            type: 'MONEY'
        },
        {
            column: 'status',
            caption: 'Status',
            type: 'STRING'
        },
        {
            column: 'created_at',
            caption: 'Created Time',
            type: 'DATETIME'
        }
    ],

    measures: []  // Dimension tables typically have no measures
};
```

---

## 7. Dimension Reuse Best Practices

In real projects, the same dimension tables (e.g., date dimension, customer dimension) are often referenced by multiple fact tables. TM files use FSScript (JavaScript-like) syntax, supporting function encapsulation and module imports. You can extract common dimension configurations into factory functions for dimension definition reuse.

### 7.1 Creating Dimension Builders

Encapsulate commonly used dimensions as functions in a separate file:

```javascript
// dimensions/common-dims.fsscript
/**
 * Common Dimension Builders
 * Provides reusable dimension definition factory functions
 */

/**
 * Build Date Dimension
 * @param {object} options - Configuration options
 * @param {string} options.name - Dimension name, default 'salesDate'
 * @param {string} options.foreignKey - Foreign key column, default 'date_key'
 * @param {string} options.caption - Display name, default 'Date'
 * @returns {object} Dimension configuration object
 */
export function buildDateDim(options = {}) {
    const {
        name = 'salesDate',
        foreignKey = 'date_key',
        caption = 'Date',
        description = 'Business event date'
    } = options;

    return {
        name,
        tableName: 'dim_date',
        foreignKey,
        primaryKey: 'date_key',
        captionColumn: 'full_date',
        caption,
        description,
        keyDescription: 'Date key, format yyyyMMdd, e.g., 20240101',
        type: 'DATETIME',

        properties: [
            { column: 'year', caption: 'Year', type: 'INTEGER', description: 'Year' },
            { column: 'quarter', caption: 'Quarter', type: 'INTEGER', description: 'Quarter (1-4)' },
            { column: 'month', caption: 'Month', type: 'INTEGER', description: 'Month (1-12)' },
            { column: 'month_name', caption: 'Month Name', type: 'STRING' },
            { column: 'week_of_year', caption: 'Week of Year', type: 'INTEGER' },
            { column: 'day_of_week', caption: 'Day of Week', type: 'INTEGER' },
            { column: 'is_weekend', caption: 'Is Weekend', type: 'BOOL' },
            { column: 'is_holiday', caption: 'Is Holiday', type: 'BOOL' }
        ]
    };
}

/**
 * Build Customer Dimension
 * @param {object} options - Configuration options
 */
export function buildCustomerDim(options = {}) {
    const {
        name = 'customer',
        foreignKey = 'customer_key',
        caption = 'Customer',
        description = 'Customer information'
    } = options;

    return {
        name,
        tableName: 'dim_customer',
        foreignKey,
        primaryKey: 'customer_key',
        captionColumn: 'customer_name',
        caption,
        description,
        keyDescription: 'Customer surrogate key, auto-increment integer',

        properties: [
            { column: 'customer_id', caption: 'Customer ID', description: 'Customer unique identifier' },
            { column: 'customer_type', caption: 'Customer Type', description: 'Individual/Enterprise' },
            { column: 'gender', caption: 'Gender' },
            { column: 'age_group', caption: 'Age Group' },
            { column: 'province', caption: 'Province' },
            { column: 'city', caption: 'City' },
            { column: 'member_level', caption: 'Member Level' }
        ]
    };
}

/**
 * Build Product Dimension
 * @param {object} options - Configuration options
 */
export function buildProductDim(options = {}) {
    const {
        name = 'product',
        foreignKey = 'product_key',
        caption = 'Product',
        description = 'Product information'
    } = options;

    return {
        name,
        tableName: 'dim_product',
        foreignKey,
        primaryKey: 'product_key',
        captionColumn: 'product_name',
        caption,
        description,
        keyDescription: 'Product surrogate key, auto-increment integer',

        properties: [
            { column: 'product_id', caption: 'Product ID' },
            { column: 'category_name', caption: 'Category Name' },
            { column: 'sub_category_name', caption: 'Sub-category Name' },
            { column: 'brand', caption: 'Brand' },
            { column: 'unit_price', caption: 'Unit Price', type: 'MONEY' },
            { column: 'unit_cost', caption: 'Unit Cost', type: 'MONEY' }
        ]
    };
}

/**
 * Build Store Dimension
 */
export function buildStoreDim(options = {}) {
    const {
        name = 'store',
        foreignKey = 'store_key',
        caption = 'Store',
        description = 'Store information'
    } = options;

    return {
        name,
        tableName: 'dim_store',
        foreignKey,
        primaryKey: 'store_key',
        captionColumn: 'store_name',
        caption,
        description,

        properties: [
            { column: 'store_id', caption: 'Store ID' },
            { column: 'store_type', caption: 'Store Type' },
            { column: 'province', caption: 'Province' },
            { column: 'city', caption: 'City' }
        ]
    };
}
```

### 7.2 Using Dimension Builders in TM Files

```javascript
// model/FactSalesModel.tm
import { dicts } from '../dicts.fsscript';
import {
    buildDateDim,
    buildCustomerDim,
    buildProductDim,
    buildStoreDim
} from '../dimensions/common-dims.fsscript';

export const model = {
    name: 'FactSalesModel',
    caption: 'Sales Fact Table',
    tableName: 'fact_sales',
    idColumn: 'sales_key',

    dimensions: [
        // Use builder with custom name and description
        buildDateDim({
            name: 'salesDate',
            caption: 'Sales Date',
            description: 'Order date'
        }),

        // Use default configuration
        buildCustomerDim(),
        buildProductDim(),
        buildStoreDim(),

        // Mixed use: builder + inline dimension
        {
            name: 'channel',
            tableName: 'dim_channel',
            foreignKey: 'channel_key',
            primaryKey: 'channel_key',
            captionColumn: 'channel_name',
            caption: 'Channel',
            properties: [
                { column: 'channel_id', caption: 'Channel ID' },
                { column: 'channel_type', caption: 'Channel Type' }
            ]
        }
    ],

    properties: [
        // ... property definitions
    ],

    measures: [
        // ... measure definitions
    ]
};
```

```javascript
// model/FactOrderModel.tm
import { buildDateDim, buildCustomerDim } from '../dimensions/common-dims.fsscript';

export const model = {
    name: 'FactOrderModel',
    caption: 'Order Fact Table',
    tableName: 'fact_order',
    idColumn: 'order_key',

    dimensions: [
        // Order model uses different dimension name
        buildDateDim({
            name: 'orderDate',
            foreignKey: 'order_date_key',
            caption: 'Order Date'
        }),
        buildCustomerDim(),

        // Order may have multiple date dimensions
        buildDateDim({
            name: 'shipDate',
            foreignKey: 'ship_date_key',
            caption: 'Ship Date',
            description: 'Order shipping date'
        })
    ],

    properties: [...],
    measures: [...]
};
```

### 7.3 Advanced Technique: Property Extension and Override

Builder-returned objects can be extended or overridden using spread operator:

```javascript
import { buildCustomerDim } from '../dimensions/common-dims.fsscript';

export const model = {
    name: 'FactVIPSalesModel',
    caption: 'VIP Sales Fact Table',
    tableName: 'fact_vip_sales',

    dimensions: [
        // Extend customer dimension with additional properties
        {
            ...buildCustomerDim({ caption: 'VIP Customer' }),
            // Add VIP-specific properties
            properties: [
                ...buildCustomerDim().properties,
                { column: 'vip_level', caption: 'VIP Level' },
                { column: 'vip_points', caption: 'Points Balance', type: 'INTEGER' },
                { column: 'vip_expire_date', caption: 'Membership Expiry', type: 'DAY' }
            ]
        }
    ],

    // ...
};
```

### 7.4 Reusing Nested Dimensions

For snowflake schema nested dimensions, build complete hierarchies with sub-dimensions:

```javascript
// dimensions/product-hierarchy.fsscript
/**
 * Build Product Dimension with Category Hierarchy (Snowflake Schema)
 */
export function buildProductWithCategoryDim(options = {}) {
    const {
        name = 'product',
        foreignKey = 'product_key',
        caption = 'Product'
    } = options;

    return {
        name,
        tableName: 'dim_product',
        foreignKey,
        primaryKey: 'product_key',
        captionColumn: 'product_name',
        caption,

        properties: [
            { column: 'product_id', caption: 'Product ID' },
            { column: 'brand', caption: 'Brand' },
            { column: 'unit_price', caption: 'Price', type: 'MONEY' }
        ],

        // Nested category dimension
        dimensions: [
            {
                name: 'category',
                alias: 'productCategory',
                tableName: 'dim_category',
                foreignKey: 'category_key',
                primaryKey: 'category_key',
                captionColumn: 'category_name',
                caption: 'Category',

                properties: [
                    { column: 'category_id', caption: 'Category ID' },
                    { column: 'category_level', caption: 'Category Level' }
                ],

                // Continue nesting with category group
                dimensions: [
                    {
                        name: 'group',
                        alias: 'categoryGroup',
                        tableName: 'dim_category_group',
                        foreignKey: 'group_key',
                        primaryKey: 'group_key',
                        captionColumn: 'group_name',
                        caption: 'Category Group',

                        properties: [
                            { column: 'group_id', caption: 'Group ID' },
                            { column: 'group_type', caption: 'Group Type' }
                        ]
                    }
                ]
            }
        ]
    };
}
```

### 7.5 Reusing Parent-Child Dimensions

For organization hierarchy and similar parent-child dimensions, parameterize closure table configuration:

```javascript
// dimensions/hierarchy-dims.fsscript
/**
 * Build Organization/Team Parent-Child Dimension
 */
export function buildOrgDim(options = {}) {
    const {
        name = 'team',
        tableName = 'dim_team',
        foreignKey = 'team_id',
        closureTableName = 'team_closure',
        caption = 'Team',
        description = 'Organization team'
    } = options;

    return {
        name,
        tableName,
        foreignKey,
        primaryKey: 'team_id',
        captionColumn: 'team_name',
        caption,
        description,

        // Parent-child configuration
        closureTableName,
        parentKey: 'parent_id',
        childKey: 'team_id',

        properties: [
            { column: 'team_id', caption: 'Team ID', type: 'STRING' },
            { column: 'team_name', caption: 'Team Name', type: 'STRING' },
            { column: 'parent_id', caption: 'Parent Team', type: 'STRING' },
            { column: 'team_level', caption: 'Level', type: 'INTEGER' },
            { column: 'manager_name', caption: 'Manager', type: 'STRING' }
        ]
    };
}

/**
 * Build Region Parent-Child Dimension
 */
export function buildRegionDim(options = {}) {
    const {
        name = 'region',
        foreignKey = 'region_id',
        caption = 'Region'
    } = options;

    return {
        name,
        tableName: 'dim_region',
        foreignKey,
        primaryKey: 'region_id',
        captionColumn: 'region_name',
        caption,

        closureTableName: 'region_closure',
        parentKey: 'parent_id',
        childKey: 'region_id',

        properties: [
            { column: 'region_id', caption: 'Region ID', type: 'STRING' },
            { column: 'region_name', caption: 'Region Name', type: 'STRING' },
            { column: 'region_type', caption: 'Region Type', type: 'STRING' },
            { column: 'region_level', caption: 'Level', type: 'INTEGER' }
        ]
    };
}
```

### 7.6 Recommended Project Structure

```
templates/
├── dimensions/                    # Dimension builders directory
│   ├── common-dims.fsscript       # Common dimensions (date, customer, product, etc.)
│   ├── hierarchy-dims.fsscript    # Parent-child dimensions (org, region, etc.)
│   └── product-hierarchy.fsscript # Product snowflake dimension
├── model/                         # TM model directory
│   ├── FactSalesModel.tm
│   ├── FactOrderModel.tm
│   └── ...
├── query/                         # QM query model directory
│   └── ...
└── dicts.fsscript                 # Dictionary definitions
```

### 7.7 Dimension Reuse Best Practices Summary

| Practice | Description |
|----------|-------------|
| **Function Encapsulation** | Encapsulate reused dimension definitions as factory functions |
| **Parameterized Configuration** | Support customization through parameters |
| **Default Values** | Provide reasonable defaults for common parameters |
| **Modular Organization** | Organize by dimension type into different `.fsscript` files |
| **Property Extension** | Use spread operator `...` to extend or override properties |
| **Unified Maintenance** | Modify in one place, effective globally |
| **Documentation Comments** | Add JSDoc comments to builder functions for easier use |

---

## 8. Naming Conventions

### 8.1 File Naming

- TM files: `{ModelName}Model.tm`
- Fact tables: `Fact{BusinessName}Model.tm`, e.g., `FactSalesModel.tm`
- Dimension tables: `Dim{BusinessName}Model.tm`, e.g., `DimCustomerModel.tm`
- Dictionary file: `dicts.fsscript`

### 8.2 Field Naming

| Location | Convention | Example |
|----------|------------|---------|
| Model `name` | PascalCase | `FactSalesModel`, `DimCustomerModel` |
| Field `name` | camelCase | `orderId`, `salesAmount`, `customerType` |
| Database `column` | snake_case | `order_id`, `sales_amount`, `customer_type` |
| Dimension property reference | `$` separator | `customer$caption`, `salesDate$year` |

### 8.3 Model Design Guidelines

1. **Fact Tables**:
   - Include business fact measures (sales amount, quantity, etc.)
   - Include foreign keys to dimension tables
   - Define clear granularity (order-line level, order level)

2. **Dimension Tables**:
   - Include descriptive attributes
   - Use surrogate keys as primary keys
   - Dimension tables typically don't define measures

3. **Star Schema vs Snowflake Schema**:
   - Star schema: Flat dimensions, better query performance (recommended)
   - Snowflake schema: Nested dimensions, saves storage space, use when needed

---

## 9. Advanced Features

### 9.1 Extended Data

Use `extData` to store custom metadata:

```javascript
{
    name: 'FactSalesModel',
    caption: 'Sales Fact Table',
    extData: {
        businessOwner: 'Sales Department',
        updateFrequency: 'daily',
        customTag: 'core-metric'
    }
}
```

### 9.2 Deprecation Marker

Mark obsolete models or fields:

```javascript
{
    name: 'oldSalesAmount',
    caption: 'Legacy Sales Amount',
    column: 'old_sales_amt',
    type: 'MONEY',
    deprecated: true  // Shows deprecation warning in frontend configuration
}
```

---

## Next Steps

- [QM Syntax Manual](./qm-syntax.md) - Query model definition
- [Query DSL](./query-dsl.md) - Complete query DSL syntax
- [Parent-Child Dimensions](./parent-child.md) - Hierarchy dimension details
- [Calculated Fields](./calculated-fields.md) - Complex calculation logic
- [Query API](../api/query-api.md) - HTTP API reference
