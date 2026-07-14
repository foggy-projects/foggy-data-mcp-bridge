---
doc_role: code-inventory
doc_purpose: Record planned 9.3.3 code touchpoints, ownership and protected boundaries.
version: 9.3.3
status: completed
created_at: 2026-07-13
updated_at: 2026-07-14
---

# 9.3.3 Code Inventory

## 文档作用

- doc_type: code-inventory
- intended_for: release owner / execution agent / reviewer
- purpose: 提前定位并发状态、刷新入口、binding adapter、cache consumer 和测试门，具体类拆分由执行 agent 在 contract 内决定。

## 受保护基线

- 当前 `.gitignore`、9.3.1/9.3.2 代码、测试和文档有大量未提交/未跟踪改动，全部视为用户成果。
- Gate 0 先记录可追溯 baseline（至少 commit/patch bundle/worktree manifest + 文件校验），再开始生产改动。
- 禁止 reset、checkout、clean、覆盖式复制或以 clean worktree 假设重建 9.3.1/9.3.2。

## 当前风险触点

| 路径 | 当前行为 | 9.3.3 目标 |
|---|---|---|
| `TableModelLoaderManagerImpl` | 已接入 exact per-key single-flight、detached candidate、有界 stale retry、binding-currentness final guard，并由 Batch 5 coordinator 完成 namespace/model refresh staging | Batch 6 验证完整 consumer parity；Batch 7 正式回归 |
| `QueryModelLoaderImpl` | 已统一 normal/synthetic alias key、dependency provenance、原子 final guard；Batch 5 configured refresh 在 request `NamespaceScope` 中构建，已知 binding 即使 provenance 不完整也必须复核 | `CATALOG-AUTHORITY` 与 `REAL-QUERY` consumer 已完成；Step 7 replay 后交 Batch 7 formal regression |
| `NamespaceContext` + QueryFacade/Semantic/loader callers | Batch 2 已完成 scope；QueryFacade 已把 exact catalog/model/binding resolution 原子 pin 到 execution context，Step 2 model 4 tests 复核 | completed；作为 cache/Pivot/real-query pinned identity 边界 |
| `RuntimeModelOperations.refreshModels` | 已改为调用核心 offline build + atomic publish；失败返回 typed lifecycle context；validate 使用 detached source，不注册 live bundle | Batch 7 关闭最终 `API-COMPAT` 与正式门 |
| `DbModelFileChangeHandler` | 已按 affected namespace/model 调用 coordinator；未知 scope 阻断已物化 namespace admission；无 production clear-first | Batch 7 正式回归 |
| `BundleLifecycleListener` / `SystemBundlesContextImpl` | add/remove 在 source commit 后发布事件并触发 namespace refresh；refresh correctness 不依赖 cache clear | Batch 7 正式回归 |
| `SemanticModelCatalogService` | Batch 6 已改为 complete active snapshot 直接投影、absent/incomplete 只经 coordinator 恢复；immutable view 同代携带 names/aliases/models/exact binding resolutions；native metadata 有界 seqlock | `CATALOG-AUTHORITY` completed；Batch 7 formal review only |
| MCP `SemanticServiceResolverImpl` / `ModelCatalogService` | Batch 6 已删除独立 names cache/watcher authority，共用 model namespace view/pinned resolutions；MCP metadata 有界 seqlock | `CATALOG-AUTHORITY` 与 consumer parity completed；Batch 7 regression |
| `RuntimeDatasourceRegistryService` / pool manager | 已持久化 epoch/sequence/generation；save/remove/bind 在 pool callback 前阻断受影响 catalog，再于 mutation monitor 外刷新；logical key canonical migration 与 credential-preserving controller boundary 已验证 | Batch 6 complete consumer proof；Batch 7 mutation failure/API review |
| MCP `DataSourceManager` / resolver | 已提供 cold generation、pinned pool、admission/lease、exactly-once retire，并在 configure/remove commit 后阻断及刷新受影响 catalog | Batch 6 consumer proof completed；Batch 7 regression |
| `QueryCacheKeyBuilder` / Caffeine / Redis | Steps 2/3 已完成 complete catalog/source/exact binding L1/L2 key、incomplete no-key、two-context equality/rotation；无 instance UUID/object-address fallback；Step 4 production-path authority passed | `CACHE-CROSS-JVM` passed；与 Step 5 Pivot evidence 共同关闭 `CACHE-GEN` |
| Pivot outer cache | 已使用 full SHA-256 catalog/source/canonical model/exact sorted bindings identity；provider/manual token additive only；provider failure no lookup/store | Steps 5–7 passed；Batch 7 regression |
| root/model POM 与 workflows | 9.3.4-A dedicated Surefire/Failsafe/no-test/DB gate 已完成；release 仍有完整矩阵/coverage skip 通道 | 9.3.4 full gate 收口 |

