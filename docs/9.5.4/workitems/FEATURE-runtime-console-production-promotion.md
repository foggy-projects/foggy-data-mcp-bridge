---
doc_type: delivery-spec
delivery_type: feature
version: 9.5.4
ticket: FEATURE-runtime-console-production-promotion
status: ACCEPTED
canonical: true
execution_mode: ultra
assurance_level: elevated
approved_by: user
approved_at: 2026-08-01
open_questions: []
---

# Delivery Spec: Runtime Console Production Promotion

## Goal

- target_outcome: Console 忠实接入已验收 release package API，在开发 Runtime 导出，在 production-enabled
  Runtime 导入、重验、apply、一步 rollback/恢复；不建设跨 Runtime 控制面。
- critical_outcomes: package 不持久化到浏览器；所有 destructive action exact confirmation；imported candidate
  immutable；零普通 publish/live fallback；desktop/mobile 证据完整。
- success_is_sufficient_when: AC-1–8 与 Console typecheck/unit/build/focused/full Playwright 全绿，API 已先签收。

## Scope

- in_scope: capability-aware export/download JSON、local file import、target Bundle selection、release provenance、
  production validate/query、apply/rollback/recovery panel、unit/Playwright/docs。
- affected_modules: `addons/foggy-runtime-console/frontend`、`docs/9.5.4`。
- external_dependencies: existing Vue/Element Plus/Axios/Vitest/Playwright only。

## Non-Goals

- out_of_scope: package registry/upload service、cross-Runtime connection、signing、Git、approval/RBAC、history UI、
  arbitrary rollback、JAR/Agent/VS Code。
- do_not_touch: Runtime/Engine/launcher/POM/dependencies after API signoff。
- non_blocking_or_waivable_items: drag/drop polish、history timeline、automatic polling。

## Acceptance Criteria

- [ ] AC-1: capability 明确区分 export 与 production import/apply/rollback；disabled 时解释且零越权请求。
- [ ] AC-2: export confirmation pin exact workspace/candidate，下载安全 filename/JSON，不写 localStorage/log/URL。
- [ ] AC-3: import 只读本地 JSON，预览 schema/package/source/candidate/resources/validation/trust boundary，明确选择
  target Namespace/eligible Bundle 后发送；不自动 apply。
- [ ] AC-4: imported workspace 显示 release provenance，save/delete disabled；生产 validate/query 沿用现有 exact flow。
- [ ] AC-5: apply confirmation 展示 package/candidate/target base/source/生产影响，只调用 promote route且零 retry/fallback。
- [ ] AC-6: PUBLISHED 展示 apply evidence；rollback 只 pin package/candidate/attempt/direct previous base，成功显示
  `ROLLED_BACK`，不描述为任意 history。
- [ ] AC-7: `ROLLING_BACK`/`ROLLBACK_REQUIRED` 所有其他 mutation disabled；显式 refresh/recover，错误安全显示。
- [ ] AC-8: typecheck/unit/build、desktop/mobile focused/full Playwright、diff/untracked checks 通过；仅批准路径改动。

## Validation Budget and Evidence Sufficiency

- assurance_level: elevated；UI 触发生产 apply/rollback，但服务端强 guard 已独立验收。
- lightweight_validation: typecheck/unit/diff/source review。
- medium_validation: build、focused + full desktop/mobile Playwright。
- expensive_validation: none。
- large_authority_or_replay_policy: prohibited-unless-user-approved
- full_chain_recommendation_trigger: none
- user_approval_status: not-requested
- maximum_expensive_attempts: 0
- reusable_evidence: accepted API exact/auth/transaction evidence、9.5.3 Console authoring flow。
- stop_when_evidence_is_sufficient: AC-1–8 current frontend evidence 绿且 zero fallback review 通过。
- validation_not_required: Maven、launcher、DB、authority/release/real production。

## Waiver Policy

- waivable_items: drag/drop/history/polling polish。
- authorized_role: product owner
- non_waivable_guards: capability、exact package/revision/attempt、confirmation、immutable import、no retry/fallback、secret。
- required_risk_record: 不得 waiver 错 target、未验证 apply、自动 rollback 或 secret leakage。

## Risks and Open Questions

- known_risks: local file transfer/retention 由操作者负责；Console 不证明 package signer identity。
- open_questions: none

## Ultra Execution Contract

- 仅在 API canonical spec `ACCEPTED` 后进入 `ULTRA_EXECUTING`。
- 使用 frontend-design 与 webapp-testing；延续现有 authoring UI，不新增依赖或第二份 workspace head。
- 完成后填写 Implementation Result 并改 `READY_FOR_SIGNOFF`。

## Implementation Result

- implementation_summary: 已在现有 `AuthoringWorkspace.vue` authoritative head 接入 capability-aware release
  package download、本地 JSON preview/target import、imported provenance 与 immutable editing guard、生产
  validate/query、exact apply、一步 rollback 和 pinned rollback recovery；未建设第二套 workspace state。
- changed_paths: `addons/foggy-runtime-console/frontend` authoring types/policy/composable/component、Namespace
  capability loading、unit/Playwright tests，以及本 workitem/README。未修改 API/Engine/launcher/POM/npm 依赖。
- tests_and_results:
  - `npm run typecheck`：通过。
  - `npm run test:unit`：10 files / 34 tests，全部通过。
  - `npm run build`：通过；仅保留既有第三方 PURE annotation 与 chunk-size warnings。
  - `npx playwright test --grep 'authoring workspace'`：desktop/mobile 10/10 通过。
  - `npx playwright test`：desktop/mobile 18/18 通过。
  - tracked `git diff --check` 与全部 untracked 逐文件 no-index whitespace check 通过。
- manual_or_experience_evidence: Playwright 实际下载/读取 JSON、上传本地 package、确认 target/provenance、
  禁用 imported 编辑、执行生产 validate/query/apply、呈现 `ROLLBACK_REQUIRED` 并 pinned recover、最终一步
  `ROLLED_BACK`；断言未调用普通 publish/save，localStorage/URL 不含 package identity。
- deviations: 无。
- residual_risks: 本地文件保管由操作者负责；Console 不证明 signer identity；drag/drop、history timeline 和
  automatic polling 仍为明确 non-goal。
- reused_evidence: 复用已验收 Runtime API exact/auth/rollback evidence与 9.5.3 Console authoring workspace、
  candidate query、publication/recovery flow；新增 desktop/mobile 场景覆盖 production promotion 增量。
- omitted_validation_and_reason: 按 elevated spec 不运行 Maven、launcher、数据库、authority/release 或真实生产
  Runtime；API 已独立验收，Console focused/full browser evidence 已达到停止条件。
- readiness: READY_FOR_SIGNOFF

## References

- related work items:
  - `docs/9.5.4/workitems/FEATURE-runtime-release-package-production-promotion-api.md`
  - `docs/9.5.3/workitems/FEATURE-runtime-console-authoring-publish-recovery.md`

## Acceptance Status

- acceptance_status: signed-off
- acceptance_decision: accepted
- signed_off_by: Codex independent reviewer
- signed_off_at: 2026-08-01
- acceptance_record: `docs/9.5.4/acceptance/FEATURE-runtime-console-production-promotion-signoff.md`
- blocking_items: none
- follow_up_required: no
