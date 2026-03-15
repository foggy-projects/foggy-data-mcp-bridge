# Quick Start

This guide helps you create TM/QM models and use DSL for queries in 10 minutes.

## 1. Environment Setup

If you are not a Java developer, it is recommended to use Docker for experience, see (TODO)

If you are a Java developer, it is recommended to use IDEs such as IDEA to introduce foggy-dataset-model through Maven for experience,
- Any IDE
- JDK 17+
- Maven
- MySQL, PostgreSQL, SQLite, or other relational databases

If you do not want to manually perform the following steps, you can deliver the prepared documentation (TODO) to current AI programming tools, such as trae, claude code, etc., to let them help you build basic examples or introduce dependencies

## 2. Add Dependencies

### 2.1 Core Dependencies (Required)

Add Foggy Dataset Model dependency in `pom.xml`:

```xml
<dependency>
    <groupId>com.foggysource</groupId>
    <artifactId>foggy-dataset-model</artifactId>
    <version>8.1.8.beta</version>
</dependency>
```

### 2.2 Complete Configuration for New Project

If you are creating a new project, you need to add the following dependencies:

```xml
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.2.0</version>
</parent>

<dependencies>
    <!-- Spring Boot Web -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>

    <!-- Spring Boot JDBC -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-jdbc</artifactId>
    </dependency>

    <!-- Foggy Dataset Model -->
    <dependency>
        <groupId>com.foggysource</groupId>
        <artifactId>foggy-dataset-model</artifactId>
        <version>8.1.8.beta</version>
    </dependency>

    <!-- Database drivers (choose one according to actual situation) -->
    <!-- MySQL -->
    <dependency>
        <groupId>com.mysql</groupId>
        <artifactId>mysql-connector-j</artifactId>
        <scope>runtime</scope>
    </dependency>

    <!-- Or PostgreSQL -->
    <!--
    <dependency>
        <groupId>org.postgresql</groupId>
        <artifactId>postgresql</artifactId>
        <scope>runtime</scope>
    </dependency>
    -->

    <!-- Or SQLite (suitable for quick testing) -->
    <!--
    <dependency>
        <groupId>org.xerial</groupId>
        <artifactId>sqlite-jdbc</artifactId>
        <version>3.44.1.0</version>
    </dependency>
    -->
</dependencies>
```

### 2.3 Introducing into Existing Project

If your project already has Spring Boot and data source configuration, you only need to:

1. Add `foggy-dataset-model` core dependency (see 2.1)
2. Ensure the project has JDBC related dependencies (`spring-boot-starter-jdbc` or `spring-boot-starter-data-jpa`)
3. Ensure the data source is configured

> **Tip**: Foggy Dataset Model can coexist with existing persistence layer frameworks such as MyBatis, JPA, etc.

## 3. Configure Main Application Class

Add `@EnableFoggyFramework` annotation to the Spring Boot main application class:

```java
@SpringBootApplication
@EnableFoggyFramework(bundleName = "my-foggy-demo")
public class MyApplication {

    public static void main(String[] args) {
        SpringApplication.run(MyApplication.class, args);
    }
}
```

> **Note**: `bundleName` is the unique identifier of the module, used to distinguish different business modules.

---

## 4. Configure Data Source and Project Structure

### 4.1 Configure Data Source

**New Project**: Create `src/main/resources/application.yml`:

```yaml
spring:
  datasource:
    # MySQL example
    url: jdbc:mysql://localhost:3306/your_database?useUnicode=true&characterEncoding=utf8
    username: root
    password: your_password
    driver-class-name: com.mysql.cj.jdbc.Driver

    # Or PostgreSQL example
    # url: jdbc:postgresql://localhost:5432/your_database
    # username: postgres
    # password: your_password
    # driver-class-name: org.postgresql.Driver

    # Or SQLite example (quick testing)
    # url: jdbc:sqlite:./data/demo.db
    # driver-class-name: org.sqlite.JDBC

# Foggy configuration (optional)
foggy:
  dataset:
    # SQL log configuration
    show-sql: true               # Print SQL statements to console (for development debugging)
    sql-format: false            # Whether to format SQL (true=multiple lines, false=single line)
    sql-log-level: DEBUG         # SQL log level (DEBUG/INFO)
    show-sql-parameters: true    # Show SQL parameter values
    show-execution-time: true    # Show SQL execution time
```

