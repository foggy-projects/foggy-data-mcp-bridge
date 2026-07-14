---
doc_role: batch_regression_exit_evidence
doc_purpose: Preserve the initially authoritative but later superseded Batch 7 replay before formal quality findings.
version: 9.3.3
batch: 7
status: superseded
decision: invalidated-for-final-signoff-by-quality-finding
batch_status: quality-fix-in-progress
version_status: in-progress
authoritative_run: 20260714T074009Z-3153871
authority_status: superseded
final_authoritative_run: 20260714T084351Z-3271604
superseded_by: batch-7-regression-exit-20260714-r2.md
independent_review: no-blocker
independent_review_scope: sealed-content-integrity-before-formal-quality
created_at: 2026-07-14
updated_at: 2026-07-14
---

# Batch 7 Compatibility and Regression Exit（Superseded History）

## Decision

At this checkpoint, the Batch 7 compatibility/regression replay **passed**. The
then-authoritative, now superseded and sealed historical run was:

```text
target/v933-batch7-regression/runs/20260714T074009Z-3153871/
```

The runner executed every lane strictly in series and closed with 3813 asserted
testcases in 517 fresh reports (511 parent Surefire XML reports plus six
REAL-QUERY child Failsafe XML reports), failures/errors/skipped=`0/0/3`. The three skips
are the exact reviewed SQLite allowlist; every other lane has zero skip.

This record initially opened the ordered post-gates. It was never the 9.3.3
version signoff；the formal quality gate subsequently invalidated it for final
signoff, and the replacement record completed the post-gates in order.

> Supersession note (2026-07-14): the formal implementation quality gate later
> identified `BUG-933-WATCHER-REGISTER-SCAN-RACE`, watcher authority-loss
> fail-open paths and unbounded Collection diagnostic depth. The run remains
> sealed evidence for its exact source snapshot, but it is no longer the final
> Batch 7 authority. Fixes and a full fresh replay were mandatory before
> post-gates could resume.
>
> Replacement authority: `batch-7-regression-exit-20260714-r2.md`, run
> `20260714T084351Z-3271604`. This historical record remains immutable in
> decision and must not be counted toward final signoff.

## Historical Authoritative Command

```bash
V933_BATCH7_SQLITE_EXPECTED_TESTS=3449 \
  scripts/verify-v933-batch7-regression.sh
```

The runner rejected discovery-based SQLite accounting and required the reviewed
positive constant `3449`; it also froze 470 SQLite reports and exactly three
named skips. At seal time, `target/v933-batch7-regression/latest-run-id` pointed
to the run above；the current pointer now points to replacement
`20260714T084351Z-3271604` and is not evidence by itself.

## Ordered Lane Ledger

| # | Lane | Tests | Reports | F/E/S | Evidence role |
|---:|---|---:|---:|---:|---|
| 01 | root compile | 0 | 0 | 0/0/0 | 25-module compile, not test evidence |
| 02 | Runtime API compatibility | 58 | 5 | 0/0/0 | real Controller 48 + frozen legacy/typed/safety contracts 10 |
| 03 | watcher/source management | 29 | 3 | 0/0/0 | runtime new-file/subdirectory/shared-root lifecycle and tracer cleanup |
| 04 | binding publication/retire | 16 | 2 | 0/0/0 | publication monitor, generation, drain/hard-revoke and scheduler failure |
| 05 | REAL-QUERY child | 11 | 6 | 0/0/0 | model lifecycle 4, required DB parity 3, Caffeine/Redis 4 |
| 06 | SQLite full | 3449 | 470 | 0/0/3 | full model-module regression with exact skip allowlist |
| 07 | MySQL 5.7 | 18 | 1 | 0/0/0 | real `MultiDatabaseQueryTest` |
| 08 | PostgreSQL 15 | 18 | 1 | 0/0/0 | real `MultiDatabaseQueryTest` |
| 09 | SQL Server 2022 | 18 | 1 | 0/0/0 | real `MultiDatabaseQueryTest` |
| 10 | 9.3.1 isolation regression | 132 | 13 | 0/0/0 | Step order, cache identity, datasource resolution and Controllers |
| 11 | 9.3.2 auto-config/Launcher | 64 | 15 | 0/0/0 | Boot 3 registration, Addon assembly and default route isolation |
| 12 | root package/artifacts | 0 | 0 | 0/0/0 | 25-module assembly and packaged metadata, not test evidence |
| **total** |  | **3813** | **517** | **0/0/3** |  |

