---
doc_type: delivery-spec
delivery_type: bug
version: 9.3.4
ticket: BUG-STEP5-POSTRUN-STEP4-ARTIFACT-VERIFIER-INTEGRITY-REPLAN
status: APPROVED
canonical: true
execution_mode: ultra
approved_by: foggy-projects
approved_at: 2026-07-21
controlling_step5_item: FEATURE-V934-RELEASE-AUTHORITY-AND-CI
predecessors:
  - BUG-STEP5-PACKAGE-IMAGE-RUNTIME-INSPECT-R3-ENVIRONMENT-DIAGNOSIS
open_questions: []
---

# Delivery Spec: Step 5 post-run Step 4 integrity-closure repair

## Document Purpose

- intended_for: Ultra implementation and independent delivery signoff.
- purpose: freeze the smallest fail-closed repair for the r4 terminal
  inconsistency: Step 4 passed before the package phase, but its independent
  post-run artifact verifier did not pass after a package-stage failure.
- canonical_path:
  docs/9.3.4/workitems/BUG-step5-postrun-step4-artifact-verifier-integrity-replan.md
- version_goal: make the package-stage terminal outcome internally consistent
  without authorizing another Step 5 rehearsal, portable replay, release, Step
  6, Step 7, 9.3.5, or 9.4.0.

## Goal

- target_outcome: after the package tool has entered its mutable work window,
  every controllable terminal path executes the same bounded after-integrity closure after
  owned cleanup and quarantine restoration. A valid image-boundary failure may
  retain its existing safe E_IMAGE classification only when that closure passes.
  A closure failure is dominant, fail-closed, and cannot be represented as a
  normal E_IMAGE result.

## Scope

- in_scope:
  1. Refactor the existing in-process Step 4 release artifact verification in
     scripts/v934/step5/release_package_tool.py into a private reusable helper.
     The helper must preserve the existing verifier interface and output
     suppression; it must not create a new public CLI or transport raw detail.
  2. Establish one shared after-integrity closure for the package command.
     Arm a mutable-work latch immediately before the first package-owned
     write, rename, staging, or quarantine action. Once armed, normal success,
     PackageError, controlled cleanup error, KeyboardInterrupt, and any
     explicitly handled in-process signal path must converge through that
     closure after owned isolation, staging, and Docker cleanup have completed
     or have themselves failed closed. Uncatchable/external process termination
     retains the existing outer fail-closed behavior and must not be described
     as having completed the closure or a normal E_IMAGE receipt.
  3. The closure must verify the already-required package postconditions:
     current Step 4 release artifact integrity, class-tree integrity,
     TEST-report integrity, tracked source/HEAD seal, and the existing Step 4
     authority bindings. It must not treat a successful pre-package check as a
     substitute for this terminal check.
  4. Preserve the fixed nine-field package failure sidecar and all existing
     enums. If the closure passes, preserve the original controlled failure
     classification, including package-image-runtime-inspect / E_IMAGE. If
     the Step 4 artifact verifier fails, publish the existing
     package-postconditions / E_STEP4_RELEASE_VERIFY failure classification.
     Other existing after-seal failures may retain only their existing bounded
     error code, always with package-postconditions. Once the latch is armed,
     any closure failure dominates E_IMAGE, KeyboardInterrupt, and cleanup
     primary errors; only a passing closure permits the original primary
     classification. No raw message may affect or enter that decision.
  5. Add deterministic, in-tool synthetic regression coverage that exercises
     the actual shared terminal-closure path without Maven, Docker, image,
     container, network, or historical runtime evidence. It must cover both a
     clean E_IMAGE terminal path and a faulted after-integrity path.
  6. Update only the required Step 4/5/6 tooling hash and CI-contract closure,
     then complete a new Cdiag -> fresh diagnostic -> independent review ->
     direct-child Cfreeze -> fresh formal -> quality/audit -> owner
     reacceptance chain before any later Step 5 decision.
- affected_modules:
  - scripts/v934/step5/release_package_tool.py
  - scripts/v934/step4/SHA256SUMS
  - scripts/v934/step5/SHA256SUMS
  - scripts/v934/step6/ci_contract_tool.py
  - scripts/v934/step6/ci-contract.json
  - scripts/v934/step6/SHA256SUMS
  - this governed work item and its later safe evidence records
- external_dependencies: Maven and Docker remain owned only by the canonical
  runner for future separately authorized evidence runs. They are not inputs
  to the synthetic regression proof in this repair.

## Non-Goals

