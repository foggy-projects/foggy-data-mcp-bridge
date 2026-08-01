---
doc_type: delivery-spec
delivery_type: bug
version: 9.5.5
ticket: BUG-runtime-api-auto-configuration-assembly
status: READY_FOR_SIGNOFF
canonical: true
execution_mode: ultra
assurance_level: standard
approved_by: user
approved_at: 2026-08-01
open_questions: []
---

# Delivery Spec: Runtime API 后续能力自动配置装配缺失

## Goal

- 修复 `runtime-console` profile 的真实 launcher 启动失败：显式 `RuntimeApiAutoConfiguration`
  必须装配已经交付的 publication、release package、published artifact 与 lifecycle inventory bean。
- 保持 API、持久化格式、权限、业务行为和依赖图不变，只修正 Spring Boot 自动配置注册清单。

## Scope

- in_scope:
  - 将当前 production controller/service 的缺失类型加入 `RuntimeApiAutoConfiguration` 显式 imports。
  - 扩充 outside-package auto-configuration contract，防止这些类型再次漏装。
  - 重跑 focused Runtime API contract、Console smoke、launcher package 与隔离 HTTP/lifecycle smoke。
- out_of_scope:
  - 修改 publication/lifecycle 业务语义、DTO、endpoint、权限或 store 格式。
  - cleanup/retention/mutation、依赖升级、真实 `.foggy-runtime` 或历史 state 迁移。
- affected_modules: `foggy-runtime-api`、`docs/9.5/9.5.5`；launcher 仅作为验证消费者。

## Acceptance Criteria

- [x] AC-1: `RuntimeAuthoringPublicationLock`、`RuntimePublishedBundleArtifactStore`、
  `RuntimeAuthoringWorkspacePublicationService`、`RuntimeAuthoringReleasePackageService`、
  `RuntimeArtifactLifecycleInventoryService` 与两个对应 controller 由自动配置显式装配。
- [x] AC-2: outside-package contract 精确核验上述 imports，focused test 通过。
- [x] AC-3: Runtime Console JUnit smoke 通过，实际 `runtime-console` launcher 在隔离 state 下启动，
  `/console/`、capabilities 与 lifecycle GET 可访问。
- [x] AC-4: 无 API/schema/store/权限/依赖或业务实现变更；完整 tracked/untracked audit 与
  `git diff --check` 通过。

## Validation and Safety

- assurance_level: standard；修复仅为装配清单，但真实启动为不可豁免证据。
- required_lanes: focused auto-configuration contract、Console 6-test smoke、incremental launcher package、
  隔离 HTTP/auth/lifecycle smoke。
- prohibited: authority/replay/full-chain、真实发布/恢复 mutation、真实 `.foggy-runtime`、push。
- stop_when_evidence_is_sufficient: AC-1～AC-4 全绿且隔离 Console 可交给用户验收。

## Implementation Result

- implementation_summary:
  - 将 5 个既有 publication/lifecycle service 与 2 个对应 controller 加入
    `RuntimeApiAutoConfiguration` 的显式 `@Import` graph，使 outside-package Spring Boot launcher 能实际装配。
  - 扩充既有 outside-package contract，逐类锁定 import 边界；未修改任何 service/controller 业务方法。
- changed_paths:
  - `foggy-runtime-api/src/main/java/com/foggyframework/runtime/api/RuntimeApiAutoConfiguration.java`
  - `foggy-runtime-api/src/test/java/io/foggytest/runtimeapi/RuntimeApiOutsidePackageAutoConfigurationContractTest.java`
  - `docs/9.5/9.5.5/{README.md,workitems/BUG-runtime-api-auto-configuration-assembly.md}`
- tests_and_results:
  - focused outside-package contract：1/1 passed。
  - Runtime API auto-config/auth/capability/lifecycle focused lane：4 classes、71/71 passed，14-module reactor
    `BUILD SUCCESS`。
  - Console auto-configuration/HTTP smoke：6/6 passed，15-module reactor `BUILD SUCCESS`。
  - 修复后 runtime-console launcher package：29-module reactor `BUILD SUCCESS`。
- manual_or_experience_evidence:
  - 修复前隔离启动 fail closed，明确报告缺失 `RuntimeAuthoringPublicationLock`；未绕过或伪造入口。
  - 修复后同一隔离边界成功启动；Console 200、capabilities/lifecycle anonymous 401、authenticated 200，
    lifecycle capability `supported`，真实 Chromium login→lifecycle passed 且 errors 0。
- deviations: none
- residual_risks:
  - 显式 import graph 仍要求未来新增 Runtime API stereotype bean 时同步更新 auto-configuration；本次 contract
    已覆盖当前后续能力，但未引入自动 component scan，以保持既有显式边界。
- omitted_validation_and_reason:
  - 未运行业务 mutation、数据库矩阵、authority/replay、release/tag/push；本修复不改变业务语义，focused
    71-test lane 与 packaged launcher evidence 已达到 standard assurance sufficiency。
- readiness: READY_FOR_SIGNOFF

## References

- discovered_by: `FEATURE-runtime-console-artifact-lifecycle-operability` 隔离 launcher handoff。
- root_cause: 自动配置采用显式 `@Import`，后续新增 bean 只有 stereotype annotation，未进入 import graph。
