---
doc_role: execution_evidence
doc_purpose: Record the committed fresh MySQL57 external subset candidate and real signal cleanup probes.
version: 9.3.4
step: 3
status: passed-subset-candidate
authority_scope: external-mysql-run-local-subset
created_at: 2026-07-15
commit: 97f1cbfa7f412fabc6f0ef96d9182c9340a51fbd
---

# Step 3 committed MySQL57 external subset candidate

## Decision

Committed run `external-mysql-candidate-97f1cbfa-r2` passes the `external-mysql` subset:

```text
3 variants / 8 reports / 23 testcase / F0/E0/S0
candidate artifacts=122
direct cases=23/23
container residue=0
volume residue=0
```

This is a run-local subset candidate. Its final report manifest has `complete=false`; it does not
prove Vector `2/20`, complete external `16/76`, database `29/370`, or Step 3 final `45/446`.
Redis, Mongo and MySQL are three separate candidates and cannot be cross-run spliced. Step 3
remains `in-progress` and Step 4 is not open.

## Frozen implementation identity

| Artifact | SHA-256 |
|---|---|
| Git commit | `97f1cbfa7f412fabc6f0ef96d9182c9340a51fbd` |
| external contract | `429a9a15ee0cd2d244e619e2ea98fe5a1ab63d2525291a0d56ae18d8d317155d` |
| report/candidate tool | `0df48d61a36f6aad61aeb92deef2756ebd23cbf60e716578f7bf123f608405d2` |
| MySQL runner | `4c4bb615d7cccee45f97a037db90c1ffe29867dc972b8a744f8de1ce14b4d468` |
| signal helper | `4190124b5f088dd9cbea6f18e7b29d92ef1a28653bdd908d97c1553d0c2c45b8` |
| signal result TSV | `3d8ef175d0eef9de01e7e88150b25526a2974596e96ba2d37f2e1b42d2daec30` |
| deferred Step 3 inventory | `89190db9370fe3117d5316c72835efffa577d132d39cef9b5d9ae3558afa7601` |

The contract freezes exact `7 variants / 16 required reports / 76 testcase` plus one optional LLM
report. It binds the runner/tool bytes, exact Maven selectors, Step 2 inventories, MySQL test-source
amendments and the curated bundle source set.

## Positive run

- run id: `external-mysql-candidate-97f1cbfa-r2`
- started/finished: `2026-07-15T13:14:11Z` / `2026-07-15T13:18:35Z`
- durable status: `passed / exit_code=0 / last_phase=completed`
- source-set logical seal before/after:
  `2c7bbe36d2dfa5af60ffc872426c35d47505fa2b15bea46d20d5f12b0a43f11f`
- source TSV file SHA before/after:
  `191e7c69c4673e648369989631f0dce03f45e140abccf43f0a578a4c21f56eb1`
- all three variant bytecode seals:
  `67069277c3084d460abc34b26d04643f627d25fcbebdec624d34799e69f314ab`
- candidate manifest:
  `e9ceed524ce897f58139e07dca442e3a52f04e984e94cda86c8e70890f735bfe`
- outer marker:
  `509f1c3902b379559b857a45a2df85f428b82d83b74fc186c176f18863af23c0`
- final report manifest:
  `c9e11374589eb4ac39257852eb02f5ccd86550c26df5401d8b49e781df98385b`
- summary / run status / run log:
  `f561cfd61d4621b876e81c83def11a68ed24e85a9fd92745a2a12654d32fa6cb` /
  `a2573b1b8134ccd1f327568d1b9941f458800a750881b3034e3ae22efc0065be` /
  `ad23f70a40807a41308d9854e62e2da357350ad3e62b65666a89cfef7dad35ea`

The versioned verifier and an independent read-only audit both recomputed:

```text
V934_EXTERNAL_CANDIDATE_VERIFY run=external-mysql-candidate-97f1cbfa-r2 reports=8 testcase_nodes=23
```

The independent audit also recomputed all 122 artifact hashes/sizes/mtimes, exact XML totals,
HEAD/contract bindings, direct report, resource cleanup and the repository Step 3 checksum set;
finding count was zero.

