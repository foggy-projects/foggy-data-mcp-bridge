---
doc_role: execution_progress
doc_purpose: Track Step 1-7 implementation, evidence and downstream readiness for 9.3.4.
version: 9.3.4
status: in-progress
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
- implementation owner: current 9.3.4 root session
- started_at: 2026-07-14
- completed_at: not-completed
- experience: N/A（build/test/CI 治理，无 UI 交付）

## Entry Conditions

| 条件 | 状态 | 证据/备注 |
|---|---|---|
| 9.3.3 signed-off / accepted-with-risks | verified | replacement run `20260714T084351Z-3271604`；`3824/519/F0/E0/S3` |
| 9.3.4-A minimum gate | verified-as-predecessor | `docs/9.3.3/preconditions/9.3.4-A-minimum-test-gate.md`；不是 9.3.4 full evidence |
| 9.3.4 requirement/plan 已就绪 | verified | 本目录 planning package |
| test/evidence contract | confirmed | r8 双路独立复核 PASS；freeze/manifest/summary 已确认 |
| dirty worktree baseline | verified | baseline commit `a377937e`；3025 protected files hash 前后均为 `2c73b895...e8e9e2` |
| clean commit authority | pending | 只在 Step 7 final replay 要求，不把当前 dirty diagnostic 冒充 release authority |

## Step 1 Frozen Inventory

confirmed run=`step1-candidate-r8-20260714`：workspace sources=`532`、active-reactor
sources=`530`、discovery rows=`820`（804 reports + 16 reviewed none）、execution
keys=`829`（required Step 2=`785`、required Step 3=`43`、optional Step 3=`1`）。
predecessor nodes/edges=`519/519`；ordered classpath=`2395`，其中 110 个 active
reactor dependency 原位使用 current `target/classes`，stale reactor m2=`0`。

33 个真实 `*IntegrationTest` 已逐 execution key 分类，但尚未改名；冻结 rename
plan=`33 sources / 62 reports / 74 keys / 50 predecessor edges`。Step 1 source count、
discovery container 与未来 actual testcase count 是三个不同口径。

## Step Progress

| Step | 内容 | 状态 | Entry | Exit/evidence |
|---:|---|---|---|---|
| 1 | 契约与静态库存冻结 | passed | predecessor verified | r8 confirmed；532/820/829/519；28/28 negatives；dual review PASS |
| 2 | Surefire/Failsafe 全量分层 | ready | Step 1 exit passed | pending：ambiguous=0、unit/hermetic IT pass、external exact-deferred |
| 3 | 五数据库与外部集成 required matrix | pending | Step 2 exit | pending：五库 + deferred DB/Redis suites S0、identity/fixture evidence |
| 4 | JaCoCo unit+IT 聚合与关键类门 | pending | Step 3 exit | pending：all-lane agent rerun、XML verifier、model merged-exec check、negative proof |
| 5 | authority runner rehearsal / immutable candidate | pending | Step 4 exit | pending：candidate root、two-layer/archive/JAR=image digest；no final pointer |
| 6 | PR/main/release CI 接线 | pending | Step 5 exit | pending：five artifacts exact、state-negative、GitHub JAR=image dry-run |
| 7 | clean-commit 权威回放与后置门 | pending | Step 6 exit | pending：full authority + quality→coverage→acceptance signed-off |

任一步未记录 exit=`passed`，后一步不得改为 in-progress。

## Execution Check-in — Step 1（passed）

- started_at: 2026-07-14
- completed_at: 2026-07-14
- owner: current 9.3.4 root session
- baseline commit: `a377937e8e6a6c03afce655396d7363f7db1d7d4`
- confirmed run: `step1-candidate-r8-20260714`
- evidence:
  `docs/9.3.4/evidence/step-1/inventory-contract-freeze-20260714.md`
- scope: discovery-only inventory generator、source/discovery/classpath/execution/rename/
  predecessor/DB/package/Maven/coverage-policy manifests、fail-closed validator、28 个
  expected-negative probes、双路独立复核与原子 confirmation。
- non-goals: 不修改 POM/test source/workflow/production source；不执行测试方法、
  数据库、Redis、coverage、package、Docker 或 remote CI。
- touched paths: `scripts/verify-v934-test-inventory.sh`、`scripts/v934/**`、
  `docs/9.3.4/**` 与 9.3.1→9.4.0 authoritative roadmap status。
- protected state: 3025 files；before=after=
  `2c73b8951dbeda43bde2b0b2aa0ef63cb0fb66e049a5c5bf7d8351e2eae8e9e2`。

Exact commands：

