---
doc_type: delivery-spec
delivery_type: bug
version: 9.3.4
ticket: BUG-STEP5-RUNTIME-IMAGE-INSPECT-FORMAT-REMEDIATION
status: NEEDS_REPLAN
canonical: true
execution_mode: ultra
approved_by: foggy-projects-via-user-delegated-continuation
approved_at: 2026-07-22
controlling_step5_item: FEATURE-V934-RELEASE-AUTHORITY-AND-CI
predecessors:
  - BUG-STEP5-RUNTIME-IMAGE-INSPECT-R6-RECEIPT-TARGET-PACKAGE-CONTEXT-DIAGNOSIS
open_questions: []
successor_replan: BUG-STEP4-R7-INSPECT-FORMAT-WORKFLOW-STATE-RESET
---

# Delivery Spec: Step 5 runtime-image inspect format remediation

## Document Purpose

- intended_for: Ultra implementation and independent signoff.
- purpose: Correct the now-reproducible runtime-image inspect format defect
  while preserving the fixed safe receipt, strict identity validation, and
  all release-authority boundaries.
- canonical_path:
  `docs/9.3.4/workitems/BUG-step5-runtime-image-inspect-format-remediation.md`

## Goal

- version_goal: Restore the package component's strict runtime-image identity
  inspection so a correctly built image reaches the existing readback/audit
  path without weakening fail-closed behavior.
- target_outcome: Replace the non-record-separating Docker Go-template form
  with Docker's documented line-emitting form, prove the command format is
  passed exactly as intended in Docker-free regression coverage, refresh the
  required integrity closures, complete the new-source Step 4 chain, and run
  one bounded post-remediation package proof.

## Scope

- in_scope:
  - in `scripts/v934/step5/release_package_tool.py`, make the runtime-image
    inspect template emit exactly the three required records through the
    documented Go-template line-emitting function, rather than literal escape
    markers; keep the existing three-record, ID-shape, and linux/amd64 checks
    strict;
  - add Docker-free regression coverage in the existing package negative/self
    test path proving the inspect command receives the exact documented
    template, accepts the canonical three-record fixture, and rejects literal
    escape-marker, malformed, and wrong-platform fixtures as `E_IMAGE`;
  - retain the nine-field failure-receipt schema, `E_IMAGE`, existing refined
    subphase behavior, staging/cleanup, Dockerfile/base identity, Maven/POM,
    package layout, and pointer semantics unchanged;
  - update only the exact integrity closure required by this source change:
    `scripts/v934/step5/SHA256SUMS`, `scripts/v934/step4/SHA256SUMS`,
    `scripts/v934/step6/ci_contract_tool.py`,
    `scripts/v934/step6/ci-contract.json`, and
    `scripts/v934/step6/SHA256SUMS`;
  - run the existing static package/CI closure validation, then reset through
    a clean new-source Step 4 diagnostic, review, Cfreeze, formal, quality,
    coverage-audit, and scoped reacceptance chain before any package proof;
  - after that fresh Step 4 source/run binding succeeds, perform exactly one
    non-authoritative direct package proof with a conforming temporary fixed
    receipt and retain only safe result facts.
- affected_modules:
  - `scripts/v934/step5/release_package_tool.py` and its Step 4/5/6 integrity
    closure;
  - governed `docs/9.3.4` workitems, evidence, quality, and acceptance records;
  - one disposable clone and owned temporary diagnostic output for the final
    post-remediation package proof.
- external_dependencies: local WSL Docker Engine, Maven cache, the governed
  Step 4 test environment, and retained CI-contract validators. No registry
  pull, remote publication, GitHub action, or production deployment is
  permitted.

## Non-Goals

- out_of_scope:
  - changing the Dockerfile, frozen base image, Maven/POM, package layout,
    API/SPI, receipt schema, error-code/subphase vocabulary, runner/workflow
    graph, coverage threshold, branch rule, release, tag, publication, Step 6,
    Step 7, 9.3.5, or 9.4.0;
  - adding retries, waits, relaxed parsing, fallback platform/ID acceptance,
    a diagnostic sidecar, raw command/log transport, or raw runtime detail;
  - reuse or promotion of R6, R5, historical failed rehearsal, or any old
    Step 4 artifact as authority for the changed source.
- do_not_touch:
  - fixed outer/nine-field receipt semantics; historical evidence; candidate
    and final authority pointers; user dirty baseline; any Docker resource not
    demonstrably owned by the one final proof.

## Confirmed Decisions

