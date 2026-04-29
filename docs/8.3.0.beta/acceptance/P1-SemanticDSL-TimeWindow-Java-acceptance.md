---
acceptance_scope: feature
version: 8.3.0.beta
target: P1-SemanticDSL-TimeWindow-Java
status: signed-off
decision: accepted
signed_off_by: codex
signed_off_at: 2026-04-28
reviewed_by: N/A
blocking_items: []
follow_up_required: no
evidence_count: 7
doc_role: feature-acceptance
doc_purpose: Sign off Java-side SemanticDSL timeWindow implementation and evidence.
---

# Feature Acceptance

## Background

本次验收对象是 Java 侧 `SemanticQueryRequest.timeWindow` 能力：LLM-facing JSON DSL 解析、校验、QueryPlan 展开、SQL preview、MCP schema 暴露、真实 SQL 执行和跨方言 evidence。

验收范围不包含 Python parity 镜像。Python 侧按 progress S12 标记为 deferred，后续独立立项，不阻断 Java feature 签收。

## Acceptance Basis

- `docs/8.3.0.beta/P1-SemanticDSL-时间窗口能力设计.md`
- `docs/8.3.0.beta/P1-SemanticDSL-时间窗口能力-progress.md`
- `docs/8.3.0.beta/quality/P1-SemanticDSL-TimeWindow-implementation-quality.md`
- `docs/8.3.0.beta/coverage/P1-SemanticDSL-TimeWindow-coverage-audit.md`
- Commit `541921a test(timeWindow): add MySQL 8 parity lane`
- Earlier timeWindow implementation commits listed in progress `代码变更记录`

## Checklist

- [x] `timeWindow` JSON contract defined and wired into Java request / service / tool path.
- [x] `field/grain/comparison/range/value/targetMetrics/rollingAggregator` validation is fail-closed.
- [x] Comparative / cumulative / rolling time windows expand through QueryPlan semantics instead of SQL string templates.
- [x] LLM-facing MCP schema exposes `payload.timeWindow`.
- [x] Parity catalog contains 11 fixture cases.
- [x] Real SQL parity covers SQLite, PostgreSQL, SQL Server and MySQL 8.
- [x] MySQL 5.7 unsupported window-function behavior is documented and guarded by capability short-circuit.
- [x] Implementation quality gate passed.
- [x] Test coverage audit concluded `ready-for-acceptance`.
- [x] Design and progress docs are updated.

## Evidence

- Unit and parity evidence:
  - `TimeWindowValidatorTest$ErrorCodes` 11 + `HappyPaths` 3.
  - `TimeWindowExpanderTest` comparative 6 + cumulative 2 + rolling 3.
  - `RelativeDateParserTest` 24 across MySQL / PostgreSQL / SQL Server / SQLite.
  - `src/test/resources/parity/timeWindow/` 11 fixtures.
- Integration evidence:
  - `ComparativeExecutionIntegrationTest` and `TimeWindowExecutionIntegrationTest`.
  - `ScriptRuntimeTest` covers `dsl()` timeWindow request mapping.
  - SQL preview output is directly executable and matched against hand-written SQL.
- MySQL 8 evidence:
  - `mvn -pl foggy-dataset-model "-Dtest=ComposeRealSqlParityTest,ComparativeExecutionIntegrationTest,TimeWindowExecutionIntegrationTest" "-Dspring.profiles.active=mysql8" "-P!multi-db" test`
  - Result: 8 passed / 0 failures / 0 skipped.
- SQLite evidence:
  - `mvn -pl foggy-dataset-model "-Dtest=TimeWindowExecutionIntegrationTest" test`
  - Result: 4 passed / 0 failures / 0 skipped.
- Quality gate:
  - `docs/8.3.0.beta/quality/P1-SemanticDSL-TimeWindow-implementation-quality.md`
  - Decision: `ready-for-coverage-audit`.
- Coverage audit:
  - `docs/8.3.0.beta/coverage/P1-SemanticDSL-TimeWindow-coverage-audit.md`
  - Conclusion: `ready-for-acceptance`.

## Failed Items

None.

## Risks / Open Items

- Python parity 镜像仍是 deferred，后续独立跟踪；不属于本次 Java feature acceptance。
- MySQL 5.7 无窗口函数能力，窗口真实执行证据由 MySQL 8 lane 承接。
- 多窗口 `timeWindows` 数组仍是未来扩展占位，本次仅签收单个 `timeWindow`。

## Final Decision

`accepted`。

Java 侧 `timeWindow` 功能满足设计稿验收标准，关键路径均有自动化或可复核 evidence，未发现阻断项。

## Signoff Marker

- acceptance_status: signed-off
- acceptance_decision: accepted
- signed_off_by: codex
- signed_off_at: 2026-04-28
- acceptance_record: docs/8.3.0.beta/acceptance/P1-SemanticDSL-TimeWindow-Java-acceptance.md
- blocking_items: none
- follow_up_required: no
