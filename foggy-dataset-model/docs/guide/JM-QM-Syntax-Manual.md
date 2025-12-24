# TM/QM 语法手册

Foggy Dataset Model 使用 **TM (Jdbc Model)** 和 **QM (Query Model)** 两种模型文件来定义数据模型和查询模型。

## 概述

| 文件类型 | 扩展名 | 用途 |
|---------|-------|------|
| TM | `.tm` | 定义数据模型（表结构、维度、度量） |
| QM | `.qm` | 定义查询模型（基于TM，定义可查询的字段和UI配置） |

## 一、TM 模型定义

TM 文件使用 JavaScript 语法导出一个 `model` 对象。

### 1.1 基本结构

```javascript
export const model = {
    name: 'FactSalesModel',      // 模型名称（必填，唯一标识）
    caption: '销售事实表',         // 模型显示名称
    tableName: 'fact_sales',     // 对应的数据库表名（必填）
    idColumn: 'sales_key',       // 主键列名

    dimensions: [...],           // 维度定义（关联其他表）
    properties: [...],           // 属性定义（本表字段）
    measures: [...]              // 度量定义（可聚合字段）
};
```

### 1.2 维度定义 (dimensions)

维度用于定义与其他表的关联关系，支持星型模型的自动 JOIN。

```javascript
dimensions: [
    {
        name: 'customer',              // 维度名称（用于查询时引用）
        tableName: 'dim_customer',     // 关联的维度表名
        foreignKey: 'customer_key',    // 本表的外键字段
        primaryKey: 'customer_key',    // 维度表的主键字段
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

#### 维度属性字段

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `name` | string | 是 | 维度名称，查询时使用 `维度名$属性名` 格式引用 |
| `tableName` | string | 是 | 关联的维度表名 |
| `foreignKey` | string | 是 | 事实表中的外键字段 |
| `primaryKey` | string | 是 | 维度表的主键字段 |
| `captionColumn` | string | 否 | 维度的显示字段，用于 `维度名$caption` |
| `caption` | string | 否 | 维度显示名称 |
| `properties` | array | 否 | 维度表中可查询的属性列表 |
| `type` | string | 否 | 维度类型，如 `DATETIME` 表示时间维度 |
| `closureTableName` | string | 否 | 闭包表名称（父子维度专用） |
| `parentKey` | string | 否 | 闭包表中的祖先列（父子维度专用） |
| `childKey` | string | 否 | 闭包表中的后代列（父子维度专用） |

> **父子维度**: 当配置了 `closureTableName`、`parentKey`、`childKey` 时，该维度将被识别为父子维度（层级维度）。
> 查询时对该维度的过滤会自动包含所选节点的所有子孙节点。详见[父子维度文档](Parent-Child-Dimension.md)。

#### 父子维度配置示例

```javascript
{
    name: 'team',
    tableName: 'dim_team',
    foreignKey: 'team_id',
    primaryKey: 'team_id',
    captionColumn: 'team_name',
    caption: '团队',

    // 父子维度配置
    closureTableName: 'team_closure',  // 闭包表名
    parentKey: 'parent_id',            // 闭包表祖先列
    childKey: 'team_id',               // 闭包表后代列

    properties: [
        { column: 'team_id', caption: '团队ID' },
        { column: 'team_name', caption: '团队名称' },
        { column: 'parent_id', caption: '上级团队' },
        { column: 'team_level', caption: '层级' }
    ]
}
```

#### 嵌套维度配置（雪花模型）

嵌套维度用于实现雪花模型，即维度表之间存在层级关系（如：产品 → 品类 → 品类组）。

**配置方式**：在父维度的 `dimensions` 属性中定义子维度。

```javascript
{
    // 一级维度：产品（与事实表直接关联）
    name: 'product',
    tableName: 'dim_product',
    foreignKey: 'product_key',      // 事实表上的外键
    primaryKey: 'product_key',
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
            alias: 'productCategory',   // 别名，用于在 QM 中简化访问
            tableName: 'dim_category',
            foreignKey: 'category_key', // 在父维度表(dim_product)上的外键
            primaryKey: 'category_key',
            captionColumn: 'category_name',
            caption: '品类',

            properties: [
                { column: 'category_id', caption: '品类ID' },
                { column: 'category_level', caption: '品类层级' }
            ],

            // 继续嵌套：品类组（与品类维度关联）
            dimensions: [
                {
                    name: 'group',
                    alias: 'categoryGroup',
                    tableName: 'dim_category_group',
                    foreignKey: 'group_key',  // 在 dim_category 表上的外键
                    primaryKey: 'group_key',
                    captionColumn: 'group_name',
                    caption: '品类组'
                }
            ]
        }
    ]
}
```

**嵌套维度字段说明**：

| 字段 | 类型 | 说明 |
|------|------|------|
| `alias` | string | 维度别名，用于在 QM 中简化列名访问。如果不设置，需要使用完整路径 |
| `foreignKey` | string | **重要**：嵌套维度的 foreignKey 指向父维度表上的列，而非事实表 |
| `dimensions` | array | 子维度列表，可继续嵌套形成多层雪花结构 |

**QM 中访问嵌套维度**：

```javascript
// 方式1：使用别名（推荐，简洁）
columns: [
    'product$caption',           // 一级维度
    'productCategory$caption',   // 二级维度（通过 alias）
    'categoryGroup$caption'      // 三级维度（通过 alias）
]

