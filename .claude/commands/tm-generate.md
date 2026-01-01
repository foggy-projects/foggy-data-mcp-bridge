# TM Generator Skill

Generate a TM (Table Model) file for the Foggy Dataset Model system based on user input.

## Input Types

The user may provide:
1. **DDL Statement**: A `CREATE TABLE` SQL statement
2. **Table Description**: Natural language description of the table and its columns
3. **Existing Table Name**: Reference to an existing database table (you'll need to infer structure)

## Output Requirements

Generate a complete TM file following this structure:

```javascript
/**
 * {Model Description}
 * @description {Detailed description}
 */
import { dicts } from '../dicts.fsscript';

export const model = {
    name: '{ModelName}Model',
    caption: '{Display Name}',
    description: '{Description for AI}',
    tableName: '{table_name}',
    idColumn: '{primary_key}',

    dimensions: [
        // Dimension relationships
    ],

    properties: [
        // Non-aggregatable fields
    ],

    measures: [
        // Aggregatable numeric fields (for fact tables)
    ]
};
```

## Type Mapping Rules

Map database types to TM types:

| Database Type | TM Type | Use Case |
|--------------|---------|----------|
| VARCHAR, TEXT, CHAR | `STRING` | Text, codes, IDs |
| INT, SMALLINT | `INTEGER` | Counts, small numbers |
| BIGINT | `BIGINT` / `LONG` | Large numbers, surrogate keys |
| DECIMAL, NUMERIC, MONEY | `MONEY` | Amounts, prices (use BigDecimal) |
| DATE | `DAY` | Date only (yyyy-MM-dd) |
| DATETIME, TIMESTAMP | `DATETIME` | Timestamps |
| BOOLEAN, TINYINT(1) | `BOOL` | Yes/No flags |

## Naming Conventions

- **Model name**: PascalCase, suffix with `Model` (e.g., `FactSalesModel`, `DimCustomerModel`)
- **Property/Measure name**: camelCase (e.g., `orderId`, `salesAmount`)
- **Fact tables**: Prefix with `Fact` (e.g., `FactSalesModel`)
- **Dimension tables**: Prefix with `Dim` (e.g., `DimCustomerModel`)

## Dimension Detection Rules

Detect potential dimensions by looking for:
1. Columns ending with `_key` or `_id` that reference other tables
2. Foreign key constraints in DDL
3. Common dimension patterns:
   - `date_key`, `time_key` → Date dimension
   - `customer_key`, `customer_id` → Customer dimension
   - `product_key`, `product_id` → Product dimension
   - `store_key`, `store_id` → Store dimension

## Dimension Reuse Best Practice

When dimensions are commonly reused (date, customer, product), suggest using dimension builders:

```javascript
import { buildDateDim, buildCustomerDim } from '../dimensions/common-dims.fsscript';

export const model = {
    // ...
    dimensions: [
        buildDateDim({ name: 'salesDate', caption: 'Sales Date' }),
        buildCustomerDim(),
        // Custom dimensions can still be inline
    ]
};
```

## Fact vs Dimension Table Detection

**Fact Table indicators**:
- Contains foreign keys to multiple dimension tables
- Has numeric columns suitable for aggregation (amounts, quantities, counts)
- Table name often contains: `fact_`, `fct_`, `sales`, `orders`, `transactions`

**Dimension Table indicators**:
- Contains descriptive attributes
- Has a surrogate key (auto-increment) and possibly a business key
- Table name often contains: `dim_`, `dimension_`, or is a noun (customers, products, dates)

## Measure vs Property Detection

**Measure indicators** (aggregatable):
- Numeric types (DECIMAL, INT for quantities)
- Column names containing: `amount`, `qty`, `quantity`, `count`, `total`, `sum`, `price`, `cost`, `profit`
- Default aggregation: `sum` for amounts, `count` for counts, `avg` for averages

**Property indicators** (non-aggregatable):
- String/Text types
- Date/Boolean types
- Identifier columns
- Status, type, category columns

## Caption and Description Guidelines

- **caption**: Short display name in user's language
- **description**: Detailed explanation for AI natural language queries
- Always provide meaningful captions and descriptions for better AI integration

## Example Output

For DDL:
```sql
CREATE TABLE fact_sales (
    sales_key BIGINT PRIMARY KEY,
    date_key INT NOT NULL,
    customer_key INT NOT NULL,
    product_key INT NOT NULL,
    order_id VARCHAR(50),
    quantity INT,
    unit_price DECIMAL(10,2),
    sales_amount DECIMAL(12,2),
    cost_amount DECIMAL(12,2)
);
```

Generate:
```javascript
/**
 * Sales Fact Table Model
 * @description E-commerce sales order detail records
 */
import { buildDateDim, buildCustomerDim, buildProductDim } from '../dimensions/common-dims.fsscript';

export const model = {
    name: 'FactSalesModel',
    caption: 'Sales Fact Table',
    description: 'Sales transaction details with customer, product, and date dimensions',
    tableName: 'fact_sales',
    idColumn: 'sales_key',

    dimensions: [
        buildDateDim({ name: 'salesDate', foreignKey: 'date_key', caption: 'Sales Date' }),
        buildCustomerDim({ foreignKey: 'customer_key' }),
        buildProductDim({ foreignKey: 'product_key' })
    ],

    properties: [
        {
            column: 'sales_key',
            caption: 'Sales Key',
            description: 'Surrogate key for sales record',
            type: 'BIGINT'
        },
        {
            column: 'order_id',
            caption: 'Order ID',
            description: 'Business order identifier',
            type: 'STRING'
        }
    ],

    measures: [
        {
            column: 'quantity',
            caption: 'Quantity',
            description: 'Number of units sold',
            type: 'INTEGER',
            aggregation: 'sum'
        },
        {
            column: 'unit_price',
            caption: 'Unit Price',
            description: 'Price per unit',
            type: 'MONEY',
            aggregation: 'avg'
        },
        {
            column: 'sales_amount',
            name: 'salesAmount',
            caption: 'Sales Amount',
            description: 'Total sales amount',
            type: 'MONEY',
            aggregation: 'sum'
        },
        {
            column: 'cost_amount',
            name: 'costAmount',
            caption: 'Cost Amount',
            description: 'Total cost amount',
            type: 'MONEY',
            aggregation: 'sum'
        }
    ]
};
```

## Checklist Before Output

- [ ] Model name follows naming convention (Fact*/Dim* prefix)
- [ ] All columns have appropriate types
- [ ] Potential dimensions identified and configured
- [ ] Measures have aggregation methods
- [ ] Captions provided for all fields
- [ ] Descriptions provided for important fields (especially for AI)
- [ ] Dictionary references suggested for enum/status fields
- [ ] Dimension reuse suggested where applicable

Now, based on the user's input, generate the TM file.
