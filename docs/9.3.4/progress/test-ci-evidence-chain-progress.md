---
doc_role: execution_progress
doc_purpose: Track Step 1-7 implementation, evidence and downstream readiness for 9.3.4.
version: 9.3.4
status: in-progress
acceptance_status: not-started
created_at: 2026-07-14
updated_at: 2026-07-20
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
| 3 | 五数据库与外部集成 required matrix | passed | Step 2 exit passed | r4 same-commit authority：DB `29/370` + external `16/76` = exact `45/446/F0E0S0`；DB state `18/18`、Redis state `4/4`、Addon companion `2/6` |
| 4 | JaCoCo unit+IT 聚合与关键类门 | in-progress / r32 non-freezable | Step 3 exit passed | r32 completed all lanes after the port precondition was restored, but aggregate branch/complexity are each one below high-water. Next=test-only Cdiag→fresh r33→new candidate/Git-safe closure/review→Cfreeze→fresh formal→post gates |
| 5 | authority runner rehearsal / immutable candidate | hold / execution closed | replacement Step 4 exit | r9 exclusion/recovery reset do not enter Step 5; wait for fresh formal successor + final quality + coverage audit/feature acceptance |
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

## Execution Check-in — Step 3（in-progress / committed Redis external subset）

- started_at: 2026-07-15
- completed_at: 2026-07-15
- owner: current 9.3.4 root session
- commit: `35ddf73f359a444faa1db4b03dcc9f3ef7274aa2`
- evidence:
  `docs/9.3.4/evidence/step-3/step3-external-redis-runner-candidate-20260715.md`。

实现与验证结果：

- external contract 冻结 `7 variants / 16 reports / 76 testcase`，optional LLM=`1/1`；
  runner/tool/helper/selectors/source amendment 均由 contract hash 绑定。
- committed run `external-redis-candidate-35ddf73f-r2` 使用 digest-pinned Redis `7.4.6`、
  动态 loopback、单一 run-labelled named volume，得到 exact `2 reports / 3 testcase /
  F0/E0/S0`。
- candidate manifest 绑定 35 个文件并重验通过；source before/after 与两个 variant bytecode
  seal 一致；12/12 report/cross-run/flaky negatives、6/6 sensitive detection probes 通过。
- real signal group `external-redis-signal-35ddf73f-r1` 的 INT/TERM/HUP 分别为
  `130/143/129`；durable status 均 failed，summary/candidate/FIFO absent，container/volume
  residue=`0/0`。
- committed `r1` 被外层工具中断，正确写 `failed/1`、无 summary/candidate、residue=`0/0`，
  已排除；所有旧 contract/tool diagnostic 均不得拼接。

Evidence boundary / non-goals：

- final manifest 明确 `complete=false`；本批只关闭 external Redis `2/3`，remaining external
  为 `14/73`，不得宣称 external `16/76` 或 Step 3 `45/446`。
- wrong-image/version、forced dirty-state/cleanup-failure resource negatives 仍开放；candidate
  仍按 mtime 为 run-local，archive portability 留到 Step 5。
- direct-tool fail-closed 已暴露 `META-001` 默认混合 bundle 装配缺陷；MySQL57 required
  仍 pending，不得把 `22/23` 作为绿色。

Decision：Redis subset candidate=`passed`，Step 3=`in-progress / not passed`。下一 external
variant 按顺序进入 Mongo/DataViewer `4/30`；同时保留 DB-state 与 Redis resource-state
negative backlog，不进入 Step 4。

## Execution Check-in — Step 3（in-progress / committed Mongo external subset）

- started_at: 2026-07-15
- completed_at: 2026-07-15
- owner: current 9.3.4 root session
- commit: `ccb29f476e0a7d6040f52e4192fe54b68aac5aa0`
- evidence:
  `docs/9.3.4/evidence/step-3/step3-external-mongo-runner-candidate-20260715.md`。

实现与验证结果：

- committed run `external-mongo-candidate-ccb29f47-r1` 使用 digest-pinned MongoDB `6.0.27`、
  动态 loopback、两个 run-labelled named volumes 与两个 hash-derived database，得到 exact
  `4 reports / 30 testcase / F0/E0/S0`。
- DataViewer source amendment 增加 `@AfterEach` fixture teardown；post-test viewer
  `list_presets=0`，model collections/count 与 foreign database 也精确匹配。
- candidate manifest 绑定 31 个文件并重验通过；source before/after、Mongo variant bytecode
  seal、12/12 report negatives、6/6 short-sensitive probes 与全目录 sensitive scan 通过。
- 本地 disposable-copy 复验中，touched status、missing cleanup、extra file 三类 candidate
  副本均以 `E_CANDIDATE` 拒绝；副本已删除，不把它们记为 durable candidate artifact。
- real signal group `external-mongo-signal-ccb29f47-r1` 的 INT/TERM/HUP 分别为
  `130/143/129`；durable status 均 failed，summary/candidate/FIFO absent，container/two-volume
  residue=`0/0`。
- Mongo loader 仍隐式读取 JDBC dialect；本 lane 只用 run-local SQLite + `dual` guard 解锁
  Step 3，不把它描述为产品级解耦修复。

Evidence boundary / non-goals：

- Mongo final manifest 为 `complete=false`。Redis 与 Mongo 两个独立 candidates 合计只关闭
  external `6/33`，remaining external=`10/43`；二者不能跨 run 拼成 external `16/76`。
- wrong-image/version、dirty-state、forced cleanup-failure resource negatives，数据库四外库
  `24/320`、MCP/MySQL57、Vector、optional LLM 与 Addon lifecycle 仍开放。
- `external-mongo-candidate-142f4360-r2`、`ef0dae38-r1`、`5b95f6f9-r1` 分别暴露 fixture
  残留、敏感探针自匹配与 verifier 标签漂移；均 fail closed 且禁止拼接。

Decision：Mongo subset candidate=`passed`，Step 3=`in-progress / not passed`。下一 external
variant 按顺序进入 MCP/MySQL57 `8/23`，随后 Vector `2/20`；不进入 Step 4。

### Implementation Quality Self-check — Mongo external subset

- mode: `lightweight-self-check`；formal Step 3 quality gate 仍在完整 `45/446` exit 前执行。
- implementation closure: runner/tool/contract、SQLite test dependency、DataViewer teardown 与
  两个 test-governance BUG 已收口；protected source before/after exact，未改生产 Mongo 查询。
- verification: test-compile、Bash/JSON/Python static checks、contract validator、candidate
  verifier、4/30 fresh execution、candidate local tamper self-check、signals 和 Docker residue
  均通过；独立 candidate/docs review blocker=`0`。
- open risks: production Mongo loader JDBC dialect coupling、resource-state negatives、run-local
  mtime portability 与 remaining Step 3 lanes 均继续显式开放。
- decision: `passed-subset / needs-formal-quality-gate-at-step3-exit`，不允许提升为 full
  authority、coverage entry 或 version acceptance。

## Execution Check-in — Step 3（in-progress / committed MySQL57 external subset）

- started_at: 2026-07-15
- completed_at: 2026-07-15
- owner: current 9.3.4 root session
- implementation commits: `664c8f21a82426206b352fbd2a9e2a09ec25df43`、
  `97f1cbfa7f412fabc6f0ef96d9182c9340a51fbd`
- evidence:
  `docs/9.3.4/evidence/step-3/step3-external-mysql-runner-candidate-20260715.md`。

实现与验证结果：

- committed run `external-mysql-candidate-97f1cbfa-r2` 使用 digest-pinned MySQL `5.7.44-log`、
  动态 loopback、单一 run-labelled named volume、独立 ephemeral root/app 密钥和精确
  schema-level SELECT-only grant，得到 exact `3 variants / 8 reports / 23 testcase /
  F0/E0/S0`。
- deterministic fixture 使用固定时区/epoch/RAND seed 与单 session commit；before/after
  `69 tables / 69 PK`、完整数据 hash `c8edcd27...` 和 grant hash `ae360368...` 均一致，
  terminal container/volume residue=`0/0`。
- run-owned curated bundle 精确 `59 files / 32 QM / 25 TM / 2 fsscript`，排除两个
  `demo/**` 权限模型；MCP 节点强制公开 catalog 精确 32 QM。direct structured report=
  `23/23`，无 business error/warning；optional LLM nested class 未进入 required selector。
- candidate manifest 绑定 122 个文件并重验通过；source before/after、三个 variant bytecode
  seal、12/12 report negatives、6/6 sensitive probes 与两条 ephemeral secret exact scan
  均通过。
- real signal group `external-mysql-signal-97f1cbfa-r2` 的 INT/TERM/HUP 分别为
  `130/143/129`；durable status 均 failed，summary/candidate/FIFO absent，container/volume
  residue=`0/0`。
- first formal attempt `external-mysql-candidate-664c8f21-r1` 因 MySQL 5.7 不提供
  `information_schema.ROUTINE_PRIVILEGES` 在 grant gate fail closed；`97f1cbfa` 改用兼容的
  `mysql.procs_priv` 精确验证。r1 无 candidate 且 residue=`0/0`，禁止拼接。

Evidence boundary / non-goals：

- MySQL final manifest 为 `complete=false`。Redis + Mongo + MySQL 三个独立 candidates 仅构成
  external progress ledger=`14/56`，不能跨 run 拼成 external `16/76`；remaining external
  为 Vector `2/20`。
- wrong-image/version、dirty-state、forced cleanup-failure resource negatives，数据库四外库
  `24/320`、optional LLM disposition、Addon lifecycle 和 archive portability 仍开放。
- 本批只关闭两个 MySQL test-governance BUG 的 Step 3 scope；production launcher/provider
  follow-up 保持开放，不以 curated test bundle 冒充生产模块化修复。

Decision：MySQL subset candidate=`passed`，Step 3=`in-progress / not passed`。下一 external
variant 按顺序进入 Vector `2/20`；不进入 Step 4。

### Implementation Quality Self-check — MySQL57 external subset

- mode: `lightweight-self-check`；formal Step 3 quality gate 仍在完整 `45/446` exit 前执行。
- implementation closure: runner/tool/contract、确定性 fixture、least-privilege grant、curated
  catalog、MCP/Compose/direct fail-closed 与信号清理已收口；未修改生产 catalog 原子刷新语义。
- verification: Bash/JSON/Python/Maven static checks、contract validator、source/bytecode seal、
  candidate verifier、8/23 fresh execution、direct 23/23、negative/sensitive probes、signals 与
  Docker residue 均通过；独立只读审计 finding=`0`。
- protected changes: 用户已有 PreAgg POM/source/test 与新 contract 文件未触碰、未暂存。
- decision: `passed-subset / needs-formal-quality-gate-at-step3-exit`，不允许提升为 full
  authority、coverage entry 或 version acceptance。

## Execution Check-in — Step 3（in-progress / committed Vector external subset）

- started_at: 2026-07-15
- completed_at: 2026-07-15
- owner: current 9.3.4 root session
- implementation commits: `9542605864ab3de114158889a45efb23c45d3734`、
  `4e39149dd6c437d0349d77f0365a32c7c7a9964b`、
  `a6c1dd867076b8cac6b2ac159fd49ac9b6123f89`、
  `4874cc276b3004dbf5d25190db6cfa40fc17ac66`、
  `35f0e78b65573930c8f061cb0222311c3cef5027`、
  `dd7d8fc342e3a1e6e41e88342907a522c3919ce4`
- evidence:
  `docs/9.3.4/evidence/step-3/step3-external-vector-runner-candidate-20260715.md`。

实现与验证结果：

- committed run `external-vector-candidate-dd7d8fc3-r1` 使用 digest-pinned Milvus `v2.4.4`、
  etcd `3.5.5`、MinIO `RELEASE.2023-03-20T20-16-18Z`，动态 loopback、单一 bridge network、
  三个 run-labelled named volumes 与独立 ephemeral MinIO 密钥，得到 exact
  `1 variant / 2 reports / 20 testcase / F0/E0/S0`。
- `VectorIT` 的 assumption/property/API-key 路径已替换为 loopback OpenAI-compatible fixture，
  精确验证十次 embedding 请求、八维向量、非空查询结果、bounded polling 与临时 collection
  cleanup；`VectorStoreIT` 移除 class-level disabled，使用 deterministic `EmbeddingModel` 和
  `FLAT/COSINE`。
- fresh initial collection count=`0`；final fixture 只含 `v934_vector_store`，精确五行
  `qt1..qt5`、fields=`content,embedding,id,metadata`、dimension=`8`，临时
  `foggy_test_documents` absent。terminal container/volume/network residue=`0/0/0`。
- candidate manifest 绑定 29 个文件并重验通过；source before/after、variant bytecode seal、
  12/12 report negatives、6/6 sensitive probes、两项 ephemeral secret exact scan 均通过。
- real signal group `external-vector-signal-dd7d8fc3-r1` 的 INT/TERM/HUP 分别为
  `130/143/129`；durable status 均 failed，summary/candidate/FIFO absent，container/volume/
  network residue=`0/0/0`。
- fail-closed diagnostics 依次暴露 shell boolean identity、protobuf `3.11.4` 与 Milvus SDK
  `2.5.8` 不兼容、VectorStore JDBC auto-configuration 泄漏及 Milvus metadata Base64 JSON
  形态。六个 failed/diagnostic runs 均无 candidate 且 residue=`0/0/0`；仅 dd7d8fc3 r1 入账。

Evidence boundary / non-goals：

- Vector final manifest 为 `complete=false`。Redis + Mongo + MySQL + Vector 四个独立 candidates
  只构成 external progress ledger=`16/76/F0/E0/S0`，不能跨 run 拼成 full external authority；
  required selector gap 已清零，但 shared outer-run replay/merge 仍开放。
- wrong-image/version、unavailable、dirty-state、fixture-mutation、forced-cleanup-failure
  resource negatives，数据库四外库 `24/320`、optional LLM disposition、Addon lifecycle 和
  archive portability 仍开放。
- protobuf pin 是 Milvus SDK 运行兼容修复；DataSource exclusion 仅限 VectorStore 集成测试，
  本批没有扩大到 9.3.5 公共 API 或 9.4.0 模块化范围。

Decision：Vector subset candidate=`passed`，Step 3=`in-progress / not passed`。下一动作是
同一 shared outer run 的 external `16/76` replay/merge 与 state negatives；不进入 Step 4。

### Implementation Quality Self-check — Vector external subset

- mode: `subset-read-only-review`；本批质量复核 result=`ready / blocking=0`，formal Step 3
  quality gate 仍在完整 `45/446` exit 前执行。
- implementation closure: runner/tool/contract、确定性 embedding、fresh Milvus topology、
  fixture/index exact gate、dependency compatibility、sensitive scan 与信号清理已收口。
- verification: Bash/Python/JSON/Maven static checks、contract validator、source/bytecode seal、
  candidate verifier、2/20 fresh execution、negative/sensitive probes、signals 与 Docker residue
  均通过；独立只读 candidate/signal 审计 finding=`0`。索引响应进一步校验唯一条目/
  Finished 状态保留为非阻断增强项。
- protected changes: 用户已有 PreAgg POM/source/test 与新 contract 文件未触碰、未暂存。
- decision: `passed-subset / needs-formal-quality-gate-at-step3-exit`，不允许提升为 full
  authority、coverage entry 或 version acceptance。

## Execution Check-in — Step 3（in-progress / shared external matrix）

- started_at: 2026-07-15
- completed_at: 2026-07-16
- owner: current 9.3.4 root session
- implementation commits:
  `18498e4d206a41fe7a13491fecaf0fcba78b56e4`、
  `b602ee510568f3d1ea0fa7d8e3cafca494b51cea`、
  `47d1afd7fb59f1cc6beab3ba68d0b7dd4589b6ab`
- evidence:
  `docs/9.3.4/evidence/step-3/step3-shared-external-matrix-candidate-20260716.md`。

实现与验证结果：

