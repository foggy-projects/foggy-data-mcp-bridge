---
doc_type: delivery-spec
delivery_type: cross-module
version: 9.5.3
ticket: SPIKE-runtime-model-authoring-foundations
status: ACCEPTED
canonical: true
execution_mode: ultra
assurance_level: standard
approved_by: owner
approved_at: 2026-07-31
open_questions: []
---

# Delivery Spec: Runtime 模型创作基础技术探针

## Document Purpose

- intended_for: normal-analysis / ultra-implementation / independent-signoff
- purpose: 用最小、可复核的测试和源码证据确认模型创作工作区可复用的 Runtime 能力、当前缺口和
  后续最小实现边界，不提前实现产品功能。
- canonical_path:
  `docs/9.5.3/workitems/SPIKE-runtime-model-authoring-foundations.md`

## Goal

- version_goal: 为 Runtime Console 的 TM/QM/FSScript 有界创作闭环建立真实技术基线。
- target_outcome:
  - 确认 detached validation 能否把一个草稿 Bundle 叠加到目标 Namespace，并复用同空间其他
    filesystem/JAR Bundle 与 FSScript 依赖，同时不污染 live catalog。
  - 判断 request-local candidate 是否具备查询验证/执行所需对象和调用边界；若当前不可行，精确
    记录缺失原语，不在探针中补实现。
  - 确认 Bundle inventory、资源类型、可写性和 JAR 多 Namespace 可见性的当前事实。
  - 用证据收敛下一阶段为直接复用 detached candidate，还是需要新的 workspace catalog。
- critical_outcomes:
  - 结论来自实际代码路径和 focused tests，不以设计推测代替。
  - 探针不修改生产行为、公共 API、模型语义、权限边界或 Console UI。
  - 每项问题明确分类为 `reusable-now`、`small-extension`、`new-runtime-primitive` 或
    `out-of-scope`。
- success_is_sufficient_when: AC-1 至 AC-8 均有直接证据，focused tests 与
  `git diff --check` 通过，并形成可以决定后续 workitem 拆分的 evidence matrix。

## Scope

- in_scope:
  - `foggy-dataset-model-engine` detached validation 的请求级 Bundle/FSScript/catalog 隔离。
  - `foggy-runtime-api` models validate、Bundle inventory、resource export/save 与 query 入口。
  - filesystem 草稿 Bundle 对同 Namespace 其他 external Bundle、classpath/JAR Bundle 和
    FSScript 的依赖解析。
  - 同名资源时草稿 overlay 与 live Bundle 的替换/冲突事实。
  - request-local candidate query 的可行性源码审计和最小测试探针。
  - `docs/9.5.3` 技术证据、结论和后续拆分建议。
- affected_modules:
  - `foggy-dataset-model-engine` test source only
  - `foggy-runtime-api` test source only
  - `docs/9.5.3`
  - 若无需新增测试文件，可只修改文档。
- external_dependencies: none；使用仓库现有 JUnit、Spring test、H2/SQLite fixture 和本地
  Maven reactor。

## Non-Goals

- out_of_scope:
  - 不实现 workspace API、编辑器、Git、release package、promotion、rollback、VS Code 插件或
    Agent。
  - 不新增 Preview Namespace、Namespace CRUD、持久 workspace catalog 或 workspace cache。
  - 不扩展 `.fsscript` resource save，不实现 JAR 多 Namespace mount，不修复探针发现的产品缺口。
  - 不修改 Runtime API、Bundle SPI、model engine 主代码、权限或数据源行为。
- do_not_touch:
  - `addons/foggy-runtime-console/frontend`
  - `foggy-mcp-launcher`
  - 生产源码与 Maven 依赖图
  - 已签收的 `docs/9.5.2/acceptance` 与历史 Implementation Result
- non_blocking_or_waivable_items:
  - 候选查询若被证实当前不可用，可以形成 `new-runtime-primitive` 结论，不要求在探针中实现。
  - JAR 多 Namespace 若被证实仅支持单 Namespace，可以形成明确缺口，不要求兼容原型。

