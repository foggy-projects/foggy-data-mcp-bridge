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
- 创建 `.tm` 格式的表模型定义

**注意**：MongoDB 集合请使用 `tm-generate-mongo` 技能。

## 输入类型

用户可能提供以下类型的输入：

1. **DDL 语句**：`CREATE TABLE` SQL 语句
2. **表描述**：表及其列的自然语言描述
3. **现有表名**：引用现有数据库表（需要从本地服务获取结构）

## 获取数据库表结构

当用户提供表名时，需要获取数据库表结构。有两种方法：

### 方法 1：使用 mysql-docker-client 技能（推荐）

使用个人技能 `mysql-docker-client` 通过 SQL 查询获取表结构。需要用户提供数据库连接信息。

#### 步骤 1：询问用户连接信息

使用 `AskUserQuestion` 工具询问用户数据库连接参数：
- host（主机地址）
- port（端口，默认 3306）
- user（用户名）
- password（密码）
- database（数据库名）

#### 步骤 2：获取表的列信息

使用 `Skill` 工具调用 `mysql-docker-client`，执行以下 SQL：

```sql
SELECT
    COLUMN_NAME,
    DATA_TYPE,
    COLUMN_TYPE,
    IS_NULLABLE,
    COLUMN_KEY,
    COLUMN_DEFAULT,
    EXTRA,
    COLUMN_COMMENT
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = '{database}'
    AND TABLE_NAME = '{table_name}'
ORDER BY ORDINAL_POSITION;
```

#### 调用 mysql-docker-client 技能

使用 `Bash` 工具直接调用脚本：

```bash
python C:\Users\oldse\.claude\skills\mysql-docker-client\scripts\execute_sql.py \
  --host {host} \
  --port {port} \
  --user {user} \
  --password {password} \
  --database {database} \
  --sql "SELECT COLUMN_NAME, DATA_TYPE, COLUMN_TYPE, IS_NULLABLE, COLUMN_KEY, COLUMN_DEFAULT, EXTRA, COLUMN_COMMENT FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = '{database}' AND TABLE_NAME = '{table_name}' ORDER BY ORDINAL_POSITION"
```

**重要提示**：
- 将 `{host}`、`{port}` 等替换为用户提供的实际值
- 将 `{database}` 和 `{table_name}` 替换为实际的数据库名和表名
- SQL 语句需要用双引号包裹
- 在 Windows 系统中，Python 路径使用反斜杠 `\`

#### 步骤 3：获取外键信息

```bash
python C:\Users\oldse\.claude\skills\mysql-docker-client\scripts\execute_sql.py \
  --host {host} \
  --port {port} \
  --user {user} \
  --password {password} \
  --database {database} \
  --sql "SELECT CONSTRAINT_NAME, COLUMN_NAME, REFERENCED_TABLE_NAME, REFERENCED_COLUMN_NAME FROM information_schema.KEY_COLUMN_USAGE WHERE TABLE_SCHEMA = '{database}' AND TABLE_NAME = '{table_name}' AND REFERENCED_TABLE_NAME IS NOT NULL"
```

#### 步骤 4：获取主键信息

主键信息已包含在步骤 2 的列信息中（`COLUMN_KEY = 'PRI'`），无需单独查询。

#### 步骤 5：获取示例数据（可选）

获取第一行数据，用于推断日期格式等信息：

```bash
python C:\Users\oldse\.claude\skills\mysql-docker-client\scripts\execute_sql.py \
  --host {host} \
  --port {port} \
  --user {user} \
  --password {password} \
  --database {database} \
  --sql "SELECT * FROM {table_name} LIMIT 1"
```

示例数据可用于推断日期格式等，写入 description 中。

### 方法 2：使用本地 HTTP API（备选）

如果用户已在本机启动 MCP 服务（端口 7108），可使用 WebFetch 工具访问：

```
WebFetch URL: http://localhost:7108/dev/tables/{tableName}
Prompt: 提取表结构信息，包括列名、类型、主键、外键和 TM 模板
```

返回包含：
- **columns**: 所有列信息（名称、类型、注释、是否主键/外键）
- **primary_key**: 主键信息
- **foreign_keys**: 外键信息及建议的维度名称
- **suggested_model_type**: 建议的模型类型（fact/dimension）
- **suggested_model_name**: 建议的模型名称
- **tm_template**: 自动生成的 TM 模板（可直接使用或调整）
- **sample_data**: 表中的第一行数据

**注意**：此方法要求本地服务已启动，否则应使用方法 1。

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
│   └── Dim{Name}Model.tm    # 维度表模型
├── query/                   # QM 查询模型目录
├── dimensions/              # 可选：维度构建器
│   └── common-dims.fsscript
└── dicts.fsscript          # 字典定义
```

**注意**：MongoDB 模型请使用 `tm-generate-mongo` 技能生成。

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

## ⚠️ Name 字段简化规则（重要）

**核心原则**：name 和 column 一致时，省略 name 字段。

### JDBC 数据源（MySQL、PostgreSQL等）

**自动转换规则**：
- 数据库字段名（蛇形命名）→ 自动转为驼峰命名
- `order_count` → `orderCount`
- `customer_name` → `customerName`
- `created_at` → `createdAt`

**错误做法** ❌：
```javascript
properties: [
    {
        column: 'order_count',
        name: 'orderCount',     // ❌ 冗余，系统自动转换
        caption: '订单数',
        type: 'INTEGER'
    }
]
```

