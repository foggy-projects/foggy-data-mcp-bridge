---
doc_role: batch_exit_evidence
doc_purpose: Close Batch 5 detached validation, committed source events and atomic catalog refresh, then hand off catalog/cache/Pivot consumption to Batch 6.
version: 9.3.3
batch: 5
status: completed
decision: passed
authoritative_run: 20260713T200646Z-2120785
created_at: 2026-07-14
updated_at: 2026-07-14
---

# Batch 5 Atomic Refresh Exit

## Decision

Batch 5 is **completed** and Batch 6 (`Catalog/Cache/Pivot 消费与真实查询`) is
**ready to start**.

The authoritative run executed 90/90 tests in 19 fresh owning reports with
failures/errors/skipped=`0/0/0`. It includes an independent Failsafe lane with
two real SQLite/QueryFacade atomic-refresh tests. The source audit found no
sleep-driven frozen test, production clear-first refresh path, promoted Batch 5
red reference, or refresh-owned executor/thread.

This is a Batch exit, not the 9.3.3 version signoff. Batch 6 consumer evidence
and Batch 7 regressions, formal implementation quality gate, coverage audit and
version acceptance remain mandatory.

This exit promotes exactly `REFRESH-ATOMIC`, `REFRESH-FAIL`, `REFRESH-SCOPE`,
`EVENT-CONVERGENCE`, `SOURCE-COMMIT` and `VALIDATE-ISOLATION`. `QM-COMPLETE`
remains passed from Batch 3. `API-COMPAT` has supporting evidence but remains
pending for Batch 7; complete `REAL-QUERY`, `CATALOG-AUTHORITY`, `CACHE-GEN` and
`CACHE-CROSS-JVM` remain pending for Batch 6.

## Delivered Boundary

### Model lifecycle authority

- `CatalogRefreshCoordinator` serializes one namespace while allowing different
  namespaces to overlap. It captures the exact catalog/source view, builds an
  invisible candidate, validates source and every known binding again, then
  performs one publication.
- namespace refresh replaces exact discovery; model refresh invalidates the
  target and reverse dependents while preserving verified siblings.
- failed source/model refresh preserves the captured old catalog only while the
  exact base snapshot/admission revision is still current. A late failure cannot
  downgrade a concurrent winner or a newer admission block.
- tracked bindings are always guarded at publication even when an unrelated
  dependency is untracked. Binding completeness controls cache safety, not the
  final currentness check.
- refresh opens the canonical request `NamespaceScope` around candidate build,
  loader callbacks and publication, then restores the outer scope on success or
  failure. This closes the named-QM/default-TM split found by the SQLite IT.

### Source and event convergence

- bundle add/remove and file/import mutation publish committed source revision
  and affected scope only after the source registry/cache/dependency view has
  converged.
- bundle, file, Runtime datasource and MCP datasource adapters call the same
  model refresh authority; production event paths no longer own `clear + warm`.
- unknown scope marks possibly affected catalogs
  `STALE_ADMISSION_BLOCKED`; exact namespace scope does not change another
  namespace identity or admission state.
- “persisted diagnostic” in this process-local catalog contract means the
  diagnostic remains in `CatalogSnapshotStore` admission state across reads
  until a successful atomic rebuild. It is not a second disk registry; after a
  process restart the process-local catalog is rebuilt. Runtime datasource
  generation/epoch persistence remains the separate durable registry contract.

### Runtime and MCP adapters

- Runtime validate uses a detached source/application context and leaves the
  live source revision, catalog generation, names and aliases unchanged for
  both valid and invalid candidates.
- Runtime refresh maps before/after generation, source revision, affected
  binding summaries, refreshed/preserved counts and current admission state.
  Failure keeps `afterCatalogGeneration=null` and uses stable lifecycle codes.
- Runtime datasource names and namespace keys are canonicalized at controller,
  service and persisted-registry boundaries. Existing v1/v2 registries are
  canonicalized and atomically rewritten; canonical collisions fail closed.
  Canonicalization applies only to logical identifiers. Connection usernames,
  passwords and other credential-bearing values are preserved exactly on the
  datasource configuration path.
- datasource save/update/remove/disable/rebind and MCP configure/remove block
  affected catalog admission at the committed mutation boundary, refresh only
  consuming namespaces, and do not reacquire a revoked old binding after a
  failed refresh.