## Batch 3 Actual Code Inventory

| Module | Actual surface | Batch 3 result | Remaining owner |
|---|---|---|---|
| model | `lifecycle/identity`, `lifecycle/catalog`, `lifecycle/port` | immutable catalog, candidate, provenance, strong catalog/source/binding identities and admission/lease ports | Batch 4 flight/currentness completed；Batch 5 refresh |
| model | `TableModelLoaderManagerImpl`, `QueryModelLoaderImpl`, `JdbcQueryModelBuilder`, `QueryModelLoader` | shared authority、single datasource pin、partial-QM/external-object fail closed、additive strong resolver defaults | Batch 4 keyed concurrency completed；Batch 5 coordinator |
| model | `QueryFacadeImpl`, `ModelResultContext`, `SemanticServiceV3Impl`, `SemanticModelCatalogService` | atomic query pin、one-final-snapshot metadata、no global model-name cache | Batch 6 remaining MCP/Pivot consumers |
| model | `DbModelAutoConfiguration`, named datasource resolver SPI/adapter | shared store wiring and strong binding resolution | Batch 5 refresh wiring |
| runtime-api | registry/resolver/pool/properties | persisted non-reused generations、pinned handles、OPEN/RETIRING/CLOSED、DRAIN/HARD | Batch 5 Runtime refresh/DTO |
| dataset-mcp | datasource manager/resolver/controller/persistence | cold generation domain、fresh pool per commit、bounded retire、sanitized logical identity | Batch 5 notification；Batch 6 catalog consumer |
| cache Addon | `QueryCacheKeyBuilder` + Caffeine tests | strong catalog/binding key support | Batch 6 complete no-fallback/Redis/Pivot/cross-JVM |
| root/tests | `verify-v933-batch3-catalog-binding.sh` and owning model/runtime/MCP/cache suites | 149-test fresh-report/count/hash/source-audit authority | Batch 7 formal regression/gates |

## Batch 4 Actual Code Inventory

| Module | Actual surface | Batch 4 result | Remaining owner |
|---|---|---|---|
| model | `lifecycle/concurrent/ModelBuild*` | exact six-dimension/canonical binding-set key、caller-inline winner、shared result/Throwable、precise removal、same-thread/self-wait cycle guard；无自有 executor | Batch 7 telemetry/lock-duration review only |
| model | `CatalogBuildView`, `StaleCatalogBuildException`, `CatalogSnapshotStore` | detached build outside namespace publication lock；exact base snapshot/source revision compare-and-publish；candidate always sealed | Batch 5 full refresh coordinator/source convergence |
| model | `BindingCurrentness`, `StaleDatasourceBindingException`, `DatasourceBindingResolver.publishIfCurrent` | final binding check 与 catalog swap 同一 adapter critical section；缺 guard 时 tracked publication fail closed | Batch 5 mutation→refresh convergence |
| model | TM/QM loaders、`ProcessLocalDefaultDataSourceResolver` | shared flight、bounded stale retry、dependency provenance、normal/synthetic canonicalization；default binding opt-in，不向旧 resolver 传空 namespace | Batch 5 refresh；Batch 6 consumers |
| runtime-api | registry/resolver binding publication adapter | 与 save/remove/bind 同 monitor；backend ownership、AOP self-proxy、external decorator recursion 和多 external domain 有确定性保护 | Batch 5 Runtime validate/refresh DTO |
| dataset-mcp | datasource manager/resolver publication adapter | 与 configure/remove 同 mutation monitor，rebind 在 catalog callback 内被阻塞 | Batch 5 affected refresh notification |
| tests/scripts | four single-flight suites、catalog/currentness/Runtime/MCP regressions、`verify-v933-batch4-single-flight.sh` | 139-test exact report/count/hash authority；100 callers/99 waiters/build=1/residual=0；source audits fail closed | Batch 7 formal regression/gates |

