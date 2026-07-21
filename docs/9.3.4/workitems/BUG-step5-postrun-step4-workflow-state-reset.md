---
doc_type: delivery-spec
delivery_type: bug
version: 9.3.4
ticket: BUG-STEP5-POSTRUN-STEP4-WORKFLOW-STATE-RESET
status: ULTRA_EXECUTING
canonical: true
execution_mode: ultra
approved_by: foggy-projects
approved_at: 2026-07-22
predecessors:
  - BUG-STEP5-POSTRUN-STEP4-ARTIFACT-VERIFIER-INTEGRITY-REPLAN
open_questions: []
---

# Delivery Spec: Step 5 post-run integrity successor workflow-state reset

## Document Purpose

- intended_for: ultra-implementation / independent-signoff
- purpose: 为已完成的 package terminal-integrity Cdiag 补齐唯一缺失的 Step 4 workflow-state
  recovery，使新 diagnostic 能在真实、完整的 `diagnostic-ready / diagnostic-pending` 前置状态运行。
- canonical_path:
  docs/9.3.4/workitems/BUG-step5-postrun-step4-workflow-state-reset.md

## Goal

- version_goal: 保持 9.3.4 Step 4/5 authority 的 fail-closed 顺序；不把旧 formal state 或历史
  evidence 伪装成新 diagnostic 输入。
- target_outcome: 由一个新的 clean Cdiag 将 Step 4 machine state 从当前
  `formal-ready / confirmed` 完整恢复为 `diagnostic-ready / diagnostic-pending`，并让随后的一次
  fresh full diagnostic 成为唯一可继续审查的输入。

## Scope

- in_scope:
  1. 只重置 `coverage-contract` 与 `coverage-thresholds` 中由状态转换控制的字段，并恢复 current
     frozen diagnostic predecessor 的 exact pending bytes：contract 的 status 与
     `tooling_manifest.publication_status` 均为 `diagnostic-ready`；threshold 状态为
     `diagnostic-pending`，三组 observed/reviewed fields 为 null，且 review 只保留四个 pending
     fields。不得保留形成混合状态的旧 formal observation 或 review data；历史 candidate/freeze
     只作为不可复用记录，绝不成为当前 state input。
  2. 保留既有 coverage policy、floor、critical set、exclusion、report/test cardinality 和
     validator semantics，只重建与当前 Cdiag bytes 匹配的 machine-state / hash closure。
  3. 重封 Step 4 manifest，并同步 Step 6 对 Step 4 manifest 的 CI-contract binding、其机械
     validator binding 与 Step 6 manifest。仅在当前 validator 要求时更新对应工具常量；不得改变
     runner、selector、workflow graph 或 fail-closed 语义。
  4. 使用既有静态 contract validation/negative 边界，证明 status-only、contract/threshold mismatch、
     pending observation 和残留 formal review 的混合状态会被拒绝。对旧 review 残留须在隔离副本中
     调用既有 structure-only validator；pending state 的 candidate/final 拒绝继续由既有 XML
     negative 证明。不得新增或放宽 validator/negative semantics，且该证明不得依赖 Maven、Docker、
     历史 runtime output。
  5. 记录一个安全的 state-reset Cdiag checkpoint，并完成新的 clean+pushed Cdiag → independent
     static review → fresh full Step 4 diagnostic → new candidate/Git-safe capsule/dual review →
     direct-child Cfreeze → fresh formal → quality/audit → foggy-projects scoped reacceptance 链。任何
     后续 Step 5 决定仍需独立 owner authorization。
- affected_modules:
  - `scripts/v934/step4/coverage-contract.json`
  - `scripts/v934/step4/coverage-thresholds.json`
  - `scripts/v934/step4/SHA256SUMS`
  - `scripts/v934/step6/{ci-contract.json,ci_contract_tool.py,SHA256SUMS}`
  - 本 canonical work item、其 predecessor 的 `NEEDS_REPLAN` linkage、一个 state-reset static
    checkpoint、其 independent review，以及 AC-5/AC-6 新产生的 run-owned safe evidence