**Existing Project**: If your project has already configured a data source, no modification is needed, Foggy will automatically use the existing data source.

### 4.2 Create Model File Directory

Create the directory for storing TM/QM model files:

```
src/main/resources/
└── foggy/
    └── templates/     # Storage location for TM and QM files
```

**Existing Project**: Just create the `foggy/templates/` directory under `src/main/resources`.

---

## 5. Scenario Description

Assume we have a simple e-commerce system containing:
- Order fact table `fact_order`
- Customer dimension table `dim_customer`
- Product dimension table `dim_product`

```sql
-- Customer dimension table
CREATE TABLE dim_customer (
    customer_id VARCHAR(64) PRIMARY KEY,
    customer_name VARCHAR(100),
    customer_type VARCHAR(20),
    province VARCHAR(50),
    city VARCHAR(50)
);

-- Product dimension table
CREATE TABLE dim_product (
    product_id VARCHAR(64) PRIMARY KEY,
    product_name VARCHAR(100),
    category VARCHAR(50),
    unit_price DECIMAL(10,2)
);

-- Order fact table
CREATE TABLE fact_order (
    order_id VARCHAR(64) PRIMARY KEY,
    customer_id VARCHAR(64),
    product_id VARCHAR(64),
    order_status VARCHAR(20),
    quantity INT,
    amount DECIMAL(10,2),
    order_time DATETIME
);

-- Insert customer data
INSERT INTO dim_customer (customer_id, customer_name, customer_type, province, city) VALUES
('CUST001', '张三', 'VIP', '广东省', '深圳市'),
('CUST002', '李四', '普通', '广东省', '广州市'),
('CUST003', '王五', 'VIP', '北京市', '北京市'),
('CUST004', '赵六', '普通', '上海市', '上海市'),
('CUST005', '钱七', 'VIP', '浙江省', '杭州市');

-- Insert product data
INSERT INTO dim_product (product_id, product_name, category, unit_price) VALUES
('PROD001', 'iPhone 15', '数码电器', 6999.00),
('PROD002', 'MacBook Pro', '数码电器', 12999.00),
('PROD003', '小米手机', '数码电器', 2999.00),
('PROD004', '耐克运动鞋', '服装鞋帽', 599.00),
('PROD005', '阿迪达斯外套', '服装鞋帽', 899.00);

-- Insert order data
INSERT INTO fact_order (order_id, customer_id, product_id, order_status, quantity, amount, order_time) VALUES
('ORD20240101001', 'CUST001', 'PROD001', 'COMPLETED', 1, 6999.00, '2024-01-15 10:30:00'),
('ORD20240101002', 'CUST001', 'PROD002', 'COMPLETED', 1, 12999.00, '2024-02-20 14:20:00'),
('ORD20240101003', 'CUST002', 'PROD003', 'SHIPPED', 2, 5998.00, '2024-03-10 09:15:00'),
('ORD20240101004', 'CUST003', 'PROD001', 'COMPLETED', 1, 6999.00, '2024-04-05 16:45:00'),
('ORD20240101005', 'CUST004', 'PROD004', 'PAID', 3, 1797.00, '2024-05-12 11:20:00'),
('ORD20240101006', 'CUST005', 'PROD005', 'COMPLETED', 2, 1798.00, '2024-06-08 13:30:00'),
('ORD20240101007', 'CUST002', 'PROD001', 'CANCELLED', 1, 6999.00, '2024-07-15 15:10:00'),
('ORD20240101008', 'CUST003', 'PROD002', 'COMPLETED', 1, 12999.00, '2024-08-22 10:00:00'),
('ORD20240101009', 'CUST001', 'PROD004', 'COMPLETED', 4, 2396.00, '2024-09-03 12:40:00'),
('ORD20240101010', 'CUST005', 'PROD003', 'SHIPPED', 1, 2999.00, '2024-10-18 14:55:00')
```

### 5.1 SQL Execution Method

The above SQL can be executed through any of the following methods:

