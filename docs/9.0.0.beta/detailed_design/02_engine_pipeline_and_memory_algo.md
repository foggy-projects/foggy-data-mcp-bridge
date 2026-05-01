# 9.0.0 详细设计 (02)：编译流水线与内存加工算法核心

在 `foggy-dataset-model-pivot` 架构中，最核心的理念是**职责分离**：将底层数据库不擅长（或各方言表现不一）的多维运算抽离到后端内存层完成。

## 一、 核心流水线概览 (Pipeline Pipeline)

当引擎接收到 `PivotQueryRequest` 后，会经历以下 5 个标准 Phase：

1. **Phase 1: SQL Compilation & Execution (SQL 萃取与下推)**
   将透视请求降维成最朴素的关系型 `GROUP BY` 查询，推给数据库。简单算术型 `calculatedFields` 也在此阶段下推为 SELECT 表达式。
2. **Phase 2: Memory Cube Operations (内存超立方体运算 - 核心!)**
   执行轴级 `having`、`TopN` 截断、笛卡尔积补全 (`CrossJoin`)、小计注入 (`Subtotals`)。
3. **Phase 3: Post-Join Properties (附属属性贴合)**
   在截断和小计完成后，只为存活的行执行延迟关联或字典映射，避免为已截断的行做无谓的 Property 查找。
4. **Phase 4: Shaping (形态塑造)**
   将超立方体转换为 `Tree` / `Grid` / `Flat` 契约结构输出。

---

## 二、 Phase 1: SQL 萃取与下推策略

**目标**：基础明细聚合查询只下推普通 `GROUP BY`，不把 `ROLLUP`、`CUBE`、`GROUPING SETS` 作为主路径依赖。小计/总计所需的辅助查询可在后续阶段按方言能力选择 `UNION ALL` 或数据库原生汇总语法，但不能污染基础查询模型。

### 1. 维度汇聚 (Dimension Flattening)
从 AST 中提取出所有的“纯维度”。
*   聚合键 (GroupBy Keys) = `pivot.rows` 中所有字段 + `pivot.columns` 中所有字段。
*   度量键 (Metrics) = `pivot.metrics` 中所有度量。

### 2. 附属属性剥离 (Property Stripping)
如果 `pivot.properties` 不为空，**绝不将其放入 GROUP BY**。引擎采取基于 TM 函数依赖证明的**确定性属性贴合**策略：
*   **证明唯一性**：引擎利用底层元数据 (TM) 解析 `properties` 所依赖的维度表层级。只有当证明当前的 `groupBy` 键完全包含了该属性所属的主键/外键（即满足严格的函数依赖关系，保证绝对唯一）时，引擎才允许在 Phase 1 的 SELECT 子句中使用 `ANY_VALUE(property_col)` 伪聚合，利用数据库极速 Hash Aggregation 避免长字符串干扰。
*   **后置 Join 与 Fail-closed**：如果无法证明函数依赖（即该属性在当前分组下可能存在多值），引擎**严禁**随意使用 `MAX()/ANY_VALUE()`，因为这会导致返回随机或误导性的业务值。此时，引擎应将该属性推迟到 Phase 3 进行内存 Post-Join；如果在内存中发现确实存在多值冲突，则严格遵循 fail-closed 策略抛出异常（或降级为 JSON 数组），绝不返回歧义单值。

> **阶段产出**：获得一个标准的、扁平的 `ResultSet (List<Map>)`，代表当前条件下的“原始稠密数据”。

### 3. 父子维度隐式列注入 (Parent-Child Implicit Injection)
当引擎检测到 `AxisField.hierarchyMode == "tree"` 时，通过 TM 元数据获取该维度的 `primaryKey`（如 `department$id`）和 `parentKey`（如 `department$parentId`），并将它们集式注入到 SELECT 子句中：
*   使用 `ANY_VALUE()` 伪聚合（与 Properties 同策略），**不放入 GROUP BY**。
*   这两个列仅用于 Phase 2 的内存建树，不出现在最终输出中。
*   闭包表不参与 Pivot 轴展开的 SQL 编译，仅在 `slice` 过滤（如 `descendantsOf`）时复用 8.x 已有逻辑。

---

## 三、 Phase 2: 内存加工算法 (Memory Cube Algorithms)

这是 9.x 引擎含金量最高的环节。

### 1. 轴级聚合后过滤 (Axis Having)
`AxisField.having` 表示“当前轴成员是否允许进入结果域”的聚合后过滤，语义上对应 MDX `Filter(Set, [Measures].[X] > value)` 的高频用法。

**执行位置**：基础聚合完成之后，`orderBy/limit` 之前。

**算法步骤（以行轴为例）**：
1. 根据当前轴字段形成候选成员域，例如 `product$category`。
2. 读取 Phase 1 已经聚合出的度量值，例如 `salesAmount`。
3. 对候选成员执行谓词判断，例如 `salesAmount > 1000000`。
4. 删除不满足条件的成员以及其下游子成员。
5. 将过滤后的成员域传给后续 `TopN` 和 `CrossJoin`。

