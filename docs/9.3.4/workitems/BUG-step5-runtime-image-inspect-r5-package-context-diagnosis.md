---
doc_type: delivery-spec
delivery_type: bug
version: 9.3.4
ticket: BUG-STEP5-RUNTIME-IMAGE-INSPECT-R5-PACKAGE-CONTEXT-DIAGNOSIS
status: NEEDS_REPLAN
canonical: true
execution_mode: ultra
approved_by: foggy-projects-via-user-delegated-continuation
approved_at: 2026-07-22
controlling_step5_item: FEATURE-V934-RELEASE-AUTHORITY-AND-CI
predecessors:
  - BUG-STEP5-RUNTIME-IMAGE-INSPECT-R4-ENVIRONMENT-DIAGNOSIS
open_questions: []
---

# Delivery Spec: Step 5 r5 package-context runtime-image diagnosis

## Document Purpose

- intended_for: Ultra implementation and independent signoff.
- purpose: Freeze one direct, non-authoritative package-component diagnosis
  after r4 proved generic Docker runtime-image inspection compatible.
- canonical_path:
  `docs/9.3.4/workitems/BUG-step5-runtime-image-inspect-r5-package-context-diagnosis.md`

## Goal

- version_goal: Determine whether the exact package component, rather than
  generic Docker compatibility, reproduces the Step 5 runtime-image boundary.
- target_outcome: Run exactly one isolated package-component invocation against
  the same tested source and read-only successful Step 4 input as the failed
  rehearsal, classify its terminal result safely, and leave no authority,
  candidate, package, or raw diagnostic artifact behind.

## Scope

- in_scope:
  - start from the clean diagnostic branch with all r4 documentation committed
    and pushed; temporarily detach only the disposable clone to the exact
    tested source of the failed rehearsal, then restore the branch unchanged;
  - use the existing successful Step 4 run root only as a read-only input;
    proceed only when this same disposable clone retains its canonical
    repo-relative Step 4 root, tested classes, and TEST XML tree with matching
    source/run bindings. Do not copy, materialize, alter, or reconstruct those
    inputs; otherwise stop before invocation as a precondition failure;
  - invoke the existing package CLI exactly once with the same logical run
    identity, a unique temporary output directory, and a temporary fixed-schema
    failure receipt; allow its direct Maven and no-pull Docker work only inside
    that invocation;
  - capture command output only transiently, independently validate any
    fixed-schema failure receipt, and classify exactly one result as
    `package-context-eimage-reproduced`,
    `package-context-passed-non-authoritative`,
    `package-context-precondition-failed`, or
    `package-context-inconclusive`;
  - remove the temporary package output, logs, receipt, and all temporary
    diagnostics after safe classification; restore the original branch and
    prove source, contract, target-pointer, and run-root state unchanged;
  - write a safe evidence record and update this work item with category,
    boolean/count results, cleanup, and no-authority status only.
- affected_modules:
  - disposable clone checkout and temporary package output only;
  - `docs/9.3.4/workitems/BUG-step5-runtime-image-inspect-r5-package-context-diagnosis.md`;
  - `docs/9.3.4/evidence/step-5/` for the safe terminal diagnosis record.
- external_dependencies: local WSL Docker Engine, Maven cache, and the
  existing run-owned Step 4 artifacts. No registry pull, remote publication,
  or GitHub action is permitted.

## Non-Goals

- out_of_scope:
  - a canonical release-gate rerun, Step 4/Unit/Integration/DB/external lane,
    portable replay, archive, candidate/final pointer, Step 6/7, release,
    tag, or publication;
  - changing the package tool, runner, Dockerfile, pinned base image,
    Maven/POM, Step 4/5/6 contracts, receipt schema, CI, production API/SPI,
    9.3.5, or 9.4.0;
  - retaining, publishing, or hashing raw Maven/Docker command text, output,
    logs, paths, image/container/OCI identity, endpoints, credentials, or
    digest values.
- do_not_touch:
  - the immutable failed rehearsal, its Step 4 root, historical E_IMAGE
    evidence, candidate/final authority pointers, and user dirty baseline;
  - any Docker resource not demonstrably owned by the one package invocation.

## Confirmed Decisions

