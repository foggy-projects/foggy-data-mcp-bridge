---
doc_type: delivery-spec
delivery_type: bug
version: 9.5.0
ticket: launcher-smoke-sqlite-fixture-path
status: ACCEPTED
canonical: true
execution_mode: ultra
assurance_level: standard
approved_by: repository-owner-via-approved-v950-legacy-exit-scope
approved_at: 2026-07-24
open_questions: []
---

# Delivery Spec: 9.5.0 Launcher Smoke SQLite Fixture Path

## Document Purpose

- intended_for: ultra-implementation / independent-signoff
- purpose: 冻结 9.5.0 authority 发现的 launcher smoke 测试夹具路径漏改及最小修复边界。
- canonical_path: `docs/9.5.0/workitems/BUG-launcher-smoke-sqlite-fixture-path.md`

## Goal

- version_goal: 支撑 9.5.0 model legacy exit 的 launcher/context 验证。
- target_outcome: `DataViewerApiSmokeTest` 从新 engine 模块定位 SQLite 夹具，并在 reactor 与模块目录执行方式下通过。
- critical_outcomes: 不再访问已删除的 `foggy-dataset-model` 测试资源路径；不改变产品代码、查询语义或运行时配置。
- success_is_sufficient_when: 受影响 smoke 测试通过，launcher 后续制品构建与尾部 reactor 模块完成。

## Scope

- in_scope: 更新 launcher smoke 测试的 SQLite fixture 候选路径；记录复现、修复和重验证结果。
- affected_modules: `foggy-mcp-launcher`；尾部依赖验证涉及 client/coverage 聚合模块。
- external_dependencies: none.

## Non-Goals

- out_of_scope: 产品功能、数据库 schema/data、SPI/API、历史 sealed `scripts/v934/**`、完整 authority 重跑。
- do_not_touch: 原始脏工作区；9.3.5 用户内容；生产 datasource/namespace/security 配置。
- non_blocking_or_waivable_items: none.

## Confirmed Decisions

| Decision | Rationale | Compatibility / Constraint |
|---|---|---|
| 将 fixture module path 从 `foggy-dataset-model` 改为 `foggy-dataset-model-engine` | 9.5.0 已完成模块物理改名，真实夹具仅存在于 engine | test-only；不得增加兼容目录或复制夹具 |
| 只重跑受影响测试和 authority 尾部 | 前 28 个模块已在同一候选代码上通过；test-only 路径修复不改变其产品证据 | 不自动重启已批准的一次完整 authority |

## Acceptance Criteria

- [ ] AC-1: launcher smoke 测试不再引用已删除的旧模块 fixture path。
- [ ] AC-2: `DataViewerApiSmokeTest` 实际通过。
- [ ] AC-3: launcher 打包、client test-compile 与 coverage 聚合尾部实际完成；制品边界可检查。
- [ ] AC-4: 未修改产品代码、API/SPI、数据、安全、CI 或历史 sealed 证据。

## Contract / Data / Security Constraints

- API or event contract: no change.
- data and migration: no DDL/DML or migration.
- compatibility and rollback: revert this test-only commit.
- permissions and secrets: no change.

## Test and Evidence Obligations

| Item | Classification | Risk | Required Validation | Reusable Evidence | Required Evidence |
|---|---|---|---|---|---|
| AC-1/2 | must-pass | major | focused launcher smoke test | authority failure reproduces stale path | exact Maven result |
| AC-3 | must-pass | major | resume reactor from launcher with upstream artifacts already built | authority modules 1-28 success | reactor tail summary and JAR inspection |
| AC-4 | must-pass | minor | diff/static review | fixed 9.5.0 candidate review | changed-path inventory |

## Validation Budget and Evidence Sufficiency

- assurance_level: standard
- lightweight_validation: focused test and static diff, expected `<5m`.
- medium_validation: resume `verify` from launcher through reactor tail, expected `5-30m`.
- expensive_validation: none.
- large_authority_or_replay_policy: prohibited-unless-user-approved
- full_chain_recommendation_trigger: none; the approved one-time authority already ran and exposed this test-only defect.
- estimated_full_chain_wall_clock: not-applicable.
- full_chain_prerequisites: none.
- user_approval_status: approved for the original lean authority; no second full run authorized or required.
- decision_if_not_approved: proceed with focused and affected tail validation.
- expensive_validation_trigger: none.
- maximum_expensive_attempts: zero additional full authority attempts.
- reusable_evidence: unchanged modules 1-28 from the 2026-07-24 root `clean verify -DskipITs`.
- stop_when_evidence_is_sufficient: focused smoke and resumed reactor tail pass, static/product diff remains test-only.
- validation_not_required: full clean reactor rerun, integration DB matrix, replay, source-seal, `mvn install`, CI/tag/release/publish.

## Waiver Policy

- waivable_items: none for AC-1/2.
- authorized_role: repository owner.
- non_waivable_guards: no product/API/SPI/data/security change; focused smoke must pass.
- required_risk_record: any tail failure must be classified and cannot be written as passed.

