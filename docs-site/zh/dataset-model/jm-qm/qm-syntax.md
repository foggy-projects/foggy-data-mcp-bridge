# QM 语法手册

QM（Query Model，查询模型）用于定义基于 TM 的查询视图，包含可查询的字段、权限控制和 UI 配置。

## 1. 基本结构

QM 文件使用 JavaScript 语法，导出一个 `queryModel` 对象：

```javascript
export const queryModel = {
    name: 'FactOrderQueryModel',    // 查询模型名称（必填）
    caption: '订单查询',             // 显示名称
    model: 'FactOrderModel',        // 关联的 TM 模型名称（必填）

    columnGroups: [...],            // 列组定义
    orders: [...],                  // 默认排序
    accesses: [...]                 // 权限控制
};
```

### 1.1 基础字段

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `name` | string | 是 | 查询模型唯一标识 |
| `caption` | string | 否 | 显示名称 |
| `model` | string/array | 是 | 关联的 TM 模型（单个或多个） |
| `columnGroups` | array | 否 | 列组定义 |
| `orders` | array | 否 | 默认排序 |
| `accesses` | array | 否 | 权限控制 |

---

## 2. 单模型关联

最常见的情况是 QM 关联单个 TM：

```javascript
export const queryModel = {
    name: 'FactOrderQueryModel',
    model: 'FactOrderModel',   // 直接使用 TM 名称
    columnGroups: [...]
};
```

---

## 3. 多模型关联

当需要关联多个事实表时，使用数组配置：

```javascript
export const queryModel = {
    name: 'OrderPaymentJoinQueryModel',
    caption: '订单支付关联查询',

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

    columnGroups: [
        {
            caption: '订单信息',
            items: [
                { name: 'fo.orderId' },
                { name: 'fo.orderStatus' }
            ]
        },
        {
            caption: '支付信息',
            items: [
                { name: 'fp.paymentId' },
                { name: 'fp.paymentAmount' }
            ]
        }
    ]
};
```

### 3.1 多模型字段说明

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `name` | string | 是 | TM 模型名称 |
| `alias` | string | 是 | 表别名，用于区分不同模型的字段 |
| `onBuilder` | function | 否 | JOIN 条件构建函数（第二个及之后的模型必填） |

### 3.2 多模型字段引用

多模型时使用 `别名.字段名` 格式：

```javascript
columns: [
    'fo.orderId',              // 订单模型的 orderId
    'fp.paymentAmount',        // 支付模型的 paymentAmount
    'fo.customer$caption'      // 订单模型关联的客户维度
]
```

---

## 4. 列组定义 (columnGroups)

列组用于对查询字段进行分组，便于 UI 展示。

```javascript
columnGroups: [
    {
        caption: '订单信息',            // 组名称
        items: [
            { name: 'orderId', ui: { fixed: 'left', width: 150 } },
            { name: 'orderStatus' },
            { name: 'orderTime' }
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
            { name: 'totalQuantity' },
            { name: 'totalAmount' }
        ]
    }
]
```

### 4.1 列组字段

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `caption` | string | 否 | 组名称 |
| `name` | string | 否 | 组标识 |
| `items` | array | 是 | 列项列表 |

### 4.2 列项字段

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `name` | string | 是 | 字段名称 |
| `caption` | string | 否 | 覆盖 TM 中的显示名称 |
| `ui` | object | 否 | UI 配置 |

### 4.3 UI 配置

| 字段 | 类型 | 说明 |
|------|------|------|
| `fixed` | string | 固定位置：`left` / `right` |
| `width` | number | 列宽度（像素） |
| `align` | string | 对齐方式：`left` / `center` / `right` |
| `visible` | boolean | 是否默认可见 |

---

## 5. 字段引用格式

在 QM 中引用 TM 的字段：

| 格式 | 说明 | 示例 |
|------|------|------|
| `属性名` | 事实表属性 | `orderId`, `orderStatus` |
| `度量名` | 度量字段 | `totalAmount`, `quantity` |
| `维度名$caption` | 维度显示值 | `customer$caption` |
| `维度名$id` | 维度 ID | `customer$id` |
| `维度名$属性名` | 维度属性 | `customer$customerType` |
| `别名.字段名` | 多模型时使用 | `fo.orderId` |
| `嵌套维度别名$属性` | 嵌套维度（通过 alias） | `productCategory$caption` |
| `维度.子维度$属性` | 嵌套维度（完整路径） | `product.category$caption` |

### 5.1 维度字段示例

```javascript
columnGroups: [
    {
        caption: '客户信息',
        items: [
            { name: 'customer$caption' },       // 客户名称
            { name: 'customer$id' },            // 客户 ID
            { name: 'customer$customerType' },  // 客户类型
            { name: 'customer$province' },      // 省份
            { name: 'customer$city' }           // 城市
        ]
    },
    {
        caption: '时间维度',
        items: [
            { name: 'orderDate$caption' },      // 日期显示值
            { name: 'orderDate$year' },         // 年
            { name: 'orderDate$quarter' },      // 季度
            { name: 'orderDate$month' }         // 月
        ]
    }
]
```

---

## 6. 默认排序 (orders)

定义查询的默认排序规则：

```javascript
orders: [
    { name: 'orderTime', order: 'desc' },
    { name: 'orderId', order: 'asc' }
]
```

### 6.1 排序字段

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `name` | string | 是 | 排序字段名 |
| `order` | string | 是 | 排序方向：`asc`（升序）/ `desc`（降序） |

---

## 7. 权限控制 (accesses)

控制不同角色可访问的字段：

