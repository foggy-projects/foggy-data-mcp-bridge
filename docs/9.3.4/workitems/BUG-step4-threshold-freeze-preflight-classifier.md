---
doc_type: delivery-spec
delivery_type: bug
version: 9.3.4
ticket: BUG-STEP4-THRESHOLD-FREEZE-PREFLIGHT-CLASSIFIER
status: NEEDS_REPLAN
canonical: true
execution_mode: ultra
approved_by: foggy-projects-via-user-delegated-continuation
approved_at: 2026-07-22
controlling_step5_item: FEATURE-V934-RELEASE-AUTHORITY-AND-CI
predecessors:
  - BUG-STEP4-R7-INSPECT-FORMAT-FRESH-DIAGNOSTIC
open_questions: []
---

# Delivery Spec: Step 4 threshold-freeze preflight classifier

## Document Purpose

- intended_for: Ultra implementation and independent signoff.
- purpose: Add one governed, diagnostic-only preflight that classifies
  threshold-freeze readiness using bounded safe facts before a future fresh
  candidate publication is attempted.
- canonical_path:
  docs/9.3.4/workitems/BUG-step4-threshold-freeze-preflight-classifier.md

## Goal

- version_goal: Preserve fail-closed Step 4 authority while making a future
  threshold-candidate failure distinguishable without retaining raw tool output.
- target_outcome: A future fresh diagnostic can be checked by a separate
  no-publication classifier before threshold-candidate generation. The
  classifier reports only a finite, documented safe category and never grants
  Cfreeze or later authority.

## Scope

- in_scope:
  - add one diagnostic-only Step 4 CLI operation that evaluates the sealed
    diagnostic input, candidate projection and designated candidate-output
    preconditions without invoking threshold-candidate publication;
  - define the exact bounded result schema, five-member classification closure,
    classification priority, stdout/stderr behavior and exit semantics in this
    contract;
  - make every non-ready class fail closed and ensure unknown exceptions,
    result-emission failure and malformed governed input cannot expose
    exception text, paths, command output, host data or identities;
  - add focused positive, negative and privacy regressions for the classifier,
    including an injected raw sentinel that must not escape the bounded result;
  - prove existing threshold-candidate generation retains its current behavior,
    output schema and fail-closed semantics;
  - update only the existing governed Step 4 and Step 6 integrity closure
    required by the changed Step 4 tooling bytes, plus this work item and
    minimal bounded evidence.
- affected_modules:
  - scripts/v934/step4 coverage XML tooling and its focused test/negative
    fixtures;
  - the existing Step 4 and Step 6 integrity-manifest/CI binding only.
- external_dependencies: none. This scope must not start Docker, Maven, an
  outer Step 4 runner, a package proof or CI/release workflow.

## Non-Goals

- out_of_scope:
  - retrying or reusing the excluded R7 diagnostic, its run ID, observation or
    any historical candidate/capsule authority;
  - changing coverage thresholds, critical set, exclusion policy, diagnostic
    state, runner lanes, Docker behavior, Maven/POM, package tooling, CI
    semantics, public API/SPI/module layout or Step 5-7 authority;
  - publishing a threshold candidate, Git-safe capsule, Cfreeze, formal result,
    package proof, release artifact or candidate pointer;
  - treating any classifier result as proof that an underlying product or
    environment defect has been identified.
- do_not_touch:
  - original user workspace and historic authority/evidence;
  - raw runner/tool output, credentials, endpoints, runtime/container/process
    identities and unstructured exception text;
  - the existing threshold-candidate command contract except for regression
    proof that it remains unchanged.

## Confirmed Decisions

| Decision | Rationale | Compatibility / Constraint |
|---|---|---|
| Separate diagnostic-only CLI | The failed candidate must not be retried merely to learn its cause | no candidate publication or authority |
| Finite safe classifications only | Raw error text is unsafe and non-durable | unknowns map to unclassified-safe-failure |
| Check before future publication | A fresh run is required after tooling bytes change | classifier cannot authorize reuse of R7 |
| Preserve existing freeze command | The repair must not silently alter candidate semantics | regression proof is mandatory |
| No runtime services | Failure classification is tooling-local | no Docker, Maven or outer runner |
| Stdout is the safe result channel | A candidate output file would add another publication failure mode | one canonical safe JSON document; stderr empty |
| Read-only target preflight | The classifier must not itself create evidence or a candidate | no temp, lock, write, rename, chmod, unlink or fsync |

## Safe Result Contract

