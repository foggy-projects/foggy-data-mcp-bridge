---
audit_scope: feature
audit_mode: pre-acceptance-check
version: 9.2.12
target: P0-runtime-managed-datasource-pool-lifecycle
status: reviewed
conclusion: ready-for-acceptance
reviewed_by: codex
reviewed_at: 2026-07-03
follow_up_required: no
---

# Test Coverage Audit

## Background

- Audit target: Runtime API-managed datasource pool lifecycle, namespace-bound datasource model/query support, and CLI diagnostics.
- Current phase: implementation quality gate passed.
- Audit goal: verify that requirements and acceptance criteria have mapped unit, integration, CLI, or manual runtime evidence.

## Audit Basis

- requirement: `docs/9.2.12/workitems/P0-runtime-managed-datasource-pool-lifecycle.md`
- implementation progress: `docs/9.2.12/README.md`
- quality gate: `docs/9.2.12/quality/runtime-managed-datasource-pool-lifecycle-implementation-quality.md`
- test records:
  - `mvn -pl foggy-runtime-api -Dtest=ManagedDataSourcePoolManagerTest test`
  - `mvn -pl foggy-dataset-model -Dtest=TableModelLoaderManagerImplDataSourceResolutionTest test`
  - `mvn -pl foggy-runtime-api test`
  - `python -m pytest` in `foggy-runtime-cli`
- manual evidence:
  - `D:\foggy-projects\foggy-data-mcp\.codex-tmp\runtime-pool-smoke-20260703-173237`
  - Runtime API on `http://127.0.0.1:18126`
  - MySQL `8.0.44`, schema `foggy_runtime_pool_test`, datasource `mysql-pool-smoke`, namespace `mysqlpoolsmoke`

## Coverage Matrix

| Item | Risk | Unit | Integration | E2E | Playwright | Manual | Evidence Path | Coverage |
|------|------|------|-------------|-----|------------|--------|---------------|----------|
| Lazy pool creation and reuse | critical | yes | yes | no | no | yes | `ManagedDataSourcePoolManagerTest.shouldCreatePoolLazilyOnResolveAndReuseLivePool`; MySQL diagnostics | covered |
| Idle cleanup closes pool but keeps config slot | critical | yes | yes | no | no | yes | `ManagedDataSourcePoolManagerTest.shouldCloseIdlePoolKeepSlotAndRecreateOnNextResolve`; H2 real pool test | covered |
| Resolve after idle close recreates pool | critical | yes | yes | no | no | yes | `ManagedDataSourcePoolManagerTest.shouldCloseIdlePoolKeepSlotAndRecreateOnNextResolve`; H2 real pool test | covered |
| Active or long-running borrowed connection prevents cleanup | critical | yes | no | no | no | no | `ManagedDataSourcePoolManagerTest.shouldNotCloseActiveOrLongRunningBorrowedConnectionUntilReturnedAndIdle` | covered |
| Same fingerprint keeps pool; changed fingerprint closes old pool | major | yes | no | no | no | no | `ManagedDataSourcePoolManagerTest.shouldKeepPoolForSameFingerprintAndCloseForChangedFingerprint` | covered |
| Remove and shutdown close pools exactly once | critical | yes | no | no | no | no | `ManagedDataSourcePoolManagerTest.shouldClosePoolOnRemoveAndShutdownExactlyOnce` | covered |
| Unresolved passwordRef does not break list/bind but fails on resolve | major | yes | yes | no | no | no | `ManagedDataSourcePoolManagerTest.shouldNotResolveUnresolvedPasswordRefDuringSaveButFailOnResolve`; runtime-api full tests | covered |
| SQLite single-connection pool settings | major | yes | no | no | no | no | `ManagedDataSourcePoolManagerTest.shouldApplySQLiteSingleConnectionPoolSettings` | covered |
| Borrow/return timestamps and idempotent wrapped close | critical | yes | yes | no | no | yes | `ManagedDataSourcePoolManagerTest.shouldTrackBorrowReturnTimestampsAndMakeWrappedConnectionCloseIdempotent`; diagnostics output | covered |
| Concurrent resolve creates only one pool | critical | yes | no | no | no | no | `ManagedDataSourcePoolManagerTest.shouldCreateOnlyOnePoolWhenResolveIsConcurrent` | covered |
| Real JDBC pool borrow/return and idle cleanup | major | yes | yes | no | no | no | `ManagedDataSourcePoolManagerTest.shouldUseRealHikariPoolForH2ConnectionAndIdleCleanup` | covered |
| Runtime API diagnostics include registry path and pool state | major | yes | yes | no | no | yes | `RuntimeCapabilitiesControllerEnabledTest`; MySQL `datasources diagnostics` output | covered |
| CLI diagnostics command and compatibility behavior | major | yes | no | no | no | no | `foggy-runtime-cli/tests/test_cli.py`; full `python -m pytest` | covered |
| Namespace default datasource resolution priority | critical | yes | yes | no | no | yes | `TableModelLoaderManagerImplDataSourceResolutionTest`; MySQL model/query smoke | covered |
| Runtime-managed MySQL datasource add/test/bind | critical | no | yes | no | no | yes | Runtime API MySQL smoke in `.codex-tmp/runtime-pool-smoke-20260703-173237` | covered |
| Uploaded namespace model validate/refresh/describe/query against MySQL | critical | no | yes | yes | no | yes | CLI Runtime API smoke: `models validate`, `models refresh`, `models describe`, `query validate`, `query execute` | covered |
| Restart preserves datasource registry and namespace binding | critical | no | yes | yes | no | yes | Restart smoke using same `runtime-datasources.json` and `runtime-bundles.json` | covered |
| Registry file location semantics | major | no | yes | no | no | yes | Runtime diagnostics and docs: default cwd `.foggy-runtime/runtime-datasources.json`; smoke override path | covered |

