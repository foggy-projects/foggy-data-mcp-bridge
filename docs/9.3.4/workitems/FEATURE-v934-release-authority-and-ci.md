---
doc_type: delivery-spec
delivery_type: cross-module
version: 9.3.4
ticket: v934-local-release-authority
status: NEEDS_REPLAN
canonical: true
execution_mode: ultra
approved_by: repository-owner-via-user-request
approved_at: 2026-07-23
replan_approved_at: 2026-07-23
activated_at: 2026-07-23
activation_parent: 22e737e09bb3e283aa21894d3f0b92ef205ff6b9
active_scope: exactly one Step 7 authority run on exact clean origin/main; no CI, tag, release or publish
replan_resolution: private full clone plus pre-activation scan-runtime-source
replacement_preflight_receipt_sha256: 5445b2593e5c6f3d929a7bbf2a7dc6ab3eb53cd6ea8c7c4c354d1475847019f9
replacement_run_id: step5-local-rehearsal-20260723-no-ci-fullclone-r2
replacement_tested_commit: 6c3ee97abbe49c0cf5cf485d2ddeb4ba7ff7c84f
replacement_completed_at: 2026-07-23
accepted_scope: replacement Step 5 rehearsal only
accepted_at: 2026-07-23
accepted_by: independent-release-reviewer
acceptance_record: docs/9.3.4/acceptance/step5-local-rehearsal-no-ci-fullclone-r2-signoff-20260723.md
step7_contract_approved_at: 2026-07-23
step7_contract_approved_by: repository-owner-via-user-request
step7_merge_authorized: true
step7_authority_runner_authorized: true
step7_entry_pr: 124
step7_merge_commit: 0c0e7a2921d7a7f10f9a3640b08d015cce5c45db
step7_authority_authorized_at: 2026-07-23
step7_activation_parent: 0c0e7a2921d7a7f10f9a3640b08d015cce5c45db
step7_preflight_receipt_sha256: 095621143a34e15c9c90e987eb8406e8f8ee9b0800a20fc6721ea33ef49894ea
step7_run_id: step7-local-authority-20260723-r1
step7_attempt_budget: 1
step7_attempt_consumed: true
step7_runner_status: candidate-passed
step7_semantic_replay_status: failed-closed
step7_replan_ticket: v934-step7-semantic-portable-replay
step7_replan_approved_at: 2026-07-23
step7_replan_approved_by: repository-owner-via-user-request
step7_replan_implementation_status: ACCEPTED
step7_replan_review_chain_status: accepted-by-repository-owner
semantic_replay_replacement_step5_attempt_budget: 1
semantic_replay_replacement_step5_attempt_consumed: false
semantic_replay_replacement_step7_attempt_budget: 1
semantic_replay_replacement_step7_attempt_consumed: false
open_questions: []
---

# Delivery Spec: 9.3.4 Local Release Authority and Version Closure

## Document Purpose