## Batch 5 Actual Code Inventory

| Module | Actual surface | Batch 5 result | Remaining owner |
|---|---|---|---|
| model lifecycle | `lifecycle/refresh/*`、`CatalogSnapshotStore` admission/failure markers、`CatalogCandidate` refresh plan | per-namespace coordinator、detached candidate、exact source/base compare、one atomic publication；late failure cannot downgrade a concurrent winner or newer block；Batch 7 prepare/freeze 已移出 binding mutation monitor且保留 final currentness | Batch 7 aggregate/formal review |
| model loaders/events | `TableModelLoaderManagerImpl`、`QueryModelLoaderImpl`、`DbModelFileChangeHandler`、`BundleLifecycleListener` | configured TM/QM refresh 在 request `NamespaceScope` 中 staging；known binding final guard 与 completeness/cache safety 分离；file/bundle 无 clear-first | Batch 6 catalog authority consumption |
| fsscript source | committed source revision registry、bundle add/remove events、file/import affected-scope events、external bundle recursive directory authority | source commit 先于 refresh event；source stale candidate 丢弃；unknown scope admission fail closed；Batch 7 `BUG-933-NEW-MODEL-WATCHER` focused GREEN 已补运行期新 `.qm`、新子目录、共享 root 和注册回滚 | Batch 7 aggregate regression |
| runtime-api | `RuntimeModelOperations`、lifecycle DTO/error/sanitizer、datasource registry/resolver/pool | detached validate、atomic refresh、latest catalog-state failure mapping；registry key canonical migration；mutation commit 后先 block catalog 再执行 pool callback；diagnostic key/value 与 opaque binding identity 脱敏 | `API-COMPAT` final gate in Batch 7 |
| dataset-mcp | `DataSourceManager`、named resolver、catalog convergence tests | configure/remove generation mutation 与 affected catalog block/refresh 收敛到同一 coordinator | Batch 6 unified catalog consumers |
| tests/scripts | `CatalogRefreshQueryIT`、Batch 5 owning suites、`verify-v933-batch5-refresh.sh` | run `20260713T200646Z-2120785`：90 tests / 19 fresh owning reports；model 32、fsscript 7、Runtime 34、MCP 17，failures/errors/skipped=0 | Batch 7 formal regression/gates |

Post-Batch5 compatibility replays：Batch 4 run `20260713T201031Z-2124453`
= 140 green；Batch 3 run `20260713T201525Z-2129344` = 166 green；entry
gate run `20260713T201941Z-2133081` = 5 positive + 4/4 expected-negative；
Batch 2 run `20260713T202421Z-2138021` = 25 product + 7 compatibility；
remaining-red run `20260713T202645Z-2141955` 仅保留 Batch 6
`CATALOG-AUTHORITY` 1/1 expected-red。

