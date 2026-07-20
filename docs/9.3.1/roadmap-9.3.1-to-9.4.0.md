---
doc_role: root_plan_review
doc_purpose: Record the reviewed dependency order and release gates from 9.3.1 through 9.4.0.
status: active
created_at: 2026-07-13
updated_at: 2026-07-20
---

# 9.3.1 → 9.4.0 迭代顺序评审

## 评审结论

有条件通过，保持以下版本顺序：

```text
9.3.1 生产隔离与 fail-closed
  → 9.3.2 自动配置与 Addon 装配
  → 9.3.3 模型生命周期与并发
  → 9.3.4 测试与 CI 证据链
  → 9.3.5 引擎阶段与公共 API
  → 9.4.0 SPI v2 与模块化
```

唯一必要调整：把 9.3.4 的最小防伪绿基座前置为 9.3.3 的开工门；完整多数据库矩阵、覆盖率和发布证据仍由 9.3.4 收口。

## 当前推进状态

| 版本 | 状态 | 说明 |
|---|---|---|
| 9.3.1 | signed-off (`accepted-with-risks`) | 声明范围已交付，无 blocker/high；后续风险已分配到 9.3.2–9.3.4 |
| 9.3.2 | signed-off (`accepted-with-risks`) | feature scope 已签收，无 blocker/high；保留项已记录到 acceptance |
| 9.3.3 | signed-off (`accepted-with-risks`) | replacement authority `20260714T084351Z-3271604`：3824 tests / 519 reports / F0/E0/S3 exact SQLite allowlist；ordered quality→coverage→acceptance completed，无 blocker/high/medium |
| 9.3.4 | in-progress / r33 excluded / r34 pending | [执行文档包](../9.3.4/README.md)；formal-r9、r30、r31、r33 均 permanent excluded；r29 与 r32=`non-freezable`。r33 在 Unit pre-marker 区间 fail-closed，cleanup closure 未获证明；当前主线为 docs-only Cdiag→governed readiness preflight→fresh r34→new review/Cfreeze→fresh formal→post gates |
| 9.3.5 | queued | 仅在 9.3.4 version signoff 后标 ready；开工先执行 Gate 0 classification-debt migration |
| 9.4.0 | queued | 依赖 9.3.5 public API/去环结果，不提前拆生产模块 |

## 版本边界与完成门

### 9.3.1 生产隔离与 fail-closed

- 固化 Step 顺序，统一 `@Order` 与 `FoggyStep.order()` 语义，拒绝安全关键 order 冲突。
- 默认关闭测试/开发 Controller。
- 显式 named datasource 和 namespace binding 解析失败时 fail closed。
- L1/L2 缓存身份至少隔离 namespace、datasource、安全策略和模型 freshness。
- PreAgg 改写、分页和 L2 cache 必须共用同一最终执行身份。

准出：跨 namespace、跨 datasource、跨权限的真实查询回归全绿，且负向用例证明不会回落到默认源或宽缓存键。

### 9.3.2 自动配置与 Addon 装配

- 移除 Launcher 根包扫描及基础模块跨 Addon 扫描。
- 所有 Addon 使用 Boot 3 `AutoConfiguration.imports`、显式条件和 back-off。
- 修复 Mongo 错误注册及无条件 loader；修复 Vector 条件/顺序；修复 Cache controller/builder 绕过条件。
- 自动配置先切片，`model-api/core/jdbc/starter/web` 的物理拆分留到 9.4.0。

准出：在 `com.foggyframework` 包外使用 `ApplicationContextRunner`/`FilteredClassLoader` 的缺依赖、条件齐备、用户 bean back-off 和 launcher 打包启动矩阵全绿。

### 9.3.3 模型生命周期与并发

开工前置的 9.3.4-A：

- Surefire/Failsafe 最小分层。
- `fail-if-no-tests` 和 required DB preflight。
- 并发测试基座及真实数据库身份/版本断言。

本版本交付：

- 每个 namespace 一个不可变 `CatalogSnapshot` 与 generation。
- single-flight key 至少包含 namespace、model、generation 和 datasource binding generation。
- refresh 离线构建/验证，成功后原子切换；失败保持旧 snapshot 完整可用。
- `NamespaceScope implements AutoCloseable`，支持嵌套恢复、异常清理和线程池复用。

准出：100 并发同 key 只构建一次；刷新并发读只能观察完整 old 或 new；失败刷新不影响旧查询；目标 namespace 变更不清全局。

