---
doc_role: feature_acceptance
doc_purpose: Sign off the 9.2.0 Java QueryModel aggregate join implementation slice.
acceptance_scope: feature
version: 9.2.0
target: QueryModel Aggregate Join
status: signed-off-tms-feedback-hardening
decision: accepted-with-risks
signed_off_by: Codex
signed_off_at: 2026-05-27
reviewed_by: N/A
blocking_items: []
follow_up_required: yes
evidence_count: 10
---

# Feature Acceptance

## Background

This record signs off the Java engine QueryModel aggregate join cut for 9.2.0. The accepted scope is runtime aggregate join semantics, query-time RHS pushdown, real execution evidence, and versioned documentation. ETL / pre-aggregation promotion is explicitly deferred and is not a blocker for this delivery.

## Acceptance Basis

- Workitem: `docs/9.2.0/workitems/query-model-aggregate-join.md`
- Quality gate: `docs/9.2.0/quality/query-model-aggregate-join-implementation-quality.md`
- Coverage audit: `docs/9.2.0/coverage/query-model-aggregate-join-coverage-audit.md`
- Implementation module: `foggy-dataset-model`
- Demo fixtures: `foggy-dataset-demo`

## Checklist

| Item | Result | Notes |
|---|---|---|
| Aggregate RHS before LEFT JOIN | accepted | Engine generates a structured derived aggregate relation instead of relying on `viewSql`. |
| Main-side measure not multiplied | accepted | Query execution compares generated aggregate join with native aggregate SQL. |
| Fixed RHS filters inside aggregate relation | accepted | Filters before `groupBy` are rendered inside the RHS relation before aggregation. |
| Query-time RHS pushdown | accepted-with-risk | AND-only group-key, measure, left join-key, and structured accessBuilder field-ref guard pushdown is implemented; OR/complex predicates stay outer-query only. |
| Aggregate field metadata inheritance | accepted-with-risk | Core QueryModel schema exposes inherited TM caption/type and aggregate lineage metadata; query-cloud/data-viewer `frontend-meta` propagation remains a separate chain check. |
| LEFT no-match semantics | accepted | Outer filters are retained where needed; no-match behavior remains normal LEFT JOIN null behavior. |
| Invalid grain fail-closed | accepted | Missing right join key in RHS `groupBy` is rejected. |
| Real database evidence | accepted-with-risk | SQLite and live MySQL 5.7 passed; PostgreSQL and target TMS database evidence remain follow-up. |
| Ordinary join regression | accepted | Existing multi-fact ordinary join regression passed. |
| ETL boundary | accepted | ETL is recorded as deferred/out of current scope. |

## Evidence

| Evidence | Result |
|---|---|
| `mvn install -pl foggy-dataset-demo -DskipTests` | success. |
| `mvn test -pl foggy-dataset-model -Dspring.profiles.active=sqlite -P!multi-db -Dtest=AggregateJoinQueryModelTest` | success; Tests run: 15, Failures: 0, Errors: 0, Skipped: 0. |
| SQLite explain evidence | RHS aggregate source uses indexed lookup for the pushed order key predicate. |
| `mvn test -pl foggy-dataset-model -Dspring.profiles.active=docker -P!multi-db -Dtest=AggregateJoinQueryModelTest` | success on live MySQL 5.7; Tests run: 15, Failures: 0, Errors: 0, Skipped: 0. |
| MySQL 5.7 explain evidence | Derived aggregate source `agg_src` uses `uk_order_line`, `type=ref`, `rows=10`, and `Using where` for the pushed `order_id`. |
| `mvn test -pl foggy-dataset-model -Dspring.profiles.active=sqlite -P!multi-db -Dtest=MultiFactTableJoinTest` | success; Tests run: 13, Failures: 0, Errors: 0, Skipped: 0. |
| Environment evidence | Docker unavailable; MySQL 5.7 local port reachable; PostgreSQL and MySQL 8 local ports closed. |
| `JAVA_HOME=/Users/fengjianguang/.jdk/temurin-17/Contents/Home mvn -pl foggy-dataset-model -P'!multi-db' -Dspring.profiles.active=sqlite -Dtest='AggregateJoinQueryModelTest#aggregateRelationOutputFieldShouldRespectFieldAccessAllowList+aggregateRelationOutputFieldShouldFailClosedWhenMissingFromFieldAccess' test` | success; aggregate relation output field `fieldAccess` allow/deny coverage; Tests run: 2, Failures: 0, Errors: 0, Skipped: 0. |
| `JAVA_HOME=/Users/fengjianguang/.jdk/temurin-17/Contents/Home mvn -pl foggy-dataset-model -P'!multi-db' -Dspring.profiles.active=sqlite -Dtest='AggregateJoinQueryModelTest#aggregateRelationSystemSliceShouldBypassUserFieldAccessForGuardFields' test` | success; aggregate relation `system_slice` guard field bypasses user `fieldAccess` for filtering without leaking the guard field to returned columns; Tests run: 1, Failures: 0, Errors: 0, Skipped: 0. |
| `JAVA_HOME=/Users/fengjianguang/.jdk/temurin-17/Contents/Home mvn -pl foggy-dataset-model -P'!multi-db' -Dspring.profiles.active=sqlite -Dtest=AggregateJoinQueryModelTest test` | success; full aggregate join sqlite regression after fieldAccess and system_slice guard coverage; Tests run: 34, Failures: 0, Errors: 0, Skipped: 0. |

## Risks / Open Items

- PostgreSQL and target TMS database `EXPLAIN` evidence is still required before claiming broad dialect optimizer confidence.
- RHS duplicate pushdown fragments are literal-rendered until the engine has a parameter-carrying derived relation.
- OR / complex predicate RHS pushdown is intentionally not enabled.
- Tenant/access guard RHS pushdown requires structured field-ref conditions and an explicit aggregate join key/group key mapping; implicit tenant guards and raw SQL predicates are not guessed.
- Query-cloud/data-viewer `frontend-meta` propagation for aggregate relation fields still needs upstream verification.
- Relation-level default aggregate projection should be pruned to referenced QM fields in a later optimization.
- Request-side `fieldAccess` allow/deny checks now cover aggregate relation output fields. System-slice guard fields may intentionally bypass user `fieldAccess` for filtering and must not leak into returned columns; authorization of the system-slice producer remains an upstream governance boundary. RHS raw-SQL guard pushdown remains a follow-up risk; system slice lifecycle and structured accessBuilder join-key guard coverage are covered.
- ETL / pre-aggregated promotion is deferred and should be handled as a separate modeling/optimization work item.

## Failed Items

None.

## Final Decision

Decision: accepted-with-risks.

The Java engine aggregate join implementation is accepted for mainline delivery. The cut is appropriate for TMS development-stage semantic modeling and LLM-facing QM fields, with documented follow-up risks for broader database evidence and production optimization.

## Signoff Marker

accepted-with-risks: QueryModel Aggregate Join, Java engine 9.2.0, signed off by Codex on 2026-05-27.
