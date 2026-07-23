---
doc_type: delivery-spec
delivery_type: bug
version: 9.3.4
ticket: BUG-STEP4-R7-SINGLE-ACTIVATION-CDIAG-FRESH-DIAGNOSTIC
status: NEEDS_REPLAN
canonical: true
execution_mode: ultra
approved_by: foggy-projects-via-user-delegated-continuation
approved_at: 2026-07-22
controlling_step5_item: FEATURE-V934-RELEASE-AUTHORITY-AND-CI
predecessors:
  - BUG-STEP4-R7-DIRECT-TERMINAL-FRESH-DIAGNOSTIC
open_questions: []
---

# Delivery Spec: R7 single-activation-Cdiag fresh Step 4 diagnostic

## Document Purpose

- intended_for: Ultra execution and independent diagnostic/capsule review.
- purpose: Replace the unexecuted r3 Cdiag-topology ambiguity with one fresh
  r4 diagnostic whose execution head and later Cfreeze parent are uniquely
  fixed before any runner starts.
- canonical_path:
  docs/9.3.4/workitems/BUG-step4-r7-single-activation-cdiag-fresh-diagnostic.md

## Goal

- version_goal: Re-establish fresh Step 4 evidence for unchanged R7 executable
  tooling and coverage policy without reusing r1, r2 or unexecuted r3
  authority.
- target_outcome: A clean Cplan is followed by exactly one clean, pushed,
  single-parent activation child Cdiag. A fresh clone at that exact Cdiag runs
  once and, only if strictly finalized, yields review-only candidate/capsule
  material for a separately governed direct-child Cfreeze.

## Scope

- in_scope:
  - create and push this approved documentation-only r4 Cplan. Cplan must be
    a clean, upstream-equal, single-parent `APPROVED` commit; it is not Cdiag
    and never authorizes a runner, candidate, capsule or Cfreeze;
  - create exactly one clean, pushed, upstream-equal, single-parent activation
    commit Cdiag for which `parent(Cdiag) = Cplan`. Its diff must contain only
    this canonical r4 path and only the two replacements `status:
    APPROVED -> ULTRA_EXECUTING` and `readiness: APPROVED ->
    ULTRA_EXECUTING`; every other byte of the work item is unchanged. That
    one-path/two-field commit is the sole Cdiag and the exact fresh-clone HEAD;
    any topology or diff deviation is launch-preflight fail-closed;
  - use a fresh non-shallow disposable clone and exactly one r4 run ID:
    step4-r7-single-activation-cdiag-diagnostic-20260722-r4;
  - repeat existing Step 4/5/6 integrity, diagnostic-state, overlay, correct
    Step 4 source-seal, CI, clean-Git, absent-run-root, sanitized environment,
    governed-port and Docker/Compose preflight at that exact Cdiag;
  - invoke exactly one direct foreground terminal command that replaces its
    shell with the existing canonical r4 runner and remains attached until it
    exits. No launcher/agent proxy may return early; no outer terminal
    transport `nohup`, background, detached process, `tee`, `timeout`,
    intermediary `bash -c` wrapper, custom exit marker or external completion
    file is allowed. The canonical runner's own existing logging lifecycle is
    unchanged and is not an outer-terminal transport;
  - an owned temporary raw-output redirection is allowed only with that direct
    shell replacement. Before launch, create it exclusively/no-clobber below a
    canonical non-symlink temporary parent, with mode `0600`, current-user
    ownership, a regular non-symlink leaf and one link; compare device/inode
    only in memory after return and retain only safe identity booleans. It is
    never read, persisted or classified by content; only terminal-result
    metadata and runner-owned records may be used. After every terminal
    outcome, verify the same path is the same owned regular non-symlink
    single-link object, then delete it. Any identity or deletion failure is
    fail-closed; do not delete a nonmatching object or proceed to candidate
    work;
  - accept completion only if terminal result and runner-owned finalizer agree
    on r4 ID, diagnostic mode, completed state, zero runner exit and
    diagnostic-observed status; require source-side diagnostic validation,
    clean source seal/worktree, diagnostic-only summary and explicit absence
    of `coverage-gate.json`, `candidate-manifest.json`, `final-manifest.json`
    and `formalization-delta.json` authority files;
  - only then derive once from canonical clone root plus this literal logical
    candidate name:
    docs/9.3.4/evidence/step-4/step4-r7-single-activation-cdiag-diagnostic-20260722-r4-threshold-candidate.json;
  - require the target is absolute, beneath the clone root, has a canonical
    non-symlink parent and is absent; call the existing candidate generator
    once and, before verifier input, revalidate that the generated same leaf is
    an owned regular non-symlink beneath the unchanged canonical parent/root.
    Pass the same resolved absolute target once to the verifier and bind its
    post-generation identity/hash-safe result; durable evidence may retain only
    logical name, form booleans, invocation count and hash-safe binding, never
    a resolved host path or raw output;
  - attest, build and verify the Git-safe two-member capsule, then obtain
    primary and independent review. Leave all success material uncommitted;
  - permit a later Cfreeze only if it is the sole direct, single-parent child
    of the runner-observed Cdiag. No status, documentation, evidence or source
    commit may intervene between Cdiag and Cfreeze.
