---
doc_role: performance_baseline
doc_purpose: Repository-local benchmark evidence for Pivot SQL TopN/Having Pushdown in 9.0.0.beta.
version: 9.0.0.beta
target: Pivot SQL TopN/Having Pushdown Performance Baseline
status: reviewed
recorded_at: 2026-05-02
source_artifacts:
  - foggy-dataset-model/src/test/java/com/foggyframework/dataset/db/model/engine/pivot/PivotSqlPerformanceBaselineTest.java
  - benchmark_results_final.txt
  - C:/Users/oldse/.gemini/antigravity/brain/3abb30ac-f7f2-425a-a79c-55c9d42bd914/pivot_sql_pushdown_benchmark_report.md
---

# Pivot SQL Pushdown Performance Baseline

## Purpose

This document records the performance baseline gathered after Pivot SQL TopN/Having Pushdown Stage 1-4 completion. It is a repository-local evidence artifact so acceptance does not depend on the external Antigravity brain directory.

The goal is not to prove that SQL pushdown is always faster on small test data. The goal is to answer whether the current 9.0.0.beta implementation has a safe performance posture, whether the `domain > 500` fail-closed limit is valid, and whether Stage 5 large-domain transport must be started before release.

## Scope

- Feature under review: Pivot SQL TopN/Having Pushdown.
- Benchmark test: `PivotSqlPerformanceBaselineTest`.
- Data shape: synthetic ecommerce `fact_sales` data, target 10,000 fact rows.
- Dialects covered by the submitted benchmark run: SQLite, MySQL 8.0, PostgreSQL.
- Paths compared:
  - SQL pushdown path using managed queryModel relation + CTE/window domain filtering.
  - Memory fallback path with `PivotPipeline.SQL_PUSHDOWN_ENABLED = false`.
- Large-domain safety scenario: non-additive subtotal/grandTotal with `customer$caption limit=600`.

## Benchmark Scenarios

| Scenario | Query Shape | Purpose |
|---|---|---|
| S1 | single row axis + additive metric TopN | Compare simple TopN pushdown overhead with memory truncation. |
| S2 | rows + columns + TopN | Compare two-axis domain filtering. |
| S3 | axis Having + TopN | Confirm database-side aggregate filtering remains cheap. |
| S4 | TopN + `COUNT_DISTINCT` + subtotals/grandTotal | Exercise non-additive rollup after bounded domain selection. |
| S5 | `customer$caption limit=600` + `COUNT_DISTINCT` + subtotals/grandTotal | Validate the `500` domain safety threshold. |

## Recorded Results

The benchmark log in `benchmark_results_final.txt` contains three benchmark groups and finishes with Maven `BUILD SUCCESS`. Timings are sampling data, not a formal microbenchmark.

| Scenario | Pushdown Result | Memory Result | Acceptance Reading |
|---|---:|---:|---|
| S1 single-axis TopN | 244-328 ms | 9-19 ms | On 10k-row synthetic data, SQL CTE/window setup is slower than memory truncation. This is expected and does not invalidate the pushdown architecture because the feature targets bounded transfer and larger intermediate-result protection. |
| S2 rows + columns | 5-27 ms | 11-26 ms | Comparable where row counts align. Some groups show different output counts, so those rows are semantic evidence rather than apples-to-apples timing evidence. |
| S3 Having + TopN | 4-19 ms | N/A | Having pushdown is cheap and avoids transferring rows that would be discarded by aggregate filters. |
| S4 non-additive subtotal | 19-120 ms | 12-78 ms | Pushdown adds SQL-side domain and rollup coordination cost but remains bounded. Memory is often faster on small data. |
| S5 large domain | failed closed in two groups; succeeded in one low-cardinality group | failed closed in two groups; succeeded in one low-cardinality group | The safety threshold is validated when the surviving customer domain actually reaches 600. Low-cardinality local data cannot validate this threshold by itself. |

Representative evidence from `benchmark_results_final.txt`:

- S1: `S1_Pushdown -> 244 ms, 5 rows`; `S1_MemoryFallback -> 9 ms, 5 rows`.
- S1: `S1_Pushdown -> 252 ms, 1 rows`; `S1_MemoryFallback -> 19 ms, 1 rows`.
- S1: `S1_Pushdown -> 328 ms, 5 rows`; `S1_MemoryFallback -> 11 ms, 5 rows`.
- S3: `S3_Pushdown -> 4 ms, 5 rows`; `S3_Pushdown -> 19 ms, 1 rows`; `S3_Pushdown -> 8 ms, 5 rows`.
- S5: `Non-additive rollup surviving domain too large: size=600, max=500`.
- S5: `S5_Pushdown_LargeDomain -> FAILED` and `S5_Memory_LargeDomain -> FAILED` when domain size is 600.
- Final Maven status: `BUILD SUCCESS`.

## Local Acceptance Rerun

The benchmark test was also rerun locally during acceptance:

```bash
mvn -pl foggy-dataset-model -P!multi-db "-DfailIfNoTests=false" "-Dtest=PivotSqlPerformanceBaselineTest" test
```

Result: `Tests run: 1, Failures: 0, Errors: 0, Skipped: 0`.

The local rerun is useful as a smoke check that the benchmark class compiles and runs. It is not sufficient by itself to prove S5 fail-closed behavior, because the local SQLite data cardinality may not produce more than 500 surviving `customer$caption` values. The S5 threshold evidence therefore comes from the benchmark groups in `benchmark_results_final.txt` where the domain reached `600`.

## Interpretation

SQL pushdown should not be enabled or justified solely by lower latency on small synthetic data. For simple TopN over small result sets, the current memory path can be faster because it avoids CTE/window planning overhead.

The value of SQL pushdown is structural:

- It prevents axis Having and TopN from becoming purely post-query JVM work.
- It reduces the chance that Pivot pulls a large intermediate result set only to discard most of it.
- It preserves queryModel-managed permission validation, pre-aggregation, physical column validation, and parameter binding.
- It gives the engine a fail-closed boundary for cases that would otherwise generate unsafe non-additive rollup SQL.

## Caveats

- `PivotSqlPerformanceBaselineTest` is a baseline sampling test, not a deterministic performance assertion suite.
- S5 catches expected exceptions and logs them as benchmark output, so Maven success does not by itself prove the threshold was hit. The log evidence must be inspected.
- Some S2 pushdown and memory rows differ in output count. Those entries should not be used for direct latency comparison until a dedicated parity-shaped benchmark request is added.
- The test toggles the static `PivotPipeline.SQL_PUSHDOWN_ENABLED` flag. It should stay isolated and should not be mixed with parallel test execution without additional guardrails.
- The generated data path only inserts fact rows. If a test database has low dimension-cardinality or missing matching dimension captions, S5 may not exceed the 500-domain threshold.

## Decision

Stage 5 large-domain transport is not required for 9.0.0.beta.

The current `domain > 500` fail-closed threshold is an acceptable release boundary for non-additive subtotal/grandTotal scenarios. A future value-table, temp-table, or CTE-join domain transport project should be considered only when customer telemetry shows repeated legitimate workloads hitting this boundary.

## Follow-Up Recommendations

- Keep the fail-closed threshold and user-facing remediation message for 9.0.0.beta.
- Add production telemetry for `NonAdditiveRollupDomainTooLargeException` before committing to Stage 5.
- If performance benchmarking becomes release-gating, convert the current sampling test into a dedicated benchmark profile with stable generated dimensions, explicit S5 threshold assertions, and non-parallel execution.