- `scripts/verify-v934-external-matrix.sh` 在唯一 authority lock、outer marker 和 source seal
  下，按 Redis → Mongo → MySQL → Vector 顺序启动四个 shared children；children 继承同一
  outer/HEAD/contract 和 lock FD，七个 variants 不再跨 run 拼接。
- committed candidate `external-matrix-candidate-47d1afd7-r1` 精确得到
  `complete=true / 7 variants / 16 reports / 76 testcase / F0/E0/S0`。分项为 Redis
  `2/3`、Mongo `4/30`、MySQL `8/23`、Vector `2/20`；MySQL direct structured report=
  `23/23 passed / business errors=0`，optional LLM 未进入 required lane。
- final candidate 绑定 273 个 artifacts；outer 与四个 nested candidates、七个 variant
  manifests、16 份 raw Failsafe XML、source/bytecode seals、resource/fixture/cleanup evidence
  均由 verifier 重验。source before/after 逻辑 SHA 均为
  `d19a27991e5bba89515b75fa48f170d26257604859a87ccb49de8ff1ce7c95ea`；Docker
  container/volume/network residue=`0/0/0`。
- outer report negatives=`12/12`，四 lane sensitive negatives 合计=`24/24`，全目录
  sensitive scan 通过；父级扫描已排除 JSON/env 中值为 null 的非凭据字段，真实短凭据
  fixtures 仍全部命中。
- 首个 outer candidate `external-matrix-candidate-18498e4d-r1` 的正向 7/16/76 虽通过，
  但后续 INT probe 证明后台 `setsid ... &` child 继承 ignored SIGINT，child durable
  status/cleanup 缺失，因此该 generation 被 supersede。Python exec launcher 在 exec 前
  reset signals、建立 PID=SID=PGID、校验 exported/inherited lock FD 和 same-OFD flock；
  parent grace=`20s`、matrix probe finalize=`60s`。
- 修复后 signal group `external-matrix-signal-47d1afd7-r1` 的 INT/TERM/HUP parent 与
  Redis child 均分别写 `130/143/129` failed status；child cleanup=`0/0`，outer aggregate
  cleanup=`0/0/0`，summary/candidate/final/FIFO absent，Docker residue=`0/0/0`。

Evidence boundary / non-goals：

- 本记录关闭的是 required external `16/76` 的 single-outer replay/merge，不等于 Step 3
  final `45/446`；database remaining `24/320`、DB/resource-state negatives、optional LLM
  disposition、Addon lifecycle 与 portable archive 仍开放。
- 旧四个 `complete=false` subset candidates 和 `18498e4d` signal-broken generation 只保留
  为诊断/历史证据，不参与当前 candidate 拼接或后续 acceptance。
- 本批不产生 JaCoCo exec，不进入 Step 4；用户已有 PreAgg POM/source/test/contract 变更
  未触碰、未暂存。

Decision：shared external subset=`passed`；Step 3 继续 `in-progress / not passed`。下一动作
是 DB/resource-state negatives、同 commit 四外库 remaining `24/320` 与 optional LLM reviewed
disposition；不进入 Step 4。

### Implementation Quality Gate — Shared external matrix

- mode: `formal-subscope-quality-gate`；candidate audit=`CLEAN`、signal audit=`CLEAN`、
  implementation quality=`PASS`，new P0/P1=`0`。
- prior blocker: 后台 child ignored SIGINT 已由 reset→setsid→exec launcher、same-OFD lock
  验证和 20s/60s 分离关闭；旧 `18498e4d` generation 明确 superseded。
- mechanical review: 273-artifact exact set、1544-row top-level source seal、13 contract
  bindings、16 XML/76 nodes、INT/TERM/HUP parent+child durable evidence及实时 Docker residue
  均独立复算通过。
- decision: `passed-external-subset / needs-step3-remaining-work`；本质量结论不签收 Step 3
  remaining `24/320`、state negatives、optional LLM、Addon lifecycle 或 Step 4。

## Execution Check-in — Step 3（passed / required matrix exit）

- started_at: 2026-07-15T20:18:24Z
- completed_at: 2026-07-15T20:42:19Z
- owner: current 9.3.4 root session
- tested commit: `ce3d70c391c7b8bd8046fe66dde0ad568d66601e`
- authority run: `step3-required-20260716-final-r4`
- evidence:
  `docs/9.3.4/evidence/step-3/step3-required-matrix-exit-20260716.md`
- scope: PreAgg runtime watermark/materialized-column lifecycle、fresh five-DB matrix、
  required Redis/Mongo/MySQL/Vector、exact deferred-union collector、state/report/sensitive
  negatives、resource cleanup 与 optional LLM disposition。
- non-goals: 不生成/接受 JaCoCo exec；不做 Step 5 portable archive、Step 6 CI/branch
  protection、Step 7 clean release authority；不签收 9.3.4 version。

Development：

- Addon `PreAggPhysicalColumnContract` 统一物化列显式映射，query 消费一致的 explicit
  mapping data；shared strict-DAY `PreAggWatermarkResolver` 统一 semantic/source/
  materialized watermark roles。DATE watermark 固定为 exclusive upper bound，hybrid=
  `materialized < W / source >= W`；事务成功后才分别发布 runtime refresh time/W，失败不
  发布；service 以 runtime preAgg 串行化 refresh，scheduler 以 taskInfo 锁覆盖
  capture→service→mirror publication。
- matcher/rewriter 对 null、非 `LocalDate`、future W、无 source JOIN proof fail closed；
  reload/restart 后 W 为空，首次 refresh 回落 FULL，不声明持久化。
- 五库补齐原生 DATE fixture、SQLite TEXT 绑定、dialect fail-closed resolution；MySQL
  provision readiness 绑定业务 identity 与 final init marker。
- external MySQL 69-table content/snapshot hash 已与 native DATE canonical fixture 同步；
  runner/report verifier 均冻结同一值。
- `MultiThreadExecutor` 等待输出改为 debug logger，并增加 shutdown=false、错误传播与
  waiter interrupt 三个 deterministic Step 4 prerequisite tests。

Testing：

- parent required=`45 reports / 446 testcase / F0E0S0`；DB/external execution keys=
  `29/16`，gap/overlap/extra=`0/0/0`。
- database=`29/370/F0E0S0`：SQLite `5/50`、MySQL 5.7 `5/50`、MySQL 8 `6/105`、
  PostgreSQL 15 `8/115`、SQL Server 2022 `5/50`。
- external=`16/76/F0E0S0`：Redis `2/3`、Mongo `4/30`、MySQL57 `8/23`、Vector `2/20`。
- required companion Addon=`2/6/F0E0S0`，明确不计入 45/446。
- negatives：parent `17/17`、DB state `18/18`、DB report `14/14`、external report
  `12/12`、external sensitive `24/24`、Redis state `4/4`、Addon `4/4`。
- source/fixture before=after；run-owned container/volume/network residue=`0/0/0`；父
  final/candidate 与所有 child authority verifier 独立重放通过。
- same-HEAD focused prerequisite：`MultiThreadExecutorTest=3/3/F0E0S0`。
  Companion artifact identity 见
  `docs/9.3.4/evidence/step-3/step3-multithread-prerequisite-evidence-20260716.md`，
  raw XML SHA=
  `5d3ba9ff2778886160262b499999753f98a0b9251f5a76f6b9f86c45d723291a`。

Experience：`N/A`。本 Step 是后端 build/test authority 与数据生命周期治理，无 UI、
浏览器或人工体验交付。

Deviations / failed attempts：r1（MySQL business readiness）、r2（final init 尚未完成）、
r3（native DATE fixture hash 传播）均按预期 fail closed、无 candidate、零残留；
external MySQL hash diagnostics/native-date r1/r2 只用于修复与预验证，不进入 r4 authority。

Blockers：none。独立 source/final evidence 审计得到 blocker/high/medium=`0/0/0`；
required workitems 的真实 checklist 已闭合。已知 Mongo loader production JDBC 解耦、
runtime watermark 非持久化、缓存非立即一致等边界保持显式，不影响 Step 3 exit。

Self-check decision：`ready-for-formal-quality-gate`。Step 3 correctness、negative、cleanup、
optional disposition、Addon companion 与 Step 4 并发前置条件全部满足；此时 Step 4
仅为 `entry-candidate / not-started`，待 formal quality→coverage→feature acceptance
顺序完成后才标记 ready。

## Execution Check-in — Step 4（in-progress / contract+agent bootstrap，historical）

- started_at: 2026-07-16
- owner: current 9.3.4 root session
- entry commit: `e1a2a275ae5f39ca0be641ef18ca5622fa4c7076`
- entry evidence: Step 3 r4 exit + ordered quality→coverage→feature acceptance；
  `MultiThreadExecutorTest=3/3/F0E0S0` prerequisite。
- exact coverage scope: all unit；6 个 hermetic/SQLite IT variants；five-DB 7 variants；
  required external 7 variants；Addon companion 2 variants。共 23 个唯一 exec；optional
  LLM 保持 `reviewed-optional-excluded`。
- contract decision: Step 1 `scripts/v934/coverage-thresholds.json` 与
  `scripts/v934/SHA256SUMS` 永久保持冻结；Step 4 在 `scripts/v934/step4/` 建
  parent-linked successor，不改写 predecessor authority。
- run layout: `target/v934-step4-coverage/runs/<run-id>/`；`exec/` +
  `exec-manifest.json` + `report/`；持久化 exit 计划写
  `docs/9.3.4/evidence/step-4/step4-coverage-exit-<date>.md`。
- Work 1 implementation: root `v934-coverage` profile 已用独立 late-bound
  `jacoco.ut.argLine` / `jacoco.it.argLine` 保留 UTF-8 JVM 参数；Surefire/Failsafe 固定
  `forkCount=1/reuseForks=true/append=true`。POM-only reporter 使 reactor 精确成为冻结 24
  个 production modules + 1 个 build-only reporter，production POM 对 reporter 反向依赖=0。
- exact overlay: `coverage-exec-ledger.tsv` 冻结 `23 exec / 48 sessions`；报告层另由
  `coverage-report-amendment.tsv` 冻结 4 个新增、6 个变更报告。当前 required overlay=
  `773 positive + 59 structural / 5,707 testcase`，Addon companion 仍单列 `2/6`，不把
  23 个 exec 冒充测试报告数。
- runner instrumentation: Unit 1、Step 2 IT 6、DB 7、external 7、Addon 2 的真实 Maven
  invocation 均接入 run-owned exec；Redis writer/restart 两个 child JVM 显式注入同一
  `redis7` exec 的独立 session。helper 拒绝非 canonical run path、覆盖已有 exec、Maven
  `-T`/coverage override 和错误 JaCoCo agent SHA。
- tooling probes: bootstrap contract validator=`passed`（parent manifest 29、frozen24+reporter、
  23/48、773/59/5707、model legacy/Step4 gates）；exec reader 使用 JaCoCo core 0.8.12
  解析 session/class ID，并验证当前 production class tree；missing/empty/corrupt/truncated/
  symlink/wrong-session/class-ID negatives=`7/7`。focused Unit agent-on、agent-off、Failsafe
  IT 与 aggregate XML/HTML reporter 均已通过；focused aggregate 包含 `2 sessions`，只作
  bootstrap proof，不是 all-lane baseline。
- inherited model gate: 新 `v934-coverage-model-check` 只消费外部 merged exec，不安装 agent；
  缺失/sentinel/相对路径在 validate 阶段 fail closed，并保留 bundle `0.77/0.62` 与
  `SemanticScaleSqlSupport=1.00/1.00`；legacy `coverage` profile 未改写。
- regression found/fixed: Step 4 report refresh 捕获 `PreAggregationEdgeCaseTest` RED=
  `22/3`。单类/单方法复现证明不是顺序污染，而是 snapshot-only fixture 默认 hybrid 且
  watermark=null；只为三个 fixture 显式设 `hybridQueryEnabled(false)`，生产 Matcher 的
  null-watermark fail-closed 不变。修后单类=`22/22/F0E0S0`、三类聚合=
  `48/48/F0E0S0`，见
  `docs/9.3.4/workitems/BUG-step4-preagg-unit-order-isolation.md`。
- historical boundary: 本段 bootstrap 当时尚未完成 XML verifier 与 single
  outer/inherited-lock orchestration；后续状态以下方 diagnostic-ready superseding
  check-in 为准。
- non-goals: 不复用 Step 2/3 correctness XML 冒充 coverage；不开始 Step 5 archive、
  Step 6 CI/branch protection、Step 7 clean authority；不改 9.3.5/9.4.0 production API。
- blockers: none；正式 rerun 前必须先让 successor/ledger/agent focused probes fail
  closed。

### Superseding Check-in — Step 4（in-progress / diagnostic-ready）

- recorded_at: 2026-07-16
- status decision: `in-progress / diagnostic-ready`，明确不是 `passed`；这一状态
  只允许在完成提交、推送并确认 clean HEAD 后启动 fresh diagnostic。
- Development: single-outer Step 4 orchestration、parent-linked coverage successor、
  run-owned exec/report provenance、aggregate/model checks 与 toolchain receipt 已进入本地
  diagnostic-ready baseline。执行库存 exact=`23 exec / 48 sessions`；required
  report overlay=`773 positive + 59 structural / 5,707 testcase / F0E0S0`，Addon
  companion 独立为 `2/6`。
- Testing: raw contract negatives=`8/8`，effective POM negatives=`4/4`，toolchain
  receipt negatives=`5/5`，report inventory negatives=`27/27`，Step 2 derived view
  negatives=`12/12`，successor overlay negatives=`8/8`（新增 Redis 显式路径错绑负例）。
  这些是静态/防篡改 readiness evidence，不是 all-lane coverage result。
- Implementation quality: formal pre-coverage-audit record=
  `docs/9.3.4/quality/step4-diagnostic-ready-implementation-quality.md`，decision=
  `ready-with-risks`；只放行提交/push 后的 clean-HEAD diagnostic，当前
  `can_enter_coverage_audit=no`，不表示 Step 4 passed。
- Regression found/fixed: root Surefire/Failsafe 的 `${argLine}` 早解析使 legacy
  model coverage `prepare-agent` 只写日志、不产 exec，report/check missing-data skip
  后仍 BUILD SUCCESS。改为 `@{argLine}` late evaluation 后，focused legacy exec=
  `336699 bytes`且实际读取，因 `0.17/0.11` 与关键类 `0.71/0.55`
  低于既有门而正确 `rc=1`；普通 `MapBuilderTest`=`rc0/no exec`；
  v934 profile=`rc0/30713 bytes/session static-audit-foggy-core`。生产代码不变，
  `coverage_tool.py` 精确强制 canonical 形式，并由 manifest + `validate-contract` +
  `8/8` negatives 自动防回退；BUG 已关闭，见
  `docs/9.3.4/workitems/BUG-step4-legacy-coverage-argline-fail-open.md`。
- Toolchain identity: run-owned receipt 绑定 Step 1 raw 工具版本、compiler realm
  ASM `9.6`、JaCoCo realm ASM `9.7`、test classpath ASM `9.7.1`以及 24 个
  production module effective compiler，并在执行链边界重放验证。
- Publication: `coverage-report-amendment.tsv` exact=
  `10 rows = 4 new + 6 changed`，SHA-256=
  `5a1a07e2c47835fa244b90a06334341e13660a305d9eb7c74c64ee2f36a06504`；
  successor declared amendments=`15`。本地 `scripts/v934/step4/SHA256SUMS` 集合/顺序/
  hash exact 49 项校验通过，manifest SHA-256=
  `c735e8c1f7b74d72afe2d1d1872128d11a16acbd7373c750e59709624560106e`。
- Supersession boundary: historical `scripts/verify-v934-step2-successor.sh` 保持冻结的
  24-production-reactor generation 语义，不因 Step 4 build-only reporter 放宽为
  25。当前路径是 immutable Step 2 parent + `step2_report_view_tool.py` derived
  view + overlay；historical runner 在 25-module root fail closed 是预期，不改写
  Step 2 authority。
