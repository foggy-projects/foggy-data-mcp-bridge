# 快速开始

本指南帮助你在 10 分钟内创建 TM/QM 模型并使用 DSL 进行查询。

## 1. 添加依赖

在 `pom.xml` 中添加依赖：

```xml
<dependency>
    <groupId>com.foggysource</groupId>
    <artifactId>foggy-dataset-model</artifactId>
    <version>8.0.1-beta</version>
</dependency>
```

---

## 2. 场景说明

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
```

---

## 3. 创建 TM 模型

### 3.1 创建事实表模型

创建文件 `FactOrderModel.tm`：

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
            name: 'totalQuantity',
            caption: '订单数量',
            type: 'INTEGER',
            aggregation: 'sum'
        },
        {
            column: 'amount',
            name: 'totalAmount',
            caption: '订单金额',
            type: 'MONEY',
            aggregation: 'sum'
        }
    ]
};
```

### 3.2 TM 模型要点

| 配置项 | 说明 |
|--------|------|
| `name` | 模型唯一标识，QM 中通过此名称引用 |
| `tableName` | 对应的数据库表名 |
| `dimensions` | 维度定义，查询时自动生成 JOIN |
| `properties` | 属性定义，不参与聚合 |
| `measures` | 度量定义，可聚合的数值字段 |

---

## 4. 创建 QM 模型

创建文件 `FactOrderQueryModel.qm`：

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
                { name: 'totalQuantity' },
                { name: 'totalAmount' }
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

### 4.1 QM 字段引用格式

| 格式 | 说明 | 示例 |
|------|------|------|
| `属性名` | 事实表属性 | `orderId`, `orderStatus` |
| `度量名` | 度量字段 | `totalAmount`, `totalQuantity` |
| `维度名$caption` | 维度显示值 | `customer$caption` |
| `维度名$属性名` | 维度其他属性 | `customer$customerType` |

---

## 5. 使用 DSL 查询

### 5.1 基本查询

通过 HTTP API 发送 DSL 查询：

```http
POST /jdbc-model/query-model/v2/FactOrderQueryModel
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
            "totalAmount"
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
                "totalAmount": 6999.00
            }
        ],
        "total": 100
    }
}
```

### 5.2 条件查询

使用 `slice` 添加过滤条件：

```json
{
    "page": 1,
    "pageSize": 20,
    "param": {
        "columns": ["orderId", "customer$caption", "totalAmount"],
        "slice": [
            { "name": "orderStatus", "type": "=", "value": "COMPLETED" },
            { "name": "totalAmount", "type": ">=", "value": 100 },
            { "name": "customer$province", "type": "=", "value": "广东省" }
        ]
    }
}
```

**生成的 SQL**：

```sql
SELECT
    t0.order_id AS orderId,
    t1.customer_name AS "customer$caption",
    t0.amount AS totalAmount
FROM fact_order t0
LEFT JOIN dim_customer t1 ON t0.customer_id = t1.customer_id
WHERE t0.order_status = 'COMPLETED'
  AND t0.amount >= 100
  AND t1.province = '广东省'
```

### 5.3 分组汇总

使用 `groupBy` 进行分组聚合：

```json
{
    "page": 1,
    "pageSize": 100,
    "param": {
        "columns": [
            "customer$customerType",
            "product$category",
            "totalQuantity",
            "totalAmount"
        ],
        "groupBy": [
            { "name": "customer$customerType" },
            { "name": "product$category" }
        ],
        "orderBy": [
            { "name": "totalAmount", "order": "desc" }
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
                "totalQuantity": 150,
                "totalAmount": 89900.00
            },
            {
                "customer$customerType": "普通",
                "product$category": "数码电器",
                "totalQuantity": 80,
                "totalAmount": 45600.00
            }
        ],
        "total": 10
    }
}
```

### 5.4 范围查询

使用区间操作符进行范围查询：

```json
{
    "param": {
        "columns": ["orderId", "orderTime", "totalAmount"],
        "slice": [
            {
                "name": "orderTime",
                "type": "[)",
                "value": ["2024-01-01", "2024-07-01"]
            },
            {
                "name": "totalAmount",
                "type": "[]",
                "value": [100, 1000]
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

### 5.5 IN 查询

```json
{
    "param": {
        "columns": ["orderId", "orderStatus", "totalAmount"],
        "slice": [
            {
                "name": "orderStatus",
                "type": "in",
                "value": ["COMPLETED", "SHIPPED", "PAID"]
            }
        ]
    }
}
```

### 5.6 模糊查询

```json
{
    "param": {
        "columns": ["orderId", "customer$caption"],
        "slice": [
            {
                "name": "customer$caption",
                "type": "like",
                "value": "%张%"
            }
        ]
    }
}
```

---

## 6. Java 调用示例

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
            "totalAmount"
        ));

        // 设置过滤条件
        List<SliceRequestDef> slices = new ArrayList<>();
        if (params.getStatus() != null) {
            SliceRequestDef slice = new SliceRequestDef();
            slice.setName("orderStatus");
            slice.setType("=");
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

## 7. 常用操作符速查

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
| `like` | 模糊匹配 | `"%关键字%"` |
| `is null` | 为空 | 无需 value |
| `is not null` | 不为空 | 无需 value |
| `[]` | 闭区间 | `[100, 500]` |
| `[)` | 左闭右开 | `["2024-01-01", "2024-07-01"]` |

---

## 下一步

- [TM 语法手册](../jm-qm/jm-syntax.md) - 完整的 TM 定义语法
- [QM 语法手册](../jm-qm/qm-syntax.md) - 完整的 QM 定义语法
- [DSL 查询 API](../api/query-api.md) - 完整的查询 API 参考
- [父子维度](../jm-qm/parent-child.md) - 层级结构维度配置
