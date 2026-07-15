---
doc_role: execution_evidence
doc_purpose: Record the committed fresh Redis external subset candidate and real signal cleanup probes.
version: 9.3.4
step: 3
status: passed-subset-candidate
authority_scope: external-redis-run-local-subset
created_at: 2026-07-15
commit: 35ddf73f359a444faa1db4b03dcc9f3ef7274aa2
---

# Step 3 committed Redis external subset candidate

## Decision

Committed run `external-redis-candidate-35ddf73f-r2` passes the `external-redis` subset:

```text
2 variants / 2 reports / 3 testcase / F0/E0/S0
candidate artifacts=35
container residue=0
volume residue=0
```

This is a run-local subset candidate. Its final report manifest has `complete=false`; it does not
prove the remaining external `14/73`, the complete external `16/76`, the database `29/370`, or the
Step 3 final `45/446`. Step 3 remains `in-progress` and Step 4 is not open.

## Frozen implementation identity

| Artifact | SHA-256 |
|---|---|
| Git commit | `35ddf73f359a444faa1db4b03dcc9f3ef7274aa2` |
| external contract | `0bd1243bff03d4acb4232718980153863b0fc5d548e149bf28fb56dc5df1061d` |
| report/candidate tool | `b84aeba02fc64659d04720dee4ac6a731d5b2dfd757014114d7b27124b1cbe5b` |
| Redis runner | `a9e898583cf408bee2e2ed4793bfa757f81ecab77492ccb2a41eb869ed89b114` |
| signal probe | `edec82c7faf84e8056da34d637d9254992cdbb8ba536bd2a3797642f22a10c6f` |
| deferred Step 3 inventory | `89190db9370fe3117d5316c72835efffa577d132d39cef9b5d9ae3558afa7601` |

The contract freezes exact `7 variants / 16 required reports / 76 testcase` plus one optional LLM
report. It also binds the runner/tool/helper bytes, exact Maven selectors, the Step 2 inventories and
the fail-closed `AiToolsIT` source amendment.

## Positive run

- run id: `external-redis-candidate-35ddf73f-r2`
- started/finished: `2026-07-15T09:54:30Z` / `2026-07-15T09:56:53Z`
- durable status: `passed / exit_code=0 / last_phase=completed`
- source-set logical seal before/after:
  `8b431e517acfa5c75f510f40f2d6d6eda44830075c3dffca9fb51a9e81ee8205`
- source TSV file SHA before/after:
  `c02d9cc6425e3cca7717e18aa67c2aecfef22e643d81495a45d5bbad48c8803d`
- two variant bytecode seals, both:
  `075b1431718b1e45d860e7a43c71d44f4c7488142184a2f0245bff3f68d149a3`
- candidate manifest:
  `35373665d586f23eff68a8d24dfcecb07e015d123640ef2e67be119905b7338a`
- outer marker:
  `4373a55987223590d127a319e1fa3053616ef47e896f3fe5ace8260344f0147f`
- final report manifest:
  `68d4efb5e67d6205080f496554724d22e15a8a1a0787ab52c23f3c54073223b8`
- summary / run status / run log:
  `1af6c06f0696c16fee5ea63ba1d43a0c661e9662fcbf9c7e978f7c147a236270` /
  `19d6b5cc51731d32bd3b19d9c275835d0faa425137bd0894c7279d92a63fb32c` /
  `43320e0e43a41c02a2df92040a62a1d5defcba4d87d01bd4a39c17a65d91c098`

The versioned verifier recomputed:

```text
V934_EXTERNAL_CANDIDATE_VERIFY run=external-redis-candidate-35ddf73f-r2 reports=2 testcase_nodes=3
```

## Exact reports

| Variant | Report | Testcase | XML SHA-256 |
|---|---|---:|---|
| `redis7` | `RedisCrossJvmCacheIT` | 1 | `f71f0f95041e7c14056130d230dc76fd76a3e466277a40eb75a7c53878940d25` |
| `redis7-sqlite` | `QueryCacheLifecycleRealQueryIT` | 2 | `f6317902f8fbd5daf86df0aafb5df3432c091a5149ab17f2407490aad60ac0f5` |