- Evidence/threshold boundary: `coverage-thresholds.json` 仍为
  `diagnostic-pending`；尚无 all-lane aggregate baseline、人工 review、confirmed
  thresholds 或 Step 4 exit evidence，不创建/预签
  `docs/9.3.4/evidence/step-4/step4-coverage-exit-<date>.md`。
- Experience: `N/A`，本 Step 是构建/测试证据链，无 UI 或人工体验面。
- Deviations: 无需扩展 9.3.5/9.4.0 范围；diagnostic-ready 不改变 Step 4
  Exit 与 Step 5 entry 契约。
- Historical boundary: 本节记录的是 r1 启动前的 quality decision；r1 实际结果与当前
  next gate 以下方 post-diagnostic check-in 为准。

### Post-diagnostic Check-in — Step 4 r1 fail-closed / fix focused-green

- recorded_at: 2026-07-16
- run identity: clean/pushed HEAD=`bc100b0f63bd3ff62d1105611dae41741790aedd`，run=
  `step4-coverage-20260716-diagnostic-r1`，phase=`child-unit`。
- result: Unit authority=`3115 tests / 1 failure / 0 errors / 0 skipped`；outer runner
  fail closed，r1 未进入 reporter/model/aggregate/threshold，不能拼接为 coverage evidence。
- reproduction: failing method、独立单方法与整类均复现；整类=`9/1`，排除跨类顺序和
  Spring context 污染。日志证明测试腐化 `preagg_daily_product_sales`，生产 matcher 因
  watermark=null 正确跳过 daily，实际 SQL 命中 `preagg_monthly_category_sales`；同时两项
  comparison 以 `preAggHit=false` 退化为 raw-vs-raw，nullable/empty 也可伪绿。
- fix: 只修改测试 fixture/assertion；monthly 腐化/恢复均要求 exact one row，先断言
  `preAggHit=true` 与 `preAggName=monthly_category_sales`，三个 snapshot 路由分别固定为
  `daily_product_sales`、`daily_product_sales`、`daily_customer_channel_sales`，并拒绝
  null/empty/missing/non-numeric 数据。生产 Matcher、threshold/exclusion 不变。
- verification: focused class=`9/F0E0S0`；DataValidation+EdgeCase+Matcher+
  RequirementBuilder=`57/F0E0S0`；corruption monthly delta=`1000.00`；source SHA-256=
  `affb6e415c1770eae7c9ab6f3505ac67acfe03eac1461bd2a48addf3ee790623`。focused module
  `target` XML 仅作可覆盖回归，不是 immutable Step 4 evidence。workitem=
  `docs/9.3.4/workitems/BUG-step4-preagg-validation-wrong-table.md`，测试缺陷已关闭。
- contract refresh: report amendment 从 `9 rows / 4 new + 5 changed` 更新为
  `10 rows / 4 new + 6 changed`，successor declared amendments 从 `14` 更新为 `15`；
  required totals 仍为 `773/59/5707/F0E0S0`，exec/session 仍为 `23/48`。
- Blockers: r2 的唯一前置是把修复与 successor refresh 提交、推送，并证明新的
  `HEAD == origin/main`、worktree clean；Step 4 exit 仍缺完整 r2、aggregate review 和
  confirmed thresholds。
- Next Gate: 在新修复 clean/pushed HEAD 执行 r2。`coverage-thresholds.json` 仍为
  `diagnostic-pending`，`can_enter_coverage_audit=no`；Step 5 与 9.3.5 保持关闭。

### Superseding Post-diagnostic Check-in — Step 4 r2 fail-closed / r3 pending

- recorded_at: 2026-07-16
- run identity: clean/pushed HEAD=`0101a44a07784bf6b484d490c7fb508727fbab70`，run=
  `step4-coverage-20260716-diagnostic-r2`，outer phase=`child-integration`，child phase=
  `variant-sqlite-broad`，status=`failed / exit_code=1 / summary absent`。
- immutable result: Unit=`681 execution + 55 structural / 4,941 testcase / F0E0S0`；
  Integration caffeine=`2/F0E0S0`、hermetic=`3/F0E0S0`、sqlite-broad=`307/F1E0S0`，
  合计 `312/F1E0S0`；唯一失败为
  `PreAggregationL2CacheIT#shouldUsePostPreAggregationIdentityForL2LookupAndWrite`。
  database、required external、Addon、aggregate、model merged-exec check 和 threshold
  均未执行；cleanup residue=`0/0/0`。
- artifact identity: source seal=
  `428ede2bab82483ed97c857ea73e16b35f9e86ea750f94cded26bc3df9d13079`；outer status=
  `e20643fe6bc8c24d1ca6c6a9979cc706bf67e8fa7f5df715b36b1671d5a584c7`；outer log=
  `16670b8c01a3fc399ad0a6e14a1f0815085533e75f4958f0c14272352a275784`；receipt=
  `a8c9aeccfecfa684b9aa99e56d24c115d9530b56908feaa3f3711c8ad1d96248`；Unit summary=
  `9227f74aa266bdda3f58a146417805fc282bc81ca4a2efe5e47bd195db334f0a`；Integration
  status=`ec1a2fcaa00458e5b998a30d323ab46ce85f4970737fcac813f0c6de2a4c6096`。
- L2 repair: 只冻结 snapshot-only fixture、exact preAgg name/table/raw negative 与
  post-rewrite lookup/write/hit identity；focused=`1/F0E0S0`，与 `PreAggregationIT` 组合=
  `30/F0E0S0`，source SHA-256=
  `bb5d6884401579447382587861e197feac2884ab701065d7826fe7a676e0c313`。
- proactive Pivot repair: legacy 单方法两次稳定 `1/F1E0S0`；只在 legacy 分支关闭 hybrid，
  保留 V934 FULL production 默认并断言两分支 exact identity。legacy/V934 SQLite 各
  `1/F0E0S0`，source SHA-256=
  `5c6dcd3b4afba4d93a93c1af47cc4484a1dfc9976da92669bfbcc4529ede6155`。
- identity/static result: coverage amendment=`11 rows / 4 new + 7 changed`，SHA-256=
  `937666fc1926ec1c4764ebb50d4b4d4bdd1f1013f0d63cc77d9a1856fae153d2`；declared
  amendments=`17`，SHA-256=
  `be9a2d553499f799d5dc81cee353397799ad3f01d2923c6aeccb82fdb9bd7548`；top manifest=
  `51/51`，SHA-256=`348ade918a5020b9b65b9fb93e4bb7034e73f197c8545c7cbbfeb3d34d044ac1`；
  successor manifest=`12/12`，SHA-256=
  `6ac8a24dd983c1929f6d21430f57adca503893e69b368b37a08731f5a5355948`。
  positives coverage=`773/59/5707`、Step 2=`724/59`、DB=`7/29/370`、overlay required=
  `45/446`、Addon=`2/6`；negatives coverage/view/successor/DB=`8/12/8/14`，全部通过。
- evidence: failed diagnostic 固定为
  `docs/9.3.4/evidence/step-4/step4-coverage-diagnostic-r2-fail-closed-20260716.md`，decision=
  `excluded-from-step4-exit`；对应 L2/Pivot workitem 保持 `in-progress` 到 r3。
- Quality/Next Gate: 当前实现 `ready-with-risks`，无阻断提交最终修复/identity 并运行 r3 的
  实现 blocker；threshold=`diagnostic-pending`、`can_enter_coverage_audit=no`。提交、推送并
  证明 clean HEAD 后运行唯一 r3；Step 5、9.3.5 与 acceptance 保持关闭。

### Superseding Post-diagnostic Check-in — Step 4 r3 fail-closed / Cdiag remediation

- recorded_at: 2026-07-16
- run identity: clean/pushed HEAD=`e16693297239f2a861f3b93b3de60c1bb783bda0`，run=
  `step4-coverage-20260716-diagnostic-r3`，outer=`failed / exit_code=1 /
  last_phase=child-unit / summary absent`。
- immutable result: contract/successor/toolchain/Step 2 view/fresh class universe 均通过；Unit=
  `681 positive + 55 structural / 4,941 testcase / F0E0S0`。Unit PASS 后 outer 报
  `child returned with live process-group residue: unit`；Integration、database、required
  external、Addon、aggregate、model gate、coverage observation 与 threshold review 均未执行；
  cleanup residue=`0/0/0`。
- root cause: Unit/Integration 使用异步 `exec > >(tee -a run.log)`，没有保存并 wait logger。
  child leader 与 process-substitution tee 位于同一 PGID；outer 只 wait leader 后立即探测
  PGID，因此可在 tee flush 窗口误判 residue。受控原模式立即探测=`100/100 alive`、10ms 后=
  `0/100 alive`；显式 close/write-end + wait logger 后立即残留=`0/100`。
- evidence: immutable record=
  `docs/9.3.4/evidence/step-4/step4-coverage-diagnostic-r3-fail-closed-20260716.md`；BUG=
  `docs/9.3.4/workitems/BUG-step4-child-run-log-tee-residue-race.md`；decision=
  `excluded-from-step4-exit`。
- remediation boundary: managed logger 是根修复；同时增加 child PGID ready receipt 与真实
  residue member snapshot。下一代 Cdiag 一次性加入 pending/confirmed 双态、exact fraction、
  freeze candidate、formal gate/candidate/final 通用能力，但 threshold 继续
  `diagnostic-pending`，formal 必须 fail closed。
- Quality/Next Gate: 当前 `can_enter_coverage_audit=no`。完成 runtime negatives、identity、
  formal implementation quality，分阶段 commit/push 并证明 clean HEAD 后，才运行 fresh r4；
  r4 observation 通过前不得 freeze threshold、启动 Step 5 或验收。

### Superseding Pre-r4 Check-in — quality passed / identity refreshed

- recorded_at: 2026-07-16
- status decision: `in-progress / pre-r4 quality passed / identity refreshed / fresh r4
  pending`，不是 Step 4 `passed`；
- implementation: Unit/Integration 共享 owned FIFO logger，child lifecycle 绑定
  PID/PGID/SID/starttime/boot-id；outer 在首个 Git/lock 前清除 ambient `GIT_*`；
  live/frozen XML verifier strict-read canonical source/context，并对 exact retained 23 raw
  exec 执行 dirfd/nofollow/stable-inode 逐字节重放；
- independent static evidence: authority=`2 positive + 14 negative`，contract=
  `20/20`，source Git identity=`7/7`，XML=`63/63`，logger=`9 类 / 14 case`，
  Step 2 view=`12/12`，overlay=`12/12`，DB=`14/14`，external=`12/12`；
- publication: declared amendments=`17`，SHA-256=
  `1e4f15c9e403d454fe07404e45b1226eae94f70faa154433a8db39531a305b47`；
  successor manifest=`12/12`，SHA-256=
  `961e50350cef1c7984c6ff6b4fd0b5716ac5bb87d42271a3478233258b30784f`；
  top manifest=`54/54`，SHA-256=
  `589a7d67f35a0f09c7f1a026dbbf07e56dc89f099ca51291418cd1c6cc5fd077`；
- formal implementation quality: `ready-with-risks`，open Blocker/High/Medium=
  `0/0/0`；唯一开放边界是保留 diagnostic raw exec 直到 Cfreeze/formal，任何
  byte/mtime/identity 漂移都会 fail closed；
- Next Gate: 提交并推送最终 Cdiag，确认 worktree clean 且
  `HEAD == origin/main`，再执行唯一
  `step4-coverage-20260716-diagnostic-r4`。threshold=`diagnostic-pending`，
  `can_enter_coverage_audit=no`，Step 5/9.3.5/acceptance 仍关闭。

### Superseding Post-diagnostic Check-in — Step 4 r4 fail-closed / source-policy remediation

- recorded_at: 2026-07-16
- status decision: `in-progress / r4 historical fail-closed / source-policy remediation
  statically passed / final review passed B/H/M/L=0/0/0/2 / amend/push + fresh r5 pending`，
  不是 Step 4
  `passed`；
- reported launch identity: 调用方报告启动前 HEAD/origin/main=
  `ceea084ca25a9d679ba128e3f6bd50a63322c112`，run=
  `step4-coverage-20260716-diagnostic-r4`。r4 没有发布 run-owned Git/source seal，故该
  commit 只记为 `reported_launch_head`，不得改写成 run-owned `tested_commit`；
- immutable result: 前置 full contract、contract negative=`20/20`、当时的 source Git
  identity=`7/7`、XML=`63/63`、successor overlay=`12/12` 与 logger=`14/14` 通过；outer
  随后在 `source-before` 以 `exit_code=2` fail closed。`git_head/started_at/source_*` 字段
  为空，source-before/after、run-context、所有 test lane、aggregate、threshold、summary
  均 absent，cleanup residue=`0/0/0`；decision=`excluded-from-step4-exit`；
- evidence boundary: row 1 的 `worktree executable mode differs` 是失败后对同
  commit/worktree 的直接重放结果，不是 immutable `run.log` 原文；r4 record=
  `docs/9.3.4/evidence/step-4/step4-coverage-diagnostic-r4-fail-closed-20260716.md`，BUG=
  `docs/9.3.4/workitems/BUG-step4-source-inventory-filemode-false.md`；
- root cause: `.git/config core.fileMode=false`；r4 commit 跟踪 `3,968` 个文件，其中
  `3,452` 个 Git `100644` 文件在 worktree 带 executable bit。旧 validator 把 authoritative
  Git mode 与 worktree permission executable bit 错误等价，因而误拒绝 clean checkout；
- remediation: source identity 现在分层验证 exact HEAD/index path+Git mode+blob 与安全
  worktree regular-file/content/owner/private-primary-group/link/stat；worktree executable bit
  不再冒充 Git mode。world-write、special-bit、hardlink、错误 owner/group、内容/identity
  漂移继续 fail closed；security Git 调用固定关闭 fsmonitor/untracked-cache，并要求
  ordinary index flags；tracked FIFO 在 worktree-aware Git 前 preflight fail-fast；
  before/after raw stat identity 拒绝 Git-clean-equivalent concurrent rewrite；outer 现会重放
  source-hash 错误；
- clean-equivalence: source seal 清除 ambient/global Git clean 配置并显式复算 raw 与
  CRLF-input 两个 candidate，使真实 CRLF worktree 在 HEAD/index clean-equivalent 时通过；
  HEAD-fixed attributes 若声明 external clean filter，则在任何 worktree-aware Git
  hash/driver hook 执行前 fail closed，negative 证明 hook 未执行；
- independent static evidence: contract=`20/20`、source identity=`22/22`、XML=`63/63`、
  overlay=`12/12`。declared amendments 保持 `17`，SHA-256=
  `1e4f15c9e403d454fe07404e45b1226eae94f70faa154433a8db39531a305b47`；coverage
  contract diagnostic/formal SHA-256=
  `5f4b49fd161b4f381a4f8c2238583eb56f27b577973ff93ce0659d84cca75f1d` /
  `58c3479666d0b786ea0ad8327b72b05c9e006dfdb516eacce9098ea83ef4c405`；successor
  manifest=`12/12`，SHA-256=
  `751018ac7c2357cface77dd125c5edc757ad488a500a3c8d9eece0354767381a`；top
  manifest=`54/54`，SHA-256=
  `ebda814b1278f92cf1ba7dc202170e4a77cb7e1f4485e6cb1375d152592a76d0`；coverage tool /
  contract-negative / XML tool SHA-256=
  `07a36a2be8edc0afc0ab1031b052c2208a4e32769c4cdb475a397f81e6121ac9` /
  `732d799619461a4b49c8e9bfbb0a3487b107c36110b9e55cd91a405352d0ddb0` /
  `b837314ac4166eeeab94124b53e4f776dcdf8095a3b3915e14e45b81d910d439`；overlay contract /
  overlay tool / outer SHA-256=
  `2d4fe0024caac33199e2ccf87289dd9a262302d3faabad6b038adadb2b2974cb` /
  `a16aadf9c4d540cda8b95d1fc1ded94cf420aa0cfe5a1653b8f90d4cb72e0f51` /
  `254c7603554787ca38d880ac607f7dd4a21ae89064674490858245f0824951c9`；
