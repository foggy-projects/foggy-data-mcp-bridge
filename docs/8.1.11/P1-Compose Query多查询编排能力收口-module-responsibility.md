# P1-Compose Query 多查询编排能力收口 — Module Responsibility

## 基本信息

- 目标版本：`8.1.11`
- 上游需求：`docs/8.1.11/P1-Compose Query多查询编排能力收口-需求.md`
- 仓库：`foggy-data-mcp-bridge`

## Root / Workspace 负责什么

- 在 `docs/8.1.11/` 下维护本版本总控文档
- 统一定义 `Compose Query` 的正式范围、阶段目标、测试口径和验收标准
- 统一约束文档、代码、测试、MCP 描述的同步收口

## 模块职责

### 1. `foggy-dataset-model`

负责 `Compose Query` 的核心执行链与语义一致性：

- `DataSetResult` / `ComposedDataSetResult` / `CteComposer` / `DslQueryFunction`
- `SemanticQueryServiceV3.generateSql()`
- `QueryFacade.buildSqlOnly()`
- 组合查询的请求语义重建
- 同库 SQL 组合、跨库内存 JOIN 的执行边界
- 单元测试与数据库集成测试主责任

### 2. `foggy-dataset`

负责数据库方言与 SQL 组合兼容性：

- `FDialect.supportsCte()`
- MySQL 5.7 子查询回退
- 多方言兼容验证

本模块不负责 compose planner 本身，只负责方言能力暴露和 SQL 执行兼容。

### 3. `foggy-dataset-mcp`

负责 MCP 暴露层、脚本入口和工具契约：

- `ComposeQueryTool`
- `compose_query_schema.json`
- `schemas/descriptions/compose_query.md`
- MCP 端到端验证样例

本模块不负责重新实现 compose 引擎，只消费 `dataset-model` 已收口能力。

### 4. 文档与测试材料

负责版本文档、开发指引和验证材料：

- `docs/dev-guide/compose-query.md`
- `docs/8.1.11/...`
- 后续测试记录、手工验证说明、验收证据

## 现在可以开工的事项

- 对当前能力边界和实现差异做收口
- 补 `withJoin()` 路径的请求语义一致性
- 明确 `joinInMemory()` 资源边界和错误提示
- 补数据库集成测试与 MCP 端到端测试框架
- 修订文档与 schema 描述

## 依赖前置条件的事项

- 是否正式支持链式 `withJoin()`，依赖本次评审确认范围
- 多方言数据库验证依赖现有测试环境或 Docker 环境可用
- `dataSourceGroup` 暴露方式依赖元数据侧最终收口方案

## 明确不由本版本负责的事项

- 不负责通用分布式 federation
- 不负责非 JDBC 引擎的 SQL 组合能力
- 不负责新增一套独立脚本语言或脚本运行时
- 不负责用 `Compose Query` 替代现有单 QM 建模主路径
