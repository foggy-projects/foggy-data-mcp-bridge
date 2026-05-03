---
doc_role: workitem
doc_purpose: Track a small follow-up iteration for query documentation, schema guardrails, and generated description maintainability discovered during the 9.1.0 docs/code alignment review.
version: 9.1.0
target: Query Docs Contract Hardening
status: completed
created_at: 2026-05-03
updated_at: 2026-05-03
source_type: optimization
---

# Query Docs Contract Hardening Small Iteration

## 文档作用

- doc_type: optimization
- intended_for: execution-agent | reviewer | signoff-owner
- purpose: 记录 9.1.0 文档与实现对齐复盘后形成的 3 个小迭代优化项，避免后续 AI 工具描述、schema 与运行时边界再次漂移。

## Background

2026-05-03 对 `docs-site/zh`、`foggy-dataset-mcp/src/main/resources/schemas`、`schemas/descriptions` 与实际 Java 代码进行复盘时，已完成 timeWindow、CTE、Pivot、AI 工具路由与边界退化说明的同步。

复盘过程中同时发现 3 个适合进入小迭代的工程化优化项：

1. `pivot` 模式下顶层 `orderBy` / `limit` 不是透视轴裁剪控制，文档已规避，但 schema / runtime 还可以给出更明确的 fail-fast 或提示。
2. `timeWindow` 相关 MCP schema 测试覆盖不足，尤其是 `value` 数组长度、`rollingAggregator=min/max` 合法性和非法聚合拒绝。
3. `query_model_v3.md`、`query_model_v3_basic.md`、`query_model_v3_no_vector.md` 存在大段重复内容，后续维护容易再次出现能力边界漂移。

## Target Outcome

本小迭代目标不是扩展公开 DSL 能力，而是加强文档契约、测试证据和描述文件维护方式：

- AI 看到的 schema / tool description 与运行时边界保持一致。
- 对易误用参数给出更直接的拒绝或降级指导。
- 让 query_model 三份描述文档具备可维护的单一来源或生成流程。

## Scope

| ID | Priority | Item | Owner | Target Outcome |
|---|---|---|---|---|
| QDOC-91-01 | P1 | Pivot 顶层 `orderBy` / `limit` 边界硬化 | MCP Schema / Java Pivot | 明确 pivot 模式下顶层排序分页是否 fail-fast；如果保持兼容，也必须在 schema description 和错误提示中明确建议使用轴级 `orderBy` / `limit` |
| QDOC-91-02 | P1 | timeWindow schema guardrail 测试补强 | MCP Schema Tests | 覆盖 `value` 两元素数组、`rollingAggregator=min/max` 合法、非法聚合拒绝，并与 Java validator 行为对齐 |
| QDOC-91-03 | P2 | query_model 描述文档去重复 | MCP Tool Docs | 建立单一源文档或生成脚本，生成 `full/basic/no_vector` 三个 variant，减少重复编辑导致的漂移 |

## Evaluation Result

2026-05-03 评估结论：本 workitem 可以合并为一个小迭代直接处理，不需要另拆独立任务。

执行策略：

- QDOC-91-01 采用兼容策略，不新增 runtime fail-fast。原因是顶层 `orderBy` / `limit` 可能已有历史请求依赖；本轮通过 schema description 和三份 query_model tool description 明确约束 AI 不生成 `payload.pivot` + 顶层排序分页的组合，并要求使用轴级 `pivot.rows[*]` / `pivot.columns[*]` 控制排序和裁剪。
- QDOC-91-02 直接补 MCP schema 测试，覆盖 `timeWindow.value` 缺省、两元素合法、过短/过长非法，以及 `rollingAggregator=min/max` 合法、未开放枚举非法。
- QDOC-91-03 暂不引入生成脚本，先落地一致性检查测试，固定三份描述文档必须同时包含关键能力边界片段。该方案成本低，能直接防止本次关注的边界漂移；后续如果文档继续膨胀，再升级为片段生成流程。

## Non-Goals

- 不实现 `pivot + timeWindow` 单请求组合。
- 不开放 `ROLLUP_TO`、`CELL_AT`、`AXIS_MEMBER`、`AXIS_REF` 或任意 MDX 集合代数。
- 不处理 `parentShare` / `baselineRatio` 参与 axis `having` / `orderBy` / `limit` 的运行时增强；该项另行评估。
- 不改变现有 Pivot 计算结果语义。

## Implementation Notes

### QDOC-91-01 Pivot 顶层排序分页边界

当前文档已说明：Pivot 输出规模由轴定义和基数保护控制，需要 TopN 或排序时应放在 `pivot.rows[*]` / `pivot.columns[*]` 轴对象中。

小迭代需要决策并落地其中一种策略：

