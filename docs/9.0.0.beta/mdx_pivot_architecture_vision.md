# 9.0.0 架构愿景：基于 MDX 思想的多维透视语义层 (Pivot Semantic Layer)

## 一、 设计背景与目标
虽然 8.x 系列解决了"跨上下文指标"的计算痛点，但底层依然局限于 **"关系型表格思维"**。
面对以下场景时，现有架构遭遇物理瓶颈：
*   **中国式复杂财务报表：** 要求固定的行列表头结构（骨架）。
*   **稀疏数据强制补全：** 哪怕某月某大区无销量，也必须输出 0 的网格。
*   **动态多级透视与小计：** 用户随意拖拽行轴、列轴，并要求父级自动 Rollup 汇总。

**9.0.0 的核心目标：**
实现范式级跨越（Paradigm Shift），彻底引入 MDX 的"多维空间坐标系 (Tuple & Context)"思维，打造一套全新的 `dataset.pivot_model` 架构。

## 二、 9.0.0 必须攻克的三大核心战役

### 战役一：定义对 LLM 极度友好的多维输入协议 (Pivot DSL)
彻底摒弃 `SELECT ... GROUP BY` 思维，转变为"绘制网格"思维。设计的 JSON 协议必须包含：
1.  **坐标轴定义 (Axes)：** 显式声明 `columns` (列轴) 和 `rows` (行轴) 的维度层级。
2.  **稀疏补全策略 (CrossJoin)：** 指示引擎是否需要对行列维度表进行笛卡尔积，以生成全量骨架空网格。
3.  **单元格映射 (Cells/Metrics)：** 定义网格交叉点（Tuple）上需要计算和展示的具体度量指标。

### 战役二：引擎编译流水线 (Engine Pipeline) — 修订版
经过深入评估，原始方案中将 `ROLLUP/CUBE/GROUPING SETS` 推入 SQL 层的设计被**否决**。
修订后的核心原则：**SQL 层只做最朴素的聚合，所有高级加工（骨架补全、小计、属性贴合、结果集整形）全部在引擎层（Java/Python 后端内存）完成。**

> 参考：Cube.dev 也不在 SQL 层处理小计，而是将其交给客户端 SDK 在内存中完成。
> 我们将此能力**上移到后端引擎层**，兼顾了性能（避免前端处理大数据量）与方言无关性（不依赖 ROLLUP/CUBE）。

#### 阶段 1：维度萃取 (Axis Extraction)
从 TM 元数据中解析 `rows`、`columns`、`metrics`、`properties`，分离分组键、附属属性和度量表达式。

#### 阶段 2：SQL 聚合（数据库层 — 职责单一）
引擎生成**最朴素的 GROUP BY 聚合 SQL**，绝不推入 ROLLUP/CUBE 等复杂语法。
*   **预聚合快路径：** 引擎首先检查当前 `rows + columns` 维度组合是否命中 QM 中已有的预聚合定义。命中则直接查预聚合表（极速路径），未命中则回退到实时 GROUP BY（兜底路径）。
*   **兼容性保证：** 此阶段生成的 SQL 与现有 `CompiledRelation` / `RelationSql` 完全一致，零方言差异风险。

#### 阶段 3：引擎内存加工（核心新增能力层）
*   **3a. 轴排序与截断 (Axis Ordering & TopN)：** 如果行轴或列轴定义了 `orderBy` 和 `limit`，引擎在内存中按度量值排序，截取 Top N 成员（详见第六节）。
*   **3b. 骨架补全 (CrossJoin Fill)：** 若 `crossjoin: true`，引擎在内存中对行轴和列轴的全量值集做笛卡尔积，将缺失的交叉点以 0/null 填充。
*   **3c. 小计注入 (Subtotal Injection)：** 引擎按 `rows` 中定义的层级进行内存聚合，生成带有 `_isSubtotal: true` 标记的汇总行。此方式可正确处理"百分比类指标不能简单 SUM"的语义陷阱。
*   **3d. 属性贴合 (Property Decoration)：** 通过内存 Map 查找，将 `properties` 字段延迟关联到对应的行节点上。

#### 阶段 4：结果集整形 (Shaping)
将扁平数据按照战役三中敲定的契约（树形或网格）重塑后返回给前端。