## Exact reports

| Variant | Report | Testcase | XML SHA-256 |
|---|---|---:|---|
| `mysql57-mcp` | `McpToolsIT` | 2 | `3439f1720ceca2def3a74ff529b81ad49ecd5e5b0d210f474523afb6e44e57c2` |
| `mysql57-mcp` | `McpToolsIT$DescriptionModelToolIntegrationTest` | 2 | `b9ed319f5c7e992224949e148519e1dd477adbf325e74e40f51d96a0970db373` |
| `mysql57-mcp` | `McpToolsIT$EndToEndScenarioTest` | 2 | `c2f6c94173189439292b6979891b64be1fe4b530cb353b3a6b0d7fde17daa949` |
| `mysql57-mcp` | `McpToolsIT$MetadataToolIntegrationTest` | 2 | `fcec13670cb73e4932b4ee37a0994daa030241902a6b12948b7c83d136200227` |
| `mysql57-mcp` | `McpToolsIT$QueryModelToolIntegrationTest` | 6 | `4eeb408e506418cca4e99748b91de2a00ae7e5cc0d15654937d97aa7cf5bc453` |
| `mysql57-direct` | `AiToolsIT` | 3 | `f84281a312831c69e75ba91e75feb0a9cc5d1093001e422850fa555040e5f505` |
| `mysql57-direct` | `AiToolsIT$DirectToolCallTest` | 4 | `b472cb7db89a09ed56a1f112149fa82650cd0a86cfa26730a239f61f28757e54` |
| `mysql57-compose` | `ComposeScriptToolIT` | 2 | `f169bdb64a614e6b47e3848f253c953ead0df0f9e6a8532a88c2f032e35db149` |

Each XML carries the exact run id, variant, frozen selector, `skipITs=false`, zero flaky/rerun
outcomes and a source SHA accepted by the frozen source amendments. Variant totals are exactly
`5/14`, `2/7` and `1/2`, all with `F0/E0/S0`.

## Fresh resource, fixture and least privilege

- image/ref:
  `mysql@sha256:4bc6bc963e6d8443453676cae56536f4b8156d78bae03c0145cbe47c2aad73bb`
- runtime: MySQL `5.7.44-log`, `mysqld`, InnoDB, `utf8mb4_unicode_ci`,
  `lower_case_table_names=1`, time zone `+08:00`
- coordinate: dynamic loopback `127.0.0.1:55500`
- storage: one explicit run-labelled named volume mounted at `/var/lib/mysql`
- initial state: current schema tables `0`, foreign databases `0`
- credentials: independently generated ephemeral root/app secrets; exact-secret scan covers both
- application grant: global `USAGE:NO`; exactly one escaped current-schema `SELECT:NO` row;
  table/column/routine/proxy grant rows all `0`
- terminal cleanup: container `0`, volume `0`

Initialization uses ten exact scripts in one fixed session with explicit commit, epoch
`1710864000`, `RAND` seeds `934/934` and time zone `+08:00`. Before/after identity is exact:

```text
tables=69 / primary-key tables=69
table-set SHA-256=6c3356917e89c46c5e37851226a40b3d28e07f1db02bb2fe5fbecec8183591b7
dim_date=1461 / dim_product=500 / dim_customer=1000 / dim_store=50
fact_sales=3088 / fact_order=20005 / fact_return=316 / compose_join=10
foreign databases=0
mysqldump data SHA-256=c8edcd273ed2b0f9383330c7546521515ce729b078d5614995cd05752123ec8f
```

Resource / fixture snapshot / grant / cleanup SHA values are
`66baa2871a1fd0be5a8fade40fd5ebb46f7d69738eaa563c45f01cd10357dd67` /
`ac77d977db183967c9a7c70e83454d21b4f86443db174f94a1d1b216227e6d06` /
`ae360368f8e57eccf437e05b5e37e9c163875ee04415ff09d7920c9c24e3cae5` /
`f82c0f62c499c81cde24d19427587c1f44bb9b932012c33a435719870916d1ea`.
Fixture and grant evidence are byte-identical before and after all three variants.

## Curated catalog and direct execution

