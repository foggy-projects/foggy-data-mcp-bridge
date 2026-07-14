---
doc_role: test_plan_and_evidence_template
doc_purpose: Define required deterministic and real-query evidence for 9.3.3; record results only after execution.
version: 9.3.3
status: completed
result: passed-with-documented-gaps
created_at: 2026-07-13
updated_at: 2026-07-14
---

# 9.3.3 测试计划与证据模板

## 文档作用

- doc_type: test-plan+evidence-template
- intended_for: test implementer / execution agent / coverage auditor / signoff owner
- purpose: 冻结必须执行的正反向证据并记录实际结果；首次 Batch 7 保持 superseded，replacement authority 与 post-gates 已完成。

## Evidence Boundary

- 本文初始状态 `not-run`；实现后在原表中填写真实结果，不预填绿色。
- 9.3.4-A 只证明本轮 suite 不会 0-test/错库伪绿；完整五数据库、coverage、release artifact 仍归 9.3.4。
- `-DskipTests package` 只算编译/装配。
- 查询链必须通过 QueryFacade 执行并逐行对比原生 SQL；只看 SQL 字符串、Map 存在或 mock interaction 不算 REAL-QUERY。
- Batch 5 SQLite Failsafe 的 REF-01/02 只直接证明 atomic refresh/failure 下的 QueryFacade + native SQLite 结果；完整 `REAL-QUERY` 由 Batch 6 Step 6 的独立 authority 提升，不倒推扩大 Batch 5 owning boundary。
- Batch 6 Catalog Authority 的 17 个 direct tests 证明单一 namespace snapshot
  authority，36 个 consumer tests 只作为兼容回归；该 53-test run 不提升
  `CACHE-GEN`、`CACHE-CROSS-JVM` 或完整 `REAL-QUERY`；这些 criterion 分别由后续
  owning authority 提升。
- Batch 6 Steps 2/3 的 73 个 tests 保留为 L1/L2 strong-key direct evidence；
  Step 4 已提升 `CACHE-CROSS-JVM`。Step 5 authoritative run
  `20260714T032135Z-2678670` 以 direct 38/4 + supporting 61/3 = 99/7、
  failures/errors/skipped=`0/0/0` 提升 `CACHE-GEN`。Step 6 authoritative run
  `20260714T041047Z-2755326` 以 11 tests / 6 Failsafe reports 提升
  `REAL-QUERY`。Step 7 aggregate authority `20260714T045604Z-2854237` 已通过，
  Batch 6 completed。

## Environment Record

执行时记录：

- git commit + dirty worktree manifest/patch checksum；
- JDK、Maven、OS；
- test profile、命令、start/end time；
- DB product、真实 version、非敏感 URL/catalog/schema identity；
- Surefire/Failsafe XML 路径和 tests/failures/errors/skipped；
- 并发数、executor size、barrier/timeout、build/publish count；
- before/after catalog generation、binding generation 和 observed result set。
- source revision、binding admission/lease state、JVM process/boot epoch 和 Redis key digest（不记录凭据）。

## Gate 0 正反证据

| ID | 操作 | 预期 | 实际 | Evidence |
|---|---|---|---|---|
| GATE-NO-UNIT | 指定不存在/空 owning unit suite | command fails | expected-negative-pass | `evidence/gate-0/negative-runner-evidence-20260713.md` |
| GATE-NO-IT | Failsafe owning IT 为 0 | verify fails | expected-negative-pass | `evidence/gate-0/negative-runner-evidence-20260713.md` |
| GATE-WRONG-DB | required DB product/version/identity/sentinel 错 | preflight fails before lifecycle tests | expected-negative-pass | `evidence/gate-0/database-preflight-evidence-20260713.md` |
| GATE-ONCE | probe unit + probe IT | each exactly once | passed | `evidence/gate-0/deterministic-harness-evidence-20260713.md` |
| GATE-HARNESS | 受控 winner/waiter | deterministic release; bounded completion | passed | `evidence/gate-0/deterministic-harness-evidence-20260713.md` |

Gate 0 的单一权威入口为：

```bash
scripts/verify-v933-entry-gate.sh
```

初始权威 run id=`20260713T104955Z-959834`，完整证据见
`evidence/gate-0/gate-0-run-20260713.md`。Batch 1 全部测试/脚本落地后以
同一入口完成 post-change run；Batch 2、Batch 3 与 Batch 4 最终代码/测试后均再次复跑。
Batch 4 历史 run id=`20260713T165330Z-1941345` 继续保留；Batch 5 final audit
后的最新 run id=`20260713T201941Z-2133081`，结果为 5 positive green + 4/4
expected-negative fail closed。Batch 1–5 的历史 run 继续保留在各自退出证据中。

预期失败证据的命令退出非零是通过 gate contract，不等于产品测试失败；记录时必须标 `expected-negative-pass`。

## Deterministic Contract Matrix