- public binding summaries and lifecycle diagnostics remove JDBC URLs, physical
  paths, credentials and stack material. A failure reports the latest observed
  blocked state rather than an older captured `ACTIVE` state.

## Criteria Evidence

| Criterion | Result | Direct evidence |
|---|---|---|
| REFRESH-ATOMIC | passed | `CatalogRefreshQueryIT` REF-01: blocked old readers and post-publication readers execute through QueryFacade/real SQLite; every identity/model/native result is entirely old or new |
| REFRESH-FAIL | passed | `CatalogRefreshQueryIT` REF-02 plus coordinator failure/currentness tests: failed candidate publishes zero and the old real query remains usable while binding is current |
| REFRESH-SCOPE | passed | candidate target/dependent/sibling tests, namespace admission isolation, exact Runtime/MCP affected-namespace convergence |
| EVENT-CONVERGENCE | passed | bundle 3, file 4, Runtime datasource 8 and MCP datasource 3 tests use the unified refresh boundary and reject clear-first behavior |
| SOURCE-COMMIT | passed | fsscript committed source registry 7 tests plus source-stale coordinator rejection and unknown-scope admission block |
| QM-COMPLETE | passed | inherited Batch 3 evidence plus Batch 5 namespace candidate build; dependency/build failure never partially publishes a QM |
| VALIDATE-ISOLATION | passed | `RuntimeModelValidationIsolationTest` 2 tests preserve live source/catalog/name/alias state for valid and invalid detached candidates |
| API-COMPAT | supporting-pass; criterion pending Batch 7 | Runtime DTO 2, safety 3, typed error 3 and refresh mapping 4 tests cover additive shape/nullability/stable-code/sanitization; old consumer JSON + real Controller regression remains mandatory |

`REF-03` namespace isolation and `REF-04` sibling preservation are deterministic
model/adapter evidence, not mislabeled as SQLite tests. The SQLite Failsafe lane
directly owns `REF-01` and `REF-02`.

## Authoritative Batch 5 Run

Command:

```bash
scripts/verify-v933-batch5-refresh.sh
```

Run root:

```text
target/v933-batch5-refresh/runs/20260713T200646Z-2120785/
```

| Lane | Reports | Tests | Failures/Errors/Skipped |
|---|---:|---:|---:|
| model refresh/admission unit | 6 | 30 | 0/0/0 |
| model real SQLite Failsafe IT | 1 | 2 | 0/0/0 |
| fsscript source convergence | 2 | 7 | 0/0/0 |
| Runtime lifecycle/convergence/API | 8 | 34 | 0/0/0 |
| MCP datasource convergence | 2 | 17 | 0/0/0 |
| total | 19 | 90 | 0/0/0 |

Evidence integrity:

- `summary.env` SHA-256:
  `a15ffc6e44cd495438a57d52f5b52044dead229184b74b6524796cc49375003d`
- `SHA256SUMS` SHA-256:
  `2ad2d7fdbed978d7ae5b77879912ff961f4293dd56a5dbf26d0660ff09a76131`
- Failsafe owning report:
  `model-refresh-sqlite-it/failsafe-reports/TEST-com.foggyframework.dataset.db.model.lifecycle.refresh.CatalogRefreshQueryIT.xml`
- Failsafe summary records completed=2, failures=0, errors=0, skipped=0.

The runner freezes source `@Test` counts before Maven, requires every expected
fresh report and exact class/test count, validates the Failsafe summary, and
hashes the complete run directory. Model IT uses Surefire skip + Failsafe
execution, so the same class is not counted twice.

## Ordered Regression Replay

| Boundary | Run | Result |
|---|---|---|
| Batch 4 single-flight | `20260713T201031Z-2124453` | 140/140 green; 51 core, total failures/errors/skipped=0 |
| Batch 3 catalog/binding | `20260713T201525Z-2129344` | 166/166 green; Runtime binding lane 34, total failures/errors/skipped=0 |
| 9.3.4-A entry gate | `20260713T201941Z-2133081` | positive 5 green + expected-negative 4/4 fail closed |
| Batch 2 NamespaceScope | `20260713T202421Z-2138021` | 25 product + 7 compatibility green; default discovery consumed 23 fresh lifecycle reports and asserted the 5 Batch 2 owners |
| remaining Batch 1 red | `20260713T202645Z-2141955` | only Batch 6 `CatalogNamespaceAuthorityRedBaseline`, 1/1 expected-red |