The run-owned ecommerce bundle is exact `59 files / 32 QM / 25 TM / 2 fsscript`; it excludes only
`demo/**`, rejects symlinks and binds every source SHA/size. Bundle and init manifests are
`38bfae0d70935b675fdc137167b840b12927d430a3a2125b459df05018c24570` and
`f143a0e41341739969e13d2eb1848d8f5c8f6070ad39b84f819506746ba0cbae`.

The required runner forces dynamic discovery with `use-all-models=true`. Existing MCP metadata
nodes require the public catalog to equal the exact 32-QM set and reject the two demo permission
models plus Vector leakage. The direct structured report is
`cac8d1fdc3073593bae6d535bdcaaf2051471c34b3a128571e9a770a3e5d0433`:

```text
resultCount=23 / passedCount=23 / failedCount=0
toolBusinessErrorCaseCount=0 / warningCaseCount=0
```

`META-001` therefore proves cold catalog assembly and both required model signatures. The required
method/nested selector excludes optional `AiModelCallTest`; no optional LLM report is present.

## Negative and sensitive evidence

The report/candidate tool passed `12/12` exact missing/extra/duplicate/count/outcome/flaky/stale/
marker/cross-run/raw-splice probes. Negative TSV SHA is
`aa786d08c4830665dca8b9afd226e6ee9ee07318074edce295f8c340e457a8ff`.
Test assertions reject empty data, wrong schema/plan and business-error responses; the candidate
verifier rejects the resulting nonzero XML outcomes and independently requires the direct report
to be exact `23/23` with no business errors or warnings.

Sensitive scanning uses five credential/token patterns plus exact matching of the two ephemeral
secrets. Six synthetic detection probes cover MySQL env, quoted JSON password, API key,
authorization header, credentialed MySQL URI and CLI password; all pass. Probe and clean-scan SHA
values are `dd71757e01a97e761855cbdebb986384389d69dbb623223be27bb3c3d2a0734b` and
`d4e44dcf40b06c946b8160843efb2715ba2e95d47739fff46ec8c9f83d567e32`.

## Real signal cleanup

Committed signal group `external-mysql-signal-97f1cbfa-r2` starts a real fresh MySQL resource,
waits until runtime/mount identity is verified, then signals the runner:

| Signal | Exit | Durable status | Summary/candidate/FIFO | Container/volume residue |
|---|---:|---|---|---|
| INT | 130 | failed | absent/absent/absent | 0/0 |
| TERM | 143 | failed | absent/absent/absent | 0/0 |
| HUP | 129 | failed | absent/absent/absent | 0/0 |

Signal TSV SHA is
`3d8ef175d0eef9de01e7e88150b25526a2974596e96ba2d37f2e1b42d2daec30`.

## Excluded attempts

- Early development attempts exposed direct `META-001` catalog assembly and MCP/Compose
  assertion false greens; each is diagnostic-only, failed durably and emitted no candidate.
- `external-mysql-candidate-664c8f21-r1` stopped at `mysql-read-only-grants` because MySQL 5.7
  lacks `information_schema.ROUTINE_PRIVILEGES`. It closed as `failed/1`, emitted no summary/final/
  candidate, cleaned container/volume to `0/0`, and is excluded. Commit `97f1cbfa` queries the
  MySQL 5.7-compatible `mysql.procs_priv` surface without weakening the zero-routine-grant gate.
- Signal evidence produced at older commits is superseded; only the `97f1cbfa-r2` signal group is
  bound to this candidate implementation.

No failed, diagnostic or superseded attempt is spliced into the passing candidate.

## Open boundary

- Wrong-image/version, forced dirty-state and forced cleanup-failure resource negatives remain
  open; positive identity/mount, before/after fixture/grant equality and real signal cleanup pass.
- The bundle is intentionally run-local because report freshness binds nanosecond mtimes. Archive
  portability remains a Step 5 requirement.
- Committed Redis + Mongo + MySQL subsets account for `14 reports / 56 testcase`, but their three
  `complete=false` manifests are only a progress ledger. Vector `2/20`, optional LLM disposition,
  four non-SQLite database cells, DB/resource-state negatives and Addon lifecycle remain pending.