最终 checkpoint：Gate 0 与 Batch 1–7 全部完成。Batch 6 aggregate authority
`20260714T045604Z-2854237` 保留为 lifecycle criteria evidence；Batch 7 首次 run
因正式质量复核发现 watcher authority gap 而封存为 superseded，修复后 replacement
authority `20260714T084351Z-3271604` 完整回放 `3824 tests / 519 reports / F0/E0/S3`。
S3 为 exact SQLite allowlist，其余 lane S0；source/worktree/container、数据库
identity/fixture、24 个 main JAR、Launcher 12/12 nested checksums 和两层 manifest
均经独立复算。implementation quality=`ready-with-risks`、coverage audit=
`ready-with-gaps`，无 blocker/high/medium 或 critical/major evidence gap；version
acceptance=`signed-off / accepted-with-risks`。签收记录见
[`docs/9.3.3/acceptance/version-signoff.md`](../9.3.3/acceptance/version-signoff.md)。

### 9.3.4 测试与 CI 证据链

当前状态：`in-progress / r32 high-water recovery`；入口为
[`docs/9.3.4/README.md`](../9.3.4/README.md)。历史 Cfreeze `f97483a0…` / formal-r4 的 feature
acceptance 与 r24/Cfreeze `439aea5e…` 的 diagnostic/review 证据保留；fresh formal-r7 正确拒绝了
未进入 bridge Git tree 的 CALCULATE parity catalog，因而永久 `failed/excluded/non-reusable`。
repo-contained exact catalog、pre-test tracked blob/SHA gate、lifecycle seals 与 Step4→Step6 hash
closure 已完成；其后的 fresh r25 在 Cdiag `5aaffbb4…` 上 public-valid，但只验证了 r25-tested
schema 1 的 MySQL consumer set。follow-up audit 确认 `DatasetJdbcUtilsTest` 是第 7 个 consumer / 第
12 个 node 且旧测试吞掉 `SQLException`，所以 r25 永久是
`pre-remediation / superseded / non-candidate`。schema 2 修复后的 r26 与 direct-child Cfreeze
`7c18019e…` 已完成；fresh formal-r8 完成 `773+59/5707/F0E0S0 + Addon 2/6` 后，在 coverage
reporter 直接执行 Git `100644` Python 工具时以 rc126 fail closed。r8 永久 excluded；三处调用已统一
显式 `python3`，runner raw bytes/292-command stream 双封印、四工具/七调用 semantic gate、Git-mode
mutation 与 non-executable smoke 已通过，machine 重置为
`diagnostic-ready / diagnostic-pending`。r27 随后虽 public-valid 但 aggregate high-water 各少一，永久
`non-freezable`，其 candidate/capsule 均 non-canonical。r28 reviewed material was frozen into
direct-child Cfreeze `34cd2452…`, but fresh formal-r9 then failed at coverage-report/exit=`2`: strict umask
published the contractually public effective-POM receipt as mode=`0600`, so the reporter emitted `E_OUTPUT`.
r9 is permanently `failed/excluded/non-reusable/non-candidate`; r28 material and r9 partial output cannot be
combined into replacement authority. Recovery baseline `39032229…` makes public receipt mode explicit, adds a
strict-umask negative probe and resets machine state to `diagnostic-ready / diagnostic-pending`; it is not the
final Cdiag. f420 Cdiag 的 r29 诊断已完成，但其 local capsule 递归包含不应进入 Git 的运行期内容，
因此永久 `non-freezable / no-cfreeze-authority`；不得补录、重打包或将其与历史材料拼接。Git-safe
tooling Cdiag `7757aa36…` 的 r30 又在 successor binding 预检 fail-closed，零 lane authority 且永久
excluded。binding repair Cdiag `f80fadd6…` 的 fresh r31 随后在 Unit fixture 固定端口前置条件处 fail
closed，尚未进入 lifecycle probe、fixture、Maven Unit 或任何 lane；其派生资源和 outer cleanup 均为零，
不得接管或复用外部 listener。端口恢复后 fresh r32 完成全 lane，但 aggregate line=`54624/76830`、
branch=`26111/44870`、complexity=`17658/35571`；后两项各低于 reviewed high-water 一，故 r32 永久
`non-freezable`。r33 已在 Unit pre-marker 区间 fail-closed，不能复用。当前唯一后续链为：docs-only
clean/pushed Cdiag→governed readiness preflight→fresh all-lane diagnostic-r34（line >= `54624/76830`、branch >=
`26112/44870`、complexity >= `17659/35571`）→new candidate/Git-safe closure/
dual review→direct-single-parent Cfreeze→fresh formal successor→post gates.

