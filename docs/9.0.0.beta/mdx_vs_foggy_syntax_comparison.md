# MDX 与 Foggy 9.x 语法等价性对比手册

在从传统的重型多维分析（以 MDX 为代表）演进到大模型原生语义层（Foggy 9.x）的过程中，核心问题不是复刻 MDX 语法，而是验证其主要表达空间能否被更结构化、对 LLM 更友好的 JSON DSL 承载。

本手册旨在通过直接的语法对比，推演 Foggy 9.x (叠加 8.6.0 AST 增强) 能否使用极度精简的 JSON DSL 覆盖 MDX 的主要表达空间。本文不是完整语法承诺；凡是依赖展示整形、`compose_script` 兜底或未实现语义糖的场景，都会显式标注边界。

---

## 一、 核心架构概念映射 (Core Paradigm Mapping)

| MDX 核心概念 | Foggy 9.x 表达方式 | 设计差异本质 |
| :--- | :--- | :--- |
| **Tuple / Tuple Set (元组/集合)** | `rows` 与 `columns` 数组 | MDX 需要用 `{}` 拼接成员集合；Foggy 直接声明字段名，引擎自动拉取集合。 |
| **Member Properties (成员属性)** | `properties` 数组 | MDX 用 `DIMENSION PROPERTIES`；Foggy 提取为与 `rows/columns` 隔离的独立数组，防范 OOM。 |
| **Slicer Axis / WHERE (切片轴)** | `slice` 对象 | 完全等价，用于全局上下文过滤。 |
| **Subcubes (子立方体)** | `compose_script` (CTE 模式) | MDX 用子查询构造 Subcube；Foggy 退回 CTE 进行流转编排。 |
| **Measures Axis (度量轴)** | `metrics` 数组 | MDX 把度量也当作一个维度成员；Foggy 明确拆成度量槽位，避免轴语义混淆。 |
| **Calculated Member (计算成员)** | `calculatedFields` / `CALCULATE` 表达式 | MDX 用 `WITH MEMBER` 临时扩展 Cube；Foggy 在查询期注入计算字段或上下文计算表达式。 |
| **Named Set (命名集合)** | 轴对象或 `compose_script` 中间变量 | 简单集合直接放入 `rows/columns`，复杂可复用集合交给 CTE 变量承载。 |
| **NON EMPTY (非空过滤)** | 默认稀疏输出 / `options.crossjoin` | Foggy 默认只返回有事实数据的格子；需要财报式补齐时显式打开 `crossjoin`。 |
| **Rollup / Grand Total (小计/总计)** | `options.rowSubtotals` / `options.columnSubtotals` / `options.grandTotal` | MDX 依赖层级成员或 `All` 成员；Foggy 由引擎在结果整形阶段注入汇总节点。 |

---

## 二、 高频查询场景实战对比 (High-Frequency Scenarios)

### 1. 基本透视 (Basic Pivot)
**需求：** 按品类看总销售额。
*   **MDX:**
    ```mdx
    SELECT 
      {[Measures].[Sales]} ON COLUMNS, 
      {[Product].[Category].Members} ON ROWS 
    FROM [SalesCube]
    ```
*   **Foggy 9.x DSL:**
    ```json
    {
      "pivot": {
        "rows": ["product$category"],
        "metrics": ["salesAmount"]
      }
    }
    ```

### 2. 维度属性带出 (Member Properties)
**需求：** 按客户看销售额，顺便带出客户电话（电话不能影响分组粒度）。
*   **MDX:**
    ```mdx
    SELECT {[Measures].[Sales]} ON COLUMNS,
    [Customer].[CustomerName].Members DIMENSION PROPERTIES [Customer].[Phone] ON ROWS
    FROM [SalesCube]
    ```
*   **Foggy 9.x DSL:**
    ```json
    {
      "pivot": {
        "rows": ["customer$name"],
        "metrics": ["salesAmount"],
        "properties": ["customer$phone"] // 引擎自动实施 Post-Join 或 ANY_VALUE
      }
    }
    ```

### 3. 排序与截断 (Top N & Order)
**需求：** 找出总销售额排名前 10 的品类。
*   **MDX:**
    ```mdx
    SELECT {[Measures].[Sales]} ON COLUMNS,
    TopCount([Product].[Category].Members, 10, [Measures].[Sales]) ON ROWS
    FROM [SalesCube]
    ```
*   **Foggy 9.x DSL:**
    ```json
    {
      "pivot": {
        "rows": {
          "fields": ["product$category"],
          "orderBy": ["-salesAmount"], // 先聚合，再按度量降序
          "limit": 10                  // 截断
        },
        "metrics": ["salesAmount"]
      }
    }
    ```

### 4. 稀疏数据强制补全 (CrossJoin / Empty Handling)
**需求：** 查看各品类近 3 个月的销量，即便某品类某月无销量也要显示 0 的网格。
*   **MDX:**
    利用 `CrossJoin()` 函数，并**去掉**常用的 `NON EMPTY` 关键字。
    ```mdx
    SELECT {[Measures].[Sales]} ON COLUMNS,
    CrossJoin([Product].[Category].Members, [Time].[Month].Members) ON ROWS
    FROM [SalesCube]
    ```
