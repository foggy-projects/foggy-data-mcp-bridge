---
doc_role: execution_evidence
doc_purpose: Record the committed fresh Mongo/DataViewer external subset candidate and real signal cleanup probes.
version: 9.3.4
step: 3
status: passed-subset-candidate
authority_scope: external-mongo-run-local-subset
created_at: 2026-07-15
commit: ccb29f476e0a7d6040f52e4192fe54b68aac5aa0
---

# Step 3 committed Mongo/DataViewer external subset candidate

## Decision

Committed run `external-mongo-candidate-ccb29f47-r1` passes the `external-mongo` subset:

```text
1 variant / 4 reports / 30 testcase / F0/E0/S0
candidate artifacts=31
container residue=0
volume residue=0
```

This is a run-local subset candidate. Its final report manifest has `complete=false`; it does not
prove the remaining external `10/43`, the complete external `16/76`, the database `29/370`, or the
Step 3 final `45/446`. Step 3 remains `in-progress` and Step 4 is not open.

## Frozen implementation identity

| Artifact | SHA-256 |
|---|---|
| Git commit | `ccb29f476e0a7d6040f52e4192fe54b68aac5aa0` |
| external contract | `a4618eba8f89286e62325fc9fff7a5008c46e2b5789b41b986850125272de314` |
| report/candidate tool | `b9779c5bffa054a55ae00e716383a679d3cc502d2d56eb31d327a75695c25f45` |
| Mongo runner | `6bcd2b4f2a08348a3390d19ec3ab6661afb5d56d3ef5ab3a60de0b555637e573` |
| signal probe | `97d66bd8794d9b2f3b9378dbf9802ba5116089224283b7918f1e89ef8dbf2dac` |
| deferred Step 3 inventory | `89190db9370fe3117d5316c72835efffa577d132d39cef9b5d9ae3558afa7601` |

The contract freezes exact `7 variants / 16 required reports / 76 testcase` plus one optional LLM
report. It binds the runner/tool/helper bytes, exact Maven selectors, Step 2 inventories, the
direct-tool fail-closed amendment and the DataViewer teardown source amendment.

## Positive run

- run id: `external-mongo-candidate-ccb29f47-r1`
- started/finished: `2026-07-15T10:54:35Z` / `2026-07-15T10:56:24Z`
- durable status: `passed / exit_code=0 / last_phase=completed`
- source-set logical seal before/after:
  `b81593d2b4f7707b1c2b29e45bd185f22faea427cd4d9663d2c84f93fa4f3baa`
- source TSV file SHA before/after:
  `9dfc106d3d202a3b2bf8854900d0a330fa8c9c58248bc20ab8fa251621104377`
- Mongo variant bytecode seal:
  `ee2c83017e47bc6f16fa7c8c153fc671a9e5a6495be748871fa8b49290de1bd3`
- candidate manifest:
  `d9423bf622eaa17c11b75365d2262bd1213b83074b6f02adaa1ed4fbf898e197`
- outer marker:
  `050492c364e8b8421173bcec567dae616d397ae5a85ef62b0c8a217c473c1b93`
- final report manifest:
  `df05cd67e808902d8c1c276535bf80d893cbbe7cad8f915ed8b796a40f320438`
- summary / run status / run log:
  `d2926ee67a9525e57e89022f586d23dc1913d11b1df4f0839bf4172b91bf54a5` /
  `20b5923498672e49210f4cfd9b345d79823349d4295b5ad5a9151de06bfc5013` /
  `6769ad2c70092463a9246b51c1ace0a2cd88adbf6cc6a1d16f5a40161c5fc52c`

The versioned verifier independently recomputed:

```text
V934_EXTERNAL_CANDIDATE_VERIFY run=external-mongo-candidate-ccb29f47-r1 reports=4 testcase_nodes=30
```

## Exact reports

| Owner | Report | Testcase | XML SHA-256 |
|---|---|---:|---|
| model-mongo | `McpAuditLogMongoIT` | 12 | `2897a6e76cc78ad4556f1a78c6c8d7ddfa78ec092dc28a6395cc676d70a2b51c` |
| model-mongo | `MongoCalculatedFieldIT` | 9 | `f6be54cce387ac59a362633b0e05aa16450005412f2c7d954edaaf455276f37a` |
| model-mongo | `MongoArrayElementAccessIT` | 6 | `6f8f643372ab7986e814aac897a6bf1cc4902af4ca93d4bad091f13ddd15f123` |
| data-viewer | `MongoListPresetStoreIT` | 3 | `0efccf9c75f45bb6444c8954e70d533585425a22719226d578c647e359f6a82a` |

Each XML carries the exact run id, `mongo6` variant, frozen selector, `skipITs=false`, zero reruns
and a source SHA accepted by the frozen source amendment.

