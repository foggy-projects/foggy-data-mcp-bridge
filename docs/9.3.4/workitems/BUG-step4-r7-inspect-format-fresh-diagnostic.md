---
doc_type: delivery-spec
delivery_type: bug
version: 9.3.4
ticket: BUG-STEP4-R7-INSPECT-FORMAT-FRESH-DIAGNOSTIC
status: APPROVED
canonical: true
execution_mode: ultra
approved_by: foggy-projects-via-user-delegated-continuation
approved_at: 2026-07-22
controlling_step5_item: FEATURE-V934-RELEASE-AUTHORITY-AND-CI
predecessors:
  - BUG-STEP4-R7-INSPECT-FORMAT-WORKFLOW-STATE-RESET
  - BUG-STEP5-RUNTIME-IMAGE-INSPECT-FORMAT-REMEDIATION
open_questions: []
---

# Delivery Spec: R7 inspect-format fresh Step 4 diagnostic

## Document Purpose

- intended_for: Ultra execution and independent diagnostic/capsule review.
- purpose: Authorize exactly one fresh all-lane Step 4 diagnostic for the R7
  repaired source after its accepted workflow-state reset, and establish only
  the evidence needed to decide whether a separate direct-child Cfreeze may be
  proposed.
- canonical_path:
  `docs/9.3.4/workitems/BUG-step4-r7-inspect-format-fresh-diagnostic.md`

## Goal

- version_goal: Replace non-reusable historical Step 4 authority with a fresh
  diagnostic observation bound to the repaired R7 tool bytes and reviewed
  diagnostic-pending state.
- target_outcome: One clean pushed Cdiag is tested once in a new disposable
  clone; if all checks pass, it yields a validated threshold candidate and
  two-member Git-safe capsule for review only. It does not produce a Cfreeze,
  formal result, package result or version authority.

## Scope

- in_scope:
  - commit and push this approved work item, then use that clean, non-shallow
    commit as the sole Cdiag input for a new disposable clone;
  - perform static preflight in that clone: exact Step 4/5/6 manifests,
    diagnostic-state contract, successor overlay, source seal and CI workflow
    contract; verify clean Git state, absent run root and a unique safe run ID;
  - perform runtime preflight only through current governed checks: required
    commands/artifacts, fail-closed Docker engine/compose checks, governed
    service-port availability, sanitized Git/Maven/JVM/Spring/authority
    environment, and no concurrent Step 4 authority run;
  - run exactly one canonical
    `scripts/verify-v934-step4-coverage.sh diagnostic <unique-run-id>`
    invocation in the disposable clone;
  - after a passing diagnostic only, run the existing source-side diagnostic
    validator, build/recompute/verify the immutable threshold candidate,
    attest/build/verify the two-member Git-safe capsule, and obtain primary
    plus independent review of candidate/source and capsule reconstruction;
  - retain only allowed safe candidate/capsule/review material needed by a
    separately specified Cfreeze, without changing current machine state.
- affected_modules:
  - existing Step 4 runner/validator and run-owned diagnostic output only;
  - one disposable clone and, on success, provisional governed
    `docs/9.3.4/evidence/step-4` and quality records carried only by a later
    direct-child Cfreeze.
- external_dependencies: local WSL Docker Engine and Compose, local Maven and
  fixed JaCoCo artifacts, governed database/external test services and the
  existing Step 4 authority lock. No registry pull, remote publication,
  GitHub action or production deployment is permitted.

## Non-Goals

- out_of_scope:
  - changing source, tests, coverage policy/floors/critical set/exclusions,
    state documents, manifests, validators, runner, workflow, Maven/POM,
    Dockerfile/base, receipt schema, API/SPI/module layout or CI semantics;
  - Cfreeze, formal/release runner, package proof/rehearsal/replay, candidate
    pointer/final promotion, Step 6/7 authority, 9.3.5 or 9.4.0 work;
  - retrying this diagnostic, reusing old diagnostic/formal/candidate/capsule
    authority, stopping/cleaning unknown host services, global Docker cleanup
    or treating a generic Docker preflight as diagnostic success.
- do_not_touch:
  - original user workspace and all historic authority/evidence;
  - any resource not proven owned by the new run ID and its existing governed
    labels/project scope; raw logs, runtime/process/container identities,
    endpoints and credentials must not enter durable evidence.

## Confirmed Decisions

| Decision | Rationale | Compatibility / Constraint |
|---|---|---|
| New disposable clone and one run ID | Old targets and source authority are non-reusable | no current-clone target reuse or retry |
| Runner remains the authority | It already performs all-lane, cleanup, source-seal and privacy gates | no manual lane substitution or relaxed preflight |
| Candidate/capsule are review-only | Diagnostic does not freeze thresholds | state stays diagnostic-pending |
| Cfreeze must be a direct child | Formal topology requires the tested Cdiag as its exact parent | do not commit successful diagnostic/review docs before later Cfreeze |
| Fail closed on any environment conflict | Unknown host resources cannot be safely altered | do not stop ports/services or delete unowned Docker resources |

## Acceptance Criteria

- [ ] AC-1: This work item is committed/pushed, then a fresh non-shallow clone
  at that exact clean Cdiag passes the Step 4/5/6 integrity, diagnostic-state,
  overlay, source-seal and CI static preflight; the selected run root is absent
  and no authority/run environment override is inherited.
