# 维度复用最佳实践

绝大部分维度都需要跨多个事实表复用，通过维度构建器实现统一维护。

## 核心理念

同一维度表（如日期、客户、产品）被多个事实表引用时，应：

1. **集中定义**：在独立的 `.fsscript` 文件中定义维度构建器
2. **参数化配置**：支持不同场景的定制需求
3. **统一维护**：修改维度定义只需改一处，全局生效

## 推荐项目结构

```
templates/
├── dimensions/                    # 维度构建器目录
│   ├── common-dims.fsscript       # 通用维度（日期、客户、商品等）
│   ├── hierarchy-dims.fsscript    # 父子维度（组织、区域等）
│   └── product-hierarchy.fsscript # 商品雪花维度
├── model/                         # TM 模型目录
│   ├── FactSalesModel.tm
│   ├── FactOrderModel.tm
│   └── ...
├── query/                         # QM 查询模型目录
└── dicts.fsscript                 # 字典定义
```

## 使用维度构建器（推荐方式）

### 1. 导入构建器

```javascript
// model/FactSalesModel.tm
import {
    buildDateDim,
    buildCustomerDim,
    buildProductDim,
    buildStoreDim
} from '../dimensions/common-dims.fsscript';
```

### 2. 使用默认配置

```javascript
export const model = {
    name: 'FactSalesModel',
    caption: '销售事实表',
    tableName: 'fact_sales',
    idColumn: 'sales_key',

    dimensions: [
        buildCustomerDim(),  // ✅ 使用默认配置
        buildProductDim(),
        buildStoreDim()
    ],

    properties: [...],
    measures: [...]
};
```

### 3. 自定义配置

```javascript
dimensions: [
    // 自定义维度名称和描述
    buildDateDim({
        name: 'salesDate',
        caption: '销售日期',
        description: '订单发生的日期'
    }),

    // 覆盖外键字段名
    buildCustomerDim({
        foreignKey: 'buyer_key',
        caption: '购买客户'
    })
]
```

### 4. 混合使用：构建器 + 内联维度

```javascript
dimensions: [
    buildDateDim({ name: 'salesDate', caption: '销售日期' }),
    buildCustomerDim(),
    buildProductDim(),

    // 特殊维度仍可内联定义
    {
        name: 'channel',
        tableName: 'dim_channel',
        foreignKey: 'channel_key',
        primaryKey: 'channel_key',
        captionColumn: 'channel_name',
        caption: '渠道',
        properties: [
            { column: 'channel_id', caption: '渠道ID' },
            { column: 'channel_type', caption: '渠道类型' }
        ]
    }
]
```

## 创建维度构建器

### 基础维度构建器