- intended_for: normal-analysis / ultra-implementation / independent-signoff
- purpose: 冻结 9.3.4 Step 5、本地 clean-commit authority 和版本签收的目标、边界、证据义务与停止条件；Steps 1–4 的既有事实继续由版本执行包承载。
- canonical_path: `docs/9.3.4/workitems/FEATURE-v934-release-authority-and-ci.md`
- compatibility_note: 文件名为历史稳定路径，不再表示 GitHub CI 接线属于当前交付范围。
- scope_revision: 2026-07-23 owner 明确移除原 Step 6 GitHub CI 接线；本文件取代 2026-07-18 的 CI-including 契约。
- step7_revision: 2026-07-23 owner 批准冻结 Step 7 最小契约，并授权在实时复核后以 merge commit 合并 PR #124；本次不授权 Step 7 authority runner。
- step7_activation_revision: 2026-07-23 owner 在 PR #124 合并后明确授权继续；exact-main private-full-clone preflight 已通过，现激活 exactly one Step 7 authority attempt。
- step7_replan_revision: 2026-07-23 downstream semantic portable replay 证明现有 replay/release-tool 契约互相冲突；旧 runner 只保留 `candidate-passed` 机械事实，不能生成 final authority pointer。总 feature 进入 `NEEDS_REPLAN`，修复契约由 `BUG-step7-semantic-portable-replay-tool-contract.md` 唯一承载。

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
| Step 5 后直接进入 exact-clean-main 的 Step 7 | 保留既有 runner/证据编号，避免重编号破坏历史引用 | Step 5 已独立签收；PR #124 merge 已获本次授权，authority runner 仍需单独授权 |
| PR #124 必须使用 merge commit | 保留 145 个既有审计提交及其 tested/accepted ancestry，不把大范围历史压成新的 squash source identity | 禁止 squash/rebase；合并前必须重新核对 exact head/base、MERGEABLE/CLEAN、Actions disabled 和无活动 run |
| GitHub Actions 在本交付期间保持 disabled 且不作为证据源 | 消除 remote run 与本地 authority 的所有权混淆 | 不配置 required check/branch protection；既有 workflow 文件不删除 |
| replacement Step 5 使用 private full clone | r1 已证明 linked worktree 的 `.git` regular file 不满足 sealed artifact tool | 不修改 Step 5 tool/manifest/hash closure；full clone 必须 exact pushed HEAD、clean、non-shallow |
| activation 前直接执行 `scan-runtime-source` | 把 r1 的环境契约缺口前移到不消耗 attempt 的轻量门 | receipt 必须 exit=0、`status=passed`、`command=scan-runtime-source`、`git_head` 精确匹配 |
| 测试只在 owning lane 执行一次，coverage collector 只聚合 exec/XML | 防止重复执行造成结果漂移或双计 | unit、SQLite broad、five-DB、external exact inventory 继续 fail closed |
| archive 使用内容相对路径和规范元数据生成 deterministic digest | Step 3/4 run-local absolute path/mtime 不可搬迁 | 解压到不同目录后必须可独立 verify |
| Launcher candidate JAR 与 runtime-only image embedded JAR 必须同源 | 本地 authority 仍需证明候选制品身份 | 禁止 package/image 路径 source rebuild 或替换 JAR |
| 9.3.4 version signoff 后才能打开 9.3.5 | 维持 roadmap 硬依赖 | 不提前并行修改 9.3.5/9.4.0 production scope |

## Acceptance Criteria

- [x] AC-1: 单一 `verify-v934-release-gate.sh rehearsal` 串联 inventory、unit、integration、five-DB/external、9.3.1–9.3.3 successor regression、coverage collect/check、package/Launcher audit，且每个 owning suite 只执行一次。
- [ ] AC-2: candidate run root 具备 source/environment/inventory/report/DB/coverage/regression/JAR/image manifests、inner/outer hashes、deterministic archive/digest；旧 run 只完成 byte-level 异目录 verify，semantic portable replay 已 fail closed，必须由 repair 后 replacement Step 5 和新 Step 7 重新证明；image `/app/app.jar` SHA 历史复算保持有效但不能替代新 source seal。
- [x] AC-3: skip flags、missing/stale/tampered artifacts、report/skip/migration/hash/source/JAR/image drift、failed pointer publication 和 credential scan 命中等 expected-negative 全部 fail closed。
- [x] AC-4: 原 Step 6 明确记录为 `owner-waived / out-of-scope`；本交付未启用 Actions、未运行或接入 GitHub required gate、未修改 branch protection/required checks，且这些项目不再阻断 Step 7。
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

## Step 7 Minimum Contract

- entry_identity:
  - PR #124 必须先以 merge commit 合入 `main`；merge 的第二父提交必须是合并前复核过的 exact PR head，第一父提交必须是同期复核过的 exact `origin/main`。
  - authority 只能在合并后的 exact clean `origin/main` 上执行；必须使用新的 private full clone，父目录权限 `0700`、`.git` 为 real directory、clean、non-shallow、canonical origin。
  - 合并完成本身只建立 Step 7 entry，不等于 authority、version signoff 或 9.3.4 acceptance。
- preflight_before_activation:
  - 先直接执行 `scan-runtime-source`，精确核对 receipt 的 command/status/git_head/module_count。
  - 复核 frozen manifests、static/focused negatives、runtime base、required ports、candidate/final/authority pointers、process owner、Actions disabled 和无 queued/in-progress run。
  - preflight 是轻量 entry proof；不产生 final/authority pointer，不消耗唯一 authority attempt。
