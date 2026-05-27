---
doc_role: workitem
doc_purpose: Track the Java MCP adapter for LLM routing calibration guard and fresh replan enforcement.
version: 9.1.0
target: LLM Routing Replan Guard Java MCP Adapter
status: ready-for-verification
created_at: 2026-05-27
updated_at: 2026-05-27
source_type: cross-project-coordination
---

# LLM Routing Replan Guard Java MCP Adapter

## 文档作用

- doc_type: cross-project-coordination
- intended_for: execution-agent | reviewer | signoff-owner
- purpose: 记录 Java MCP 自然语言查询入口接入上游路由校准守卫的实现状态、代码清单、验证证据和剩余阻塞。

## Background

LLM 题库路由评估已经确认：仅靠提示词无法稳定约束所有模型。跨模型调优链路增加了结构化路由校准守卫，用于标记上游 raw route、校准后 route、风险变化和是否必须重新规划。

本仓库承担 Java MCP 自然语言查询入口的最小生产适配：当上游把 `routing_calibration_guard` 放入 `DatasetNLQueryRequest.hints.extra` 时，Java MCP 必须识别守卫并避免继续执行旧 route 产物。

上游设计来源：

- `../docs/v3.6/workitems/LLM-routing-production-replan-integration-design-20260527.md`

## Target Outcome

- route-changing 校准必须触发 fresh replan，不得复用旧 route 的计划、SQL、DSL、CTE、memory grid 或已生成工具参数。
- 缺失 `calibrated_route` 的 route-changing 守卫必须 fail closed。
- risk-only 校准保留为审计信息，不阻断原执行。
- MCP tool schema 和 Java 参数解析链路必须允许 `routing_calibration_guard` 从 tool hints 透传到 service 层。

## Implementation Summary

| Area | Status | Notes |
|---|---|---|
| Guard action model | completed | 新增 `RoutingCalibrationActionType`、`RoutingCalibrationAction`、`RoutingCalibrationActionResolver` |
| Guard field compatibility | completed | 支持 nested/direct guard，snake_case 与 camelCase 字段 |
| Java MCP NL tool passthrough | completed | `NaturalLanguageQueryTool` 将未消费的 hints 字段放入 `QueryHints.extra` |
| Tool schema | completed | `dataset_nl_query_schema.json` 增加 `hints.routing_calibration_guard` |
| QueryExpertService blocking | completed | 缺失 `calibrated_route` 时返回 `ROUTING_REPLAN_REQUIRED`，不进入 ChatClient 或工具调度 |
| QueryExpertService replan prompt | completed | route-changing 且有 calibrated route 时向 LLM 注入守卫说明，要求按校准后 route 重新规划 |
| Unit coverage | added | 新增 resolver、service blocked guard、tool passthrough 测试 |

## Code Inventory

- `foggy-dataset-mcp/src/main/java/com/foggyframework/dataset/mcp/service/routing/RoutingCalibrationActionType.java`
- `foggy-dataset-mcp/src/main/java/com/foggyframework/dataset/mcp/service/routing/RoutingCalibrationAction.java`
- `foggy-dataset-mcp/src/main/java/com/foggyframework/dataset/mcp/service/routing/RoutingCalibrationActionResolver.java`
- `foggy-dataset-mcp/src/main/java/com/foggyframework/dataset/mcp/service/QueryExpertService.java`
- `foggy-dataset-mcp/src/main/java/com/foggyframework/dataset/mcp/tools/NaturalLanguageQueryTool.java`
- `foggy-dataset-mcp/src/main/resources/schemas/dataset_nl_query_schema.json`
- `foggy-dataset-mcp/src/test/java/com/foggyframework/dataset/mcp/service/routing/RoutingCalibrationActionResolverTest.java`
- `foggy-dataset-mcp/src/test/java/com/foggyframework/dataset/mcp/service/QueryExpertServiceRoutingCalibrationTest.java`
- `foggy-dataset-mcp/src/test/java/com/foggyframework/dataset/mcp/tools/NaturalLanguageQueryToolTest.java`

