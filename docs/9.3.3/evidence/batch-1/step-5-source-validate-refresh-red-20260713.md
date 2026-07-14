---
doc_role: expected_red_evidence
doc_purpose: Capture pre-fix source commit, file scope, detached validate and atomic refresh failures.
version: 9.3.3
batch: 1
step: 5
status: completed-expected-red-and-source-proof
recorded_at: 2026-07-13
---

# Batch 1 Step 5: Source, Validate and Refresh Expected-Red Evidence

## Direct behavioral results

| Contract | Class | Tests/Failures/Errors/Skipped | Current observation |
|---|---|---:|---|
| REFRESH-FAIL / VALIDATE-ISOLATION | `RuntimeModelLifecycleRedBaseline` | 2/2/0/0 | failed refresh invokes all three live clears; validate invokes both live add and live remove |
| SOURCE-COMMIT | `BundleSourceCommitOrderingRedBaseline` | 1/1/0/0 | `BundleRemovedEvent` listener still sees bundle list, definition and external registry entries |
| REFRESH-SCOPE | `FileChangeNamespaceScopeRedBaseline` | 1/1/0/0 | one file event calls both TM and QM `clearAll()` |

The Runtime test uses `assertAll`, so its XML records all three refresh and both
validate mutations rather than stopping at the first Mockito violation. The
file-scope test likewise records both global clears. All classes end in
`RedBaseline`, use no sleep or unbounded wait and are explicit-only.

Commands used for the initial captures:

```bash
mvn -B -pl foggy-runtime-api \
  -Dtest=com.foggyframework.runtime.api.service.RuntimeModelLifecycleRedBaseline \
  test

mvn -B -pl foggy-fsscript -P'!multi-db' \
  -Dtest=com.foggyframework.bundle.lifecycle.red.BundleSourceCommitOrderingRedBaseline \
  test

mvn -B -pl foggy-dataset-model -P'!multi-db' \
  -Dtest=com.foggyframework.dataset.db.model.lifecycle.red.FileChangeNamespaceScopeRedBaseline \
  test
```

Initial local hashes:

| Artifact | SHA-256 |
|---|---|
| Runtime lifecycle XML | `8b71a468f1f8a2fa6fc4781ae1cb4498411743453bcd7b57e913f054d3b93359` |
| bundle source Maven log | `9c2f0fd7298d2b0266a0805e247ff7f9a7b5e0e14f7e0a0625021fc43358dd83` |
| bundle source XML | `8dc5287d581c297f00bd94e74ad0ac47ee7f64e2815af557535d884e627fb254` |
| file scope Maven log | `c0d611ca3e4a779d4f3ebb0e4788b1b85906e0b34ec40195c9ba207c5e780bef` |
| file scope XML | `ad48931d0ada778572c834008303d1acb0a0ceeb6f7cb63c8b96d6a3f286a499` |

These default report paths are initial diagnostic evidence only. Step 7
replayed every class in marker-bound run `20260713T115746Z-1058249` before
closing Batch 1.

## Atomicity and SourceRevision source proof

- Runtime refresh synchronously clears TM, QM and model names before discovery
  or any candidate build, then loads models one by one (current
  `RuntimeModelOperations` lines 173–223). There is no detached candidate,
  active reference or CAS; a failed model occurs after live authority changed.
- Runtime validate registers a temporary external bundle into the live source
  registry and removes it in `finally` (current lines 280–352).
- bundle remove publishes before clearing resource/script/watch indexes and
  before removing list/definition state (current `SystemBundlesContextImpl`
  lines 527–545).
- an arbitrary `FsscriptRemoveEvent` carries no committed revision/admission
  transition into the current handler; it clears every namespace (current
  `DbModelFileChangeHandler` lines 33–43).
- no `SourceRevision`, `CatalogGeneration` or `CatalogSnapshot` type/reference
  exists in current model, Runtime or fsscript main source.

Consequently a meaningful old/new concurrent-reader or stale-candidate test
cannot attach to a production snapshot/revision port yet. Creating a fake port
inside the test would only test the fake. Batch 5 must immediately add these
green-transition proofs after the real port exists:

1. block candidate build while readers repeatedly query; each result/catalog
   identity is entirely old until one publish, then entirely new.
2. candidate/dependency failure keeps old generation and real query result;
   publish count remains zero.
3. namespace A/model X refresh preserves namespace B and unchanged siblings.
4. mutate committed source or binding while a candidate is blocked; stale
   candidate publishes zero and reports the exact stable lifecycle code.
5. unknown file scope transitions possibly affected catalogs to
   `STALE_ADMISSION_BLOCKED`; it neither global-clears nor silently serves old.
6. valid and invalid Runtime validation leave bundle list, model maps, names,
   aliases, source revision and catalog generation identical.

This source proof is a pre-port baseline, not acceptance of atomic refresh.
