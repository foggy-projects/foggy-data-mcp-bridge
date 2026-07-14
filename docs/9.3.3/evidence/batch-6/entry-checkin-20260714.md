---
doc_role: batch_entry_checkin
doc_purpose: Freeze Batch 6 catalog/cache/Pivot consumer and complete real-query evidence scope.
version: 9.3.3
batch: 6
status: in-progress
recorded_at: 2026-07-14
---

# Batch 6 Catalog/Cache/Pivot Entry Check-in

## Entry decision

Batch 6 is **in progress**. Entry prerequisites are satisfied:

- Batch 5 completed via authoritative run `20260713T200646Z-2120785` with
  90/90 tests in 19 fresh owning reports and failures/errors/skipped=`0/0/0`;
- the ordered post-Batch5 replay remained green: Batch 4
  `20260713T201031Z-2124453`=140, Batch 3
  `20260713T201525Z-2129344`=166, entry gate
  `20260713T201941Z-2133081`=5 positive + 4/4 expected-negative, and Batch 2
  `20260713T202421Z-2138021`=25 product + 7 compatibility;
- remaining-red run `20260713T202645Z-2141955` contains exactly one owning
  failure, `CATALOG-AUTHORITY` 1/1, assigned to this batch;
- immutable snapshot, binding identity, keyed single-flight and atomic refresh
  are available as stable producer-side inputs;
- the protected dirty-worktree baseline remains in force; reset, checkout,
  clean and stash are not authorized.

## Ordered scope

1. Make model and MCP discovery/alias views consume one namespace catalog
   authority, remove MCP global model-name caching and preserve additive
   non-Spring compatibility without a second cache.
2. Put catalog identity and the resolved model's exact dependency binding
   identities into every L1/L2/Caffeine/Redis key; incomplete or conflicting
   identity is cache no-read/no-write.
3. Prove two independent application contexts serialize the same complete
   identity to the same key, and every catalog/binding generation change
   changes the key.
4. Prove independent JVM/restart behavior against shared Redis: the current
   boot epoch is a deliberate cold identity and cannot hit the prior process.
5. Make Pivot outer-cache key, pipeline and telemetry consume the same strong
   catalog/binding identity; manual bundle token remains additive only.
6. Execute old/new, two-namespace and two-datasource sentinels through
   `QueryFacade`, compare complete rows/columns/order with native SQL, and bind
   every result to the expected catalog and physical datasource identity.
7. Freeze a Batch 6 runner with fresh report/count/hash/source audits, replay
   Batch 5 and all earlier gates in order, then write the Batch 6 exit record.

## Open criteria

- `CATALOG-AUTHORITY`
- `CACHE-GEN`
- `CACHE-CROSS-JVM`
- `REAL-QUERY`

`API-COMPAT`, full regressions and formal quality/coverage/version acceptance
remain Batch 7 work. Batch 5 SQLite `REF-01/02` are supporting inputs and do not
by themselves satisfy complete `REAL-QUERY`.

## Frozen entry findings

- model `SemanticModelCatalogService` already materializes namespace discovery
  through `CatalogSnapshotStore`, but MCP `SemanticServiceResolverImpl` still
  owns one process-global `cachedModelNames` list and file-event invalidation;
- MCP `ModelCatalogService` dynamic selection calls the namespace-free resolver
  method even when an explicit namespace was supplied;
- cache providers have strong identity building blocks from Batch 3, but the
  null/instance fallback and every actual L1/L2/Redis/Pivot consumer must be
  audited before `CACHE-GEN` can be promoted;
- cross-context equality is not cross-JVM evidence; the runner must launch a
  separate process or a process-exit/restart probe against the same Redis;
- Batch 5 real SQLite tests prove publication atomicity and failure preservation,
  not the full namespace/datasource/cache/Pivot result-parity matrix.

## Initial verification boundary

The `CatalogNamespaceAuthorityRedBaseline` remains an expected-red repair-before
test until the production MCP resolver and catalog selection use the shared
namespace authority. Promotion requires a normal Surefire owning test, an exact
fresh report, and a remaining-red replay with no Batch 6 red cases. No cache or
real-query criterion is promoted from unit-only source proof.