| ID | 场景 | 直接断言 | 最低层级 |
|---|---|---|---|
| NS-01 | A→B nested scope | inner=B；close 后=A | unit |
| NS-02 | A→default nested scope | inner 精确为 default，不继承 A；close 后=A | unit |
| NS-03 | scope body throws | previous 精确恢复 | unit |
| NS-04 | single-thread executor 连续任务 | task2 初始 namespace unset | unit |
| NS-05 | wrong-thread/out-of-order/double close | stable fail/no duplicate pop | unit |
| SNAP-01 | collection immutability | external mutation impossible；old snapshot 不变 | unit |
| SNAP-02 | TM/QM/alias/catalog identity | same namespace generation across view | unit/IT |
| SNAP-03 | deterministic alias | reverse/random load order produces same aliases | unit |
| GEN-01 | plain read/cache hit | lifecycle generation unchanged | unit |
| GEN-02 | observable materialization/refresh | each successful publish changes identity exactly once；failure unchanged | unit/IT |
| DS-01 | save/update/remove/disable/rebind | affected binding generation changes and persists | adapter IT |
| DS-02 | pinned old handle | old identity never routes to new physical sentinel | real DB IT |
| DS-03 | one namespace, two model bindings | each model pins its own binding identity；refresh one binding does not rewrite the other | adapter/real DB IT |
| DS-04 | remove/disable/rebind after commit | new query gets new binding or stable failure；never reacquires old target | adapter/real DB IT |
| DS-05 | mutation with pre-existing lease | ordinary mutation bounded-drains old lease without retry；hard revoke closes/fails it | adapter/real DB IT |
| SF-01 | 100 same key callers | build count=1；same model/identity | concurrency unit |
| SF-02 | distinct namespace/model/generation/source revision/backend/binding set | executions overlap；future/result isolated | concurrency unit |
| SF-03 | winner throws | all waiters see same category；flight removed；retry succeeds | concurrency unit |
| SF-04 | refresh during build | stale build not published；bounded retry/new identity | concurrency unit |
| SF-05 | dependency cycle | stable error before timeout；no self-deadlock | unit |
| REF-01 | concurrent readers + success refresh | every result entirely old or entirely new | SQLite/external IT |
| REF-02 | candidate model fails while binding remains valid | old generation/model/query remains usable | SQLite IT |
| REF-03 | namespace A refresh | namespace B identity/result unchanged | IT |
| REF-04 | model X refresh | sibling Y preserved and valid | IT |
| REF-05 | source changes during candidate | candidate discarded/retried；no hybrid publish | concurrency IT |
| REF-06 | root QM succeeds, dependency fails | no partial QM/snapshot publication | unit/IT |
| EVT-01 | bundle add/remove | source commit 后 refresh；无 stale reload window | fsscript+model IT |
| EVT-02 | file change/import dependency | event revision equals committed post-mutation view；candidate sees new import closure；scope is exact | fsscript+model IT |
| EVT-03 | source changes while candidate builds | old SourceRevision result discarded；new committed revision wins | concurrency IT |
| EVT-04 | file event scope unknown | possibly affected catalogs admission-blocked + persistent diagnostic until rebuild | integration |
| VAL-01 | Runtime validate | invalid/valid temporary bundle 都不污染 live snapshot | runtime IT |
| CAT-01 | model/MCP catalog | same namespace snapshot view；无 global name leak | integration |
| CACHE-01 | same stable identity, two contexts | identical cache key；can hit shared provider | cache integration |
| CACHE-02 | catalog/binding generation changes | L1/L2/Redis/Pivot old key unreachable | cache integration |
| CACHE-03 | identity unknown/routing unresolved | no cache read/write | unit/integration |
| CACHE-04 | two JVMs/process restart + shared Redis | key contains no instance address；new boot epoch cannot hit old key | multi-process integration |
| API-01 | old Runtime response consumer | additive fields do not break old JSON contract；opaque/nullability/error behavior matches contract | runtime contract test |

所有并发用例使用 latch/barrier/phaser 和有界 timeout；关键交错禁止以 `Thread.sleep` 驱动。

## Batch 2 NamespaceScope Evidence

| 范围 | 实际 | Evidence |
|---|---:|---|
| NS-01/02 nested named/default + inherited/default canonicalization | passed | `NamespaceScopeTest` 9-case suite |
| NS-03 exception/early return/exact legacy state restore | passed | `NamespaceScopeTest` |
| NS-04 single-worker sequential reuse | passed；task 2 starts unset | bounded Future + executor termination；no sleep |
| NS-05 wrong-thread/out-of-order/double/stale-token/legacy mutation | passed | stable errors；failed close does not mutate stack |
| QueryFacade/managed production entries | 11/0/0/0 | `NamespaceProductionEntryRestorationTest` |
| Semantic metadata | 2/0/0/0 | null/blank/named normal + exceptional loops |
| QueryModelLoader | 3/0/0/0 | named/default cache hit + lookup/script failure |
| legacy NamespaceContext compatibility | 7/0/0/0 | isolated compatibility lane |

Authoritative command/run:

```bash
scripts/verify-v933-batch2-namespace.sh
# target/v933-batch2-namespace/runs/20260713T130626Z-1313396
```

Result: 25 NamespaceScope product tests and 7 compatibility tests passed with
0 failures/errors/skips. Default discovery also retained one Gate probe, so its
exact five reports total 26 tests. Production legacy mutation count and
sleep-driven Batch 2 test count are both zero. Full hashes and the post-Batch2
entry-gate replay are in
`evidence/batch-2/namespace-scope-exit-20260713.md`.

## Batch 3 Catalog / Binding Evidence

| 范围 | 实际 | Evidence |
|---|---:|---|
| SNAP-01/02/03 + GEN-01/02 | 46/0/0/0 catalog authority lane | immutable containers、exact slot/provenance/dependency/alias invariants、read/no-op stable、materialization +1、failure stable |
| candidate fail closed | store suite 10 | pure `fail()` checked before no-op；failed candidate keeps active object/identity；sealed + owner-thread guarded |
| deterministic alias | catalog unit + SQLite consumer | static collision order stable；dynamic synthetic canonical hash stable across sequential/reverse arrival |
| QueryFacade/metadata pin | 4 + 3 | atomic catalog/binding pin；conflicting repin/model swap rejected；multi-model one final snapshot；legacy explicitly untracked |
| QM-COMPLETE | 2 plus store failure cases | joined TM failure returns no partial QM；external same-name fresh TM cannot publish hybrid object graph |
| DS-GENERATION | Runtime 28 + MCP 12 | persisted Runtime epoch/sequence/v1 migration；MCP cold generation；configure/remove/re-add non-reused identity |
| BINDING-REVOKE | Runtime/MCP lifecycle suites | DRAIN/HARD、bounded deadline、borrow/commit race、exactly-once close、old new-borrow rejected |
| old/new physical sentinel | Runtime lifecycle | real H2 held-old=`OLD`，new handle=`NEW`，old handle cannot reopen |
| existing consumers | 33/0/0/0 SQLite lane | alias 1、synthetic 4、QueryFacade 5、Semantic 23 |
| cache strong identity support | 30/0/0/0 | Caffeine only；supporting evidence，不提升 Batch 6 Redis/Pivot/CACHE-GEN |

