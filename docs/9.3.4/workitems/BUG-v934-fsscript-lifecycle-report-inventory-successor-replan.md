---
doc_type: delivery-spec
delivery_type: bug
version: 9.3.4
ticket: BUG-V934-FSSCRIPT-LIFECYCLE-REPORT-INVENTORY-SUCCESSOR-REPLAN
status: NEEDS_REPLAN
canonical: true
execution_mode: ultra
approved_by: project-owner-via-confirmed-full-authority-replan
approved_at: 2026-07-22
controlling_item: FEATURE-V934-RELEASE-AUTHORITY-AND-CI
predecessors:
  - BUG-V934-FSSCRIPT-STEP4-TAKEOVER-REPLAN
  - BUG-FSSCRIPT-SHARED-DEFAULT-SPRING-DESTROY
open_questions: []
---

# Delivery Spec: FSScript lifecycle report-inventory successor replan

## Document Purpose

- intended_for: Ultra implementation and independent release-authority signoff.
- purpose: 将已签收并实际通过的 FSScript Spring lifecycle regression 纳入 Step 4
  run-owned Step2 derived report view，修复 r5 的 `E_EXTRA_REPORT`，随后只执行一次新的
  fresh all-lane authority。
- canonical_path:
  `docs/9.3.4/workitems/BUG-v934-fsscript-lifecycle-report-inventory-successor-replan.md`

## Goal

- version_goal: 让 v9.3.4 release-authority 的冻结报告集合与 PR #124 当前测试源码一致，
  同时保留 exact inventory fail-closed、coverage floor 和既有历史 Step2 parent authority。
- target_outcome: `DefaultExpFactorySpringLifecycleTest` 作为一个 2-testcase Surefire Unit
  positive report 被 Step4 derived view 接纳；全部 direct/transitive contracts 闭合，并从
  唯一新 Cdiag 完成 fresh diagnostic 及后续 Step4-7 收口。

## Scope

- in_scope:
  - 保持 `scripts/v934/successor/step2` 为 immutable historical parent，不重写其 724/59/5205
    冻结库存；
  - 在 `scripts/v934/step4/coverage-report-amendment.tsv` 增加唯一 lifecycle test
    `new-positive-report`，绑定源码路径、module、FQCN、Surefire Unit、当前源码 SHA-256、
    exact 2 testcase 和已签收 FSScript work item；
  - 将 amendment 从 12 rows / 4 new + 8 changed / testcase delta 56 更新为
    13 rows / 5 new + 8 changed / testcase delta 58；
  - 将 Step4 run-owned derived Step2 view 从 `728+59/5261` 更新为 `729+59/5263`，其中
    Surefire 从 `681+55/4941` 更新为 `682+55/4943`，Failsafe 保持 `47+4/320`；
  - 将 required aggregate 从 `773+59/5707` 更新为 `774+59/5709`；Step3 required
    `45/446`、Addon companion `2/6`、exec/session `23/48` 和 coverage floor 保持不变；
  - 同步 report-view、Unit fixture、report inventory、coverage XML/tool、outer runner、
    Step4/5/6 contract 与 CI/package 的所有直接及传递 hash/count bindings；
  - 完成低成本静态/负向/focused 验证后，创建唯一单父、单路径、两字段 activation Cdiag，
    从 fresh non-shallow clone 执行一次新 diagnostic；成功才按既有治理完成
    candidate/capsule review、direct-child Cfreeze、formal、Step5-7。
- affected_modules:
  - `scripts/v934/step4` 及 Step4 outer runner；
  - 依赖 Step4 identity/totals 的 `scripts/v934/step5`、`scripts/v934/step6` 和 required CI；
  - `docs/9.3.4` workitem/evidence/acceptance 状态。
- external_dependencies: GitHub PR #124、GitHub Actions、Maven、Docker/Compose 和既有五数据库
  authority 环境；不发布制品、不部署业务环境。

## Non-Goals

- out_of_scope:
  - 修改 FSScript 生产代码、生命周期测试源码、测试名称、测试节点或已签收方案 A 语义；
  - 改写历史 Step2 successor parent inventory、旧 r5 run root 或任何已封存 evidence；
  - 降低 coverage floor、扩大 exclusion、改变 selector、fork、skip、测试顺序、DB set 或
    report exact-set fail-closed policy；
  - 复用 r5 partial Unit lane、Cdiag、run ID、candidate、capsule 或 authority；
  - 方案 B/C、public API/SPI、Spring Bean contract、POM 或 v9.3.5/9.4.0 工作。