```bash
V934_SUPERSEDES=step1-candidate-r3-20260714 \
  scripts/verify-v934-test-inventory.sh step1-candidate-r8-20260714
python3 scripts/v934/inventory_tool.py confirm --root . --directory scripts/v934 \
  --reviewer dual-independent-review:precommit_scope_audit+v934_step1_contract \
  --evidence docs/9.3.4/evidence/step-1/inventory-contract-freeze-20260714.md \
  --summary target/v934-step1-inventory/runs/step1-candidate-r8-20260714/summary.env
python3 scripts/v934/inventory_tool.py validate --root . --directory scripts/v934
python3 scripts/v934/inventory_tool.py validate-summary --root . \
  --directory scripts/v934 \
  --summary target/v934-step1-inventory/runs/step1-candidate-r8-20260714/summary.env
(cd scripts/v934 && sha256sum -c SHA256SUMS)
```

Result：

- compile/discovery: 21 owning modules；820 discovery rows、804 report owners、16
  reviewed none rows、5251 discovery nodes；JUnit helper bytecode 只调用
  `Launcher.discover`，不调用 `execute`。
- execution contract: 829 keys；Step 2 required=785、Step 3 required=43、Step 3
  optional=1；实际 tests executed=`0`，external fixtures=`0`。
- classpath: 2395 ordered entries；110 current reactor class-tree replacements；stale
  active-reactor m2=`0`；live file/tree SHA 全部复算一致。
- migration: predecessor raw XML/nodes/edges=`519/519/519`；rename plan=
  `33/62/74/50`，SHA-256=`acba7a9dc22c9c0fdbaa0438fe91018b61eee8a457a36f1918995f39aa74cfe2`。
- fail-closed: expected-negative=`28/28 passed`；无 unexpected failure/error/skip。
- independent review: `precommit_scope_audit=PASS`、`v934_step1_contract=PASS`，
  blocker/non-blocking finding=`0/0`。
- confirmed digests: freeze=
  `ff418e04f6a938a853ce7bbd0700223627f42520705530e819a53e5591e82876`；manifest=
  `e601c6c70ff02e9e50b86fd2b14b14aba9cfede096b42c93ff6e5968a918640f`；summary=
  `579e9430bea6f873e7c4465cd1a6e45c49d348d84a89d5d648d25e3a5a4bbc50`。

Excluded attempts：首个 candidate 因 run-root marker bootstrap 失败；r2 因未审阅
zero-report 分类停止；r3 的旧 runner 漏掉每模块 classpath 最后一项且 summary scope
不一致；r4–r7 分别在 scope/hash、stale reactor m2、confirmation provenance、跨 Step
rename chain 加固过程中中断或 partial/no-summary。它们全部是 diagnostic/superseded，
不得与 r8 拼接。

Self-check：Step 1 lightweight implementation self-check=`passed`；机器 manifest、
protected source、discovery/classpath/predecessor 和 contract schema 均由两名独立
reviewer 复算。formal implementation quality、coverage audit 与 version acceptance
仍按 Step 7 gate order 执行，本次不提前创建绿色记录。Step 2 entry=`ready`。

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

- 33 个 `*IntegrationTest` 已在 rename plan 逐项分类，但尚未实施；Step 2 只能按
  33/62/74/50 frozen delta 改名并生成独立 successor，任何计划外 delta 都停止。
- v933 Batch 7 冻结旧 FQCN/count，重命名后不能原样重跑；519-node predecessor
  migration 已冻结，Step 5/7 只能运行 v934 successor regression。
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
| implementation self-check | Step 1 lightweight passed | 双路独立复算；全版本 final self-check 仍在 Step 7 |
| formal implementation quality | not-started | planned `docs/9.3.4/quality/` |
| coverage evidence audit | not-started | planned `docs/9.3.4/coverage/` |
| version acceptance | not-started | planned `docs/9.3.4/acceptance/version-signoff.md` |
| roadmap sync / 9.3.5 ready | not-started | 仅在 version signoff 后执行 |

## Next Action

执行 Step 2：以 confirmed Step 1 manifest SHA
`e601c6c70ff02e9e50b86fd2b14b14aba9cfede096b42c93ff6e5968a918640f` 与 rename-plan
SHA `acba7a9dc22c9c0fdbaa0438fe91018b61eee8a457a36f1918995f39aa74cfe2`
为双 parent，先按计划完成 33 个受控 rename 和 Surefire/Failsafe POM 分层，再生成
独立 `scripts/v934/successor/step2/` candidate 并复核确认。只有 successor confirmed
后才实跑 all-unit/hermetic IT，外部 required suites 只允许 exact defer 到 Step 3。
