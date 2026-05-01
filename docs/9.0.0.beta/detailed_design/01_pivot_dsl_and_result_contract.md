# 9.0.0 详细设计 (01)：Pivot DSL 输入模型与多模态结果集契约

在 `foggy-dataset-model-pivot` 模块中，首要任务是定义强类型的 Java AST 模型，以承接前端或 LLM 传递的 JSON DSL，同时明确最终输出给调用方的结果集结构。

## 一、 输入端：Pivot DSL AST 模型设计

为保证对 LLM 最友好，我们采用**字段级局部挂载 (Field-Level Config)** 的理念设计 AST。

### 1. `PivotQueryRequest` (根请求)
这是继承/组合自基础 `DatasetRequest` 的新入口。

```java
public class PivotQueryRequest {
    // 基础过滤条件 (复用 8.x 的 Slice)
    private Slice slice; 
    
    // 动态计算字段 (复用 8.x 的 CalculatedField)
    private List<CalculatedField> calculatedFields;
    
    // 核心透视定义
    private PivotSpec pivot;
}

public class PivotSpec {
    // 行轴层级定义 (顺序极其重要)
    private List<AxisField> rows;
    
    // 列轴层级定义 (顺序极其重要)
    private List<AxisField> columns;
    
    // 度量集合
    private List<String> metrics;
    
    // 附属属性 (不参与聚合的装饰字段)
    private List<String> properties;
    
    // 透视行为开关
    private PivotOptions options;
    
    // 输出形态：tree | grid | flat
    private String outputFormat = "tree"; 

    // 结果整形布局，不改变查询聚合语义
    private PivotLayout layout;
}
```

### 2. `AxisField` (轴字段与局部截断控制)
这是取代晦涩的 `topNPerGroup` 全局配置的核心设计。每一个轴字段都可以携带针对其“隐式父级分组”的排序与截断指令。

它能覆盖 MDX `Generate(parentMembers, TopCount(childMembers, N, metric))` 的一个高频受控子集：父子层级都明确出现在同一个轴上，且子级只做 TopN 截断。但它不等价于任意 `Generate`，不能表达“遍历集合后拼接任意新集合”的通用集合生成能力。

```java
public class AxisField {
    // 字段引用表达式，例如: "product$subCategory"
    // JSON 键名为 "field"，与 DSL 示例保持一致
    private String field;
    
    // 按度量排序规则，例如: ["-salesAmount", "orderCount"]
    // 负号前缀表示降序；支持引用 calculatedFields 中的虚拟字段
    private List<String> orderBy;
    
    // 截断阈值，例如: 3
    // 语义：在其所有父级分组（即在 rows 数组中排在它前面的所有字段）确定的上下文中，取 Top N
    // 当 hierarchyMode = "tree" 时，语义变为：每个父节点下取 Top N 子节点（逐层截断）
    private Integer limit;
    
    // 轴级过滤 (Having)
    // 语义：在当前分组粒度完成基础聚合后，过滤掉不符合条件的成员
    private List<MetricFilter> having;
    
    // 父子维度层级展开模式 (可选)
    // null：普通维度（默认）
    // "tree"：启用父子维度树形展开，引擎通过 TM 元数据获取 parentKey 邻接关系，
    //         在内存中动态建树并递归卷起度量
    private String hierarchyMode;
    
    // 树形展开深度 (仅 hierarchyMode = "tree" 时生效)
    // -1：全展开到叶子节点（默认）
    //  0：仅根节点（子节点折叠，度量包含后代汇总）
    //  N：展开到第 N 层
    // 注：expandDepth 控制的是"展示层级"，不影响"聚合范围"——折叠的节点度量仍含后代汇总
    private Integer expandDepth;
}

public class MetricFilter {
    // 引用的度量字段名，支持 TM 原生度量或 calculatedFields 虚拟字段
    private String metric;
    // 比较运算符：">", ">=", "<", "<=", "=", "!="
    private String op;
    // 比较阈值
    private Number value;
}
```
> **LLM 提示词契约：** 在生成 Prompt 时，我们只需告诉 LLM："如果需要每个 A 里面取前 3 个 B，直接在 B 节点下加 `limit: 3`"。对于父子维度："如果需要按组织架构展开透视，在该字段上加 `hierarchyMode: \"tree\"`"。

> **`orderBy` 引用解析规则：** 引擎解析 `orderBy` 时，先在 `calculatedFields` 注册表中查找匹配，未命中则回退到 TM 原生度量。`calculatedFields` 的求值必须在字段级截断之前完成（详见 02 文档 Phase 1.5）。

> **`expandDepth` 与 `maxDepth` 的区别：** 8.x `slice` 中的 `maxDepth` 是**过滤语义**（真正丢弃深层节点）。9.x `expandDepth` 是**展示语义**（深层节点数据汇总到展开的最深层），与前端 TreeTable 的折叠/展开行为一致。

### 3. `PivotOptions` (行为控制开关)
用声明式开关代替复杂的 MDX 操作函数。

```java
public class PivotOptions {
    // 稀疏补全：是否在内存中对行、列截断后的成员做笛卡尔积，补全 0/null
    // 互斥约束：当任意轴字段启用了 hierarchyMode="tree" 时，crossjoin 必须为 false（不支持父子维与笛卡尔积同时启用，避免语义歧义）。
    private boolean crossjoin = false;
    
    // 行轴小计：是否在树形结构中注入父级维度的汇总节点
    private boolean rowSubtotals = false;
    
    // 列轴小计
    private boolean columnSubtotals = false;
    
    // 祖父级总计
    private boolean grandTotal = false;
}
```

