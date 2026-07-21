---
doc_type: delivery-spec
delivery_type: bug
version: 9.3.4
ticket: BUG-STEP5-PACKAGE-IMAGE-EIMAGE-CLASSIFICATION
status: APPROVED
canonical: true
execution_mode: ultra
approved_by: foggy-projects-via-user-direction (successor-overlay replan approved)
approved_at: 2026-07-21
open_questions: []
---

# Delivery Spec: Step 5 package-image `E_IMAGE` classification

## Document Purpose

- intended_for: normal-analysis / ultra-implementation / independent-signoff
- purpose: Freeze the proposed compatibility-preserving diagnostic refinement
  for repeated safe `package-image / E_IMAGE` failures.
- canonical_path:
  `docs/9.3.4/workitems/BUG-step5-package-image-eimage-classification.md`

## Goal

- version_goal: Make a future Step 5 failure distinguish its safe runtime-image
  boundary without exposing raw execution data or weakening fail-closed behavior.
- target_outcome: New runs retain the existing nine-field receipt and `E_IMAGE`
  code, while emitting one bounded, verifiable subphase that identifies the
  checkpoint category.

## Scope

- in_scope:
  - retain the outer five-field failure receipt, the nine-field receipt schema,
    and `error_code=E_IMAGE` compatibility;
  - retain historical `package-image` receipt reading, and add only these
    newly-emitted package subphases: `package-image-runtime-inspect`,
    `package-image-readback-precondition`, and
    `package-image-receipt-completeness`;
  - map the existing controlled runtime-inspect, readback-precondition, and
    receipt-completeness boundaries to the new allowlisted subphases without
    publishing raw command text, paths, output, image/container/OCI identity,
    or logs;
  - add synthetic negative/round-trip coverage and update the Step 5/Step 6
    hash and CI-contract closure required by the changed tool bytes;
  - reset authority through a clean Cdiag and perform the mandated fresh
    diagnostic → reviews → direct-child Cfreeze → formal chain before any later
    Step 5 rehearsal is considered.
- affected_modules:
  - `scripts/v934/step5/release_package_tool.py`
  - `scripts/v934/step5/SHA256SUMS`
  - `scripts/v934/step6/ci_contract_tool.py`
  - `scripts/v934/step6/ci-contract.json`
  - `scripts/v934/step6/SHA256SUMS`
  - `scripts/v934/step4/coverage-contract.json`
  - `scripts/v934/step4/coverage-thresholds.json`
  - `scripts/v934/step4/coverage_tool.py`
  - `scripts/v934/step4/coverage_xml_tool.py`
  - `scripts/v934/step4/SHA256SUMS`
  - `scripts/v934/step4/successor/overlay_tool.py`
  - `scripts/v934/step4/successor/overlay-contract.json`
  - `scripts/v934/step4/successor/SHA256SUMS`
  - governed `docs/9.3.4` workitems and evidence.
- external_dependencies: Existing Docker/Maven evidence environments are used
  only by the approved controlled Cdiag and mandated revalidation; no separate
  image replacement, pull, cleanup, or ambient-environment remediation is
  authorized.

## Non-Goals

- out_of_scope:
  - changing Dockerfile, frozen base-image identity, Maven/POM, public API/SPI,
    package layout, outer receipt schema, pointer format, CI workflow graph, or
    coverage policy;
  - adding a second diagnostic sidecar, raw command/log transport, hashes of
    raw logs, or any free-form failure detail;
  - modifying existing Cfreeze `f7da93c1ad79be2dede5494b99990092ba110071`,
    retrying r44/r45, or treating either as a success/candidate;
  - authority/final promotion, release publication, and Step 6/7 execution or
    authority runs; the static Step 6 hash/CI-contract closure required by a
    changed Step 5 tool remains in scope. 9.3.5 and 9.4.0 remain closed.
- do_not_touch:
  - historical r43/r44/r45 evidence and approvals;
  - fixed outer failure semantics and final-authority publication rules.

## Confirmed Decisions

| Decision | Rationale | Compatibility / Constraint |
|---|---|---|
| Existing outer and nine-field schemas stay fixed | Consumers and safe receipt proof remain valid | no fields or raw payload added |
| `E_IMAGE` remains the error code | Existing error-code consumers retain compatibility | precision moves only to bounded `subphase` |
| Historical `package-image` remains readable | r44/r45 evidence must remain verifiable | only new writes use new subphases |
| New Cdiag is mandatory | Cfreeze bytes cannot be modified | full authority chain restarts |

