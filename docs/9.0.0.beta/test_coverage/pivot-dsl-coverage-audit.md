---
audit_scope: version
audit_mode: pre-acceptance-check
version: 9.0.0.beta
target: pivot-dsl-java-core-and-mcp-schema
status: reviewed
conclusion: ready-with-gaps
reviewed_by: codex
reviewed_at: 2026-05-01
follow_up_required: yes
---

# Test Coverage Audit

## Background

- 审计对象：9.0.0.beta Pivot DSL Java Core、MCP Schema 与 Non-Additive Rollup 收口证据。
- 当前阶段：S9 正式验收前覆盖审计。
- 审计目标：确认 9.0.0.beta 的核心 requirement、acceptance item 与测试证据是否足以进入签收。

## Audit Basis

- requirement: `docs/9.0.0.beta/detailed_design/00_version_goal_and_scope.md`
- implementation plan: `docs/9.0.0.beta/detailed_design/01_pivot_dsl_and_result_contract.md`
- implementation plan: `docs/9.0.0.beta/detailed_design/02_engine_pipeline_and_memory_algo.md`
- guardrail plan: `docs/9.0.0.beta/detailed_design/03_guardrails_and_mcp_routing.md`
- non-additive plan: `docs/9.0.0.beta/detailed_design/04_non_additive_rollup_design.md`
- semantic comparison: `docs/9.0.0.beta/mdx_vs_foggy_syntax_comparison.md`
- test records: Maven Surefire reports under `foggy-dataset-model/target/surefire-reports/`
- test records: Maven Surefire reports under `foggy-dataset-mcp/target/surefire-reports/`

## Coverage Matrix

| Item | Risk | Unit | Integration | E2E | Playwright | Manual | Evidence Path | Coverage |
|------|------|------|-------------|-----|------------|--------|---------------|----------|
| Pivot AST / request contract | critical | yes | yes | no | no | yes | `PivotAstTest`, `PivotIntegrationTest`, `01_pivot_dsl_and_result_contract.md` | covered |
| Axis Having / TopN / CrossJoin / Shaper algorithms | critical | yes | yes | no | no | yes | `AxisHavingFilterTest`, `AxisTopNTruncatorTest`, `CrossJoinFillerTest`, `ResultShaperTest`, `PivotIntegrationTest` | covered |
| Subtotals / grand total additive baseline | critical | yes | yes | no | no | yes | `SubtotalInjectorTest`, `PivotIntegrationTest` | covered |
| Cardinality circuit breaker | critical | yes | yes | no | no | yes | `CardinalityBreakerTest`, `PivotIntegrationTest`, `03_guardrails_and_mcp_routing.md` | covered |
| MCP JSON Schema guardrails | critical | yes | no | no | no | yes | `PivotSchemaValidationTest`, `query_model_v3_schema.json` | covered |
| Properties post-join attachment | major | yes | yes | no | no | yes | `PropertyResolverTest`, `PropertyAttacherTest`, `PivotIntegrationTest` | covered |
| Parent-child hierarchy tree | major | yes | yes | no | no | yes | `HierarchyTreeBuilderTest`, `PivotIntegrationTest` | covered |
| Non-additive rollup strategy planning | critical | yes | yes | no | no | yes | `MetricAdditivityAnalyzerTest`, `RollupGrainEnumeratorTest`, `PivotIntegrationTest`, `04_non_additive_rollup_design.md` | covered |
| Non-additive auxiliary query and cache-aware subtotal | critical | yes | yes | no | no | yes | `SubtotalInjectorTest`, `PivotIntegrationTest`, `NonAdditiveRollupExecutor` | covered |
| UNION ALL batch merge with serial fallback | major | code-path evidence | integration | no | no | yes | `NonAdditiveRollupExecutor`, `PivotIntegrationTest` | partially-covered |
| REST / MCP error response envelope | major | partial | partial | no | no | yes | `03_guardrails_and_mcp_routing.md`, `PivotSchemaValidationTest` | partially-covered |
| Python mirror | major | no | no | no | no | no | version goal only | not-covered |

## Evidence Summary

- 自动化测试已执行：
  - `mvn test -pl foggy-dataset-model -P!multi-db`
  - Result: `reports=352 tests=2295 failures=0 errors=0 skipped=1`
  - Pivot subset from Surefire XML: `pivot_reports=13 tests=109 failures=0 errors=0 skipped=0`
  - `mvn test -pl foggy-dataset-mcp -Dtest=PivotSchemaValidationTest -P!multi-db`
  - Result: `Tests run: 6, Failures: 0, Errors: 0, Skipped: 0`
- 重点测试类：
  - `foggy-dataset-model/src/test/java/com/foggyframework/dataset/db/model/engine/pivot/PivotIntegrationTest.java`
  - `foggy-dataset-model/src/test/java/com/foggyframework/dataset/db/model/engine/pivot/rollup/MetricAdditivityAnalyzerTest.java`
  - `foggy-dataset-model/src/test/java/com/foggyframework/dataset/db/model/engine/pivot/rollup/RollupGrainEnumeratorTest.java`
  - `foggy-dataset-model/src/test/java/com/foggyframework/dataset/db/model/engine/pivot/algo/SubtotalInjectorTest.java`
  - `foggy-dataset-mcp/src/test/java/com/foggyframework/dataset/mcp/schema/PivotSchemaValidationTest.java`
- 回归保护：
  - 全量 `foggy-dataset-model` 测试通过，说明 Pivot 新链路未破坏现有 query、compose、calculate、schema 等核心路径。
  - MCP schema 独立测试通过，说明 Agent 可见契约具备基础防漏能力。

## Gaps

- `UNION ALL` batch merge 有实现与集成覆盖，但还缺专门断言 fallback 分支、分批边界 `MAX_GRAINS_PER_BATCH`、以及 UNION SQL 列对齐的细粒度单元测试；当前属于非阻断性能/健壮性补强项。
- REST API `RX` 包装与 MCP JSON-RPC Error Object 的端到端错误响应未在本轮证据中完整覆盖；Schema 与引擎异常已有证据，但网关包装层仍建议补集成测试。
- Python Mirror 没有证据。本次验收仅覆盖 Java Core 与 MCP Schema，不应外推为双端镜像已完成。
- 设计要求中“无法解析度量元数据时 fail-closed”的边界仍需继续收紧；当前主路径依赖 QueryModel 元数据，缺失元数据的兼容策略建议后续单独补负例测试。

## Recommended Next Skills

- `foggy-acceptance-signoff`: 当前可进入正式签收，但结论应带风险。
- `integration-test`: 后续补 REST/MCP 错误响应 envelope、UNION ALL fallback/分批边界测试。
- `plan-evaluator`: 如要把 Python Mirror 纳入 9.0.0.beta 正式范围，应先复核版本边界。

## Conclusion

- conclusion: ready-with-gaps
- can_enter_acceptance: yes
- follow_up_required: yes

Java Core + MCP Schema 的核心功能和回归证据充分，可以进入 S9 签收。Python Mirror、网关错误 envelope、UNION ALL 边界测试和元数据缺失 fail-closed 需要作为后续跟进项记录，不能在本次签收中假定已经完成。