- final-byte quality: decision=`ready-with-risks`，open Blocker/High/Medium/Low=`0/0/0/2`；
  两项 Low 均 accepted：`/usr/bin/echo` 平台前提漂移会 fail closed；同 UID 视为 build
  authority，未来更强隔离改用 readonly snapshot/独立 checkout。当前只放行 amend/push 与
  fresh r5，不开放 audit 或 Step 5；
- Next Gate: amend/push，证明 clean worktree 且 `HEAD == origin/main`，再用唯一新 run id
  执行 fresh r5 all-lane
  diagnostic。threshold=`diagnostic-pending`，`can_enter_coverage_audit=no`，Step 5、9.3.5
  与 acceptance 继续关闭。

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

### Superseding Post-diagnostic Check-in — Step 4 r5 fail-closed / successor remediation

- recorded_at: 2026-07-16
- status decision: `in-progress / r5 historical fail-closed / database-state successor
  remediation quality passed B/H/M/L=0/0/0/0 / commit-push + fresh r6 pending`，不是
  Step 4 `passed`；
- immutable r5: tested commit=`a35b99cb08f42817d8e75c440f18910b6961841b`，run=
  `step4-coverage-20260716-diagnostic-r5`，source-before=`3,970 files` / SHA-256=
  `1d8fedc784e3f3a2f70d21666e38417de8aba76d55a51df7cf76919d78c1ad17`；
- failed boundary: Unit=`681 positive + 55 structural / 4,941 / F0E0S0`、Integration=
  `47 positive + 4 structural / 320 / F0E0S0`、Addon=`2/6/F0E0S0` 已完成；
  database-state companion 随后因 frozen Step 3 authority manifest 中 model POM SHA stale
  而以 `E_AUTHORITY_MANIFEST` fail closed。database cells、external matrix、aggregate、
  threshold、source-after 和 summary 均 absent；r5=`excluded-from-step4-exit`，上述
  partial lanes 不得拼接或复用；
- remediation: 保持 frozen Step 3 字节不变，新增 Step 4 successor
  database-state 与 required-report adapters，使 database positive/state/final verifier 共用
  successor authority。current static identity：contract diagnostic/formal=
  `16677d3ae64a7d24aa5796e7c1bbb8ca5af347d6843878471a7e48bdc52c82af` /
  `d8e7efa775d021d42485f1ffa6cb51a98a3f3f6662b1793e6b06f69852d12463`，
  successor=`14/14` / `9fa9ddb23aa36c48961e54393f1fe747bf5d0433645cb1a0529e607db4f211cb`，
  top=`56/56` / `be8c4c9c1698674917f1115388d3e7b6a6078d698daf52cb4fa55916166460f9`，
  overlay contract/tool=
  `cd691d3d91540dd6ddba0045648493d16feaf9ebf3175da3b9ad15b0e399aadd` /
  `4df218807847beb789dcf1ef748e13bf21f39da071e4bcf7337fe97b78f8c84a`，
  coverage tool=`bf317dd09bb2f909773dba602ab00037acf112b835a166bfd64ef9709045179a`，
  declared amendments=`17` / `187aac883460b259cd002f6c12bb72d8d9824d1e4dd8f12a12959f6866bfccfe`，
  database/required contracts=
  `553dabf2b4c266b531fb4ce36f4a498dce223b6449106274a3a2b103ccb775ea` /
  `893ac03231cb4f6fd8ae427c01aa3f9f04267c96e3945814b9b70a3445a58af5`；
- records:
  `docs/9.3.4/evidence/step-4/step4-coverage-diagnostic-r5-fail-closed-20260716.md`；
  `docs/9.3.4/workitems/BUG-step4-database-state-successor-authority-manifest.md`；
- quality: formal gate=`pass`，B/H/M/L=`0/0/0/0`；
- next gate: commit/push 并证明 clean `HEAD == origin/main`，
  再执行全新 `step4-coverage-20260716-diagnostic-r6`。threshold=
  `diagnostic-pending`，`can_enter_coverage_audit=no`，Step 5 保持关闭。

### Superseding Post-diagnostic Check-in — Step 4 r6 environment fail-closed

- recorded_at: 2026-07-16
- status decision: `in-progress / r6 historical environment fail-closed / fresh r7 pending`，
  不是 Step 4 `passed`；
- immutable r6: clean/pushed tested commit=
  `eb10d9c10a73f379db9ce4fa3d05ff340b489fd4`，run=
  `step4-coverage-20260716-diagnostic-r6`，source-before=`3,974 files` / SHA-256=
  `3a4322e8442646c58ed522c0d4fb52071b3219cc1c2f204c209299bd8acc1cff`；
- partial boundary: Unit=`681 positive + 55 structural / 4,941 / F0E0S0`、Integration=
  `47 positive + 4 structural / 320 / F0E0S0`、Addon=`2/6/F0E0S0` 已完成；仅形成
  `9/23` exec。这些结果属于 r6 failed run，禁止拼接或复用；
- fail-closed boundary: Step 3 required child 的 database-state dynamic precondition 发现
  frozen MySQL 5.7 port `13306` 已被 repo demo compose project
  `foggy-dataset-demo` 的长期容器 `foggy-demo-mysql` 占用，以
  `E_DYNAMIC_PRECONDITION` 终止；outer=`failed / child-step3-required / exit 1`；database
  cells、external、aggregate、threshold、source-after 与 summary absent；
- classification: `environment-precondition / product_regression=false`。r6 已越过 r5 的
  `E_AUTHORITY_MANIFEST` 边界，successor selector remediation 未复发；runner 没有启动、
  接管、复用或改变既有 listener，run-owned cleanup residue=`0/0/0`；
- immutable artifact hashes: outer status/context/cleanup/class-universe/toolchain=
  `4029bb382ffea564ee3b8c2fc20021b0d4fd4d928a631bc94ca947019463ac10` /
  `21251c5ab1b16e401a18bd8ecbfefefb4d5059cc202d70f1d879cc60f49db332` /
  `5524f0c68afb6ff1d13358f8fa3d2d19f7c1270454f2dfed1d2fe72cfe334ff1` /
  `3aeaabd75ba0a75e99801f8b50e8a7453d512989da50f49afaddc35ba523e926` /
  `3a101e4041b8186366c1d3c834f1c15e4a4c7ef47d4ed1b40221517188a65f20`；
- records:
  `docs/9.3.4/evidence/step-4/step4-coverage-diagnostic-r6-environment-fail-closed-20260716.md`；
  `docs/9.3.4/workitems/BLOCKER-step4-r6-mysql57-port-occupation.md`；
- next gate: repo demo DB 容器已在 r7 evidence window 外停止，四个 frozen ports 均无
  listener；执行全新 `step4-coverage-20260716-diagnostic-r7`。threshold=`diagnostic-pending`，
  `can_enter_coverage_audit=no`，Step 5 保持关闭。

### Superseding Post-diagnostic Check-in — Step 4 r7 Unit hermeticity fail-closed

- recorded_at: 2026-07-16
- status decision: `in-progress / r7 historical Unit hermeticity fail-closed / formal
  remediation quality pending / fresh r8 pending`，不是 Step 4 `passed`；
- immutable r7: clean/pushed tested commit=
  `528a0a541d90ef77d577e1816b392d33168cb558`，run=
  `step4-coverage-20260716-diagnostic-r7`，source-before=`3,976 files` / SHA-256=
  `b3fc04ee0d16a7a81f5e9697b10b5edeaafec0f59cd5dbec1e65625381c3fe43`；
- fail-closed boundary: 四个 frozen ports 已无 listener；Unit `foggy-dataset` 出现
  6 suites / 11 errors，直接根因是测试隐式连接 ambient `127.0.0.1:13306`。outer=
  `failed / child-unit / exit 1`；只有不完整 `1/23` Unit exec，后续 lane、aggregate、
  threshold、source-after 与 summary absent；r7 与其产物 excluded/non-reusable；
- remediation: Unit 派生唯一 run-owned MySQL 5.7 child/project，固定 image/database/port 与
  `M_ETL_TEST` schema；原唯一 Maven invocation 作为 frozen provisioner callback；before/after
  schema seal、provisioner fixture、restricted test credential/connection receipt、cleanup/port
  seal 纳入 Unit summary 与 report inventory；
- classification boundary: remediation 替换完整 Unit lane并保持 `681+55/4,941`；
  `6 reports / 11 testcase nodes` 只是已知隐藏依赖清单，不是其他 Unit 测试无 DB 访问的
  声明。Step 2 identity/cardinality 继续保留结构含义，其 Unit 正确性绿色不复用。机器契约=
  `scripts/v934/step4/unit-mysql57-fixture-contract.json`，迁移债务=
  `docs/9.3.4/workitems/DEBT-unit-mysql57-fixture-classification-migration.md`；
- focused validation: `681 positive + 55 structural / 4,941 / F0E0S0`，schema before=after=
  `93a9a8d51c8e8188173ce905965293adbd163e2d1e21c12d2f1f8637bbe4da0d`，temporary
  residue=`0/0/0`、port free；occupied-port negative 在 preflight fail closed，外部 demo
  container exact identity/health 不变；
- static closure: overlay=`12/12`、Unit negative receipt=`27/27`（原 fixture/manifest
  schema/tamper=`20/20`、connection receipt typed=`4/4`、atomic publisher=`3/3`），negative
  receipt schema tamper=`4/4`；真实 lifecycle=`5/5`，report inventory negatives=`30/30`；
  frozen coverage contract 仍为 `23 exec / 48 sessions`、required=`773+59/5,707`；fixture
  hardening 后 top manifest=`59/59` /
  `2a52dbf591238a9c163c0774014e1407dadd4d5037a62a4ce2d0c3af931d6aa7`、successor=
  `14/14` / `bd8d1f1ef97db15b1fb08548c52c6be3fa60d82e848d5741b6a36f1f828924db`、
  coverage amendment=`12 rows / 4 new + 8 changed` /
  `998ae49927721576c26327b8477010b0238843565e6afdbc70987e97544a028c`，静态复验通过；
- records:
  `docs/9.3.4/evidence/step-4/step4-coverage-diagnostic-r7-unit-hidden-mysql-fail-closed-20260716.md`；
  `docs/9.3.4/workitems/BUG-step4-unit-hidden-mysql-environment-dependency.md`；
- next gate: 正式实现质量闸门（pending）→commit/push→clean source seal→fresh r8 all-lane
  diagnostic（pending）。
  threshold=`diagnostic-pending`，`can_enter_coverage_audit=no`，Step 5 保持关闭。

### Superseding Unit Remediation Check-in — profile isolation r2 fail-closed

- recorded_at: 2026-07-16
- status decision: `in-progress / Unit remediation r2 excluded / profile-scoped repair static
  closed / fresh Unit r3 + formal remediation quality + commit-push + fresh r8 pending`，不是
  Step 4 `passed`；
- immutable r2: run=`step4-unit-fixture-quality-20260716-r2`，tested commit=
  `a603f839a98d99b2d7beb8379f76b4d85539328c`，source-before=`3,981 files` / SHA-256=
  `087d074f3497aff3fe305806f82b4d62ff41cdd4d3b26556e58f034138b14c2c`；
- failed boundary: fixture lifecycle negatives=`5/5` 后，主 child=
  `unit-mysql57-90da4977dc197f81` 成功启动；唯一 Surefire Maven invocation 在
  `foggy-dataset-model` 得到 `3,115/F0E631S0`。全局 `-Dspring.datasource.*` 覆盖了
  SQLite profile 的 URL，却保留 `org.sqlite.JDBC`，首因是 SQLite driver 拒绝
  `jdbc:mysql`。Unit final manifest/summary、fixture after/connection receipt、source-after、
  aggregate 与 threshold absent，r2=`excluded/non-reusable`；
- cleanup/restoration: child container/volume/network=`0/0/0`、`13306=free`；demo MySQL
  exact container 已在 evidence window 外恢复为 `running/healthy`；
- minimal repair: `foggy-dataset` test resource 以
  `V934_UNIT_MYSQL57_URL/USERNAME/PASSWORD` placeholders 接收 callback 受控环境；移除所有
  global Spring datasource args，保持其他 SQLite/显式 profile。outer/callback 双层拒绝
  underscore/dotted/hyphen Spring/custom key 与 `@argfile`、`VMOptionsFile`、
  `javaagent/agentlib/agentpath` 间接注入；adapter path/hash/唯一 consumer 由 scrubbed Git
  environment、`HEAD` tree、no-replace object inventory 封存。closed Unit Maven window 从
  root 配置 `init_connect` 到 Maven 返回后同一 root batch 先 disable 再 SELECT；receipt
  保存有序 `connection_id + observed user`，窗口内全部 non-super connections 必须为
  `v934_unit`，callback 后 provisioner `foggy` 控制面在窗口外；
- static closure: Unit negatives=`36/36`（fixture/manifest=`20/20`、connection typed=`7/7`、
  publisher=`3/3`、profile isolation=`6/6`），negative receipt schema=`4/4`、真实
  lifecycle=`5/5`、report inventory=`30/30`；top=`60/60` /
  `6056a930a1d0deec59767ffc0239485ae42b4067e343c0d68e3f899c3440e587`，successor=
  `14/14` / `acb580e92a72eb407f31f5d6f9a8139a3509f3a0bfbf58537922465f4086a112`，
  diagnostic/formal contract=
  `c062219a6335ae41330c6d5924d6fce60941c5d168b361081fbb41df77428477` /
  `341991d6b5a15d19cdb9e0de70a8cc6ace29480227596c413c07e6bf7fdbc73d`，coverage tool=
  `27afd37350fa7f1646fba4be59791ec6bdec94fe57e0cdfecc2a08e0f43f2f18`，fixture
  contract/tool/runner=
  `7aa1e21aef85b51a13aacc8c134a1c363c595deffbfb3acf6aafdb942519b53a` /
  `cc19390ce6c0cfb307b7632dbe4e25540b1e4d49d11ec1512739f6724646d345` /
  `45536c0a969731f6b7c87acecdb225b13a8a0fca45a9a04c9cdfb2173fc60c66`；
  declared amendments=`18` /
  `8e21b8527f290061361ef0b8fbf084d51b2536ef479e1f70e45488f996090bfc`，overlay
  contract/tool=
  `84d09bfc333bb40d8ef830979734933717555845cebe9943f70ff7087a9a482d` /
  `1fea2816504519b7e7f1dc6839744ee943a9a4bf3feb783375e21e935da63d31`，adapter=
  `9500cd4d50930b121a36798857cd0a1cc0c8b2190b0a2fc9ad0ea464394bb256`；
- record:
  `docs/9.3.4/evidence/step-4/step4-unit-profile-isolation-r2-fail-closed-20260716.md`；
- next gate: fresh Unit r3（pending）→formal remediation quality（pending）→commit/push +
  clean HEAD（pending）→fresh r8 all-lane（pending）。静态 closure 不等于上述 gate 通过；
  `can_enter_coverage_audit=no`，Step 5 保持关闭。

### Superseding Unit Remediation Check-in — fresh r3 passed / quality passed

- recorded_at: 2026-07-17；
- run=`step4-unit-fixture-quality-20260716-r3`，tested commit=
  `50161a0a869430e353f3933d9bb00dda59d9c4b1`；source before=after=`3,982 files` /
  `1db06cc18bb86c288ffa79e7094e3b9e509d866ddc23b6c93bcaaa0422b99eb2`；
- 唯一 Surefire Maven invocation=`681 positive + 55 structural = 736 raw reports / 4,941
  testcase / F0E0S0`；fixture before=after；
