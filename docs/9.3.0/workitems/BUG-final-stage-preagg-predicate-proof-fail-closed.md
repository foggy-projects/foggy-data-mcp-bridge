---
type: bug
version: 9.3.0
status: fixed
priority: P1
owner: foggy-dataset-model
detected_at: 2026-07-09
fixed_at: 2026-07-09
source: implementation-review
---

# BUG: Final-Stage PreAgg Predicate Proof Must Fail Closed

## Summary

The P0-P2 bounded final-stage preAgg `returnTotal` equivalence path reused the general preAgg slice WHERE builder. That builder is permissive for ordinary preAgg rewriting and can return an empty or partial WHERE fragment for unsupported predicate forms. In the final-stage equivalent aggregate path, this is not safe because the preAgg aggregate SQL is treated as a semantic equivalent for the final count.

## Detection Source

Implementation review after the bounded final-stage preAgg restoration.

## Impact

- Affected path: `preAggOptimizationPolicy=return-total-equivalent-only`.
- Main-query preAgg was still skipped for final-stage plans.
- Risk was limited to aggregate-only `returnTotal` restoration when slice predicates used unprovable `$field`, `$expr`, logical group, unsupported operator, or invalid range forms.

## Expected Behavior

Final-stage equivalent aggregate preAgg should be used only when every slice predicate can be proven and mapped to the preAgg table. If any predicate part is unprovable, aggregate preAgg must be skipped with a deterministic reason.

## Actual Behavior Before Fix

The final-stage equivalent builder reused the non-strict slice builder. Unmapped predicate parts could be dropped or represented as incomplete SQL fragments before the aggregate preAgg SQL was attached.

## Fix

- Added strict final-stage-only predicate proof in `PreAggQueryRewriter.buildProvableWhereClauseFromSlices`.
- `FinalStagePreAggAggregateSqlBuilder` now throws `PredicateNotProvableException` if any slice predicate is not fully proven.
- `PreAggRewriteStep` records skip reason `return-total-equivalent-predicate-not-provable` and preserves the detail in `preAggAggregateSkipDetail`.
- The strict proof maps only provable preAgg dimension/property predicates. Row-level measure predicates are intentionally not treated as equivalent aggregate WHERE predicates.

## Regression Tests

- `PreAggregationEdgeCaseTest` order 28: provable `$field` and `$expr` predicates keep final-stage equivalent aggregate preAgg hit and match non-preAgg total.
- `PreAggregationEdgeCaseTest` order 29: unprovable `$field` right-side reference fails closed.
- `PreAggregationEdgeCaseTest` order 30: unprovable `$expr` token fails closed.
- `PreAggregationEdgeCaseTest` order 31: partially unprovable logical group fails closed.

## Verification

- `mvn -pl foggy-dataset-model -Dtest=PreAggregationEdgeCaseTest test`: pass, 17 tests, 0 failures, 0 errors, 0 skipped.
- `mvn -pl foggy-dataset-model -Dtest=PreAggregationEdgeCaseTest,JdbcModelQueryEngineCteWrapTest test`: pass, 41 tests, 0 failures, 0 errors, 0 skipped; configured second surefire execution also passed 41 tests.

## Closure Checklist

- Bug recorded in versioned docs: completed.
- Failure mode made fail-closed: completed.
- Regression coverage added before closure: completed.
- PreAgg capability reviewed: completed. Proven no-result-filter `returnTotal` equivalence remains available; unprovable predicates now fall back to original final count.
