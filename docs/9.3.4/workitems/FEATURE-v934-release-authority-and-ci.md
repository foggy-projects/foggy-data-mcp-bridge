---
doc_type: delivery-spec
delivery_type: cross-module
version: 9.3.4
ticket: v934-local-release-authority
status: APPROVED
canonical: true
execution_mode: ultra
approved_by: repository-owner-via-user-request
approved_at: 2026-07-23
replan_approved_at: 2026-07-23
activated_at: pending
activation_parent: pending
active_scope: replacement Step 5 full-clone preflight only
replan_resolution: private full clone plus pre-activation scan-runtime-source
open_questions: []
---

# Delivery Spec: 9.3.4 Local Release Authority and Version Closure

## Document Purpose

- intended_for: normal-analysis / ultra-implementation / independent-signoff
- purpose: 冻结 9.3.4 Step 5、本地 clean-commit authority 和版本签收的目标、边界、证据义务与停止条件；Steps 1–4 的既有事实继续由版本执行包承载。
- canonical_path: `docs/9.3.4/workitems/FEATURE-v934-release-authority-and-ci.md`
- compatibility_note: 文件名为历史稳定路径，不再表示 GitHub CI 接线属于当前交付范围。
- scope_revision: 2026-07-23 owner 明确移除原 Step 6 GitHub CI 接线；本文件取代 2026-07-18 的 CI-including 契约。

## Goal

- version_goal: 用仓库内受控 runner 完成 9.3.4 可搬迁、可独立复算的本地 release authority 和版本签收，不依赖 GitHub Actions、required check 或 branch protection。
- target_outcome: Step 5 生成 immutable candidate、portable archive 和 JAR/image identity 证据；随后在 exact clean `origin/main` 上取得单一 fresh local authority，并按 implementation quality -> coverage audit -> version signoff 顺序关闭 9.3.4。

## Scope

- in_scope:
  - Step 5：基于 9.3.4 Steps 1–4 stable lanes 执行单一 authority rehearsal、immutable evidence archive、candidate pointer、runtime-only image/JAR identity 和 expected-negative contract。
  - Step 7：在 exact clean `origin/main` commit 执行完整 local authority，独立复算证据，并按 implementation quality -> coverage audit -> version signoff 顺序关闭 9.3.4。
  - 复验 `DEBT-unit-mysql57-fixture-classification-migration.md` 的既有临时放行条件；实际分类迁移仍在 9.3.5 验收前关闭。
- affected_modules: root Maven/build support、`scripts/v934/step4`、`scripts/v934/step5`、Launcher package/runtime Dockerfile、9.3.1–9.3.3 successor regression owners、`docs/9.3.4`。
- external_dependencies: Docker Engine、五个 required database kinds、现有 Redis/Mongo/Vector external lanes、Maven Central、用于取得 exact `origin/main` 的常规 Git 远端操作。

## Non-Goals

- out_of_scope:
  - 原 Step 6 GitHub CI 接线整体移出：不启用或运行 GitHub Actions required gate，不建设 PR/main reusable gate、five-cell artifact collector、always-run aggregator、required check、branch protection 或 GitHub release dry-run。
  - 不删除、重写或宣称验收现有 `.github/workflows`、`scripts/v934/step6` 及其历史证据；它们保留为未启用的历史实现输入，不是当前 authority。
  - 不发布真实版本 tag、GitHub Release、生产 Docker tag 或其他外部制品。
  - 不实施 9.3.5 的 QueryFacade/public API 重构，也不实施 9.4.0 的 production module split/SPI v2。
  - 不修改 9.3.3 historical exact authority runner，也不拼接历史 run 形成新 authority。
- do_not_touch:
  - 不降低 coverage threshold、不扩大 exclusion、不允许 required skip/assumption。
  - 不清理、覆盖、stash 或混入用户的 dirty baseline；replacement runner 必须在
    独立 private full clone 中 fail closed，且 `.git` 必须为真实目录。
  - 不把 candidate pointer 当 final authority pointer；失败运行不得更新任何成功 pointer。
  - 本交付不得修改 repository Actions enablement、branch rule、required checks 或 PR merge policy。

## Confirmed Decisions

