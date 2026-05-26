---
doc_role: bug
doc_purpose: Record and close the root mvn test multi-database fixture stability regression.
version: v3.0
ticket: BUG-mvn-test-multidb-fixture-stability
bug_source: regression-found
severity: major
status: closed
owner: foggy-dataset-model
created_at: 2026-05-26
closed_at: 2026-05-26
---

# BUG: Root mvn test multi-db fixture stability

## Summary

Running root `mvn test` exposed multi-database integration test instability in the dataset-model module. The failures were not caused by semantic-scale formula behavior directly; they came from stale shared MySQL/PostgreSQL fixture state and SQLite-only manual SQL assumptions in DSL_CTE regression tests.

## Symptoms

| Area | Symptom |
|---|---|
| Pivot integration | Test expected a hardcoded product row, but the shared database contained different product data. |
| CRM funnel DSL_CTE | MySQL/PostgreSQL profiles could miss `crm_lead` or baseline `fact_order` rows. |
| SLA DSL_CTE | MySQL/PostgreSQL profiles could miss `service_ticket`; manual SQL used SQLite `julianday`. |
| CRM attribution bridge | Generated attribution-window SQL used SQLite `date(x, '+N days')`, which PostgreSQL rejects. |
| Relation metric manual SQL | PostgreSQL required aliases for derived-table subqueries. |

## Root Cause

- Some integration tests assumed a freshly initialized fixture database, while local multi-db profiles reused existing schemas.
- Manual baseline SQL in several tests was SQLite-oriented and lacked PostgreSQL-compatible date arithmetic or derived table aliases.
- The DSL_CTE cross-model funnel attribution bridge generated a SQLite date-window expression without considering the SQL dialect already present in the generated base SQL.

## Fix

- Reset `crm_lead` and `service_ticket` fixtures per test, including dialect-compatible timestamp column types and MySQL collation.
- Reset CRM baseline `fact_order` rows used by the funnel fixture.
- Make pivot-domain slice fixtures select products by stable business attributes instead of hardcoded product keys/captions.
- Add dialect-aware manual SQL helpers for SLA hour differences and CRM attribution windows.
- Add derived-table aliases for PostgreSQL manual SQL baselines.
- Add dialect-aware attribution-window generation in `DslCteDslRequestMapper` for SQLite, PostgreSQL, MySQL, and SQL Server style SQL.

## Regression Coverage

| Command | Result | Notes |
|---|---|---|
| `mvn test -pl foggy-dataset-model "-Dspring.profiles.active=postgres" "-Dtest=DslCteCrmFunnelFixtureIntegrationTest,DslCteSlaFixtureIntegrationTest,DslCteRelationMetricFixtureIntegrationTest" "-P!multi-db"` | passed | Covers the PostgreSQL failures found during root `mvn test`. |
| `mvn test` | passed | Full reactor test run completed with exit code 0. |

## Residual Risk

Root `mvn test` still prints a background-thread `ArrayIndexOutOfBoundsException` from `FsscriptClientProxyTest`, but Surefire does not mark the build as failed. This is outside the dataset-model fixture regression fixed here and should be tracked separately if it becomes user-visible or starts failing the build.
