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
evidence_count: 9
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
- `docs/9.0.0.beta/mdx_vs_foggy_syntax_comparison.md`
- `docs/9.0.0.beta/test_coverage/pivot-dsl-coverage-audit.md`

## Module Summary

| Module | Owner | Status | Acceptance Record | Notes |
|---|---|---|---|---|
| `foggy-dataset-model` | Java Core | signed-off-with-risks | `docs/9.0.0.beta/acceptance/version-signoff.md` | Pivot Pipeline、内存算法、Non-Additive Rollup 已通过自动化测试 |
| `foggy-dataset-mcp` | MCP Gateway Schema | signed-off-with-risks | `docs/9.0.0.beta/acceptance/version-signoff.md` | JSON Schema guardrail 测试通过；网关错误 envelope 仍需后续 E2E |
| Python Mirror | Python Engine | out-of-scope-for-this-signoff | N/A | 本次未验收，不计入本签收结论 |

## Checklist

- [x] Pivot DSL AST 与 query_model 入口已落地。
- [x] `columns` 与 `pivot` 互斥、`pivot + timeWindow` 互斥的 Schema/Guardrail 已覆盖。
- [x] Pivot Pipeline 已覆盖 Phase 1 聚合、Having、TopN、CrossJoin、Subtotal、Properties、Shaping。
- [x] 父子层级 `hierarchyMode=tree` 已具备基础建树与守卫规则。
- [x] Non-Additive Rollup 已覆盖 strategy planning、aux query、RollupCache、cache-aware subtotal。
- [x] S8.3 正式完成形态已补齐 UNION ALL 或等价 batch merge，并保留 serial fallback。
- [x] 核心功能已有 unit 与 integration 证据，且基于真实 SQLite 查询链路。
- [x] 9.0.0.beta 的 MDX 等价推演边界已在文档中显式标注。
- [ ] Python Mirror 未验收，本签收明确排除。
- [ ] REST/MCP 错误响应 envelope 的端到端证据仍建议补齐。

## Evidence

- Test:
  - `mvn test -pl foggy-dataset-model -P!multi-db`
  - Result: `reports=352 tests=2295 failures=0 errors=0 skipped=1`
  - Pivot subset: `pivot_reports=13 tests=109 failures=0 errors=0 skipped=0`
  - `mvn test -pl foggy-dataset-mcp -Dtest=PivotSchemaValidationTest -P!multi-db`
  - Result: `Tests run: 6, Failures: 0, Errors: 0, Skipped: 0`
- Delivery Artifacts:
  - `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/engine/pivot/`
  - `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/semantic/domain/pivot/`
  - `foggy-dataset-mcp/src/main/resources/schemas/query_model_v3_schema.json`
  - `docs/9.0.0.beta/detailed_design/`
- Coverage:
  - `docs/9.0.0.beta/test_coverage/pivot-dsl-coverage-audit.md`

## Blocking Items

- none

## Risks / Open Items

- Python Mirror 未在本轮验收中覆盖。如果 9.0.0.beta 的发布口径要求双端镜像一致，需要另开验收项。
- REST `RX` 与 MCP JSON-RPC Error Object 的端到端错误响应包装证据不完整，建议后续补网关层集成测试。
- `UNION ALL` batch merge 已实现并有集成链路覆盖，但 fallback、分批边界和列对齐建议增加更细的单元/集成测试。
- 度量元数据缺失时的 fail-closed 策略仍建议继续收紧，避免兼容 fallback 在异常模型上掩盖配置错误。

## Final Decision

Decision: `accepted-with-risks`.

9.0.0.beta Pivot DSL 的 Java Core 与 MCP Schema 已满足当前签收范围内的核心验收标准：功能链路完整、主要边界有 guardrail、Non-Additive Rollup 已从 correctness prototype 收口到 UNION ALL batch merge，且自动化测试通过。上述风险项不阻断 Java Core + MCP Schema 的阶段性签收，但必须作为后续版本或发布前质量补强项跟踪。

## Signoff Marker

- acceptance_status: signed-off
- acceptance_decision: accepted-with-risks
- signed_off_by: codex
- signed_off_at: 2026-05-01
- acceptance_record: docs/9.0.0.beta/acceptance/version-signoff.md
- blocking_items: none
- follow_up_required: yes