Authoritative command/run:

```bash
scripts/verify-v933-batch3-catalog-binding.sh
# target/v933-batch3-catalog-binding/runs/20260713T150948Z-1719636
```

Result: 149 tests passed with failures/errors/skips=`0/0/0`. Each expected
class has a fresh owning XML and exact count. Source audits report zero legacy
mutable catalog authority, sleep-driven Batch 3 tests, promoted red references
and physical/credential generation inputs. Full hashes, the fail-closed runner
evolution and final entry-gate replay are recorded in
`evidence/batch-3/catalog-binding-exit-20260713.md`.

## Batch 4 Single-flight Evidence

| 范围 | 实际 | Evidence |
|---|---:|---|
| SF-01 same key | 100 callers / 99 waiters / build=1 | same result；residual flight=0；caller-inline winner |
| SF-02 exact-key isolation | six independent dimensions overlap | namespace、model、catalog generation、source revision、backend、binding set 均隔离 future/result |
| SF-03 shared failure/retry | same exact Throwable；retry build total=2 | ordinary failure、cancellation、checked timeout；precise removal |
| SF-05 dependency cycle | stable fail before wait | frozen scope 内 same-thread/self-wait guard；不声称任意跨线程 wait-for 图 |
| detached loader publication | TM 4 + QM 2 plus store/currentness suites | exact base catalog/source view stale retry；binding final guard 与 catalog swap 原子 |
| compatibility/adapters | catalog 34 + SQLite 33 + namespace 3 + Runtime 5 + MCP 14 | process-local default opt-in；Runtime/MCP mutation-monitor currentness guard |
| source audits | 4 categories all zero | no long build lock、owned executor/thread、sleep-driven Batch 4 test、promoted-red reference |

Authoritative command/run:

```bash
scripts/verify-v933-batch4-single-flight.sh
# target/v933-batch4-single-flight/runs/20260713T164144Z-1910217
```

Result: single-flight core 50 + catalog 34 + SQLite 33 + namespace 3 +
Runtime 5 + MCP 14 = 139 tests，failures/errors/skips=`0/0/0`。
`summary.env` SHA-256=`f4d37813c3a99640101cfbae5671fb4b5795b1011257d0d6857ad4fd793777f6`，
`SHA256SUMS` SHA-256=`88b606868fa4199fdcc51553bd8d203310c69c2dc9d5993e48e8b9e70123d2f9`，
全量 checksum 复核通过。

`SF-04` 只获得 lazy-load detached catalog/source view 与 final binding guard
supporting-pass；真正 source refresh during build、old-or-new readers 与 event
convergence 仍由 Batch 5 证明，不提升任何 `REFRESH-*`/`SOURCE-COMMIT`。

## Batch 5 Atomic Refresh Evidence

| 范围 | 实际 | Evidence |
|---|---:|---|
| REFRESH-ATOMIC / REFRESH-FAIL | SQLite Failsafe 2/0/0/0 | `CatalogRefreshQueryIT` REF-01/02：blocked old readers 与 post-publish readers 只观察完整 old/new QueryFacade identity/model/native result；failed candidate publish=0，binding current 时旧真实查询仍可用 |
| REFRESH-SCOPE | passed | target/dependent/sibling preservation、namespace admission isolation、Runtime/MCP exact affected-namespace convergence；REF-03/04 是 deterministic model/adapter evidence，不冒充 SQLite IT |
| EVENT-CONVERGENCE | passed | bundle 3、file 4、Runtime datasource 8、MCP datasource 3 的统一 refresh boundary；production clear-first path=0 |
| SOURCE-COMMIT | passed | fsscript committed source registry 7、source-stale rejection、unknown-scope persistent admission block |
| VALIDATE-ISOLATION | passed | valid/invalid detached Runtime validation 均保持 live source revision、catalog generation、names、aliases 不变 |
| API-COMPAT | passed | additive DTO、typed lifecycle errors、nullability、hostile diagnostic key/value/Collection depth sanitization；Batch 7 replacement API authority 62 tests / 6 reports green（Controller/legacy subset 58/5 + sanitizer 4/1） |
| final code audit | 4 items closed | mixed provenance known-binding guard；block catalog before pool callback；hostile diagnostic key/value sanitize；ID canonicalization preserves credentials |

Authoritative command/run:

```bash
scripts/verify-v933-batch5-refresh.sh
# target/v933-batch5-refresh/runs/20260713T200646Z-2120785
```

| Lane | Reports | Tests | Failures/Errors/Skipped |
|---|---:|---:|---:|
| model refresh/admission unit | 6 | 30 | 0/0/0 |
| model real SQLite Failsafe IT | 1 | 2 | 0/0/0 |
| fsscript source convergence | 2 | 7 | 0/0/0 |
| Runtime lifecycle/convergence/API | 8 | 34 | 0/0/0 |
| MCP datasource convergence | 2 | 17 | 0/0/0 |
| total | 19 | 90 | 0/0/0 |

Final ordered replay:

