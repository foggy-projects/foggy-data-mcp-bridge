---
type: optimization
version: 9.2.12
ticket: P0-runtime-managed-datasource-pool-lifecycle
priority: P0
status: implementation-validated
owner: foggy-runtime-api
owner_modules:
  - foggy-runtime-api
  - foggy-runtime-cli
created_at: 2026-07-03
updated_at: 2026-07-03
---

# P0 Runtime Managed Datasource Pool Lifecycle

## Document Purpose

- doc_type: workitem
- intended_for: execution-agent, reviewer, signoff-owner
- purpose: Define the Runtime API-managed datasource pool lifecycle before implementation.

## Background

Runtime API-managed datasources are becoming the public CLI onboarding path for user-owned databases. The initial generic JDBC support can register and resolve non-SQLite datasources, but it does not yet own a durable pool lifecycle.

The existing FSScript path can create `HikariDataSource` instances through `dataSourceFactory.create(...)`, but those pools are cached in `DataSourceFactoryImpl` without explicit close semantics. That path should not be the recommended public datasource path until its lifecycle is hardened.

## Problem Statement

Runtime-created datasources need predictable lifecycle behavior:

- JVM GC must not be treated as connection-pool cleanup.
- Namespace/model refresh should clear model caches, not close shared datasource pools.
- Keeping every pool open forever is wasteful for demo, local, and agent-driven workflows.
- Complex namespace/model reference counting is too heavy for the current public runtime.
- A closed idle pool should be restartable from its saved config without requiring the user to re-add the datasource.

## Target Outcome

Introduce a `ManagedDataSourcePoolManager` for Runtime API-managed datasources:

- Manager stores datasource config slots in memory and continues to persist configs through the runtime datasource registry file.
- Hikari pool instances are lazy and optional per slot.
- Resolving a datasource creates a Hikari pool only when no live pool exists.
- Closing a pool does not remove the slot or registry record.
- A later resolve recreates a fresh Hikari instance from the saved config.
- The manager closes pools on config replacement, datasource removal, and runtime shutdown.
- Idle cleanup closes pools that have no active connections and have been returned idle for longer than the configured threshold.

## Pool Lifecycle Rules

| Event | Required behavior |
|---|---|
| First resolve | Create Hikari pool from the saved runtime datasource record. |
| Resolve while pool live | Reuse the current pool. |
| Resolve after idle close | Create a new Hikari pool from the saved record. |
| Save/update with same config fingerprint | Keep the current pool. |
| Save/update with changed config fingerprint | Close the old pool and keep the new config; recreate lazily on next resolve. |
| Remove datasource | Close pool, delete record, and remove namespace bindings for that datasource. |
| Runtime shutdown | Close all live pools. |
| Namespace refresh | Do not close datasource pools. |

## Idle Close Design

Hikari exposes active/idle connection counts through pool MXBean APIs, but it does not provide the exact "last connection returned at" lifecycle timestamp required by this design as a stable public contract. Foggy should wrap datasource/connection usage to maintain the timestamps it needs.

Required tracking:

- `lastBorrowedAt`: updated when `getConnection()` is called.
- `lastReturnedAt`: updated when the wrapped `Connection.close()` returns the connection to Hikari.
- `activeConnections`: read from Hikari MXBean, or maintained by the wrapper if MXBean is unavailable.

Cleanup rule:

- A scheduled cleaner runs every `cleanupIntervalMinutes`.
- A pool can be closed only when:
  - the pool exists;
  - the pool is not already closed;
  - active connection count is `0`;
  - `now - lastReturnedAt > idlePoolCloseMinutes`.
- Closing the pool does not remove its manager slot or registry config.

Recommended defaults:

| Setting | Default | Notes |
|---|---:|---|
| `idlePoolCloseMinutes` | `15` | Keeps local/demo resources bounded. |
| `cleanupIntervalMinutes` | `1` | Low overhead, fast enough for manual testing. |
| `maximumPoolSize` | `4` | Conservative default for runtime demos and agent workflows. |
| `minimumIdle` | `0` | Lets idle pools fully drain before the manager closes them. |
| `connectionTimeoutMs` | `10000` | Fails quickly during onboarding mistakes. |
| `idleTimeoutMs` | `120000` | Lets Hikari shrink idle connections before manager-level close. |
| `maxLifetimeMs` | `1800000` | Standard conservative Hikari lifetime. |

SQLite handling:

- Either keep SQLite non-pooled, or use Hikari with `maximumPoolSize=1` and `minimumIdle=0`.
- Do not default SQLite to multi-connection pools.

## Public API And CLI Expectations

