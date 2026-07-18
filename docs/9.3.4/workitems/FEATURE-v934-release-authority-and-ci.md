---
doc_type: delivery-spec
delivery_type: cross-module
version: 9.3.4
ticket: v934-release-authority-and-ci
status: ULTRA_EXECUTING
canonical: true
execution_mode: ultra
approved_by: repository-owner-via-user-request
approved_at: 2026-07-18
open_questions: []
---

# Delivery Spec: 9.3.4 Release Authority, Required CI, and Version Closure

## Document Purpose

- intended_for: normal-analysis / ultra-implementation / independent-signoff
- purpose: 冻结已确认的 9.3.4 Steps 5–7 目标、边界、证据义务和签收门槛；Steps 1–4 的既有事实继续由版本执行包承载。
- canonical_path: `docs/9.3.4/workitems/FEATURE-v934-release-authority-and-ci.md`

## Goal

- version_goal: 完成 9.3.4 测试与 CI 证据链，使 PR、main 和 release 不存在测试/外库跳过、缺失 required job 或重建不同发布 JAR 所形成的伪绿色通道。
- target_outcome: 单一 release authority runner 可生成可搬迁、可独立复算的 immutable candidate；GitHub Actions 实际运行稳定 required aggregator；release 只消费已测 Launcher JAR；最终 clean-commit authority、质量、覆盖和版本签收闭环。

## Scope

- in_scope:
  - Step 5：基于 9.3.4 Steps 1–4 stable lanes 建立单一 authority rehearsal、immutable evidence archive、candidate pointer、runtime-only image/JAR identity 和 expected-negative contract。
  - Step 6：建立 PR/main reusable required gate、五数据库 exact artifact collector、always-run aggregator、branch protection required check 以及 same-tested-artifact release dry-run/发布路径。
  - Step 7：在 exact clean commit 执行完整 authority，独立复算证据，并按 implementation quality -> coverage audit -> version signoff 顺序关闭 9.3.4。
  - 修复阻断上述门禁的当前仓库 CI/runner 兼容问题；按既有临时放行契约复验 `DEBT-unit-mysql57-fixture-classification-migration.md`，实际分类迁移仍在 9.3.5 验收前关闭。
- affected_modules: root Maven/build support、`scripts/v934`、`.github/workflows`、Launcher package/runtime Dockerfile、9.3.1–9.3.3 successor regression owners、`docs/9.3.4`。
- external_dependencies: GitHub Actions/artifacts/branch rules、Docker Engine、五个 required database kinds、现有 Redis/Mongo/Vector external lanes、Maven Central。

## Non-Goals

- out_of_scope:
  - 不实施 9.3.5 的 QueryFacade/public API 重构，也不实施 9.4.0 的 production module split/SPI v2。
  - 不修改 9.3.3 historical exact authority runner 或拼接历史 run 形成新 authority。
  - 不发布真实版本 tag、GitHub Release 或生产 Docker tag；Step 6 只要求可证明的 dry-run/same-artifact path，除非版本发布另有明确授权。
- do_not_touch:
  - 不降低 coverage threshold、不扩大 exclusion、不允许 required skip/assumption。
  - 不清理或覆盖用户的 dirty baseline；runner 必须在保护现状的前提下 fail closed。
  - 不把 candidate pointer 当 final authority pointer；失败运行不得更新任何成功 pointer。

## Confirmed Decisions

| Decision | Rationale | Compatibility / Constraint |
|---|---|---|
| 以现有 Step 4 formal-r4 lane/contract 为输入，新建 v934 release runner | 复用已验收的报告、DB、coverage 和 lifecycle 语义 | 不修改或冒充 v933 exact runner；Step 7 必须 fresh replay |
| 测试只在 owning lane 执行一次，coverage collector 只聚合 exec/XML | 防止重复执行造成结果漂移或双计 | unit、SQLite broad、five-DB、external 的 exact inventory 继续 fail closed |
| 五库 artifact 必须是 `{sqlite,mysql57,mysql8,postgres15,sqlserver2022}` exact set | `needs.result` 不能证明 cell 完整性 | artifact identity 绑定 commit/run/attempt/db kind，重复、缺失、wrong-kind 都失败 |
| required aggregator 使用 `always()` 并拒绝 failure/skipped/cancelled | required check 必须阻断局部绿色 | check name 稳定并由 main branch rule 实际引用 |
| release 仅下载已测 Launcher JAR，runtime-only Dockerfile 直接 COPY | 保证 GitHub asset、evidence 和 image 内 JAR 同源 | release path 禁止 `-DskipTests`、`--skip-external-db` 和 source rebuild |
| archive 使用内容相对路径/规范元数据生成 deterministic digest | Step 3/4 run-local absolute path/mtime 不可搬迁 | 解压到不同目录后必须可独立 verify |
| 9.3.4 version signoff 后才能打开 9.3.5 | 维持 roadmap 硬依赖 | 不提前并行修改 9.3.5/9.4.0 production scope |

## Acceptance Criteria