## Confirmed Decisions

| Decision | Rationale | Compatibility / Constraint |
|---|---|---|
| 技术探针独立于产品实现 | 当前问题会决定 workspace 架构 | 禁止顺手修复或扩展 API |
| 首期假设一个 writable Bundle | 显著降低 change set、回滚和 UI 复杂度 | 同 Namespace 其他 Bundle 仍需作为只读依赖 |
| 优先复用 detached validation | 已有请求级 script/catalog 隔离与 live fallback 代码 | 必须用真实依赖 fixture 验证，不能只做源码推断 |
| 工作区不是普通 Namespace | 避免 Namespace CRUD、JAR 复制和临时 binding 清理 | 探针不引入 workspace identity |
| JAR 永远只读 | JAR 修改走重新构建和部署 | 本探针只检查发现、复用和 namespace 可见性 |
| 候选查询先做可行性判断 | 可能避免持久 workspace catalog | 若需要主代码实现则只记录最小缺口 |
| assurance 使用 standard | 事项为 test/docs 技术验证，无生产行为变更 | 不自行升级验证强度 |

## Acceptance Criteria

- [x] AC-1: 证明草稿 Bundle 内 `.tm`/`.qm` 可以 import 同一草稿 Bundle 内的 `.fsscript`，或记录
  精确失败阶段与原因。
- [x] AC-2: 证明草稿 Bundle 在目标 Namespace 下可以解析 live external Bundle 的只读资源/模型
  依赖，或记录精确限制。
- [x] AC-3: 证明草稿 Bundle 在目标 Namespace 下可以解析 classpath/JAR Bundle 的只读资源/模型
  依赖，或记录精确限制。
- [x] AC-4: 验证同名资源存在于草稿与 live Bundle 时，是草稿显式覆盖、重复冲突还是其他行为；
  不允许以加载顺序猜测。
- [x] AC-5: 验证成功和失败 detached validation 均不改变 live Bundle、script cache、catalog
  generation 或 source revision。
- [x] AC-6: 对 request-local candidate query 给出代码与测试支持的结论：
  `reusable-now`、`small-extension` 或 `new-runtime-primitive`，并列出最小缺失边界。
- [x] AC-7: 建立当前 capability inventory，至少覆盖 external/JAR Bundle 是否可列出、Bundle
  namespace cardinality、`.fsscript` export/save、configured/runtime-managed 可写性。
- [x] AC-8: 创建简洁 evidence matrix、风险与后续拆分建议；focused tests 和
  `git diff --check` 实际通过。

## Contract / Data / Security Constraints

- API or event contract: 本探针不得新增或改变 API、Header、DTO、事件、Bundle SPI 或
  RuntimeEnvelope。
- data and migration: 不写生产 registry、Bundle 目录、数据库或用户 Git 仓库；测试只使用
  临时目录和测试 ApplicationContext。
- compatibility and rollback: 只允许 test/docs 变更，可直接删除探针测试或回退文档；不得改变
  存量运行行为。
- permissions and secrets: 不使用真实 token、Git 凭据、连接串或外部服务；不得把任意测试临时
  路径提升为浏览器可提交的产品合同。

## Test and Evidence Obligations

| Item | Classification | Risk | Required Validation | Reusable Evidence | Required Evidence |
|---|---|---|---|---|---|
| AC-1/AC-2 | must-pass | major | focused JUnit with real temporary Bundle resources | detached validation isolation tests | FSScript 与 live external dependency assertions |
| AC-3 | must-pass | major | focused JUnit with classpath/JAR-style read-only fixture | existing classpath Bundle loaders | namespace-aware resolution result |
| AC-4/AC-5 | must-pass | major | focused JUnit + catalog identity assertions | existing isolation test | overlay precedence/conflict and unchanged live identities |
| AC-6 | must-pass | medium | source review + minimal compile/test probe | detached candidate builders | supported path or exact missing port/object |
| AC-7 | must-pass | medium | source/API DTO review + existing focused tests | current Bundle/resource controller tests | capability matrix with file/line evidence |
| AC-8 | must-pass | medium | affected test lane + diff check | existing reactor build rules | commands, totals, duration, evidence matrix |

