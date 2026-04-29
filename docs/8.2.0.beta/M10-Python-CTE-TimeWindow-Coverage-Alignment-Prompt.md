# M10 Python CTE / TimeWindow Coverage Alignment Prompt

请在 Python 仓对齐 Java 侧 CTE 编排与 timeWindow 测试深度。目标不是补 preview-only 测试，而是补“真实执行结果 vs 手写 SQL”的 parity 证据。

## Java 侧当前证据

- CTE / Compose Query：
  - `ComposeRealSqlParityTest`：3 tests，覆盖 derived/filter、join aggregate、union all，真实执行结果与手写 SQL 逐行比较。
  - `ScriptResourceRealSqlParityTest`：3 tests，覆盖脚本资源真实执行，自动执行 `plans`，再与手写 SQL 比较。
  - `ComposedDataSetResultIntegrationTest`：1 test，覆盖 legacy `withJoin` 延迟执行容器、`execute()` 缓存、真实 DB/QM parity。
  - `ComposeScriptToolIntegrationTest`：2 tests，覆盖 MCP `dataset.compose_script` 工具注册、embedded runtime bundle、真实 join 脚本执行、手写 SQL parity。
- timeWindow DSL 单查询通道：
  - `TimeWindowDefTest` / `TimeWindowValidatorTest` / `RelativeDateParserTest` / `TimeWindowExpanderTest` 覆盖 DSL value、兼容矩阵、相对日期四方言 lowering、AST 展开。
  - `ComparativeExecutionIntegrationTest` 与 `TimeWindowExecutionIntegrationTest` 覆盖 YoY / MTD / YTD / rolling 等真实 SQL parity。
  - MySQL 5.7 不支持窗口函数：Java 侧窗口函数用例按 capability 记录 info log 并 no-op，不把 skip 计入失败；MySQL 8 lane 才能作为窗口能力真实验收。

## Python 侧任务

1. 盘点 Python 现有 CTE / compose / script runtime / MCP compose tool / timeWindow 测试，标出哪些只是 SQL preview 或 shape 断言。
2. 按 Java 侧矩阵补真实 SQL parity：
   - derived/filter
   - join aggregate
   - union all
   - script resource auto-execute `plans`
   - MCP compose script tool embedded-mode execution
   - timeWindow YoY / MTD / YTD / rolling
3. 每个 parity case 必须：
   - 使用真实模型 / 真实测试库执行框架生成查询。
   - 用等价手写 SQL 查询同一测试库。
   - 对结果做稳定归一化后逐行比较，处理数字 scale、排序不稳定和 null。
4. 对 MySQL 5.7：
   - 非窗口 compose / CTE fallback 必须真实执行并通过。
   - 窗口函数相关 timeWindow 用例按能力检测记录清晰 log 后 no-op 或 xfail，不要制造需要人工解释的 skip 噪音。
5. 文档回写：
   - 更新 Python 侧 progress / test coverage 文档，列出测试类、数量、lane 结果、仍未覆盖项。
   - 明确 MySQL 8 lane 是 timeWindow 跨方言真实验收的硬门槛。

## 成功标准

- 新增或调整后的测试不能只比较 SQL 字符串。
- 至少覆盖 Java 侧已列出的 CTE / script / MCP / timeWindow parity 维度。
- 本地目标套件、MySQL 5.7 非窗口 lane 通过；MySQL 8 若环境不可用，登记为明确 follow-up。