- authority_execution:
  - 仅在 repository owner 再次明确授权后，使用
    `verify-v934-release-gate.sh authority <fresh-run-id>` 启动 exactly one fresh run。
  - runner 必须由新的私有 `0700` tmux control directory 持有；当前会话及其他会话启动后仅作观察者，不抢占 Maven/JVM/Docker/process ownership。
  - 启动环境必须 `env -u` 清除 `MAVEN_ARGS`、`MAVEN_BASEDIR`、
    `MAVEN_CONFIG`、`MAVEN_OPTS`、`MAVEN_SKIP_RC`、`JAVA_TOOL_OPTIONS`、
    `JDK_JAVA_OPTIONS`、`_JAVA_OPTIONS`。
  - attempt budget=`1`；任何 runner 失败均保留原始证据、设置
    `NEEDS_REPLAN` 并停止，不自动重试、不跨 run 拼接。
- authority_exit:
  - 同一 run 必须覆盖 inventory、required S0、Unit/Integration、五库/external、
    successor regression、coverage critical gates、package/Launcher、archive、
    portable verify、sensitive scan 和 candidate JAR=image identity。
  - 只有完整成功 run 才能更新 final/authority pointers；candidate pointer、Step 5
    evidence 或历史绿色均不得替代。
  - semantic portable replay 保持 Step 7 downstream required evidence；
    Unit MySQL 5.7 临时分类必须复验并明确移交 9.3.5。
  - runner 完成后按 implementation self-check -> independent quality ->
    coverage audit -> version signoff 顺序执行；任何门失败即停止。
- boundaries:
  - no-CI 范围保持不变；不得启用 Actions、required checks、branch protection 或 Step 6。
  - 本契约不授权 tag、GitHub Release、publish、9.3.5/9.4.0 production scope 或 public API/SPI 变化。

## Active Execution Authorization

- authorized_stage: 在 activation commit 推送并精确成为 `origin/main` 后，以
  `step7-local-authority-20260723-r1` 启动 exactly one Step 7 authority runner。
- clone_identity:
  - private parent=`/tmp/foggy-v934-step7-authority-20260723.LwJ1DR`，mode=`0700`；
  - clone `.git` 为 real directory、clean、non-shallow、canonical SSH origin；
  - activation parent 和 preflight tested commit 均为 merge commit
    `0c0e7a2921d7a7f10f9a3640b08d015cce5c45db`。
- preflight_gate:
  - `scan-runtime-source`=`passed`，modules=`13`、files=`1411`、
    set SHA-256=`22670362fff8f063791129e1e875768d6b1b44286ec8237591f458b5486b07f8`；
  - Step4/5/6 manifests=`63/8/16`，Bash=`19`、Python=`21`，artifact/package/
    pointer negatives=`105/120/5`；
  - runtime base index/manifest/config 精确等于冻结 digest；
  - ports `13306/13307/15432/11433/17017/16379/19530` 全部 free；
  - target/pointers/runs root 和 future run slug Docker residue 均 absent；
  - 两个历史 tmux pane 均为 `pane_dead=1`，无活动 Maven/JVM/Step4/release child；
  - PR #124=`MERGED`，Actions=`enabled=false`，checks 为空，queued/in-progress=`0/0`。
- activation_gate: 本 docs-only activation commit 必须先推送并满足 clean
  `HEAD == origin/main == ls-remote refs/heads/main`；runner 才能启动。
- process_owner: 新的 private `0700` tmux control directory；启动后当前会话只作
  observer，不向 pane 写入、不抢占 Maven/JVM/Docker/process ownership。
- launch_environment: 必须 `env -u` 清除 `MAVEN_ARGS`、`MAVEN_BASEDIR`、
  `MAVEN_CONFIG`、`MAVEN_OPTS`、`MAVEN_SKIP_RC`、`JAVA_TOOL_OPTIONS`、
  `JDK_JAVA_OPTIONS`、`_JAVA_OPTIONS`。宿主继承的 `MAVEN_OPTS` 当前非空，
  因此禁止省略该清理。
- attempt_budget: exactly one；runner 启动即把本次 attempt 视为 consumed。
- stop_rule: activation identity 不满足则不启动；runner 启动后的任何失败均保留
  immutable evidence、将本 work item 设置为 `NEEDS_REPLAN` 并停止，不自动重试。
- explicitly_not_authorized: GitHub Actions/Step 6、tag、release、publish、
  final version acceptance 或任何 9.3.5/9.4.0 production change。

## Risks and Open Questions

