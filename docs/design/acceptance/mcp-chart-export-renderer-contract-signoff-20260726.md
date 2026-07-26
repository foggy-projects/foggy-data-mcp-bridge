---
acceptance_scope: feature
version: next-unassigned
target: mcp-chart-export-renderer-contract
status: rejected
decision: rejected
signed_off_by: independent-codex-reviewer
signed_off_at: 2026-07-26
reviewed_by: N/A
blocking_items:
  - AC-10: XChart validation errors can place query row values in application logs
follow_up_required: yes
evidence_count: 10
assurance_level: standard
---

# Feature Delivery Signoff: MCP 图表双渲染导出

## Document Purpose

- intended_for: signoff-owner / reviewer / project-root-session
- purpose: 对 XChart 默认渲染、ECharts 可选渲染和双 export 工具交付形成独立、可复核的正式签收结论。

## Background

- delivery_spec: `docs/design/mcp-chart-export-renderer-contract.md`
- target_outcome: 默认使用 JVM 内 XChart 生成图片，同时提供接受原生 ECharts Option 的显式外部渲染工具。
- signoff_scope: canonical spec 声明的 `foggy-dataset-mcp`、launcher、demo、benchmark、Mongo 测试夹具和设计文档改动。
- critical_outcomes:
  - 双工具公开合同与引擎原生配置边界。
  - 单一查询数据源与 QueryModel 权限链。
  - DSL、DSL_CTE、timeWindow、Pivot flat 的结果绑定。
  - XChart 无 sidecar 渲染和 ECharts 部署凭据隔离。
  - 日志不输出 token、完整 data URI 或查询数据。
- non_blocking_or_waivable_items:
  - 非核心 XChart API 映射完整度。
  - 真实 ECharts 外部服务 E2E；canonical spec 允许 WireMock 合同测试替代。

## Acceptance Basis

- approved delivery spec: canonical spec 已由 repository owner 通过用户请求批准，验收前状态为 `READY_FOR_SIGNOFF`，保障等级为 `standard`。
- changed paths / diff:
  - 实际工作区变更落在 spec 声明的 MCP、launcher、demo、benchmark、Mongo 测试夹具与 `docs/design` 范围内。
  - 旧 `ExportWithChartTool`、旧描述和旧 Schema 已删除；新增 renderer SPI、XChart/ECharts renderer、共享 executor 和两个固定引擎工具。
  - 未发现语义查询引擎、权限注入、DSL_CTE 沙箱或持久化模型改动。
- test records:
  - `mvn -B -ntp -pl foggy-dataset-mcp -am test -DskipITs`：15 个 Reactor 模块成功；MCP 模块 516 tests，0 failures / 0 errors / 0 skipped。
  - 聚焦测试 `ChartToolTest,ExportWithChartToolsTest,XChartRendererTest,EChartsRendererTest,ToolConfigLoaderTest,ToolFilterServiceTest,AnalystMcpControllerTest,ResultValidatorTest`：93 tests，0 failures / 0 errors / 0 skipped。
  - `mvn -B -ntp -pl foggy-mcp-launcher -am -DskipTests compile`：27 个 Reactor 模块成功。
  - `mvn -B -ntp -pl addons/foggy-dataset-model-mongo -DskipTests test-compile`：主代码及 9 个测试源编译成功。
- experience evidence:
  - 三份图表 JSON Schema 可由 `jq` 解析。
  - 生产源码和活动配置未发现旧公开工具标识；仅合同测试保留负向断言。
  - export Schema/描述未发现公共 `options` 或通用转换层。
- migration / compatibility evidence:
  - 旧工具名被有意移除，符合批准的破坏性变更决策。
  - 无数据库迁移；回滚可通过回退本次代码和配置变更完成。

## Contract Conformance

| Item | Expected | Delivered | Evidence | Result |
|---|---|---|---|---|
| AC-1 | 两个固定引擎 export 工具，无旧别名和 `engine` 参数 | 两个工具、描述、Schema、注册与默认配置均已拆分 | 配置/controller/Schema 测试与旧标识扫描 | pass |
| AC-2 | XChart Adapter Config 在 JVM 内生成 png/jpg | XChart renderer 使用 builder/styler/series 映射并生成真实图片字节 | `XChartRendererTest`、`ChartToolTest` | pass |
| AC-3 | 原生 ECharts Option，唯一 `dataset.source`，符合 HTTP 合同 | Option 保留并注入查询行；拒绝第二份数据与 dataset 链 | `EChartsRendererTest` WireMock 请求捕获 | pass |
| AC-4 | 无公共 config/options/translator | 两个 config 合同独立，未发现跨引擎转换或 merge 路径 | Schema/描述扫描与代码复核 | pass |
| AC-5 | `chart.generate` 接受顶层 data，默认 XChart，支持显式 ECharts | data 为 required/non-empty；registry 默认 XChart并支持 ECharts | `ChartToolTest` | pass |
| AC-6 | DSL/DSL_CTE 仅消费最终 items，失败或空结果不渲染 | 共享 executor 只消费 `SemanticQueryResponse.items` | `ExportWithChartToolsTest` | pass |
| AC-7 | timeWindow null 保持缺口且不使 X/Y 错位 | XChart 将 null Y 映射为 `NaN`，ECharts保留原始行 | renderer/export tests | pass |
| AC-8 | Pivot 副本注入 flat，拒绝 tree/grid，过滤 totals | payload 深复制、预查询拒绝、`_sys_meta` totals 过滤均已实现 | `ExportWithChartToolsTest` | pass |
| AC-9 | 全部活动工具面迁移到新合同 | launcher、角色、审计、AI 校验、mock、controller、addon/demo 已迁移 | 93 项聚焦测试、编译与静态扫描 | pass |
| AC-10 | 调用方凭据不进入 sidecar，日志不输出 token/data URI/查询数据 | sidecar 凭据隔离通过；但 XChart 数据类型错误会把实际查询值拼入异常，随后被 `ChartTool` 记录 | `EChartsRendererTest` header 断言；`XChartRenderer.java:282,304,443` 与 `ChartTool.java:116-130` 静态追踪 | fail |