The three JUnit nodes are the cross-process cold-catalog Redis test and the two real-query
L1/L2/catalog-generation lifecycle tests. Each XML carries the exact run id, variant, frozen
`it.test` selector, `skipITs=false` and rerun count `0`.

## Fresh resource and fixture

- image/ref:
  `redis@sha256:3b73847e72874be07e6657b129a94761662b79bc0f679273757d4218573b2a98`
- runtime: Redis `7.4.6`, standalone
- coordinate: dynamic loopback `127.0.0.1:60607`
- storage: one explicit run-labelled named volume mounted at `/data`
- initial state: `DBSIZE=0`
- post-test fixture: four run-owned cross-JVM keys, lifecycle keys `0`, foreign keys `0`
- terminal cleanup: container `0`, volume `0`
- resource / fixture / cleanup SHA:
  `ab433591fe7124528ff43691ccf292168e5926683398b7286e718311d9af92dd` /
  `a46c262b7aa56a3483cd262d2f899620b951347aff0e6ad9b671148b78fc0c28` /
  `4a928224c3713ce9dab636c9d72e150e827e45fa51269d44ea11084fdc3e8d4d`

The runner explicitly cleaned the seven-module Redis reactor graph before execution while
preserving the root `target` evidence tree. Protected source/POM bytes were clean and unchanged.

## Negative and sensitive evidence

The report/candidate tool passed `12/12` exact probes:

- missing, extra and duplicate report;
- wrong testcase count;
- failure, error, skipped and flaky/rerun outcome;
- stale report;
- wrong variant marker;
- cross-run manifest splice;
- raw-report run-context splice.

Negative TSV SHA is
`aa786d08c4830665dca8b9afd226e6ee9ee07318074edce295f8c340e457a8ff`.
Candidate tree self-checks also reject a touched status, missing cleanup and extra file.

Sensitive scanning uses five credential/token patterns. Six synthetic detection probes
(`REDIS_PASSWORD`, quoted JSON password, API key, Bearer, credentialed Redis URI and CLI password)
pass `6/6`; the real candidate scan is clean. Their SHA values are
`693c89af0fd4000beb76e8e41b87ab743fd44a0f23d6d4f0738babf14e721e30` and
`3b65b3a0794c7391438eecb3a86d99041e2ac5a69267ad3935a52e1dca2af9a8`.

## Real signal cleanup

Committed signal group `external-redis-signal-35ddf73f-r1` starts a real fresh Redis resource,
waits until identity/mount verification, then signals the runner:

| Signal | Exit | Durable status | Summary/candidate/FIFO | Container/volume residue |
|---|---:|---|---|---|
| INT | 130 | failed | absent/absent/absent | 0/0 |
| TERM | 143 | failed | absent/absent/absent | 0/0 |
| HUP | 129 | failed | absent/absent/absent | 0/0 |

Signal TSV SHA is
`42f9f37cf48dd0be88354cd0615c227a78038c9dc27033cf48460c5870a75236`.

## Excluded attempts

- The earliest feasibility run implicitly created an anonymous `/data` volume. It and the volume
  were manually removed; it is diagnostic-only.
- Old formal `external-redis-candidate-r1` failed the mount identity check; old `r2` and hardened
  `r3c` were superseded when contract/tool bytes changed.
- Committed `external-redis-candidate-35ddf73f-r1` was interrupted by the outer tool session. It
  closed as `failed/1`, emitted no summary/candidate and left Docker residue `0/0`; it is excluded.

No failed, diagnostic or superseded attempt is spliced into the passing candidate.

## Open boundary

- Redis wrong-image/version, forced dirty-state and forced cleanup-failure resource negatives remain
  open; positive identity/mount and real signal cleanup are complete.
- The bundle is intentionally run-local because report freshness still binds nanosecond mtimes.
  Archive portability remains a Step 5 requirement.
- Mongo/DataViewer `4/30`, MCP/MySQL57 `8/23`, Vector `2/20`, optional LLM disposition and the four
  non-SQLite database cells remain pending.