The aggregate arithmetic is independently frozen as SQLite `3449/470` plus
non-SQLite `364/47`. Compile and package do not inflate the test count.

## API Compatibility Closure

The failed diagnostic run `20260714T070421Z-3070417` accidentally executed the
Runtime full suite during package and exposed three real red results. They were
not ignored:

- `diagnostics.attributes.validation` and `refresh` had been replaced by
  lifecycle-only attributes, violating the additive Runtime DTO contract;
- the Controller fixture still stubbed the removed clear/warmup path rather
  than `CatalogRefreshCoordinator`;
- root package used `skipTests` without the root POM's controlling
  `skipUnitTests=true` property.

The fix retained atomic coordinator ownership and restored sanitized structured
legacy diagnostics with stable nested nullability. Controller tests now mock
coordinator success/failure and explicitly reject `clearByNamespace` and
`getJdbcQueryModel` warmup. Focused closure was 3/3 green; the complete
Controller plus refresh/validate/legacy supporting suites were 56/56 green.
The historical API lane then passed 58 tests in five reports.

## SQLite and REAL-QUERY Evidence

SQLite full-suite result is 3449 tests / 470 reports / F0/E0/S3. The observed
and expected skip files are byte-for-byte equal and contain only:

1. `CalculateMvpIntegrationTest#calculateFailsClosedForRuntimeUnsupportedDatabase`
2. `PivotCascadeGenerateSqlParityIntegrationTest#testMysql57RowsCascadeFailsClosedWithoutMemoryFallback`
3. `JavaQueryModelAggregateJoinSnapshotTest#shouldProduceSnapshot`

The nested REAL-QUERY authority inside this sealed historical run is
`20260714T074009Z-3153871-05-real-query`. It passed 11 tests / 6 Failsafe
reports, with no skip:

- four QueryFacade lifecycle cases for atomic refresh, namespace isolation,
  binding rebind/drain and Pivot generation rotation;
- SQLite 3.42, MySQL 5.7 and PostgreSQL 15 rows/columns/order/value parity;
- Caffeine and real Redis 7.4.6 L1/L2 miss-write-hit-generation rotation.

The Redis authority started and ended with zero keys and removed its run-owned
container. The child summary's generation-time
`independent_review=pending-authoritative-run-review` value remains sealed; the
external Batch 7 run review is recorded in this document rather than rewriting
the child evidence.

## Required Database Identity and Fixture Stability

| Kind | Product version | Catalog/schema | fact_sales | dim_product | dict_status | Before/after |
|---|---|---|---:|---:|---:|---|
| MySQL 5.7 | `5.7.44-log` | `foggy_test` / none | 110317 | 500 | 48 | equal |
| PostgreSQL 15 | `15.17` | `foggy_test` / `public` | 17384 | 500 | 48 | equal |
| SQL Server 2022 | `16.0.4236.2` | `foggy_test` / `dbo` | 5940 | 500 | 48 | equal |

Every database lane passed 18/18. Fixed container name, image, container ID,
image ID, running state and health were captured before and after the aggregate
and remained identical. Credentials were expanded only inside the containers
and were not written to evidence.

## 9.3.1 and 9.3.2 Regression Boundary

- 9.3.1 isolation lane passed 132 tests / 13 reports. It includes both result
  and query execution ordering, complete cache isolation keys, datasource
  resolution fail-closed behavior, PreAgg-to-L2 identity and test/semantic
  Controller isolation. No repeated PreAgg report is used to inflate totals.
- 9.3.2 lane passed 64 tests / 15 reports. It covers Mongo, Vector, Cache,
  GraphQL, Cloud and DataViewer slices, Boot 3 boundary/registration uniqueness,
  full Addon assembly, package-outside discovery and Launcher default-route
  isolation.

## Packaged Artifact Evidence

Root package used both `-DskipUnitTests=true` and `-DskipTests`; the package log
contains zero `Running ...Test` lines. It is assembly evidence only.

| Packaged assertion | Result |
|---|---:|
| reactor modules | 25/25 |
| fresh main JARs | 24 |
| JARs containing Boot 3 imports | 17 |
| unique `AutoConfiguration.imports` entries | 21 |
| duplicate imports | 0 |
| legacy `EnableAutoConfiguration` entries | 0 |
| Launcher local nested JARs | 12 |
| nested/local SHA-256 matches | 12/12 |

