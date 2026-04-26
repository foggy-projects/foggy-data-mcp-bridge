# M11 CTE / TimeWindow Coverage Closure Plan

## 文档作用

- doc_type: workitem
- intended_for: execution-agent, reviewer, python-agent
- purpose: 固化 CTE 编排与 timeWindow 单查询通道的剩余测试收口计划，确保 Java / Python 两侧都以真实 SQL parity 作为验收证据。

## 背景

M10 已把 Java 侧 CTE 编排测试从 preview / shape 断言推进到真实 DB/QM 执行：

- `ComposeRealSqlParityTest`：derived/filter、join aggregate、union all 与手写 SQL parity。
- `ScriptResourceRealSqlParityTest`：脚本资源自动执行 `plans`，与手写 SQL parity。
- `ComposedDataSetResultIntegrationTest`：legacy `withJoin` 延迟容器真实执行、缓存与手写 SQL parity。
- `ComposeScriptToolIntegrationTest`：MCP `dataset.compose_script` 注册、embedded runtime bundle、真实脚本执行与手写 SQL parity。

timeWindow DSL 单查询通道已经覆盖 value object、校验器、相对日期 lowering、AST 展开，以及 YoY / MTD / YTD / rolling 的执行层 SQL parity。MySQL 5.7 不支持窗口函数，因此窗口用例只能走 capability short-circuit + info log no-op，不能作为窗口能力真实验收。

## 收口目标

1. Java 侧把 CTE / script / MCP / timeWindow 覆盖矩阵补到可签收状态。
2. MySQL 8 lane 成为 timeWindow 窗口函数跨方言真实验收的硬门槛。
3. Python 侧按同一测试口径补齐 parity，不接受只比 SQL 字符串的测试。
4. 文档把 MySQL 5.7 的 no-op 语义讲清楚，避免每次看到 skip / no-op 都需要人工解释。

## 执行拆分

| Step | 任务 | 状态 | 验收证据 |
|---|---|---|---|
| M11-S1 | Java CTE / script / MCP 现有覆盖盘点 | completed | `docs/dev-guide/compose-query.md` 覆盖矩阵已列出测试类、数量、lane |
| M11-S2 | Java MCP `dataset.compose_script` 端到端 parity | completed | `ComposeScriptToolIntegrationTest` 2 passed / 0 skipped |
| M11-S3 | MySQL 5.7 lane 噪音收口 | completed | 窗口函数用例记录 info log no-op；非窗口 compose / CTE 真实执行 |
| M11-S4 | Python 对齐提示词 | completed | `M10-Python-CTE-TimeWindow-Coverage-Alignment-Prompt.md` |
| M11-S5 | MySQL 8 lane | pending | YoY / MTD / YTD / rolling 在 MySQL 8 上真实执行并与手写 SQL parity |
| M11-S6 | `dataSourceGroup` 元数据暴露 | pending | get_metadata / schema 中能判断同源 join 或跨源 joinInMemory |
| M11-S7 | legacy `ComposeQueryTool` 收口 | pending | 决定 deprecated、迁移到 `dataset.compose_script`，或补旧工具端到端测试 |
| M11-S8 | Python parity 镜像 | pending | Python 侧 progress / test coverage 文档列出 CTE、script、MCP、timeWindow 对齐结果 |

## 成功标准

- CTE / compose / script / MCP 的 happy path 都必须执行真实测试库，并与手写 SQL 逐行归一化比较。
- timeWindow 的执行层测试必须覆盖 comparative、cumulative、rolling 三类。
- MySQL 5.7 对窗口函数不制造失败或 skip 噪音，但文档必须明确它不是窗口能力 parity。
- MySQL 8 环境未就绪时，不能把跨方言验收标为 completed，只能登记 blocker / follow-up。
- Python 侧对齐完成前，M11 只能标为 partial，不能进入最终签收。

## 当前计划外说明

Java `CteComposer` 与 Python `CteComposer.compose` 仍存在轻微 API 漂移：Python 顶层有 `select_columns`，Java 目前通过 `CteUnit#getSelectColumns()` 分散表达外层选列，复杂 join / raw ON 场景主要由 `ComposePlanner` 的 M6 编译路径承担。该差异暂不阻塞本轮测试收口，但 Python 对齐时需要作为能力差异记录。

## 下一步

1. 建 MySQL 8 测试 lane，复跑 `TimeWindowExecutionIntegrationTest` 和 CTE/script parity 套件。
2. 为 `dataSourceGroup` 设计最小 schema 输出，不扩展运行时 join 行为。
3. 评估 legacy `ComposeQueryTool` 的保留策略，优先减少重复入口。
4. 将本计划与 Python 对齐提示词交给 Python 侧执行，并要求回写同级 coverage evidence。
