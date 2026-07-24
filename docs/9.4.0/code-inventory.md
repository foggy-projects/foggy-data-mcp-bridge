---
doc_role: modularization_inventory_and_progress
version: 9.4.0
status: SIGNED_OFF / target-modules-validated
baseline_commit: 26081a3b4853914de8e6effe9a21b1353d590917
implementation_base_commit: 3b1c7249ba75b3bab54cb0f898ea1c198e5303d4
recorded_at: 2026-07-20
updated_at: 2026-07-24
---

# 9.4.0 SPI v2 与模块化静态基线

## 观察边界

初始记录来自 baseline commit 的 Maven/source-only 盘点；实施开始时已在精确
`origin/main` 基线 `3b1c7249ba75b3bab54cb0f898ea1c198e5303d4` 复算。下列“初始基线”保留历史计数，
“当前实现”记录已落地的物理边界与兼容证据。

## 初始模块与泄漏面

- 根 POM 当前声明 25 个 active reactor modules；目标 `model-api`、`model-core`、`model-jdbc`、
  `model-starter`、`model-web` 目前均不存在。
- 当前 `foggy-dataset-model` 内 `com.foggyframework.dataset.db.model.spi` 有 52 个 main Java
  types；模型模块外有 44 个 main-source importer，分布在 `addons`、`foggy-dataset-mcp`、
  `foggy-mcp-launcher` 与 `foggy-runtime-api`。
- 同一模型模块当前同时包含 `DbModelAutoConfiguration` 和 7 个 controller/exception-handler
  candidates，证明 API/core/JDBC/starter/web 还未物理分层。
- 现有 `foggy-mcp-spi` 是独立的 MCP tool SPI（4 个 main types），而
  `foggy-dataset-mcp/.../spi` 是 MCP 局部 port（5 个 main types）；二者都不能被假定为未来 model
  SPI v2 的直接替代或机械迁移来源。
- main source 中当前不存在 `BackendProvider`；它是未来 v2 的设计项，不是可复用的既有 contract。

## 未来边界：当前只冻结方向，不冻结类搬迁

| Future module | Intended responsibility | Must not depend on |
|---|---|---|
| `model-api` | stable DTOs, small ports, provider identity/capabilities | Spring, JDBC, implementations, web |
| `model-core` | planner/compiler/catalog/domain orchestration | JDBC/web adapters |
| `model-jdbc` | JDBC execution and backend adapters | web/starter |
| `model-starter` | Spring auto-configuration and back-off wiring | web controllers |
| `model-web` | HTTP/controller exposure | JDBC implementation details |

This table is a target constraint, not an approved package-to-module move list. The 52 existing model SPI types
must be classified individually after 9.3.5 exposes a stable public/advanced-port boundary.

## Future SPI v2 / Addon TCK matrix

| Capability | Later positive evidence | Later negative/compatibility evidence |
|---|---|---|
| provider identity and discovery | exactly one provider for a backendId | duplicate/missing/unknown provider fails closed |
| capability and small factory/port | declared capability routes to supported backend | unsupported capability is explicit, not fallback |
| model load/query | addon performs governed load/query through public port | direct/internal leakage is rejected |
| namespace isolation | independent namespaces remain isolated | cross-namespace reuse is rejected |
| atomic refresh / cache invalidation | lifecycle and cache identity survive refresh | stale cache or mixed generation is rejected |
| error contract | stable typed errors across providers | implementation-specific leakage is rejected |
| starter assembly | `ApplicationContextRunner`/filtered-classloader scenarios pass | missing dependency and user-bean back-off fail closed |
| launcher compatibility | launcher smoke consumes the tested compatibility layer | rebuilt or mismatched artifact path fails |

The TCK is intentionally not created or executed until the dependency gates are satisfied; creating a test shell
without the approved API would falsely imply a settled contract.

## Reproduction commands

```bash
find foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/spi -type f -name '*.java' | wc -l
find foggy-mcp-spi/src/main/java -type f -name '*.java' | wc -l
find foggy-dataset-mcp/src/main/java/com/foggyframework/dataset/mcp/spi -type f -name '*.java' | wc -l
rg -l --glob '*.java' --glob '!foggy-dataset-model/**' --glob '!**/src/test/**' 'com\.foggyframework\.dataset\.db\.model\.spi' .
rg -n '^[[:space:]]*<module>[^<]+</module>' pom.xml
```

Recompute these baselines on the exact implementation start commit; changed counts or paths require an updated
compatibility plan before modules are extracted.

## 当前实现进度

- 实施基线复算得到 25 个 active reactor modules、52 个 legacy model SPI types、46 个模型模块外
  main-source importer；importer 相比旧盘点的 44 个发生漂移，因此后续迁移以 46 个为准。
- 已新增第 26 个 active module `foggy-dataset-model-api`。它不继承会注入 Spring/实现依赖的旧
  repository parent，而以独立、JDK-only POM 参与根 reactor；compile dependency tree 只有模块自身。
- `QueryFacade`、`QueryFacadeRequest`、`QueryFacadeResult` 保持
  `com.foggyframework.dataset.model.api` 包名和二进制名称迁入新模块；旧聚合依赖新 API 模块，
  因此既有消费者无需立即修改 Maven 坐标。
- 新增最小 `BackendId`、`BackendCapability`、`BackendDescriptor`、`BackendProvider` 契约；identity
  采用稳定小写标识并 fail closed，capabilities 为不可变显式集合，不提供未知能力 fallback。
- 当前依赖方向为 `foggy-dataset-model -> foggy-dataset-model-api`；API 不反向依赖旧聚合、Spring、
  JDBC、implementation 或 web。
