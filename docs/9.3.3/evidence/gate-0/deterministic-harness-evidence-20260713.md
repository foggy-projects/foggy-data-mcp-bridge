---
doc_role: test-evidence
doc_purpose: Record the deterministic concurrency harness Gate 0 proof.
version: 9.3.3
gate: 9.3.4-A
result: passed
executed_at: 2026-07-13
---

# Gate 0 Deterministic Concurrency Harness Evidence

## Scope

This evidence proves only the reusable Gate 0 harness and its owning runner
boundaries. It is not evidence that the Batch 1–6 lifecycle contracts have
already been implemented.

## Unit probe

Authoritative final command (the script runs unit and IT in one reactor through
the `verify` lifecycle before installing current sibling artifacts for isolated
negative lanes):

```bash
scripts/verify-v933-entry-gate.sh
```

Observed:

- callers: 8; executor size: 8;
- start is coordinated by `Phaser`; winner/waiters are held by bounded
  `CountDownLatch` controls;
- one winner and seven waiters joined before release;
- build count: 1; final in-flight count: 0;
- all futures completed without cancellation; executor termination asserted;
- a separately controlled winner was interrupted before release and left
  in-flight count 0;
- a reused single worker observed an empty `NamespaceContext` after the prior
  task cleared it;
- Surefire owning report: `target/v933-entry-gate/runs/20260713T104955Z-959834/probe-pair/surefire-reports/TEST-com.foggyframework.dataset.db.model.lifecycle.gate.DeterministicConcurrencyHarnessProbeTest.xml`;
- report result: tests=1, failures=0, errors=0, skipped=0;
- shared probe-pair Maven log SHA-256: `57a48915c3e067eea306009cbadb0636731ab9b02759f9b2fc9a86cab0d9fd9e`.

## SQLite integration probe

Observed:

- callers: 2; executor size: 2;
- `Phaser` controls entry, one controlled winner executes the real SQLite
  `SELECT 1`, and the waiter receives the shared result;
- build count: 1; results: `[1, 1]`; final in-flight count: 0;
- a bounded `CyclicBarrier` holds both reused-worker cleanup observations;
- all futures completed without cancellation; executor termination asserted;
- Failsafe owning report: `target/v933-entry-gate/runs/20260713T104955Z-959834/probe-pair/failsafe-reports/TEST-com.foggyframework.dataset.db.model.lifecycle.gate.DeterministicConcurrencyHarnessProbeIT.xml`;
- report result: tests=1, failures=0, errors=0, skipped=0;
- Maven log contains exactly one Failsafe `integration-test (default)` and one
  matching `verify (default)` execution;
- shared probe-pair Maven log SHA-256: `57a48915c3e067eea306009cbadb0636731ab9b02759f9b2fc9a86cab0d9fd9e`.

## Anti-flake checks

- All harness step waits and `Future#get` calls have a five-second bound;
  each probe also has a JUnit-level timeout.
- The lifecycle harness source tree contains no `Thread.sleep` call.
- Every exceptional path releases the controlled winner, cancels incomplete
  futures and asserts executor termination.
- `scripts/assert-v933-test-report.sh` independently verified the exact owning
  class, exact test count, report freshness and zero failure/error/skip values
  in both reports.

Full runner and database evidence: `gate-0-run-20260713.md`.

Decision: **passed** for the deterministic-harness portion of 9.3.4-A.