## Approved Design

Use the three bounded subphases in Scope rather than a new diagnostic sidecar
or new error codes. Static control-flow review maps the current broad
`E_IMAGE` only to runtime-inspect, readback-precondition, or
receipt-completeness boundaries; build and frozen-base validation already use
other bounded codes. This is the smallest compatible refinement and carries no
raw runtime payload.

## Acceptance Criteria

- [ ] AC-1: Legacy `package-image / E_IMAGE` receipts continue to verify.
- [ ] AC-2: Each new controlled `E_IMAGE` source maps to exactly one proposed
  allowlisted subphase; unknown paths remain fail-closed.
- [ ] AC-3: Receipt writer/reader and synthetic negatives reject malformed,
  preexisting, symlinked, mismatched, or raw-detail-bearing data.
- [ ] AC-4: Step 4 coverage-contract/threshold/path-allowlist and successor
  overlay closure, plus Step 4/5/6 hash and CI-contract closure, validate with
  no workflow, Dockerfile, POM, API, SPI, package-layout, or pointer drift.
- [ ] AC-5: A clean Cdiag and complete fresh required revalidation chain pass;
  no historical run artifacts are reused.

## Contract / Data / Security Constraints

- API or event contract: no public API/SPI or event change.
- data and migration: no migration or persistent business-data change.
- compatibility and rollback: old receipts remain accepted; absence, malformed
  data, or an unknown boundary remains fail-closed.
- permissions and secrets: no command text, paths, stdout/stderr, logs,
  image/container/OCI identity, credentials, endpoint, or raw hash payload may
  enter a receipt or evidence record.

## Test and Evidence Obligations

| Item | Risk | Required Validation | Required Evidence |
|---|---|---|---|
| AC-1/AC-3 | critical | synthetic writer/reader and negative matrix | safe enum/status/count summary |
| AC-2 | critical | deterministic mapping probes for each new boundary | run-bound safe subphase result |
| AC-4 | critical | Step 4 contract/threshold/path-allowlist validation, Step 4/5/6 manifests, and CI contract validation | closure pass record |
| AC-5 | blocker | fresh Cdiag through formal; a later rehearsal is excluded and separately owner-gated | new run-owned evidence only |

## Bug Context

- bug_source: acceptance-found
- severity: critical
- environment: WSL isolated Step 5 rehearsals r44 and r45 on exact Cfreeze.
- current_behavior: Both rehearsals correctly fail closed at
  `package-image / E_IMAGE`, but the current bounded classification cannot
  distinguish the remaining safe runtime-image boundaries.
- expected_behavior: A future controlled failure exposes exactly one new
  bounded subphase while preserving all current fail-closed and confidentiality
  rules.
- reproduction_steps: Do not rerun r44/r45. Reproduce only after a separately
  approved Cdiag and fresh governed chain.
- reproduction_status: confirmed
- existing_evidence:
  `step5-r44-package-image-fail-closed-20260721.md` and
  `step5-r45-fail-closed-20260721.md`.
- existing_tests: receipt reader/writer, package negatives, Step 5/6 closure.
- regression_protection: required
- waiver_reason_and_risk: N/A

## Risks and Open Questions

- known_risks: An enum/refinement mistake could reject valid historical
  receipts, leak raw detail, or drift the hash closure; each must fail closed.
- open_questions: none

## Replan Trigger

Implementation preflight established that a clean Cdiag for this changed Step
5 tool also requires the existing Step 4 machine-state and path-allowlist
closure:
`scripts/v934/step4/coverage-contract.json`,
`scripts/v934/step4/coverage-thresholds.json`, and
`scripts/v934/step4/coverage_tool.py`,
`scripts/v934/step4/coverage_xml_tool.py`, and
`scripts/v934/step4/SHA256SUMS`. Step 6 must then bind their new manifest hash
alongside the changed Step 5 manifest. These paths were not in the approved
`affected_modules` list. The exact prior owner approval was
`BUG-step5-package-image-eimage-classification-replan-step4-closure`, signed
by `foggy-projects` on 2026-07-21. No tool code, hash manifest, Docker/Maven
run, gate, or runtime evidence was changed before the resolved replan marker.

