---
doc_role: workitem
doc_purpose: Track Java-produced Pivot output snapshots used by the Python engine parity line.
version: 9.2.0
target: Pivot Output Snapshot Parity
status: local-verified
created_at: 2026-06-06
updated_at: 2026-06-06
source_type: alignment
owner_repo: foggy-data-mcp-bridge-wt-dev-compose
owner_module: foggy-dataset-model
cross_repo_followup: foggy-data-mcp-bridge-python
---

# Pivot Output Snapshot Parity

## Purpose

This workitem records the Java-side Pivot output snapshot producer used by the Python alignment stream. The Java engine remains the current oracle for selected real Pivot output shapes, while Python can replay the generated fixture without depending on Java runtime state.

## Scope

In scope:

- Java snapshot producer: `JavaPivotOutputSnapshotTest`.
- Fixture target: `foggy-data-mcp-bridge-python/tests/fixtures/java_pivot_output_snapshot_parity.json`.
- Output shapes: flat rows, flat rows + columns, grid rows + columns, grand total, row subtotals + grand total.
- Seed isolation: rows are keyed by `orderStatus = PY_ALIGN_PIVOT_OUT`.

Out of scope:

- Python implementation changes.
- Python parity assertion changes.
- Public Pivot DSL changes.
- Tree + cascade semantics.

## Current Snapshot Contract

The generated fixture currently contains 8 cases:

| Case | Shape |
|---|---|
| `pivot-flat-rows` | flat row axis |
| `pivot-flat-rows-columns` | flat row + column axis |
| `pivot-grid-rows-columns` | grid row + column axis |
| `pivot-flat-rows-grand-total` | flat row axis with grand total |
| `pivot-flat-rows-columns-grand-total` | flat row + column axis with grand total |
| `pivot-grid-rows-columns-grand-total` | grid row + column axis with grand total |
| `pivot-flat-rows-subtotals-grand-total` | flat two-level rows with row subtotals and grand total |
| `pivot-grid-rows-columns-subtotals-grand-total` | grid two-level rows + columns with row subtotals and grand total |

## Verification Evidence

| Command / Check | Result |
|---|---|
| `JAVA_HOME=/Users/fengjianguang/.jdk/temurin-17/Contents/Home mvn -pl foggy-dataset-model -am -P'!multi-db' -Dspring.profiles.active=sqlite -Dtest='JavaPivotOutputSnapshotTest' -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false test` | success; Tests run: 1, Failures: 0, Errors: 0, Skipped: 0. |
| Read generated Python fixture | success; fixture exists and contains 8 cases including row subtotal + grand total flat/grid cases. |
| `git status --short -- tests/fixtures/java_pivot_output_snapshot_parity.json` in Python repo | clean; generated fixture matches the Python repo tracked file. |

## Status

Status: local-verified.

The Java producer can generate the current Python alignment fixture successfully. The Python repo already has the same fixture content, so no cross-repo fixture commit was needed in this Java-side checkin.

## Follow-Ups

- Python alignment session should replay the 8-case fixture and record parity status in the Python repo.
- If new Pivot output features are added, extend `JavaPivotOutputSnapshotTest` first, regenerate the Python fixture, then update this workitem with the new case count.
- Keep tree + cascade / tree subtotal semantics under `PIVOT-92-D1`; do not infer those semantics from this flat/grid snapshot fixture.