- [ ] AC-1: 单一 `verify-v934-release-gate.sh` 在 rehearsal 中串联 inventory、unit、integration、five-DB/external、9.3.1–9.3.3 successor regression、coverage collect/check、package/Launcher audit，且每个 owning suite 只执行一次。
- [ ] AC-2: candidate run root 具备 source/environment/inventory/report/DB/coverage/regression/JAR/image manifests、inner/outer hashes、deterministic archive/digest；异目录 download-and-verify 与 image `/app/app.jar` SHA 复算通过。
- [ ] AC-3: skip flags、missing/stale/tampered artifacts、report/skip/migration/hash/source/JAR/image drift、failed pointer publication、credential scan 命中等 expected-negative 全部 fail closed。
- [ ] AC-4: reusable CI 的 required jobs 与 always-run aggregator 在 PR/main 实际运行；五库 exact set/cardinality=5，aggregator 对 failure/skipped/cancelled 和 artifact missing/duplicate/wrong-kind 的负向验证通过。
- [ ] AC-5: main branch protection 实际要求稳定的 9.3.4 aggregator check；权限或平台状态无法证明时版本签收必须 blocked。
- [ ] AC-6: release dry-run 校验目标 SHA，消费同一已测 Launcher JAR/archive/digests；GitHub asset candidate SHA 与 runtime-only image embedded JAR SHA 精确一致，缺件或 rebuild 路径失败。
- [ ] AC-7: Step 7 在 exact clean commit 取得单一 fresh authority；inventory、required S0、五库/external、coverage critical gates、successor regression、package/Launcher、archive 和 same-JAR 全绿，无跨 run 拼接。
- [ ] AC-8: Unit MySQL 5.7 临时分类例外在 fresh authority 中继续满足 run-owned fixture、唯一连接回执、精确报告与 cleanup 条件，并在 9.3.4 signoff 中明确移交给 9.3.5；不得把 Step 2 历史绿色当正确性证据。
- [ ] AC-9: implementation result 完整回写后状态为 `READY_FOR_SIGNOFF`，随后独立执行 version quality、coverage audit 和 signoff；仅 accepted/accepted-with-risks 才更新 roadmap 并把 9.3.5 标 ready。

## Contract / Data / Security Constraints

- API or event contract: 不改变 production API；workflow `workflow_call` 输入、artifact 命名、summary schema、aggregator check name 与 pointer publication 视为版本化 CI contract。
- data and migration: required DB fixture 必须 run-owned、身份/版本/sentinel 可复算并清理；不得复用 ambient mutable database state。
- compatibility and rollback: 保留 legacy workflows 仅到 replacement authority 实际证明完成；随后删除或降为非权威且不得产生同名 required check。回滚 workflow 时不得恢复 skip/rebuild 通道。
- permissions and secrets: workflow 最小权限；证据、日志、artifact 和 summary 做敏感扫描；禁止持久化 credential/token/password/带密钥 URL。branch rule 变更仅限本仓库 `main` 和本契约 stable check。

## Test and Evidence Obligations

| Item | Risk | Required Validation | Required Evidence |
|---|---|---|---|
| AC-1/AC-7 | critical | local rehearsal + clean authority | 精确命令、run id、tested commit、raw XML/manifests、durable status |
| AC-2/AC-3 | critical | archive/image positive + mutation/absence negatives | archive digest、异目录 verify、embedded JAR hash、negative matrix |
| AC-4/AC-5 | critical | actual GitHub Actions PR/main + branch API inspection | workflow/run/job/artifact IDs、aggregator states、branch protection response |
| AC-6 | critical | non-publishing release dry-run | tested JAR/archive/image digest receipt；missing/rebuild negative |
| AC-8 | major | full unit lane + fixture lifecycle/negative replay | 临时放行复验、exact reports/counts/F0E0S0、9.3.5 handoff |
| AC-9 | major | ordered independent reviews | quality record、coverage audit、version signoff、roadmap status diff |

所有测试必须实际运行；仅存在测试文件、历史绿色或未执行原因均不等于通过。UI/浏览器体验为 N/A。

## Risks and Open Questions

- known_risks:
  - GitHub-hosted runner 对 Docker/database image、容量和执行时长有限制；必须通过真实 run 证明，不能用本地结果代替。
  - 当前 main 无 branch protection，既有 lifecycle/pivot workflows 连续失败，旧 release 使用 skip/rebuild；这些是本 work item 的已确认阻断现状。
  - 完整 authority 成本高；先用 contract/negative/focused rehearsal 收敛，再执行不可复用的 final run。
- open_questions: none

## Ultra Execution Contract

- 先读取本文件、`docs/9.3.4/README.md`、implementation/test/acceptance plans 和现有 v934 runner contracts。
- 在 scope 内自主决定具体脚本、workflow、tool 和测试结构；优先扩展已验收的 authority libraries。
- 如需改变目标、required DB set、coverage minima、兼容、安全、production API 或后续版本边界，设置 `NEEDS_REPLAN` 并停止扩展。
- 运行与改动面匹配的验证，不得声称未实际运行的测试通过；失败 evidence 保留但不得拼接。
- 完成后填写 `Implementation Result`，并将状态改为 `READY_FOR_SIGNOFF`；不得自行设置 `ACCEPTED`。

## Implementation Result

> 由执行会话填写。

- implementation_summary: pending
- changed_paths: pending
- tests_and_results: pending
- manual_or_experience_evidence: experience N/A; CI/platform evidence pending
- deviations: none
- residual_risks: pending
- readiness: pending

## References

- requirement / issue: `docs/9.3.4/requirement/P0-test-ci-evidence-chain.md`
- architecture / glossary: `docs/9.3.4/implementation-plan.md`, `docs/9.3.4/contract/test-lane-evidence-contract.md`
- related work items: `docs/9.3.4/workitems/DEBT-unit-mysql57-fixture-classification-migration.md`
