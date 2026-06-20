---
doc_role: workitem
doc_purpose: Record and evaluate an optional before-execute pipeline loop hook for bounded convergence-only engine sub-pipelines.
version: 9.2.0
target: Java engine pipeline extension boundary
status: plan-normalize-pipeline-local-verified
priority: medium
owner: foggy-dataset-model
created_at: 2026-06-20
updated_at: 2026-06-20
---

# OPT: Optional Pipeline Loop Hook Boundary

## Purpose

Record the design boundary for adding an optional loop hook after the existing before-execute pipeline mainline completes successfully.

The goal is not to make the main query pipeline cyclic. The goal is to support a small number of convergence-style sub-pipelines, such as QueryPlan normalization or pre-aggregation strategy fallback, without copying ad hoc retry/fallback logic into each caller.

The current implementation is intentionally before-execute only. The after-execute pipeline often owns result post-processing, cache/store decisions, diagnostics, and audit-facing state; looping it risks duplicate result mutation and misleading evidence.

## Current Agreement

The existing main pipeline remains one-way and deterministic:

```text
executeBeforeExecute(ctx)
-> runBeforeLoopHookUntilStop(ctx)

executeAfterExecute(ctx)
-> no loop hook
```

The loop hook is a separate extension point. It must not re-enter the main pipeline method.

Default behavior remains no loop:

```text
maxLoopCount = 0
supportsLoop(ctx) = false
runLoop(ctx) = stop / no-op
```

Only specifically approved pipeline steps may implement loop behavior.

## Proposed API Shape

The concrete class names are not final. The intended contract is:

```java
public interface PipelineStep<C extends LoopablePipelineContext> {

    int run(C ctx);

    default boolean supportsLoop(C ctx) {
        return false;
    }

    default LoopDecision runLoop(C ctx) {
        return LoopDecision.stop("not loopable");
    }
}
```

The loop-aware context must carry loop state separately from ordinary business `extData`:

```java
public interface LoopablePipelineContext {

    int getLoopIndex();

    int getMaxLoopCount();

    boolean isLoopStopRequested();

    void requestLoopStop(String reason);

    boolean isLoopChanged();

    void markLoopChanged();

    void clearLoopChanged();

    void addLoopTrace(String stepName, String action, String reason);
}
```

The runner contract:

```text
1. Execute the existing before-execute main pipeline once.
2. If no step supports loop, return immediately.
3. Execute only loop-capable steps in bounded iterations.
4. Stop when a step requests stop.
5. Stop when one full loop produces no change.
6. Fail closed when maxLoopCount is exceeded.
7. Record per-iteration trace.
8. Never run loop hooks from after-execute.
```

## Candidate Pipelines

| Candidate | Recommendation | Reason |
|---|---|---|
| QueryPlan / CTE normalization | Good first candidate | It operates on an AST-like plan tree and may need bounded convergence for CTE hoisting, alias normalization, empty wrapper removal, and adjacent `DerivedQueryPlan` merge. |
| PreAgg rewrite/fallback | Possible, but better as strategy chain | Direct/hybrid/aggregate/original fallback can be modeled as first-applied candidates. It benefits from shared loop context less than QueryPlan normalization. |
| calculatedFields / rollup dependency analysis | Possible later | A graph fixed-point analyzer may replace recursive dependency propagation if calculatedField semantics grow. |
| Semantic `plan / execute / generateSql / validate` entrypoints | Do not loop | These are public mainline entrypoints and must remain deterministic. |
| Request normalization | Do not loop | Repeated normalization risks duplicated slice conversion and request mutation drift. |
| Permission validation | Do not loop | Repeated permission steps risk duplicate filtering and misleading audit evidence. |
| SQL render | Do not loop | Re-render loops can hide alias or parameter binding bugs. |

## Design Review

- Review object type: architecture / execution planning document.
- Review result: conditionally accepted.

This design is worth keeping as a narrow extension point, but it should not be implemented globally before a concrete first consumer exists.

The first acceptable consumer is `PlanNormalizePipeline`. It has natural convergence semantics and keeps the loop away from database execution, permissions, request mutation, and final SQL rendering.

During implementation review, the after-execute hook was removed from scope. The useful lifecycle boundary is before-execute normalization/fallback only; after-execute must remain a single pass.

## Positive Impact

- Makes convergence-style transformations explicit instead of scattering repeat/fallback logic across callers.
- Keeps the existing main pipeline stable while still allowing controlled second-phase behavior.
- Provides a standard place for loop limit, changed/unchanged decisions, and diagnostic trace.
- Reduces risk of future CTE/alias/plan-wrapper code duplicating the same normalization in multiple entrypoints.
- Gives tests a clear observable contract: iteration count, changed flag, stop reason, and final normalized plan.

## Negative Impact