- external_dependencies: Maven、Docker 与测试数据库只可由后续 canonical Step 4 runner 在 fresh
  diagnostic/formal 中使用；state-reset Cdiag 的静态验证不得调用它们。

## Non-Goals

- out_of_scope:
  - 不重新校准、降低或放宽任何 coverage threshold、floor、critical set、exclusion、report/test
    cardinality、source/authority policy 或 fail-closed result；`thresholds.status` 的状态恢复不构成
    threshold policy 变更。
  - 不修改 package terminal-integrity 实现、Step 5 runner/rehearsal/replay/pointer/final authority，
    不重试或重标 r3/r4，也不删除历史 Cfreeze、formal 或 evidence。
  - 不修改 production/test source、POM、Dockerfile、database/fixture policy、CI workflow schema/
    semantics/job graph、public API/SPI/module layout，亦不进入 9.3.5 或 9.4.0；唯一允许的 CI
    contract delta 是声明的 state/hash mechanical binding。
- do_not_touch:
  - `scripts/verify-v934-release-gate.sh`、`scripts/verify-v934-step4-coverage.sh` 的运行语义、
    `scripts/v934/step5/release_package_tool.py`，以及 existing package failure schemas。
  - `scripts/v934/step4/coverage_tool.py`、`coverage_xml_tool.py`、successor overlay policy、coverage
    XML/verifier semantics；若当前 validator 不能表达所需 reset，应设置 `NEEDS_REPLAN`，不得静默
    修改其语义。
  - 除 declared docs allowlist 外的版本文档，尤其历史 run/evidence/candidate/capsule/freeze/formal
    文件及任何 raw logs、XML、runtime payload。

## Confirmed Decisions

| Decision | Rationale | Compatibility / Constraint |
|---|---|---|
| exact pending predecessor reset，而非只改两个 status | runner 已 fail-closed 拒绝 `formal/confirmed` diagnostic，单字段翻转或泛化重建会留下不一致绑定/identity | 必须恢复 current frozen predecessor 的 exact bytes，并由现有 validator 接受 |
| 旧 formal/Cfreeze 仅保留为历史 | package tooling bytes 已变化，旧 authority 不能与新 Cdiag 拼接 | 不删除、不重写、不重新标记历史 evidence |
| reset 是新 Cdiag，不是 Cfreeze 或 formal 替代 | diagnostic 的入口必须先恢复；formal mode 不能替代 diagnostic | 后续顺序严格为 diagnostic → review → direct-child Cfreeze → formal |
| Step 4→Step 6 hash/CI closure 同步 | machine-state bytes 是 governed input，局部状态写入会造成 CI binding drift | 只更新机械绑定，不改变 workflow/runner policy |
| 静态先行、运行时后置 | Cdiag 先证明 reset 可验证且 fail-closed，再消耗一次 fresh diagnostic | static phase 不运行 Maven、Docker 或 Step 5 |

## Acceptance Criteria

- [ ] AC-1: `validate-contract` 在新 Cdiag 上仅返回 current frozen diagnostic predecessor 的 exact
  `diagnostic-ready / diagnostic-pending` state：contract status 与
  `tooling_manifest.publication_status` 均为 `diagnostic-ready`；threshold 的 observed/reviewed
  三组字段为 null，review 仅有 reviewer/reviewed_at/diagnostic_run_id/decision 四字段且前三者为
  null、decision=`pending-all-lane-diagnostic`。没有 formal observation/review data 成为当前 machine
  state input，历史 candidate/freeze 不被引入或读取为 state input，且 exact predecessor
  identity/binding 同时通过现有 validator 与安全布尔 identity comparison。
- [ ] AC-2: 既有 policy/floor/critical/exclusion、source/authority contract、report/test cardinality 与
  validator fail-closed semantics 没有变化；当前 package terminal-integrity closure bytes 不被本事项
  修改或回退。
- [ ] AC-3: 确定性静态负向覆盖拒绝至少 status-only、contract/threshold mismatch、pending
  observation 和残留 formal-review 的混合状态；旧 review 残留通过隔离副本的现有 structure-only
  validator 验证，pending state 的 candidate/final 拒绝沿用既有 XML negative。不得产生 raw-output
  evidence，且不调用 Maven/Docker 或 outer diagnostic runner。
