---
doc_type: delivery-spec
delivery_type: bug
version: 9.3.4
ticket: BUG-STEP5-PACKAGE-SUBPHASE-RECEIPT
status: ACCEPTED
canonical: true
execution_mode: ultra
approved_by: foggy-projects-via-user-direction
approved_at: 2026-07-20
predecessors:
  - STEP5-R41-NONAUTHORITATIVE-PACKAGE-CLASSIFICATION
  - STEP5-R41-RECEIPTED-NONAUTHORITATIVE-PACKAGE-CLASSIFICATION
cdiag_branch: codex/v934-step5-package-receipt-cdiag
cdiag_base: b05dd0ec659c283b1a59a82c1c67710f4c10368e
revalidation_status: r45-failed-package-image-e-image-after-authorized-cache-preparation-no-retry-authorized
acceptance_record: docs/9.3.4/acceptance/step4-r43-cfreeze-formal-scoped-reacceptance-20260721.md
r45_authorization_record: docs/9.3.4/acceptance/step5-r45-pinned-base-image-remediation-and-rehearsal-with-nonfinal-candidate-pointer-20260721.md
open_questions: []
---

# Delivery Spec: Step 5 Package Subphase Failure Receipt

## Document Purpose

- intended_for: release-authority implementation / independent-signoff
- purpose: 在 release package tool 内建立 durable、fail-closed 且无原始输出泄漏的失败子阶段回执，
  取代已耗尽的外置分类尝试。
- canonical_path: `docs/9.3.4/workitems/BUG-step5-package-subphase-receipt.md`

## Goal

- version_goal: 让 9.3.4 Step 5 的 `package-tested-tree` 在失败时提供安全、受限且可验证的
  package/verify 子阶段分类，同时保持既有 release authority 的 fail-closed 边界。
- target_outcome: outer gate 仍只以既有五字段 `failure.env` 表示失败；仅在受控 package/verify
  失败时，于 run root 额外发布固定九字段 sidecar，供诊断和后续受控重验使用。

## Scope

- in_scope:
  - 在 `release_package_tool.py` 增加仅供受控 gate 使用的 failure-receipt mode；receipt 必须在
    `$RUN_ROOT/package-tested-tree-failure.env`，绝不位于 `$PACKAGE_ROOT` 或其子目录。
  - 固定 ASCII/UTF-8/newline、固定顺序的九字段 schema：`schema_version`、`kind`、`run_id`、
    `gate_phase`、`operation`、`subphase`、`error_code`、`tool_exit_code`、`status`。不含
    message、stdout/stderr、argv、path、time、hash、日志、JAR/image/container/OCI identity。
  - operation 只允许 `package|verify`；subphase 只允许
    `package-preflight|package-maven-reactor|package-maven-launcher-jar|package-image|`
    `package-postconditions|package-manifest|package-internal-verify|verify-package`；错误码必须是
    bounded `E_*`，未知异常仅 `E_INTERNAL`，中断仅 `E_SIGNAL`。
  - package/verify receipt mode 以原子 no-replace、regular/non-symlink、durable parent sync 发布
    sidecar；目标预置、symlink、缺失、重复、畸形或不匹配一律 fail closed。
  - gate 在 package-tested-tree 失败时严格验证 sidecar 的 path/九字段/顺序/enum/run-id/phase/
    exit code，再保留既有 outer failure 语义；package 与 verify 成功后必须断言 sidecar 不存在。
  - 受控 receipt mode 不得把 `PackageError.message` 或其他 raw output 写到 stderr、stdout、run
    evidence 或 CI log；默认无 receipt mode 的既有 CLI 合约保持兼容。
  - 为 verify receipt mode 增加来自 gate 的 run-id binding，并与 package manifest run-id 精确一致。
  - 增加纯 synthetic negative/self-test，覆盖 writer/reader/runner contract；不以 Maven/Docker 或
    历史 r40/r41 runtime artifact 作为回归证明。
  - 更新 Step 5/Step 6 hash 与 CI closure，并按新的 Cdiag → diagnostic → review → direct-child
    Cfreeze → formal → quality/audit → owner reacceptance → fresh Step 5 rehearsal 链完成重验。
- affected_modules:
  - `scripts/verify-v934-release-gate.sh`
  - `scripts/v934/step5/release_package_tool.py`
  - `scripts/v934/step5/SHA256SUMS`
  - `scripts/v934/step6/ci-contract.json`
  - `scripts/v934/step6/ci_contract_tool.py`
  - `scripts/v934/step6/SHA256SUMS`
  - Cdiag 所必需的 Step 4 machine-state/hash closure 与 `docs/9.3.4` governed records。
- external_dependencies: Maven, Docker Engine and existing Step 4 release inputs remain external execution
  dependencies only; no release/publish action is authorized.

