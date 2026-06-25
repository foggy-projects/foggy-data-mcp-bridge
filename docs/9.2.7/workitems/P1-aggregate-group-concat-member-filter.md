---
doc_role: workitem
doc_purpose: Track exact member filtering for GROUP_CONCAT aggregate relation aliases.
version: 9.2.7
target: P1-aggregate-group-concat-member-filter
status: implemented-installed
created_at: 2026-06-25
updated_at: 2026-06-25
owner_module: foggy-dataset-model
priority: P1
source_type: upstream-feedback
upstream_issue: foggy-projects/foggy-data-mcp-bridge#97
test_strategy: integration-test
automation_decision: required
---

# P1 Aggregate GROUP_CONCAT Member Filter

## Document Purpose

- doc_type: requirement-and-implementation-plan
- intended_for: execution-agent, reviewer, signoff-owner
- purpose: Record the agreed design and execution plan for filtering `GROUP_CONCAT` aggregate relation aliases by exact source member value.

## Background

Customer-style models often have a 1:N detail relation, for example one customer with multiple telephone rows. QueryModel aggregate join can expose a presentation field such as:

```js
customer.leftJoinAggregate(tel)
    .groupBy(tel.customerId)
    .groupConcat(tel.tel, 'linkmanTelList')
    .on(customer.customerId, tel.customerId)
```

The user-facing query should remain simple:

```json
{
  "columns": ["customerId", "customerName", "linkmanTelList"],
  "slice": [
    { "field": "linkmanTelList", "op": "=", "value": "18911897366" }
  ]
}
```

The current aggregate-output condition semantics would naturally mean `GROUP_CONCAT(tel.tel) = ?`, which is not the intended business query. The intended meaning is "customer has a telephone member equal to this value".

## Target Outcome

- QM authors can keep using `groupConcat(tel.tel, 'linkmanTelList')`.
- Callers can filter `linkmanTelList = '18911897366'` or `linkmanTelList in [...]` without knowing `CustomerTelModel.tel`.
- The engine rewrites the condition to a source-member predicate, preferably correlated `EXISTS`.
- The selected aggregate output remains the full telephone list, not only the matched member.
- Existing `like` behavior on aggregate string output remains unchanged.

## Non-Goals

- No new caller-facing DSL operator.
- No requirement for callers to write source fields such as `CustomerTelModel.tel`.
- No `!=` / `not in` rewrite in this cut because negative member semantics are ambiguous.
- No broad relation-filter language redesign.
- No raw SQL, viewSql, or caller-authored CTE.

## Agreed Design

Use a two-stage design:

1. A before-query pipeline classifier detects eligible conditions and records a rewrite plan in `ModelResultContext.extData`.
2. The SQL engine only consumes the recorded plan. It does not repeat complex aggregate-field/op inference in the core condition builder.

The marker should be a semantic plan, not raw SQL and not a boolean. It should identify:

- the condition object or stable condition path;
- aggregate output alias, for example `linkmanTelList`;
- source column lineage, for example `CustomerTelModel.tel`;
- aggregate relation join keys;
- supported op and value;
- rewrite target type, for example `GROUP_CONCAT_MEMBER_FILTER`.

Rendering should happen later, when the engine has current table aliases, dialect, and parameter context.

Expected SQL shape:

```sql
where exists (
  select 1
  from customer_tel tel_filter
  where tel_filter.customer_id = customer.customer_id
    and tel_filter.tel = ?
)
```

The aggregate join that renders `linkmanTelList` must remain independent, so a selected row still returns the full group-concat list.

## Module Responsibility

| Area | Owner | Responsibility |
|---|---|---|
| Root workspace | `foggy-data-mcp-bridge` | Versioned workitem, implementation tracking, quality and acceptance records. |
| Core engine module | `foggy-dataset-model` | Query pipeline marker, aggregate relation metadata support, SQL rewrite rendering, diagnostics, regression tests. |
| Demo/test templates | `foggy-dataset-demo` and/or `foggy-dataset-model/src/test/resources` | Minimal ecommerce/customer-style fixtures for exact member filtering. |
| MCP / public tool schema | none in this cut | No public DSL or schema change. Existing query payload shape is reused. |

## Code Inventory

