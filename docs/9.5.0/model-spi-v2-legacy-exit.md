---
doc_role: migration-guide
version: 9.5.0
status: READY_FOR_REVIEW
recorded_at: 2026-07-24
---

# Model SPI v2 Legacy Exit Migration

## 1. Identity migration

| Before 9.5.0 | 9.5.0 replacement | Action |
|---|---|---|
| `com.foggysource:foggy-dataset-model` | `com.foggysource:foggy-dataset-model-engine` or the exact API/core/adapter modules needed | replace the dependency explicitly |
| module path `foggy-dataset-model/` | `foggy-dataset-model-engine/` | update reactor, scripts and artifact inspection |
| `com.foggyframework.dataset.db.model.*` | `com.foggyframework.dataset.model.*` | replace imports/reflection/Spring metadata and recompile |
| `LegacyQueryFacadeAdapter` | `com.foggyframework.dataset.model.api.QueryFacade` plus stable DTO | map requests at the owning boundary |
| engine `service.QueryFacade` | engine-internal `AdvancedQueryFacade` | use only inside engine-owned advanced flows |

旧坐标和旧包不会以空壳、relocation JAR 或双包方式继续发布。

## 2. Stable SPI v2 roles

### Query

公共查询入口保持：

- `QueryFacade`
- `QueryFacadeRequest`
- `QueryFacadeResult`

这些类型继续位于 `foggy-dataset-model-api`，不依赖 Spring、JDBC 或 engine 类型。

### Model load

新增稳定角色：

- `ModelLoadBackendProvider`
- `ModelLoadPort`
- `ModelLoadRequest`
- `ModelLoadResult`

请求显式携带 model name 和 namespace；空 namespace 规范化为默认 namespace identity。
结果返回 catalog generation、source revision 和 datasource identity 完整性，不暴露 loader 内部类型。

### Atomic refresh

新增稳定角色：

- `AtomicRefreshBackendProvider`
- `AtomicRefreshPort`
- `AtomicRefreshRequest`
- `AtomicRefreshResult`

JDBC engine adapter 复用现有 `CatalogRefreshCoordinator` 的 candidate-build/admission/atomic-publication
路径。只有该真实路径声明 ATOMIC_REFRESH；没有原子 publication 语义的 backend 不声明该能力。

### Capability truthfulness

provider discovery 在以下情况 fail closed：

- duplicate backend identity；
- missing backend；
- requested capability unsupported；
- provider 声明 QUERY 但未实现 `QueryBackendProvider`；
- 声明 MODEL_LOAD 但未实现 `ModelLoadBackendProvider`；
- 声明 ATOMIC_REFRESH 但未实现 `AtomicRefreshBackendProvider`；
- 声明 CACHE_INVALIDATION 但未实现 `CacheInvalidationBackendProvider`。

descriptor、backend identity 和 capability set 保持不可变。

## 3. First-party physical ownership

- API/core/TCK/JDBC/starter/web 保持单向基础模块边界。
- JDBC/semantic/lifecycle/pivot/compose 的高度内聚实现统一位于
  `foggy-dataset-model-engine`。
- Mongo、vector、cache、preagg 实现仍归各自 addon；本版本只迁移包身份和依赖，不虚构新能力。
- runtime、MCP、memory-grid、launcher、viewer、GraphQL 和 benchmark 改用新包与新 engine 坐标。
- 顶层可执行验证脚本迁移到新模块路径和新 FQCN；`scripts/v934/**` 作为历史 source-sealed
  证据保留原文本，不作为 9.5.0 可执行入口。

## 4. Mechanical checks

升级后的仓库应满足：

```text
first-party POM/module exact foggy-dataset-model coordinate: 0
src/main production references to com.foggyframework.dataset.db.model: 0
LegacyQueryFacadeAdapter in production source: 0
governed direct engine model.query() bypass: 0
top-level verification script references to old package path/FQCN: 0
```

`LegacyExitArchitectureTest` 固定前四项；顶层脚本另执行 tracked-text 检查和 `bash -n`。

## 5. Breaking impact

外部调用方会受到以下显式 breaking change：

- Maven artifact/module identity 变化；
- 所有旧 model Java 包的 binary name 变化；
- 删除 deprecated `LegacyQueryFacadeAdapter`；
- 删除旧 engine `service.QueryFacade` 名称；
- provider 声明 MODEL_LOAD/ATOMIC_REFRESH 时必须实现对应角色，否则 discovery 失败。

稳定 `model-api QueryFacade` 请求/结果契约未改变。数据库 schema、查询结果语义、namespace/security、
field access、physical denied columns 和 datasource currentness 规则未放宽。

## 6. Upgrade sequence

1. 把外部依赖替换为精确 API/core/engine/adapter 模块。
2. 机械替换旧包根并重新编译；处理自定义反射、Spring 配置和序列化 type name。
3. 把查询调用迁移到稳定 `QueryFacade` DTO；只在 engine-owned 代码中使用 `AdvancedQueryFacade`。
4. 自定义 provider 按实际能力实现对应小角色；不得只修改 capability set。
5. 运行调用方 compile/unit/context/TCK，再进行其自身发布治理。

## 7. Rollback

本版本没有 DDL/DML 或不可逆数据迁移。回滚方式是：

1. 回退 9.5.0 代码提交；
2. 恢复 9.4.1 的 `foggy-dataset-model` 制品和旧 import；
3. 重新构建、部署并验证 9.4.1。

不支持在同一进程同时加载 9.4.1 旧包和 9.5.0 新包；避免混用两代制品。

## 8. Signoff gap

focused/affected evidence 足以形成 `READY_FOR_SIGNOFF` 候选，但未覆盖完整 release-governance
authority、semantic/portable replay、source seal 或通用数据库矩阵。breaking legacy exit 在
tag/release/publish 前建议对最终 SHA 执行一次明确批准的 authority：

- lean authority：预计 60–120 分钟；
- 含 semantic/portable replay 与完整数据库矩阵：预计 2–4 小时，另计外部 fixture 启动时间。
