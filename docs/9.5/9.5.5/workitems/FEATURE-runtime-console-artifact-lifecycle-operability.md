---
doc_type: delivery-spec
delivery_type: feature
version: 9.5.5
ticket: FEATURE-runtime-console-artifact-lifecycle-operability
status: READY_FOR_SIGNOFF
canonical: true
execution_mode: ultra
assurance_level: standard
approved_by: user
approved_at: 2026-08-01
open_questions: []
---

# Delivery Spec: Runtime Console Artifact Lifecycle 运维视图

## Document Purpose

- intended_for: normal-analysis / ultra-implementation / independent-signoff
- purpose: 冻结 Console 对 accepted lifecycle inventory 的只读消费、操作级诊断和用户验收入口；不在浏览器中实现
  cleanup/retention 语义。
- canonical_path: `docs/9.5/9.5.5/workitems/FEATURE-runtime-console-artifact-lifecycle-operability.md`

## Goal

- version_goal: 让单 Runtime 运维人员无需调用 API 或猜目录即可判断 artifact store 是否健康、占用多少、哪些必须
  保留、哪些只是候选、哪些被阻断以及下一步应如何处理。
- target_outcome: Console 新增 lifecycle 运维工作台，忠实显示 overall/root health、容量/分类摘要、对象 ledger、
  references/blocked reason、刷新与筛选；任何 candidate 都明确标注“不是删除授权”。
- critical_outcomes:
  - 只调用 management-auth protected GET，不增加 mutation、path 参数或浏览器目录扫描。
  - loading/empty/partial/blocked/error/stale-response 均有清晰状态；刷新不覆盖迟到的新结果。
  - absolute path/storeId/content/secret 不进入 UI、console log、storage、URL 或截图。
  - desktop/tablet/mobile 的信息层级、键盘焦点、对比度和高密度对象阅读可用。
- success_is_sufficient_when: AC-1～AC-8 的 unit/typecheck/build、desktop+mobile focused Playwright、affected Console E2E、
  backend capability/auth baseline 和实际隔离 Console handoff evidence 完成，用户可打开页面验收。

## Scope

- in_scope:
  - 新增稳定主导航/route 和 industrial control-room 风格 lifecycle page，延续现有 Console shell/token/Namespace。
  - typed API client/model：capturedAt、health、roots、summary、objects、references、blockedReasons。
  - health/容量/classification overview、root cards、filter/search/object ledger、blocked detail、手工 refresh。
  - capability 不支持、auth 失效、API blocked inventory 与 transport error 的明确差异。
  - unit、typecheck/build、desktop/mobile Playwright、manual-ready isolated launcher evidence、docs/result writeback。
- affected_modules: `addons/foggy-runtime-console/frontend`、`docs/9.5/9.5.5`。
- external_dependencies: 复用 Vue 3、Element Plus、Axios、Vitest、Playwright；不新增 npm/Maven 依赖。

## Non-Goals

- out_of_scope:
  - cleanup/delete/repair/quarantine/retention/grace、candidate selection、confirmation 或 recovery mutation button。
  - 后端 API/schema/store、Engine/Model SPI、Console server/BFF、launcher/POM/dependency graph 改动。
  - metrics/history/alerting、跨 Runtime 聚合、后台 polling、持久筛选/偏好、导出/下载模型内容。
- do_not_touch: `foggy-runtime-api` production、Console server Java/POM、Engine、launcher、历史 acceptance、真实
  `.foggy-runtime`。
- non_blocking_or_waivable_items: 大对象分页/virtualization、长期历史趋势和 retention 操作属于后续项。

## Confirmed Decisions

