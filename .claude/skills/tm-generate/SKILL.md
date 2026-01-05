---
name: tm-generate
description: 根据 DDL 语句、表描述或表名生成 TM（表模型）文件。当用户需要创建数据模型、生成 .tm 文件、或将数据库表转换为 Foggy Dataset Model 格式时使用。
---

# TM 生成器

根据用户输入为 Foggy Dataset Model 系统生成 TM（表模型）文件。

## 使用场景

当用户需要以下操作时使用本技能：
- 根据 DDL 语句创建 TM 文件
- 将数据库表结构转换为数据模型
- 生成事实表或维度表模型
- 创建 `.tm` 格式的表模型定义

## 输入类型

用户可能提供以下类型的输入：

1. **DDL 语句**：`CREATE TABLE` SQL 语句
2. **表描述**：表及其列的自然语言描述
3. **现有表名**：引用现有数据库表（需要推断结构）

## 输出要求

生成遵循以下结构的完整 TM 文件：

```javascript
/**
 * {模型描述}
 * @description {详细描述}
 */
import { dicts } from '../dicts.fsscript';

export const model = {
    name: '{模型名称}Model',
    caption: '{显示名称}',
    description: '{AI 使用的描述}',
    tableName: '{table_name}',
    idColumn: '{primary_key}',

    dimensions: [
        // 维度关系
    ],

    properties: [
        // 不可聚合的字段
    ],

    measures: [
        // 可聚合的数值字段（用于事实表）
    ]
};
```

## 类型映射规则

将数据库类型映射为 TM 类型：

| 数据库类型 | TM 类型 | 使用场景 |
|-----------|---------|----------|
| VARCHAR, TEXT, CHAR | `STRING` | 文本、代码、ID |
| INT, SMALLINT | `INTEGER` | 计数、小数字 |
| BIGINT | `BIGINT` / `LONG` | 大数字、代理键 |
| DECIMAL, NUMERIC, MONEY | `MONEY` | 金额、价格（使用 BigDecimal） |
| DATE | `DAY` | 仅日期（yyyy-MM-dd） |
| DATETIME, TIMESTAMP | `DATETIME` | 时间戳 |
| BOOLEAN, TINYINT(1) | `BOOL` | 是/否 标志 |

## 命名规范

- **模型名称**：PascalCase，以 `Model` 为后缀（如 `FactSalesModel`、`DimCustomerModel`）
- **属性/度量名称**：camelCase（如 `orderId`、`salesAmount`）
- **事实表**：以 `Fact` 为前缀（如 `FactSalesModel`）
- **维度表**：以 `Dim` 为前缀（如 `DimCustomerModel`）

## 维度检测规则

通过以下方式检测潜在维度：

1. 以 `_key` 或 `_id` 结尾且引用其他表的列
2. DDL 中的外键约束
3. 常见维度模式：
   - `date_key`、`time_key` → 日期维度
   - `customer_key`、`customer_id` → 客户维度
   - `product_key`、`product_id` → 产品维度
   - `store_key`、`store_id` → 门店维度

## 维度复用最佳实践

当维度被普遍复用（日期、客户、产品）时，建议使用维度构建器：

```javascript
import { buildDateDim, buildCustomerDim, buildProductDim } from '../dimensions/common-dims.fsscript';

export const model = {
    // ...
    dimensions: [
        buildDateDim({ name: 'salesDate', caption: '销售日期' }),
        buildCustomerDim(),
        // 自定义维度仍可内联定义
    ]
};
```

## 事实表 vs 维度表检测

**事实表特征**：
- 包含多个维度表的外键
- 有适合聚合的数值列（金额、数量、计数）
- 表名通常包含：`fact_`、`fct_`、`sales`、`orders`、`transactions`

**维度表特征**：
- 包含描述性属性
- 有代理键（自增）和可能的业务键
- 表名通常包含：`dim_`、`dimension_`，或为名词（customers、products、dates）

## 度量 vs 属性检测

**度量特征**（可聚合）：
- 数值类型（DECIMAL、用于数量的 INT）
- 列名包含：`amount`、`qty`、`quantity`、`count`、`total`、`sum`、`price`、`cost`、`profit`
- 默认聚合方式：金额用 `sum`，计数用 `count`，平均值用 `avg`

**属性特征**（不可聚合）：
- 字符串/文本类型
- 日期/布尔类型
- 标识符列
- 状态、类型、类别列

## 标题和描述指南

- **caption**：用户语言的简短显示名称
- **description**：供 AI 自然语言查询使用的详细说明
- 始终提供有意义的标题和描述，以便更好地与 AI 集成

## 输出示例

对于 DDL：
```sql
CREATE TABLE fact_sales (
    sales_key BIGINT PRIMARY KEY,
    date_key INT NOT NULL,
    customer_key INT NOT NULL,
    product_key INT NOT NULL,
    order_id VARCHAR(50),
    quantity INT,
    unit_price DECIMAL(10,2),
    sales_amount DECIMAL(12,2),
    cost_amount DECIMAL(12,2)
);
```

生成：
```javascript
/**
 * 销售事实表模型
 * @description 电商销售订单明细记录
 */
import { buildDateDim, buildCustomerDim, buildProductDim } from '../dimensions/common-dims.fsscript';

export const model = {
    name: 'FactSalesModel',
    caption: '销售事实表',
    description: '包含客户、产品和日期维度的销售交易明细',
    tableName: 'fact_sales',
    idColumn: 'sales_key',

    dimensions: [
        buildDateDim({ name: 'salesDate', foreignKey: 'date_key', caption: '销售日期' }),
        buildCustomerDim({ foreignKey: 'customer_key' }),
        buildProductDim({ foreignKey: 'product_key' })
    ],

    properties: [
        {
            column: 'sales_key',
            caption: '销售键',
            description: '销售记录的代理键',
            type: 'BIGINT'
        },
        {
            column: 'order_id',
            caption: '订单号',
            description: '业务订单标识符',
            type: 'STRING'
        }
    ],

    measures: [
        {
            column: 'quantity',
            caption: '数量',
            description: '销售数量',
            type: 'INTEGER',
            aggregation: 'sum'
        },
        {
            column: 'unit_price',
            caption: '单价',
            description: '单位价格',
            type: 'MONEY',
            aggregation: 'avg'
        },
        {
            column: 'sales_amount',
            name: 'salesAmount',
            caption: '销售金额',
            description: '销售总金额',
            type: 'MONEY',
            aggregation: 'sum'
        },
        {
            column: 'cost_amount',
            name: 'costAmount',
            caption: '成本金额',
            description: '成本总金额',
            type: 'MONEY',
            aggregation: 'sum'
        }
    ]
};
```

## 输出前检查清单

- [ ] 模型名称遵循命名规范（Fact*/Dim* 前缀）
- [ ] 所有列都有适当的类型
- [ ] 已识别并配置潜在维度
- [ ] 度量有聚合方法
- [ ] 所有字段都提供了标题
- [ ] 重要字段提供了描述（尤其是供 AI 使用）
- [ ] 为枚举/状态字段建议了字典引用
- [ ] 在适用时建议了维度复用

## 操作步骤

1. **分析用户输入**：确定是 DDL、表描述还是表名
2. **识别表类型**：判断是事实表还是维度表
3. **提取列信息**：列出所有列及其类型
4. **分类字段**：区分维度、属性和度量
5. **生成 TM 文件**：按照模板结构输出完整的 .tm 文件
6. **验证输出**：对照检查清单确保完整性

根据用户输入，生成相应的 TM 文件。
