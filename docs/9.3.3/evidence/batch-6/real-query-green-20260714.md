---
doc_role: criterion_evidence
doc_purpose: Record the authoritative Batch 6 Step 6 REAL-QUERY pass and hand off to the Step 7 aggregate exit replay.
version: 9.3.3
batch: 6
step: 6
status: passed
criterion: REAL-QUERY
criterion_status: passed
batch_status: in-progress
current_step: 7
authoritative_run: 20260714T041047Z-2755326
created_at: 2026-07-14
updated_at: 2026-07-14
---

# Batch 6 Step 6 REAL-QUERY Green

## Decision

Step 6 is signed off and `REAL-QUERY` is **passed**. The authoritative run is:

```text
target/v933-batch6-real-query/runs/20260714T041047Z-2755326/
```

The independent post-run review found no blocker. The six serial lanes contain
11 fresh Failsafe tests in six fixed owning reports, with
failures/errors/skipped=`0/0/0`. They exercise production QueryFacade,
Semantic/Pivot and Spring cache-provider paths against independent native SQL
oracles; mock interaction, SQL-string-only checks and hand-built cache
providers are not accepted as evidence.

Batch 6 remains **in progress / current Step 7**. The aggregate runner must now
replay Batch 6 and retained predecessor gates in the frozen order and create the
Batch 6 exit record. Batch 7 remains closed/not-started until that succeeds.

## Authoritative Result

Recorded command（not re-executed by this documentation update）：

```bash
scripts/verify-v933-batch6-real-query.sh
```

| Lane | Owning reports | Tests | Failures/Errors/Skipped | Direct boundary |
|---|---:|---:|---:|---|
| model lifecycle / SQLite | 1 | 4 | 0/0/0 | atomic old/new refresh and sibling preservation；same-name namespace A/B isolation；datasource rebind admission/lease drain/stale-publish rejection；Pivot SUM miss→hit→generation miss→hit with native parity |
| required SQLite | 1 | 1 | 0/0/0 | exact QueryFacade/native rows, columns, order and values；SQLite 3.42；sentinel rows 8/2 |
| required MySQL 5.7 | 1 | 1 | 0/0/0 | same test contract on required MySQL 5.7；sentinel rows 25/25 |
| required PostgreSQL 15 | 1 | 1 | 0/0/0 | same test contract on PostgreSQL 15.17/public；sentinel rows 25/25 |
| Caffeine provider | 1 | 2 | 0/0/0 | production auto-configuration；L1 and explicit-L1-off L2 miss/write/hit, then refresh-generation miss/write/hit |
| Redis provider | 1 | 2 | 0/0/0 | production Redis template/serializers and provider；same L1/L2 lifecycle flow with a run-scoped real Redis |
| **total** | **6** | **11** | **0/0/0** | **complete REAL-QUERY authority** |

Every lane has exactly one target test class and one `BUILD SUCCESS`. Each XML
report and `failsafe-summary.xml` is newer than its lane marker. The runner
rejects missing, duplicate, stale, skipped or unexpected reports.

## Lifecycle and Native-Parity Boundary

- atomic model refresh publishes a complete new catalog generation while the
  sibling model retains its identity; observed old/new rows each equal their
  corresponding native SQLite query;
- two namespaces can expose the same logical model name without moving the
  other namespace when A refreshes;
- datasource rebind closes old admission, permits only the already pinned
  request-scoped lease to drain, rejects new old-handle callers and prevents a
  stale publication or re-pin;
- Pivot aggregate columns and values match native `SUM`, and cache observations
  prove first-generation miss/write/hit followed by new-generation
  miss/write/hit;
- the same required-database test is executed on SQLite, MySQL 5.7 and
  PostgreSQL 15, with fail-closed expected product selection and full
  catalog/source/binding identity;
- Caffeine and Redis are selected by the production Spring auto-configuration
  path; L2 is separately proven with L1 explicitly disabled;
- rows, columns, column order and values are compared directly with native SQL;
  the test does not sort actual output to manufacture parity.

## Database and Redis Evidence

| Physical role | Product/version | Catalog/schema | Sentinel rows |
|---|---|---|---:|
| embedded shared-memory | SQLite 3.42 | `<none>/<none>` | 8/2 |
| required MySQL container | MySQL 5.7 | `foggy_test/<none>` | 25/25 |
| required PostgreSQL container | PostgreSQL 15.17 | `foggy_test/public` | 25/25 |

The fixed MySQL/PostgreSQL containers retained the same configured image, image
ID, health and mapped port before and after the run.

The Redis lane used a dedicated `redis:7-alpine` container, Redis 7.4.6, random
loopback port and prefix
`v933:real-query:20260714T041047Z-2755326:`. Initial and final key counts were
both zero. The runner removed the container and then confirmed it no longer
existed; it did not mutate or remove a shared fixed Redis container.

## Anti-false-green and Integrity Review

The independent review reconfirmed:

- exact test-class method counts are 4 model lifecycle + 1 database parity + 2
  cache lifecycle, with the latter two cache tests executed once per provider;
- no Mockito/mock, assumption/skip/`@Disabled`, `Thread.sleep`, actual-output
  sorting, empty catch, conditional early success return or hand-built cache
  provider exists in the evidence classes;
- all six reports, lane markers and Maven logs are fresh and mutually
  consistent;
- the selected 164-file source/resource manifest still verifies;
- `SHA256SUMS` and its outer checksum both verify.

Authoritative integrity values:

- `summary.env` SHA-256:
  `5602a3a75b6fb99d938ace19f7ca6b6528d0a9876fc2f799db14d24df5604f9e`
- source manifest `source-audit/source-files.sha256` SHA-256:
  `95aa4d9d5fbe31465724ad87eb704b531e04a4a2be0393f1a0b8afc2baf61bb0`
- inner artifact manifest `SHA256SUMS` SHA-256:
  `071987f7df30a3d08a6a194bf77f39a0861853ce36470db19090a36fd351f1aa`
- outer checksum file `SHA256SUMS.sha256` SHA-256:
  `6464baeda9e899736f3031dea27f1ceafc4a0b72e3863dbcafcbce499ce17cfe`

`summary.env` was sealed before the independent review and therefore retains
`independent_review=pending-authoritative-run-review`; this document records
the completed review without mutating the hashed authority.

Earlier local/preflight runs are diagnostic only and are not counted or cited
as authority. Only the run above promotes `REAL-QUERY`.

## Next Ordered Gate

7. Execute `scripts/verify-v933-batch6-exit.sh` with a fresh run ID. It must run
   eleven children strictly in order, including a fresh Step 2/3 cache-identity
   replay, revalidate every child manifest and source snapshot, and account for
   676 criterion tests / 677 asserted report testcases / 99 asserted reports /
   4 expected negatives / 0 remaining red. It must leave no run-owned container
   and then write the Batch 6 exit record.

Batch 7 remains closed until Step 7 completes. `API-COMPAT`, full regression,
formal implementation quality, coverage audit and version acceptance remain
Batch 7 work and are not promoted by this criterion checkpoint.
