# Aggregate relation sibling instances lost during request planning

## Document purpose

- doc_type: bug
- intended_for: reviewer
- purpose: Record the generic identity bug exposed by a three-relation inventory measurement query.

## Problem and cause

A QueryModel with three independently aliased aggregate relations only propagated a safe outer join-key predicate to the first relation. The other relations aggregated their entire tenant domain. Projection pruning and request-state cleanup used the same collector and could also omit sibling instances.

`GeneratedAggregateRelationQueryObject` inherits `ViewSqlQueryObject` value equality. Every generated aggregate starts with `super("", sqlTable)`, so the inherited `viewSql` value is `"()"` for distinct relations. `JdbcModelQueryEngine.collectAggregateRelationQueryObjects()` used a `LinkedHashSet`, which collapsed these equal values even though their aliases, sources and thread-local planning states differ.

## Fix boundary

Collect aggregate relation objects by reference identity, preserving model encounter order. Do not change global `QueryObject` equality, pushdown safety rules, tenant authorization, LEFT JOIN semantics, or OR handling.

No persistence, datasource configuration, public DSL or industry-specific behavior changes are introduced.

## Regression evidence

`AggregateRelationSiblingIdentityTest` declares three separately aliased aggregates over the same sales source and verifies:

- an outer conjunctive order-key predicate reaches all three RHS relations;
- projection pruning applies independently to each relation;
- consecutive requests with different keys and selected outputs do not leak state;
- OR predicates remain outer-only and unmatched left rows remain visible.

Validation results are recorded with the delivery commit and the consuming TMS issue #36 evidence. SQL-shape and scale measurements use synthetic local fixtures; they do not attest to a deployed MySQL instance.

On 2026-10-28 the normal owning reactor completed:

```text
mvn -pl foggy-dataset-model-engine -am -Dtest=AggregateRelationSiblingIdentityTest,AggregateJoinQueryModelTest -Dsurefire.failIfNoSpecifiedTests=false -Dsurefire.failIfNoTests=false install
```

All 71 selected SQLite tests passed (67 existing aggregate-relation cases and four new sibling-identity cases). The no-tests flags allow dependency modules without these selected classes to participate; they do not skip the selected engine tests.

The thin `9.3.0-SNAPSHOT` engine JAR was installed in the normal local Maven repository. Its SHA-256 is `ABAAB10276934A7CC0CA9D1B885E4407B7FCDDABFD2942ACD93573FB263F351C`. No remote Maven deployment or application environment activation was performed. Other build hosts must consume a controlled publication or rebuild the recorded source revision before this fix can be assumed present.