- Adds a second lifecycle phase to pipeline execution, which can confuse maintainers if naming and trace are weak.
- Step authors may misuse `runLoop` to perform business mutation that belongs in the main pipeline.
- A broad loop abstraction can hide bugs by making repeated processing eventually "work".
- Context state becomes more complex and must be isolated from ordinary `extData`.
- Without strong test gates, max-iteration failures could appear only in rare nested plan shapes.

## Guardrails

- Default loop count must be zero.
- The loop phase must not call the main pipeline method again.
- The loop phase must run only after before-execute completes with `CONTINUE`.
- The loop phase must not run after after-execute.
- Loop state must be isolated from normal request/result `extData`.
- Stale stop/change state must be cleared before probing `supportsLoop`.
- Each loop step must be idempotent or explicitly convergence-oriented.
- Each loop iteration must emit trace with step name, action, changed flag, and stop reason.
- Exceeding `maxLoopCount` must fail closed instead of silently using the last state.
- Loop steps must not execute SQL, perform permission validation, or mutate user request semantics.
- Raw SQL / raw CTE exposure remains out of scope.

## Implementation Gate

The first checked-in slice is default-off `QueryExecutionStep` infrastructure only. This is allowed because no business step opts in and `maxLoopCount` defaults to zero. Any semantic consumer still requires its own workitem, tests, and acceptance evidence.

The recommended first implementation slice is:

```text
PlanNormalizePipeline
-> loop-aware context
-> CTE/DerivedPlan normalization rules
-> unit tests for convergence, no-change stop, max-loop fail-closed, and trace
```

PreAgg should be revisited separately as a `RewriteStrategyChain` before deciding whether it actually needs the shared loop hook.

## PlanNormalizePipeline Plan

`PlanNormalizePipeline` is implemented as an independent compose-plan normalization pipeline, not as a `QueryExecutionStep`.

Package boundary:

```text
engine.compose.normalization
-> PlanNormalizePipeline
-> PlanNormalizeContext implements LoopablePipelineContext
-> PlanNormalizeRule
-> PlanNormalizeResult
```

API shape:

```java
public final class PlanNormalizePipeline {

    public PlanNormalizeResult normalize(QueryPlan plan, PlanNormalizeOptions options) {
        // run ordered rules until unchanged, explicit stop, or maxLoopCount
    }
}

public interface PlanNormalizeRule {

    LoopDecision apply(PlanNormalizeContext ctx);
}
```

Default boundary:

- `maxLoopCount = 4`.
- Input/output is immutable `QueryPlan`; no SQL execution.
- The pipeline may reuse `LoopDecision`, but should own `PlanNormalizeContext` instead of reusing `QueryExecutionContext`.
- It should run before `ComposeSqlCompiler` / `ComposeRelationCompiler` lower the plan into SQL.
- It must not perform authority resolution, permission validation, request normalization, raw SQL parsing, or pre-aggregation rewrite.

First implemented rule:

- Remove empty `DerivedQueryPlan` wrappers when no columns, slice, groupBy, orderBy, limit, start, or distinct state exists.

Deferred rule candidates:

- Merge adjacent `DerivedQueryPlan` wrappers only when final controls and projection semantics remain equivalent.
- Normalize structured CTE carriage before relation rendering, preserving `RelationSql.flattenParams()` order.
- Validate alias normalization idempotency so repeated passes do not rewrite the same alias twice.

Required tests before enabling as a compile pre-phase:

- No-change plan returns the original or structurally equivalent plan and stops without extra passes.
- Nested CTE stages hoist once, with stable parameter order.
- Final order/limit wrappers do not repeatedly wrap.
- Alias rewrite is idempotent across repeated loop attempts.
- A deliberately toggling rule fails closed at `maxLoopCount`.
- Compiled SQL before/after normalization remains equivalent for the safe-wrapper cases.

Implementation boundary:

- `PlanNormalizePipeline.defaults()` currently enables only `RemoveEmptyDerivedQueryPlanRule`.
- It is not connected to `ComposeSqlCompiler` or `ComposeRelationCompiler` by default.
- It can be invoked explicitly by future compile pre-phase work.
- `RemoveEmptyDerivedQueryPlanRule` is deliberately conservative: it does not rebuild a non-empty `DerivedQueryPlan` just to normalize its source, because plan-qualified column visibility is identity-based. This avoids breaking references that were validated against the original source chain.

## Test Strategy

Required unit tests for the first consumer:

- no loop-capable steps keeps current behavior unchanged.
- one loop-capable step stops after unchanged iteration.
- multiple loop-capable steps preserve deterministic order.
- max loop count is enforced and fails closed.
- stop reason and per-step trace are recorded.
- QueryPlan normalization does not execute SQL.
- request normalization, permission validation, and SQL render paths are not invoked from loop phase.
- after-execute does not run loop hooks.
- stale loop stop state is cleared before `supportsLoop` is checked.

Required regression tests for future compile pre-phase enablement:

- nested structured CTE stages hoist once and do not duplicate.
- adjacent `DerivedQueryPlan` wrappers merge without changing parameter order.
- final order/limit wrapper does not repeatedly wrap.
- alias rewrite is idempotent across repeated loop attempts.
- validate/generateSql/execute still observe the same normalized plan once the compile pre-phase is enabled.

## Decision

Adopt the design as a documented optional extension boundary.

Do not retrofit the existing main pipeline or all step executors. Keep the current `QueryExecutionStep` hook default-off and before-only. Implement the first convergence-style semantic consumer separately, preferably `PlanNormalizePipeline`.

The design is useful if it remains small, bounded, traceable, and opt-in. It becomes harmful if treated as a general-purpose retry mechanism for query execution.

## Implementation Check-In

2026-06-20: the first infrastructure slice is implemented for `QueryExecutionStep`.

Implemented scope:

- Added `LoopDecision`, `LoopTraceEntry`, and `LoopablePipelineContext`.
- Added default `supportsLoop(phase, ctx)` and `runLoop(phase, ctx)` methods to `QueryExecutionStep`.
- Added loop state to `QueryExecutionContext`, including `maxLoopCount`, current loop index, stop reason, changed flag, and isolated loop trace.
- Updated `QueryExecutionStepExecutor` to run loop hooks only after the main before-execute pipeline completes with `CONTINUE`.
- Kept after-execute single-pass; it never runs loop hooks.
- Preserved default no-loop behavior through `maxLoopCount = 0` and `supportsLoop = false`.
- Ensured an aborted main pipeline does not enter loop hooks.
- Enforced fail-closed behavior when every allowed loop pass still reports `changed`.
- Added a regression guard for stale stop state when the same context instance is reused; stop state is cleared before `supportsLoop` is probed.

Not implemented in this slice:

- No `DataSetResultStep` loop hook.
- No `PlanNormalizePipeline` compiler pre-phase integration in this slice.
- No PreAgg strategy-chain migration.
- No existing business step opts into loop behavior.

## PlanNormalizePipeline Check-In

2026-06-20: the first compose-plan normalization consumer is implemented, but not wired into the compile main path.

Implemented scope:

- Added `engine.compose.normalization.PlanNormalizePipeline`.
- Added `PlanNormalizeContext`, `PlanNormalizeOptions`, `PlanNormalizeResult`, and `PlanNormalizeRule`.
- Reused shared `LoopDecision` / `LoopTraceEntry` for bounded convergence diagnostics.
- Added `RemoveEmptyDerivedQueryPlanRule` as the first default rule.
- Kept default max loop count at 4 for explicit plan normalization.
- Kept compile/render/permission/request paths unchanged; no compiler entrypoint invokes the normalizer yet.

Covered behavior:

- Empty rule list returns original plan without loop.
- `maxLoopCount = 0` disables normalization rules.
- Unchanged rule stops after one pass.
- Changed-then-unchanged rule stops with trace.
- Explicit stop records reason.
- Fail decision fails closed.
- Always-changing rule fails closed at `maxLoopCount`.
- Empty root `DerivedQueryPlan` wrappers are removed.
- Non-empty `DerivedQueryPlan` wrappers are preserved.
- Empty wrappers inside `JoinPlan` / `UnionPlan` branches are removed while preserving branch plans.

Still not implemented:

- No adjacent non-empty derived merge.
- No structured CTE hoist normalization.
- No alias rewrite rule.
- No default compiler pre-phase wiring.

## Verification

Passed on 2026-06-20:

```powershell
mvn -pl foggy-dataset-model "-Dtest=QueryExecutionStepExecutorPhaseTest,QueryExecutionStepExecutorLoopHookTest" test
mvn -pl foggy-dataset-model "-Dtest=PlanNormalizePipelineTest" test
mvn -pl foggy-dataset-model "-Dtest=PlanNormalizePipelineTest,QueryExecutionStepExecutorPhaseTest,QueryExecutionStepExecutorLoopHookTest,DerivedLoweringTest,ComposeSqlCompilerTest,ComposeRelationCompilerTest" test
```

Result:

- Default, MySQL, and PostgreSQL surefire executions passed.
- Each execution ran 8 loop-hook tests plus the phase tests, 0 failures, 0 errors.
- `PlanNormalizePipelineTest` ran 10 tests in each surefire execution, 0 failures, 0 errors.
- The compose/compiler regression command ran 101 tests in each surefire execution, 0 failures, 0 errors.

Covered behavior:

- `maxLoopCount = 0` disables loop hooks.
- Steps that do not support loop are not called.
- Loop stops on unchanged iteration.
- Loop honors explicit stop decision.
- Loop exceeds max count with fail-closed exception.
- Main pipeline abort skips loop execution.
- after-execute skips loop execution.
- Stale stop state is cleared before the next `supportsLoop` probe.