#### 技术栈决策
*   **零外部依赖：** 全部基于现有 AST 降维展开 (Desugaring)，不引入 Calcite、Mondrian 或任何第三方框架。
*   **双引擎同步：** Java 侧先定义 `PivotCompileContext` 和内存加工逻辑并冻结快照，Python 侧镜像复刻。

### 战役三：结果集契约的自适应输出 (Result Set Contract)
扁平的 `List<Map>` 数据结构无法优雅承载带有复杂表头跨度（RowSpan/ColSpan）的透视数据。
9.0.0 在结果集输出上采取 **"全模态支持 (Multi-Modal Output)"** 的策略，把最终数据结构的决定权交给上游调用方（通过 DSL 中的 `outputFormat` 参数指定）：

*   **格式 A：树形嵌套 (Tree Structure)**
    *   **形态：** 以行轴为骨架生成父子 `children` 树，列轴压平为键值对。
    *   **适用场景：** 前端标准 Tree-Table 直接渲染；LLM 快速阅读理解（层级结构符合自然语言习惯）。
*   **格式 B：平铺坐标系 (DataCell Grid)**
    *   **形态：** 彻底分离 `RowHeaders` 和 `ColumnHeaders`，数据区为纯粹的二维矩阵 `cells[x][y]`。
    *   **适用场景：** 专业级交叉表组件（如虚拟滚动网格、Handsontable）；图表库（如 ECharts）的数据装载引擎；大批量数据的网络传输（消除所有 JSON Key 冗余）。
*   **格式 C：带元数据的扁平集 (Flat with Meta)**
    *   **形态：** 依然返回 `List<Map>`，但在数据行中附加 `_isSubtotal` 和 `_level` 等系统元数据标记。
    *   **适用场景：** 兼容老版本组件降级渲染，或纯粹的 CSV 导出。

引擎层在内存中完成"骨架补全"和"小计注入"后，其内部已形成高度统一的多维抽象树，因此最后一步的"结果集整形 (Shaping)"可以极低成本地转换为上述任意格式。

## 三、 演进路线图
由于 9.0.0 的改动涉及到底层编译引擎与前端渲染器的双向重构，本方案将作为大版本迭代的战略蓝图。在 8.x 的单点增强打磨完毕前，不建议在主线分支提前启动底层改造。

## 四、 LLM 工具调用边界契约 (Decision Tree)
随着 9.0.0 引入多维透视能力，引擎对外暴露的能力将分为三种模式。为了确保 LLM 和开发者能够准确命中，必须遵循以下边界划分规则：

### 1. 扁平查询模式：单模型 `columns` (默认)
*   **适用场景：** 数据明细列表、简单的柱状图/折线图取数、基于单一维度的主体聚合排序。
*   **典型特征：** "查询订单明细"、"按月统计总销售额"、"销量排名前十的客户"。
*   **实现：** 继续使用现有的 `dataset.query_model` 工具，通过 `columns` 进行投影，引擎自动隐式推断 `groupBy`。

### 2. 多维透视模式：单模型 `pivot` (9.0.0 新增)
*   **适用场景：** 用户意图在 **二维空间** 展开数据；要求输出带有行头和列头的交叉报表；要求强制补全稀疏时间网格；要求各层级数据的自动小计/总计。
*   **典型特征：** 包含关键词"交叉报表"、"各月对比表"、"带小计"、"透视"。
*   **实现：** 使用扩展后的 `dataset.query_model` 工具，弃用 `columns`，改用 `pivot` 节点定义 `rows`、`columns` 和 `metrics`。此时所有 `metrics` 默认为 TM 中定义的聚合方式。

### 3. 多步流转模式：CTE 脚本编排 (`compose_script`)
*   **适用场景：** 超出单模型边界；需要跨业务域进行联合分析（如销售关联库存）；或者极其复杂的派生计算（如先算出每个用户的首单日期，然后再进行漏斗分析）。
*   **典型特征：** 包含跨模型的 JOIN、数据集合的 UNION，或者针对聚合结果集再进行 WHERE/HAVING 过滤。
*   **实现：** 坚决退回使用 `dataset.compose_script`，通过 FSScript 流水线构建中间变量和代数关系。

通过以上三层结构的互补，系统将在保证极低心智负担的同时，实现图灵完备的数据分析表达能力。

