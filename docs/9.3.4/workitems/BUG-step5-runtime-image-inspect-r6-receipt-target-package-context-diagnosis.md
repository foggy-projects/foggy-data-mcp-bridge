---
doc_type: delivery-spec
delivery_type: bug
version: 9.3.4
ticket: BUG-STEP5-RUNTIME-IMAGE-INSPECT-R6-RECEIPT-TARGET-PACKAGE-CONTEXT-DIAGNOSIS
status: ULTRA_EXECUTING
canonical: true
execution_mode: ultra
approved_by: foggy-projects-via-user-delegated-continuation
approved_at: 2026-07-22
controlling_step5_item: FEATURE-V934-RELEASE-AUTHORITY-AND-CI
predecessors:
  - BUG-STEP5-RUNTIME-IMAGE-INSPECT-R5-PACKAGE-CONTEXT-DIAGNOSIS
open_questions: []
---

# Delivery Spec: Step 5 r6 receipt-target package-context diagnosis

## Document Purpose

- intended_for: Ultra implementation and independent signoff.
- purpose: Freeze one corrected, non-authoritative package-context diagnosis
  after r5 ended fail-closed before yielding a valid fixed safe receipt.
- canonical_path:
  `docs/9.3.4/workitems/BUG-step5-runtime-image-inspect-r6-receipt-target-package-context-diagnosis.md`

## Goal

- version_goal: Determine the exact package-component terminal category without
  weakening the immutable failure-receipt contract.
- target_outcome: First prove the owned temporary receipt target conforms to
  the existing package tool contract without invoking package work; then make
  exactly one direct package-component invocation against the same tested
  source and read-only Step 4 input, retain only a safe classification, and
  leave no authority output behind.

## Scope

- in_scope:
  - start from the clean diagnostic branch after r5's terminal evidence is
    committed and pushed; temporarily detach only the disposable clone to the
    exact source bound to the retained successful Step 4 input, then restore
    the branch unchanged;
  - before the live invocation, perform a no-output receipt-target preflight:
    prove the new target uses the immutable fixed basename and sibling
    placement enforced by the existing package tool, is in an existing real
    directory, and neither it nor the package output exists or is a link;
  - repeat all same-root Step 4/source/classes/TEST XML/report-inventory seals
    read-only. Any missing, mismatched, or changed input stops before a package
    invocation;
  - only after both preflights pass, invoke the existing package CLI exactly
    once with the matching logical run identity, temporary output, and that
    contract-conforming temporary fixed receipt; allow its direct Maven and
    no-pull Docker work only inside that invocation;
  - if it fails, independently validate the fixed safe receipt through the
    existing verifier before classifying it; if it succeeds, classify only
    `package-context-passed-non-authoritative` and remove its output;
  - remove all temporary output, receipt, logs, and diagnostics after safe
    classification; restore the branch and prove source, package contract,
    Step 4 input, and pointer state unchanged;
  - write only safe evidence and update this work item with category,
    booleans/counts, cleanup, and no-authority status.
- affected_modules:
  - disposable clone checkout and owned temporary diagnostic output only;
  - `docs/9.3.4/workitems/BUG-step5-runtime-image-inspect-r6-receipt-target-package-context-diagnosis.md`;
  - `docs/9.3.4/evidence/step-5/` for one safe terminal record.
- external_dependencies: local WSL Docker Engine, Maven cache, and retained
  run-owned Step 4 artifacts. No registry pull, remote publication, or GitHub
  action is permitted.

## Non-Goals

- out_of_scope:
  - a canonical release-gate rerun, Step 4/Unit/Integration/DB/external lane,
    portable replay, archive, candidate/final pointer, Step 6/7, release, tag,
    or publication;
  - changing the package tool, runner, Dockerfile, pinned base image,
    Maven/POM, Step 4/5/6 contracts, receipt schema, CI, production API/SPI,
    9.3.5, or 9.4.0;
  - retaining, publishing, or hashing raw Maven/Docker command text, output,
    logs, temporary locations, image/container/OCI identity, endpoints,
    credentials, or digest values.
- do_not_touch:
  - the immutable failed rehearsal, r5 terminal evidence, historical E_IMAGE
    evidence, candidate/final authority pointers, and user dirty baseline;
  - any Docker resource not demonstrably owned by this one package invocation.

## Confirmed Decisions

| Decision | Rationale | Compatibility / Constraint |
|---|---|---|
| Preserve the package tool's fixed receipt rule | r5's result was non-adjudicable at the caller envelope; relaxing the tool would weaken fail-closed receipt semantics | no package-tool or schema change |
| Preflight receipt ownership before live package work | A caller-side receipt setup error must be stopped before Maven/Docker work | no package CLI invocation during this preflight |
| Reuse the matching Step 4 root read-only in the same disposable clone | package authority bindings require the exact tested classes/reports and source identity | no artifact copying, reconstruction, or promotion |
| Allow one corrected package invocation only | it isolates the exact missing package-context evidence at bounded cost | no runner, archive, candidate, or pointer authority |
| Treat success as non-authoritative and invalid/missing receipt as inconclusive | component evidence cannot replace full Step 5, and ambiguity must fail closed | a later owner-governed decision remains required |

## Acceptance Criteria

- [ ] AC-1: The clone starts clean with r5 evidence pushed; the temporary
  receipt/output preflight proves the existing fixed receipt contract and
  ownership before any package CLI invocation.
- [ ] AC-2: The detached source, same-root Step 4/classes/TEST XML/report
  inventory seals are valid and read-only; missing or mismatched input stops
  before invocation.
