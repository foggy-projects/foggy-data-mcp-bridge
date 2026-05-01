---
acceptance_scope: version
version: 9.0.0.beta
target: pivot-dsl-java-core-and-mcp-schema
doc_role: acceptance-record
doc_purpose: 记录 9.0.0.beta Pivot DSL Java Core 与 MCP Schema 的正式验收结论
status: signed-off
decision: accepted-with-risks
signed_off_by: codex
signed_off_at: 2026-05-01
reviewed_by: N/A
blocking_items: []
follow_up_required: yes
evidence_count: 13
---

# Version Acceptance

## Document Purpose

- doc_type: acceptance
- intended_for: signoff-owner / reviewer / root-controller / implementation-agent
- purpose: 记录 9.0.0.beta Pivot DSL Java Core 与 MCP Schema 的正式签收结论、测试证据和后续风险。

## Background

- Version: 9.0.0.beta
- Scope: Pivot DSL Java Core、MCP JSON Schema guardrails、Non-Additive Rollup。
- Goal: 为 LLM Agent 提供安全、结构化、可验收的多维透视接口，覆盖 rows/columns/metrics、axis having/topN、crossjoin、subtotals/grandTotal、properties post-join、hierarchy tree、non-additive rollup 和 schema guardrail。
- Boundary: 本签收不覆盖 Python Mirror 的实现完成度，也不声明所有 MDX 坐标能力已等价实现。

## Acceptance Basis

- `docs/9.0.0.beta/detailed_design/00_version_goal_and_scope.md`
- `docs/9.0.0.beta/detailed_design/01_pivot_dsl_and_result_contract.md`
- `docs/9.0.0.beta/detailed_design/02_engine_pipeline_and_memory_algo.md`
- `docs/9.0.0.beta/detailed_design/03_guardrails_and_mcp_routing.md`
- `docs/9.0.0.beta/detailed_design/04_non_additive_rollup_design.md`
- `docs/9.0.0.beta/detailed_design/05_cell_at_cross_axis_evaluation.md`
- `docs/9.0.0.beta/mdx_vs_foggy_syntax_comparison.md`
- `docs/9.0.0.beta/test_coverage/pivot-dsl-coverage-audit.md`

## Module Summary

| Module | Owner | Status | Acceptance Record | Notes |
|---|---|---|---|---|
| `foggy-dataset-model` | Java Core | signed-off-with-risks | `docs/9.0.0.beta/acceptance/version-signoff.md` | Pivot Pipeline、内存算法、Non-Additive Rollup、SQL Parity 已通过 SQLite/MySQL8/PostgreSQL 验证 |
| `foggy-dataset-mcp` | MCP Gateway Schema | signed-off-with-risks | `docs/9.0.0.beta/acceptance/version-signoff.md` | JSON Schema guardrail、网关错误 envelope、权限绑定测试通过 |
| Python Mirror | Python Engine | out-of-scope-for-this-signoff | N/A | 本次未验收，不计入本签收结论 |

## Checklist

- [x] Pivot DSL AST 与 query_model 入口已落地。
- [x] `columns` 与 `pivot` 互斥、`pivot + timeWindow` 互斥的 Schema/Guardrail 已覆盖。
- [x] Pivot Pipeline 已覆盖 Phase 1 聚合、Having、TopN、CrossJoin、Subtotal、Properties、Shaping。
- [x] 父子层级 `hierarchyMode=tree` 已具备基础建树与守卫规则。
- [x] Non-Additive Rollup 已覆盖 strategy planning、aux query、RollupCache、cache-aware subtotal。
- [x] S8.3 正式完成形态已补齐 UNION ALL 或等价 batch merge，并保留 serial fallback。
- [x] 核心功能已有 unit 与 integration 证据，且基于真实 SQLite 查询链路。
- [x] Pivot 合法能力已补充 SQL Parity 验收：直接执行 Pivot 查询，并与独立 SQL 查询结果做真实比对。
- [x] SQL Parity 验收已覆盖 SQLite、MySQL8、PostgreSQL 三种数据库 profile。
- [x] `systemSlice` 与 `deniedColumns` 权限链路已有 Pivot/MCP 侧自动化测试覆盖。
- [x] 9.0.0.beta 的 MDX 等价推演边界已在文档中显式标注。
- [x] MDX Generate 的**单层**分组内 TopN 受控子集已落地并通过 Schema/Parity 双重验收。级联多层 Generate 暂未开放（中间层排序基于明细行而非聚合值），已在文档中明确标注限制。
- [x] REST/MCP 错误响应 envelope 及各种非法查询边界测试已全部完成 (S10)。
- [x] S11 `pivot.metrics` 混合数组与 `parentShare` 第一版已完成补充签收：Schema 前置拒绝 `expr`/缺失 `of`/`axis=columns`，runtime 阻断隐式 columns、tree、不可加度量，且 SQLite/MySQL8/PostgreSQL SQL Parity 通过。
- [x] S12 `baselineRatio` 派生指标已完成补充签收：Schema 与 Runtime fail-closed 拦截完备，算法消除非确定性排序与 NULL 值污染，且在 SQLite、MySQL8、PostgreSQL 三大数据库上的 SQL Parity 均已通过。
- [ ] Python Mirror 未验收，本签收明确排除，并已沉淀 `s10_python_parity_plan.md` 待后续跟进。

