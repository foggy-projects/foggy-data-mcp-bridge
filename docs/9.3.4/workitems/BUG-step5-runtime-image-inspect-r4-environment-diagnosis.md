---
doc_type: delivery-spec
delivery_type: bug
version: 9.3.4
ticket: BUG-STEP5-RUNTIME-IMAGE-INSPECT-R4-ENVIRONMENT-DIAGNOSIS
status: ACCEPTED
canonical: true
execution_mode: ultra
approved_by: foggy-projects-via-user-delegated-continuation
approved_at: 2026-07-22
controlling_step5_item: FEATURE-V934-RELEASE-AUTHORITY-AND-CI
predecessors:
  - BUG-STEP5-PACKAGE-IMAGE-EIMAGE-CLASSIFICATION
  - BUG-STEP5-POSTRUN-STEP4-WORKFLOW-STATE-RESET
open_questions: []
---

# Delivery Spec: Step 5 runtime-image inspect r4 environment diagnosis

## Document Purpose

- intended_for: Ultra implementation and independent signoff.
- purpose: Freeze one isolated, non-publishing diagnosis for the fresh Step 5
  `package-image-runtime-inspect / E_IMAGE` terminal result.
- canonical_path:
  `docs/9.3.4/workitems/BUG-step5-runtime-image-inspect-r4-environment-diagnosis.md`

## Goal

- version_goal: Restore a trustworthy path to a Step 5 rehearsal candidate
  without weakening image/JAR identity or fail-closed semantics.
- target_outcome: Determine, through a disposable run-owned Docker exercise,
  whether the runtime-image identity inspection is reproducibly compatible
  with the current WSL Docker environment, and record only a safe bounded
  diagnosis category.

## Scope

- in_scope:
  - use a fresh, clean clone and a unique temporary Docker build context;
  - first confirm, without a pull, that the frozen runtime base is locally
    usable; stop as `precondition-unavailable` if it is not;
  - copy the existing release Dockerfile and a regular disposable placeholder
    `app.jar`, build with `--pull=false`, and inspect the resulting temporary
    image using the same identity field contract as the package tool three
    times on the same image;
  - check only safe local-environment predicates needed to interpret that
    exercise: Docker server availability, selected context/build capability,
    inspect command completion, identity field shape, and linux/amd64 match;
  - capture any Docker response only transiently inside the diagnostic process,
    map it to the approved safe category, and discard it before a receipt or
    evidence record is written;
  - remove every named image, container, temporary file, and directory owned
    by the diagnostic; report cleanup as a bounded status;
  - prove source/Dockerfile/POM/contract-hash and candidate/final-pointer
    state are unchanged before and after the exercise;
  - write one safe evidence record and update this work item with the observed
    category, command classes only, and cleanup result.
- affected_modules:
  - disposable Docker context and image only;
  - `docs/9.3.4/workitems/BUG-step5-runtime-image-inspect-r4-environment-diagnosis.md`;
  - `docs/9.3.4/evidence/step-5/` for the safe terminal diagnosis record.
- external_dependencies: local WSL Docker Engine and the already available
  frozen runtime base image. No registry pull, image replacement, or remote
  publication is permitted.

## Non-Goals

- out_of_scope:
  - rerunning `verify-v934-release-gate.sh`, reusing or relabelling the failed
    rehearsal, creating an archive/candidate/final pointer, Step 6/7, release,
    tag, or publication;
  - modifying the Dockerfile, pinned base image, Maven/POM, package tool,
    receipt schema, coverage/CI contracts, production API/SPI, or 9.3.5/9.4.0;
  - retaining or publishing raw Docker commands, stdout/stderr, logs, image,
    container, OCI, endpoint, credential, or digest identities.
- do_not_touch:
  - the immutable failed rehearsal evidence and all historical E_IMAGE records;
  - user dirty baseline and any non-diagnostic Docker resource;
  - candidate and final-authority pointer files.

## Confirmed Decisions

