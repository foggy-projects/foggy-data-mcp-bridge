---
doc_type: delivery-spec
delivery_type: feature
version: 9.5.3
ticket: FEATURE-runtime-console-authoring-publish-recovery
status: ACCEPTED
canonical: true
execution_mode: ultra
assurance_level: standard
approved_by: user
approved_at: 2026-08-01
open_questions: []
---

# Delivery Spec: Runtime Console authoring publish 与失败恢复闭环

## Document Purpose

- intended_for: normal-analysis / ultra-implementation / independent-signoff
- purpose: 冻结 Console 对已验收 Runtime authoring publish/recovery API 的受控接入；本事项只表达服务端
  exact revision、publication evidence 和终态，不实现伪 publish、live save/refresh fallback 或产品级 rollback。
- canonical_path:
  `docs/9.5.3/workitems/FEATURE-runtime-console-authoring-publish-recovery.md`

## Goal

- version_goal: 完成 9.5.3 开发 Runtime 的手工模型创作闭环，使用户能确认并发布 exact validated
  candidate、观察发布过程与证据、在服务端要求时执行 pinned recovery，并从已发布 Bundle 开始下一轮创作。
- target_outcome: Console 忠实调用已签收的 publish/recover routes；所有请求 identity 来自当前服务端
  workspace head，所有状态和 publication evidence 来自服务端响应，失败时不自动重试或改走 live API。
- critical_outcomes:
  - 只有 exact current `VALIDATED` candidate 可进入 publish confirmation，并提交三个固定 revision identity。
  - `PUBLISHING`、`RECOVERY_REQUIRED`、`PUBLISHED` 的只读/恢复/终态动作严格遵循后端 contract。
  - recovery 只 pin 服务端 attempt 与 candidate，成功后显示 `STALE/RECOVERED`，不宣称历史 rollback。
  - `PUBLISHED` 保留只读证据，并允许从当前 eligible Bundle 创建一个新的独立 workspace。
  - 零自动 mutation retry、零 live resource save/model refresh/live query fallback、零 API/Engine 改动。
- success_is_sufficient_when: AC-1 至 AC-10 均有当前源码证据，desktop/mobile Playwright 覆盖成功发布与
  failure-to-recovery，Console unit/typecheck/build/full affected lane 通过，changed paths 严格在批准范围。

## Scope

- in_scope:
  - 扩展 Console workspace/publication 类型、状态动作矩阵和安全文案，消费 additive `lastPublication`。
  - 对 exact current validated candidate 提供显式 publish confirmation；显示 Namespace、Bundle、candidate、
    base Bundle 和 base Namespace source revision 以及影响边界。
  - 调用 `POST /api/v1/authoring/workspaces/{workspaceId}/publish`，request 仅提交当前 workspace 的
    `expectedCandidateRevision`、`expectedBaseBundleRevision`、`expectedBaseNamespaceSourceRevision`。
  - 展示 `PUBLISHING` metadata 刷新入口；期间禁止 save/delete/validate/query/discard/publish/recover。
  - 展示 `RECOVERY_REQUIRED` diagnostics 和 exact attempt/candidate；经二次确认调用 pinned recover route。
  - 展示 `PUBLISHED` applied/source/catalog evidence 与 immutable terminal state，资源、diff、validation
    evidence 继续只读可见。
  - 从 `PUBLISHED` workspace 的当前 `workspaceEligible` source Bundle 创建下一 workspace；旧 workspace
    不重新变为可写。
  - 把 publish/recovery 编排集中在一个 composable；`AuthoringWorkspace.vue` 保持唯一 authoritative
    workspace/revision owner，纯展示组件不得复制 head。
  - unit、typecheck、production build、desktop/mobile focused Playwright、affected full Playwright 和 diff checks。
- affected_modules:
  - `addons/foggy-runtime-console/frontend`
  - `docs/9.5.3`
