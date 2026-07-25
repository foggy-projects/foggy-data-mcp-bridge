---
doc_type: delivery-spec
delivery_type: cross-module
version: 9.5.1
ticket: addon-bundle-lifecycle-main-port
status: ACCEPTED
canonical: true
execution_mode: ultra
assurance_level: standard
approved_by: repository-owner-via-user-request
approved_at: 2026-07-25
open_questions: []
---

# Delivery Spec: Add-on 多 Bundle 生命周期能力迁回 main

## Document Purpose

- intended_for: normal-analysis / ultra-implementation / independent-signoff
- purpose: 固定 v910beta 原型的归档、选择性迁移、验证和 main 合并边界。
- canonical_path: `docs/9.5.1/workitems/FEATURE-addon-bundle-lifecycle-main-port.md`

## Goal

- version_goal: 在 main-after-9.5.0 模块边界上补齐同 namespace 多 Bundle 来源治理和
  Runtime Bundle/registry 一致性，不恢复任何 9.5.0 已删除的 legacy 身份。
- target_outcome: 模型可定位所属 Bundle；替换或删除一个 Bundle 不影响同 namespace 的兄弟
  Bundle；Runtime source 与 registry 在正常失败路径上不留下半提交状态。
- critical_outcomes:
  - 来源信息准确、不可确定时显式 unknown，不伪造 ownership。
  - replace 不采用先删后加造成的可见空窗。
  - registry 持久化失败会恢复原 source 状态；恢复失败返回稳定的不可恢复错误。
  - 保持 main 的显式 validate/refresh 和 Catalog admission 语义。
- success_is_sufficient_when: AC-1 至 AC-7 全部有实际运行证据，且受影响模块测试通过。

## Scope

- in_scope:
  - 归档 `codex/addon-bundle-lifecycle-v910beta` 未提交原型。
  - 在 `foggy-dataset-model-engine` 记录并展示 Bundle/resource 来源。
  - 在 `foggy-fsscript` 提供同名外部 Bundle 的原子 source replacement。
  - 在 `foggy-runtime-api` 协调 source mutation 与 registry 持久化/回滚。
  - 迁移同 namespace 多 Bundle、来源、replace/remove 和持久化失败回归测试。
- affected_modules:
  - `foggy-fsscript`
  - `foggy-dataset-model-engine`
  - `foggy-runtime-api`
  - `docs/9.5.1`
- external_dependencies: none

## Non-Goals

- out_of_scope:
  - 不恢复 `foggy-dataset-model` Maven 坐标或
    `com.foggyframework.dataset.db.model.*` Java 包。
  - 不新增第二套模型冲突检测或 detached admission service。
  - 不把 Bundle add/update 改为默认隐式 validate/refresh。
  - 不改变 datasource binding、namespace 迁移或 query API 语义。
  - 不运行 release authority、数据库矩阵或 source-seal 全链路。
- do_not_touch:
  - 9.5.0 Model SPI v2 模块边界和兼容决策。
  - `BundleLifecycleListener` 的 Catalog refresh 失败策略：source 已提交时保留旧 Catalog
    或维持 admission blocked，不向 mutation owner 回抛。
- non_blocking_or_waivable_items:
  - 不新增对外 API 版本；来源字段只扩展现有响应。
  - 不为本功能执行超过 30 分钟的 release/full-chain 验证。

## Confirmed Decisions

| Decision | Rationale | Compatibility / Constraint |
|---|---|---|
| 旧 v910beta 仅归档，不直接 cherry-pick | 分支落后且基于已删除模块/包 | 只迁移仍适用的语义和测试 |
| source mutation 成功后再持久化 registry | 与 main 资源生命周期一致 | registry 失败必须回滚 source |
| replace 使用单一原子 source mutation | 避免 remove/add 空窗和兄弟 Bundle 误伤 | 失败后旧 Bundle、定义和 watcher 保持 |
| Catalog refresh 失败不让 Bundle event listener 回抛 | 注册资源不隐式承诺查询可见 | 保留 main admission/convergence 策略 |
| 复用现有 `RuntimeBundleModelConflictDetector` | main 已有 canonical TM/QM 冲突检查 | 不引入重复 admission service |
| validate/refresh 保持显式 | 遵循 canonical runtime/model lifecycle | 现有 flags 的 Stage 1 行为不扩大 |

## Acceptance Criteria