- [ ] AC-2: Exactly one unique diagnostic invocation runs. It seals a passing
  diagnostic with all governed lanes, required cardinalities, negative and
  sensitive gates, source-before/after identity and run-ID-owned cleanup; it
  generates no formal/candidate/final authority artifact.
- [ ] AC-3: Post-run source-side diagnostic validation, threshold-candidate
  build/recomputation/verification, Git-safe attestation and two-member capsule
  build/verification all pass without changing current diagnostic-pending
  machine state.
- [ ] AC-4: Primary review independently recomputes diagnostic/candidate
  semantics, independent review materializes/rebuilds the capsule, and both
  report no blocker or required unlisted change.
- [ ] AC-5: Safe result records distinguish `diagnostic-observed` from formal,
  Step 5, release and version authority. A success may authorize only proposal
  of one separately specified direct-child Cfreeze; it does not execute one.
- [ ] AC-6: Any failed preflight, runner, validation, review, cleanup,
  privacy, scope or topology condition is fail-closed: preserve only bounded
  safe failure facts, mark this item `NEEDS_REPLAN`, and do not make a second
  attempt or reuse the run.

## Contract / Data / Security Constraints

- API or event contract: no public API, SPI, configuration, receipt or package
  contract change.
- data and migration: runner-owned ephemeral test data/resources only; all
  cleanup uses existing run-ID ownership checks. No business-data migration or
  host-wide resource mutation.
- compatibility and rollback: unchanged code/policy means no deployment or
  migration action. A diagnostic failure leaves prior formal authority
  non-reusable and requires a new governed plan.
- permissions and secrets: durable records may contain only phase, status,
  count, hash-safe binding and boolean facts. They must not contain raw command
  output, filesystem paths, credentials, endpoints, runtime/container/process
  identity, image detail or unstructured log. The capsule may contain only the
  validated safe attestation and JaCoCo XML.

## Test and Evidence Obligations

| Item | Risk | Required Validation | Required Evidence |
|---|---|---|---|
| AC-1 | blocker | clean fresh-clone static preflight | pass/fail and no-drift booleans |
| AC-2 | blocker | exactly one full diagnostic runner | safe status, lane/cardinality/cleanup facts |
| AC-3 | blocker | validator, candidate recompute, attestation/capsule verify | safe candidate/capsule binding facts |
| AC-4 | critical | two independent read-only reviews | review verdicts and bounded findings |
| AC-5/AC-6 | blocker | authority/scope/privacy/topology review | no-authority/no-retry decision |

## Bug Context

- bug_source: acceptance-found.
- severity: critical.
- environment: WSL hosted Docker diagnostic authority environment.
- current_behavior: the R7 runtime-image inspect caller bytes changed after a
  valid package-context finding, so all historical Step 4 diagnostic/formal
  authority is non-reusable for the repair.
- expected_behavior: one fresh all-lane diagnostic proves the changed source
  under the existing strict authority chain before any Cfreeze or package step.
- reproduction_steps: start only from the accepted clean Cdiag and invoke the
  governed diagnostic once in a new disposable clone.
- reproduction_status: fresh diagnostic required; no result is presumed.
- existing_evidence: accepted R7 source repair and accepted R7 workflow-state
  reset Cdiag.
- existing_tests: Step 4 static validators/negatives and the canonical outer
  diagnostic runner.
- regression_protection: required.
- waiver_reason_and_risk: N/A.

## Risks and Open Questions

- known_risks: the full matrix is costly and depends on local Docker/Maven
  health. An occupied governed port, unavailable daemon, stale lock, cleanup
  failure or any nonconforming evidence remains a hard stop, not a retry cue.
- open_questions: none.

## Approval Record

- approval_authority: project-owner delegated continuation direction.
- approved_by: `foggy-projects-via-user-delegated-continuation`.
- approved_at: 2026-07-22.
- approved_scope: exactly one fresh diagnostic and its review-only candidate/
  capsule validation. It does not approve Cfreeze, formal, Step 5 or later
  roadmap action.

## Ultra Execution Contract

- Start only after this work item is pushed and the disposable clone is clean.
  Use a new unique run ID and never reuse target output from another clone.
- Run static and owned-resource preflight first. Do not infer safe authority
  from a socket/CLI alone; let the runner perform its own fail-closed engine
  checks. Do not stop or remove unknown host resources.
- After the single diagnostic attempt, do not commit any success result,
  candidate, capsule or review record before a separately governed Cfreeze;
  direct-parent topology is mandatory. Failure evidence may be recorded only
  after declaring this Cdiag unusable.
- If any declared condition fails or a new path/semantic change is needed, set
  `NEEDS_REPLAN` and stop. Do not retry the diagnostic or advance authority.
- Complete all criteria before setting `READY_FOR_SIGNOFF`; do not self-accept.

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

- accepted Cdiag predecessor:
  `docs/9.3.4/workitems/BUG-step4-r7-inspect-format-workflow-state-reset.md`
- R7 source repair predecessor:
  `docs/9.3.4/workitems/BUG-step5-runtime-image-inspect-format-remediation.md`
- controlling Step 5 scope:
  `docs/9.3.4/workitems/FEATURE-v934-release-authority-and-ci.md`