- external_dependencies: 只复用现有 Vue 3、Element Plus、Axios、Vitest 与 Playwright；不新增 npm/Maven 依赖。

## Non-Goals

- out_of_scope:
  - Runtime API、Engine、Bundle SPI、model behavior、launcher、Maven/POM、数据库或 server-side store 改动。
  - release package、生产 promotion/import、成功发布后的历史 rollback、revision selector、rebase/merge。
  - Git、审批/RBAC/审计、多 Runtime 编排、JAR fork/upgrade/多 Namespace binding、Agent 或 VS Code 插件。
  - 自动 publish/recover retry、后台自动 polling、自动刷新 live model、自动创建下一 workspace。
  - 重做 Console 主题、建设大型 Web IDE 或引入新编辑器/状态管理依赖。
- do_not_touch:
  - `foggy-runtime-api`、`foggy-dataset-model-*`、`foggy-mcp-launcher`、任何 POM/lock dependency graph。
  - 已签收 API/workspace/cleanup workitem 的历史结果和验收记录。
  - `docs/9.5.3/runtime-model-authoring-design.md` 的 `PROPOSED` 状态。
- non_blocking_or_waivable_items:
  - 自动 polling、publication 历史时间线、artifact 下载、语法高亮和高级 diff 属于后续体验增强。
  - publish 中间态以用户显式刷新 metadata 收敛，不要求后台轮询。

## Confirmed Decisions

| Decision | Rationale | Compatibility / Constraint |
|---|---|---|
| Console 只调用已验收 publish/recover routes | 发布事务与恢复由 Runtime 权威编排 | 不调用 `/resources/save` 或 `/models/refresh` 拼装流程 |
| publish identity 全部来自 current workspace metadata | 避免 path、Namespace、revision 被客户端覆盖 | confirmation 后若 head 变化，由服务端 exact guard 拒绝 |
| 不自动 retry publish/recover | 两者都是高风险控制面 mutation | 错误后先刷新 metadata，由用户重新确认 |
| `PUBLISHING` 采用显式刷新而非 polling | 避免后台请求和隐式状态迁移 | metadata 可读，所有 mutation disabled |
| recovery pin `lastPublication.attemptId` 与 candidate | 失败现场必须与 durable attempt 对齐 | 缺失或变化时不发送请求 |
| `PUBLISHED` 是 immutable terminal workspace | 后端已固定 published evidence | 下一轮修改必须创建新 workspace |
| 下一 workspace 复用现有 create API | published Bundle 仍由服务端声明 eligibility | eligibility 不成立时只解释，不伪造入口 |
| publication 编排提取单一 composable | cleanup 已建立 authoritative state 边界 | composable 不持有 workspace head，只构造动作并返回响应 |
| 延续现有工业控制台视觉语言 | 产品章程要求克制、高密度、证据优先 | 不重做主题，不新增视觉依赖 |
| recovery 不是历史 rollback | API 只补偿 failed attempt 的 base | UI 使用“失败恢复”，不使用任意版本回滚术语 |

## State and Action Matrix

| State | Read resource/diff/evidence | Save/delete | Validate/query | Discard | Publish | Recover |
|---|---:|---:|---:|---:|---:|---:|
| `DRAFT` | yes | yes | validate yes | yes | no | no |
| `VALIDATED` | yes | yes | yes | yes | exact current only | no |
| `STALE` | yes | existing contract | no | yes | no | no |
| `PUBLISHING` | yes | no | no | no | no | no; refresh metadata only |
| `RECOVERY_REQUIRED` | yes | no | no | no | no | exact attempt/candidate only |
| `PUBLISHED` | yes | no | no | no | no | no; create next workspace only |
| `DISCARDED` | metadata only | no | no | no | no | no |

## Acceptance Criteria

- [x] AC-1: Console 类型和 state/action policy 忠实覆盖 `PUBLISHING`、`RECOVERY_REQUIRED`、`PUBLISHED`
  与完整 `lastPublication`；未知/缺失 evidence fail closed，不开放推断动作。
