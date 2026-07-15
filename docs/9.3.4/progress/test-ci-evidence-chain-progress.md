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
| 3 | 五数据库与外部集成 required matrix | in-progress | Step 2 exit passed | run-local DB runner/collector candidate；fresh SQLite `5/50/F0/E0/S0`；full 29 DB + 16 required external + DB-state negatives pending |
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

## Execution Check-in — Step 3（in-progress / five-DB foundation）

- started_at: 2026-07-15
- completed_at: not-completed
- owner: current 9.3.4 root session
- predecessor commit: `a0f3a2db83365951a08b65f36765abf2920c6369`
- scope: v934-only OCI digest override、隔离的五库同构 sentinel/确定性 QueryFacade
  parity fixture、MySQL 8/SQL Server identity 与 native oracle 扩展、v933/v934 双模式、
  五库 diagnostic raw XML。
- result: preflight `5/F0/E0/S0` + QueryFacade/native parity `5/F0/E0/S0`。
- evidence:
  `docs/9.3.4/evidence/step-3/step3-five-db-foundation-20260715.md`
- failed diagnostic retained: MySQL 8 unsupported enum `1/F1/E0/S0`；旧持久卷缺 lane-2
  fixture `1/F1/E0/S0`。两次均在根因修复前 fail closed，不进入 passed ledger。
- compatibility: v933-only preflight `3/F0/E0/S0`；完整 batch6 real-query replacement
  `v934-step3-compat-r5-20260715`=`11 tests / 6 reports / F0/E0/S0`，旧 probe 精确保持
  SQLite `8/2`、MySQL57/PostgreSQL15 `25/25`。historical runner 字节不变，由 v934
  wrapper 修复 Step 2 directed replay 与新 Failsafe 默认值的 selector compatibility。
- diagnostic negatives: property conflict、wrong MySQL major、SQLite wrong cache mode 均
  `exit 1；1/F1/E0/S0`。
- non-goals: 本 check-in 不证明其余 19 个 DB executions、16 个 required external
  executions、完整 negative set、run-owned archive、coverage 或 Step 3 exit。
- review finding: Pivot preagg 用例会生成不存在的物理列但未执行 SQL，属于 P0 伪绿；
  PostgreSQL/SQL Server 又缺同构 preagg fixture。该缺口已列为下一批首修。
- next: 修复 Pivot preagg relation/oracle，再清除 Pivot assumption skip 和
  MultiDatabase early-return，建立 fresh-volume exact matrix runner/report collector；
  Step 3 继续 `in-progress`。

### Implementation Quality Self-check — five-DB foundation

- mode: `lightweight-self-check`；正式质量闸门仍在 Step 3 exit 前执行。
- changed paths: 两个 required database owning IT、三个数据库 profile、v934-only compose/
  fixture/profile 管理、v934 compatibility wrapper（historical runner byte-identical）、
  Step 3 docs/workitems。
- review: 初审发现 v933 probe/count 回归、全局 fixture 污染、identity 过度声明与
  historical runner immutability 风险；已通过双模式/专属 fixture/恢复 base
  compose/init、v934 wrapper 与精确 metadata 断言修复。review evidence 见
  `docs/9.3.4/evidence/step-3/step3-five-db-foundation-independent-review-20260715.md`。
- verification: test-compile passed；v934 five-DB=`10/F0/E0/S0`；diagnostic negative
  `3/3` rejected；v933-only preflight=`3/F0/E0/S0`；v933 batch6 real-query=
  `11 tests / 6 reports / F0/E0/S0`；fixture manager apply/clean 均通过，并校验实际
  image/health/version/fixture rows。
- open risk: fresh/run-scoped DB runner、OCI/JAR byte identity、Pivot preagg 真执行、其余
  DB/external executions 未完成；均阻止 Step 3 exit，但不阻止提交本 foundation 批次。
- decision: `needs-formal-quality-gate-at-step3-exit`；当前批次可作为 diagnostic foundation
  合入，禁止提升为 authority/accepted。

## Execution Check-in — Step 3（in-progress / Pivot preagg query diagnostic）

- started_at: 2026-07-15
- completed_at: 2026-07-15
- owner: current 9.3.4 root session
- scope: 修复 Pivot 预聚合 relation 的物理列映射与参数顺序伪绿；把 query matcher、
  main/final-stage rewriter、hybrid watermark 收紧为 fail-closed；补齐隔离的五库同构
  V934 preagg fixture，并在每库真实执行 rewritten SQL、native oracle 和 TopN 包装。
- evidence:
  `docs/9.3.4/evidence/step-3/step3-pivot-preagg-method-diagnostic-20260715.md`、
  `docs/9.3.4/evidence/step-3/step3-pivot-preagg-method-source-sha256.txt`。

实现结果：

- `PreAggregation` 区分命名约定提示与明确物化属性契约；caption/id/time bucket、slice
  RHS、表达式 token 和 semantic watermark 均不能再由维度存在、时间粒度或猜测列名
  反向证明。无法证明时 matcher/rewriter/final-stage/hybrid 全部回退源查询。