- [x] AC-1: v910beta 原型已形成可追溯归档提交，原 worktree 无未提交源码。
- [x] AC-2: Catalog TM/QM provenance 包含可选 `bundleName`、namespace 和 resource identity，
  list/describe 返回这些信息；来源不可确定时显式标记。
- [x] AC-3: 同 namespace 两个 Bundle 可同时发布并查询；replace/remove 一个 Bundle 后兄弟
  Bundle 及其模型继续可用。
- [x] AC-4: `SystemBundlesContext` 支持同名外部 Bundle 原子 replace；候选或 watcher 等
  预提交步骤失败时恢复旧 Bundle、definition、watcher 且不推进 source revision。事件监听失败
  遵循 main 既有语义：source 保持已提交，由 Catalog admission/convergence 处理。
- [x] AC-5: Runtime add/update/remove 在 registry 持久化失败时恢复 source；恢复失败返回稳定
  rollback error，且 registry service 自身持久化失败不污染内存记录。
- [x] AC-6: 当前模型冲突检测、显式 validate/refresh、配置 Bundle 不可变等既有行为保持。
- [x] AC-7: 聚焦测试、三个受影响 Maven 模块的测试/编译实际通过，diff 无 whitespace error。

## Contract / Data / Security Constraints

- API or event contract: 仅向现有模型 catalog 响应扩展可空来源字段；Bundle mutation error code
  保持稳定、可诊断，不返回本地文件内容。
- data and migration: registry JSON schema 不升版；不新增数据库迁移。
- compatibility and rollback: 实现可通过 revert 本次 main merge 回滚；旧 registry 文件仍可读取。
- permissions and secrets: resource identity 仅为 Bundle 内资源 identity/path，不包含 datasource
  密码、token 或文件内容。

## Test and Evidence Obligations

| Item | Classification | Risk | Required Validation | Reusable Evidence | Required Evidence |
|---|---|---|---|---|---|
| AC-1 | must-pass | minor | Git status/log | archive commit | commit id and clean status |
| AC-2/AC-3 | must-pass | major | unit + SQLite integration | v910beta scenarios as design input only | actual main test output |
| AC-4 | must-pass | major | fsscript unit/integration | none | add/replace/remove success and rollback tests |
| AC-5/AC-6 | must-pass | major | runtime-api unit/controller tests | current conflict tests | actual main test output |
| AC-7 | must-pass | major | focused then affected reactor | none | exact commands and results |

## Validation Budget and Evidence Sufficiency

- assurance_level: standard
- lightweight_validation: `git diff --check`; focused JUnit classes; expected under 5 minutes each.
- medium_validation: affected module reactor compile/test with `-pl ... -am`; expected 5-30 minutes.
- expensive_validation: none required.
- large_authority_or_replay_policy: prohibited-unless-user-approved
- full_chain_recommendation_trigger: none; no release/tag/publish scope and no broad SPI change.
- estimated_full_chain_wall_clock: not-estimated
- full_chain_prerequisites: none
- user_approval_status: not-requested
- decision_if_not_approved: proceed-with-focused-and-affected-validation
- expensive_validation_trigger: only if focused/affected evidence cannot determine a must-pass result.
- maximum_expensive_attempts: 0 without replan/user approval.
- reusable_evidence: v910beta 32 passing tests only prove archived baseline behavior and cannot replace main evidence.
- stop_when_evidence_is_sufficient: all ACs mapped to passing focused and affected-module evidence, clean diff,
  and signoff review finds no blocker.
- validation_not_required: root release authority, external database matrix, Playwright/UI, artifact promotion,
  source seal and remote CI.

## Waiver Policy

- waivable_items: documentation wording or non-core diagnostic formatting only.
- authorized_role: repository owner.
- non_waivable_guards: source/registry consistency, sibling Bundle isolation, provenance accuracy, no legacy
  module/package restoration.
- required_risk_record: bounded impact, detection, rollback and owner follow-up.

## Risks and Open Questions

- known_risks:
  - Spring events are synchronous and multiple listeners may observe a committed source；监听失败不回滚
    source，必须依赖现有 Catalog admission/convergence 保留旧 Catalog 或阻断查询。
  - watcher teardown/restore is implementation-sensitive and requires direct tests.
  - runtime registry failure injection must verify both disk and in-memory state.
- open_questions: none

## Ultra Execution Contract

