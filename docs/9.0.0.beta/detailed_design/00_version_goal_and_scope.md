# 9.0.0.beta 版本目标与边界收口 (Goal & Scope Freeze)

本文档是 9.0.0 Pivot 语义层迭代的最高纲领文件。所有相关设计文档、代码开发和测试验收均以此为准。

## 一、 版本目标 (Version Goals)

9.0.0.beta 的核心目标是**为 LLM Agent 提供一个安全、无歧义且能力强大的多维透视接口**，弥补 8.x 系列只能输出明细流水表或一维聚合表的短板。

### 核心能力范围 (In Scope)
1.  **Pivot DSL 契约**：作为 `dataset.query_model` 的扩展模式，支持显式定义 `rows` (行轴) 和 `columns` (列轴)。
2.  **四阶段内存引擎**：实现 `Phase 1 SQL` -> `Phase 1.5 建树` -> `Phase 2 聚合与补全` -> `Phase 3 属性与整形` 的标准流水线。
3.  **零外部依赖**：仅使用 Java/Python 内置库实现内存数据立方体（Memory Cube），杜绝引入外部 OLAP 引擎或 SQL 转化库。
4.  **智能小计 (Subtotals)**：支持行轴和列轴的自动小计，特别是针对不可加度量（Non-Additive Measures）的辅助查询批次下推策略。
5.  **父子维度支持**：引入 `hierarchyMode: "tree"` 支持隐式层级展开和动态树聚合。
6.  **安全护栏体系**：实现基于 `MAX_PIVOT_CELLS` 的两段式（预估+运行时）基数熔断，防范 OOM。

### 非目标与能力禁用 (Out of Scope / Disabled)
1.  **不再新增 `dataset.pivot_model` 工具**：Pivot 能力完全合并入 `dataset.query_model`，通过 JSON schema 中的 `pivot` 节点触发。
2.  **`AXIS_MEMBER` — `rejected-for-public-dsl`**：因多层列轴下的语义歧义，该跨轴引用语法不作为 LLM 可生成的公开 DSL（等价高频场景已通过 S12 结构化 `baselineRatio` 覆盖）。
3.  **`CELL_AT` — `rejected-for-public-dsl`**：坐标漫游复杂度过高，不适合作为 LLM 可生成接口（等价高频场景已通过 S12 结构化 `baselineRatio` 覆盖）。
4.  **禁用 `timeWindow`**：当请求处于 Pivot 模式时，严禁使用 8.x 的 `timeWindow`，时间智能需通过 `calculatedFields` 表达。
5.  **级联 Generate — `deferred / known-limitation`**：不支持 MDX 中随意的跨集合合并与复杂遍历，级联多层截断暂缓。
6.  **`ROLLUP_TO` — 不作为公开函数字符串**：等价语义已通过 S11 结构化 `parentShare` 第一版覆盖。

### S11 状态与后续阶段
S11 Pivot Metrics Unification 已统一 `pivot.metrics` 为混合结构（字符串 + 对象）并签收 `parentShare`（父级占比）第一版。`baselineRatio`（基准引用）已在 S12 中完成实现与全面签收。详见 `06_s11_metrics_unification_and_derived_metrics.md` 与 `07_s12_baseline_ratio_execution_plan.md`。

## 二、 成功标准与验收契约 (Acceptance Criteria)

### 1. 契约一致性
*   **MCP 路由层**：Agent 只能看到 `dataset.query_model` 和 `dataset.compose_script`。`query_model` Schema 使用 `oneOf` 确保 `columns` 模式和 `pivot` 模式互斥。
*   **错误响应契约**：
    *   REST API 端点：所有熔断错误必须包装在系统标准的 `RX` 响应体中。
    *   MCP 端点：必须返回标准的 JSON-RPC Error Object（`code: -32000` 或 `-32602`），以触发 LLM 的重试与降级思考。

### 2. 测试策略与基线
所有 Pivot 内存引擎的计算结果必须通过**真实 SQL 数据比对验证**（不能仅仅是测试 SQL 字符串生成是否正确）：
*   **基准测试**：将 Pivot DSL 产出的树形/网格数据，与人工编写的等价 `UNION ALL` 宽表结果进行深度断言比对（包含小计、截断、骨架空值）。
*   **边界测试**：显式覆盖 `hierarchyMode: "tree"` 下存在空缺中间层级（Slice Trap）的建树成功率。
*   **熔断测试**：使用海量模拟域测试基数预估熔断器的触发准确率，确保内存消耗被死死拦截在阈值之下。

## 三、 模块归属与执行阶段

本次迭代将严格遵守"Java 端先定义快照 + Python 引擎纯镜像复刻"的开发策略。

*   **P0 (Java Core)**：
    *   在 `foggy-dataset-model-pivot` (Java) 中实现 AST、四阶段内存流水线、熔断器。
    *   补齐基于 H2/MySQL 真实执行环境的集成测试。
*   **P1 (Python Mirror)**：
    *   在 Python 引擎侧按照 Java 侧快照，逐行复刻 AST 模型与 Pivot Pipeline 逻辑，保障双端完全一致。
*   **P2 (MCP Gateway)**：
    *   更新工具 Schema 路由映射，将错误处理逻辑对接到 JSON-RPC 与 RX 标准体系。

## Acceptance Status

- acceptance_status: signed-off
- acceptance_decision: accepted-with-risks
- signed_off_by: codex
- signed_off_at: 2026-05-01
- acceptance_record: docs/9.0.0.beta/acceptance/version-signoff.md
- blocking_items: none
- follow_up_required: yes

> 本次签收范围为 Pivot DSL Java Core 与 MCP Schema。Python Mirror 仍在本次签收范围之外；S11 的 `pivot.metrics` 对象元素与 `parentShare` 第一版已完成补充签收，S12 `baselineRatio` 已完成完全实现与签收。