## Validation Budget and Evidence Sufficiency

- assurance_level: standard
- lightweight_validation:
  - `git diff --check`
  - focused source review and targeted single-test execution；单次预期 `<5m`。
- medium_validation:
  - `mvn -B -ntp -pl foggy-runtime-api -am -DskipITs
    -Dtest=<focused-tests> -Dsurefire.failIfNoSpecifiedTests=false test`
  - 如测试归属 engine，使用对应 `-pl foggy-dataset-model-engine -am` focused lane；
    单次预期 `5-20m`。
- expensive_validation: none by default；不运行完整 reactor、数据库矩阵或 launcher package。
- large_authority_or_replay_policy: prohibited-unless-user-approved
- full_chain_recommendation_trigger: none
- estimated_full_chain_wall_clock: not-estimated
- full_chain_prerequisites: none
- user_approval_status: not-requested
- decision_if_not_approved: proceed-with-focused-and-affected-validation
- expensive_validation_trigger: none；若探针意外要求生产代码或公共契约变化，进入
  `NEEDS_REPLAN`，不得扩大验证。
- maximum_expensive_attempts: 0
- reusable_evidence:
  - `RuntimeModelValidationIsolationTest`
  - `DetachedModelValidationSessionBuilderTest`
  - Bundle/resource controller tests
  - catalog refresh identity tests
- stop_when_evidence_is_sufficient: AC-1 至 AC-8 已有可复核的通过或明确“不支持”证据，focused
  lane 通过，结论足以决定下一 workitem；不为提高证据数量追加全量测试。
- validation_not_required:
  - Console typecheck/build/Playwright
  - launcher/Maven package
  - full reactor、真实数据库矩阵
  - authority/replay/rehearsal/source-seal/tag/release/publish

## Waiver Policy

- waivable_items: 无；探针允许结论为当前不支持，但不允许把未验证描述成支持。
- authorized_role: owner
- non_waivable_guards: 不修改生产行为、不污染 live catalog、不泄露 secret、证据真实性。
- required_risk_record: 任一未能稳定构造的 fixture 必须记录限制、替代源码证据和对后续决策的
  影响。

## Risks and Open Questions

- known_risks:
  - 当前 detached isolation 测试偏重 mock，真实跨 Bundle/JAR 依赖可能暴露未覆盖限制。
  - classpath directory fixture 能证明 Spring Resource 路径，但不一定覆盖打包后嵌套 JAR 的全部
    行为；证据必须区分两者。
  - 候选 query 可能需要把 request-local catalog 注入语义执行链，超出本探针修改边界。
  - 当前 Bundle inventory 可能只列 external Bundle，不能据此误报所有运行 Bundle 均可管理。
- open_questions: none

## Ultra Execution Contract

- 先读取本文件、项目 `CLAUDE.md`、相关模块规范和 `foggy-runtime-cli-workflow` Skill。
- 在 scope 内自主决定测试 fixture 和证据组织，不修改生产源码。
- 如需改变目标、范围、已确认决策、兼容或安全边界，设置 `NEEDS_REPLAN` 并停止扩展。
- 运行与改动面匹配的验证，不得声称未实际运行的测试通过。
- 未经用户明确批准，不得主动运行预计超过 30 分钟或包含
  authority/replay/rehearsal/source-seal 的大型链路。
- 达到 evidence sufficiency 后停止，不追加与后续架构决策无关的验证。
- 完成后填写 `Implementation Result`，并将状态改为 `READY_FOR_SIGNOFF`；不得自行设置
  `ACCEPTED`。

## Evidence Matrix

