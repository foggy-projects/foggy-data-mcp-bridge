---
type: bug
bug_source: github-issue
version: 9.1.0.beta
status: ready-for-verification
severity: major
owner: foggy-dataset-model
issue_url: https://github.com/foggy-projects/foggy-data-mcp-bridge/issues/102
reported_runtime_version: 8.1.10.beta
fixed_version: 9.1.0.beta
reproduction_status: confirmed-by-code-and-regression-test
automation_decision: required
test_strategy: integration-regression-test
---

# BUG: self-inclusive hierarchy compatibility for non-standard closure tables

## Problem

GitHub issue #102 reports that TMS X3 org management uses
`orgHierarchy$id selfAndDescendantsOf 89078`, but the selected org itself is not
returned. The reported TMS runtime dependency is `foggy-data.version=8.1.10.beta`.

By the Foggy parent-child closure-table contract, closure tables should contain
distance=0 self-link rows. The TMS runtime closure data behind this report does
not satisfy that contract, so the primary defect belongs to the upstream model or
data build process.

The current `9.1.0.beta` engine still receives a defensive compatibility fix for
self-inclusive hierarchy operators so `selfAndDescendantsOf` and
`selfAndAncestorsOf` remain semantically stable even when an upstream closure
table is missing self-link rows.

## Root Cause

The engine generated a closure-table join such as:

```sql
left join team_closure d2 on t1.team_id = d2.team_id
where d2.parent_id = ?
```

For non-standard closure tables that store strict ancestor-to-descendant rows
only, there is no `parent_id = child_id` self-link, so a direct closure-table
filter makes `selfAndDescendantsOf` behave like `descendantsOf`.

Adding a plain `OR base_id = ?` after the closure join is not safe because the
join can duplicate fact rows and inflate measures.

## Fix

`JdbcModelQueryEngine` now renders self-inclusive hierarchy predicates as:

```sql
base_fk in (?)
or exists (
  select 1
  from closure_table c
  where c.parent_key in (?)
    and c.child_key = base_fk
)
```

The same shape is used for `selfAndAncestorsOf` with the ancestor direction keys.
This keeps the self row independent from closure self-links and avoids duplicate
fact rows. Non-self operators such as `childrenOf`, `descendantsOf`, and
`ancestorsOf` keep the existing closure join behavior.

## Regression Coverage

- `ParentChildDimensionTest#testHierarchyOp_SelfAndDescendantsOf_StrictClosureTableIncludesSelf`
  temporarily removes closure self-links and verifies `selfAndDescendantsOf`
  still returns the selected node plus descendants without duplicate business
  rows.
- Existing parent-child hierarchy tests continue to cover self-link closure
  tables and non-self hierarchy operators.
- Existing Odoo hierarchy tests cover `selfAndAncestorsOf` and `$or` composition.

## Verification

- `mvn -pl foggy-dataset-model -Dtest=ParentChildDimensionTest#testHierarchyOp_SelfAndDescendantsOf_StrictClosureTableIncludesSelf test`
- `mvn -pl foggy-dataset-model -Dtest=ParentChildDimensionTest test`
- `mvn -pl foggy-dataset-model "-Dtest=OdooHierarchyQueryTest,ClosureOperatorOrSliceTest" test`
