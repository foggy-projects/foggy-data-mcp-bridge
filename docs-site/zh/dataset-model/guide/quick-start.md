# 快速开始

本指南帮助你在 10 分钟内创建 TM/QM 模型并使用 DSL 进行查询。

## 1. 环境准备

如果您不是java 开发者，建议使用docker的方式进行体验，详见（TODO）

如果您是java 开发者，建议使用idea等开发工具，通过maven引入foggy-dataset-model进行体验，
- 任意IDE
- JDK 17+
- Maven
- mysql、PostgreSQL或sqlite等关系数据库

如果您不想手动操作如下步骤，您可以将我们准备的文档（TODO）交付给目前的AI编程工具，如trae、claude code等，让其帮您构建基础示例，或引入依赖等

## 2. 添加依赖

### 2.1 核心依赖（必需）

在 `pom.xml` 中添加 Foggy Dataset Model 依赖：

```xml
<dependency>
    <groupId>com.foggysource</groupId>
    <artifactId>foggy-dataset-model</artifactId>
    <version>8.0.1-beta</version>
</dependency>
```

### 2.2 新建项目完整配置

如果你正在创建一个新项目，需要添加以下依赖：

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
        <version>8.0.1-beta</version>
    </dependency>

    <!-- 数据库驱动（根据实际情况选择一个） -->
    <!-- MySQL -->
    <dependency>
        <groupId>com.mysql</groupId>
        <artifactId>mysql-connector-j</artifactId>
        <scope>runtime</scope>
    </dependency>

    <!-- 或 PostgreSQL -->
    <!--
    <dependency>
        <groupId>org.postgresql</groupId>
        <artifactId>postgresql</artifactId>
        <scope>runtime</scope>
    </dependency>
    -->

    <!-- 或 SQLite（适合快速测试） -->
    <!--
    <dependency>
        <groupId>org.xerial</groupId>
        <artifactId>sqlite-jdbc</artifactId>
        <version>3.44.1.0</version>
    </dependency>
    -->
</dependencies>
```

### 2.3 已有项目引入

如果你的项目已经有Spring Boot和数据源配置，只需：

1. 添加 `foggy-dataset-model` 核心依赖（见 2.1）
2. 确保项目中有 JDBC 相关依赖（`spring-boot-starter-jdbc` 或 `spring-boot-starter-data-jpa`）
3. 确保已配置数据源

> **提示**：Foggy Dataset Model 可以与现有的 MyBatis、JPA 等持久层框架共存。
## 3. 配置主应用类

在Spring Boot主应用类上添加 `@EnableFoggyFramework` 注解：

```java
@SpringBootApplication
@EnableFoggyFramework(bundleName = "my-foggy-demo")
public class MyApplication {

    public static void main(String[] args) {
        SpringApplication.run(MyApplication.class, args);
    }
}
```

> **说明**：`bundleName` 是模块的唯一标识，用于区分不同的业务模块。

---

## 4. 配置数据源和项目结构

### 4.1 配置数据源

**新建项目**：创建 `src/main/resources/application.yml`：

```yaml
spring:
  datasource:
    # MySQL 示例
    url: jdbc:mysql://localhost:3306/your_database?useUnicode=true&characterEncoding=utf8
    username: root
    password: your_password
    driver-class-name: com.mysql.cj.jdbc.Driver

    # 或 PostgreSQL 示例
    # url: jdbc:postgresql://localhost:5432/your_database
    # username: postgres
    # password: your_password
    # driver-class-name: org.postgresql.Driver

    # 或 SQLite 示例（快速测试）
    # url: jdbc:sqlite:./data/demo.db
    # driver-class-name: org.sqlite.JDBC

# Foggy 配置（可选）
foggy:
  dataset:
    # SQL 日志配置
    show-sql: true               # 打印 SQL 语句到控制台（开发调试用）
    sql-format: false            # 是否格式化 SQL（true=多行，false=单行）
    sql-log-level: DEBUG         # SQL 日志级别（DEBUG/INFO）
    show-sql-parameters: true    # 显示 SQL 参数值
    show-execution-time: true    # 显示 SQL 执行时间