Batch 5 passed criteria 仅为 `REFRESH-ATOMIC`、`REFRESH-FAIL`、
`REFRESH-SCOPE`、`EVENT-CONVERGENCE`、`SOURCE-COMMIT`、
`VALIDATE-ISOLATION`。其 exit 时 `CATALOG-AUTHORITY`、`CACHE-GEN`、
`CACHE-CROSS-JVM`、完整 `REAL-QUERY` 仍归 Batch 6；后续 Catalog Authority
checkpoint 已提升第一项，见下节。`API-COMPAT` 仅为 supporting-pass，最终归
Batch 7。

## Batch 6 Catalog Authority Actual Code Inventory

| Module | Actual surface | Catalog checkpoint result | Remaining owner at checkpoint |
|---|---|---|---|
| model | `SemanticModelCatalogService`、`NamespaceCatalogView` | Spring 注入 shared store/coordinator；complete active snapshot 直接投影；absent/incomplete coordinator-only recovery；immutable identity/names/aliases/models/resolutions；exact binding provenance；empty/deletion、blocked/partial fail closed；native metadata bounded seqlock | Batch 7 formal review；无独立 catalog follow-up |
| dataset-mcp | `SemanticServiceResolverImpl`、`ModelCatalogService` | resolver 无 names cache/watcher/invalidation authority；MCP catalog 消费同一 namespace view 与 exact pinned resolutions；metadata bounded seqlock 防 old/new hybrid | Batch 6 cache/Pivot/real-query consumers |
| Spring wiring | MCP resolver/catalog tracked constructors | resolver 与 MCP catalog 共用一个 `SemanticModelCatalogService` authority bean，且无 legacy loader/bundle interaction；model service store/coordinator 注入由 model test/source audit 证明 | Batch 7 assembly regression |
| direct tests | `SemanticModelCatalogServiceTest`、`CatalogNamespaceAuthorityTest`、`CatalogAuthoritySpringWiringTest` | 11 + 5 + 1 = 17 tests / 3 owning reports；criterion-direct | Batch 7 regression replay |
| supporting tests | `SemanticServiceResolverImplTest`、`ListModelsToolTest`、`ListModelsCatalogControllerTest` | 11 + 21 + 4 = 36 tests / 4 owning reports；consumer compatibility | Batch 7 regression replay |
| runner/evidence | `target/v933-batch6-catalog/runs/20260714T021057Z-2445113/` | 53 tests / 7 fresh reports / failures/errors/skipped=`0/0/0`；names cache/watcher/direct candidate/sleep/red-source audits all zero | Batch 6 remaining criterion runners |

Evidence hashes：`summary.env`=
`62604e1053c328c3c219da2f8792cdcb1d9ab13878f5d8ad021055ea7ea21563`；
`SHA256SUMS`=
`5b017cb750b0d28f4df8cd1998b63f27f46edcad25e27b9edda37aaa13fda3a1`。
remaining-red replay `20260714T021251Z-2449309` 为 0 cases / 0 tests / 0
sources，`summary.tsv` SHA-256=
`690b22d6cad493e293c6d9bbd5d8c7f37d7b027f06c46d82ba2c9306ef7a5406`。

旧 run `20260714T014919Z-2373851` 仅为 diagnostic，已
`superseded-by-review`；其内部 `status=passed` 不计入本 criterion。在该
Catalog checkpoint 只把 `CATALOG-AUTHORITY` 标为 passed；Steps 2–4 的后续
状态见下一节。

## Batch 6 Steps 2–4 Cache Identity / Cross-JVM Review Inventory

