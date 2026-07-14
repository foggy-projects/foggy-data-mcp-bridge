---
doc_role: batch_regression_exit_evidence
doc_purpose: Record the replacement Batch 7 authority after quality findings were repaired and independently audited.
version: 9.3.3
batch: 7
status: passed
decision: passed-to-ordered-post-gates
batch_status: completed
version_status_at_run: in-progress
version_status: signed-off
post_gates_status: completed
acceptance_decision: accepted-with-risks
acceptance_record: ../../acceptance/version-signoff.md
authoritative_run: 20260714T084351Z-3271604
supersedes: 20260714T074009Z-3153871
independent_review: no-blocker
created_at: 2026-07-14
updated_at: 2026-07-14
---

# Batch 7 Replacement Compatibility and Regression Exit

## Decision

Batch 7 replacement authority passed and, at run seal time, was allowed to enter
the ordered post-gates. Those gates have since completed；the immutable run is:

```text
target/v933-batch7-regression/runs/20260714T084351Z-3271604/
```

It contains `3824` asserted testcases in `519` fresh reports, with
failures/errors/skipped=`0/0/3`. The three skips are the exact reviewed SQLite
allowlist; every other lane has zero skip. The earlier run
`20260714T074009Z-3153871` remains sealed but superseded and contributes no
final authority.

This run record closes regression replay；it does not replace the post-gate
records. Execution self-check、formal implementation quality、coverage audit and
version acceptance subsequently completed in that order, ending in
`signed-off / accepted-with-risks` at
`docs/9.3.3/acceptance/version-signoff.md`.

## Authoritative Command

```bash
V933_BATCH7_SQLITE_EXPECTED_TESTS=3449 \
  scripts/verify-v933-batch7-regression.sh
```

The runner froze the SQLite count/report/skip inventory, rejected external test
skip flags, held a repository aggregate lock, executed all lanes in series and
updated `latest-run-id` only after final integrity passed.

## Lane Ledger

| # | Lane | Tests | Reports | F/E/S |
|---:|---|---:|---:|---:|
| 01 | root compile | 0 | 0 | 0/0/0 |
| 02 | Runtime API compatibility | 62 | 6 | 0/0/0 |
| 03 | watcher/source management | 36 | 4 | 0/0/0 |
| 04 | binding publication/retire | 16 | 2 | 0/0/0 |
| 05 | REAL-QUERY child | 11 | 6 | 0/0/0 |
| 06 | SQLite full | 3449 | 470 | 0/0/3 |
| 07 | MySQL 5.7 | 18 | 1 | 0/0/0 |
| 08 | PostgreSQL 15 | 18 | 1 | 0/0/0 |
| 09 | SQL Server 2022 | 18 | 1 | 0/0/0 |
| 10 | 9.3.1 isolation regression | 132 | 13 | 0/0/0 |
| 11 | 9.3.2 auto-config/Launcher | 64 | 15 | 0/0/0 |
| 12 | root package/artifacts | 0 | 0 | 0/0/0 |
| **total** |  | **3824** | **519** | **0/0/3** |

The frozen arithmetic is SQLite `3449/470` plus non-SQLite `375/49`.
Compile and package are build evidence and do not inflate test counts.

## Quality-finding Closure

- Runtime diagnostics: `RuntimeLifecycleSanitizerTest` adds four direct cases;
  Map and Collection share depth `5` and width `100`, including a 20,000-level
  hostile List. API authority is now `62/6`.
- watcher registration: target root is registered before scanning; an
  eight-attempt fixed-point discards any snapshot that discovered an
  unregistered child and accepts only a later stable scan. A second-window
  deterministic test proves a source created after reconciliation discovery
  but before child registration is committed exactly once.
- watcher authority loss: OVERFLOW, invalid WatchKey, watched-directory delete,
  file-watch registration failure and reconciliation exhaustion produce an
  additive loss signal and unknown-scope fail-closed commit. Repeated loss is
  idempotent and a child loss preserves the valid shared root.
- focused pre-replay closure: core `11`, fsscript authority `4`, lifecycle `5`,
  management `16`, sanitizer `4`; all failures/errors/skips are zero.
- independent implementation re-review: blocker/high/medium=`0/0/0`; two low
  follow-ups remain for an explicit eight-round churn test and 9.3.5 large-class
  decomposition.

## SQLite, REAL-QUERY and Databases

SQLite passed `3449` tests in `470` reports. Its exact skip allowlist is:

