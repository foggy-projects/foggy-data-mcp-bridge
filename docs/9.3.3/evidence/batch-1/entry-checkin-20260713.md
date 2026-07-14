---
doc_role: execution-checkin
doc_purpose: Record Batch 1 entry, current source-proven failure contracts and the first deterministic red-test order.
version: 9.3.3
batch: 1
status: completed
started_at: 2026-07-13
closed_at: 2026-07-13
experience: N/A
---

# 9.3.3 Batch 1 Entry Check-in

## Entry decision

This entry was opened after Gate 0 run `20260713T104955Z-959834`. It is now
closed by `step-7-exit-readiness-20260713.md`: Steps 1–7 produced the frozen
contract, marker-bound expected-red/source evidence and post-change Gate replay
`20260713T120110Z-1066109`. Batch 1 is **completed for baseline purposes only**;
all lifecycle product criteria remain pending and Batch 2 is the first
production implementation lane.

## Current source-proven failure baseline

These anchors establish that the target contract is not already implemented;
they are source evidence for test design, not substitutes for the automated
red/green evidence required before Batch 1 exits.

| Contract gap | Current source evidence | Directly affected criteria |
|---|---|---|
| Namespace has no stack/token and production entry can erase the outer scope | `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/spi/NamespaceContext.java:14,21-23,40-42`; `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/service/impl/QueryFacadeImpl.java:122-127,177-180`; `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/engine/query_model/QueryModelLoaderImpl.java:181-200` | NS-02/03/05 |
| TM authority is a mutable global map behind an instance-wide load lock and global alias counters | `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/impl/loader/TableModelLoaderManagerImpl.java:72-75,101-170,426-450` | SNAPSHOT, SF-ISOLATION, deterministic alias |
| QM cache/alias state is nested mutable collections with multi-step publication and no single-flight | `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/engine/query_model/QueryModelLoaderImpl.java:60,78-98,111-173,812-865` | SNAP-01/03, SF-SAME, no-hybrid publish |
| A successful root TM can allow a QM with a failed joined TM to continue to registration | `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/engine/query_model/JdbcQueryModelBuilder.java:83-109,148-175,370-379`; `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/engine/query_model/QueryModelLoaderImpl.java:191-193` | QM-COMPLETE, REF-06 |
| Runtime refresh clears live TM/QM/catalog before sequential warmup and reports failure only afterwards | `foggy-runtime-api/src/main/java/com/foggyframework/runtime/api/service/RuntimeModelOperations.java:173-223` | REFRESH-ATOMIC, REFRESH-FAIL |
| Runtime validate registers a temporary directory in the live bundle/loader authority | `foggy-runtime-api/src/main/java/com/foggyframework/runtime/api/service/RuntimeModelOperations.java:280-352,382-406` | VALIDATE-ISOLATION |
| model and MCP each retain a global, non-generation, non-namespace names cache | `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/semantic/service/SemanticModelCatalogService.java:39,62-70,133-149,173-200`; `foggy-dataset-mcp/src/main/java/com/foggyframework/dataset/mcp/spi/impl/SemanticServiceResolverImpl.java:59,220-264` | CATALOG-AUTHORITY |
| Runtime datasource registry has no generation and an old stable wrapper can open a replacement physical pool | `foggy-runtime-api/src/main/java/com/foggyframework/runtime/api/service/RuntimeDatasourceRegistryService.java:35-37,61-97,137-151,340-359`; `foggy-runtime-api/src/main/java/com/foggyframework/runtime/api/service/ManagedDataSourcePoolManager.java:335-377,506-522` | DS-02/04/05, BINDING-REVOKE |
| Bundle removal publishes before source commit; arbitrary file removal clears every namespace | `foggy-fsscript/src/main/java/com/foggyframework/bundle/SystemBundlesContextImpl.java:527-544`; `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/event/BundleLifecycleListener.java:51-69`; `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/engine/query_model/DbModelFileChangeHandler.java:39-43` | SOURCE-COMMIT, EVT-01/02, REFRESH-SCOPE |

One compatibility test intentionally captures behavior that the new contract
must replace: `foggy-runtime-api/src/test/java/com/foggyframework/runtime/api/service/ManagedDataSourcePoolManagerTest.java:42-46,73-77` currently
expects the same wrapper after pool rebuild. It must be revised explicitly to
“same generation = same handle; changed/revoked generation = different or
rejected,” not silently deleted.

## Deterministic red-test order

All concurrency tests reuse the Gate 0 bounded harness; no test may use sleep
to decide interleaving.

1. `NamespaceProductionEntryRestorationContractTest`: outer A → inner B or explicit default, normal/exception return, wrong-thread/out-of-order close.
2. `JdbcQueryModelCompletenessContractTest`: root TM succeeds, joined TM fails, entire QM fails and remains undiscoverable.
3. `TableModelLoadConcurrencyContractTest#distinctKeysOverlap`: two keys must cross a barrier concurrently; the current manager-wide lock must fail this proof.
4. Same fixture: shared winner failure/retry plus 100 same-key callers. The 100-call success case alone is insufficient because a global lock can produce build=1 while still serializing everything.
5. `QueryModelAliasDeterminismContractTest`: colliding models loaded in forward/reverse order must publish the same alias map; add same-key single-publication proof.
6. `DatasourceBindingHandleContractTest`: old/new physical sentinels, generation-pinned handle, revoke admission and bounded in-flight drain.
7. `BundleSourceCommitOrderingTest` plus `FileChangeNamespaceScopeContractTest`: listener sees committed source and namespace A never clears B.
8. `RuntimeValidateIsolationContractTest`: valid and invalid validation leave live bundle/model/catalog identity unchanged.
9. `RuntimeAtomicRefreshContractTest`: failed candidate preserves old result; concurrent readers observe all-old or all-new only.
10. `CatalogNamespaceAuthorityContractTest`: model and MCP consumers expose the same namespace snapshot identity without a second names cache.

## Historical Batch 1 work boundary

- Keep the frozen Runtime DTO, error, lease and package decisions unchanged
  while capturing the remaining red baselines.
- Implement only the deterministic red tests/source assertions above until the
  contract review is closed; do not start snapshot/single-flight/refresh
  production code yet.
- Preserve Gate 0 as the required runner for every Batch 1 test change.
- Batch 1 exit requires reproducible red evidence (or explicit source proof
  where a compiling test cannot precede the port) mapped to every critical
  contract item; assertions must not be weakened when old code happens to
  pass.

## Frozen Step 1 decision (2026-07-13)

- contract status changed from `under-review` to `confirmed`; pending decisions
  are now `none`.
- model authority remains under
  `com.foggyframework.dataset.db.model.lifecycle`; only `NamespaceScope` joins
  the existing `spi` package. This is not a premature 9.4.0 SPI v2 promise.
- Runtime DTO keeps every legacy field/HTTP/error-code semantic and adds exact
  opaque generation/revision/state fields plus nullable `lifecycleCode` and a
  typed, bounded, sanitized failure context carried by `error.lifecycle` while
  failure `data` remains null.
- Runtime binding admission freezes `OPEN -> RETIRING -> REVOKED/CLOSED`,
  `revokeMode=DRAIN|HARD` with default DRAIN, and
  `lease-drain-timeout-ms=60000` constrained to 1000..300000 ms.
- no production file changed for this decision; pre-fix source behavior remains
  intentionally unchanged for red-baseline capture.

Closure: Steps 2–6 and Step 7 were completed in order without production
lifecycle code. Next execution point is Batch 2 only: implement
`NamespaceScope`, migrate production namespace entry points and turn the
NS-SCOPE baseline into the required normal-green matrix.
