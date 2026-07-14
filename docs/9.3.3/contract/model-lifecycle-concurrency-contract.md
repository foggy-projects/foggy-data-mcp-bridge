---
doc_role: lifecycle_contract
doc_purpose: Freeze observable snapshot, generation, single-flight, refresh, datasource and NamespaceScope semantics for 9.3.3.
version: 9.3.3
status: confirmed
created_at: 2026-07-13
updated_at: 2026-07-13
---

# 9.3.3 Model Lifecycle Concurrency Contract

## 文档作用

- doc_type: implementation-contract
- intended_for: lifecycle authority implementer / adapter implementer / cache consumer / reviewer
- purpose: 先冻结可观察不变量，再由执行 agent 按现有模块结构决定内部类拆分。

## Decision Status

| 状态 | 决策 |
|---|---|
| confirmed-upstream | per-namespace immutable snapshot、generation-aware single-flight、offline atomic refresh、NamespaceScope、9.3.4-A entry gate |
| inherited-security | 9.3.1 namespace/datasource fail-closed；binding 撤销后不得回落旧物理目标 |
| confirmed-9.3.3 | SourceRevision、generation-pinned lease/retire、observable enrichment 切代、跨 JVM cold-cache epoch、Runtime additive DTO、MySQL 5.7 + PostgreSQL 15 required preflight |
| confirmed-batch-1 | authority/package boundary、Runtime DTO 字段/nullability、错误码枚举、binding 状态机、`lease-drain-timeout-ms=60000` |
| pending | none |

本文件于 2026-07-13 完成 Batch 1 冻结。observable contract owner 为
`foggy-dataset-model` lifecycle authority；Runtime DTO/binding adapter owner 为
`foggy-runtime-api`；source event adapter owner 为 `foggy-fsscript`；MCP 仅消费
authority/adapter，不建立第二套 lifecycle authority。冻结后字段只允许 additive
兼容扩展；若必须改变本文件语义，须先回写 requirement、contract 和迁移证据。

## 0. Frozen Type and Package Boundary

9.3.3 先在现有模块内建立内部 authority，不提前宣称 9.4.0 SPI v2：

| 包/类型 | 冻结职责 |
|---|---|
| `com.foggyframework.dataset.db.model.spi.NamespaceScope`、`NamespaceContext.open(String)`、`NamespaceContext.openInherited()` | 唯一生产 ThreadLocal scope API |
| `com.foggyframework.dataset.db.model.lifecycle.identity`：`CatalogGeneration`、`SourceRevision`、`DatasourceBindingGeneration`、`DatasourceBindingIdentity`、`CatalogIdentity` | opaque、强类型、不可复用 identity |
| `com.foggyframework.dataset.db.model.lifecycle.catalog`：`CatalogSnapshot`、`CatalogCandidate`、`CatalogSnapshotStore` | per-namespace immutable catalog authority |
| `com.foggyframework.dataset.db.model.lifecycle.concurrent`：`ModelBuildKey`、`ModelBuildSingleFlight` | generation-aware build coordination |
| `com.foggyframework.dataset.db.model.lifecycle.refresh`：`CatalogRefreshCoordinator`、`CatalogRefreshRequest`、`CatalogRefreshResult` | detached build/validate/atomic publish |
| `com.foggyframework.dataset.db.model.lifecycle.port` | source、binding、admission/lease adapter ports；不得依赖 Runtime/MCP |

上述是最小稳定命名和依赖方向；纯内部 helper 可继续拆分。9.4.0 只在这些
契约经过 9.3.3/9.3.4 证据验证后，选择最小子集提升到 `model-api`。

## 1. Canonical Namespace

- scope 内部保留三态：unset、显式 default `""`、trim 后的 named namespace；catalog key 的 default 统一为 `""`。
- API 层的 default namespace 解析先完成，再进入 catalog；catalog 不允许 named namespace 回退到 global/default source。
- 每次查询入口创建 `NamespaceScope`；仅未显式指定 namespace 的嵌套加载继承外层，显式 default 必须遮蔽外层 named namespace。
- `NamespaceContext.open(String)` 表示显式 scope：`null`/blank 均规范化为显式 default；`openInherited()` 在已有 scope 时继承 canonical effective 值，在 root/unset 时进入显式 default。兼容 API 写入的 raw 值进入 scope 时规范化，但 close 必须精确恢复该 raw previous state。生产入口根据“参数是否提供”选择二者，不能仅以 trim 后是否为空判断。

## 2. CatalogSnapshot

每个 namespace 同时只有一个 active snapshot。snapshot 至少包含：

