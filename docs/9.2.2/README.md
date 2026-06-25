---
doc_role: version_followup_plan
doc_purpose: Track 9.2.2 technical follow-up work for Runtime API and FSScript-backed runtime behavior.
version: 9.2.2
target: Java Engine 9.2.2 Follow-Up Roadmap
status: signed-off
created_at: 2026-06-25
updated_at: 2026-06-25
---

# Foggy Java Engine 9.2.2 Follow-Ups

## Purpose

9.2.2 continues the technical-risk cleanup after 9.2.1 signoff. This iteration focuses on code-level runtime behavior that can be exposed by focused tests before fixing.

This version uses the root-level versioned documentation convention: workitems live under `docs/9.2.2/workitems/`, while module manuals remain under each owning module when long-term user-facing documentation changes are needed.

## Current Scope

| Area | Workitem | Status | Owner Module | Boundary |
|---|---|---|---|---|
| Runtime model validation lifecycle | `workitems/P1-runtime-model-validation-bundle-cleanup.md` | signed-off | `foggy-runtime-api`, FSScript bundle runtime | Keep `/models/validate` validation bundles temporary even when `clearExisting=false`; does not change auth-code, RBAC, audit, or customer-facing security policy. |

## Acceptance Status

- acceptance_status: signed-off
- acceptance_decision: accepted. Runtime model validation bundle cleanup is signed off.
- signed_off_by: codex
- signed_off_at: 2026-06-25
- acceptance_record: docs/9.2.2/acceptance/P1-runtime-model-validation-bundle-cleanup-acceptance.md
- blocking_items: none
- follow_up_required: no

## Guardrails

- Do not extend 9.2.1 signed-off records unless documenting an erratum.
- Do not introduce security layering, RBAC, user permissions, audit, or credential rotation in this iteration.
- Keep the existing auth-code management gate contract unchanged.
- Prefer test-first exposure for each selected technical risk.
