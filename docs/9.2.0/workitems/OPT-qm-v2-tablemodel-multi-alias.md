---
type: optimization
bug_source: upstream-feedback
version: 9.2.0
ticket: OPT-qm-v2-tablemodel-multi-alias
severity: major
status: implemented
test_strategy: unit-test + integration-test
automation_decision: required
owner: foggy-dataset-model
created_at: 2026-06-05
updated_at: 2026-06-06
owner_repo: foggy-data-mcp-bridge-wt-dev-compose
owner_module: foggy-dataset-model
---

# OPT: Support QM v2 TableModel Multiple Explicit Aliases

## Purpose

Record and track the Java query engine change that makes ordinary QM v2 `TableModel` aliases a stable public capability. This unblocks query models that need the same physical TM to participate in one query model multiple times, for example one station model joined as both origin and destination stops.

## Background

TMS O614 needs one `FactTaskStationModel` to be loaded twice in the same QM:

```js
const originStop = loadTableModel('FactTaskStationModel').as('originStop');
const destinationStop = loadTableModel('FactTaskStationModel').as('destinationStop');
```

Runtime probes showed that ordinary `leftJoin` and field-to-field comparison such as `.gt(leftField, rightField)` were already usable, but ordinary TM aliasing was not exposed safely:

```text
loadTableModel('FactTaskStationModel').as('originStop')
-> failed while resolving fields from java.lang.Object

loadTableModel('FactTaskStationModel', 'originStop')
-> failed because the returned object did not expose join methods
```

Without a stable alias surface, model authors cannot express `originStop` and `destinationStop` independently, and direct query callers cannot address aliased fields in `columns`, `slice`, or `orderBy`.

## Public Syntax

Both forms are now supported for ordinary table models:

```js
const originStop = loadTableModel('FactTaskStationModel').as('originStop');
const destinationStop = loadTableModel('FactTaskStationModel', 'destinationStop');
```

Alias-qualified fields are exposed through the same field name that the QM author declares:

```json
{
  "columns": [
    "originStop.sequence",
    "destinationStop.sequence"
  ],
  "slice": [
    { "field": "originStop.sequence", "op": ">", "value": 0 },
    { "field": "destinationStop.sequence", "op": ">", "value": 0 }
  ],
  "orderBy": [
    { "field": "destinationStop.sequence", "order": "asc" }
  ]
}
```

Join conditions may compare fields from different aliases:

```js
task.leftJoin(originStop)
  .on(task.tenantId, originStop.tenantId)
  .and(task.originStationId, originStop.stationId);

task.leftJoin(destinationStop)
  .on(task.tenantId, destinationStop.tenantId)
  .and(task.destinationStationId, destinationStop.stationId)
  .gt(destinationStop.sequence, originStop.sequence);
```

Aggregate relation joins may also reference aliases that have already been
registered earlier in the same query graph. The extra condition remains in the
current JOIN ON clause:

```js
task.leftJoin(lane)
  .on(task.tenantId, lane.tenantId)
  .and(originStop.stationId, lane.originStationId)
  .and(destinationStop.stationId, lane.destinationStationId);
```

## Expected Behavior

- The same TM can appear more than once in one QM when every repeated instance has a distinct explicit alias.
- Explicit aliases are preserved as public field qualifiers in selected columns.
- `slice`, `columns`, `orderBy`, and join `on` conditions can resolve alias-qualified root TM fields independently.
- Generated SQL uses the explicit alias as the physical table alias, so the two occurrences do not collide.
- Later aggregate relation joins may use already joined RHS aliases as the left side of additional ON predicates.
- Alias predicates declared in a join stay in that join's ON expression and are not promoted to the main query WHERE.
- Duplicate aliases are rejected during QM parsing with a clear model-author error.
- Runtime-generated aliases such as `t1` remain internal and are not exposed as public field qualifiers.

## Non-Goals

- Do not expose raw SQL or CTE syntax as an alias workaround.
- Do not change business DSL payload shape beyond allowing alias-qualified field names that come from QM definitions.
- Do not relax field resolution for undeclared aliases.
- Do not claim full nested dimension alias disambiguation from this item; this implementation and verification focus on ordinary TM root fields.

## Code Inventory

| Path | Change |
|---|---|
| `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/proxy/TableModelProxy.java` | Add `.as(alias)` public proxy operation and keep explicit alias identity. |
| `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/proxy/LoadTableModelFunction.java` | Add `loadTableModel(modelName, alias)` overload. |
| `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/engine/query_model/JdbcQueryModelBuilder.java` | Parse same TM multiple times by model+alias key and reject duplicate aliases. |
| `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/engine/query_model/QueryModelSupport.java` | Resolve alias-qualified fields and build join graph paths against alias-specific query objects. |
| `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/impl/model/AggregateJoinTableModel.java` | Validate aggregate relation ON left refs against the current query graph's registered left-side aliases. |
| `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/plugins/result_set_filter/SchemaAwareFieldValidationStep.java` | Allow schema validation to accept dynamically resolvable alias-qualified fields. |
| `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/proxy/JoinCondition.java` | Resolve join fields against the intended table alias. |
| `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/def/query/SelectColumnDef.java` | Preserve explicit alias qualifiers in public selected field names. |

