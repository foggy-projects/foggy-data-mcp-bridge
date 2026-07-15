---
doc_role: root_plan_review
doc_purpose: Record the reviewed dependency order and release gates from 9.3.1 through 9.4.0.
status: active
created_at: 2026-07-13
updated_at: 2026-07-15
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
| 9.3.4 | in-progress / Steps 1–2 passed / Step 3 in-progress | [执行文档包](../9.3.4/README.md)；five-DB foundation 已 diagnostic 10/F0/E0/S0，下一批先修 Pivot preagg 伪绿，再消费 exact 45 required + 1 optional deferred |
| 9.3.5 | queued | 仅在 9.3.4 version signoff 后标 ready |
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

当前状态：`in-progress / Steps 1–2 passed / Step 3 in-progress`；入口为
[`docs/9.3.4/README.md`](../9.3.4/README.md)。confirmed run
`step1-candidate-r8-20260714` 已冻结 532 sources、820 discovery rows、829 execution
keys、519 predecessor nodes/edges，28/28 expected-negative probes 通过；两路独立
复核 blocker=0。Step 2 保持该 baseline immutable，并将 59 个 zero-test outer report
typed 为 structural；confirmed r8e successor 为 `770 positive = 724 Step 2 + 46 Step 3`
和 519 typed predecessor refs。Surefire/Failsafe 实际通过 `5,205 testcase / F0/E0/S0`，
INT/TERM/HUP durable fail-closed probe=`130/143/129`；r8d 已作废。Step 3 以 exact 46
external executions 进入五库/Redis/Mongo matrix；9.3.5 仍为 queued。

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
