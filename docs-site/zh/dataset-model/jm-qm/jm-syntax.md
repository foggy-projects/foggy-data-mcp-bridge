# TM 语法手册

TM（Table Model，表格模型）用于定义数据库表的结构和关联关系。本文档详细介绍 TM 的完整语法。

## 1. 基本结构

TM 文件使用 JavaScript 语法，导出一个 `model` 对象：

```javascript
export const model = {
    name: 'FactOrderModel',      // 模型名称（必填，唯一标识）
    caption: '订单事实表',         // 模型显示名称
    tableName: 'fact_order',     // 对应的数据库表名（必填）
    idColumn: 'order_id',        // 主键列名

    dimensions: [...],           // 维度定义（关联其他表）
    properties: [...],           // 属性定义（本表字段）
    measures: [...]              // 度量定义（可聚合字段）
};
```

### 1.1 基础字段

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `name` | string | 是 | 模型唯一标识，QM 中通过此名称引用 |
| `caption` | string | 否 | 模型显示名称 |
| `tableName` | string | 是 | 对应的数据库表名 |
| `tableSchema` | string | 否 | 数据库 Schema（跨 Schema 访问时使用） |
| `idColumn` | string | 否 | 主键列名 |

---

## 2. 维度定义 (dimensions)

维度用于定义与其他表的关联关系，查询时自动生成 JOIN。

### 2.1 基本维度

```javascript
dimensions: [
    {
        name: 'customer',              // 维度名称（用于查询时引用）
        tableName: 'dim_customer',     // 关联的维度表名
        foreignKey: 'customer_id',     // 本表的外键字段
        primaryKey: 'customer_id',     // 维度表的主键字段
        captionColumn: 'customer_name', // 维度的显示字段
        caption: '客户',                // 维度显示名称

        // 维度属性（维度表中可查询的字段）
        properties: [
            { column: 'customer_id', caption: '客户ID' },
            { column: 'customer_type', caption: '客户类型' },
            { column: 'province', caption: '省份' },
            { column: 'city', caption: '城市' },
            { column: 'member_level', caption: '会员等级' }
        ]
    }
]
```

### 2.2 维度字段说明

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `name` | string | 是 | 维度名称，查询时使用 `维度名$属性名` 格式引用 |
| `tableName` | string | 是 | 关联的维度表名 |
| `tableSchema` | string | 否 | 维度表的 Schema |
| `foreignKey` | string | 是 | 事实表中的外键字段 |
| `primaryKey` | string | 是 | 维度表的主键字段 |
| `captionColumn` | string | 否 | 维度的显示字段，用于 `维度名$caption` |
| `caption` | string | 否 | 维度显示名称 |
| `properties` | array | 否 | 维度表中可查询的属性列表 |
| `type` | string | 否 | 维度类型，如 `DATETIME` 表示时间维度 |

### 2.3 父子维度

父子维度用于处理层级结构数据（如组织架构、商品分类），通过闭包表实现。

```javascript
{
    name: 'team',
    tableName: 'dim_team',
    foreignKey: 'team_id',
    primaryKey: 'team_id',
    captionColumn: 'team_name',
    caption: '团队',

    // 父子维度配置
    closureTableName: 'team_closure',  // 闭包表名（必填）
    parentKey: 'parent_id',            // 闭包表祖先列（必填）
    childKey: 'team_id',               // 闭包表后代列（必填）

    properties: [
        { column: 'team_id', caption: '团队ID' },
        { column: 'team_name', caption: '团队名称' },
        { column: 'parent_id', caption: '上级团队' },
        { column: 'team_level', caption: '层级' }
    ]
}
```

**父子维度专用字段**：

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `closureTableName` | string | 是 | 闭包表名称 |
| `closureTableSchema` | string | 否 | 闭包表 Schema |
| `parentKey` | string | 是 | 闭包表中的祖先列（如 `parent_id`） |
| `childKey` | string | 是 | 闭包表中的后代列（如 `team_id`） |

> 详细说明请参考 [父子维度文档](./parent-child.md)

### 2.4 嵌套维度（雪花模型）

嵌套维度用于实现雪花模型，即维度表之间存在层级关系。