- [x] AC-2: publish 只在 current `VALIDATED`、validation evidence 精确匹配 candidate、无 dirty/busy 时开放；
  confirmation 展示 Namespace、Bundle、candidate/base/source revisions、不可自动回滚与开发 Runtime 边界。
- [x] AC-3: publish request 只提交三个已验收 exact identity 字段，使用当前 management session 和 `X-NS`；
  不提交 caller path/Namespace/Bundle 覆盖，不调用 live save/refresh/query fallback。
- [x] AC-4: publish 成功或 metadata refresh 后准确展示 `PUBLISHED`、attempt、applied Bundle revision、published
  Namespace source revision 和 before/after catalog generation；终态只读，资源/diff/evidence 仍可检查。
- [x] AC-5: `PUBLISHED` workspace 仅在当前 source Bundle 仍由服务端返回 `workspaceEligible=true` 时开放
  “创建下一 workspace”；调用既有 create API 后打开新 identity，不修改或复用旧 terminal workspace。
- [x] AC-6: `PUBLISHING` 禁止所有 mutation/validate/query/discard/publish/recover，仅允许显式刷新 metadata；
  不后台 polling，不把未知中间态写成成功或失败。
- [x] AC-7: `RECOVERY_REQUIRED` 展示 safe diagnostics、attempt 与 candidate，只经显式确认用二者调用 recover；
  缺失 evidence 时 fail closed，零自动 retry，错误保留 code/phase/suggested action 且不泄露 path/secret。
- [x] AC-8: recovery 成功后展示服务端 `STALE` 与 `RECOVERED` evidence/recovered catalog generation，保留草稿
  只读/迁移路径，并明确这不是成功发布后的历史 rollback。
- [x] AC-9: publish/recover busy、disabled、confirmation、error、refresh 和 terminal states 在 desktop/mobile
  清晰可操作；现有 create/edit/diff/validate/candidate-query/discard 行为在旧状态保持兼容。
- [x] AC-10: `git diff --check`、全部 untracked no-index check、typecheck、unit、build、focused 与 full
  Playwright 通过；changed paths 仅 Console frontend 与 9.5.3 文档，零新依赖和后端/launcher改动。

## Contract / Data / Security Constraints

- API or event contract:
  - `POST .../{workspaceId}/publish` body 固定三个 expected revision 字段。
  - `POST .../{workspaceId}/publish/recover` body 固定 expected candidate revision 与 opaque attempt ID。
  - GET workspace metadata 是 publication state/evidence 的唯一刷新来源；不在浏览器推导成功。
  - 复用现有 RuntimeEnvelope、`X-Foggy-Runtime-Code`、`X-NS` 与 management session；publish/recover 不发送
    candidate query 的业务 `Authorization`。
- data and migration:
  - 不新增浏览器持久化、数据库、workspace schema 或本地 publication log。
  - confirmation facts 与 busy/error 只存在当前页面内存；authoritative state 仍在 Runtime。
- compatibility and rollback:
  - additive Console UI；Runtime 不支持新字段/route 时 fail closed 并保持原草稿能力。
  - UI 回退不修改 server-side workspace/publication 数据。
- permissions and secrets:
  - workspaceId/attemptId 不是权限身份；请求继续通过 configured management session。
  - token、Authorization、绝对 path、store identity、stack trace 和资源正文不得写入 URL、日志、截图或文档。
  - diagnostics 只显示服务端已脱敏字段；不将 `safeToAutoRepair` 解释为自动 mutation 授权。

## Test and Evidence Obligations