```

**已有项目**：如果你的项目已经配置了数据源，无需修改，Foggy 会自动使用现有数据源。

### 4.2 创建模型文件目录

创建 TM/QM 模型文件的存放目录：

```
src/main/resources/
└── foggy/
    └── templates/     # TM 和 QM 文件存放位置
```

**已有项目**：只需在 `src/main/resources` 下创建 `foggy/templates/` 目录即可。

---

## 5. 场景说明

假设我们有一个简单的电商系统，包含：
- 订单事实表 `fact_order`
- 客户维度表 `dim_customer`
- 商品维度表 `dim_product`

```sql
-- 客户维度表
CREATE TABLE dim_customer (
    customer_id VARCHAR(64) PRIMARY KEY,
    customer_name VARCHAR(100),
    customer_type VARCHAR(20),
    province VARCHAR(50),
    city VARCHAR(50)
);

-- 商品维度表
CREATE TABLE dim_product (
    product_id VARCHAR(64) PRIMARY KEY,
    product_name VARCHAR(100),
    category VARCHAR(50),
    unit_price DECIMAL(10,2)
);

-- 订单事实表
CREATE TABLE fact_order (
    order_id VARCHAR(64) PRIMARY KEY,
    customer_id VARCHAR(64),
    product_id VARCHAR(64),
    order_status VARCHAR(20),
    quantity INT,
    amount DECIMAL(10,2),
    order_time DATETIME
);

-- 插入客户数据
INSERT INTO dim_customer (customer_id, customer_name, customer_type, province, city) VALUES
('CUST001', '张三', 'VIP', '广东省', '深圳市'),
('CUST002', '李四', '普通', '广东省', '广州市'),
('CUST003', '王五', 'VIP', '北京市', '北京市'),
('CUST004', '赵六', '普通', '上海市', '上海市'),
('CUST005', '钱七', 'VIP', '浙江省', '杭州市');

-- 插入商品数据
INSERT INTO dim_product (product_id, product_name, category, unit_price) VALUES
('PROD001', 'iPhone 15', '数码电器', 6999.00),
('PROD002', 'MacBook Pro', '数码电器', 12999.00),
('PROD003', '小米手机', '数码电器', 2999.00),
('PROD004', '耐克运动鞋', '服装鞋帽', 599.00),
('PROD005', '阿迪达斯外套', '服装鞋帽', 899.00);

-- 插入订单数据
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

### 5.1 SQL 执行方式

以上 SQL 可以通过以下任一方式执行：

**方式1：自动执行（推荐新建项目）**

创建 SQL 文件并配置自动执行：

1. 创建文件：
   - `src/main/resources/db/schema.sql` - 存放表结构
   - `src/main/resources/db/data.sql` - 存放测试数据

2. 在 `application.yml` 中配置：

```yaml
spring:
  sql:
    init:
      mode: always  # 或 embedded（仅嵌入式数据库）
      schema-locations: classpath:db/schema.sql
      data-locations: classpath:db/data.sql
      encoding: UTF-8
```

**方式2：手动执行（推荐已有项目）**

使用数据库客户端工具直接执行上述 SQL：
- MySQL Workbench、Navicat、DBeaver 等
- 或使用命令行客户端：`mysql -u root -p < schema.sql`

**方式3：使用数据库迁移工具**

如果项目使用 Flyway 或 Liquibase，可以将 SQL 添加到相应的迁移脚本中。

> **注意**：生产环境建议使用方式2或方式3，避免数据被重复初始化。

---

## 6. 创建 TM 模型

### 6.1 创建事实表模型

创建文件 `src/main/resources/foggy/templates/FactOrderModel.tm`：