- affected_modules:
  - existing Step 4 runner/validator/candidate/capsule tooling and run-owned
    output only; provisional evidence/quality material only after success.
- external_dependencies: existing governed local Docker/Compose, Maven and
  test services required by the unchanged runner. No publication/deployment.

## Non-Goals

- out_of_scope:
  - reusing/retrying r1, r2 or r3, changing source/tooling/policy/threshold/
    contract/manifest/runner/workflow/Maven/Docker/package/CI/API/SPI/module
    bytes, Cfreeze, formal/release, package proof, Step 5-7, 9.3.5 or 9.4.0;
  - any detached launch, raw-output persistence/content inspection, custom
    launch telemetry, or stopping/cleaning unknown host resources.
- do_not_touch:
  - original user workspace and historic authority/evidence;
  - raw logs, host paths, credentials, endpoints and runtime/process/container
    identities.

## Confirmed Decisions

| Decision | Rationale | Compatibility / Constraint |
|---|---|---|
| Cplan is not executable | r3 conflated preparation and execution head | no runner before activation child |
| One-path activation Cdiag | sealed Git head and Cfreeze parent must agree | clone exact activation head only |
| r4 is fresh | r3 topology is excluded before launch | no r3 evidence or ID reuse |
| Direct terminal only | r2 finalization was unverifiable | tool terminal exit must be runner exit |
| Metadata-only raw cleanup | raw output is not durable authority | never read content; deletion required |
| Direct-child Cfreeze later | prevents evidence/topology drift | no intervening commit |

## Acceptance Criteria

- [ ] AC-1: A clean, pushed, upstream-equal, single-parent `APPROVED` Cplan
  and one clean, pushed, upstream-equal, single-parent Cdiag exist with
  `parent(Cdiag) = Cplan`. Cdiag is the sole execution head and exact
  fresh-clone HEAD; its diff has only the r4 path and exactly the two specified
  status/readiness replacements, with every other work-item byte unchanged.
- [ ] AC-2: The fresh Cdiag clone is clean/non-shallow/upstream-equal; r4 root
  is absent; all existing static/runtime preflight, including correct
  `coverage_tool.py source-hash`, passes.
- [ ] AC-3: Exactly one attached direct terminal r4 runner is invoked. Its
  terminal result and finalizer records agree; validator/source seal/worktree
  checks pass; raw output is never read and is identity-verified/deleted on
  every terminal outcome; the diagnostic root lacks `coverage-gate.json`,
  `candidate-manifest.json`, `final-manifest.json` and
  `formalization-delta.json`.
- [ ] AC-4: The literal candidate target is derived only after AC-3, passes all
  target-form checks, is used once identically for generator/verifier, and is
  revalidated as the owned regular non-symlink same leaf below unchanged
  canonical parent/root before verification. Durable evidence excludes host
  paths/raw output and binds only safe post-generation identity/hash facts.
