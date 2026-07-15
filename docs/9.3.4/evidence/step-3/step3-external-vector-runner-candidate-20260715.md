---
doc_role: execution_evidence
doc_purpose: Record the committed fresh Milvus 2.4 external subset candidate and real signal cleanup probes.
version: 9.3.4
step: 3
status: passed-subset-candidate
authority_scope: external-vector-run-local-subset
created_at: 2026-07-15
commit: dd7d8fc342e3a1e6e41e88342907a522c3919ce4
---

# Step 3 committed Vector external subset candidate

## Decision

Committed run `external-vector-candidate-dd7d8fc3-r1` passes the `external-vector` subset:

```text
1 variant / 2 reports / 20 testcase / F0/E0/S0
candidate artifacts=29
container residue=0
volume residue=0
network residue=0
```

This is a run-local subset candidate. Its final report manifest has `complete=false`; it does not
prove database `29/370`, a unified external `16/76` replay, or Step 3 final `45/446`. Redis,
Mongo, MySQL and Vector are four separate candidates and cannot be cross-run spliced. The external
progress ledger is now `16/76/F0/E0/S0`, but full external authority is still open. Step 3 remains
`in-progress / not passed` and Step 4 is not open.

## Frozen implementation identity

| Artifact | SHA-256 |
|---|---|
| Git commit | `dd7d8fc342e3a1e6e41e88342907a522c3919ce4` |
| external contract | `ac17888c4b242a6447926e10203966a466eb79fb867e2a8918b9825234da08c7` |
| report/candidate tool | `eb89de223d04991632c1b0b4abd529ae90d48a86db6a8e550e241ab2c7b10aae` |
| Vector runner | `ecf54f452ccbe93becd5f5af80ac667059ae74373ee4c0fae2243a41ad9fef23` |
| signal helper | `840bb078dfde16852778c1a1d6b2735ddab5fef5c1c2209a7113e8e901e4da56` |
| signal result TSV | `db24ff6e8a5a17779f84dcd8401af2fb51faf95b36f2e902be282671a5080234` |
| deferred Step 3 inventory | `89190db9370fe3117d5316c72835efffa577d132d39cef9b5d9ae3558afa7601` |

The contract freezes exact `7 variants / 16 required reports / 76 testcase` plus one optional LLM
report. It binds the runner/tool bytes, exact Maven selector, Step 2 inventories, both Vector test
sources and the required production source set.

## Positive run

- run id: `external-vector-candidate-dd7d8fc3-r1`
- started/finished: `2026-07-15T14:28:31Z` / `2026-07-15T14:30:38Z`
- durable status: `passed / exit_code=0 / last_phase=completed`
- source-set logical seal before/after:
  `a5ab33e810c7820f52f88297f072dc5a4a6a06e13c47c7b63a1b77b12e44cb25`
- source TSV file SHA before/after:
  `eba9c987be266798a8c4c395c67aa47a615620c313da4379f762bf5d79730b7c`
- variant bytecode TSV:
  `ba2a0b2aca4c65954daaad3c892ffaec50e039aef655d416e717e3556040ccd8`
- candidate manifest:
  `547c77c62d55f1e234950a8c5bc66811c37be088c75584d7a8ddb5b80decc5f1`
- outer marker:
  `94da1e74b069e966e8a13832ccfc75fe39366484baa24e642d789954a3c1898b`
- final report manifest:
  `167e814a32efcab2ae055042a05b90a3a258065a6c7712ba1c5dc9d8b593fc9a`
- summary / run status / run log:
  `33b68ce4e1bf8edfd530199eb343f609263c6771839f08a4ba6bb99cd967bb5d` /
  `bd0d9468a15f9ad67e53f6b2643548851891361f1eeca9e6ebae6d23dccb5a30` /
  `b9b2dcbf883b76a7ced9970c3635b24d2a9af9d6df9d788dfacb4805deafa221`

The versioned verifier recomputed:

```text
V934_EXTERNAL_CANDIDATE_VERIFY run=external-vector-candidate-dd7d8fc3-r1 reports=2 testcase_nodes=20
```