以下仅记录已被 replacement recovery 撤回授权的 historical formal-r4 checkpoint：当时 post-formal
quality=`ready-for-coverage-audit / B/H/M/L 0/0/0/1`；coverage audit 在补齐同一
tested HEAD 的 Pivot legacy companion `1/F0E0S0` 后得到 25/25 workitem covered、
critical/major gap=`0/0`；feature acceptance=`signed-off / accepted / blocking none`。25 个
Step 4 workitem 当时已关闭，Unit MySQL classification DEBT 继续由 9.3.5 version acceptance 收口；
这些历史结论不授权当前 Step 5。

因此当前只允许完成 Step 4 replacement authority；`can_enter_step5=no`、coverage audit/acceptance 均未
开放。Step 5–7、9.3.4 version signoff、9.3.5 与 9.4.0 仍不得提前。formal-r6 永久 failed/excluded，
r22 只作历史 diagnostic evidence。

- Surefire 只跑 unit，Failsafe 只跑 integration/E2E，禁止同一测试重复或漏跑。
- required matrix：SQLite、MySQL 5.7、MySQL 8、PostgreSQL、SQL Server。
- 数据库不可用、0 tests、suite 缺失、超 skip budget 必须失败。
- 聚合 unit+IT 覆盖率并冻结基线后逐步提高；生命周期和隔离关键类设置独立门。
- release job 必须依赖完整矩阵并产出不可变 evidence artifact。

准出：PR、合并和发布链路不存在 `skip-external-db`/`-DskipTests` 形成的伪绿色通道。

### 9.3.5 引擎阶段与公共 API

- 沿用现有 `QueryExecutionPhase`，不另造与 `QueryStageType` 混淆的新 Stage。
- 所有外部查询路径统一经 QueryFacade，禁止 controller/runtime/addon 直接 loader + `model.query()`。
- 公共门面只暴露稳定 request/result DTO；context、JDBC engine、managed relation 下沉到 internal/advanced port。
- 通过 port 拆解 pivot/compose/semantic 的反向依赖，并按 planner/compiler/executor/catalog/adapters 拆大类。

准出：无未批准 bypass、无新增包循环、公共 API compatibility test 和阶段轨迹回归全绿。

### 9.4.0 SPI v2 与模块化

- 渐进抽取顺序：`model-api → model-core → model-jdbc → model-starter → model-web`。
- 旧 `foggy-dataset-model` 保留一个兼容周期，作为聚合/转发层。
- `BackendProvider` 使用稳定 backendId、能力描述和小型 factory/port，避免 mega-interface。
- 建立独立 Addon TCK，覆盖自动装配、provider 唯一发现、load/query、namespace 隔离、原子刷新、缓存失效和错误契约。

准出：Maven 依赖单向无环，`model-api` 无 Spring/JDBC/impl/web 依赖，所有 Addon TCK、starter context 和 launcher smoke 全绿，并有兼容性基线及迁移文档。

## 硬依赖关系

- 9.3.3 依赖 9.3.1 的 datasource 与缓存隔离语义，否则 snapshot 会固化错误回退。
- 9.3.5 依赖 9.3.3 的 generation/NamespaceScope 和 9.3.4 的强制回归门。
- 9.4.0 依赖 9.3.2 的自动配置边界及 9.3.5 的瘦公共 API/去环结果。

## 禁止提前实施

- 不在 9.3.2 直接完成物理模块大拆分。
- 不在 9.3.3 用全局锁或先清后热代替 snapshot 原子切换。
- 不在 9.3.5 再造第二套执行阶段枚举。
- 不在公共 API 未瘦身前把现有 `spi` 包机械搬入 `model-api`。

## 2026-07-19 formal-r7 portability checkpoint

- r24=`completed / diagnostic-observed / public-valid`，required=`773+59/5707`、exec/session=
  `23/48`、source exact、cleanup/DB restore PASS；candidate/capsule/双审完成；
- direct-child Cfreeze=`439aea5e…` 已 clean/pushed；fresh formal-r7 的 Unit 完整通过，但
  Integration `sqlite-broad` 因 CALCULATE parity catalog 只存在于仓外父目录而 fail closed；
