---
doc_role: workitem
doc_purpose: Track Java engine parity for compose join qualified field references and fsscript source aliases.
version: v3.0
ticket: REQ-compose-join-qualified-field-references-java-parity
priority: P1
status: implemented-targeted-tests-passed
source_type: requirement-bug-parity
owner: foggy-dataset-model
created_at: 2026-05-11
---

# REQ: Compose Join Qualified Field References Java Parity

## Document Purpose

- doc_type: workitem / parity-tracking / bug-regression-plan
- intended_for: compose-engine-owner | java-engine-owner | odoo-vendored-runtime-owner | reviewer
- purpose: Record the Java engine alignment with the Python/Odoo compose runtime behavior for post-join qualified field references, source alias capture, and derived-query field visibility.

## Upstream Source

- Odoo release workitem: `D:/foggy-projects/foggy-data-mcp/foggy-odoo-bridge-pro/docs/v1.6/workitems/REQ-compose-join-qualified-field-references.md`
- Python source-of-truth commit: `b4cd404 Preserve compose source aliases through derived queries`
- Odoo vendored runtime sync commit: `3be44cc Sync inherited compose source aliases to Odoo`
- Java worktree: `D:/foggy-projects/foggy-data-mcp/foggy-data-mcp-bridge-wt-dev-compose`

## Problem

The benchmark and external-user script shape below is natural and should be accepted:

```javascript
const firstOrders = dsl({
  model: "OdooSaleOrderQueryModel",
  columns: ["partner$id", "partner$caption", "MIN(dateOrder$id) as firstOrderDate"],
  groupBy: ["partner$id", "partner$caption"]
});

const mayFirstCustomers = firstOrders.query({
  slice: [
    {"field": "firstOrderDate", "op": ">=", "value": "2026-05-01"},
    {"field": "firstOrderDate", "op": "<", "value": "2026-06-01"}
  ]
});

const mayOrders = dsl({
  model: "OdooSaleOrderQueryModel",
  columns: ["partner$id", "count(id) as orderCount", "sum(amountTotal) as totalAmount"],
  groupBy: ["partner$id"]
});

const joined = mayFirstCustomers.join(
  mayOrders,
  "left",
  [{"left": "partner$id", "op": "=", "right": "partner$id"}]
);

const result = joined.query({
  columns: ["firstOrders.partner$caption", "left.firstOrderDate", "mayOrders.orderCount", "right.totalAmount"],
  orderBy: ["-mayOrders.totalAmount"]
});
```

Before this Java parity work, the Python/Odoo side had already fixed source alias inheritance and post-join qualified reference behavior. Java needed matching behavior without changing Odoo release-scope code in this worktree.

## Target Contract

Java compose must support:

- unique unqualified post-join fields in `joined.query({ columns/orderBy/slice/groupBy })`;
- `left.<field>` and `right.<field>` field references after join;
- fsscript assignment aliases such as `firstOrders.partner$caption` and `mayOrders.totalAmount`;
- source alias inheritance through derived plans, gated by the current derived output schema;
- duplicate join keys in `join(on)` without forcing a failure when they are only used in the join predicate;
- fail-closed behavior when a known source alias references a field dropped by an intermediate derived query.

This Java work intentionally does not implement a broad new compose framework or change the Odoo vendored runtime.

## Implementation Notes

| Area | Status | Notes |
|---|---|---|
| `QueryPlan` source alias storage | done | Added internal compose source aliases and fsscript `.query({...})` method dispatch |
| `PlanAliasSupport` | done | New helper lets runtime bind fsscript variable names to `QueryPlan` aliases without exposing a new script method |
| `ScriptRuntime` alias binding | done | Custom evaluator records assignment names when assigned values are `QueryPlan` instances |
| `PlanQualifiedFieldResolver` | done | Normalizes qualified references in derived query surfaces and join predicates |
| `DerivedQueryPlan` normalization | done | Normalizes columns, slice, groupBy, and orderBy before validation/storage |
| Java deterministic regression tests | done | Added compile/runtime coverage in `JoinCompileTest` and `ScriptRuntimeTest` |

## Visibility and ACL Notes

Source aliases are convenience metadata over the current plan output schema. They do not expose fields removed by a derived projection. A known alias referencing a dropped field fails with:

```text
compose-schema-error/derived-query/unknown-field
```

This preserves the current output schema and keeps later ACL/field-access validation from receiving hidden alias shortcuts.

## Verification

Targeted command:

```powershell
mvn -pl foggy-dataset-model "-Dtest=JoinCompileTest,ScriptRuntimeTest" test
```

Result on 2026-05-11:

- default surefire pass: 47 tests, 0 failures, 0 errors
- `test-mysql` pass: 47 tests, 0 failures, 0 errors
- `test-postgres` pass: 47 tests, 0 failures, 0 errors

Broader Java project verification, user-reported on 2026-05-11:

```powershell
mvn test
```

Result:

- no errors reported by the operator
- detailed test counts were not captured in this document; preserve the Maven console or CI log before formal release signoff if exact totals are required

## Current Status

- implementation_status: implemented-targeted-tests-passed
- java_engine_scope: complete for this parity slice
- odoo_vendored_runtime_scope: not changed in this Java worktree; already synced in Odoo release worktree before this Java parity task
- remaining_risk: exact full `mvn test` totals and release packaging evidence are not captured in this focused Java parity pass

## Next Steps

1. Preserve or attach the full `mvn test` console/CI artifact before formal release signoff if exact totals are required.
2. If Java artifacts are embedded into Odoo later, sync the compiled/runtime artifact through the normal Odoo vendored engine update path and rerun the Odoo benchmark repro.
3. Keep Python, Odoo vendored runtime, and Java parity docs aligned if the qualified reference contract is expanded to `having` or explicit public plan aliases.
