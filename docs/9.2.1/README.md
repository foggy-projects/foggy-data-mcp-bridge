---
doc_role: version_followup_plan
doc_purpose: Track 9.2.1 focused follow-up work for the Java engine and FSScript runtime.
version: 9.2.1
target: Java Engine 9.2.1 Follow-Up Roadmap
status: signed-off
created_at: 2026-06-25
updated_at: 2026-06-25
---

# Foggy Java Engine 9.2.1 Follow-Ups

## Purpose

9.2.1 starts as a focused follow-up line for runtime stability, resource boundary, and packaging compatibility issues found during module review.

This version keeps the existing root-level versioned documentation convention: workitems live under `docs/9.2.1/workitems/`, while module manuals remain in each module's own `docs/` directory.

## Current Scope

| Area | Workitem | Status | Owner Module | Boundary |
|---|---|---|---|---|
| FSScript runtime stability and resource boundary | `workitems/P1-fsscript-runtime-stability-resource-boundary.md` | signed-off | `foggy-fsscript` | Resource-boundary fixes, concurrency hardening, watcher cleanup, primary bundle load-failure handling, and runtime error-handling cleanup are signed off; excludes management endpoint exposure and authentication policy. |
| Runtime API auth-code management gate | `workitems/P1-runtime-api-auth-code-management-gate.md` | signed-off | `foggy-runtime-api`, `foggy-fsscript` | Lightweight authorization-code gate for Runtime API management writes and default-off legacy FSScript bundle management endpoint. |

## Acceptance Status

- acceptance_status: signed-off
- acceptance_decision: accepted. FSScript stability/resource-boundary item and Runtime API auth-code management gate are both signed off.
- signed_off_by: codex
- signed_off_at: 2026-06-25
- acceptance_record_fsscript: docs/9.2.1/acceptance/P1-fsscript-runtime-stability-resource-boundary-acceptance.md
- acceptance_record_runtime_api: docs/9.2.1/acceptance/P1-runtime-api-auth-code-management-gate-acceptance.md
- blocking_items: none
- follow_up_required: no

## Guardrails

- Keep FSScript syntax and Java API behavior backward compatible unless a workitem explicitly accepts a breaking change.
- Do not expand this version into unrelated query-model or Pivot semantics without a separate workitem.
- Treat module docs under `foggy-fsscript/docs/` as long-term manuals, not version-tracking records.
- If implementation changes syntax, API behavior, or documented deployment behavior, update the relevant `foggy-fsscript/docs/FSScript-*.md` manual after code changes.
