---
doc_role: batch_entry_checkin
doc_purpose: Freeze Batch 5 atomic refresh, committed source and detached validation scope.
version: 9.3.3
batch: 5
status: in-progress
recorded_at: 2026-07-13
---

# Batch 5 Atomic Refresh Entry Check-in

## Entry decision

Batch 5 is **in progress**. Entry prerequisites are satisfied:

- Batch 4 completed via authoritative run `20260713T164144Z-1910217`;
- the post-Batch4 entry gate passed via run `20260713T165330Z-1941345`;
- exact immutable catalog/source/binding views and keyed loader single-flight are
  available as refresh inputs;
- Batch 3 replay `20260713T164538Z-1920394` remained green after Batch 4;
- the protected dirty-worktree baseline remains in force; no reset, checkout,
  clean or stash is authorized.

## Ordered scope

1. Add refresh-only candidate semantics for exact namespace replacement and
   model/dependency/reverse-dependent replacement while preserving siblings.
2. Add one per-namespace coordinator for capture, detached build, full
   validation, final source/binding currentness and one catalog publication.
3. Make source registry/script cache/reverse-import mutations commit before
   rich bundle/file events, with a source-side publication guard closing the
   commit-to-listener race.
4. Route bundle/file/Runtime/datasource mutations into the same coordinator;
   unknown file scope blocks catalog admission instead of clearing globally.
5. Replace Runtime clear-first refresh with the coordinator and expose the
   frozen additive generation/state/diagnostic DTO fields.
6. Validate external model directories with isolated source/script/catalog
   state and no temporary live bundle registration.
7. Prove old-or-new atomic reads, failure preservation, namespace/sibling
   isolation, event convergence and sanitized compatibility responses.

## Open criteria

- `REFRESH-ATOMIC`
- `REFRESH-FAIL`
- `REFRESH-SCOPE`
- `EVENT-CONVERGENCE`
- `SOURCE-COMMIT`
- `VALIDATE-ISOLATION`

`QM-COMPLETE` remains passed from Batch 3 and receives a coordinator-level
regression only. Runtime `API-COMPAT` evidence in this batch is supporting;
final Launcher/old-consumer sign-off remains Batch 7.

## Frozen implementation findings

- the pre-Batch5 candidate copied its base and exposed only `putIfAbsent` plus
  discovery union, so it could neither replace changed models nor remove a
  deleted source model;
- existing loaders owned their own candidate commit, so looping over them
  could expose several generations rather than one atomic refresh;
- bundle removal published before source registry/cache removal, and file
  invalidation removed the root script before consulting reverse importers;
- file events carried neither committed revision nor affected namespace, and
  the model listener used production `clearAll`;
- Runtime refresh cleared live TM/QM/catalog first, while validate temporarily
  registered and removed a live external bundle;
- Batch 6 catalog/cache/Pivot consumer unification remains explicitly closed.

## Initial verification boundary

The Batch 5 runner must bind only fresh owning reports, fail on zero tests or
stale XML, reject production clear-first/source-unknown paths, and continue to
require the Batch 6 `CatalogNamespaceAuthorityRedBaseline` expected-red case.
No Batch 5 criterion is promoted until the final authoritative run and its
checksums are recorded.
