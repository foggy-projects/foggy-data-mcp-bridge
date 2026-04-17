# P1-Compose Query 多查询编排能力收口-需求

## 基本信息

- 目标版本：`8.1.11`
- 需求等级：`P1`
- 状态：`待评审`
- 责任项目：`foggy-data-mcp-bridge`

## 背景

`Compose Query` 已在 `dev-compose` 分支完成首轮实现，目标是在现有 QM 语义查询能力之上，为 AI / MCP 场景补一层“多查询编排能力”。

当前设计已经覆盖以下基础能力：

- `dsl()` 触发多个 QM 查询
- `column()` 支持 ID 下推
- `withJoin()` 支持同库 SQL 层组合
- `joinInMemory()` 支持跨库内存 Hash JOIN
- `filter()` / `sort()` / `compute()` 支持结果后二次计算
- `dataset.compose_query` 作为 MCP 工具对外暴露

当前代码与文档表明，这条能力链已经具备 MVP 和单元测试基础，但还没有完成“主路径可继续推进开发与测试”的收口：

- 文档能力边界与对外 API 仍有不一致
- planner 依据仍停留在文档层，尚未形成可靠元数据闭环
- `withJoin()` 路径与 `dsl()` 路径的请求语义存在不完全一致风险
- 缺少数据库集成测试、MCP 端到端测试和多方言验证
- 资源治理与结果契约还不够明确

## 问题定义

当前真正需要解决的问题，不是“是否再发明一个查询引擎”，而是：

- 如何在不绕开现有 `SemanticQueryServiceV3 -> QueryFacade -> beforeQuery -> SQL 生成 / 执行` 主链的前提下，把 `Compose Query` 收口成可持续开发、可持续测试、可持续演进的高级能力。

如果继续保持“文档写了、单测过了、但运行时边界和验收标准不清”，后续推进会出现以下问题：

- 研发会把工具能力和真实交付能力混在一起
- 测试无法明确该补单测、集成测试还是端到端验证
- AI 侧难以稳定判断何时应走 `withJoin()`、何时应走 `joinInMemory()`
- 版本推进容易在“继续扩能力”和“先补验证闭环”之间反复摇摆

## 目标

- 明确 `Compose Query` 在 `8.1.11` 的正式能力边界和非目标
- 在现有设计基础上完成主链收口，而不是新建旁路执行模型
- 让 `withJoin()`、`joinInMemory()`、MCP 工具的行为和文档描述保持一致
- 建立从单元测试到数据库集成测试、再到 MCP 端到端验证的测试分层
- 为后续继续扩展多表编排、更多 planner 规则、更多方言支持打好基础

## 约束

- 必须复用现有 `SemanticQueryServiceV3 -> QueryFacade -> beforeQuery -> SQL 生成 / 执行` 主链
- 不新增独立查询引擎，不引入旁路 SQL 执行协议
- `withJoin()` 仍以 JDBC Query Model 为主，不在本版本扩展为通用跨引擎 federation
- `joinInMemory()` 仍定位为受限能力，必须显式补充资源治理和错误提示
- MCP 侧仍保持白名单沙箱，不开放文件 I/O、网络、Java import、Spring Bean 注入
- 文档、schema 描述、实现、测试四者必须同步收口，不允许只修其中一项

## 非目标

- 不把 `Compose Query` 做成通用分布式查询引擎
- 不在 `8.1.11` 引入独立成本优化器或复杂代价模型
- 不以 `Compose Query` 替代单 QM、预聚合、物化视图等主流建模路径
- 不在本版本开放任意脚本执行能力
- 不默认支持超大结果集跨库拼接

## 当前已具备能力

- `DataSetResult`、`ComposedDataSetResult`、`CteComposer`、`DslQueryFunction`、`ComposeQueryTool` 已存在
- `SemanticQueryServiceV3.generateSql()` 与 `QueryFacade.buildSqlOnly()` 已打通
- `FDialect.supportsCte()` 与 MySQL 5.7 子查询回退已接入
- `DataSetResultTest` 与 `CteComposerTest` 已覆盖 59 个相关单元测试

这些能力说明 `Compose Query` 不是从零开始，而是进入“收口 + 补齐 + 验证”阶段。

## 本版本需要收口的关键事项

### 1. 能力边界收口

- `withJoin()` 对外是否正式支持多表链式调用，必须在实现与文档之间做一致化处理
- `withJoin()` 与 `joinInMemory()` 的选择规则必须从“文档经验”提升到“运行时可判断”
- 组合查询返回结果是否保留 schema / pagination / total 等契约信息，必须有明确结论

### 2. 请求语义收口

- `dsl()` 支持的请求字段，在 `withJoin()` 重建请求路径中应尽量保持一致
- 至少补齐 `groupBy`、嵌套 `$or/$and`、compose hints 等已存在语义
- 若某些语义在组合场景下暂不支持，必须在文档和错误信息中显式说明

### 3. 测试闭环收口

- 单元测试继续覆盖纯内存逻辑
- 数据库集成测试覆盖 `generateSql()` 到最终执行
- MCP 端到端测试覆盖脚本执行、错误返回、契约输出
- 多方言验证覆盖 PostgreSQL、MySQL 8、MySQL 5.7 子查询回退

### 4. 运行治理收口

- 除查询次数限制外，需要补充结果规模和组合风险治理
- 对跨库内存 JOIN 的适用范围和错误提示要明确
- 对脚本失败、planner 失败、能力不支持场景提供稳定报错

## 验收标准

- 文档、schema 描述、实现、测试对 `withJoin()` / `joinInMemory()` 的行为描述一致
- `Compose Query` 继续复用现有语义查询主链，不引入旁路执行模型
- `withJoin()` 路径的请求语义与 `dsl()` 路径不存在明显背离，或已对差异显式约束
- `withJoin()` 的同库判断依据有可执行方案，不再只停留在“由 LLM 猜测”
- `joinInMemory()` 的适用边界、限制和失败提示明确
- 新增数据库集成测试，能覆盖 CTE 模式与子查询回退模式
- 新增 MCP 端到端验证，能覆盖成功脚本、失败脚本、无结果脚本等场景
- 版本文档中能明确回答“当前支持什么、暂不支持什么、如何验证”

## 待评审事项

- 是否在 `8.1.11` 正式实现“链式 `withJoin()` 对外可用”，还是将本版本范围收口为“二元 `withJoin()` 正式化”
- 是否在 `8.1.11` 对组合结果保留完整响应 envelope，还是先明确返回 `items` 契约
- `dataSourceGroup` 是否通过现有元数据直接暴露，还是由运行时 service 提供判定接口

## 关联文档

- `docs/dev-guide/compose-query.md`
- `foggy-dataset-mcp/src/main/resources/schemas/descriptions/compose_query.md`

## 跟踪维度

- 开发进度：`待开始`
- 测试进度：`待开始`
- 体验进度：`N/A`
