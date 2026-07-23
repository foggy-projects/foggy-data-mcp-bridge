---
doc_type: delivery-spec
delivery_type: bug
version: 9.3.4
ticket: BUG-V934-FSSCRIPT-FORMAL-EXTERNAL-INTERRUPTION-RECOVERY-REPLAN
status: ULTRA_EXECUTING
canonical: true
execution_mode: ultra
approved_by: project-owner-via-explicit-recovery-authorization
approved_at: 2026-07-23
controlling_item: FEATURE-V934-RELEASE-AUTHORITY-AND-CI
predecessors:
  - BUG-V934-FSSCRIPT-LIFECYCLE-INVENTORY-DUAL-SEAL-SUCCESSOR-REPLAN
open_questions: []
---

# Delivery Spec: FSScript formal external-interruption recovery replan

## Document Purpose

- intended_for: ultra-implementation / v9.3.4 release-authority owner / independent signoff.
- purpose: 在不伪造新 threshold freeze、不复用中断运行证据的前提下，为 immutable Cfreeze
  `5c57e537603004d47be936f74e92f873de5fe431` 授权一次全新 Step4 formal execution；成功后按
  `FEATURE-v934-release-authority-and-ci.md` 顺序恢复 Step5、Step6、Step7。
- canonical_path:
  `docs/9.3.4/workitems/BUG-v934-fsscript-formal-external-interruption-recovery-replan.md`

## Goal

- version_goal: 关闭 formal r8 外部中断造成的 authority 缺口，同时保持 diagnostic observation、
  reviewed thresholds、formalization delta、产品与测试字节完全不变。
- target_outcome: 新 activation identity 被 clean/pushed 后，从 fresh non-shallow clone detached 到
  exact immutable Cfreeze，使用唯一新 run ID 完整执行 formal；只有 terminal PASS、source/cleanup/
  candidate/capsule 全闭合后，才恢复 Step5-7 的既有顺序执行。

## Scope

- in_scope:
  - 将本 APPROVED Cplan 作为 r8 failure record 后的唯一 recovery authority；
  - 创建只改变本文件 `status/readiness` 的单父 activation commit，并记录其 exact SHA；
  - 对 Cfreeze `5c57e537603004d47be936f74e92f873de5fe431` 重新验证 direct-single-parent
    formalization topology、clean/non-shallow/source seal、contract/manifest/threshold identity；
  - 在全新 disposable clone、全新 evidence root 和全新 run ID 下，从零执行所有 Step4 formal
    required lanes、数据库矩阵、coverage gate、model/sensitive/cleanup、candidate/capsule；
  - formal terminal PASS 后，将恢复运行与 activation/Cfreeze/run identity 写入 durable evidence，
    再按 controlling item 依次推进 Step5 package/release gate、Step6 required CI、Step7 version
    acceptance；
  - 任一阶段失败时只记录该阶段的 failed/excluded evidence，停止后续阶段并重新规划。
- affected_modules:
  - `docs/9.3.4` recovery governance/evidence；
  - 既有 `scripts/v934/step4`、Step5、Step6 runner 仅作为不可修改的执行输入。
- external_dependencies: GitHub PR #124、Maven、Docker/Compose、既有五数据库 authority 环境、
  GitHub required CI；不发布制品、不部署业务环境。

## Non-Goals

- out_of_scope:
  - 修改产品、测试、POM、runner、coverage threshold/exclusion、selector、fork、skip、测试顺序、
    report inventory、API/SPI、Bean 或 Spring 生命周期语义；
  - 创建虚假的新 Cdiag/Cfreeze，或对六个 formalization exact paths 制造无语义变更；
  - 复用、续跑、补写或拼接
    `step4-v934-fsscript-lifecycle-dual-seal-formal-20260722-r8` 的任何 runtime evidence；
  - 把 diagnostic r7、Cfreeze 或 focused PASS 冒充 formal terminal authority；
  - 扩大到方案 B/C、v9.3.5 或 v9.4.0。
- do_not_touch:
  - 原工作区 `docs/9.3.5/README.md` 和 `docs/9.3.5/workitems/`；
  - immutable Cfreeze 的 tracked bytes；
  - historical r8 run root、旧 evidence、未知 host 资源及非新 run-owned Docker/数据库资源。

## Confirmed Decisions

| Decision | Rationale | Compatibility / Constraint |
|---|---|---|
| reuse immutable Cfreeze input, never reuse r8 evidence | 外部中断未改变 source/tool/policy/threshold；formal gate又要求当前 HEAD 必须是 diagnostic 的 direct-single-parent child | fresh clone、fresh evidence root、new run ID；r8 永久 FAILED/EXCLUDED |
| activation is a governance commit, not a replacement Cfreeze | 在 Cfreeze 后追加文档会令 formal-delta gate拒绝；伪造六个 required path 变化更不正确 | activation SHA 必须 clean/pushed，并在前后 evidence 中双向绑定 exact Cfreeze/run ID |
| no new diagnostic | diagnostic r7 已 terminal PASS 且中断发生在 unchanged Cfreeze 的 formal execution environment | 若 source/tool/policy/selector/environment assumption 改变，则本决定失效并须 new Cdiag |
| one wholly fresh formal attempt | formal 是 >60m 高成本 authority；必须完整重跑而非从 sqlite-refresh 续接 | 新 run ID 最多一次；第二次非产品昂贵失败也必须 replan |
| Step5-7 only after terminal formal PASS | partial child PASS 不构成 outer authority | formal 任一 terminal receipt 缺失即停止，不能进入 Step5 |