- do_not_touch:
  - 原工作区 `docs/9.3.5/README.md` 和 `docs/9.3.5/workitems/`；
  - `foggy-fsscript/src/main`、`foggy-fsscript/src/test` 和 launcher 产品/测试字节；
  - historical Step2 parent artifacts under `scripts/v934/successor/step2`；
  - 未知 host 资源、凭证、endpoint、业务数据和非 run-owned 容器/卷/网络。

## Confirmed Decisions

| Decision | Rationale | Compatibility / Constraint |
|---|---|---|
| 使用 Step4 report amendment，而非改写 Step2 parent | lifecycle test 在冻结 Step2 parent 后由已签收修复新增；现有 derived-view 层就是接纳 reviewed new reports 的兼容机制 | parent 的 724/59/5205 identity 与历史 authority 保持不变 |
| 精确接受 1 report / 2 testcase | r5 XML 和源码均证明该类只含两个 JUnit testcase；不得用 wildcard 或放宽 unexpected-extra | report FQCN、source hash、module、runner、variant 和 cardinality 全部冻结 |
| required totals 只增加 1/2 | Failsafe、Step3、Addon 和 structural inventory 均未变化 | 新 totals 为 774 positive / 59 structural / 5709 testcase |
| 保留 full fresh authority | report inventory 是 authority 输入，r5 partial lane 不可拼接 | focused 绿后只运行一次新 all-lane diagnostic；失败即 replan |
| 不重复修改产品 | FSScript fix 和 lifecycle regression 已独立签收，当前缺陷是治理库存未跟随新增测试 | 不产生 API/SPI、产品语义、POM、测试顺序或 coverage policy 变化 |

## Acceptance Criteria

- [ ] AC-1: Cplan 为 clean/pushed `APPROVED` 文档提交；其 parent 是 r5 fail-closed 记录
  `c115aab415c0d71684d27f47dff4fb821e09c5dd`，原工作区未改。
- [ ] AC-2: Step4 amendment 精确为 13 unique rows，新增 lifecycle row 绑定源码 SHA-256
  `d068e4bd96471fea2eddbe74a65f138d5aaf957b57f08b433c4f88a45635ad80`、Surefire Unit、
  2 testcase 和 FSScript signed-off work item；parent inventory 字节不变。
- [ ] AC-3: derived view 精确为 `729+59/5263`，Surefire `682+55/4943`、Failsafe
  `47+4/320`；unexpected/missing/duplicate/cardinality/source drift 继续 fail closed。
- [ ] AC-4: required inventory 精确为 `774+59/5709/F0E0S0`，Addon=`2/6`、
  Step3=`45/446`、exec/session=`23/48`；coverage floor、critical set、exclusion 不变。
- [ ] AC-5: Step4/5/6 manifests、report/coverage/Unit fixture tools、outer output、package/CI
  contracts 和 nested digest bindings 全部闭合；原负向测试数量及语义不减少。
- [ ] AC-6: 唯一 activation Cdiag 是 diagnostic-ready integration commit 的直接单父子；
  diff 只包含本文件 `status/readiness: APPROVED -> ULTRA_EXECUTING`，fresh clone 精确检出。
- [ ] AC-7: 唯一新 diagnostic 完成全部 required lanes、source before/after、coverage、
  sensitive、cleanup、candidate/capsule 和独立复核；所有 report/testcase F/E/S 均为 0。
- [ ] AC-8: 成功后 direct-child Cfreeze、fresh formal、Step5 package/release gate、Step6
  required CI 和 Step7 signoff 按现有顺序完成；PR #124 required checks 绿色。
- [ ] AC-9: 最终 diff 不含产品源码、测试源码、POM、coverage floor/exclusion、selector、
  skip、fork 或测试顺序变化；不影响 public API/SPI。

## Contract / Data / Security Constraints

- API or event contract: 无产品 API/SPI、Spring Bean、配置、事件或 evidence schema 变化；
  仅扩展现有 Step4 report amendment 实例和既有 count/hash values。
- data and migration: 无数据库 schema 或业务数据迁移；仅 run-owned fixture/evidence。
- compatibility and rollback: inventory integration commit 可独立 revert；回滚会恢复已证明的
  `E_EXTRA_REPORT`，不得通过删除 lifecycle test 或放宽 verifier 回滚。
- permissions and secrets: 使用已授权的 v934 runner、evidence 和 PR #124 范围；持久记录
  不保存 raw output、凭证、host path 或资源 identity。

## Test and Evidence Obligations