- **fail-fast 策略**：当请求同时包含 `payload.pivot` 与顶层 `orderBy` / `limit` 时，在 schema 或 runtime 层明确拒绝，并提示改用轴级控制。
- **兼容策略**：继续允许顶层字段存在，但 schema description、tool description 和错误指南必须明确说明它们不作为透视轴裁剪控制，AI 不应生成这种组合。

优先建议 fail-fast；如果存在兼容性顾虑，则至少补充测试证明顶层字段不会被误认为轴级控制。

### QDOC-91-02 timeWindow schema 测试补强

新增或扩展 MCP schema 测试，至少覆盖：

- 合法：`timeWindow.value` 缺省。
- 合法：`timeWindow.value` 为两个字符串元素。
- 违规：`timeWindow.value` 少于两个或多于两个元素。
- 合法：`rollingAggregator` 为 `min` / `max`。
- 违规：`rollingAggregator` 为未开放值。

测试应与 Java `TimeWindowValidator` 保持一致：`value` 可选，但提供时必须恰好两个可解析表达式；`rollingAggregator` 支持 `sum` / `avg` / `count` / `min` / `max`。

### QDOC-91-03 query_model 描述文档去重复

当前三份描述文件承担不同上下文：

- `query_model_v3.md`: 完整描述。
- `query_model_v3_basic.md`: 精简基础版。
- `query_model_v3_no_vector.md`: 去向量/裁剪版。

小迭代应先选择维护策略：

- 建立一个源文档片段目录，通过脚本生成三份 variant。
- 或建立一个检查脚本，校验关键能力边界片段在三份文档中的一致性。

必须覆盖的关键一致性片段：

- AI 能力选择矩阵。
- `timeWindow` 使用场景、互斥规则与 `rollingAggregator` 枚举。
- `pivot` 使用场景、`columns` 互斥、`timeWindow` 互斥。
- `parentShare` / `baselineRatio` 能力边界与退化策略。
- 禁止隐藏函数：`ROLLUP_TO`、`CELL_AT`、`AXIS_MEMBER`、`AXIS_REF`。

## Acceptance Criteria

- QDOC-91-01: Pivot 顶层 `orderBy` / `limit` 策略明确，并有 schema/runtime/docs 任一层面的可验证保护。
- QDOC-91-02: MCP schema 测试覆盖 timeWindow `value` 和 `rollingAggregator` 的合法/非法分支。
- QDOC-91-03: 三份 query_model 描述文档有单一来源、生成脚本或一致性检查，后续修改不需要人工同步三遍关键边界。
- `docs-site` 构建不因新增文档或链接失败。
- 相关 Maven 测试通过，至少包含 `PivotSchemaValidationTest` 和新增/调整的 timeWindow schema 测试。

## Progress Tracking

### Development Progress

| Item | Status | Notes |
|---|---|---|
| QDOC-91-01 | completed | 已采用兼容策略；`query_model_v3_schema.json` 与 `query_model_v3.md` / `query_model_v3_basic.md` / `query_model_v3_no_vector.md` 均明确 pivot 模式下顶层 `orderBy` / `limit` 不是透视轴排序或 TopN 控制 |
| QDOC-91-02 | completed | 已扩展 `PivotSchemaValidationTest`，覆盖 `timeWindow.value` 数组长度和 `rollingAggregator` 合法/非法枚举 |
| QDOC-91-03 | completed | 已新增 `QueryModelDescriptionConsistencyTest`，校验三份 query_model 描述文件的关键能力边界片段一致存在 |

### Testing Progress

| Test Area | Required | Status |
|---|---:|---|
| MCP schema JSON parse | yes | passed: `Get-Content ...query_model_v3_schema.json \| ConvertFrom-Json` |
| `PivotSchemaValidationTest` | yes | passed: `mvn -pl foggy-dataset-mcp "-Dtest=PivotSchemaValidationTest,QueryModelDescriptionConsistencyTest" test` |
| timeWindow schema validation tests | yes | passed: included in `PivotSchemaValidationTest` |
| query_model description consistency test | yes | passed: included in `QueryModelDescriptionConsistencyTest` |
| docs-site build | yes | passed: `npm run build` in `docs-site` |
| whitespace check | yes | passed: `git diff --check` |

### Experience Progress

experience: N/A.

Reason: 本小迭代仅涉及 schema、AI tool description、文档生成/检查链路，不涉及 UI 页面或用户交互。

## Acceptance Readiness

- current_status: completed
- ready_for_execution: no, already completed in this small iteration
- requires_formal_quality_gate: no, unless runtime fail-fast logic is added beyond schema/docs tests
- signoff_requirement: lightweight reviewer check; test evidence is complete for this scoped compatibility strategy
