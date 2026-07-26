---
acceptance_scope: feature
version: next-unassigned
target: mcp-chart-export-renderer-contract
status: signed-off
decision: accepted
signed_off_by: codex-signoff-reviewer
signed_off_at: 2026-07-26
reviewed_by: N/A
blocking_items: []
follow_up_required: no
evidence_count: 12
assurance_level: standard
---

# Feature Delivery Signoff: MCP 图表双渲染导出 R2

## Document Purpose

- intended_for: signoff-owner / reviewer / project-root-session
- purpose: 对 AC-10 修复后的 XChart 默认渲染、ECharts 可选渲染和双 export 工具交付形成
  新的正式签收结论。

## Background

- delivery_spec: `docs/design/mcp-chart-export-renderer-contract.md`
- target_outcome: 默认使用 JVM 内 XChart 生成图片，同时提供接受原生 ECharts Option 的显式
  外部渲染工具。
- signoff_scope: canonical spec 声明的 MCP、launcher、demo、benchmark、Mongo 测试夹具与
  设计文档改动。
- critical_outcomes: 双工具原生配置、唯一查询数据源、DSL/DSL_CTE/timeWindow/Pivot flat
  绑定、XChart 无 sidecar、ECharts 凭据隔离和日志脱敏。
- non_blocking_or_waivable_items: 非核心 XChart API 映射完整度和真实 ECharts 服务 E2E。

## Acceptance Basis

- approved delivery spec: canonical 状态为 `READY_FOR_SIGNOFF`，assurance level 为
  `standard`；首次拒签仅阻断 AC-10。
- changed paths / diff: 实际变更位于批准的 MCP、launcher、demo、benchmark、Mongo 测试
  夹具和设计文档范围；未发现语义查询引擎、权限模型或 DSL_CTE 沙箱扩张。
- test records:
  - 图表合同聚焦集合：96 tests，0 failures / 0 errors / 0 skipped，BUILD SUCCESS。
  - AC-10 聚焦集合：29 tests，0 failures / 0 errors / 0 skipped，BUILD SUCCESS。
  - `mvn -B -ntp -pl foggy-dataset-mcp -am test -DskipITs`：15 个 Reactor 模块成功；
    MCP 模块 520 tests，0 failures / 0 errors / 0 skipped。
  - launcher compile 和 Mongo addon test-compile 证据保持有效。
- experience evidence: 三份 Schema 可解析；活动配置只公开两个固定 export 工具；
  export Schema 没有 `engine` 或公共 `options`。
- migration / compatibility evidence: 旧工具名按批准决策移除；无数据库迁移；回滚为回退
  本次代码和配置变更。

## Contract Conformance

| Item | Expected | Delivered | Evidence | Result |
|---|---|---|---|---|
| AC-1 | 两个固定引擎 export 工具，无旧别名 | 注册、Schema、描述和配置均已拆分 | 配置/Schema/工具扫描与测试 | pass |
| AC-2 | XChart JVM 内生成 png/jpg | Adapter Config 映射并生成真实图片字节 | XChart renderer/tool tests | pass |
| AC-3 | 原生 ECharts Option 和唯一数据源 | 查询行注入单个 `dataset.source` | WireMock renderer tests | pass |
| AC-4 | 无公共 config/options/translator | 两个 config 合同独立 | Schema/描述/代码扫描 | pass |
| AC-5 | direct data 默认 XChart | `chart.generate` 支持两引擎 | ChartTool tests | pass |
| AC-6 | DSL/DSL_CTE 只消费最终 items | 共享 executor 只使用查询响应 | Export tests | pass |
| AC-7 | timeWindow null 保持缺口 | XChart 使用 NaN gap，ECharts 保留行 | Renderer/export tests | pass |
| AC-8 | Pivot flat、拒绝 tree/grid、过滤 totals | 请求副本归一化及结果过滤 | Export tests | pass |
| AC-9 | 全部活动工具面迁移 | launcher、角色、审计、校验和测试已迁移 | 96 项聚焦测试及静态扫描 | pass |
| AC-10 | 不泄漏调用凭据、data URI 或查询数据 | sidecar 使用部署凭据；XChart 错误不含实际值 | WireMock header 与三类日志捕获测试 | pass |