| Item | Classification | Risk | Required Validation | Reusable Evidence | Required Evidence |
|---|---|---|---|---|---|
| AC-1/AC-2 | must-pass | major | state/payload unit + Playwright | accepted Console workspace policy | exact eligibility、evidence、dirty/busy gates 与 confirmation facts |
| AC-3/AC-4 | must-pass | critical | request interception + success flow | accepted publish API contract | exact body/header/route、terminal evidence、零 fallback |
| AC-5/AC-6 | must-pass | major | desktop/mobile Playwright | accepted create API | terminal guard、eligible next workspace、显式 metadata refresh |
| AC-7/AC-8 | must-pass | critical | failure/recovery Playwright | accepted recovery API contract | exact attempt/candidate、no retry、STALE/RECOVERED evidence |
| AC-9 | must-pass | major | focused + full desktop/mobile lane | existing responsive authoring flow | busy/error/confirm/terminal 与既有行为回归 |
| AC-10 | must-pass | major | typecheck/unit/build/E2E/diff review | accepted backend evidence | 精确命令、数量与 changed path audit |
| polling/history/rollback/Git/JAR binding | non-goal | deferred | none | roadmap | UI 不声明这些能力 |

## Validation Budget and Evidence Sufficiency

- assurance_level: standard；纯 Console 接入已验收 API，不修改发布事务、安全边界或 live production behavior。
- lightweight_validation:
  - `git diff --check` 及每个 untracked 文件的 `git diff --no-index --check /dev/null <file>`。
  - 在 `addons/foggy-runtime-console/frontend` 运行 `npm run typecheck`、`npm run test:unit`。
  - source review state/action matrix、request payload、zero fallback 和 changed paths。
- medium_validation:
  - `npm run build`。
  - `npx playwright test --grep "authoring workspace"`。
  - `npx playwright test`。
- expensive_validation: none by default。
- large_authority_or_replay_policy: prohibited-unless-user-approved
- full_chain_recommendation_trigger: none；若需要 Runtime API/Engine/auth/依赖/launcher 改动，设置
  `NEEDS_REPLAN`，不扩大本 workitem。
- estimated_full_chain_wall_clock: not-estimated
- full_chain_prerequisites: none
- user_approval_status: not-requested
- decision_if_not_approved: proceed-with-focused-and-affected-validation
- expensive_validation_trigger: none within approved scope
- maximum_expensive_attempts: 0
- reusable_evidence:
  - publish/recovery API R2 的 exact identity、immutable artifact、recovery、auth 与 compatibility evidence；
    Console 不修改其生产输入或实现。
  - 已签收 Console workspace/cleanup 的 create/edit/CAS/diff/validate/query/discard 与 responsive evidence；
    被改动的 frontend lane 仍须基于当前源码重跑。
- stop_when_evidence_is_sufficient: AC-1 至 AC-10 对应 unit、desktop/mobile focused/full evidence 绿，source
  review 证明零 fallback/越界，diff/untracked checks 通过后停止。
- validation_not_required: Maven reactor、Runtime/Engine tests、launcher package、数据库矩阵、authority/replay/
  rehearsal/source-seal、tag、release、production publish、真实外部 Runtime。

## Waiver Policy

- waivable_items: 仅自动 polling、publication 历史时间线、artifact 下载和非关键动画/排版优化。
- authorized_role: product owner / delivery owner
- non_waivable_guards: exact revision/attempt、validation/currentness gate、confirmation、immutable terminal guard、
  no automatic retry、no live fallback、secret/path redaction、desktop/mobile core flows 和零后端改动。
- required_risk_record: waiver 必须记录缺失体验、影响、当前替代路径和 follow-up owner；不得 waiver 错绑
  revision、错误恢复、重复 mutation、权限泄露或虚假 publish。

## Risks and Open Questions

- known_risks:
  - publish 完成前若页面关闭，Console 不会后台继续观察；重新打开 workspace 后以 GET metadata 收敛。
  - recovery 只处理 failed publication attempt，不提供成功发布的历史 rollback。
  - `PUBLISHED` 后若 Bundle eligibility 被 operator 改变，Console 不能创建下一 workspace，只能解释服务端事实。
  - Console 只覆盖开发 Runtime；release package、生产 promotion/import 仍是后续 workitem。
