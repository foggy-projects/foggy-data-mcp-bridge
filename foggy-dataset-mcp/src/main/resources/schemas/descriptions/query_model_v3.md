# query_model_v3 工具描述

## 基本信息
- **工具名称**: query_model_v2
- **功能**: 执行指定模型的数据查询，支持复杂查询条件、分页、排序、分组聚合、计算字段等

## 核心概念与名词解释

### 📊 数据模型 (Data Model)
数据模型是数据查询的核心单元，代表一个具体的业务实体或数据表。

### 🎯 字段分类

**V3版本的核心简化**：所有字段直接使用字段名，无需拼接后缀。

#### 1. 维度字段 (Dimension)
维度已展开为两个独立字段：
- **xxx$id**: 维度的ID/值字段（用于精确查询、作为外键关联）
- **xxx$caption**: 维度的显示名称字段（用于展示、模糊查询）

每个字段都有独立的描述说明其格式和用途。

**示例**：
- `salesDate$id` - 销售日期ID，格式：yyyymmdd（如 20250101）
- `salesDate$caption` - 销售日期显示名称，格式：yyyy年mm月dd日（如 2025年01月01日）
- `customer$id` - 客户ID，数值类型
- `customer$caption` - 客户名称，文本类型

#### 2. 属性字段 (Attribute)
属性是数据模型中的普通字段，直接使用字段名。
- **文本属性**: `customerName`(客户名称)、`description`(描述)
- **数值属性**: `customerTel`(客户电话)、`orderNo`(订单号)
- **日期属性**: `createdDate`(创建日期)
- **字典属性**: `customerLevel`(客户等级) - 如果有$id/$caption后缀则按独立字段处理

#### 3. 度量字段 (Measure)
度量是用于统计计算的数值字段，直接使用字段名。
- **数值类型**: 用于数学运算
- **内置聚合**: 每个度量字段都有默认的聚合方式
- **无需后缀**: 直接使用字段名

#### 4. 计算字段 (Calculated Field)
计算字段是查询时动态创建的虚拟字段，基于表达式计算得出。
- **动态定义**: 在查询请求中通过 `calculatedFields` 参数定义
- **表达式驱动**: 支持算术运算、函数调用、字段引用
- **链式引用**: 后定义的计算字段可以引用前面定义的计算字段

## 工具描述

Query a specific dataset model with advanced filtering, sorting, grouping and aggregation capabilities.

🔍 **API能力概览**:
- 支持复杂查询条件、分页、排序、分组聚合等功能
- **所有字段直接使用字段名**，无需判断和拼接后缀
- 丰富的过滤操作符：=、!=、>、<、like、in、not in、区间查询[)、[]等
- 聚合函数：SUM、AVG、MAX、MIN、COUNT等
- **计算字段**：支持动态定义基于表达式的虚拟字段

📊 **支持的查询类型**:
1. 基础查询：客户信息、订单数据等明细查询
2. 条件查询：时间范围、状态筛选、模糊匹配等
3. 聚合查询：按团队/地区统计、KPI指标计算等
4. 复合查询：多维分组、多字段排序、分页等
5. **计算字段查询**：动态计算利润率、含税金额、净销售额等

🛠️ **查询参数详解**:

**计算字段(calculatedFields)**:

计算字段允许在查询时动态创建基于表达式的虚拟字段，无需修改数据模型即可实现复杂的业务计算。

```json
"calculatedFields": [
  {
    "name": "netAmount",
    "caption": "净销售额",
    "expression": "salesAmount - discountAmount"
  },
  {
    "name": "taxIncludedAmount",
    "caption": "含税金额",
    "expression": "netAmount * 1.13"
  },
  {
    "name": "profitRate",
    "caption": "利润率(%)",
    "expression": "ROUND(profitAmount * 100.0 / salesAmount, 2)"
  }
]
```

**表达式语法**:
- **算术运算**: `+`, `-`, `*`, `/`, `%`（取模）
- **括号分组**: `(salesAmount - discountAmount) * 1.13`
- **字段引用**: 直接使用模型中的字段名，如 `salesAmount`、`profitAmount`
- **链式引用**: 后定义的计算字段可以引用前面定义的计算字段，如 `taxIncludedAmount` 引用 `netAmount`

**支持的函数**:
- **数学函数**: `ABS(x)`, `ROUND(x, n)`, `CEIL(x)`, `FLOOR(x)`
- **空值处理**: `COALESCE(x, default)` - 如果 x 为 null，返回 default 值
- **条件函数**: `IF(condition, trueValue, falseValue)`

**使用规则**:
1. 计算字段名称不能与模型中已有字段重复
2. 计算字段可以在 `columns`、`orderBy`、`slice` 中使用
3. 计算字段定义顺序重要：后面的字段可以引用前面的字段
4. 表达式中的字段名必须是模型中存在的度量字段或已定义的计算字段

**列选择(columns)**:
```json
["customerName", "salesDate$caption", "customer$caption", "totalAmount", "netAmount", "profitRate"]
```
- **直接使用字段名**，无需判断是否需要后缀
- 维度字段已展开为独立的 `xxx$id` 和 `xxx$caption` 字段
- 根据需要选择 `$id` 或 `$caption` 变体
- **计算字段**：使用 `calculatedFields` 中定义的 `name`