**正确做法** ✅：
```javascript
properties: [
    {
        column: 'order_count',   // ✅ 省略 name，自动转为 orderCount
        caption: '订单数',
        type: 'INTEGER'
    }
]
```

**何时需要 name**：
```javascript
properties: [
    {
        column: 'order_count',
        name: 'totalOrders',     // ✅ 需要 name，因为与 column 不一致
        caption: '订单总数',
        type: 'INTEGER'
    }
]
```

### 决策规则

**省略 name 的场景**（推荐）：
- JDBC 蛇形命名自动转驼峰：`order_count` → 省略 name，自动转为 `orderCount`

**必须指定 name 的场景**：
1. 需要自定义名称：`column: 'cnt'` → `name: 'orderCount'`
2. 用户明确要求特定名称

**总结**：除非用户明确指定或需要自定义映射，否则 **name 与 column 保持一致时省略 name**。

**注意**：MongoDB 模型的 name 字段规则请参考 `tm-generate-mongo` 技能。

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

## ⚠️ Measures 字段设计原则（重要）

**Foggy Dataset Model 的核心理念**：同一字段支持明细查询和聚合查询两种模式。

### 错误做法（传统数仓建模）❌
```javascript
measures: [
    { column: 'routeCount', name: 'totalRouteCount', caption: '总路线数', type: 'INTEGER', aggregation: 'sum' },
    { column: 'routeCount', name: 'avgRouteCount', caption: '平均路线数', type: 'INTEGER', aggregation: 'avg' },
    { column: 'routeCount', name: 'maxRouteCount', caption: '最大路线数', type: 'INTEGER', aggregation: 'max' }
]
```

### 正确做法（Foggy Dataset Model）✅
```javascript
measures: [
    { column: 'routeCount', name: 'routeCount', caption: '路线数', type: 'INTEGER', aggregation: 'sum' }
]
```

**关键原则**：
1. **不要为同一字段创建多个聚合度量**（如 totalXxx、avgXxx、maxXxx）
2. **name 和 column 保持一致**，不添加 total/avg/max 前缀
3. **aggregation 字段仅作为默认聚合方式**，查询时可动态覆盖

### 为什么这样设计？

Foggy Dataset Model 兼顾明细和分析查询：
- **同一字段支持两种模式**：明细查询返回原始值，聚合查询应用聚合函数
- **查询时动态指定**：可使用 sum/avg/max/min/count 等聚合函数
- **模型保持简洁**：避免为每种聚合方式预定义字段

**查询语法详见**：`dsl-syntax-guide` 技能

### 常见度量示例

| 业务场景 | 默认聚合 | 说明 |
|---------|---------|------|
| 金额（salesAmount, totalPrice） | sum | 求和 |
| 数量（quantity, count） | sum | 求和 |
| 百分比（batteryLevel, progress） | avg | 平均值 |
| 速度/频率（speed, frequency） | avg | 平均值 |
| 距离/时长（distance, duration） | sum | 求和 |

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
            caption: '销售金额',
            description: '销售总金额',
            type: 'MONEY',
            aggregation: 'sum'
        },
        {
            column: 'cost_amount',
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

   **优先使用 mysql-docker-client 技能：**
   - 使用 `AskUserQuestion` 询问数据库连接信息（host、port、user、password、database）
   - 使用 `Skill` 工具调用 `mysql-docker-client`
   - 执行 SQL 查询获取列信息、外键信息
   - 可选：获取示例数据用于推断格式

   **备选使用 HTTP API：**
   - 如果用户已启动本地服务，可使用 `WebFetch` 访问 `http://localhost:7108/dev/tables/{tableName}`
   - 从返回的 JSON 中提取列信息、主键、外键等
   - 参考 `tm_template` 字段作为生成基础

3. **识别表类型**：根据表名和字段特征判断是事实表还是维度表

4. **分类字段**：区分维度、属性和度量
   - 外键 → dimensions
   - 数值类型且名称包含 amount/qty/count → measures
   - 其他字段 → properties

5. **生成 TM 文件**：按照模板结构输出完整的 .tm 文件
   - 遵循命名规范（Fact*/Dim* 前缀）
   - 添加有意义的 caption 和 description
   - 使用维度构建器（如有可复用维度）
   - 为度量添加聚合方法

6. **验证输出**：对照检查清单确保完整性

### 示例：使用 mysql-docker-client 获取表结构

```
用户：为 fact_order 表生成 TM 文件

1. 询问数据库连接信息：
   - host: localhost
   - port: 3306
   - user: root
   - password: ****
   - database: ecommerce

2. 使用 mysql-docker-client 执行 SQL 查询：
   - 获取列信息（字段名、类型、注释、主键）
   - 获取外键信息（关联的表和字段）
   - 获取示例数据（第一行）

3. 分析结果：
   - 表名包含 "fact_" → 事实表
   - customer_key, product_key 外键 → 生成维度
   - order_amount, quantity → 度量字段

4. 生成 TM 文件：
   - 模型名称：FactOrderModel
   - 使用维度构建器：buildCustomerDim、buildProductDim
   - 度量添加聚合方法：sum

5. 保存到默认路径：
   src/main/resources/foggy/templates/model/FactOrderModel.tm
```

根据用户输入，生成相应的 TM 文件。