## Bug Context

- bug_source: acceptance-found
- severity: major
- environment: final 9.5.0 candidate `954539716c4e657f78d75a0ac8c8299644fea2be`, root Maven reactor.
- current_behavior: launcher `DataViewerApiSmokeTest` fails Spring context initialization because it searches only the deleted `foggy-dataset-model/src/test/resources/sqlite` path.
- expected_behavior: it locates `foggy-dataset-model-engine/src/test/resources/sqlite` from root or launcher module working directory.
- reproduction_steps: run `mvn -B -ntp clean verify -DskipITs`; observe launcher error after modules 1-28 succeed.
- reproduction_status: confirmed
- existing_evidence: `/tmp/foggy-v950-lean-authority-JLgiNx/root-clean-verify.log` and launcher Surefire XML.
- existing_tests: `DataViewerApiSmokeTest`.
- regression_protection: required; the existing smoke test is the long-term regression.
- waiver_reason_and_risk: none.

## Risks and Open Questions

- known_risks: Maven `clean` removes two historically tracked `target/**` manifests; they must remain restored and unchanged.
- open_questions: none

## Ultra Execution Contract

- 先读取本文件和 9.5.0 canonical feature spec。
- 只修改 test fixture path 和本 BUG 结果记录。
- 如需改变产品、目标、兼容或安全边界，设置 `NEEDS_REPLAN` 并停止。
- 运行 focused smoke，再从 launcher 恢复 reactor 尾部；不得重跑完整 authority。
- 完成后填写 `Implementation Result` 并设为 `READY_FOR_SIGNOFF`，不得自行设置 `ACCEPTED`。

## Implementation Result

- implementation_summary:
  launcher smoke 的 SQLite fixture 候选路径已从已删除的 `foggy-dataset-model` 更新到
  `foggy-dataset-model-engine`；仅修改测试代码，不改变产品、API/SPI、数据或安全行为。
- changed_paths:
  `foggy-mcp-launcher/src/test/java/com/foggyframework/mcp/launcher/DataViewerApiSmokeTest.java`
  和本 BUG 交付记录。
- tests_and_results:
  - focused smoke：
    `mvn -B -ntp -pl foggy-mcp-launcher -am test -DskipITs
    -Dtest=DataViewerApiSmokeTest -Dsurefire.failIfNoSpecifiedTests=false
    -Dsurefire.failIfNoTests=false`
    — BUILD SUCCESS，27/27 reactor modules success；
    `DataViewerApiSmokeTest` 6 tests，0 failures/errors/skips，9.018 秒。
  - 完整制品尾部：
    `mvn -B -ntp verify -Dmaven.test.skip=true -DskipITs
    -Dsurefire.failIfNoTests=false -Dfailsafe.failIfNoTests=false`
    — BUILD SUCCESS，32/32 reactor modules success，1 分 59 秒；launcher fat JAR、
    FSScript client、dataset client 和 coverage aggregator 全部完成。
- manual_or_experience_evidence:
  活动源码/测试资源中旧 fixture path 精确计数为 0；launcher JAR manifest、nested engine identity
  和旧聚合/TCK 排除边界复核通过。
- deviations:
  首次尾部命令使用 `-DskipTests` 时，仓库插件属性组合仍进入了 Surefire，并在无测试模块触发
  `No tests to run`；这是命令属性选择偏差，不是候选缺陷。改用
  `-Dmaven.test.skip=true` 后 32/32 制品构建通过。
- residual_risks:
  未再次执行完整 root `clean verify`；修复是测试夹具路径更新，按一次完整 authority 预算只重验
  受影响 smoke 和完整制品尾部。
- reused_evidence:
  2026-07-24 root authority 在原候选上完成的前 28 个模块：
  4960 tests，0 failures，除 launcher fixture error 外无产品 error，engine 有 2 个既有 skipped；
  其中 modules 1-28 全部 SUCCESS。
- omitted_validation_and_reason:
  未重跑完整 authority、数据库矩阵、semantic/portable replay、source-seal、Step 5/7、CI、
  tag/release/publish；均超出本 test-only 修复和 owner 批准的一次 lean authority 预算。
- readiness:
  `READY_FOR_SIGNOFF`；AC-1 至 AC-4 已由 focused smoke、32-module artifact verify、静态检查和
  changed-path review 覆盖。

## References

- requirement / issue: 9.5.0 lean authority failure on 2026-07-24.
- architecture / glossary: `docs/9.5.0/workitems/FEATURE-v950-legacy-exit.md`
- related work items: `docs/9.5.0/workitems/FEATURE-v950-legacy-exit.md`

## Acceptance Status

- acceptance_status: signed-off
- acceptance_decision: accepted-with-risks
- signed_off_by: codex-reviewer
- signed_off_at: 2026-07-24
- acceptance_record: `docs/9.5.0/acceptance/version-signoff.md`
- blocking_items: none
- follow_up_required: covered-by-version-residual-risks
