---
name: tm-generate
description: 根据 DDL 语句、表描述或表名生成 TM（表模型）文件。适用于 JDBC 数据源（MySQL、PostgreSQL 等关系型数据库）。MongoDB 模型请使用 tm-generate-mongo 技能。
---

# TM 生成器（JDBC 数据源）

根据用户输入为 Foggy Dataset Model 系统生成 JDBC 类型的 TM（表模型）文件。

## 使用场景

当用户需要为 **JDBC 数据源**（MySQL、PostgreSQL、Oracle 等关系型数据库）生成 TM 模型时使用：
- 根据 DDL 语句创建 TM 文件
- 将关系型数据库表结构转换为数据模型
- 生成事实表或维度表模型

**注意**：MongoDB 集合请使用 `tm-generate-mongo` 技能。

## 输入类型

用户可能提供以下类型的输入：

1. **DDL 语句**：`CREATE TABLE` SQL 语句
2. **表描述**：表及其列的自然语言描述
3. **现有表名**：引用现有数据库表（需要从本地服务获取结构）

## 执行流程

### 1. 获取表结构

#### 方法 1：使用 mysql-docker-client 技能（推荐）

当用户提供表名时，使用 `mysql-docker-client` 技能获取表结构：

**步骤**：
1. 使用 `AskUserQuestion` 询问数据库连接信息（host、port、user、password、database）
2. 使用 `Bash` 工具调用脚本获取列信息、外键信息、示例数据

**SQL 查询**：
```sql
-- 获取列信息
SELECT COLUMN_NAME, DATA_TYPE, COLUMN_TYPE, IS_NULLABLE, COLUMN_KEY, COLUMN_DEFAULT, EXTRA, COLUMN_COMMENT
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = '{database}' AND TABLE_NAME = '{table_name}'
ORDER BY ORDINAL_POSITION;

-- 获取外键信息
SELECT CONSTRAINT_NAME, COLUMN_NAME, REFERENCED_TABLE_NAME, REFERENCED_COLUMN_NAME
FROM information_schema.KEY_COLUMN_USAGE
WHERE TABLE_SCHEMA = '{database}' AND TABLE_NAME = '{table_name}' AND REFERENCED_TABLE_NAME IS NOT NULL;

-- 获取示例数据（可选）
SELECT * FROM {table_name} LIMIT 1;
```

**调用示例**：
```bash
python C:\Users\oldse\.claude\skills\mysql-docker-client\scripts\execute_sql.py \
  --host {host} \
  --port {port} \
  --user {user} \
  --password {password} \
  --database {database} \
  --sql "SELECT ..."
```

#### 方法 2：使用本地 HTTP API（备选）

如果用户已在本机启动 MCP 服务（端口 7108），可使用 `WebFetch` 访问：

```
URL: http://localhost:7108/dev/tables/{tableName}
Prompt: 提取表结构信息，包括列名、类型、主键、外键和 TM 模板
```

### 2. 应用 TM 语法规则

使用 `tm-syntax-reference` 技能中的规则：

- **类型映射**：`VARCHAR` → `STRING`、`DECIMAL` → `MONEY`、`DATE` → `DAY`
- **Name 字段简化**：蛇形命名自动转驼峰（`order_count` → `orderCount`），省略 name
- **Measures 设计**：不为同一字段创建多个聚合版本

**详细规则见**：`tm-syntax-reference` 技能

### 3. 识别表类型

**事实表特征**：
- 包含多个维度表的外键
- 有适合聚合的数值列（金额、数量）
- 表名通常包含：`fact_`、`fct_`、`sales`、`orders`、`transactions`

**维度表特征**：
- 包含描述性属性
- 有代理键（自增）和业务键
- 表名通常包含：`dim_`、`dimension_`，或为名词（customers、products）

### 4. 分类字段

- **外键** → `dimensions`（使用维度构建器）
- **数值类型且名称包含 amount/qty/count** → `measures`
- **其他字段** → `properties`

### 5. 生成 TM 文件

**文件路径**（用户未指定时）：
```
src/main/resources/foggy/templates/model/{模型名称}Model.tm
```

**文件结构**：
```javascript
/**
 * {模型描述}
 * @description {详细描述}
 */
import { buildDateDim, buildCustomerDim, buildProductDim } from '../dimensions/common-dims.fsscript';

export const model = {
    name: 'FactSalesModel',      // 遵循命名规范
    caption: '销售事实表',
    description: '{AI使用的描述}',
    tableName: 'fact_sales',
    idColumn: 'sales_key',
    // type: 'jdbc' 可省略（默认）

    dimensions: [
        buildDateDim({ name: 'salesDate', caption: '销售日期' }),
        buildCustomerDim(),
        buildProductDim()
    ],

    properties: [
        // 不可聚合字段
    ],

    measures: [
        // 可聚合数值字段
    ]
};
```

