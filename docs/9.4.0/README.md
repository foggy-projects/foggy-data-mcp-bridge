---
doc_role: version_preexecution_index
version: 9.4.0
status: queued / planning-baseline-only
entry_gate: 9.3.5-public-api-thinning-and-decycling-accepted
baseline_commit: 26081a3b4853914de8e6effe9a21b1353d590917
recorded_at: 2026-07-20
---

# 9.4.0 SPI v2 与模块化

## 当前决定

9.4.0 仍为 `queued`。本目录只保留当前模块/SPI 的静态基线与将来验证矩阵，不创建 Maven module、
不移动包、不修改 POM、不引入 `BackendProvider`，也不把任何现有 `spi` 类型机械搬到未来
`model-api`。

## 硬依赖

1. 9.3.4 必须完成 version signoff；
2. 9.3.5 必须完成公共 API 瘦身、外部入口收敛和去环，并取得其正式接受；
3. 9.3.2 的 auto-configuration/back-off 边界必须保留并由后续 starter tests 复证。

## 已确认目标模块顺序

`model-api → model-core → model-jdbc → model-starter → model-web`

旧 `foggy-dataset-model` 只在兼容期保留为聚合/转发层。后续不得先物理拆分再定义 API，或让
`model-api` 引入 Spring、JDBC、implementation 或 web 依赖。

## 基线材料

- [模块与 SPI 静态盘点](code-inventory.md)
- 版本路线图：[9.3.1 → 9.4.0 迭代顺序评审](../9.3.1/roadmap-9.3.1-to-9.4.0.md)
- 9.3.5 前置：[引擎阶段与公共 API 基线](../9.3.5/README.md)

## 将来的准出

Maven 依赖单向无环，`model-api` 无 Spring/JDBC/impl/web 依赖；Addon TCK、starter context、
launcher smoke、API/binary compatibility baseline 与迁移文档均需实际通过。这些不是本次
planning-only 基线的完成声明。
