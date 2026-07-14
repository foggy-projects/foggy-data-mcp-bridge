---
doc_role: expected_red_evidence
doc_purpose: Capture the pre-fix split model/MCP catalog authority and namespace identity source proof.
version: 9.3.3
batch: 1
step: 6
status: completed-expected-red-and-source-proof
recorded_at: 2026-07-13
---

# Batch 1 Step 6: Catalog Authority Expected-Red Evidence

## Behavioral result

`CatalogNamespaceAuthorityRedBaseline` starts
`SemanticModelCatalogService` and `SemanticServiceResolverImpl` over the same
mocked source and loader:

```text
before event: model=[BeforeEventModel], MCP=[BeforeEventModel]
after one committed .qm event: model=[BeforeEventModel], MCP=[AfterEventModel]
```

The one direct assertion is expected red: tests=1, failures=1, errors=0,
skipped=0. This proves the two consumers can expose different names views after
the same source change. It uses no Spring context, thread, sleep, filesystem
secret or external service.

Initial command/report:

```bash
mvn -B -pl foggy-dataset-mcp \
  -Dtest=com.foggyframework.dataset.mcp.spi.impl.CatalogNamespaceAuthorityRedBaseline \
  test
```

- XML SHA-256:
  `287e5d08bd2b94d8d0592316a1fb65e794ed61d5383a83ed082f48286cce874e`.
- the module default report directory contains other suites; this initial hash
  is diagnostic only. Step 7 performed the exact-one, marker-bound replay in
  run `20260713T115746Z-1058249`.

## Namespace/catalog identity source proof

- model `SemanticModelCatalogService` declares one global
  `volatile List<String> cachedModelNames` and synchronized scan cache; neither
  the cache key nor return value carries namespace, source revision or catalog
  generation (current lines 39, 62–70, 133–149).
- MCP `SemanticServiceResolverImpl` independently declares the same global
  names cache and its own invalidation listener (current lines 59, 220–264).
  `getAllModelNames(namespace)` filters a global list after the fact; it does
  not read a namespace snapshot identity.
- the model service has no matching source-event authority; MCP invalidation can
  advance one view while the model-side cache stays stale, as the direct red
  test demonstrates.
- current main source contains no `CatalogSnapshot`/`CatalogIdentity` port for
  either consumer to return, so a test cannot yet compare real per-namespace
  snapshot identities without inventing a test-only authority.

Batch 3/6 green transition must:

1. create two namespaces whose visible model sets differ and assert model REST,
   MCP resolver and catalog service read the same snapshot identity for each;
2. publish a source change for namespace A and assert all A consumers switch
   once while every namespace B identity/view remains unchanged;
3. remove both independent `cachedModelNames` fields and consumer-side
   invalidation ownership; compatibility clear methods may only delegate the
   core coordinator;
4. assert alias/name/model resolution all originate from one immutable snapshot
   and never mix generations;
5. add model + MCP contract tests that compare generation/source/binding
   identity, not merely list equality.

The current red/source proof establishes the repair target; CATALOG-AUTHORITY
remains unimplemented until those normal green tests pass.
