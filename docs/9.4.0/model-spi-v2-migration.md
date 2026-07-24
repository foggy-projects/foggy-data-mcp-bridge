---
doc_role: model_spi_v2_migration_guide
version: 9.4.0
compatibility_window: one-release-cycle
status: SIGNED_OFF
recorded_at: 2026-07-24
---

# Model SPI v2 迁移说明

## 兼容基线

9.4.0 将稳定查询契约和 backend provider 契约拆到独立 Maven 构件，同时保留旧
`com.foggysource:foggy-dataset-model` 一个兼容周期。旧聚合继续传递引入五个生产模块，因此
现有消费者不需要在同一版本内强制切换坐标：

```text
foggy-dataset-model-starter -> foggy-dataset-model-jdbc -> foggy-dataset-model-core -> foggy-dataset-model-api
foggy-dataset-model-web -------------------------------> foggy-dataset-model-core -> foggy-dataset-model-api
foggy-dataset-model (compatibility aggregate) -> api + core + jdbc + starter + web
```

`foggy-dataset-model-tck` 是测试构件，生产模块不得依赖它。Addon 只能以 test scope 消费 TCK。

## QueryFacade 调用方

稳定入口仍是以下 JVM 名称，没有因物理拆分而改包：

- `com.foggyframework.dataset.model.api.QueryFacade`
- `com.foggyframework.dataset.model.api.QueryFacadeRequest`
- `com.foggyframework.dataset.model.api.QueryFacadeResult`

新调用方应直接依赖 `foggy-dataset-model-api`，只使用 JDK DTO。不要把 engine context、JDBC result、
managed relation 或旧 model implementation 类型带回公共入口。

旧 `com.foggyframework.dataset.db.model.service.QueryFacade` 继续继承稳定 facade，并保留旧高级方法一个
兼容周期。这些方法已标记 deprecated；它们是迁移桥，不是 SPI v2 的扩展点。现有调用方可先保持旧
坐标和旧类型，再逐个改为稳定 request/result DTO。

## Provider 实现步骤

1. 选择稳定、小写的 `BackendId`；同一 catalog 内 identity 必须唯一。
2. 用不可变 `BackendDescriptor` 只声明真实支持的 `BackendCapability`。
3. 实现与能力对应的小角色接口，例如查询使用 `QueryBackendProvider`，缓存失效使用
   `CacheInvalidationBackendProvider`；不要只声明 capability 后依赖调用方强转。
4. 将具体实现保留在 adapter/addon 模块，API 模块只放 JDK-only 契约。
5. Spring addon 在自己的 auto-configuration 中发布 provider bean，并在
   `ModelBackendAutoConfiguration` 构建 catalog 之前装配。默认 bean 使用有名称的
   `@ConditionalOnMissingBean` back-off，允许用户显式覆盖。
6. 继承 `BackendProviderTck`，提供真实 provider，并保留 addon 自己的 delegation、context 和错误路径
   测试。TCK 不能代替 addon 的业务测试。

最小查询 provider 示例：

```java
public final class AcmeQueryBackendProvider implements QueryBackendProvider {
    private static final BackendDescriptor DESCRIPTOR = new BackendDescriptor(
            BackendId.of("acme"), Set.of(BackendCapability.QUERY));

    private final QueryFacade queryFacade;

    public AcmeQueryBackendProvider(QueryFacade queryFacade) {
        this.queryFacade = Objects.requireNonNull(queryFacade);
    }

    @Override
    public BackendDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public QueryFacade queryFacade() {
        return queryFacade;
    }
}
```

## Query-cache addon 的实际迁移面

当前 `query-cache` adapter 只发布 `CACHE_INVALIDATION`，并把 v2 的 `evict/evictAll` 转发到既有
`QueryCacheProvider`。它没有迁移或声明 `MODEL_LOAD`、`QUERY`、namespace isolation 或
`ATOMIC_REFRESH`。在对应实现和测试存在前，不得据此推断这些能力已经可用。

## Starter 与 Web

- 一般 Spring Boot 消费者使用 `foggy-dataset-model-starter`。当稳定 `QueryFacade` bean 存在时，它
  发布默认 JDBC provider，并从所有有序 `BackendProvider` 构建不可变 catalog。
- 只需要 HTTP diagnostics 的消费者显式增加 `foggy-dataset-model-web`。该模块不依赖 starter/JDBC，
  默认不暴露 endpoint；设置 `foggy.model.backends.web.enabled=true` 且存在 catalog 时，才注册只读
  `/foggy/model/backends`。
- 旧聚合在兼容周期内会传递带入 starter/web，但 endpoint 仍保持默认关闭。

## Maven 迁移表

| 使用场景 | 兼容期可保留 | 推荐目标依赖 |
|---|---|---|
| 仅稳定 QueryFacade/DTO | `foggy-dataset-model` | `foggy-dataset-model-api` |
| provider catalog/core 编排 | `foggy-dataset-model` | `foggy-dataset-model-core` |
| JDBC provider adapter | `foggy-dataset-model` | `foggy-dataset-model-jdbc` |
| Spring Boot 自动装配 | `foggy-dataset-model` | `foggy-dataset-model-starter` |
| backend diagnostics HTTP | `foggy-dataset-model` | `foggy-dataset-model-web` + 显式启用属性 |
| addon provider 合约测试 | 无生产依赖 | `foggy-dataset-model-tck`，test scope |

## 错误语义

provider discovery 和解析都 fail closed：

- 同 identity 多 provider：`DuplicateBackendProviderException`；
- identity 不存在：`MissingBackendProviderException`；
- provider 未声明请求的 capability：`UnsupportedBackendCapabilityException`；
- capability 已声明但 provider 未实现所需角色：`BackendProviderTypeMismatchException`。

catalog 在 discovery 时快照 descriptor，不会因 provider 后续返回可变 metadata 而改变路由结果。
调用方不得对缺失或不支持能力进行静默 fallback。

## 兼容周期边界

本次只迁移稳定 facade、provider catalog、JDBC adapter、starter/web 装配和 query-cache 的失效小角色。
旧 model SPI 中尚未迁移的 loader、engine、semantic、pivot/compose 等类型继续由兼容聚合承载；它们
没有因 SPI v2 构件存在而自动成为稳定公共 API。一个兼容周期结束前，需要基于实际调用方盘点另行
决定删除或继续保留旧桥接层，9.4.0 不执行无过渡期移除。

## 9.4.1 continuation

9.4.1 已执行无破坏 adoption/hardening：冻结直接聚合依赖和旧包导入、迁移安全的第一方查询入口，
并对已实现的 QUERY/CACHE_INVALIDATION provider role 做 discovery fail-closed 加固。准确的剩余消费者、
能力覆盖和 9.5.0 去向见 `docs/9.4.1/model-spi-v2-adoption.md`。9.4.1 仍保留本文件承诺的兼容聚合和
deprecated bridge；该 continuation 不构成 legacy removal 授权。
