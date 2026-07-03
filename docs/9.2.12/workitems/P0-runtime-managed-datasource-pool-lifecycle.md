---
type: optimization
version: 9.2.12
ticket: P0-runtime-managed-datasource-pool-lifecycle
priority: P0
status: ready-for-implementation
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

- `foggy-runtime-api/src/main/java/com/foggyframework/runtime/api/service/RuntimeDatasourceRegistryService.java`
- `foggy-runtime-api/src/main/java/com/foggyframework/runtime/api/service/RuntimeNamedDataSourceResolver.java`
- `foggy-runtime-api/src/main/java/com/foggyframework/runtime/api/config/FoggyRuntimeApiProperties.java`
- `foggy-runtime-api/src/main/java/com/foggyframework/runtime/api/controller/RuntimeDatasourcesController.java`
- `foggy-runtime-api/src/test/java/com/foggyframework/runtime/api/RuntimeCapabilitiesControllerEnabledTest.java`
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
- Java tests cover pool reuse, idle close/recreate, update close, remove close, and shutdown close.

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

## Progress Tracking

| Item | Status | Notes |
|---|---|---|
| Workitem recorded | done | 9.2.12 work item created from datasource lifecycle design discussion. |
| Design review | done | Passed for implementation with wrapper timestamp requirement. |
| Runtime pool manager implementation | pending | Add managed lazy Hikari lifecycle. |
| Runtime API diagnostics | pending | Add pool state and resolved registry path. |
| CLI diagnostics | pending | Add command/output path after Runtime API exposes state. |
| MySQL restart validation | pending | Must create runtime datasource, upload new namespace models, refresh, query, restart, and query again. |
| Quality | pending | Required after implementation. |
| Coverage | pending | Required after implementation tests pass. |
| Acceptance | pending | Required after runtime and CLI evidence is complete. |

## Experience Progress

experience: N/A

Reason: Backend/runtime datasource lifecycle and CLI diagnostics; no UI workflow is changed.

## Execution Checklist

- [x] Record 9.2.12 work item.
- [x] Record idle-close design and review constraints.
- [ ] Implement managed pool manager.
- [ ] Add Runtime API pool state diagnostics.
- [ ] Add CLI diagnostics.
- [ ] Add Java lifecycle tests.
- [ ] Add CLI tests.
- [ ] Run MySQL Runtime API restart validation.
- [ ] Record implementation check-in.
- [ ] Run quality and coverage review.
- [ ] Sign off acceptance.

## Acceptance Readiness

status: not-ready

Reason: Design is reviewed and ready for implementation, but code, tests, diagnostics, and restart validation are pending.