- canonical namespace；
- opaque catalog generation；
- 每个 model provenance 中 canonical、不可变的 `BindingKey -> DatasourceBindingIdentity` dependency set；同一 namespace 不假定只有一个 datasource；
- 构建该内容的 committed SourceRevision；
- discovered model names；
- TM、QM、synthetic QM 的只读映射；
- deterministic short-alias 双向索引；
- model provenance/依赖关系与构建诊断。

规则：

1. map/set/list 发布前做防御性复制，不能向 consumer 暴露 mutable backing collection。
2. 模型对象只在 candidate 内构建和初始化；publish 后不得继续补字段、alias 或物理列映射。
3. alias 由稳定模型集合和确定性顺序生成，不能取决于并发到达顺序。
4. query entry 先完成 model resolution；若触发 materialization，则等待其 candidate 原子提交，再捕获最终 snapshot/model reference，并把 catalog identity 带到整个执行上下文；嵌套 TM/QM 解析不得跨代。
5. 任何改变 TM/QM/alias/discovery/provenance 或查询结果的 lazy enrichment 都是可观察 publication，必须产生新 immutable snapshot 并推进 generation。只有不改变上述状态、cache/single-flight identity 或返回结果的内部 memoization 才可留在同代。
6. 任一 TM/QM dependency 或 builder 累计错误都会使 candidate 失败；禁止发布“根模型可用、关联模型缺失”的部分 QM。

## 3. Generation

- `CatalogGeneration` 标识 namespace 的一次已提交生命周期版本。
- `DatasourceBindingGeneration` 标识某个有效 datasource/backend binding 版本。
- generation 是 identity，不是展示时间；本轮 catalog generation 使用不可复用 boot epoch + monotonic sequence，binding generation 使用 persisted registry epoch/sequence；二者都不能在有效缓存寿命内复用。
- successful refresh 或 observable materialization commit：目标 namespace 的 catalog generation 恰好变化一次，且 after identity != before。
- failed/cancelled/stale refresh：active generation 不变。
- read/cache hit：generation 不变；single-flight waiter 返回 winner 实际提交的 catalog identity，自身不重复推进。
- model-scoped refresh 也提交一个新的 namespace generation，但必须保留未变 sibling。
- datasource binding generation 变化后，旧 generation 的 build result 不得发布；调用方重试或显式返回 refresh failure。
- catalog/source generation 的内部实现使用不可复用 process boot UUID + monotonic sequence；Runtime binding registry 使用持久化 registry epoch + monotonic sequence。外部格式不构成契约，任何调用方均不得解析。

## 4. Single-flight

single-flight key 至少包含：

```text
model kind + canonical namespace + canonical model name
+ catalog generation + committed source revision
+ canonical effective datasource/backend binding identity set
```

规则：

1. alias 在构造 key 前解析为 canonical model name；synthetic model key 包含其 canonical source/selector；binding set 按 BindingKey 排序并包含 backend id + binding generation，不由调用方猜测。
2. winner 在私有 builder state 中构建；waiter 共享同一个完成结果或同一个失败原因。
3. 完成后只移除当前 key 对应的同一 future，不能误删后来者。
4. winner 失败、超时或取消后必须唤醒全部 waiter，并允许下一次调用重新成为 winner。
5. publish 前复核 active catalog/binding generation；stale result 丢弃并按有界策略重试，不能写入新 snapshot。
6. 不同 key 必须可实际重叠执行；禁止退化为 manager 级全局 `synchronized`。
7. 检测同线程/同构建图的循环依赖并快速失败，禁止递归等待自身 future。

## 5. Atomic Refresh

统一 refresh 协议：

```text
capture source + binding view
  -> build detached candidate
  -> validate all requested/discovered models and dependencies
  -> verify input view is still current
  -> one atomic publish
  -> best-effort cleanup/telemetry
```

- refresh 按 namespace single-flight/串行；不同 namespace 可并行。
- namespace refresh 构建完整目标 namespace；model refresh 重建 requested models 及传递依赖，并复制验证未变 sibling。
- candidate 构建期间读请求继续使用 old snapshot。
- publish 是 active reference 的一次 CAS/等价原子替换；不得逐 map、逐 model 暴露。
- 任一模型加载/校验失败、source/binding 视图变旧或 publish 竞争失败时，不清空、不半发布。只有全部 effective bindings 仍有效时，新查询才可继续使用 old snapshot；binding mutation 的 admission 规则见第 7 节。
- 失败响应包含 namespace、before generation、目标、稳定错误码和失败模型，但不包含密钥/凭据。
- 成功响应包含 before/after generation、受影响 binding generation 摘要、refreshed/preserved model count 和耗时。