- known_risks:
  - 不接入 required CI/branch protection 后，未来 PR/merge 不具备自动 fail-closed 保障；这是 owner 明确接受的流程风险，不是本地 9.3.4 authority blocker。
  - 完整 authority 成本高；必须先用 contract/negative/focused preflight 收敛，再各执行唯一一次 Step 5 和 Step 7。
  - PR #124 已形成 merge commit；在 Step 7 authority 运行并独立签收前，该 commit
    及其 docs-only activation child 只是 authority input，不是已验收版本。
  - Step 7 runner 已获 exactly-one 授权；tag、release、publish 和最终签收仍未授权。
- open_questions: none

## Step 7 Semantic Replay Replan Hold — 2026-07-23

- historical_run: `step7-local-authority-20260723-r1` at tested commit
  `62bf3fe1456af01179f09e2a9a80c3d229e2435f` completed its repository runner as
  `candidate-passed`; the candidate pointer exists and the final authority
  pointer remains absent.
- downstream_failures:
  - same-filesystem replay failed closed with `E_RUN_CONTEXT` because canonical
    evidence was hardlinked while the verifier requires a regular single-link
    inode;
  - cross-filesystem replay failed closed with `E_CHILD_LIFECYCLE` because the
    release artifact normalized ordinary files to `0644` while canonical
    `child-ready/*.json` evidence requires verifier-private mode `0600`.
- authority_effect: the old Step 7 attempt is consumed and immutable. Its
  runner success, byte-level archive verification and candidate pointer are not
  final authority and must not be promoted, amended, retried in place or
  combined with replacement evidence.
- approved_minimum_replan: canonical evidence and tested classes must be copied
  into independent inodes; semantic replay must restore the complete audited
  set of canonical verifier-required modes without mutating extracted artifact
  bytes or metadata; receipt must record copy counts and the explicit mode
  policy; deterministic positive and negative regressions are mandatory.
- replacement_radius: because governed tool bytes and source seal change, one
  replacement Step 5 rehearsal and one new Step 7 authority are required after
  the repair reaches `READY_FOR_SIGNOFF` and the frozen contract/attempt budgets
  are rechecked. Neither long run may start from this hold section alone.
- unchanged_boundaries: no GitHub CI/Step 6, tag, release or publish; no coverage
  verifier weakening; no production API/SPI change; no final authority pointer
  before semantic replay and the ordered review chain pass.

## Step 7 Semantic Replay Repair Implementation Checkpoint — 2026-07-23

- repair_status: the canonical BUG is `ACCEPTED` by explicit repository-owner
  approval after delivery-signoff audit；this parent feature remains
  `NEEDS_REPLAN` until replacement Step 5 and a new Step 7 authority complete.
- implementation: canonical evidence and tested classes are copied into
  independent inodes only; hardlinks are prohibited. The exact audited
  permission restoration set is limited to
  `child-ready/{unit,integration,step3-required}.json: 0644 -> 0600` in the
  separate semantic target. The extracted artifact is not mutated.
- receipt: named/versioned policy, per-tree and total copies, zero hardlinks and
  the three permission restoration items/counts are recorded and verified.
- focused_evidence: same-filesystem and cross-filesystem positives passed;
  replay mutation negatives=`9/9`, artifact negatives=`105`, coverage XML=`130`,
  coverage contract=`28`, package=`120`, pointer=`5+66`, CI static contract=`86`.
  Step 4/5/6 hash closure and unchanged workflow validation pass.
- scope_audit: `release_artifact_tool.py` and `coverage_xml_tool.py` were audited
  but their normalization and verifier safety semantics were not weakened or
  changed. No production API/SPI, POM behavior or GitHub workflow was modified.
- attempt_ledger: historical Step 7 r1 consumed=`true`; semantic-replay
  replacement Step 5 budget=`1`, consumed=`false`; replacement Step 7
  budget=`1`, consumed=`false`.
- long_run_gate: neither replacement run may start until the repair receives
  independent signoff, its exact bytes are clean/pushed, the frozen contract
  and attempt ledger are rechecked, and a private tmux owner is established.
  The replacement Step 7 additionally requires accepted replacement Step 5 and
  a separately frozen exact-main activation.
- pointer_state: historical candidate pointer exists; final authority pointer
  remains absent and must remain absent through semantic replay and the ordered
  review chain.

## Ultra Execution Contract