```javascript
{
    // 一级维度：产品（与事实表直接关联）
    name: 'product',
    tableName: 'dim_product',
    foreignKey: 'product_id',       // 事实表上的外键
    primaryKey: 'product_id',
    captionColumn: 'product_name',
    caption: '商品',

    properties: [
        { column: 'brand', caption: '品牌' },
        { column: 'unit_price', caption: '单价', type: 'MONEY' }
    ],

    // 嵌套子维度：品类（与产品维度关联，而非事实表）
    dimensions: [
        {
            name: 'category',
            alias: 'productCategory',   // 别名，简化 QM 访问
            tableName: 'dim_category',
            foreignKey: 'category_id',  // 在父维度表(dim_product)上的外键
            primaryKey: 'category_id',
            captionColumn: 'category_name',
            caption: '品类',

            properties: [
                { column: 'category_id', caption: '品类ID' },
                { column: 'category_level', caption: '品类层级' }
            ],

            // 继续嵌套：品类组
            dimensions: [
                {
                    name: 'group',
                    alias: 'categoryGroup',
                    tableName: 'dim_category_group',
                    foreignKey: 'group_id',
                    primaryKey: 'group_id',
                    captionColumn: 'group_name',
                    caption: '品类组'
                }
            ]
        }
    ]
}
```

**嵌套维度字段**：

| 字段 | 类型 | 说明 |
|------|------|------|
| `alias` | string | 维度别名，用于在 QM 中简化列名访问 |
| `foreignKey` | string | **重要**：嵌套维度的 foreignKey 指向父维度表上的列 |
| `dimensions` | array | 子维度列表，可继续嵌套 |

**QM 中访问嵌套维度**：

```javascript
// 方式1：使用别名（推荐）
columns: [
    'product$caption',           // 一级维度
    'productCategory$caption',   // 二级维度（通过 alias）
    'categoryGroup$caption'      // 三级维度（通过 alias）
]

// 方式2：使用完整路径
columns: [
    'product$caption',
    'product.category$caption',
    'product.category.group$caption'
]
```

**生成的 SQL JOIN**：

```sql
SELECT ...
FROM fact_sales f
LEFT JOIN dim_product p ON f.product_id = p.product_id
LEFT JOIN dim_category c ON p.category_id = c.category_id
LEFT JOIN dim_category_group g ON c.group_id = g.group_id
```

---

## 3. 属性定义 (properties)

属性用于定义事实表自身的字段（非聚合字段）。

```javascript
properties: [
    {
        column: 'order_id',        // 数据库列名（必填）
        name: 'orderId',           // 属性名称（可选，默认为 column 的驼峰形式）
        caption: '订单ID',          // 显示名称
        type: 'STRING'             // 数据类型
    },
    {
        column: 'order_status',
        caption: '订单状态',
        type: 'STRING'
    },
    {
        column: 'created_at',
        caption: '创建时间',
        type: 'DATETIME'
    }
]
```

### 3.1 属性字段说明

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `column` | string | 是 | 数据库列名 |
| `name` | string | 否 | 属性名称，默认为 column 的驼峰形式 |
| `caption` | string | 否 | 显示名称 |
| `type` | string | 否 | 数据类型 |
| `format` | string | 否 | 格式化模板（用于日期等） |

---

## 4. 度量定义 (measures)

度量用于定义可聚合的数值字段。

```javascript
measures: [
    {
        column: 'quantity',         // 数据库列名（必填）
        name: 'salesQuantity',      // 度量名称（可选）
        caption: '销售数量',          // 显示名称
        type: 'INTEGER',            // 数据类型
        aggregation: 'sum'          // 聚合方式（必填）
    },
    {
        column: 'sales_amount',
        caption: '销售金额',
        type: 'MONEY',
        aggregation: 'sum'
    },
    {
        column: 'unit_price',
        caption: '单价',
        type: 'MONEY',
        aggregation: 'avg'          // 平均值聚合
    }
]
```

### 4.1 度量字段说明

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `column` | string | 是 | 数据库列名 |
| `name` | string | 否 | 度量名称，默认为 column 的驼峰形式 |
| `caption` | string | 否 | 显示名称 |
| `type` | string | 否 | 数据类型 |
| `aggregation` | string | 是 | 聚合方式 |

### 4.2 聚合方式

| 值 | 说明 |
|----|------|
| `sum` | 求和 |
| `avg` | 平均值 |
| `count` | 计数 |
| `max` | 最大值 |
| `min` | 最小值 |
| `none` | 不聚合 |

---

## 5. 数据类型

| 类型 | 别名 | 说明 | Java 类型 |
|------|------|------|-----------|
| `STRING` | `TEXT` | 字符串 | String |
| `INTEGER` | - | 整数 | Integer |
| `BIGINT` | `Long` | 长整数 | Long |
| `MONEY` | `NUMBER`, `BigDecimal` | 金额/精确小数 | BigDecimal |
| `DATETIME` | - | 日期时间 | Date |
| `DAY` | - | 日期（yyyy-MM-dd） | Date |
| `BOOL` | `Boolean` | 布尔值 | Boolean |
| `DICT` | - | 字典值 | Integer |