## 五、 维度分组轴 (Level) 与 附属属性 (Property) 的动态解耦

在传统 MDX 和部分重型 BI 模型中，Level（参与 GroupBy 的主维度）和 Property（仅作为辅助展示的属性）的边界是在元数据 (TM) 层严格锁死的。但这极大限制了查询的灵活性（例如"门店类型"在场景 A 中是分组轴，在场景 B 中退化为附属属性）。

9.0.0 方案通过在 DSL 查询层引入显式的 `properties` 节点，将判定权交给当次查询的 LLM 或开发者：

### 1. 动态解耦的 DSL 设计
*   **作为轴 (Level)**：当字段决定了数据的聚合粒度时，将其放入 `columns`（扁平模式）或 `rows/columns`（透视模式）。
*   **作为属性 (Property)**：当字段仅仅作为主维度的补充说明（如客户手机号、商品品牌），不能破坏聚合粒度时，放入 `properties` 数组。

```json
{
  "model": "FactSalesQueryModel",
  "pivot": {
    "rows": ["customer$name"],
    "columns": ["salesDate$year"], 
    "metrics": ["salesAmount"],
    "properties": ["customer$phone", "customer$memberLevel"]
  }
}
```

### 2. 底层编译的降维优化
当引擎检测到 `properties` 节点时，绝不会将其加入底层 SQL 的 `GROUP BY` 子句，从而彻底杜绝数据库因为对冗长字符串或多余列执行 Hash Aggregation 而造成的性能灾难。引擎将采用以下策略：
*   **智能伪聚合 (Smart Aggregation)：** 在 SELECT 处自动包裹 `ANY_VALUE()` 或 `MAX()`。
*   **延迟关联 (Post-Join)：** 仅拿核心分组轴去事实表极速聚合，完成后在外层再 `LEFT JOIN` 维度表带出属性。

### 3. 业界方案对比复盘
对比目前流行的 Headless BI（如 Cube.dev），Cube 的查询协议中默认没有 `properties` 节点，用户请求的所有非度量字段都会被统一塞入 `dimensions` 数组，最终不可避免地生成极度臃肿的 `GROUP BY`。
我们的 **"DSL 显式解耦 + LLM 意图推断"** 方案，不仅保持了 TM 元数据定义的纯净性，还通过赋予 LLM 区分主次的能力，在底层 SQL 性能和语义表达的准确性上，反超了部分传统 Headless BI 的"一刀切"做法。

## 六、 轴级分页与 Top N (Axis-Level Pagination)

在透视表场景中，传统的 `LIMIT/OFFSET` 语义不再适用，因为透视表有两个独立的轴。分页和 Top N 必须**按轴独立定义**。

### 1. 语义定义
*   **行轴分页/Top N（最常见）：** "销量前 10 的品类" — Top N 作用在行轴，列轴（月份）保持完整。
*   **列轴截断（偶尔）：** "最近 6 个月的趋势" — 截断作用在列轴，行保持完整。
*   **双轴限制（极少）：** 前 10 品类 x 最近 3 个月。

### 2. DSL 表达（轴对象化）
为支持轴级控制，`rows` 和 `columns` 从简单的字符串数组升级为可选的对象形式：

```json
{
  "pivot": {
    "rows": {
      "fields": ["product$categoryName"],
      "orderBy": ["-salesAmount"],
      "limit": 10
    },
    "columns": {
      "fields": ["salesDate$year", "salesDate$month"],
      "orderBy": ["salesDate$year", "salesDate$month"],
      "limit": 6
    },
    "metrics": ["salesAmount"],
    "options": { "crossjoin": true, "rowSubtotals": true }
  }
}
```

> 当 `rows` / `columns` 为简单的字符串数组时，等价于 `{ fields: [...] }`，无排序无截断。

### 3. 引擎执行顺序
行轴的 `orderBy` 可以引用度量值（如 `-salesAmount`），这意味着必须**先完成聚合才能决定哪些行有资格留下**。执行顺序为：
1.  SQL 层：全量朴素聚合（不带 LIMIT）。
2.  引擎层：按度量值排序行轴，截取 Top N 行成员。
3.  引擎层：按列轴定义排序并截断列成员。
4.  引擎层：在截断后的成员空间内做骨架补全和小计注入。