| Decision | Rationale | Compatibility / Constraint |
|---|---|---|
| Use Docker's documented line-emitting template function | The current caller passes literal escape markers, while the parser requires three actual records | strict three-record parser remains unchanged |
| Repair the caller format, not the parser | Accepting literal escape markers would hide a malformed command contract | malformed output remains `E_IMAGE` |
| Preserve receipt schema/code/subphase | R6 already supplies the correct bounded public failure semantics | no new fields, enums, sidecars, or raw data |
| Treat tool bytes as a new authority source | Step 4 and CI integrity manifests bind this package tool | fresh Cdiag through formal/reacceptance is mandatory |
| Limit runtime proof to one direct package component after fresh Step 4 | It verifies the exact repaired boundary without claiming release authority | no canonical Step 5 runner/candidate/archive/pointer |

## Acceptance Criteria

- [ ] AC-1: The runtime-image inspect command uses the documented
  line-emitting template form and emits exactly three records; the strict
  parser still requires exactly three fields, a valid ID shape, and
  linux/amd64.
- [ ] AC-2: Docker-free regression coverage proves the exact command-template
  argument, canonical three-record acceptance, literal-escape/malformed output
  rejection, wrong platform rejection, and no raw fixture marker in any safe
  receipt/result.
- [ ] AC-3: Existing package failure receipts, including historical broad and
  refined `E_IMAGE` forms, remain reader/verifier compatible with unchanged
  nine-field schema and error/subphase semantics.
- [ ] AC-4: Step 4/5/6 integrity manifests and CI contract validate the exact
  approved closure; no Dockerfile, Maven/POM, workflow, API/SPI, threshold,
  pointer, or unlisted source path drifts.
- [ ] AC-5: A clean new-source Step 4 diagnostic → review → Cfreeze → formal
  → quality/coverage-audit → scoped reacceptance chain passes with no historic
  run reuse and with the prior source authority retired.
- [ ] AC-6: Exactly one direct post-remediation package proof runs only after
  AC-5, with matching fresh Step 4 input and a conforming temporary receipt.
  A component success is `package-context-passed-non-authoritative`; a valid
  `E_IMAGE` or any invalid/inconclusive result remains fail-closed.
- [ ] AC-7: The final proof removes owned output/receipt/logs/resources,
  restores the disposable clone, preserves source/contract/Step-4/pointers,
  retains no raw runtime data, and creates no package/candidate/archive/final
  authority output.
- [ ] AC-8: The work item records the actual validation classes and bounded
  results, and all implementation/quality/coverage/signoff records distinguish
  this repair from Step 5 acceptance. No full release-gate retry occurs here.
- [ ] AC-9: If the direct proof is not a non-authoritative component pass,
  freeze a successor diagnosis/remediation before another attempt. If it
  passes, freeze a separate owner-governed fresh canonical Step 5 rehearsal
  contract; neither outcome directly unlocks Step 6/7, 9.3.5, or 9.4.0.

## Contract / Data / Security Constraints

- API or event contract: no production API/SPI/event/config contract change.
  The fixed receipt remains the only persisted package failure interface.
- data and migration: no business-data/fixture mutation. The fresh Step 4
  run is authoritative only for this changed source; proof output is temporary.
- compatibility and rollback: the change is a one-line format-contract repair
  plus regression coverage/closure. Revert restores the prior strict failure,
  never a relaxed success path.
- permissions and secrets: no raw Docker/Maven output, paths, identities,
  endpoints, credentials, or digests may enter receipt, evidence, or docs.

## Test and Evidence Obligations

| Item | Risk | Required Validation | Required Evidence |
|---|---|---|---|
| AC-1/AC-2 | critical | Docker-free package negative/self-test | safe case count/status and privacy result |
| AC-3/AC-4 | critical | receipt compatibility plus Step 4/5/6 hash/CI validators | closure pass/fail-safe record |
| AC-5 | blocker | fresh new-source Step 4 complete mandated chain | run-owned Step 4/review/reacceptance evidence |
| AC-6/AC-7 | blocker | one direct package proof and cleanup/invariance checks | category, receipt-valid, cleanup/pointer booleans |
| AC-8/AC-9 | critical | scope/privacy/independent signoff | no-authority and successor decision record |

## Bug Context

- bug_source: acceptance-found
- severity: critical
- environment: WSL Docker package context; R4 generic inspection did not
  reproduce, while R6 validly reproduced the bounded package failure class.