## Implementation Checklist

- [x] Add `.as(alias)` to ordinary `TableModelProxy`.
- [x] Add `loadTableModel(modelName, alias)` overload.
- [x] Keep same model with different aliases as separate parsed models.
- [x] Reject duplicate aliases in one QM.
- [x] Preserve alias-qualified field names for selected columns and lookup.
- [x] Resolve alias-qualified `slice` and `orderBy` fields.
- [x] Resolve join conditions against alias-specific fields.
- [x] Allow later aggregate relation ON conditions to reference previously joined aliases.
- [x] Keep aggregate relation ON predicates in the current JOIN ON SQL.
- [x] Add integration coverage for one TM joined twice in one QM.
- [x] Run targeted tests and record results.

## Verification

Passed on 2026-06-05:

```powershell
mvn -pl foggy-dataset-model "-Dtest=TableModelProxyTest,AdvancedAnalyticsTest#testQmV2SameTableModelMultipleAliases" "-Dspring.profiles.active=sqlite" test
```

Result:

- Total: 25 tests, 0 failures, 0 errors.
- Covers `.as(alias)`, `loadTableModel(modelName, alias)`, same-TM dual alias join, alias-qualified `columns`, `slice`, and `orderBy`.

Additional targeted regression passed on 2026-06-05:

```powershell
mvn -pl foggy-dataset-model "-Dtest=AdvancedAnalyticsTest#testQmPredefinedScalarFormulaOuterAggregation,BasicQueryTest#testSimpleFieldQuery" "-Dspring.profiles.active=sqlite" test
```

Result:

- Targeted tests passed with 0 failures and 0 errors.

The self-alias integration test rendered SQL with distinct physical aliases:

```sql
left join fact_sales leftSales ...
left join fact_sales rightSales ...
where leftSales.order_line_no = ? and rightSales.order_line_no > ?
order by rightSales.order_line_no asc
```

Additional aggregate relation ON regression passed on 2026-06-06:

```powershell
mvn -pl foggy-dataset-model "-Dtest=AggregateJoinQueryModelTest" "-Dspring.profiles.active=sqlite" test
mvn -pl foggy-dataset-model "-Dtest=AdvancedAnalyticsTest#testQmV2SameTableModelMultipleAliases" "-Dspring.profiles.active=sqlite" test
mvn -pl foggy-dataset-model "-Dtest=TableModelProxyTest" "-Dspring.profiles.active=sqlite" test
```

Result:

- `AggregateJoinQueryModelTest`: 21 tests, 0 failures, 0 errors.
- `AdvancedAnalyticsTest#testQmV2SameTableModelMultipleAliases`: 1 test, 0 failures, 0 errors.
- `TableModelProxyTest`: 24 tests per surefire profile, 0 failures, 0 errors.

The aggregate relation regression rendered the previously joined alias field in
the current join ON clause:

```sql
left join (...) aggregateSalesByLine
  on t1.order_id = aggregateSalesByLine.orderId
 AND leftSales.order_line_no = aggregateSalesByLine.orderLineNo
```

## Changed Files

- `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/proxy/TableModelProxy.java`
- `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/proxy/LoadTableModelFunction.java`
- `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/proxy/ColumnRef.java`
- `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/proxy/DimensionProxy.java`
- `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/proxy/JoinCondition.java`
- `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/def/query/SelectColumnDef.java`
- `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/engine/query_model/JdbcQueryModelBuilder.java`
- `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/engine/query_model/QueryModelSupport.java`
- `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/impl/model/AggregateJoinTableModel.java`
- `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/plugins/result_set_filter/SchemaAwareFieldValidationStep.java`
- `foggy-dataset-model/src/test/java/com/foggyframework/dataset/db/model/proxy/TableModelProxyTest.java`
- `foggy-dataset-model/src/test/java/com/foggyframework/dataset/db/model/ecommerce/AdvancedAnalyticsTest.java`
- `foggy-dataset-model/src/test/resources/foggy/templates/ecommerce/query/FactSalesSelfAliasJoinQueryModel.qm`
- `foggy-dataset-demo/src/main/resources/foggy/templates/ecommerce/query/FactSalesSelfAliasJoinQueryModel.qm`

## Quality Check

Self-check only. The change is scoped to QM v2 ordinary table-model alias parsing, alias-qualified field resolution, join graph alias binding, and aggregate relation ON validation against the already registered query graph aliases. Existing non-aliased aggregate relation behavior is covered by the aggregate relation regression suite.

## Experience

N/A. Backend Java query engine behavior only; no frontend or UX surface changed.