- [ ] AC-3: Exactly one direct package-component invocation occurs only after
  AC-1 and AC-2 pass. No release gate, test lane, portable replay, archive, or
  pointer command runs.
- [ ] AC-4: Any package failure yields a fixed safe receipt that independently
  validates against the invocation's bounded inputs; package success is only
  `package-context-passed-non-authoritative`; missing/invalid receipt is
  `package-context-inconclusive` and no retry occurs.
- [ ] AC-5: The final result is exactly one approved category supported by
  safe booleans/counts and a no-authority assertion. A valid reproduction may
  use only `package-context-eimage-reproduced`; no raw runtime detail remains.
- [ ] AC-6: Owned temporary package output, receipt, logs, diagnostics, and
  component-owned Docker resources are removed or their verified cleanup is
  reported; uncertainty fails the diagnosis.
- [ ] AC-7: Original branch/head, tracked source, package-contract inputs,
  Step 4 input, and candidate/final pointer state are restored or unchanged;
  no package/candidate/archive artifact survives outside temporary storage.
- [ ] AC-8: Safe evidence passes a privacy scan and records no raw command,
  output, path, identity, endpoint, credential, or digest. It does not change
  code or authorize a fresh canonical rehearsal.
- [ ] AC-9: An E_IMAGE reproduction, precondition failure, or inconclusive
  result freezes a successor instrumented-diagnosis/remediation spec before
  any retry. A component pass still needs a new owner-governed Step 5 decision.

## Contract / Data / Security Constraints

- API or event contract: no API, SPI, Dockerfile, runner, package, receipt,
  pointer, workflow, CI, or production module contract change.
- data and migration: no business-data or fixture mutation. Step 4 is a
  read-only input; all package output is owned temporary state and removed.
- compatibility and rollback: detach/restore is confined to the disposable
  clone; deleting validated temporary output completes rollback. r5 and
  historical failure evidence remain immutable.
- permissions and secrets: persist only approved categories and safe
  booleans/counts. Raw process data is transient and discarded before evidence
  publication.

## Test and Evidence Obligations

| Item | Risk | Required Validation | Required Evidence |
|---|---|---|---|
| AC-1/AC-2 | critical | receipt-target plus source/Step-4/root preflight | safe booleans and zero/one invocation count |
| AC-3/AC-5 | critical | exact-one command graph and fixed receipt verifier | category, receipt-valid boolean, no-authority boolean |
| AC-6/AC-7 | critical | owned cleanup and before/after seals | cleanup/restoration/invariance booleans |
| AC-8/AC-9 | critical | privacy scan and scope review | no-raw checklist and successor marker if required |

## Bug Context

- bug_source: acceptance-found
- severity: critical
- environment: WSL Docker environment with a retained same-root Step 4 tree.
- current_behavior: r5 completed one bounded command envelope but could not
  validate its fixed safe receipt, leaving package-context behavior unknown.
- expected_behavior: the receipt target is validated before package work so a
  single invocation yields an adjudicable safe component category or a clearly
  classified precondition stop.
- reproduction_steps: execute only the approved no-output preflight followed,
  when eligible, by one direct package-component invocation.
- reproduction_status: partial; generic Docker compatibility is accepted and
  r5 established the need for a corrected receipt-target envelope.
- existing_evidence: accepted r4 environment diagnosis and r5 terminal
  inconclusive evidence.
- existing_tests: package-tool receipt verifier, package negative suite, and
  r4 classification/privacy self-check.
- regression_protection: required for any later package-tool reliability fix;
  r6 changes no product/tool code.
- waiver_reason_and_risk: N/A.

## Risks and Open Questions

- known_risks: one direct package attempt can be expensive and creates raw
  transient data, so output must be cleaned. A component pass is not a
  candidate; a failure may need a targeted successor rather than direct repair.
- open_questions: none.

## Ultra Execution Contract

- Read this work item, `CLAUDE.md`, the controlling Step 5 scope, r4/r5
  evidence, and existing package failure-receipt rules before acting.
- Run the no-output receipt-target preflight before any package CLI command;
  if it fails, set `NEEDS_REPLAN` and do not consume the one live invocation.
- Run no more than the one allowed direct package-component invocation. Do not
  invoke the canonical runner or any test lane.
- Do not persist raw package process data. Independently validate a failure
  receipt and retain only fixed safe facts after temporary output cleanup.
- Any failed cleanup, changed source/pointer/root, malformed receipt, or scope
  expansion is fail-closed. Set `NEEDS_REPLAN` if a repair or new instrumented
  detail is required.
- Complete the implementation result and set `READY_FOR_SIGNOFF`; do not
  self-accept.

## Implementation Result

> Ultra execution is in progress under the receipt-target preflight and
> exact-one-invocation boundary. R5 remains fail-closed and is not eligible
> for delivery signoff.

- implementation_summary: pending
- changed_paths: pending
- tests_and_results: pending
- manual_or_experience_evidence: pending
- deviations: none
- residual_risks: pending
- readiness: ULTRA_EXECUTING

## References

- controlling Step 5 scope:
  `docs/9.3.4/workitems/FEATURE-v934-release-authority-and-ci.md`
- predecessor terminal record:
  `docs/9.3.4/workitems/BUG-step5-runtime-image-inspect-r5-package-context-diagnosis.md`
- r5 evidence:
  `docs/9.3.4/evidence/step-5/step5-runtime-image-inspect-r5-package-context-diagnosis-20260722.md`
- accepted predecessor:
  `docs/9.3.4/workitems/BUG-step5-runtime-image-inspect-r4-environment-diagnosis.md`
