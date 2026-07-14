---
doc_role: execution_progress
doc_purpose: Track Step 1-7 implementation, evidence and downstream readiness for 9.3.4.
version: 9.3.4
status: ready
acceptance_status: not-started
created_at: 2026-07-14
updated_at: 2026-07-14
---

# 9.3.4 测试与 CI 证据链进度

## 文档作用

- doc_type: progress
- intended_for: project-root execution / build owner / CI owner / reviewers
- purpose: 记录每个 Step 的真实实现、验证、负向证据、偏差和 exit decision；这是
  9.3.4 唯一进度 authority。

## 基本信息

- requirement: `docs/9.3.4/requirement/P0-test-ci-evidence-chain.md`
- contract: `docs/9.3.4/contract/test-lane-evidence-contract.md`
- implementation plan: `docs/9.3.4/implementation-plan.md`
- test plan: `docs/9.3.4/test/test-ci-evidence-chain-test-plan.md`
- predecessor signoff: `docs/9.3.3/acceptance/version-signoff.md`
- execution mode: single-root-delivery / strict Step 1→7
- implementation owner: unassigned current 9.3.4 root session
- started_at: not-started
- completed_at: not-completed
- experience: N/A（build/test/CI 治理，无 UI 交付）

## Entry Conditions

| 条件 | 状态 | 证据/备注 |
|---|---|---|
| 9.3.3 signed-off / accepted-with-risks | verified | replacement run `20260714T084351Z-3271604`；`3824/519/F0/E0/S3` |
| 9.3.4-A minimum gate | verified-as-predecessor | `docs/9.3.3/preconditions/9.3.4-A-minimum-test-gate.md`；不是 9.3.4 full evidence |
| 9.3.4 requirement/plan 已就绪 | verified | 本目录 planning package |
| test/evidence contract | proposed | 必须在 Step 1 review 后 confirmed |
| dirty worktree baseline | pending | Step 1 执行前记录；不得 destructive cleanup |
| clean commit authority | pending | 只在 Step 7 final replay 要求，不把当前 dirty diagnostic 冒充 release authority |

## Planning Inventory Snapshot

2026-07-14 workspace 只读互斥静态候选：unit-pattern（排除双义
`*IntegrationTest`）=`492`、`*IntegrationTest=33`、`*IT=7`、prefix-only
`IT*=0`、`*E2E=0`，共 `532` source files；两个
`addons/foggy-benchmark-spider2` WIP source 不在 active root reactor，reactor
diagnostic=`490+33+7=530`。另有 `65` 个 source 含 `@Nested`，因此 source count
绝不作为 report/execution count。上述都只用于估算；Step 1 必须重新生成并 review
`source-inventory.tsv` + `execution-inventory.tsv` 后记录 hashes。

## Step Progress

| Step | 内容 | 状态 | Entry | Exit/evidence |
|---:|---|---|---|---|
| 1 | 契约与静态库存冻结 | ready | predecessor verified | pending：source/execution manifests、migration cardinality/hash、orphan/overlap/unmapped=0 |
| 2 | Surefire/Failsafe 全量分层 | pending | Step 1 exit | pending：ambiguous=0、unit/hermetic IT pass、external exact-deferred |
| 3 | 五数据库与外部集成 required matrix | pending | Step 2 exit | pending：五库 + deferred DB/Redis suites S0、identity/fixture evidence |
| 4 | JaCoCo unit+IT 聚合与关键类门 | pending | Step 3 exit | pending：all-lane agent rerun、XML verifier、model merged-exec check、negative proof |
| 5 | authority runner rehearsal / immutable candidate | pending | Step 4 exit | pending：candidate root、two-layer/archive/JAR=image digest；no final pointer |
| 6 | PR/main/release CI 接线 | pending | Step 5 exit | pending：five artifacts exact、state-negative、GitHub JAR=image dry-run |
| 7 | clean-commit 权威回放与后置门 | pending | Step 6 exit | pending：full authority + quality→coverage→acceptance signed-off |

任一步未记录 exit=`passed`，后一步不得改为 in-progress。

## Execution Check-in Template

每个 Step 完成或发生关键失败时追加一节，至少记录：

- started_at / completed_at / owner；
- exact scope 和 non-goals；
- touched paths 与 protected user-owned changes；
- implementation summary 和 contract decisions；
- exact commands、run id、commit/source/worktree marker；
- tests/reports/failures/errors/skips 和 expected-negative 结果；
- DB/coverage/JAR/image/archive/CI identity（适用时）；
- failed/diagnostic/superseded attempts 及排除理由；
- deviations、open risks、blockers 和下一步 entry decision；
- self-check decision，以及是否允许进入 formal quality gate。

## Current Risks / Stop Conditions

- 33 个 `*IntegrationTest` 尚未逐项分类，不能根据文件名机械批量改名。
- v933 Batch 7 冻结旧 FQCN/count，重命名后不能原样重跑；Step 1 必须先冻结
  predecessor migration，Step 5/7 只能运行 v934 successor regression。
- MySQL 8 未进入现有统一 required runner；SQL Server 现有执行链不完整。
- 当前只有 model 局部门，无 reviewed reactor aggregate baseline；0.80/0.70 只是
  critical-class candidate floor；aggregate XML verifier 尚未实现。
- remote required check、five-cell collector、branch protection、release artifact reuse
  和 Docker embedded-JAR equality 尚无实际证据。
- 当前工作树不是 clean commit；可做 Step 1–6 实现与 diagnostic，不能作为 Step 7
  final authority。

上述是已知执行风险，不是已通过项。触发 implementation plan 的 stop condition 时，
当前 Step 保持未通过并记录 blocker，不继续后续 Step。

## Planning Reviews

- consistency review：`READY`；9.3.3 signoff/replacement/superseded 状态、9.3.4
  1→7/gate order、relative links 均一致；初始两项 low（模块级 CLAUDE、SQLite lane
  overlap）已回写 contract/prompt。
- technical feasibility review：首轮发现 historical runner migration、Step 2–4
  dependency、aggregate empty check、Docker rebuild 等 4 high / 3 medium；二轮发现
  source/report inventory cardinality high；均已改为 predecessor group mapping、
  source/execution 两层 manifests、XML verifier、candidate/final split、five-cell
  collector 和 runtime-only image。
- final review：`READY`，blocker/high/medium=`0/0/0`；最后一项 low（1:N/N:1
  mapping 的 stop-condition 措辞）已修正，无未处理 planning finding。

## Post-Gate Status

| Gate | 状态 | 证据 |
|---|---|---|
| implementation self-check | not-started | Step 7 前不得预填绿色 |
| formal implementation quality | not-started | planned `docs/9.3.4/quality/` |
| coverage evidence audit | not-started | planned `docs/9.3.4/coverage/` |
| version acceptance | not-started | planned `docs/9.3.4/acceptance/version-signoff.md` |
| roadmap sync / 9.3.5 ready | not-started | 仅在 version signoff 后执行 |

## Next Action

执行 Step 1：生成可复现的 workspace source inventory 与 reactor execution
inventory；分类当前 workspace 532/reactor 530 个诊断候选，展开 65 个 `@Nested`
source 的 report keys，
冻结 predecessor migration、external execution step、五库/coverage XML verifier/
Docker same-JAR/evidence contract，并先证明 orphan/overlap/unmapped/missing/zero/
stale 负向探针会 fail closed。Step 1 exit 前不进入 POM/rename 改造。
