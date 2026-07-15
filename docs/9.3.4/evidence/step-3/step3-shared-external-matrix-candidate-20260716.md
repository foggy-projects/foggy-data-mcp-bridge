---
doc_role: execution_evidence
doc_purpose: Record the committed single-outer required external matrix candidate and repaired parent-child signal cleanup evidence.
version: 9.3.4
step: 3
status: passed-external-subset
authority_scope: required-external-single-outer
created_at: 2026-07-16
commit: 47d1afd7fb59f1cc6beab3ba68d0b7dd4589b6ab
---

# Step 3 Shared External Matrix Candidate

## Decision

Committed run `external-matrix-candidate-47d1afd7-r1` passes the complete required external
subset under one outer marker, HEAD, contract and authority lock:

```text
complete=true
7 variants / 16 reports / 76 testcase / F0/E0/S0
candidate artifacts=273
container/volume/network residue=0/0/0
```

The repaired signal group `external-matrix-signal-47d1afd7-r1` also passes real
INT/TERM/HUP parent-child durable cleanup. Candidate audit=`CLEAN`、signal audit=`CLEAN`、
implementation quality gate=`PASS`，P0/P1=`0`。

This closes only the required external `16/76` single-outer replay. Database remaining
`24/320`、DB/resource-state negatives、optional LLM disposition、Addon lifecycle and the final
Step 3 union `45/446` remain open. Step 3 is still `in-progress / not passed`; Step 4 is not open.

## Frozen identity

| Artifact | SHA-256 / identity |
|---|---|
| Git commit | `47d1afd7fb59f1cc6beab3ba68d0b7dd4589b6ab` |
| external contract | `c4873bcd9d1f2896b677a3c49b9518df4ad907f6c7dd89077ed81f30fc028d91` |
| outer runner | `4a46a1def187ed195709e4c8f9ead07c32da20ee1693f383f86a91a0c08b392f` |
| lane launcher | `8c8bdd25e462c6f70d9b5731dd1e7eb1c8fab7a23edf3591514986e140a498c4` |
| report/candidate tool | `e54fe202dc2f3d72b43c1189dac2661e866635aaf1bc25e649915f5af6da03be` |
| shared context helper | `0674e905c019bd84af02c557f16f48e6b32cb3e9abebe5a8d8f9585f8b434875` |
| signal helper | `cf042bc137aab190c829f9537b1b90d32b9ceb2adedf53eab5d1171994006850` |
| deferred Step 3 inventory | `89190db9370fe3117d5316c72835efffa577d132d39cef9b5d9ae3558afa7601` |
| outer marker | `b9620eff5bbdbc870a1646cb35b98592b791067371024d6c0b209b276f044a60` |
| final report manifest | `3c767b6679ac1011e5fcb829216ed60dadc12a1a846fba0189b01cd557703260` |
| candidate manifest | `8eec444db529981999d734871f16180aaabfcccd021a56657cc0b38938ef2216` |
| signal result TSV | `1a7b5b0a00965e2ce028175377ff16c73dc8e411d1025d6634f326763b1a638c` |

The contract binds 13 inventory/framework inputs. Independent review recomputed every binding
against the current file bytes and found no drift.

## Positive execution

- run id: `external-matrix-candidate-47d1afd7-r1`
- started/finished: `2026-07-15T15:50:21Z` / `2026-07-15T16:01:59Z`
- durable status: `passed / exit_code=0 / last_phase=completed`
- summary SHA: `3a3f30f84da369a50f68c0c4af9dfbb0bc95ec07ef04c6a866dc72de757c2fa8`
- run-status SHA: `a17066d61bf0ab80c2034c124d89ffa9f73f48e7d7177736515baa49a37f863f`
- source logical seal before/after:
  `d19a27991e5bba89515b75fa48f170d26257604859a87ccb49de8ff1ce7c95ea`
- source TSV file SHA before/after:
  `485fdd76d01f2d63fe542603940418c72fbe18ef50e29b6ce8120d78bbd79995`

| Lane / variants | Reports | Testcase | Nested candidate SHA-256 |
|---|---:|---:|---|
| Redis: `redis7`, `redis7-sqlite` | 2 | 3 | `7476281096a644e934183f99830f3f27020218b2085e2dc44f436678feccaab5` |
| Mongo: `mongo6` | 4 | 30 | `99d0ac0116f70b510b5475deb35912265676918df1290df7928042b24bbf89d8` |
| MySQL: `mysql57-mcp`, `mysql57-direct`, `mysql57-compose` | 8 | 23 | `109cbd5cc8abeb30b4fefead6ffcf5e3c24632877bacbc305df427eebcf19d8e` |
| Vector: `milvus24-embedding` | 2 | 20 | `01840c37557f298cfa6abe6e15df65f163dcee139da62b346f2df980821c5787` |
| Total | 16 | 76 | `complete=true / F0/E0/S0` |