*   **Foggy 9.x DSL:**
    ```json
    {
      "pivot": {
        "rows": ["product$category"],
        "columns": ["salesDate$month"],
        "metrics": ["salesAmount"],
        "options": { "crossjoin": true } // 一键式开关，引擎在内存中进行笛卡尔积补零
      }
    }
    ```

### 5. 非空格子过滤 (NON EMPTY)
**需求：** 只看实际有销售数据的品类与月份，空白组合不输出。
*   **MDX:**
    ```mdx
    SELECT NON EMPTY {[Measures].[Sales]} ON COLUMNS,
    NON EMPTY CrossJoin([Product].[Category].Members, [Time].[Month].Members) ON ROWS
    FROM [SalesCube]
    ```
*   **Foggy 9.x DSL:**
    ```json
    {
      "pivot": {
        "rows": ["product$category"],
        "columns": ["salesDate$month"],
        "metrics": ["salesAmount"],
        "options": { "crossjoin": false } // 默认稀疏输出，不制造无事实数据的空格子
      }
    }
    ```

### 6. 全局切片过滤 (WHERE / Slicer Axis)
**需求：** 只统计华东大区、2024 年的数据，再按品类透视销售额。
*   **MDX:**
    ```mdx
    SELECT {[Measures].[Sales]} ON COLUMNS,
    {[Product].[Category].Members} ON ROWS
    FROM [SalesCube]
    WHERE ([Region].[East], [Time].[Year].[2024])
    ```
*   **Foggy 9.x DSL:**
    ```json
    {
      "slice": {
        "region$name": "East",
        "salesDate$year": 2024
      },
      "pivot": {
        "rows": ["product$category"],
        "metrics": ["salesAmount"]
      }
    }
    ```

### 7. 多度量并列展示 (Multiple Measures)
**需求：** 同时查看销售额、订单数和客单价。
*   **MDX:**
    ```mdx
    SELECT 
      {[Measures].[Sales], [Measures].[Order Count], [Measures].[Avg Ticket]} ON COLUMNS,
      {[Product].[Category].Members} ON ROWS
    FROM [SalesCube]
    ```
*   **Foggy 9.x DSL:**
    ```json
    {
      "pivot": {
        "rows": ["product$category"],
        "metrics": ["salesAmount", "orderCount", "avgTicketAmount"]
      }
    }
    ```

### 8. 行列双轴透视 (Rows x Columns)
**需求：** 行上按品类展开，列上按月份展开，每个交叉点显示销售额。
*   **MDX:**
    ```mdx
    SELECT {[Time].[Month].Members} ON COLUMNS,
    {[Product].[Category].Members} ON ROWS
    FROM [SalesCube]
    WHERE ([Measures].[Sales])
    ```
*   **Foggy 9.x DSL:**
    ```json
    {
      "pivot": {
        "rows": ["product$category"],
        "columns": ["salesDate$month"],
        "metrics": ["salesAmount"],
        "outputFormat": "grid"
      }
    }
    ```

### 9. 多级行头与自动小计 (Hierarchical Rows & Subtotals)
**需求：** 按大区、城市两级展开销售额，并在每个大区后追加小计行。
*   **MDX:**
    ```mdx
    SELECT {[Measures].[Sales]} ON COLUMNS,
    Hierarchize(
      DrilldownLevel([Region].[Region].Members)
    ) ON ROWS
    FROM [SalesCube]
    ```
*   **Foggy 9.x DSL:**
    ```json
    {
      "pivot": {
        "rows": ["region$name", "city$name"],
        "metrics": ["salesAmount"],
        "options": { "rowSubtotals": true },
        "outputFormat": "tree"
      }
    }
    ```

### 10. 总计行与总计列 (Grand Total)
**需求：** 输出品类 x 月份的交叉表，并追加行总计、列总计和全表总计。
*   **MDX:**
    ```mdx
    SELECT 
      {[Time].[Month].Members, [Time].[All Periods]} ON COLUMNS,
      {[Product].[Category].Members, [Product].[All Products]} ON ROWS
    FROM [SalesCube]
    WHERE ([Measures].[Sales])
    ```
*   **Foggy 9.x DSL:**
    ```json
    {
      "pivot": {
        "rows": ["product$category"],
        "columns": ["salesDate$month"],
        "metrics": ["salesAmount"],
        "options": {
          "rowSubtotals": true,
          "columnSubtotals": true,
          "grandTotal": true
        }
      }
    }
    ```

### 11. 轴级分页与双轴截断 (Axis-Level Limit)
**需求：** 只看销售额前 10 的品类，以及最近 6 个月的表现。
*   **MDX:**
    ```mdx
    SELECT 
      Tail([Time].[Month].Members, 6) ON COLUMNS,
      TopCount([Product].[Category].Members, 10, [Measures].[Sales]) ON ROWS
    FROM [SalesCube]
    ```
*   **Foggy 9.x DSL:**
    ```json
    {
      "pivot": {
        "rows": {
          "fields": ["product$category"],
          "orderBy": ["-salesAmount"],
          "limit": 10
        },
        "columns": {
          "fields": ["salesDate$month"],
          "orderBy": ["salesDate$month"],
          "limit": 6
        },
        "metrics": ["salesAmount"]
      }
    }
    ```