The approved Cdiag static closure then reached the canonical Step 4 successor
overlay positive control and correctly stopped with
`E_SUCCESSOR_OVERLAY_BINDING`. This is a new, smaller scope expansion: the
overlay still binds the prior diagnostic/formal projections of the changed
coverage contract and the prior coverage-tool bytes. A commit cannot repair
those stale bindings.

The exact owner-approved addition is limited to:

- content changes:
  - `scripts/v934/step4/successor/overlay_tool.py`
  - `scripts/v934/step4/successor/overlay-contract.json`
- hash-only closure:
  - `scripts/v934/step4/successor/SHA256SUMS`

The only semantic update is the synchronized existing Step 4 runtime-binding
identity for the coverage contract's diagnostic/formal projections and the
coverage tool. It adds no runtime-binding path, error code, sidecar, raw
payload, workflow, Dockerfile, Maven/POM, API/SPI, package-layout, release,
or pointer behavior.

The existing approved root Step 4 manifest and Step 6 files remain the
transitive closure only: root `scripts/v934/step4/SHA256SUMS` updates the
successor manifest and the two overlay entries; `scripts/v934/step6/ci_contract_tool.py`
and `scripts/v934/step6/ci-contract.json` bind the resulting Step 4 manifest;
`scripts/v934/step6/SHA256SUMS` then updates those exact entries. Step 5
bytes and its manifest need no further change.

`scripts/v934/step4/successor/declared-amendments.tsv` is explicitly excluded:
its path set is frozen and does not cover these Step 4 runtime bindings.
`scripts/v934/step4/coverage_xml_tool.py` must likewise retain a formalization
allowlist that excludes the overlay paths, because this is a Cdiag repair and
not a Cdiag-to-Cfreeze delta. The owner signed this exact three-path addition
on 2026-07-21; Docker/Maven and governed runtime evidence remain limited to
the subsequently required Cdiag chain.

## Approval Record

- approval_authority: direct project-owner continuation direction
- approved_by: `foggy-projects-via-user-direction`
- approved_at: 2026-07-21
- approved_scope: the bounded subphase Cdiag, its Step 5/6 static closure, and
  the approved Step 4 machine-state/path-allowlist closure, plus the required
  fresh Cdiag → diagnostic → reviews → Cfreeze → formal chain.
  A later Step 5 rehearsal remains a separately owner-gated action.
- prior_replan_approval:
  `BUG-step5-package-image-eimage-classification-replan-step4-closure`
- prior_replan_owner: `foggy-projects`
- prior_replan_scope: `coverage-contract.json`, `coverage-thresholds.json`,
  `coverage_tool.py`, `coverage_xml_tool.py`, and root Step 4 `SHA256SUMS`,
  with the already listed Step 6 transitive closure.
- successor_overlay_replan_approval:
  `BUG-step5-package-image-eimage-classification-replan-successor-overlay-closure`
- successor_overlay_replan_owner: `foggy-projects`
- successor_overlay_replan_scope: only `overlay_tool.py`,
  `overlay-contract.json`, and successor `SHA256SUMS`; root Step 4 and Step 6
  files remain their already-authorized transitive hash closure.
- scope_status: approved; no other paths are authorized.

## Ultra Execution Contract

- Status is `APPROVED`: implement only the full affected-module list in a
  clean Cdiag, including the three owner-approved successor-overlay paths.
  Any further sidecar, error-code, workflow, Dockerfile, Maven/POM, API/SPI,
  release/pointer, environment-remediation, or scope addition must return here
  as `NEEDS_REPLAN`.
- Complete the stated validation and append implementation summary, changed
  paths, tests, deviations, and residual risks. Set only `READY_FOR_SIGNOFF`
  after the approved implementation and evidence actually complete.

## Implementation Result

> Owner approval now covers the isolated Cdiag implementation and the minimal
> successor-overlay closure. The earlier static stop remains historical; no
> Docker/Maven run, gate, or runtime validation has started after it.

- readiness: APPROVED

## References

- related work item:
  `docs/9.3.4/workitems/BUG-step5-package-subphase-receipt.md`
- r44 evidence:
  `docs/9.3.4/evidence/step-5/step5-r44-package-image-fail-closed-20260721.md`
- r45 evidence:
  `docs/9.3.4/evidence/step-5/step5-r45-fail-closed-20260721.md`
