---
doc_role: criterion_evidence
doc_purpose: Record Steps 2-3 direct cache-identity evidence and the remediated authoritative Step 4 cross-JVM pass as a historical Step 4 checkpoint.
version: 9.3.3
batch: 6
steps: 2-4
status: passed
cache_gen_status: passed-by-step-5-evidence
cache_cross_jvm_status: passed
batch_status: in-progress
checkpoint_step: 4
current_step: 6
authoritative_cross_jvm_run: 20260714T025444Z-2628329
next_evidence: pivot-cache-generation-green-20260714.md
created_at: 2026-07-14
updated_at: 2026-07-14
---

# Batch 6 Cache Identity and Cross-JVM Green

## Decision

The remediated Step 4 evidence is authoritative after independent second
review found no blocker:

- Step 2 proves QueryFacade pins the exact lifecycle resolution into execution
  context and L1/L2 Caffeine/Redis consumers require the complete canonical
  catalog/source/binding identity. Missing, malformed or conflicting identity
  is cache no-read/no-write.
- Step 3 proves two real independent Spring application contexts, using
  different provider and key-builder instances, serialize the same complete
  identity to identical L1/L2 keys. Catalog or binding generation changes
  rotate both keys.
- Step 4 run `20260714T025444Z-2628329` proves the production auto-configured
  Redis template/provider path in two child JVMs against one real Redis, using
  an exact resolution derived from a non-empty published snapshot. The runner
  independently verifies child, report and Redis state instead of accepting a
  self-reported summary as authority.

`CACHE-CROSS-JVM` is **passed**. At this historical Step 4 checkpoint, Steps
2/3 were direct partial evidence for `CACHE-GEN`. Step 5 has since passed in
`pivot-cache-generation-green-20260714.md`, so the current status is
`CACHE-GEN=passed`, Batch 6 current Step 6 complete `REAL-QUERY` in-progress,
Step 7 pending and Batch 7 closed/not-started.

## Identity Boundary

- `QueryCacheKeyBuilder` has no generated instance UUID, object-address,
  `identityHashCode` or similar per-instance fallback. It builds L1/L2 keys only
  from canonical lifecycle/query/security identity and returns no key when the
  required catalog/source/binding identity is absent or inconsistent.
- the boot UUID exists only in `CatalogSnapshotStore`, where it is part of the
  published catalog/source epoch. A restart changing that epoch is the expected
  process-cold identity, not a key-builder fallback.
- canonical binding entries are order independent, but backend ID, binding
  generation, catalog generation or source revision changes rotate both cache
  layers. JDBC cannot claim datasource-free completeness.

## Step 2 Strong-key Evidence

| Lane | Tests | Result | Evidence |
|---|---:|---|---|
| model `QueryFacadeCatalogIdentityTest` | 4 | 4/0/0/0 | atomic catalog resolution pin；namespace/model conflict fail-closed；log `target/v933-batch6-cache-model-pin.log` |
| addon `QueryCacheKeyBuilderStrongIdentityTest` | 12 | 12/0/0/0 | complete L1/L2 catalog/source/binding key、generation rotation、canonical ordering、malformed/incomplete identity no-key |
| addon `CaffeineQueryCacheProviderTest` | 30 | 30/0/0/0 | L1 provider strong-identity behavior and compatibility |
| addon `RedisQueryCacheProviderTest` | 25 | 25/0/0/0 | L2 provider strong-identity behavior and real serializer contract |
| addon `CaffeineQueryCacheProviderDataSourceIsolationIntegrationTest` | 1 | 1/0/0/0 | SQLite datasource isolation sentinel |
| **Step 2 total** | **72** | **72/0/0/0** | **model 4 + addon focused 68** |

Evidence integrity:

- `target/v933-batch6-cache-model-pin.log` SHA-256:
  `c139540a97d1e7fe55ee4bbe5aa200d0b3e5ee250aa3ebf97ced41b8dfdcc048`
- `target/v933-batch6-cache-addon-focused.log` SHA-256:
  `370481a259c84376fc9e83ea84586534c94c0c4cae47d93d3d2f6e5ce97b555a`

These tests directly cover Step 2, but do not include Pivot and therefore do
not close `CACHE-GEN`.

## Step 3 Cross-ApplicationContext Evidence

`QueryCacheKeyCrossApplicationContextTest` executed 1/1 test with
failures/errors/skipped=`0/0/0`:

- two real Spring contexts create different provider and builder instances;
- identical complete catalog/source/binding/query/security identity yields
  identical L1 and L2 keys across those contexts;
- catalog generation or binding generation changes rotate both keys.

Evidence:

