# 语义层架构深度剖析与演进分析 (TM/QM、DSL/CTE、MDX)

这是一份针对当前语义层架构（TM/QM）、查询编排能力（DSL/CTE）以及未来演进方向（引入 MDX/DAX 思想）的深度分析报告。

## 一、 目前 tm/qm 定义与 DSL / CTE 编排能力的深度剖析

### 1. 架构抽象的本质
* **TM (Table Model):** 本质是一个基于星型/雪花模型的 **逻辑宽表 (OBT, One Big Table)** 的映射层。它通过 `dimensions` 封装了事实表到各维度表的关联路径（Join Paths），并定义了基础的度量（`measures`）和属性。
* **QM (Query Model):** 本质是面向特定业务场景的 **语义视图**。它基于 TM 进行字段挑选、分组（`columnGroups`），并沉淀了业务特定的高级分析字段（如移动平均 `ma7`、利润率等公式），屏蔽了底层的物理结构。

### 2. 它们解决了什么问题？
* **单 DSL (`dataset.query_model`) 解决的问题：**
    * **消除 SQL 组装的繁琐与易错性：** 自动推断 `groupBy`，自动处理隐式的维度表 `JOIN`，极大地降低了生成基础报表的门槛。
    * **标准化复杂业务逻辑：** 声明式的 `timeWindow` 直接将同环比、滚动计算抽象为配置项，避免了让人类或 LLM 手写极其复杂的日期偏移和自连接 SQL。
    * **层级查询的平民化：** 通过 `$hierarchy$id` 和 `descendantsOf` 操作符，将复杂的闭包表/树形结构查询扁平化。
* **CTE 编排 (`dataset.compose_script`) 解决的问题：**
    * **突破单模型的边界：** 解决了跨业务域的联合查询（Join/Union，如销售数据关联库存数据）。
    * **多阶派生计算 (Multi-pass Aggregation)：** 解决了 SQL 中 `HAVING` 和嵌套子查询的噩梦。例如“先按月求和，再求各月的平均值，最后筛选出高于平均值的月份”，通过步骤化的 `dsl({...})` 变量引用，化解了 SQL 的洋葱式嵌套。
    * **控制流与安全隔离：** 用 FSScript 作为沙盒，既赋予了关系代数（Relational Algebra）的图灵完备表达力，又杜绝了原生 SQL 注入的风险。

---

## 二、 哪些问题无法解决，或解决极其不优雅？（心智负担所在）

尽管 DSL 和 CTE 解决了“如何把表拼起来计算”的问题，但面对深度的商业智能分析，**它们依然受困于“关系型/关系代数（Relational）”的底层思维逻辑（基于表和行）**。这导致了以下极其不优雅的痛点：

### 1. 结构占比与跨层级计算（上下文撕裂）
* **场景：** 计算“某省份的销售额占全国总销售额的比重”，或者“某子品类占该父品类的比重”。
* **痛点：** 在 SQL/CTE 思维下，你必须：
    1. 写一个 CTE 算出全国总销售额。
    2. 写一个 CTE 算出各省份销售额。
    3. 将两者 Cross Join 或使用复杂的 Window Function (`SUM(sales) OVER()`) 关联起来相除。
* **心智负担：** 对于 LLM 和人来说，**“为了获取一个不同上下文的值，我必须大动干戈去建表并关联”**，这完全违背了人的直觉。

### 2. 复杂的交叉维度比较（多维坐标偏移）
* **场景：** 对比“华东区 3 月的 A 产品销售额” 与 “华北区 2 月的 B 产品销售额”。
* **痛点：** 当前的 `timeWindow` 虽然解决了时间轴的单维偏移，但如果是时间和空间的双重偏移，CTE 必须构建多棵子树然后精准书写复杂的 `ON` 条件进行 `JOIN`。稍微写错一个关联条件，数据就会出现笛卡尔积或丢失。

### 3. 固化行列的“稀疏/稠密”转换（补零问题）
* **场景：** 财务报表要求雷打不动地展示 1-12 月，哪怕某个月没有销售额也要展示 0。
* **痛点：** 在当前模型下，必须在 CTE 里做一个完整的 `DimDate` CTE，去 `Left Join` 你的业务聚合结果，并加上 `COALESCE`。LLM 极难一次性写对这种维表前置的补全逻辑。

### 4. `calculatedFields` 中聚合与标量的界限模糊
* 目前的 `calculatedFields` 强依赖于窗口函数（`partitionBy`）。当 LLM 需要做条件计算时（如计算只包含 VIP 的客单价），它容易混淆是在 `columns` 里写 `sum(if(...))` 还是在 `calculatedFields` 里写。

---

## 三、 借助 MDX 思想的破局之道（并非强行引入 MDX）

MDX (Multi-Dimensional eXpressions) 的核心精髓并不在于它的语法（MDX 语法本身极其晦涩），而在于它的 **“多维空间坐标系 (Tuple & Context)”** 思维。微软后来的 DAX 语言就是完美继承了 MDX 的思想，并包上了关系型的外衣。

如果我们在语义层借鉴这种思想，可以引入以下能力，实现降维打击：