> **执行顺序示例**：假设某城市下有 50 家门店，`having` 条件过滤掉了销量 < 1000 的 30 家门店。如果此时配置了 `limit: 10`，那么 TopN 是从剩余的 20 家达标门店中取前 10 家，而不是在最初的 50 家里取前 10 家后再做 Having 过滤。

> **边界说明：** `having` 只允许引用当前 Pivot 聚合结果中的度量和计算字段。若谓词依赖窗口计算、跨模型 Join、最终 shaped result 或外部查询结果，应退回 `compose_script`。

### 1.5 父子维度动态建树 (Parent-Child Tree Building)
当存在 `hierarchyMode == "tree"` 的轴字段时，在 Having 之后、TopN 之前执行：

**算法步骤**：
1. **预查维度骨架**：为避免 `slice` 过滤导致事实表中没有父节点记录（即 Phase 1 聚合结果中缺失中间层级，导致 `parentId == null` 寻根失败），引擎必须额外查询一次维度表，获取当前 `slice` 范围内的完整父子树结构骨架。
2. **合并骨架与事实数据**：将维度表的骨架与 Phase 1 `ResultSet` 隐式注入的 `id`/`parentId` 数据在内存中合并。没有事实数据的中间节点度量置为 null。
3. 以骨架中 `parentId == null` 的节点为根，递归构建多叉树。
4. 根据 `expandDepth` 截断展示层级：超过 `expandDepth` 的子节点不展开，但其度量值向上汇总到展开的最深层节点。
5. 将树形结构和扁平 `ResultSet` 同时保持在内存中，供后续 TopN/Subtotal 使用。

> **与 TopN 的交互**：当 `hierarchyMode == "tree"` 与 `limit` 同时存在时，`limit` 的语义为"每个父节点下取 Top N 子节点"，与字段级截断的隐式分区逻辑一致。截断在建树完成后、CrossJoin 之前执行。

### 2. 轴向截断算法 (Axis TopN Truncation)
根据 `AxisField` 上的局部截断配置，在内存中进行排序淘汰。

**算法步骤（以行轴为例）**：
1. 提取出当前配置了 `limit` 的节点，例如 `city$name (limit:3, orderBy: -sales)`。
2. 查找其所有的隐式父节点，例如 `region$name`。
3. 利用 Stream API 或内存字典树，对 `ResultSet` 按 `region$name` 进行分组 (Partition)。
4. 在每个组内，按 `sales` 进行降序排列。
5. 丢弃排序超过 `limit (3)` 的数据行。
6. 更新内存中的有效成员集合。

这可以覆盖 `Generate(parentMembers, TopCount(childMembers, N, metric))` 的受控子集：父级和子级都必须在同一个轴路径上，`limit` 只表达“每个隐式父级分组内保留前 N 个子成员”。如果 MDX `Generate` 的第二个参数不是简单 TopN，而是任意集合表达式或跨轴集合拼接，则不进入 Pivot 引擎主路径。

### 3. 骨架补全算法 (CrossJoin Cartesian Fill)
当 `options.crossjoin == true` 时触发。

**问题背景**：SQL 返回的结果只包含有事实数据的格子（稠密集）。财报需求要求即使华东区3月无销量，也要输出一行/列。
**算法步骤**：
1. **抽取行域 (Row Domain)**：遍历截断后的 `ResultSet`，提取出所有的合法行键组合集合 (Set<RowTuple>)。
2. **抽取列域 (Column Domain)**：提取出所有的合法列键组合集合 (Set<ColTuple>)。
3. **笛卡尔积**：生成完整的 `Domain Matrix = Row Domain x Column Domain`。
4. **填充**：遍历矩阵，如果坐标点在 `ResultSet` 中不存在，则凭空构造一个数据点，将其度量值置为 `null` 或 `0`。

### 4. 小计与总计注入 (Subtotal & Grand Total Injection)
这是在多维树形结构中生成“父级节点”的过程。

**挑战与陷阱 (Non-Additive Measures)**：
绝不能在内存中简单地对子节点求和（如 `利润率 = 利润/销售额`，不能写成 `利润率A + 利润率B`）。

**算法策略 (The Double-Pass Rule)**：
为了保证小计完全正确，分为两种情况：
*   **情况 A：纯可加度量 (Additive - 如 SUM, COUNT)**
    可以直接在内存中执行自底向上的 Rollup 累加，极速完成。
*   **情况 B：不可加度量 (Non-Additive - 如 COUNT DISTINCT, 自定义计算比率)**
    引擎需要先做度量可加性判定和计算字段依赖分析，再枚举 row subtotal、column subtotal、grand total 所需的父级 grain。不可加度量必须通过辅助聚合查询获得父级值，并写入 `RollupCache`；`SubtotalInjector` 只负责按坐标注入 cache 值，绝不允许对子节点结果做 SUM。详细设计见 `04_non_additive_rollup_design.md`。