- XML:
  `addons/foggy-dataset-model-cache/target/surefire-reports/TEST-com.foggyframework.dataset.db.model.cache.provider.QueryCacheKeyCrossApplicationContextTest.xml`
- XML SHA-256:
  `cc252b5de28832631b19257245c831eaabaa0002769e6078379da770676c37cb`
- log: `target/v933-batch6-cache-cross-application-context.log`
- log SHA-256:
  `fa7e0f962eda61cb8f97801ccc2a458fed8e91c861e43d6660219b30eb119563`

This is direct Step 3 evidence, not a substitute for the independent-process
Step 4 or the pending Pivot Step 5.

## Step 4 Authoritative Cross-JVM Run

Recorded command (not re-executed by this documentation update):

```bash
scripts/verify-v933-batch6-cache-cross-jvm.sh
```

Authoritative run root:

```text
target/v933-batch6-cache-cross-jvm/runs/20260714T025444Z-2628329/
```

| Item | Authoritative observation |
|---|---|
| result | `RedisCrossJvmCacheIT` 1/0/0/0 in exactly one owning XML；independent second review no blocker |
| Redis | 7.4.6 standalone；image `redis:7-alpine`；existing image ID `sha256:3b73847e72874be07e6657b129a94761662b79bc0f679273757d4218573b2a98` |
| child JVMs | exactly 2 distinct processes；writer PID `2629762`，restart PID `2630070` |
| production path | production auto-configured Redis template/provider；no test-only serializer/provider |
| lifecycle resolution | exact resolution derived from a real non-empty published snapshot；27 independently checked probe fields |
| binding | both processes pin the same `primary` / `runtime-registry` / `binding:persisted:1` binding |
| old physical-key read controls | `previous_identity_control_hits=2`；2 次读取 writer 已发出的物理 Redis keys，仅证明旧物理 key 可读，不代表 lifecycle resolution/provider 命中，也没有构造伪造的旧 `CatalogResolution` |
| restart current identity | 2 misses for the restart JVM's current catalog/source identity |
| post-write controls | 2 hits after writing under the restart JVM's current identity |
| emitted Redis keys | Redis `SCAN` result = 4 and `DBSIZE` = 4；the two independently derived sets match |
| integrity/safety | two-layer hash verification passed；container cleaned；no credentials retained in evidence |

The `previous_identity_control_hits=2` old physical-key read controls prove
only that the previously emitted physical Redis values remain readable through
the production serialization path. They are deliberately not counted as
lifecycle resolution or provider hits and do not construct a fabricated old
`CatalogResolution`. The criterion assertion is that the restart JVM's
snapshot-derived current identity cannot reach those old keys, while its own
post-write reads succeed.

Evidence integrity:

- owning XML SHA-256:
  `93e92cf913098bbd110da8b0a98fd2a16414b2fe615e7fc98edb9277dffd51f9`
- `summary.env` SHA-256:
  `ff76b8a81360606e97a316797f40f7d145951c56e7e803911be030d4a3e5358e`
- outer `SHA256SUMS` SHA-256:
  `a54b7a4b18b750a5afb99fc3ee47b91af4759d0420f60cf9fb4c28edb1950ed3`
- source manifest `source-audit/source-files.sha256` SHA-256:
  `f7c53054262502c7a348ea93e0ed9e12d461e2c18c4dd2105563b03ab300eea6`

The two-layer check independently validates files against the source manifest
and validates the frozen run artifacts through `SHA256SUMS`; hashes establish
integrity while the runner's independently derived assertions establish
correctness.

## Diagnostic / Superseded Runs

- `20260714T023304Z-2522929` is diagnostic failed and excluded. Generic Jackson
  initially rejected an unknown derived `empty` field.
- `20260714T023442Z-2527116` is diagnostic/superseded and excluded. Independent
  review found a test-only serializer, an empty snapshot with hand-written
  resolution, and runner probe/summary/hash self-reporting that could
  false-green.
- `20260714T025247Z-2623994` is diagnostic/superseded and excluded. Its product
  test was green, but the runner's model-segment parser failed, so no
  authoritative summary/checksum package was frozen.

None of these runs contributes to the Step 4 pass. Only
`20260714T025444Z-2628329` is authoritative.

## Current Ordered Gate

Step 5 has passed under the separate Pivot evidence record. The remaining
ordered work is:

6. execute the complete namespace/datasource/generation/cache/Pivot
   `REAL-QUERY` matrix through `QueryFacade` and compare rows, columns and order
   with native SQL.
7. freeze the Batch 6 runner, replay prior gates in order, write the Batch 6
   exit record and only then open Batch 7.