- formal-r7 永久 `failed/excluded/non-reusable`；失败 capsule、四个 demo DB exact restore 与 cleanup
  已封存，r24/Cfreeze/r7 不得拼接成后续 positive authority；
- repo-local catalog exact blob/SHA、pre-test Git ownership/hash gate、focused `14/F0E0S0`、
  lifecycle 与 Step4→Step6 hash closure均已通过，pre-Cdiag quality=`0/0/0/0`；
- 下一动作是提交/push replacement Cdiag，完成 isolated positive/negative proof，并执行唯一 fresh
  diagnostic-r25；之后依次为 review/Cfreeze→fresh formal-r8。主线优先不变，安全工作仅限维持
  fail-closed gate。

## 2026-07-19 diagnostic-r25 Unit MySQL 7/12 checkpoint

- replacement Cdiag=`5aaffbb4cd217d3d891c22eca4d3ae31d4e9d6e7` 与 fresh r25 已完成；public
  observation=`01487f7e…e42a`，required=`773+59/5707/F0E0S0`、Addon=`2/6`、exec/session=
  `23/48`，source/cleanup/exact demo DB restore PASS；
- follow-up audit 证明 r7 `6 reports / 11 errors` 只是 immutable historical failure set，current
  known-consumer lower bound 是 `7 reports / 12 nodes`；r25 因 tested schema 1 不完整而
  `superseded / non-candidate`，不得生成 candidate/capsule 或 freeze；
- 测试 oracle 已改为异常传播和资源安全的 `SELECT 1` 精确断言；schema 2 分离 historical 6/11 与
  current lower-bound 7/12，contract negatives 扩为 42/42，并固定该 Java source 为 LF；
- pre-Cdiag machine/quality closure 已完成；下一动作是提交并 push new Cdiag，完成 isolated durable
  proof，再运行唯一 fresh diagnostic-r26→candidate/review→Cfreeze→formal-r8→post gates。
  9.3.4 签收后，9.3.5 只先进入
  Gate 0 classification-debt migration，债务关闭前不得执行 9.3.5 version acceptance。

## 2026-07-20 diagnostic-r32 high-water checkpoint

- 宿主 Docker 停止后，fresh strict-umask r32 完整通过 required=`773+59/5707/F0E0S0`、Addon=`2/6`、
  exec/session=`23/48`、source closure 与 cleanup=`0/0/0`；这确认固定端口不再是当前阻塞项；
- r32 的 line 已达 `54624/76830`，但 branch=`26111/44870`、complexity=`17658/35571` 各低于高水位一，
  所以只能作为 `diagnostic-observed / non-freezable`，不得进入 candidate、Cfreeze 或 formal；
- 唯一差异是 `WatchServiceFileTracer.java:442` 的过滤删除事件覆盖。修复仅扩展一个既有 mock-key 测试，
  五个 focused JVM 和完整 `foggy-core=97/F0E0S0` 均恢复预期 counter；生产 API/模块边界未变；
- r33 已消费该 Cdiag 的一次 fresh-run 授权，并在 Unit pre-marker 区间 fail-closed；其最终 fallback
  cleanup 非零不构成可用 cleanup closure，也不证明具体 Docker 根因。唯一后续是封存 r33→docs-only
  Cdiag→Docker/control-plane、固定端口与 run-owned scope 的独立预检→fresh r34。
- 主线仍优先完成 9.3.4。9.3.5 仍必须等待 version signoff 后的 Gate 0，9.4.0 仍必须等待 9.3.5 的公共
  API 瘦身与去环结果；两者均不提前开工。

## 2026-07-20 Step 4 evidence-ready / downstream static-baseline checkpoint

- fresh formal-r11、same-Cfreeze Pivot companion、final quality 与 35-row replacement audit 已完成；
  Step 4 replacement feature signoff 已由 `foggy-projects` accepted，Step 5=`ready / not-started`，但
  Steps 6–7 和 version signoff 仍未开放。
- 为避免下游在实际开工时依赖猜测，新增了 planning-only 的
  [9.3.5 code baseline](../9.3.5/code-inventory.md) 与
  [9.4.0 module/SPI baseline](../9.4.0/code-inventory.md)。这些记录没有改变生产代码、POM、测试
  inventory 或模块边界，也不使 9.3.5/9.4.0 变为 ready。