| Boundary | Run | Result |
|---|---|---|
| Batch 4 single-flight | `20260713T201031Z-2124453` | 140 green；failures/errors/skipped=`0/0/0` |
| Batch 3 catalog/binding | `20260713T201525Z-2129344` | 166 green；failures/errors/skipped=`0/0/0` |
| 9.3.4-A entry gate | `20260713T201941Z-2133081` | positive 5 green + expected-negative 4/4 fail closed |
| Batch 2 NamespaceScope | `20260713T202421Z-2138021` | 25 product + 7 compatibility green |
| remaining Batch 1 red | `20260713T202645Z-2141955` | only Batch 6 `CATALOG-AUTHORITY`；1 suite / 1 test expected-red |

Batch 5 checkpoint closes six owning criteria and hands Batch 6 the
catalog/cache/Pivot/full-real-query consumer work. 在该历史 checkpoint，formal
implementation quality gate、test coverage audit 与 version acceptance 仍为
`not-started`，9.3.3 状态为 `in-progress`；最终状态见本文件末尾 Batch 7 replacement
authority 与 ordered post-gates。

## Batch 6 Catalog Authority Evidence

`CATALOG-AUTHORITY` 的权威 run：

```bash
scripts/verify-v933-batch6-catalog-authority.sh
```

```text
target/v933-batch6-catalog/runs/20260714T021057Z-2445113/
```

| Evidence lane | Reports | Tests | Failures/Errors/Skipped | Role |
|---|---:|---:|---:|---|
| model `SemanticModelCatalogServiceTest` | 1 | 11 | 0/0/0 | direct：coordinator-only recovery、exact empty/deletion、blocked/partial/provenance fail-closed、immutable identity/alias/model/binding view、native metadata seqlock |
| MCP `CatalogNamespaceAuthorityTest` | 1 | 5 | 0/0/0 | direct：model/resolver/MCP same view、namespace isolation、whole-generation switch、MCP metadata seqlock/block/churn |
| Spring `CatalogAuthoritySpringWiringTest` | 1 | 1 | 0/0/0 | direct：resolver 与 MCP catalog tracked constructors 共用一个 `SemanticModelCatalogService` authority bean；无 legacy loader/bundle interaction |
| resolver consumer regression | 1 | 11 | 0/0/0 | supporting：namespace delegation and no MCP names cache/invalidation authority |
| ListModels consumer regression | 2 | 21 | 0/0/0 | supporting：configured/dynamic namespace model-list behavior |
| Controller consumer regression | 1 | 4 | 0/0/0 | supporting：JSON/Markdown response compatibility |
| **total** | **7** | **53** | **0/0/0** | **direct 17 + supporting 36** |

Direct assertions cover:

- active complete snapshot direct projection；cold exact-empty and incomplete
  recovery only through `CatalogRefreshCoordinator`；no service-owned candidate
  discovery/resolve/commit；
- recovery failure preserves prior state，blocked admission stops before
  recovery，missing binding provenance and partial view fail closed；
- one immutable namespace view contains identity、names、aliases、models and
  exact per-model binding resolutions；exact deletion does not retain old names；
- native/MCP metadata callbacks use bounded whole-operation identity seqlock，
  return a complete old/new generation and fail closed after three unstable
  attempts or admission block；
- resolver 与 MCP catalog tracked constructors receive one shared Spring-owned
  `SemanticModelCatalogService` authority bean，without legacy loader/bundle
  interactions；model service 的 store/coordinator 注入由 model test/source
  audit 证明。

Evidence integrity:

- `summary.env` SHA-256=
  `62604e1053c328c3c219da2f8792cdcb1d9ab13878f5d8ad021055ea7ea21563`
- `SHA256SUMS` SHA-256=
  `5b017cb750b0d28f4df8cd1998b63f27f46edcad25e27b9edda37aaa13fda3a1`
- source audit：consumer names caches=0、independent MCP watcher
  registrations=0、direct model candidate access=0、sleep-driven catalog
  tests=0、remaining RedBaseline sources=0。

Post-promotion remaining-red replay：

```text
target/v933-batch1-red/runs/20260714T021251Z-2449309/
```

Result：0 cases / 0 tests / 0 sources；`summary.tsv` SHA-256=
`690b22d6cad493e293c6d9bbd5d8c7f37d7b027f06c46d82ba2c9306ef7a5406`。

Earlier run `20260714T014919Z-2373851` is diagnostic and
`superseded-by-review`. Its internal `status=passed` is not accepted as product
evidence and none of its 43 tests/6 reports is added to the authoritative total.

This historical checkpoint promoted only `CATALOG-AUTHORITY`. Batch 6 later
completed Steps 2–7 in the frozen order. Batch 7 is now ready/not-started.

## Batch 6 Steps 2–4 Cache Identity / Cross-JVM Evidence

### Step 2 — L1/L2 strong identity

| Lane | Tests | Failures/Errors/Skipped | Evidence |
|---|---:|---:|---|
| model `QueryFacadeCatalogIdentityTest` | 4 | 0/0/0 | exact catalog resolution is atomically pinned into execution context；conflict/namespace/model mismatch fail closed |
| addon `QueryCacheKeyBuilderStrongIdentityTest` | 12 | 0/0/0 | canonical catalog/source/binding identity、rotation、malformed/incomplete no-key |
| addon Caffeine provider | 30 | 0/0/0 | L1 strong-key provider behavior |
| addon Redis provider | 25 | 0/0/0 | L2 strong-key and serializer behavior |
| addon SQLite datasource isolation | 1 | 0/0/0 | physical datasource isolation sentinel |
| **total** | **72** | **0/0/0** | **model 4 + addon focused 68** |

Logs:

- `target/v933-batch6-cache-model-pin.log` SHA-256=
  `c139540a97d1e7fe55ee4bbe5aa200d0b3e5ee250aa3ebf97ced41b8dfdcc048`
- `target/v933-batch6-cache-addon-focused.log` SHA-256=
  `370481a259c84376fc9e83ea84586534c94c0c4cae47d93d3d2f6e5ce97b555a`