## Behavior Contract

| Guard State | Java MCP Action |
|---|---|
| no guard | execute raw request |
| route changed / requires replan / execution not allowed, with `calibrated_route` | fresh LLM planning with explicit calibrated-route guard |
| route changed / requires replan / execution not allowed, missing `calibrated_route` | fail closed with `ROUTING_REPLAN_REQUIRED` |
| risk changed only or applied rules only | audit-only; execute raw request |

## Verification

Commands attempted on 2026-05-27:

```bash
mvn -Dtest=RoutingCalibrationActionResolverTest,QueryExpertServiceRoutingCalibrationTest,NaturalLanguageQueryToolTest test
```

Result:

- status: blocked before test execution
- blocker: existing main compilation error in `LocalDatasetAccessor.java`
- error: `SemanticQueryRequest.OutputFormattingItem` cannot be found
- location: `foggy-dataset-mcp/src/main/java/com/foggyframework/dataset/mcp/spi/impl/LocalDatasetAccessor.java`

Root reactor verification on 2026-05-27:

```bash
mvn -pl foggy-dataset-mcp -am -Dtest=RoutingCalibrationActionResolverTest,QueryExpertServiceRoutingCalibrationTest,NaturalLanguageQueryToolTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Result:

- status: passed
- tests: 26 run, 0 failures, 0 errors, 0 skipped
- note: the earlier module-local command used the installed/local dependency artifact; the root reactor command builds `foggy-dataset-model` first, so `SemanticQueryRequest.OutputFormattingItem` resolves correctly.

Additional checks:

- `git diff --check`: passed
- secret scan on diff content: passed, no hits

## Progress Tracking

### Development Progress

| Item | Status | Notes |
|---|---|---|
| Parse calibration guard from request hints | completed | Resolver reads `DatasetNLQueryRequest.hints.extra` |
| Classify action type | completed | `EXECUTE_RAW` / `AUDIT_ONLY` / `REPLAN_REQUIRED` / `BLOCKED` |
| Block malformed replan guard | completed | Missing calibrated route returns error before ChatClient/tool invocation |
| Preserve fresh replan instruction | completed | Calibrated route and applied rules are added to the user message |
| Expose schema passthrough | completed | MCP schema accepts `routing_calibration_guard` |

### Testing Progress

| Test Area | Required | Status |
|---|---:|---|
| Resolver unit tests | yes | passed in root reactor command |
| Service fail-closed unit test | yes | passed in root reactor command |
| Tool hints passthrough unit test | yes | passed in root reactor command |
| Module targeted Maven test | yes | passed via `-pl foggy-dataset-mcp -am`; module-local command remains unsuitable without refreshed local artifacts |
| Whitespace check | yes | passed: `git diff --check` |
| Secret scan | yes | passed on diff content |

### Experience Progress

experience: N/A.

Reason: 本次变更是 Java MCP 后端路由守卫适配，不涉及 UI 或人工交互流程。

## Risks And Follow-Ups

- 当前 Java MCP adapter 自身不计算 Python 侧校准规则，依赖上游 runner 把 `routing_calibration_guard` 写入 `hints.extra`。
- route-changing 且有 `calibrated_route` 的 fresh replan 通过 Spring AI 新一轮 planning prompt 约束完成；如果后续生产链路存在非 LLM 复用旧计划的路径，需要在该路径增加同等 fail-closed 检查。
- 单模块目录直接执行 Maven 可能命中旧的本地 `foggy-dataset-model` artifact；该仓库内验证应从 bridge 根使用 `-pl foggy-dataset-mcp -am`。

## Acceptance Readiness

- current_status: ready-for-verification
- ready_for_acceptance: yes, for the scoped Java MCP adapter
- blocking_items: none for root-reactor verification
- follow_up_required: yes
