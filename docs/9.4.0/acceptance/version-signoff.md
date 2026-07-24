---
acceptance_scope: cross-version
version: 9.3.4-to-9.4.0
target: v934-v940-speed-forward
doc_role: acceptance-record
status: signed-off
decision: accepted-with-risks
signed_off_by: codex-reviewer
signed_off_at: 2026-07-24
reviewed_by: codex-same-implementation-session
independent_review: false
blocking_items: []
follow_up_required: no
evidence_count: 8
assurance_level: standard
---

# 9.3.4 → 9.4.0 Speed-Forward Version Signoff

## Document Purpose

- intended_for: release-owner / reviewer / project-root-session
- purpose: 对 9.3.4 carry-forward、9.3.5 收敛目标和 9.4.0 SPI v2/模块化形成正式签收结论。

## Background

本记录对 9.3.5 Gate 0 classification debt、QueryFacade/DTO、bypass 清理、internal ports、
反向依赖拆解，以及 9.4.0 Model SPI v2/模块化形成最终跨版本验收结论。

验收范围遵循 owner 已批准的 speed-forward 契约，不复活 Step 5/Step 7 authority、
semantic/portable replay、source seal、五库通用矩阵或 GitHub CI，也不执行 tag、release、publish。

- version_goal: 在 owner 承担既有 authority/replay 流程风险的前提下，完成 9.3.5 公共 API 收敛和
  9.4.0 SPI v2/模块化，并由一次最终候选整体验收给出准出结论。
- success_criteria: Gate 0 debt 关闭；稳定 facade/DTO 和 internal ports 落地；未批准 bypass 清零；
  目标反向依赖拆解；API/core/JDBC/starter/web/TCK 单向模块化；兼容层、迁移说明和成品装配可用。
- canonical_plan_or_index: `docs/9.4.0/workitems/FEATURE-v934-v940-speed-forward.md`
- critical_outcomes: full reactor、MySQL 5.7 Gate 0、依赖方向、兼容 facade、launcher 成品边界。
- non_blocking_or_waivable_items: owner 明确排除的 authority/replay/source-seal/CI，以及一个周期的
  legacy aggregate。

## Acceptance Basis

- canonical spec: `docs/9.4.0/workitems/FEATURE-v934-v940-speed-forward.md`
- original implementation base: `3b1c7249ba75b3bab54cb0f898ea1c198e5303d4`
- final `origin/main` base: `20ef23e8d3e00f05c9864f2ec1bd3bd2785fbf6b`
- accepted code candidate: `9d02808e141f2fed7eba8bb9d0bebb7c84a206db`
- branch: `codex/v940-speed-forward`
- changed surface against final main: 171 paths, 5,610 insertions, 899 deletions
- environment: independent Git worktree, JDK 17.0.19, Maven 3.8.7, Docker MySQL 5.7 fixture
- original dirty workspace remained untouched:
  `M docs/9.3.5/README.md` and `?? docs/9.3.5/workitems/`

## Version Goal Conformance

| Goal / Criterion | Delivered | Evidence | Result |
|---|---|---|---|
| 9.3.5 Gate 0 | 分类债关闭，缺失配置 fail-closed，MySQL 5.7 lane 受控 | Gate 0 script + 7 reports/12 tests | pass |
| QueryFacade/DTO | 稳定 API 模块与兼容 facade，DTO 语义保持 | API/compatibility tests + code review | pass |
| bypass/internal ports | 外部入口经 governed facade/port，runtime/MCP validation 依赖收窄 | boundary/affected tests | pass |
| 反向依赖拆解 | compose/pivot/semantic 角色拆为规划、执行、缓存、catalog ports | dependency and boundary tests | pass |
| 9.4.0 SPI v2 | API/core/JDBC/starter/web/TCK 建立并单向依赖 | reactor + dependency tree | pass |
| 兼容与成品 | legacy aggregate 保留，迁移文档完成，launcher 内容正确 | compatibility + JAR inspection | pass |
| 最终候选 | 32-module clean verify 与必要 MySQL 5.7 集成回归通过 | executed commands below | pass |

## Module Summary

