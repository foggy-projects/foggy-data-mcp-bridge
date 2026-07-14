---
doc_role: module-responsibility
doc_purpose: Define ownership and dependency boundaries for the planned 9.3.3 delivery.
version: 9.3.3
status: completed
created_at: 2026-07-13
updated_at: 2026-07-14
---

# 9.3.3 Module Responsibility

## 文档作用

- doc_type: module-responsibility
- intended_for: root-controller / execution agent / reviewer
- purpose: 明确 lifecycle authority、adapter、consumer、test owner 和禁止落点。

## Delivery Mode

- mode: single-root-delivery
- root workspace: `foggy-data-mcp-bridge`
- reason: 变更均在同一 Maven reactor；按 capability lane 并行，但只维护一个 root progress 和一个 version signoff。

## 模块职责

| 模块/区域 | 9.3.3 责任 | 前置条件 |
|---|---|---|
| root build / `.github` / `scripts` | 9.3.4-A 最小 Surefire/Failsafe、no-test、DB preflight、并发 gate | 可立即开始；生产实现的硬前置 |
| `foggy-dataset-model` | lifecycle authority：snapshot、generation、SourceRevision stale check、single-flight、NamespaceScope、refresh、TM/QM/catalog view | Gate 0 + contract freeze |
| `foggy-fsscript` | source registry/script cache/dependency view commit 后提供 affected-scope + SourceRevision 事件，不拥有 model refresh | 核心 refresh port 冻结 |
| `foggy-runtime-api` | Runtime refresh/DTO 与 datasource registry/pool adapter；提供 generation-pinned binding、admission/lease | identity/refresh contract 冻结 |
| `foggy-dataset-mcp` | datasource binding generation/变更通知；catalog consumer 改用统一 snapshot，移除独立全局 names cache | binding/catalog contract 冻结 |
| `addons/foggy-dataset-model-cache` | 消费 catalog/binding identity 生成 L1/L2/Redis key；身份缺失 fail closed | ModelResultContext identity 可用 |
| `foggy-dataset-demo` | old/new、双 namespace、双 datasource sentinel TM/QM/SQL fixture | snapshot contract 冻结 |
| `foggy-mcp-launcher` | 最终自动装配、Runtime API、默认路由与发布物 smoke | 各 adapter 完成 |
| Mongo/Vector/PreAgg/Pivot 等 consumer | 兼容回归；除 Pivot freshness 外不在本轮重做 backend SPI | core lifecycle 稳定 |
| `docs/9.3.3` | progress、test、quality、coverage、version acceptance 证据闭环 | 全程回写 |

## Authority Boundary

```text
source/binding adapters
  foggy-fsscript / runtime-api / dataset-mcp
                    |
                    v
foggy-dataset-model lifecycle authority
  CatalogSnapshot -> single-flight -> atomic refresh -> CatalogIdentity
                    |
                    v
query/catalog/cache/pivot/launcher consumers
```

- adapter 可以通知 source/binding 变化，但不能自行 `clear + warm`。
- cache Addon 只能消费 identity，不能反向拥有 generation 或 refresh。
- Runtime/MCP 可以依赖 model；model 不反向依赖 Runtime/MCP，避免 Maven 循环。
- source event 类型留在 fsscript；model 通过既有依赖监听，不把 model 类型下沉到 fsscript。
- model 若继续直接引用 fsscript event contract，必须在 model POM 声明直接依赖，不能把传递依赖当 API 保证。

## 开工顺序

1. Gate lane：completed；baseline 与 9.3.4-A preflight 已建立并完成 post-Batch5 replay。
2. Core contract lane：completed；Batch 1–4 已关闭失败契约、`NamespaceScope`、`CatalogSnapshot`、binding identity 与 single-flight。
3. Adapter/refresh lanes：completed；Batch 5 已关闭 committed source event、Runtime/MCP binding convergence、detached validate 与 atomic refresh。
4. Consumer lane：Steps 1–6 completed；catalog authority、cache/cross-JVM、Pivot identity 与 complete real-query evidence 已通过。
5. Batch exit lane：completed；fresh Cache Identity 与 11-child aggregate authority `20260714T045604Z-2854237` 已形成 Batch 6 exit record。
6. Integration/post-gate lane：completed；Batch 7 replacement regression、formal quality、coverage 与 version acceptance 已按序闭环。

Gate 0 与 Batch 1–7 已完成；9.3.3 overall 为
`signed-off / accepted-with-risks`。Batch 6 exit run
`20260714T045604Z-2854237` 为 11 children、676 criterion tests / 677 asserted
XML testcases / 99 reports、4/4 expected-negative、0 remaining red、F/E/S=
`0/0/0`，两路独立二审无 blocker。Batch 7 replacement run
`20260714T084351Z-3271604`=`3824/519/F0/E0/S3`，独立复核 no blocker；
`API-COMPAT`、REGRESSION、quality、coverage 与 acceptance 均已形成正式记录。

## 模块归属校验

- 已读取 root `CLAUDE.md`、`foggy-dataset-mcp/CLAUDE.md` 及目标 POM。
- `foggy-dataset-model` 已依赖 dataset/fsscript 所需基础能力；新增 lifecycle authority 放在该模块不会新增反向依赖。
- `foggy-runtime-api`、`foggy-dataset-mcp`、cache Addon 均已依赖 model，只实现 adapter/consumer，不需要 model 反向依赖。
- `foggy-mcp-launcher` 是部署壳，只新增 smoke/装配证据，不放 lifecycle Service、Controller DTO 或 datasource registry 逻辑。
- 本轮不创建跨 impl 编排模块，也不触及 9.4.0 物理模块拆分。