- out_of_scope:
  - no Docker, host, base-image, registry, credential, POM, Dockerfile, Maven
    policy, threshold, coverage XML/verifier contract, database, workflow,
    candidate/archive/pointer, API, SPI, module-layout, 9.3.5, or 9.4.0 change;
  - no conclusion about the exact r4 mutable file or Docker/image cause. The
    observed state proves a missing terminal integrity closure, not a specific
    underlying product or host mutation;
  - no repair, reuse, relabel, retry, or deletion of r3/r4 evidence or
    pointers.
- do_not_touch:
  - scripts/verify-v934-release-gate.sh;
  - scripts/v934/step4/coverage_xml_tool.py, coverage contracts, and coverage
    thresholds;
  - the outer five-field failure.env schema, the nine-field sidecar schema,
    existing subphase/error-code vocabulary, candidate and final-authority
    pointer semantics, public APIs/SPI, POMs, Dockerfiles, and CI job graph.

## Confirmed Decisions

| Decision | Rationale | Compatibility / constraint |
|---|---|---|
| Repair package exception handling, not the Step 4 verifier | The existing verifier passed before package work and is the intended independent terminal check | no coverage-policy or verifier weakening |
| Use one shared after-integrity closure | r4 showed that success-only after checks leave an observable terminal gap | all mutable-path exits receive the same bounded proof |
| Preserve E_IMAGE only after closure passes | E_IMAGE is valid bounded failure evidence only if all postconditions still hold | no false normal failure classification |
| Dominant package-postconditions classification on closure failure | a primary package error must not hide integrity loss | existing fixed schema and vocabulary remain unchanged |
| Explicit mutable latch and precedence | terminal handling must be deterministic even when primary and integrity failures coexist | latch precedes first owned write/rename; only catchable in-process exits are closable |
| Synthetic regression first | the control-flow defect must be reproducible without external runtime dependencies | no Docker/Maven execution for the new regression case |
| Fresh Cdiag chain, then separately approved Step 5 | changed governed tooling invalidates r4/Cfreeze reuse | no automatic rehearsal or replay |

## Acceptance Criteria

- [ ] AC-1: the initial Step 4 verification and the new terminal verification
  call the same private helper, preserve canonical arguments and output
  suppression, and introduce no public interface or raw-output artifact.
- [ ] AC-2: after the package command enters its mutable window, all successful
  and failed paths execute the shared closure after owned restoration/cleanup.
  The closure validates the Step 4 release artifact plus existing class,
  report, source/HEAD, and Step 4 authority postconditions.
- [ ] AC-3: deterministic synthetic fault injection proves that an
  image-boundary failure with a passing terminal closure retains the existing
  nine-field package-image-runtime-inspect / E_IMAGE receipt, emits no
  candidate/pointer, and exposes no raw detail.
- [ ] AC-4: deterministic synthetic fault injection proves that an
  image-boundary failure combined with a Step 4 artifact-integrity failure
  produces package-postconditions / E_STEP4_RELEASE_VERIFY, never claims a
  normal E_IMAGE result, emits no candidate/pointer, and leaves the fixed
  sidecar schema unchanged.
- [ ] AC-5: synthetic coverage starts at the real mutable-work latch and
  proves a class/report/source after-seal failure is fail-closed and dominant
  over E_IMAGE, a controlled cleanup error, and KeyboardInterrupt. It also
  proves a passing closure preserves the original primary classification.
  Uncatchable/external process termination remains under the existing outer
  fail-closed behavior and is not claimed as a closure execution.
- [ ] AC-6: existing package receipt writer/reader negatives, package output
  cardinality, cleanup/quarantine invariants, Python/Bash syntax, Step 4/5/6
  hash manifests, and Step 6 CI-contract/self/negative validation pass with no
  undeclared path change.
- [ ] AC-7: a clean new Cdiag containing only the declared code/hash/docs
  closure is independently reviewed; a fresh full Step 4 diagnostic, its
  independent review, a direct-child Cfreeze, fresh formal validation,
  quality/audit evidence, and foggy-projects scoped reacceptance all complete.
  r3/r4 and their prior Cfreeze remain historical and non-reusable.
- [ ] AC-8: no new Step 5 rehearsal, portable replay, authority/final
  promotion, Step 6/7 execution, 9.3.5, or 9.4.0 transition occurs under this
  work item. A fresh Step 5 rehearsal remains subject to a later explicit
  owner approval.

## Contract / Data / Security Constraints