| Module / Area | Delivery Status | Acceptance Evidence | Notes |
|---|---|---|---|
| 9.3.5 Gate 0 | signed-off | MySQL 5.7 governed lane | temporary classification exception removed |
| Query API and legacy facade | signed-off | API + compatibility tests | JVM names preserved |
| Compose/pivot/semantic ports | signed-off | boundary and affected runtime/model tests | narrow internal roles |
| Model API/core/JDBC/starter/web | signed-off | full reactor + dependency tree | target graph is one-way |
| Model TCK/cache addon | signed-off | TCK/context tests | cache declares only actual capability |
| Launcher | signed-off | smoke + JAR inspection | TCK/cache absent from runtime JAR |

## Evidence Coverage Summary

| Area | Classification | Required | Evidence | Reused / New | Gaps | Result |
|---|---|---|---|---|---|---|
| automated tests | core-blocker | full affected and final reactor | 32-module clean verify | new | none | pass |
| integration / E2E | core-blocker for changed DB path | MySQL 5.7 Gate 0 | 7 reports/12 tests | new | generic five-DB matrix waived | pass |
| API/SPI compatibility | core-blocker | stable names and legacy bridge | focused compatibility suites | new | legacy removal deferred | pass |
| dependency architecture | core-blocker | target graph one-way | Maven compile dependency tree | new | none | pass |
| artifact assembly | core-blocker | launcher production contents | smoke + JAR inspection | new | none | pass |
| UI / experience | out-of-scope | none | no user-facing UI change | N/A | none | N/A |
| authority / replay | scoped-risk | explicitly waived | owner decision in canonical spec | reused | not rerun | accepted-risk |

## Review Findings and Remediation

Review was performed before the final integrated acceptance. It found and closed the following candidate issues:

- detached model validation builder test was moved to the owning model module;
- facade request parameters now snapshot mutably supplied maps while preserving JSON null values;
- legacy semantic planning/execution adapters avoid default-method dispatch ambiguity in mocks/proxies;
- null compose planning output now maps to a typed compile exception instead of an NPE;
- aggregate relation aliases no longer leak as public qualifiers, while ambiguous same-source participants remain qualified;
- runtime tests were aligned with the new execution and validation ports;
- targeted reactor test selection remains fail-closed by default but can be explicitly scoped;
- Gate 0 verifier is stored executable and succeeds through direct invocation.

No incompatible API/SPI break, security/permission reduction, data-correctness weakening, or irreversible migration
was introduced.

## Contract Conformance

| Item | Delivered evidence | Result |
|---|---|---|
| 9.3.5 Gate 0 debt | missing configuration fails closed; MySQL 5.7 lane 7 reports/12 tests, F0/E0/S0 | pass |
| Stable facade and DTO | stable API module, compatibility facade, namespace and parameter semantics covered | pass |
| Bypass and internal ports | controller/runtime/addon paths use governed facade/ports; boundary tests pass | pass |
| Compose/pivot/semantic dependency split | planning, SQL, runtime, cache and catalog roles separated behind narrow ports | pass |
| SPI v2 modules | API/core/JDBC/starter/web/TCK modules compile and test in the target order | pass |
| Dependency direction | `core→api`, `jdbc→core→api`, `starter→jdbc→core→api`, `web/tck→core→api` | pass |
| Compatibility cycle | legacy aggregate/facade retained for one documented compatibility cycle | pass |
| Addon and launcher assembly | cache TCK/context and launcher smoke pass; production package content verified | pass |
| Full candidate validation | all 32 root reactor modules succeeded under `clean verify` | pass |
| Delivery guardrails | no CI, tag, release, publish, authority/replay/source-seal run, or `mvn install` | pass |

## Executed Evidence

- `mvn clean verify -DskipITs -Dsurefire.failIfNoTests=false -Dfailsafe.failIfNoTests=false`
  - 32/32 reactor modules succeeded.
  - total time: 6 minutes 11 seconds.
  - included API/core/JDBC/starter/web/TCK, legacy model, cache addon, runtime, launcher and addon tests.
- `V935_GATE0_SKIP_UNIT=true scripts/v935/gate0/verify-mysql57-classification.sh`
  - negative missing-configuration gate succeeded;
  - run-owned pinned MySQL 5.7 fixture succeeded;
  - 7 Failsafe reports, 12 tests, 0 failures, 0 errors, 0 skipped.
- Targeted dependency tree for API/core/JDBC/starter/web/TCK:
  - reactor order and compile edges were one-way;
  - no target module depended back on the legacy aggregate.