- Runtime API datasource add/update remains config-oriented.
- Password handling remains dev/test oriented in this iteration:
  - prefer `passwordRef` for user workflows;
  - plaintext `password` remains local/dev only if supported by the current endpoint.
- The default datasource registry file remains cwd-relative:
  - default path: `.foggy-runtime/runtime-datasources.json`;
  - resolved relative to the runtime process working directory, not the user home directory;
  - namespace datasource bindings are stored in the same file;
  - `.foggy-runtime/runtime-datasource-registry.json` is not a default file name in this implementation.
- CLI should gain a datasource diagnostic command or output mode that shows:
  - registry path;
  - datasource type;
  - whether the datasource is runtime-managed;
  - whether a pool currently exists;
  - whether the pool is live or idle-closed;
  - last borrowed/returned timestamps when available;
  - effective pool defaults.

## FSScript Boundary

FSScript datasource lifecycle is related but not the primary 9.2.12 implementation path.

Follow-up requirements:

- `DataSourceFactoryImpl` should expose `closeAll()` and close cached Hikari pools on Spring/JVM shutdown.
- The FSScript datasource cache key should use a config fingerprint, not only `url + username`.
- Namespace refresh should not close FSScript-created datasources unless a future owner model is added.
- Public onboarding should continue to recommend Runtime API-managed `dataSourceName` over `dataSource: ds.customDb`.

## Touched Code Areas

- `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/impl/loader/TableModelLoaderManagerImpl.java`
- `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/spi/NamedDataSourceResolver.java`
- `foggy-dataset-model/src/test/java/com/foggyframework/dataset/db/model/impl/loader/TableModelLoaderManagerImplDataSourceResolutionTest.java`
- `foggy-runtime-api/src/main/java/com/foggyframework/runtime/api/service/RuntimeDatasourceRegistryService.java`
- `foggy-runtime-api/src/main/java/com/foggyframework/runtime/api/service/RuntimeNamedDataSourceResolver.java`
- `foggy-runtime-api/src/main/java/com/foggyframework/runtime/api/service/ManagedDataSourcePoolManager.java`
- `foggy-runtime-api/src/main/java/com/foggyframework/runtime/api/service/ManagedDataSourcePool.java`
- `foggy-runtime-api/src/main/java/com/foggyframework/runtime/api/service/HikariManagedDataSourcePoolFactory.java`
- `foggy-runtime-api/src/main/java/com/foggyframework/runtime/api/service/ManagedDataSourcePoolSettings.java`
- `foggy-runtime-api/src/main/java/com/foggyframework/runtime/api/dto/DatasourcePoolInfo.java`
- `foggy-runtime-api/src/main/java/com/foggyframework/runtime/api/dto/DatasourceInfo.java`
- `foggy-runtime-api/src/main/java/com/foggyframework/runtime/api/config/FoggyRuntimeApiProperties.java`
- `foggy-runtime-api/src/main/java/com/foggyframework/runtime/api/controller/RuntimeDatasourcesController.java`
- `foggy-runtime-api/src/main/java/com/foggyframework/runtime/api/controller/RuntimeCapabilitiesController.java`
- `foggy-runtime-api/src/test/java/com/foggyframework/runtime/api/RuntimeCapabilitiesControllerEnabledTest.java`
- `foggy-runtime-api/src/test/java/com/foggyframework/runtime/api/service/ManagedDataSourcePoolManagerTest.java`
- `foggy-runtime-cli/src/foggy_runtime_cli/main.py`
- `foggy-runtime-cli/tests/test_cli.py`

## Acceptance Criteria

- Runtime API-managed MySQL datasource can be created through Runtime API/CLI.
- A TM in a newly uploaded namespace can use `dataSourceName` to query that MySQL datasource.
- `models validate`, `models refresh`, `models describe`, and query execution pass against the MySQL datasource.
- Runtime restart preserves the datasource registry and namespace binding.
- After restart, the same namespace/model query passes without re-adding the datasource.
- Idle cleanup closes an unused pool after the configured threshold without deleting the registry record.
- A new request after idle close recreates the pool and succeeds.
- Updating a datasource with a changed config closes the old pool.
- Removing a datasource closes its pool and removes its namespace binding.
- Runtime shutdown closes live pools.
- CLI tests cover generic datasource credentials and diagnostics.
- Java tests cover the full `ManagedDataSourcePoolManager` lifecycle and satisfy the strict test and quality bar below.

## ManagedDataSourcePoolManager Test And Quality Bar

`ManagedDataSourcePoolManager` is a core runtime lifecycle component. This work item cannot be accepted with only controller-level happy path coverage.

Required unit coverage, directly targeting `ManagedDataSourcePoolManager` and its datasource/connection wrapper:

- Lazy pool creation on first resolve/use.
- Pool reuse while the existing pool is live.
- Idle cleanup closes the live Hikari pool but keeps the managed config slot.
- Resolving after idle cleanup recreates a new Hikari instance from the retained config.
- Active borrowed connections prevent idle cleanup even when the idle threshold has elapsed.
- A long-running borrowed connection past the threshold is not closed until returned, then becomes cleanup-eligible.
- Updating a datasource with an unchanged config fingerprint keeps the current pool.
- Updating a datasource with a changed config fingerprint closes the old pool and creates on next use.
- Removing a datasource closes the pool and removes registry/binding state.
- Runtime shutdown closes every live pool exactly once.
- Unresolved `passwordRef` fails only at resolve/test time and does not break list, bind, or configured-state checks.
- SQLite uses the intended special handling: either non-pooled execution or max-pool-size 1 with no retained idle connections.
- Connection wrapper records `lastBorrowedAt` and `lastReturnedAt`; repeated `close()` calls are idempotent.
- Cleanup is deterministic in tests through injected `Clock`/ticker/manual cleanup trigger; no `Thread.sleep`-based unit tests.

Required integration coverage:

- Fast JUnit integration path with H2 or SQLite proving real `getConnection()` borrow/return and idle cleanup behavior.
- Runtime API/CLI MySQL path proving add datasource, bind namespace, upload model, validate, refresh, describe/query, restart runtime, and query again.

Strict implementation quality requirements:

- `Clock`, cleanup scheduler/executor, and Hikari factory must be injectable so lifecycle tests are deterministic.
- No static mutable singleton state for managed pools.
- `isConfigured`, datasource listing, namespace bind, and diagnostics must not create or borrow a pool.
- `resolve`, test-connection, and query execution may create a pool.
- Pool close must be idempotent and exception-safe.
- Concurrent resolve, cleanup, update, and remove must be synchronized or otherwise atomic; avoid double pool creation and use-after-close races.
- Pool config fingerprint must include JDBC URL, username, credential identity/material hash, driver, and pool settings, but logs and persisted diagnostics must not expose plaintext secrets.
- New lifecycle classes should provide JaCoCo evidence when module coverage tooling is available: target at least 90% line and 85% branch coverage for the manager/wrapper/fingerprint classes, with all public methods covered. If tooling is unavailable, the coverage audit must include a method-level requirement mapping table.
- Formal `foggy-implementation-quality-gate` and `foggy-test-coverage-audit` are required before this work item can move to acceptance; a lightweight self-check is not sufficient.

## Constraints / Non-Goals

- No complex namespace/model reference counting.
- No datasource close during namespace refresh.
- No production RBAC, audit, auth-code expansion, or credential-vault redesign.
- No cross-datasource semantic query enablement in this item.
- No broad FSScript datasource redesign in the primary implementation path.

## Design Review

review_status: passed-for-implementation

Review notes:

- The idle-close approach is simpler and safer than namespace reference counting for the standalone Runtime API.
- The design must not close pools based only on last borrow time; long-running queries would be at risk. The close condition must require active connections to be zero and use last return time.
- Hikari can provide active connection counts, but Foggy should own the borrow/return timestamps through a wrapper because those timestamps are part of Foggy's lifecycle contract.
- `minimumIdle=0` is required for this strategy; otherwise Hikari can keep idle physical connections even when the runtime wants pools to drain.
- Keeping the config slot after close is correct: configs are small, persisted, and let the runtime recreate pools without user action.
- The current Runtime API registry path remains cwd-relative unless explicitly configured; diagnostics should surface the resolved absolute path.

## Implementation Check-In

implemented_at: 2026-07-03

Runtime API changes:

- Added `ManagedDataSourcePoolManager` with lazy Hikari pool creation, config-slot retention, deterministic idle cleanup, config fingerprint replacement behavior, remove/shutdown close handling, and pool diagnostics.
- Added pool defaults under `foggy.runtime-api.datasource-pool.*`.
- Added runtime datasource diagnostics in `GET /api/v1/datasources`, surfaced by CLI `datasources diagnostics`.
- Updated runtime datasource registry integration so runtime-managed datasource `resolve` uses the managed pool manager, while list/bind/diagnostics avoid creating pools.
- Updated namespace datasource resolution so model validation, refresh, describe, and query execution can use the namespace-bound Runtime API-managed datasource when no TM-level datasource is explicitly set.
- Preserved datasource configs and namespace bindings in the runtime datasource registry file.

CLI changes:

- Added `foggy-runtime datasources diagnostics`.
- Added feature preflight compatibility for newer diagnostics and older datasource list support.
- Added tests for diagnostics output and generic datasource credential paths.

## Progress Tracking

