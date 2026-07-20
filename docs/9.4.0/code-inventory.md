---
doc_role: read_only_modularization_inventory
version: 9.4.0
status: baseline-only / no-module-extraction-authority
baseline_commit: 26081a3b4853914de8e6effe9a21b1353d590917
recorded_at: 2026-07-20
---

# 9.4.0 SPI v2 与模块化静态基线

## 观察边界

此记录来自 baseline commit 的 Maven/source-only 盘点。它不创建目标模块、不定义最终 API、不改变
现有 addon contract，也不把名称相似的 SPI 视为同一个抽取候选。

## 当前模块与泄漏面

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