- 验证证据：API tests 4/4、旧聚合 facade compatibility/boundary tests 3/3、受影响 12-module
  reactor package 成功，API/旧聚合 JAR 无重复 facade class。
- 已新增第 27 个 active module `foggy-dataset-model-core`，依赖方向为
  `model-core -> model-api`；其 compile dependency tree 不含 Spring、JDBC、web 或旧聚合。
- core 提供不可变 `BackendProviderCatalog` 与 typed resolution errors。provider descriptor 在 discovery
  时快照；duplicate/missing/unsupported capability 均 fail closed，不以顺序或默认 provider 回退。
- API+core tests 8/8，旧聚合及其 9-module upstream reactor package 成功。
- 已新增第 28 个 active module `foggy-dataset-model-jdbc`，依赖图继续单向为
  `model-jdbc -> model-core -> model-api`。
- API 新增单方法 `QueryBackendProvider` 小角色，未扩张基础 provider；core typed resolver 同时验证
  capability 与角色类型，防止 capability-only impostor 被调用方强转。
- JDBC compatibility adapter 将稳定 `QueryFacade` 发布为 `jdbc` 或显式 dialect backend identity。
  旧 JDBC 执行实现仍由兼容聚合承载，新 SPI 不反向依赖旧聚合。
- API+core+JDBC tests 11/11；JDBC compile dependency tree 仅含 core/API，旧聚合及其 10-module
  upstream reactor package 成功。
- 已新增第 29 个 active module `foggy-dataset-model-starter`，依赖图继续单向为
  `model-starter -> model-jdbc -> model-core -> model-api`；starter 不依赖旧聚合或 web。
- starter 在稳定 `QueryFacade` 存在时提供默认 JDBC adapter，并发现全部 `BackendProvider` 构建
  immutable catalog。其他 backend 与 JDBC 共存；只有同名 `jdbcQueryBackendProvider` 覆盖默认
  adapter，用户自定义 catalog 仍按 Spring Boot back-off 规则优先。
- filtered-classloader、缺失 facade、用户 provider/catalog、跨 backend 共存、JDBC override、重复
  identity fail-closed 场景均由 context tests 覆盖。API+core+JDBC+starter tests 18/18，旧聚合
  `DbModelAutoConfigurationTest` 11/11；starter compile dependency tree 仅增加 Spring Boot
  auto-configuration，不含旧聚合或 web。
- 已新增第 30 个 active module `foggy-dataset-model-web`，五个目标模块均已物理建立。web 依赖
  `model-core -> model-api`，以及 Spring Boot auto-configuration、Spring MVC、Jackson；不依赖
  starter、JDBC adapter 或旧聚合，也不携带 embedded server。
- web diagnostics 默认关闭；只在 servlet context、provider catalog 与
  `foggy.model.backends.web.enabled=true` 同时满足时注册 `/foggy/model/backends`。endpoint 使用 catalog
  的 discovery-time descriptor snapshot，不重新读取可变 provider，实现类也不进入 HTTP DTO。
- API/core/web tests 14/14、旧聚合 `DbModelAutoConfigurationTest` 11/11；web context/MockMvc、用户
  controller back-off、missing-core、classpath boundary 与 compile dependency tree 验证均通过。
- 当前新模块有向边为 `core -> api`、`jdbc -> core`、`starter -> jdbc`、`web -> core`；旧
  `foggy-dataset-model` 作为兼容聚合依赖五个新模块，五个新模块均不反向依赖旧聚合。
- 已新增第 31 个 active module `foggy-dataset-model-tck`。该模块是 addon 测试构件，依赖
  `model-core -> model-api` 与 JUnit API；生产模块不依赖 TCK，addon 仅以 test scope 消费。
- API 新增独立 `CacheInvalidationBackendProvider/CacheInvalidationPort` 小角色，未把缓存生命周期扩张到
  基础 `BackendProvider`。query-cache addon 通过兼容 adapter 发布 backend identity `query-cache`，且只
  声明实际可桥接的 `CACHE_INVALIDATION` capability。
- TCK 自检覆盖 descriptor identity/capability 不可变性、catalog typed resolution，以及
  duplicate/missing/unsupported fail-closed。真实 cache addon 另覆盖旧 SPI delegation、非法 model
  identity fail-before-delegate、Spring auto-configuration/back-off 与既有 cache context 兼容。
- API/core/TCK tests 12/12；cache addon 定向 TCK/auto-configuration/既有 context tests 23/23。
  TCK compile dependency tree 只有 core/API 与 JUnit；cache 的 TCK/starter 依赖均为 test scope。
- launcher 的 full-addon assembly、auto-configuration registration uniqueness 与 outside-package smoke
  合计 4/4。装配测试从 catalog 以 `query-cache` + `CACHE_INVALIDATION` + 小角色类型解析真实 cache
  adapter，确认 provider 发布顺序和角色解析同时成立。
- launcher 跳过测试的独立 production package 成功。Boot JAR 精确包含旧兼容聚合与
  `model-api/core/jdbc/starter/web`，不包含 test-only `model-tck`，也不包含 launcher 中 test-scope 的
  query-cache addon；测试依赖没有改变生产制品的 scope mediation。
- 最终候选复证再次通过六个新模块 tests 26/26、旧 facade compatibility/boundary tests 6/6、cache
  TCK/context tests 23/23 和 launcher smoke 4/4。六个新构件的 compile dependency tree 只出现
  `core -> api`、`jdbc -> core`、`starter -> jdbc`、`web -> core`、`tck -> core`，未出现回边或生产模块
  指向 TCK；因此当前目标模块图满足单向无环约束。