## Non-Goals

- out_of_scope:
  - 不改变 production API/SPI/module boundary、POM、Dockerfile、runtime image、DB matrix、coverage
    threshold/critical set/exclusion、workflow job graph、candidate/pointer/archive/replay contract。
  - 不改变或回填 r40/r41 的 failed record，不从 raw message/log/XML/manifest 推断历史根因。
  - 不把 sidecar 放入 six-file package output，亦不将它当作 candidate/final-authority receipt。
  - 不在 Cfreeze 中修改 runner/package tool；这些字节只能属于新的 Cdiag。
- do_not_touch:
  - 既有 `E_CROSS_RUN_SPLICE`、outer five-field `failure.env` schema、success pointer/final pointer
    publication rules、Step 6 required-check naming、public API/SPI and 9.3.5/9.4.0 scope.

## Confirmed Decisions

| Decision | Rationale | Compatibility / Constraint |
|---|---|---|
| In-process sidecar, not external CLI parsing | only package tool knows the active internal boundary without raw-message inference | requires frozen-tooling revalidation |
| Sidecar outside package root | package verifier requires exactly six package files | no package output contract expansion |
| Fixed nine-field failure-only schema | diagnostic value must not expose runtime payload | no free-form message or paths |
| Gate validates sidecar but retains outer failure.env | preserves established consumers and fail-closed outer semantics | old five-field schema remains byte-compatible |
| Receipt mode suppresses raw terminal detail | PackageError messages may contain downstream output | default CLI compatibility remains outside receipt mode |
| Fresh Cdiag chain is mandatory | runner/package bytes and hash closure change | r40/r41 are historical/non-reusable only |

## Acceptance Criteria

- [x] AC-1: package and verify receipt modes publish only a valid atomic nine-field sidecar on controlled
  failure, with correct operation/subphase/error/exit binding; success leaves no sidecar.
- [x] AC-2: the gate accepts only a canonical safe sidecar under its exact run root and otherwise fails closed,
  while preserving the unchanged outer five-field failure.env schema.
- [x] AC-3: synthetic negative matrix proves missing, malformed, duplicate-key, symlink, preexisting,
  mismatched run/phase/enum/exit and raw-message-leak cases reject; failed paths publish no success/candidate
  state.
- [x] AC-4: package output remains exactly six files; runner/package default CLI behavior and existing negative
  matrix do not regress.
- [x] AC-5: Step 5/Step 6 hashes, CI contract/tool and static closure validate with no workflow/API/SPI/POM or
  coverage-policy drift.
- [x] AC-6: a clean/pushed Cdiag resets Step 4 to diagnostic-ready/pending, a fresh full diagnostic is
  independently reviewed, and Cfreeze is its sole direct child.
- [x] AC-7: fresh formal, final quality, replacement coverage audit and `foggy-projects` scoped reacceptance
  complete before a new Step 5 rehearsal; r40/r41 remain non-candidate throughout.

## Contract / Data / Security Constraints

- API or event contract: only a new internal failure sidecar is introduced; public APIs/SPI, package artifact
  layout, pointers and workflows retain their current contracts.
- data and migration: no migration or fixture policy change. The sidecar has no credential, endpoint, path,
  digest, image or report payload.
- compatibility and rollback: absence or invalidity of sidecar preserves the old generic outer failure, never
  success. Revert uses a new governed Cdiag/revalidation path; do not restore historical authority.
- permissions and secrets: regular non-link path checks, no-replace atomic publication, durable directory
  sync, strict field/enum validation and receipt-mode suppression of raw exception detail are mandatory.

## Test and Evidence Obligations

| Item | Risk | Required Validation | Required Evidence |
|---|---|---|---|
| AC-1/AC-3 | critical | synthetic receipt writer/reader and negative matrix | safe case/count/status summary only |
| AC-2 | critical | runner static/negative sidecar parser tests for package and verify failure | safe schema/absence/preservation result |
| AC-4 | critical | package output contract and existing negative command | exact six-file invariant and no-regression summary |
| AC-5 | critical | Step 5/6 hash and CI-contract static validation | governed digest/closure result |
| AC-6/AC-7 | blocker | fresh Cdiag→diagnostic→review→Cfreeze→formal chain | new run-owned safe evidence and independent reviews |

## Bug Context

- bug_source: acceptance-found
- severity: critical
- environment: WSL Step 5 r40/r41 b05 rehearsal boundary plus two exhausted non-authoritative classifiers.
- current_behavior: outer failure is correctly fail-closed but reports only composite `package-tested-tree`; raw
  package errors are not safe evidence and external receipt transport could not establish a valid outcome.