| Decision | Rationale | Compatibility / Constraint |
|---|---|---|
| 独立主导航 `制品生命周期` | 运维对象量与 blocked detail 不适合塞入概览卡片 | 保持现有 Console shell/工业视觉语言 |
| 页面只消费 accepted GET inventory | Console 不发明第二套 filesystem 语义 | capability unsupported 时解释并禁用读取 |
| `candidate` 使用中性警示而非成功/可删除色 | 防止用户把 reachability 当删除授权 | 页面固定展示 safety notice |
| object ledger 默认按后端 deterministic 顺序 | 保持证据可复核 | 客户端筛选不修改 server facts |
| 手工 refresh，不做 polling | 避免后台读取与迟到结果覆盖 | 显示 capturedAt 和本地 loading 状态 |
| 视觉方向为“evidence ledger / industrial control room” | 适配高密度运维事实和现有产品章程 | 克制中性色、单一 amber/cyan 语义强调，无主题重做 |

## Acceptance Criteria

- [x] AC-1: Console shell 增加 desktop/mobile 可达 route/nav，直接 URL、login redirect、logout 与现有 navigation
  均不回归；页面清楚标记 Runtime-global，不误报为 Namespace 私有。
- [x] AC-2: typed client 忠实解析 accepted inventory DTO；不记录/存储/拼接 absolute path、storeId、模型内容或凭据。
- [x] AC-3: summary 显示 overall health、capturedAt、total objects/bytes、must retain/candidate/unknown/blocked，bytes 使用
  可读且精确 title；两个 root health/bytes/reasons 明确。
- [x] AC-4: object ledger 可按 store/reference class/blocked/filter 搜索，显示 type/identity/status/bytes/references/reason；
  空值和长 identity 在 desktop/mobile 可读且可访问。
- [x] AC-5: `NOT_INITIALIZED/PARTIAL/HEALTHY/BLOCKED`、transport error、auth failure、capability unsupported、loading/
  empty 均有差异化状态和可执行下一步；刷新具备 stale-response protection。
- [x] AC-6: 页面明确 candidate 不是删除授权，unknown/blocked 必须保留；无 cleanup、repair 或路径操作 UI。
- [x] AC-7: unit 覆盖 type/filter/format/stale behavior；desktop+mobile Playwright 覆盖 login→navigation→healthy/blocked→
  filter→refresh/error，并保存可复核 screenshot，console/page errors 为零。
- [x] AC-8: `npm run typecheck`、unit、build、focused/affected Playwright、必要 Maven Console smoke 和完整 diff checks
  通过；使用隔离 state 启动 Console，交付 URL/token-file path/验收步骤但不打印 token。

## Contract / Data / Security Constraints

- API or event contract: 仅 `GET /api/v1/authoring/artifacts/lifecycle` 与 capability
  `authoring.artifacts.lifecycleInventory=supported`。
- data and migration: 无 persistence、storage schema、cookie/localStorage 或 migration；token 继续仅 sessionStorage。
- compatibility and rollback: additive lazy route/page；回滚删除新前端文件/route/nav/test，无 backend/data 迁移。
- permissions and secrets: 复用 same-origin `X-Foggy-Runtime-Code`；页面、错误、截图和 console log 不显示 token/path/storeId。

## Test and Evidence Obligations

| Item | Classification | Risk | Required Validation | Reusable Evidence | Required Evidence |
|---|---|---|---|---|---|
| AC-1/5 | must-pass | major | router/session unit + desktop/mobile Playwright | accepted Console shell/auth | direct route、states、stale refresh |
| AC-2/3/4/6 | must-pass | major | typed unit + mocked API E2E | accepted inventory DTO/redaction | exact summaries/filter/notice/no mutation |
| AC-7 | must-pass | major | focused + affected Playwright/screenshots/log checks | existing Playwright harness | desktop/mobile evidence |
| AC-8 | must-pass | major | typecheck/unit/build/Console smoke/diff + isolated handoff | current Console build | exact commands/results/URL |

## Validation Budget and Evidence Sufficiency

