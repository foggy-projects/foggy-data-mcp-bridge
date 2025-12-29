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

当需要关联多个事实表时，使用 `loadTableModel` 加载模型，通过 `ref` 引用字段。

```javascript
// 加载模型
const fo = loadTableModel('FactOrderModel');
const fp = loadTableModel('FactPaymentModel');

export const queryModel = {
    name: 'OrderPaymentJoinQueryModel',
    caption: '订单支付关联查询',

    // 多模型配置
    model: [
        {
            name: fo,
            alias: 'fo'                    // 表别名
        },
        {
            name: fp,
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
                { ref: fo.orderId },           // V2：使用 ref 引用
                { ref: fo.orderStatus },
                { ref: fo.customer }           // 维度引用，自动展开为 $id 和 $caption
            ]
        },
        {
            caption: '支付信息',
            items: [
                { ref: fp.paymentId },
                { ref: fp.payAmount }
            ]
        }
    ]
};
```

### 3.1 多模型字段说明

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `name` | string/proxy | 是 | TM 模型名称或 loadTableModel 返回的代理 |
| `alias` | string | 是 | 表别名，用于区分不同模型的字段 |
| `onBuilder` | function | 否 | JOIN 条件构建函数（第二个及之后的模型必填） |

---

## 4. 列组定义 (columnGroups)

列组用于对查询字段进行分组，便于 UI 展示。使用 `loadTableModel` 加载模型后，通过 `ref` 引用字段：

```javascript
const fo = loadTableModel('FactOrderModel');

columnGroups: [
    {
        caption: '订单信息',
        items: [
            { ref: fo.orderId, ui: { fixed: 'left', width: 150 } },
            { ref: fo.orderStatus }
        ]
    },
    {
        caption: '客户维度',
        items: [
            { ref: fo.customer },              // 自动展开为 $id + $caption
            { ref: fo.customer$customerType }  // 维度属性
        ]
    }
]
```

**ref 语法优势**：
- IDE 支持代码补全和类型检查
- 重构时自动更新引用
- 编译时即可发现错误

### 4.1 维度引用的自动展开

当 `ref` 指向一个维度（无 `$` 后缀）时，会自动展开为两列：

```javascript
{ ref: fo.customer }
// 等价于自动生成两列：
// customer$id
// customer$caption
```

### 4.2 列组字段

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `caption` | string | 否 | 组名称 |
| `name` | string | 否 | 组标识 |
| `items` | array | 是 | 列项列表 |

### 4.3 列项字段

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `ref` | object | 是 | 字段引用（使用 loadTableModel 代理） |
| `caption` | string | 否 | 覆盖 TM 中的显示名称 |
| `alias` | string | 否 | 输出列别名 |
| `ui` | object | 否 | UI 配置 |

### 4.4 UI 配置

| 字段 | 类型 | 说明 |
|------|------|------|
| `fixed` | string | 固定位置：`left` / `right` |
| `width` | number | 列宽度（像素） |
| `align` | string | 对齐方式：`left` / `center` / `right` |
| `visible` | boolean | 是否默认可见 |

---

## 5. 字段引用格式

使用 `loadTableModel` 加载模型后，通过代理对象引用字段：

```javascript
const fo = loadTableModel('FactOrderModel');

// 事实表属性
fo.orderId
fo.orderStatus

// 度量
fo.totalAmount

// 维度（自动展开为 $id + $caption）
fo.customer

// 维度属性
fo.customer$customerType
fo.customer$province

// 嵌套维度（使用 . 路径语法）
fo.product.category$caption
fo.product.category.group$caption
```

### 5.1 嵌套维度引用

嵌套维度使用路径语法引用：

```javascript
{ ref: fo.product.category$caption }
{ ref: fo.product.category.group$caption }
```

**路径语法说明**：

- `fo.product` → 一级维度
- `fo.product.category` → 二级维度（product 的子维度）
- `fo.product.category$caption` → 二级维度的 caption 属性
- `fo.product.category$id` → 二级维度的 id
- `fo.product.category.group$caption` → 三级维度的 caption

**输出列名格式**：

路径语法在输出时使用下划线分隔，避免 JavaScript 属性名中的 `.` 问题：

| 引用 | 输出列名 |
|------|---------|
| `fo.product$caption` | `product$caption` |
| `fo.product.category$caption` | `product_category$caption` |
| `fo.product.category.group$caption` | `product_category_group$caption` |

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

const fo = loadTableModel('FactOrderModel');

export const queryModel = {
    name: 'FactOrderQueryModel',
    caption: '订单查询',
    model: fo,

    columnGroups: [
        {
            caption: '订单信息',
            items: [
                { ref: fo.orderId, ui: { fixed: 'left', width: 150 } },
                { ref: fo.orderStatus },
                { ref: fo.orderTime }
            ]
        },
        {
            caption: '客户信息',
            items: [
                { ref: fo.customer },
                { ref: fo.customer$customerType },
                { ref: fo.customer$province }
            ]
        },
        {
            caption: '商品信息',
            items: [
                { ref: fo.product },
                { ref: fo.product$category },
                { ref: fo.product$unitPrice }
            ]
        },
        {
            caption: '度量',
            items: [
                { ref: fo.totalQuantity },
                { ref: fo.totalAmount },
                { ref: fo.profitAmount }
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

const order = loadTableModel('FactOrderModel');
const payment = loadTableModel('FactPaymentModel');

export const queryModel = {
    name: 'OrderPaymentQueryModel',
    caption: '订单支付查询',

    model: [
        {
            name: order,
            alias: 'order'
        },
        {
            name: payment,
            alias: 'payment',
            onBuilder: () => 'order.order_id = payment.order_id'
        }
    ],

    columnGroups: [
        {
            caption: '订单信息',
            items: [
                { ref: order.orderId, ui: { fixed: 'left' } },
                { ref: order.orderStatus },
                { ref: order.totalAmount }
            ]
        },
        {
            caption: '支付信息',
            items: [
                { ref: payment.paymentId },
                { ref: payment.paymentMethod },
                { ref: payment.paymentAmount },
                { ref: payment.paymentTime }
            ]
        },
        {
            caption: '客户信息',
            items: [
                { ref: order.customer },
                { ref: order.customer$customerType }
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

const fs = loadTableModel('FactSalesModel');

export const queryModel = {
    name: 'SalesAnalysisQueryModel',
    caption: '销售分析',
    model: fs,

    columnGroups: [
        {
            caption: '维度',
            items: [
                { ref: fs.salesDate$year },
                { ref: fs.salesDate$month },
                { ref: fs.product$category },
                { ref: fs.customer$customerType }
            ]
        },
        {
            caption: '基础度量',
            items: [
                { ref: fs.salesQuantity },
                { ref: fs.salesAmount },
                { ref: fs.costAmount },
                { ref: fs.profitAmount }
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

- [TM 语法手册](./tm-syntax.md) - 表格模型定义
- [DSL 查询 API](../api/query-api.md) - 使用 DSL 查询数据
- [父子维度](./parent-child.md) - 层级结构维度
- [权限控制](../api/authorization.md) - 详细的权限配置
