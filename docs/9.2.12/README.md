---
doc_role: version_followup_plan
doc_purpose: Track 9.2.12 Runtime API managed datasource pool lifecycle work.
version: 9.2.12
status: ready-for-implementation
created_at: 2026-07-03
updated_at: 2026-07-03
---

# 9.2.12 Runtime Managed Datasource Pool Lifecycle

## Document Purpose

- doc_type: version-summary
- intended_for: execution-agent, reviewer, signoff-owner
- purpose: Track the Runtime API managed datasource pool lifecycle design and implementation follow-up.

## Scope

9.2.12 turns Runtime API-managed JDBC datasource support into the primary public datasource path for standalone runtime and CLI onboarding. It adds an explicit connection-pool lifecycle design so runtime-created datasources can support MySQL/PostgreSQL-style user databases without relying on JVM GC or namespace refresh side effects.

## Work Items

| Item | Doc | Status | Owner Module | Summary |
|---|---|---|---|---|
| Runtime managed datasource pool lifecycle | `workitems/P0-runtime-managed-datasource-pool-lifecycle.md` | ready-for-implementation | `foggy-runtime-api`, `foggy-runtime-cli` | Add a managed pool manager with lazy Hikari initialization, idle pool close, update/remove/shutdown close semantics, diagnostics, and MySQL restart validation. |

## Guardrails

- No complex namespace/model reference counting.
- Namespace refresh must not close datasource pools.
- Closing an idle pool must not delete the datasource registry record.
- Runtime API-managed datasource is the recommended public path; FSScript datasource remains an advanced embedded path until its factory lifecycle is hardened.
- No Runtime API auth-code, RBAC, audit, per-user permission, or production secret-manager redesign in this iteration.

## Progress Summary

- development: not-started
- testing: not-started
- quality: pending implementation review
- coverage: pending implementation review
- acceptance: pending
- experience: N/A, backend/runtime and CLI diagnostics only.