### 12. 固定报表骨架 (Fixed Members / Asymmetric Rows)
**需求：** 中国式报表中行头不是某个维度全量成员，而是人工指定的几个项目，如“收入、成本、毛利、毛利率”。
*   **MDX:**
    ```mdx
    WITH
      MEMBER [Measures].[Gross Profit] AS [Measures].[Revenue] - [Measures].[Cost]
      MEMBER [Measures].[Gross Margin] AS [Measures].[Gross Profit] / [Measures].[Revenue]
    SELECT 
      {[Time].[Month].Members} ON COLUMNS,
      {[Measures].[Revenue], [Measures].[Cost], [Measures].[Gross Profit], [Measures].[Gross Margin]} ON ROWS
    FROM [FinanceCube]
    ```
*   **Foggy 策略:**
    **等价性结论：部分等价。** 自定义度量由 `calculatedFields` 承载；但 MDX 的“度量成员放在行轴”不是普通维度轴，Foggy 当前更适合把它视为结果整形层的报表模板骨架。基础查询层用多度量透视输出；若需要严格的“指标在行、月份在列”版式，需要结果整形层显式转置。
    > 注：Pivot 模式下度量会依据 TM `measures` 中的默认聚合方式自动聚合。这里先保持裸度量表达，是否在 `calculatedFields.expression` 中显式写 `SUM(...)` 需要后续结合 Pivot 编译阶段再评估。
    ```json
    {
      "calculatedFields": [
        {
          "name": "grossProfit",
          "expression": "revenueAmount - costAmount"
        },
        {
          "name": "grossMargin",
          "expression": "(revenueAmount - costAmount) / NULLIF(revenueAmount, 0)"
        }
      ],
      "pivot": {
        "columns": ["postingDate$month"],
        "metrics": [
          "revenueAmount",
          "costAmount",
          "grossProfit",
          "grossMargin"
        ],
        "outputFormat": "grid"
      }
    }
    ```
    需要补充的 9.x 契约是：LLM 如何显式表达“度量作为行头”的展示意图，以及转置后指标行、时间列和数据单元的对应关系。例如可评估在结果整形层增加 `metricPlacement: "rows"` 之类的受控选项，而不是把度量伪装成普通维度。

---

## 三、 高级分析函数等价对照 (Advanced Analytic Functions)

这里是多维分析的深水区，Foggy 的策略是：**高频场景优先收敛到 8.6.0 的 `CALCULATE` 家族、时间窗口语义或隐式推导中；无法严谨等价的 MDX 坐标能力必须标注为待实现能力或设计边界。**

### 1. 智能聚合推导 (Aggregate)
**需求：** 提取度量，并依据底层元数据（SUM, COUNT, LAST_VALUE 等）自动决定聚合方式。
*   **MDX:** `Aggregate(Set, [Measure])`
*   **Foggy 策略:** **隐式推导**。在 `metrics` 数组或 `CALCULATE` 中只要直接引用裸字段 `salesAmount`，底层解析器自动去 TM 元数据中查找 `aggType` 并套用正确的聚合行为。绝不要求前端显式传入。

### 2. 占比计算：打破作用域 (Percent of Total)
**需求：** 计算某个子品类的销售额占整个大类的百分比。
*   **MDX:** 
    利用元组坐标覆盖，强制定位到当前成员的父级。
    ```mdx
    [Measures].[Sales] / 
    ( [Measures].[Sales], [Product].[Category].CurrentMember.Parent )
    ```
*   **Foggy 9.x 语义:**
    **等价性结论：S11 已通过结构化 `parentShare` 覆盖第一版。** 这个 MDX 场景本质是“父级坐标导航”，不适合用 `REMOVE(product$subCategory)` 表达。`REMOVE` 更适合“全局占比”或“从当前 groupBy 中剔除某个维度”的关系型场景；父级占比应通过 `pivot.metrics` 对象元素声明，而不是生成 `ROLLUP_TO` 字符串函数。

    如果要表达“子品类占全体子品类总额”，那才是 `REMOVE(product$subCategory)` 的场景：
    ```sql
    salesAmount / NULLIF(CALCULATE(salesAmount, REMOVE(product$subCategory)), 0)
    ```
    对应的 Pivot 结构应显式保留父子层级，便于引擎识别当前单元格的父级坐标：
    ```json
    {
      "pivot": {
        "rows": ["product$category", "product$subCategory"],
        "metrics": [
          "salesAmount",
          {
            "name": "subCategoryShareInCategory",
            "type": "parentShare",
            "of": "salesAmount"
          }
        ]
      }
    }
    ```
    > 注：`parentShare` 第一版仅支持 `rows` 轴相邻层级和可加度量；显式或隐式落到 `columns`、`tree` 模式、不可加度量都会 fail-closed。`ROLLUP_TO` 不作为公开 DSL 开放。