| Item | Status | Notes |
|---|---|---|
| Workitem recorded | done | 9.2.12 work item created from datasource lifecycle design discussion. |
| Design review | done | Passed for implementation with wrapper timestamp requirement. |
| Strict manager test bar | done | Full lifecycle, deterministic cleanup, coverage, and quality requirements recorded. |
| Runtime pool manager implementation | done | Added managed lazy Hikari lifecycle with retained config slots and idle close. |
| Runtime API diagnostics | done | `GET /api/v1/datasources` returns resolved registry path and pool state. |
| CLI diagnostics | done | `foggy-runtime datasources diagnostics` added and covered by tests. |
| MySQL restart validation | done | Runtime-managed MySQL datasource, namespace binding, bundle, model validation, refresh, describe, query, restart, and query again passed. |
| Quality | done | Formal implementation quality gate passed; see `docs/9.2.12/quality/runtime-managed-datasource-pool-lifecycle-implementation-quality.md`. |
| Coverage | done | Coverage audit passed with direct lifecycle tests and MySQL restart evidence; see `docs/9.2.12/coverage/runtime-managed-datasource-pool-lifecycle-coverage-audit.md`. |
| Acceptance | ready | Ready for formal acceptance signoff if requested. |

## Experience Progress

experience: N/A

Reason: Backend/runtime datasource lifecycle and CLI diagnostics; no UI workflow is changed.

## Execution Checklist

- [x] Record 9.2.12 work item.
- [x] Record idle-close design and review constraints.
- [x] Implement managed pool manager.
- [x] Add Runtime API pool state diagnostics.
- [x] Add CLI diagnostics.
- [x] Add Java lifecycle tests.
- [x] Add CLI tests.
- [x] Run MySQL Runtime API restart validation.
- [x] Record implementation check-in.
- [x] Run quality and coverage review.
- [ ] Sign off acceptance.

## Validation Evidence

Local runtime evidence directory:

- `D:\foggy-projects\foggy-data-mcp\.codex-tmp\runtime-pool-smoke-20260703-173237`

Runtime setup:

- Runtime API: `http://127.0.0.1:18126`
- Datasource registry override: `D:\foggy-projects\foggy-data-mcp\.codex-tmp\runtime-pool-smoke-20260703-173237\runtime-datasources.json`
- Bundle registry override: `D:\foggy-projects\foggy-data-mcp\.codex-tmp\runtime-pool-smoke-20260703-173237\runtime-bundles.json`
- MySQL container: `mysql:8.0`, exposed on `127.0.0.1:13308`
- MySQL product version reported by Runtime API: `8.0.44`
- MySQL schema: `foggy_runtime_pool_test`
- Runtime datasource: `mysql-pool-smoke`
- Namespace: `mysqlpoolsmoke`
- Bundle: `mysql-pool-smoke-models`
- Model fixture: `docs/v4.1/contracts/runtime-api-v1/model-fixtures/minimal-fact-order`

Runtime API/CLI validation:

- `wait-ready`: passed.
- `capabilities`: `datasources.diagnostics`, `models.validate`, `models.refresh`, `models.describe`, and `query.execute` all reported `supported`.
- `datasources diagnostics`: recovered `mysql-pool-smoke` from `runtime-datasources.json` with `managedByRuntimeApi=true`, `lifecycleStatus=not-created`, `poolExists=false` before first use after restart.
- `datasources test mysql-pool-smoke`: connected to MySQL `8.0.44`.
- `datasources binding --namespace mysqlpoolsmoke`: recovered binding to `mysql-pool-smoke`.
- `bundles list --namespace mysqlpoolsmoke`: recovered `mysql-pool-smoke-models`.
- `models validate --namespace mysqlpoolsmoke`: valid, `totalFiles=2`, `validFiles=2`.
- `models refresh --namespace mysqlpoolsmoke --model FactOrderQueryModel`: loaded `FactOrderQueryModel`.
- `models describe --namespace mysqlpoolsmoke FactOrderQueryModel`: returned runtime-observed dictionary values from MySQL rows.
- `query validate --namespace mysqlpoolsmoke FactOrderQueryModel`: passed.
- `query execute --namespace mysqlpoolsmoke FactOrderQueryModel`: returned `ORD-1001`, `ORD-1002`, and `ORD-1003` from MySQL.
- Full restart validation: after stopping and restarting Runtime API with the same registry files, datasource, namespace binding, bundle, model refresh, and query execution all passed without re-adding datasource, binding, or bundle.

## Acceptance Readiness

status: ready-for-acceptance

Reason: Implementation, automated tests, MySQL restart validation, quality gate, and coverage audit are complete. Formal acceptance signoff is the remaining optional step.