- temporal grain 合并覆盖 natural WEEK 与 calendar month/quarter/year 非嵌套边界；
  monthly `year_month` 不再被冒充 `salesDate$month`，仅 monthly 候选时明确拒绝；daily
  month 物化 SQL 在 SQLite 真实执行并返回非空分组。
- HAVING、复杂 main predicate、hybrid+slice、null watermark、legacy returnTotal rollup/
  hybrid、stale/wrong candidate 等未证明路径均 fail-closed；typed/open range、LIKE、
  `$field`/合法 `$expr` 的 final-stage 参数和语义 parity 有正向/负向回归。
- 历史 SQLite/MySQL `FactSalesPreAggModel`、`FactReturnPreAggModel` 的公开物化列和度量
  契约已对齐；V934 模型明确声明 `salesDate$id -> date_key`、`product$id -> product_key`
  与 `product$categoryName -> category_name`。

最终冻结的 diagnostic runs：

- demo reactor install：`target/v934-step3-pivot-demo-install-r3`，`BUILD SUCCESS`；
- preagg unit bundle：`target/v934-step3-preagg-unit-bundle-r12`，
  `57/F0/E0/S0`；
- `PreAggregationIT`：
  `foggy-dataset-model/target/v934-step3-preagg-regression-r12`，`29/F0/E0/S0`；
- query-stage regression：`target/v934-step3-query-stage-r2`，`24/F0/E0/S0`；
- L2 integration：`target/v934-step3-preagg-l2-r5`，`1/F0/E0/S0`；
- Pivot method matrix：SQLite r4、MySQL 5.7 r5、MySQL 8 r3、PostgreSQL 15 r3、
  SQL Server 2022 r3，各 `1/F0/E0/S0`，合计 `5/F0/E0/S0`；
- fixture apply/clean：
  `target/v934-step3-pivot-fixtures-final/{apply-verified,clean-verified}.log`；四外库
  apply 均为 `preagg_rows=4 / preagg_total=100.0000`，clean 均为
  `sentinel_rows=0 / fixture_tables=0`。

Evidence boundary / non-goals：

- 这是 long-lived fixed-container 上的 method-level diagnostic，不是 fresh/run-scoped
  29 DB execution authority，也没有清零 16 个 required external execution；Step 3 仍为
  `in-progress`，不得进入 Step 4。
- 本批物化契约只覆盖 query matcher/rewriter/final-stage/hybrid read path 与 Pivot 定点
  执行。Addon `PreAggSqlBuilder` 的 DDL/refresh 路径仍有独立猜测映射，已登记
  `BUG-step3-preagg-addon-materialization-contract.md`；因此不宣称预聚合生成/刷新全生命周期
  已统一。
- formal implementation quality gate、coverage audit 和 acceptance 均保留到 Step 3 exit/
  Step 7 规定位置；本批仅通过 lightweight self-check 与独立 source/contract review。

Decision：本批 query diagnostic 可合入并作为后续 exact runner 的前置；Step 3 exit 仍
被剩余 required Pivot/MultiDatabase execution、完整 29+16 matrix、fresh storage、exact
collector/negative probes 和 Addon 生命周期 follow-up 阻止。

## Execution Check-in — Step 3（in-progress / required DB S0 diagnostic）

- started_at: 2026-07-15
- completed_at: 2026-07-15
- owner: current 9.3.4 root session
- scope: 清除 Pivot/cascade/MultiDatabase 的 required assumption、empty-fixture、
  early-return 与局部 oracle 伪绿色；在五库重放 frozen database selector。
- evidence:
  `docs/9.3.4/evidence/step-3/step3-db-required-s0-diagnostic-20260715.md`、
  `docs/9.3.4/evidence/step-3/step3-db-required-s0-diagnostic-source-sha256.txt`。

实现结果：

- Pivot/cascade 的支持能力走真实正向执行；MySQL 5.7 不支持能力走 typed/refusal
  assertion；结果采用完整 cardinality、结构化 key set、duplicate 与 value parity。
- MultiDatabase 对分页、offset、aggregate、join、subquery、null、CTE、window、LAG、
  moving average 执行确定性断言；MySQL 5.7 与 SQLite 的 dialect refusal 校验实际异常
  类型/code，不再 skip 或空返回。
- 标准五库各 `5 reports / 50 tests`，合计 `25/250`；MySQL 8 `PivotIT=55`，
  PostgreSQL `PivotIT+2 CTE suites=65`。最终为
  `29 reports / 370 tests / F0/E0/S0`。
- PostgreSQL 首轮 diagnostic 抓到 NULL dimension group 的 baseline oracle 偏差；修复为
  与 SQL `MIN/MAX` 一致地忽略 NULL 选择 baseline，同时 NULL group 仍参加完整 parity。
- 外库 fixture 最终 clean 均为 `sentinel_rows=0 fixture_tables=0`。

Evidence boundary / non-goals：

- raw XML 位于 ignored `target/v934-step3-db-s0-diagnostic-r1`，运行的是 long-lived fixed
  demo container/volume，不是 fresh/run-scoped authority；`inventory_consumption=0`。