### 3. 时间智能：同环比计算 (Time Intelligence)
**需求：** 计算去年同期的销售额 (YoY)。
*   **MDX:** 
    使用 `ParallelPeriod` 进行时间轴偏移。
    ```mdx
    ( ParallelPeriod([Time].[Year], 1, [Time].[CurrentMember]), [Measures].[Sales] )
    ```
*   **Foggy (基于 8.6.0):** 
    统一收束到 `OFFSET` 函数，极其直白。
    ```sql
    CALCULATE(salesAmount, OFFSET(salesDate$year, -1))
    ```

### 4. 层级导航过滤 (Hierarchical Drilldown)
**需求：** 想看“电子产品”下的所有子分类数据。
*   **MDX:** 
    使用结构导航函数 `Children()` 或 `Descendants()`。
    ```mdx
    SELECT {[Measures].[Sales]} ON COLUMNS,
    Descendants([Product].[Category].[Electronics]) ON ROWS
    ```
*   **Foggy 策略:** 
    **降维为关系型过滤**。不需要树形导航函数，只需在 `slice` 做相等过滤，并在 `rows` 放下一级维度即可。
    ```json
    {
      "slice": { "product$category": "Electronics" },
      "pivot": {
        "rows": ["product$subCategory"],
        "metrics": ["salesAmount"]
      }
    }
    ```

### 5. 计算成员：毛利与毛利率 (Calculated Member)
**需求：** 在查询期新增毛利和毛利率指标。
*   **MDX:**
    ```mdx
    WITH
      MEMBER [Measures].[Gross Profit] AS [Measures].[Sales] - [Measures].[Cost]
      MEMBER [Measures].[Gross Margin] AS [Measures].[Gross Profit] / [Measures].[Sales]
    SELECT {[Measures].[Gross Profit], [Measures].[Gross Margin]} ON COLUMNS,
    {[Product].[Category].Members} ON ROWS
    FROM [SalesCube]
    ```
*   **Foggy 9.x DSL:**
    > 注：Pivot 模式下度量会依据 TM `measures` 中的默认聚合方式自动聚合。这里先保持裸度量表达，是否在 `calculatedFields.expression` 中显式写 `SUM(...)` 需要后续结合 Pivot 编译阶段再评估。
    ```json
    {
      "calculatedFields": [
        {
          "name": "grossProfit",
          "expression": "salesAmount - costAmount"
        },
        {
          "name": "grossMargin",
          "expression": "(salesAmount - costAmount) / NULLIF(salesAmount, 0)"
        }
      ],
      "pivot": {
        "rows": ["product$category"],
        "metrics": ["grossProfit", "grossMargin"]
      }
    }
    ```

### 6. 命名集合复用 (Named Set)
**需求：** 多处复用“销售额前 20 的客户”集合。
*   **MDX:**
    ```mdx
    WITH SET [Top Customers] AS
      TopCount([Customer].[CustomerName].Members, 20, [Measures].[Sales])
    SELECT {[Measures].[Sales]} ON COLUMNS,
    [Top Customers] ON ROWS
    FROM [SalesCube]
    ```
*   **Foggy 策略:**
    单次查询内直接把集合逻辑下沉到行轴对象；若需要跨步骤复用，把 Top 客户集合先做成 `compose_script` 中间变量，再参与后续 Pivot。
    ```json
    {
      "pivot": {
        "rows": {
          "fields": ["customer$name"],
          "orderBy": ["-salesAmount"],
          "limit": 20
        },
        "metrics": ["salesAmount"]
      }
    }
    ```

### 7. 聚合后过滤 (Filter Set / HAVING)
**需求：** 只保留销售额超过 100 万的品类。
*   **MDX:**
    ```mdx
    SELECT {[Measures].[Sales]} ON COLUMNS,
    Filter([Product].[Category].Members, [Measures].[Sales] > 1000000) ON ROWS
    FROM [SalesCube]
    ```
*   **Foggy 策略:**
    这是轴成员过滤，和 `TopCount` 同属轴级控制。9.x Pivot 不应默认退回 `compose_script`，而应在轴对象上提供 `having`，表示“先按轴聚合，再筛选可出现在轴上的成员”。
    ```json
    {
      "pivot": {
        "rows": {
          "fields": ["product$category"],
          "having": [
            { "metric": "salesAmount", "op": ">", "value": 1000000 }
          ]
        },
        "metrics": ["salesAmount"]
      }
    }
    ```
    推荐执行顺序为：基础聚合 -> 轴级 `having` 过滤 -> `orderBy/limit` -> `crossjoin` 补全 -> 小计/总计 -> 结果整形。只有当谓词依赖跨模型中间结果、窗口后计算或最终 shaped result 时，才退回 `compose_script`。

### 8. 子查询限定 Cube 空间 (Subselect / Subcube)
**需求：** 先把 Cube 限定到 2024 年和华东大区，再在这个子空间里做品类 x 月份透视。
*   **MDX:**
    ```mdx
    SELECT {[Time].[Month].Members} ON COLUMNS,
    {[Product].[Category].Members} ON ROWS
    FROM (
      SELECT {[Region].[East]} ON 0
      FROM (
        SELECT {[Time].[Year].[2024]} ON 0
        FROM [SalesCube]
      )
    )
    WHERE ([Measures].[Sales])
    ```