- current_behavior: the inspect caller supplies literal escape markers to a
  Docker Go template even though its strict parser requires three actual
  records, causing the package path to reject the resulting noncanonical
  identity shape as `E_IMAGE`.
- expected_behavior: the caller uses Docker's documented line-emitting
  template form, producing the three records consumed by the existing strict
  identity validator.
- reproduction_steps: run Docker-free template regression coverage; only after
  the fresh source's Step 4 chain, run the single bounded package proof.
- reproduction_status: confirmed by source-level command construction plus
  R6's valid bounded package-context reproduction.
- existing_evidence: accepted R4 environment diagnosis and R6 safe receipt
  diagnosis.
- existing_tests: package receipt reader/writer/negative suite and Step 4/5/6
  integrity/CI validators.
- regression_protection: required.
- waiver_reason_and_risk: N/A.

## Risks and Open Questions

- known_risks: integrity closure and fresh Step 4 reset are costly but required
  by source binding. A package component pass remains insufficient for Step 5
  release authority.
- open_questions: none.

## Ultra Execution Contract

- Read this work item, `CLAUDE.md`, R4/R5/R6 evidence, Step 4 reset rules,
  package receipt tests, and Step 4/5/6 hash/CI contracts before editing.
- Change only the approved format construction, regression coverage, exact
  closure, and governed documentation. If another path requires modification,
  set `NEEDS_REPLAN` before changing it.
- Run Docker-free checks before any fresh Step 4 or Docker activity. Never
  loosen identity validation or add an automatic retry/fallback.
- Complete the mandatory fresh Step 4 chain before the one allowed package
  proof. Do not call the canonical release runner or publish any authority.
- Persist only bounded safe facts; remove temporary output and set
  `READY_FOR_SIGNOFF` only after all criteria are met. Do not self-accept.

## Implementation Result

> The source repair and its Docker-free/static integrity closure are complete,
> but no new-source Step 4 diagnostic may begin from the inherited
> `formal-ready / confirmed` machine state. The necessary reset paths are
> outside this work item's declared source-repair closure, so execution is
> fail-closed pending the approved dedicated Cdiag successor.

- implementation_summary: corrected the runtime-image inspect caller to use
  Docker's documented line-emitting template function, retained the strict
  three-record/ID/linux-amd64 parser, and added Docker-free regression probes
  for the exact template, canonical success and fail-closed malformed/literal-
  escape/wrong-platform cases.
- changed_paths: `scripts/v934/step5/release_package_tool.py` plus its exact
  Step 5 -> Step 4 -> Step 6 integrity/CI closure and this governed work item.
- tests_and_results: Python syntax, package Docker-free negative/self-test,
  Step 4/5/6 manifest verification, Step 4 contract validation, and Step 6
  workflow-contract/self-negative validation passed. No Maven, Docker, outer
  Step 4 runner, package proof, candidate, pointer or release action ran.
- manual_or_experience_evidence: static results establish only the repaired
  source and closure; they do not constitute Step 4 diagnostic, formal, Step 5
  or version authority.
- deviations: the mandatory fresh Step 4 Cdiag requires an exact workflow-
  state reset that was not listed in this work item's declared closure.
- residual_risks: the repaired source has not yet entered a fresh Step 4
  diagnostic/formal chain or the permitted one-shot package proof; previous
  authority remains non-reusable for the changed bytes.
- readiness: NEEDS_REPLAN

## Replan Status

- replan_status: required and successor approved.
- replan_reason: current governed Step 4 machine state is
  `formal-ready / confirmed`, while diagnostic mode requires the exact
  `diagnostic-ready / diagnostic-pending` predecessor shape. Altering those
  state documents and their Step 4 -> Step 6 mechanical closure would exceed
  the source-repair scope.
- successor_work_item:
  `docs/9.3.4/workitems/BUG-step4-r7-inspect-format-workflow-state-reset.md`
- signoff_eligibility: not eligible; no delivery-signoff may be created for
  this remediation until the successor reset and fresh Step 4 chain complete.

## References

- controlling Step 5 scope:
  `docs/9.3.4/workitems/FEATURE-v934-release-authority-and-ci.md`
- predecessor:
  `docs/9.3.4/workitems/BUG-step5-runtime-image-inspect-r6-receipt-target-package-context-diagnosis.md`
- R6 evidence:
  `docs/9.3.4/evidence/step-5/step5-runtime-image-inspect-r6-receipt-target-package-context-diagnosis-20260722.md`
- Docker formatting reference: `https://docs.docker.com/engine/cli/formatting/`