| Decision | Rationale | Compatibility / Constraint |
|---|---|---|
| Diagnose with an isolated Docker exercise, not a gate retry | The failed run is immutable and its receipt intentionally omits raw cause | no candidate/pointer or runner authority may be created |
| Preserve the fixed E_IMAGE taxonomy | The prior classification repair already narrowed the boundary safely | output is a bounded category, never raw runtime detail |
| Use the exact release Dockerfile with a disposable regular placeholder | It exercises the runtime platform/image-inspect path without rebuilding tested application outputs | the placeholder is never executed or presented as a release JAR |
| No pull or image remediation | Diagnosis must distinguish existing local compatibility from environment mutation | `--pull=false`; any missing prerequisite is a safe diagnostic result |
| Use one random tag plus an ownership label | Cleanup must not rely on a name collision or touch a foreign image | ownership is verified before removal; no container is created |
| Stop before remediation | A tool, Dockerfile, image, platform, or CI change would be a new material solution | set `NEEDS_REPLAN` and freeze a successor contract first |

## Acceptance Criteria

- [x] AC-1: The exercise starts only from a clean clone, creates one uniquely
  owned image/context, invokes no Maven, Step 4, package tool, release gate,
  pointer, or publication path, and leaves tracked source unchanged.
- [x] AC-2: The frozen base is confirmed locally usable without a pull, then
  the unmodified release Dockerfile/`--pull=false` build is attempted once and
  the exact three-field runtime image inspection contract is sampled three
  times on that same owned image. The result is classified as exactly one of
  `valid-inspect-not-reproduced`, `runtime-inspect-reproduced`,
  `runtime-inspect-intermittent`, or `precondition-unavailable`; no raw
  response survives classification.
- [x] AC-3: Docker daemon/context/builder checks are persisted only as safe
  availability/capability booleans. The receipt must reject raw output, image,
  tag, container, OCI, endpoint, credential, digest, version, and path data.
- [x] AC-4: The diagnostic proves candidate/final pointer state and
  source/Dockerfile/POM/contract-hash state unchanged before and after the
  exercise. It creates no release run-root, package, archive, or pointer.
- [x] AC-5: `finally` cleanup removes only this run's ownership-label-verified
  image/context and proves no owned container, image tag, readback, or
  filesystem residue. It must not create a container, prune, or alter base
  images, caches, or pre-existing Docker resources; any cleanup uncertainty is
  fail-closed.
- [x] AC-6: Safe evidence records the failed Step 5 boundary, one diagnosis
  category, no-candidate/no-pointer status, command classes, privacy scan, and
  cleanup status without raw Docker payloads.
- [x] AC-7: If the category is not a bounded compatible result, or if a
  remediation would change a governed tool/policy/environment, this work item
  becomes `NEEDS_REPLAN`; no repair or new canonical rehearsal runs.

## Contract / Data / Security Constraints

- API or event contract: no public API, SPI, Dockerfile, runner, receipt, or
  pointer contract change.
- data and migration: no business data or fixture mutation; only a disposable
  local image/build context may be created and must be removed.
- compatibility and rollback: historical failure receipts remain immutable;
  deleting the owned temporary image/context is the complete rollback.
- permissions and secrets: no credentials, endpoints, commands, raw logs,
  paths, image/container/OCI identities, or digest values may be written to
  governed evidence. The diagnostic may inspect them transiently only to map a
  safe category.

## Test and Evidence Obligations

| Item | Risk | Required Validation | Required Evidence |
|---|---|---|---|
| AC-1/AC-4/AC-5 | critical | ownership, before/after seals and cleanup | safe boolean/count summary |
| AC-2/AC-3 | critical | base preflight, one isolated build, three exact inspect samples and privacy rejection checks | bounded category plus capability booleans |
| AC-6 | critical | evidence privacy/sensitive scan | no-raw-field checklist and terminal status |
| AC-7 | critical | scope/authority review | replan marker or explicitly bounded compatible result |

## Bug Context

- bug_source: acceptance-found
- severity: critical
- environment: WSL local Docker Engine; fresh Step 5 non-publishing rehearsal
  after the accepted workflow-state reset.
- current_behavior: Unit, integration, required database/external matrices,
  and coverage passed, but `package-tested-tree` stopped at the bounded
  `package-image-runtime-inspect / E_IMAGE` receipt before package publication.