`QueryCacheKeyBuilder` contains no instance UUID/object address fallback.
Incomplete or conflicting identity produces no L1/L2 key. The boot UUID is
owned only by `CatalogSnapshotStore` as the deliberate process-cold
catalog/source epoch.

### Step 3 — two independent Spring contexts

`QueryCacheKeyCrossApplicationContextTest` passed 1/0/0/0. Two real Spring
contexts create distinct provider/builder instances while the same complete
identity produces identical L1/L2 keys; catalog and binding generation changes
each rotate both keys.

- XML SHA-256:
  `cc252b5de28832631b19257245c831eaabaa0002769e6078379da770676c37cb`
- log SHA-256:
  `fa7e0f962eda61cb8f97801ccc2a458fed8e91c861e43d6660219b30eb119563`

At the historical Step 3 checkpoint, Steps 2/3 together were direct partial
evidence for `CACHE-GEN`; Pivot was not covered. Step 5 has since passed and
promoted the criterion, as recorded below.

### Steps 2/3 — Step 7 fresh replay authority

The Step 7 gate does not reuse the historical focused logs. It executes the
current source through `scripts/verify-v933-batch6-cache-identity.sh`:

```text
target/v933-batch6-cache-identity/runs/20260714T044313Z-2824177/
```

| Lane | Tests | Reports | Failures/Errors/Skipped |
|---|---:|---:|---:|
| current QueryFacade catalog pin | 6 | 1 | 0/0/0 |
| addon strong key/providers/SQLite isolation | 68 | 4 | 0/0/0 |
| two independent ApplicationContexts | 1 | 1 | 0/0/0 |
| **total** | **75** | **6** | **0/0/0** |

Step 2 is 74 tests in current source rather than historical 72 because Pivot
work added two QueryFacade generation-switch/idempotence assertions. Step 3
remains one test. All three serial lanes contain exactly one `BUILD SUCCESS`；
all XML is marker-fresh；source and two-layer artifact manifests verify；the
independent post-run review found no blocker.

Summary/source/inner/outer SHA-256:

- `b8cff91a339df48b8ba79549af40948bae1a0bad1618a8f809266bd0898e2e9c`
- `0563a797fcdb4a9c33e8e176851b50b466012734340f2eb077b5a28763dfe053`
- `143bbccea012ff2a8ed4d183bb468dfbde7770953bdac021d959b1fdc16855ea`
- `ffc7a11de0fcd2e9b3ec843ccdc2471c5636bd6df6ddfd72b63887991825e296`

Run `20260714T044020Z-2817047` is diagnostic/superseded because the earlier
runner revision left output after latest publication; it is not authority.

### Step 4 — authoritative shared-Redis run

Recorded command/run（本次文档回写未重跑）：

```bash
scripts/verify-v933-batch6-cache-cross-jvm.sh
# target/v933-batch6-cache-cross-jvm/runs/20260714T025444Z-2628329/
```

| Authoritative observation | Recorded value |
|---|---|
| Redis | 7.4.6；`redis:7-alpine`；digest `sha256:3b73847e72874be07e6657b129a94761662b79bc0f679273757d4218573b2a98` |
| child JVMs | exactly two distinct PIDs：writer `2629762` / restart `2630070` |
| production path | production auto-configured Redis template/provider；no test-only serializer/provider |
| lifecycle input | exact resolution derived from a real non-empty published snapshot；27 independently checked probes |
| binding | same `primary` / `runtime-registry` / `binding:persisted:1` in both processes |
| old physical-key read controls | 2；仅证明旧物理 Redis key 可读，不代表 lifecycle resolution/provider 命中 |
| restart current identity | 2 misses；then 2 post-write hits under the current identity |
| emitted Redis state | `SCAN`=4 and `DBSIZE`=4；independently derived key sets match |
| owning report | `RedisCrossJvmCacheIT` 1/0/0/0 in exactly one XML |
| safety | two-layer hash check passed；container cleaned；no credentials in evidence |

Artifact hashes:

- owning XML SHA-256=
  `93e92cf913098bbd110da8b0a98fd2a16414b2fe615e7fc98edb9277dffd51f9`
- `summary.env` SHA-256=
  `ff76b8a81360606e97a316797f40f7d145951c56e7e803911be030d4a3e5358e`
- `SHA256SUMS` SHA-256=
  `a54b7a4b18b750a5afb99fc3ee47b91af4759d0420f60cf9fb4c28edb1950ed3`
- source manifest SHA-256=
  `f7c53054262502c7a348ea93e0ed9e12d461e2c18c4dd2105563b03ab300eea6`

Independent second review found no blocker. The two old physical-key reads are
controls proving physical values are readable through the production path;
they are not lifecycle resolution/provider hits. The current
snapshot-derived identity misses both old keys and hits both newly written
keys.

Diagnostic exclusions:

- `20260714T023304Z-2522929`: diagnostic failed；generic Jackson rejected an
  unknown derived `empty` field.
- `20260714T023442Z-2527116`: diagnostic/superseded；test-only serializer、empty
  snapshot/manual resolution and runner self-report/hash false-green window.
- `20260714T025247Z-2623994`: diagnostic/superseded；product test green but the
  runner model-segment parser failed.

Only `20260714T025444Z-2628329` promotes `CACHE-CROSS-JVM`.

At the historical Step 4 checkpoint, `CACHE-CROSS-JVM` passed and `CACHE-GEN`
remained direct partial. The later Step 5 decision is recorded below.

## Batch 6 Step 5 Pivot / CACHE-GEN Evidence

Authoritative command/run（本次文档回写未重跑）：

```bash
scripts/verify-v933-batch6-pivot-identity.sh
# target/v933-batch6-pivot/runs/20260714T032135Z-2678670/
```