- [ ] AC-4: Step 4 contract/successor-overlay positive-and-negative validation、Step 4/5/6 manifests、
  Step 6 CI-contract self/negative validation全部通过；Step 5 manifest/tool 无 drift。没有未声明路径，
  也没有 workflow schema/semantic/graph、API/SPI/POM drift。
- [ ] AC-5: 新 clean+pushed Cdiag 及其 static checkpoint 在 fresh diagnostic 前经独立审阅；checkpoint
  只声明安全静态结果，不把它表述为 diagnostic、Cfreeze、formal、Step 5 或 version authority。
- [ ] AC-6: clean+pushed Cdiag 后的一次 fresh full Step 4 diagnostic、新 candidate/Git-safe capsule、
  两路独立 review、direct-child Cfreeze、fresh formal、quality/audit 和 foggy-projects scoped
  reacceptance 均实际完成；历史 r3/r4、原 formal/Cfreeze 及其 artifacts 不可复用。
- [ ] AC-7: 在 AC-1 至 AC-6 全部完成之前，不启动新的 Step 5 rehearsal、portable replay、release/
  pointer/final promotion、Step 6/7 runtime or authority action、9.3.5 或 9.4.0 transition。AC-4 的
  本地 Step 6 static contract self/negative validation 是唯一非运行时例外。

## Contract / Data / Security Constraints

- API or event contract: 无 public API、SPI、configuration、package layout、failure receipt、pointer
  或 workflow schema/semantic/graph 变化；声明的 machine-state 与机械 hash binding 除外。
- data and migration: state-reset static phase 无业务数据、schema、fixture、Docker image、registry、
  cache 或 host mutation；它只作用于受控 repository machine-state documents。AC-6 的后续 runtime
  仅可由 canonical runner 在其既有受控范围内执行。
- compatibility and rollback: 任何 reset validation 失败都保持 fail-closed，不能通过 formal/release
  模式绕过。回滚或再设计必须建立新的 governed replan，历史 authority 永不恢复。
- permissions and secrets: 不读取、发布、哈希或运输 raw stdout/stderr、logs、commands、paths、
  credentials、endpoint、container/image/OCI identity 或 digest；evidence 仅保留 approved status、
  phase、schema、count 和 boolean。

## Test and Evidence Obligations

| Item | Risk | Required Validation | Required Evidence |
|---|---|---|---|
| AC-1/AC-3 | critical | contract validation, successor-overlay closure and deterministic hybrid-state negatives | safe state/category/count/pass-fail summary |
| AC-2 | critical | declared-path review and static policy/validator comparison | no-policy-drift boolean matrix |
| AC-4 | critical | Step 4 successor overlay, Step 4/5/6 manifests, Step 6 CI self/negative closure | governed closure result only |
| AC-5 | blocker | clean Cdiag independent review | safe static checkpoint and review decision |
| AC-6 | blocker | clean+pushed Cdiag → fresh diagnostic → candidate/Git-safe capsule/dual review → Cfreeze → formal → quality/audit → owner reacceptance | new run-owned safe evidence only |
| AC-7 | blocker | authority-boundary review | no-Step-5/no-downstream-action decision |

## Bug Context

- bug_source: governance-preflight-found
- severity: blocker
- environment: WSL isolated Cdiag diagnostic-preflight.
- current_behavior: the approved package terminal-integrity Cdiag inherits `formal-ready / confirmed` from
  its predecessor. The recorded isolated preflight correctly refuses that state, while the predecessor work
  item forbids modifying coverage contract and thresholds.
- expected_behavior: the successor Cdiag must first establish a complete diagnostic-pending machine state;
  only then may the canonical runner consume one fresh diagnostic authorization.
- reproduction_steps: use the already recorded isolated preflight showing `formal-ready / confirmed` against
  diagnostic mode's required state. The state-reset static proof may invoke only this work item's declared
  static validators/negatives; it must not rerun the outer diagnostic runner, Maven, Docker or any historical
  run before the new Cdiag gate.