An independent read-only audit returned `CLEAN / finding=0`. It recomputed all 29 artifact
paths/hashes/sizes/mtimes, parsed both XML files independently, checked audit-time
HEAD/origin/candidate equality and
contract/source/bytecode bindings, revalidated resource and fixture evidence, and confirmed all
candidate plus signal-probe Docker residue as `0/0/0`. The signal runner's designed preclean removes
live module targets, so bytecode review used the immutable candidate seal and its recorded generation
order; the reviewer recorded this as expected behavior, not a finding.

## Exact reports

| Variant | Report | Testcase | XML SHA-256 |
|---|---|---:|---|
| `milvus24-embedding` | `VectorIT` | 15 | `3e63cc9460740581a9d0ae1894d6df395b5e26e0d41a2076cfcde6243e3a30d7` |
| `milvus24-embedding` | `VectorStoreIT` | 5 | `98122fc54ff49a7edddc3d946613dbf0d2f0905e3abca56176cfc861e3d0c6a4` |

Each XML carries the exact run id, variant, frozen selector, `skipITs=false`, zero flaky/rerun
outcomes and a source SHA accepted by the frozen source amendments. The variant manifest SHA is
`cf2e6f64737f15e0f7ff68abe2afb8446bf710c53cb690383f1dbc7ed1795c19`; its tree SHA is
`77c45d24309f1d9ca0edb0cd5a6aa02fa209e3bac52f461fcf412d55223566d8`.

## Fresh resource identity and cleanup

The run creates one bridge network with `internal=false`, exactly three containers and exactly
three run-labelled named volumes:

| Service | Runtime identity | Storage |
|---|---|---|
| Milvus | `v2.4.4` / git `8e7f36d9` / digest `212ec3cd86e35ceda1892adae3bb718278b0e8187b4c797725d98270bc3dcfa7` | `/var/lib/milvus` |
| etcd | `3.5.5` / git `19002cfc6` / digest `89b6debd43502d1088f3e02f39442fd3e951aa52bee846ed601cf4477114b89e` | `/etcd` |
| MinIO | `RELEASE.2023-03-20T20-16-18Z` / commit `05444a0f6af8389b9bb85280fc31337c556d4300` / digest `6d770d7f255cda1f18d841ffc4365cb7e0d237f6af6a15fcdb587480cd7c3b93` | `/data` |

Milvus is exposed only on dynamic loopback coordinates `127.0.0.1:54366` and health port
`127.0.0.1:54367`. Initial collection count is exactly zero. MinIO uses two independently generated
ephemeral root credentials, and the runner verifies they differ without persisting their values in
the evidence bundle. Resource identity SHA is
`69add95cc8279f5be26cbb18300595ab995aba4a9eed1d1e70dad702789e795e`.

Terminal cleanup removes all three containers, all three named volumes and the network. Cleanup
evidence SHA is `fc4169280c6497f252249d5363e383c6f7602f0a15f09eac5aa9bf964ed00bd6`;
residue is exact `0/0/0`.

## Deterministic embedding and fixture governance

`VectorIT` no longer reads a gitignored property file or assumption-skips when an external API key
is absent. It starts a loopback OpenAI-compatible embedding endpoint, verifies request method/path/
authorization/model/input, returns exact eight-dimensional vectors and requires all ten expected
embedding requests. Its temporary collection uses `IVF_FLAT` with `nlist=1` and `COSINE`.
Collection creation, load, insert, search and cleanup use bounded polling; the temporary
`foggy_test_documents` collection must be absent at terminal fixture verification.

`VectorStoreIT` no longer has class-level `@Disabled`. It injects a deterministic local
`EmbeddingModel`, disables remote model providers, isolates JDBC auto-configuration and uses exact
`FLAT/COSINE` semantics with dimension `8`. Its baseline is the five fixed rows `qt1` through `qt5`.
The final strong-consistency query proves:

```text
collections=v934_vector_store
row_count=5
fields=content,embedding,id,metadata
embedding_dimension=8
index_type=FLAT
metric_type=COSINE
foggy_test_documents=absent
```

The five exact ids, contents, `template_type` and `model_name` values match the committed fixture.
Before/after snapshot SHAs are
`f8040ecc074170c3553a7f353a79429d344195fc2b74129e7af2e8383e15f837` and
`fa14fa2d631df2ea291ca6dbbfd8761d4b5c680481fbe27145f2430765eaf9b9`;
fixture evidence SHA is `c6dc38d53a1243b31e5d2c8f95815839cc1086b02a118d02f1ca519a56a4f3c8`.

