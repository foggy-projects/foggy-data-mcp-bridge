---
doc_type: delivery-spec
delivery_type: bug
version: 9.3.4
ticket: BUG-STEP4-R7-ABSOLUTE-TARGET-FRESH-DIAGNOSTIC
status: ULTRA_EXECUTING
canonical: true
execution_mode: ultra
approved_by: foggy-projects-via-user-delegated-continuation
approved_at: 2026-07-22
controlling_step5_item: FEATURE-V934-RELEASE-AUTHORITY-AND-CI
predecessors:
  - BUG-STEP4-R7-INSPECT-FORMAT-FRESH-DIAGNOSTIC
  - BUG-STEP4-THRESHOLD-FREEZE-PREFLIGHT-CLASSIFIER
open_questions: []
---

# Delivery Spec: R7 absolute-target fresh Step 4 diagnostic

## Document Purpose

- intended_for: Ultra execution and independent diagnostic/capsule review.
- purpose: Replace the excluded R7 candidate invocation with exactly one new
  fresh diagnostic and one correctly formed absolute-target candidate sequence.
- canonical_path:
  docs/9.3.4/workitems/BUG-step4-r7-absolute-target-fresh-diagnostic.md

## Goal

- version_goal: Re-establish fresh, non-reusable Step 4 diagnostic evidence for
  the unchanged R7 executable tooling and coverage policy after the prior Cdiag
  was excluded by a caller-side candidate-target form violation.
- target_outcome: One clean pushed Cdiag is tested once in a new disposable
  clone. If it passes every post-run condition, it yields a validated
  review-only threshold candidate and two-member Git-safe capsule, but no
  Cfreeze, formal, package or version authority.

## Scope

- in_scope:
  - commit and push this approved work item together with the bounded
    correction of the excluded R7 failure evidence and the superseded classifier
    work item; that clean commit is the sole Cdiag input;
  - the Cdiag commit may alter only these documentation paths:
    docs/9.3.4/evidence/step-4/step4-r7-inspect-format-diagnostic-20260722-r1-postrun-candidate-generation-fail-closed.md,
    docs/9.3.4/workitems/BUG-step4-r7-inspect-format-fresh-diagnostic.md,
    docs/9.3.4/workitems/BUG-step4-threshold-freeze-preflight-classifier.md
    and this work item;
  - use a fresh non-shallow disposable clone and exactly one run ID:
    step4-r7-absolute-target-diagnostic-20260722-r2;
  - repeat the existing static/runtime owned-resource preflight: Step 4/5/6
    integrity, diagnostic state, overlay, source seal, CI contract, clean Git,
    absent run root, sanitized environment, governed port availability and
    existing Docker/Compose checks;
  - run exactly one canonical outer diagnostic with that run ID;
  - only after a strictly passing diagnostic and source-side validator, derive
    one candidate target from the disposable clone's canonical absolute root
    and this exact repository-relative logical evidence name:
    docs/9.3.4/evidence/step-4/step4-r7-absolute-target-diagnostic-20260722-r2-threshold-candidate.json;
  - derive the target once as canonical-clone-root plus that literal logical
    name, require it remains beneath that clone root, and use the same derived
    target for the one generator call and subsequent recomputation/verification;
  - before candidate generation, require only bounded booleans: target is
    absolute, its existing parent is canonical/non-symlink and the target does
    not exist. Do not persist a host path;
  - call the existing threshold-candidate generator exactly once with that
    absolute target, then perform the existing candidate recomputation/
    verification, Git-safe attestation, capsule build/verify and two
    independent reviews;
  - on success, retain candidate/capsule/reviews as uncommitted material in the
    disposable clone for a separately specified direct-child Cfreeze only.
- affected_modules:
  - existing Step 4 runner/validator/candidate/capsule tooling and run-owned
    output only;
  - provisional docs under docs/9.3.4/evidence/step-4 and quality records only
    after successful post-run validation.
