---
doc_role: batch_exit_evidence
doc_purpose: Close Batch 4 keyed single-flight implementation and hand off atomic refresh work to Batch 5.
version: 9.3.3
batch: 4
status: completed
recorded_at: 2026-07-13
next_batch: 5
---

# Batch 4 Keyed Single-flight Exit

## Exit decision

Batch 4 is **completed** and Batch 5 (`离线构建与原子刷新`) is **ready to
start**. The following owning criteria now have deterministic normal-green
evidence:

- `SF-SAME`
- `SF-ISOLATION`
- `SF-FAIL`

The detached catalog/source view, bounded stale retry and final datasource
binding publication guard are required loader-safety evidence. They do not
promote `SF-04` as a production refresh scenario and do not promote any
`REFRESH-*`, `EVENT-CONVERGENCE`, `SOURCE-COMMIT` or `VALIDATE-ISOLATION`
criterion. Those remain Batch 5 mandatory-green work.

## Implemented single-flight and publication boundary

The model lifecycle authority now provides exact keyed build coordination:

- `ModelBuildKey` includes model kind, canonical namespace/name, captured
  catalog generation, committed source revision and the sorted exact set of
  backend/datasource binding identities; an untracked resolution receives a
  unique nonce and is never shared accidentally;
- the first caller is the caller-inline winner; waiters share the same future,
  result object or original `Throwable`; the coordinator owns no executor or
  thread;
- completion, failure, cancellation, checked timeout and observer failure all
  use precise `remove(key, candidate)` cleanup, leaving the next call free to
  retry;
- the dependency stack is lazy and removed at root exit; waiters do not create
  an empty ThreadLocal; same-thread/self-wait TM/QM/synthetic cycles fail with
  stable `MODEL_BUILD_DEPENDENCY_CYCLE` before waiting;
- the cycle claim is deliberately limited to the frozen build scope. No claim
  is made for an arbitrary cross-thread wait-for graph.

Catalog construction no longer holds a namespace lock over the long build:

- `CatalogBuildView` captures the exact base snapshot reference, generation and
  source revision;
- a candidate builds detached and owner-thread-local; publication briefly
  compares the exact base/source view, freezes the candidate and swaps the
  active snapshot;
- stale, failed, abandoned and successful candidates are sealed; clearing an
  empty namespace advances source revision so null-base ABA cannot publish;
- TM/QM loaders share one flight coordinator, use bounded three-attempt stale
  retry, preserve dependency provenance and canonicalize normal/synthetic alias
  keys;
- an undeclared dependency in an active candidate fails closed instead of
  escaping the candidate graph.

The final loader publication closes the datasource binding TOCTOU discovered
during review:

- `DatasourceBindingResolver.publishIfCurrent` rechecks the exact identity set
  and executes the catalog swap in the adapter's mutation critical section;
- the default implementation permits only an empty/untracked set; a tracked
  publication without an atomic adapter fails with
  `DATASOURCE_BINDING_PUBLICATION_GUARD_UNAVAILABLE`;
- Runtime uses the registry monitor shared by save/remove/bind; MCP uses the
  manager monitor shared by configure/remove;
- Runtime composition filters foreign backend ownership and AOP self proxies,
  protects external decorator recursion, orders external identity domains
  deterministically and permits legitimate nested multi-domain guards;
- default binding is additive opt-in through
  `ProcessLocalDefaultDataSourceResolver`; legacy resolvers never receive an
  empty namespace.

This guard only protects lazy-loader publication. Batch 5 still owns the
mutation-to-refresh coordinator and event convergence.

## Authoritative Batch 4 run

- command: `scripts/verify-v933-batch4-single-flight.sh`
- final run: `20260713T164144Z-1910217`
- result: passed
- single-flight core: 50 tests
  - key 4, coordinator 17, TM loader 4, QM loader 2, snapshot store 16,
    snapshot provenance 5, resolver currentness 2
- catalog regression: 34 tests
- SQLite consumer regression: 33 tests
- namespace regression: 3 tests
- Runtime binding publication guard: 5 tests
- MCP binding publication guard: 14 tests
- total: 139
- failures/errors/skipped: `0/0/0`
- same-key callers/waiters/build: `100/99/1`
- residual flight entries: `0`
- evidence root:
  `target/v933-batch4-single-flight/runs/20260713T164144Z-1910217/`
- `summary.env` SHA-256:
  `f4d37813c3a99640101cfbae5671fb4b5795b1011257d0d6857ad4fd793777f6`
- `SHA256SUMS` SHA-256:
  `88b606868fa4199fdcc51553bd8d203310c69c2dc9d5993e48e8b9e70123d2f9`
- `sha256sum -c SHA256SUMS`: all files OK

The runner binds every class to a fresh exact owning XML/count. Its source
audits distinguish `rg` match, clean and scan-error statuses and report zero:

- long build lock: 0
- production-owned executor/thread: 0
- sleep-driven Batch 4 concurrency tests: 0
- promoted distinct-key red reference: 0

## Cross-batch regression evidence

### Batch 3 replay after Batch 4

- command: `scripts/verify-v933-batch3-catalog-binding.sh`
- run: `20260713T164538Z-1920394`
- catalog 55 + SQLite 33 + Runtime 32 + MCP 14 + Caffeine 30 = 164
- failures/errors/skipped: `0/0/0`
- `summary.env` SHA-256:
  `9335bdd4716f4f35712f2fe5d29f81e0a7e3c61719940b87f2b23ccc6dafa6a6`
- `SHA256SUMS` SHA-256:
  `07535205fd9f3853e30542e1471005bfe54bf97edcc8d5f73007b8aff6c1757d`
- checksum verification: all files OK

