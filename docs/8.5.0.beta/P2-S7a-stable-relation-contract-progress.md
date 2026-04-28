# P2-S7a Stable Relation Contract Progress

## 基本信息

- version: 8.5.0.beta
- status: poc-complete / contract-frozen
- contract_ref: `foggy-data-mcp-bridge-python/docs/v1.5/S7a-plan-stable-view-relation-contract-preflight.md`
- contract_version: S7a-1

## Object Model Decision

`CteUnit` 保持内部 SQL assembly primitive (alias, sql, params, selectColumns)。不扩展。

Stable Relation 由新 `compose.relation` 包承载：
- `CompiledRelation` — 正式对外 stable relation contract
- `RelationSql` — 结构化 SQL（withItems + bodySql + bodyParams + preferredAlias）
- `CteItem` — 单个 CTE 子句
- `RelationCapabilities` — 方言感知包装策略
- `SemanticKind` / `ReferencePolicy` / `RelationWrapStrategy` / `RelationPermissionState` — 闭合字符串常量

## SQL Server Strategy

- 无 inner CTE → `inline_subquery`（所有方言一致）
- 有 inner CTE + SQL Server → `hoisted_cte`，`requiresTopLevelWith=true`
- 有 inner CTE + MySQL 5.7 → `fail_closed`
- 有 inner CTE + CTE-capable → `hoisted_cte`
- 禁止生成 `FROM (WITH`

## ColumnSpec 扩展

新增 4 个 nullable metadata 字段（excluded from equals/hashCode）：
- `semanticKind` — base_field / aggregate_measure / time_window_derived / scalar_calc / window_calc
- `valueMeaning` — LLM/reviewer 语义说明
- `lineage` — 上游字段来源
- `referencePolicy` — readable / groupable / aggregatable / windowable / orderable

## Error Codes

ComposeCompileErrorCodes:
- `RELATION_WRAP_UNSUPPORTED`
- `RELATION_CTE_HOIST_UNSUPPORTED`
- `RELATION_DATASOURCE_MISMATCH`

ComposeSchemaErrorCodes:
- `RELATION_OUTPUT_SCHEMA_UNAVAILABLE`
- `RELATION_COLUMN_REFERENCE_UNSUPPORTED`

## Changed Files

### New Files

| File | Description |
|---|---|
| `relation/SemanticKind.java` | 列语义分类常量 |
| `relation/ReferencePolicy.java` | 列引用策略常量 |
| `relation/RelationWrapStrategy.java` | 关系包装策略常量 |
| `relation/RelationPermissionState.java` | 权限状态常量 |
| `relation/CteItem.java` | CTE 子句模型 |
| `relation/RelationSql.java` | 结构化 SQL 模型 |
| `relation/RelationCapabilities.java` | 方言感知能力标志 |
| `relation/CompiledRelation.java` | 正式 stable relation |
| `relation/RelationModelTest.java` | 关系模型单元测试 |
| `schema/ColumnSpecMetadataTest.java` | ColumnSpec metadata 测试 |
| `plan/TimeWindowOutputSchemaTest.java` | TimeWindow OutputSchema 测试 |
| `parity/StableRelationSnapshotTest.java` | Schema snapshot 生成 |

### Modified Files

| File | Change |
|---|---|
| `schema/ColumnSpec.java` | +4 nullable metadata fields, builder, accessors |
| `plan/TimeWindowExpander.java` | +getOutputSchema() method |
| `compilation/ComposeCompileErrorCodes.java` | +3 relation error codes |
| `schema/ComposeSchemaErrorCodes.java` | +2 relation error codes |

## Snapshot

- Path: `target/parity/_stable_relation_schema_snapshot.json`
- Cases: 12 (4 dialects × 3 shapes)
- Contract version: S7a-1

## Non-Goals (Not Changed)

- 不开放 timeWindow + calculatedFields.agg
- 不开放 timeWindow + calculatedFields.windowFrame
- 不开放 targetMetrics 引用 request-level calculatedFields
- 不实现 named / recursive CTE
- 不处理普通列 alias
- 不物化 CREATE VIEW
- 不改 Python runtime
- 不改 CteUnit
- Stage 7 runtime capability: **NOT opened**