```javascript
accesses: [
    {
        role: 'admin',
        columns: ['*']              // 可访问所有列
    },
    {
        role: 'manager',
        columns: [
            'orderId',
            'orderStatus',
            'customer$caption',
            'totalAmount'
        ]
    },
    {
        role: 'user',
        columns: ['orderId', 'orderStatus']  // 限制可访问列
    }
]
```

### 7.1 权限字段

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `role` | string | 是 | 角色标识 |
| `columns` | array | 是 | 可访问的列，`['*']` 表示所有列 |

### 7.2 权限匹配规则

1. 系统根据当前用户的角色匹配 accesses 配置
2. 如果找到匹配的角色，只返回 columns 中指定的字段
3. 如果没有匹配的角色，默认返回所有字段
4. `['*']` 表示可访问所有字段

---

## 8. 计算字段

可以在 QM 中定义计算字段：

```javascript
columnGroups: [
    {
        caption: '计算字段',
        items: [
            {
                name: 'profitRate',
                caption: '利润率',
                formula: 'profitAmount / salesAmount * 100',
                type: 'NUMBER'
            },
            {
                name: 'avgPrice',
                caption: '平均单价',
                formula: 'totalAmount / totalQuantity',
                type: 'MONEY'
            }
        ]
    }
]
```

### 8.1 计算字段配置

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `name` | string | 是 | 计算字段名 |
| `caption` | string | 否 | 显示名称 |
| `formula` | string | 是 | 计算公式 |
| `type` | string | 否 | 结果数据类型 |

---

## 9. 完整示例

### 9.1 基础查询模型

```javascript
// FactOrderQueryModel.qm

export const queryModel = {
    name: 'FactOrderQueryModel',
    caption: '订单查询',
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
            caption: '客户信息',
            items: [
                { name: 'customer$caption' },
                { name: 'customer$customerType' },
                { name: 'customer$province' }
            ]
        },
        {
            caption: '商品信息',
            items: [
                { name: 'product$caption' },
                { name: 'product$category' },
                { name: 'product$unitPrice' }
            ]
        },
        {
            caption: '度量',
            items: [
                { name: 'totalQuantity' },
                { name: 'totalAmount' },
                { name: 'profitAmount' }
            ]
        }
    ],

    orders: [
        { name: 'orderTime', order: 'desc' }
    ],

    accesses: [
        { role: 'admin', columns: ['*'] },
        { role: 'sales', columns: [
            'orderId', 'orderStatus', 'orderTime',
            'customer$caption', 'customer$customerType',
            'product$caption',
            'totalQuantity', 'totalAmount'
        ]}
    ]
};
```

### 9.2 多事实表关联

```javascript
// OrderPaymentQueryModel.qm

export const queryModel = {
    name: 'OrderPaymentQueryModel',
    caption: '订单支付查询',

    model: [
        {
            name: 'FactOrderModel',
            alias: 'order'
        },
        {
            name: 'FactPaymentModel',
            alias: 'payment',
            onBuilder: () => 'order.order_id = payment.order_id'
        }
    ],

    columnGroups: [
        {
            caption: '订单信息',
            items: [
                { name: 'order.orderId', ui: { fixed: 'left' } },
                { name: 'order.orderStatus' },
                { name: 'order.totalAmount' }
            ]
        },
        {
            caption: '支付信息',
            items: [
                { name: 'payment.paymentId' },
                { name: 'payment.paymentMethod' },
                { name: 'payment.paymentAmount' },
                { name: 'payment.paymentTime' }
            ]
        },
        {
            caption: '客户信息',
            items: [
                { name: 'order.customer$caption' },
                { name: 'order.customer$customerType' }
            ]
        }
    ],

    orders: [
        { name: 'payment.paymentTime', order: 'desc' }
    ]
};
```

### 9.3 带计算字段的查询模型

```javascript
// SalesAnalysisQueryModel.qm

export const queryModel = {
    name: 'SalesAnalysisQueryModel',
    caption: '销售分析',
    model: 'FactSalesModel',

    columnGroups: [
        {
            caption: '维度',
            items: [
                { name: 'salesDate$year' },
                { name: 'salesDate$month' },
                { name: 'product$category' },
                { name: 'customer$customerType' }
            ]
        },
        {
            caption: '基础度量',
            items: [
                { name: 'salesQuantity' },
                { name: 'salesAmount' },
                { name: 'costAmount' },
                { name: 'profitAmount' }
            ]
        },
        {
            caption: '计算指标',
            items: [
                {
                    name: 'profitRate',
                    caption: '利润率(%)',
                    formula: 'profitAmount / salesAmount * 100',
                    type: 'NUMBER'
                },
                {
                    name: 'avgOrderAmount',
                    caption: '客单价',
                    formula: 'salesAmount / COUNT(*)',
                    type: 'MONEY'
                }
            ]
        }
    ],

    orders: [
        { name: 'salesDate$year', order: 'desc' },
        { name: 'salesDate$month', order: 'desc' }
    ]
};
```

---

## 10. 命名约定

### 10.1 文件命名

- QM 文件：`{TM模型名}QueryModel.qm`
- 示例：`FactOrderQueryModel.qm`

### 10.2 模型命名

- 查询模型名：`{TM模型名}QueryModel`
- 示例：`FactOrderQueryModel`

---

## 下一步

- [TM 语法手册](./jm-syntax.md) - 表格模型定义
- [DSL 查询 API](../api/query-api.md) - 使用 DSL 查询数据
- [父子维度](./parent-child.md) - 层级结构维度
- [权限控制](../api/authorization.md) - 详细的权限配置
