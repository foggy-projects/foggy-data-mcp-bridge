# S7f Outer Window — Implementation Progress

**Contract Version**: S7f-1  
**Status**: ✅ Implementation complete, all tests pass  
**Snapshot**: `target/parity/_stable_relation_outer_window_snapshot.json`

## Scope

Outer window query capability on `CompiledRelation`:
- **Ranking**: `ROW_NUMBER()`, `RANK()`, `DENSE_RANK()`
- **Offset**: `LAG()`, `LEAD()`
- **Aggregate Window**: `SUM()`, `AVG()`, `MIN()`, `MAX()`, `COUNT()`

## Dialect Capability Matrix

| Dialect | `supportsOuterWindow` | Notes |
|---|---|---|
| mysql8 | ✅ true | Full window support |
| postgres | ✅ true | Full window support |
| sqlite | ✅ true | Full window support |
| mssql/sqlserver | ✅ true | CTE hoisted; `;WITH` prefix |
| mysql/mysql57 | ❌ false | No window functions — fail-closed |

## Reference Policy

| Default Set | WINDOWABLE? |
|---|---|
| `MEASURE_DEFAULT` | ✅ Yes |
| `DIMENSION_DEFAULT` | ❌ No |
| `TIME_WINDOW_DERIVED_DEFAULT` | ❌ No |

## Error Codes

| Code | When |
|---|---|
| `relation-outer-window-not-supported` | `supportsOuterWindow=false` |
| `relation-column-not-windowable` | Input column lacks `WINDOWABLE` |

## File Change Manifest

### New Files
- `WindowSelectSpec.java` — Structured window function specification
- `WindowSelectParser.java` — Restricted parser for window expressions
- `StableRelationOuterWindowSnapshotTest.java` — S7f snapshot producer (5 cases)
- `P2-S7f-outer-window-progress.md` — This document

### Modified Files
- `ReferencePolicy.java` — MEASURE_DEFAULT gains WINDOWABLE
- `RelationCapabilities.java` — `forDialect()` opens supportsOuterWindow
- `ComposeCompileErrorCodes.java` — New RELATION_COLUMN_NOT_WINDOWABLE
- `RelationOuterQueryBuilder.java` — Window validation, rendering, output schema
- `ComposeRelationCompiler.java` — Javadoc updated
- `RelationModelTest.java` — Updated capability matrix assertions
- `ColumnSpecMetadataTest.java` — WINDOWABLE policy tests
- `RelationOuterQueryBuilderTest.java` — 8 new window test cases
- `ComposeRelationCompilerTest.java` — Split window capability test
- `ComposeCompileErrorCodesTest.java` — Updated ALL_CODES count

## Test Results

- **Total**: 2038 tests, 0 failures, 0 errors
- **S7a snapshot**: 12 cases, contractVersion S7a-1 ✅
- **S7e snapshot**: 4 cases, contractVersion S7e-1 ✅
- **S7f snapshot**: 5 cases, contractVersion S7f-1 ✅

## Quality Gate Follow-up

- Window frame clauses are now validated through a restricted whitelist instead of being passed through after extraction.
- Supported frame forms are `ROWS` / `RANGE` with `UNBOUNDED PRECEDING`, `UNBOUNDED FOLLOWING`, `CURRENT ROW`, or numeric `PRECEDING` / `FOLLOWING` bounds, including `BETWEEN ... AND ...`.
- Unsupported frame clauses fail closed with `relation-outer-window-not-supported`.
- Focused verification: `RelationOuterQueryBuilderTest`, `StableRelationOuterWindowSnapshotTest`, `StableRelationOuterAggregateSnapshotTest`, `StableRelationSnapshotTest`, `RelationModelTest`, `ComposeRelationCompilerTest`, `ComposeCompileErrorCodesTest`, `ColumnSpecMetadataTest` -> 3 Surefire lanes, each 132 passed.