## 6. Source Events and Revision

- Runtime API、bundle add/remove、file change、datasource save/remove/rebind 最终调用同一核心 refresh contract。
- Runtime validate 在 detached source/catalog 中运行，不得通过临时 live bundle 污染 active snapshot。
- `SourceRevision` 是 committed source view 的 opaque、不复用 identity；snapshot/candidate 记录其实际 provenance revision，publish 前必须复核。
- bundle add/remove 与 file/import mutation 必须先一致更新 source registry、script cache 和 reverse-dependency index，再发布 affected namespace/source + revision；remove 事件仍可携带 removed bundle 作为诊断。
- file event 必须携带或可靠推导 affected namespace/dependency closure。未知 scope 不得静默全局 clear，也不得只记日志后无限期服务旧目录：无法证明不受影响的 catalog 标为 stale/admission-blocked，持久化健康诊断，直到显式 scoped/full atomic rebuild 成功。
- target namespace 事件不得改变其他 namespace generation。
- `clearAll/clearByNamespace` 可作为兼容/测试入口保留，但不得作为 9.3.3 生产事件或 Runtime refresh 的实现。

## 7. Datasource Binding

- resolver 对模型构建返回 generation-pinned binding identity，而不是会在旧 identity 下切换物理目标的 mutable handle。
- save/update/remove/disable/namespace rebind 必须推进受影响 binding generation；持久 registry 同步持久化 generation/epoch。
- rebind/remove/disable mutation commit 是 admission boundary：commit 后开始的新查询只能取得新 binding 或稳定失败，不能从 old snapshot 重新 acquire 旧 handle；candidate refresh 失败也不得回退。
- mutation 前已取得 lease 的 in-flight query 可在 bounded timeout 内使用原物理目标完成 drain，但不得 retry/reacquire；hard security revoke 可立即关闭旧 handle 并使在途查询失败。
- 旧 snapshot 为诊断/回滚构建可保留对象引用，但 binding admission 与 catalog active reference 分离；保留 snapshot 不等于允许新查询访问已撤销数据源。
- 一个 QM 引用多个 TM 时继续遵守既有“同一有效 datasource”约束；比较 binding identity，不依赖 wrapper 对象偶然相等。
- 无法给出稳定物理 identity/generation 的 routing datasource：查询可按既有能力执行，但缓存 fail closed；不得伪造可复用 generation。

冻结的 admission/lease 状态机为：

```text
OPEN
  ├─ ordinary mutation ─> RETIRING ── activeLeases=0 ─> CLOSED
  │                              └── deadline ─> REVOKED ─> CLOSED
  └─ hard security revoke ────────> REVOKED ───────────> CLOSED
```

- `OPEN` 是否允许新 lease 与底层 pool 是否因 idle cleanup 关闭是两件事；同 generation、同物理配置可以 lazy reopen。
- rebind 创建新 generation 的 `OPEN` handle，同时旧 generation 进入 `RETIRING`；remove/disable 不创建 current handle。
- mutation commit 是 admission linearization point；旧 generation 从该点起不再接受新 lease。
- mutation additive 参数 `revokeMode=DRAIN|HARD`，默认 `DRAIN`；只有明确安全撤销使用 `HARD`。
- 配置项固定为 `foggy.runtime-api.datasource-pool.lease-drain-timeout-ms`，默认 `60000`，合法范围 `1000..300000`；越界启动失败，不静默修正。deadline 到期强制 revoke/close 是不变量，不提供关闭开关。
- 测试必须使用受控 clock/scheduler 推进 deadline，禁止以 60 秒真实等待证明 drain。
- Runtime registry 升级时持久化 registry epoch、next sequence 和每条 record 的 binding generation；旧 v1 registry 首次加载需原子迁移，generation 不能因重启、remove/recreate 或 sequence 回拨而复用。
- MCP legacy `DataSourceManager` 适配同一 model binding/admission/lease port，但不把
  generation 写入现有 datasource credential JSON：每次进程启动创建不可复用 boot UUID，
  以 boot UUID + monotonic sequence 生成 binding generation；启动恢复按 canonical name
  排序分配新代，重启故意 cold identity。每次 configure（即使物理配置等价）、remove、
  re-add 都推进/退役对应代；普通变更和 HARD 撤销遵守同一 60 秒状态机。identity 不得由
  URL、username、password 或对象地址生成，Runtime persisted epoch 与 MCP boot epoch
  不得混作同一序列域。