| AC | 结果 | 分类 | 直接证据 |
|---|---|---|---|
| AC-1 | TM 和 QM 均可 import 草稿 Bundle 内相对路径 `.fsscript` | `reusable-now` | `DetachedModelAuthoringFoundationProbeTest:78-141` |
| AC-2 | 草稿 QM 可加载同 Namespace live external TM，且该 TM 可 import 自己的 FSScript | `reusable-now` | `DetachedModelAuthoringFoundationProbeTest:144-176` |
| AC-3 | 草稿 QM 可加载真实 `jar:` Resource TM/FSScript；资源不是 filesystem file | `reusable-now` | `DetachedModelAuthoringFoundationProbeTest:179-219` |
| AC-4 | detached context 在 Namespace 相同时先查 source，命中后不进入 live fallback；实测同名 external TM 来自草稿 | `reusable-now` + workspace policy guard | `DetachedModelValidationSessionImpl:473-488`；probe `:118-140` |
| AC-5 | 成功和语法失败后，live catalog identity、FSScript cache、Bundle cache/inventory 和 committed source revision 均保持不变 | `reusable-now` | probe `:141,176,219,222-246,417-445` |
| AC-6 | 当前 session 只暴露 TM/QM validate；Runtime query 固定调用 live `SemanticQueryServiceV3`，其 service/facade 又固定使用 live loader/context | `new-runtime-primitive` | `DetachedModelValidationSession:12-21`；`RuntimeQueryController:27-105`；`SemanticQueryServiceV3Impl:99-158`；`QueryFacadeImpl:47-178` |
| AC-7 | `/bundles` 与 `listExternalBundles()` 均遗漏 JAR；Bundle 只有单 Namespace；resource allowlist 无 `.fsscript`；save 只允许 Runtime registry Bundle | `small-extension` + JAR multi-mount `new-runtime-primitive` | `RuntimeBundlesController:56-75`；`SystemBundlesContextImpl:397-439,690-694`；`BundleDefinition:3-17`；`RuntimeResourcesController:125-215,263-280` |
| AC-8 | 设计、能力分类、分阶段路线、focused engine/runtime-api tests 与 diff check 均已形成 | complete | `docs/9.5.3/runtime-model-authoring-design.md` 与下列执行记录 |

### Candidate Query 结论

结论为 `new-runtime-primitive`。detached validation 已经能构造 request-local
`SystemBundlesContext`、FSScript loader、TM/QM loader 和 catalog，但 session 不返回 candidate
model/catalog，也没有 validate/generate SQL/execute port。现有语义查询入口不能安全“换一个
loader”：

- `SemanticQueryServiceV3Impl` 同时依赖 live `QueryModelLoader`、query execution port、权限、
  datasource、pivot/cache 等组件；
- `QueryFacadeImpl` 再次持有 live `QueryModelLoader` 和 `SystemBundlesContext`，并负责
  namespace scope、过滤链、pinned catalog identity 和最终执行；
- 直接从 detached QM 调用底层 `query()` 会绕开现有语义校验、权限、cache identity 和 Runtime
  envelope，不是可接受的过渡方案。

最小后续边界是受治理的 request-local candidate execution port：在同一 detached session 内
固定 candidate revision/model identity，同时复用目标 Namespace 的 datasource binding、
Authorization、权限/过滤/执行链，并隔离 cache 和 live catalog。

### Capability Inventory

| 能力 | 当前事实 | 后续影响 |
|---|---|---|
| external Bundle 列表 | 可列出 configured 与 Runtime-managed external | 可复用，但需明确 capability |
| JAR/classpath Bundle 列表 | Runtime `/bundles` 不可见 | inventory 小扩展 |
| JAR 多 Namespace | 一个 definition 只有一个 Namespace；其他 Namespace 查找不到 | 显式 mount/binding 是独立 Runtime 原语 |
| `.fsscript` import | 草稿、external 和 JAR 相对 import 均通过 | detached loader 可复用 |
| `.fsscript` export/save | allowlist 不接受 | 工作区 resource contract 小扩展 |
| configured external save | 拒绝 | 保持只读 |
| Runtime-managed external save | 支持 SHA 冲突检查与原子单文件写 | 只作为低层能力；工作区 publish 仍需 revision/refresh/rollback 契约 |
| JAR save/export | filesystem resource API 不支持 | 符合永远只读边界 |

## Implementation Result

