# MDX 与 Foggy 9.x 语法等价性对比手册

在从传统的重型多维分析（以 MDX 为代表）演进到大模型原生语义层（Foggy 9.x）的过程中，我们并未丢失多维计算的表达力，而是对其进行了深度的**降维与解构**。

本手册旨在通过直接的语法对比，论证 Foggy 9.x (叠加 8.6.0 AST 增强) 是如何使用极度精简的 JSON DSL 平替掉晦涩难懂的 MDX 的。

---

## 一、 核心架构概念映射 (Core Paradigm Mapping)

| MDX 核心概念 | Foggy 9.x 等价表达 | 设计差异本质 |
| :--- | :--- | :--- |
| **Tuple / Tuple Set (元组/集合)** | `rows` 与 `columns` 数组 | MDX 需要用 `{}` 拼接成员集合；Foggy 直接声明字段名，引擎自动拉取集合。 |
| **Member Properties (成员属性)** | `properties` 数组 | MDX 用 `DIMENSION PROPERTIES`；Foggy 提取为与 `rows/columns` 隔离的独立数组，防范 OOM。 |
| **Slicer Axis / WHERE (切片轴)** | `slice` 对象 | 完全等价，用于全局上下文过滤。 |
| **Subcubes (子立方体)** | `compose_script` (CTE 模式) | MDX 用子查询构造 Subcube；Foggy 退回 CTE 进行流转编排。 |

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

---

## 三、 高级分析函数等价对照 (Advanced Analytic Functions)

这里是多维分析的深水区，Foggy 的策略是：**摒弃独立函数，全部收敛到 8.6.0 的 `CALCULATE` 家族或隐式推导中。**

### 1. 智能聚合推导 (Aggregate)
**需求：** 提取度量，并依据底层元数据（SUM, COUNT, LAST_VALUE 等）自动决定聚合方式。
*   **MDX:** `Aggregate(Set, [Measure])`
*   **Foggy 策略:** **隐式推导**。在 `metrics` 数组或 `CALCULATE` 中只要直接引用裸字段 `salesAmount`，底层解析器自动去 TM 元数据中查找 `aggType` 并套用正确的聚合行为。绝不要求前端显式传入。

### 2. 占比计算：打破作用域 (Percent of Total)
**需求：** 计算某个子品类的销售额占整个大类的百分比。
*   **MDX:** 
    利用元组坐标覆盖，强制定位到父级。
    ```mdx
    [Measures].[Sales] / 
    ( [Measures].[Sales], [Product].[Category].CurrentMember.Parent )
    ```
*   **Foggy (基于 8.6.0):** 
    使用 `REMOVE_FILTERS` (类似于 DAX 的 `ALL`) 移除指定的上下文切片。
    ```sql
    salesAmount / CALCULATE(salesAmount, REMOVE_FILTERS(product$subCategory))
    ```

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
      "rows": ["product$subCategory"]
    }
    ```

---

## 四、 总结：降维打击的本质

通过以上对比可以清晰地看出，Foggy 实现“图灵完备的多维表达能力”并没有走 MDX（发明上百个函数）的老路。它的本质依赖于三大支柱：
1. **结构化控制抽象**：用 `options.crossjoin`、`properties`、`limit/orderBy` 替代 MDX 中恶心的集合操作函数。
2. **TM 元数据托底**：用底层隐式聚合能力彻底平替 `Aggregate()` 概念。
3. **8.6.0 上下文魔改引擎**：用 `CALCULATE` + `OFFSET` + `REMOVE_FILTERS` 的极简三板斧，承接了 MDX 中最复杂的时间智能和视觉占比计算。

这使得大模型（LLM）在面对复杂分析需求时，只需要填填 JSON 的坑、套两三个公式即可，彻底终结了“AI 写不对 MDX”的业界级难题。
