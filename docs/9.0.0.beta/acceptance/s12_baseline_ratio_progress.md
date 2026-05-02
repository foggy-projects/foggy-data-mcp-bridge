# S12 baselineRatio 进度与验收记录

> **intended_for**：S12 执行 Agent、后置 review / audit / acceptance。
>
> **purpose**：记录 `baselineRatio` 的实现进度、测试证据、三库 SQL Parity 结果和最终签收结论。未完成的外部环境测试必须记录原因，不得标记为完成。

## 阶段目标

实现结构化 `pivot.metrics` 派生指标 `baselineRatio`，覆盖“当前单元格 / 同一行坐标下首列或末列基准值”的高频跨轴引用场景，并保持 `CELL_AT / AXIS_MEMBER` 不作为公开 DSL 暴露。

## 任务清单

| 阶段 | 内容 | 状态 | 证据 |
|---|---|---|---|
| S12.1 | AST 与 Schema 扩展 | done | `PivotMetricItem` 扩展，Schema validation 通过 |
| S12.2 | `BaselineRatioCalculator` 内存算法 | done | 单元测试通过，实现 fail-closed |
| S12.3 | PivotPipeline Guardrail 与阶段接入 | done | Pipeline phase 2.9，验证通过 |
| S12.4 | SQLite 单元 / 集成 / SQL Parity | done | SQLite Parity 通过 |
| S12.5 | MySQL8 / PostgreSQL SQL Parity | done | MySQL8 / PostgreSQL 均通过 (19 parity tests)。 |
| S12.6 | MCP Prompt、MDX 对比、Signoff 文档更新 | done | 已更新 |

## 必测项

### Java Core

- [x] `PivotMetricItem` 支持 `type=baselineRatio`。
- [x] `PivotRequest.getSqlMetricNames()` 包含 `baselineRatio.of`。
- [x] `PivotRequest.getAllOutputMetricNames()` 包含 `baselineRatio.name`。
- [x] `BaselineRatioCalculator` 支持 `first / last`。
- [x] subtotal / grandTotal 输出 `null`。
- [x] `hierarchyMode=tree + baselineRatio` fail-closed。
- [x] `axis=rows` fail-closed。
- [x] `of` 不在原生 metrics 中 fail-closed。

### Schema / MCP

- [x] 合法 `baselineRatio` 通过。
- [x] 缺失 `baseline` 拒绝。
- [x] `axis=rows` 拒绝。
- [x] 额外字段 `expr/path/member/index` 拒绝。
- [x] JSON-RPC 错误包装可读。

### SQL Parity

- [x] SQLite：直接 Pivot 查询 vs 独立 SQL oracle。
- [x] SQLite：`baseline=first`。
- [x] SQLite：`baseline=last`。
- [x] SQLite：`systemSlice` / 用户 `slice`。
- [x] SQLite：`deniedColumns` fail-closed。
- [x] MySQL8：同等 parity。（使用 `mysql8` profile，19 parity tests, 0 failures）
- [x] PostgreSQL：同等 parity。（使用 `postgres` profile，19 parity tests, 0 failures）

## 建议命令

```powershell
mvn test -pl foggy-dataset-model "-Dtest=PivotMetricItemTest,BaselineRatioCalculatorTest,ParentShareCalculatorTest" "-P!multi-db"
mvn test -pl foggy-dataset-model "-Dtest=PivotIntegrationTest,PivotSqlParityIntegrationTest" "-Dspring.profiles.active=sqlite" "-P!multi-db"
mvn test -pl foggy-dataset-mcp "-Dtest=PivotSchemaValidationTest,AnalystMcpControllerTest" "-P!multi-db"
mvn test -pl foggy-dataset-model "-Dtest=PivotSqlParityIntegrationTest" "-Dspring.profiles.active=mysql8" "-P!multi-db"
mvn test -pl foggy-dataset-model "-Dtest=PivotSqlParityIntegrationTest" "-Dspring.profiles.active=postgres" "-P!multi-db"
```

## 签收结论

当前状态：`done` (SQL Parity 测试在 SQLite、MySQL8 和 PostgreSQL 上完全通过，MCP prompt 和相关对比文档已完成同步更新)。

签收条件：`07_s12_baseline_ratio_execution_plan.md` 第七节全部满足。

## Acceptance Status

- acceptance_status: signed-off
- acceptance_decision: accepted
- signed_off_by: Codex acceptance reviewer
- signed_off_at: 2026-05-01
- acceptance_record: docs/9.0.0.beta/acceptance/s12_baseline_ratio_acceptance.md
- blocking_items: none
- follow_up_required: no
