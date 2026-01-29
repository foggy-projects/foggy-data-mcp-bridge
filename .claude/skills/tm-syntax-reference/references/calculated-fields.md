# 计算属性和度量

使用 `formulaDef` 定义计算字段，支持 JSON 提取、字符串拼接、数学运算等。

## 使用场景

- **JSON 字段提取**：从 JSON 列中提取特定字段
- **字符串拼接**：组合多个字段为一个显示值
- **数学运算**：计算利润率、折扣等
- **条件逻辑**：基于其他字段的条件表达式

## formulaDef 字段说明

| 字段 | 类型 | 说明 |
|------|------|------|
| `builder` | function | SQL 构建函数，参数 `alias` 为表别名 |
| `value` | string | 公式表达式（基于度量名称） |
| `description` | string | 公式的文字描述 |

> `builder` 和 `value` 二选一，`builder` 更灵活，可直接操作 SQL

## 计算属性（properties）

### 1. JSON 字段提取

从 JSON 列中提取嵌套字段：

```javascript
properties: [
    {
        column: 'send_addr_info',  // JSON 类型字段
        name: 'sendStreet',
        caption: '收货街道',
        description: '从地址 JSON 中提取街道信息',
        type: 'STRING',
        formulaDef: {
            builder: (alias) => {
                return `${alias}.send_addr_info ->> '$.send_street'`;
            },
            description: '提取收货地址中的街道字段'
        }
    },
    {
        column: 'send_addr_info',
        name: 'sendCity',
        caption: '收货城市',
        type: 'STRING',
        formulaDef: {
            builder: (alias) => {
                return `${alias}.send_addr_info ->> '$.city'`;
            },
            description: '提取收货地址中的城市字段'
        }
    }
]
```

**MySQL JSON 操作符**：
- `->>` : 提取 JSON 值并返回字符串
- `->` : 提取 JSON 值保持 JSON 类型
- `JSON_EXTRACT()` : 函数形式提取

### 2. 字符串拼接

组合多个字段：

```javascript
properties: [
    {
        column: 'customer_name',
        name: 'fullName',
        caption: '客户全名',
        type: 'STRING',
        formulaDef: {
            builder: (alias) => {
                return `CONCAT(${alias}.first_name, ' ', ${alias}.last_name)`;
            },
            description: '拼接姓和名'
        }
    },
    {
        column: 'address',
        name: 'fullAddress',
        caption: '完整地址',
        type: 'STRING',
        formulaDef: {
            builder: (alias) => {
                return `CONCAT(${alias}.province, ${alias}.city, ${alias}.district, ${alias}.street)`;
            },
            description: '拼接省市区街道'
        }
    }
]
```

### 3. 条件逻辑

基于条件的计算：

```javascript
properties: [
    {
        column: 'order_status',
        name: 'statusGroup',
        caption: '状态分组',
        type: 'STRING',
        formulaDef: {
            builder: (alias) => {
                return `CASE
                    WHEN ${alias}.order_status IN ('PENDING', 'CONFIRMED') THEN '进行中'
                    WHEN ${alias}.order_status IN ('SHIPPED', 'COMPLETED') THEN '已完成'
                    ELSE '已取消'
                END`;
            },
            description: '订单状态分组'
        }
    }
]
```

### 4. 日期转换

```javascript
properties: [
    {
        column: 'created_at',
        name: 'createdMonth',
        caption: '创建月份',
        type: 'STRING',
        formulaDef: {
            builder: (alias) => {
                return `DATE_FORMAT(${alias}.created_at, '%Y-%m')`;
            },
            description: '提取创建时间的年月'
        }
    }
]
```

## 计算度量（measures）

### 1. 基于现有列的计算

```javascript
measures: [
    {
        column: 'sales_amount',
        name: 'salesAmountWithTax',
        caption: '含税销售额',
        type: 'MONEY',
        aggregation: 'sum',
        formulaDef: {
            builder: (alias) => {
                return `${alias}.sales_amount * 1.13`;
            },
            description: '销售额乘以 1.13（含13%增值税）'
        }
    }
]
```

### 2. 多字段运算

```javascript
measures: [
    {
        column: 'sales_amount',
        name: 'profitAmount',
        caption: '利润金额',
        type: 'MONEY',
        aggregation: 'sum',
        formulaDef: {
            builder: (alias) => {
                return `${alias}.sales_amount - ${alias}.cost_amount`;
            },
            description: '销售额减去成本'
        }
    }
]
```

### 3. 百分比计算

```javascript
measures: [
    {
        column: 'sales_amount',
        name: 'profitRate',
        caption: '利润率',
        type: 'NUMBER',
        aggregation: 'avg',
        formulaDef: {
            builder: (alias) => {
                return `CASE
                    WHEN ${alias}.sales_amount = 0 THEN 0
                    ELSE (${alias}.sales_amount - ${alias}.cost_amount) / ${alias}.sales_amount * 100
                END`;
            },
            description: '利润金额除以销售额乘以100'
        }
    }
]
```

