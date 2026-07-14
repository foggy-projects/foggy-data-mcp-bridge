---
doc_role: batch_exit_evidence
doc_purpose: Close Batch 3 CatalogSnapshot and datasource binding identity implementation and hand off single-flight work to Batch 4.
version: 9.3.3
batch: 3
status: completed
recorded_at: 2026-07-13
next_batch: 4
---

# Batch 3 CatalogSnapshot / Binding Identity Exit

## Exit decision

Batch 3 is **completed** and Batch 4 (`Single-flight 读路径`) is **ready to
start**. The following owning criteria now have normal-green evidence:

- `SNAPSHOT`
- `GENERATION`
- `DS-GENERATION`
- `BINDING-REVOKE`
- `QM-COMPLETE`

This record does not promote any Batch 4–7 criterion. In particular, the
Caffeine identity lane is supporting compatibility evidence only; it is not a
claim that Batch 6 Redis/Pivot/cross-JVM cache work is complete.

## Implemented authority and adapters

The model module now owns one immutable per-namespace catalog authority:

- strong opaque `CatalogIdentity`, `CatalogGeneration`, `SourceRevision`,
  `DatasourceBindingIdentity` and `DatasourceBindingGeneration` types;
- `CatalogSnapshotStore` with request-local `CatalogCandidate`, atomic publish,
  exact TM/QM/synthetic/discovery/alias/provenance invariants and defensive
  immutable projections;
- candidate owner-thread and post-publish seal protection; accumulated build
  failures are checked before the no-op path, so a pure `fail()` cannot be
  silently accepted;
- deterministic aliases for static discovery sets and canonical-stable hashed
  aliases for dynamically arriving synthetic member models;
- TM/QM loaders use the shared store rather than mutable manager maps/counters;
  the named datasource is resolved once and pinned for the complete TM build;
- partial joined-TM failure rejects the whole QM, while an external legacy
  `TableModelLoaderManager` is explicitly untracked and cannot replace an
  existing same-name TM slot with a fresh object instance;
- QueryFacade atomically pins model, catalog and exact dependency binding
  identities into `ModelResultContext`; conflicting repins, namespace mismatch
  and query-engine model replacement fail closed;
- metadata bulk resolution re-reads all models from one final snapshot and
  retains the catalog/per-model resolutions; legacy bulk-null fallback is
  explicitly marked untracked.

The binding adapters now implement generation-specific admission and leases:

- Runtime registry v2 persists boot epoch/sequence and binding generations,
  migrates v1 records, and rejects incomplete v2 identity records;
- Runtime and MCP create a fresh generation-specific physical handle for each
  committed configure/rebind; old handles never switch their physical target;
- remove/rebind closes admission before any new old-generation borrow, supports
  default bounded `DRAIN` and explicit `HARD`, and closes pools/connections
  exactly once;
- MCP startup assigns cold generations in canonical-name order and fails closed
  on canonical collisions; persistence failures leave the current binding open;
- generation/log/error values use logical identifiers only and do not contain
  JDBC URLs, hosts, database names, usernames or credentials.

Primary production surfaces:

- `foggy-dataset-model/.../lifecycle/{identity,catalog,port}`
- `TableModelLoaderManagerImpl`, `QueryModelLoaderImpl`,
  `JdbcQueryModelBuilder`, `QueryModelLoader`, `DbModelAutoConfiguration`
- `QueryFacadeImpl`, `ModelResultContext`, `SemanticServiceV3Impl`,
  `SemanticModelCatalogService`, named datasource resolver SPI/adapter
- `foggy-runtime-api` registry/resolver/pool/properties services
- `foggy-dataset-mcp` datasource manager/resolver/controller/persistence adapter
- `addons/foggy-dataset-model-cache` strong identity key compatibility

## Authoritative Batch 3 run

- command: `scripts/verify-v933-batch3-catalog-binding.sh`
- final run: `20260713T150948Z-1719636`
- result: passed
- catalog authority: 46 tests
  - snapshot 4, store/candidate 10, discovery 3, metadata identity 3,
    QM completeness 2, QueryFacade pinning 4, TM datasource resolution 12,
    auto-configuration 8
- SQLite consumer regression: 33 tests
  - deterministic alias 1, synthetic lifecycle 4, QueryFacade 5,
    Semantic metadata 23 (14 + nested 5 + nested 4)
