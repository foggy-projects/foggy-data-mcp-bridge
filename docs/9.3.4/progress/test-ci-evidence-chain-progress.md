---
doc_role: execution_progress
doc_purpose: Track Step 1-7 implementation, evidence and downstream readiness for 9.3.4.
version: 9.3.4
status: in-progress
acceptance_status: not-started
created_at: 2026-07-14
updated_at: 2026-07-15
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
| 2 | Surefire/Failsafe 全量分层 | passed | Step 1 exit passed | r8e confirmed；724 positive + 59 structural；5,205 testcase；F0/E0/S0；signal-safe authority |
| 3 | 五数据库与外部集成 required matrix | ready | Step 2 exit passed | exact deferred=46；五库 + DB/Redis/Mongo suites S0、identity/fixture evidence pending |
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
reviewer 复算。当时不为 Step 1 单独创建绿色后置门；version-scope formal quality、
coverage audit 与 acceptance 仍按 Step 7 gate order 执行。Step 2 entry=`ready`。

## Execution Check-in — Step 2（passed）

- started_at: 2026-07-14
- completed_at: 2026-07-15
- owner: current 9.3.4 root session
- implementation: root Surefire/Failsafe `3.5.3` split、独立 skip properties、取消
  `multi-db` active-by-default、33 个 frozen rename + 2 个 Mongo corrective rename、
  current scripts/workflow/guides runner 迁移、typed structural report verifier、
  shared authority lock/CAS 与 signal-safe durable status。
- production/test regression fixes: Mongo loader auto-configuration ordering、
  MultiThreadExecutor completion、immutable calculated-field list、namespace fixture、
  zero-skip snapshot/embedding、nested IT leakage、Failsafe selector ownership。
- contract result: Step 1 的 829/785 baseline 保持 immutable；current successor 为
  `770 positive = 724 Step 2 + 46 Step 3 deferred`、`59 structural`；predecessor refs=
  `480 positive + 39 structural`，总 edges=`519`。
- confirmed successor: `step2-candidate-r8e-20260715`；freeze=
  `44b11ed756bf41e3b271ac57b59c2c882a0b31a56963f42ae154fdb5d37b2fb6`；manifest=
  `4259a452bf4282f85ebb8bfe092127ec3ebec95652e7c009792081f86b84b919`；summary=
  `f6b80aa5f48c6f32aaa99336823dd00d183d75a096767c74f7de2c21c1ac4b75`；双路独立复核
  PASS，successor negatives=`33/33`。
- Unit authority: `step2-unit-r8e-20260715`；`677 positive + 55 structural = 732 raw`；
  `4,890 testcase / F0/E0/S0`；summary=
  `55e9e8b67301aa24a743dfa56fd1f2c01ca9bd94c3889f11093c281d6ef2565a`；status=
  `d66eb17f3010a32cd82b85471540db146d72ddf9a4833f9523575a2fcf8b3a11`。
- Integration authority: `step2-it-r8e-20260715`；六个 variant，
  `47 positive + 4 structural = 51 raw`；`315 testcase / F0/E0/S0`；summary=
  `0ee6c45907a9b37b4dda726bb7ba38030a57503a40e52995f26d397ba0610e83`；status=
  `4942b0f99d6c3d049e72ebba97ad30bb1c4951e9212dad4be54d310a2bf42b68`。
- combined result: `724 positive + 59 structural = 783 raw`；`5,205 testcase`；
  F/E/S=`0/0/0`；runner report negatives 各 `20/20`；source before/after/current=
  `12749d1fb9d37af04b8a3dd80ac49ea0fcc177309edcc0c49645a2c2c19a1a53`。
- signal contract: INT/TERM/HUP 的 process/durable exit 分别为 `130/143/129`，均写
  failed status、删除 summary；success path 先屏蔽信号再撤销 EXIT trap。

Exact commands：

```bash
scripts/verify-v934-step2-successor.sh step2-candidate-r8e-20260715
python3 scripts/v934/step2_successor_tool.py confirm --root . \
  --directory scripts/v934/successor/step2 \
  --reviewer dual-independent-review:v934_r8e_identity_review+v934_snapshot_skip \
  --evidence docs/9.3.4/evidence/step-2/step2-successor-r8e-independent-review-20260715.md \
  --summary target/v934-step2-successor/runs/step2-candidate-r8e-20260715/summary.env
scripts/verify-v934-unit.sh step2-unit-r8e-20260715
scripts/verify-v934-integration.sh step2-it-r8e-20260715
```

Evidence：

- `docs/9.3.4/evidence/step-2/step2-structural-container-contract-amendment-20260714.md`；
- `docs/9.3.4/evidence/step-2/step2-successor-r8e-independent-review-20260715.md`；
- `docs/9.3.4/evidence/step-2/step2-runner-split-exit-r8e-20260715.md`。

Excluded evidence：r1–r8c 的 interrupted/failed/diagnostic candidates、superseded r2–r7
generations、以及 `step2-candidate-r8d-20260715`、`step2-unit-r8-20260715`、
`step2-it-r8-20260715` 均不得
拼接。r8d 虽有机械绿色结果，但 formal quality review 的 completed-window signal
probe 证明它可留下 `process=143` 与 durable `passed/0` 分裂，已由 signal bug workitem
正式作废。

Decision：Step 2=`passed`；Step 3 entry=`ready`。本记录只证明 runner split 和
hermetic/SQLite correctness；五库/external、coverage、CI/release 与 version acceptance
仍未完成，9.3.5 保持 `queued`。

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

- 33 个 frozen rename + 2 个 Mongo corrective rename 已由 r8e successor exact
  确认；当前 35 source / 64 planned reports / 76 planned keys 被分类为 72 positive
  key rename + 4 structural rename，计划外 source/semantic delta=`0`。
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
| implementation self-check | Steps 1–2 passed | r8e 双路 successor review + raw Unit/IT independent recomputation；version final self-check 仍在 Step 7 |
| formal implementation quality | Step 2 ready-with-risks | `docs/9.3.4/quality/step2-runner-split-implementation-quality.md`；open blocker/high/medium=0 |
| coverage evidence audit | Step 2 ready-for-acceptance | `docs/9.3.4/coverage/step2-runner-split-coverage-audit.md`；15/15 BUG covered；critical/major gap=0 |
| version acceptance | not-started | planned `docs/9.3.4/acceptance/version-signoff.md` |
| roadmap sync / downstream | Step 3 ready recorded | roadmap 已同步 Steps 1–2 passed；9.3.5 仍 queued，仅在 version signoff 后标 ready |

## Next Action

开始 Step 3：以 confirmed successor manifest
`4259a452bf4282f85ebb8bfe092127ec3ebec95652e7c009792081f86b84b919` 和
`deferred-step3.tsv` SHA
`89190db9370fe3117d5316c72835efffa577d132d39cef9b5d9ae3558afa7601` 为输入，先冻结
SQLite/MySQL57/MySQL8/PostgreSQL15/SQLServer2022 与 Redis/Mongo 的 exact owner、
preflight、fixture 和 identity，再实现 required matrix runner。46 个 deferred execution
必须全部 actual pass 且 S0；Step 4 coverage 不得提前混入本阶段 correctness evidence。