### 4. 基于度量名称的公式（value）

使用 `value` 字段引用其他度量：

```javascript
measures: [
    {
        column: 'sales_amount',
        name: 'salesAmount',
        caption: '销售金额',
        type: 'MONEY',
        aggregation: 'sum'
    },
    {
        column: 'cost_amount',
        name: 'costAmount',
        caption: '成本金额',
        type: 'MONEY',
        aggregation: 'sum'
    },
    {
        name: 'profitAmount',
        caption: '利润金额',
        type: 'MONEY',
        aggregation: 'sum',
        formulaDef: {
            value: 'salesAmount - costAmount',  // ✅ 引用度量名称
            description: '销售额减去成本'
        }
    }
]
```

> **注意**：`value` 引用的是度量的 `name`，不是 `column`

## 聚合后的计算度量

计算度量会先聚合，再进行计算：

```javascript
measures: [
    {
        column: 'quantity',
        name: 'totalQuantity',
        caption: '总数量',
        type: 'INTEGER',
        aggregation: 'sum'
    },
    {
        column: 'sales_amount',
        name: 'totalSalesAmount',
        caption: '总销售额',
        type: 'MONEY',
        aggregation: 'sum'
    },
    {
        name: 'avgUnitPrice',
        caption: '平均单价',
        type: 'MONEY',
        aggregation: 'none',  // ⚠️ 计算度量通常使用 none
        formulaDef: {
            value: 'totalSalesAmount / totalQuantity',
            description: '总销售额除以总数量'
        }
    }
]
```

**生成的 SQL**：

```sql
SELECT
    SUM(sales_amount) as total_sales_amount,
    SUM(quantity) as total_quantity,
    SUM(sales_amount) / SUM(quantity) as avg_unit_price
FROM fact_sales
GROUP BY ...
```

## MySQL 方言差异

不同数据库的 JSON 提取语法：

### MySQL 5.7+

```javascript
formulaDef: {
    builder: (alias) => {
        return `${alias}.data ->> '$.field'`;  // ->> 返回字符串
    }
}
```

### PostgreSQL

```javascript
formulaDef: {
    builder: (alias) => {
        return `${alias}.data ->> 'field'`;  // PG 不需要 $.前缀
    }
}
```

### SQL Server

```javascript
formulaDef: {
    builder: (alias) => {
        return `JSON_VALUE(${alias}.data, '$.field')`;
    }
}
```

## 最佳实践

1. **避免过度计算**：
   - 简单的计算可以在应用层完成
   - 复杂的逻辑考虑使用数据库视图

2. **使用 description**：
   - 必须提供清晰的文字描述
   - 方便 AI 理解字段含义

3. **处理 NULL 值**：
   ```javascript
   formulaDef: {
       builder: (alias) => {
           return `COALESCE(${alias}.discount, 0)`;
       }
   }
   ```

4. **除法运算防止除零**：
   ```javascript
   formulaDef: {
       builder: (alias) => {
           return `CASE
               WHEN ${alias}.quantity = 0 THEN 0
               ELSE ${alias}.sales_amount / ${alias}.quantity
           END`;
       }
   }
   ```

5. **性能优化**：
   - 计算字段不能使用索引
   - 频繁查询的计算字段考虑物化（添加实际列）
   - JSON 提取性能较差，考虑提前展开

## 示例：电商订单模型

```javascript
export const model = {
    name: 'FactOrderModel',
    caption: '订单事实表',
    tableName: 'fact_order',

    properties: [
        // 基础字段
        { column: 'order_id', caption: '订单ID', type: 'STRING' },
        { column: 'customer_info', caption: '客户信息', type: 'STRING' },  // JSON

        // 计算字段：从 JSON 提取
        {
            column: 'customer_info',
            name: 'customerName',
            caption: '客户姓名',
            type: 'STRING',
            formulaDef: {
                builder: (alias) => {
                    return `${alias}.customer_info ->> '$.name'`;
                },
                description: '从客户信息 JSON 中提取姓名'
            }
        },
        {
            column: 'customer_info',
            name: 'customerPhone',
            caption: '客户手机',
            type: 'STRING',
            formulaDef: {
                builder: (alias) => {
                    return `${alias}.customer_info ->> '$.phone'`;
                },
                description: '从客户信息 JSON 中提取手机号'
            }
        }
    ],

    measures: [
        { column: 'order_amount', name: 'orderAmount', caption: '订单金额', type: 'MONEY', aggregation: 'sum' },
        { column: 'discount_amount', name: 'discountAmount', caption: '折扣金额', type: 'MONEY', aggregation: 'sum' },

        // 计算度量：实付金额
        {
            name: 'actualAmount',
            caption: '实付金额',
            type: 'MONEY',
            aggregation: 'sum',
            formulaDef: {
                value: 'orderAmount - discountAmount',
                description: '订单金额减去折扣金额'
            }
        }
    ]
};
```