### 1. 上下文重写 / 计算器模式 (Context Override)
与其通过 CTE 去 JOIN 全局数据，不如在表达式层面允许 **“改变计算上下文”**。
类似 DAX 的 `CALCULATE` 函数：
```json
// 在当前的 calculatedFields 中引入上下文过滤器
{
  "name": "nationalSales",
  "expression": "CALCULATE(SUM(salesAmount), REMOVE_FILTERS(province))"
}
{
  "name": "contributionRate",
  "expression": "SUM(salesAmount) / nationalSales"
}
```
**价值：** 一行表达式干掉了一个 CTE 和一次自连接。LLM 只需要理解“我想在什么过滤条件下计算这个指标”，心智负担降至最低。

### 2. 相对坐标导航 (Relative Navigation)
借鉴 MDX 的 `CurrentMember.Parent` 概念。
```json
{
  "name": "parentCategorySales",
  "expression": "CALCULATE(SUM(salesAmount), product$category = CURRENT(product$category).Parent)"
}
```
**价值：** 处理层级结构的占比分析如履平地。

### 3. 剥离时间智能到度量内部
现在的 `timeWindow` 是一种“宽表展开”逻辑（直接生出几列），这在单 DSL 中很方便，但在多步计算中很死板。借鉴 MDX/DAX，将时间转换视为**特殊的上下文偏移**：
```json
{
  "name": "lastMonthSales",
  "expression": "OFFSET(SUM(salesAmount), salesDate$month, -1)"
}
```
**价值：** 可以将时间偏移的结果作为中间变量，自由地参与到其他复杂的数学公式中，不再受限于固定的输出列名结构。

---

## 四、 三种模式的场景与优缺点总结

结合现状与未来的演进可能，单 DSL、CTE、以及融合了 MDX 思想的模式（CTE_MDX）各自有着明确的生态位：

### 1. 单 DSL 模式 (Query Model)
* **解决场景：** 基础取数、单表探查、标准的列表和聚合图表、标准的时间同环比看板。
* **优点：** 
    * **极简结构：** JSON 声明式，没有控制流。
    * **LLM 友好度极高：** 给定 Schema，LLM 几乎 100% 能生成合法的 JSON，没有语法闭合和变量作用域的问题。
    * **引擎执行极快：** 一对一翻译为单条原生 SQL。
* **缺点：** 表达力天花板极低，无法处理跨域、多层计算。

### 2. CTE 模式 (Compose Script)
* **解决场景：** 跨模型数据融合（如 销量 + 库存预警）、漏斗分析、归因分析、分步式的复杂指标计算。
* **优点：**
    * **图灵完备的关系代数：** 只要 SQL 能写出来的，它都能通过变量流转写出来。
    * **调试与中间态可见：** 变量赋值机制让调试和查看中间执行计划变得非常直观。
* **缺点：**
    * **代码冗长 (Boilerplate)：** 简单的同环比/占比逻辑需要大量的模板代码和 JOIN。
    * **LLM 幻觉重灾区：** 多次 JOIN 后，左右两侧的列名空间容易混淆，LLM 极易在第 3 步引用了不存在的列。

### 3. CTE_MDX 融合模式 (未来演进 / 关系与多维的统一)
* **定义：** 在外围依然保持 CTE（流程式管道）处理数据集级别的 Join/Union，但在**投影表达式内部（Calculated Fields）**引入 MDX/DAX 的 **Tuple 与 Context 切换**能力。
* **解决场景：** 极度复杂的财务报表、动态维度占比分析、复杂的业务假设测算（What-if 分析）、灵活的动态行列透视。
* **优点：**
    * **消灭冗余的自连接：** 把复杂的表格逻辑降维成了数学公式逻辑。
    * **极低的心智负担：** 完美契合业务人员的“单元格”思考习惯（“我想要当前格子的值 除以 总计格子的值”）。
* **缺点：**
    * **底层编译极其复杂：** 引擎需要将 DAX/MDX 风格的上下文变换，逆向编译为兼容各数据库底层的 SQL Window Functions 或高度优化的 Correlated Subqueries，开发成本巨大。
    * **人类/LLM 的概念混淆：** 虽然写公式简单了，但必须引入并理解“行上下文 (Row Context)”与“筛选上下文 (Filter Context)”的深刻区别（这也是 PowerBI/DAX 初学者最大的噩梦）。

---
**总结建议：**
目前基于 `query_model` 和 `compose_script` 的组合已经具备了强大的工程落地能力。若要解决 LLM 生成复杂分析的痛点，**无需全盘推翻引入真正的 MDX 引擎（这太重了）**。

最优雅的路径是在现有的 `calculatedFields` 语法抽象树 (AST) 中，**局部引入 `CALCULATE(expr, filter_overrides)` 函数**，以及简单的 `OFFSET()`。这样既保留了关系型数据库作为底座的轻量级优势，又赋予了单表 DSL 强大的“跨维度/跨上下文”表达力，能将 60% 需要用 `compose_script` 绕路解决的问题拉回到单 DSL 的舒适区中。