**Method 1: Automatic Execution (Recommended for New Projects)**

Create SQL files and configure automatic execution:

1. Create files:
   - `src/main/resources/db/schema.sql` - Store table structure
   - `src/main/resources/db/data.sql` - Store test data

2. Configure in `application.yml`:

```yaml
spring:
  sql:
    init:
      mode: always  # Or embedded (only for embedded databases)
      schema-locations: classpath:db/schema.sql
      data-locations: classpath:db/data.sql
      encoding: UTF-8
```

**Method 2: Manual Execution (Recommended for Existing Projects)**

Execute the above SQL directly using database client tools:
- MySQL Workbench, Navicat, DBeaver, etc.
- Or use command line client: `mysql -u root -p < schema.sql`

**Method 3: Use Database Migration Tools**

If the project uses Flyway or Liquibase, you can add the SQL to the corresponding migration script.

> **Note**: For production environments, it is recommended to use method 2 or method 3 to avoid data being repeatedly initialized.

---

## 6. Create TM Model

### 6.1 Create Fact Table Model

Create file `src/main/resources/foggy/templates/FactOrderModel.tm`:

```javascript
// FactOrderModel.tm - Order fact table model

export const model = {
    name: 'FactOrderModel',
    caption: 'Order Fact Table',
    tableName: 'fact_order',
    idColumn: 'order_id',

    // Dimension definition: associate customers and products
    dimensions: [
        {
            name: 'customer',
            caption: 'Customer',
            tableName: 'dim_customer',
            foreignKey: 'customer_id',
            primaryKey: 'customer_id',
            captionColumn: 'customer_name',
            properties: [
                { column: 'customer_id', caption: 'Customer ID' },
                { column: 'customer_name', caption: 'Customer Name' },
                { column: 'customer_type', caption: 'Customer Type' },
                { column: 'province', caption: 'Province' },
                { column: 'city', caption: 'City' }
            ]
        },
        {
            name: 'product',
            caption: 'Product',
            tableName: 'dim_product',
            foreignKey: 'product_id',
            primaryKey: 'product_id',
            captionColumn: 'product_name',
            properties: [
                { column: 'product_id', caption: 'Product ID' },
                { column: 'product_name', caption: 'Product Name' },
                { column: 'category', caption: 'Category' },
                { column: 'unit_price', caption: 'Unit Price', type: 'MONEY' }
            ]
        }
    ],

    // Property definition: fact table's own fields
    properties: [
        { column: 'order_id', caption: 'Order ID', type: 'STRING' },
        { column: 'order_status', caption: 'Order Status', type: 'STRING' },
        { column: 'order_time', caption: 'Order Time', type: 'DATETIME' }
    ],

    // Measure definition: aggregatable numeric values
    measures: [
        {
            column: 'quantity',
            caption: 'Order Quantity',
            type: 'INTEGER',
            aggregation: 'sum'
        },
        {
            column: 'amount',
            caption: 'Order Amount',
            type: 'MONEY',
            aggregation: 'sum'
        }
    ]
};
```

### 6.2 TM Model Key Points

| Configuration Item | Description |
|--------|------|
| `name` | Unique model identifier, referenced by this name in QM |
| `tableName` | Corresponding database table name |
| `dimensions` | Dimension definition, automatically generates JOIN when querying |
| `properties` | Property definition, does not participate in aggregation |
| `measures` | Measure definition, aggregatable numeric fields |

---

## 7. Create QM Model

Create file `src/main/resources/foggy/templates/FactOrderQueryModel.qm`:

```javascript
// FactOrderQueryModel.qm - Order query model

export const queryModel = {
    name: 'FactOrderQueryModel',
    caption: 'Order Query',
    model: 'FactOrderModel',   // Associated TM model

    // Column group definition: organize queryable fields
    columnGroups: [
        {
            caption: 'Order Information',
            items: [
                { name: 'orderId' },
                { name: 'orderStatus' },
                { name: 'orderTime' }
            ]
        },
        {
            caption: 'Customer Information',
            items: [
                { name: 'customer$caption' },       // Customer name
                { name: 'customer$customerType' },  // Customer type
                { name: 'customer$province' }       // Province
            ]
        },
        {
            caption: 'Product Information',
            items: [
                { name: 'product$caption' },        // Product name
                { name: 'product$category' },       // Category
                { name: 'product$unitPrice' }       // Unit price
            ]
        },
        {
            caption: 'Measures',
            items: [
                { name: 'quantity' },
                { name: 'amount' }
            ]
        }
    ],

    // Default sorting
    orders: [
        { name: 'orderTime', order: 'desc' }
    ],

    // Permission control (optional)
    accesses: []
};
```