## Evidence Summary

- Automated tests:
  - `ManagedDataSourcePoolManagerTest`: 10 tests passed, covering all public manager lifecycle paths and connection wrapper behavior.
  - `TableModelLoaderManagerImplDataSourceResolutionTest`: 4 tests passed in each configured surefire execution, covering datasource priority and namespace fallback.
  - `mvn -pl foggy-runtime-api test`: 58 tests passed.
  - `python -m pytest` in `foggy-runtime-cli`: 87 tests passed.
- Manual/integration runtime smoke:
  - MySQL container exposed `127.0.0.1:13308`; Runtime API reported MySQL `8.0.44`.
  - Datasource `mysql-pool-smoke` was registered through Runtime API/CLI and bound to namespace `mysqlpoolsmoke`.
  - Bundle `mysql-pool-smoke-models` pointed to `docs/v4.1/contracts/runtime-api-v1/model-fixtures/minimal-fact-order`.
  - `models validate`, `models refresh`, `models describe`, `query validate`, and `query execute` passed against `FactOrderQueryModel`.
  - Query execution returned `ORD-1001`, `ORD-1002`, and `ORD-1003` from MySQL.
  - Runtime was stopped and restarted with the same registry files; datasource, namespace binding, bundle, refresh, and query still passed without re-adding config.
- Coverage tooling:
  - `foggy-runtime-api` has no existing module-level JaCoCo profile.
  - Because module coverage tooling is unavailable, this audit uses the required method/acceptance mapping table above.

## Gaps

- No blocking coverage gaps.
- Non-blocking release gap: public release assets must be rebuilt/tagged before external users can consume the 9.2.12 behavior from downloads.
- Non-blocking follow-up: FSScript datasource lifecycle hardening remains outside this feature and is not covered here.

## Recommended Next Skills

- `foggy-acceptance-signoff`: recommended if formal signoff is requested for 9.2.12.
- `integration-test`: not required for the current acceptance set; add only if release packaging introduces a new launcher distribution path.
- `webapp-testing`: not applicable.
- `foggy-bug-regression-workflow`: not required; no blocking bug remains.
- `plan-evaluator`: not required.

## Conclusion

- conclusion: ready-for-acceptance
- can_enter_acceptance: yes
- follow_up_required: no
