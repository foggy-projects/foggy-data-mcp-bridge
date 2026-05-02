# Google Antigravity Execution Prompt: Pivot SQL TopN Pushdown Performance Baseline

You are working in Google Antigravity on this local workspace:

- Workspace: `D:\foggy-projects\foggy-data-mcp\foggy-data-mcp-bridge-wt-dev-compose`
- Repo/module: `foggy-dataset-model`
- Target task: establish a repeatable performance baseline for Pivot SQL TopN/Having Pushdown and evaluate whether the accepted `domain > 500` fail-closed boundary needs a separate large-domain transport project.

Important: do not assume Codex/Claude memory, previous chat, `CLAUDE.md`, `AGENTS.md`, or local skills have been loaded. Read the listed context files before making edits.

## Read First

- `docs/9.0.0.beta/detailed_design/08_pivot_sql_topn_pushdown_refactor_plan.md`: architecture, rejected approaches, managed relation design, Stage 1-4 decisions.
- `docs/9.0.0.beta/acceptance/pivot-sql-topn-pushdown-acceptance.md`: formal signoff, accepted risks, current evidence.
- `docs/9.0.0.beta/test_coverage/pivot-sql-topn-pushdown-coverage-audit.md`: coverage state and remaining boundary.
- `docs/9.0.0.beta/acceptance/version-signoff.md`: version-level acceptance context.
- `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/engine/pivot/PivotPipeline.java`: pushdown entry, fallback behavior, phase sequencing.
- `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/engine/pivot/sql/PivotAxisDomainSqlPlanner.java`: CTE/window SQL domain planning.
- `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/engine/pivot/rollup/NonAdditiveRollupExecutor.java`: non-additive subtotal/grandTotal auxiliary queries and domain bounding.
- `foggy-dataset-model/src/test/java/com/foggyframework/dataset/db/model/engine/pivot/PivotSqlParityIntegrationTest.java`: integration oracle examples.
- Check whether `CLAUDE.md`, `AGENTS.md`, or equivalent repo instructions exist in the workspace and follow them if present.

## Background

Pivot SQL TopN/Having Pushdown Stage 1-4 has been accepted with non-blocking risks:

- SQL pushdown must continue to go through managed queryModel relation. Do not bypass permission validation, pre-aggregation rewrite, physical column checks, or queryModel SQL generation.
- Unsupported dialects or unsafe semantics must fail closed to the memory path.
- Stage 4 fixed non-additive rollup alignment: subtotals and grandTotal must be bounded to the post-TopN domain, including full tuple correlation and null semantics.
- Current large-domain boundary: non-additive subtotal/grandTotal domain size greater than 500 fails closed through `NonAdditiveRollupDomainTooLargeException`.
- MySQL 5.7 is accepted as memory fallback only because it lacks the required CTE/window capabilities.

This task is not Stage 5. Do not implement cascade Generate, tree SQL TopN, public DSL changes, or a large-domain transport production feature. The goal is evidence: quantify performance gain, fallback behavior, and whether `domain > 500` is likely to matter.

## Implementation Plan

1. Inspect current performance observability:
   - Find existing SQL logging, Pivot phase logging, integration test fixtures, and demo data builders.
   - Prefer using existing test infrastructure over adding a new benchmark framework.
   - If production code lacks row-count/phase timing hooks, use test-side timing/log capture first.

2. Build a repeatable local baseline:
   - Use deterministic synthetic data if current demo data is too small.
   - Keep generated data local to tests or helper fixtures.
   - Avoid committing large binary/data files.
   - Target enough rows to make memory path vs SQL pushdown visibly different; if local runtime becomes too high, document the limit and use the largest practical dataset.

3. Compare at least these scenarios:
   - S1: single row axis + `limit/orderBy` using additive metric.
   - S2: rows + columns + `limit/orderBy` using additive metric.
   - S3: axis `having` + TopN co-pushdown.
   - S4: TopN + `COUNT_DISTINCT` + rowSubtotals + grandTotal.
   - S5: unsupported dialect or forced capability refusal fallback path.

