# TM 语法手册

TM（Table Model，表模型）用于定义数据库表的结构和关联关系。本文档详细介绍 TM 的完整语法规范。

## 1. 基本结构

TM 文件使用 JavaScript ES6 模块语法，导出一个 `model` 对象：

```javascript
export const model = {
    name: 'FactSalesModel',      // 模型名称（必填，唯一标识）
    caption: '销售事实表',         // 模型显示名称
    description: '销售订单明细数据', // 模型描述
    tableName: 'fact_sales',     // 对应的数据库表名（必填）
    idColumn: 'sales_key',       // 主键列名

    dimensions: [...],           // 维度定义（关联其他表）
    properties: [...],           // 属性定义（本表字段）
    measures: [...]              // 度量定义（可聚合字段）
};
```

### 1.1 模型基础字段

| 字段 | 类型 | 必填 | 说明                         |
|------|------|------|----------------------------|
| `name` | string | 是 | 模型唯一标识，QM 中通过此名称引用         |
| `caption` | string | 否 | 模型显示名称，建议填写，使用mcp时会传递给AI   |
| `description` | string | 否 | 模型详细描述，建议填写，使用mcp时会传递给AI   |
| `tableName` | string | 是¹ | 对应的数据库表名、mongo集合名          |
| `viewSql` | string | 否¹ | 视图SQL，与 tableName 二选一      |
| `schema` | string | 否 | 数据库 Schema（跨 Schema 访问时使用） |
| `idColumn` | string | 否 | 主键列名                       |
| `type` | string | 否 | 模型类型，默认 `jdbc`、`mongo`     |
| `deprecated` | boolean | 否 | 标记为废弃，默认 false             |

> ¹ `tableName` 和 `viewSql` 二选一，优先使用 `tableName`

### 1.2 AI 增强配置

可为模型、维度、属性、度量添加 `ai` 配置，用于优化 AI 自然语言查询：