## Implementation Quality

- scope and changed surface: 与批准范围一致，没有查询引擎或权限边界旁路。
- maintainability and duplication: renderer registry、固定引擎工具和共享 executor 职责清晰，
  未引入跨引擎 translator。
- error handling and edge cases: 空结果、查询失败、Pivot 非 flat、timeWindow null、存储降级和
  Category/XY/Pie 数据类型错误均有覆盖。
- contract, data and compatibility: 工具名、Schema、唯一数据源和有意破坏性变更与方案一致。
- terminology and documentation: XChart Adapter Config 与 ECharts Option 区分一致。

## Evidence Matrix

| Item | Classification | Risk | Evidence | Reused / New | Result |
|---|---|---|---|---|---|
| AC-1 | core-blocker | major | 工具配置、Schema、旧名称扫描 | reused + new scan | pass |
| AC-2 | core-blocker | major | XChart 图片字节测试 | reused | pass |
| AC-3 | core-blocker | major | ECharts HTTP/data injection 测试 | reused | pass |
| AC-4 | core-blocker | major | Schema/描述/实现扫描 | reused + new scan | pass |
| AC-5 | core-blocker | major | direct data 双引擎测试 | reused | pass |
| AC-6 | core-blocker | major | DSL_CTE、失败、空结果测试 | reused | pass |
| AC-7 | core-blocker | major | null-gap 测试 | reused | pass |
| AC-8 | core-blocker | major | Pivot 副本、拒绝与 totals 测试 | reused | pass |
| AC-9 | core-blocker | major | 96 项聚焦与 affected-module 测试 | reused + refreshed | pass |
| AC-10 | core-blocker | major | header 隔离、Category/XY/Pie 日志捕获 | refreshed | pass |

## Evidence Sufficiency

- assurance_level: standard
- why_existing_evidence_is_sufficient_or_not: AC-10 修复仅改变 XChart 类型错误文本及对应测试；
  29 项直接依赖测试、96 项完整图表合同测试和 520 项 affected-module 证据覆盖了最小重验证
  半径。文档回填没有使产品证据失效。
- new_validation_that_could_change_decision: 活动配置、旧工具名、Schema 结构、生产查询值拼接和
  Surefire 敏感标记扫描均通过。
- expensive_validation_omitted_and_reason: 未运行真实 ECharts 服务、数据库矩阵、远程 CI 或
  authority/replay 链；standard assurance 下 WireMock 和受影响模块证据已经足以判断。

## Optional Full-Chain Recommendation

- recommendation: not-needed
- qualifying_condition: 当前不是最终版本发布候选，且核心结果已有充分证据。
- estimated_wall_clock_and_basis: N/A
- scope_and_prerequisites: N/A
- maximum_attempts: 1
- decision_impact: N/A
- user_approval: not-requested
- execution_status: not-run

## Waivers

| Waived Item | Authority | Reason | Bounded Impact | Non-Waivable Guards | Follow-up |
|---|---|---|---|---|---|
| none | | | | | |

## Failed Items

- none

## Risks / Follow-ups

- ECharts 仍是显式选择的外部受信任数据处理组件；默认 XChart 路径不依赖该服务。
- XChart Adapter 只覆盖 Schema 声明能力，不等同于完整 XChart Java API，符合 Non-Goals。

## Final Decision

- decision: accepted
- rationale: AC-1 至 AC-10 全部通过，首次拒签的 AC-10 查询数据日志泄漏路径已修复并形成
  Category/XY/Pie 长期回归保护；没有阻断项或需要 waiver 的风险。
- blocking_items: none
- follow_up_owner_and_due: none

## Signoff Marker

- acceptance_status: signed-off
- acceptance_decision: accepted
- signed_off_by: codex-signoff-reviewer
- signed_off_at: 2026-07-26
- acceptance_record:
  `docs/design/acceptance/mcp-chart-export-renderer-contract-signoff-20260726-r2.md`
- blocking_items: none
- follow_up_required: no