4. Compare execution modes:
   - SQL pushdown enabled.
   - Memory fallback path.
   - Guarded unsupported-dialect fallback where feasible.
   - If a mode cannot be isolated cleanly without production changes, document why and use the closest existing switch/test seam.

5. Capture metrics:
   - input fact-row scale;
   - leaf groups before domain filtering if measurable;
   - DB returned row count;
   - final pivot output row count;
   - total query time;
   - Phase 1 / Phase 2 / non-additive auxiliary time if available;
   - generated SQL shape summary;
   - fallback reason when fallback occurs;
   - JVM memory observation if practical, but do not block on perfect memory measurement.

6. Large-domain trigger assessment:
   - Determine how easily `domain > 500` is reached under realistic TopN/subtotal requests.
   - Do not replace the current fail-closed implementation.
   - Compare future options only at design level: CTE value table, temp table, dialect-specific tuple join, and keeping fail-closed.
   - Explicitly evaluate parameter count limits, SQL length, dialect support, transaction cleanup, cache impact, and queryModel permission/preAgg preservation.

7. Documentation:
   - Create `docs/9.0.0.beta/acceptance/pivot-sql-topn-pushdown-performance-baseline.md`.
   - Do not update the formal acceptance decision unless the evidence shows a correctness or release-blocking problem.
   - If evidence changes the risk level, call that out clearly and leave final signoff to Codex.

## Constraints

- Do not revert unrelated changes in the working tree.
- Start by inspecting `git status --short`; preserve unrelated user changes.
- Keep code changes scoped to tests, benchmark helpers, or clearly isolated experimental code unless a production change is explicitly approved.
- Do not bypass queryModel to make a benchmark look faster.
- Do not weaken fail-closed behavior for `domain > 500` without a reviewed replacement design.
- Do not make Stage 5 changes.
- Do not add JMH or a heavy benchmark framework unless existing project conventions already support it; test-side measurement is enough for this evidence pass.
- Preserve SQLite default tests; external DB profiles may require Docker containers.

## Verification

Run the existing acceptance subset before and after any change:

```powershell
mvn -pl foggy-dataset-model -P!multi-db "-DfailIfNoTests=false" "-Dtest=PivotSqlParityIntegrationTest#testSqlPushdownNonAdditiveRollupWithTopNAndSubtotalsParity+testSqlPushdownTriggeredInNormalExecution,PivotAxisDomainSqlPlannerTest#testBaseRelationParamsWithHavingAndLimit,NonAdditiveRollupExecutorDomainSliceTest" test
```

If cross-database verification is in scope, also run the relevant SQLite, MySQL 5.7 fallback, MySQL 8, and PostgreSQL profiles used by the project.

At minimum, after any code/test fixture change run:

```powershell
mvn -pl foggy-dataset-model -P!multi-db "-DfailIfNoTests=false" "-Dtest=PivotSqlParityIntegrationTest,NonAdditiveRollupExecutorDomainSliceTest,PivotAxisDomainSqlPlannerTest" test
```

## Deliverable

Produce `docs/9.0.0.beta/acceptance/pivot-sql-topn-pushdown-performance-baseline.md` with:

- document purpose and scope;
- changed files, if any;
- commands run and results;
- performance comparison table;
- scenario-by-scenario interpretation;
- large-domain trigger assessment;
- recommendation for keeping fail-closed vs implementing a large-domain transport;
- explicit risks and maintenance cost;
- whether the original acceptance decision should remain `accepted-with-risks` or be updated.

When finished, report back in this format so Codex can perform acceptance review:

```markdown
## Performance Baseline Completion

- Changed files:
- Commands run:
- Test results:
- Baseline doc:
- Main findings:
- Recommendation:
- Risks / blockers:
- Ready for Codex acceptance: yes/no
```
