---
doc_role: workitem
doc_purpose: Track Java lite order sales-team fixture coverage for v3.9 semantic-underexecution samples.
version: 9.1.0
target: FactOrderQueryModel lite fixture semantics
status: implemented
created_at: 2026-06-01
updated_at: 2026-06-01
source_type: optimization
---

# Order Sales-Team Semantic Fixture

## 文档作用

- doc_type: workitem
- intended_for: engine-owner | eval-runner-owner | reviewer
- purpose: 记录订单题库中销售团队和待处理阶段语义样本所需的 Java lite 模型与 fixture 补强。

## Background

v3.9 semantic-underexecution gate 已能检查 trace-visible query payload signals，但 `biz-002` 与 `biz-018` 暂不能稳定提升为语义期望样本：订单查询模型没有一条受治理的 `salesTeam` 维度，模型只能把“销售团队”近似映射到门店、经理或其他字段；待处理阶段也需要明确通过 `orderStatus` 表达当前生命周期状态。

这不是 prompt-only 问题。要让不同模型稳定产出可执行 payload，Java lite 模型目录必须先提供明确的业务字段。

## Scope

| Item | Decision |
|---|---|
| Order sales team | Add governed `salesTeam` dimension on `FactOrderModel` / `FactOrderQueryModel`, backed by `dim_sales_team` and `fact_order.sales_team_key`. |
| Backlog stage | Keep `orderStatus` as the governed lifecycle-stage proxy for pending/open order backlog. |
| Existing team model | Do not reuse `FactTeamSalesQueryModel`; it is a separate parent-child semantic fixture and is not the order backlog model. |
| Prompt tuning | Out of scope. This work fixes model/fixture observability before any semantic expectation promotion. |

## Implementation

| Path | Change |
|---|---|
| `foggy-dataset-demo/src/main/resources/foggy/templates/ecommerce/model/FactOrderModel.tm` | Added inline `salesTeam` dimension with caption, team id, region, manager, and status properties. |
| `foggy-dataset-demo/src/main/resources/foggy/templates/ecommerce/query/FactOrderQueryModel.qm` | Exposed sales-team columns and clarified `orderStatus` as lifecycle-stage/backlog semantics. |
| `foggy-mcp-launcher/src/main/resources/db/lite-demo-schema.sql` | Added `dim_sales_team`, `fact_order.sales_team_key`, and an index for the FK. |
| `foggy-mcp-launcher/src/main/resources/db/lite-demo-data.sql` | Seeded three sales teams and mapped all lite order rows to teams. |
| `foggy-mcp-launcher/src/test/java/com/foggyframework/mcp/launcher/McpLauncherLiteProfileConfigurationTest.java` | Added fixture regression coverage for schema/data/model/query-model sales-team exposure and made the existing anomaly assertion stable after column-list expansion. |

## Testing

| Command / Probe | Result |
|---|---|
| `JAVA_HOME=/Users/fengjianguang/.jdk/temurin-17/Contents/Home mvn -pl foggy-mcp-launcher -am -Dtest=McpLauncherLiteProfileConfigurationTest -Dsurefire.failIfNoSpecifiedTests=false test -P'!multi-db'` | passed: launcher reactor test, `Tests run: 4, Failures: 0, Errors: 0, Skipped: 0`. |
| `JAVA_HOME=/Users/fengjianguang/.jdk/temurin-17/Contents/Home mvn -pl foggy-mcp-launcher -am -DskipTests package -P'!multi-db'` | passed: launcher package reactor build. |
| Fresh Java lite fixture smoke on `localhost:8066` | passed: `calls=2`, `transport_errors=0`, `mcp_errors=0`, `query_rows=3`. |
| Direct MCP sales-team aggregation | passed: generated SQL joined `dim_sales_team`; returned three teams with total amount and distinct order count. |
| Direct MCP backlog by sales team and stage | passed: generated SQL joined `dim_sales_team`, filtered `orderStatus in PENDING/CONFIRMED/PROCESSING`, and grouped by `salesTeam` plus `orderStatus`. |
| `python3 experiments/spider-routing-eval/scripts/test_score_order_sales_team_semantic_gate.py` | passed: scorer accepts stable semantic rows, rejects missing semantic contracts, and reports missing expected model rows. |
| `make gate-v39-order-sales-team-semantic-stable` | passed: `biz-002/biz-018` scored `stable_gate_ok=6/6` with residuals `0` from the promoted v3.9 evidence matrix. |

## Progress Tracking

| Dimension | Status | Notes |
|---|---|---|
| Development | complete | Governed order sales-team dimension and file-backed lite seed data are implemented. |
| Testing | complete | Focused launcher regression, package build, fixture smoke, direct MCP semantic probes, and promoted semantic gate passed. |
| Experience | N/A | Pure backend/model fixture change; no UI surface. |
| Semantic promotion | ready | `biz-002` and `biz-018` are now covered by `gate-v39-order-sales-team-semantic-stable`; this is the second business-domain stable gate after ServiceTicket SLA. |

## Execution Check-in

| Item | Status |
|---|---|
| Scope implemented as intended | yes |
| Non-goals preserved | yes; no prompt-only rewrite and no new public DSL. |
| Code paths listed | yes |
| Basic self-review completed | yes |
| Test status recorded | pass |
| Acceptance readiness | promoted-gate-ready; `biz-002` / `biz-018` have three-model semantic gate evidence, while broader order-domain signoff remains separate. |

## Follow-up

Continue order-domain expansion beyond `biz-002` / `biz-018`. The current gate proves governed `salesTeam` aggregation and backlog-by-team/stage semantics; it is not yet a full order-domain acceptance package.
