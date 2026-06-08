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
- Tool contract forbidden markers that keep legacy Java `DataSetResult` / `ComposedDataSetResult` APIs out of the current SemanticDSL script surface.
- Runtime globals contract for Java accepted globals and Python compatibility globals.
- Script cases covering literal return, empty plan envelope, base / derived / union / join preview SQL envelopes, base / derived / union / join execution rows envelopes, and security parameter refusal.
- MCP tool error payload cases covering resolver-null host misconfiguration, remote authority-binding principal mismatch, and remote missing authority binding.

Out of scope:

- Python production implementation changes.
- Public Compose DSL expansion.
- Legacy Java `DataSetResult` / `ComposedDataSetResult` method-surface parity unless product explicitly reopens that API as part of `dataset.compose_script`.
- Free SQL / caller-authored CTE exposure.

## Current Snapshot Contract

The generated runtime fixture currently contains 11 cases:

| Case | Contract |
|---|---|
| `literal-return` | Plain script return keeps numeric value shape. |
| `empty-plans-envelope` | `return { plans: [] }` keeps the map/envelope shape without SQL. |
| `preview-base-plan-sql` | Preview-mode base `dsl({...})` returns `ComposedSql` under `plans` and preserves model SQL markers. |
| `execute-base-plan-rows-envelope` | Execute-mode base `dsl({...})` returns row data under `plans`, including the routed model marker. |
| `preview-derived-plan-sql` | Derived `dsl({ source, ... })` preview preserves nested source SQL and derived-column markers. |
| `execute-derived-plan-rows-envelope` | Derived execute path keeps the rows envelope and routed model marker. |
| `preview-union-plan-sql` | Union preview preserves both branch model markers and `UNION ALL`. |
| `execute-union-plan-rows-envelope` | Union execute path keeps the rows envelope and routed model marker. |
| `preview-join-plan-sql` | Join preview preserves both branch model markers, join type, and join key marker. |
| `execute-join-plan-rows-envelope` | Join execute path keeps the rows envelope and routed model marker. |
| `security-param-denied` | Unsafe caller-supplied security parameters fail closed with the expected error marker. |

The generated MCP tool error fixture currently contains seven cases:

| Case | Contract |
|---|---|
| `missing-script` | Missing required `script` argument is normalized to `missing-script` without stack leakage. |
| `missing-context` | Missing `ToolExecutionContext` is normalized to `internal-error` without stack leakage. |
| `missing-user-id-header` | Header-mode context bridge without `X-User-Id` is normalized to `internal-error` without stack leakage. |
| `missing-namespace-header` | Header-mode context bridge without namespace / namespace header is normalized to `internal-error` without stack leakage. |
| `resolver-null-host-misconfig` | Host-side resolver factory returning `null` is normalized to structured MCP error fields without stack leakage. |
| `remote-principal-mismatch` | Remote authority binding whose principal differs from request headers is normalized to `compose-authority-resolve/principal-mismatch` without stack leakage. |
| `remote-missing-authority-binding` | Remote authority response missing a requested model binding is normalized to `compose-authority-resolve/invalid-response` without stack leakage. |

The runtime/tool snapshot deliberately treats `DataSetResult`,
`ComposedDataSetResult`, `toList`, `withJoin`, and `joinInMemory` as forbidden
markers for the `dataset.compose_script` tool contract. Those APIs still exist
inside Java's legacy compose result tests, but the current AI-facing script
surface is QueryPlan based: return `{ plans: dsl(...) }`, then let the runtime
preview SQL or execute rows.

## Verification Evidence

| Command / Check | Result |
|---|---|
| `JAVA_HOME=/Users/fengjianguang/.jdk/temurin-17/Contents/Home mvn -pl foggy-dataset-model -am -P'!multi-db' -Dspring.profiles.active=sqlite -Dtest=JavaComposeScriptSnapshotTest -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false test` | success; Tests run: 1, Failures: 0, Errors: 0, Skipped: 0. |
| `JAVA_HOME=/Users/fengjianguang/.jdk/temurin-17/Contents/Home mvn -pl foggy-dataset-mcp -am -P'!multi-db' -Dspring.profiles.active=sqlite -Dtest=JavaComposeScriptToolErrorSnapshotTest -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false test` | success; Tests run: 1, Failures: 0, Errors: 0, Skipped: 0. |
| Read generated Python runtime fixture | success; fixture exists at `foggy-data-mcp-bridge-python/tests/fixtures/java_compose_script_snapshot_parity.json` and contains the 11 Java snapshot cases, including derived / union / join preview and execute envelopes. |
| Read generated Python tool error fixture | success; fixture exists at `foggy-data-mcp-bridge-python/tests/fixtures/java_compose_script_tool_error_snapshot_parity.json` and contains 7 Java snapshot cases: missing script, missing context, missing user header, missing namespace header, resolver-null, remote principal-mismatch, and remote missing-binding. |
| `if [ -x .venv/bin/python ]; then .venv/bin/python -m pytest tests/integration/test_java_compose_script_snapshot_parity.py -q; else python -m pytest tests/integration/test_java_compose_script_snapshot_parity.py -q; fi` | success; 4 passed. |
| `if [ -x .venv/bin/python ]; then .venv/bin/python -m pytest tests/integration/test_java_compose_script_tool_error_snapshot_parity.py -q; else python -m pytest tests/integration/test_java_compose_script_tool_error_snapshot_parity.py -q; fi` | success; 2 passed, 2 warnings from upstream `datetime.utcnow()` deprecation. |

## Status

Status: local-verified.

The Java producers can generate the current compose script runtime and MCP tool error snapshot fixtures successfully. Python replay now consumes both generated fixtures locally, while production Python implementation work remains outside this Java-side workitem.

## Follow-Ups

- Python alignment session should keep replaying the 11-case runtime fixture and seven-case tool error fixture.
- Keep legacy Java result-object methods out of the current `dataset.compose_script` SemanticDSL tool surface unless a future product decision explicitly revives them.
- If script runtime return shapes, tool descriptions, or structured tool error payloads change, extend the Java producer first, regenerate the Python fixture, then update this workitem with the new case count.
- Keep `dataset.compose_script` as the structured DSL path for cross-model Join / Union / derived plans; do not use this parity fixture to justify exposing free SQL or free CTE authoring.
