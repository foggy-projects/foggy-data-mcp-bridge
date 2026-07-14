---
doc_role: gate_replay_evidence
doc_purpose: Record the fresh Batch 6 Steps 2 and 3 cache-identity replay required by the Step 7 aggregate exit gate.
version: 9.3.3
batch: 6
steps: 2-3
status: passed
criterion: CACHE-IDENTITY
batch_status: in-progress
current_step: 7
authoritative_run: 20260714T044313Z-2824177
created_at: 2026-07-14
updated_at: 2026-07-14
---

# Batch 6 Cache Identity Replay Green

## Decision

The fresh Step 2/3 replay required by the Batch 6 exit gate is **passed**:

```text
target/v933-batch6-cache-identity/runs/20260714T044313Z-2824177/
```

It replaces historical focused logs as the Step 7 replay authority. The
independent review found no blocker. This record does not close Batch 6 by
itself; current work remains Step 7 aggregate replay, and Batch 7 remains
closed/not-started.

## Result

Recorded command（not re-executed by this documentation update）：

```bash
scripts/verify-v933-batch6-cache-identity.sh
```

| Ordered lane | Tests | Owning reports | Failures/Errors/Skipped |
|---|---:|---:|---:|
| Step 2 QueryFacade catalog pin | 6 | 1 | 0/0/0 |
| Step 2 addon strong key：builder 12 + Caffeine 30 + Redis 25 + SQLite isolation 1 | 68 | 4 | 0/0/0 |
| Step 3 two independent ApplicationContexts | 1 | 1 | 0/0/0 |
| **total** | **75** | **6** | **0/0/0** |

Step 2 is therefore 74 tests in the current source, not the historical 72:
`QueryFacadeCatalogIdentityTest` gained two generation-switch/idempotence
assertions during the Pivot work. Step 3 remains one test.

Each lane contains exactly one `BUILD SUCCESS`, the expected number of running
classes (`1/4/1`), and only marker-fresh owning XML. The runner asserts every
report through `assert-v933-test-report.sh` and rechecks the source manifest at
the end.

## Contract Boundary

- QueryFacade resolves and atomically pins the exact catalog/model/binding
  resolution; namespace/model/generation conflicts fail before execution;
- L1/L2 keys contain namespace, requested/canonical model, catalog generation,
  source revision, sorted exact binding key/backend/generation and security
  identity, encoded with full SHA-256;
- missing/malformed/conflicting identity yields no key, with no instance UUID,
  object address or datasource-instance fallback;
- Caffeine and Redis providers both delegate L1 and L2 key construction to the
  same production key builder;
- two production auto-configured Spring contexts have distinct context,
  provider and builder instances, but equal complete identity produces equal
  keys; catalog or binding generation rotates both L1 and L2;
- the embedded SQLite case uses two physical datasource files and mutually
  exclusive sentinels, then proves an untracked delegating datasource remains
  fail closed with cache size zero.

The Redis provider lane is a Mockito unit contract. Real Redis is intentionally
owned by Step 4 cross-JVM and Step 6 REAL-QUERY evidence; this replay does not
claim otherwise.

## Integrity

- `summary.env` SHA-256:
  `b8cff91a339df48b8ba79549af40948bae1a0bad1618a8f809266bd0898e2e9c`
- source manifest SHA-256:
  `0563a797fcdb4a9c33e8e176851b50b466012734340f2eb077b5a28763dfe053`
- inner artifact manifest `SHA256SUMS` SHA-256:
  `143bbccea012ff2a8ed4d183bb468dfbde7770953bdac021d959b1fdc16855ea`
- outer checksum file SHA-256:
  `ffc7a11de0fcd2e9b3ec843ccdc2471c5636bd6df6ddfd72b63887991825e296`

Both manifest checks passed, and `latest-run-id` points to the authoritative run.
The sealed summary retains `independent_review=pending-authoritative-run-review`;
this document records the completed independent review.

Run `20260714T044020Z-2817047` is diagnostic/superseded because review found a
post-latest output window in that runner revision. Its tests were green but it
is not cited or counted as authority. The runner was then changed so atomic
latest replacement plus `SUCCESS_FINALIZED=1` are the final operations, and the
authoritative run above used that frozen source.

## Step 7 Accounting

The aggregate exit must run this authority as child 02, followed by Step 4
cross-JVM, Step 5 Pivot and Step 6 REAL-QUERY. With current Batch 3/4 QueryFacade
counts, the full gate is 11 ordered children, 676 criterion tests, 677 asserted
report testcases, 99 asserted reports, 4 expected-negative cases and 0 remaining
red sources/tests.