Launcher SHA-256 is
`ea53a66c7e0213b87e96524489669f199e1c144f3524fdc0054933a9c0cbef3e`.

## Integrity and Reproducibility

The aggregate verified every lane's inner and outer manifests, then sealed the
complete run. It also proved:

- Git HEAD remained `90003beb3e06d57cebd91ea300ff132b077bddd9`;
- tracked diff hash remained
  `fc66da523320bffdbfd82a57cfba94ae70a2d6efab83204c64e64dde8f82a809`;
- untracked path-list hash remained
  `cbbae6886f5ea5a6da48f62547eca7933d007a7d13c0bf5c81022f458afbfb54`;
- untracked content hash remained
  `af006cbe32a2945bfd439d9c73fbee15bb61749e64ac30ed85bcd2ebb665cd27`;
- all 13,516 source-manifest entries reverified after the final package;
- 379 concurrency/generation observations were extracted from the fresh
  testcase ledger (380 lines including header).

Aggregate hashes:

- `summary.env`: `f70223ef09b45c0835de6d8c92212d20653da8392b3b744d4e9c65f79b9a6817`
- `lanes.tsv`: `d5a88711cd1208fb358c9c6ccea39339478a452aa231b396d35bfc83f08e5b50`
- concurrency observations: `3f000ef0fb6850802ff229e675a5ed3790a58e4b33e965a8c805419526980119`
- source manifest: `e2e555ce795fa5d4d62ec3dfc66a0ae31607625a1a78a60cf18d8b92b3e25a8e`
- sealed `SHA256SUMS`: `934d79156449c1fa485ca95db69c5446dc04ab72886c5d365b6e747b96ee0217`
- outer checksum file: `ebf173fcb1d1d55c7fce36529152d34097f529f86b35990dc9abb478a7b74811`

## Failure-driven Closure

The following runs are diagnostic and excluded from all pass totals:

- `20260714T065517Z-3055185`: stopped after imports-count review;
- `20260714T065938Z-3061481`: stopped after the missing 9.3.1 ordering owner was
  identified;
- `20260714T070421Z-3070417`: all test lanes green but root package failed after
  unintentionally running tests and exposing the Runtime Controller regression.

At this checkpoint Batch 7 had regression evidence for five documented
pre-gate defects: runtime
new-model watcher lifecycle, multi-binding scheduler fail-closed, catalog
prepare outside the binding monitor, Pivot refusal diagnostic order, and the
Runtime full-Controller compatibility regression. Their final workitem closure,
together with the three quality-gate findings found later, cites replacement
authority `20260714T084351Z-3271604`, not this run.

## Independent Review

Independent read-only content/integrity review at this checkpoint: **NO
BLOCKER**. This did not replace the later formal implementation quality gate,
which found the watcher/sanitizer gaps that superseded this run.

The reviewer independently recomputed all 517 reports and 3813 testcase nodes
from raw XML, verified every lane against `lanes.tsv`, compared the three
database fixtures and fixed-container records byte-for-byte, rechecked all
13,516 source entries, all parent/child manifests and the packaged JAR/imports
inventory. The review also confirmed that the 379 records in
`concurrency-generation-observations.tsv` are an evidence index selected by
lifecycle keywords, not a claim of 379 dedicated concurrency tests.

The mutable `latest-run-id` pointer is only a convenience. The immutable
citation for this historical source snapshot is run
`20260714T074009Z-3153871` together with top-level manifest SHA-256
`934d79156449c1fa485ca95db69c5446dc04ab72886c5d365b6e747b96ee0217`；it must
not be used as final version authority.

## Historical Post-gate Handoff

At this checkpoint, the allowed next order was fixed:

1. update version progress and complete the implementation self-check;
2. execute the formal implementation quality gate;
3. execute the test evidence coverage audit;
4. create the version-scope acceptance record;
5. synchronize README, requirement, progress, acceptance plan and roadmap.

9.3.3 therefore remained `in-progress` at this checkpoint. The later quality
findings stopped this handoff；replacement authority
`20260714T084351Z-3271604` and
`docs/9.3.3/acceptance/version-signoff.md` record the completed ordered gates
and final `signed-off / accepted-with-risks` decision.
