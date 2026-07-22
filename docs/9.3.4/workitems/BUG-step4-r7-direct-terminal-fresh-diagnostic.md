---
doc_type: delivery-spec
delivery_type: bug
version: 9.3.4
ticket: BUG-STEP4-R7-DIRECT-TERMINAL-FRESH-DIAGNOSTIC
status: NEEDS_REPLAN
canonical: true
execution_mode: ultra
approved_by: foggy-projects-via-user-delegated-continuation
approved_at: 2026-07-22
controlling_step5_item: FEATURE-V934-RELEASE-AUTHORITY-AND-CI
predecessors:
  - BUG-STEP4-R7-ABSOLUTE-TARGET-FRESH-DIAGNOSTIC
open_questions: []
---

# Delivery Spec: R7 direct-terminal fresh Step 4 diagnostic

## Document Purpose

- intended_for: Ultra execution and independent diagnostic/capsule review.
- purpose: Replace excluded r2 launch finalization with exactly one fresh
  terminal-direct diagnostic and absolute-target candidate sequence.
- canonical_path:
  docs/9.3.4/workitems/BUG-step4-r7-direct-terminal-fresh-diagnostic.md

## Goal

- version_goal: Re-establish fresh Step 4 evidence for unchanged R7 executable
  tooling and coverage policy after r2 could not establish a runner finalizer.
- target_outcome: A clean pushed Cdiag is run once through a foreground,
  terminal-managed runner. Only a strictly finalized diagnostic may proceed to
  one review-only absolute-target candidate/capsule sequence.

## Scope

- in_scope:
  - commit and push this approved work item with only the bounded r2 work-item
    state and r2 failure record; this three-document clean commit is the sole
    Cdiag input;
  - use a fresh non-shallow disposable clone and exactly one run ID:
    step4-r7-direct-terminal-diagnostic-20260722-r3;
  - repeat the existing Step 4/5/6 integrity, diagnostic-state, overlay,
    correct Step 4 source-seal, CI, clean-Git, absent-run-root, sanitized
    environment, governed-port and Docker/Compose preflight;
  - invoke exactly one foreground terminal-managed command that directly
    replaces its foreground process with the existing canonical r3 runner and
    remains attached until that runner exits. No launcher/agent may return a
    proxy state before the runner exit; no nohup, backgrounding, detached
    process, intermediary bash-c wrapper, custom exit marker or external
    completion file is allowed. An owned temporary raw-output redirection is
    allowed only if the terminal command's own exit status is exactly the
    runner's exit status and the raw file is deleted after safe classification;
  - accept completion only from the terminal result plus the runner-owned fixed
    finalizer records. Missing/contradictory finalization is fail-closed;
  - after a passing diagnostic and source-side validator only, derive once from
    canonical clone root plus this literal logical name:
    docs/9.3.4/evidence/step-4/step4-r7-direct-terminal-diagnostic-20260722-r3-threshold-candidate.json;
  - require safe target-form booleans (absolute, canonical parent, absent),
    record candidate invocation count, call the existing generator once with
    that same target, recompute/verify it, then attest/build/verify a Git-safe
    capsule and obtain two independent reviews;
  - retain success candidate/capsule/reviews uncommitted for a separately
    specified direct-child Cfreeze only.
- affected_modules:
  - existing Step 4 runner/validator/candidate/capsule tooling and run-owned
    output only; provisional docs evidence/quality material only after success.
- external_dependencies: existing governed local Docker/Compose, Maven and
  test services required by the unchanged runner. No publication/deployment.

## Non-Goals

- out_of_scope:
  - retrying/reusing r1 or r2, changing any source/tooling/policy/threshold/
    contract/manifest/runner/workflow/Maven/Docker/package/CI/API/SPI/module
    byte, Cfreeze, formal/release, package proof, Step 5-7, 9.3.5 or 9.4.0;
  - detached/background launch mechanisms, custom launch diagnostics or raw
    output persistence;
  - stopping/cleaning unknown host resources.
- do_not_touch:
  - original user workspace and all historic authority/evidence except these
    two bounded r2 paths:
    docs/9.3.4/workitems/BUG-step4-r7-absolute-target-fresh-diagnostic.md and
    docs/9.3.4/evidence/step-4/step4-r7-absolute-target-diagnostic-20260722-r2-launch-finalization-unverifiable.md;
  - raw logs, host paths, credentials, endpoints and runtime/process/container
    identities.

## Confirmed Decisions

| Decision | Rationale | Compatibility / Constraint |
|---|---|---|
| New Cdiag/r3 only | r2 is permanently excluded | no r2 retry or reuse |
| Foreground direct terminal | r2 lacked all runner finalization | direct runner exit must be observable |
| Existing absolute candidate contract | r1 identified caller target form, not tooling defect | no source change |
| Literal same target | Preflight/publication/verify must not drift | only booleans persisted |
| Direct-child Cfreeze later | Formal topology remains unchanged | no success commit before Cfreeze |

## Acceptance Criteria

- [ ] AC-1: The three allowed Cdiag docs are committed/pushed. A fresh
  non-shallow clone at that exact head passes all static/runtime preflight and
  has no r3 run root.