```javascript
{
    name: 'salesAmount',
    caption: '销售金额',
    type: 'MONEY',
    ai: {
        enabled: true,              // 是否激活AI分析（默认 true）
        prompt: '客户实际支付金额',   // 替代 description 的提示词
        levels: [1, 2]              // 激活等级列表
    }
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `enabled` | boolean | 是否激活AI分析，默认 true |
| `prompt` | string | 提示词，若填写则替代 description |
| `levels` | number[] | 激活等级列表，字段可属于多个级别 |

---

## 2. 维度定义 (dimensions)

维度用于定义与其他表的关联关系，查询时自动生成 JOIN。

### 2.1 基本维度

```javascript
dimensions: [
    {
        name: 'customer',              // 维度名称（用于查询时引用）
        caption: '客户',                // 维度显示名称
        description: '购买商品的客户信息', // 维度描述

        tableName: 'dim_customer',     // 关联的维度表名
        foreignKey: 'customer_key',    // 本表的外键字段
        primaryKey: 'customer_key',    // 维度表的主键字段
        captionColumn: 'customer_name', // 维度的显示字段

        keyCaption: '客户Key',          // 主键字段的显示名称
        keyDescription: '客户代理键，自增整数', // 主键字段的描述

        // 维度属性（维度表中可查询的字段）
        properties: [
            {
                column: 'customer_id',
                caption: '客户ID',
                description: '客户唯一标识'
            },
            {
                column: 'customer_type',
                caption: '客户类型',
                description: '客户类型：个人/企业'
            },
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
| `caption` | string | 否 | 维度显示名称 |
| `description` | string | 否 | 维度详细描述 |
| `tableName` | string | 是¹ | 关联的维度表名 |
| `viewSql` | string | 否¹ | 维度视图SQL，与 tableName 二选一 |
| `schema` | string | 否 | 维度表的 Schema |
| `foreignKey` | string | 是 | 事实表中的外键字段 |
| `primaryKey` | string | 是 | 维度表的主键字段 |
| `captionColumn` | string | 否 | 维度的显示字段，用于 `维度名$caption` |
| `keyCaption` | string | 否 | 主键字段的显示名称，默认为 `${caption}Key` |
| `keyDescription` | string | 否 | 主键字段的描述信息 |
| `type` | string | 否 | 维度类型，如 `DATETIME` 表示时间维度 |
| `properties` | array | 否 | 维度表中可查询的属性列表 |
| `forceIndex` | string | 否 | 强制使用的索引名称 |

> ¹ `tableName` 和 `viewSql` 二选一，优先使用 `tableName`

### 2.3 嵌套维度（雪花模型）

嵌套维度用于实现雪花模型，即维度表之间存在层级关系。

```javascript
{
    // 一级维度：产品（与事实表直接关联）
    name: 'product',
    tableName: 'dim_product',
    foreignKey: 'product_key',       // 事实表上的外键
    primaryKey: 'product_key',
    captionColumn: 'product_name',
    caption: '商品',

    properties: [
        { column: 'product_id', caption: '商品ID' },
        { column: 'brand', caption: '品牌' },
        { column: 'unit_price', caption: '单价', type: 'MONEY' }
    ],

    // 嵌套子维度：品类（与产品维度关联，而非事实表）
    dimensions: [
        {
            name: 'category',
            alias: 'productCategory',   // 别名，简化 QM 访问
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
                    foreignKey: 'group_key',  // 在父维度表(dim_category)上的外键
                    primaryKey: 'group_key',
                    captionColumn: 'group_name',
                    caption: '品类组',

                    properties: [
                        { column: 'group_id', caption: '品类组ID' },
                        { column: 'group_type', caption: '组类型' }
                    ]
                }
            ]
        }
    ]
}
```

**嵌套维度关键点**：

| 字段 | 说明 |
|------|------|
| `alias` | 维度别名，用于在 QM 中简化列名访问，避免路径过长 |
| `foreignKey` | **重要**：嵌套维度的 foreignKey 指向父维度表上的列 |
| `dimensions` | 子维度列表，可继续嵌套形成多层结构 |

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
LEFT JOIN dim_product p ON f.product_key = p.product_key
LEFT JOIN dim_category c ON p.category_key = c.category_key
LEFT JOIN dim_category_group g ON c.group_key = g.group_key
```

### 2.4 父子维度（层级结构）

父子维度用于处理树形层级结构数据（如组织架构、商品分类），通过闭包表实现高效查询。

```javascript
{
    name: 'team',
    tableName: 'dim_team',
    foreignKey: 'team_id',
    primaryKey: 'team_id',
    captionColumn: 'team_name',
    caption: '团队',
    description: '销售所属团队',
    keyDescription: '团队ID，字符串格式',

    // 父子维度配置
    closureTableName: 'team_closure',  // 闭包表名（必填）
    parentKey: 'parent_id',            // 闭包表祖先列（必填）
    childKey: 'team_id',               // 闭包表后代列（必填）

    properties: [
        { column: 'team_id', caption: '团队ID', type: 'STRING' },
        { column: 'team_name', caption: '团队名称', type: 'STRING' },
        { column: 'parent_id', caption: '上级团队', type: 'STRING' },
        { column: 'team_level', caption: '层级', type: 'INTEGER' },
        { column: 'manager_name', caption: '负责人', type: 'STRING' }
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

**闭包表结构示例**：

```sql
CREATE TABLE team_closure (
    parent_id VARCHAR(50),  -- 祖先节点ID
    child_id  VARCHAR(50),  -- 后代节点ID
    depth     INT,          -- 层级深度（0表示自己）
    PRIMARY KEY (parent_id, child_id)
);
```

> 详细说明请参考 [父子维度文档](./parent-child.md)

---

## 3. 属性定义 (properties)

属性用于定义表自身的字段（非聚合字段）。

### 3.1 基本属性

```javascript
properties: [
    {
        column: 'order_id',        // 数据库列名（必填）
        name: 'orderId',           // 属性名称（可选）
        caption: '订单ID',          // 显示名称
        description: '订单唯一标识', // 详细描述
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

### 3.2 字典引用属性

使用 `dictRef` 将数据库值映射为显示标签：

```javascript
import { dicts } from '../dicts.fsscript';

properties: [
    {
        column: 'order_status',
        caption: '订单状态',
        type: 'STRING',
        dictRef: dicts.order_status  // 引用字典
    },
    {
        column: 'payment_method',
        caption: '支付方式',
        type: 'STRING',
        dictRef: dicts.payment_method
    }
]
```

**字典定义示例** (dicts.fsscript):

```javascript
import { registerDict } from '@jdbcModelDictService';

export const dicts = {
    order_status: registerDict({
        id: 'order_status',
        caption: '订单状态',
        items: [
            { value: 'PENDING', label: '待处理' },
            { value: 'CONFIRMED', label: '已确认' },
            { value: 'SHIPPED', label: '已发货' },
            { value: 'COMPLETED', label: '已完成' },
            { value: 'CANCELLED', label: '已取消' }
        ]
    }),

    payment_method: registerDict({
        id: 'payment_method',
        caption: '支付方式',
        items: [
            { value: '1', label: '现付' },
            { value: '2', label: '到付' },
            { value: '3', label: '货到付款' }
        ]
    })
};
```

### 3.3 计算属性

使用 `formulaDef` 定义计算字段。常见场景包括 JSON 字段提取、字符串拼接等：

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
    }
]
```

### 3.4 属性字段说明

| 字段 | 类型 | 必填 | 说明                        |
|------|------|------|---------------------------|
| `column` | string | 是 | 数据库列名                     |
| `name` | string | 否 | 属性名称，默认为 column 的驼峰形式     |
| `alias` | string | 否 | 属性别名                      |
| `caption` | string | 否 | 显示名称                      |
| `description` | string | 否 | 详细描述，若字段含义复杂，建议填写，有助于AI推断 |
| `type` | string | 否 | 数据类型（见 [5. 数据类型](#5-数据类型)）           |
| `format` | string | 否 | 格式化模板（用于日期等）              |
| `dictRef` | string | 否 | 字典引用，用于值到标签的转换            |
| `formulaDef` | object | 否 | 公式定义（见 3.5）               |

### 3.5 公式定义 (formulaDef)

| 字段 | 类型 | 说明 |
|------|------|------|
| `builder` | function | SQL 构建函数，参数 `alias` 为表别名 |
| `value` | string | 公式表达式（基于度量名称） |
| `description` | string | 公式的文字描述 |

> `builder` 和 `value` 二选一，`builder` 更灵活，可直接操作 SQL

---

## 4. 度量定义 (measures)

度量用于定义可聚合的数值字段。

### 4.1 基本度量

```javascript
measures: [
    {
        column: 'quantity',         // 数据库列名（必填）
        name: 'salesQuantity',      // 度量名称（可选）
        caption: '销售数量',          // 显示名称
        description: '商品销售件数',  // 详细描述
        type: 'INTEGER',            // 数据类型
        aggregation: 'sum'          // 聚合方式（必填）
    },
    {
        column: 'sales_amount',
        name: 'salesAmount',
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

### 4.2 计算度量

使用 `formulaDef` 定义计算度量：

```javascript
measures: [
    {
        column: 'tax_amount',
        name: 'taxAmount2',
        caption: '税额*2',
        description: '用于测试计算字段',
        type: 'MONEY',
        formulaDef: {
            builder: (alias) => {
                return `${alias}.tax_amount + 1`;
            },
            description: '税额加一'
        }
    }
]
```

### 4.3 COUNT 聚合

不基于具体列的计数：

```javascript
measures: [
    {
        name: 'recordCount',
        caption: '记录数',
        aggregation: 'count',
        type: 'INTEGER'
    }
]
```

### 4.4 度量字段说明

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `column` | string | 否¹ | 数据库列名 |
| `name` | string | 否 | 度量名称，默认为 column 的驼峰形式 |
| `alias` | string | 否 | 度量别名 |
| `caption` | string | 否 | 显示名称 |
| `description` | string | 否 | 详细描述 |
| `type` | string | 否 | 数据类型（见 [5. 数据类型](#5-数据类型)） |
| `aggregation` | string | 是 | 聚合方式（见 4.5） |
| `formulaDef` | object | 否 | 公式定义（见 3.5） |

> ¹ `count` 聚合可以不指定 column

### 4.5 聚合方式

| 值 | 说明 | 适用类型 |
|----|------|----------|
| `sum` | 求和 | 数值类型 |
| `avg` | 平均值 | 数值类型 |
| `count` | 计数 | 所有类型 |
| `max` | 最大值 | 数值/日期类型 |
| `min` | 最小值 | 数值/日期类型 |
| `none` | 不聚合 | 所有类型 |

---

## 5. 数据类型

### 5.1 类型列表

| 类型 | 别名 | 说明 | Java 类型 | 使用场景 |
|------|------|------|-----------|----------|
| `STRING` | `TEXT` | 字符串 | String | 文本、编�� |
| `INTEGER` | - | 整数 | Integer | 计数、枚举 |
| `BIGINT` | `LONG` | 长整数 | Long | 大数值主键 |
| `MONEY` | `NUMBER`, `BigDecimal` | 金额/精确小数 | BigDecimal | 金额、价格 |
| `DATETIME` | - | 日期时间 | Date | 时间戳 |
| `DAY` | `DATE` | 日期 | Date | 日期（yyyy-MM-dd） |
| `BOOL` | `Boolean` | 布尔值 | Boolean | 是/否标志 |
| `DICT` | - | 字典值 | Integer | 字典编码 |

### 5.2 类型选择建议

- **金额字段**：使用 `MONEY`，避免浮点精度问题
- **主键字段**：代理键用 `INTEGER` 或 `BIGINT`，业务键用 `STRING`
- **日期字段**：时间戳用 `DATETIME`，仅日期用 `DAY`
- **枚举字段**：优先使用 `dictRef` + `STRING`，而非创建维度表

---

## 6. 完整示例

### 6.1 事实表模型

```javascript
// FactSalesModel.tm
/**
 * 销售事实表模型定义
 *
 * @description 电商测试数据 - 销售事实表（订单明细）
 *              包含日期、商品、客户、门店、渠道、促销等维度关联
 */
import { dicts } from '../dicts.fsscript';

export const model = {
    name: 'FactSalesModel',
    caption: '销售事实表',
    tableName: 'fact_sales',
    idColumn: 'sales_key',

    // 维度定义 - 关联维度表
    dimensions: [
        {
            name: 'salesDate',
            tableName: 'dim_date',
            foreignKey: 'date_key',
            primaryKey: 'date_key',
            captionColumn: 'full_date',
            caption: '销售日期',
            description: '订单发生的日期',
            keyDescription: '日期主键，格式yyyyMMdd，如20240101',

            properties: [
                { column: 'year', caption: '年', description: '销售发生的年份' },
                { column: 'quarter', caption: '季度', description: '销售发生的季度（1-4）' },
                { column: 'month', caption: '月', description: '销售发生的月份（1-12）' },
                { column: 'month_name', caption: '月份名称' },
                { column: 'day_of_week', caption: '周几' },
                { column: 'is_weekend', caption: '是否周末' }
            ]
        },
        {
            name: 'product',
            tableName: 'dim_product',
            foreignKey: 'product_key',
            primaryKey: 'product_key',
            captionColumn: 'product_name',
            caption: '商品',
            description: '销售的商品信息',

            properties: [
                { column: 'product_id', caption: '商品ID' },
                { column: 'category_name', caption: '一级品类名称' },
                { column: 'sub_category_name', caption: '二级品类名称' },
                { column: 'brand', caption: '品牌' },
                { column: 'unit_price', caption: '商品售价', type: 'MONEY' },
                { column: 'unit_cost', caption: '商品成本', type: 'MONEY' }
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
                { column: 'gender', caption: '性别' },
                { column: 'age_group', caption: '年龄段' },
                { column: 'province', caption: '省份' },
                { column: 'city', caption: '城市' },
                { column: 'member_level', caption: '会员等级' }
            ]
        }
    ],

    // 属性定义 - 事实表自身属性
    properties: [
        {
            column: 'sales_key',
            caption: '销售代理键',
            type: 'BIGINT'
        },
        {
            column: 'order_id',
            caption: '订单ID',
            type: 'STRING'
        },
        {
            column: 'order_line_no',
            caption: '订单行号',
            type: 'INTEGER'
        },
        {
            column: 'order_status',
            caption: '订单状态',
            type: 'STRING',
            dictRef: dicts.order_status
        },
        {
            column: 'payment_method',
            caption: '支付方式',
            type: 'STRING',
            dictRef: dicts.payment_method
        },
        {
            column: 'created_at',
            caption: '创建时间',
            type: 'DATETIME'
        }
    ],

    // 度量定义
    measures: [
        {
            column: 'quantity',
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
// DimProductModel.tm
/**
 * 商品维度模型定义
 *
 * @description 电商测试数据 - 商品维度表
 */
export const model = {
    name: 'DimProductModel',
    caption: '商品维度',
    tableName: 'dim_product',
    idColumn: 'product_key',

    dimensions: [],  // 维度表通常不关联其他维度

    properties: [
        {
            column: 'product_key',
            caption: '商品代理键',
            type: 'INTEGER'
        },
        {
            column: 'product_id',
            caption: '商品业务ID',
            type: 'STRING'
        },
        {
            column: 'product_name',
            caption: '商品名称',
            type: 'STRING'
        },
        {
            column: 'category_id',
            caption: '一级品类ID',
            type: 'STRING'
        },
        {
            column: 'category_name',
            caption: '一级品类名称',
            type: 'STRING'
        },
        {
            column: 'sub_category_id',
            caption: '二级品类ID',
            type: 'STRING'
        },
        {
            column: 'sub_category_name',
            caption: '二级品类名称',
            type: 'STRING'
        },
        {
            column: 'brand',
            caption: '品牌',
            type: 'STRING'
        },
        {
            column: 'unit_price',
            caption: '售价',
            type: 'MONEY'
        },
        {
            column: 'unit_cost',
            caption: '成本',
            type: 'MONEY'
        },
        {
            column: 'status',
            caption: '状态',
            type: 'STRING'
        },
        {
            column: 'created_at',
            caption: '创建时间',
            type: 'DATETIME'
        }
    ],

    measures: []  // 维度表通常没有度量
};
```

### 6.3 日期维度表模型

```javascript
// DimDateModel.tm
/**
 * 日期维度模型定义
 *
 * @description 电商测试数据 - 日期维度表
 */
export const model = {
    name: 'DimDateModel',
    caption: '日期维度',
    tableName: 'dim_date',
    idColumn: 'date_key',

    dimensions: [],

    properties: [
        {
            column: 'date_key',
            caption: '日期键',
            description: '日期主键，格式为yyyyMMdd的整数，如20240101',
            type: 'INTEGER'
        },
        {
            column: 'full_date',
            caption: '完整日期',
            description: '完整日期，格式为yyyy-MM-dd',
            type: 'DAY'
        },
        {
            column: 'year',
            caption: '年',
            description: '年份，如2024',
            type: 'INTEGER'
        },
        {
            column: 'quarter',
            caption: '季度',
            description: '季度数字，1-4表示第一到第四季度',
            type: 'INTEGER'
        },
        {
            column: 'month',
            caption: '月',
            description: '月份数字，1-12',
            type: 'INTEGER'
        },
        {
            column: 'month_name',
            caption: '月份名称',
            description: '月份中文名，如一月、二月、十二月',
            type: 'STRING'
        },
        {
            column: 'week_of_year',
            caption: '年度周数',
            description: '一年中的第几周，1-53',
            type: 'INTEGER'
        },
        {
            column: 'day_of_week',
            caption: '周几',
            description: '一周中的第几天，1=周一，7=周日',
            type: 'INTEGER'
        },
        {
            column: 'is_weekend',
            caption: '是否周末',
            description: '是否为周末（周六或周日）',
            type: 'BOOL'
        },
        {
            column: 'is_holiday',
            caption: '是否节假日',
            description: '是否为法定节假日',
            type: 'BOOL'
        }
    ],

    measures: []
};
```

---

## 7. 命名约定

### 7.1 文件命名

- TM 文件：`{模型名}Model.tm`
- 事实表：`Fact{业务名}Model.tm`，如 `FactSalesModel.tm`
- 维度表：`Dim{业务名}Model.tm`，如 `DimCustomerModel.tm`
- 字典文件：`dicts.fsscript`

### 7.2 字段命名

| 位置 | 规范 | 示例 |
|------|------|------|
| 模型 `name` | 大驼峰 PascalCase | `FactSalesModel`, `DimCustomerModel` |
| 字段 `name` | 小驼峰 camelCase | `orderId`, `salesAmount`, `customerType` |
| 数据库 `column` | 蛇形 snake_case | `order_id`, `sales_amount`, `customer_type` |
| 维度属性引用 | `$` 分隔 | `customer$caption`, `salesDate$year` |

### 7.3 模型设计建议

1. **事实表**：
   - 包含业务事实度量（销售额、数量等）
   - 包含指向维度表的外键
   - 粒度要明确（如订单行级、订单级）

2. **维度表**：
   - 包含描述性属性
   - 使用代理键（surrogate key）作为主键
   - 维度表一般不定义度量

3. **星型模型 vs 雪花模型**：
   - 星型模型：维度表不嵌套，查询性能更好（推荐）
   - 雪花模型：维度表嵌套，节省存储空间，需要时使用

---

## 8. 高级特性

### 8.1 扩展数据

使用 `extData` 存储自定义元数据：

```javascript
{
    name: 'FactSalesModel',
    caption: '销售事实表',
    extData: {
        businessOwner: '销售部',
        updateFrequency: 'daily',
        customTag: 'core-metric'
    }
}
```

### 8.2 废弃标记

标记过时的模型或字段：

```javascript
{
    name: 'oldSalesAmount',
    caption: '旧版销售金额',
    column: 'old_sales_amt',
    type: 'MONEY',
    deprecated: true  // 前端配置时会显示废弃提示
}
```

---

## 下一步

- [QM 语法手册](./qm-syntax.md) - 查询模型定义
- [父子维度](./parent-child.md) - 层级结构维度详解
- [计算字段](./calculated-fields.md) - 复杂计算逻辑
- [查询 API](../api/query-api.md) - 使用 DSL 查询数据
