---
doc_role: workitem
doc_purpose: Track Java-produced compose script runtime snapshots used by the Python engine parity line.
version: 9.2.0
target: Compose Script Snapshot Parity
status: local-verified
created_at: 2026-06-07
updated_at: 2026-06-07
source_type: alignment
owner_repo: foggy-data-mcp-bridge-wt-dev-compose
owner_module: foggy-dataset-model
cross_repo_followup: foggy-data-mcp-bridge-python
---

# Compose Script Snapshot Parity

## Purpose

This workitem records the Java-side `dataset.compose_script` runtime snapshot producer used by the Python alignment stream. The Java engine remains the current oracle for selected script runtime contracts, while Python can replay the generated fixture without depending on Java runtime state.

## Scope

In scope:

- Java snapshot producer: `JavaComposeScriptSnapshotTest`.
- Fixture target: `foggy-data-mcp-bridge-python/tests/fixtures/java_compose_script_snapshot_parity.json`.
- Tool contract markers for `dataset.compose_script`, `compose_query_schema.json`, and `compose_script_m2.md`.
- Runtime globals contract for Java accepted globals and Python compatibility globals.
- Script cases covering literal return, empty plan envelope, preview SQL envelope, execution rows envelope, and security parameter refusal.

Out of scope:

- Python implementation changes.
- Python parity assertion changes.
- Public Compose DSL expansion.
- Free SQL / caller-authored CTE exposure.

## Current Snapshot Contract

The generated fixture currently contains five cases:

| Case | Contract |
|---|---|
| `literal-return` | Plain script return keeps numeric value shape. |
| `empty-plans-envelope` | `return { plans: [] }` keeps the map/envelope shape without SQL. |
| `preview-base-plan-sql` | Preview-mode `dsl({...})` returns `ComposedSql` under `plans` and preserves SQL markers. |
| `execute-base-plan-rows-envelope` | Execute-mode `dsl({...})` returns row data under `plans`, proving the rows envelope shape for Python replay. |
| `security-param-denied` | Unsafe caller-supplied security parameters fail closed with the expected error marker. |

## Verification Evidence

| Command / Check | Result |
|---|---|
| `JAVA_HOME=/Users/fengjianguang/.jdk/temurin-17/Contents/Home mvn -pl foggy-dataset-model -am -P'!multi-db' -Dspring.profiles.active=sqlite -Dtest=JavaComposeScriptSnapshotTest -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false test` | success; Tests run: 1, Failures: 0, Errors: 0, Skipped: 0. |
| Read generated Python fixture | success; fixture exists at `foggy-data-mcp-bridge-python/tests/fixtures/java_compose_script_snapshot_parity.json` and contains the five Java snapshot cases, including `execute-base-plan-rows-envelope`. |

## Status

Status: local-verified.

The Java producer can generate the current compose script snapshot fixture successfully. Python replay is tracked in the Python repo and should be recorded there when the Python alignment session runs the fixture.

## Follow-Ups

- Python alignment session should keep replaying the five-case fixture and record parity status in the Python repo.
- If script runtime return shapes or tool descriptions change, extend `JavaComposeScriptSnapshotTest` first, regenerate the Python fixture, then update this workitem with the new case count.
- Keep `dataset.compose_script` as the structured DSL path for cross-model Join / Union / derived plans; do not use this parity fixture to justify exposing free SQL or free CTE authoring.