| Repo | Path | Role | Expected Change | Notes |
|---|---|---|---|---|
| bridge | `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/plugins/result_set_filter/` | before-query lifecycle | update/create | Add a focused classifier step or reuse existing query preprocessing boundary to mark eligible slices. |
| bridge | `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/engine/` | QueryModel condition planning | update | Consume the rewrite plan with a thin hook before normal aggregate-output condition rendering. |
| bridge | `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/impl/model/` | aggregate relation metadata and SQL support | update | Expose enough relation/source metadata to render a correlated member predicate safely. |
| bridge | `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/engine/query/` | query condition carrier and visitor | update if needed | Preserve parameterized raw SQL fragments and alias-safe rendering. |
| bridge | `foggy-dataset-model/src/test/java/com/foggyframework/dataset/db/model/ecommerce/` | integration regression | update | Add real query execution tests for `=` / `in`, output preservation, and non-rewritten operators. |
| bridge | `foggy-dataset-model/src/test/resources/foggy/templates/ecommerce/` | test model fixtures | update/create | Add explicit `groupConcat(..., 'linkmanTelList')` aggregate join fixture if existing fixtures are not enough. |
| bridge | `docs/9.2.7/` | version tracking | update | Record progress, quality, coverage, and acceptance evidence. |

## Implementation Plan

### Stage 1. Classifier and Plan Carrier

- Add a small aggregate-member-filter classifier used by the before-query pipeline.
- Traverse structured `slice` leaves under AND-safe contexts.
- Resolve each field through the current `JdbcQueryModel`.
- Match only aggregate relation output columns whose aggregation is `GROUP_CONCAT`.
- Match only `=` and `in` with non-empty values.
- Store rewrite plans in `ModelResultContext.extData` using a reserved internal key.
- Keep a fallback classifier at the engine entry for direct tests or legacy callers that bypass `beforeQuery`, but reuse the same classifier implementation.

### Stage 2. Engine Consumption

- In single-condition building, check whether the current slice has a rewrite plan.
- If present, render the source-member predicate and return without adding the normal `linkmanTelList = ?` outer condition.
- If absent, preserve the existing behavior exactly.
- Do not mutate the caller request payload.

### Stage 3. Aggregate Relation SQL Support

- Render a parameterized correlated member predicate from aggregate relation metadata.
- Prefer `EXISTS` to RHS prefiltering so the selected aggregate list is not narrowed to the matched values.
- Include aggregate relation fixed filters and required RHS source joins when the source member field or relation keys require them.
- Keep source values bound through JDBC parameters.
- Add diagnostics indicating that the alias condition was rewritten as a member filter.

### Stage 4. Tests and Evidence

- Test `linkmanTelList = '...'` returns customers with that phone.
- Test returned `linkmanTelList` remains the full list when the customer has multiple phones.
- Test `linkmanTelList in [...]` works.
- Test `like` still uses existing aggregate string behavior and is not rewritten as member equality.
- Test unsupported negative ops are not rewritten.
- Test direct engine path, before-query pipeline planning, and QueryFacade lifecycle do not miss the classifier.
- Use real query execution parity, not only SQL substring assertions.

## Implementation Progress

- status: implemented-installed
- implemented_at: 2026-06-25
- classifier: `AggregateMemberFilterPlanner` records AND-safe `GROUP_CONCAT` alias `=` / `in` plans in `ModelResultContext.extData`.
- pipeline step: `AggregateMemberFilterRewriteStep` runs before auto-grouping and reuses the classifier.
- engine hook: `JdbcModelQueryEngine` consumes only recorded plans before normal aggregate-output condition rendering.
- SQL support: `AggregateRelationQueryObject` exposes member-filter SQL construction; `AggregateJoinTableModel` renders correlated parameterized `EXISTS`.
- diagnostics: `AggregateRelationDiagnostic` records `rewritten` / `member` evidence for alias member filters.
- regression fixtures: ecommerce string aggregate TM/QM fixtures and `AggregateJoinQueryModelTest` cover `like`, `=`, `in`, negative operators not rewritten, direct OR retained behavior, nested AND-under-OR retained behavior, before-query plan recording, and QueryFacade lifecycle behavior.

## Acceptance Criteria

- Exact member filter works through the public alias with no caller-side source-field knowledge.
- Generated SQL does not compare the aggregate alias/string expression with `= ?` for rewritten conditions.
- Generated SQL uses parameterized source-member predicates.
- Aggregate output selection is preserved as full list.
- Unsupported operators do not silently change semantics.
- Existing aggregate relation test suite still passes.
- Progress, quality review, coverage audit, and acceptance records are updated before signoff.

## Verification Evidence