## Implementation Quality

- scope and changed surface: 与批准范围一致，未发现未经确认的查询引擎或数据模型扩张。
- maintainability and duplication: renderer registry、共享 image envelope 和 export executor 边界清晰；未引入通用图表 translator。
- error handling and edge cases: 数据源覆盖、空结果、Pivot 非 flat、timeWindow null、存储降级等路径有覆盖；日志脱敏仍有阻断缺口。
- contract, data and compatibility: 工具名、Schema、默认配置和有意的破坏性兼容决策一致。
- terminology and documentation: XChart Adapter Config 与 ECharts Option 的术语区分明确，文档与活动配置已迁移。

## Evidence Matrix

| Item | Classification | Risk | Evidence | Reused / New | Result |
|---|---|---|---|---|---|
| AC-1 | core-blocker | major | 工具发现、配置、Schema 和旧名称扫描 | reused + new scan | pass |
| AC-2 | core-blocker | major | XChart png/jpg 真实字节测试 | reused | pass |
| AC-3 | core-blocker | major | WireMock HTTP body/header 合同 | reused | pass |
| AC-4 | core-blocker | major | Schema/描述/实现扫描 | reused + new scan | pass |
| AC-5 | core-blocker | major | direct data 默认及显式 engine 测试 | reused | pass |
| AC-6 | core-blocker | major | DSL_CTE、失败、空结果、单次查询测试 | reused | pass |
| AC-7 | core-blocker | major | null-gap 和最终 items 透传测试 | reused | pass |
| AC-8 | core-blocker | major | Pivot 副本、拒绝与 totals 过滤测试 | reused | pass |
| AC-9 | core-blocker | major | focused tests、launcher compile、addon test-compile、扫描 | reused + new scan | pass |
| AC-10 | core-blocker | major | 凭据 header 测试通过；日志路径静态追踪失败 | reused + new review | fail |

## Evidence Sufficiency

- assurance_level: standard
- why_existing_evidence_is_sufficient_or_not:
  - AC-1 至 AC-9 的 focused 与 affected-module 证据仍对应当前生产改动。
  - 516 项 affected-module 测试之后仅新增了一项 direct ECharts test；后续 93 项聚焦测试已覆盖该 test-only 变化，生产实现未在两次运行之间变化。
  - AC-10 的失败由直接代码路径确定：查询行中的非数值 Y/Pie value/XY X 值进入异常消息，`ChartTool` 对该消息执行 warn/error 日志记录。该结论不依赖外部环境。
- new_validation_that_could_change_decision:
  - 修复后新增日志捕获回归测试，使用唯一敏感标记验证 XChart export 的 Category/XY/Pie 错误日志均不包含数据值。
  - 重跑 chart/export focused tests 与 `foggy-dataset-mcp` affected-module lane。
- expensive_validation_omitted_and_reason:
  - 未运行真实 ECharts 服务、数据库矩阵、远程 CI 或 release authority 链路；它们不能改变当前 AC-10 代码级阻断，且不属于 standard assurance 必需范围。

## Optional Full-Chain Recommendation

- recommendation: not-needed
- qualifying_condition: 当前失败可由局部安全修复和 focused/affected-module 验证确定。
- estimated_wall_clock_and_basis: N/A
- scope_and_prerequisites: N/A
- maximum_attempts: 1
- decision_impact: 不影响当前拒绝结论。
- user_approval: not-requested
- execution_status: not-run

## Waivers

| Waived Item | Authority | Reason | Bounded Impact | Non-Waivable Guards | Follow-up |
|---|---|---|---|---|---|
| none | | | | | |

## Failed Items

- AC-10：`XChartRenderer` 的三条校验错误包含实际数据值：
  - 非数值 Category/XY Y 值。
  - 非数值 Pie value。
  - 非数值/非日期 XY X 值。
- `ChartTool` 在捕获这些 `IllegalArgumentException` 后将 `e.getMessage()` 写入 warn 日志；export 路径把查询最终 `items` 作为该 renderer 的 data，因此错误行中的查询值可进入应用日志。

## Risks / Follow-ups

- 阻断修复应移除异常消息中的实际数据值，或在日志边界使用不含输入值的稳定错误码/消息；不能仅依赖调用方避免异常数据。
- 增加日志捕获回归测试，至少覆盖 Category/XY/Pie 三类查询数据错误，并断言 token、完整 data URI 和敏感查询值均未出现。
- 修复后只需重验 AC-10 及其依赖的 chart/export focused tests，再运行 MCP affected-module lane；无需真实 ECharts 全链路。

## Final Decision

- decision: rejected
- rationale: AC-1 至 AC-9 已满足，但 AC-10 是 must-pass 安全合同；查询数据可能进入日志，符合验收红线中的核心失败，不能签收或豁免。
- blocking_items:
  - 修复 XChart 校验异常到 `ChartTool` 日志的查询值泄露路径，并增加回归证据。
- follow_up_owner_and_due: implementation owner；修复完成后重新提交独立签收，日期待 owner 安排。

## Signoff Marker

- acceptance_status: rejected
- acceptance_decision: rejected
- signed_off_by: independent-codex-reviewer
- signed_off_at: 2026-07-26
- acceptance_record: `docs/design/acceptance/mcp-chart-export-renderer-contract-signoff-20260726.md`
- blocking_items: AC-10 query-data log leakage
- follow_up_required: yes
