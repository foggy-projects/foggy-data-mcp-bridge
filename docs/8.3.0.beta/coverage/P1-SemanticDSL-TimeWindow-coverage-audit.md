---
audit_scope: feature
audit_mode: pre-acceptance-check
version: 8.3.0.beta
target: P1-SemanticDSL-TimeWindow-Java
status: reviewed
conclusion: ready-for-acceptance
reviewed_by: codex
reviewed_at: 2026-04-28
follow_up_required: no
---

# Test Coverage Audit

## Background

本审计检查 Java 侧 `timeWindow` DSL 是否具备进入正式签收的测试证据。审计对象是 `docs/8.3.0.beta/P1-SemanticDSL-时间窗口能力设计.md` 和 progress 中列出的 AC-1 ~ AC-10。Python parity 镜像是 S12 deferred，不纳入本次 Java feature acceptance。

## Audit Basis

- Design: `docs/8.3.0.beta/P1-SemanticDSL-时间窗口能力设计.md`
- Progress: `docs/8.3.0.beta/P1-SemanticDSL-时间窗口能力-progress.md`
- Quality gate: `docs/8.3.0.beta/quality/P1-SemanticDSL-TimeWindow-implementation-quality.md`
- Latest evidence commit: `541921a test(timeWindow): add MySQL 8 parity lane`

## Coverage Matrix

| Item | Risk | Evidence Layer | Evidence | Conclusion |
|---|---|---|---|---|
| AC-1 `TimeWindowDef` 7 字段 + fail-closed 构造 | major | unit-test | `TimeWindowDefTest` / progress AC-1 | covered |
| AC-2 grain x comparison 兼容矩阵 | critical | unit-test | `TimeWindowValidatorTest$ErrorCodes` 11 + `HappyPaths` 3 | covered |
| AC-3 8 种 comparison 展开合法 SQL | critical | unit-test / parity | `TimeWindowExpanderTest` comparative 6 + cumulative 2 + rolling 3 | covered |
| AC-4 相对日期表达式四方言 lowering | major | unit-test | `RelativeDateParserTest` 24，覆盖 MySQL / PostgreSQL / SQL Server / SQLite | covered |
| AC-5 真实 SQL 数据比对 | critical | integration-test | `ComparativeExecutionIntegrationTest` 1 + `TimeWindowExecutionIntegrationTest` 4；SQLite / PostgreSQL / SQL Server / MySQL 8 通过 | covered |
| AC-6 parity catalog 与上游 P1 基线对齐 | major | parity-test | `src/test/resources/parity/timeWindow/` 11 fixture，7 happy + 4 negative | covered |
| AC-7 request / controller / tool 接入 | critical | integration-test / script-runtime | `ScriptRuntimeTest` 覆盖 `dsl()` timeWindow 请求映射；generateSql preview 可执行 | covered |
| AC-8 MCP schema 暴露 timeWindow shape | major | unit-test / schema-parse | `ToolConfigLoaderTest` 8；`query_model_v3_schema.json` JSON 解析通过 | covered |
| AC-9 跨方言 lane 全量验收 | critical | integration-test / manual-evidence | SQLite / PostgreSQL / SQL Server / MySQL 8 passed；MySQL 5.7 capability short-circuit | covered |
| AC-10 design / progress 状态回写 | minor | documentation-evidence | design / progress frontmatter 和阶段表已回写 | covered |

## Evidence Summary

- MySQL 8 lane:
  - `mvn -pl foggy-dataset-model "-Dtest=ComposeRealSqlParityTest,ComparativeExecutionIntegrationTest,TimeWindowExecutionIntegrationTest" "-Dspring.profiles.active=mysql8" "-P!multi-db" test`
  - result: 8 passed / 0 failures / 0 skipped
- SQLite default lane:
  - `mvn -pl foggy-dataset-model "-Dtest=TimeWindowExecutionIntegrationTest" test`
  - result: 4 passed / 0 failures / 0 skipped
- MySQL 5.7 lane:
  - result recorded in progress: 8 passed / 0 failures / 0 skipped
  - note: 5.7 不支持窗口函数，timeWindow execution 4 tests 走 capability short-circuit
- Earlier progress evidence:
  - timeWindow / comparative feature subset: 75 passed
  - parity baseline: 50 passed
  - schema loader: 8 passed
  - script runtime: covered request mapping

## Gaps

- Python parity 镜像未启动，按 S12 deferred 单独跟踪；不影响 Java feature acceptance。
- MySQL 5.7 没有真实窗口函数结果比对，原因是数据库能力限制；MySQL 8 lane 已补足 MySQL 家族窗口能力证据。
- 多窗口 `timeWindows` 数组不在本批验收范围。

## Recommended Next Skills

- `foggy-acceptance-signoff`

## Conclusion

`ready-for-acceptance`。

Java 侧高风险验收项均有自动化或可复核 evidence 承接，当前测试证据足以进入正式签收。
