---
doc_role: module-responsibility
doc_purpose: Record the completed ownership and dependency boundaries for the 9.3.2 delivery.
version: 9.3.2
status: completed
updated_at: 2026-07-13
---

# 9.3.2 Module Responsibility

## 文档作用

- doc_type: module-responsibility
- intended_for: root-controller / execution-agent / reviewer
- purpose: 明确单仓多模块交付中的 ownership、依赖顺序和不可越界范围。

## Delivery Mode

- mode: single-root-delivery
- root workspace: `foggy-data-mcp-bridge`
- reason: 所有变更位于同一 Maven reactor，使用一个版本执行包跟踪；按模块分工但不拆子仓文档。

## 模块职责

| 模块 | 责任 | 前置条件 |
|---|---|---|
| `foggy-mcp-launcher` | 移除根扫描；提供最终启动与默认路由黑盒 smoke | model/MCP/imports 稳定 |
| `foggy-dataset-model` | 以显式内部 Bean 清单替代跨 Addon 扫描；提供核心自动配置顺序与 fallback 风险诊断 | dataset/fsscript 自动配置可用 |
| `foggy-dataset-mcp` | 以显式内部 Bean 清单替代宽扫描；保持 dev/test Controller 默认关闭 | model 自动配置稳定 |
| `addons/foggy-dataset-mongo` | dataset Mongo loader 的条件、back-off 与 Boot 3 入口 | Mongo 类、基础 fsscript Bean |
| `addons/foggy-dataset-model-mongo` | model Mongo loader 的正确自动配置入口；移除错误注册和无条件 `@Service` 发现 | model + Mongo Bean |
| `addons/foggy-dataset-vector` | dataset Vector loader 的 Boot 3 入口、开关、依赖与 Bean 条件 | VectorStore + fsscript Bean |
| `addons/foggy-dataset-model-vector` | model Vector loader 的 Boot 3 入口、开关、可选 Milvus 依赖和顺序 | model + Milvus 类 |
| `addons/foggy-dataset-model-cache` | provider/builder/controller/eviction 条件一致，Redis/Caffeine 分支隔离与 back-off | model + 对应 provider 依赖 |
| 其他已有自动配置模块 | 移除旧 `EnableAutoConfiguration` 双入口；保留现有 imports 或补齐缺口 | 不改变业务行为 |
| `docs/9.3.2` | 记录 inventory、progress、test、quality、coverage、acceptance | 全流程持续回写 |

## 依赖与装配顺序

```text
Foggy core/fsscript/dataset
  → DbModelAutoConfiguration
    → Mongo/Vector model Addon
    → QueryCache Addon
    → DatasetMcpAutoConfiguration
      → Launcher local configuration
```

## 模块归属校验

- 新增自动配置类留在其 owning Addon，不放入 Launcher 部署壳。
- Launcher 只保留启动类、本地集成配置和 smoke 测试，不承载 Addon Bean 定义。
- model-mongo/model-vector 依赖 model，model 不反向依赖 Addon，因此不存在新增 Maven 循环。
- 本轮不创建跨 impl 编排模块，不触及 9.4.0 物理拆分。

## Delivery Result

- 所有 owning module 的声明责任均已完成。
- 根交付统一完成测试、质量门、覆盖审计和正式签收。
- blocker/high: none。