- open_questions: none

## Ultra Execution Contract

- 先完整读取本文件、项目 `CLAUDE.md`、Console 产品章程、authoring design、已验收 publish/recovery API
  spec 与 Console cleanup workitem，并使用适用前端与浏览器测试技能。
- 开始实现时把状态改为 `ULTRA_EXECUTING`；保护实际工作树，不回退无关用户改动。
- 在 scope 内自主决定 composable、展示组件、纯 policy 与测试拆分；authoritative workspace head 只能由
  `AuthoringWorkspace.vue` 持有。
- 若需要后端、依赖、产品边界或 must-pass contract 变化，设置 `NEEDS_REPLAN` 并停止扩展。
- 完成后填写 `Implementation Result`，将状态改为 `READY_FOR_SIGNOFF`；不得自行设为 `ACCEPTED`。
- 正式验收只有 accepted 或有 owner waiver 的 accepted-with-risks 才能回写 `ACCEPTED` 并 commit/push。

## Implementation Result

- implementation_summary:
  - 扩展 workspace/publication types 与 fail-closed action policy，覆盖 `PUBLISHING`、
    `RECOVERY_REQUIRED`、`PUBLISHED` 和完整 `lastPublication` evidence；未知状态零动作。
  - 新增单一 `useWorkspacePublication` composable，集中构造 exact publish/pinned recovery request 和
    metadata refresh；页面继续独占 authoritative workspace head。
  - 新增克制的 publication evidence panel，展示 attempt、applied/source/catalog revision、diagnostics、
    明确开发 Runtime/非历史 rollback 边界，并按状态开放 publish、refresh、recover 或下一 workspace。
  - publish 只在 valid exact current validation、无 dirty/busy 时开放；失败后只读取 metadata，不自动重试。
    recovery 只有 attempt/candidate 与当前 head 精确一致才开放。
  - `PUBLISHED` 保留资源/diff/evidence 只读视图；下一 workspace 只从当前服务端仍声明 eligible 的同名
    Bundle 通过既有 create API 创建，不复用 terminal identity。
- changed_paths:
  - `addons/foggy-runtime-console/frontend/src/features/authoring/AuthoringWorkspace.vue`
  - `addons/foggy-runtime-console/frontend/src/features/authoring/WorkspacePublication.vue`
  - `addons/foggy-runtime-console/frontend/src/features/authoring/authoringWorkspace.ts`
  - `addons/foggy-runtime-console/frontend/src/features/authoring/types.ts`
  - `addons/foggy-runtime-console/frontend/src/features/authoring/useWorkspacePublication.ts`
  - `addons/foggy-runtime-console/frontend/tests/unit/authoringWorkspace.test.ts`
  - `addons/foggy-runtime-console/frontend/tests/e2e/runtime-console.spec.ts`
  - `docs/9.5.3/README.md`
  - `docs/9.5.3/workitems/FEATURE-runtime-console-authoring-publish-recovery.md`
- tests_and_results:
  - `npm run typecheck`：exit 0。
  - `npm run test:unit`：exit 0；10 个 test files、33 个 tests 全部通过。
  - `npm run build`：exit 0；Vite 转换 2487 个 modules 并完成 production build；仅有既有依赖 PURE
    annotation 与大 chunk warning。
  - `npx playwright test --grep "authoring workspace"`：exit 0；desktop/mobile 共 6/6 tests 通过（21.5s）。
  - `npx playwright test`：exit 0；desktop/mobile affected Console lane 共 14/14 tests 通过（1.1m）。
  - `git diff --check`：exit 0；三个 untracked 文件逐一执行
    `git diff --no-index --check /dev/null <file>` 均无 whitespace error。