| Module | Actual surface | Steps 2–4 result | Remaining owner at checkpoint |
|---|---|---|---|
| model execution | `QueryFacadeImpl`、`ModelResultContext`、`QueryFacadeCatalogIdentityTest` | exact catalog/model/binding resolution atomically pinned；conflicting repin、namespace/model/generation mismatch fail closed；historical 4，current fresh replay 6/0/0/0 | Step 7 aggregate；Batch 7 regression |
| cache key | `QueryCacheKeyBuilder`、`QueryCacheKeyBuilderStrongIdentityTest` | canonical L1/L2 catalog generation + source revision + exact binding set + security/query identity；missing/malformed/conflicting identity no-key；no instance UUID/object address fallback；12/0/0/0 | Step 5/6 fulfilled；Step 7 aggregate |
| cache providers | `CaffeineQueryCacheProvider`、`RedisQueryCacheProvider` and owning tests | Caffeine 30 + Redis 25 + SQLite datasource isolation 1 = 56/0/0/0；Step 2 addon focused total with key builder=68 | Step 5 fulfilled；Step 7 regression |
| Spring contexts | `QueryCacheKeyCrossApplicationContextTest` | two real contexts with different provider/builders；same complete identity same L1/L2 key；catalog/binding generation rotates both；1/0/0/0 | Step 5/6 fulfilled；Step 7 aggregate |
| cross-JVM/Redis | `RedisCrossJvmCacheIT`、`verify-v933-batch6-cache-cross-jvm.sh` | authoritative run `20260714T025444Z-2628329`：production auto-config template/provider、snapshot-derived exact resolution、2 child JVMs/27 probes/same binding、old physical-key read controls=2、current identity misses=2、post-write hits=2、Redis `SCAN`=`DBSIZE`=4、two-layer hashes；1/0/0/0；independent second review no blocker | `CACHE-CROSS-JVM` passed；Step 7 regression |

Evidence integrity:

- model/addon focused log SHA-256:
  `c139540a97d1e7fe55ee4bbe5aa200d0b3e5ee250aa3ebf97ced41b8dfdcc048` /
  `370481a259c84376fc9e83ea84586534c94c0c4cae47d93d3d2f6e5ce97b555a`
- Step 3 XML/log SHA-256:
  `cc252b5de28832631b19257245c831eaabaa0002769e6078379da770676c37cb` /
  `fa7e0f962eda61cb8f97801ccc2a458fed8e91c861e43d6660219b30eb119563`
- Step 4 authoritative root:
  `target/v933-batch6-cache-cross-jvm/runs/20260714T025444Z-2628329/`；
  owning XML SHA-256=
  `93e92cf913098bbd110da8b0a98fd2a16414b2fe615e7fc98edb9277dffd51f9`；
  `summary.env` SHA-256=
  `ff76b8a81360606e97a316797f40f7d145951c56e7e803911be030d4a3e5358e`；
  `SHA256SUMS` SHA-256=
  `a54b7a4b18b750a5afb99fc3ee47b91af4759d0420f60cf9fb4c28edb1950ed3`；
  source manifest SHA-256=
  `f7c53054262502c7a348ea93e0ed9e12d461e2c18c4dd2105563b03ab300eea6`。
  两层 hash check 通过；container cleaned；无 credentials。

`CatalogSnapshotStore` 的 boot UUID 是预期 process-cold catalog/source epoch，
不是 `QueryCacheKeyBuilder` fallback。权威 run 使用真实 published snapshot
派生 exact resolution；old physical-key read controls 只验证物理 Redis 值可读，
不代表 lifecycle resolution/provider 命中。`20260714T023304Z-2522929` 为 diagnostic
failed；`20260714T023442Z-2527116` 为 diagnostic/superseded；
`20260714T025247Z-2623994` product test green 但 runner model-segment parser
failed，亦为 diagnostic/superseded；三者均不计入 pass。

At the Step 4 checkpoint，`CACHE-CROSS-JVM` passed，Steps 2/3 使
`CACHE-GEN` 保持 direct partial。当前结论见下一节。

## Batch 6 Step 5 Pivot / CACHE-GEN Actual Code Inventory