## Changelog & Known Limitations

**S8阶段核心更新总结**:
1. **S8.1 后置属性 (Properties)**: 支持在 Phase 2 的最后一步为主键挂载多语言等扩展属性，减少 DB 聚合查询带来的 `GROUP BY` 基数膨胀。
2. **S8.2 父子层级 (Hierarchy Tree)**: 允许声明 `hierarchyMode=tree`，实现从平铺行数据到树形嵌套对象 (`TreeNode`) 的转换，支持父节点自动汇集。
3. **S8.3 Non-Additive Rollup**: 彻底取代了旧有的盲目 `SUM` 策略，支持基于 QueryModel 分析聚合特性，使用批量 `UNION ALL` (以及安全降级机制) 生成精准的跨层级小计。

**已知限制 (Known Limitations)**:
1. `hierarchyMode=tree` 仅支持在 `rows` 轴使用，并且不能与 `crossjoin=true` 稀疏展开混用。
2. `hierarchyMode=tree` 的树形模式暂不支持内存 `subtotals` 自动补全（父子关系本身已充当了 Subtotal，引擎不允许叠加开启）。
3. 极其复杂的 CTE 模型或特定旧版方言 (如 SQL Server 早期版本) 的辅助查询中，`UNION ALL` 合并可能失败，系统会自动降级为串行查询，确保可用性但性能略有折损。
4. 当前并不支持 `ROLLUP_TO` / `CELL_AT` 等高级 MDX 坐标系漫游函数。父级占比已通过 `parentShare` 第一版覆盖；跨轴绝对坐标引用不得用 `REMOVE(...)` 假装等价，应在 Guardrail 中明确标注为待实现或降级到 `compose_script`。

## Evidence

- Test:
  - `mvn test -pl foggy-dataset-model "-Dtest=PivotIntegrationTest,PivotSqlParityIntegrationTest,ParentShareCalculatorTest,PivotMetricItemTest" "-Dspring.profiles.active=sqlite" "-P!multi-db"`
  - Result: `Tests run: 70, Failures: 0, Errors: 0, Skipped: 0`
  - `mvn test -pl foggy-dataset-model "-Dtest=PivotSqlParityIntegrationTest" "-Dspring.profiles.active=mysql8" "-P!multi-db"`
  - Result: `Tests run: 15, Failures: 0, Errors: 0, Skipped: 0`
  - `mvn test -pl foggy-dataset-model "-Dtest=PivotSqlParityIntegrationTest" "-Dspring.profiles.active=postgres" "-P!multi-db"`
  - Result: `Tests run: 15, Failures: 0, Errors: 0, Skipped: 0`
  - `mvn test -pl foggy-dataset-mcp "-Dtest=PivotSchemaValidationTest,AnalystMcpControllerTest" "-P!multi-db"`
  - Result: `Tests run: 23, Failures: 0, Errors: 0, Skipped: 0`
  - `mvn test -pl foggy-dataset-model -P!multi-db`
  - Result: `reports=352 tests=2295 failures=0 errors=0 skipped=1`
  - Pivot subset: `pivot_reports=13 tests=109 failures=0 errors=0 skipped=0`
  - `mvn test -pl foggy-dataset-mcp -Dtest=PivotSchemaValidationTest -P!multi-db`
  - Result: `Tests run: 6, Failures: 0, Errors: 0, Skipped: 0`
  - `mvn test -pl foggy-dataset-model "-Dtest=PivotSqlParityIntegrationTest,PivotIntegrationTest,MetricAdditivityAnalyzerTest" "-Dspring.profiles.active=sqlite" "-P!multi-db"`
  - Result: `Tests run: 50, Failures: 0, Errors: 0, Skipped: 0`
  - `mvn test -pl foggy-dataset-model "-Dtest=PivotSqlParityIntegrationTest,PivotIntegrationTest,MetricAdditivityAnalyzerTest" "-Dspring.profiles.active=mysql8" "-P!multi-db"`
  - Result: `Tests run: 50, Failures: 0, Errors: 0, Skipped: 0`
  - `mvn test -pl foggy-dataset-model "-Dtest=PivotSqlParityIntegrationTest,PivotIntegrationTest,MetricAdditivityAnalyzerTest" "-Dspring.profiles.active=postgres" "-P!multi-db"`
  - Result: `Tests run: 50, Failures: 0, Errors: 0, Skipped: 0`
  - `mvn test -pl foggy-dataset-mcp "-Dtest=AnalystMcpControllerTest,LocalDatasetAccessorGovernanceTest,QueryModelToolTest,MetadataToolTest,ComposeScriptToolBindingTest,PivotSchemaValidationTest" "-P!multi-db"`
  - Result: `Tests run: 61, Failures: 0, Errors: 0, Skipped: 0`