- expected_behavior: controlled failures expose only the bounded internal receipt; all unknown/malformed paths
  remain generic outer failures.
- reproduction_steps: run synthetic receipt negatives and a new Cdiag validation chain; do not retry r40/r41.
- reproduction_status: durable in-tool classification is implemented and fully revalidated through fresh
  Cdiag, r42 diagnostic, direct-child Cfreeze, r43 formal, independent reviews, and owner scoped acceptance.
- existing_evidence: r40/r41 failure records and two `unverifiable` classifier records; none is authority.
- existing_tests: existing package negative/self-test, runner gate contracts and Step 6 CI closure.
- regression_protection: required
- waiver_reason_and_risk: N/A.

## Risks and Open Questions

- known_risks: receipt code may accidentally leak raw detail, expand the six-file package contract, drift hash
  closure, or mislabel cleanup/unknown exceptions. Each is a fail-closed blocker.
- open_questions: none

## Approval Record

- approval_authority: direct project-owner continuation direction
- approved_by: `foggy-projects-via-user-direction`
- approved_at: 2026-07-20
- signoff_owner: `foggy-projects`
- approved_scope: durable internal package-subphase failure receipt and its full required revalidation chain;
  no later roadmap/API/SPI scope is opened.

## Ultra Execution Contract

- Start from a new clean Cdiag worktree; do not alter b05 authority or the evidence branch's authority state.
- Keep the implementation inside the declared tooling/hash closure. If a required path lies outside it, set
  `NEEDS_REPLAN` before changing it.
- Prove all synthetic/static contracts before launching a fresh Step 4 diagnostic. Do not claim runtime or
  formal success until it has actually completed.
- Record changed paths, exact safe test results, Cdiag topology, review results and residual risk here. Set
  `READY_FOR_SIGNOFF` only after AC-1 through AC-7 are actually complete; do not self-accept.

## Implementation Result

> The new clean Cdiag remains based directly on b05. Durable receipt implementation, r42 diagnostic,
> direct-child Cfreeze, r43 formal, independent reviews, and owner scoped reacceptance are complete.
> This does not claim Step 5 rehearsal, release, pointer, or version success.

- implementation_summary: package/verify receipt mode now emits only the bounded sidecar on failure, keeps
  default CLI behavior outside receipt mode, uses sibling staging so a failed controlled package does not retain
  partial package evidence under its run root, and binds external verify to the exact gate run id.
- changed_paths: declared Step 4/5/6 tooling/hash closure, release gate, governed Cdiag workitem, and one
  safe static checkpoint only.
- tests_and_results: Python/Bash syntax, whitespace, full package synthetic negative matrix, package/verify
  receipt CLI silence/binding probes, Step 4/5/6 manifests, Step 4 contract/overlay validation, Step 6
  workflow-contract validation, fresh r42 diagnostic, r43 formal artifact/frozen replay, independent quality,
  and replacement coverage audit all passed.
- manual_or_experience_evidence:
  `docs/9.3.4/evidence/step-5/step5-package-subphase-receipt-cdiag-static-20260720.md`
- deviations: none.
- residual_risks: P2 filesystem-fault cleanup boundary recorded in the static checkpoint; all such observed
  paths remain fail-closed and cannot become a success/candidate state.
- acceptance_record:
  `docs/9.3.4/acceptance/step4-r43-cfreeze-formal-scoped-reacceptance-20260721.md`
- r44_rehearsal_result:
  `docs/9.3.4/evidence/step-5/step5-r44-package-image-fail-closed-20260721.md`
- r45_authorization_and_result:
  `docs/9.3.4/acceptance/step5-r45-pinned-base-image-remediation-and-rehearsal-with-nonfinal-candidate-pointer-20260721.md`
  and `docs/9.3.4/evidence/step-5/step5-r45-fail-closed-20260721.md`.
- r45_rehearsal_result: a fresh canonical Cfreeze rehearsal, after the limited
  authorized frozen-base-image cache preparation, remained fail-closed at the
  same bounded `package-image / E_IMAGE` classification. This does not assign
  an `E_IMAGE` root cause.
- downstream_authority: none. r45 consumed its independent single-attempt
  authorization without a candidate or final authority pointer. Any retry,
  remediation, diagnostic redesign, runner/Cfreeze change, release, or later
  roadmap work requires a new explicit owner direction.
- readiness: ACCEPTED

## References

- predecessor evidence:
  `docs/9.3.4/evidence/step-5/step5-r41-receipted-nonauthoritative-package-classification-20260720.md`
- Step 5 authority scope:
  `docs/9.3.4/workitems/FEATURE-v934-release-authority-and-ci.md`
- Step 4 replacement signoff:
  `docs/9.3.4/workitems/FEATURE-step4-replacement-coverage-gate-signoff.md`