### 7.1 QM Field Reference Format

| Format | Description | Example |
|------|------|------|
| `Property Name` | Fact table property | `orderId`, `orderStatus` |
| `Measure Name` | Measure field | `amount`, `quantity` |
| `Dimension Name$caption` | Dimension display value | `customer$caption` |
| `Dimension Name$Property Name` | Dimension other properties | `customer$customerType` |

---

## 8. Start and Test

### 8.1 Start Project

Use Maven to start the Spring Boot project:

```bash
# Execute in the project root directory
mvn spring-boot:run
```

Or run the main application class directly in the IDE (the class with `@SpringBootApplication` annotation).

### 8.2 Verify Successful Startup

After successful startup, you can see logs similar to the following:

```
Started MyApplication in 3.5 seconds (JVM running for 4.2)
```

### 8.3 Test Query Interface

Use curl or Postman to test the query interface:

```bash
curl -X POST http://localhost:8080/jdbc-model/query-model/v2/FactOrderQueryModel \
  -H "Content-Type: application/json" \
  -d '{
    "page": 1,
    "pageSize": 10,
    "param": {
      "columns": ["orderId", "customer$caption", "amount"]
    }
  }'
```

If data is returned, the configuration is successful!

> **Tip**:
> - The default port is 8080, which can be modified in `application.yml`: `server.port: 8081`
> - Query interface path format: `/jdbc-model/query-model/v2/{QueryModelName}`
> - If `foggy.dataset.show-sql: true` is enabled, you can see the generated SQL statements and execution time in the console

---

## 9. Use DSL Query

### 9.1 Basic Query

Send DSL query through HTTP API:

```http
POST http://localhost:8080/jdbc-model/query-model/v2/FactOrderQueryModel
Content-Type: application/json

{
    "page": 1,
    "pageSize": 20,
    "param": {
        "columns": [
            "orderId",
            "orderStatus",
            "customer$caption",
            "product$caption",
            "amount"
        ]
    }
}
```

**Return Result**:

```json
{
    "code": 0,
    "data": {
        "items": [
            {
                "orderId": "ORD20240101001",
                "orderStatus": "COMPLETED",
                "customer$caption": "张三",
                "product$caption": "iPhone 15",
                "amount": 6999.00
            }
        ],
        "total": 100
    }
}
```

### 9.2 Conditional Query

Use `slice` to add filter conditions:

```json
{
    "page": 1,
    "pageSize": 20,
    "param": {
        "columns": ["orderId", "customer$caption", "amount"],
        "slice": [
            { "field": "orderStatus", "op": "=", "value": "COMPLETED" },
            { "field": "amount", "op": ">=", "value": 100 },
            { "field": "customer$province", "op": "=", "value": "广东省" }
        ]
    }
}
```

**Generated SQL**:

```sql
SELECT
    t0.order_id AS orderId,
    t1.customer_name AS "customer$caption",
    t0.amount AS amount
FROM fact_order t0
LEFT JOIN dim_customer t1 ON t0.customer_id = t1.customer_id
WHERE t0.order_status = 'COMPLETED'
  AND t0.amount >= 100
  AND t1.province = '广东省'
```

### 9.3 Group Aggregation

Use `groupBy` for group aggregation:

```json
{
    "page": 1,
    "pageSize": 100,
    "param": {
        "columns": [
            "customer$customerType",
            "product$category",
            "quantity",
            "amount"
        ],
        "groupBy": [
            { "field": "customer$customerType" },
            { "field": "product$category" }
        ],
        "orderBy": [
            { "field": "amount", "order": "desc" }
        ]
    }
}
```

**Return Result**:

