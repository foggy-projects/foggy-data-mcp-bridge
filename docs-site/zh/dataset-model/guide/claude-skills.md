# Claude Skills 使用指南

本指南介绍如何使用 Claude Code 的 Slash Commands（斜杠命令）快速生成和配置 TM/QM 模型文件。

## 什么是 Claude Skills？

Claude Skills 是预定义的提示词模板，以 Slash Command 的形式集成在 Claude Code 中。当您输入 `/命令名` 时，Claude 会加载对应的专业知识和规则，帮助您更高效地完成特定任务。

本项目提供了两个 TM/QM 相关的 Skills：

| 命令 | 功能 |
|------|------|
| `/tm-generate` | 根据 DDL 或表描述生成完整的 TM 文件 |
| `/tm-dimension` | 配置维度关系（基本、嵌套、父子层次） |

## 前置条件

1. 安装 [Claude Code](https://claude.ai/code)
2. 在项目根目录运行 Claude Code
3. 确保 `.claude/commands/` 目录下存在对应的 skill 文件

## `/tm-generate` - TM 文件生成

### 功能概述

根据用户提供的信息自动生成 TM（Table Model）文件，支持：
- DDL 语句解析
- 自然语言表描述
- 数据库表名（结合 `dataset.inspect_table` 工具）

### 使用方法

在 Claude Code 中输入：

```
/tm-generate

请为以下表生成 TM 文件：

CREATE TABLE fact_sales (
    sales_key BIGINT PRIMARY KEY,
    date_key INT NOT NULL,
    customer_key INT NOT NULL,
    product_key INT NOT NULL,
    order_id VARCHAR(50),
    quantity INT,
    unit_price DECIMAL(10,2),
    sales_amount DECIMAL(12,2)
);
```

### 智能推断功能

`/tm-generate` 会自动：

1. **类型映射**：将数据库类型转换为 TM 类型
   - `BIGINT` → `BIGINT`
   - `DECIMAL` → `MONEY`
   - `VARCHAR` → `STRING`
   - `DATE` → `DAY`

2. **角色检测**：
   - 外键列 → 维度（dimension）
   - 金额、数量列 → 度量（measure）
   - 其他列 → 属性（property）

3. **维度复用建议**：
   - 识别常用维度（日期、客户、产品）
   - 建议使用维度构建器函数

### 输出示例

```javascript
import { buildDateDim, buildCustomerDim, buildProductDim }
    from '../dimensions/common-dims.fsscript';

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
        { column: 'sales_key', caption: '销售键', type: 'BIGINT' },
        { column: 'order_id', caption: '订单号', type: 'STRING' }
    ],

    measures: [
        { column: 'quantity', caption: '数量', type: 'INTEGER', aggregation: 'sum' },
        { column: 'unit_price', caption: '单价', type: 'MONEY', aggregation: 'avg' },
        { column: 'sales_amount', caption: '销售金额', type: 'MONEY', aggregation: 'sum' }
    ]
};
```

### 结合数据库检查工具

如果您的数据库表已存在，可以结合 `dataset.inspect_table` 工具：

```
/tm-generate

请检查数据库中的 fact_sales 表并生成 TM 文件。
```

Claude 会先调用 `dataset.inspect_table` 获取表结构，然后生成 TM 文件。

## `/tm-dimension` - 维度配置助手

### 功能概述

帮助配置各种类型的维度关系：
- **基本维度**：事实表与维度表的直接关联
- **嵌套维度**（雪花模型）：多级维度层次结构
- **父子维度**（层次结构）：使用闭包表的树形结构

### 使用方法

```
/tm-dimension

我需要配置一个产品维度，产品关联到品类，品类关联到品类组。
```

### 基本维度配置

```javascript
{
    name: 'customer',
    caption: '客户',
    tableName: 'dim_customer',
    foreignKey: 'customer_key',    // 事实表中的外键
    primaryKey: 'customer_key',    // 维度表中的主键
    captionColumn: 'customer_name',

    properties: [
        { column: 'customer_id', caption: '客户编号' },
        { column: 'customer_type', caption: '客户类型' }
    ]
}
```

### 嵌套维度配置（雪花模型）

**重要**：嵌套维度中，`foreignKey` 指的是**父表**中的列，而非事实表！

```javascript
{
    name: 'product',
    tableName: 'dim_product',
    foreignKey: 'product_key',      // 事实表中的列
    primaryKey: 'product_key',
    captionColumn: 'product_name',

    dimensions: [                    // 嵌套子维度
        {
            name: 'category',
            alias: 'productCategory',
            tableName: 'dim_category',
            foreignKey: 'category_key', // dim_product 表中的列！
            primaryKey: 'category_key',
            captionColumn: 'category_name'
        }
    ]
}
```

### 父子维度配置（层次结构）

需要预先创建闭包表：

```javascript
{
    name: 'team',
    tableName: 'dim_team',
    foreignKey: 'team_id',
    primaryKey: 'team_id',
    captionColumn: 'team_name',

    // 父子维度特有配置
    closureTableName: 'team_closure',  // 必需
    parentKey: 'parent_id',
    childKey: 'team_id'
}
```

### 维度复用模式

`/tm-dimension` 会建议将常用维度提取为构建器函数：

```javascript
// dimensions/common-dims.fsscript
export function buildDateDim(options = {}) {
    const { name = 'date', foreignKey = 'date_key' } = options;
    return {
        name,
        tableName: 'dim_date',
        foreignKey,
        primaryKey: 'date_key',
        properties: [
            { column: 'year', caption: '年' },
            { column: 'month', caption: '月' },
            { column: 'day_of_week', caption: '星期' }
        ]
    };
}
```

## 最佳实践

### 1. 先检查后生成

对于已存在的数据库表，建议先使用 `inspect_table` 检查结构：

```
先用 inspect_table 检查 fact_orders 表，然后用 /tm-generate 生成 TM 文件
```

### 2. 维度复用优先

当多个事实表使用相同维度时，优先使用维度构建器：

```
/tm-dimension

请创建一个通用的日期维度构建器，我有多个事实表都需要用到
```

### 3. 分步配置复杂维度

对于嵌套或父子维度，建议分步配置：

```
/tm-dimension

第一步：配置产品维度的基本结构
第二步：添加品类嵌套维度
第三步：添加品类组二级嵌套
```

### 4. 提供业务上下文

提供业务背景可以获得更好的字段命名和描述：

```
/tm-generate

这是一个电商平台的订单明细表，用于分析销售业绩：
- date_key: 订单日期
- customer_key: 下单客户
- sales_amount: 订单金额（需要按日期和客户汇总）
```

## 常见问题

### Q: Skill 没有加载？

确保 `.claude/commands/tm-generate.md` 和 `.claude/commands/tm-dimension.md` 文件存在。

### Q: 如何自定义 Skill？

直接编辑 `.claude/commands/` 目录下的 markdown 文件即可。

### Q: 可以添加更多 Skill 吗？

可以。在 `.claude/commands/` 目录创建新的 `.md` 文件，文件名即为命令名。

## 相关文档

- [TM 语法参考](/zh/dataset-model/tm-qm/tm-syntax)
- [QM 语法参考](/zh/dataset-model/tm-qm/qm-syntax)
- [维度复用最佳实践](/zh/dataset-model/tm-qm/tm-syntax#_7-维度复用最佳实践)
- [父子维度配置](/zh/dataset-model/tm-qm/parent-child)