| Item | Risk | Required Validation | Required Evidence |
|---|---|---|---|
| AC-1..AC-2 | blocker | Git topology、changed-path、parent artifact hash、amendment static validation | exact Cplan/integration SHA、13-row tuple、parent unchanged proof |
| AC-3 | critical | derived-view generate/validate/negative、Step2 report verifier focused fixture | 729/59/5263、682/55/4943 和 negative counts |
| AC-4..AC-5 | blocker | coverage/report inventory、Unit fixture、Step4/5/6 manifests、package/CI static/negative | exact totals、digest closure、所有 suites exit 0 |
| AC-6 | blocker | Cdiag parent/path/two-field diff、fresh-clone preflight | exact Cint/Cdiag SHA 和 clean/non-shallow proof |
| AC-7 | blocker | 一次完整 fresh Step4 diagnostic | run ID、lane counts、coverage/candidate/capsule/cleanup receipts |
| AC-8 | blocker | Cfreeze/formal/Step5-7 和 GitHub required checks | commit/run IDs、verdict、PR check summary |
| AC-9 | critical | final changed-path/API/POM/policy audit | protected-path diff 和 compatibility statement |

验证成本与循环控制：

- `<5m`：JSON/TSV schema、amendment/view validators、manifest/hash、Git topology、静态扫描；
  每次相关 contract 字节变化后重跑。
- `5-30m`：derived-view negatives、report inventory/coverage/Unit fixture/package/CI negative
  suites；在字节冻结后各运行一次。
- `>60m`：fresh Step4 diagnostic、formal 和后续完整 release-authority；每个新 governed run ID
  最多一次。只有源码、tooling、policy、selector、环境假设或产物绑定变化才使对应证据失效。
- r5 的 FSScript product correctness/signoff 证据可作为变更边界证据，但不能替代新的 full
  authority；r5 partial Unit lane、report manifest 和 Cdiag 不可复用。
- 任一新昂贵运行失败均设置本事项 `NEEDS_REPLAN` 并停止；不得自动 retry、拼接 partial
  lane、删除测试或降低门槛。

## Bug Context

- bug_source: acceptance-found
- severity: critical
- environment: PR #124；Cdiag `54c774e636e33971b6cae612b8b89fb1acdb33c6`；run
  `step4-v934-fsscript-amendment-diagnostic-20260722-r5`。
- current_behavior: Unit Maven tests 全部成功后，canonical report verifier 以
  `E_EXTRA_REPORT` 拒绝 `DefaultExpFactorySpringLifecycleTest`，因为 Step4 derived report
  amendment 尚未纳入该新增 report。
- expected_behavior: derived view 精确声明该 report 和两个 testcase，并继续拒绝任何未声明
  extra、missing、duplicate、source drift 或 cardinality drift。
- reproduction_steps:
  1. 从 r5 Cdiag fresh clone 执行唯一 Step4 diagnostic；
  2. Unit Surefire 生成 lifecycle report，测试 F/E/S 为 0；
  3. `step2_report_tool.py verify` 对 run-owned derived view 返回
     `E_EXTRA_REPORT` 和该唯一 FQCN。
- reproduction_status: confirmed
- existing_evidence:
  `docs/9.3.4/evidence/step-4/step4-v934-fsscript-amendment-diagnostic-20260722-r5-report-inventory-fail-closed.md`。
- existing_tests: lifecycle 2/2；FSScript 403/403 normal/reverse；launcher 7/7、6/6、20/20；
  r5 Unit XML 无 F/E/S，但 partial lane 不可复用。
- regression_protection: required；保留 lifecycle test 和 exact report inventory verifier。
- waiver_reason_and_risk: N/A。

## Risks and Open Questions

- known_risks:
  - report counts 在 derived view、fixture、aggregate、outer output、package/CI 和 manifests 间
    多层绑定，漏更新任一层会在 focused/preflight fail closed；
  - 将 row 错加到 immutable Step2 parent 会破坏历史 authority；只能使用 Step4 amendment；
  - 新 all-lane run 仍受 Docker/端口/runner image 影响，环境失败也必须按一次性规则 replan；
  - GitHub required CI 可能暴露本地 runner 与 hosted runner 差异，仍需据实停止。
- open_questions: none

## Ultra Execution Contract

- 先读取本文件、根 `CLAUDE.md`、r5 fail-closed evidence、FSScript signoff、Step4 derived-view/
  report/coverage/Unit fixture contracts 和 Step4/5/6 manifests。