*   **Foggy 9.x DSL:**
    简单子空间直接变成 `slice`；只有跨模型或多阶段派生时才退回 CTE。
    ```json
    {
      "slice": {
        "region$name": "East",
        "salesDate$year": 2024
      },
      "pivot": {
        "rows": ["product$category"],
        "columns": ["salesDate$month"],
        "metrics": ["salesAmount"]
      }
    }
    ```

### 9. 累计值：年初至今 (PeriodsToDate / YTD)
**需求：** 按月展示销售额，并额外展示年初至当月的累计销售额。
*   **MDX:**
    ```mdx
    WITH MEMBER [Measures].[Sales YTD] AS
      Aggregate(PeriodsToDate([Time].[Year], [Time].CurrentMember), [Measures].[Sales])
    SELECT {[Measures].[Sales], [Measures].[Sales YTD]} ON COLUMNS,
    {[Time].[Month].Members} ON ROWS
    FROM [SalesCube]
    ```
*   **Foggy 策略:**
    时间累计不是独立 MDX 函数树，而是上下文窗口的语义糖。单模型可收束为 `CALCULATE` 的时间范围过滤；更复杂的滚动累计可退回 `compose_script` 做二次加工。
    ```sql
    CALCULATE(
      salesAmount,
      RANGE(salesDate$date, START_OF_YEAR(CURRENT(salesDate$date)), CURRENT(salesDate$date))
    )
    ```

### 10. 移动窗口：最近 3 个月均值 (LastPeriods / Moving Average)
**需求：** 每个月展示最近 3 个月平均销售额。
*   **MDX:**
    ```mdx
    WITH MEMBER [Measures].[Sales 3M Avg] AS
      Avg(LastPeriods(3, [Time].CurrentMember), [Measures].[Sales])
    SELECT {[Measures].[Sales 3M Avg]} ON COLUMNS,
    {[Time].[Month].Members} ON ROWS
    FROM [SalesCube]
    ```
*   **Foggy 策略:**
    **等价性结论：部分等价。** 从 LLM 生成稳定性看，不应该把移动窗口强行塞进 Pivot DSL。推荐分两档处理：
    1. 标准滚动窗口（如 rolling 7/30/90 day）优先使用既有 `timeWindow` 声明式能力，避免手写窗口逻辑。
    2. 像 `LastPeriods(3, Month)` 这种“最近 3 个时间成员”的非标准窗口，使用 `compose_script` 拆成两个清晰步骤：先生成月度聚合结果，再在聚合结果上做后置窗口计算。

    这种两步写法对 LLM 更稳定：第一步只负责取数和聚合，第二步只负责窗口加工，避免在一个 JSON 节点里混入太多语义。但需要注意，下面的 `window(...)` 是目标编排原语/伪代码。如果当前 `compose_script` 尚未提供稳定的窗口函数封装，那么 `LastPeriods(N)` 的任意 N 成员窗口仍是能力缺口；现有 `rolling_7d/30d/90d` 只能覆盖预设自然日滚动窗口。
    ```sql
    monthly_sales = query_model("SalesQuery", {
      "pivot": {
        "rows": ["salesDate$month"],
        "metrics": ["salesAmount"]
      }
    });

    result = window(monthly_sales, orderBy = salesDate$month, frame = 3, expr = avg(salesAmount));
    ```

### 11. 钻取明细 (DRILLTHROUGH)
**需求：** 在透视表中点击“电子产品 / 2024-03”的单元格，查看构成该销售额的订单明细。
*   **MDX:**
    ```mdx
    DRILLTHROUGH
    SELECT ([Product].[Category].[Electronics], [Time].[Month].[2024-03], [Measures].[Sales])
    ON 0 FROM [SalesCube]
    RETURN [Order].[OrderNo], [Customer].[Name], [Measures].[Sales]
    ```
*   **Foggy 策略:**
    Drillthrough 不属于 Pivot 聚合本身，而是从单元格坐标反推出明细查询条件，再调用普通 `query_model`。
    ```json
    {
      "slice": {
        "product$category": "Electronics",
        "salesDate$month": "2024-03"
      },
      "columns": ["orderNo", "customer$name", "salesAmount"]
    }
    ```

### 12. 非对称轴 (Asymmetric Axis)
**需求：** 同一个列轴上混排“本月销售额、上月销售额、环比变化率”。
*   **MDX:**
    ```mdx
    WITH
      MEMBER [Measures].[Prior Sales] AS
        (ParallelPeriod([Time].[Month], 1, [Time].CurrentMember), [Measures].[Sales])
      MEMBER [Measures].[MoM Rate] AS
        ([Measures].[Sales] - [Measures].[Prior Sales]) / [Measures].[Prior Sales]
    SELECT {[Measures].[Sales], [Measures].[Prior Sales], [Measures].[MoM Rate]} ON COLUMNS,
    {[Product].[Category].Members} ON ROWS
    FROM [SalesCube]
    ```