## Acceptance Criteria

- [ ] AC-1: 本 Cplan 是 failure-record commit
  `351d68cfc4c48c8509ee1076b2b4fd46ad4236df` 的 clean/pushed 单父直接子提交；原工作区保持
  `9743f97d9d935d5e26311b78c158755bca51f17a` 与用户 v9.3.5 改动不变。
- [ ] AC-2: activation commit 是 Cplan 的 clean/pushed 单父直接子，diff 仅为本文件
  `status: APPROVED -> ULTRA_EXECUTING` 与 readiness activation 记录；activation SHA、Cfreeze SHA、
  新 run ID 在执行前 durable 绑定。
- [ ] AC-3: fresh clone preflight 证明 remote exact、clean、non-shallow、无 replace/graft、detached
  exact Cfreeze；formal delta 仍为 Cdiag `462d64ac...` 的 direct-single-parent child 与六个 exact
  formalization paths；source/contract/manifest/threshold digests 与 frozen record一致。
- [ ] AC-4: 不存在新 run ID 的旧目录、进程、端口或 run-owned Docker residue；r8 root 未被读取为
  执行输入；新 formal 仅由一个 foreground terminal-managed invocation 启动。
- [ ] AC-5: 新 formal 完成所有 required lanes，required report inventory 至少保持 reviewed
  `774 positive + 59 structural / 5709 testcases`，Addon 保持 `2/6`，所有 Failures/Errors/Skipped=0。
- [ ] AC-6: formal outer terminal chain 完整：integration-complete、source-after、summary/run-status、
  final coverage、critical floors、model、sensitive、cleanup、candidate、capsule 均 PASS，且没有
  process/container/database/port residue。
- [ ] AC-7: post-formal durable evidence 明确记录 activation/Cfreeze/run identity、terminal receipts、
  aggregate/coverage/cleanup 结果，并声明 r8 partial bytes 未复用；本 work item 达到
  `READY_FOR_SIGNOFF`，但不自行 `ACCEPTED`。
- [ ] AC-8: 仅 AC-1..AC-7 全部成功后，依据 controlling item 顺序推进 Step5、Step6、Step7；
  每阶段使用独立 run/evidence identity，任一失败立即 fail closed、停止后续阶段并交付 blocker。

## Contract / Data / Security Constraints

- API or event contract: 不修改 production API/SPI、Spring Bean、配置、事件、数据库或 evidence
  schema；本 recovery 只改变治理状态并产生新运行证据。
- data and migration: 无业务 schema/data migration；只允许新 run-owned fixture 和临时容器。
- compatibility and rollback: Cplan/activation 可作为治理记录保留；失败时不得回滚或抹除记录，
  只追加 fail-closed evidence。immutable Cfreeze 不变。
- permissions and secrets: 限于已授权 v934 runner、evidence 目录和 PR #124；Git 只保存安全摘要、
  digest 与可审计 receipt，不保存凭证、raw runtime tree、host secret 或外部资源 identity。

## Test and Evidence Obligations

| Item | Risk | Required Validation | Required Evidence |
|---|---|---|---|
| AC-1..AC-2 | blocker | Git parent/path/status diff、push/remote exact、原工作区保护 | Cplan/Cactivation SHA 与 status/diff proof |
| AC-3..AC-4 | blocker | frozen formal-delta/contract/manifests/source、fresh clone/runtime residue preflight | preflight receipt and exact digests |
| AC-5 | blocker | one complete fresh Step4 formal all-lane execution | canonical child/outer terminal receipts and report totals |
| AC-6 | blocker | final-artifact replay、coverage/critical/model/sensitive/cleanup/candidate/capsule | summary/run-status and sanitized durable evidence |
| AC-7 | critical | source/provenance/no-splice review | recovery formal evidence record and work-item result |
| AC-8 | blocker | controlling-item Step5→Step6→Step7 gates | each stage's canonical evidence and final handoff |

验证成本与循环控制：

- `<5m`: Git topology、digest、manifest、formal-delta、source/contract、residue preflight；输入不变时
  只执行一次，发现差异立即停止。
- `5-30m`: post-run receipt/final-artifact replay与 focused static validation；只对新 formal output执行。
- `>60m`: fresh formal 及后续 Step5/6 governed runs；每个 stage/run ID 最多一次。
- reusable evidence: diagnostic r7 的 candidate/capsule/review、Cfreeze formalization bytes 与已签收
  FSScript focused product tests可作为 frozen input provenance；它们不能替代任何新 formal lane。