| Module/surface | Actual result | Evidence role | Remaining owner at checkpoint |
|---|---|---|---|
| `PivotOuterCacheStrongIdentity` | length-framed full SHA-256 over catalog generation、source revision、canonical namespace/model、exact sorted dependency bindings；missing/incomplete/JDBC-empty/conflict refused | direct 4 strong-identity tests | Step 7 regression |
| `PivotPipeline` | one typed resolution for read/write；all refusal gates no lookup/store；provider/manual additive only；provider exception diagnostic class-only | direct 8 pipeline tests；provider-exception blocker closed by eighth case | Step 6 real-query consumer |
| `SemanticRequestContext` / `SemanticQueryServiceV3Impl` / managed relation | typed catalog resolution transported and pre-pinned before Pivot evaluation | direct Semantic context 20 + pipeline entry cases | Step 6 real-query consumer |
| `QueryFacadeImpl` / `ModelResultContext` | atomic pin；generation/model/namespace conflict before filters/query，no old-key store after switch | direct QueryFacade 6 | Step 6 real-query consumer |
| public Pivot records/provider/options | public record components、provider SAM、legacy `OuterCacheOptions` constructor and wiring retained | source audit + supporting SPI tests | Batch 7 API/regression |
| diagnostics/telemetry/integration | failure output sanitized；query/bundle/security identity telemetry and operational behavior retained | supporting 61 in 3 reports | Step 7 regression |
| runner/evidence | `target/v933-batch6-pivot/runs/20260714T032135Z-2678670/` | direct 38/4 + supporting 61/3 = 99/7，failures/errors/skipped=`0/0/0`；independent second review no blocker | Step 7 aggregate |

Evidence integrity：`summary.txt` SHA-256=
`45f9cc2ab69f6e63434606be645b607a430869ca1a6ff073ce16a709163c40d7`；
source manifest SHA-256=
`50f3dc8fd40a9bde4d88f5487318730f54a8be22eeeb011ff0cbfb2fe207849b`；
outer `SHA256SUMS` SHA-256=
`e7014adaaa1932d62993d2a051830e9bb3f3b2fb4c7a982f356b32fc67d04ad7`。
source/outer checks passed independently；direct sleep/skip=0；sensitive output=0。

Checkpoint decision：Steps 5–7 passed；Batch 6 completed；当时 Batch 7
ready/not-started、9.3.3 overall in-progress，现已 completed / signed-off。

## Batch 6 Step 6 REAL-QUERY and Step 7 Runner Inventory

| Module/surface | Actual result | Evidence | Remaining owner |
|---|---|---|---|
| model real-query tests | `ModelLifecycleRealQueryIT` 4：atomic refresh/sibling、same-name namespace isolation、binding rebind drain/fail-closed、Pivot native SUM + cache generation | authoritative REAL-QUERY model lane 4/0/0/0 | Batch 7 wider regression |
| required DB parity | `RequiredDatabaseQueryFacadeParityIT` on SQLite 3.42、MySQL 5.7、PostgreSQL 15.17；same test verifies rows/columns/order/values and complete catalog/binding identity | 3 tests / 3 Failsafe reports；sentinels 8/2、25/25、25/25 | Batch 7 wider regression |
| cache real-query | `QueryCacheLifecycleRealQueryIT` via production Caffeine/Redis auto-config；L1 and explicit-L1-off L2 miss/write/hit → generation miss/write/hit | 4 executions / 2 test classes x 2 providers；dedicated Redis 7.4.6 cleaned | Batch 7 wider regression |
| POM/profile | model `model-lifecycle` + addon `query-cache-real-query` Failsafe profiles；addon test-scope SQLite | exact non-default integration lanes；no normal test-surface widening | Batch 7 formal CI review |
| Step 6 runner | `scripts/verify-v933-batch6-real-query.sh` | run `20260714T041047Z-2755326`：11 tests / 6 reports，F/E/S=0；source + two-layer hashes；independent review no blocker | completed |
| Step 2/3 fresh runner | `scripts/verify-v933-batch6-cache-identity.sh` | run `20260714T044313Z-2824177`：current QueryFacade 6 + addon 68 + two contexts 1 = 75 tests / 6 reports；first run `20260714T044020Z-2817047` diagnostic/superseded | completed；aggregate child 02 replayed |
| predecessor runner drift | `verify-v933-batch3-catalog-binding.sh`、`verify-v933-batch4-single-flight.sh` | current QueryFacade 6 requires Batch 3 total 168 and Batch 4 total 142；fixed reports remain 20/22 | completed；aggregate children 08/07 replayed |
| aggregate runner | `scripts/verify-v933-batch6-exit.sh` | authority `20260714T045604Z-2854237`：11 strict children；676 criteria / 677 XML testcases / 99 reports / 4 expected-negative / 0 red；F/E/S=0；source/child/inner/outer/dirty/container checks passed；two independent reviews no blocker | completed；handoff Batch 7 |