- 先读取本文件、项目架构规范和相关模块测试。
- 在 scope 内自主决定具体类和实现结构，不机械搬运旧包路径。
- 如需改变目标、范围、已确认决策、兼容或安全边界，设置 `NEEDS_REPLAN` 并停止扩展。
- 运行与改动面匹配的验证，不得声称未实际运行的测试通过。
- 未经用户明确批准，不运行预计超过 30 分钟或包含 authority/replay/rehearsal/source-seal 的链路。
- 完成后填写 `Implementation Result`，将状态改为 `READY_FOR_SIGNOFF`；不得自行设置
  `ACCEPTED`。

## Implementation Result

- implementation_summary:
  - 为 TM、QM 和 synthetic QM 的 catalog provenance 增加可选 Bundle/resource 来源，并在
    catalog list 与 runtime describe 中以 known/unknown 形式暴露。
  - 为外部 Bundle 增加同名、同 namespace 的原子 replace；watcher 预提交失败恢复旧
    Bundle/definition/watcher 且不推进 source revision，事件监听失败保留已提交 source。
  - Runtime Bundle add/update/remove 改为 source mutation 后持久化 registry；持久化失败恢复
    source，registry service 同步恢复内存记录并返回稳定错误码。
- changed_paths:
  - `foggy-dataset-model-engine`: provenance、TM/QM loader、catalog list、detached context 与
    SQLite 多 Bundle 集成测试。
  - `foggy-fsscript`: `SystemBundlesContext` 原子 replace 实现及 source revision/watcher/event
    回归测试。
  - `foggy-runtime-api`: Bundle 编排、registry 事务性内存恢复、model describe 来源字段及
    controller/service/兼容测试。
  - `docs/9.5.1`: canonical delivery spec 和验收证据。
- tests_and_results:
  - `mvn -pl foggy-dataset-model-engine -DskipTests install`：
    3159 tests，0 failures，0 errors，2 skipped；BUILD SUCCESS。
  - `mvn -pl foggy-dataset-model-engine -Dtest=JdbcQueryModelCompletenessTest
    -DskipITs=false -Dit.test=CatalogRefreshQueryIT verify`：
    2 unit tests 与 3 integration tests 全部通过；BUILD SUCCESS。
  - `mvn -pl foggy-fsscript test`：406 tests，0 failures/errors；BUILD SUCCESS。
  - `mvn -pl foggy-fsscript -Dtest=BundleSourceCommitOrderingTest test`：
    6 tests，0 failures/errors；BUILD SUCCESS（最终 watcher/source revision 断言版本）。
  - `mvn -pl foggy-runtime-api test`：144 tests，0 failures/errors；BUILD SUCCESS。
  - `mvn -pl foggy-runtime-api
    -Dtest=RuntimeBundleRegistryServiceTest,RuntimeBundlesControllerTest,RuntimeModelOperationsDescribeTest
    test`：10 tests，0 failures/errors；BUILD SUCCESS。
  - `git diff --check`：通过。
- manual_or_experience_evidence: N/A
- deviations:
  - 实现过程中发现 `JdbcQueryModelCompletenessTest` 通过反射依赖旧的五参数
    `stageQueryModel`；保留兼容 overload 后重跑通过，不改变批准目标或公共 API。
  - runtime 既有测试仍断言 remove/add replacement，已按批准的原子 replace 契约更新并完成
    全量回归。
- residual_risks:
  - Spring listener 在 source 提交后失败仍采用 main 现有 admission/convergence 语义；该行为
    已通过直接失败注入验证，但未扩展为 release authority/full-chain 验证。
- reused_evidence:
  - 归档提交 `4a3a3ef4` 仅用于 AC-1 可追溯性；产品正确性结论全部使用 main 当前实现的实际
    测试结果。
- omitted_validation_and_reason:
  - 未运行外部数据库矩阵、release authority、source seal、artifact promotion 和远程 CI；
    均为批准 non-goal，且现有 affected-module 证据足以支持 standard assurance。
- readiness: READY_FOR_SIGNOFF

## References

- requirement / issue:
  `../foggy-data-mcp-bridge-addon-bundle-v910beta/docs/9.1.0.beta/P0-Add-on多Bundle原子注册与来源治理-需求.md`
- architecture / glossary:
  - `docs/architecture/runtime-and-model-lifecycle.md`
  - `docs/architecture/module-boundaries.md`
- related work items:
  - archived prototype commit `4a3a3ef4`

## Acceptance Status

- acceptance_status: signed-off
- acceptance_decision: accepted
- signed_off_by: independent-reviewer-codex
- signed_off_at: 2026-07-25
- acceptance_record:
  `../acceptance/addon-bundle-lifecycle-main-port-acceptance.md`
- blocking_items: none
- follow_up_required: no