- fixture negatives=`36/36`（`20+7+3+6`），negative receipt schema=`4/4`；closed
  `unit-maven-invocation` window 内 `18/18` connections（ID `21..38`）均为
  `v934_unit@172.29.0.1`；lifecycle=`5/5`；run-owned cleanup=`0/0/0` 且 evidence-window
  结束时 port free；
- evidence window 外已恢复原 demo MySQL exact container
  `0c50bf7e8684950ee1f9c3c257d3b2a8ba1ace8fbc85f2aa57fd35ff0fe5c166`，同一 ID 为
  `running/healthy`；
- independent evidence review=`PASS`，B/H/M/L=`0/0/0/0`；formal remediation
  implementation quality 在完成本次权威文档回写后=`pass`，B/H/M/L=`0/0/0/0`；
- records：
  `docs/9.3.4/evidence/step-4/step4-unit-fixture-quality-r3-pass-20260717.md`、
  `docs/9.3.4/quality/step4-diagnostic-ready-implementation-quality.md`；
- next gate：commit/push 并证明 clean `HEAD == origin/main` → 在四个 frozen ports 无
  listener 的 evidence window 执行 fresh r8 all-lane diagnostic。threshold 仍为
  `diagnostic-pending`，`can_enter_coverage_audit=no`；Step 5、formal、coverage audit 与
  acceptance 仍关闭。

### Superseding Post-diagnostic Check-in — r8 lifecycle contract drift fail-closed / r9 ready

- recorded_at: 2026-07-17；
- status decision: `in-progress / r8 bootstrap-negative excluded / lifecycle remediation
  quality passed / ready-for-commit-and-fresh-r9`，不是 Step 4 `passed`；
- immutable r8: run=`step4-coverage-20260716-diagnostic-r8`，reported launch HEAD=
  `3a3dd21a9aa956f0bedfffcf648ebb1cac0b756a`；full contract、successor overlay、authority 与
  coverage contract negatives=`20/20` 先通过；
- failure boundary: lifecycle dynamic cases 已到达 static shape validation，但旧 validator
  仍要求 Unit direct `v934_run_log_exit_trap "$?" ...` token，错误拒绝 fixture-aware
  `v934_unit_exit_trap` wrapper；outer=`failed / bootstrap-negative / exit 1`。run-owned
  Git/source seal、Unit/Integration/Addon/database/external、JaCoCo exec、aggregate、threshold
  与 summary 全 absent；r8=`excluded/non-reusable`；cleanup=`0/0/0`；
- immutable hashes: run-status/cleanup/run-log/coverage-negative/partial-lifecycle=
  `ad4c50c64a7d121fb76dc1b7dc910d94f0e1efd6990ef5bc48975aa90d5fcb75` /
  `5524f0c68afb6ff1d13358f8fa3d2d19f7c1270454f2dfed1d2fe72cfe334ff1` /
  `c231987f46a6d5e5cc29285295cd3d1e4f0e3f5e2c34921643fa977edfed3033` /
  `412011aeb5c27bf4e971f172a0ea159e4bbc1146ea0411baa0c43a1addad4943` /
  `174155e8f90a28a9c27db0ffee2b7952d2b9e4f736b57781ca019185974d7e7a`；
- remediation: Unit/Integration 使用分类型 executable physical/logical contract、critical
  slices 与 canonical lifecycle references；whole-runner raw-byte SHA-256 seal 先验 exact
  bytes 后 strict UTF-8 decode。comment/quoted heredoc、EXIT/numeric-0、early return、
  function shadow、false/subshell context、heredoc source/eval 与 CRLF drift 均 fail closed；
- verification: lifecycle focused 唯一 PASS；dynamic=`9 类 / 14 case`，Unit shape/source-seal=
  `13/13 + 3/3`，Integration=`11/11 + 5/5`；script SHA-256=
  `8dcc679c2762ff8908b3bc26e8dfb0553a083eb75003dd80366fd82e78d8ed9b`，top=
  `60/60` / `0c4e6c18f4af0a2c35418604ce80d20828ceb4c275375b7d12461b4683553a81`，successor=
  `14/14` / `acb580e92a72eb407f31f5d6f9a8139a3509f3a0bfbf58537922465f4086a112`，
  contract/overlay/bash-n/diff-check 全通过；两路独立正式质量 B/H/M/L=`0/0/0/0`；
- records:
  `docs/9.3.4/evidence/step-4/step4-coverage-diagnostic-r8-bootstrap-negative-contract-drift-fail-closed-20260717.md`；
  `docs/9.3.4/workitems/BUG-step4-unit-lifecycle-static-contract-drift.md`；
- next gate: commit/push 本轮 authoritative closure，证明 clean `HEAD == origin/main`；在
  四个 frozen ports 无 listener 的 evidence window 使用全新 run ID 执行 r9 all-lane
  diagnostic。threshold=`diagnostic-pending`，`can_enter_coverage_audit=no`；Step 5、formal、
  coverage audit 与 acceptance 保持关闭。

## Historical formal-r4 checkpoint risks / stop conditions（superseded）

- 在 formal-r4 checkpoint，Step 4 曾 accepted；r1–r19 failed/diagnostic history 永久
  immutable/excluded，当时 Step 5 不得选择、拼接或重标这些产物，只能消费 `f97483a0…`
  formal-r4 与已登记 companion。后续 replacement chain 已重新关闭 Step 5。
- Step 5 当时的风险是 single-authority orchestration、portable raw archive、candidate two-layer digest
  与 live lifecycle/durable replay 入口混淆；任一 identity/hash/replay 不一致必须 fail closed，
  final authority pointer 保持不变。
- Unit MySQL classification DEBT 仍 open；Step 5 不得把 9.3.4-only fixture 例外永久化。只有
  9.3.4 version signoff 后才由 9.3.5 Gate 0 owner 接管 migration，deadline=
  `9.3.5 version acceptance`。

## Execution check-in — formal-r8 report-runner fail-closed recovery（2026-07-19）

- Cfreeze=`7c18019ed12d25c029de7e7e49caef77a79b2e67` direct-parent/topology/push/clean
  PASS；fresh clone formal-r8=`failed / coverage-report / exit 126 / excluded`；
- before failure：Unit=`681+55/4941`、Integration=`47+4/320`、database=`29/370`、external=
  `16/76`、Step3 required=`45/446`、Addon=`2/6`、report inventory=
  `773+59/5707/F0E0S0`，child residue=`0`；
- success-only exec-manifest/aggregate/observation/gate/summary/candidate/final 全 absent；r8 raw exec
  不得复用。runner cleanup=`0/0/0`，wrapper restore=`rc0`，四个 demo DB exact ID 均
  `running/healthy`；
- cause：report runner 三处直接执行 Git `100644` Python tools；main `core.fileMode=false` 的偶然
  x bit 掩盖缺陷，fresh clone `0664` 正确拒绝；
- remediation：三处统一 `python3`；runner raw/292-command-stream seal、全部四工具/七调用 logical
  binding、raw/stream/semantic mutation=`44/44 / 43/43 / 33/33`、Git-mode mutation=`4/4`、
  nonexec smoke=`4/4`；Step4 manifest=`6a48ab01…0782`、Step6 manifest=`d1efe031…43bd`，full diagnostic
  contract/overlay/lifecycle/XML/authority/CI validation PASS；machine=
  `diagnostic-ready / diagnostic-pending`；
- code/docs reviews=`PASS / 0/0/0/0 / mandatory 0`；current：Step 4=
  `in-progress / ready-for-new-Cdiag`；next=new Cdiag
  commit/push/clean→fresh r27→
  candidate/capsule/双审→direct-child Cfreeze→fresh formal-r9→post gates `31/31`。Step 5–7、9.3.5、
  9.4.0 继续关闭。
- remote required check、five-cell collector、branch protection、release artifact reuse 与 Docker
  embedded-JAR equality 尚无实际证据，分别属于 Steps 6/7。
- v933 Batch 7 旧 FQCN/count 不能原样重跑；Step 5/7 必须继续使用 frozen predecessor mapping 与
  v934 successor regression。

上述是 formal-r4 checkpoint 的历史风险，不是当前状态；当前 authority 以文末 r26 check-in 为准。
历史 checkpoint 触发 stop condition 时保持当时 Step 未通过并记录 blocker，不得跳到 Step 6/7。

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

## Historical post-gate status after formal-r4 acceptance

This table records the 2026-07-18 checkpoint only. Its downstream authorization was withdrawn by the
formal-r6 replacement recovery recorded at the end of this document; it is not the current status.

| Gate | 状态 | 证据 |
|---|---|---|
| implementation self-check | Steps 1–4 passed | Step 4 formal-r4 exact authority + independent source/artifact/state review；version final self-check 仍在 Step 7 |
| formal implementation quality | Steps 2–4 quality complete | Step 4 record=`docs/9.3.4/quality/step4-coverage-gate-final-implementation-quality.md`，B/H/M/L=`0/0/0/1`、mandatory fixes=0 |
| coverage evidence audit | Steps 2–4 complete | Step 4 record=`docs/9.3.4/coverage/step4-coverage-gate-coverage-audit.md`；25/25 workitem，critical/major gap=`0/0` |
| feature acceptance | Steps 3–4 signed-off / accepted | Step 4 record=`docs/9.3.4/acceptance/step4-coverage-gate-acceptance.md`；只签收 feature |
| version acceptance | not-started | planned `docs/9.3.4/acceptance/version-signoff.md` |
| roadmap sync / downstream | Steps 1–4 passed / Step 5 ready | roadmap/README 已同步；Step 5=`ready / not-started`，9.3.5=`queued` |

## Historical next action after formal-r4 acceptance

Step 4 已完成 formal-r4、final quality、coverage audit 与 feature acceptance；decision=
`accepted`，25/25 workitem closed，blocking items=`none`。

At that checkpoint，next action was Step 5 single-authority rehearsal，生成 portable immutable candidate，拆分
live lifecycle validation 与 durable artifact replay，并保持 final authority pointer 不变。
At that checkpoint `can_enter_step5=yes`，Step 5=`ready / not-started`；该授权已由文末
formal-r6 recovery 重新关闭。

### Superseding Post-diagnostic Check-in — r9 exec identity scope fail-closed / r10 remediation

- recorded_at: 2026-07-17；
- immutable run：`step4-coverage-20260717-diagnostic-r9`，tested commit=
  `a0466ec04c51c436413e85836a7dee6153e18010`；outer=`failed / coverage-report / exit 1`；
  source-before=
  `3e07c3de0a7c804d2aa4c36b64bfb0c8c2f2d5910f54c335376bbe206c8b49b4`；
- completed boundary：Unit=`681+55/4,941/F0E0S0`、Integration=`47+4/320/F0E0S0`、
  database=`29/370`、external=`16/76`、Step 3 required=`45/446`、Addon=`2/6`、Step 4
  inventory=`773+59/5,707/F0E0S0`、exec inputs=`23/48`；
- failure：旧 exec verifier 以 binary name 折叠 all-loaded classes 并要求同名单 ID；r9
  read-only analysis=`16,693 unique names / 16,939 JaCoCo IDs / 135 same-name multi-ID /
  2,098 frozen production classes / 0 production conflict`；
- absence：`exec-manifest.json`、source-after、aggregate/provenance/XML、observation、threshold
  candidate、summary 均 absent；cleanup=`0/0/0`；r9 excluded/non-reusable；四个 exact demo
  DB container 已在 evidence window 外恢复 healthy；
- immutable hashes：run-status/cleanup/run-log/report-inventory/child-lifecycle/toolchain=
  `7c50cafd8f28716db7ad996b31204934eaa6ae04804eb455bc442bb7e72070de` /
  `5524f0c68afb6ff1d13358f8fa3d2d19f7c1270454f2dfed1d2fe72cfe334ff1` /
  `765fb22f81ef2e4c2aca7eaad899e365f6cbb098d5a0d87e61b5df05d90a928c` /
  `13dd63dcd49fc0eec445ca6b558c13b841da9ecb97a7a9ad089a8c994b8074d0` /
  `43a4046ef35bcb0c5b6bf02ed6db47a99dfc23103358652ae66269e3fce2b064` /
  `82ba200a5ffa1a14bacb38aa75c0cb99c0d740b150db2824f23d4becbdd8acb8`；
- exec remediation：contract scope 冻结到 24-module production universe；raw/aggregate
  保留全部 class ID，aggregate 以 ID 做 exact shape/bitmap union；focused exec=`17/17`、
  contract=`21/21`、XML identity/provenance=`68/68`、overlay=`12/12`；
- implementation quality remediation：独立审计确认 outer/library raw comment/dead-context 与
  Unit/Integration dynamic `t$''rap` 等可绕过 semantic validator；现有 8 个 source-seal
  negative 未触达声称 guard。已登记
  `BUG-step4-lifecycle-semantic-validator-bypass.md`；stable-code/executable-stream 修复现已
  通过 Unit/Integration shape=`16/16 + 14/14`、semantic stream=`2/2 + 5/5`、raw
  seal=`2/2`、outer/library=`3/3 + 3/3`；三路独立复核最终 B/H/M/L=`0/0/0/0`；
- records：
  `docs/9.3.4/evidence/step-4/step4-coverage-diagnostic-r9-exec-class-scope-fail-closed-20260717.md`、
  `docs/9.3.4/workitems/BUG-step4-exec-class-id-scope-drift.md`、
  `docs/9.3.4/workitems/BUG-step4-lifecycle-semantic-validator-bypass.md`；
- next gate：commit/push/clean HEAD → fresh r10 diagnostic。r10 成功前不得 threshold freeze；
  threshold=`diagnostic-pending`、`can_enter_coverage_audit=no`，Step 5/formal/audit/
  acceptance 均关闭。

### Superseding Post-diagnostic Check-in — r10 sensitive-scan fail-closed / r11 remediation

- recorded_at: 2026-07-17；
- immutable run：`step4-coverage-20260717-diagnostic-r10`，tested commit=
  `47e0c027cd205a49d40db400ba26b99e6f97d60e`；outer=
  `failed / sensitive-scan / exit 1`；source-before=after=
  `7c60f5ce6a7174487d77ea64881be37d68408129bf3c570972918a818a04a000`；
- completed boundary：Unit=`681+55/4,941/F0E0S0`、Integration=`47+4/320/F0E0S0`、
  database=`29/370`、external=`16/76`、Step 3 required=`45/446`、Addon=`2/6`、Step 4
  inventory=`773+59/5,707/F0E0S0`、exec=`23/48/16,947 class IDs`；aggregate line=
  `54,478/76,830`、branch=`25,980/44,870`，thresholds_frozen_by_observe=`false`；
- failure/absence：`run.log` 唯一脱敏命中来自 demo identity producer 的 credential-shaped
  authorization label；match SHA-256=
  `d375d94172c0dbded90d08b61f8425e5bb8ed28d8b43141ef3e7cacc80d06c59`；
  `sensitive-scan.env`、`summary.env`、gate/candidate absent，r10 excluded/non-reusable；
- cleanup/restoration：run-owned container/volume/network=`0/0/0`；四个 exact demo DB
  containers 已在 evidence window 外恢复原 ID 且 running/healthy；
- remediation：五条扫描规则不变；producer 改为明确的 demo identity result wording；outer
  在 bootstrap-negative 最前以同一 pattern 数组执行 `7 dangerous + 3 safe` 内存 probe，
  `rg rc>1` fail closed，fixture 不进入 run root/log；launcher request smoke=`1/F0E0S0`；
  top manifest=`60/60`，contract=`21/21`、overlay=`12/12` focused validation 通过；
- records：
  `docs/9.3.4/evidence/step-4/step4-coverage-diagnostic-r10-sensitive-scan-fail-closed-20260717.md`、
  `docs/9.3.4/workitems/BUG-step4-sensitive-scan-authorization-context-false-positive.md`；