Step 6 evidence：`evidence/batch-6/real-query-green-20260714.md`。Step 2/3
fresh replay evidence：`evidence/batch-6/cache-identity-replay-green-20260714.md`。
Batch 6 exit evidence：`evidence/batch-6/batch-6-exit-20260714.md`。

## Planned Code Inventory

| Repo/module | Path/capability | Role | Expected change | Notes |
|---|---|---|---|---|
| root/model | `pom.xml`, `foggy-dataset-model/pom.xml` | minimum test runner + direct dependency contract | update | Surefire/Failsafe 仅完成 9.3.4-A；model 直接消费 fsscript event 时显式声明依赖 |
| root | `scripts/verify-v933-entry-gate.sh`, `scripts/assert-v933-test-report.sh` | DB preflight + report/count gate | created in Gate 0 | root automation，禁止泄露连接凭据 |
| root | `.github/workflows/model-lifecycle-concurrency.yml` | dedicated required lifecycle gate | created in Gate 0 | 不替代 9.3.4 full CI |
| model | lifecycle/catalog capability directory | snapshot store, identity, build/refresh coordination | create | 放现有 model authority，不提升为 SPI v2 |
| model | `.../spi/NamespaceContext.java` and scoped companion | namespace scope | update | 保留兼容方法并创建 scoped companion，生产入口迁移 |
| model | `.../spi/TableModelLoaderManager.java`, `.../spi/QueryModelLoader.java` | compatibility loader ports | update | legacy 方法委托单一 catalog authority；禁止双写 |
| model | `.../impl/loader/TableModelLoaderManagerImpl.java` | TM builder/adapter | update | 移除全局锁/cache/counters 作为 authority |
| model | `.../engine/query_model/QueryModelLoaderImpl.java` | QM builder/adapter | update | 移除 mutable namespace cache/竞态 alias |
| model | `.../engine/query_model/JdbcQueryModelBuilder.java` | nested TM resolution | update | 固定 namespace/snapshot view；保留 builder ThreadLocal 清理 |
| model | `.../DbModelAutoConfiguration.java` | lifecycle bean wiring | update | 延续 9.3.2 显式 Bean/AutoConfiguration 边界 |
| model | `.../service/impl/QueryFacadeImpl.java` | query snapshot capture | completed for Step 2 | exact catalog/model/binding resolution atomically pinned into context |
| model | `.../semantic/service/impl/SemanticServiceV3Impl.java` | metadata snapshot capture | update | scope + pinned identity |
| model | `.../plugins/result_set_filter/ModelResultContext.java` | internal execution identity carrier | update | 强类型字段，不用 request extData |
| model | `.../semantic/service/SemanticModelCatalogService.java` | namespace catalog view | updated in Batch 6 Catalog checkpoint | coordinator-only recovery；完整 immutable identity/name/alias/model/resolution view；native bounded seqlock |
| model | `.../engine/query_model/DbModelFileChangeHandler.java` | file event adapter | update | affected scope → core refresh |
| model | `.../event/BundleLifecycleListener.java` | bundle event adapter | update | add/remove → core refresh；cache cleanup 非 correctness 条件 |
| fsscript | `.../loadder/AbstractFileFsscriptLoader.java`, `RootFsscriptLoader.java`, `FsscriptRemoveEvent.java` | affected-scope/source revision producer | update | 验证首次编译、import closure 与失效顺序，不把 model refresh 下沉到 fsscript |
| model | `.../engine/pivot/*OuterCache*` and pipeline | Pivot freshness consumer | completed in Step 5 | full SHA-256 lifecycle identity；provider/manual additive only；refusal/provider failure no cache I/O |
| fsscript | `SystemBundlesContextImpl`, bundle events, `FsscriptRemoveEvent` | committed source event | update | remove 后发布；携带 affected namespace/source revision |
| runtime-api | datasource registry/resolver/pool services | binding generation authority/adapter | update | generation persisted；old handle 不换物理目标 |
| runtime-api | `RuntimeModelOperations` + refresh DTO/diagnostics | detached validate + atomic refresh adapter | update | validate 不注册 live bundle；增加 before/after generation |
| dataset-mcp | datasource manager/resolver/controllers | named binding generation adapter | update | configure/remove 产生 generation 与 refresh notification |
| dataset-mcp | `SemanticServiceResolverImpl`, `ModelCatalogService` | unified catalog consumers | updated in Batch 6 Catalog checkpoint | 已移除独立 names authority/watcher，消费 shared view/pinned resolutions；MCP bounded seqlock |
| cache Addon | `QueryCacheKeyBuilder`, provider tests | Steps 2/3 retained；Step 4/5 passed | no instance fallback；unknown identity no-key；two-context/two-JVM evidence retained；`CACHE-GEN` passed |
| demo | test TM/QM/SQL fixture | old/new and namespace/datasource sentinels | create | 必要时更新既有 fixture；真实返回值与原生 SQL 对比 |
| model tests | namespace/catalog/lifecycle test directories | deterministic unit + SQLite IT | create | 必要时更新既有 harness；100 callers、failure、scope、atomicity |
| runtime/MCP/cache tests | owning module test directories | adapter/binding/cache integration | create | 必要时更新既有 harness；save/remove/rebind、two contexts/two JVM、Redis key |
| launcher tests | existing assembly/smoke area | black-box regression | update | 不承载实现 |
| docs | `docs/9.3.3` | execution/evidence package | create | progress 持续回写 |

