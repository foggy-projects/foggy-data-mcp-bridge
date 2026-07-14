---
doc_role: criterion_evidence
doc_purpose: Record the authoritative CATALOG-AUTHORITY green checkpoint without closing Batch 6.
version: 9.3.3
batch: 6
criterion: CATALOG-AUTHORITY
status: passed
batch_status: in-progress
authoritative_run: 20260714T021057Z-2445113
remaining_red_replay: 20260714T021251Z-2449309
created_at: 2026-07-14
updated_at: 2026-07-14
---

# Batch 6 Catalog Authority Green

## Decision

`CATALOG-AUTHORITY` is **passed**. The authoritative run executed 53/53 tests
in 7 fresh owning reports with failures/errors/skipped=`0/0/0`. Direct authority
evidence is 17 tests and supporting consumer regression is 36 tests.

This is a criterion checkpoint, not the Batch 6 exit or the 9.3.3 version
signoff. `CACHE-GEN`, `CACHE-CROSS-JVM` and complete `REAL-QUERY` remain pending,
Batch 6 remains **in progress**, and Batch 7 remains closed/not-started.

Run `20260714T014919Z-2373851` is diagnostic only and
`superseded-by-review`. Although its internal summary recorded `status=passed`,
it predates the final coordinator-only recovery, complete binding-provenance
view, bounded metadata seqlock and shared Spring wiring review closure. It is
excluded from the criterion result and all authoritative counts below.

## Delivered Authority Boundary

### Model namespace view

- Spring injects the shared `CatalogSnapshotStore` and
  `CatalogRefreshCoordinator` into `SemanticModelCatalogService`.
- a complete active snapshot is projected directly. Absent or incomplete state
  can recover only through `CatalogRefreshCoordinator` and is reread before
  projection; the service does not independently open, discover, resolve,
  stage or commit a candidate.
- blocked admission propagates before recovery. Recovery failure preserves the
  prior snapshot and never returns a partial view. Exact empty/deletion
  snapshots remain empty; there is no discovery union that can retain deleted
  names.
- `NamespaceCatalogView` is immutable and binds one exact `CatalogIdentity` to
  model names, aliases, `QueryModel` objects and per-model
  `CatalogResolution`. Every tracked resolution carries the exact snapshot
  identity, dependency binding identities and completeness from snapshot
  provenance. Missing provenance fails closed; legacy/custom loaders remain an
  explicitly untracked, identity-null compatibility path with no second cache.

### Native and MCP metadata consumers

- native catalog assembly and MCP catalog assembly pin one namespace view and
  use a bounded three-attempt identity seqlock around metadata callbacks. A
  publication during the callback returns a complete old or new generation;
  repeated churn, blocked admission or partial provenance fails closed instead
  of returning a hybrid response.
- MCP `SemanticServiceResolverImpl` delegates namespace discovery to the model
  catalog service. It owns no model-names cache, invalidation authority or
  directory watcher registration.
- MCP `ModelCatalogService` consumes the same immutable view and exact pinned
  resolutions. It does not reread models through an independent names/model
  authority.
- `CatalogAuthoritySpringWiringTest` proves the resolver and MCP catalog tracked
  constructors receive one shared `SemanticModelCatalogService` authority bean
  and do not fall back to legacy loader/bundle interactions. Model-service
  store/coordinator injection is covered by `SemanticModelCatalogServiceTest`
  and the source audit.

## Direct Criterion Evidence

| Owning suite | Tests | Direct assertions |
|---|---:|---|
| `SemanticModelCatalogServiceTest` | 11 | namespace/default scope；cold exact-empty and incomplete coordinator recovery；failure preservation；exact deletion；blocked admission；identity/name/alias/model/binding provenance pinning；missing provenance fail-closed；native metadata seqlock/retry exhaustion；no per-model active read |
| `CatalogNamespaceAuthorityTest` | 5 | model/resolver/MCP one identity-bound view；namespace isolation；complete generation switch；MCP metadata bounded retry；admission block and continuous churn fail-closed |
| `CatalogAuthoritySpringWiringTest` | 1 | resolver and MCP catalog tracked constructors receive one shared `SemanticModelCatalogService` authority bean；no legacy loader/bundle interactions |
| **direct total** | **17** | **3 owning reports；failures/errors/skipped=`0/0/0`** |

## Supporting Consumer Regression

| Owning suite | Tests | Role |
|---|---:|---|
| `SemanticServiceResolverImplTest` | 11 | namespace delegation, repeated discovery, event no-op and no MCP cache compatibility |
| `ListModelsToolTest` | 21 | configured/dynamic namespace selection and JSON/Markdown model-list consumers |
| `ListModelsCatalogControllerTest` | 4 | controller JSON/Markdown catalog response compatibility |
| **supporting total** | **36** | **4 owning reports；failures/errors/skipped=`0/0/0`** |

`ListModelsToolTest` has two JUnit nested-class XML reports; therefore the
supporting 36 tests occupy four owning reports and the complete evidence has
seven owning reports.

## Authoritative Run and Integrity

Command (recorded from the completed run; not re-executed during this
documentation update):

```bash
scripts/verify-v933-batch6-catalog-authority.sh
```

Run root:

```text
target/v933-batch6-catalog/runs/20260714T021057Z-2445113/
```

| Category | Tests | Reports | Failures/Errors/Skipped |
|---|---:|---:|---:|
| direct model authority | 11 | 1 | 0/0/0 |
| direct MCP authority | 5 | 1 | 0/0/0 |
| direct Spring wiring | 1 | 1 | 0/0/0 |
| supporting resolver | 11 | 1 | 0/0/0 |
| supporting ListModels | 21 | 2 | 0/0/0 |
| supporting controller | 4 | 1 | 0/0/0 |
| **total** | **53** | **7** | **0/0/0** |

Evidence integrity:

- `summary.env` SHA-256:
  `62604e1053c328c3c219da2f8792cdcb1d9ab13878f5d8ad021055ea7ea21563`
- `SHA256SUMS` SHA-256:
  `5b017cb750b0d28f4df8cd1998b63f27f46edcad25e27b9edda37aaa13fda3a1`
- source audit: consumer-owned names caches=0, independent MCP watcher
  registrations=0, direct model candidate access=0, sleep-driven catalog
  tests=0 and remaining `RedBaseline` sources=0.

## Remaining-red Replay

Run root:

```text
target/v933-batch1-red/runs/20260714T021251Z-2449309/
```

The replay reports 0 cases, 0 tests and 0 sources. `summary.tsv` SHA-256 is
`690b22d6cad493e293c6d9bbd5d8c7f37d7b027f06c46d82ba2c9306ef7a5406`.
This removes the historical `CATALOG-AUTHORITY` expected-red from the active red
inventory; it does not promote any cache or real-query criterion.

## Next Ordered Gate

Step 1 (catalog authority) is complete. Batch 6 continues in the frozen entry
order:

2. put catalog identity and exact dependency binding identities into
   L1/L2/Caffeine/Redis strong keys; incomplete/conflicting identity is cache
   no-read/no-write;
3. prove two independent application contexts serialize the same complete
   identity to the same key and generation changes rotate it;
4. prove shared-Redis behavior across two independent JVMs/restart, including
   deliberate boot-epoch cold miss;
5. make Pivot outer-cache key, pipeline and telemetry consume the same strong
   identity while the manual bundle token remains additive only;
6. execute the complete namespace/datasource/generation/cache/Pivot
   `REAL-QUERY` matrix through `QueryFacade` and compare rows, columns and order
   with native SQL;
7. freeze the Batch 6 runner, replay prior gates in order, write the exit record
   and only then open Batch 7.