1. `CalculateMvpIntegrationTest#calculateFailsClosedForRuntimeUnsupportedDatabase`
2. `PivotCascadeGenerateSqlParityIntegrationTest#testMysql57RowsCascadeFailsClosedWithoutMemoryFallback`
3. `JavaQueryModelAggregateJoinSnapshotTest#shouldProduceSnapshot`

The nested REAL-QUERY run
`20260714T084351Z-3271604-05-real-query` passed `11/6/F0/E0/S0`: model lifecycle
`4`, SQLite/MySQL 5.7/PostgreSQL 15 native parity `3`, and Caffeine/Redis
lifecycle `4`. Redis 7.4.6 began and ended with zero keys and its run-owned
container was removed.

| Kind | Version | Catalog/schema | fact_sales | dim_product | dict_status | Before/after |
|---|---|---|---:|---:|---:|---|
| MySQL 5.7 | `5.7.44-log` | `foggy_test` / none | 110317 | 500 | 48 | equal |
| PostgreSQL 15 | `15.17` | `foggy_test` / `public` | 17384 | 500 | 48 | equal |
| SQL Server 2022 | `16.0.4236.2` | `foggy_test` / `dbo` | 5940 | 500 | 48 | equal |

Each external database lane passed `18/18`. Container name, image/container ID,
state and health were identical before and after the aggregate.

## Regression and Packaged Artifacts

- 9.3.1 isolation passed `132/13`, covering Step order, strong cache identity,
  datasource resolution and test/semantic Controller isolation.
- 9.3.2 auto-configuration/Launcher passed `64/15`, covering Boot 3 registration,
  Addon assembly, package-outside discovery and default-route isolation.
- root compile and package each completed all `25` reactor modules.
- package audit found `24` main JARs, `17` JARs containing Boot 3 imports,
  `21` unique imports entries, zero duplicate or legacy EnableAutoConfiguration
  entries, and `12/12` Launcher nested/local JAR checksum matches.
- Launcher SHA-256:
  `2e5cdce2b78dc83efdab578a4c346b327b05cb887d94216d30ccb29d80a0bb76`.

## Integrity

- Git HEAD: `90003beb3e06d57cebd91ea300ff132b077bddd9`.
- tracked diff, untracked path list/content and fixed containers are identical
  before and after.
- all `13,523` source-manifest entries reverified after package.
- concurrency/generation index contains `383` selected testcase records plus
  header; it is an index, not a claim of 383 dedicated concurrency tests.
- `summary.env` SHA-256:
  `601835f8bacca640678764a30bd919a74cddc6e7c7836c4b9bfcc73edfbf2db9`.
- `lanes.tsv` SHA-256:
  `3091e3744c8e4706d134cabe2a51263314fef2e743d02faf3fad3ac4948f5b29`.
- source manifest SHA-256:
  `f6c3fc8ee69d53925dc2841480713133c6a827d2c336b5be61bd5a9bf41dc577`.
- sealed `SHA256SUMS` SHA-256:
  `e8593ba0a3cb5acbce4308875159aa653b59e363ac33e96d7e75378fc258bc4d`.
- outer checksum file SHA-256:
  `9e629cd1d983c0325424f4e94d1676b210b5479d053cf04954ffca74b4f3679b`.

## Independent Review

Independent read-only review found **NO BLOCKER**. It recomputed all `519` raw
XML reports and `3824` testcase nodes, normalized the three SQLite skips,
verified `lanes.tsv` and `summary.env`, checked the run root, all 12 lane
manifests and REAL-QUERY child inner/outer manifests, and independently checked
source/container before-after, three database identities/fixtures, `24` main
JARs, Boot imports and all `12` Launcher nested JAR hashes.

`latest-run-id` points exactly to `20260714T084351Z-3271604`. The prior evidence
record remains `status: superseded` and is explicitly excluded from authority.

## Ordered Post-gate Completion

The allowed order at run seal time was:

1. update progress and finish implementation self-check;
2. formal implementation quality gate;
3. test evidence coverage audit;
4. version-scope acceptance;
5. synchronize the version index, requirement, plans and roadmap.

All five actions are now completed. Final decisions：implementation self-check
`passed-to-formal-quality-gate` → quality `ready-with-risks` → coverage
`ready-with-gaps` → version acceptance `signed-off / accepted-with-risks`；9.3.4
is ready and 9.3.5/9.4.0 remain queued.
