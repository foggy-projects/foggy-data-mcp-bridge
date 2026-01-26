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
3. **现有表名**：引用现有数据库表（需要从本地服务获取结构）

## 获取数据库表结构

开发人员编写 TM/QM 时，通常已在本机启动服务。可通过以下 HTTP API 获取表结构：

### API 端点

| 端点 | 方法 | 描述 |
|------|------|------|
| `GET /dev/tables` | GET | 列出所有表 |
| `GET /dev/tables/{tableName}` | GET | 获取表详细结构 |

### 列出所有表

```bash
# 默认端口 7108（MCP 服务端口）
curl http://localhost:7108/dev/tables
```

返回示例：
```json
{
    "database": "MySQL",
    "schema": "demo_db",
    "count": 5,
    "tables": [
        {"name": "fact_order", "type": "TABLE"},
        {"name": "dim_customer", "type": "TABLE"},
        {"name": "dim_product", "type": "TABLE"}
    ]
}
```

### 获取表详细结构

```bash
curl http://localhost:7108/dev/tables/fact_order
```

返回包含：
- **columns**: 所有列信息（名称、类型、注释、是否主键/外键）
- **primary_key**: 主键信息
- **foreign_keys**: 外键信息及建议的维度名称
- **suggested_model_type**: 建议的模型类型（fact/dimension）
- **suggested_model_name**: 建议的模型名称
- **tm_template**: 自动生成的 TM 模板（可直接使用或调整）
- **sample_data**: 表中的第一行数据，例如可以用来推断日期的格式等，写到description中

### 可选参数

| 参数 | 类型 | 默认值 | 描述 |
|------|------|--------|------|
| `datasource` | string | defaultDataSource | 数据源 Bean 名称 |
| `schema` | string | 当前连接 schema | 指定数据库 schema |
| `includeIndexes` | boolean | false | 是否包含索引信息 |
| `includeForeignKeys` | boolean | true | 是否包含外键信息 |

### 使用 WebFetch 工具获取

当用户提供表名时，使用 WebFetch 工具获取表结构：

```
WebFetch URL: http://localhost:7108/dev/tables/{tableName}
Prompt: 提取表结构信息，包括列名、类型、主键、外键和 TM 模板
```

## 输出要求

### 文件存放路径

**默认路径**（用户未指定时）：
```
src/main/resources/foggy/templates/model/{模型名称}Model.tm
```

**目录结构说明**：
```
src/main/resources/foggy/templates/
├── model/                    # TM 表模型目录
│   ├── Fact{Name}Model.tm   # 事实表模型
│   ├── Dim{Name}Model.tm    # 维度表模型
│   └── mongo/               # MongoDB 模型（单独子目录）
├── query/                   # QM 查询模型目录
├── dimensions/              # 可选：维度构建器
│   └── common-dims.fsscript
└── dicts.fsscript          # 字典定义
```

如果用户指定了其他路径，按用户指定的路径生成。

### 文件内容结构

```javascript
/**
 * {模型描述}
 * @description {详细描述}
 */

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

**文件名**：`{模型名称}Model.tm`，与 `model.name` 相同。

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

## TM语法规范
如果需要获取更多的tm语法规范，请参考[Foggy TM 语法规范](https://foggy-projects.github.io/foggy-data-mcp-bridge/downloads/tm-syntax.md)

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

绝大部分维度都是需要复用的，因此判断是维度时，需要构建维度构建器：

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
2. **获取表结构**（如果用户提供表名）：
   - 使用 WebFetch 工具访问 `http://localhost:7108/dev/tables/{tableName}`
   - 从返回的 JSON 中提取列信息、主键、外键等
   - 参考 `tm_template` 字段作为生成基础
   - 在windows系统中不要使用curl -s 否则控制台会卡住(要求输入url)
3. **识别表类型**：判断是事实表还是维度表
4. **分类字段**：区分维度、属性和度量
5. **生成 TM 文件**：按照模板结构输出完整的 .tm 文件
6. **验证输出**：对照检查清单确保完整性

### 示例：用户提供表名

```
用户：为 fact_order 表生成 TM 文件

1. 使用 WebFetch 获取表结构：
   URL: http://localhost:7108/dev/tables/fact_order

2. 从返回结果提取信息：
   - columns: 所有列及类型
   - foreign_keys: 外键关系 → 生成 dimensions
   - tm_template: 参考模板

3. 优化生成的 TM：
   - 添加有意义的 caption 和 description
   - 调整维度命名
   - 补充字典引用
```

根据用户输入，生成相应的 TM 文件。