- invalidated evidence: r8 全部 partial runtime output、未终结 sqlite-refresh、无 outer terminal receipt
  的任何观察永久 excluded。
- rerun trigger: source/tool/policy/threshold/selector/environment assumption 或 frozen digest变化时，
  recovery authorization立即失效，必须 new Cdiag；不得在本 Cplan 下修补。
- stop/replan: 本次 formal 是 r8 后第二个昂贵 attempt。若再次因产品或非产品原因未 terminal PASS，
  必须记录、转 `NEEDS_REPLAN` 并停；不得授权第三次尝试。

## Bug Context

- bug_source: release-authority formal execution externally interrupted.
- severity: blocker to Step5-7 and v9.3.4 signoff.
- environment: PR #124；Cdiag `462d64acf0865f44582b2b1245a9b4c771aad4cd`；Cfreeze
  `5c57e537603004d47be936f74e92f873de5fe431`；failed run r8。
- current_behavior: r8 的 Unit child terminal PASS，Integration 仅到 sqlite-refresh local zero-failure
  report；缺少 integration-complete、outer summary/run-status、source-after、final coverage/cleanup、
  candidate/capsule，因此无 formal authority。
- expected_behavior: 从相同 immutable Cfreeze 的全新 clone/run root/run ID 完成独立 terminal formal，
  任何结果均不依赖 r8 bytes。
- reproduction_steps: 从 r8 failure record 检查缺失 terminal receipts；运行 formal-delta validator
  证明 Cfreeze 是唯一合法 formal HEAD；验证在 Cfreeze 后追加 commit 会被 direct-parent gate拒绝。
- reproduction_status: confirmed governance/execution interruption; no product failure verdict.
- existing_evidence:
  `docs/9.3.4/evidence/step-4/step4-v934-fsscript-lifecycle-dual-seal-formal-20260722-r8-external-interruption-fail-closed.md`。
- existing_tests: diagnostic r7 all-lane PASS；r8 partial outputs仅用于解释中断，不可计入验收。
- regression_protection: required fresh formal and no-splice/source/cleanup/final-artifact checks。
- waiver_reason_and_risk: N/A。

## Risks and Open Questions

- known_risks:
  - 长时间 foreground tool session 仍可能被外部终止；本次须保持终端 session 并频繁轮询，但不能
    用后台续跑绕过 runner ownership；
  - 同一 Cfreeze recovery 的授权不在执行 HEAD ancestry 内，因此必须由 clean/pushed activation
    record 与 post-run evidence 同时绑定，任何 SHA/run mismatch 都 fail closed；
  - 若 host 环境与 diagnostic/formal preflight assumption 不同，不能把环境失败解释为 PASS。
- open_questions: none

## Ultra Execution Contract

- 先提交并 push 本 `APPROVED` Cplan；它本身不授权 runner。
- 再创建唯一 activation commit，仅把本文件改为 `ULTRA_EXECUTING` 并填写 activation readiness；
  clean/pushed 后才允许执行。
- 新 formal 建议 run ID：
  `step4-v934-fsscript-lifecycle-dual-seal-formal-recovery-20260723-r9`。
- 执行 clone 必须 fresh/non-shallow/clean，detached exact Cfreeze；其 remote origin 必须解析到包含
  activation commit 的同一受控仓库，但 tracked execution bytes只能来自 Cfreeze。
- 禁止读取 r8 runtime root作为输入，禁止 partial rerun，禁止修改 frozen bytes；所有 lanes从零执行。
- formal PASS 后先完成本 recovery 的 durable evidence/result，再按 controlling item 逐步执行
  Step5、Step6、Step7；不得跳步或把 required GitHub CI 状态手工视为成功。
- 任一 gate失败，将本文件设为 `NEEDS_REPLAN`，记录 failed/excluded evidence并停止。
- 完成 recovery implementation 后设 `READY_FOR_SIGNOFF`；只有独立签收 owner 可设 `ACCEPTED`。

## Implementation Result

- implementation_summary: pending
- changed_paths: pending
- tests_and_results: pending
- manual_or_experience_evidence: pending
- deviations: none
- residual_risks: pending
- readiness: ULTRA_EXECUTING — this exact two-field transition is the activation identity; bind its clean/pushed Git SHA in preflight and post-run evidence

## References

- predecessor work item:
  `docs/9.3.4/workitems/BUG-v934-fsscript-lifecycle-inventory-dual-seal-successor-replan.md`
- interruption record:
  `docs/9.3.4/evidence/step-4/step4-v934-fsscript-lifecycle-dual-seal-formal-20260722-r8-external-interruption-fail-closed.md`
- controlling release item:
  `docs/9.3.4/workitems/FEATURE-v934-release-authority-and-ci.md`