---

## 6. 完整示例

### 6.1 事实表模型

```javascript
// FactSalesModel.tm
export const model = {
    name: 'FactSalesModel',
    caption: '销售事实表',
    tableName: 'fact_sales',
    idColumn: 'sales_key',

    dimensions: [
        {
            name: 'salesDate',
            tableName: 'dim_date',
            foreignKey: 'date_key',
            primaryKey: 'date_key',
            captionColumn: 'full_date',
            caption: '销售日期',
            type: 'DATETIME',
            properties: [
                { column: 'year', caption: '年' },
                { column: 'quarter', caption: '季度' },
                { column: 'month', caption: '月' },
                { column: 'week', caption: '周' },
                { column: 'day_of_week', caption: '星期' }
            ]
        },
        {
            name: 'customer',
            tableName: 'dim_customer',
            foreignKey: 'customer_key',
            primaryKey: 'customer_key',
            captionColumn: 'customer_name',
            caption: '客户',
            properties: [
                { column: 'customer_id', caption: '客户ID' },
                { column: 'customer_type', caption: '客户类型' },
                { column: 'province', caption: '省份' },
                { column: 'city', caption: '城市' },
                { column: 'member_level', caption: '会员等级' }
            ]
        },
        {
            name: 'product',
            tableName: 'dim_product',
            foreignKey: 'product_key',
            primaryKey: 'product_key',
            captionColumn: 'product_name',
            caption: '商品',
            properties: [
                { column: 'product_id', caption: '商品ID' },
                { column: 'product_name', caption: '商品名称' },
                { column: 'category', caption: '品类' },
                { column: 'brand', caption: '品牌' },
                { column: 'unit_price', caption: '单价', type: 'MONEY' }
            ]
        }
    ],

    properties: [
        { column: 'sales_key', caption: '销售键', type: 'BIGINT' },
        { column: 'order_id', caption: '订单ID', type: 'STRING' },
        { column: 'order_status', caption: '订单状态', type: 'STRING' },
        { column: 'create_time', caption: '创建时间', type: 'DATETIME' }
    ],

    measures: [
        {
            column: 'quantity',
            name: 'salesQuantity',
            caption: '销售数量',
            type: 'INTEGER',
            aggregation: 'sum'
        },
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
            column: 'profit_amount',
            name: 'profitAmount',
            caption: '利润金额',
            type: 'MONEY',
            aggregation: 'sum'
        }
    ]
};
```

### 6.2 维度表模型

```javascript
// DimCustomerModel.tm
export const model = {
    name: 'DimCustomerModel',
    caption: '客户维度',
    tableName: 'dim_customer',
    idColumn: 'customer_key',

    dimensions: [],  // 维度表通常不关联其他维度

    properties: [
        { column: 'customer_key', caption: '客户代理键', type: 'INTEGER' },
        { column: 'customer_id', caption: '客户业务ID', type: 'STRING' },
        { column: 'customer_name', caption: '客户名称', type: 'STRING' },
        { column: 'customer_type', caption: '客户类型', type: 'STRING' },
        { column: 'province', caption: '省份', type: 'STRING' },
        { column: 'city', caption: '城市', type: 'STRING' },
        { column: 'member_level', caption: '会员等级', type: 'STRING' },
        { column: 'register_date', caption: '注册日期', type: 'DAY' }
    ],

    measures: []  // 维度表通常没有度量
};
```

---

## 7. 命名约定

### 7.1 文件命名

- TM 文件：`{模型名}Model.tm`
- 事实表：`Fact{业务名}Model.tm`，如 `FactOrderModel.tm`
- 维度表：`Dim{业务名}Model.tm`，如 `DimCustomerModel.tm`

### 7.2 字段命名

| 位置 | 规范 | 示例 |
|------|------|------|
| 模型 `name` | 驼峰命名 | `orderId`, `customerType` |
| 数据库 `column` | 蛇形命名 | `order_id`, `customer_type` |
| 维度属性引用 | `$` 分隔 | `customer$caption`, `orderDate$year` |

---

## 下一步

- [QM 语法手册](./qm-syntax.md) - 查询模型定义
- [DSL 查询 API](../api/query-api.md) - 使用 DSL 查询数据
- [父子维度](./parent-child.md) - 层级结构维度
