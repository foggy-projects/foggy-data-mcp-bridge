---
doc_type: delivery-spec
delivery_type: bug
version: 9.3.4
ticket: BUG-STEP4-R7-INSPECT-FORMAT-WORKFLOW-STATE-RESET
status: APPROVED
canonical: true
execution_mode: ultra
approved_by: foggy-projects-via-user-delegated-continuation
approved_at: 2026-07-22
controlling_step5_item: FEATURE-V934-RELEASE-AUTHORITY-AND-CI
predecessors:
  - BUG-STEP5-RUNTIME-IMAGE-INSPECT-FORMAT-REMEDIATION
open_questions: []
---

# Delivery Spec: R7 inspect-format Step 4 workflow-state reset

## Document Purpose

- intended_for: Ultra implementation and independent static review.
- purpose: Restore the exact Step 4 diagnostic entry state required by the
  already-approved R7 inspect-format source bytes, without changing those
  bytes, test policy, runtime behavior, or any release authority.
- canonical_path:
  `docs/9.3.4/workitems/BUG-step4-r7-inspect-format-workflow-state-reset.md`

## Goal

- version_goal: Establish one clean, reviewable Cdiag baseline for the R7
  source repair. It is a state-and-integrity reset only, not a diagnostic,
  formalization, Step 5 retry, or version acceptance.
- target_outcome: The current machine state becomes the exact canonical
  `diagnostic-ready / diagnostic-pending` shape required by the existing Step
  4 validators, with its minimal Step 4 -> Step 6 mechanical hash/CI closure
  synchronized and statically verified before any fresh full diagnostic.

## Scope

- in_scope:
  - restore `scripts/v934/step4/coverage-contract.json` to the existing
    diagnostic-ready identity bytes (including key order/format) with root
    status and `tooling_manifest.publication_status` both
    `diagnostic-ready`, without changing its policy, parent links, layout,
    successor rules or validators;
  - set `scripts/v934/step4/coverage-thresholds.json` to the exact pending
    shape accepted by the existing validator: status `diagnostic-pending`,
    `aggregate_observed`, `aggregate_reviewed_thresholds`, and
    `critical_reviewed_thresholds` all `null`, and a four-field review with
    null reviewer/reviewed_at/diagnostic_run_id and decision
    `pending-all-lane-diagnostic`; delete rather than null any retained formal
    evidence fields so the review has exactly those four keys;
  - update only the resulting exact integrity closure:
    `scripts/v934/step4/SHA256SUMS`,
    `scripts/v934/step6/ci_contract_tool.py`,
    `scripts/v934/step6/ci-contract.json`, and
    `scripts/v934/step6/SHA256SUMS`;
  - record a safe Cdiag static checkpoint and independent static review; after
    that review, leave one clean pushed Cdiag as the only permissible input to
    a separately executed fresh full Step 4 diagnostic;
  - preserve the already-repaired R7 package tool and its Step 5 manifest as
    Cdiag input, without altering either.
- affected_modules:
  - the two named Step 4 machine-state documents;
  - Step 4/Step 6 mechanical integrity and CI binding files;
  - this work item and safe static review/checkpoint records only.
- external_dependencies: None for the reset/static proof. Maven, Docker,
  database services and the outer Step 4 runner are explicitly deferred to
  the later fresh diagnostic authorized only after this Cdiag is reviewed.

## Non-Goals

- out_of_scope:
  - modifying `scripts/v934/step5/release_package_tool.py`, either Step 5
    manifest, Dockerfile/base image, Maven/POM, production or test source,
    coverage policy/floors/critical set/exclusions, validators, runners,
    workflow schema/graph, receipt interfaces, API/SPI/module layout, or
    historical evidence;
  - running Maven, Docker, a database action, the outer Step 4 runner, a
    package proof/rehearsal/replay, candidate/capsule/Cfreeze/formal, release,
    pointer/final promotion, Step 6/7 authority action, 9.3.5, or 9.4.0;
  - deleting/relabeling any old diagnostic, Cfreeze, formal or acceptance
    record, or reusing it as authority for R7 bytes.
- do_not_touch:
  - coverage/threshold policy values and all validator semantics;
  - the R7 repaired tool and its existing hash closure except that later Cdiag
    source sealing may consume them unchanged;
  - user baseline and any non-owned runtime/container resource.

## Confirmed Decisions

| Decision | Rationale | Constraint |
|---|---|---|
| Exact pending state, not status-only mutation | The validator rejects hybrid pending/formal state | observations/reviews must be cleared to canonical pending shape |
| New Cdiag, not reuse of historical authority | R7 source/manifest bytes differ | old diagnostic/Cfreeze/formal records stay immutable and non-reusable |
| Mechanical Step 4 -> Step 6 closure only | State bytes are governed inputs | no workflow/runner/validator semantic change |
| Static proof before runtime consumption | Prevents a costly diagnostic from starting on invalid bytes | no Maven/Docker/outer runner in this work item |

## Acceptance Criteria

