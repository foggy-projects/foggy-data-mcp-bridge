---
doc_role: batch_entry_checkin
doc_purpose: Freeze Batch 3 Binding Identity and CatalogSnapshot scope, prerequisites and execution order.
version: 9.3.3
batch: 3
status: in-progress
recorded_at: 2026-07-13
---

# Batch 3 Entry Check-in

## Entry decision

Batch 3 is **in progress**. Entry prerequisites are satisfied:

- Batch 2 `NS-SCOPE=passed` via run `20260713T130626Z-1313396`;
- post-Batch2 9.3.3 entry gate passed via run
  `20260713T130717Z-1316013`;
- lifecycle contract has no pending decision;
- the 9.3.1/9.3.2 dirty worktree remains protected by the Gate 0 baseline;
  no reset/checkout/clean/stash is authorized.

## Ordered scope

1. Strong opaque catalog/source/binding identity types and immutable
   per-namespace snapshot store.
2. Candidate-to-snapshot publication of TM/QM/synthetic/discovery/alias and
   provenance through one authority.
3. Candidate/request-local loader build state; no partial QM publication.
4. Deterministic aliases independent of load arrival order.
5. Datasource binding generation and admission/lease core ports, followed by
   Runtime and MCP compatibility adapters.
6. Persisted Runtime registry epoch/sequence and generation-pinned handles with
   DRAIN/HARD retirement semantics.
7. Query/metadata capture of one catalog identity plus the resolved model's
   exact dependency binding identities.

## Open criteria

- `SNAPSHOT`
- `GENERATION`
- `DS-GENERATION`
- `BINDING-REVOKE`
- `QM-COMPLETE`

Each criterion remains pending until its normal-green tests and authoritative
Batch 3 run exist.

## Boundaries

- Batch 4 single-flight is closed until the Batch 3 key identities are stable.
- Batch 5 refresh/source-event/Runtime DTO work is closed.
- Batch 6 model/MCP catalog-consumer unification and cache/Pivot generation
  consumption are closed.
- No 9.3.4 full CI, 9.3.5 API refactor or 9.4.0 module/SPI split.
- Experience: N/A; this is backend lifecycle and datasource state management.

## Initial risk focus

- Existing TM/QM maps and alias counters are mutable and split across loaders.
- `JdbcQueryModelBuilder` accumulates errors but can return a partial model if a
  later dependency fails unless candidate publication validates the full build.
- Runtime pool slots can currently replace the delegate behind a stable wrapper.
- Runtime registry has no persisted generation/epoch; MCP manager has no cold
  boot generation domain.
- Admission linearization must remain separate from active catalog retention so
  a preserved old snapshot cannot reacquire a revoked binding.
