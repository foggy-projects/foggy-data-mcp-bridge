---
evidence_type: diagnostic-preflight-fail-closed
version: 9.3.4
step: 4
run_id: step4-coverage-20260719-diagnostic-r30
tested_commit: 7757aa36c0efd0970422669e0f88f74daa8f15b0
phase: contract-validate
status: failed-excluded
decision: no-cfreeze-authority
recorded_at: 2026-07-19
---

# Step 4 diagnostic-r30 successor-binding fail-closed record

## Decision

Fresh r30 started from the clean, pushed Git-safe capsule Cdiag under outer
`umask 077`. The canonical coverage-contract validation passed, then the Step 3
successor overlay rejected the stale pre-authorized dual coverage-contract
projection and coverage-tool bindings. The runner stopped in `contract-validate`
before any lane could start.

r30 is permanently **failed / excluded / non-reusable / non-candidate** and
has **zero lane authority**. It has no source seal, child-lane result, aggregate
exec/XML, coverage observation, summary, candidate or final artifact. None of
its partial run state may be repaired in place, replayed under the same ID, or
combined with r29 or historical evidence.

## Cause and corrective boundary

The Git-safe capsule Cdiag changed the coverage contract and coverage validator,
but the successor overlay still contained the previous dual diagnostic/formal
contract projections and validator digest. The correction must synchronize both
overlay binding sources and their successor → Step 4 → Step 6 integrity chain.

The contract-negative suite now runs the same canonical overlay validation as a
positive control and confirms its input hashes remain stable. This is a static
preflight repair, not a formalization delta; a new clean, pushed Cdiag is
required before one fresh strict-umask diagnostic-r31.

## Authority status

`can_enter_cfreeze=no / can_enter_formal=no / can_enter_step5=no /
can_enter_coverage_audit=no / can_enter_acceptance=no`.

The only permitted successor path is:

`binding repair Cdiag → fresh diagnostic-r31 → new candidate/Git-safe capsule/
independent review → direct-single-parent Cfreeze → fresh formal → post gates`
