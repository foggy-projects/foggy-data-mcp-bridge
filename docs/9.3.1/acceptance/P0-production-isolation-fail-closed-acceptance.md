---
acceptance_scope: feature
version: 9.3.1
target: P0-production-isolation-fail-closed
doc_role: acceptance-record
doc_purpose: Record the formal acceptance decision for 9.3.1 production isolation and fail-closed hardening.
status: signed-off
decision: accepted-with-risks
signed_off_by: Codex
signed_off_at: 2026-07-13
reviewed_by: Codex + independent read-only reviewers
blocking_items: []
follow_up_required: yes
evidence_count: 8
---

# 9.3.1 P0 Production Isolation and Fail-Closed Acceptance

## Document Purpose

- doc_type: acceptance
- intended_for: release owner / module owner / reviewer
- purpose: 记录 9.3.1 生产隔离与 fail-closed 功能的正式验收结论、证据边界和后续责任。

## Background

- Version: 9.3.1
- Target: P0-production-isolation-fail-closed
- Owner: `foggy-dataset-model` / `foggy-dataset-mcp` / `addons/foggy-dataset-model-cache`
- Goal: 在自动配置、模型生命周期和模块化改造前，关闭测试入口、数据源回退、Step 顺序和缓存隔离中的已知 fail-open 路径。

## Acceptance Basis

- `docs/9.3.1/workitems/P0-production-isolation-fail-closed.md`
- `docs/9.3.1/roadmap-9.3.1-to-9.4.0.md`
- `docs/9.3.1/workitems/P0-production-isolation-fail-closed-progress.md`
- `docs/9.3.1/quality/production-isolation-fail-closed-implementation-quality.md`
- `docs/9.3.1/coverage/production-isolation-fail-closed-coverage-audit.md`
- `docs/9.3.1/test/production-isolation-fail-closed-test-evidence.md`
- 相关 Surefire 报告与本地串行 Maven 执行记录
- 根 reactor 25 模块 package 装配记录

## Checklist

- [x] scope 内功能点已全部交付
- [x] 原始功能 acceptance criteria 已逐项覆盖；“先红后绿”的不可变历史证据作为非阻断遗留单独列出
- [x] 关键测试已通过
- [x] 体验验证已标记 `N/A`，本轮无页面与交互交付
- [x] 文档、配置、依赖项已闭环，跨版本事项已指定 owner 和 follow-up 版本

## Evidence

- Requirement:
  - `docs/9.3.1/workitems/P0-production-isolation-fail-closed.md`
  - `docs/9.3.1/roadmap-9.3.1-to-9.4.0.md`
- Test:
  - `docs/9.3.1/test/production-isolation-fail-closed-test-evidence.md`
  - `docs/9.3.1/coverage/production-isolation-fail-closed-coverage-audit.md`
  - cache 专项 86/86，model 定向 27/27，PreAgg→L2 1/1，MCP 5/5，Semantic Controller 2/2
  - 新增 PreAgg 联合测试前的 SQLite 全量基线 3289 tests，0 failure/error，3 skipped，新增用例另行 1/1 通过；MySQL 5.7、PostgreSQL 15、SQL Server 2022 各 18/18
- Experience:
  - N/A，后端隔离和执行链治理，无页面验收项
- Artifact:
  - `addons/foggy-dataset-model-cache/target/surefire-reports`
  - `foggy-dataset-model/target/surefire-reports`
  - `foggy-dataset-mcp/target/surefire-reports`
  - 根 reactor `mvn '-P!multi-db' -DskipTests package`：25/25 modules success，仅作为编译与装配证据

## Failed Items

- none

## Risks / Open Items

- launcher/starter 最终发布物默认 404 黑盒 smoke 未归档；owner: starter/web 自动配置；follow-up: 9.3.2。
- 显式 `allow-global-fallback-for-namespace` 兼容开关仍可放宽非默认 namespace 语义；owner: starter/configuration；follow-up: 9.3.2 纳入生产配置检查。
- catalog generation、跨 JVM Redis 身份与弱注册表并发/GC 压力证据尚未交付；owner: model lifecycle/cache；follow-up: 9.3.3。
- Surefire/Failsafe 分层、覆盖率门禁、不可变 CI evidence artifact 以及可归档的“先红后绿”历史证据尚未交付；owner: build/CI；follow-up: 9.3.4。

## Final Decision

- decision: `accepted-with-risks`
- 9.3.1 声明范围内的 critical/major 正确性与隔离项已有充分自动化和真实数据库证据，无 blocker/high。
- 上述风险不构成 9.3.1 阻断，但不得被描述为已完成；分别由 9.3.2、9.3.3 和 9.3.4 继续收口。
- 9.3.2 开工门已解锁，下一迭代按既定顺序执行。

## Signoff Marker

- acceptance_status: signed-off
- acceptance_decision: accepted-with-risks
- signed_off_by: Codex
- signed_off_at: 2026-07-13
- acceptance_record: docs/9.3.1/acceptance/P0-production-isolation-fail-closed-acceptance.md
- blocking_items: none
- follow_up_required: yes