- next gate：commit/push/clean HEAD → fresh r11 diagnostic。
  r11 成功前不得 threshold freeze；threshold=`diagnostic-pending`、
  `can_enter_coverage_audit=no`，Step 5/formal/audit/acceptance 均关闭。

### Superseding Post-diagnostic Check-in — r11 outer source-seal fail-closed / r12 remediation

- recorded_at: 2026-07-17；
- immutable run：`step4-coverage-20260717-diagnostic-r11`，reported clean/pushed launch
  HEAD=`141592ca9f4219d87a018774ee607b09a8e5a8a1`；outer=
  `failed / bootstrap-negative / exit 1`，stable code=`E_SOURCE_SEAL`；
- completed boundary：full coverage contract、successor overlay、sensitive bootstrap probe、
  authority 与 coverage contract negatives 在 failure 前通过；lifecycle suite 未发布最终
  PASS。run-owned Git/source seal 未建立，Unit/Integration/database/external/Addon 与全部
  test lanes=`0`；exec、aggregate、observation、sensitive receipt、summary absent；
- mismatch：historical outer actual raw=
  `57f5da9a23c4973beef54a6bfd303c3dfd38fccb03a7bc2cadadbfaa3206f649`，nested frozen raw=
  `02a920d91d1b8792cad47d65ce860352a8e9ecf39106f4489a714df01888dbaa`；r11=
  `excluded/non-reusable`，前置绿色不得拼接；
- cleanup/restoration：run-owned container/volume/network=`0/0/0`；四个 exact demo DB
  containers 已在 evidence window 外恢复原 ID，running/healthy=`4/4`、listener=`4/4`；
- remediation：在 run-root/source-seal/lane 前做 Unit、Integration、outer、library early
  four-way binding；成功=`1`，六类 stable `E_SOURCE_SEAL_BINDING` negatives 为
  outer+manifest refresh/nested stale、outer-only drift、valid-64 nested-only wrong、missing、
  duplicate、invalid-format constant；保留 raw CRLF 与 executable no-op 双层 source seal；
- current identities：outer raw/semantic=
  `90b4b979e55c17243644cce186767a4647ce79c85b431adcb415bddd18cc1cec` /
  `065211912aab5227125ef02f40e2965fce7ff5060df5c7b91a902c4ad4f34cae`；lifecycle tool/top
  manifest=
  `61bf7b990bdef6e0d75c53010644bcc6d1525a67119cd36c5f82eeb911e005fc` /
  `a9b105ce2f8f640dfa09863e797697bcf9892a7b0fa68b38f83b5bbd7435afb4`；
- security review：preflight path-check/read TOCTOU Medium 已用 descriptor-bound strict read
  （`O_NOFOLLOW`、`fstat`、fd read、post-`lstat` stable identity）关闭；stable error 不变，
  两路 post-fix implementation review 与独立 docs/status review B/H/M/L=`0/0/0/0`；
- focused validation：lifecycle suite=`PASS`、manifest=`60/60`、successor=`14/14`、coverage
  contract=`21/21`、overlay=`12/12`；这些不是 r12 或 Step 4 exit evidence；
- records：
  `docs/9.3.4/evidence/step-4/step4-coverage-diagnostic-r11-outer-source-seal-fail-closed-20260717.md`、
  `docs/9.3.4/workitems/BUG-step4-outer-runner-source-seal-binding-drift.md`；
- next gate：commit/push/clean `HEAD == origin/main` → fresh
  `step4-coverage-20260717-diagnostic-r12`。r12 成功前 threshold freeze、formal coverage run、
  Step 5、coverage audit 与 acceptance 均关闭；`can_enter_coverage_audit=no`。

### Superseding Post-diagnostic Check-in — r12 observed / r13 remediation

- recorded_at: 2026-07-17；
- immutable run：`step4-coverage-20260717-diagnostic-r12`，tested commit=
  `05351ecab0d7fc43d12dfa307ffecf81feb41539`，outer=`diagnostic-observed / completed / exit 0`；
- authority result：required=`773+59/5,707/F0E0S0`、Addon=`2/6`、exec=
  `23/48/16,935`、aggregate line=`54,478/76,830`、branch=`25,980/44,870`、sensitive=
  passed、cleanup=`0/0/0`；source-before=after；四个 exact demo DB containers 已恢复
  running/healthy=`4/4`；
- gap：critical=`12`，below floor=`9`，structural N/A=`1`；旧 freeze consumer 对真实 enriched
  metric shape 返回 schema 错误，candidate absent，threshold 仍为 `diagnostic-pending`；
- remediation：八份既有 test source 覆盖九个 critical class，report/testcase cardinality不变；
  focused=`136/F0E0S0`；唯一 N/A machine tuple、真实 row/metric exact schema、strict JSON
  identity、frozen raw-exec replay receipt 与 formal replay call binding 已实现；
- precommit review：首轮发现 bool/int、float/int 与 `gap:false` aliases 和 frozen replay
  contract 缺口，均已回补；当前 focused XML=`118/118`、contract=`27/27`、threshold/frozen
  replay policy=`12/12`、overlay=`12/12`、top=`60/60`、successor=`14/14`；
- quality cross-review 另登记
  `BUG-934-STEP4-EVIDENCE-JSON-NUMERIC-TYPE-ALIAS-BYPASS`：child lifecycle、provenance
  size、threshold schema 与 aggregate ratio 已改为 exact typed consumption，新增 9 项负例后
  XML=`118/118`；post-fix full-closure 与正式 quality re-review 均为 B/H/M/L=`0/0/0/0`；
- next gate：commit/push Cdiag → clean
  `HEAD == origin/main` → 停止 exact DB containers/确认 ports free → fresh
  `step4-coverage-20260717-diagnostic-r13` → restore exact IDs。r13 通过前不得 Cfreeze；
  `can_enter_coverage_audit=no`，Step 5/formal/audit/acceptance 关闭。

### Historical Post-diagnostic Check-in — r13 sealed / Cfreeze formal-ready

- recorded_at: 2026-07-17；
- immutable run：`step4-coverage-20260717-diagnostic-r13`，tested Cdiag commit=
  `b76552e21479c75111f648a4aa678abe018cc3f9`，outer=
  `diagnostic-observed / completed / exit 0`；
- authority result：required=`773+59/5,707/F0E0S0`、Addon=`2/6`、exec=`23/48`；critical
  below-floor=`0`，structural N/A=`1` 且仅为 `NamespaceScope.branch`；sensitive scan=
  `passed`、cleanup=`0/0/0`；
- threshold freeze candidate：public verification=`passed`，SHA-256=
  `8bb47382444fd66893d250a8787416c9ce73f9590be4c66308fb7a2e3e014d00`；candidate 不是
  confirmed threshold、formal final 或 Step 4 exit；
- independent review：SHA-256=
  `2ab3dc50ed15399c07c1281c70961bf56593eae925727e5cc357bb448e737d8e`；canonical
  threshold=`confirmed`，SHA-256=
  `0cfc6765eda1aa8a5209e46bf668136ee1786c4761d66a07262ac3557e7227cb`；contract/publication=
  `formal-ready`，SHA-256=
  `6b5e03002ab10bb921d6cb06a4ff3472f2b0605524da6f0f9dc65452a8a21160`；
- status decision at r13 snapshot：Step 4=`in-progress`，Cfreeze=`formal-ready`、formal=`pending`、
  `can_enter_coverage_audit=no`、`can_enter_acceptance=no`；Step 5、audit、acceptance 与 9.3.5
  保持关闭；
- next gate at r13 snapshot：Cdiag direct-single-parent Cfreeze commit/push → direct-parent delta → clean
  `HEAD == origin/main` → fresh formal → final implementation quality → coverage audit →
  acceptance。任一步失败继续 fail closed。

### Superseding Check-in — formal-r1 fail-closed / new diagnostic generation

- recorded_at: 2026-07-17；
- immutable failed run：`step4-coverage-20260717-formal-r1`，tested commit=
  `86d505810524383da6211bcc2a7965e9a4afb34e`，status=
  `failed / formal-coverage-gate / exit 1`；summary/coverage-gate/candidate/final absent；
- completed lanes：required=`773+59/5,707/F0E0S0`、Addon=`2/6`、exec=`23/48`、class
  universe=`24/2098`、DB state=`18/18`、Redis state=`4/4`；cleanup=`0/0/0`、sensitive passed；
- failure isolation：only `WatchServiceFileTracer` changed, line `204/244 -> 195/244`, branch
  `98/128 -> 95/128`; aggregate exact `-9/-3`; only `jacoco-ut.exec` differs；
- root cause：tracer/JaCoCo JVM shutdown hooks concurrently race；r13 recorded incidental exit
  coverage，formal-r1 correctly rejected it；
- remediation：existing testcase 内反射 isolated tracer 并显式 shutdown；5/5 independent
  forks=`177/245 probes`、target7 all covered、bitmap unique=`1`、test report=`11/F0E0S0`，
  singleton 保持 available；
- machine recovery：contract=`diagnostic-ready` SHA `15dae282...0b0b`，threshold=
  `diagnostic-pending` SHA `0df17a87...ff96`，manifest SHA `cc356897...dc60` / `60/60`；
- decision：formal-r1 immutable，不重用/修补/盲重跑。pre-Cdiag quality 已 PASS；Step 4=
  `in-progress`，下一门为新 Cdiag commit/push/clean identity；`can_enter_coverage_audit=no`、
  `can_enter_acceptance=no`，Step 5、formal、audit、acceptance 关闭。

### Historical Check-in — r14 sealed / second Cfreeze formal-ready working tree

- recorded_at: 2026-07-17；
- new Cdiag：`322bb346cca19998a90d6d990505ef033f3a496a`，已 commit/push，run 前
  `HEAD == origin/main` clean；
- fresh diagnostic：`step4-coverage-20260717-diagnostic-r14`，outer=
  `diagnostic-observed / completed / exit 0`，source before=after=
  `04e485088ae233c388cd0fd0bd9190d347e3b2a4d65faac238a1678147ec9d81`；
- authority result：required=`773+59/5,707/F0E0S0`、Addon=`2/6`、exec=`23/48/16,956`、
  class universe=`24/2098`、aggregate line=`54,622/76,830`、branch=`26,106/44,870`；
  critical below-floor=`0`、unique N/A=`NamespaceScope.branch`、sensitive=`passed`、cleanup=`0/0/0`；
- WatchService：r14=`204/244 line, 99/128 branch`，相对 formal-r1 `+9/+4`，unit bitmap
  包含 formal-r1 缺失 7 probes；5/5 focused identical bitmap remains a subset；
- candidate：SHA-256=`9087774387f0bb4b177a1b5f2fe28a4102e0434afe7a5b316aa106511c9e6d55`，
  public validator and independent exact projection PASS；two reviews B/H/M/L=`0/0/0/0` and
  `0/0/0/1`；Low is non-critical PostgreSQL Pivot probe variance，not a freeze blocker；
- machine transition in working tree：threshold=`confirmed` SHA-256=
  `04544480ef73df4bfcba4ddb1d0323b8314fbb4a6934eae5eae51bb2a958486e`，contract/publication=
  `formal-ready` SHA-256=`6b5e03002ab10bb921d6cb06a4ff3472f2b0605524da6f0f9dc65452a8a21160`，
  Step 4 manifest SHA-256=`915bf603c2cb04766143d73f0a2e81ab1a30863506fc194169d07dc06db173e3` / `60/60`；
- validators：full contract=`formal/confirmed/passed`，frozen diagnostic receipt=
  `3ab047bd45c5f1f82712d49db50a872512ac9a0c325d79031757bb0b595a99ce`，overlay=`passed`；
- next gate：single Cfreeze commit directly on `322bb346…` → push/direct-parent/formal-delta/clean
  identity → fresh formal。`can_enter_coverage_audit=no`、`can_enter_acceptance=no`；Step 5、audit、
  acceptance closed。

### Superseding Check-in — formal-r2 fail-closed / ListPreset deterministic recovery

- recorded_at: 2026-07-17；
- Cfreeze：`1901a10138bac06a09b875c907b7aea6e2789b04`，direct parent=
  `322bb346cca19998a90d6d990505ef033f3a496a`，已 commit/push/clean；
- immutable failed run：`step4-coverage-20260717-formal-r2`，status=
  `failed / formal-coverage-gate / exit 1`；source before=after；summary/gate absent，cleanup=
  `0/0/0`、sensitive PASS；
- completed lanes：required=`773+59/5,707/F0E0S0`、Addon=`2/6`、DB=`29/370`、external=
  `16/76`、exec=`23/48`、class universe=`24/2098`；
- exact failure：aggregate line=`54622/76830` exact，branch=`26105/44870` versus threshold
  `26106/44870`；12 critical exact、below-floor=`0`、N/A=`NamespaceScope.branch`；
- root cause：only `FileSystemListPresetStore` branch `18/26 -> 17/26`；Unit class ID=
  `d1bd017e92baa090` only missing probe 106；`Files.find(...).findFirst()` 在 UUID 文件上按
  未定义目录顺序短路；
- remediation：existing `shouldIsolatePresetByUserAndBusinessKey` 增加 missing-ID empty assertion；
  no production/no new testcase；focused 5/5 probe106 hit、bitmap unique=`1`，module=
  `104/F0E0S0`、target class restored r14 exact `74/113`；
- machine recovery：contract=`diagnostic-ready`、threshold=`diagnostic-pending`、manifest=`60/60`，
  validators PASS；
- decision：formal-r2 immutable；formal-r2 recovery quality -> one new Cdiag commit/push/clean ->
  fresh diagnostic -> review/Cfreeze/formal。`can_enter_coverage_audit=no`、
  `can_enter_acceptance=no`；Step 5、audit、acceptance、9.3.5 closed。

### Superseding Check-in — diagnostic-r15 Unit timing-oracle fail-closed / new Cdiag pending

- recorded_at: 2026-07-17；prior recovery Cdiag=
  `9270d2d4e58684226aeb15eff55b027e6aa4a7eb`，parent=`1901a101…`，已 commit/push/clean；
- immutable failed run：`step4-coverage-20260717-diagnostic-r15`，status=
  `failed / child-unit / exit 1`；source-before sealed、source-after absent、cleanup=`0/0/0`；
- partial boundary：26 XML reports=`124/F1E0S0`，`jacoco-ut.exec` 仅 `2/48 sessions`；final
  sensitive scan、report/exec inventories、aggregate、observation、candidate、summary/gate absent；
- exact failure：`Bean2MapUtilsTest#testCachingMechanism` first/second/third=
  `26,839/304,859/8,517ns`，second 超过 first*3；同类 static cache 已在方法前预热，三个采样
  均为 cache hit，故为环境敏感 test oracle，不是 product regression；
- remediation：三个同类、不同 source 实例使用不同 name/age 并逐 target 精确断言；1000-copy test 保留循环和首末
  correctness，删除墙钟阈值；无 production/API/POM/runner/floor/testcase-cardinality 变化；
- verification：10 个 fresh JVM focused=`10/10`，class=`23/F0E0S0`，module=`27/F0E0S0`；
- quality：formal pre-Cdiag PASS，B/H/M/L=`0/0/0/0`；三路独立复验均 PASS；
- machine：contract=`diagnostic-ready`、threshold=`diagnostic-pending`、manifest=`60/60`；
- decision：r15 immutable；one new Cdiag commit/push/clean -> fresh
  diagnostic -> review/Cfreeze/formal。`can_enter_coverage_audit=no`、`can_enter_acceptance=no`；
  Step 5、audit、acceptance、9.3.5 closed。

### Superseding Check-in — diagnostic-r16 PASS / reviewed Cfreeze working tree

- recorded_at: 2026-07-17；superseding Cdiag=
  `f863c672029d5d1e5a4903df74cf6cba22a04a85`，已 commit/push；
- immutable run：`step4-coverage-20260717-diagnostic-r16`，status=
  `diagnostic-observed / completed / exit 0`；required=`773+59/5707/F0E0S0`、
  exec/session/identity=`23/48/16948`，aggregate line=`54624/76830`、branch=`26111/44870`；