- reproduction_status: confirmed by independent preflight review.
- existing_evidence: the terminal-integrity Cdiag static checkpoint and independent implementation/negative/
  hash reviews are retained; none is a Step 4 runtime or Step 5 authority input.
- existing_tests: Step 4 contract validation, static contract-negative suite, Step 4/5/6 manifest validation,
  and Step 6 CI-contract self/negative validation.
- regression_protection: required.
- waiver_reason_and_risk: N/A.

## Risks and Open Questions

- known_risks: an incomplete reset could retain a stale reviewed threshold or candidate binding, weaken a
  policy to make it pass, or create Step 4/Step 6 hash drift. Each is a fail-closed blocker.
- open_questions: none.

## Approval Record

- approval_authority: direct project-owner continuation direction.
- approved_by: `foggy-projects`.
- approved_at: 2026-07-22.
- approved_scope: minimal complete workflow-state reset and its required revalidation chain only; it does
  not authorize a Step 5 retry or any later roadmap/API/SPI work.

## Ultra Execution Contract

- Start from a clean descendant of the terminal-integrity Cdiag; preserve its declared code/hash/docs repair
  and do not amend or rewrite it.
- First establish deterministic static hybrid-state rejection and full hash/CI closure. Do not run Maven,
  Docker or a Step 4 diagnostic during the state-reset static proof.
- State files must restore the current frozen diagnostic predecessor's exact pending bytes and bindings, not
  a generic schema reconstruction or bytes copied from an unrelated historical run. Keep historical evidence
  immutable and unreferenced as current input. Do not invoke the outer runner until the new clean+pushed
  Cdiag has passed its static review.
- If a required path is outside the declared state/negative/hash/CI/docs closure, if validator semantics must
  change, or if any state policy would need adjustment, set `NEEDS_REPLAN` and stop expansion.
- Complete all AC-1 through AC-7 before setting `READY_FOR_SIGNOFF`; do not self-accept and do not start a
  Step 5 retry without separate owner approval.

## Implementation Result

- implementation_summary: restored the current frozen diagnostic predecessor's exact Step 4 contract and
  threshold state, then refreshed only the Step 4 manifest and the transitive Step 6 CI binding/manifest.
  No Step 4 validator, overlay, XML tool, Step 5 tool, runner, policy, workflow semantics or artifact
  authority rule changed.
- changed_paths: the declared two Step 4 state files, Step 4 manifest, Step 6 CI binding tool/contract/
  manifest, this work item and one safe static checkpoint only.
- tests_and_results: exact predecessor/state and allowed-path booleans, Step 4/5/6 manifest checks,
  contract/overlay positive validation, isolated status-only and stale-formal-review rejection, existing
  contract/XML/overlay/CI negative suites, package synthetic negative, Python/Bash syntax and whitespace
  checks passed. No Maven, Docker, outer runner or Step 5 runtime action ran.
- manual_or_experience_evidence:
  `docs/9.3.4/evidence/step-4/step4-postrun-integrity-workflow-state-reset-cdiag-static-20260722.md`
  and `docs/9.3.4/evidence/step-4/step4-postrun-integrity-workflow-state-reset-cdiag-review-20260722.md`
- deviations: none
- residual_risks: this is static Cdiag evidence only. It neither creates a diagnostic/candidate/capsule/
  Cfreeze/formal result nor reuses historical authority. The fresh runtime chain remains mandatory.
- readiness: ULTRA_EXECUTING — static Cdiag and independent review are complete; the fresh diagnostic and
  every later authority gate remain pending.

## References

- predecessor work item:
  `docs/9.3.4/workitems/BUG-step5-postrun-step4-artifact-verifier-integrity-replan.md`
- predecessor static checkpoint:
  `docs/9.3.4/evidence/step-5/step5-postrun-step4-integrity-closure-cdiag-static-20260721.md`
- controlling Step 5 scope:
  `docs/9.3.4/workitems/FEATURE-v934-release-authority-and-ci.md`
