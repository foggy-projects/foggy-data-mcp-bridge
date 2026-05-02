# 9.0.0 详细设计 (03)：系统级护栏与 MCP 路由策略

在构建强大的多维内存加工引擎时，最致命的风险就是不受控的笛卡尔积导致内存溢出 (OOM)。同时，既然 9.x 是专为 AI 智能体 (Agent) 设计的语义层，如何通过 MCP (Model Context Protocol) 引导大语言模型 (LLM) 正确使用这些工具也至关重要。

## 一、 系统级护栏：基数熔断器 (Cardinality Circuit Breaker)

当引擎开启 Pivot 模式时，内存超立方体的数据格数 = `行集合基数 × 列集合基数`。多维透视无论是否启用 `crossjoin` 补全，其结果集输出（特别是 `grid` 模式下的矩阵）在存在小计行、多层级树展开时均会产生显著膨胀。如果不对其输出 cell 数施加限制，极易击穿应用内存和网络传输。

### 1. 静态熔断阈值设计
在配置层或租户级别，设定强制阈值，例如 `MAX_PIVOT_CELLS = 100,000`。

### 2. 两段式校验算法 (Two-Phase Verification)
引擎在 Phase 2（内存运算前）必须执行强校验：

*   **前置校验 (预估校验)**：
    *   在提取出完整的 Row Domain 和 Column Domain 后，立即进行乘积预估，涵盖基数和小计膨胀。
    *   `estimated_output_rows = rowDomain.size() * (rowSubtotals ? (1 + row_levels * 0.1) : 1)`
    *   `estimated_output_cols = colDomain.size() * (columnSubtotals ? (1 + col_levels * 0.1) : 1)`
    *   `if (estimated_output_rows * estimated_output_cols > MAX_PIVOT_CELLS) throw new TooManyPivotCellsException(...)`
*   **运行时防御**：
    *   默认开启轴级全局 `limit`。如果用户没有指定，则在最底层的叶子节点上强加一个默认上限（例如：`defaultRowLimit = 1000`, `defaultColLimit = 500`），强制切断不受控的维度发散。

### 3. 父子维度深度熔断 (Hierarchy Depth Breaker)
当轴字段启用 `hierarchyMode: "tree"` 时，动态层级深度可能导致不可加度量的辅助查询数量爆炸。
*   **深度阈值**：`MAX_HIERARCHY_DEPTH = 8`（可租户级配置）。
*   **校验时机**：Phase 1 SQL 编译前，通过闭包表元数据查询 `MAX(distance)`。
*   **超过阈值时**：不可加度量的父级节点显示为 `null`（前端展示为 "-"），可加度量仍在内存中递归累加，不受影响。
*   **基数估算**：树展开后的行域基数应纳入普通的 `MAX_PIVOT_CELLS` 熔断计算。

### 4. 多租户隔离与资源预留
在 SaaS 环境下，即使熔断器生效，大并发的 `CrossJoin` 仍可能造成 CPU 尖峰。对并发的重量级透视请求需排队限流。注意：在水平扩展的集群环境下，不能使用单节点 JVM 级别的 `Semaphore`。应在 API 网关层或使用 Redis 实现分布式 `Execution Slot` 信号量，以限制单个租户跨节点的总并发。

### 5. 错误响应契约

当熔断器触发时，引擎必须返回结构化的错误信息，使 LLM 能够根据错误提示自动降级：

```json
{
  "error": "TooManyPivotCells",
  "message": "行集合基数(50000) × 列集合基数(365) = 18,250,000 超过阈值 100,000",
  "suggestion": "请在行轴或列轴添加 limit 约束，或缩小 slice 范围",
  "details": {
    "rowDomainSize": 50000,
    "colDomainSize": 365,
    "cellCount": 18250000,
    "maxAllowed": 100000
  }
}
```

**降级策略**：
*   **熔断器拦截**：对于 MCP 端点，返回标准 JSON-RPC 错误对象（`code: -32000` 或 `-32602`），内部包含上述结构。对于 REST API，统一使用项目规范的 `RX` 对象包装。LLM 可根据 `suggestion` 自动添加 `limit` 或收窄 `slice`。
*   **SQL 超时**：返回 `QueryTimeout` 错误，建议 LLM 拆分为 `compose_script` 多步执行或添加更严格的 `slice` 条件。
*   **内存 OOM 防御**：在 `Execution Slot` 排队超时时，返回 `PivotResourceExhausted` 错误，建议稍后重试。