### 4. `PivotLayout` (结果整形布局)
`layout` 只描述最终结果如何摆放，不改变 Phase 1 的 `GROUP BY`、度量聚合和上下文计算语义。它用于让 LLM 明确表达“中国式报表里度量作为行头”的展示意图，避免把度量伪装成普通维度。

```java
public class PivotLayout {
    // 度量摆放位置：columns | rows
    // 默认 columns：度量出现在列头叶子层。
    // rows：结果整形层将 metrics 转为固定指标行，适合收入/成本/毛利/毛利率这类固定报表骨架。
    // 注意：当 metricPlacement = "rows" 时，行头指标的排序严格由 pivot.metrics 数组的声明顺序决定。
    private String metricPlacement = "columns";
}
```

> **边界说明：** `metricPlacement = "rows"` 是结果整形契约，不是 MDX Measures Axis 的查询层原生等价。它只允许把 `metrics` 中已经声明的度量按固定顺序转置到行头；不支持在度量轴中混入任意维度成员或 MDX 元组集合。

---

## 二、 输出端：多模态结果集契约 (Result Set Shaping)

由于透视表数据的结构高度不确定（可能是树形前端组件，也可能是平面交叉表），引擎的最终结果集必须能够根据 `outputFormat` 灵活变换形态。

### 1. Format: `tree` (默认形态 - 最适合前端组件与 LLM 阅读)
将 `rows` 转化为嵌套的父子树，将 `columns` 压平作为数据节点的 Key。

**JSON Schema 结构：**
```json
{
  "format": "tree",
  "data": [
    {
      "node": { "region$name": "华东" },
      "isSubtotal": false, // 如果是自动卷起的小计行，此值为 true
      "cells": {
        // 列名规则：[col1_value]|[col2_value]|[metric_name]
        "2024|Q1|salesAmount": 1000,
        "2024|Q2|salesAmount": 1200
      },
      "children": [
        {
          "node": { "city$name": "上海" },
          "properties": { "city$level": "T1" }, // 挂载附属属性
          "isSubtotal": false,
          "cells": {
            "2024|Q1|salesAmount": 600
          }
        }
      ]
    }
  ]
}
```

### 2. Format: `grid` (适用于专业的交叉表组件或导出图表)
分离表头坐标系与纯净的数据二维矩阵，降低网络传输体积。

**JSON Schema 结构：**
```json
{
  "format": "grid",
  "layout": { "metricPlacement": "columns" },
  "rowHeaders": [
    // 定义每一行的坐标
    { "region$name": "华东", "city$name": "上海", "isSubtotal": false },
    { "region$name": "华东", "city$name": "杭州", "isSubtotal": false },
    { "region$name": "华东", "city$name": "ALL", "isSubtotal": true }
  ],
  "columnHeaders": [
    // 定义每一列的坐标
    { "salesDate$year": "2024", "salesDate$quarter": "Q1", "metric": "salesAmount" },
    { "salesDate$year": "2024", "salesDate$quarter": "Q2", "metric": "salesAmount" }
  ],
  "cells": [
    // 纯数据矩阵，对应 [rowIndex][colIndex]
    // null 语义：crossjoin=true 时，无数据的格子填充 null（由前端决定展示为 0 或 "-"）
    //           crossjoin=false 时，该行/列不会出现在 Domain 中
    [600, 700],
    [400, null],
    [1000, 1200]
  ]
}
```

当 `layout.metricPlacement = "rows"` 时，`grid` 的坐标含义变为：度量进入 `rowHeaders` 的叶子层，时间或其他列维度保留在 `columnHeaders` 中。

```json
{
  "format": "grid",
  "layout": { "metricPlacement": "rows" },
  "rowHeaders": [
    { "metric": "revenueAmount", "label": "收入" },
    { "metric": "costAmount", "label": "成本" },
    { "metric": "grossProfit", "label": "毛利" },
    { "metric": "grossMargin", "label": "毛利率" }
  ],
  "columnHeaders": [
    { "postingDate$month": "2024-01" },
    { "postingDate$month": "2024-02" }
  ],
  "cells": [
    [1200000, 1300000],
    [800000, 850000],
    [400000, 450000],
    [0.3333, 0.3461]
  ]
}
```

### 3. Format: `flat` (兼容模式 - 扁平带元数据)
将所有内容铺平为一个标准的 JSON Array，但在每行内部附带系统级的隐藏标记，供低级解析器使用。

**JSON Schema 结构：**
```json
{
  "format": "flat",
  "data": [
    {
      "region$name": "华东",
      "city$name": "上海",
      "salesDate$year": "2024",
      "salesAmount": 600,
      "_sys_meta": {
        "isRowSubtotal": false,
        "isColSubtotal": false,
        "rowLevel": 2
      }
    }
  ]
}
```

---
## 三、 遗留边界明确 (Design Boundaries)
1. **父级坐标导航 (`ROLLUP_TO`)**：`CALCULATE(metric, ROLLUP_TO(dim))` 不作为公开 DSL 开放。等价高频语义已在 S11 中通过 `pivot.metrics` 的结构化 `parentShare` 第一版覆盖（rows 轴相邻层级、可加度量）。
2. **任意 `Generate` 集合生成**：`AxisField.limit` 只覆盖“明确父子轴 + 子级 TopN”的受控场景，不覆盖 MDX 任意集合遍历和集合拼接。
3. **跨轴绝对坐标引用 (`AXIS_MEMBER` / `CELL_AT`) — `rejected-for-public-dsl`**：不作为 LLM 可生成的公开 DSL。等价高频语义已通过 S12 结构化 `baselineRatio` 覆盖；SQL 窗口或内存坐标索引只能作为引擎内部优化策略，不改变公开契约。
4. **多指标格式化**：百分比、千分位等格式化逻辑不应污染核心 Pivot Model，由外层展示包装组件（如 `formatter` 配置）接管。