The outer verifier revalidated four nested candidates, seven variant manifests and the final
manifest. Independent XML parsing reproduced all 16 reports and 76 testcase nodes with zero
failure, error, skip, flaky or rerun outcomes. MySQL direct structured evidence separately proves
`23/23 passed / failed=0 / tool_business_errors=0`; the optional LLM report did not execute.

## Artifact, source and resource integrity

The top-level candidate binds exactly 273 artifacts. With `candidate-manifest.json`, the run tree
contains 274 regular files and no symlink or special file. Independent review recomputed every
path, SHA, size and mtime with mismatch=`0`.

The top-level source manifest contains 1544 protected entries. Its before/after bytes match, and
all four lane-specific source/bytecode seals also match their current protected inputs. Existing
user-owned PreAgg changes are outside this protected external set and were neither staged nor
modified by this work.

| Evidence | SHA-256 | Result |
|---|---|---|
| aggregate resources | `a9af4efdb7b2a927894b8032f115174be3c5467f0efe24bee726dce8052aacbe` | four lane rows verified |
| aggregate fixtures | `d6063f8d535b3cf0bff584698eaf35a5e8393ff169c4fe23a339cc7bfdcefea5` | four lane rows verified |
| aggregate cleanup | `087d681d517d9b6bdf9eff2cf504cb1672e53f9673817a581f24144511840a17` | `0/0/0`, lane_count=4 |
| report negatives | `aa786d08c4830665dca8b9afd226e6ee9ee07318074edce295f8c340e457a8ff` | `12/12` |
| sensitive negatives | `f0a3e7f954d00bc4311919223fd6184aaf5239e74b427da17b43088abf09bb6d` | `24/24` |
| sensitive clean scan | `d943f4e84760876ddbdd3ca1d2dd156f23e85de657b2a5056ae99c743921b28d` | passed |

Audit-time Docker checks covered six exact container names, seven volume names, one network name
and the shared run label; all current container/volume/network residue is `0/0/0`.

## Signal regression and fix

The first committed shared candidate `external-matrix-candidate-18498e4d-r1` produced a valid
positive `7/16/76`, but the following real INT probe timed out. The parent eventually wrote
`130/failed` and removed all resources, while the Redis child had no durable status or cleanup.

Root cause: non-job-control Bash starts an asynchronous `setsid ... &` child with SIGINT ignored.
That disposition crosses `setsid/env` into the child Bash and cannot be restored by a Bash trap.
The child therefore remained in its ready loop until the parent killed it at the old 45-second
grace boundary. This generation is superseded and current verification rejects it under the new
contract.

The fix uses a constrained Python exec launcher that:

- accepts only the four frozen lane runner/environment pairs;
- requires the argument FD to equal exported `V934_AUTHORITY_LOCK_FD`;
- proves the FD is open, inheritable and owns the same flock open-file-description;
- resets INT/TERM/HUP plus Python-ignored exec signals before `execve`;
- creates and asserts `PID=SID=PGID`, preserving the PID for parent negative-PGID signalling;
- separates the parent graceful budget (`20s`) from matrix probe finalization (`60s`).

## Real parent-child signals

Signal result TSV SHA is
`1a7b5b0a00965e2ce028175377ff16c73dc8e411d1025d6634f326763b1a638c`:

| Signal | Parent / child exit | Parent / child phase | Child cleanup | Outer cleanup | Success artifacts/FIFO |
|---|---|---|---|---|---|
| INT | `130 / 130` | `lane-external-redis / signal-probe-ready` | `0/0 passed` | `0/0/0 passed` | absent |
| TERM | `143 / 143` | `lane-external-redis / signal-probe-ready` | `0/0 passed` | `0/0/0 passed` | absent |
| HUP | `129 / 129` | `lane-external-redis / signal-probe-ready` | `0/0 passed` | `0/0/0 passed` | absent |

All six durable statuses are `failed`, as required for interrupted runs. Parent/child context bytes
match per run, source seals remain valid, no run tree retains a FIFO/symlink/socket, and live Docker
residue is zero.

## Independent review

- candidate audit: `CLEAN`; outer/final/four nested candidates/seven manifests all verified,
  independent XML total=`16/76/F0/E0/S0`, 273-artifact exact set mismatch=`0`;
- signal audit: `CLEAN`; parent/child exit, phase, cleanup, context, source and live residue all
  independently matched for INT/TERM/HUP;
- implementation quality: `PASS` for the shared external signal-fix sub-scope, new P0/P1=`0`;
  the earlier child-INT P1 is closed.

The only operational action after these reviews is to commit the version-document updates and push
the two local implementation commits plus the documentation commit to `origin/main`.

## Open boundary

This candidate is the complete required external subset, not the complete Step 3 authority. Step 3
still requires the four non-SQLite database cells `24/320`, unavailable/wrong-image-or-version/
dirty-state/fixture-mutation/forced-cleanup negatives, optional LLM reviewed disposition, Addon
lifecycle evidence and exact database+external union `45/446/F0/E0/S0`. No coverage or Step 4 result
is inferred from this correctness candidate.