The immutable historical Batch 2–4 exit runs remain valid. These are
post-Batch5 compatibility replays and do not rewrite their historical evidence.

## Failure-driven Closure

The following non-authoritative runs correctly stopped before this exit:

- the first real SQLite IT resolved the QM's TM in default namespace. The test
  was not weakened; the coordinator gained the missing request namespace scope,
  a success/failure restoration unit test was added, and the real IT then passed;
- mixed tracked/untracked provenance showed that completeness had incorrectly
  bypassed a known binding guard. Publication now guards every known identity;
- discovery and late-failure review exposed that a failed attempt could mark a
  concurrent winner. Failure markers now compare the exact captured snapshot
  reference and store revision;
- Runtime review exposed whitespace aliases in persisted registries, unsafe
  binding summaries and stale failure-state reporting. Canonical migration,
  sanitization and latest-state tests were added before the authoritative run;
- datasource convergence review proved that a committed mutation must block
  catalog admission before a pool callback can block or fail; the mutation
  sequence and deterministic callback test now enforce that order;
- hostile diagnostics review extended recursive sanitization to map keys as
  well as values, while controller coverage proves logical-id canonicalization
  does not trim or otherwise rewrite credential-bearing connection fields;
- the first post-Batch5 Batch 2 replay found an obsolete exact-total assumption:
  default lifecycle discovery had grown from 5 to 23 reports. The runner still
  uses default discovery without `-Dtest`, requires every report to be fresh,
  and isolates the exact five Batch 2 owning reports instead of treating later
  lifecycle suites as stale evidence.

## Light Implementation Self-check

- no production refresh authority owns a thread or executor;
- no frozen concurrency/refresh test uses `Thread.sleep` or
  `TimeUnit.sleep` to create the critical interleaving;
- no Runtime/bundle/file/MCP production event path uses global clear-first as
  the refresh protocol;
- no Batch 5 promoted red class remains in the red runner;
- no model-to-Runtime/MCP/cache reverse dependency was introduced;
- `git diff --check` and changed runner `bash -n` checks pass (existing CRLF
  conversion warnings are baseline warnings, not whitespace errors);
- formal `foggy-implementation-quality-gate`, coverage audit and acceptance
  signoff remain Batch 7 work.

Self-check decision: **self-check-only / Batch 5 pass**. No observed Batch 5
contract failure remains in the frozen evidence.

## Residual Review Items

These do not expand Batch 5 after its frozen criteria, but must remain visible:

- Batch 7 formal quality review must inspect mutation-monitor/callback latency
  and multi-binding retire failure atomicity; current deterministic tests prove
  committed admission fail-closed, pinned old/new handles and exactly-once
  close, but do not claim a general distributed transaction across registry,
  pool callbacks and catalog refresh.
- internal admission reason selection still derives one stable external code
  from a sanitized diagnostic prefix. Converting that internal reason to a
  closed typed value is a follow-up; the external code mapping is tested.
- repeated source seqlock instability currently ends in a bounded generic
  capture failure. A dedicated typed retry-exhaustion error and hostile
  continuous-mutation test are quality hardening candidates.
- committed datasource mutation followed by refresh failure is intentionally
  fail closed, but a future API may distinguish “mutation committed, catalog
  convergence failed” to make client retry semantics clearer.
- registry/pool large-class decomposition belongs to 9.3.5; lifecycle ports are
  left in the current module for 9.4.0 SPI v2 extraction.

## Batch 6 Handoff

Batch 6 now owns only the downstream consumer work:

1. make model and MCP catalog discovery/alias consume the same namespace
   snapshot and remove the remaining MCP-side global authority;
2. require catalog + exact dependency binding generations in L1/L2/Redis and
   Pivot identity, with unknown identity no-read/no-write;
3. prove two-context and two-process/restart cold-key behavior;
4. execute namespace/datasource/generation sentinels through QueryFacade and
   compare complete results with native SQL;
5. promote the sole remaining
   `CatalogNamespaceAuthorityRedBaseline` only when `CATALOG-AUTHORITY` has
   direct product-green evidence.

Batch 7 and all downstream versions remain closed until Batch 6 exits.
