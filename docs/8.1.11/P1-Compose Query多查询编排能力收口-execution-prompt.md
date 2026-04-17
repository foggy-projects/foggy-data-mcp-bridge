# P1-Compose Query 多查询编排能力收口 — Execution Prompt

## 适用对象

- 后续执行 `8.1.11` Compose Query 收口开发的 agent / 开发者

## 你开始前必须先读

1. `docs/8.1.11/P1-Compose Query多查询编排能力收口-需求.md`
2. `docs/8.1.11/P1-Compose Query多查询编排能力收口-module-responsibility.md`
3. `docs/8.1.11/P1-Compose Query多查询编排能力收口-code-inventory.md`
4. `docs/8.1.11/P1-Compose Query多查询编排能力收口-implementation-plan.md`
5. `docs/dev-guide/compose-query.md`

## 你需要做的事

按 implementation plan 的 Step 1-5 顺序执行：

### Step 1. 收口范围

- 先确认本版本是否正式支持链式 `withJoin()`
- 先确认组合结果是否保留完整响应 envelope
- 先确认同库判断的正式方案
- 未确认前，不要一边改实现一边改文档

### Step 2. 收口请求语义

- 检查 `DslQueryFunction` 与 `ComposedDataSetResult` 的请求构建差异
- 统一 `groupBy`、复杂 slice、compose hints
- 对暂不支持能力明确抛错或落文档

### Step 3. 收口运行保护

- 明确 `withJoin()` 与 `joinInMemory()` 的选择和失败提示
- 补规模限制、错误分类、返回契约
- 保持沙箱白名单不扩权

### Step 4. 补测试

- 先补 `dataset-model` 集成测试
- 再补 `dataset-mcp` 端到端测试
- 覆盖成功路径、失败路径、边界路径

### Step 5. 更新文档

- 同步更新 `docs/dev-guide/compose-query.md`
- 同步更新 MCP description / schema
- 执行完成后更新 progress 模板

## 你不需要做的事

- 不新增旁路执行引擎
- 不把 `Compose Query` 扩展成通用分布式 federation
- 不直接开放更高权限脚本能力
- 不把所有未决能力一股脑塞进 `8.1.11`
- 不跳过测试直接宣布“文档已完成”

## 实施边界

- 主链必须继续复用 `SemanticQueryServiceV3 -> QueryFacade -> beforeQuery -> SQL 生成 / 执行`
- `withJoin()` 仍以 JDBC Query Model 为主
- `joinInMemory()` 必须被视为受限能力
- 文档、测试、实现必须同步改，不接受单点漂移

## 验收方式

至少完成以下检查：

### 代码检查

- `DataSetResult` / `ComposedDataSetResult` / `DslQueryFunction` 的能力边界一致
- `ComposeQueryTool` 返回契约与文档一致
- `docs/dev-guide/compose-query.md` 与 MCP description 一致

### 测试检查

- 运行现有 compose 单元测试
- 运行新增 compose 集成测试
- 运行 MCP 端到端测试
- 如环境允许，完成 PostgreSQL / MySQL 8 / MySQL 5.7 差异验证

### 推荐命令

```powershell
mvn -pl foggy-dataset-model "-Dtest=DataSetResultTest,CteComposerTest" test
```

新增集成测试后，补充相应模块命令到测试记录中。

## 执行完成后

- 更新 `docs/8.1.11/P1-Compose Query多查询编排能力收口-progress.md`
- 若补了专门测试记录，放在 `docs/8.1.11/` 下并在 progress 中链接
- 若发现范围需调整，先回写需求文档和 implementation plan，再继续编码