- critical：12/12 全部达标、below-floor=`0`；唯一 structural N/A=
  `NamespaceScope.branch=0/0`；
- threshold evidence：candidate SHA-256=
  `2160ef2e16fad161b91c8e3d2571a91a6a8142ae84e06d4539ec69e976563919` 已两路独立复算；
  review SHA-256=`88b99e76e5584d3cd17bcdcffd138f1fe6655ce0b7795d3430d2f15a018c8fb3`，
  B/H/M/L=`0/0/0/1`；
- machine：canonical threshold=`confirmed` / SHA-256=
  `ca6a25c66fbbe9a595adde74f1b7589bd3829b93edebfd5b11dc394ab8d088c8`；contract=
  `formal-ready` / SHA-256=
  `6b5e03002ab10bb921d6cb06a4ff3472f2b0605524da6f0f9dc65452a8a21160`；
- quality：pre-Cfreeze PASS，B/H/M/L=`0/0/0/1`，只授权一次 direct-child Cfreeze 与
  fresh formal；
- Low：fresh formal 必须完整复现 r16 aggregate 高水位，禁止降低 threshold；
- decision：Cfreeze 尚未 commit/push，fresh formal 尚未运行；next=
  direct-child commit/push -> formal-delta/clean identity -> one fresh formal。
  Step 4、coverage audit、acceptance 均未签收，`can_enter_coverage_audit=no`、
  `can_enter_acceptance=no`；Step 5 与 9.3.5 closed。

### Superseding Check-in — formal-r3 fail-closed / deterministic QueryModel recovery

- recorded_at: 2026-07-17；tested Cfreeze=
  `a63c82c53ebaad1a1c22d78647fbda70b4bd6594`，parent=
  `f863c672029d5d1e5a4903df74cf6cba22a04a85`，已 commit/push/clean；
- immutable run：`step4-coverage-20260717-formal-r3`，status=
  `failed / formal-coverage-gate / exit 1`；required=`773+59/5707/F0E0S0`、Addon=`2/6`、
  exec/session=`23/48`、cleanup=`0/0/0`、sensitive=`passed`；
- exact gate：aggregate line=`54624/76830` exact，branch=`26110/44870`，低于 reviewed
  exact `26111/44870` 一个 outcome；success-only summary/gate/candidate/final absent；
- localization：r16/formal-r3 只有
  `QueryModelSupport#getMergedJoinGraph` line 316 inner double-check branch 不同；r16=
  `0 missed/2 covered`，formal-r3=`1 missed/1 covered`；root cause 是测试偶然调度，
  不是 product regression 或 report/exec/class-universe drift；
- deterministic remediation：既有
  `RuntimeNamedDataSourceResolverBindingTest#publicationGuardSerializesTheCallbackWithAConcurrentRebind`
  使用受控 QueryModel first-build 窗口，确认 second caller 在 exact support monitor
  上 `BLOCKED`后释放，断言 single build/same graph；无新/改名 `@Test`，
  targeted/overlay 与 5/5 fresh Maven/JVM PASS；QueryModelSupport class id=
  `d242dafe9de31249`、probes=`34/629`、packed bitmap=
  `4P-_7xsAAIADAgAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAEA`、
  unique=`1`，Surefire=`1/F0E0S0`；`foggy-runtime-api` full module=
  `128/F0E0S0`，`RuntimeNamedDataSourceResolverBindingTest=5/F0E0S0`；pre-Cdiag quality=
  `PASS / 0/0/0/0`，machine/contract/overlay/negative suites 通过；record=
  `docs/9.3.4/quality/step4-formal-r3-recovery-implementation-quality.md`；
- machine reset：contract=`diagnostic-ready` / SHA-256=`15dae282…`，threshold=
  `diagnostic-pending` / SHA-256=`0df17a87…`，manifest SHA-256=`cc356897…` /
  `60/60`；
- decision：formal-r3 immutable，禁止 rerun 或降阈。next=one new Cdiag commit/push/clean ->
  fresh diagnostic -> candidate/review -> direct-child Cfreeze ->
  fresh formal -> final quality -> coverage audit -> acceptance。当前 Step 4 `in-progress`，
  `can_enter_coverage_audit=no`、`can_enter_acceptance=no`，Step 5/9.3.5 closed。

### Superseding Check-in — diagnostic-r17 Unit final-mysqld handoff fail-closed / replacement Cdiag pending

- recorded_at: 2026-07-17；tested Cdiag=
  `316a71f753827f8f34063b0eb0669271f696c5ee`，已 commit/push/clean；
- immutable run：`step4-coverage-20260717-diagnostic-r17`，outer=
  `failed / child-unit / exit 1`，Unit=`failed / unit-mysql57-lifecycle-negative / exit 1`；
  third HUP child 在 `fixture-first-apply`、callback ready 前退出；
- absence boundary：canonical lifecycle receipt、normal fixture manifest/negative、Unit XML/exec、
  source-after、sensitive/model/inventory/aggregate/observation/summary、candidate/final 全 absent；
  cleanup JSON 只证明 EXIT cleanup，不是 lifecycle `5/5` 或 Unit PASS；r17=
  `excluded/non-reusable`，也不能证明 QueryModel all-lane remediation；
- root cause：runtime RED 捕获 MySQL57 samples `15..40` 为
  `healthy / PID1=docker-entrypoi`，sample `41` 才 final `mysqld`；watermark 可见早于 final-server
  handoff。authority Compose remediation 对 MySQL57/8 同时要求 final PID1 与 ping；两库 runtime
  GREEN 均为 first healthy=`mysqld`、premature=`0`；
- focused verification：旧修复字节三个唯一 lifecycle run=`15/15`，receipt SHA-256=
  `8a3900678a718a2df5b604854ad43a5622273128b94f18413ddc6a5979fdf5f2`、
  `eb41ee0675c6c2677de708390125e1095178772e4849aece332be3eb89bb6da4`、
  `dca7439a316899c064f231411ee4a48e052c0e1b1f28d25da5c8041ca8ddd48a`；penultimate
  diagnostics 字节 `5/5` receipt=
  `159fbe80595933e29f05f13c0f1d82e9b65d7f947dddec253477f0b3f3876799`，successful logs absent、
  residue=`0/0/0`、demo restore=`0/0`；latest callback diagnostics 字节 r2 再以 `5/5` PASS，
  receipt=`e3bad41ad9ec634c1702ffe20f4c0ddbff3050a227956e2ba9054a11f6b606c7`，successful logs
  absent，demo exact restore=`runner_rc=0 / restore_rc=0` 且 healthy/listening；
- static：overlay=`12/12` / `cf98e3306fcf5650ca4aad9475beb0e986b5363d15e4f3d326568753edd92e06`；
  Unit fixture=`36/36` / `a5620aa80ac122a9489b14f8fc5352bf685c61e2fcd2426fdadfd36fb882212d`；
  coverage=`27 + source/Git 22 + replay 12` / current r2
  `0f8f5c7bbd6b8fcf18363f979b9948bd396b39b436f94b30c1ca697204fc6856`；
- formal quality：pre-Cdiag `PASS / 0/0/0/0`，record=
  `docs/9.3.4/quality/step4-diagnostic-r17-recovery-implementation-quality.md`；只授权 replacement
  Cdiag/fresh diagnostic，不授权 full Unit、Cfreeze、audit 或 acceptance 结论；
- machine=`diagnostic-ready/diagnostic-pending`；full Unit 尚无 replacement authority PASS。next=
  replacement Cdiag commit/push/clean -> fresh
  diagnostic。Step 4/5、final quality、coverage audit 与 acceptance 全部保持关闭。

### Superseding Check-in — diagnostic-r18 PASS / governed high-water gap / deterministic Pivot oracle

- recorded_at: 2026-07-17；tested Cdiag=
  `5be1edaa16c5883cde2f66396ac26a1ae113430b`，已 commit/push/clean；
- immutable run：`step4-coverage-20260717-diagnostic-r18`，status=
  `diagnostic-observed / completed / exit 0`；public validator=`VALID`，observation=
  `7a02bfaaec7d6d1afeac4a5cff20c708fb8ff1092185c25a31ac588b6845dd76`；
- lane/result inventory：Unit=`681 positive + 55 structural / 4,941 / F0E0S0`；required=
  `773 positive + 59 structural / 5,707 / F0E0S0`；Addon=`2/6`；Step 3 required=
  `45/446`，其中 database=`29/370`、external=`16 reports / 76 nodes`；coverage=
  `23 exec / 48 sessions / 16,940 classes`，production universe=`24 modules / 2,098 classes`；
- gates：aggregate line=`54,622/76,830`、branch=`26,107/44,870`；12 个 critical class
  below-floor=`0`，model gate=`PASS`；exec negatives=`17/17`、XML negatives=`9/9`、
  cleanup=`0/0/0`；source before=after=`63fdcfb…`；outer exact restore=
  `runner_rc=0/restore_rc=0`，但它是 canonical evidence window 外观察；
- governed high-water decision：reviewed r16 aggregate=line `54,624/76,830`、branch
  `26,111/44,870`，故 r18 delta=`-2 line / -4 branch`。r18 仍是有效 diagnostic PASS，
  但 decision=`threshold-candidate-not-authorized`；candidate 必须 absent，禁止降低 threshold；
- localization：`BaselineRatioCalculator=-2 line/-3 branch`、`ResultShaper=-1 branch`；
  production/test/class shape 不变，差异仅来自 PostgreSQL exec bitmap 偶发性。governed record=
  `docs/9.3.4/evidence/step-4/step4-coverage-diagnostic-r18-governed-high-water-gap-20260717.md`，
  workitem=`docs/9.3.4/workitems/BUG-step4-pivot-null-axis-coverage-oracle.md`；
- deterministic remediation：当前 working tree 已在既有 `PivotSqlParityIT` S12 增加 null-axis
  semantic oracle，无新/改名 `@Test`、无 production 变更；三次 fresh JVM/JaCoCo focused 均
  `1/F0E0S0`，`BaselineRatioCalculator=79/131 probes`、`ResultShaper=45/139 probes` 的 bitmap
  均 `3/3 identical`；完整 `PivotSqlParityIT=23/F0E0S0`；successor/top manifest=
  `14/14 + 60/60`，database/required/overlay/coverage validators 全部 PASS；
- pre-Cdiag implementation quality=`PASS / 0/0/0/0`，record=
  `docs/9.3.4/quality/step4-diagnostic-r18-pivot-null-axis-implementation-quality.md`；machine=
  `diagnostic-ready/diagnostic-pending`；next=replacement Cdiag commit/push/clean -> fresh r19
  diagnostic。当前不得创建 candidate/Cfreeze 或执行 formal、
  final quality、coverage audit、acceptance；Step 4=`in-progress`，Step 5/9.3.5 closed，
  `can_enter_coverage_audit=no`、`can_enter_acceptance=no`。

### Superseding Check-in — diagnostic-r19 PASS / candidate reviewed / Cfreeze worktree formal-ready

- recorded_at: 2026-07-17；tested Cdiag=
  `613b11a0ae6732f865f918551cd9116079771b5e`，已 commit/push/clean；run=
  `step4-coverage-20260717-diagnostic-r19`，status=`diagnostic-observed / completed / exit 0`；
- lane/result：Unit=`681+55/4941/F0E0S0`、Integration=`47+4/320/F0E0S0`、required=
  `773+59/5707/F0E0S0`、Addon=`2/6`、database/external=`29/370 + 16/76 = 45/446`；
- execution/provenance：`23 exec / 48 sessions / 16,931 class identities`，production universe=
  `24/2,098`，class tree=`a72007a8…99be`；source before=after=`a4ccff29…38f65`，outer restore=
  `0/0`，cleanup=`0/0/0`，model/sensitive gates PASS；
- coverage：line=`54,624/76,830`、branch=`26,111/44,870`，与 r16 reviewed high-water exact；
  critical=`12`、positive metrics=`23`、below-floor=`0`、唯一 N/A=`NamespaceScope.branch`；
- candidate=`6588e30bd6e51aa27bcab06cf633cc51cefa2d5a27824eee5c003bcbe9f545b8`，public verify
  与 `/root/r19_threshold_metric_review`、`/root/r19_threshold_binding_review` 两路独立审查 PASS；
- canonical working tree：threshold=`confirmed`、contract/publication=`formal-ready`，frozen
  diagnostic replay PASS；pre-Cfreeze implementation quality=`PASS / 0/0/0/0`；
- next=direct-child Cfreeze commit -> formalization-delta -> push/clean -> one fresh formal。
  Step 4 仍 `in-progress`；final quality、coverage audit、acceptance、Step 5/9.3.5 closed，
  `can_enter_coverage_audit=no`、`can_enter_acceptance=no`。

### Superseding Check-in — Cfreeze committed / formal-r4 PASS / final quality PASS

- recorded_at: 2026-07-18；Cfreeze=
  `f97483a0b87a82734d21888e7b5bea74b0c5fe55`，direct parent=
  `613b11a0ae6732f865f918551cd9116079771b5e`；`HEAD == origin/main`、clean、single parent；
  formalization delta=`passed / formal / 17 paths`；
- immutable run=`step4-coverage-20260717-formal-r4`，window=
  `2026-07-17T15:09:20Z..16:16:14Z`，status=
  `formal-passed / completed / exit 0`；public final artifact verifier=
  `ARTIFACT VALID stage=final`；
- lane/result：Unit=`681+55/4941/F0E0S0`、Integration=`47+4/320/F0E0S0`、required=
  `773+59/5707/F0E0S0`、Addon=`2/6`、database/external=`29/370 + 16/76 = 45/446`；
- execution/provenance：`23 exec / 48 sessions / 16,953 class identities`，production universe=
  `24/2,098`，class tree=`a72007a8…99be`；source before=after=`96e2c871…c3a3`；
- coverage gate：line=`54,624/76,830`、branch=`26,111/44,870`，均 exact 达到 confirmed
  minimum；critical=`12/23/below0`，唯一 N/A=`NamespaceScope.branch`；model/sensitive PASS；
- lifecycle/cleanup：三个 child 均 reaped、PG residue=0；Unit fixture negatives=`36/36`、
  lifecycle=`5/5`；runner residue=`0/0/0`；四个 evidence-window 外 demo DB exact ID 已恢复为
  `running/healthy`；
- persistent evidence=
  `docs/9.3.4/evidence/step-4/step4-coverage-exit-20260717.md`；formal gate 已通过；
  post-formal implementation quality=
  `docs/9.3.4/quality/step4-coverage-gate-final-implementation-quality.md`，decision=
  `ready-for-coverage-audit`，B/H/M/L=`0/0/0/1`、mandatory fixes=`0`；
- 外层 restore 后单独 live 重放 `report_inventory_tool validate` 会因 Unit fixture 的全局
  `13306 free` 动态前置条件返回 `E_UNIT_FIXTURE`；运行内 validate 与 post-run public final
  verifier 均 PASS。正式质量闸门将其定为非阻断 Low，owner=Step 5 single authority/rehearsal；
- At that checkpoint：Step 4=`in-progress / formal-passed / final-quality-passed`，
  `can_enter_coverage_audit=yes`、`can_enter_acceptance=no`；Step 5/9.3.5 closed。

### Superseding Check-in — Step 4 coverage audit / feature acceptance complete

- recorded_at: 2026-07-18；coverage audit=
  `docs/9.3.4/coverage/step4-coverage-gate-coverage-audit.md`；workitem matrix=
  `25 covered / 0 partial / 0 not-covered`，critical/major gap=`0/0`；
- audit 中发现 formal 五库未覆盖 Pivot legacy fallback；同一 tested HEAD/source 的 current-source
  companion 已补跑 `1/F0E0S0`，XML/source SHA 与 formal source inventory 绑定，两路独立复核
  确认无需修改 runner 或重跑 unchanged formal；
