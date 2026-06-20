---
type: bug
bug_source: regression-found
version: 9.2.0
ticket: BUG-qm-v2-same-table-alias-aggregate-sql
severity: major
status: local-verified
reproduction_status: confirmed
test_strategy: integration-test
automation_decision: required
owner: foggy-dataset-model
created_at: 2026-06-20
updated_at: 2026-06-20
owner_module: foggy-dataset-model
---

# BUG: QM v2 Same-table Alias Aggregate SQL Uses Unqualified Fields

## Background

Full-module verification fails in a QM v2 same-table multi-alias aggregate join
case. The generated SQL projects and joins fields such as `salesAmount` and
`orderId` without a table alias, then SQLite fails with `no such column:
salesAmount`.

## Reproduction

Run:

```bash
mvn -pl foggy-dataset-model -Dtest=AdvancedAnalyticsTest#testQmV2SameTableModelMultipleAliases test
```

## Expected vs Actual

Expected:

- The generated SQL qualifies fields with the correct table or aggregate alias.
- The query executes and returns rows for the same-table alias scenario.

Actual:

- Generated SQL contains unqualified projection fields, including
  `salesAmount "salesAmount"`.
- SQLite raises `no such column: salesAmount`.

## Impact Scope

- QM v2 same-table alias joins.
- Aggregate relation joins that reuse the same source table under multiple
  aliases.
- Any runtime query that requires disambiguated alias-qualified SQL.

## Test Strategy

- Use the existing integration test as the reproduction guard.
- Add or tighten focused assertions only if the fix touches shared SQL alias
  rendering behavior.

## Code Inventory

- `foggy-dataset-model/src/test/java/com/foggyframework/dataset/db/model/ecommerce/AdvancedAnalyticsTest.java`
- `foggy-dataset-model/src/test/resources/foggy/templates/ecommerce/query/FactSalesSelfAliasJoinQueryModelTest.qm`
- `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/engine`
- `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/impl/model`

## Fix Checklist

- [x] Confirm failing SQL and source model mapping.
- [x] Identify alias qualification loss point.
- [x] Add focused regression if current integration test is too broad.
- [x] Repair SQL rendering without changing unrelated query shapes.
- [x] Run the focused integration test.

## Verification

- 2026-06-20: Reproduced by full-module test baseline.
- 2026-06-20: `mvn -pl foggy-dataset-model -Dtest=AdvancedAnalyticsTest#testQmV2SameTableModelMultipleAliases test` passed after the fix.
- 2026-06-20: `mvn -pl foggy-dataset-model -Dtest=AdvancedAnalyticsTest test` passed, 20 tests.

## References

- `docs/9.2.0/workitems/BUG-model-module-test-baseline-20260620.md`