- manual_or_experience_evidence:
  - Playwright 拦截并逐字段断言 publish body 只有 candidate/base Bundle/base Namespace source revisions，
    recovery body 只有 candidate 与 opaque attempt；两条路径均携带 current `X-NS`。
  - desktop/mobile 实测 `PUBLISHING` 期间 new/validate/discard disabled、显式 metadata refresh 后进入
    `PUBLISHED`；随后从 eligible Bundle 创建新的 DRAFT workspace。
  - desktop/mobile 实测 publish failure 只发送一次，GET metadata 收敛到 `RECOVERY_REQUIRED`，确认框展示
    attempt/candidate/非 rollback 边界，显式 recover 只发送一次并进入 `STALE/RECOVERED`。
  - published screenshots：
    `addons/foggy-runtime-console/frontend/test-results/runtime-console-authoring--8e0d5-d-starts-the-next-workspace-desktop-chromium/authoring-published-desktop.png`
    与同目录 mobile project 对应截图。
  - recovered screenshots：
    `addons/foggy-runtime-console/frontend/test-results/runtime-console-authoring--4b744--pinned-publication-attempt-desktop-chromium/authoring-recovered-desktop.png`
    与同目录 mobile project 对应截图；人工检查无横向溢出、dialog/toast 遮挡或错误动作层级。
- deviations: none
- residual_risks:
  - Console 不后台 polling；用户离开 `PUBLISHING` 后需重新打开或显式刷新 metadata。这是契约确认的
    有界行为，不影响服务端 publication transaction。
  - 当前宿主缺少 CJK 字体，截图中文使用缺字 fallback；DOM 文案、accessible name 和交互断言均通过，
    产品沿用既有 CJK font stack，未新增依赖。
  - release package、生产 promotion/import、成功发布后的历史 rollback、Git 与 JAR 多 Namespace binding
    仍是后续 workitem，不是本 Console 闭环声称已交付的能力。
- reused_evidence:
  - 复用已签收 publish/recovery API R2 的 exact validation/identity、immutable artifact、source/catalog
    convergence、recovery、auth 与 compatibility evidence；本事项未修改任何后端输入契约或生产实现。
  - 现有 Console workspace/cleanup 的 create/edit/CAS/diff/validate/query/discard evidence 由本次 full
    Playwright 基于当前源码重新覆盖，不只依赖历史记录。
- omitted_validation_and_reason:
  - 未运行 Maven reactor、Runtime/Engine tests、launcher、数据库矩阵、authority/replay/rehearsal/
    source-seal、tag、release、production publish 或真实外部 Runtime；changed paths 仅在 Console frontend
    与 9.5.3 文档，standard assurance 明确不要求这些验证。
- readiness: READY_FOR_SIGNOFF

## References

- requirement / issue: 用户批准按 delivery spec、publish、recovery、验证、signoff/commit 的 1～5 顺序推进。
- architecture / glossary:
  - `CLAUDE.md`
  - `docs/design/runtime-console-product-charter.md`
  - `docs/9.5.3/README.md`
  - `docs/9.5.3/runtime-model-authoring-design.md`
- related work items:
  - `docs/9.5.3/workitems/FEATURE-runtime-console-authoring-workspace.md`
  - `docs/9.5.3/workitems/REF-runtime-console-authoring-workspace-cleanup.md`
  - `docs/9.5.3/workitems/FEATURE-runtime-authoring-workspace-publish-recovery-api.md`
  - `docs/9.5.3/workitems/BUG-runtime-authoring-published-base-republish-and-query-race-evidence.md`

## Acceptance Status

- acceptance_status: signed-off
- acceptance_decision: accepted
- signed_off_by: Codex independent reviewer
- signed_off_at: 2026-08-01
- acceptance_record: `docs/9.5.3/acceptance/FEATURE-runtime-console-authoring-publish-recovery-signoff.md`
- blocking_items: none
- follow_up_required: yes