- Launcher JAR inspection:
  - included `foggy-dataset-model`, `-api`, `-core`, `-jdbc`, `-starter`, `-web`;
  - excluded `foggy-dataset-model-tck` and `foggy-dataset-model-cache`.
- Review-focused suites:
  - aggregate/alias compatibility: 111 tests, F0/E0/S0;
  - compose/validation focus: 34 tests, F0/E0/S0;
  - runtime affected reactor selection: 57 tests, F0/E0/S0.

All Maven evidence used reactor `test`, `verify`, or packaging lifecycles. No `mvn install` was executed during
the final review and acceptance process.

## Evidence Sufficiency

- assurance_level: standard
- why_existing_evidence_is_sufficient_or_not: API/SPI and module boundaries have focused tests, the complete
  32-module reactor was rebuilt from `clean`, the only newly governed database lane ran against pinned MySQL 5.7,
  and launcher runtime contents were inspected directly.
- new_validation_that_could_change_decision: an independent reviewer could improve organizational assurance but is
  not expected to change the technical result absent new findings.
- expensive_validation_omitted_and_reason: Step 5/Step 7 authority, portable replay, source seal, generic five-DB
  matrix and CI were explicitly excluded by the owner-approved speed-forward contract.

## Optional Full-Chain Recommendation

- recommendation: user-requested
- qualifying_condition: final 9.4.0 candidate reached `READY_FOR_SIGNOFF`
- estimated_wall_clock_and_basis: 10–25 minutes estimated before execution
- scope_and_prerequisites: root reactor, Gate 0 MySQL 5.7, dependency and launcher artifact checks
- maximum_attempts: 1 broad candidate
- decision_impact: determine final cross-version signoff
- user_approval: approved
- execution_status: passed

## Waivers

| Waived Item | Authority | Reason | Bounded Impact | Non-Waivable Guards | Follow-up |
|---|---|---|---|---|---|
| Step 5/Step 7 authority and replay/source-seal | repository owner via user request | speed-forward; prior 9.3.4 evidence carried forward | process assurance only | correctness/security/compatibility/artifact integrity | none for this delivery |
| generic five-database matrix | repository owner via canonical spec | no broad DB behavior expansion; MySQL 5.7 is the changed governed lane | untested generic DB breadth | affected DB lane must pass | future release authority if desired |
| GitHub CI / tag / release / publish | repository owner via scope | explicitly outside delivery | no remote release evidence or action | local reactor and artifact checks | release owner workflow |

## Blocking Items

- none

## Failed Items

- none

The first direct Gate 0 invocation before remediation returned `Permission denied` because the script lacked an
executable bit. The script content was then run successfully with `bash`, its executable bit was corrected, and the
same governed lane passed again through direct invocation. This is closed and is not a residual failure.

## Accepted Risks

- The final review was performed in the same Codex implementation session and is therefore not organizationally
  independent. This prevents an unqualified `ACCEPTED` conclusion.
- Owner-approved exclusions remain exclusions: Step 5/Step 7 authority, semantic/portable replay, source seal,
  generic five-database matrix and GitHub CI were not rerun.
- The legacy `foggy-dataset-model` aggregate and facade remain for one compatibility cycle; removal requires a later,
  separately governed migration.
- Existing compiler/deprecation/unchecked warnings observed in unchanged or compatible code paths were not expanded
  into this delivery because tests and contract checks remained green.

None of these risks is a core correctness, security, compatibility, or artifact-integrity blocker for the approved
speed-forward scope.

## Final Decision

- decision: `accepted-with-risks`
- core blocking items: none
- rationale: all approved 9.3.5 and 9.4.0 outcomes are implemented, review findings were remediated, the full
  32-module candidate reactor passed, the governed MySQL 5.7 Gate 0 lane passed, dependency and launcher artifact
  boundaries match the contract, and all remaining gaps are explicit owner-approved process or compatibility risks.
- follow-up required for this delivery: no

## Signoff Marker

- acceptance_status: signed-off
- acceptance_decision: accepted-with-risks
- signed_off_by: codex-reviewer
- signed_off_at: 2026-07-24
- acceptance_record: `docs/9.4.0/acceptance/version-signoff.md`
- blocking_items: none
- follow_up_required: no