| Decision | Rationale | Compatibility / Constraint |
|---|---|---|
| 原 Step 6 标记为 owner-waived / out-of-scope | owner 明确不考虑接入 CI | 不再是 Step 7 entry 或 9.3.4 signoff blocker |
| Step 5 后直接进入 exact-clean-main 的 Step 7 | 保留既有 runner/证据编号，避免重编号破坏历史引用 | Step 5 必须先独立签收；merge/main 操作仍需单独授权 |
| GitHub Actions 在本交付期间保持 disabled 且不作为证据源 | 消除 remote run 与本地 authority 的所有权混淆 | 不配置 required check/branch protection；既有 workflow 文件不删除 |
| replacement Step 5 使用 private full clone | r1 已证明 linked worktree 的 `.git` regular file 不满足 sealed artifact tool | 不修改 Step 5 tool/manifest/hash closure；full clone 必须 exact pushed HEAD、clean、non-shallow |
| activation 前直接执行 `scan-runtime-source` | 把 r1 的环境契约缺口前移到不消耗 attempt 的轻量门 | receipt 必须 exit=0、`status=passed`、`command=scan-runtime-source`、`git_head` 精确匹配 |
| 测试只在 owning lane 执行一次，coverage collector 只聚合 exec/XML | 防止重复执行造成结果漂移或双计 | unit、SQLite broad、five-DB、external exact inventory 继续 fail closed |
| archive 使用内容相对路径和规范元数据生成 deterministic digest | Step 3/4 run-local absolute path/mtime 不可搬迁 | 解压到不同目录后必须可独立 verify |
| Launcher candidate JAR 与 runtime-only image embedded JAR 必须同源 | 本地 authority 仍需证明候选制品身份 | 禁止 package/image 路径 source rebuild 或替换 JAR |
| 9.3.4 version signoff 后才能打开 9.3.5 | 维持 roadmap 硬依赖 | 不提前并行修改 9.3.5/9.4.0 production scope |

## Acceptance Criteria

- [ ] AC-1: 单一 `verify-v934-release-gate.sh rehearsal` 串联 inventory、unit、integration、five-DB/external、9.3.1–9.3.3 successor regression、coverage collect/check、package/Launcher audit，且每个 owning suite 只执行一次。
- [ ] AC-2: candidate run root 具备 source/environment/inventory/report/DB/coverage/regression/JAR/image manifests、inner/outer hashes、deterministic archive/digest；异目录 verify、portable replay 与 image `/app/app.jar` SHA 复算通过。
- [ ] AC-3: skip flags、missing/stale/tampered artifacts、report/skip/migration/hash/source/JAR/image drift、failed pointer publication 和 credential scan 命中等 expected-negative 全部 fail closed。
- [ ] AC-4: 原 Step 6 明确记录为 `owner-waived / out-of-scope`；本交付未启用 Actions、未运行或接入 GitHub required gate、未修改 branch protection/required checks，且这些项目不再阻断 Step 7。
- [ ] AC-5: `verify-v934-release-gate.sh authority` 在 exact clean `origin/main` commit 取得单一 fresh local authority；inventory、required S0、五库/external、coverage critical gates、successor regression、package/Launcher、archive 和 same-JAR 全绿，无跨 run 拼接。
- [ ] AC-6: Unit MySQL 5.7 临时分类例外在 fresh authority 中继续满足 run-owned fixture、唯一连接回执、精确报告与 cleanup 条件，并在 9.3.4 signoff 中明确移交给 9.3.5；不得把 Step 2 历史绿色当正确性证据。
- [ ] AC-7: implementation result 完整回写后状态为 `READY_FOR_SIGNOFF`，随后独立执行 version quality、coverage audit 和 signoff；仅 accepted/accepted-with-risks 才更新 roadmap 并把 9.3.5 标 ready。

## Contract / Data / Security Constraints

- API or event contract: 不改变 production API/SPI、Bean、POM 或用户配置；candidate/final pointer、archive、summary 和 local authority schema 保持现有版本化契约。
- data and migration: required DB fixture 必须 run-owned、身份/版本/sentinel 可复算并清理；不得复用 ambient mutable database state。
- compatibility and rollback: 现有 CI/workflow/Step6 文件保持原样且不参与当前签收；恢复 CI 接线必须建立新的 owner-approved delivery spec，不得静默恢复为当前门槛。
- permissions and secrets: 本交付不变更 GitHub Actions 或 branch settings；本地证据、日志、archive 和 summary 必须做敏感扫描，禁止持久化 credential/token/password/带密钥 URL。

## Test and Evidence Obligations

| Item | Risk | Required Validation | Required Evidence |
|---|---|---|---|
| AC-1/AC-5 | critical | one local rehearsal + one exact-clean-main local authority | 精确命令、run id、tested commit、raw XML/manifests、durable status |
| AC-2/AC-3 | critical | archive/image positive + mutation/absence negatives | archive digest、异目录 verify、portable replay、embedded JAR hash、negative matrix |
| AC-4 | major | repository/remote configuration diff audit | Actions 未被本交付启用、branch settings 未改、无 CI run 被当作 authority |
| AC-6 | major | full unit lane + fixture lifecycle/negative replay | 临时放行复验、exact reports/counts/F0E0S0、9.3.5 handoff |
| AC-7 | major | ordered independent reviews | quality record、coverage audit、version signoff、roadmap status diff |

所有测试必须实际运行；仅存在测试文件、历史绿色或未执行原因均不等于通过。GitHub CI/platform evidence 与 UI/浏览器体验均为 N/A。

## Validation Cost and Attempt Control

- focused/static preflight: `<5m`，可按输入变化重跑。
- Step 5 rehearsal r1: attempt 已消耗且保持 failed-closed，不得改写或拼接。
- Step 5 replacement rehearsal: `>60m`，maximum attempts=`1`；仅在 private full
  clone preflight 全部通过并提交 activation 后启动。失败后保留安全证据、设置
  `NEEDS_REPLAN` 并停止，不自动重试。