## 8. NamespaceScope

- `NamespaceScope implements AutoCloseable`，由 try-with-resources 使用。
- open 保存精确 previous state（含 unset、默认 namespace 和 named namespace），close 后恢复 previous state。
- 嵌套必须 LIFO；异常路径和 early return 同样恢复。
- scope 只能由创建线程关闭；重复 close 不重复 pop，非法乱序快速失败。
- wrong-thread/out-of-order close 失败时不得修改 stack，owner 随后仍可按 LIFO 正常关闭；只有成功 close 后的重复 close 才是 idempotent no-op。
- 线程池任务结束后 ThreadLocal 必须为空；下一任务不得看到前一任务 namespace。
- `QueryFacade` 无 namespace 参数的重载使用 `openInherited()`；显式 namespace
  重载、`ModelResultContext`/`SemanticRequestContext`、QueryModelLoader 参数中的
  null/blank 仍按既有 API 语义表示显式 default。入口必须把 scope 解析出的
  canonical effective namespace 同时传给 loader/filter/cache，禁止 ThreadLocal 与
  实际模型 namespace 分叉。
- `NamespaceContext.setNamespace/clear` 仅保留兼容性并逐步停止生产调用；无 active
  scope 时保持旧值语义，active scope 内调用必须以
  `NAMESPACE_SCOPE_LEGACY_MUTATION_WHILE_ACTIVE` fail closed 且不修改 current/stack。

## 9. Cache and Catalog Consumers

- QueryFacade/metadata/catalog 在解析模型时把 catalog identity 与该模型实际 dependency binding identities 写入强类型执行上下文，不使用 request `extData` 冒充内部身份。
- L1/L2/Redis key 保留 9.3.1 的 namespace、requested/resolved model、datasource 和 security policy，并增加 catalog/binding generation。
- Pivot outer-cache freshness 同步消费 generation；显式 bundle token 可保留为额外 identity，不替代 catalog identity。
- generation 缺失、冲突或与 resolved model 不一致时返回 cache miss/no-write，不能放宽 key。
- model `SemanticModelCatalogService` 与 MCP `SemanticServiceResolverImpl`/`ModelCatalogService` 的 discovery/alias view 均按 namespace 从同一 snapshot 读取，不得保留独立 names/alias cache 或双边 invalidate authority。
- key 的可序列化 identity 不得包含对象地址/instance hash。本轮 catalog generation 使用不可复用 process/boot epoch，独立 JVM 或重启默认 cold miss；只有未来能证明 snapshot 内容和 binding identity 完全等价时才可复用跨 JVM key。

## 10. Runtime External Contract

- refresh/validate DTO 只追加字段，不删除、改名或改变现有字段/HTTP 成功失败语义。
- catalog generation、binding generation 和 SourceRevision 对外均为 opaque string；客户端只比较相等/不等。
- success 提供 before/after catalog generation、受影响 binding 摘要、refreshed/preserved count；cold start 的 before 可空。
- failure 提供稳定 error code、before generation、sanitized failed targets/diagnostics；after 为空，并明确 active-old 或 stale/admission-blocked 状态。
- nullable 字段在 Launcher 的 `NON_NULL` JSON 配置下可省略；客户端必须把 absent 与 null 等价。任何响应不得包含 secret 或可逆 JDBC identity。

### 10.1 Frozen DTO Additions

新增 `RuntimeCatalogState`：`ACTIVE`、`ACTIVE_OLD_PRESERVED`、
`STALE_ADMISSION_BLOCKED`、`ABSENT`。

新增 `DatasourceBindingGenerationSummary(String bindingKey, String backendId,
String generation)`；三个值均 non-null/nonblank/opaque，列表按
`bindingKey,backendId` 排序。该 DTO 禁止 URL、host、port、catalog、username、
credential 或 pool instance identity。

`ModelRefreshResponse` 保留现有 8 个字段及顺序，在 `warnings` 后追加：

| 字段 | nullability/语义 |
|---|---|
| `beforeCatalogGeneration` | nullable；cold start/尚无 active catalog 时为空 |
| `afterCatalogGeneration` | 成功 non-null；任一失败为 null |
| `sourceRevision` | capture 前可 null；candidate attempt 已开始后 non-null |
| `affectedBindingGenerations` | non-null sorted list，可空 |
| `refreshedCount` | non-negative，兼容期必须等于旧 `loadedCount` |
| `preservedCount` | non-negative |
| `durationMs` | 仅 attempt 尚未开始可 null |
| `catalogState` | API 正常产出 non-null |

