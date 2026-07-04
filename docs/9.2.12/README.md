---
doc_role: version_followup_plan
doc_purpose: Track 9.2.12 Runtime API managed datasource pool lifecycle work and focused follow-up fixes.
version: 9.2.12
status: ready-for-acceptance
created_at: 2026-07-03
updated_at: 2026-07-03
---

# 9.2.12 Follow-up Iteration

## Document Purpose

- doc_type: version-summary
- intended_for: execution-agent, reviewer, signoff-owner
- purpose: Track the Runtime API managed datasource pool lifecycle work and focused 9.2.12 follow-up fixes.

## Scope

9.2.12 turns Runtime API-managed JDBC datasource support into the primary public datasource path for standalone runtime and CLI onboarding. It adds an explicit connection-pool lifecycle design so runtime-created datasources can support MySQL/PostgreSQL-style user databases without relying on JVM GC or namespace refresh side effects.

This iteration also carries focused backend/query-engine bug fixes that need versioned tracking before reviewer verification.

## Work Items

| Item | Doc | Status | Owner Module | Summary |
|---|---|---|---|---|
| Runtime managed datasource pool lifecycle | `workitems/P0-runtime-managed-datasource-pool-lifecycle.md` | ready-for-acceptance | `foggy-runtime-api`, `foggy-runtime-cli` | Added managed lazy Hikari pools, idle close, update/remove/shutdown close semantics, diagnostics, namespace-bound Runtime API datasource resolution for models/query, and MySQL restart validation. |
| Query access dimension filter join | `workitems/BUG-query-access-dimension-filter-join.md` | ready-for-verification | `foggy-dataset-model` | Fixed Issue 12 where access DSL dimension-field filters resolved WHERE predicates without registering the required dimension join. |

## Guardrails

- No complex namespace/model reference counting.
- Namespace refresh must not close datasource pools.
- Closing an idle pool must not delete the datasource registry record.
- `ManagedDataSourcePoolManager` must have direct deterministic lifecycle tests, not only Runtime API controller happy path tests.
- Manager/wrapper/fingerprint classes require strict coverage evidence: target at least 90% line and 85% branch coverage when coverage tooling is available, or a method-level requirement mapping table when it is not.
- Runtime API-managed datasource is the recommended public path; FSScript datasource remains an advanced embedded path until its factory lifecycle is hardened.
- No Runtime API auth-code, RBAC, audit, per-user permission, or production secret-manager redesign in this iteration.

## Progress Summary

- development: complete for Runtime API pool manager, namespace datasource resolution, and CLI diagnostics
- testing: Java lifecycle tests, Runtime API controller tests, CLI tests, and MySQL restart smoke are in place
- quality: formal implementation quality gate passed
- coverage: formal coverage audit passed; direct manager lifecycle coverage and MySQL restart evidence are recorded
- acceptance: ready for formal signoff if requested
- experience: N/A, backend/runtime and CLI diagnostics only.
- query-engine follow-up: Issue 12 fix is ready for reviewer verification; targeted and adjacent O615 regression tests passed.

## Validation Snapshot

- Runtime-managed MySQL datasource `mysql-pool-smoke` validated against MySQL `8.0.44`.
- Namespace `mysqlpoolsmoke` recovered its datasource binding from `runtime-datasources.json`.
- Bundle `mysql-pool-smoke-models` recovered from `runtime-bundles.json`.
- `models validate`, `models refresh`, `models describe`, `query validate`, and `query execute` passed against `FactOrderQueryModel`.
- Runtime restart did not require re-adding datasource, binding, or bundle.
- Registry default remains `.foggy-runtime/runtime-datasources.json` under the runtime process working directory unless explicitly configured; namespace bindings are stored in the same file.