*   **Foggy 9.x DSL:**
    通过多度量 + `CALCULATE/OFFSET` 表达，避免 MDX 式的轴上混排元组。
    > 注：Pivot 模式下度量会依据 TM `measures` 中的默认聚合方式自动聚合。这里先保持裸度量表达，`CALCULATE` 内是否必须显式包裹 `SUM(...)` 需要后续结合 Pivot 编译阶段再评估。
    ```json
    {
      "calculatedFields": [
        {
          "name": "priorSalesAmount",
          "expression": "CALCULATE(salesAmount, OFFSET(salesDate$month, -1))"
        },
        {
          "name": "momRate",
          "expression": "(salesAmount - priorSalesAmount) / NULLIF(priorSalesAmount, 0)"
        }
      ],
      "pivot": {
        "rows": ["product$category"],
        "metrics": ["salesAmount", "priorSalesAmount", "momRate"]
      }
    }
    ```

### 13. 分组内 TopN 展开 (Generate / Per-Group Ranking)
**需求：** 对每个大品类分别取销售额 Top 3 的子品类，各组截断后合并展示（结果为非均匀成员集合，每个品类下的子品类数量可能不同）。
*   **MDX:**
    ```mdx
    SELECT {[Measures].[Sales]} ON COLUMNS,
    Generate(
      [Product].[Category].Members,
      TopCount(
        Descendants([Product].[Category].CurrentMember, [Product].[SubCategory]),
        3, [Measures].[Sales]
      )
    ) ON ROWS
    FROM [SalesCube]
    ```