- feature acceptance=
  `docs/9.3.4/acceptance/step4-coverage-gate-acceptance.md`，status/decision=
  `signed-off / accepted`，blocking items=`none`，Experience=`N/A`；
- 25 个 Step 4 workitem 已关闭；classification DEBT 继续
  `open / accepted-for-9.3.4-only / due-before-9.3.5-acceptance`；
- At that checkpoint：Step 4=`passed`；Step 5=`ready / not-started`；`can_enter_step5=yes`；
  9.3.4=`in-progress`，Steps 6/7=`pending`，9.3.5=`queued`。

## Execution check-in — formal-r6 recovery（2026-07-19）

- Cfreeze=`14931b68…`，fresh formal-r6=`failed / bootstrap-negative / exit 1`；delta、r22 replay、
  contract、overlay PASS，source/tests/summary absent，cleanup/DB restore PASS；
- cause=`synthetic fsmonitor v2 newline token`；real main/fresh index=`4066 × ordinary H`；
- fixed hook=`token\0`；independent stress=`1000/1000`、local focused=`100/100`、five full negative=
  `5/5`；
- machine=`diagnostic-ready / diagnostic-pending`，Step4 manifest=`51ff1d26…f76`，Step6 workflow
  closure PASS；
- r23：`completed / diagnostic-observed / public-valid`，required=`773+59/5707`、exec/session=
  `23/48`、source exact、cleanup/restore PASS；
- r23 high-water：branch=`26112/44870`、complexity=`17659/35571`，唯一 delta 为
  `MapBeanInfoHelper#getBeanProperty` inner double-check；candidate/capsule absent；
- controlled regression：existing node、100 interleavings、5 fresh JVM class probe bitmap exact、
  `foggy-core=97/F0E0S0`、independent review=`0/0/0/0`；
- current：Step 4=`in-progress`，Step 5–7=`closed/hold`，9.3.5=`queued`；next=`replacement Cdiag
  commit/push -> fresh diagnostic-r24`。

## Execution check-in — diagnostic-r24 reviewed Cfreeze（2026-07-19）

- Cdiag=`414c8b12…` 已 clean/pushed；r24=`completed / diagnostic-observed / exit 0 / public-valid`；
- lanes=`773+59/5707/F0E0S0 + Addon 2/6`，exec/session/classes=`23/48/16953`，production
  universe=`24/2098`，source exact，cleanup=`0/0/0`，四个 exact demo DB restored healthy；
- aggregate=`54624/76830 line, 26112/44870 branch, 17659/35571 complexity`；MapBeanInfo
  method=`4/4`，raw Unit probe=`10/11 / _wU`；
- candidate=`f13f3c35…2ee` 保持 `review-required`；capsule=`6638 entries / 0 symlink`，独立双
  rebuild、verify、空目录 materialize PASS；Reviewer A/B=`APPROVE / combined 0/0/0/0`；
- canonical working tree=`confirmed/formal-ready`；Step 4/6 manifests=`61/61 + 16/16`，frozen
  diagnostic、contract、workflow 与 negatives PASS；pre-Cfreeze quality=`PASS / 0/0/0/0`；
- current Step 4=`in-progress / ready-for-one-direct-child-Cfreeze`，`can_enter_step5=no`、
  `can_enter_coverage_audit=no`、`can_enter_acceptance=no`；next=Cfreeze commit/push/topology/clean，
  then one fresh-clone formal-r7。

## Execution check-in — formal-r7 CALCULATE catalog portability recovery（historical；2026-07-19）

- Cfreeze=`439aea5e…`；fresh formal-r7=`failed / child-integration / exit 1`；Unit=
  `681+55/4941/F0E0S0`，Integration `caffeine-sqlite=2`、`hermetic=3` PASS，`sqlite-broad=307/F1`；
- 唯一失败=`CalculateMvpIT.parityCatalogCasesStayExecutable`，根因是 catalog 从未进入 bridge Git tree，
  diagnostic 被上层 workspace 文件偶然喂绿；production/test source、node、selector无回归；
- r7 capsule=`9 entries / 10303 bytes`，Base64 封装的 byte-exact Failsafe text、per-entry provenance、cleanup、source、
  sensitive scan、四 DB exact restore 均已复核；r7 永久 excluded；
- remediation：repo-local catalog exact blob/SHA + pre-test Git ownership/hash gate；lifecycle raw/executable
  seals与 Step4/6 hash closure同步；focused=`14/F0E0S0`；machine=`diagnostic-ready/diagnostic-pending`；
- checkpoint state at that time：只授权 Cdiag commit/push、isolated focused/negative proof 和 fresh
  diagnostic-r25；该状态已被下方 r25/r26 check-in supersede。Step 5–7、post-formal gates、
  9.3.5/9.4.0 当时均 closed。

## Execution check-in — diagnostic-r25 public-valid / Unit MySQL 7/12 remediation（2026-07-19）

- tested Cdiag=`5aaffbb4cd217d3d891c22eca4d3ae31d4e9d6e7`；run=
  `step4-coverage-20260719-diagnostic-r25`，status=
  `diagnostic-observed / completed / exit 0`；public validator=`DIAGNOSTIC VALID`，observation=
  `01487f7efd930406ffa05af9408012aa1fb215d94ba9c36c261f72c1aec7e42a`；
- lanes=`773+59/5707/F0E0S0 + Addon 2/6`，exec/session=`23/48`，production class universe=
  `24 modules / 2098 classes`，class-universe SHA-256=
  `e53103549fc7f4f460ca36847c82d441000c433b5619030d688a3c54d046f9b8`；source
  before=after=`2f41810585ade813671740218a2c303b1306a14236337712ef71d3e4aa5b1677`；
- wrapper=`runner_rc=0 / restore_rc=0 / wrapper_outcome_rc=0`；四个开跑前 demo DB container
  original ID 均 exact 恢复为 `running/healthy`；runner cleanup=`container/volume/network 0/0/0`；
- post-run consumer audit 发现
  `v934|8:surefire|4:unit|4:unit|51:com.foggyframework.dataset.fun.DatasetJdbcUtilsTest`
  的 `getOrCreateDataSource` 会建立连接并执行 `SELECT 1`，但旧实现捕获 `SQLException` 后只
  `printStackTrace`。因此 r7 的 `6 reports / 11 errors` 仍是 immutable historical
  observation，而真实 known-consumer lower bound 是 `7 reports / 12 nodes`；
- decision：r25 的 diagnostic observation 有效，但它发生在 7/12 修复前，状态=
  `superseded / non-candidate`；candidate/capsule/Cfreeze 均不授权。schema 2 fixture contract、
  negative=`42/42`、lifecycle=`5/5`、Step4=`61/61`、Step6=`16/16` 与 pre-Cdiag
  quality=`APPROVE / 0/0/0/0` 已 PASS；disposable MySQL 正向/错误密码结果仅为未封存 local
  observation，不能替代 new Cdiag 后的 isolated durable proof；
- next=new Cdiag commit/push/clean→isolated durable positive/negative proof→
  fresh diagnostic-r26→candidate/capsule/双审→direct-child Cfreeze→fresh formal-r8→final
  quality/audit/acceptance。Step 5–7、9.3.5、9.4.0 保持关闭。

Records：

- `docs/9.3.4/evidence/step-4/step4-unit-mysql57-known-consumer-7of12-remediation-20260719.md`
- `docs/9.3.4/workitems/BUG-step4-unit-mysql57-known-consumer-understatement.md`

## Execution check-in — diagnostic-r26 reviewed candidate / pre-Cfreeze（2026-07-19）

- replacement Cdiag=`4fe86929de6206aa3e514c974635e90395c28b2e` 已 push/clean；isolated r4
  durable proof 已完成：positive=`Maven rc0 / XML 1/F0E0S0`、wrong-password=
  `Maven rc1 / XML 1/F0E1S0`，disposable container absent、random port released；
- fresh r26=`step4-coverage-20260719-diagnostic-r26` public-valid，observation=
  `15e1ed76eaa624c0899b980472689e34e1b272ddda58b2bc5cf27994abffe705`；required=
  `773+59/5707/F0E0S0`、Addon=`2/6`、exec/session=`23/48`，source before=after=
  `6acfad24cc3d43c3bf550c904aa61c7e01f5b7829d4e2f204d489ab6cc40a8f5`；
- candidate/capsule 与两路独立 review 已完成，decision=`confirm-observed-thresholds`、
  B/H/M/L=`0/0/0/0`、mandatory=`0`，当前只授权唯一 direct-single-parent Cfreeze；
- r25 永久保持 `pre-remediation / superseded / non-candidate`，不得拼接或提升。Cfreeze、fresh
  formal-r8、post-formal quality/coverage audit/acceptance 均尚未完成；Step 5–7、9.3.5、9.4.0
  继续关闭；9.3.4 version signoff 后 classification debt 交 9.3.5 Gate 0 owner，deadline=
  `9.3.5 version acceptance`。

## Execution check-in — diagnostic-r27 governed high-water rejection（2026-07-19）

- fresh r27 on `f102b52c…` completed/public-valid with required=`773+59/5707/F0E0S0`、Addon=`2/6`、
  exec/session=`23/48`、source exact、cleanup and external restore PASS;
- aggregate was line=`54624/76830`、branch=`26111/44870`、complexity=`17658/35571`; r26 reviewed
  high-water is branch=`26112/44870`、complexity=`17659/35571`, so r27 is `non-freezable`;
- sole delta=`ExportWithChartTool.java:248` due to unordered test fixture. Candidate/capsule were isolated
  and are non-canonical. Ordered-test remediation and five fresh-JVM exact probe proofs are complete;
  next at that checkpoint=clean/pushed Cdiag -> fresh diagnostic-r28; the formal-r9 check-in below supersedes
  this next gate.

## Execution check-in — formal-r9 strict-umask fail-closed / recovery baseline（2026-07-19）

- tested Cfreeze=`34cd2452c1bbe793c0567ebe23179b290227ae3d`; fresh
  `step4-coverage-20260719-formal-r9` ended `failed / coverage-report / exit 2`. The failure occurred after
  report-inventory/exec validation when a strict caller umask yielded effective-POM public output mode=`0600`;
  the reporter emitted `E_OUTPUT: unexpected output mode: 0600`.
- r9 is immutable `failed / excluded / non-reusable / non-candidate`. Its formalization-delta and child-lane
  observations remain failure records, while success summary/candidate/final authority is absent. Neither r28
  reviewed material nor any r9 partial XML/exec/report output may be combined with a replacement run.
- runner cleanup recorded `container/volume/network=0/0/0`; outer restoration recorded `restore_rc=0`.
  These are environment-boundary observations only and do not alter Step 4, Step 5 or acceptance status.
- recovery baseline=`390322295e1efce34399468f98076edf7fcc6f73`: explicit public receipt mode plus a strict-umask
  negative probe, followed by reset to `coverage-contract=diagnostic-ready` and
  `coverage-thresholds=diagnostic-pending`. This commit is not the final Cdiag; its
  [implementation-quality record](../quality/step4-formal-r9-effective-pom-output-mode-recovery-implementation-quality.md)
  is `ready-for-new-Cdiag` only.
- next=one new clean/pushed Cdiag successor→fresh all-lane diagnostic-r29→new candidate/capsule/dual review→
  direct-single-parent Cfreeze→fresh formal successor→final quality→replacement coverage audit `31/31`→
  Step 4 feature acceptance. `can_enter_cfreeze=no / can_enter_step5=no /
  can_enter_coverage_audit=no / can_enter_acceptance=no`; Steps 5–7, 9.3.5 and 9.4.0 remain closed.

## Execution check-in — diagnostic-r29 Git-safety hold（2026-07-19）

- Cdiag=`f420a4eaa3cf9bed0d7027b656ea71af6d0b03ca`; fresh r29 completed under `umask 077` and its
  diagnostic replay/candidate facts remain recorded as non-authoritative observation.
- Git-safety review identified that the recursive local capsule captures excluded raw runtime content and
  process/runtime metadata. The r29 closure is therefore not a Git-safe publication artifact.
- r29=`non-freezable / no-cfreeze-authority`: its local candidate, capsule and reviews must not be reused,
  repackaged, linked as tracked evidence, or combined with historical material.
- The fail-closed tooling repair is locally validated: explicit allowlist, de-identified attestation, safe frozen
  recomputation, and persistent rejection tests. It still requires a new Cdiag and a fresh diagnostic before any
  candidate, Cfreeze or formal decision. Step 5–7, coverage audit, acceptance, 9.3.5 and 9.4.0 remain closed.

## Execution check-in — diagnostic-r30 successor-binding fail-closed（2026-07-19）

- Cdiag=`7757aa36c0efd0970422669e0f88f74daa8f15b0`; fresh strict-umask
  `step4-coverage-20260719-diagnostic-r30` failed in `contract-validate` when the Step 3 successor overlay
  rejected stale dual coverage-contract and coverage-tool bindings.
- r30=`failed / excluded / non-reusable / non-candidate / zero-lane-authority`. No source seal, lane result,
  aggregate, XML, observation, summary, candidate or final output exists; it cannot be rerun, repaired or merged
  with another run.
- Remediation synchronizes the two binding sources and Step 4/6 manifest chain, then exercises canonical overlay
  validation from the contract-negative suite. A new Cdiag and fresh r31 are required before any review/Cfreeze/
  formal transition; Step 5–7, 9.3.5 and 9.4.0 remain closed.

## Execution check-in — diagnostic-r31 fixed-port environment fail-closed（2026-07-19）

- Cdiag=`f80fadd62ca00d3ba56f1be04e92113ba1145019`; fresh strict-umask
  `step4-coverage-20260719-diagnostic-r31` completed preflight/source-seal/class-universe and stopped in
  `child-unit` before lifecycle probes, fixture provision or Maven Unit execution.
- r31=`failed / excluded / non-reusable / non-candidate / zero-lane-authority`. Derived Docker resource and outer
  cleanup counts are `0/0/0`; only the fixed-port-free precondition was false, from outside the derived project.
  No external listener is reused, changed or entered as tracked runtime evidence.
- The one fresh diagnostic authorized by f80 is consumed. The only successor is r31 exclusion record → new
  clean/pushed Cdiag → independently verify port free → fresh r32 → new candidate/Git-safe capsule/review →
  direct-child Cfreeze → fresh formal → post gates. Step 5–7, 9.3.5 and 9.4.0 remain closed.

## Execution check-in — diagnostic-r32 WatchService delete high-water rejection（2026-07-20）

- fresh strict-umask `step4-coverage-20260720-diagnostic-r32` from clean `a9ec2a2f…` completed:
  required=`773+59/5707/F0E0S0`, Addon=`2/6/F0E0S0`, exec/session=`23/48`, source before=after, critical
  policy=`12/23/1 N/A/below 0`, cleanup=`0/0/0`;
- r32 is `diagnostic-observed / public-valid / non-freezable`: line=`54624/76830`, branch=`26111/44870`,
  complexity=`17658/35571`; branch and complexity are one below the governed high-water, so no r32
  non-canonical material may become candidate, Cfreeze, formal, audit, or acceptance authority;
- semantic comparison isolated line 442 of `WatchServiceFileTracer`. The existing mock-key test now drives
  unfiltered, filtered-reject, and filtered-match deletion. Five focused JVMs restored line=`4/4` and method
  branch/complexity=`11/12` / `6/7`; full `foggy-core`=`97/F0E0S0`; independent review=
  `PASS / B/H/M/L=0/0/0/1`, mandatory=`0`;
- only successor: commit/push/clean this test-only Cdiag → fresh strict-umask r33 with all lanes, source/cleanup
  closure, line >= `54624/76830`, branch >= `26112/44870`, complexity >= `17659/35571` → new
  candidate/Git-safe closure/dual review → direct-child Cfreeze → fresh formal → post gates. Step 5–7,
  9.3.5 and 9.4.0 remain closed.
