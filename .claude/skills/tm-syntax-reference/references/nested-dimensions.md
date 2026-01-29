# 嵌套维度（雪花模型）

嵌套维度用于实现雪花模型，即维度表之间存在层级关系。

## 使用场景

当维度表之间存在层级关系时使用：

- **商品层级**：商品 → 品类 → 品类组
- **地理层级**：门店 → 区域 → 大区
- **组织层级**：员工 → 部门 → 事业部

## 语法结构

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
            alias: 'productCategory',   // ✅ 别名，简化 QM 访问
            tableName: 'dim_category',
            foreignKey: 'category_key', // ⚠️ 在父维度表(dim_product)上的外键
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
                    foreignKey: 'group_key',  // ⚠️ 在父维度表(dim_category)上的外键
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

## 关键字段说明

| 字段 | 说明 |
|------|------|
| `alias` | 维度别名，用于在 QM 中简化列名访问，避免路径过长 |
| `foreignKey` | **重要**：嵌套维度的 foreignKey 指向父维度表上的列 |
| `dimensions` | 子维度列表，可继续嵌套形成多层结构 |

## QM 中访问嵌套维度

### 方式1：使用别名（推荐）

```javascript
columns: [
    'product$caption',           // 一级维度
    'productCategory$caption',   // 二级维度（通过 alias）
    'categoryGroup$caption'      // 三级维度（通过 alias）
]
```

### 方式2：使用完整路径

```javascript
columns: [
    'product$caption',
    'product.category$caption',
    'product.category.group$caption'
]
```

## 生成的 SQL JOIN

系统自动生成级联 JOIN：

```sql
SELECT ...
FROM fact_sales f
LEFT JOIN dim_product p ON f.product_key = p.product_key
LEFT JOIN dim_category c ON p.category_key = c.category_key
LEFT JOIN dim_category_group g ON c.group_key = g.group_key
```

## 维度复用

嵌套维度同样支持构建器复用：

```javascript
// dimensions/product-hierarchy.fsscript
export function buildProductWithCategoryDim(options = {}) {
    const { name = 'product', foreignKey = 'product_key' } = options;

    return {
        name,
        tableName: 'dim_product',
        foreignKey,
        primaryKey: 'product_key',
        captionColumn: 'product_name',
        caption: '商品',

        properties: [
            { column: 'product_id', caption: '商品ID' },
            { column: 'brand', caption: '品牌' }
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
                    { column: 'category_id', caption: '品类ID' }
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
                            { column: 'group_id', caption: '品类组ID' }
                        ]
                    }
                ]
            }
        ]
    };
}
```

## 使用建议

### 星型模型 vs 雪花模型

| 模型 | 优点 | 缺点 | 推荐场景 |
|------|------|------|---------|
| **星型模型** | 查询性能好，结构简单 | 维度表可能冗余 | **推荐**用于大部分场景 |
| **雪花模型** | 节省存储空间 | JOIN 层级多，性能较差 | 维度层级确实需要独立维护时使用 |

### 决策规则

- 如层级属性变化频率低 → 建议使用星型模型（扁平化到一张维度表）
- 如层级关系需要独立维护 → 使用雪花模型（嵌套维度）
- 如 QM 查询频繁使用嵌套属性 → 添加 `alias` 简化访问

## 完整示例

```javascript
// FactSalesModel.tm
import { buildProductWithCategoryDim } from '../dimensions/product-hierarchy.fsscript';

export const model = {
    name: 'FactSalesModel',
    caption: '销售事实表',
    tableName: 'fact_sales',
    idColumn: 'sales_key',

    dimensions: [
        buildProductWithCategoryDim(),
        // 其他维度...
    ],

    properties: [...],
    measures: [...]
};
```
