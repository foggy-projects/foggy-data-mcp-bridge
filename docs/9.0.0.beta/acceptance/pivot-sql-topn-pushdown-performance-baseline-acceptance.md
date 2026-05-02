---
doc_role: feature_acceptance
doc_purpose: Formal acceptance record for the Pivot SQL TopN/Having Pushdown performance baseline follow-up in Foggy Pivot 9.0.0.beta.
acceptance_scope: feature
version: 9.0.0.beta
target: Pivot SQL TopN/Having Pushdown Performance Baseline
status: signed-off
decision: accepted-with-risks
signed_off_by: Codex acceptance reviewer
signed_off_at: 2026-05-02
reviewed_by: Codex
blocking_items: []
follow_up_required: yes
evidence_count: 6
---

# Feature Acceptance

## Background

After Pivot SQL TopN/Having Pushdown Stage 1-4 was accepted, the remaining decision was whether to immediately start Stage 5 large-domain transport, or first establish a performance and safety baseline.

The performance baseline follow-up added `PivotSqlPerformanceBaselineTest`, repaired the invalid `customer$customerName` benchmark field to `customer$caption`, reduced noisy JDBC TRACE logging, and captured SQLite / MySQL 8.0 / PostgreSQL benchmark results for pushdown and memory paths.

## Acceptance Basis

- `docs/9.0.0.beta/acceptance/pivot-sql-topn-pushdown-followup-prompt.md`
- `docs/9.0.0.beta/acceptance/pivot-sql-topn-pushdown-performance-baseline.md`
- `foggy-dataset-model/src/test/java/com/foggyframework/dataset/db/model/engine/pivot/PivotSqlPerformanceBaselineTest.java`
- `foggy-dataset-model/src/test/resources/application-docker.yml`
- `foggy-dataset-model/src/test/resources/application-sqlite.yml`
- `benchmark_results_final.txt`

## Checklist

- [x] Benchmark test compiles and runs under Maven.
- [x] Benchmark uses a valid model field for customer caption (`customer$caption`) instead of the invalid `customer$customerName`.
- [x] Benchmark covers SQL pushdown and memory fallback paths for simple TopN, two-axis TopN, Having + TopN, non-additive subtotal/grandTotal, and large-domain threshold scenarios.
- [x] Benchmark evidence covers SQLite, MySQL 8.0, and PostgreSQL according to the submitted result report.
- [x] S5 validates fail-closed behavior when the non-additive rollup surviving domain reaches `600 > 500`.
- [x] The result is interpreted as a performance baseline and release-boundary decision, not as a deterministic microbenchmark.
- [x] Stage 5 large-domain transport is explicitly deferred for 9.0.0.beta unless future telemetry proves demand.

## Evidence

- Repository-local benchmark report: `docs/9.0.0.beta/acceptance/pivot-sql-topn-pushdown-performance-baseline.md`.
- Benchmark implementation: `PivotSqlPerformanceBaselineTest`.
- Benchmark log file: `benchmark_results_final.txt`, ending in Maven `BUILD SUCCESS`.
- Large-domain threshold evidence in `benchmark_results_final.txt`: `Non-additive rollup surviving domain too large: size=600, max=500`.
- Submitted benchmark summary reports SQLite, MySQL 8.0, and PostgreSQL coverage with 10,000 synthetic fact rows.
- Local acceptance rerun on 2026-05-02:

```bash
mvn -pl foggy-dataset-model -P!multi-db "-DfailIfNoTests=false" "-Dtest=PivotSqlPerformanceBaselineTest" test
```

Result: `Tests run: 1, Failures: 0, Errors: 0, Skipped: 0`.

## Failed Items

No blocking failed acceptance item remains.

## Risks / Open Items

- The benchmark is a sampling test. It should not be treated as a stable latency gate without a dedicated benchmark profile and controlled data generation.
- A local SQLite rerun may not trigger S5 fail-closed behavior if the available customer caption domain does not exceed 500. The accepted S5 evidence is the recorded `600 > 500` benchmark group in `benchmark_results_final.txt`.
- Some rows in the S2 benchmark produce different pushdown and memory output counts. Those entries are not used as direct latency comparisons.
- Stage 5 remains a future scalability enhancement, not a 9.0.0.beta release blocker. Telemetry should decide whether it is worth the additional complexity.

## Final Decision

Accepted with non-blocking risks. The performance baseline is sufficient for the 9.0.0.beta decision: keep the current fail-closed large-domain boundary, do not start Stage 5 large-domain transport before release, and add telemetry before committing to a temp-table / value-table / CTE-join transport design.

## Signoff Marker

- acceptance_status: signed-off
- acceptance_decision: accepted-with-risks
- signed_off_by: Codex acceptance reviewer
- signed_off_at: 2026-05-02
- acceptance_record: docs/9.0.0.beta/acceptance/pivot-sql-topn-pushdown-performance-baseline-acceptance.md
- blocking_items: none
- follow_up_required: yes