```json
{
    "code": 0,
    "data": {
        "items": [
            {
                "customer$customerType": "VIP",
                "product$category": "数码电器",
                "quantity": 150,
                "amount": 89900.00
            },
            {
                "customer$customerType": "普通",
                "product$category": "数码电器",
                "quantity": 80,
                "amount": 45600.00
            }
        ],
        "total": 10
    }
}
```

### 9.4 Range Query

Use range operators for range queries:

```json
{
    "param": {
        "columns": ["orderId", "orderTime", "amount"],
        "slice": [
            {
                "field": "orderTime",
                "op": "[)",
                "value": ["2024-01-01", "2024-07-01"]
            },
            {
                "field": "amount",
                "op": "[]",
                "value": [100, 8000]
            }
        ]
    }
}
```

**Range Operator Description**:

| Operator | Description | SQL |
|--------|------|-----|
| `[]` | Closed interval | `>= AND <=` |
| `[)` | Left closed right open | `>= AND <` |
| `(]` | Left open right closed | `> AND <=` |
| `()` | Open interval | `> AND <` |

### 9.5 IN Query

```json
{
    "param": {
        "columns": ["orderId", "orderStatus", "amount"],
        "slice": [
            {
                "field": "orderStatus",
                "op": "in",
                "value": ["COMPLETED", "SHIPPED", "PAID"]
            }
        ]
    }
}
```

### 9.6 Fuzzy Query

```json
{
    "param": {
        "columns": ["orderId", "customer$caption"],
        "slice": [
            {
                "field": "customer$caption",
                "op": "like",
                "value": "张"
            }
        ]
    }
}
```

---

## 10. Java Call Example

```java
@Service
public class OrderQueryService {

    @Autowired
    private JdbcModelQueryEngine queryEngine;

    public PageResult<Map<String, Object>> queryOrders(QueryParams params) {
        // Build query request
        JdbcQueryRequestDef request = new JdbcQueryRequestDef();
        request.setQueryModel("FactOrderQueryModel");

        // Set query columns
        request.setColumns(Arrays.asList(
            "orderId",
            "orderStatus",
            "customer$caption",
            "product$caption",
            "amount"
        ));

        // Set filter conditions
        List<SliceRequestDef> slices = new ArrayList<>();
        if (params.getStatus() != null) {
            SliceRequestDef slice = new SliceRequestDef();
            slice.setField("orderStatus");
            slice.setOp("=");
            slice.setValue(params.getStatus());
            slices.add(slice);
        }
        request.setSlice(slices);

        // Set pagination
        request.setPage(params.getPage());
        request.setPageSize(params.getPageSize());

        // Execute query
        return queryEngine.query(request);
    }
}
```

---

## 11. Common Operators Quick Reference

| Operator | Description | Example Value |
|--------|------|--------|
| `=` | Equal | `"COMPLETED"` |
| `!=` | Not equal | `"CANCELLED"` |
| `>` | Greater than | `100` |
| `>=` | Greater than or equal | `100` |
| `<` | Less than | `1000` |
| `<=` | Less than or equal | `1000` |
| `in` | Contains | `["A", "B", "C"]` |
| `not in` | Does not contain | `["X", "Y"]` |
| `like` | Fuzzy match | `Automatically pad % on both sides of the string, for example, query '3', will be padded to '%3%'` |
| `left_like` | Fuzzy match | `Automatically pad % on the left side of the string, for example, query '3', will be padded to '%3'` |
| `right_like` | Fuzzy match | `Automatically pad % on the right side of the string, for example, query '3', will be padded to '3%'` |
| `is null` | Is null | No value needed |
| `is not null` | Is not null | No value needed |
| `[]` | Closed interval | `[100, 500]` |
| `[)` | Left closed right open | `["2024-01-01", "2024-07-01"]` |

---

## 12. Next Steps

- [TM Syntax Manual](../tm-qm/tm-syntax.md) - Complete TM definition syntax
- [QM Syntax Manual](../tm-qm/qm-syntax.md) - Complete QM definition syntax
- [DSL Query API](../api/query-api.md) - Complete query API reference
- [Parent-Child Dimension](../tm-qm/parent-child.md) - Hierarchical structure dimension configuration