## Read-only Analysis / Compatibility Targets

| Path/module | Expected change | Reason |
|---|---|---|
| `addons/foggy-dataset-model-mongo` | read-only-analysis | backend SPI v2/TCK 不在本轮；做统一 catalog 兼容回归，若发现 contract break 先更新 inventory |
| `addons/foggy-dataset-model-vector` | read-only-analysis | 同上；做未知 backend generation cache fail-closed 回归 |
| `addons/foggy-dataset-model-preagg` | read-only-analysis | 不重做 refresh strategy；只做 query model generation 回归 |
| `foggy-dataset-memory-grid-*` | read-only-analysis | 不扩 lifecycle ownership；只做兼容回归 |
| 9.3.1/9.3.2 unrelated implementation files | do-not-touch | 只允许 code inventory 明确列出的 9.3.3 必需增量，不做清理式重写 |

## Do Not Touch as 9.3.3 Design Work

- 不创建 `model-api/core/jdbc/starter/web` 新 Maven 模块。
- 不机械移动现有 `spi` 包，不冻结 BackendProvider/Addon TCK。
- 不新增 Query execution Stage/Phase 类型，不统一所有 bypass。
- 不把 lifecycle Service/Controller/DTO 放到 `foggy-mcp-launcher`。
- 不在 cache Addon 生成 catalog generation。

## Module Placement Verification

- 每个 create 目标都位于其 owning module：core lifecycle 在 model、source event 在 fsscript、registry adapter 在 runtime/MCP、test gate 在 root、fixture 在 demo。
- 依赖方向为 adapter/consumer → model，model → fsscript/dataset；没有要求 model → runtime/MCP/cache。
- 如果执行中发现必须反向依赖，先停更并更新本 inventory；不得用反射、静态全局或 Launcher 编排绕过循环。