```javascript
// dimensions/common-dims.fsscript

/**
 * 构建日期维度
 * @param {object} options - 配置选项
 * @param {string} options.name - 维度名称，默认 'salesDate'
 * @param {string} options.foreignKey - 外键列名，默认 'date_key'
 * @param {string} options.caption - 显示名称，默认 '日期'
 * @returns {object} 维度配置对象
 */
export function buildDateDim(options = {}) {
    const {
        name = 'salesDate',
        foreignKey = 'date_key',
        caption = '日期',
        description = '业务发生的日期'
    } = options;

    return {
        name,
        tableName: 'dim_date',
        foreignKey,
        primaryKey: 'date_key',
        captionColumn: 'full_date',
        caption,
        description,
        keyDescription: '日期主键，格式yyyyMMdd，如20240101',
        type: 'DATETIME',

        properties: [
            { column: 'year', caption: '年', type: 'INTEGER', description: '年份' },
            { column: 'quarter', caption: '季度', type: 'INTEGER', description: '季度（1-4）' },
            { column: 'month', caption: '月', type: 'INTEGER', description: '月份（1-12）' },
            { column: 'month_name', caption: '月份名称', type: 'STRING' },
            { column: 'week_of_year', caption: '年度周数', type: 'INTEGER' },
            { column: 'day_of_week', caption: '周几', type: 'INTEGER' },
            { column: 'is_weekend', caption: '是否周末', type: 'BOOL' },
            { column: 'is_holiday', caption: '是否节假日', type: 'BOOL' }
        ]
    };
}

/**
 * 构建客户维度
 */
export function buildCustomerDim(options = {}) {
    const {
        name = 'customer',
        foreignKey = 'customer_key',
        caption = '客户',
        description = '客户信息'
    } = options;

    return {
        name,
        tableName: 'dim_customer',
        foreignKey,
        primaryKey: 'customer_key',
        captionColumn: 'customer_name',
        caption,
        description,
        keyDescription: '客户代理键，自增整数',

        properties: [
            { column: 'customer_id', caption: '客户ID', description: '客户唯一标识' },
            { column: 'customer_type', caption: '客户类型', description: '个人/企业' },
            { column: 'gender', caption: '性别' },
            { column: 'age_group', caption: '年龄段' },
            { column: 'province', caption: '省份' },
            { column: 'city', caption: '城市' },
            { column: 'member_level', caption: '会员等级' }
        ]
    };
}

/**
 * 构建商品维度
 */
export function buildProductDim(options = {}) {
    const {
        name = 'product',
        foreignKey = 'product_key',
        caption = '商品',
        description = '商品信息'
    } = options;

    return {
        name,
        tableName: 'dim_product',
        foreignKey,
        primaryKey: 'product_key',
        captionColumn: 'product_name',
        caption,
        description,
        keyDescription: '商品代理键，自增整数',

        properties: [
            { column: 'product_id', caption: '商品ID' },
            { column: 'category_name', caption: '一级品类名称' },
            { column: 'sub_category_name', caption: '二级品类名称' },
            { column: 'brand', caption: '品牌' },
            { column: 'unit_price', caption: '商品售价', type: 'MONEY' },
            { column: 'unit_cost', caption: '商品成本', type: 'MONEY' }
        ]
    };
}

/**
 * 构建门店维度
 */
export function buildStoreDim(options = {}) {
    const {
        name = 'store',
        foreignKey = 'store_key',
        caption = '门店',
        description = '门店信息'
    } = options;

    return {
        name,
        tableName: 'dim_store',
        foreignKey,
        primaryKey: 'store_key',
        captionColumn: 'store_name',
        caption,
        description,

        properties: [
            { column: 'store_id', caption: '门店ID' },
            { column: 'store_type', caption: '门店类型' },
            { column: 'province', caption: '省份' },
            { column: 'city', caption: '城市' }
        ]
    };
}
```

## 高级技巧：属性扩展与覆盖

### 展开运算符扩展

使用 `...` 展开运算符扩展维度属性：

```javascript
import { buildCustomerDim } from '../dimensions/common-dims.fsscript';

export const model = {
    name: 'FactVIPSalesModel',
    caption: 'VIP销售事实表',
    tableName: 'fact_vip_sales',

    dimensions: [
        // 扩展客户维度，添加 VIP 特有属性
        {
            ...buildCustomerDim({ caption: 'VIP客户' }),
            properties: [
                ...buildCustomerDim().properties,  // ✅ 保留原有属性
                { column: 'vip_level', caption: 'VIP等级' },
                { column: 'vip_points', caption: '积分余额', type: 'INTEGER' },
                { column: 'vip_expire_date', caption: '会员到期日', type: 'DAY' }
            ]
        }
    ]
};
```

### 覆盖部分配置

```javascript
dimensions: [
    {
        ...buildCustomerDim(),
        caption: '购买客户',           // ✅ 覆盖 caption
        description: '实际购买商品的客户'  // ✅ 覆盖 description
    }
]
```

## 多日期维度场景

订单模型可能需要多个日期维度：