The original Batch 3 exit run `20260713T150948Z-1719636` remains the immutable
historical Batch 3 authority. This 164-test result is a post-Batch4 regression,
not a rewrite of the earlier 149-test record.

### Remaining expected-red replay

- command: `scripts/verify-v933-batch1-red-baselines.sh`
- final run: `20260713T165835Z-1949477`
- result: 5 cases / 6 expected assertion failures / 0 errors / 0 skipped
- `summary.tsv` SHA-256:
  `ed970b295953e35bf8a2a76327fcd77ae98dfe4f64ccc3af0a7998033dc76a97`
- `sha256sum.txt` SHA-256:
  `53cb8feca2fdb83a18af601fd341beda07babab3460e24ef332131553cd630ef`
- checksum verification: all listed files OK
- reactor proof: the MCP XML classpath contains the workspace model
  `target/classes` and no local-repository model jar

The frozen Batch 1 run `20260713T115746Z-1058249` remains the historical
10-suite/12-red record. Promoted Batch 2–4 cases are absent from the active
replay. The former Batch 6 event/list fixture became green when Batch 3 removed
the model-side names cache, but it did not test a shared snapshot identity.
The active red was therefore hardened to prove the still-open cross-namespace
MCP global names cache split. `CATALOG-AUTHORITY` remains Batch 6 pending.

The replay now uses reactor `-am` dependencies and then validates the target
module's fresh exact XML, so it does not depend on a drifting local Maven jar.
Its RedBaseline sleep audit also treats `rg` execution errors as gate failures.

### Final 9.3.4-A replay

- command: `scripts/verify-v933-entry-gate.sh`
- run: `20260713T165330Z-1941345`
- positive: deterministic unit 1, deterministic IT 1, SQLite 1, MySQL 5.7 1,
  PostgreSQL 15 1; all failures/errors/skipped = 0
- expected-negative: missing owning unit, missing owning IT, wrong required DB
  and missing owning report all failed closed (`4/4`)
- `summary.env` SHA-256:
  `b7f5eb8110e44465b91b0238d937050ef9ce07608f1e289c4916a4017185c1c7`
- `SHA256SUMS` SHA-256:
  `afcf8d81ff644478f53c1a0c983522c10e18be56f2e209db4916d5aa75a5fbe4`
- `sha256sum -c SHA256SUMS`: all files OK

This remains a 9.3.4-A preflight-only gate. It is not the complete 9.3.4
database matrix, coverage/release evidence or remote CI result.

## Fail-closed evidence evolution

Only `20260713T164144Z-1910217` is Batch 4 exit authority:

- the earlier 107-test run `20260713T160327Z-1796766` was superseded as review
  added atomic binding publication and compatibility coverage;
- run `20260713T162625Z-1851616` completed 137 product tests, but its source
  audit correctly stopped the exit; the thread regex was then narrowed so it
  no longer mistook `ThreadLocal` for `new Thread`, while retaining explicit
  scan-error failure semantics;
- run `20260713T163916Z-1902388` was deliberately stopped after review found
  the multi-external Runtime guard composition gap; the implementation and
  deterministic test were fixed before the final run;
- observer `Error`, waiter ThreadLocal allocation, cancellation/checked timeout,
  six key dimensions, process-local default compatibility and Runtime
  ownership/AOP/decorator recursion were likewise added before final authority.

The partial remaining-red run `20260713T164932Z-1930467` stopped on the stale
Batch 6 fixture, and module-only run `20260713T165156Z-1937717` was superseded
after reactor-hermeticity review. Neither is exit evidence.

## Lightweight implementation self-check

- independent read-only review found no blocker or major issue;
- `putIfAbsent`, shared completion, exact removal, detached candidate sealing,
  stale checks and Runtime/MCP monitor ownership are explicit and fail closed;
- no production executor/thread, long build lock, sleep-driven interleaving,
  mutable snapshot exposure or refresh/event implementation was introduced;
- `git diff --check` and relevant `bash -n` checks passed at review time;
- experience: N/A (backend lifecycle/concurrency delivery, no UI).

Non-blocking risks retained for the formal Batch 7 quality gate:

- snapshot freeze occurs inside the binding mutation guard and may lengthen
  configure/rebind wait time for a very large catalog;
- three-attempt stale retry is intentionally duplicated at TM, QM and discovery
  entry points and should retain telemetry under high churn;
- the current red runner is authoritative only when executed serially because
  standard Surefire modules still write their default owning report path.

- self-check decision: `self-check-only`
- quality blocker: none
- needs-formal-quality-gate: yes; Batch 7 still owns the formal implementation
  quality gate, coverage audit and version acceptance

## Remaining boundaries and next execution point

Batch 5 owns:

- namespace/model refresh coordinator and complete candidate build/validate/
  publish lifecycle;
- concurrent-reader old-or-new atomicity, failed refresh keeps old when binding
  remains valid, target namespace/sibling preservation;
- committed source mutation, affected scope/reverse dependency, SourceRevision
  event order and unknown-scope admission block/persistent diagnostics;
- detached Runtime validate, refresh DTO/diagnostics and removal of production
  clear-first/warm ownership;
- bundle/file/datasource mutation-to-refresh convergence;
- `REFRESH-ATOMIC`, `REFRESH-FAIL`, `REFRESH-SCOPE`, `EVENT-CONVERGENCE`,
  `SOURCE-COMMIT`, `VALIDATE-ISOLATION` and its part of `API-COMPAT`.

Batch 6 still owns unified model/MCP catalog consumers, complete cache/Pivot
identity, independent-process proof and real-query parity. Batch 7 owns full
regressions and post-gates.

Start Batch 5 only. Batch 6–7 remain closed.
