---
doc_role: batch_entry_checkin
doc_purpose: Freeze Batch 4 keyed single-flight scope, prerequisites and failure semantics.
version: 9.3.3
batch: 4
status: in-progress
recorded_at: 2026-07-13
---

# Batch 4 Single-flight Entry Check-in

## Entry decision

Batch 4 is **in progress**. Entry prerequisites are satisfied:

- Batch 3 completed via authoritative run `20260713T150948Z-1719636`;
- post-Batch3 9.3.4-A gate passed via run
  `20260713T151323Z-1726207`;
- `CatalogIdentity`, `CatalogGeneration`, `SourceRevision` and exact datasource
  binding identities are stable inputs for a flight key;
- `SNAPSHOT`, `GENERATION`, `DS-GENERATION`, `BINDING-REVOKE` and
  `QM-COMPLETE` are passed;
- the protected dirty-worktree baseline remains in force; no
  reset/checkout/clean/stash is authorized.

## Ordered scope

1. Introduce a lifecycle-owned keyed flight coordinator with one winner and a
   shared success/failure future per exact key.
2. Key by model kind, canonical namespace/name, captured catalog generation,
   committed source revision and canonical dependency binding identity set.
3. Let different keys perform detached build work concurrently; serialize only
   the final namespace publication check/swap.
4. Detect a candidate built from a stale catalog generation before publish and
   perform a bounded retry against the new identity.
5. Remove failed/cancelled/completed flights exactly once so the next call can
   retry; do not retain failed futures or executor/thread state.
6. Detect same-thread TM/QM/synthetic dependency cycles before a caller can
   wait on its own flight.
7. Promote the remaining distinct-key expected-red case into normal-green
   evidence and remove it from the Batch 1 red runner.

## Open criteria

- `SF-SAME`
- `SF-ISOLATION`
- `SF-FAIL`

All remain pending until deterministic normal-green tests and a fresh
authoritative Batch 4 runner exist. The internal stale/cycle cases are required
Batch 4 exit evidence even though they do not have separate top-level criteria.

## Boundaries

- Batch 5 still owns source mutation, refresh coordinator, detached Runtime
  validate and bundle/file/datasource event convergence. Batch 4 may reject a
  catalog-generation-stale publish but does not implement source refresh.
- Batch 6 catalog/cache/Pivot consumers and independent-process proof remain
  closed.
- No 9.3.4 full CI, 9.3.5 API refactor or 9.4.0 module/SPI split.
- Experience: N/A; backend concurrency/lifecycle work only.

## Initial risk focus

- `CatalogSnapshotStore.openCandidate` currently holds one namespace lock for
  the whole build, so distinct keys cannot overlap.
- nested TM builds join a thread-local candidate; the flight design must not
  make a thread wait on a future it owns.
- two different-key candidates may capture the same base snapshot; only one may
  publish first, so the other must be rejected as stale and retried rather than
  merged blindly.
- a winner exception must preserve one stable category/message for all waiters
  while still clearing the in-flight entry.
