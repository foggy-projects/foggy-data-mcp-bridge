---
acceptance_scope: feature
version: 9.5.3
target: SPIKE-runtime-model-authoring-foundations
status: signed-off
decision: accepted
signed_off_by: Codex independent reviewer
signed_off_at: 2026-07-31
reviewed_by: N/A
blocking_items: []
follow_up_required: yes
evidence_count: 8
assurance_level: standard
---

# Runtime 模型创作基础技术探针正式验收

## Document Purpose

- intended_for: signoff-owner / reviewer / project-root-session
- purpose: 对 9.5.3 Runtime 模型创作基础技术探针形成独立、可复核的正式签收结论。

## Background

- delivery_spec:
  `docs/9.5.3/workitems/SPIKE-runtime-model-authoring-foundations.md`
- target_outcome: 用真实 detached external/JAR/FSScript 路径和源码证据冻结 Runtime 模型创作
  基础能力、缺口与后续最小边界，不实现产品能力。
- signoff_scope: 当前 `main` 工作树中的 tracked 改动和全部 untracked 文件。
- critical_outcomes: AC-1 至 AC-8；不修改生产行为、公共 API、Bundle SPI、权限或 Console UI。
- non_blocking_or_waivable_items: candidate query 和 JAR 多 Namespace 当前不支持时，准确分类为
  后续原语，不要求在本探针实现。

## Acceptance Basis

- approved delivery spec: canonical spec 状态为 `READY_FOR_SIGNOFF`，assurance 为 `standard`。
- changed paths / diff: 实现候选包含 2 个 tracked 文档修改、5 个 untracked 文件；本记录是签收
  新增的第 6 个 untracked 文件。边界限于版本/架构/产品文档、验收记录和 model-engine test
  source。无生产源码、Maven、launcher 或 Console UI 改动。
- test records:
  - engine focused lane：2 classes / 5 tests，0 failures/errors/skips，独立重跑通过。
  - Runtime API affected lane：3 classes / 13 tests，0 failures/errors/skips，独立重跑通过。
  - tracked `git diff --check` 返回 0；最终 6 个 untracked 文件逐项
    `git diff --no-index --check /dev/null <file>` 均无 whitespace 诊断。
- independent rerun commands:

  ```bash
  mvn -B -ntp -pl foggy-dataset-model-engine -DskipITs \
    -Dtest=DetachedModelAuthoringFoundationProbeTest,DetachedModelValidationSessionBuilderTest \
    -Dsurefire.failIfNoTests=false \
    -Dsurefire.failIfNoSpecifiedTests=false test

  mvn -B -ntp -pl foggy-runtime-api -am -DskipITs \
    '-Dtest=RuntimeModelValidationIsolationTest,RuntimeBundlesControllerTest,RuntimeCapabilitiesControllerEnabledTest#shouldListConfiguredBundleAsReadOnly+shouldExportRuntimeManagedBundleResources+shouldSaveRuntimeManagedBundleResources+shouldRejectInvalidResourceSaveBatchWithoutPartialWrites+shouldRejectSavingConfiguredBundleResources' \
    -Dsurefire.failIfNoTests=false \
    -Dsurefire.failIfNoSpecifiedTests=false test
  ```

  Reviewer wall clock：engine `25.761s`；Runtime API reactor `56.060s`。
- experience evidence: 不适用；本交付为 test/docs 技术探针。
- migration / compatibility evidence: 无生产、API、SPI、依赖或持久化变更，不需要迁移。

## Contract Conformance

| Item | Expected | Delivered | Evidence | Result |
|---|---|---|---|---|
| AC-1 | 草稿 TM/QM import 同 Bundle FSScript | 两条真实解析链均以脚本导出值形成模型 | probe `:78-141` | pass |
| AC-2 | 草稿 QM 解析同 Namespace external TM/FSScript | QM 加载 `LiveExternalModel`，观测到 external Bundle 与脚本 tableName | probe `:144-176` | pass |
| AC-3 | 草稿 QM 解析真实 JAR TM/FSScript | 动态 JAR 产生非 file 的标准 `jar:` Resource，并完成模型加载 | probe `:179-219` | pass |
| AC-4 | 明确同名资源语义 | 同 Namespace 先查 source、命中即返回；实测模型来自草稿而非 live | detached context `:473-488`；probe `:78-141` | pass |
| AC-5 | 成功/失败均不污染 live 状态 | catalog 对象/identity、FSScript cache、Bundle cache/list、committed source revision 均不变 | probe `:141,176,219,222-246,417-445`；Runtime isolation 2 tests | pass |
| AC-6 | candidate query 准确分类 | session 只暴露 validate；Runtime controller/service/facade 固定绑定 live loader、context、权限/filter/cache 链，结论为 `new-runtime-primitive` | session `:12-21`；Runtime query `:27-105`；semantic service `:99-158,198-261`；query facade `:47-178,285-301` | pass |
| AC-7 | capability inventory 准确 | `/bundles` 仅基于 external inventory；Bundle 单 Namespace；allowlist 无 `.fsscript`；configured 只读，Runtime-managed 仅支持低层保存 | bundles controller `:56-75`；context `:397-439,690-694`；BundleDefinition `:3-17`；resources controller `:125-215,263-280` | pass |
| AC-8 | evidence matrix、路线、focused tests、diff check | 文档已形成；5/5、13/13 与全部 whitespace check 通过 | canonical spec、设计文档和本验收记录 | pass |

