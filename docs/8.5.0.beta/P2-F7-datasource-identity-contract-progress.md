# P2-F7 Datasource Identity Contract — Java Mirror Progress

**Status**: ✅ Complete
**Python parity commit**: `9273c73 feat(compose): close Python follow-up stages 1 and 2`

## Contract Decision

- `ModelInfoProvider.getDatasourceId(modelName, namespace) → Optional<String>`
- Default method — `@FunctionalInterface` preserved
- `Optional.empty()` = unknown datasource (permissive, no rejection)
- `ModelBinding` **not modified**

## Changed Files

### Production (6 files)

| File | Change |
|------|--------|
| `ModelInfoProvider.java` | Added `getDatasourceId` default method + F-7 Javadoc |
| `NullModelInfoProvider.java` | Override returning `Optional.empty()` + updated Javadoc |
| `DatasourceIdCollector.java` | **NEW** — mirrors Python `datasource_ids.py`; fail-open with `java.util.logging` |
| `ComposeSqlCompiler.java` | `CompileOptions.datasourceIds` field + auto-collection from provider |
| `ComposePlanner.java` | `CompileState.datasourceIds` + `checkCrossDatasource()` guard in `compileUnion`/`compileJoin` |
| `ComposeCompileErrorCodes.java` | Updated `CROSS_DATASOURCE_REJECTED` Javadoc (no longer "deferred") |

### Test (4 files)

| File | Change |
|------|--------|
| `ModelInfoProviderSmokeTest.java` | +2 tests: default method + NullProvider getDatasourceId |
| `ComposeSqlCompilerTest.java` | Builder readback + provider auto-collection rejection path |
| `UnionCompileTest.java` | Replaced `@Disabled` placeholder with 7 real F-7 tests |
| `JoinCompileTest.java` | +5 F-7 cross-datasource tests |

### Docs (1 file)

| File | Change |
|------|--------|
| `docs/8.5.0.beta/P2-F7-datasource-identity-contract-progress.md` | Java mirror progress and evidence |

## Test Coverage

- Union: rejection, same-ds pass, unknown permissive, both-unknown permissive, no-provider compat, 3-way mismatch, UNION ALL mismatch
- Join: inner rejection, same-ds pass, unknown permissive, no-provider compat, left join mismatch
- ModelInfoProvider: default method, NullProvider, lambda @FunctionalInterface proof
- ComposeSqlCompiler: explicit datasourceIds and modelInfoProvider auto-collection paths

## Test Results

```
Focused:  ModelInfoProviderSmokeTest + ComposeSqlCompilerTest + UnionCompileTest + JoinCompileTest + ComposeCompileErrorCodesTest
          three surefire lanes, each 66 tests / 0 failures / 0 errors / 0 skipped — BUILD SUCCESS
Compose:  184 tests run, 0 failures, 0 errors, 0 skipped — BUILD SUCCESS
Parity:   FormulaParitySnapshotTest
          three surefire lanes, each 5 tests / 0 failures / 0 errors / 0 skipped — BUILD SUCCESS
```

## Residual Risk

- Provider exception → fail-open via `DatasourceIdCollector.safeGetDatasourceId` — compatibility design, not security boundary
- No CI workflow exists yet; when CI is added, the provider can be wired to real datasource registry

## Python Alignment

| Python | Java |
|--------|------|
| `authority/datasource_ids.py` | `authority/DatasourceIdCollector.java` |
| `ModelInfoProvider.get_datasource_id()` | `ModelInfoProvider.getDatasourceId()` |
| `compiler.compile_plan_to_sql(datasource_ids=)` | `CompileOptions.datasourceIds()` |
| `_CompileState.datasource_ids` | `CompileState.datasourceIds` |
| `_check_cross_datasource(plan, state, kind)` | `checkCrossDatasource(plan, state, planKind)` |
| Error code: `compose-compile-error/cross-datasource-rejected` | Same (byte-for-byte) |
| Phase: `plan-lower` | Same (byte-for-byte) |
