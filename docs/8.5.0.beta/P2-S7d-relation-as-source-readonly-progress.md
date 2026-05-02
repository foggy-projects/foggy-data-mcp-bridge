# P2-S7d relation-as-source read-only progress

## Metadata

- version: 8.5.0.beta
- status: completed
- owner: Java
- previous_stage: S7c compileToRelation runtime entry
- next_stage: S7e outer aggregate
- contract_ref: `foggy-data-mcp-bridge-python/docs/v1.5/S7b-stage7-runtime-contract-plan.md`

## Delivered

S7d adds the first runtime use of `CompiledRelation` as an outer query source.
The stage remains read-only: it allows selecting readable columns, ordering by
orderable columns, filtering through declared readable filter dependencies, and
pagination. It does not open outer aggregate, outer window, relation join, or
relation union.

Changed runtime objects:

- `OuterQuerySpec`: immutable outer query request with select/order/filter/limit
  fields and explicit `filterColumns` dependency declaration.
- `RelationOuterQuery`: immutable rendered outer SQL result with params,
  output schema, datasource id, and dialect.
- `RelationOuterQueryBuilder`: validates column references against
  `OutputSchema` and `referencePolicy`, renders inline subquery or hoisted CTE,
  and preserves relation compile fail-closed invariants.

## Boundary Decisions

- `supportsOuterAggregate=false` remains enforced. Any `SUM` / `AVG` /
  `COUNT` / `MIN` / `MAX` select expression is rejected with
  `RELATION_OUTER_AGGREGATE_NOT_SUPPORTED`.
- `supportsOuterWindow=false` remains enforced. Any `OVER(...)` expression is
  rejected with `RELATION_OUTER_WINDOW_NOT_SUPPORTED`.
- Raw filter SQL is accepted only when `OuterQuerySpec.filterColumns` declares
  the referenced relation output columns. Missing declarations fail closed with
  `RELATION_COLUMN_NOT_READABLE`, because the builder cannot validate
  `referencePolicy` safely.
- SQL Server hoisted CTE output uses defensive `;WITH`.
- Generated SQL is still checked for the forbidden `FROM (WITH` marker.
- MySQL 5.7 + CTE remains fail-closed through `RelationWrapStrategy.FAIL_CLOSED`.
- `ColumnSpec.equals()` and `hashCode()` are unchanged.

## Evidence

External execution report before root-controller tightening:

- S7d dedicated tests: 21 tests x 3 Surefire lanes = 63 executions, 0 failures.
- Full Java suite: 270 tests, 0 failures, 0 errors.
- `git diff --check`: clean except expected Windows CRLF warnings.

Root-controller follow-up after tightening filter and SQL Server CTE behavior:

```powershell
mvn test -pl foggy-dataset-model "-Dtest=RelationOuterQueryBuilderTest,ComposeCompileErrorCodesTest,ComposeRelationCompilerTest,StableRelationSnapshotTest,RelationModelTest,ColumnSpecMetadataTest,TimeWindowOutputSchemaTest"
```

Result:

- 108 tests per Surefire run set, 3 lanes executed by the module profile.
- 324 total executions, 0 failures, 0 errors, 0 skipped.

## S7e Readiness

S7e may start from this baseline. Required next changes are:

- set `supportsOuterAggregate=true` only for explicitly supported relation
  capabilities;
- allow aggregate expressions only when all measure inputs include
  `ReferencePolicy.AGGREGATABLE`;
- keep ratio / percent derived columns non-aggregatable by default;
- derive a new aggregate output schema instead of reusing the inner schema
  blindly;
- add positive and negative snapshot cases for Python mirror consumption.