| Decision | Rationale | Compatibility / Constraint |
|---|---|---|
| Reuse the Step 4 root read-only at its matching source/run identity | Direct package preflight must see the same tested tree without relabelling a failed authority run | input is never modified or promoted |
| Use the existing disposable clone's matching target tree | Package authority paths and bindings are repo-relative and require the complete tested classes/reports | no artifact materialization or cross-root splice is permitted |
| Temporarily detach the disposable clone | The current documentation branch is newer than the tested source | branch restoration/cleanliness is an acceptance gate |
| Invoke the package CLI once, not the release gate | It exercises the precise Maven/JAR/Docker package context that r4 intentionally excluded | no candidate, pointer, archive, or release authority exists |
| Treat a package success as non-authoritative | A component result cannot replace the full runner, archive, or portable replay | output is deleted after safe classification |
| Preserve only fixed receipt/category facts | Diagnosis must not leak raw runtime details | all raw package logs remain temporary and are removed |
| Stop before instrumentation or repair | Any safer detail refinement or product/tool change is a new governed solution | create a successor replan after r5 signoff |

## Acceptance Criteria

- [x] AC-1: Before the live attempt, the clone is clean, r4 docs are pushed,
  the temporary detached source exactly matches the successful Step 4 input,
  and its same-root Step 4/classes/TEST XML inputs are read-only and
  structurally valid. Missing or mismatched input stops before invocation.
- [ ] AC-2: Exactly one direct package-component invocation occurs with the
  matching logical run identity and an owned temporary output/receipt. No
  release-gate, test lane, portable replay, archive, or pointer command runs.
- [ ] AC-3: A failure produces and independently validates only the fixed
  safe receipt; a successful package result is explicitly classified
  `package-context-passed-non-authoritative`. Both paths retain no raw output.
- [x] AC-4: The final result is exactly one approved category, supported by
  safe booleans/counts and a no-authority assertion; unknown or malformed
  results are `package-context-inconclusive` and fail closed.
- [x] AC-5: Temporary package output, logs, receipt, and diagnostic directory
  are removed after classification. Docker cleanup is accepted only when the
  component itself reports its owned cleanup exact; any residue or uncertain
  cleanup fails the diagnosis.
- [x] AC-6: The original branch/head, tracked source, package-contract inputs,
  Step 4 root, and candidate/final pointer state are restored or unchanged;
  no package/candidate/archive artifact survives outside temporary storage.
- [x] AC-7: Safe evidence passes a privacy scan and records no raw command,
  output, path, identity, endpoint, credential, or digest. The work item does
  not implement a fix or authorize a fresh canonical rehearsal.
- [x] AC-8: If the package context reproduces the boundary or yields an
  inconclusive/precondition result, freeze a successor instrumented-diagnosis
  or remediation spec before any retry. If it passes, still require a new
  owner-governed decision before canonical Step 5.

## Contract / Data / Security Constraints

- API or event contract: no API, SPI, Dockerfile, runner, package, receipt,
  pointer, workflow, or CI contract change.
- data and migration: no business data or fixture mutation. The existing Step
  4 run root is read-only; all package output is temporary and removed.
- compatibility and rollback: detach/restore is confined to the disposable
  clone; deleting temporary output completes rollback. Historic failure state
  stays immutable.
- permissions and secrets: evidence contains only approved categories and safe
  booleans/counts. Raw process data is transient and must be discarded before
  evidence publication.

## Test and Evidence Obligations

| Item | Risk | Required Validation | Required Evidence |
|---|---|---|---|
| AC-1/AC-6 | critical | source/head/root/pointer before-after seals | safe boolean summary |
| AC-2/AC-3 | critical | exactly-one command graph plus safe receipt validation | category, receipt-valid boolean, no-authority boolean |
| AC-4/AC-5 | critical | category matrix and owned cleanup checks | safe count/cleanup summary |
| AC-7/AC-8 | critical | privacy scan and scope review | no-raw checklist and successor marker if needed |

## Bug Context

- bug_source: acceptance-found
- severity: critical
- environment: WSL Docker environment; r4 independently proved the generic
  runtime-image inspect path valid but intentionally did not invoke packaging.
- current_behavior: the canonical rehearsal failed at the package component's
  runtime-image boundary, while the generic isolated Docker exercise did not
  reproduce it.