For every parsed governed classifier invocation whose safe result can be
emitted, stdout must contain exactly one canonical JSON document followed by one
newline; stderr must be empty. Canonical JSON means UTF-8 without a BOM,
ASCII-only escaped strings, lexicographically sorted keys, compact comma/colon
separators and exactly one final LF. The document has exactly these fields:

| Field | Type / allowed value |
|---|---|
| schema_version | integer 1 |
| kind | v934-step4-threshold-freeze-preflight |
| status | ready or blocked |
| classification | one member of the five-value closure below |
| diagnostic | null, or an object containing only validated run_id, git_head and source_sha256 |
| checks | object with exactly diagnostic_input, candidate_projection and candidate_target, each passed, failed or not-run |
| publication | object with exactly candidate_write_attempted=false and publication_guaranteed=false |

The classification closure, field values and evaluation priority are fixed:

| Classification | status | diagnostic | checks: input / projection / target | exit |
|---|---|---|---|---|
| diagnostic-input-invalid | blocked | null | failed / not-run / not-run | 1 |
| candidate-projection-invalid | blocked | validated object | passed / failed / not-run | 1 |
| candidate-target-invalid | blocked | validated object | passed / passed / failed | 1 |
| ready-to-freeze | ready | validated object | passed / passed / passed | 0 |
| unclassified-safe-failure | blocked | null | not-run / not-run / not-run | 2 |

The classifier evaluates the checks in exactly that order. It exits 0 only for
ready-to-freeze, 1 for the three known blocked classifications, and 2 for
unclassified-safe-failure. If safe-result emission itself fails, it exits 2
with both stdout and stderr empty; no partial or alternate result is allowed.
Any classifier argument-parsing failure other than an explicit help request
also exits 2 with both streams empty. A valid non-ready result is still a
failed precondition, never a successful candidate operation. Implementation
must intercept the classifier path before any generic exception printer so no
free-form stdout or stderr can escape.

Candidate projection must be the exact in-memory projection of the current
freeze_thresholds_data behavior (or a refactor mechanically proven equivalent)
and must be discarded. Candidate-target preflight may use only read-only
metadata/identity checks equivalent to the existing atomic publisher's
preconditions: target absent, canonical existing parent, no target/parent
symlink and required ownership/mode identity predicates. It must not create,
open for writing, rename, chmod, delete, lock or fsync anything. A
ready-to-freeze result is only an instantaneous precondition observation; it
does not guarantee later atomic publication or remove TOCTOU risk.

## Acceptance Criteria

- [ ] AC-1: A diagnostic-only classifier implements the exact Safe Result
  Contract, closed five-value classification, evaluation priority, stdout/
  stderr discipline and exit semantics above, including silent exit 2 if safe
  result emission fails. Unvalidated run IDs and targets never appear in its
  result.
- [ ] AC-2: The classifier performs no candidate publication and its target
  preflight is demonstrably read-only: no target, parent or temporary artifact
  is created or mutated. A ready result explicitly retains TOCTOU non-guarantee.
- [ ] AC-3: Focused positive and negative tests cover every classification,
  each exact matrix row and exit status, malformed input/target and CLI parsing
  handling, safe-result emission failure, raw-sentinel non-disclosure and the
  absence of free-form stderr.
- [ ] AC-4: Candidate projection is mechanically identical to the current
  freeze_thresholds_data behavior and discarded; existing threshold-candidate
  generation remains behaviorally and schema compatible under its current
  success and fail-closed negative tests.
- [ ] AC-5: Required Step 4/Step 6 manifest closure, contract/overlay and CI
  static validation pass; the staged diff contains only the classifier,
  focused tests/fixtures, governed integrity files and docs under this scope.
- [ ] AC-6: Result evidence states explicitly that no R7 candidate, Cfreeze,
  formal, Step 5, release, 9.3.5 or 9.4.0 authority is created. A passing
  classifier may authorize only proposal of a separately specified
  candidate-generation sequence after a later fresh diagnostic.

## Contract / Data / Security Constraints

- API or event contract: no public API, SPI, configuration, receipt or package
  contract changes. The new CLI is internal governed tooling and must use a
  documented, versioned safe-result schema exactly as frozen above.
- data and migration: no business data, migration or persistent runtime state.
  Test fixtures and ignored local output only; classifier target inspection is
  read-only and does not publish a report or candidate.
- compatibility and rollback: retain the current threshold-candidate command
  behavior. Removing the new diagnostic-only command restores prior behavior
  without data migration.