**过滤条件(slice)**:
```json
[
  {"field": "customer$caption", "op": "like", "value": "张三"},
  {"field": "salesDate$id", "op": "[)", "value": ["20250101", "20251231"]},
  {"field": "customerType", "op": "in", "value": [10, 20, 30]},
  {"field": "customerLevel", "op": "is not null"},
  {"field": "description", "op": "is null"},
  {"field": "profitRate", "op": ">", "value": 10}
]
```

**重要语法规则**:
- **空值检查**: 使用 `is null` 或 `is not null` 时，**不需要提供 value 参数**
- **区间查询**: 使用 `[]`, `[)`, `()`, `(]` 时，value 必须是长度为2的数组 `[start, end]`
- **集合查询**: 使用 `in`, `not in` 时，value 可以是单个值或数组
- **模糊查询**: 系统会自动添加通配符，无需手动添加 `%`
- **计算字段过滤**: 计算字段可以直接在 slice 中使用

**支持的查询类型**:
- **等值查询**: `=`, `!=`, `<>` (不等于的两种写法)
- **比较查询**: `>`, `>=`, `<`, `<=`
- **模糊查询**: `like`, `left_like`, `right_like`
  - `like`: 包含匹配，等价于 `%value%`
  - `left_like`: 左匹配，等价于 `%value`
  - `right_like`: 右匹配，等价于 `value%`
- **集合查询**: `in`, `not in`
- **空值查询**: `is null`, `is not null` (**重要：无需传value参数**)
- **区间查询**: `[]`, `[)`, `()`, `(]`
- **位运算查询**: `bit_in` (位运算匹配)
- **逻辑连接**: 条件间默认为 `AND`，支持 `OR` 连接（通过 `children` 和 `link` 参数）

**排序(orderBy)**:
```json
[
  {"field": "createdDate", "dir": "DESC"},
  {"field": "customer$caption", "dir": "ASC"},
  {"field": "profitRate", "dir": "DESC"}
]
```
- 计算字段可以直接用于排序

**分组聚合(groupBy)**:
```json
[
  {"field": "salesDate$caption"},
  {"field": "customer$caption"},
  {"field": "totalAmount", "agg": "SUM"}
]
```
- **聚合类型(agg)**: MAX, MIN, SUM, AVG, COUNT, PK
- **重要规则**:
  - 度量字段有内置默认聚合方式，不需要在groupBy中指定
  - 在groupBy中出现的字段，**必须**在columns中也出现
  - COUNT聚合会返回统计数量，结果字段名为 `total`

**分页参数**:
- `start`: 起始行号，从0开始计数
- `limit`: 每页记录数，建议控制在合适大小
- `hasNext`: 响应字段，用于判断是否有下一页

**性能优化参数(returnTotal)**:
- `returnTotal`: Boolean类型，是否返回总数及合计
- 设置为`false`可以显著提升查询性能

💡 **查询示例**:

**基础查询**:
```json
{
  "model": "TmsOrderModel",
  "payload": {
    "columns": ["orderNo", "customer$caption", "salesDate$caption", "totalAmount"],
    "slice": [
      {"field": "salesDate$id", "op": "[)", "value": ["20250101", "20251231"]}
    ],
    "orderBy": [{"field": "salesDate$id", "dir": "DESC"}],
    "start": 0,
    "limit": 30,
    "returnTotal": true
  }
}
```

**聚合统计**:
```json
{
  "model": "TmsOrderModel",
  "payload": {
    "columns": ["salesDate$caption", "customer$caption", "totalAmount"],
    "groupBy": [
      {"field": "salesDate$caption"},
      {"field": "customer$caption"},
      {"field": "totalAmount", "agg": "SUM"}
    ],
    "slice": [
      {"field": "salesDate$id", "op": "[)", "value": ["20250101", "20250701"]}
    ],
    "start": 0,
    "limit": 50
  }
}
```

**计算字段查询**:
```json
{
  "model": "TmsOrderModel",
  "payload": {
    "calculatedFields": [
      {
        "name": "netAmount",
        "caption": "净销售额",
        "expression": "salesAmount - discountAmount"
      },
      {
        "name": "profitRate",
        "caption": "利润率(%)",
        "expression": "ROUND(profitAmount * 100.0 / salesAmount, 2)"
      }
    ],
    "columns": ["orderNo", "customer$caption", "salesAmount", "netAmount", "profitRate"],
    "slice": [
      {"field": "salesDate$id", "op": "[)", "value": ["20250101", "20251231"]},
      {"field": "profitRate", "op": ">", "value": 10}
    ],
    "orderBy": [{"field": "profitRate", "dir": "DESC"}],
    "start": 0,
    "limit": 30
  }
}
```

🎯 **最佳实践**:
- 直接从元数据中选择需要的字段名
- 展示数据时优先选择 `$caption` 变体
- 精确查询时使用 `$id` 变体（通常格式更规范）
- 参考字段描述了解每个字段的格式说明
- **计算字段命名**: 使用有意义的名称，如 `netAmount`、`profitRate`
- **链式计算**: 利用链式引用简化复杂表达式，如先定义 `netAmount`，再基于它定义 `taxIncludedAmount`
- **空值处理**: 对可能为 null 的字段使用 `COALESCE(field, 0)` 避免计算错误