| Role / suite | Reports | Tests | Failures/Errors/Skipped |
|---|---:|---:|---:|
| direct：Pivot strong identity 4 + pipeline 8 + QueryFacade 6 + SemanticRequestContext 20 | 4 | 38 | 0/0/0 |
| supporting：Pivot integration 55 + operational SPI 3 + telemetry 3 | 3 | 61 | 0/0/0 |
| **total** | **7** | **99** | **0/0/0** |

Direct assertions prove:

- length-framed full SHA-256 over catalog generation、source revision、canonical
  namespace/model and exact sorted dependency bindings;
- missing/incomplete/JDBC-empty/conflict and supplementary provider exception
  all refuse lookup/store;
- provider/manual tokens are additive only and cannot rescue lifecycle refusal;
- provider failures expose normalized class only, without message/token/binding/
  stack;
- Semantic service and managed relation pre-pin the same typed resolution;
- QueryFacade rejects a generation conflict before filters/query/store;
- public record/provider SAM/legacy options construction remain compatible.

The provider-exception blocker is closed by the eighth pipeline direct case,
`completeLifecycleStillRefusesCacheWhenSupplementaryProviderThrows`.
Independent second review found no blocker. The direct lane has no sleep/skip;
one existing bounded 20 ms TTL wait remains supporting-only.

Evidence integrity:

- `summary.txt` SHA-256=
  `45f9cc2ab69f6e63434606be645b607a430869ca1a6ff073ce16a709163c40d7`
- source manifest SHA-256=
  `50f3dc8fd40a9bde4d88f5487318730f54a8be22eeeb011ff0cbfb2fe207849b`
- outer `SHA256SUMS` SHA-256=
  `e7014adaaa1932d62993d2a051830e9bb3f3b2fb4c7a982f356b32fc67d04ad7`

Both manifests passed independent `sha256sum -c`; both Maven lanes have exactly
one `BUILD SUCCESS`; all seven XML reports are fresh. Only the run above is
cited as Step 5 authority.

Checkpoint decision: `CACHE-GEN` and `REAL-QUERY` passed；Step 7 aggregate later
passed，Batch 6 completed；当时 Batch 7 ready/not-started，现已 completed。

## Batch 6 Step 6 REAL-QUERY Evidence

Authoritative command/run（本次文档回写未重跑）：

```bash
scripts/verify-v933-batch6-real-query.sh
# target/v933-batch6-real-query/runs/20260714T041047Z-2755326/
```

| Serial lane | Reports | Tests | Failures/Errors/Skipped |
|---|---:|---:|---:|
| model lifecycle / SQLite | 1 | 4 | 0/0/0 |
| required SQLite parity | 1 | 1 | 0/0/0 |
| required MySQL 5.7 parity | 1 | 1 | 0/0/0 |
| required PostgreSQL 15 parity | 1 | 1 | 0/0/0 |
| production Caffeine provider | 1 | 2 | 0/0/0 |
| production Redis provider | 1 | 2 | 0/0/0 |
| **total** | **6** | **11** | **0/0/0** |

The authority proves atomic old/new refresh with sibling preservation；same-name
namespace A/B isolation；datasource rebind admission, pinned-lease drain and
stale-publication rejection；Pivot native `SUM` parity and lifecycle cache
rotation；and direct rows/columns/order/values parity on SQLite 3.42, MySQL 5.7
and PostgreSQL 15.17. Caffeine and Redis are selected through production Spring
auto-configuration, including an explicit L1-off L2 flow. The run-scoped Redis
7.4.6 container begins and ends with zero keys and is removed.

Every lane has exactly one `BUILD SUCCESS`, one target class and fresh Failsafe
artifacts. Source/inner/outer manifests verify. Independent post-run review
found no blocker and promoted `REAL-QUERY`.

Summary/source/inner/outer SHA-256:

- `5602a3a75b6fb99d938ace19f7ca6b6528d0a9876fc2f799db14d24df5604f9e`
- `95aa4d9d5fbe31465724ad87eb704b531e04a4a2be0393f1a0b8afc2baf61bb0`
- `071987f7df30a3d08a6a194bf77f39a0861853ce36470db19090a36fd351f1aa`
- `6464baeda9e899736f3031dea27f1ceafc4a0b72e3863dbcafcbce499ce17cfe`

The Step 7 aggregate exit subsequently passed with the frozen accounting below.

## Batch 6 Step 7 Aggregate Exit Evidence

```bash
scripts/verify-v933-batch6-exit.sh
# target/v933-batch6-exit/runs/20260714T045604Z-2854237/
```

| Boundary | Result |
|---|---:|
| strict ordered children | 11 |
| criterion tests | 676 |
| asserted XML testcases | 677 |
| asserted reports | 99 |
| failures/errors/skipped | 0/0/0 |
| expected-negative | 4/4 |
| remaining red sources/tests | 0/0 |

Children 01–11 are Catalog 53/7、Cache Identity 75/6、cross-JVM 1/1、Pivot
99/7、REAL-QUERY 11/6、Batch 5 90/19、Batch 4 current 142/22、Batch 3
current 168/20、entry positive 5/5、Batch 2 criteria 32 / XML 33 in 6 reports、
remaining-red 0/0. Batch 2's extra XML testcase is the executed deterministic
harness probe and is intentionally excluded from the 32 `NS-SCOPE` criteria.

All child initial/final manifests, source/aggregate manifests, dirty state and
fixed/run-owned container before-after checks passed. Both expected run-owned
Redis containers were removed. Two independent read-only reviews found no
blocker.

Aggregate summary/children/source/inner/sealed/outer SHA-256:

- `10d72c2daa76361861a3796d0bc5699e1f09fecd53f82fa4b185b05ec8c803b1`
- `b2ddfc24c44c762464fb4df54a5532b58a51f433fedbbf030239b871dc4e052a`
- `e22630c9b1ccbc102861e1396bc9c55a68bab6d5557ada6a5c1fa86f0fdfdea1`
- `3318f2e69e3d41de57886a56cd70f23e0dbf76d53186f8a32f7eb5820366223f`
- `81a6456852fdb41f14eea316a60f4eea2f27b632dfc710c637a38f7435edab6d`
- `fc73ce9e1e19ce14c2733706daf720bf3af6a7fa35688fad54c43285ea2a43a9`