- external_dependencies: local governed Docker Engine/Compose, Maven and
  existing database/external test services required by the unchanged runner.
  No registry publication, GitHub action or production deployment is allowed.

## Non-Goals

- out_of_scope:
  - retrying or reusing the excluded r1 run, its observation or its failed
    candidate invocation;
  - changing Step 4 source, freeze tooling, coverage policy, thresholds,
    contracts, manifests, runner, workflow, Maven/POM, Dockerfile, receipt
    schema, package tooling, CI semantics, API/SPI/module layout or Step 5-7;
  - Cfreeze, formal/release runner, package proof/rehearsal/replay, pointer/
    promotion, Step 6/7 authority, 9.3.5 or 9.4.0 work;
  - adding a preflight classifier, relaxing the absolute-target contract,
    writing raw output or stopping/cleaning unknown host resources.
- do_not_touch:
  - original user workspace and all historic authority/evidence except these
    three bounded correction paths: the r1 failure record, the r1 fresh-
    diagnostic work item and the superseded classifier work item named in this
    document;
  - any resource not proven owned by r2; raw logs, runtime/process/container
    identities, endpoints and credentials must not enter durable evidence.

## Confirmed Decisions

| Decision | Rationale | Compatibility / Constraint |
|---|---|---|
| New Cdiag and new run ID | r1 was permanently excluded by AC-6 | no r1 retry or authority reuse |
| Use existing absolute-target contract | Static audit established a caller-side violation, not a product defect | no tool/policy change |
| Literal target derivation | Candidate identity must not drift between preflight, publication and verify | canonical clone root + fixed logical name |
| Candidate target is canonical absolute | Existing atomic publisher rejects relative targets before publication | only safe booleans persisted |
| One post-run candidate attempt | Candidate remains a fail-closed authority boundary | no retry on any failure |
| Cfreeze remains a direct child | A later formalization must bind the newly tested Cdiag | no success-result commit before Cfreeze |

## Acceptance Criteria

- [ ] AC-1: This work item is committed/pushed as a clean non-shallow Cdiag.
  A fresh clone at that exact head passes the existing Step 4/5/6 integrity,
  diagnostic-state, overlay, source-seal, CI, environment and owned-resource
  preflight; r2 run root is absent.
- [ ] AC-2: Exactly one r2 outer diagnostic runs and the existing strict
  finalizer/validator establish diagnostic-observed without formal, candidate
  manifest or final authority.
- [ ] AC-3: The source-side validator passes. Immediately before the one
  candidate call, a safe preflight records only absolute/canonical-parent/
  target-absent booleans and candidate invocation count (zero if target
  preflight fails, one only for the permitted generator call). The target is
  derived exactly once from the canonical clone root plus the literal logical
  name in Scope, remains beneath that root, and is the same target passed to
  the one existing generator call and its immutable recomputation verifier.
- [ ] AC-4: Git-safe attestation and two-member capsule build/verify pass.
  Primary review independently recomputes diagnostic/candidate semantics and
  independent review materializes/rebuilds the capsule with no blocker.
- [ ] AC-5: Success records distinguish diagnostic-observed from formal, Step
  5, release and version authority. They authorize only proposal of one
  separately specified direct-child Cfreeze.
- [ ] AC-6: Any preflight, runner, validator, candidate, capsule, review,
  cleanup, privacy, scope or topology failure preserves only bounded facts,
  marks this item NEEDS_REPLAN and stops without a second attempt or authority
  reuse.

## Contract / Data / Security Constraints

- API or event contract: no public API, SPI, configuration, receipt or package
  contract change.
- data and migration: run-owned ephemeral test data/resources only; no business
  data migration or host-wide resource mutation.
- compatibility and rollback: unchanged source/policy means no deployment or
  migration. Any r2 failure leaves it non-reusable and requires a new governed
  plan.
- permissions and secrets: durable records may contain only phase, status,
  counts, hash-safe binding and target-form booleans. They must not contain
  command output, host filesystem paths, credentials, endpoints or
  runtime/container/process identity.