### 6. 验证输出

对照检查清单：
- [ ] 模型名称遵循命名规范（Fact*/Dim* 前缀）
- [ ] 所有字段都有 caption
- [ ] 类型映射正确（金额用 MONEY，日期用 DAY/DATETIME）
- [ ] Name 字段按规则省略（避免冗余）
- [ ] Measures 不为同一字段创建多个聚合版本
- [ ] 已识别并配置维度
- [ ] 使用维度构建器（如有可复用维度）
- [ ] 枚举字段建议添加 dictRef
- [ ] 重要字段提供 description（供 AI 使用）

## JDBC 专属规则

### 1. 支持 Dimensions（维度关联）

```javascript
dimensions: [
    {
        name: 'customer',
        tableName: 'dim_customer',
        foreignKey: 'customer_key',      // 本表外键
        primaryKey: 'customer_key',      // 维度表主键
        captionColumn: 'customer_name',
        caption: '客户',
        properties: [
            { column: 'customer_id', caption: '客户ID' },
            { column: 'province', caption: '省份' }
        ]
    }
]
```

### 2. Name 字段自动转驼峰

JDBC 数据库使用蛇形命名，系统自动转为驼峰：

```javascript
properties: [
    {
        column: 'order_count',   // ✅ 省略 name，自动转为 orderCount
        caption: '订单数',
        type: 'INTEGER'
    }
]
```

### 3. 推荐使用维度构建器

```javascript
import { buildDateDim, buildCustomerDim, buildProductDim } from '../dimensions/common-dims.fsscript';

dimensions: [
    buildDateDim({ name: 'salesDate', caption: '销售日期' }),
    buildCustomerDim(),
    buildProductDim()
]
```

## 维度检测规则

通过以下方式检测潜在维度：

1. 以 `_key` 或 `_id` 结尾且引用其他表的列
2. DDL 中的外键约束
3. 常见维度模式：
   - `date_key`、`time_key` → 日期维度
   - `customer_key`、`customer_id` → 客户维度
   - `product_key`、`product_id` → 产品维度
   - `store_key`、`store_id` → 门店维度

## 高级特性

当遇到以下场景时，参考 `tm-syntax-reference` 技能的扩展文档：

| 场景 | 扩展文档 |
|------|---------|
| 雪花模型（商品→品类→品类组） | `references/nested-dimensions.md` |
| 组织架构/树形结构 | `references/parent-child-dimensions.md` |
| JSON 提取/复杂计算 | `references/calculated-fields.md` |
| 创建维度构建器 | `references/dimension-reuse.md` |

## 输出示例

对于 DDL：
```sql
CREATE TABLE fact_sales (
    sales_key BIGINT PRIMARY KEY,
    date_key INT NOT NULL,
    customer_key INT NOT NULL,
    product_key INT NOT NULL,
    quantity INT,
    sales_amount DECIMAL(12,2)
);
```

生成：
```javascript
import { buildDateDim, buildCustomerDim, buildProductDim } from '../dimensions/common-dims.fsscript';

export const model = {
    name: 'FactSalesModel',
    caption: '销售事实表',
    tableName: 'fact_sales',
    idColumn: 'sales_key',

    dimensions: [
        buildDateDim({ name: 'salesDate', foreignKey: 'date_key' }),
        buildCustomerDim({ foreignKey: 'customer_key' }),
        buildProductDim({ foreignKey: 'product_key' })
    ],

    properties: [
        {
            column: 'sales_key',
            caption: '销售键',
            type: 'BIGINT'
        }
    ],

    measures: [
        {
            column: 'quantity',
            caption: '数量',
            type: 'INTEGER',
            aggregation: 'sum'
        },
        {
            column: 'sales_amount',
            caption: '销售金额',
            type: 'MONEY',
            aggregation: 'sum'
        }
    ]
};
```

## 决策规则

- 如用户提供 DDL → 直接解析生成
- 如用户提供表名 → 优先使用 mysql-docker-client 获取结构
- 如检测到外键 → 生成 dimensions（使用构建器）
- 如表名含 fact_/fct_ → 事实表（FactXxxModel）
- 如表名含 dim_或为名词 → 维度表（DimXxxModel）
- 如需嵌套维度/父子维度 → 使用 `Read` 工具读取 `tm-syntax-reference` 扩展文档

## 参考文档

详细语法规则、类型映射、高级特性请参考：
- **核心语法**：`tm-syntax-reference` 技能
- **完整手册**：[TM 语法手册](https://foggy-projects.github.io/foggy-data-mcp-bridge/zh/dataset-model/tm-qm/tm-syntax.html)