- exact 46-execution runner/collector、image/JAR identity、negative probes、16 个 required
  external execution 与 optional LLM disposition 仍未完成。
- Addon DDL/refresh candidate 未进入本批：独立复核发现 COUNT、公式/semantic-scale、
  SQLite timestamp default、整数 date-key watermark 和内置 TM mapping normalization 等
  高风险兼容缺口，workitem 保持 `in-progress`。

Decision：required database false-green cleanup 可独立合入；Step 3 仍为 `in-progress`。
下一批优先建立 fresh/run-owned database runner/collector，同时继续在独立提交中修复并
真实执行 Addon lifecycle；不得以本 diagnostic 进入 Step 4。

## Execution Check-in — Step 3（in-progress / database runner candidate）

- started_at: 2026-07-15
- completed_at: 2026-07-15
- owner: current 9.3.4 root session
- scope: 实现 run-scoped 五库 provision、exact 29-report collector、运行态 cell
  identity/fixture/cleanup 绑定、run-local final bundle 重验、同步日志/敏感扫描与 durable
  signal failure；让 SQLite authority lane 使用真实物理文件而非 shared memory。
- evidence:
  `docs/9.3.4/evidence/step-3/step3-database-matrix-runner-candidate-20260715.md`。

实现与验证结果：

- frozen contract=`def0693d...`；authority files=`66/66`；protected source before=
  `1e6c2ce5...`；exact contract=`5 cells / 7 variants / 29 reports / 370 testcase`。
- report/final-bundle synthetic negatives=`14/14`；它们只证明 XML、manifest、cross-run、
  final exact-tree/cell evidence fail closed，不是完整 DB-state negative set。
- real run `sqlite-collector-candidate-r3` 使用 run-owned physical SQLite file，执行标准
  5 suites=`5 reports / 50 tests / F0/E0/S0`；fixture before/after=`70b1a5d7...`，
  SQLite JDBC JAR before/after=`53174d76...`，terminal `database.sqlite*` residue=`0`。
- full runner `matrix-port-owned-negative-r3` 在 frozen MySQL57 port 被现有长期容器占用时
  于 preflight 退出：process=`1`、durable=`failed/1`、summary absent、positive lane 未执行、
  run-owned Docker resource=`0`、preflight cleanup passed。
- runner signal harness 的 INT/TERM/HUP 分别为 `130/143/129`，均写 failed status、无
  summary/FIFO；stopped tee 在受控超时内置红收口。

Evidence boundary / non-goals：

- 本机没有执行同一 run 的四外库 fresh storage，因此没有 final `29/370` database
  authority；长期容器的 diagnostic `29/370` 不得与 fresh SQLite 拼接。
- final bundle 当前只保证 run-local 自包含；absolute SQLite origin coordinate 与
  nanosecond mtime 使其不能直接搬迁/ZIP 提取后重验。该 portability high 必须在 Step 5
  immutable candidate/archive 前修复。
- unavailable/wrong identity/fixture mutation/provision cleanup 等 DB-state negatives、
  16 个 required external execution、optional LLM disposition 和 Addon lifecycle 仍开放。

Decision：本 runner/collector candidate 可独立合入；Step 3 exit 仍为 `not passed`，不得
进入 Step 4。下一批补 DB-state negatives 并按既定顺序消费 16 个 required external。

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
- MySQL 8/SQL Server 已进入统一 runner 契约，但尚未在冻结端口空闲的 clean host 完成
  同一 run 的 fresh-storage authority replay。
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
| roadmap sync / downstream | Step 3 in-progress recorded | roadmap 保持 Steps 1–2 passed；9.3.5 仍 queued，仅在 version signoff 后标 ready |

## Next Action

继续 Step 3：以 confirmed successor manifest
`4259a452bf4282f85ebb8bfe092127ec3ebec95652e7c009792081f86b84b919` 和
`deferred-step3.tsv` SHA
`89190db9370fe3117d5316c72835efffa577d132d39cef9b5d9ae3558afa7601` 为输入。
SQLite/MySQL57/MySQL8/PostgreSQL15/SQLServer2022 的 preflight 与 QueryFacade parity
foundation 已 diagnostic `10/F0/E0/S0`，数据库 frozen selectors 已进一步达到
`29 reports / 370 tests / F0/E0/S0`，证据见
`docs/9.3.4/evidence/step-3/step3-db-required-s0-diagnostic-20260715.md`。
runner/collector candidate 已实现，真实 fresh SQLite=`5/50/F0/E0/S0`；下一批补齐
unavailable/wrong identity/fixture mutation/provision cleanup 等 DB-state negatives，并按
Redis→Mongo/DataViewer→MCP/MySQL57→Vector 顺序消费 16 个 required external
execution。在冻结端口空闲的 clean host 上再以同一 commit 重放四外库 fresh
`29/370`；1 个 external LLM 保持 optional reviewed disposition。Addon lifecycle 继续
按独立 workitem 修复 COUNT/formula/SQLite/watermark/TM normalization 后执行 SQLite +
外库真实 create/full/incremental/query parity。Step 4 coverage 不得提前混入本阶段
correctness evidence。