## Test and Evidence Obligations

| Item | Risk | Required Validation | Required Evidence |
|---|---|---|---|
| AC-1 | blocker | clean clone static/runtime preflight | bounded pass/no-drift booleans |
| AC-2 | blocker | one governed r2 diagnostic | strict status and cleanup facts |
| AC-3 | blocker | validator, safe target form, one candidate/recompute | target-form booleans, call count and candidate binding |
| AC-4 | critical | capsule plus two independent reviews | safe review verdicts |
| AC-5/6 | blocker | authority/scope/privacy/topology review | no-authority/no-retry decision |

## Bug Context

- bug_source: acceptance-found.
- severity: critical.
- environment: governed Step 4 post-run candidate tooling.
- current_behavior: r1 diagnostic itself completed, but its caller supplied a
  repository-relative candidate target to an existing absolute-target publisher.
  The target was rejected before candidate publication.
- expected_behavior: a future fresh sequence resolves a canonical absolute
  target before its sole candidate call and remains fail-closed otherwise.
- reproduction_steps: do not replay r1. Start from the new clean Cdiag and run
  r2 exactly once with the governed candidate sequence.
- reproduction_status: confirmed by static publisher contract and bounded
  invocation fact.
- existing_evidence: r1 fail-closed record; static basis that the existing
  freeze command forwards its output unchanged to the atomic publisher, which
  rejects non-absolute targets; and the existing relative-output negative
  coverage in the Step 4 XML negative tool.
- existing_tests: canonical outer runner, source-side diagnostic validator and
  threshold-candidate recomputation verifier.
- regression_protection: required through the governed r2 evidence chain.
- waiver_reason_and_risk: N/A.

## Risks and Open Questions

- known_risks: the full matrix remains costly and local Docker/Maven dependent.
  A correct candidate target does not relax any other fail-closed condition.
- open_questions: none.

## Approval Record

- approval_authority: project-owner delegated continuation direction.
- approved_by: foggy-projects-via-user-delegated-continuation.
- approved_at: 2026-07-22.
- approved_scope: exactly one r2 fresh diagnostic and one absolute-target
  candidate/capsule/review sequence; it does not approve Cfreeze, formal,
  Step 5 or later authority.

## Ultra Execution Contract

- Start only after this work item is pushed and a fresh disposable clone is
  clean/non-shallow at that Cdiag.
- Do not modify source or run r1. Use only r2 and no candidate retry.
- Resolve the candidate target to an absolute canonical location inside the
  disposable clone exactly once from the literal logical name in Scope; use
  that same target for preflight, candidate generation and verification.
  Persist only the required safe booleans, never its host path.
- Do not commit successful candidate/capsule/review material before a
  separately governed direct-child Cfreeze.
- If any condition fails, set NEEDS_REPLAN and stop. Complete every criterion
  before READY_FOR_SIGNOFF; do not self-accept.

## Implementation Result

> Approved for Ultra execution.

- implementation_summary: pending
- changed_paths: pending
- tests_and_results: pending
- manual_or_experience_evidence: pending
- deviations: none
- residual_risks: pending
- readiness: ULTRA_EXECUTING

## References

- excluded r1 work item:
  docs/9.3.4/workitems/BUG-step4-r7-inspect-format-fresh-diagnostic.md
- r1 failure record:
  docs/9.3.4/evidence/step-4/step4-r7-inspect-format-diagnostic-20260722-r1-postrun-candidate-generation-fail-closed.md
- static invocation basis:
  scripts/v934/step4/coverage_xml_tool.py and
  scripts/v934/step4/coverage_xml_negative_tool.py
- superseded classifier scope:
  docs/9.3.4/workitems/BUG-step4-threshold-freeze-preflight-classifier.md
- controlling Step 5 scope:
  docs/9.3.4/workitems/FEATURE-v934-release-authority-and-ci.md
