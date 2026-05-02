# Development Handoff for Google Antigravity

You are working in Google Antigravity on this local workspace:

- Workspace: `D:\foggy-projects\foggy-data-mcp\foggy-data-mcp-bridge-wt-dev-compose`
- Repo/module: `foggy-dataset-model`
- Target task: run a Stage 5A spike for Pivot large-domain transport without changing the public Pivot DSL or enabling a production path by default.

Important: do not assume any Codex/Claude memory, previous chat, `CLAUDE.md`, `AGENTS.md`, or local skills have been loaded. Read the listed context files before making edits.

## Read First

- `docs/9.0.0.beta/detailed_design/09_pivot_stage5_domain_transport_spike_plan.md`: authoritative scope for this spike.
- `docs/9.0.0.beta/detailed_design/08_pivot_sql_topn_pushdown_refactor_plan.md`: Stage 1-4 architecture and accepted constraints.
- `docs/9.0.0.beta/acceptance/pivot-sql-topn-pushdown-acceptance.md`: formal Stage 1-4 signoff.
- `docs/9.0.0.beta/acceptance/pivot-sql-topn-pushdown-performance-baseline.md`: benchmark and large-domain decision evidence.
- `docs/9.0.0.beta/acceptance/pivot-sql-topn-pushdown-performance-baseline-acceptance.md`: performance baseline signoff.
- `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/engine/pivot/PivotPipeline.java`: pushdown/fallback integration.
- `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/engine/pivot/sql/PivotAxisDomainSqlPlanner.java`: current CTE/window TopN planner.
- `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/engine/pivot/rollup/NonAdditiveRollupExecutor.java`: current non-additive auxiliary query and `domain > 500` boundary.
- `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/engine/pivot/rollup/NonAdditiveRollupDomainTooLargeException.java`: current fail-closed exception.
- `foggy-dataset-model/src/test/java/com/foggyframework/dataset/db/model/engine/pivot/rollup/NonAdditiveRollupExecutorDomainSliceTest.java`: tuple/null/domain-boundary tests.
- `foggy-dataset-model/src/test/java/com/foggyframework/dataset/db/model/engine/pivot/PivotSqlParityIntegrationTest.java`: SQL parity examples.

## Background

Pivot SQL TopN/Having Pushdown Stage 1-4 is accepted. The remaining large-domain boundary is:

- Non-additive subtotal/grandTotal auxiliary queries receive the post-TopN surviving axis domain.
- If the domain exceeds `500`, the Java engine fails closed through `NonAdditiveRollupDomainTooLargeException`.
- This prevents unsafe huge `IN` / `OR-of-AND` SQL predicates and avoids silent wrong results.
- Performance baseline concluded Stage 5A is not required for 9.0.0.beta release.

This task is a spike, not production implementation. Its goal is to decide whether and how a future large-domain transport should replace huge predicates with a joinable domain relation.

## Scope

Stage 5A means large-domain transport only:

- `VALUES` CTE;
- `UNION ALL` domain CTE;
- temporary/session table;
- or a dialect-specific derived table.

Stage 5A does not mean:

- public DSL changes;
- cascade Generate;
- multi-level TopN semantics;
- tree SQL TopN;
- outer Pivot cache;
- bypassing queryModel.

## Implementation Plan

1. Inspect current code and tests.
   - Confirm where `MAX_IN_LIST_SIZE` / `500` is enforced.
   - Confirm where tuple correlation and null handling are rendered.
   - Confirm how base SQL params and auxiliary params are ordered.

2. Produce a dialect capability matrix.
   - SQLite, MySQL 8.0, PostgreSQL are required.
   - MySQL 5.7 should remain fallback/fail-closed unless there is already a safe path.
   - Include support notes for `VALUES`, `UNION ALL` CTE, temp table, null-safe join, parameter count, SQL text size, and transaction/session risk.

3. Design an internal transport abstraction.
   - Suggested names: `DomainTransportPlan`, `DomainRelationRenderer`, or project-consistent equivalents.
   - It must be internal; do not expose it in Pivot DSL.
   - It must preserve queryModel-managed relation semantics.
   - It must have a default fail-closed implementation.

4. Optional disabled prototype.
   - If implementation risk is low, prototype `UNION ALL Domain CTE` behind an internal flag.
   - The flag must default to off.
   - Do not remove or weaken the current `domain > 500` fail-closed behavior on the default path.

5. Test if prototype code is committed.
   - Tuple correlation with multiple axis fields.
   - Null tuple matching.
   - `preAgg + systemSlice + domain transport + non-additive subtotal`.
   - Params order: base params first, then domain transport params.
   - Dialect capability refusal.
   - Guarded fallback/fail-closed behavior.

6. Document the result.
   - Update or create a spike result doc under `docs/9.0.0.beta/acceptance/`.
   - Include go/no-go recommendation for 9.0.0.beta, 9.0.x, or 9.1.0.
   - Include Python mirror impact and whether Python must change its current plan.

## Constraints

- Do not change external Pivot DSL syntax or JSON schema.
- Do not add user-visible request options for domain transport.
- Do not bypass queryModel, `ManagedSqlRelation`, permission validation, preAgg rewrite, physical column validation, or parameter binding.
- Do not make large-domain transport default-on during the spike.
- Do not revert unrelated dirty working tree changes.
- Keep edits scoped. If uncertain, prefer documentation and design over production code.
- Stage 5B advanced semantics are out of scope.

## Verification

If only documentation is changed, run:

```powershell
git diff --check -- docs/9.0.0.beta
```

If production or test code is changed, run at minimum:

```powershell
mvn -pl foggy-dataset-model -P!multi-db "-DfailIfNoTests=false" "-Dtest=PivotSqlParityIntegrationTest#testSqlPushdownNonAdditiveRollupWithTopNAndSubtotalsParity+testSqlPushdownTriggeredInNormalExecution,PivotAxisDomainSqlPlannerTest#testBaseRelationParamsWithHavingAndLimit,NonAdditiveRollupExecutorDomainSliceTest" test
```

If cross-database behavior is touched, also run the project SQLite / MySQL 8.0 / PostgreSQL profiles used by the release readiness flow.

## Deliverable

Report back in this format:

```markdown
## Stage 5A Spike Completion

- Changed files:
- Prototype committed: yes/no
- Default behavior changed: yes/no
- Dialect recommendation:
- Python impact:
- Commands run:
- Test results:
- Go/no-go recommendation:
- Remaining risks:
```