## Runtime findings closed by the candidate

- MySQL Connector had selected `protobuf-java 3.11.4`, incompatible with
  `milvus-sdk-java 2.5.8`; the Vector module now pins the SDK-compatible `3.24.0` and the dependency
  tree resolves a single version.
- The VectorStore Spring context accidentally activated JDBC `DataSourceAutoConfiguration`; the
  integration test now excludes that unrelated auto-configuration without changing production
  runtime behavior.
- Milvus returns the metadata field as Base64-encoded JSON in the REST query response. The runner
  now performs strict Base64 decode followed by JSON decode before comparing the five exact rows.
- Milvus index inspection now derives the unique embedding index name and supplies it to the
  describe request; empty or ambiguous index identity fails closed.

## Expected-negative and sensitive evidence

All `12/12` report/candidate probes passed, including missing/extra/duplicate/stale reports,
wrong testcase count, F/E/S, flaky outcome, wrong marker, raw-report splice and cross-run manifest
splice. Probe evidence SHA is
`aa786d08c4830665dca8b9afd226e6ee9ee07318074edce295f8c340e457a8ff`.

All `6/6` sensitive negative probes passed. The clean scan covers five credential patterns plus
both exact ephemeral MinIO values; no match is accepted and scan errors fail closed. Sensitive
probe and clean-scan SHAs are
`7c2dd249f5eff48c6bee57ee764038e9273d1706c52e25e136a5ad3c5e3a10df` and
`d4e44dcf40b06c946b8160843efb2715ba2e95d47739fff46ec8c9f83d567e32`.

## Real signal cleanup

The signal group `external-vector-signal-dd7d8fc3-r1` sends real signals after all three containers
and volumes exist. Result TSV SHA is
`db24ff6e8a5a17779f84dcd8401af2fb51faf95b36f2e902be282671a5080234`:

| Signal | Exit | Durable status | Summary/candidate/FIFO | Container/volume/network residue |
|---|---:|---|---|---|
| INT | `130` | failed | absent/absent/absent | `0/0/0` |
| TERM | `143` | failed | absent/absent/absent | `0/0/0` |
| HUP | `129` | failed | absent/absent/absent | `0/0/0` |

## Excluded attempts

The following attempts are diagnostic or failed evidence and are forbidden from the candidate:

| Run | Fail-closed reason | Exit/phase |
|---|---|---|
| `external-vector-candidate-4e39149d-r1` | legitimate network `Internal=false` was treated as a failed command status | `1 / vector-identity` |
| `external-vector-diagnostic-4e39149d-r2` | same identity-gate diagnosis | `1 / vector-identity` |
| `external-vector-diagnostic-4e39149d-r3` | protobuf `3.11.4` caused `GeneratedMessageV3.isStringEmpty` `NoSuchMethodError` | `143 / variant-milvus24-embedding` |
| `external-vector-candidate-a6c1dd86-r1` | unrelated JDBC auto-configuration required a DataSource driver | `1 / variant-milvus24-embedding` |
| `external-vector-candidate-4874cc27-r1` | fixture gate exposed metadata as an encoded string, not an object | `5 / vector-fixture` |
| `external-vector-candidate-35f0e78b-r1` | fixture gate exposed the string as Base64 JSON | `5 / vector-fixture` |

Every excluded run has durable `failed` status, no candidate manifest and cleanup residue `0/0/0`.
Only `external-vector-candidate-dd7d8fc3-r1` enters the progress ledger.

## Open boundary

The four committed external candidates close the selector ledger as Redis `2/3`, Mongo `4/30`,
MySQL `8/23` and Vector `2/20`, exact total `16/76/F0/E0/S0`. All four final manifests have
`complete=false`; a shared outer orchestrator must replay and merge the exact set in one run before
external authority can pass.

Step 3 also still requires four non-SQLite database cells `24/320`, unavailable/wrong-image-or-
version/dirty-state/fixture-mutation/forced-cleanup-failure negatives, optional LLM reviewed
disposition and Addon lifecycle evidence. Until the exact `45/446/F0/E0/S0` exit is independently
recomputed, Step 3 remains open and no Step 4 coverage evidence may be inferred.
