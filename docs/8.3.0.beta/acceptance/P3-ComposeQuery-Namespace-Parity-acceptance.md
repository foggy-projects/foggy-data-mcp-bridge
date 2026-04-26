---
acceptance_scope: feature
version: 8.3.0.beta
target: P3-ComposeQuery-Namespace-Test-And-Parity-Sync
doc_role: acceptance-record
doc_purpose: 记录 Compose Query namespace 测试同步与 columns API parity 文字同步的功能级正式验收结论与证据摘要
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
- purpose: 记录 P3-ComposeQuery-Namespace-Test-And-Parity-Sync 功能级正式验收结论与证据摘要。

## Background

- Version: 8.3.0.beta
- Target: P3-ComposeQuery-Namespace-Test-And-Parity-Sync
- Owner: `foggy-data-mcp-bridge-wt-dev-compose`
- Goal: 把 8.2.0.beta P0 sqlite lane 残留的 2 条 namespace 校验 failure 同步到 working-tree 行为（A1：`null/empty namespace → ""` 是合法默认），同时把 P2 已收口的 columns API heterogeneous wildcard 契约写入上游 spec，让 Java↔Python parity 在文字层面完全对齐。

## Acceptance Basis

- Decision: A1（接受 working-tree 放宽，namespace null/empty fallback 到 `""`）
- `docs/8.3.0.beta/P3-ComposeQuery-Namespace测试与Parity文字同步-需求.md`
- `docs/8.3.0.beta/P3-ComposeQuery-Namespace测试与Parity文字同步-progress.md`
- `docs/8.2.0.beta/P0-ComposeQuery-QueryPlan派生查询与关系复用规范-需求.md` §核心语义 §7
- Review rerun command (sandbox + namespace targets):
  `mvn -pl foggy-dataset-model "-Dtest=ComposeQueryContextTest,AuthorityRequestTest,SandboxLayerATest,SandboxLayerBTest,SandboxLayerCTest,ColumnsApiContractTest" -P!multi-db test`
- Full lane verification:
  `mvn -pl foggy-dataset-model -Dspring.profiles.active=sqlite -P!multi-db test` → **1702 passed / 0 failures / 1 skipped**

## Checklist

- [x] Item A 决策点：A1 选定，需求文档 §决策点 已标明（含日期与理由）。
- [x] `ComposeQueryContextTest.namespaceRequiredNonBlank` 改写为 `namespaceFallsBackToEmptyOnNullOrBlank`，断言 `null` / `""` 输入构造成功且 `namespace() == ""`。
- [x] `AuthorityRequestTest.namespaceRequired` 同上改写。
- [x] 8.2.0.beta P0 §核心语义 新增 §7 «columns 元素类型（heterogeneous wildcard）»，含元素类型枚举 / 4 条 fail-closed 规则 / 跨仓 invariant / 落地参考链接。
- [x] 全仓 sqlite lane 0 failure 达成（1702 passed）。
- [x] Python parity 0 行代码改动（语义本来就对等，仅 Java 端文字补齐）。

## Evidence

- Requirement: `docs/8.3.0.beta/P3-ComposeQuery-Namespace测试与Parity文字同步-需求.md`
- Progress: `docs/8.3.0.beta/P3-ComposeQuery-Namespace测试与Parity文字同步-progress.md`
- Spec landing: `docs/8.2.0.beta/P0-ComposeQuery-QueryPlan派生查询与关系复用规范-需求.md` §核心语义 §7
- Test rewrites: `ComposeQueryContextTest.namespaceFallsBackToEmptyOnNullOrBlank` + `AuthorityRequestTest.namespaceFallsBackToEmptyOnNullOrBlank`，targeted rerun **15 passed / 0 failures**
- Full lane: `mvn -pl foggy-dataset-model -Dspring.profiles.active=sqlite -P!multi-db test` → **1702 passed / 0 failures / 1 skipped**

## Failed Items

- none

## Risks / Open Items

- A1 决策放宽了 namespace 强约束，下游若依赖非空 namespace 必须自行校验；该约束已在测试 `@DisplayName` 与 spec 文字中显式声明。
- 后续如新增 PlanExpression 子类型（例如 `SubqueryExpr` / `CastExpr`），须在 8.2 P0 §7 列表、Python `Union[str, ...]` 类型、sandbox Layer-B `validatePlanExpression` 三处一起改 —— 已在 progress §遗留与跟踪 提示。

## Final Decision

`accepted`。Item A 测试同步与 Item B spec 文字补齐均按 A1 路径完成；sqlite lane 失败数从 7 降到 0；Python 仓零代码改动；8.2.0.beta P0 M10 整体签收的 sqlite-0-failure 硬门槛同时达成。

## Signoff Marker

- acceptance_status: signed-off
- acceptance_decision: accepted
- signed_off_by: Codex reviewer
- signed_off_at: 2026-04-26
- acceptance_record: docs/8.3.0.beta/acceptance/P3-ComposeQuery-Namespace-Parity-acceptance.md
- blocking_items: none
- follow_up_required: no