---

## 二、 `timeWindow` 在 Pivot 模式下的禁用规则

当请求中包含 `pivot` 节点时，`timeWindow` 必须被显式禁用。原因：

1. `timeWindow` 的语义是"在 SQL 层对时间维度做预定义的窗口展开"（如 rolling_7d、同比等），其产物是在 SELECT 中新增虚拟列。
2. 在 Pivot 模式下，时间维度已经是轴成员（`rows` 或 `columns`），不能同时被 `timeWindow` 展开和作为轴坐标使用，否则语义冲突。
3. Pivot 模式下的时间智能需求应通过 `calculatedFields` + `CALCULATE/OFFSET` 表达（8.6.0 能力）。

**引擎校验规则**：
```
if (request.pivot != null && request.timeWindow != null) {
    throw new InvalidPivotConfigException(
        "timeWindow 与 pivot 模式互斥。时间智能需求请使用 calculatedFields + CALCULATE/OFFSET 表达。"
    );
}
```

---

## 三、 MCP 路由策略与 Prompt 设计规范

### 设计决策：`query_model` 保持唯一入口，`pivot` 作为模式节点

9.0.0 **不新增独立的 `pivot_model` 工具**。`pivot` 是 `dataset.query_model` 的模式扩展，通过 JSON 中是否存在 `pivot` 节点自动切换编译管线：

```
请求中有 columns（无 pivot） → 扁平编译管线（现有行为）
请求中有 pivot（无 columns）  → Pivot 编译管线（9.0.0 新增）
两者互斥，不能同时出现
```

**合并的核心理由**：
1. **CTE 入口统一**：在 `compose_script` 中，`dsl(...)` 是唯一的取数函数。如果 Pivot 是独立工具，CTE 中就会出现两个入口函数，增加 LLM 的函数选择决策成本。
2. **减少工具选择幻觉**：MCP 层从 3 个工具选择（query/pivot/compose）降到 2 个，LLM 做出错误工具选择的概率降低。
3. **Schema 互斥天然清晰**：JSON Schema 中用 `oneOf` 约束 `columns` 和 `pivot` 不能共存，引擎根据有无 `pivot` 键自动切换。

### 1. 扁平/透视统一模式：`dataset.query_model`
*   **工具职责**：负责所有的数据查询，包括明细抽取、一维聚合、多维透视。
*   **Prompt 触发指引**：
    *   "当你需要查询明细列表或按一个维度画图表时，使用 `columns` 模式。"
    *   "当用户明确要求【透视】、【交叉表】、【行和列都有不同的分类标准】时，使用 `pivot` 模式。"
    *   "当用户要求【各层级小计】、【补齐没有数据的月份（全景补全）】时，使用 `pivot` 模式，并将相应选项设为 true。"
    *   "**禁止**：`columns` 和 `pivot` 不能同时出现！"

### 2. 多步流转模式：`dataset.compose_script` (CTE 编排)
*   **工具职责**：跨业务模型关联、漏斗分析、特殊的窗口计算（非自然月的任意 N 个月移动平均）、聚合结果集的二次 Join 加工。
*   **Prompt 触发指引**：
    *   "当你发现需要查询 A 模型，又要查询 B 模型，并将它们组合比较时，使用此工具。"
    *   "当你需要先计算一个聚合结果，然后在该结果之上再进行极为复杂的公式过滤时，使用此工具定义多个查询节点流水线。"
    *   "在 `compose_script` 中，`dsl(...)` 函数同时支持 `columns` 模式和 `pivot` 模式。"

### 3. 上下文修正提示 (Preventing Context Hallucination)
> "如果你需要按某个维度进行截断（例如每个城市的前 3 名），请直接在该维度名称对应的节点上使用 `limit` 属性。不需要声明 partitionBy，排在它前面的维度自动成为它的分区限定键。"

> "如果数据模型包含父子维度（如组织架构、部门树），并且用户要求按层级展开透视，请在对应的轴字段上添加 `hierarchyMode: \"tree\"`。可用 `expandDepth` 控制展开深度（-1 为全展开，0 为仅根节点，N 为展开 N 层）。注意：引擎会自动通过 TM 元数据获取父子关系，不需要手动声明 id 或 parentId 字段。"
