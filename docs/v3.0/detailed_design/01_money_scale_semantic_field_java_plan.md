---
doc_role: implementation_plan
doc_purpose: Plan Java TM/QM implementation steps for money semantic scale fields.
version: v3.0
ticket: MONEY-30-J1
status: implemented-pending-review
owner: foggy-dataset-model
created_at: 2026-05-10
---

# Java Plan: Money Scale Semantic Field Contract

## 文档作用

- doc_type: implementation-plan
- intended_for: execution-agent | reviewer
- purpose: 约束 Java 第一阶段实现顺序、代码触点、测试要求和非目标，避免与现有 TM/QM 命名和治理链路冲突。

## Implementation Strategy

Use the existing TM/QM field binding model. Do not introduce a separate physical SQL field.

Java V1 implements semantic scaling by wrapping the runtime SQL declaration for simple physical-column fields:

```text
semantic declare = (physical column declare / semanticScaleFactor)
```

The wrapping is applied inside `DbMeasureSupport.MeasureDbColumn` and `DbPropertyImpl.PropertyDbColumn` through `SemanticScaleSqlSupport.scaledDeclare(...)`. This is equivalent to an internal field binding for query compilation, but it does not inject or expose a public `formulaDef`.

The model author only declares `column` plus `semanticScaleFactor`. The LLM-facing metadata should not describe the physical conversion. Formula fields and dialect-specific formula fields remain unsupported with `semanticScaleFactor` in this phase and fail closed during model loading.

Before implementation, the execution agent must read the Java worktree `CLAUDE.md` and treat its TM/QM engine, testing, physical-column permission, and real SQL comparison rules as mandatory constraints.

## Stage Plan

| Step | Status | Goal | Notes |
|---|---|---|---|
| S1 | done | Add model definition fields | Added semantic metadata to measure/property definitions and SPI defaults |
| S2 | done | Add loader validation | Rejects non-positive factor, unsupported `formulaDef + semanticScaleFactor` stacking, and SQL expressions in `column` when scale is enabled |
| S3 | done | Apply semantic field binding | Runtime declaration wrapping is used for projection, slice, orderBy, aggregation, having, and calculated field references |
| S4 | done | Preserve governance mapping | Physical mapping remains bound to the original `column`; deniedColumns and fieldAccess covered by targeted tests |
| S5 | done | Add integration fixtures and tests | Added ecommerce fixtures and real-query integration tests with native SQL baselines |
| S6 | in-progress | Update progress and run review chain | Docs, self-check, and coverage audit are updated; formal quality gate and acceptance remain follow-up stages |

## Non-Goals

- No Python parity in this Java workitem.
- No SQL optimization or reverse predicate rewrite in V1.
- No currency exchange, FX conversion, or multi-currency normalization.
- No public arbitrary SQL fragment field.
- No UI work.

## Code Inventory

| Repo | Path | Role | Expected Change | Notes |
|---|---|---|---|---|
| `foggy-data-mcp-bridge-wt-dev-compose` | `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/def/measure/DbMeasureDef.java` | TM measure definition | updated | Added `semanticScaleFactor`, `semanticUnit`, `semanticUnitLabel` |
| `foggy-data-mcp-bridge-wt-dev-compose` | `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/def/property/DbPropertyDef.java` | TM property definition | updated | Added `semanticScaleFactor`, `semanticUnit`, `semanticUnitLabel` |
| `foggy-data-mcp-bridge-wt-dev-compose` | `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/spi/DbMeasure.java` | measure SPI | updated | Added default semantic metadata getters |
| `foggy-data-mcp-bridge-wt-dev-compose` | `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/spi/DbProperty.java` | property SPI | updated | Added default semantic metadata getters |
| `foggy-data-mcp-bridge-wt-dev-compose` | `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/impl/SemanticScaleSqlSupport.java` | SQL declaration helper | created | Validates semantic scale contract and wraps simple physical column declarations |
| `foggy-data-mcp-bridge-wt-dev-compose` | `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/impl/loader/TableModelLoaderManagerImpl.java` | TM loader validation | updated | Invokes property semantic scale validation before formula binding |
| `foggy-data-mcp-bridge-wt-dev-compose` | `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/impl/measure/DbMeasureSupport.java` | measure runtime support | updated | Carries metadata and applies declaration scaling for non-formula physical columns |
| `foggy-data-mcp-bridge-wt-dev-compose` | `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/impl/property/DbPropertyImpl.java` | property runtime support | updated | Carries metadata, validates formula conflict, and applies declaration scaling for non-formula physical columns |
| `foggy-data-mcp-bridge-wt-dev-compose` | `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/engine/query_model/PhysicalColumnMappingBuilder.java` | deniedColumns / metadata mapping | verified unchanged | Existing physical mapping continues to point to the original `column`; targeted deniedColumns test covers behavior |
| `foggy-data-mcp-bridge-wt-dev-compose` | `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/semantic/service/impl/SemanticServiceV3Impl.java` | describe/metadata output | unchanged | Metadata surfacing beyond runtime model fields is not expanded in Java V1 |
| `foggy-data-mcp-bridge-wt-dev-compose` | `foggy-dataset-demo/src/main/resources/foggy/templates/ecommerce/` | test TM/QM fixtures | updated | Added valid and invalid semantic scale fixtures |
| `foggy-data-mcp-bridge-wt-dev-compose` | `foggy-dataset-model/src/test/java/com/foggyframework/dataset/db/model/ecommerce/SemanticScaleFactorIntegrationTest.java` | Java tests | created | Focused real-query tests with native SQL baselines |
| `foggy-data-mcp-bridge-wt-dev-compose` | `foggy-dataset-mcp/` | MCP schema/tool docs | not changed | Out of scope for this Java V1 runtime behavior |
| `foggy-data-mcp-bridge-wt-dev-compose` | `foggy-mcp-launcher/` | deployment shell | do-not-touch | No service/controller/deployment shell changes are expected |