```javascript
// model/FactOrderModel.tm
import { buildDateDim } from '../dimensions/common-dims.fsscript';

export const model = {
    name: 'FactOrderModel',
    caption: '订单事实表',
    tableName: 'fact_order',

    dimensions: [
        // 下单日期
        buildDateDim({
            name: 'orderDate',
            foreignKey: 'order_date_key',
            caption: '下单日期',
            description: '客户下单的日期'
        }),

        // 发货日期
        buildDateDim({
            name: 'shipDate',
            foreignKey: 'ship_date_key',
            caption: '发货日期',
            description: '订单发货的日期'
        }),

        // 签收日期
        buildDateDim({
            name: 'receiveDate',
            foreignKey: 'receive_date_key',
            caption: '签收日期',
            description: '客户签收的日期'
        })
    ]
};
```

## 嵌套维度复用

创建包含子维度的完整层级：

```javascript
// dimensions/product-hierarchy.fsscript

/**
 * 构建带品类层级的商品维度（雪花模型）
 */
export function buildProductWithCategoryDim(options = {}) {
    const {
        name = 'product',
        foreignKey = 'product_key',
        caption = '商品'
    } = options;

    return {
        name,
        tableName: 'dim_product',
        foreignKey,
        primaryKey: 'product_key',
        captionColumn: 'product_name',
        caption,

        properties: [
            { column: 'product_id', caption: '商品ID' },
            { column: 'brand', caption: '品牌' },
            { column: 'unit_price', caption: '售价', type: 'MONEY' }
        ],

        // 嵌套品类维度
        dimensions: [
            {
                name: 'category',
                alias: 'productCategory',
                tableName: 'dim_category',
                foreignKey: 'category_key',
                primaryKey: 'category_key',
                captionColumn: 'category_name',
                caption: '品类',

                properties: [
                    { column: 'category_id', caption: '品类ID' },
                    { column: 'category_level', caption: '品类层级' }
                ],

                // 继续嵌套品类组
                dimensions: [
                    {
                        name: 'group',
                        alias: 'categoryGroup',
                        tableName: 'dim_category_group',
                        foreignKey: 'group_key',
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
    };
}
```

使用嵌套维度构建器：

```javascript
import { buildProductWithCategoryDim } from '../dimensions/product-hierarchy.fsscript';

export const model = {
    name: 'FactSalesModel',
    // ...
    dimensions: [
        buildProductWithCategoryDim()
    ]
};
```

## 父子维度复用

详见 `parent-child-dimensions.md` 中的构建器示例。

## 维度复用最佳实践总结

| 实践 | 说明 |
|------|------|
| **函数封装** | 将重复使用的维度定义封装为工厂函数 |
| **参数化配置** | 通过参数支持不同场景的定制需求 |
| **默认值** | 为常用参数提供合理的默认值 |
| **模块化组织** | 按维度类型组织到不同的 `.fsscript` 文件 |
| **属性扩展** | 使用展开运算符 `...` 扩展或覆盖属性 |
| **统一维护** | 修改维度定义时只需改一处，全局生效 |
| **JSDoc 注释** | 为构建器函数添加文档注释，便于使用 |

## 代码质量

### 推荐做法 ✅

```javascript
// ✅ 清晰的参数命名
export function buildDateDim(options = {}) {
    const { name = 'salesDate', foreignKey = 'date_key' } = options;
    // ...
}

// ✅ JSDoc 注释
/**
 * 构建客户维度
 * @param {object} options - 配置选项
 * @param {string} options.name - 维度名称，默认 'customer'
 */
export function buildCustomerDim(options = {}) { ... }

// ✅ 合理的默认值
const { caption = '客户', description = '客户信息' } = options;
```

### 避免做法 ❌

```javascript
// ❌ 硬编码，无法自定义
export function buildDateDim() {
    return {
        name: 'salesDate',  // 无法修改
        foreignKey: 'date_key',
        // ...
    };
}

// ❌ 参数过多，难以使用
export function buildDateDim(name, foreignKey, caption, description, keyDescription) {
    // 调用时：buildDateDim('salesDate', 'date_key', '销售日期', '...', '...')
}
```

## 维护建议

1. **集中管理**：所有维度构建器放在 `dimensions/` 目录
2. **版本控制**：维度结构变更时，评估影响范围
3. **向后兼容**：添加新属性时使用默认值，避免破坏现有模型
4. **文档完善**：为每个构建器添加清晰的 JSDoc 注释