- Delivery Artifacts:
  - `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/engine/pivot/`
  - `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/semantic/domain/pivot/`
  - `foggy-dataset-mcp/src/main/resources/schemas/query_model_v3_schema.json`
  - `foggy-dataset-model/src/test/java/com/foggyframework/dataset/db/model/engine/pivot/PivotSqlParityIntegrationTest.java`
  - `docs/9.0.0.beta/detailed_design/`
- Coverage:
  - `docs/9.0.0.beta/test_coverage/pivot-dsl-coverage-audit.md`

## Blocking Items

- none

## Risks / Open Items

- Python Mirror 未在本轮验收中覆盖。如果 9.0.0.beta 的发布口径要求双端镜像一致，需要另开验收项 (已产出 `s10_python_parity_plan.md`)。
- `CELL_AT` / `AXIS_MEMBER`：状态为 `rejected-for-public-dsl`——不作为 LLM 可生成的公开 DSL 暴露。高频跨轴引用场景已由 S12 `baselineRatio` 结构化派生指标完全覆盖。
- `ROLLUP_TO`：不作为公开函数字符串暴露，等价语义已通过 `pivot.metrics` 的 `parentShare` 结构化类型实现第一版。
- 级联多层 Generate（多个层级同时设置 `limit`）：状态为 `deferred / known-limitation`。单层分组 TopN 已覆盖多数场景，级联需求频率低。当前 `AxisTopNTruncator` 在中间层的排序基于明细行而非中间聚合值，可能产生错误排名。
- `UNION ALL` 批量路径已有真实链路覆盖；后续仍可把断言加强为 Pivot key set 与 SQL key set 完全一致，作为非阻断测试打磨项。
- ~~REST `RX` 与 MCP JSON-RPC Error Object 的端到端错误响应包装证据不完整，建议后续补网关层集成测试。~~ (已在 S10 收口修复)
- ~~`UNION ALL` batch merge 已实现并有集成链路覆盖，但 fallback、分批边界和列对齐建议增加更细的单元/集成测试。~~ (列对齐 P0 Bug 已修复，相关测试已在 S10 补齐)
- ~~度量元数据缺失时的 fail-closed 策略仍建议继续收紧，避免兼容 fallback 在异常模型上掩盖配置错误。~~ (Fail-closed 严格断言已在 S10 补齐)

## Final Decision

Decision: `accepted-with-risks`.

9.0.0.beta Pivot DSL 的 Java Core 与 MCP Schema 已满足当前签收范围内的核心验收标准：功能链路完整、主要边界有 guardrail、Non-Additive Rollup 已从 correctness prototype 收口到 UNION ALL batch merge，且合法 Pivot 能力已通过 SQLite、MySQL8、PostgreSQL 的真实 SQL Parity 验收。上述风险项不阻断 Java Core + MCP Schema 的阶段性签收，但必须作为后续版本或发布前质量补强项跟踪。

## Signoff Marker

- acceptance_status: signed-off
- acceptance_decision: accepted-with-risks
- signed_off_by: codex
- signed_off_at: 2026-05-01
- acceptance_record: docs/9.0.0.beta/acceptance/version-signoff.md
- blocking_items: none
- follow_up_required: yes
