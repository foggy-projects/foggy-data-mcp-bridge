---
acceptance_scope: feature
version: 8.3.0.beta
target: P2-ComposeQuery-Columns-API
doc_role: acceptance-record
doc_purpose: 记录 Compose Query columns API 收口的功能级正式验收结论与证据摘要
status: signed-off
decision: accepted
signed_off_by: Codex reviewer
signed_off_at: 2026-04-26
reviewed_by: Codex reviewer
blocking_items: []
follow_up_required: no
evidence_count: 5
---

# Feature Acceptance

## Document Purpose

- doc_type: acceptance
- intended_for: signoff-owner / reviewer / owning-module
- purpose: 记录 P2-ComposeQuery-Columns-API 功能级正式验收结论与证据摘要。

## Background

- Version: 8.3.0.beta
- Target: P2-ComposeQuery-Columns-API
- Owner: `foggy-data-mcp-bridge-wt-dev-compose`
- Goal: 将 Java Compose Query 的 dual columns API 收口为单字段、单 setter、`List<?>` 接收的契约，并解除 fsscript 边界对 PlanExpression columns 元素的强转限制。

## Acceptance Basis

- `docs/8.3.0.beta/P2-ComposeQuery-Columns-API-收口-需求.md`
- `docs/8.3.0.beta/P2-ComposeQuery-Columns-API-收口-progress.md`
- `docs/8.2.0.beta/P0-ComposeQuery-QueryPlan派生查询与关系复用规范-progress.md`
- `foggy-dataset-model/src/test/java/com/foggyframework/dataset/db/model/engine/compose/plan/ColumnsApiContractTest.java`
- Review rerun command: `mvn -pl foggy-dataset-model "-Dtest=ColumnsApiContractTest,BaseModelPlanTest,DerivedQueryPlanTest,ScriptRuntimeTest" -P!multi-db test`

## Checklist

- [x] `BaseModelPlan / DerivedQueryPlan / Dsl.FromOptions / QueryOptions` 已移除 `columnsObj` 字段和方法。
- [x] `Dsl.from()` 与 `QueryPlan.query(opts)` 不再保留 `columnsObj() != null ?` 过渡逻辑。
- [x] `ScriptRuntime` 已移除 `(List<String>) args.get("columns")` 强转，columns 元素可承载 PlanExpression。
- [x] `ExpressionWhitelistValidator` 已支持 heterogeneous columns 与 PlanExpression 节点白名单。
- [x] 守护测试覆盖 mixed columns、非法元素拒绝、`columnsObj` 不可回流、wildcard setter、`Query.col(...).sum().as(...)` 示例入口。
- [x] 8.2.0.beta P0 M10 progress 行已补交叉引用。

## Evidence

- Requirement: `docs/8.3.0.beta/P2-ComposeQuery-Columns-API-收口-需求.md`
- Progress: `docs/8.3.0.beta/P2-ComposeQuery-Columns-API-收口-progress.md`
- Cross-reference: `docs/8.2.0.beta/P0-ComposeQuery-QueryPlan派生查询与关系复用规范-progress.md`
- Guard tests: `ColumnsApiContractTest` 7 tests
- Review rerun: **44 passed / 0 failures** for `ColumnsApiContractTest,BaseModelPlanTest,DerivedQueryPlanTest,ScriptRuntimeTest`

## Failed Items

- none

## Risks / Open Items

- Full sqlite lane still has 7 known pre-existing failures documented in progress; none are introduced by this P2 scope.
- Broader Layer-C public surface cleanup remains outside this P2 signoff and should stay tracked under the existing M9/M10 sandbox follow-up path.

## Final Decision

`accepted`。The feature meets the stated P2 acceptance criteria, has targeted guard coverage, and has no P2-blocking regression.

## Signoff Marker

- acceptance_status: signed-off
- acceptance_decision: accepted
- signed_off_by: Codex reviewer
- signed_off_at: 2026-04-26
- acceptance_record: docs/8.3.0.beta/acceptance/P2-ComposeQuery-Columns-API-acceptance.md
- blocking_items: none
- follow_up_required: no