- assurance_level: standard；read-only UI 消费已 accepted management API，不新增权限或 mutation。
- lightweight_validation: source/spec review、typecheck、unit、build、diff checks，单次 `<5m`。
- medium_validation: focused desktop/mobile Playwright、affected Console E2E、Console Maven smoke，预计 `5-20m`。
- expensive_validation: launcher profile package + isolated manual server 若现有 artifact 失效，预计 `5-20m`，最终最多一次。
- large_authority_or_replay_policy: prohibited-unless-user-approved
- full_chain_recommendation_trigger: none
- estimated_full_chain_wall_clock: not-estimated
- full_chain_prerequisites: none
- user_approval_status: not-requested
- decision_if_not_approved: proceed-with-focused-and-affected-validation
- expensive_validation_trigger: 只有前端静态资产/launcher artifact 不对应候选时才重新 package。
- maximum_expensive_attempts: 1
- reusable_evidence: accepted Console shell/auth/session/navigation/authoring Playwright 与 accepted inventory API；仅新 route/page
  和修改的 E2E mock/input 需重验。
- stop_when_evidence_is_sufficient: unit/typecheck/build、desktop/mobile key flow、affected Console lane、isolated handoff 全绿。
- validation_not_required: DB matrix、Engine、external services、shared-NFS/multi-process、authority/replay/rehearsal/tag/release。

## Waiver Policy

- waivable_items: isolated launcher handoff 可因环境不可用记录 process gap，但不得声称用户可验收。
- authorized_role: owner
- non_waivable_guards: auth/secret/path redaction、candidate safety notice、zero mutation、desktop/mobile core flow。
- required_risk_record: no pagination/history，single Runtime，candidate 非删除授权。

## Risks and Open Questions

- known_risks: v1 同步完整 payload 在大 store 可能较重；long identity 在小屏需渐进披露。
- open_questions: none

## Ultra Execution Contract

- 先读本 spec、Console charter、accepted inventory API、现有 shell/client/test harness 与 frontend-design/webapp-testing skills。
- 开始实现时改 `ULTRA_EXECUTING`；在既有工业视觉语言内自主决定组件拆分和响应式细节。
- 不新增 dependency、backend mutation、自动 polling、cleanup UI 或 token/path exposure；需要时转 `NEEDS_REPLAN`。
- 运行精确 unit/typecheck/build/Playwright/smoke/diff，保存截图；环境不可用不得伪造验收入口。
- 完成后填写 `Implementation Result` 并改 `READY_FOR_SIGNOFF`；Console 最终人工体验结论由用户给出。

## Implementation Result

- implementation_summary:
  - 新增 Runtime-global、只读 `#/artifact-lifecycle` 主导航与 evidence-ledger 页面，展示 overall/root health、
    capturedAt、容量与引用分类摘要、对象 facts、references/blocked reason、筛选、搜索和手工 refresh。
  - typed presentation policy 覆盖 accepted inventory DTO、bytes 精确 title、capability gate、client-side deterministic
    filtering 与 `LatestRequestGate`；刷新失败保留上一快照，迟到响应不能覆盖更新结果。
  - 固定展示“候选不是删除授权”；页面仅调用 capabilities 与 lifecycle GET，不提供 cleanup/delete/repair/path
    操作，不新增 npm/Maven dependency、storage、polling 或 backend mutation。
- changed_paths:
  - `addons/foggy-runtime-console/frontend/src/components/ConsoleShell.vue`
  - `addons/foggy-runtime-console/frontend/src/router.ts`
  - `addons/foggy-runtime-console/frontend/src/features/lifecycle/artifactLifecycle.ts`
  - `addons/foggy-runtime-console/frontend/src/pages/ArtifactLifecyclePage.vue`
  - `addons/foggy-runtime-console/frontend/tests/unit/artifactLifecycle.test.ts`
  - `addons/foggy-runtime-console/frontend/tests/e2e/runtime-console.spec.ts`
  - `docs/9.5/9.5.5/{README.md,workitems/FEATURE-runtime-console-artifact-lifecycle-operability.md}`