Batch 6 is completed. First Batch 7 run `20260714T074009Z-3153871` remains
superseded. Replacement authority `20260714T084351Z-3271604` passed and the
ordered self-check, formal quality, coverage and acceptance gates completed.

## Real Query Fixtures

至少准备：

1. 同一 namespace 的 old/new model fixture：相同查询返回可区分 version sentinel，并各有等价原生 SQL。
2. namespace A/B 同名模型：数据互斥，A refresh 期间 B 结果和 generation 不变。
3. datasource binding old/new：两个物理数据库有互斥 sentinel，rebind 前后 identity 与返回值一致对应。
4. model X/Y sibling：只刷新 X 时 Y 仍由 candidate 完整保留。
5. binding remove/disable：mutation 前先阻塞一条已持有 lease 的查询，commit 后发起新查询，分别证明 bounded drain 与新查询 fail closed。

每次 QueryFacade 结果必须比较记录数、列、排序和 sentinel；禁止出现 old/new 混合行。

## Required Database Scope

| DB | 9.3.3 role | Requirement |
|---|---|---|
| SQLite | fast deterministic real-query gate | required |
| MySQL 5.7 | external identity/binding + regression | required |
| PostgreSQL 15 | second external identity/binding + regression | required |
| SQL Server 2022 | preserve existing MultiDatabaseQueryTest | regression, not 9.3.4-A lifecycle proof |
| MySQL 8 | full 9.3.4 matrix | deferred |

## Cache Evidence

- Caffeine：passed in Step 2；同代命中，catalog/binding 任一变化必 miss。
- Redis strong key：passed in Steps 2/3；两个独立 application contexts 对同一完整 identity 生成同 key，generation 变化轮换两层 key。
- 跨 JVM：passed；run `20260714T025444Z-2628329` 覆盖 production auto-config template/provider、snapshot-derived exact resolution、2 child JVMs/27 probes、same binding、old physical-key read controls=2、current identity misses=2、post-write hits=2、Redis `SCAN`=`DBSIZE`=4 与两层 hash checks。
- routing/unknown generation：passed in Step 2；明确 no-key/no-cache，不用 instance hash 或宽 key 补偿。
- Pivot：passed；Step 5 run `20260714T032135Z-2678670` = direct 38/4 + supporting 61/3，99/99 green；full SHA-256 lifecycle identity，provider/manual additive only，refusal/provider exception no lookup/store。
- end-to-end provider lifecycle：passed；Step 6 run `20260714T041047Z-2755326` 以 production Caffeine/Redis auto-configuration 各执行 2 个 Failsafe tests，覆盖 L1 与显式 L1-off L2 的 miss/write/hit→generation miss/write/hit；real Redis cleanup verified。

## Baseline Regression Commands

执行前由 Gate 0 固化最终 profile；现有基线至少包括：

```bash
mvn -pl foggy-fsscript -Dtest=DynamicBundleManagementTest,NamespaceBundleTest,RootFsscriptLoaderTest,DynamicBundleLifecycleTest test

mvn -pl foggy-dataset-model -P!multi-db -Dspring.profiles.active=sqlite \
  -Dtest=NamespaceContextTest,BundleLifecycleListenerTest,SyntheticMemberQueryModelLifecycleTest,TableModelLoaderManagerImplDataSourceResolutionTest test

mvn -pl foggy-dataset-model -P!multi-db test

mvn test -pl foggy-dataset-model -P!multi-db -Dtest=MultiDatabaseQueryTest -Dspring.profiles.active=docker
mvn test -pl foggy-dataset-model -P!multi-db -Dtest=MultiDatabaseQueryTest -Dspring.profiles.active=postgres
mvn test -pl foggy-dataset-model -P!multi-db -Dtest=MultiDatabaseQueryTest -Dspring.profiles.active=sqlserver
```

完成 Gate 0 后，生命周期 IT 必须由 `verify` 运行，目标形态：

```bash
mvn -pl foggy-dataset-model -P!multi-db -Dspring.profiles.active=sqlite \
  -Dit.test=CatalogRefreshQueryIT,NamespaceCatalogIsolationIT,DatasourceBindingGenerationIT verify
```

## Final Evidence Summary

截至 2026-07-14 Batch 7 compatibility/regression authority：