| Scope | Command | Result | Evidence |
|---|---|---|---|
| Compile | `mvn -pl foggy-dataset-model -DskipTests compile` | pass | Main module compiled after engine changes. |
| Focused SQLite regression | `mvn -pl foggy-dataset-model "-Dspring.profiles.active=sqlite" "-Dtest=AggregateJoinQueryModelTest#aggregateRelationGroupConcatMeasureShouldRenderAndFilterByLike+aggregateRelationGroupConcatMeasureEqualsShouldRewriteToMemberExists+aggregateRelationGroupConcatMeasureInShouldRewriteToMemberExists+aggregateRelationGroupConcatEqualsInsideOrShouldStayOuterOnly+aggregateRelationGroupConcatEqualsInsideNestedAndUnderOrShouldStayOuterOnly" test` | pass | 5 tests, 0 failures, 0 errors. |
| Coverage gap closure | `mvn -pl foggy-dataset-model "-Dspring.profiles.active=sqlite" "-Dtest=AggregateJoinQueryModelTest#aggregateRelationGroupConcatMeasureEqualsShouldRewriteToMemberExists+aggregateRelationGroupConcatMeasureInShouldRewriteToMemberExists+aggregateRelationGroupConcatNegativeOpsShouldNotRewriteToMemberExists+aggregateMemberFilterRewriteStepShouldPlanBeforeEngineFallback+aggregateRelationGroupConcatMemberFilterShouldWorkThroughQueryFacade" test` | pass | 5 selected tests, 0 failures, 0 errors. |
| Broader SQLite regression | `mvn -pl foggy-dataset-model "-Dspring.profiles.active=sqlite" "-Dtest=AggregateJoinQueryModelTest,ColumnObjectNormalizerF5Test,AggregateRelationDiagnosticContractTest" test` | pass | Maven exited with build success. |
| Focused MySQL member-filter regression | `mvn -pl foggy-dataset-model "-P!multi-db" "-Dspring.profiles.active=docker" "-Dtest=AggregateJoinQueryModelTest#aggregateRelationGroupConcatMeasureEqualsShouldRewriteToMemberExists+aggregateRelationGroupConcatMeasureInShouldRewriteToMemberExists" test` | pass | 2 tests, 0 failures, 0 errors. |
| Focused SQLite member-filter regression after dynamic fixture adjustment | `mvn -pl foggy-dataset-model "-P!multi-db" "-Dspring.profiles.active=sqlite" "-Dtest=AggregateJoinQueryModelTest#aggregateRelationGroupConcatMeasureEqualsShouldRewriteToMemberExists+aggregateRelationGroupConcatMeasureInShouldRewriteToMemberExists" test` | pass | 2 tests, 0 failures, 0 errors. |
| Full Maven regression | `mvn test` | pass | Root reactor 25 modules succeeded. `foggy-dataset-model` ran default/sqlite, `test-mysql`/docker, and `test-postgres`; `AggregateJoinQueryModelTest` passed 61 tests in each execution. |
| Local Maven install | `mvn install` | pass | Root reactor 25 modules succeeded and artifacts were installed to the local Maven repository. |

## Review Update

- implementation_self_check: passed
- design_review_status: still pass-with-implementation-cautions
- coverage_status: reviewed-ready-full-maven-test-passed
- acceptance_status: pending formal signoff
- install_status: local-maven-install-passed
- residual_risk: No known cross-dialect test gap for the scoped aggregate member filter path after root `mvn test`; formal acceptance signoff remains pending.

## Design Review

- review_object_type: total planning document
- review_result: pass-with-implementation-cautions
- alternative_considered: Rewrite by prefiltering RHS aggregate relation before `GROUP BY`.
- decision: rejected for this use case because it would narrow the returned `GROUP_CONCAT` value to matched members unless a second aggregate relation is introduced.
- complexity: contained if the classifier emits semantic plans and the engine only consumes those plans.
- risks:
  - OR groups can change boolean semantics if rewritten too broadly; MVP should stay AND-safe or add explicit OR regression tests.
  - Negative operators have ambiguous "not has member" semantics and are out of scope.
  - RHS dimension paths require alias-context support; tests should start with root source fields and add dimension coverage only if existing aggregate relation source context supports it cleanly.
- evidence_gap_before_implementation: no automated regression exists yet for member-equality rewrite.

## Experience Progress

experience: N/A

Reason: Backend/query-engine-only behavior; no UI page, form, list, or browser workflow changes.

## Completion Gates

- Execution agent read root `CLAUDE.md` and inherited its real SQL integration-test requirement.
- Implementation self-check completed for the scoped engine changes.
- Formal implementation quality gate remains required before final acceptance.
- Test coverage audit completed at `docs/9.2.7/coverage/P1-aggregate-group-concat-member-filter-coverage-audit.md`; previously identified negative-operator and QueryFacade lifecycle gaps are covered.
- Final acceptance signoff remains required after quality and coverage records are complete.
- Progress and targeted verification evidence have been written back to this workitem.