- expected_behavior: one exact package-context diagnostic determines whether
  that component context itself reproduces the safe E_IMAGE boundary.
- reproduction_steps: run only the approved direct package-component attempt
  with temporary output and matching tested source/Step 4 input.
- reproduction_status: partial; generic environment compatibility confirmed,
  exact package context not yet replayed.
- existing_evidence: accepted r4 diagnosis and the immutable failed rehearsal
  receipt.
- existing_tests: package-tool receipt verifier, package negative suite, and
  r4 classification/privacy self-check.
- regression_protection: required for a later governed tooling repair; this
  diagnosis changes no product code.
- waiver_reason_and_risk: N/A.

## Risks and Open Questions

- known_risks: direct packaging can be expensive and writes raw logs, so it
  must remain temporary and be cleaned. A component pass is not a candidate;
  a component failure still may require safe instrumentation rather than a
  direct repair.
- open_questions: none.

## Ultra Execution Contract

- Read this work item, `CLAUDE.md`, the controlling Step 5 scope, r4 evidence,
  and existing package failure-receipt rules before acting.
- Run no more than the single allowed package-component invocation. Do not
  invoke the canonical release runner or any test lane.
- Do not persist raw package process data. Validate and retain only the fixed
  safe receipt/category facts after temporary output cleanup.
- Any failed cleanup, changed source/pointer/root, malformed receipt, or
  scope expansion is fail-closed. Set `NEEDS_REPLAN` if a repair or new
  instrumented detail is required.
- Complete the implementation result and set `READY_FOR_SIGNOFF`; do not
  self-accept.

## Implementation Result

> Ultra execution completed its single-attempt envelope but did not produce a
> diagnostically valid package-context result. It is therefore fail-closed and
> requires a successor replan, not signoff.

- implementation_summary: The same-source/read-only-Step-4 preflight passed
  and exactly one package CLI invocation was attempted. Its terminal outcome
  could not be independently validated through the fixed safe receipt, so the
  only permitted classification is `package-context-inconclusive`.
- changed_paths:
  - docs/9.3.4/workitems/BUG-step5-runtime-image-inspect-r5-package-context-diagnosis.md
  - docs/9.3.4/evidence/step-5/step5-runtime-image-inspect-r5-package-context-diagnosis-20260722.md
- tests_and_results: One bounded invocation only; preflight, temporary cleanup,
  branch restoration, source/package-contract/Step-4/pointer invariants, and
  privacy review passed. Fixed safe-receipt validation did not pass. No
  release gate, test lane, archive, candidate/final pointer, or publication
  command ran.
- manual_or_experience_evidence: The disposable-clone state was restored and
  no authority output survived. The terminal safe evidence records only the
  approved inconclusive classification and bounded booleans/counts.
- deviations: The attempted command did not reach a diagnostically valid
  package-component outcome because its fixed receipt contract was not
  established. This is recorded as a fail-closed execution result, not as a
  package or runtime-image finding.
- residual_risks: The exact package-context behavior remains unknown. A
  receipt-target preflight plus one corrected bounded diagnosis is required
  before any package retry, tool remediation, or canonical Step 5 decision.
- readiness: NEEDS_REPLAN

## Replan Status

- replan_status: required
- replan_reason: The critical fixed safe-receipt validation obligation was not
  met, so AC-2 and AC-3 remain unsatisfied and the package context is unknown.
- terminal_evidence:
  `docs/9.3.4/evidence/step-5/step5-runtime-image-inspect-r5-package-context-diagnosis-20260722.md`
- successor_work_item:
  `docs/9.3.4/workitems/BUG-step5-runtime-image-inspect-r6-receipt-target-package-context-diagnosis.md`
- signoff_eligibility: not eligible; do not create a delivery-signoff record
  for this inconclusive diagnostic.

## References

- controlling Step 5 scope:
  `docs/9.3.4/workitems/FEATURE-v934-release-authority-and-ci.md`
- accepted predecessor:
  `docs/9.3.4/workitems/BUG-step5-runtime-image-inspect-r4-environment-diagnosis.md`
- r4 signoff:
  `docs/9.3.4/acceptance/step5-runtime-image-inspect-r4-environment-diagnosis-signoff-20260722.md`