*   **情况 C：父子维度的不可加度量**
    父子维度的层级数动态不固定，第一版建议 fail-closed：`hierarchyMode=tree + non-additive metric + rowSubtotals/grandTotal` 直接拒绝。后续如需支持，应基于闭包表枚举每个父节点的 descendants，并对最大深度和节点数量做熔断。

**小计节点打标**：
生成的父级汇总节点，必须强行打上 `_sys_meta: { isRowSubtotal: true, level: ... }` 的标签，以便进入最后的整形阶段。

---

## 四、 Phase 3: 附属属性贴合 (Post-Join Properties)

在截断和小计完成后，引擎只为 **存活的行** 执行属性贴合。此阶段放在 TopN/Subtotal 之后的原因是：如果先贴合 Properties 再截断，被截断的行也会执行无意义的维度表查找，浪费资源。

**贴合策略**：
*   如果 Phase 1 已通过 `ANY_VALUE()` 伪聚合带出了 Properties，则直接使用，无需额外查询。
*   如果 Phase 1 采用了纯净聚合（未带出 Properties），则在此阶段通过内存 Map 查找或延迟 `LEFT JOIN` 补充。

---

## 五、 Phase 4: 多模态整形算法 (Shaping Algorithms)

内存加工完毕后，数据本质上已经是一个标准的超立方体 (HyperCube) 抽象。现在根据 `outputFormat` 变形。

### 1. `Grid` 形态抽取
*   **RowHeaders 生成**：按顺序遍历 Row Domain，生成行表头数组，保留 Subtotal 标记。
*   **ColumnHeaders 生成**：由于列也可能有多个度量，列头不仅包含列维度的组合，还必须在最底层(Leaf Level) 加上一维度量名称，形成完整的坐标系。
*   **Data Cells 填充**：按 `[RowIndex][ColIndex]` 抽取度量值填充二维数组。
*   **Metric Placement**：默认 `metrics` 是列头叶子层；当 `layout.metricPlacement = "rows"` 时，Shaping 阶段将 metric 维转置到 `rowHeaders` 叶子层。这一步只改变坐标摆放，不重新聚合，不允许引入未在 `metrics` 中声明的度量。

### 2. `Tree` 形态抽取
采用典型的深度优先搜索 (DFS) 或多级字典建立算法：
1. 将 RowTuple 按照维度层级展开为路径，例如 `['华东', '上海']`。
2. 按路径构建多叉树 `TreeNode`。
3. `isRowSubtotal = true` 的节点，其数据挂载在当前层级的 `cells` 属性中。非 Subtotal 节点则存放在叶子 `TreeNode` 的 `cells` 属性中。
4. Column 坐标直接通过字符串分隔符连接（如 `2024|Q1|salesAmount`）压平为 KV 键值对，挂载在 `cells` Map 中。

### 3. `Flat` 形态抽取
最简单的整形模式，本质是将超立方体重新展平为行式记录：
1. 遍历内存超立方体中的每个数据点（包含小计节点）。
2. 将行维度 + 列维度 + 度量值平铺为一个 Map。
3. 在每行中注入 `_sys_meta` 元数据标记（`isRowSubtotal`、`isColSubtotal`、`rowLevel`）。
4. 输出为标准的 `List<Map>` 结构，兼容老版本组件和 CSV 导出。

---

## 六、 不进入 Pivot 主路径的能力边界

1. **任意 N 成员窗口**：`LastPeriods(3, CurrentMonth)` 这类“最近 N 个时间成员”不是 Phase 3 的内存立方体操作。标准 rolling 7/30/90 day 优先走 `timeWindow`；任意 N 成员窗口应由 `compose_script` 的稳定窗口原语承接。在该原语未实现前，只能标注为能力缺口，不能在文档中假设 `window(...)` 已存在。
2. **父级坐标导航 (`ROLLUP_TO`)**：如果计算字段需要“当前子品类占其所属大品类”，必须依赖表达式引擎识别当前单元格父级坐标。该能力不是普通 `GROUP BY` 后处理，也不能用 `REMOVE(childDim)` 代替；实现前应被 Guardrail 拦截或降级为“不支持”。
3. **跨轴绝对坐标引用 (`AXIS_MEMBER`) — 已设计，待实现**：`CALCULATE(metric, AXIS_MEMBER('columns', 0))` 在 Phase 1 编译阶段被降维为 SQL 窗口函数 `NTH_VALUE(metric, 1) OVER (PARTITION BY rowDims ORDER BY colDims)`，属于 `calculatedFields` 的 SQL 下推路径，不需要引擎内存中持有完整坐标系。限制：仅支持按序数位置引用（第 0 个、第 N 个），不支持任意命名成员跨轴寻址。
