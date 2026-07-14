---
doc_role: batch_exit_evidence
doc_purpose: Close Batch 2 NamespaceScope implementation and hand off CatalogSnapshot/binding identity work to Batch 3.
version: 9.3.3
batch: 2
status: completed
recorded_at: 2026-07-13
next_batch: 3
---

# Batch 2 NamespaceScope Exit

## Exit decision

Batch 2 is **completed** and Batch 3 (`Binding Identity + CatalogSnapshot`) is
**ready to start**. `NS-SCOPE` now has normal-green product evidence; no other
9.3.3 product criterion is promoted by this record.

The implementation establishes one stack/token-backed namespace lifecycle:

- `NamespaceContext.open(String)` treats null/blank as explicit default and
  trims named namespaces;
- `openInherited()` inherits the current canonical scope and uses default at
  root/unset;
- close restores the exact previous unset/default/named or legacy raw state;
- wrong-thread and out-of-order close fail without changing the stack, while a
  successful owner double-close is idempotent;
- active-scope legacy `setNamespace/clear` fails closed with
  `NAMESPACE_SCOPE_LEGACY_MUTATION_WHILE_ACTIVE`;
- final root close removes the ThreadLocal so a reused worker starts unset.

## Production migration

The production entries migrated to try-with-resources are:

- QueryFacade query, SQL-only, managed-relation prepare and execution paths;
- Semantic V3 metadata;
- QueryModelLoader name/alias/cache/synthetic/script path and BundleResource
  load path.

Namespace presence is kept separate from namespace value: QueryFacade
overloads without a namespace inherit, while explicit null/blank/context values
mean default. The canonical effective namespace is written to the per-query
context and passed to the loader so loader/filter/cache cannot diverge from the
ThreadLocal value.

Production source audit found zero calls to legacy
`NamespaceContext.setNamespace/clear` outside tests. CatalogSnapshot, datasource
binding, single-flight, refresh and cache-generation code were not changed in
this batch.

## Authoritative NamespaceScope run

- command: `scripts/verify-v933-batch2-namespace.sh`
- run: `20260713T130626Z-1313396`
- result: passed
- default-discovery suites/tests: 5/26, including one retained Gate probe and
  25 NamespaceScope product tests
- product suite split: core scope 9, QueryFacade/managed entries 11, Semantic
  metadata 2, QueryModelLoader 3
- legacy compatibility: 7 tests
- failures/errors/skipped: 0/0/0 across both lanes
- source audit: production legacy mutations 0; sleep-driven Batch 2 tests 0
- `summary.env` SHA-256:
  `279d84740fabf5d300952b4070490db0b181583167606843da6d7c8bda106b9b`
- `SHA256SUMS` SHA-256:
  `299ef43049d732d967d379abdae4f0e1be8ba8bbb4ee0a8e158e4fde15cd66e4`
- `sha256sum -c SHA256SUMS`: all files OK
- credential-value scan over Maven/XML artifacts: 0 matches

The default-discovery lane deliberately runs without `-Dtest` and asserts the
exact five owning reports. This proves the promoted NamespaceScope regression
is part of normal discovery rather than an explicit-only replacement for the
Batch 1 red baseline.

## Retained regressions and entry gate

Focused SQLite regression after the production migration passed 32 tests with
0 failures/errors/skips:

- `QueryFacadeImplTest`
- `SemanticServiceV3Test`
- `SyntheticMemberQueryModelLifecycleTest`

The complete 9.3.3 entry gate was then replayed after the final Batch 2 code and
tests:

- command: `scripts/verify-v933-entry-gate.sh`
- run: `20260713T130717Z-1316013`
- positive: deterministic unit 1, deterministic IT 1, SQLite 1, MySQL 5.7 1,
  PostgreSQL 15 1; all failures/errors/skipped = 0
- expected-negative: missing unit, missing IT, wrong required DB and missing
  owning report all failed closed (4/4)
- `summary.env` SHA-256:
  `12464c0d7fdeda832662a7cff966ac095b63f05a72c2fb29d946578d492e53df`
- `SHA256SUMS` SHA-256:
  `17e0499c6176240114948a0a0e6fb81a0df7f81c7edaa3eb5c4c34040bc82714`
- `sha256sum -c SHA256SUMS`: all files OK
- credential-value scan over Maven/XML artifacts: 0 matches

No remote GitHub Actions or full 9.3.4 database/coverage evidence is claimed.

## Batch 1 baseline transition

The Batch 1 expected-red run `20260713T115746Z-1058249` remains the immutable
repair-before artifact. Its old Namespace red source was promoted into the
normal-green `NamespaceProductionEntryRestorationTest` and removed, so the old
Batch 1 runner is historical and is not claimed to be replayable post-fix.

## Implementation self-check

- scope stayed limited to `NS-SCOPE`; no Batch 3–6 production work leaked in;
- no global lock, clear-first refresh, mutable catalog authority, debug branch,
  sleep-driven interleaving or unbounded wait was introduced;
- compatibility methods and explicit/inherited semantics are documented;
- owner/LIFO failure paths, nested/default/early-return/exception, thread-pool
  reuse, production entries and legacy API behavior have direct tests;
- the one remaining non-blocking test-maintenance risk is the QueryModelLoader
  cache-hit seam, which uses reflection/raw Map to observe a private cache;
- experience: N/A (backend ThreadLocal lifecycle only).

- self-check decision: `self-check-only`
- quality blocker: none
- needs-formal-quality-gate: yes; the formal implementation quality gate remains
  owned by Batch 7 after all production batches complete

## Next execution point

Start Batch 3 only: immutable per-namespace `CatalogSnapshot`/identity,
deterministic alias/completeness, persisted datasource binding generation and
generation-pinned admission/lease ports. Single-flight remains closed until the
Batch 3 identities and state transitions are green.
