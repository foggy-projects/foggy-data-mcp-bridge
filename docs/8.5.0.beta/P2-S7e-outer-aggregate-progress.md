# P2-S7e outer aggregate progress

## Metadata

- version: 8.5.0.beta
- status: completed
- owner: Java
- previous_stage: S7d relation-as-source read-only
- next_stage: S7f outer window
- contract_ref: `foggy-data-mcp-bridge-python/docs/v1.5/S7b-stage7-runtime-contract-plan.md`

## Delivered

S7e opens outer aggregate over `CompiledRelation` while keeping outer window
closed. Aggregation is allowed only when the relation capability is open and
the input column's `referencePolicy` contains `aggregatable`.

Implemented behavior:

- `ReferencePolicy.MEASURE_DEFAULT` now includes `aggregatable`.
- `RelationCapabilities.forDialect(...)` sets `supportsOuterAggregate=true`
  for wrappable relations.
- MySQL 5.7 with inner CTE remains `FAIL_CLOSED` and does not open aggregate.
- `OuterQuerySpec` now carries explicit `groupBy`.
- `RelationOuterQueryBuilder` supports `SUM`, `AVG`, `COUNT`, `MIN`, `MAX`
  over aggregatable columns.
- `COUNT(*)` is allowed; other star aggregates are rejected.
- ratio / percent style timeWindow derived columns remain non-aggregatable.
- aggregate output schema is re-derived with
  `semanticKind=aggregate_measure`, lineage, value meaning, and measure
  reference policy.
- SQL Server hoisted CTE rendering now preserves structured inner `withItems`
  before the relation CTE and retains stable parameter order.

## Boundary Decisions

- Outer window remains closed: `supportsOuterWindow=false` and `OVER(...)`
  still throws `RELATION_OUTER_WINDOW_NOT_SUPPORTED`.
- S7a schema snapshot stays frozen as `S7a-1`; it intentionally records
  `supportsOuterAggregate=false` for the original mirror contract.
- S7e uses a new snapshot:
  `target/parity/_stable_relation_outer_aggregate_snapshot.json` with
  `contractVersion=S7e-1`.
- GroupBy currently reuses the readable-column error family when a column is
  present but not groupable; this keeps the error surface small for S7e.

## Evidence

Focused S7e/S7c/S7d verification:

```powershell
mvn test -pl foggy-dataset-model "-Dtest=RelationOuterQueryBuilderTest,StableRelationOuterAggregateSnapshotTest,StableRelationSnapshotTest,ComposeCompileErrorCodesTest,RelationModelTest,ComposeRelationCompilerTest,ColumnSpecMetadataTest,TimeWindowOutputSchemaTest"
```

Result:

- 115 tests per Surefire run set.
- 3 Surefire lanes executed by the module profile.
- 345 total executions, 0 failures, 0 errors, 0 skipped.

Snapshot-only verification:

```powershell
mvn test -pl foggy-dataset-model "-Dtest=StableRelationSnapshotTest,StableRelationOuterAggregateSnapshotTest"
```

Result:

- 3 tests per Surefire run set.
- 3 Surefire lanes executed.
- 9 total executions, 0 failures, 0 errors, 0 skipped.

## Python Mirror Readiness

Python can now add a new S7e snapshot consumer for:

- `_stable_relation_outer_aggregate_snapshot.json`
- `contractVersion == "S7e-1"`
- positive `SUM + GROUP BY` cases
- ratio aggregate rejection
- MySQL 5.7 CTE fail-closed
- SQL Server hoisted CTE with no `FROM (WITH`

The existing Python S7a snapshot tests should continue consuming
`_stable_relation_schema_snapshot.json` and should not be changed to expect
S7e capabilities.
