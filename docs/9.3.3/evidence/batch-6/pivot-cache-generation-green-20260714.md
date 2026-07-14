---
doc_role: criterion_evidence
doc_purpose: Record the authoritative Batch 6 Step 5 Pivot cache-generation pass and hand off to Step 6 REAL-QUERY.
version: 9.3.3
batch: 6
step: 5
status: passed
criterion: CACHE-GEN
criterion_status: passed
batch_status: in-progress
current_step: 6
authoritative_run: 20260714T032135Z-2678670
created_at: 2026-07-14
updated_at: 2026-07-14
---

# Batch 6 Step 5 Pivot Cache Generation Green

## Decision

Step 5 is signed off and `CACHE-GEN` is **passed**. The authoritative run is:

```text
target/v933-batch6-pivot/runs/20260714T032135Z-2678670/
```

Independent second review found no blocker. Together with the retained Step 2
L1/L2 strong-key and Step 3 cross-context evidence, this run proves that Pivot
outer-cache lookup/store, pipeline pinning and diagnostics consume the same
complete lifecycle identity. Batch 6 is now **in progress / current Step 6**:
complete `REAL-QUERY` is in progress, Step 7 is pending, and Batch 7 remains
closed/not-started.

## Authoritative Result

Recorded command（not re-executed by this documentation update）：

```bash
scripts/verify-v933-batch6-pivot-identity.sh
```

| Evidence role | Owning reports | Tests | Failures/Errors/Skipped |
|---|---:|---:|---:|
| direct | 4 | 38 | 0/0/0 |
| supporting | 3 | 61 | 0/0/0 |
| **total** | **7** | **99** | **0/0/0** |

Both Maven lanes contain exactly one `BUILD SUCCESS`. All seven owning XML
reports are newer than their lane run markers, and their testcase counts match
the frozen summary.

### Direct evidence

| Owning suite | Tests | Direct boundary |
|---|---:|---|
| `PivotOuterCacheStrongIdentityTest` | 4 | complete length-framed SHA-256 identity；all catalog/source/binding components rotate the key；canonical exact bindings are sorted；missing/incomplete/JDBC-empty/conflicting identity is refused |
| `PivotPipelineCatalogIdentityTest` | 8 | one resolution/pin across entry/read/write；no lookup/store after refusal；Semantic service and managed-relation pre-pin；generation conflict fails before old-key store |
| `QueryFacadeCatalogIdentityTest` | 6 | atomic pin；same-resolution idempotence；namespace/model/generation conflicts fail before filters or query execution |
| `SemanticRequestContextTest` | 20 | typed catalog resolution survives domain transport plans and rejects namespace/generation conflicts |
| **direct total** | **38** | **4 fresh owning reports** |

The provider-exception blocker is closed by the eighth
`PivotPipelineCatalogIdentityTest` direct case,
`completeLifecycleStillRefusesCacheWhenSupplementaryProviderThrows`. Even with
a complete lifecycle identity, a supplementary provider exception produces no
cache lookup and no store.

### Supporting evidence

| Owning suite | Tests | Supporting boundary |
|---|---:|---|
| `PivotIntegrationTest` | 55 | Pivot query behavior、outer-cache hit/miss/TTL/security/warning/invalidation compatibility |
| `PivotOuterCacheOperationalSpiTest` | 3 | existing operational SPI behavior and runtime bundle identity provider |
| `PivotOuterCacheTelemetryTest` | 3 | query-model、bundle/manual freshness and permission telemetry compatibility |
| **supporting total** | **61** | **3 fresh owning reports** |

The supporting lane retains one existing bounded 20 ms TTL-expiry wait. The
direct identity lane has no sleep or skip, so the supporting wait does not
weaken the `CACHE-GEN` criterion evidence.

## Identity and Fail-closed Contract

- the Pivot lifecycle segment is length-framed and hashed with full SHA-256;
- it includes catalog generation, source revision, canonical namespace/model
  and the exact sorted dependency binding identities;
- missing, incomplete, JDBC-empty or conflicting identity refuses cache I/O;
- supplementary provider failure also refuses lookup/store, including when the
  core lifecycle identity is otherwise complete;
- provider/manual tokens are additive segments only and cannot rescue missing
  or refused lifecycle identity;
- provider failure diagnostics expose only a normalized exception type, never
  exception message, token, binding text or stack trace;
- `SemanticQueryServiceV3Impl` and managed-relation entry paths directly prove
  pre-pinning of the same typed catalog resolution;
- QueryFacade rejects a mid-request generation conflict before filters or query
  execution, so an old identity cannot be stored after the switch;
- public record components, provider SAM, legacy `OuterCacheOptions`
  construction and component wiring remain compatible.

Source audits report zero forbidden identity fallbacks, zero direct-lane
sleep/skip and zero sensitive output.

## Evidence Integrity

- `summary.txt` SHA-256:
  `45f9cc2ab69f6e63434606be645b607a430869ca1a6ff073ce16a709163c40d7`
- source manifest `source-audit/source-files.sha256` SHA-256:
  `50f3dc8fd40a9bde4d88f5487318730f54a8be22eeeb011ff0cbfb2fe207849b`
- outer `SHA256SUMS` SHA-256:
  `e7014adaaa1932d62993d2a051830e9bb3f3b2fb4c7a982f356b32fc67d04ad7`

The source manifest and outer artifact manifest were each independently
verified with `sha256sum -c`. Only the authoritative run above is cited as
criterion evidence.

## Next Ordered Gate

6. finish the in-progress complete `REAL-QUERY` matrix through `QueryFacade`:
   namespace/datasource/generation/cache/Pivot results must match native SQL in
   rows, columns and order, with the correct physical sentinel.
7. after Step 6 passes, freeze the Batch 6 runner, replay prior gates in order
   and write the Batch 6 exit record.

Batch 7 remains closed until `REAL-QUERY` is passed and Batch 6 Step 7 has
completed.