旧 `clearedCaches` 保留，但 atomic refresh 成功时为 non-null 空列表；旧
`refreshedModels/loadedCount/failedCount/failures/warnings` 类型与含义不变。

`ModelValidateResponse` 保留现有 10 个字段及顺序，在 `warnings` 后追加
`beforeCatalogGeneration`、`afterCatalogGeneration`、`sourceRevision`、
`affectedBindingGenerations`、`catalogState`。validate success 必须
`after==before`（可同为 null）；invalid/failure 的 `after=null`，state 只描述
未被 validate 修改的 live catalog。旧 record constructor 通过显式 overload 保留。

新增 `RuntimeLifecycleFailureContext`：

| 字段 | nullability/语义 |
|---|---|
| `namespace` | non-null canonical namespace；default 为 `""` |
| `beforeCatalogGeneration` | nullable；failure 前没有 active catalog 时为空 |
| `afterCatalogGeneration` | failure 必须为 null；`NON_NULL` JSON 下省略 |
| `sourceRevision` | capture 前可 null；capture 后的失败 non-null |
| `catalogState` | non-null |
| `affectedBindingGenerations` | non-null sorted list，可空 |
| `failedTargets` | non-null、deduped、sorted logical model/binding keys，可空 |
| `diagnostics` | non-null `List<RuntimeLifecycleFailureDiagnostic>`，可空 |

`RuntimeLifecycleFailureDiagnostic(String target, String phase, String message,
String suggestedNextAction)` 中 `target`/`suggestedNextAction` 可 null，`phase`/`message`
non-null。最多返回 50 条，每条 message 最多 512 字符；`failedTargets` 最多 100 条。
target 只能是逻辑 model/binding key，禁止绝对路径、JDBC URL、host、catalog、username、
credential、pool identity 或 stack trace。datasource identity 统一替换为
`<redacted-datasource>`，绝对路径统一替换为 basename 或 `<redacted-path>`。

`RuntimeError` 在末尾依次追加 nullable `RuntimeLifecycleErrorCode lifecycleCode`
和 nullable `RuntimeLifecycleFailureContext lifecycle`，保留旧 8 参数 constructor。
request validation 继续使用旧 `INVALID_REQUEST` 且两个新增字段均 null；进入 lifecycle
attempt 后的 validate/refresh 失败两个字段均 non-null。旧 `error.code` 分别继续是
`MODEL_VALIDATE_FAILED`/`MODEL_REFRESH_FAILED`，HTTP 200、`success=false`、
`data=null` 语义不变。failure 的 generation/target/state 由 `error.lifecycle` 承载，
不能放到 null `data` 中；envelope `diagnostics` 即使为兼容保留，也必须执行同一脱敏，
不得成为旁路泄漏。

Runtime adapter 内部的 `RuntimeModelOperationException` 同步追加 nullable
`lifecycleCode`/`lifecycle` 字段与 accessor，并保留旧 constructor 委托 null；
`RuntimeModelsController` 只做无损映射，不在 Controller 重新推断 generation、state
或 failed targets。

## 11. Observable Failure Codes

`RuntimeLifecycleErrorCode` 固定为：

- `CATALOG_BUILD_FAILED`
- `CATALOG_VALIDATION_FAILED`
- `CATALOG_CANDIDATE_STALE`
- `DATASOURCE_BINDING_NOT_CURRENT`
- `SINGLE_FLIGHT_CYCLIC_DEPENDENCY`
- `NAMESPACE_SCOPE_MISUSE`
- `REFRESH_SCOPE_UNKNOWN`
- `SOURCE_REVISION_STALE`
- `DATASOURCE_BINDING_REVOKED`

`DATASOURCE_BINDING_NOT_CURRENT` 的 sanitized diagnostics 仅允许进一步标识
`CHANGED` 或 `UNAVAILABLE`；`CATALOG_CANDIDATE_STALE` 仅表示 active generation/CAS
竞争，`SOURCE_REVISION_STALE` 仅表示 committed source view 改变。catalog admission
状态由 `catalogState=STALE_ADMISSION_BLOCKED` 表达，不能回显底层连接信息。

## 12. Compatibility Boundary

- 本契约先留在现有 `foggy-dataset-model` authority 内，不宣称为 SPI v2 稳定公共面。
- 旧 loader 方法可委托新 lifecycle，但同一调用只能有一个权威 cache/snapshot，禁止双写两套可变目录。
- 9.4.0 抽模块时再把已验证的最小 identity/refresh port 提升到 `model-api`。