- 先提交本 `APPROVED` Cplan；它不授权 runner。随后只允许一个 diagnostic-ready integration
  commit，完成第 13 条 amendment、count/hash closure 和 focused validation。
- integration clean/pushed 后创建唯一 activation Cdiag；其 diff 只能是本文件两字段变化。
- 只从 Cdiag fresh non-shallow clone 启动一次新 run；遵守既有 foreground、ownership、
  privacy、source-seal、cleanup、candidate/capsule 和 direct-child Cfreeze 规则。
- 如需修改产品/测试字节、public API/SPI、POM、coverage/selector/DB set、安全边界，或昂贵
  运行失败，将本文件设为 `NEEDS_REPLAN` 并停止。
- 完成后填写 `Implementation Result` 并设为 `READY_FOR_SIGNOFF`；不得自行设置
  `ACCEPTED`，最终签收必须独立完成。

## Implementation Result

> Diagnostic-ready integration 已完成，但唯一 fresh diagnostic 在 outer-runner source-seal
> preflight fail closed；本事项不可重试并转入 successor replan。

- implementation_summary: 在 Step4 report amendment 中精确加入
  `DefaultExpFactorySpringLifecycleTest` 的 1 report / 2 testcase，并将 derived Step2、Unit
  fixture、required aggregate、Step5 portable replay、Step6 CI 及所有传递 digest 绑定更新为
  `729+59/5263`、`682+55/4943` 和 `774+59/5709`；未改历史 Step2 parent、产品或测试字节。
- changed_paths:
  - `scripts/v934/step4/{coverage-report-amendment.tsv,step2-report-view-contract.json,step2_report_view_tool.py,unit-mysql57-fixture-contract.json,unit_mysql_fixture_tool.py,coverage-contract.json,coverage_tool.py,coverage_xml_tool.py,report_inventory_tool.py,SHA256SUMS}`；
  - `scripts/v934/step4/successor/{overlay-contract.json,overlay_tool.py,SHA256SUMS}` 和
    `scripts/verify-v934-step4-coverage.sh`；
  - `scripts/v934/step5/{portable_replay_tool.py,SHA256SUMS}`；
  - `scripts/v934/step6/{ci-contract.json,ci_contract_tool.py,SHA256SUMS}`；
  - 本交付契约的 Implementation Result。
- tests_and_results:
  - reactor `mvn -DskipTests test-compile`：26/26 modules BUILD SUCCESS；
  - derived Step2 parent/generate/validate：`729+59/5263`；negative 12/12；
  - coverage contract、successor overlay、Step4/5/6 manifests、CI workflows：全部 PASS；
  - coverage contract negative 28/28、coverage XML fast negative 130/130、report inventory
    synthetic negative 30/30、Unit fixture negative 42/42、Unit MySQL lifecycle negative 5/5；
  - package negative 117/117、CI negative 86/86；Python/JSON、`git diff --check` PASS。
- manual_or_experience_evidence: focused 输出位于执行机临时目录
  `/tmp/v934-fsscript-successor-focused/`；derived view 精确观测到 729 positive、59 structural、
  5263 testcase，aggregate contract 精确观测到 774 positive、59 structural、5709 testcase；
  唯一 Cdiag `b8be71ae` 的 runner invocation 在创建 run root 前以
  `E_SOURCE_SEAL_BINDING` 退出，详见 r6 fail-closed record。
- deviations: none
- residual_risks: `run_log_lifecycle_negative_test.sh` 内嵌 outer runner SHA 仍绑定旧字节；
  当前 Cdiag/run ID 已永久排除。必须新建 approved successor replan、integration、Cdiag 和
  run ID 后才能再次进入 diagnostic。
- readiness: NEEDS_REPLAN

## References

- r5 predecessor:
  `docs/9.3.4/workitems/BUG-v934-fsscript-step4-takeover-replan.md`
- r5 fail-closed record:
  `docs/9.3.4/evidence/step-4/step4-v934-fsscript-amendment-diagnostic-20260722-r5-report-inventory-fail-closed.md`
- r6 source-seal preflight fail-closed record:
  `docs/9.3.4/evidence/step-4/step4-v934-fsscript-lifecycle-inventory-diagnostic-20260722-r6-source-seal-preflight-fail-closed.md`
- FSScript spec/signoff:
  `docs/9.3.4/workitems/BUG-fsscript-shared-default-spring-destroy.md` /
  `docs/9.3.4/acceptance/BUG-fsscript-shared-default-spring-destroy-signoff-20260722.md`
- controlling release item:
  `docs/9.3.4/workitems/FEATURE-v934-release-authority-and-ci.md`