- permissions and secrets: stdout uses only the Safe Result Contract and
  stderr is empty for every parsed invocation; safe-result emission failure is
  silent. Results may contain only the bounded classification, validated
  hash-safe identity and check values; they must never contain raw output, host
  or repository filesystem locations, endpoints, credentials or runtime
  identities.

## Test and Evidence Obligations

| Item | Risk | Required Validation | Required Evidence |
|---|---|---|---|
| AC-1/2 | critical | exact schema, exit and read-only target fixtures | safe class matrix and no-side-effect proof |
| AC-3 | critical | malformed/unknown/emission/sentinel privacy negatives | bounded no-leak stdout and empty stderr |
| AC-4 | blocker | existing freeze command success/negative regression | unchanged behavior evidence |
| AC-5 | blocker | Step 4/6 manifests, contract, overlay and CI static checks | pass summaries and exact scope |
| AC-6 | blocker | independent authority/scope review | no-authority conclusion |

## Bug Context

- bug_source: acceptance-found.
- severity: critical.
- environment: governed Step 4 diagnostic tooling.
- current_behavior: the one authorized R7 diagnostic was structurally valid,
  but its first external threshold-freeze candidate publication did not
  complete. The safe evidence intentionally cannot distinguish diagnostic
  input, candidate projection or atomic target-publication causes.
- expected_behavior: a future fresh diagnostic can be classified before
  candidate publication through a bounded no-publication operation.
- reproduction_steps: do not replay the excluded R7 candidate operation. Use
  focused fixtures and, only under a later scope, a new fresh diagnostic.
- reproduction_status: partial; safe classification absent by design.
- existing_evidence: the R7 post-run candidate-generation fail-closed record.
- existing_tests: Step 4 contract, XML and candidate tooling negatives.
- regression_protection: required.
- waiver_reason_and_risk: N/A.

## Risks and Open Questions

- known_risks: a ready-to-freeze result is a read-only instantaneous
  precondition observation, not a candidate publication guarantee; TOCTOU
  remains. Any subsequent candidate failure remains fail-closed and requires
  its own bounded evidence.
- open_questions: none.

## Approval Record

- approval_authority: project-owner delegated continuation direction.
- approved_by: foggy-projects-via-user-delegated-continuation.
- approved_at: 2026-07-22.
- approved_scope: superseded before integration when static audit identified
  the existing absolute-target invocation contract as the deterministic cause
  of the excluded R7 candidate failure.

## Ultra Execution Contract

- Read this work item, project rules and existing Step 4 tooling before work.
- Keep the classifier separate from threshold-candidate publication and preserve
  all existing freeze behavior unless this work item is explicitly replanned.
- Implement the exact Safe Result Contract; do not add an unbounded message,
  path, exception or alternate result field.
- Use the existing freeze projection equivalently in memory, discard it, and
  keep every candidate-target operation read-only.
- Do not run an outer diagnostic, Docker, Maven, package, formal or release
  action. Do not consume the excluded R7 run in any authority decision.
- Use only focused deterministic tests and existing static governed validation.
- If a safe result schema, required regression, manifest closure or scope
  boundary cannot be met, set this item to NEEDS_REPLAN and stop.
- Complete the implementation result and set READY_FOR_SIGNOFF only after every
  acceptance criterion is evidenced; do not self-accept.

## Implementation Result

> Execution stopped before integration because its premise was superseded.

- implementation_summary: no classifier delivery is required. Static audit
  proved the excluded candidate invocation used a repository-relative target
  against an existing absolute-target publisher contract.
- changed_paths: none; no source, test, manifest or CI byte from this scope is
  part of a delivery.
- tests_and_results: no delivery validation is claimed. A transient
  uncommitted prototype was not integrated, committed or used as authority.
- manual_or_experience_evidence: the documented static publisher contract and
  the bounded invocation fact identify the cause without reading raw output.
- deviations: none
- residual_risks: a future sequence must still use a new Cdiag and fresh run;
  this item must not be revived merely to add preventive tooling.
- readiness: NEEDS_REPLAN

## References

- excluded R7 work item:
  docs/9.3.4/workitems/BUG-step4-r7-inspect-format-fresh-diagnostic.md
- bounded failure record:
  docs/9.3.4/evidence/step-4/step4-r7-inspect-format-diagnostic-20260722-r1-postrun-candidate-generation-fail-closed.md
- controlling Step 5 scope:
  docs/9.3.4/workitems/FEATURE-v934-release-authority-and-ci.md