- tests_and_results:
  - `npm run typecheck`：exit 0。
  - `npm run test:unit`：11 files / 38 tests passed。
  - `npm run build`：2494 modules transformed，production build passed；仅既有 third-party PURE annotation 与
    aggregate chunk size warning。
  - focused Playwright `--grep "artifact lifecycle ledger"`：desktop/mobile 2/2 passed。
  - affected `npm run test:e2e`：desktop/mobile 20/20 passed（1.2m），console/page errors 为 0。
  - Console Maven smoke：`RuntimeConsoleAutoConfigurationTest` 4 tests、`RuntimeConsoleHttpSmokeTest` 2 tests，
    共 6/6 passed；最终 15-module reactor `BUILD SUCCESS`。
  - `mvn -B -ntp -pl foggy-mcp-launcher -am -Pruntime-console -Dmaven.test.skip=true package`：修复后最终
    29-module reactor `BUILD SUCCESS`，Console clean frontend lifecycle 再次得到 38/38 unit 和 successful build。
- manual_or_experience_evidence:
  - Playwright screenshots：
    `addons/foggy-runtime-console/frontend/test-results/runtime-console-artifact-l-cf6f6-erable-across-health-states-desktop-chromium/artifact-lifecycle-desktop.png`
    与对应 mobile project screenshot。
  - 当前隔离实例：`http://127.0.0.1:18117/console/`；token file：
    `/tmp/foggy-runtime-console-acceptance.oMkmlY/runtime-auth-code.txt`。token 未进入命令输出、文档、截图或日志。
  - 隔离实例 `/console/` 200 且 CSP 存在；capabilities/lifecycle 匿名均 401、带隔离 token 均 200；capability
    为 `supported`，清空 state 的 lifecycle 为 `NOT_INITIALIZED`、0 objects、0 blocked。
  - 真实 Chromium 完成 login→overview→lifecycle，核验 Runtime-global、候选安全提示与空 ledger，browser errors 0；
    live screenshot 位于隔离 state 的 `live-artifact-lifecycle.png`。
- deviations:
  - 首次 launcher package 后的隔离启动暴露 Runtime API 自动配置漏装既有 publication/lifecycle bean；未绕过。
    该问题由独立 BUG spec 修复并补 focused contract 后，执行了第二次必要 package。超过原单次 package 预算，
    但这是获得真实可启动候选的必要升级，未扩大业务行为或测试矩阵。
- residual_risks:
  - v1 inventory 仍是单 Runtime 同步完整 payload；大对象分页/virtualization 与历史趋势在本版本范围外。
  - 当前验证宿主缺少 CJK 字体，截图中文使用缺字 fallback；DOM、accessible name、布局与交互断言均通过，
    产品继续依赖客户端系统 CJK font stack，未新增字体依赖。
  - Vite aggregate vendor chunk 大于 500 kB 的既有非阻断 warning 仍存在。
- reused_evidence:
  - 复用 accepted management auth/session/shell 与 lifecycle inventory API 证据；当前 full Console E2E、Maven smoke、
    packaged launcher HTTP/auth/lifecycle 和真实 browser 基于本候选重新覆盖了变更边界。
- omitted_validation_and_reason:
  - 未运行 DB matrix、Engine authority/replay/rehearsal/source-seal、external service、tag、release、push 或生产
    mutation；standard read-only Console 契约明确不要求，未触发大型链路升级条件。
  - 用户人工体验验收尚未给出；本事项只推进到 `READY_FOR_SIGNOFF`，未设置 `ACCEPTED`。
- readiness: READY_FOR_SIGNOFF

## References

- requirement / issue: 用户要求继续推进到可验收 Console。
- architecture / glossary: `docs/design/runtime-console-product-charter.md`、
  `docs/architecture/runtime-and-model-lifecycle.md`
- related work items:
  - `docs/9.5/9.5.5/workitems/FEATURE-runtime-artifact-lifecycle-inventory.md`
  - `docs/9.5/9.5.5/workitems/BUG-runtime-published-store-interrupted-write-recovery.md`