Module ownership check:

- Primary implementation belongs in `foggy-dataset-model`, which is the TM/QM engine module declared in the root `pom.xml`.
- Test fixtures may require `foggy-dataset-demo`, which is already a Maven module and existing source for ecommerce integration test resources.
- This plan does not place service, controller, Form, Result, or orchestration logic into any deployment shell.
- No new cross-module orchestration module is needed because the work is confined to the model engine and test fixtures.

## Contract Details

### Valid Field Shape

```javascript
{
  name: "totalCost",
  column: "total_cost_cent",
  aggregation: "sum",
  semanticScaleFactor: 100,
  semanticUnit: "currency",
  semanticUnitLabel: "元"
}
```

### Invalid Shapes

Reject or load-error:

```javascript
{
  name: "totalCost",
  column: "total_cost_cent",
  formulaDef: { builder: (alias) => `${alias}.total_cost_cent / 100.0` },
  semanticScaleFactor: 100
}
```

Reject:

```javascript
{
  name: "totalCost",
  column: "total_cost_cent / 100.0",
  semanticScaleFactor: 100
}
```

Reject:

```javascript
{
  name: "totalCost",
  column: "total_cost_cent",
  semanticScaleFactor: 0
}
```

## Test Matrix

| Area | Required | Evidence |
|---|---:|---|
| Plain projection | yes | actual query result equals native SQL `physical / factor` |
| Slice | yes | actual query result contains only rows satisfying semantic threshold |
| OrderBy | yes | actual order equals native SQL semantic order |
| Aggregation | yes | sum/aggregation result equals semantic unit baseline |
| Having | yes | aggregate filter compares semantic units |
| Calculated fields | yes | money arithmetic uses already-converted leaf fields |
| Ratio | yes | ratio does not divide again by factor |
| Pivot metrics | yes | dedicated semantic-scale pivot fixture compares `pivot.metrics` result with native SQL `sum(physical / factor)` |
| timeWindow targetMetrics | yes | dedicated semantic-scale rolling_7d fixture compares window output with native SQL baseline |
| deniedColumns | yes | denied physical `total_cost_cent` denies semantic `totalCost` |
| fieldAccess | yes | field allowlist still applies to semantic field name |
| Model validation | yes | invalid factor and formula stacking fail closed |

## Completion Definition

Implementation is not complete until:

- `mvn -pl foggy-dataset-model test` passes, or any unavailable external dependency is explicitly documented with risk and follow-up. Done with SQLite profile and `-P!multi-db`.
- Progress doc is updated with development, testing, and experience status. Done.
- Implementation self-check is recorded. Done in progress doc.
- Formal `foggy-implementation-quality-gate` is completed because this changes shared semantic engine behavior. Pending.
- Test coverage audit is completed before acceptance signoff. Done; conclusion is ready for acceptance after quality gate.

## Experience

experience: N/A. This is a backend semantic engine capability with no UI interaction surface in Java V1.
