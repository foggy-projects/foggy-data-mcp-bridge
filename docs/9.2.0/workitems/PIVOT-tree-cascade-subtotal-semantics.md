---
doc_role: workitem
doc_purpose: Record the 9.2.0 semantic review for Pivot tree mode with cascade and totals.
version: 9.2.0
target: Java Pivot tree/cascade/subtotal semantics
status: semantic-review-complete-fail-closed
created_at: 2026-06-12
updated_at: 2026-06-12
---

# Pivot Tree Cascade Subtotal Semantics

## Purpose

`PIVOT-92-D1` exists because tree-shaped Pivot output, cascade TopN, and subtotal/grand-total semantics look similar to users but have different correctness rules.

The 9.2.0 engine must keep these combinations explicit and fail-closed until the semantics can be proven with oracle fixtures.

## Current Signed Scope

The accepted Pivot cascade subset remains C2 v1:

- Rows-axis cascade only.
- Exactly two rows levels.
- Additive metrics only.
- No column-axis cascade.
- No tree hierarchy mode.
- No parent-share or baseline-ratio cascade.
- No memory fallback for unsupported cascade shapes.

Tree mode remains an output shaping mode with a stricter contract:

- Tree is rows-axis only.
- Tree output must use `outputFormat=tree`.
- Tree does not support crossjoin.
- Tree does not support `domainSlice`, `start`, or `offset`.
- Tree can contain at most one tree hierarchy field.

## Current Fail-Closed Matrix

| Shape | Current Behavior | Reason |
|---|---|---|
| Column-axis `hierarchyMode=tree` | rejected | The tree renderer and axis contract are rows-axis only. |
| Tree with non-tree output format | rejected | A caller that asks for tree semantics must accept the tree envelope. |
| Tree with crossjoin | rejected | Crossjoin would flatten independent dimensions and break ancestor/child rendering. |
| More than one rows tree field | rejected | Multi-tree expansion has no stable ancestor merge contract. |
| Tree with `domainSlice`, `start`, or `offset` | rejected | Cursor and paging semantics for domain trees are not defined. |
| Tree with `baselineRatio` | rejected | Baseline selection across ancestor/descendant rows is ambiguous. |
| Tree with `parentShare` | rejected | Parent-share over tree totals needs a signed parent denominator rule. |
| Tree with cascade TopN | rejected through `PIVOT_CASCADE_TREE_REJECTED` | Ranking may mean top parents, top children per parent, or top visible nodes; the DSL does not disambiguate. |
| Tree with row/column subtotals or grand total | options disabled before execution | The engine does not currently sign a tree totals contract; this should be surfaced through explicit diagnostics before future acceptance. |

## Semantic Review Conclusion

Tree plus cascade must remain refused in 9.2.0. The current DSL cannot distinguish these different user intents:

- Rank parents first, then show all descendants.
- Rank each parent's children independently.
- Rank only visible nodes after expansion.
- Aggregate over all descendants, or only over nodes visible after TopN/paging.

Subtotal and grand-total behavior is also not signed for tree output. The current implementation disables subtotal and grand-total options when tree mode is used. That preserves correctness by avoiding unsupported totals, but a future implementation should expose an explicit response diagnostic or warning so AI/debug consumers can explain why totals are absent.

## Future Acceptance Gates

Tree + cascade or tree totals can only move from refused to supported after these gates are met:

| Gate | Requirement |
|---|---|
| Oracle fixture | A stable parent/child dataset with at least three levels, multiple siblings, zero/NULL metric rows, and ties. |
| Ranking rule | The requirement must choose top parents, top children per parent, or top visible nodes. |
| Total rule | The requirement must choose full-descendant totals or visible-domain totals. |
| Ancestor retention | The response contract must define whether ancestors with no ranked children are retained. |
| Tie handling | Ordering must be deterministic across memory, SQLite, MySQL, PostgreSQL, and SQL Server paths. |
| SQL/memory parity | Fixtures must prove the same tree envelope and totals across SQL pushdown and Java shaping. |
| Diagnostics | Refused, downgraded, or disabled tree totals must be visible in `pivotDiagnostics` or an equivalent warning surface. |

## LLM Rewrite Guidance

When a user asks for tree plus cascade, the safe rewrite is one of:

- Remove tree mode and use the accepted two-level rows cascade if the user needs "top children by parent".
- Keep tree mode and remove cascade if the user needs hierarchy browsing.
- Ask the user whether totals should include all descendants or only visible nodes before proposing a future tree-total shape.

The engine should not infer one of these choices silently.