- [ ] AC-5: Git-safe attestation/capsule build/verify and primary plus
  independent reviews pass without blocker; success remains uncommitted.
- [ ] AC-6: Any preflight, topology, terminal, finalizer, validator, cleanup,
  candidate, capsule, review, privacy, scope or topology failure marks r4
  NEEDS_REPLAN and stops without retry/reuse or authority creation.

## Contract / Data / Security Constraints

- API or event contract: no public API, SPI, configuration, receipt or package
  contract change.
- data and migration: runner-owned ephemeral data/resources only; no business
  data migration or host-wide mutation.
- compatibility and rollback: unchanged source/policy. Any r4 failure is
  excluded and requires a separately governed successor.
- permissions and secrets: durable records contain only phase/status/count/
  hash-safe binding/target-form facts; never raw output, host paths,
  credentials, endpoints or runtime identities.

## Test and Evidence Obligations

| Item | Risk | Required Validation | Required Evidence |
|---|---|---|---|
| AC-1 | blocker | parent/topology/path-only Cdiag audit | safe topology facts |
| AC-2 | blocker | fresh-clone full preflight | bounded pass/no-drift facts |
| AC-3 | blocker | one terminal/finalizer/absence audit | safe terminal/finalizer facts |
| AC-4 | blocker | target form + one candidate/recompute | booleans/count/binding |
| AC-5 | critical | capsule and independent reviews | safe verdicts |
| AC-6 | blocker | authority/scope/privacy/topology audit | no-authority/no-retry result |

## Bug Context

- bug_source: acceptance-found.
- severity: critical.
- environment: governed Step 4 launch environment.
- current_behavior: r3 was stopped before launch because its approval head and
  activation child were both described as Cdiag, leaving no unique sealed head.
- expected_behavior: r4 has one explicit activation Cdiag before any runner
  starts, and its observed head is the only allowed Cfreeze parent.
- reproduction_steps: do not replay r3. Create r4 Cplan and its activation
  child, then use r4 once from a fresh clone.
- reproduction_status: confirmed pre-execution topology ambiguity.
- existing_evidence: r3 canonical work item records no runner/root/authority.
- existing_tests: canonical runner, diagnostic validator and candidate verifier.
- regression_protection: required through governed r4 evidence.
- waiver_reason_and_risk: N/A.

## Risks and Open Questions

- known_risks: direct terminal transport failure and any preflight drift remain
  hard stops; correct topology does not relax Docker/Maven, candidate or
  cleanup gates.
- open_questions: none.

## Approval Record

- approval_authority: project-owner delegated continuation direction.
- approved_by: foggy-projects-via-user-delegated-continuation.
- approved_at: 2026-07-22.
- approved_scope: bounded r4 single-activation-Cdiag diagnostic/candidate
  sequence; two independent reviews passed.

## Ultra Execution Contract

- Start only after Cplan, one exact activation Cdiag and fresh-clone preflight.
- Use exactly r4 and one direct foreground terminal invocation; do not
  background/detach/wrap it, read raw output or create external exit records.
- On any anomaly set NEEDS_REPLAN and stop; do not self-accept.

## Implementation Result

> Approved Cplan; it is not executable until its separately verified activation
> child is created.

- implementation_summary: pending
- changed_paths: pending
- tests_and_results: pending
- manual_or_experience_evidence: pending
- deviations: none
- residual_risks: pending
- readiness: NEEDS_REPLAN

## References

- excluded r3 work item:
  docs/9.3.4/workitems/BUG-step4-r7-direct-terminal-fresh-diagnostic.md
- excluded r2 work item:
  docs/9.3.4/workitems/BUG-step4-r7-absolute-target-fresh-diagnostic.md
- r2 finalization record:
  docs/9.3.4/evidence/step-4/step4-r7-absolute-target-diagnostic-20260722-r2-launch-finalization-unverifiable.md