- expected_behavior: a safe diagnosis determines whether the current Docker
  environment can satisfy the package tool's runtime image identity contract,
  without exposing raw Docker data or weakening the contract.
- reproduction_steps: do not retry the release runner; execute only the
  isolated disposable Docker exercise defined in this work item.
- reproduction_status: confirmed boundary; exact low-level trigger unknown by
  design because the fixed receipt is privacy-preserving.
- existing_evidence: the fresh failed rehearsal's outer and nine-field
  receipts; historical r44/r45 E_IMAGE records; accepted bounded-subphase and
  post-run-integrity repairs.
- existing_tests: package-tool synthetic receipt negatives and the successful
  Step 4/Step 5 static closures.
- regression_protection: required if and only if a subsequent repair changes
  governed tooling; this diagnosis itself adds no product code.
- waiver_reason_and_risk: N/A.

## Risks and Open Questions

- known_risks: Docker build cache behavior can change transient local state;
  a missing base, mixed inspection samples, or incomplete cleanup must be
  recorded as non-compatible, not repaired in place. The exercise cannot prove
  a canonical release candidate.
- open_questions: none.

## Ultra Execution Contract

- Read this work item, `CLAUDE.md`, the controlling Step 5 work item, and the
  existing package-image classification before acting.
- Execute exactly one bounded diagnostic attempt. Do not rerun Maven, the
  canonical runner, or a release package command.
- Maintain a private transient mapping from raw Docker observations to one
  approved category. Persist only the category and safe booleans; reject any
  attempted raw field before evidence publication.
- Self-check the fixed classification matrix, forbidden command graph,
  foreign-resource refusal, cleanup-failure behavior, and evidence privacy
  before the live exercise. These checks must not invoke Docker.
- If resources cannot be proven owned/cleaned, or if remediation is needed,
  set `NEEDS_REPLAN` and stop the affected expansion.
- On completion, write the implementation result and set
  `READY_FOR_SIGNOFF`; do not self-accept.

## Implementation Result

> Ultra execution completed under the approved single-attempt boundary and is
> ready for independent signoff.

- implementation_summary: Completed one isolated, non-publishing Docker
  diagnosis. The governed runtime identity inspection was valid in all three
  same-image samples, so the generic WSL Docker environment did not reproduce
  the failed package boundary.
- changed_paths:
  - docs/9.3.4/workitems/BUG-step5-runtime-image-inspect-r4-environment-diagnosis.md
  - docs/9.3.4/evidence/step-5/step5-runtime-image-inspect-r4-environment-diagnosis-20260722.md
- tests_and_results: Docker-free classification/privacy/forbidden-command
  self-check passed; one disposable no-pull Docker build and three
  identity samples passed; owned-resource cleanup and source/pointer
  invariants passed. Maven, canonical runner, package, archive, and release
  commands were not run by contract.
- manual_or_experience_evidence: WSL Docker daemon/context/build capability
  available; no container created and owned temporary resources cleaned.
- deviations: none
- residual_risks: The canonical package-context trigger remains unidentified;
  this diagnostic does not authorize a retry. A new, narrower package-context
  diagnosis/replan is required before remediation or a fresh rehearsal.
- readiness: ACCEPTED

## Acceptance Status

- acceptance_status: signed-off
- acceptance_decision: accepted
- signed_off_by: foggy-projects-via-user-delegated-continuation
- signed_off_at: 2026-07-22
- acceptance_record:
  docs/9.3.4/acceptance/step5-runtime-image-inspect-r4-environment-diagnosis-signoff-20260722.md
- blocking_items: none
- follow_up_required: yes

## References

- controlling Step 5 scope:
  `docs/9.3.4/workitems/FEATURE-v934-release-authority-and-ci.md`
- prior bounded classification:
  `docs/9.3.4/workitems/BUG-step5-package-image-eimage-classification.md`
- prior terminal-closure/reset chain:
  `docs/9.3.4/workitems/BUG-step5-postrun-step4-artifact-verifier-integrity-replan.md`
  and `docs/9.3.4/workitems/BUG-step5-postrun-step4-workflow-state-reset.md`