*   **Foggy 9.x DSL:**
    **等价性结论：受控子集已支持。** 采用"字段级局部挂载 (Field-Level Config)"设计：在具体的层级字段上直接定义 `orderBy` 和 `limit`，利用字段数组的隐式先后顺序自动推断分区键（排在它前面的所有字段即为 `PARTITION BY` 的分区键）。
    ```json
    {
      "pivot": {
        "rows": [
          "product$category",
          {
            "field": "product$subCategory",
            "orderBy": ["-salesAmount"],
            "limit": 3
          }
        ],
        "metrics": ["salesAmount"],
        "options": { "rowSubtotals": true }
      }
    }
    ```
    **DSL 设计要点：**
    - **混合数组语法**：`rows`（或 `columns`）数组中的元素可以是简单字符串（无特殊控制）或对象（承载局部截断/排序）。
    - **隐式分区推断**：引擎自动将对象化字段**前面的所有字段**作为 `PARTITION BY` 分区键。无需 LLM 手动重复声明 `partitionBy`，彻底消灭冗余拼写错误。
    - **`orderBy`**：支持引用度量字段，前缀 `-` 表示降序。语义为"在每个分区内，按此度量排名"。
    - **`limit`**：每个分区内保留的最大成员数。
    - **当前支持范围**：仅支持**单层受控截断**——即同一轴路径上只有一个字段设置 `limit`，在其所有前置字段形成的分区内按度量排序截断。此时引擎操作的数据已经过 Phase 1 SQL 聚合，截断排序的度量值即为该粒度的聚合值，语义与 `ROW_NUMBER() OVER (PARTITION BY parent ORDER BY metric)` 严格等价。
    - **级联多级截断的已知限制**：如果多个层级同时设置 `limit`（如"每个大区取 Top 5 城市，每个城市取 Top 3 门店"），当前实现在中间层的排序基于明细行而非中间聚合值，可能导致排名语义与预期不一致。**级联 Generate 暂未开放**，后续需引入中间聚合步骤后才能正式支持。
    - **`orderBy` 可引用 `calculatedFields`**：`orderBy` 不限于 TM 原生度量，还可以引用 `calculatedFields` 中定义的计算字段。因为 `calculatedFields` 的求值发生在字段级截断（步骤 3a'）之前，截断阶段可以安全地引用任何已计算字段。典型场景："每个品类下利润率 Top 3 的子品类"：
    ```json
    {
      "calculatedFields": [
        {
          "name": "profitRate",
          "expression": "(salesAmount - costAmount) / NULLIF(salesAmount, 0)"
        }
      ],
      "pivot": {
        "rows": [
          "product$category",
          { "field": "product$subCategory", "orderBy": ["-profitRate"], "limit": 3 }
        ],
        "metrics": ["salesAmount", "costAmount", "profitRate"]
      }
    }
    ```
    - **`orderBy` 引用目标的支持范围**：

      | 引用目标 | 是否支持 | 说明 |
      | :-- | :-- | :-- |
      | TM 原生度量（`salesAmount`） | ✅ | SQL 聚合后直接可用 |
      | 简单算术计算字段（`profitRate`） | ✅ | 聚合后简单运算，3a' 前已求值 |
      | `CALCULATE + OFFSET` 计算字段 | ⚠️ 需评估 | 编译为窗口函数，理论可行但需确认管线时序 |
      | 跨模型 `compose_script` 变量 | ❌ | 超出单次 Pivot 边界 |

    **从 LLM 视角的设计优势：**
    1. **消灭冗余声明**：LLM 不需要在 `partitionBy` 里重抄前面的字段，在长上下文中重抄极易引发错漏。
    2. **极佳的局部性**：LLM 的注意力机制在推理到"需要对子品类做截断"时，直接在该字段节点下输出 `limit` 和 `orderBy` 即可，认知链路最短。

    **引擎执行管线：**
    1. **SQL 层**：提取所有字段名（无论简写还是对象化形式），按全部字段朴素 `GROUP BY` 聚合——零方言依赖。
    2. **引擎内存层 (步骤 3a' — 截断)**：
       - 扫描 `rows` 数组，识别带 `limit` 的对象化字段。
       - 以该字段前面所有字段为分区键，在每个分区内按 `orderBy` 排序，截断到 `limit` 条。
       - 当前仅支持单层截断。级联多层截断需要先对中间层做聚合再排名，属于待实现能力。
    3. **后续流程**：截断后的非均匀成员集合进入正常的 CrossJoin → Subtotal → Shaping 流程。

    > 注：单层受控截断本质上等价于 SQL 的 `ROW_NUMBER() OVER (PARTITION BY ... ORDER BY ...) <= N`，但完全在引擎内存中执行，避免了不同数据库对窗口函数支持不一致的问题。

### 14. 跨轴绝对坐标引用 (Cross-Axis Reference)
**需求：** 在当前单元格中，引用另一个轴上的绝对位置（例如，以列轴的第一个成员的值作为基准，计算后续月份相对于首月的比率）。
*   **MDX:**
    利用 `Axis()` 函数进行绝对坐标寻址。
    ```mdx
    [Measures].[Sales] / 
    ( [Measures].[Sales], Axis(0).Item(0) )
    ```
*   **Foggy 后续目标方案（未纳入当前签收）:**
    `CALCULATE + AXIS_MEMBER` 字符串函数已被评估后 **`rejected-for-public-dsl`**（不作为 LLM 可生成的公开 DSL）。多层列轴下 index 语义天然歧义（"第 0 列"指第一年还是第一个月？），且 LLM 极易误用坐标漫游。

    该场景的高频子集（基准指数）已通过结构化 `pivot.metrics` 派生指标 `baselineRatio` 完全覆盖：
    ```json
    {
      "pivot": {
        "rows": ["product$category"],
        "columns": ["salesDate$month"],
        "metrics": [
          "salesAmount",
          {
            "name": "salesIndex",
            "type": "baselineRatio",
            "of": "salesAmount",
            "axis": "columns",
            "baseline": "first"
          }
        ]
      }
    }
    ```
    **设计要点**：
    - 不暴露 `AXIS_MEMBER / CELL_AT` 函数字符串，使用结构化 JSON 对象表达业务意图。
    - 第一版支持 `baseline: "first" | "last"`，固定成员 path 作为第二阶段扩展。
    - 引擎在内存后置计算阶段（SubtotalInjector 之后）构建坐标索引完成求值。
    - 缺失基准、除零、null 均返回 `null`。
    - 统一指标设计见 `detailed_design/06_s11_metrics_unification_and_derived_metrics.md`，S12 执行见 `detailed_design/07_s12_baseline_ratio_execution_plan.md`，**本能力已在 S12 中完成实现与签收**。

    如果需要更复杂的跨轴坐标漫游（如跨 row + 跨 column 同时改写坐标），请降级使用 `compose_script` 手写窗口函数。

### 15. 父子维度与动态层级展开 (Parent-Child Hierarchy)
**需求：** 按照员工汇报线、部门架构树等具有动态深度的父子结构展开透视。由于树的层级数不固定，无法在查询时写死平铺的层级字段（如 `level1`, `level2`）。
*   **MDX:**
    直接将具有 Parent-Child 属性的维度放置在轴上，OLAP 引擎会在内存中自动展开为递归树。
    ```mdx
    SELECT 
      [Measures].[Sales] ON COLUMNS,
      [Employee].[Employees].Members ON ROWS 
    FROM [SalesCube]
    ```
*   **Foggy 9.x DSL:**
    利用 8.x 的 TM 闭包表元数据与 9.x 的内存超立方能力。在 DSL 的 `AxisField` 中通过 `hierarchyMode` 表达展示意图，无需暴露物理的关联外键。
    ```json
    {
      "pivot": {
        "rows": [
          { "field": "department$caption", "hierarchyMode": "tree", "expandDepth": -1 }
        ],
        "metrics": ["salesAmount"]
      }
    }
    ```
    **引擎底层推导逻辑**：
    父子维的处理完美印证了 9.x "SQL 轻量聚合 + 内存重度塑形" 架构的优越性：
    1. **SQL 隐式注入 (Phase 1)**：引擎通过 TM 元数据获知该维度配置了 `parentKey` 邻接关系。在下推 SQL 时，除了用户声明的 `caption`，编译器会**隐式注入**节点主键 (`department$id`) 和父键 (`department$parentId`)，以 `ANY_VALUE()` 伪聚合带出，获取一份扁平的"邻接表"数据集。闭包表仅在 `slice` 过滤（如 `descendantsOf`）时复用 8.x 已有逻辑，不参与 Pivot 轴展开的 SQL 编译。
    2. **内存建树与递归卷起 (Phase 3)**：内存引擎拿到带有主键/父键的扁平数据后，执行动态建树。随后，复用常规的多维小计 (Subtotal) 逻辑，自底向上完成度量的递归累加 (Rollup)——即使是不可加的复杂度量，也能通过父级节点的预计算查缺补漏精准挂载。
    3. **树形输出 (Phase 4)**：最终结果顺理成章地输出为 9.x 标准的 `format: "tree"` 契约。此方案彻底规避了不同数据库对递归 CTE 支持不一的泥潭，建议作为后续演进的标准能力（不破坏现有底座）。

---

## 四、 推演边界与真实缺口

以上场景可以说明 Foggy 9.x 对主流 BI 透视需求具备较高覆盖率，但不能把所有 MDX 坐标能力都宣称为已等价。以下场景需要作为设计边界或后续增强项明确记录。

### 1. 分组内 TopN 展开 (Generate) — 单层受控子集已支持，级联待实现
**MDX 场景：** 对每个大品类分别取 Top 3 子品类，并把这些非均匀成员合并成行轴集合。
```mdx
Generate(
  [Product].[Category].Members,
  TopCount([Product].[SubCategory].Members, 3, [Measures].[Sales])
)
```
**Foggy 结论：** 已在第三节场景 13 中设计了"字段级局部挂载 (Field-Level Config)"方案（详见上文）。该设计允许在 `rows` 数组中对具体层级字段挂载 `orderBy` + `limit`，引擎自动推断前置字段为分区键，在内存层完成分区排名与截断，零 SQL 方言依赖、零冗余声明、天然支持多级截断扩展。此场景在通用 BI 中属于高频需求（如“每个大区的 Top 3 客户”、“各品类下销量最高的 5 个 SKU”），建议纳入 9.0.0 正式范围而非 post-9.0 增强。

### 2. 多修饰符上下文覆盖
**MDX 场景：** 一个元组中同时固定多个维度坐标。
```mdx
( [Measures].[Sales],
  [Product].[Category].[Electronics],
  [Time].[Year].[2024] )
```
**Foggy 结论：** 多个 `CALCULATE` 修饰符组合在理论上可表达，但需要冻结优先级和冲突规则，例如 `REMOVE(dim)` 与固定同一 `dim` 的坐标同时出现时谁生效、`OFFSET` 与固定时间成员同时出现时如何处理。当前文档只推演了单一修饰符或简单组合的可行性，不能外推为任意嵌套元组已覆盖。

### 3. 度量轴转置与固定报表骨架
MDX 可以把 Measures 放在行轴上，Foggy 当前更自然的方式是查询层返回多度量，结果整形层负责转置。这是可落地路径，但不是查询层原生等价。后续如果要面向中国式财务报表稳定生成，需要把 `metricPlacement`、固定指标顺序、空行/分组标题、指标格式等报表模板契约显式化。

### 4. 任意 N 时间成员窗口
`timeWindow` 已覆盖同环比、YTD/MTD、rolling 7/30/90 day 等高频窗口，但 MDX 的 `LastPeriods(N, CurrentMember)` 是任意 N 时间成员窗口。若业务需要“最近 3 个月均值”“最近 5 个周期间合计”，应扩展 `timeWindow` 的参数化窗口，或提供稳定的 `compose_script window(...)` 原语；否则只能视为部分覆盖。

## 五、 总结：主流覆盖与边界清晰

通过以上对比可以看出，Foggy 不需要复刻 MDX 的上百个函数，就可以覆盖大部分主流 BI 分析场景。这个推演成立的前提不是“100% 兼容 MDX”，而是把高频需求收敛到结构化 JSON、少量上下文修饰符和清晰的结果整形边界。它的本质依赖于四个支柱：
1. **结构化控制抽象**：用 `options.crossjoin`、`properties`、`limit/orderBy`、`rowSubtotals/grandTotal` 替代 MDX 中晦涩的集合操作函数。
2. **TM 元数据托底**：用底层隐式聚合能力承接 `Aggregate()` 的高频使用方式。
3. **8.6.0 上下文计算引擎**：用 `CALCULATE` + `OFFSET` + `REMOVE` + 时间范围语义糖，承接了 MDX 中最复杂的时间智能和视觉占比计算。
4. **CTE 与结果整形边界清晰**：凡是非标准移动窗口、跨模型派生、最终结果二次加工、严格报表模板转置等超出单次 Pivot 的场景，退回 `compose_script` 或结果整形层，不污染核心 Pivot DSL。

因此，本手册的合理结论应是：Foggy 9.x 已用 LLM 友好的 DSL 覆盖 85%+ 主流多维分析需求；S11 已补齐 `parentShare` 第一版，S12 `baselineRatio` 的完全签收使其覆盖率提升至 92%+。其中 `Generate`（分组内 TopN）的单层受控子集已通过 SQL Parity 验收纳入正式支持；`AXIS_MEMBER` 和通用 `CELL_AT` 已 `rejected-for-public-dsl`，其高频基准引用子集已由 S12 `baselineRatio` 完全覆盖；级联 Generate 状态为 `deferred / known-limitation`；对于任意嵌套元组、度量轴原生转置、任意 N 时间成员窗口等 MDX 硬场景，应明确作为设计边界或后续增强项，而不是用绕路方案假装完全等价。
