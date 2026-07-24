---
doc_role: architecture
status: canonical
baseline: main-after-9.5.0
last_reviewed: 2026-07-24
---

# Model SPI v2 与扩展

## 1. 稳定 API

`foggy-dataset-model-api` 是 JDK-only 公共契约。稳定查询面包括：

- `QueryFacade`
- `QueryFacadeRequest`
- `QueryFacadeResult`

消费者只需要稳定查询能力时，应依赖这个模块，不依赖 engine 的 loader、semantic、pivot、
compose 或 SQL 内部类型。DTO 应保持不可变或防御性复制，避免把 Spring、JDBC、JSON 框架和
engine 对象泄漏进公共契约。

## 2. Backend identity 与 capability

每个 provider 通过 `BackendId` 和不可变 `BackendDescriptor` 声明 identity 与能力。当前
capability/role 对应关系：

| Capability | Provider role | Port |
|---|---|---|
| `QUERY` | `QueryBackendProvider` | `QueryFacade` |
| `MODEL_LOAD` | `ModelLoadBackendProvider` | `ModelLoadPort` |
| `ATOMIC_REFRESH` | `AtomicRefreshBackendProvider` | `AtomicRefreshPort` |
| `CACHE_INVALIDATION` | `CacheInvalidationBackendProvider` | `CacheInvalidationPort` |

Capability 不是功能愿望清单。只有 provider 同时完成对应 role/port 且行为满足契约时才能声明。

## 3. 当前 provider 真值

| Provider | QUERY | MODEL_LOAD | ATOMIC_REFRESH | CACHE_INVALIDATION |
|---|---:|---:|---:|---:|
| `JdbcEngineBackendProvider` | yes | yes | yes | no |
| `JdbcQueryBackendProvider` | yes | no | no | no |
| `QueryCacheBackendProvider` | no | no | no | yes |
| Mongo/vector/preagg 等未接入 provider | no new claim | no | no | no new claim |

`JdbcQueryBackendProvider` 是最小 QueryFacade adapter；engine 场景由
`JdbcEngineBackendProvider` 提供查询、模型加载和原子刷新。两者不能以相同 identity 在同一
catalog 中形成模糊发现。

## 4. Provider catalog

`BackendProviderCatalog` 在发现阶段完成并冻结 provider snapshot：

1. 读取 provider descriptor；
2. 校验 identity 唯一；
3. 校验 capability 与实现 role 一致；
4. 建立按 identity 和 typed role 的不可变索引；
5. 解析请求时检查 provider 存在且支持所需 capability。

Catalog 不选择“第一个可用实现”，也不在缺少 capability 时降级到另一条未经声明的路径。

## 5. Fail-closed 规则

必须抛出明确错误而不是容错放行：

- duplicate provider identity；
- missing provider；
- unsupported capability；
- capability-role type mismatch。

Descriptor 与 capability 集合必须不可变。provider 创建后改变 capability 会破坏 catalog
snapshot 与运行时真实行为的一致性，因此不允许。

## 6. Spring 装配和诊断

- `foggy-dataset-model-starter` 负责默认 bean、provider 集合和 catalog 装配。
- 应用可以显式提供 bean 覆盖默认实现，但仍受 identity/capability 校验。
- `foggy-dataset-model-web` 只提供只读 diagnostics，不持有新的查询执行路径。
- 缺少某个可选 addon 时，核心 catalog 应继续建立；只有请求该 addon identity/capability 时失败。

## 7. TCK

`foggy-dataset-model-tck` 验证 provider 对已声明角色的公共行为，重点覆盖：

- descriptor/identity/capability 不可变与一致；
- duplicate、missing、unsupported、type mismatch fail closed；
- QUERY 请求/结果契约；
- MODEL_LOAD 的 namespace 与错误契约；
- ATOMIC_REFRESH 不发布半完成状态；
- CACHE_INVALIDATION 仅对实际支持的失效边界生效。

TCK 是测试依赖，不进入产品运行时。新增 TCK 场景前必须先有真实 provider 能力；不能为了让
能力表更完整而虚报 model load、namespace isolation、atomic refresh 或 cache invalidation。

## 8. 新增 provider 的最小清单

1. 选择唯一、稳定的 `BackendId`。
2. 只声明已经实现的 capability。
3. 实现每项 capability 对应的 typed provider role 与 port。
4. 保持 descriptor、capability 和暴露 DTO 不可变。
5. 明确 namespace、资源 identity、并发与错误语义。
6. 接入 catalog，验证重复 identity 和缺失 capability 失败。
7. 增加受影响角色的 TCK/聚焦集成测试。
8. 若改变公共扩展边界，同步更新本文和对应版本迁移文档。

9.5.0 的 breaking 迁移记录见
[Model SPI v2 Legacy Exit](../9.5.0/model-spi-v2-legacy-exit.md)。