## Fresh resource and fixture

- image/ref:
  `mongo@sha256:03cda579c8caad6573cb98c2b3d5ff5ead452a6450561129b89595b4b9c18de2`
- runtime: MongoDB `6.0.27`, gitVersion
  `fc88ca137231d7457aed6265d4f32a361ae71716`, standalone, WiredTiger, auth disabled
- coordinate: dynamic loopback `127.0.0.1:64448`
- storage: two explicit run-labelled named volumes mounted at `/data/db` and `/data/configdb`
- initial state: model/viewer collection counts `0/0`, foreign database count `0`
- post-test model fixture: `mcp_tool_audit_log=25`, `sales_order_test=20`,
  `geo_station_test=0`; exact collection set has two retained collections
- post-test viewer fixture: exact `list_presets` collection with `0` documents
- terminal cleanup: user databases `0`, container `0`, volumes `0`
- resource / fixture / cleanup SHA:
  `897260208842584abfee8a937cf18c7b13b98e0daabcba85f29ef26c92131c20` /
  `c4a6c56fda188a394184b8761ebdb23d384088d6126897ee8e7f2bc51ad7f000` /
  `d05afcd4bb6ce8a5b441c93e0e253ffa4a057b7c08ecb697aadeab3c4226becd`

The runner cleaned the nine-module Mongo reactor graph while preserving the root evidence tree.
Protected Mongo/DataViewer source and POM bytes were clean and unchanged. A run-local SQLite
datasource plus `dual` initialization only supplies the loader dialect path; this candidate does
not claim that the production Mongo loader is decoupled from JDBC metadata.

## Negative and sensitive evidence

The report/candidate tool passed `12/12` exact report, outcome, freshness, marker and cross-run
splice probes. Negative TSV SHA is
`aa786d08c4830665dca8b9afd226e6ee9ee07318074edce295f8c340e457a8ff`.

A local disposable-copy self-check rejected three isolated candidate-tree copies with
`E_CANDIDATE`; the copies were deleted and are not listed as durable candidate artifacts:

- touched `run-status.env`: artifact identity differs;
- missing `cells/mongo6/cleanup.env`: artifact missing;
- extra file: candidate run-root file set differs.

Sensitive scanning uses five credential/token patterns. Six one-character detection fixtures
cover Mongo env, quoted JSON password, API key, authorization header, credentialed Mongo URI and
CLI password; all pass. Durable probe evidence uses the non-secret `auth-header` label and the full
run-owned tree is clean. Their SHA values are
`681594f98a7db7e7012b1d1686d6ae90ed998dd088824870d7ea241ad1290f3f` and
`3b65b3a0794c7391438eecb3a86d99041e2ac5a69267ad3935a52e1dca2af9a8`.

## Real signal cleanup

Committed signal group `external-mongo-signal-ccb29f47-r1` starts a real fresh Mongo resource,
waits until both named volumes and runtime identity are verified, then signals the runner:

| Signal | Exit | Durable status | Summary/candidate/FIFO | Container/volume residue |
|---|---:|---|---|---|
| INT | 130 | failed | absent/absent/absent | 0/0 |
| TERM | 143 | failed | absent/absent/absent | 0/0 |
| HUP | 129 | failed | absent/absent/absent | 0/0 |

Signal TSV SHA is
`33a7dde6f06193d402e82bb8bea7a221035a46b98b905322466de1ad3426da04`.

## Excluded attempts

- `external-mongo-dev-r1` proved the loader's hidden JDBC dialect dependency with 21 errors;
  `external-mongo-dev-r2` then failed because the Mongo module lacked the SQLite driver.
- `external-mongo-candidate-e05e1f3b-r1` reached SQLite but failed because `dual` was absent.
- `external-mongo-candidate-142f4360-r2` produced green XML but left 3 DataViewer documents; the
  fixture gate rejected it, leading to `@AfterEach` teardown instead of order-dependent acceptance.
- `external-mongo-candidate-ef0dae38-r1` rejected sensitive-probe self-match;
  `external-mongo-candidate-5b95f6f9-r1` rejected verifier label drift.

Every excluded run has failed durable status, no candidate manifest and zero Docker residue. No
failed, diagnostic or superseded attempt is spliced into the passing candidate.

## Open boundary

- Wrong-image/version, forced dirty-state and forced cleanup-failure resource negatives remain
  open; positive identity/mount and real signal cleanup are complete.
- The bundle is intentionally run-local because report freshness binds nanosecond mtimes. Archive
  portability remains a Step 5 requirement.
- MCP/MySQL57 `8/23`, Vector `2/20`, optional LLM disposition and the four non-SQLite database cells
  remain pending. Redis and Mongo candidates cannot be cross-run spliced into full authority.