- Gate 0 probe unit: 1
- Gate 0 probe/DB IT: 4
- Gate 0 positive failures/errors/skipped: 0/0/0
- Gate 0 expected-negative cases: 4/4 passed
- required DB preflight: SQLite 3.42 / MySQL 5.7 / PostgreSQL 15.17 passed
- Batch 1 expected-red baseline: 10 suites / 12 tests / 12 assertion failures / 0 errors / 0 skipped（run `20260713T115746Z-1058249`）
- Batch 2 NamespaceScope historical exit: 25 product + 7 compatibility / 0 failures / 0 errors / 0 skipped（run `20260713T130626Z-1313396`）；aggregate current replay 32 criteria + 1 harness probe = 33 XML testcases / 6 reports（run `20260714T045604Z-2854237-10-batch2`）
- Batch 3 Catalog/Binding historical exit: 149 / 0 failures / 0 errors / 0 skipped（run `20260713T150948Z-1719636`）；aggregate current-source replay 168 / 20 reports / 0/0/0（run `20260714T045604Z-2854237-08-batch3`）
- Batch 4 Single-flight historical exit: 139 / 0 failures / 0 errors / 0 skipped（run `20260713T164144Z-1910217`）；aggregate current-source replay 142 / 22 reports / 0/0/0（run `20260714T045604Z-2854237-07-batch4`）
- Batch 5 Atomic Refresh current aggregate replay: model 32（unit 30 + SQLite Failsafe IT 2）+ fsscript 7 + Runtime 34 + MCP 17 = 90 / 19 reports / 0 failures / 0 errors / 0 skipped（run `20260714T045604Z-2854237-06-batch5`）
- latest 9.3.4-A replay: positive 5 / expected-negative 4/4（run `20260714T052029Z-2908765`）
- Batch 6 Catalog Authority: direct 17（model 11 + MCP authority 5 + Spring wiring 1）+ supporting consumers 36（resolver 11 + ListModels 21 + controller 4）=53 tests / 7 owning reports / 0 failures / 0 errors / 0 skipped（run `20260714T021057Z-2445113`）
- Batch 6 Step 2 strong identity: model 4 + addon 68 = 72 / 0 failures / 0 errors / 0 skipped；model/addon log SHA-256=`c139540a97d1e7fe55ee4bbe5aa200d0b3e5ee250aa3ebf97ced41b8dfdcc048` / `370481a259c84376fc9e83ea84586534c94c0c4cae47d93d3d2f6e5ce97b555a`
- Batch 6 Step 3 two contexts: 1 / 0/0/0；same complete identity same L1/L2 keys across distinct Spring instances，catalog/binding mutation rotates both
- Batch 6 Step 4 cross-JVM: authoritative run `20260714T025444Z-2628329` = 1 test / 1 owning report / 0 failures / 0 errors / 0 skipped；2 child JVMs、production auto-config、snapshot-derived exact resolution、27 probes、same binding、old physical-key read controls=2、current identity misses=2、post-write hits=2、Redis `SCAN`=`DBSIZE`=4、two-layer hash checks；independent second review no blocker
- Batch 6 Step 5 Pivot/CACHE-GEN: authoritative run `20260714T032135Z-2678670` = direct 38 tests / 4 reports + supporting 61 tests / 3 reports = 99/99 green；failures/errors/skipped=`0/0/0`；provider-exception direct case included；independent second review no blocker
- Batch 6 Step 6 REAL-QUERY: authoritative run `20260714T041047Z-2755326` = 11 tests / 6 Failsafe reports / 0 failures/errors/skips；model lifecycle 4 + required SQLite/MySQL 5.7/PostgreSQL 15 parity 3 + Caffeine/Redis 4；independent post-run review no blocker
- Batch 6 Steps 2/3 fresh replay: authoritative Step 7 child run `20260714T044313Z-2824177` = current Step 2 74 + Step 3 one = 75 tests / 6 reports / 0 failures/errors/skips；independent post-run review no blocker；diagnostic `20260714T044020Z-2817047` excluded
- Batch 6 aggregate exit: authoritative run `20260714T045604Z-2854237` = 11 children / 676 criteria / 677 XML testcases / 99 reports / 0 failures/errors/skips / 4 expected-negative / 0 remaining red；source/child/inner/outer/dirty/container checks passed；two independent reviews no blocker
- remaining expected-red: 0 cases / 0 tests / 0 sources（aggregate child `20260714T045604Z-2854237-11-remaining-red`）；历史 entry run `20260713T202645Z-2141955` 的 `CATALOG-AUTHORITY` 1/1 已提升
- 100-call actual build count: 1（99 waiters；residual flight=0）
- observed refresh generations/results: REF-01 success publishes once and each reader sees complete old/new identity/model/native result；REF-02 failure publishes zero and retains the current old query
- observed source revisions/binding admission/process epochs: committed source stale checks、exact/unknown scope admission、Runtime persisted epoch + MCP cold epoch + OPEN/RETIRING/CLOSED all passed within their owning criteria
- Batch 7 superseded history: run `20260714T074009Z-3153871`；3813/517/F0/E0/S3；excluded from final authority
- Batch 7 replacement authority: run `20260714T084351Z-3271604`；3824 tests / 519 fresh reports / failures/errors/skipped=`0/0/3`；SQLite exact allowlist three skips，all other lanes zero skip；independent audit no blocker
- Runtime API compatibility: 62 tests / 6 reports；real Controller 48 + refresh/validate/legacy supporting suites 10 + sanitizer 4；legacy envelope remains additive and lifecycle errors remain typed/sanitized/bounded
- SQL Server 2022 regression: `16.0.4236.2` / `foggy_test.dbo`；18 tests / 1 report / `0/0/0`；fixture counts before/after identical
- 9.3.1 isolation regression: 132 tests / 13 reports / `0/0/0`
- 9.3.2 auto-config/Launcher regression: 64 tests / 15 reports / `0/0/0`
- root package/artifacts: 25 reactor modules；zero tests executed；24 fresh main JARs；21 unique imports entries；zero duplicates/legacy entries；Launcher nested/local checksums 12/12
- passed through current checkpoint: Batch 5 `REFRESH-ATOMIC`、`REFRESH-FAIL`、`REFRESH-SCOPE`、`EVENT-CONVERGENCE`、`SOURCE-COMMIT`、`VALIDATE-ISOLATION`；Batch 6 `CATALOG-AUTHORITY`、`CACHE-CROSS-JVM`、`CACHE-GEN`、`REAL-QUERY`
- API compatibility boundary: `API-COMPAT` passed by the Batch 7 real Controller authority；Batch 5 SQLite REF-01/02 retain their narrower owning boundary while Step 6 independently supplies complete `REAL-QUERY`
- evidence gaps: low branch gap=`RECONCILIATION_LIMIT_EXCEEDED` 持续 8 轮 churn 尚缺 direct seam test；9.3.4 full five-DB/JaCoCo/CI evidence 不属于本版本完成声明
- test decision: replacement authority passed；coverage audit=`ready-with-gaps`；version acceptance=`signed-off / accepted-with-risks`