- API or event contract: no public API, SPI, configuration, package-layout,
  archive, pointer, workflow, outer failure receipt, or sidecar-schema change.
- data and migration: no business data, database, fixture, Docker image,
  cache, registry, or host-environment migration or mutation outside the
  canonical runner.
- compatibility and rollback: a closure failure can only make an outcome more
  fail-closed. Revert or redesign requires a new governed Cdiag/revalidation
  path; historic r4 authority is never restored.
- permissions and secrets: all new verification output remains suppressed.
  Do not read, publish, hash, or transport raw stderr/stdout, logs, commands,
  paths, credentials, endpoints, image/container/OCI identities, or digests.
  Evidence may contain only approved status, schema, phase, category, and
  boolean results.

## Test and Evidence Obligations

| Item | Risk | Required validation | Required evidence |
|---|---|---|---|
| AC-1/AC-2 | critical | in-tool synthetic shared-closure checks and static control-flow review | safe case/status/count summary |
| AC-3 | critical | injected E_IMAGE with passing terminal closure | original bounded category retained; no pointer boolean |
| AC-4/AC-5 | critical | injected verifier/seal/cleanup conflict cases | dominant fail-closed category and no-pointer boolean |
| AC-6 | critical | package negative, syntax, manifests, CI-contract self/negative closure | safe pass/fail boolean matrix |
| AC-7 | blocker | fresh Cdiag, diagnostic, review, Cfreeze, formal, quality/audit, owner reacceptance | new governed safe evidence only |

## Bug Context

- bug_source: acceptance-found
- severity: critical
- environment: approved r4 Step 5 rehearsal in the WSL-owned local execution
  environment.
- current_behavior: the gate verifies Step 4 before package work; the package
  tool verifies it again on entry. Its class/report/source and terminal Step 4
  authority checks are placed after image work on the success path. An
  image-boundary failure therefore bypasses all of those after checks.
- expected_behavior: every catchable in-process package path after the mutable
  latch has armed has one fail-closed postcondition result. A valid E_IMAGE
  sidecar is possible only after that result is known to pass. Uncatchable or
  external termination remains an outer fail-closed outcome, not a claimed
  postcondition pass.
- reproduction_steps: use the new deterministic synthetic fault cases. Do not
  rerun r4 or inspect its raw artifacts.
- reproduction_status: confirmed control-flow gap; the exact live mutable
  artifact remains intentionally unclassified.
- existing_evidence: r4 recorded a valid package E_IMAGE sidecar, no
  candidate/authority/final pointer, unchanged tracked source/HEAD, and a
  failed independent terminal Step 4 artifact-verifier boolean. These facts
  are insufficient to identify a specific file and must not be broadened.
- existing_tests: package negative/self-test, gate contracts, Step 4 artifact
  verifier, and Step 6 CI closure.
- regression_protection: required
- waiver_reason_and_risk: N/A.

## Risks and Open Questions

- known_risks: a superficial helper extraction could skip a postcondition,
  change error precedence, leak raw exception detail, weaken receipt semantics,
  or let cleanup failure bypass integrity verification. Each is a blocker.
- open_questions: none

## Ultra Execution Contract

- Read this work item, CLAUDE.md, the package/Step 4/Step 6 contracts, and
  applicable governed test instructions before edits.
- Keep the implementation within the declared path budget. If a needed change
  lies outside it, set NEEDS_REPLAN before changing it.
- First make the synthetic regression fail through the real shared closure,
  then implement the repair; do not claim a specific r4 low-level cause.
- Do not call Maven or Docker outside an explicitly later authorized canonical
  run. Keep all diagnostic/verifier command output out of evidence.
- Complete all AC-1 through AC-7 before setting READY_FOR_SIGNOFF. Do not
  self-accept and do not start a Step 5 retry without a separate owner decision.

## Implementation Result

> To be completed by the Ultra implementation session.

- implementation_summary:
- changed_paths:
- tests_and_results:
- manual_or_experience_evidence:
- deviations: none
- residual_risks:
- readiness: READY_FOR_SIGNOFF | NEEDS_REPLAN | BLOCKED

## References

- predecessor work item:
  docs/9.3.4/workitems/BUG-step5-package-image-runtime-inspect-r3-environment-diagnosis.md
- r4 safe terminal record:
  docs/9.3.4/evidence/step-5/step5-rehearsal-eimage-r4-postrun-integrity-inconsistency-20260721.md
- controlling Step 5 scope:
  docs/9.3.4/workitems/FEATURE-v934-release-authority-and-ci.md