- Runtime binding: 28 tests
  - lifecycle 10, registry generation 5, resolver 1, pool manager 12
- MCP binding: 12 tests
- Caffeine identity compatibility: 30 tests
- total: 149
- failures/errors/skipped: `0/0/0`
- evidence root:
  `target/v933-batch3-catalog-binding/runs/20260713T150948Z-1719636/`
- `summary.env` SHA-256:
  `9cffe4af981958bbcae65c58920cb05daf63e82650bd6a30f5ffe2f1e52d5038`
- `SHA256SUMS` SHA-256:
  `6bf875d530c75bb62eb185ccdfbf9def4593c0a3cba013d1ccb6a0ad7459100d`
- `sha256sum -c SHA256SUMS`: all files OK

The runner binds each expected class to a fresh owning XML report and exact
test count. Source audits also passed:

- legacy mutable catalog authority: 0
- sleep-driven Batch 3 tests: 0
- promoted product-green cases still referenced by the Batch 1 red runner: 0
- physical/credential inputs used to construct binding generations: 0

## Fail-closed evidence evolution

The first runner attempt, `20260713T144351Z-1647790`, stopped because the report
set assertion expected four Semantic reports while JUnit produced the owning
class plus two nested-class reports. This was runner fail-closed behavior, not a
product-test failure; the exact report set was corrected without weakening any
assertion.

Run `20260713T145617Z-1669200` then passed 146 tests, but was deliberately
superseded after static review found three uncovered correctness gaps: pure
candidate failure bypassing no-op publication, direct cross-thread candidate
mutation and external-loader same-name TM object replacement. Each gap received
a deterministic regression test and implementation fix. The final 149-test run
above is the only Batch 3 exit authority.

## Final 9.3.4-A replay

The entry gate was replayed after the final production/test source:

- command: `scripts/verify-v933-entry-gate.sh`
- run: `20260713T151323Z-1726207`
- positive: deterministic unit 1, deterministic IT 1, SQLite 1,
  MySQL 5.7 1 and PostgreSQL 15 1; all failures/errors/skipped = 0
- expected-negative: missing owning unit, missing owning IT, wrong required DB
  and missing owning report all failed closed (`4/4`)
- `summary.env` SHA-256:
  `74d624b53864342bebbe8ef22b6dd1a2066cdb859dc85ff58f693cfab059f229`
- `SHA256SUMS` SHA-256:
  `88d9231da3c5457a59893343a0b43adf4f045f477d2d2f6f65b1aadd5a5435b4`
- `sha256sum -c SHA256SUMS`: all files OK

This remains a 9.3.4-A preflight-only gate. No remote GitHub Actions, complete
five-database matrix, coverage threshold or immutable release artifact is
claimed.

## Lightweight implementation self-check

- scope stayed inside Batch 3 identity/snapshot/binding work; no single-flight,
  refresh/event ownership, Redis/Pivot consumer or public API/module split was
  pulled forward;
- the shared authority removes duplicate mutable model maps/counters; candidate
  mutation and publication boundaries are explicit and guarded;
- deterministic tests use latch/manual clock/manual scheduler/bounded future,
  never `Thread.sleep`;
- build/persistence/revoke/namespace/object-graph conflicts fail closed and do
  not partially publish or silently reuse an old physical target;
- compatibility defaults remain additive: old `QueryModelLoader` implementations
  return untracked resolutions, and legacy datasource entry signatures remain;
- two rounds of focused static review closed all identified Batch 3 blockers;
  final review found no remaining exit blocker;
- experience: N/A (backend lifecycle/binding delivery, no UI).

- self-check decision: `self-check-only`
- quality blocker: none
- needs-formal-quality-gate: yes; formal implementation quality review remains
  owned by Batch 7 after all production batches complete

## Remaining boundaries and next execution point

- Batch 4 owns per-key single-flight, distinct-key overlap, shared failure,
  retry cleanup and dependency-cycle/self-wait prevention.
- Batch 5 owns committed source revision stale checks, detached validate,
  refresh coordination and bundle/file/datasource event convergence.
- Batch 6 owns unified model/MCP catalog consumers, complete L1/L2/Redis/Pivot
  identity consumption, independent-process cache proof and real-query parity.
- Batch 7 owns full regressions, formal quality gate, coverage audit and version
  acceptance.

Start Batch 4 only. Batch 5–7 remain closed.