## Implementation Quality

- scope and changed surface: 与批准边界一致；唯一 Java 新文件位于 test source。
- maintainability and duplication: probe 使用集中 fixture/state snapshot/helper，无生产复制或测试绕过。
- error handling and edge cases: 同时覆盖成功、语法失败、source overlay、external/JAR fallback 和
  Runtime resource invalid batch/configured read-only。
- contract, data and compatibility: 测试只使用临时目录、临时 JAR、测试
  `GenericApplicationContext` 和终端 builder test double；无生产数据或外部服务。
- terminology and documentation: `reusable-now`、`small-extension`、`new-runtime-primitive` 与
  product charter、canonical spec 和 PROPOSED 设计一致。

## Evidence Matrix

| Item | Classification | Risk | Evidence | Reused / New | Result |
|---|---|---|---|---|---|
| AC-1 | core-blocker | major | 草稿 TM/QM + FSScript marker 断言 | new | pass |
| AC-2 | core-blocker | major | live external TM/FSScript 真实 fallback | new | pass |
| AC-3 | core-blocker | major | 标准 `jar:` Resource + JAR TM/FSScript | new | pass |
| AC-4 | core-blocker | major | source-first 源码分支 + 草稿来源观测 | new | pass |
| AC-5 | core-blocker | major | live state snapshot；Runtime isolation tests | new + reused | pass |
| AC-6 | core-blocker | medium | detached API 与 live query execution chain 源码审计 | new | pass |
| AC-7 | core-blocker | medium | inventory/cardinality/allowlist/writability 源码与 focused API tests | new + reused | pass |
| AC-8 | core-blocker | medium | 设计/路线、5/5、13/13、whitespace checks | new | pass |

## Evidence Sufficiency

- assurance_level: standard
- why_existing_evidence_is_sufficient_or_not: 新 probe 覆盖真实 loader/Bundle/FSScript/detached
  production path，复用测试覆盖 Runtime isolation 与现有 Bundle/resource 契约；两组必须通过的
  focused lanes 已由 reviewer 独立重跑。
- new_validation_that_could_change_decision: none。
- expensive_validation_omitted_and_reason: 未运行完整 reactor、Console build/Playwright、
  launcher package、数据库矩阵、authority/replay/rehearsal/source-seal；均被 delivery spec
  明确排除，且不会改变 test/docs spike 的 standard assurance 结论。

## Optional Full-Chain Recommendation

- recommendation: not-needed
- qualifying_condition: none
- estimated_wall_clock_and_basis: not-estimated
- scope_and_prerequisites: none
- maximum_attempts: 1
- decision_impact: none
- user_approval: not-requested
- execution_status: not-run

## Waivers

| Waived Item | Authority | Reason | Bounded Impact | Non-Waivable Guards | Follow-up |
|---|---|---|---|---|---|
| none | | | | | |

## Failed Items

- none

## Risks / Follow-ups

- `scoped-risk`：动态 JAR fixture 证明标准 `jar:` Resource，不代表所有 fat-JAR/nested-JAR
  打包器；进入相应部署 workitem 时补真实打包形态验证。
- `scoped-risk`：detached source 技术上可覆盖任意同名 live 资源；正式 workspace policy 必须
  限制为所选 writable Bundle，并拒绝遮蔽 JAR 或其他 Bundle。
- `scoped-risk`：终端 TM/QM builder 使用无数据库 test double；loader、Bundle、FSScript 与
  detached session 均走生产路径，符合本 probe 的 standard assurance。candidate query 下一
  workitem 需以真实数据库和权限流水线验证。
- `out-of-scope`：candidate query、JAR 多 Namespace、`.fsscript` workspace resource contract、
  revision/publish 尚未实现；它们是后续 workitem，不是本 spike 声称交付的产品能力。
- `process-gap`：none。

## Final Decision

- decision: accepted
- rationale: AC-1 至 AC-8 均有直接源码/测试证据，两组 must-pass focused lanes 与全部
  whitespace checks 通过，changed surface 遵守 test/docs 边界，无生产行为变化或未声明偏差。
  设计中的“不创建临时 Namespace、单 writable Bundle、不持久化 candidate catalog、Git
  非前提、JAR 多 Namespace 后置、candidate query 优先”均可回指技术证据和产品章程。
- blocking_items: none
- follow_up_owner_and_due: 后续 workitem owner；candidate-query overlay 应作为下一阶段首项，
  日期未在本次 spike 中承诺。

## Signoff Marker

- acceptance_status: signed-off
- acceptance_decision: accepted
- signed_off_by: Codex independent reviewer
- signed_off_at: 2026-07-31
- acceptance_record:
  `docs/9.5.3/acceptance/SPIKE-runtime-model-authoring-foundations-signoff.md`
- blocking_items: none
- follow_up_required: yes