// 方式2：使用完整路径
columns: [
    'product$caption',
    'product.category$caption',        // 父.子
    'product.category.group$caption'   // 父.子.孙
]
```

**生成的 SQL JOIN**：

```sql
SELECT ...
FROM fact_sales f
LEFT JOIN dim_product p ON f.product_key = p.product_key
LEFT JOIN dim_category c ON p.category_key = c.category_key
LEFT JOIN dim_category_group g ON c.group_key = g.group_key
```

#### 维度属性 (properties) 字段

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `column` | string | 是 | 数据库列名 |
| `caption` | string | 否 | 显示名称 |
| `type` | string | 否 | 数据类型（见类型说明） |

### 1.3 属性定义 (properties)

属性用于定义事实表自身的字段（非聚合字段）。

```javascript
properties: [
    {
        column: 'order_id',        // 数据库列名（必填）
        name: 'orderId',           // 属性名称（可选，默认为column的驼峰形式）
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

#### 属性字段

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `column` | string | 是 | 数据库列名 |
| `name` | string | 否 | 属性名称，默认为 column 的驼峰形式 |
| `caption` | string | 否 | 显示名称 |
| `type` | string | 否 | 数据类型 |
| `format` | string | 否 | 格式化模板（用于日期等） |

### 1.4 度量定义 (measures)

度量用于定义可聚合的数值字段。

```javascript
measures: [
    {
        column: 'quantity',         // 数据库列名（必填）
        name: 'salesQuantity',      // 度量名称（可选）
        caption: '销售数量',          // 显示名称
        type: 'INTEGER',            // 数据类型
        aggregation: 'sum'           // 聚合方式（必填）
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
        aggregation: 'avg'           // 平均值聚合
    }
]
```

#### 度量字段

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `column` | string | 是 | 数据库列名 |
| `name` | string | 否 | 度量名称，默认为 column 的驼峰形式 |
| `caption` | string | 否 | 显示名称 |
| `type` | string | 否 | 数据类型 |
| `aggregation` | string | 是 | 聚合方式 |

#### 聚合方式 (aggregation)

| 值 | 说明 |
|----|------|
| `sum` | 求和 |
| `avg` | 平均值 |
| `count` | 计数 |
| `max` | 最大值 |
| `none` | 不聚合 |

### 1.5 数据类型 (type)

| 类型 | 说明 |
|------|------|
| `STRING` | 字符串 |
| `INTEGER` | 整数 |
| `LONG` | 长整数 |
| `DOUBLE` | 浮点数 |
| `MONEY` | 金额（精确小数） |
| `DATETIME` | 日期时间 |
| `DAY` | 日期（yyyy-MM-dd格式） |
| `BOOL` | 布尔值 |
| `DICT` | 字典值 |

---

## 二、QM 查询模型定义

QM 文件定义基于 TM 的查询视图，包含可查询的列、分组、排序等配置。

### 2.1 基本结构（单模型）

```javascript
export const queryModel = {
    name: 'FactSalesQueryModel',    // 查询模型名称（必填）
    model: 'FactSalesModel',        // 关联的 TM 模型名称（必填）

    columnGroups: [...],            // 列组定义
    orders: [...],                  // 默认排序
    accesses: [...]                 // 权限控制
};
```

### 2.2 多模型关联（多事实表 JOIN）

```javascript
export const queryModel = {
    name: 'OrderPaymentJoinQueryModel',

    // 多模型配置
    model: [
        {
            name: 'FactOrderModel',
            alias: 'fo'                    // 表别名
        },
        {
            name: 'FactPaymentModel',
            alias: 'fp',
            onBuilder: () => {             // JOIN 条件
                return 'fo.order_id = fp.order_id';
            }
        }
    ],

    columnGroups: [...],
    orders: [...],
    accesses: []
};
```

### 2.3 列组定义 (columnGroups)

列组用于对查询字段进行分组，便于 UI 展示。

```javascript
columnGroups: [
    {
        caption: '订单信息',            // 组名称
        items: [
            { name: 'orderId', ui: { fixed: 'left', width: 150 } },
            { name: 'orderStatus' },
            { name: 'totalAmount' }
        ]
    },
    {
        caption: '客户维度',
        items: [
            { name: 'customer$caption' },      // 维度显示值
            { name: 'customer$customerType' }, // 维度属性
            { name: 'customer$province' }
        ]
    },
    {
        caption: '度量',
        items: [
            { name: 'quantity' },
            { name: 'salesAmount' },
            { name: 'profitAmount' }
        ]
    }
]
```

#### 列项字段

| 字段 | 类型 | 说明 |
|------|------|------|
| `name` | string | 字段名称（属性名、度量名、维度$属性名） |
| `ui` | object | UI 配置 |
| `ui.fixed` | string | 固定位置：`left` / `right` |
| `ui.width` | number | 列宽度（像素） |

### 2.4 字段引用格式

| 格式 | 说明 | 示例 |
|------|------|------|
| `属性名` | 事实表属性 | `orderId`, `orderStatus` |
| `度量名` | 度量字段 | `salesAmount`, `quantity` |
| `维度名$caption` | 维度显示值 | `customer$caption` |
| `维度名$id` | 维度ID | `customer$id` |
| `维度名$属性名` | 维度属性 | `customer$customerType`, `customer$province` |

### 2.5 默认排序 (orders)

```javascript
orders: [
    { name: 'orderDate$caption', order: 'desc' },
    { name: 'orderId', order: 'asc' }
]
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `name` | string | 排序字段名 |
| `order` | string | 排序方向：`asc` / `desc` |

### 2.6 权限控制 (accesses)

```javascript
accesses: [
    {
        role: 'admin',
        columns: ['*']              // 可访问所有列
    },
    {
        role: 'user',
        columns: ['orderId', 'orderStatus', 'totalAmount']  // 限制可访问列
    }
]
```

---

## 三、完整示例

### 3.1 事实表模型 (FactOrderModel.tm)

```javascript
export const model = {
    name: 'FactOrderModel',
    caption: '订单事实表',
    tableName: 'fact_order',
    idColumn: 'order_key',

    dimensions: [
        {
            name: 'orderDate',
            tableName: 'dim_date',
            foreignKey: 'date_key',
            primaryKey: 'date_key',
            captionColumn: 'full_date',
            caption: '订单日期',
            properties: [
                { column: 'year', caption: '年' },
                { column: 'quarter', caption: '季度' },
                { column: 'month', caption: '月' }
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
                { column: 'customer_type', caption: '客户类型' },
                { column: 'province', caption: '省份' }
            ]
        }
    ],

    properties: [
        { column: 'order_id', caption: '订单ID', type: 'STRING' },
        { column: 'order_status', caption: '订单状态', type: 'STRING' },
        { column: 'order_time', caption: '下单时间', type: 'DATETIME' }
    ],

    measures: [
        { column: 'total_quantity', caption: '订单数量', type: 'INTEGER', aggregation: 'sum' },
        { column: 'total_amount', caption: '订单总额', type: 'MONEY', aggregation: 'sum' },
        { column: 'pay_amount', caption: '应付金额', type: 'MONEY', aggregation: 'sum' }
    ]
};
```

### 3.2 维度表模型 (DimCustomerModel.tm)

```javascript
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
        { column: 'member_level', caption: '会员等级', type: 'STRING' }
    ],

    measures: []  // 维度表通常没有度量
};
```

### 3.3 查询模型 (FactOrderQueryModel.qm)

```javascript
export const queryModel = {
    name: 'FactOrderQueryModel',
    model: 'FactOrderModel',

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
            caption: '日期维度',
            items: [
                { name: 'orderDate$caption' },
                { name: 'orderDate$year' },
                { name: 'orderDate$month' }
            ]
        },
        {
            caption: '客户维度',
            items: [
                { name: 'customer$caption' },
                { name: 'customer$customerType' },
                { name: 'customer$province' }
            ]
        },
        {
            caption: '度量',
            items: [
                { name: 'totalQuantity' },
                { name: 'totalAmount' },
                { name: 'payAmount' }
            ]
        }
    ],

    orders: [
        { name: 'orderTime', order: 'desc' }
    ],

    accesses: []
};
```

---

## 四、命名约定

### 4.1 文件命名
- TM 文件：`{模型名}Model.tm`，如 `FactOrderModel.tm`
- QM 文件：`{模型名}QueryModel.qm`，如 `FactOrderQueryModel.qm`

### 4.2 字段命名
- 模型中的 `name` 使用 **驼峰命名法**：`orderId`, `customerType`
- 数据库 `column` 使用 **蛇形命名法**：`order_id`, `customer_type`
- 维度属性引用使用 `$` 分隔：`customer$caption`, `orderDate$year`

### 4.3 模型命名
- 事实表模型：`Fact{业务名}Model`，如 `FactOrderModel`
- 维度表模型：`Dim{业务名}Model`，如 `DimCustomerModel`
- 查询模型：`{TM模型名}QueryModel`，如 `FactOrderQueryModel`