- Step 7 authority: `>60m`，maximum attempts=`1`；只在 Step 5 已独立签收、exact clean `origin/main` 和单独执行授权齐备后运行。失败后设置 `NEEDS_REPLAN` 并停止。
- test-only、文档或证据传输变更不自动推翻与其无依赖关系的产品正确性证据。

## Active Execution Authorization

- authorized_stage: replacement Step 5 private-full-clone preflight only；preflight
  通过后允许提交 activation，并启动 exactly one replacement rehearsal；不授权
  merge、Step 7、tag、release 或 publish。
- clone_identity: canonical origin 的新 private full clone；`.git` 为 real
  directory；clean、non-shallow、HEAD 与 pushed PR head 精确一致。
- required_new_preflight:
  - `python3 scripts/v934/step5/release_artifact_tool.py scan-runtime-source
    --repo-root <clone>` exit=`0`；
  - receipt 的 `command=scan-runtime-source`、`status=passed`、
    `git_head=<exact HEAD>`、`module_count=13`；
  - 既有 manifest/static/focused negatives、runtime base、ports、pointer、
    process-owner 和 Actions-disabled 检查仍全部通过。
- activation_gate: preflight receipt 固化后，用 docs-only commit 把本文件改为
  `ULTRA_EXECUTING` 并记录 exact activation parent；activation 必须先推送并与
  PR head 精确一致，runner 才能启动。
- process_owner: 一个新的私有 `0700` tmux control directory；启动后当前会话只作观察者。
- environment: 启动命令必须 `env -u` 清除 `MAVEN_ARGS`、`MAVEN_BASEDIR`、
  `MAVEN_CONFIG`、`MAVEN_OPTS`、`MAVEN_SKIP_RC`、`JAVA_TOOL_OPTIONS`、
  `JDK_JAVA_OPTIONS`、`_JAVA_OPTIONS`。
- attempt_budget: replacement exactly one；无自动重试。
- stop_rule: preflight 不满足则不激活、不消耗 replacement attempt；runner
  启动后的任何失败均保留证据、设置 `NEEDS_REPLAN` 并停止。

## Risks and Open Questions

- known_risks:
  - 不接入 required CI/branch protection 后，未来 PR/merge 不具备自动 fail-closed 保障；这是 owner 明确接受的流程风险，不是本地 9.3.4 authority blocker。
  - 完整 authority 成本高；必须先用 contract/negative/focused preflight 收敛，再各执行唯一一次 Step 5 和 Step 7。
  - exact clean `origin/main` 仍需要后续单独 merge/main 授权；本契约不授权 merge、tag、release 或 publish。
- open_questions: none

## Ultra Execution Contract

- 先读取本文件、`docs/9.3.4/README.md`、implementation/test/acceptance plans 和现有 v934 runner contracts。
- 在 scope 内自主决定具体本地 runner、tool 和测试结构；不得修改或接入 GitHub workflow/Step6/branch settings。
- 如需改变目标、required DB set、coverage minima、兼容、安全、production API 或后续版本边界，设置 `NEEDS_REPLAN` 并停止扩展。
- 运行与改动面匹配的验证，不得声称未实际运行的测试通过；失败 evidence 保留但不得拼接。
- 完成后填写 `Implementation Result`，并将状态改为 `READY_FOR_SIGNOFF`；不得自行设置 `ACCEPTED`。

## Implementation Result

> 由执行会话填写。

- implementation_summary: no-CI scope and one-attempt activation were committed; the
  authorized rehearsal failed closed in `runtime-source-before` because the
  release artifact tool requires `.git` to be a real directory and rejects a
  linked Git worktree.
- changed_paths: docs-only scope/activation/evidence changes; no production,
  POM, workflow, runner, API or SPI changes.
- tests_and_results: manifests PASS; shell/Python static PASS; artifact
  self-test PASS; package negative `120/120`; pointer negative `5/5`; formal
  rehearsal exit=`1` before Step 4/Maven/Docker.
- manual_or_experience_evidence: candidate/final pointers absent; no run-owned
  Docker resources; GitHub CI/platform evidence N/A by approved scope.
- deviations: the clean-worktree execution environment satisfied the written
  delivery contract but not the tool's previously unstated full-clone
  requirement.
- residual_risks: required CI/branch protection intentionally absent by owner
  decision; replacement full-clone rehearsal remains pending.
- readiness: APPROVED / replacement preflight pending

## References

- requirement / issue: `docs/9.3.4/requirement/P0-test-ci-evidence-chain.md`
- architecture / glossary: `docs/9.3.4/implementation-plan.md`, `docs/9.3.4/contract/test-lane-evidence-contract.md`
- related work items: `docs/9.3.4/workitems/DEBT-unit-mysql57-fixture-classification-migration.md`
- failed execution evidence:
  `docs/9.3.4/evidence/step-5/step5-local-rehearsal-no-ci-r1-git-metadata-preflight-fail-closed-20260723.md`