- implementation_summary:
  - 新增真实 external/JAR Bundle、TM/QM/FSScript 和 detached loader 技术探针。
  - 证明 request-local overlay、跨 Bundle 依赖、覆盖优先级及成功/失败隔离。
  - 完成 candidate query 与 Bundle/resource capability 源码审计，并形成简化设计和后续路线。
- changed_paths:
  - `foggy-dataset-model-engine/src/test/java/com/foggyframework/dataset/model/validation/DetachedModelAuthoringFoundationProbeTest.java`
  - `docs/9.5.3/**`
  - 产品章程和文档索引：`docs/design/runtime-console-product-charter.md`、
    `docs/architecture/README.md`、`docs/9.5.2/README.md`
- tests_and_results:
  - `mvn -B -ntp -pl foggy-dataset-model-engine -DskipITs
    -Dtest=DetachedModelAuthoringFoundationProbeTest,DetachedModelValidationSessionBuilderTest
    -Dsurefire.failIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false test`：
    2 classes / 5 tests，通过，23.479s。
  - `mvn -B -ntp -pl foggy-runtime-api -am -DskipITs
    -Dtest=RuntimeModelValidationIsolationTest,RuntimeBundlesControllerTest,RuntimeCapabilitiesControllerEnabledTest#shouldListConfiguredBundleAsReadOnly+shouldExportRuntimeManagedBundleResources+shouldSaveRuntimeManagedBundleResources+shouldRejectInvalidResourceSaveBatchWithoutPartialWrites+shouldRejectSavingConfiguredBundleResources
    -Dsurefire.failIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false test`：
    3 classes / 13 tests，通过，57.792s。
  - `git diff --check` 及 untracked 文件逐文件 `git diff --no-index --check`：通过。
- manual_or_experience_evidence:
  - 无浏览器或人工体验范围；本事项为 test/docs 技术探针。
  - 源码审计确认 candidate query、inventory、Namespace cardinality 和 resource allowlist 边界。
- deviations: none
- residual_risks:
  - 动态 JAR fixture 覆盖标准 `jar:` Resource；未额外声称覆盖所有可执行 fat-JAR/nested-JAR
    打包器形态。
  - 当前 detached source 可以覆盖任意同名 live 资源；正式工作区必须限制为所选 source Bundle
    自身资源，并拒绝遮蔽 JAR/其他 Bundle。
  - candidate query、JAR 多 Namespace、`.fsscript` workspace resource 和 revision/publish
    均是后续 workitem，不是本探针已实现能力。
- reused_evidence:
  - `DetachedModelValidationSessionBuilderTest`：1 test。
  - `RuntimeModelValidationIsolationTest`：2 tests。
  - `RuntimeBundlesControllerTest`：6 tests。
  - `RuntimeCapabilitiesControllerEnabledTest` 的 Bundle/resource focused methods：5 tests。
- omitted_validation_and_reason:
  - 未运行 Console typecheck/build/Playwright、launcher package、完整 reactor、数据库矩阵和大型
    authority/replay/rehearsal；均不属于 test/docs changed surface，且 delivery spec 明确排除。
- readiness: READY_FOR_SIGNOFF

## Acceptance Status

- acceptance_status: signed-off
- acceptance_decision: accepted
- signed_off_by: Codex independent reviewer
- signed_off_at: 2026-07-31
- acceptance_record:
  `docs/9.5.3/acceptance/SPIKE-runtime-model-authoring-foundations-signoff.md`
- blocking_items: none
- follow_up_required: yes

## References

- requirement / issue: 用户确认先落档 9.5.3，再验证模型工作区技术问题，最后规划后续交付。
- architecture / glossary:
  - `docs/design/runtime-console-product-charter.md`
  - `docs/architecture/runtime-and-model-lifecycle.md`
- related work items:
  - `docs/9.5.2/workitems/OPT-runtime-console-namespace-workspace.md`
  - `docs/9.5.2/workitems/OPT-runtime-console-bundle-resource-operations.md`
  - `docs/9.5.2/workitems/OPT-runtime-console-model-lifecycle-center.md`
