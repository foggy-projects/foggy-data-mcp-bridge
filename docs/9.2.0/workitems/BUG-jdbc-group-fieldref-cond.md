---
type: bug
bug_source: user-report
version: 9.2.0
ticket: BUG-jdbc-group-fieldref-cond
severity: major
status: ready-for-verification
reproduction_status: confirmed
test_strategy: integration-test
automation_decision: required
owner: dataset-model
---

# BUG Work Item

## Background

GitHub issue #103 reports that QM `accessBuilder` can add structured field-reference guards through `context.query.and(fieldRef, value)`, but the nested `JdbcGroupCond` API cannot express the same equality predicate inside grouped OR conditions.

## Reproduction

Use a QM accessBuilder with a nested group:

```js
const group = context.query.where.newGroupCond('and');
group.or(fo.orderId, 'ORD20240101000001');
group.or(fo.orderStatus, 'CANCELLED');
context.query.where.addCond(group);
```

Before the fix, fsscript cannot resolve `JdbcGroupCond.or(ColumnRef|DimensionProxy, value)` because `JdbcListCond` only exposes SQL-fragment overloads.

## Expected vs Actual

Expected: grouped conditions support field-reference equality predicates and render parameterized SQL such as `(t1.order_id = ? OR t1.order_status = ?)`.

Actual: runtime method resolution fails with `未找到方法 [or] 在 class[class com.foggyframework.dataset.db.model.engine.query.JdbcQuery$JdbcGroupCond]中`.

## Impact Scope

Affected surface:

- QM `accessBuilder` scripts that need grouped OR guards.
- Any nested `JdbcWhere` / `JdbcHaving` group authored through `JdbcListCond` rather than request slice DSL.

Not affected:

- Request-side `$or` / `$and` slice lowering, which already builds `JdbcGroupCond` internally.
- Root `context.query.and(fieldRef, value)` guards.
- Raw SQL fragment guards, though they require alias knowledge and are not a preferred replacement.

## Test Strategy

Add a regression query model and integration test under `foggy-dataset-model`:

- Build SQL from a grouped accessBuilder that calls `group.or(fieldRef, value)`.
- Assert the generated SQL keeps an outer parameterized OR group.
- Assert OR grouped access guards are not copied to RHS aggregate pushdown.
- Execute the generated SQL against the SQLite fixture.

## Code Inventory

- `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/engine/query/JdbcQuery.java`
- `foggy-dataset-model/src/test/java/com/foggyframework/dataset/db/model/ecommerce/AggregateJoinQueryModelTest.java`
- `foggy-dataset-model/src/test/resources/foggy/templates/ecommerce/query/OrderSalesAggregateRelationGroupedAccessQueryModel.qm`

## Fix Checklist

- [x] Add a regression QM using `JdbcGroupCond.or(fieldRef, value)`.
- [x] Confirm the regression fails before implementation.
- [x] Add field-reference overloads to `JdbcListCond`.
- [x] Keep raw SQL overload behavior unchanged.
- [x] Run targeted SQLite test.

## Verification

Failing-before evidence:

```powershell
mvn -pl foggy-dataset-model "-P!multi-db" "-Dspring.profiles.active=sqlite" "-Dtest=AggregateJoinQueryModelTest#aggregateRelationGroupedAccessBuilderFieldRefOrShouldRenderParameterizedGroup" test
```

Result before implementation: failed during accessBuilder execution with `未找到方法 [or] 在 class[class com.foggyframework.dataset.db.model.engine.query.JdbcQuery$JdbcGroupCond]中`.

Passing-after evidence:

```powershell
mvn -pl foggy-dataset-model "-P!multi-db" "-Dspring.profiles.active=sqlite" "-Dtest=AggregateJoinQueryModelTest#aggregateRelationGroupedAccessBuilderFieldRefOrShouldRenderParameterizedGroup" test
```

Result after implementation: `Tests run: 1, Failures: 0, Errors: 0, Skipped: 0`.

```powershell
mvn -pl foggy-dataset-model "-P!multi-db" "-Dspring.profiles.active=sqlite" "-Dtest=AggregateJoinQueryModelTest#aggregateRelationAccessBuilderFieldRefShouldPushRightWhere+aggregateRelationRawSqlAccessBuilderShouldStayOuterOnly+aggregateRelationGroupedAccessBuilderFieldRefOrShouldRenderParameterizedGroup" test
```

Result: `Tests run: 3, Failures: 0, Errors: 0, Skipped: 0`.

## References

- https://github.com/foggy-projects/foggy-data-mcp-bridge/issues/103