```javascript
// FactOrderModel.tm - 订单事实表模型

export const model = {
    name: 'FactOrderModel',
    caption: '订单事实表',
    tableName: 'fact_order',
    idColumn: 'order_id',

    // 维度定义：关联客户和商品
    dimensions: [
        {
            name: 'customer',
            caption: '客户',
            tableName: 'dim_customer',
            foreignKey: 'customer_id',
            primaryKey: 'customer_id',
            captionColumn: 'customer_name',
            properties: [
                { column: 'customer_id', caption: '客户ID' },
                { column: 'customer_name', caption: '客户名称' },
                { column: 'customer_type', caption: '客户类型' },
                { column: 'province', caption: '省份' },
                { column: 'city', caption: '城市' }
            ]
        },
        {
            name: 'product',
            caption: '商品',
            tableName: 'dim_product',
            foreignKey: 'product_id',
            primaryKey: 'product_id',
            captionColumn: 'product_name',
            properties: [
                { column: 'product_id', caption: '商品ID' },
                { column: 'product_name', caption: '商品名称' },
                { column: 'category', caption: '品类' },
                { column: 'unit_price', caption: '单价', type: 'MONEY' }
            ]
        }
    ],

    // 属性定义：事实表自身字段
    properties: [
        { column: 'order_id', caption: '订单ID', type: 'STRING' },
        { column: 'order_status', caption: '订单状态', type: 'STRING' },
        { column: 'order_time', caption: '下单时间', type: 'DATETIME' }
    ],

    // 度量定义：可聚合的数值
    measures: [
        {
            column: 'quantity',
            caption: '订单数量',
            type: 'INTEGER',
            aggregation: 'sum'
        },
        {
            column: 'amount',
            caption: '订单金额',
            type: 'MONEY',
            aggregation: 'sum'
        }
    ]
};
```

### 6.2 TM 模型要点

| 配置项 | 说明 |
|--------|------|
| `name` | 模型唯一标识，QM 中通过此名称引用 |
| `tableName` | 对应的数据库表名 |
| `dimensions` | 维度定义，查询时自动生成 JOIN |
| `properties` | 属性定义，不参与聚合 |
| `measures` | 度量定义，可聚合的数值字段 |

---

## 7. 创建 QM 模型

创建文件 `src/main/resources/foggy/templates/FactOrderQueryModel.qm`：

```javascript
// FactOrderQueryModel.qm - 订单查询模型

export const queryModel = {
    name: 'FactOrderQueryModel',
    caption: '订单查询',
    model: 'FactOrderModel',   // 关联的 TM 模型

    // 列组定义：组织可查询的字段
    columnGroups: [
        {
            caption: '订单信息',
            items: [
                { name: 'orderId', ui: { fixed: 'left', width: 150 } },
                { name: 'orderStatus' },
                { name: 'orderTime' }
            ]
        },
        {
            caption: '客户信息',
            items: [
                { name: 'customer$caption' },       // 客户名称
                { name: 'customer$customerType' },  // 客户类型
                { name: 'customer$province' }       // 省份
            ]
        },
        {
            caption: '商品信息',
            items: [
                { name: 'product$caption' },        // 商品名称
                { name: 'product$category' },       // 品类
                { name: 'product$unitPrice' }       // 单价
            ]
        },
        {
            caption: '度量',
            items: [
                { name: 'quantity' },
                { name: 'amount' }
            ]
        }
    ],

    // 默认排序
    orders: [
        { name: 'orderTime', order: 'desc' }
    ],

    // 权限控制（可选）
    accesses: []
};
```

### 7.1 QM 字段引用格式

| 格式 | 说明 | 示例 |
|------|------|------|
| `属性名` | 事实表属性 | `orderId`, `orderStatus` |
| `度量名` | 度量字段 | `amount`, `quantity` |
| `维度名$caption` | 维度显示值 | `customer$caption` |
| `维度名$属性名` | 维度其他属性 | `customer$customerType` |

---

## 8. 启动和测试

### 8.1 启动项目

使用 Maven 启动 Spring Boot 项目：

```bash
# 在项目根目录执行
mvn spring-boot:run
```

或者在 IDE 中直接运行主应用类（带有 `@SpringBootApplication` 注解的类）。