- [ ] AC-1: The contract matches the frozen diagnostic-ready identity bytes
  and is `diagnostic-ready` both at root and tooling-publication level, and
  the threshold document is exactly
  `diagnostic-pending` with all three observation/reviewed values null and the
  four-field pending review shape (with no retained formal evidence keys);
  existing validation accepts it.
- [ ] AC-2: Policy/floors/critical set/exclusions, source/authority policy,
  report/test cardinality, validators, Step 5 tool/manifest, workflows,
  receipts, APIs and SPIs have no drift.
- [ ] AC-3: Deterministic isolated structure-only/static negatives prove
  rejection of at least a status-only mismatch, a pending state carrying
  observations, and a pending review carrying stale formal evidence fields;
  existing XML negatives continue to reject pending state at frozen/formal/
  candidate/final boundaries, and canonical files are not mutated by probes.
- [ ] AC-4: Step 4 contract, XML and successor-overlay positive/negative
  checks, the existing Docker-free package negative/self-test, Step 4/5/6
  manifests, and Step 6 CI-contract validation/self-negative check pass for
  the exact declared closure.
- [ ] AC-5: A safe static checkpoint records only status/count/boolean facts;
  an independent review confirms exact state, scope, closure and no-policy
  drift. Neither record claims diagnostic, formal, Step 5 or version authority.
- [ ] AC-6: The reviewed Cdiag is clean and pushed before a later fresh full
  Step 4 diagnostic. Until then, no authority action or runtime workload in
  this item occurs.
- [ ] AC-7: Any required path outside this scope, validator semantic change,
  policy drift, failed static validation, unsafe output, or unclean Cdiag is
  fail-closed and creates a successor replan rather than a retry or bypass.

## Contract / Data / Security Constraints

- API or event contract: no public API, SPI, config, receipt, workflow or
  package-layout change.
- data and migration: no business data, schema, fixture, image, cache or host
  state mutation; only named repository machine-state documents change.
- compatibility and rollback: invalid pending shape remains rejected. A
  rollback/rework requires a new governed contract; historical authority is
  never restored as current input.
- permissions and secrets: records retain no raw command/output, paths,
  credentials, endpoint, container/image identity, digest or runtime payload.

## Test and Evidence Obligations

| Item | Risk | Required validation | Required safe evidence |
|---|---|---|---|
| AC-1/AC-3 | blocker | contract/structure-only and XML negatives plus isolated canonical/hybrid-state probes | pass/fail/count only |
| AC-2 | critical | declared-path and policy comparison | no-drift booleans |
| AC-4 | critical | overlay/contract/XML negatives, package self-test, all manifests, CI self/negative | closure pass/fail only |
| AC-5 | blocker | independent static scope/closure review | review decision and bounded facts |
| AC-6/AC-7 | blocker | clean-pushed Cdiag boundary review | no-runtime/no-authority booleans |

## Bug Context

- bug_source: governance-preflight-found.
- severity: blocker.
- current_behavior: R7's repaired package-tool source must enter a new Step 4
  diagnostic, but current machine state is `formal-ready / confirmed`; the
  existing diagnostic gate correctly refuses that state.
- expected_behavior: an exact reviewed pending-state Cdiag precedes one fresh
  diagnostic for the changed source.
- reproduction_status: confirmed by the existing diagnostic state validator.
- regression_protection: existing state validator and static negative suite;
  no new policy or permissive branch is allowed.

## Approval Record

- approval_authority: project-owner delegated continuation direction.
- approved_by: `foggy-projects-via-user-delegated-continuation`.
- approved_at: 2026-07-22.
- approved_scope: the exact reset/static-Cdiag envelope above. It does not
  authorize the subsequent runtime diagnostic, Step 5 proof or downstream
  roadmap authority; those remain governed by their successor gates.

## Ultra Execution Contract

- Begin from the R7 repaired source closure. Do not change it or any path
  outside this item's allowlist.
- Use only isolated/static validators and owned disposable output. Do not run
  Maven, Docker, database or an outer runner.
- Recompute every declared manifest/CI binding after state bytes change, then
  run the full static matrix. Keep raw output out of evidence.
- Commit and push the Cdiag only after independent static review. Do not start
  the fresh full diagnostic in the same unreviewed state-reset action.
- Set `READY_FOR_SIGNOFF` only after AC-1 through AC-7 are met. Do not
  self-accept; a failed condition remains `NEEDS_REPLAN`.

## Implementation Result

> Pending execution.

- implementation_summary: pending
- changed_paths: pending
- tests_and_results: pending
- manual_or_experience_evidence: pending
- deviations: none
- residual_risks: pending
- readiness: APPROVED

## References

- R7 predecessor:
  `docs/9.3.4/workitems/BUG-step5-runtime-image-inspect-format-remediation.md`
- controlling Step 5 scope:
  `docs/9.3.4/workitems/FEATURE-v934-release-authority-and-ci.md`
- accepted prior reset pattern:
  `docs/9.3.4/workitems/BUG-step5-postrun-step4-workflow-state-reset.md`