- 先读取本文件、`docs/9.3.4/README.md`、implementation/test/acceptance plans 和现有 v934 runner contracts。
- 在 scope 内自主决定具体本地 runner、tool 和测试结构；不得修改或接入 GitHub workflow/Step6/branch settings。
- 如需改变目标、required DB set、coverage minima、兼容、安全、production API 或后续版本边界，设置 `NEEDS_REPLAN` 并停止扩展。
- 运行与改动面匹配的验证，不得声称未实际运行的测试通过；失败 evidence 保留但不得拼接。
- 完成后填写 `Implementation Result`，并将状态改为 `READY_FOR_SIGNOFF`；不得自行设置 `ACCEPTED`。

## Implementation Result

- implementation_summary: the single owner-approved replacement rehearsal
  completed successfully in a clean, non-shallow private full clone. It produced
  an immutable candidate pointer, portable byte-verified archive, exact
  Launcher JAR/runtime-image identity, complete same-run Step4 evidence and
  expected-negative results. The predecessor r1 remains immutable failed
  evidence and was not retried or spliced.
- changed_paths: execution closure changes are documentation/evidence only; no
  production, POM, workflow, runner, manifest, coverage, API or SPI bytes were
  changed.
- tests_and_results:
  - run=`step5-local-rehearsal-20260723-no-ci-fullclone-r2` at
    `6c3ee97abbe49c0cf5cf485d2ddeb4ba7ff7c84f`, runner terminal status=
    `candidate-passed`;
  - Step4=`23 exec / 48 sessions`；required=`774 positive + 59 structural /
    5709 testcase / F0E0S0`；Addon=`2/6/F0E0S0`;
  - Unit=`682+55 / 4943 / F0E0S0`；Integration=`47+4 / 320 /
    F0E0S0`；five-DB=`29/370/F0E0S0`；external=`16/76/F0E0S0`;
  - artifact self-test=`105` negatives, package=`120` negatives and
    pointer=`5` negatives, all passed;
  - Launcher JAR and image `/app/app.jar` SHA-256 both equal
    `14aac41389fd4728232ca99e47ed792e1da57641a2c6ce8dd2a73d3f7889f677`;
    archive SHA-256=
    `36245c874d13f914750a9a1e7e2cb1ef4e95de2bc2c37c8f9ce8cae8ca035ba6`.
- manual_or_experience_evidence: source before/after is exact
  `f0a33a720920b0a07464419fe12b7f28578c6828d92e7cf76a6c564365cbcb49`;
  archive verify/extract-verify, durable seal, sensitive scan and Docker
  cleanup passed; run-owned containers/networks/volumes are absent and required
  ports are free. Candidate pointer is exact; final/authority pointers remain
  absent. Actions remained disabled and no remote CI result was used.
- deviations: none from the approved full-clone replacement contract. The
  environment-only r1 failure was resolved by the approved minimal replan; no
  runner/tool/manifest production semantics changed.
- residual_risks: semantic portable replay remains explicitly downstream;
  required CI/branch protection is intentionally absent by owner decision;
  Step7 exact-clean-main authority, ordered independent reviews and version
  final signoff remain pending and separately authorized.
- readiness: `READY_FOR_SIGNOFF` for Step5 rehearsal only. This is not Step7 or
  9.3.4 final authority.

## Acceptance Status

- acceptance_status: signed-off
- acceptance_decision: accepted
- accepted_scope: replacement Step 5 rehearsal only
- signed_off_by: independent-release-reviewer
- signed_off_at: 2026-07-23
- acceptance_record:
  `docs/9.3.4/acceptance/step5-local-rehearsal-no-ci-fullclone-r2-signoff-20260723.md`
- blocking_items: none
- follow_up_required: yes；Step 7 requires separate authorization and is not
  started by this acceptance.

## References

- requirement / issue: `docs/9.3.4/requirement/P0-test-ci-evidence-chain.md`
- architecture / glossary: `docs/9.3.4/implementation-plan.md`, `docs/9.3.4/contract/test-lane-evidence-contract.md`
- related work items: `docs/9.3.4/workitems/DEBT-unit-mysql57-fixture-classification-migration.md`
- failed execution evidence:
  `docs/9.3.4/evidence/step-5/step5-local-rehearsal-no-ci-r1-git-metadata-preflight-fail-closed-20260723.md`
- replacement preflight evidence:
  `docs/9.3.4/evidence/step-5/step5-local-rehearsal-no-ci-fullclone-r2-preflight-20260723.md`
- replacement execution evidence:
  `docs/9.3.4/evidence/step-5/step5-local-rehearsal-no-ci-fullclone-r2-passed-20260723.md`
