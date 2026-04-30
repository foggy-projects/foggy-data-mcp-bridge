# 8.6.0 设计提案：AST 抽象语法树上下文重写增强 (Context Override)

## 一、 设计背景与目标
在目前的 `dataset.compose_script` 实践中，为了计算跨上下文的指标（如“省份销售额占全国比重”、“部门本月对比上月”），LLM 和开发者必须编写冗长且容易出错的 CTE（Common Table Expressions）以及复杂的自连接（Self Join）。
这使得基于关系代数的 DSL 表达显得极度繁琐，增加了“幻觉”的概率。

**8.6.0 的核心目标：**
采用“战术级改良”策略，在不破坏现有关系型结果集（Tabular ResultSet）的前提下，在 `calculatedFields` 语法树 (AST) 层面局部引入显式上下文覆盖能力，大幅简化跨维度分析代码。

## 二、 核心语法增强提案

提取 MDX/DAX 中对 LLM 最友好的“显式坐标覆盖”概念，废弃引发混淆的隐式上下文转换。

### 1. `CALCULATE(expr, filter_overrides)`
赋予单一单元格突破当前 `groupBy` 过滤域的能力。
*   **语法示例：**
    ```json
    {
      "name": "nationalSales",
      "expression": "CALCULATE(SUM(salesAmount), province = null)" 
    }
    ```
*   **行为语义：** 忽略当前行的 `province` 分组限定，强制在全国范围内计算 `salesAmount` 的总和。

### 2. `OFFSET(expr, field, step)`
将时间或层级偏移剥离到表达式级别，而不依赖顶级的 `timeWindow`。
*   **语法示例：**
    ```json
    {
      "name": "lastMonthSales",
      "expression": "OFFSET(SUM(salesAmount), salesDate$month, -1)" 
    }
    ```
*   **行为语义：** 将计算的上下文偏移到 `salesDate$month` 的前一个周期。

## 三、 引擎编译与执行路径 (Engine Translation)

为了在底层关系型数据库中执行上述 AST，编译引擎采用以下降级降维策略：

1.  **Window Function 优先机制 (推荐路径)：**
    引擎自动分析上下文重写需求，尽可能将其转换为 SQL 原生的窗口函数。例如 `CALCULATE` 移除 `province` 分组，转换为：
    `SUM(salesAmount) OVER(PARTITION BY 除去province之外的当前分组键)`
2.  **Correlated Subquery / Left Join CTE (兜底路径)：**
    如果底层方言不支持复杂的窗口框架，或者出现复杂的 `OFFSET` 跨时间联结，引擎将在内部生成一个子查询，并通过外部主键进行安全的 `LEFT JOIN`。

## 四、 影响边界与收益
*   **零破坏性：** 依然输出 `List<Map>` 格式的结果集，完全兼容现有的前端 `data-viewer` 和报表消费方。
*   **LLM 友好：** 通过单行声明式语法，代替 3 步以上的 CTE 流转代码，预计可消灭 60% 以上关于“跨维度占比计算”的工具调用错误。