- [ ] AC-2: Exactly one foreground terminal-managed r3 runner is invoked. Its
  terminal result and runner-owned run-status agree on run ID, diagnostic mode,
  completed state, zero runner exit and diagnostic-observed status; the
  source-side diagnostic validator also passes. The diagnostic root contains no
  formal, candidate or final authority.
- [ ] AC-3: Source-side validation passes. The one candidate target is derived
  from canonical clone root plus the literal logical name, remains beneath that
  root, has safe absolute/canonical-parent/absent booleans and one invocation
  count. The same resolved absolute target is the existing generator output and
  verifier input; durable evidence records only logical name, beneath-root,
  absolute, canonical-parent, absent, invocation-count and candidate-binding
  facts, never the resolved host path.
- [ ] AC-4: Git-safe attestation/capsule build/verify and primary plus
  independent review both pass without a blocker.
- [ ] AC-5: Safe evidence distinguishes diagnostic-observed from formal,
  Step 5, release and version authority; success may propose only a separate
  direct-child Cfreeze.
- [ ] AC-6: Any preflight, terminal, finalizer, validator, candidate, capsule,
  review, cleanup, privacy, scope or topology failure marks this item
  NEEDS_REPLAN with bounded facts and no retry/reuse.

## Contract / Data / Security Constraints

- API or event contract: no public API, SPI, configuration, receipt or package
  contract change.
- data and migration: runner-owned ephemeral data/resources only; no business
  data migration or host-wide mutation.
- compatibility and rollback: unchanged source/policy. Any r3 failure is
  excluded and requires a new governed plan.
- permissions and secrets: durable evidence contains only phase/status/count/
  hash-safe binding/target-form booleans; never raw output, host paths,
  credentials, endpoints or runtime identities.

## Test and Evidence Obligations

| Item | Risk | Required Validation | Required Evidence |
|---|---|---|---|
| AC-1 | blocker | fresh clone full preflight | bounded pass/no-drift facts |
| AC-2 | blocker | one direct terminal runner/finalizer agreement | safe terminal/finalizer facts |
| AC-3 | blocker | validator, target form, one candidate/recompute | booleans/count/binding |
| AC-4 | critical | capsule and independent reviews | safe verdicts |
| AC-5/6 | blocker | authority/scope/privacy/topology audit | no-authority/no-retry result |

## Bug Context

- bug_source: acceptance-found.
- severity: critical.
- environment: governed Step 4 launch environment.
- current_behavior: r2 launch ended without any runner-owned root/finalizer,
  so it cannot prove diagnostic state or a classified failure.
- expected_behavior: r3 uses a direct foreground terminal invocation whose
  status agrees with runner-owned finalization before any post-run work.
- reproduction_steps: do not replay r2. Use r3 once from the new clean Cdiag.
- reproduction_status: confirmed absence of required finalization.
- existing_evidence: r2 finalization-unverifiable record and static runner
  finalizer lifecycle audit.
- existing_tests: canonical runner, diagnostic validator and candidate verifier.
- regression_protection: required through governed r3 evidence.
- waiver_reason_and_risk: N/A.

## Risks and Open Questions

- known_risks: Cdiag identity must be unique because it seals the Git head for
  the diagnostic and any later direct-child Cfreeze. The current three-doc
  approval head and later status-only head were both described as execution
  input; this topology ambiguity is a hard stop. No r3 terminal invocation or
  r3 run root was created.
- open_questions: none.

## Approval Record

- approval_authority: project-owner delegated continuation direction.
- approved_by: foggy-projects-via-user-delegated-continuation.
- approved_at: 2026-07-22.
- approved_scope: superseded before launch by the Cdiag-topology preflight
  finding; a new independent review and approval are required.

## Ultra Execution Contract

- Stopped before terminal launch. Do not use either prior r3 head as Cdiag,
  create a candidate, or start a runner until a reviewed replan fixes the
  unique Cdiag topology.
- The absence of a terminal invocation and r3 run root is bounded context, not
  diagnostic authority or a completed attempt.

## Implementation Result

> Execution stopped fail-closed before terminal launch because the Cdiag head
> was not uniquely defined.

- implementation_summary: no r3 terminal invocation, candidate, capsule or
  authority operation occurred. Fresh-clone topology review found that the
  three-document approval commit and its status-only child were both described
  as Cdiag input.
- changed_paths: this canonical work-item state only; no source, policy,
  runner, CI, package or API path changed.
- tests_and_results: clean/non-shallow clone and absent r3 run-root checks
  passed; execution-head identity failed the preflight topology review.
- manual_or_experience_evidence: the isolated clone remained clean and had no
  r3 runner-owned records.
- deviations: none
- residual_risks: a narrowly reviewed replan must make one future status-only
  execution head the sole Cdiag before any fresh clone or terminal run.
- readiness: NEEDS_REPLAN

## References

- excluded r2 work item:
  docs/9.3.4/workitems/BUG-step4-r7-absolute-target-fresh-diagnostic.md
- r2 failure record:
  docs/9.3.4/evidence/step-4/step4-r7-absolute-target-diagnostic-20260722-r2-launch-finalization-unverifiable.md
- r1 target-form exclusion:
  docs/9.3.4/evidence/step-4/step4-r7-inspect-format-diagnostic-20260722-r1-postrun-candidate-generation-fail-closed.md
