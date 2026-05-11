---
doc_role: workitem
doc_purpose: Track follow-up planning for compose source alias lexical scope, closure behavior, and ambiguity diagnostics.
version: v3.0
ticket: REQ-compose-source-alias-lexical-scope-and-ambiguity
priority: P2
status: planning-required
source_type: requirement-followup
owner: foggy-dataset-model
created_at: 2026-05-11
---

# REQ: Compose Source Alias Lexical Scope and Ambiguity

## Document Purpose

- doc_type: follow-up requirement / planning guardrail
- intended_for: compose-engine-owner | fsscript-owner | java-engine-owner | python-engine-owner | reviewer
- purpose: Record the known boundary after the Java qualified alias parity pass and require scenario alignment before any broader function/closure/source-alias development starts.

## Background

The Java parity workitem `REQ-compose-join-qualified-field-references-java-parity.md`
implemented the current benchmark-safe contract:

- top-level fsscript assignment aliases such as `firstOrders.partner$caption`;
- side aliases such as `left.firstOrderDate` and `right.totalAmount`;
- source alias inheritance through derived plans, gated by current output schema;
- deterministic tests for the common post-join projection path.

That implementation is intentionally scoped to linear compose scripts and common LLM-generated benchmark payloads. It does not claim full lexical-scope semantics for arbitrary fsscript functions, closures, factories, or highly dynamic CTE composition.

## Risk Statement

Current source alias metadata is attached to `QueryPlan` instances. In more complex scripts, this can differ from strict lexical variable visibility.

Potential risk scenarios:

1. A plan created inside a function may carry an internal variable alias outside that function's lexical scope.
2. A factory function called multiple times may create multiple plans with the same internal alias, for example `orders`, causing ambiguous downstream references.
3. Nested scopes may shadow an outer variable name, but the current plan metadata does not model lexical shadowing.
4. Future expression support such as `SUM(firstOrders.amount)` or `firstOrders.amount / mayOrders.total` would require AST-level qualified reference normalization, not only simple `prefix.field` string handling.
5. Qualified aliases could become confusing in business-facing scripts if users expect JavaScript lexical names, SQL CTE names, and compose source aliases to be identical concepts.

## Planning Requirement

Do not start implementation for this follow-up until the owning team has agreed on expected business semantics and user-facing behavior.

Before development, the team must document:

- representative script shapes that product/support/technical users expect to allow;
- whether aliases created inside function scope are externally addressable after the function returns;
- whether repeated factory calls with the same internal variable name should be allowed, ignored, or rejected as ambiguous;
- how lexical shadowing should interact with plan provenance;
- how source aliases differ from SQL CTE aliases and final result column aliases;
- whether ambiguity diagnostics should be fail-closed before SQL generation;
- whether the contract must be implemented in Python first, Java first, or both in lockstep.

## Candidate Principle Set

These principles are proposed for discussion, not yet approved for implementation:

1. Source aliases should be convenience names for plan provenance, not SQL CTE names.
2. Alias resolution should fail closed when the same source alias can identify more than one visible branch.
3. Function-local aliases should not become a public addressing contract unless explicitly returned or rebound by the caller.
4. Rebinding a returned plan at the caller scope may add a new alias, but should not make inaccessible dropped fields visible again.
5. Qualified references inside complex expressions should be normalized by structured expression parsing, not by ad hoc substring replacement.
6. User-facing documentation should avoid advertising full closure/factory CTE authoring until these semantics are deterministic.

## Non-Goals for Current Parity Pass

This follow-up does not reopen the completed Java parity implementation for:

- benchmark-safe top-level `firstOrders.partner$caption` references;
- `left.` / `right.` side alias references;
- source alias inheritance through straightforward derived plans;
- duplicate join keys used only in `join(on)`;
- dropped-field fail-closed behavior.

Those remain accepted for the current release-scope parity slice.

## Required Future Regression Scenarios

When implementation starts, add deterministic tests for at least:

- function-local plan alias used outside the function;
- returned plan rebound to a caller alias;
- two factory calls producing plans with the same internal alias and then joined;
- nested shadowed variable names;
- source alias references inside `columns`, `slice`, `groupBy`, `orderBy`, and any future `having` surface;
- denied or dropped fields accessed through function-created aliases;
- parity shape in Python and Java if both engines expose the same public contract.

## Current Status

- planning_status: not-started
- implementation_status: not-started
- release_impact: not blocking the current benchmark-safe compose join alias parity pass
- required_next_step: scenario review and principle signoff before coding
