# P1-Compose Query 多查询编排能力收口 — Implementation Plan

## 基本信息

- 目标版本：`8.1.11`
- 上游需求：`docs/8.1.11/P1-Compose Query多查询编排能力收口-需求.md`
- 模块职责：`docs/8.1.11/P1-Compose Query多查询编排能力收口-module-responsibility.md`
- 代码清单：`docs/8.1.11/P1-Compose Query多查询编排能力收口-code-inventory.md`
- 仓库：`foggy-data-mcp-bridge`

## 前置条件

- 当前 `Compose Query` 基础实现已存在
- `docs/dev-guide/compose-query.md` 可作为现状基线
- 本版本评审确认 `8.1.11` 的正式收口范围
- 相关测试环境可提供至少同库数据库和 MySQL 5.7 / 8 差异验证条件

## 实施步骤

### Step 1. 收口正式能力边界

整理并固化以下结论：

1. `withJoin()` 在 `8.1.11` 的正式能力边界
2. `joinInMemory()` 的适用边界和风险提示
3. 组合结果是否保留完整响应 envelope
4. planner 如何决定同库 SQL 组合与跨库内存 JOIN

输出要求：

- 需求文档与开发指南明确写出“支持 / 暂不支持”
- MCP description 与 schema 描述同步收口
- 对无法在本版本支持的能力给出明确非目标说明

验收：

- 文档、schema、实现目标一致
- 研发和测试可以据此直接拆任务，不再依赖口头解释

### Step 2. 统一 compose 请求语义

让 `withJoin()` 路径与 `dsl()` 请求构建逻辑尽量共用一套能力：

1. 复用或抽取 `SemanticQueryRequest` 构建逻辑
2. 补齐 `groupBy`
3. 补齐嵌套 `$or / $and`
4. 保持 compose hints 传递一致
5. 明确 `calculatedFields` 等暂不支持项是否保留或显式拒绝

重点要求：

- 不允许 `dsl()` 能表达、而 `withJoin()` 无声降级
- 若组合场景确实不支持某些字段，必须显式抛错或在文档写明

验收：

- `withJoin()` 对 `groupBy`、复杂 slice 的行为可预测
- 新增针对请求语义一致性的测试

### Step 3. 收口 planner 与运行保护

补齐 `Compose Query` 的运行治理：

1. 明确同库判断入口
2. 明确 `dataSourceGroup` 或等价元数据的来源
3. 为 `joinInMemory()` 增加规模限制或失败保护
4. 为脚本执行失败、能力不支持、planner 失败提供稳定错误信息
5. 收口 `ComposeQueryTool` 的返回契约

重点要求：

- 不再依赖“由 LLM 自己猜是否同库”
- 不把超大结果集无保护地送入内存 JOIN
- 不让调用方对返回结构产生歧义

验收：

- 工具层能稳定区分脚本错误、能力不支持、运行超限等场景
- 组合结果输出契约明确

### Step 4. 补数据库集成测试与 MCP 端到端测试

建立正式测试分层：

1. 单元测试继续覆盖 `DataSetResult`、`CteComposer`
2. 数据库集成测试覆盖 `ComposedDataSetResult`
3. MCP 端到端测试覆盖 `ComposeQueryTool`
4. 多方言验证覆盖 PostgreSQL、MySQL 8、MySQL 5.7 子查询回退

推荐覆盖场景：

- `withJoin()` 二元 LEFT / INNER JOIN
- 同名字段冲突与左表优先
- `groupBy` + `slice` + `orderBy` 组合
- 复杂 `$or / $and`
- `joinInMemory()` 大小边界与失败保护
- `return` 缺失、非法脚本、超过查询次数、空结果返回

验收：

- 新增测试文件可以在本地和 CI 稳定执行
- 测试结果能直接支撑版本验收，而不是只做 smoke

### Step 5. 收口开发文档与验收材料

基于最终实现更新：

1. `docs/dev-guide/compose-query.md`
2. MCP description
3. 本版本进度模板与后续测试记录模板
4. 如需要，补充验证步骤手册或验收材料包

验收：

- 文档能独立回答“如何使用、如何测试、当前限制是什么”
- 版本目录中的材料足以支撑后续开发与测试协作

## 不做的事

- 不将 `Compose Query` 扩展为通用跨引擎 federation
- 不为 compose 单独建设另一套 query planner / optimizer
- 不默认支持无上限的跨库大结果集拼接
- 不在本版本引入新的外部脚本能力或执行权限

## 推荐实施顺序

1. 先完成 Step 1，锁定边界
2. 再做 Step 2，避免后续测试建立在错误契约之上
3. 接着做 Step 3，确保运行保护和对外契约稳定
4. 再进入 Step 4，集中补测试
5. 最后做 Step 5，更新版本与开发文档

## 预估工作量

| Step | 预估 | 说明 |
|------|------|------|
| 1. 能力边界收口 | 0.5 d | 文档、接口、范围确认 |
| 2. 请求语义统一 | 1.0 d | 代码重构 + 测试补齐 |
| 3. planner 与运行保护 | 0.5-1.0 d | 元数据接入、报错与限制 |
| 4. 集成 / E2E 测试 | 1.0-1.5 d | 数据库环境 + 用例补齐 |
| 5. 文档与验收材料 | 0.5 d | 指南、说明、模板 |
| **合计** | **3.5-4.5 d** | 不含评审等待时间 |