### 8.2 验证启动成功

启动成功后，可以看到类似以下日志：

```
Started MyApplication in 3.5 seconds (JVM running for 4.2)
```

### 8.3 测试查询接口

使用 curl 或 Postman 测试查询接口：

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

如果返回数据，说明配置成功！

> **提示**：
> - 默认端口是 8080，可在 `application.yml` 中修改：`server.port: 8081`
> - 查询接口路径格式：`/jdbc-model/query-model/v2/{QueryModelName}`
> - 如果启用了 `foggy.dataset.show-sql: true`，可以在控制台看到生成的 SQL 语句和执行时间

---

## 9. 使用 DSL 查询

### 9.1 基本查询

通过 HTTP API 发送 DSL 查询：

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

**返回结果**：

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

### 9.2 条件查询

使用 `slice` 添加过滤条件：

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

**生成的 SQL**：

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

### 9.3 分组汇总

使用 `groupBy` 进行分组聚合：

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

**返回结果**：

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

### 9.4 范围查询

使用区间操作符进行范围查询：

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

**区间操作符说明**：

| 操作符 | 说明 | SQL |
|--------|------|-----|
| `[]` | 闭区间 | `>= AND <=` |
| `[)` | 左闭右开 | `>= AND <` |
| `(]` | 左开右闭 | `> AND <=` |
| `()` | 开区间 | `> AND <` |

### 9.5 IN 查询

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

### 9.6 模糊查询

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

## 10. Java 调用示例

```java
@Service
public class OrderQueryService {

    @Autowired
    private JdbcModelQueryEngine queryEngine;

    public PageResult<Map<String, Object>> queryOrders(QueryParams params) {
        // 构建查询请求
        JdbcQueryRequestDef request = new JdbcQueryRequestDef();
        request.setQueryModel("FactOrderQueryModel");

        // 设置查询列
        request.setColumns(Arrays.asList(
            "orderId",
            "orderStatus",
            "customer$caption",
            "product$caption",
            "amount"
        ));

        // 设置过滤条件
        List<SliceRequestDef> slices = new ArrayList<>();
        if (params.getStatus() != null) {
            SliceRequestDef slice = new SliceRequestDef();
            slice.setField("orderStatus");
            slice.setOp("=");
            slice.setValue(params.getStatus());
            slices.add(slice);
        }
        request.setSlice(slices);

        // 设置分页
        request.setPage(params.getPage());
        request.setPageSize(params.getPageSize());

        // 执行查询
        return queryEngine.query(request);
    }
}
```

---

## 11. 常用操作符速查

| 操作符 | 说明 | 示例值 |
|--------|------|--------|
| `=` | 等于 | `"COMPLETED"` |
| `!=` | 不等于 | `"CANCELLED"` |
| `>` | 大于 | `100` |
| `>=` | 大于等于 | `100` |
| `<` | 小于 | `1000` |
| `<=` | 小于等于 | `1000` |
| `in` | 包含 | `["A", "B", "C"]` |
| `not in` | 不包含 | `["X", "Y"]` |
| `like` | 模糊匹配 | `字符串左右自动补%，例如查'3',会补成 '%3%'` |
| `left_like` | 模糊匹配 | `字符串左侧自动补%，例如查'3',会补成 '%3'` |
| `right_like` | 模糊匹配 | `字符串右侧自动补%，例如查'3',会补成 '3%'` |
| `is null` | 为空 | 无需 value |
| `is not null` | 不为空 | 无需 value |
| `[]` | 闭区间 | `[100, 500]` |
| `[)` | 左闭右开 | `["2024-01-01", "2024-07-01"]` |

---

## 12. 下一步

- [TM 语法手册](../tm-qm/tm-syntax.md) - 完整的 TM 定义语法
- [QM 语法手册](../tm-qm/qm-syntax.md) - 完整的 QM 定义语法
- [DSL 查询 API](../api/query-api.md) - 完整的查询 API 参考
- [父子维度](../tm-qm/parent-child.md) - 层级结构维度配置
