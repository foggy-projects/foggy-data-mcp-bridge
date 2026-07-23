---
doc_type: delivery-spec
delivery_type: bug
version: 9.3.4
ticket: BUG-STEP5-INSPECT-FORMAT-POST-REMEDIATION-PACKAGE-PROOF-REPLAN
status: APPROVED
canonical: true
execution_mode: ultra
approved_by: project-owner-via-sequential-release-authority-authorization
approved_at: 2026-07-23
controlling_item: FEATURE-V934-RELEASE-AUTHORITY-AND-CI
predecessors:
  - BUG-STEP5-RUNTIME-IMAGE-INSPECT-FORMAT-REMEDIATION
  - BUG-V934-FSSCRIPT-FORMAL-TMUX-OWNER-RECOVERY-REPLAN
open_questions: []
---

# Delivery Spec: Step 5 inspect-format post-remediation package proof replan

## Document Purpose

- intended_for: ultra-implementation / v9.3.4 release-authority owner / independent signoff.
- purpose: 在 repaired runtime-image inspect source 已完成 fresh Step4 diagnostic/formal chain 后，
  以 package tool 强制要求的 fresh Step4 `release` successor 为同源输入，授权一次受限的
  non-authoritative package component proof；不调用 canonical Step5 release gate，不生成 candidate、
  archive 或 pointer。
- canonical_path:
  `docs/9.3.4/workitems/BUG-step5-inspect-format-post-remediation-package-proof-replan.md`

## Goal

- version_goal: 关闭 `BUG-step5-runtime-image-inspect-format-remediation.md` 的 AC-6/AC-7 package
  runtime proof 缺口，使 repaired Docker inspect template 在真实 Maven/Docker package context 中得到
  一次可裁决结果。
- target_outcome: clean/pushed activation 后，从 fresh clean non-shallow clone detached exact Cfreeze
  `5c57e537603004d47be936f74e92f873de5fe431`，先运行唯一 fresh Step4 release successor，再以其
  same-root classes/reports/source 调用一次 package command。成功结果只能分类为
  `package-context-passed-non-authoritative`；任何失败、receipt 无效、清理不完整或 identity 漂移均
  fail closed，且不得自动进入 canonical Step5 rehearsal。

## Scope

- in_scope:
  - 本 APPROVED Cplan 作为 r10 formal PASS 后 package proof 的唯一 successor authority；
  - 创建只改变本文件 `status/readiness` 的 clean/pushed activation commit；
  - 复核 Cfreeze、r10 formal terminal evidence、Step4/5/6 manifests、package Docker-free regression
    closure，以及 package tool 对 formal input 的显式拒绝和 release input 的强制要求；
  - 在全新 disposable clone、run root、run ID、0700 control directory 与独立 tmux socket/session 下，
    完整运行一次 canonical Step4 `release` successor；
  - release terminal PASS 后，以同一 run ID、同一 clone、canonical same-root Step4 release root，
    在第二个独立 tmux session 中直接调用一次 `release_package_tool.py package`，使用固定 sibling
    failure receipt；
  - package success 时验证内部 `verified=true`、manifest/JAR/image/embedded-JAR/cleanup、source/
    classes/reports before-after seals和无 failure receipt，再删除 package output；
  - package failure 时只读取并独立验证固定九字段 safe receipt，保留允许的 category/booleans，
    删除 owned output；
  - 无论结果如何，核对 source、Step4 input、candidate/final pointer、Docker residue与受保护原工作区
    不变，写入 bounded durable evidence；
  - 仅 component PASS 时，把原 format-remediation work item 补全为 `READY_FOR_SIGNOFF`，并另行冻结
    fresh canonical Step5 rehearsal Cplan；本 proof 本身不授权 rehearsal invocation。
- affected_modules:
  - `docs/9.3.4` governance/evidence；
  - 既有 Step4/Step5 package tools 仅作 immutable execution input；
  - host-local disposable clone、tmux socket与 run-owned Maven/Docker/package output。
- external_dependencies: tmux 3.4、Maven、Docker Engine、已缓存 frozen base image、既有五数据库与
  external-lane测试环境；无 registry pull、GitHub publication、release/tag或部署。

## Non-Goals

- out_of_scope:
  - 修改产品、测试、POM、Dockerfile、runner、package tool、receipt schema、manifest、coverage
    threshold/exclusion、selector、skip、API/SPI或 Spring/FSScript 语义；
  - 调用 `verify-v934-release-gate.sh`、portable replay、archive、candidate pointer、final pointer、
    Step6/Step7、release dry-run或 GitHub workflow；
  - 把 r10 formal artifact传给 package tool，放宽 package tool的 release-only input contract，或把
    Step4 release successor冒充 Step5 candidate；
  - 保留 raw Maven/Docker output、image/container identity、临时绝对路径、endpoint、credential或
    package digest到 Git evidence；
  - package失败后的自动 retry、instrumentation、修补或第二次 package invocation。
- do_not_touch:
  - 原工作区 `docs/9.3.5/README.md` 与 `docs/9.3.5/workitems/`；
  - immutable Cfreeze tracked bytes、r8/r9 excluded roots、r10 formal root；
  - historical Step5 failure evidence、candidate/final authority pointers和非本 run-owned Docker资源。

## Confirmed Decisions

| Decision | Rationale | Compatibility / Constraint |
|---|---|---|
| Run a fresh Step4 release successor before package | package tool deliberately rejects formal artifacts and requires `release-passed / release-candidate-ready / release-final` same-root input | this is Step4 release evidence only; no Step5 runner/candidate/pointer |
| Use two sequential tmux-owned sessions | package may start only after release terminal PASS, while Codex process lifetime must not own either runner | no wrapper retry/translation; Codex observer-only during each live invocation |
| Exactly one package command | the old remediation authorizes one bounded runtime proof, not iterative diagnosis | internal package verification is retained; no second attempt |
| Success is non-authoritative | component success proves repaired package boundary only | separate owner-governed canonical Step5 rehearsal contract remains mandatory |
| Failure receipt remains the sole safe failure interface | raw process detail is unnecessary and prohibited | fixed sibling name, nine fields, independent verifier, no raw evidence |
| Remove package output after classification | proof must not create a candidate or reusable release package | Step4 release root may remain only inside the disposable clone until evidence is closed |

## Acceptance Criteria

- [ ] AC-1: 本 Cplan 是 pushed r10 PASS checkpoint
  `65eb7c73e877576ce0a3c90aab50f334e4c42652` 的 clean single-parent child；activation 又是 Cplan
  的 clean/pushed single-parent child，diff仅为本文件 `APPROVED -> ULTRA_EXECUTING` 与 readiness。
- [ ] AC-2: 原工作区仍为 `9743f97d9d935d5e26311b78c158755bca51f17a`，用户 v9.3.5 改动
  原样保留；fresh execution clone clean/non-shallow、无 replace/graft、detached exact Cfreeze，remote
  包含 activation。
- [ ] AC-3: preflight验证 r10 formal terminal PASS、Cfreeze/source/contract/threshold、Step4/5/6
  manifests、package negative/self-test closure、Docker/base cache、required ports、无并发 authority lock、
  run root/socket/session/output/receipt均不存在。
- [ ] AC-4: 唯一 Step4 release invocation由独立 tmux pane `exec` canonical runner持有；Codex全程
  只读观察。pane dead status为0且 canonical release terminal chain为 `release-passed /
  release-candidate-ready / release-final`，required lanes F/E/S=0，source/cleanup全部闭合。
- [ ] AC-5: 仅 AC-4 PASS 后启动唯一 package invocation；run ID、repo root、Step4 run root、output和
  fixed sibling receipt全部精确绑定，且不调用 canonical Step5 runner、archive、pointer或 publication。
- [ ] AC-6: package success必须同时满足 exit=0、无 failure receipt、内部 `verified=true`、package/
  image manifest与 app/embedded JAR identity一致、source/classes/reports before-after一致、package-owned
  Maven/Docker cleanup exact；最终 category只能是 `package-context-passed-non-authoritative`。
- [ ] AC-7: package failure必须有可独立验证的 fixed safe receipt并形成允许的 fail-closed category；
  missing/invalid receipt、tmux/identity不一致、cleanup或invariance失败均为 inconclusive。任一非PASS结果
  都转 `NEEDS_REPLAN`，且不自动重试或进入 Step5 rehearsal。
- [ ] AC-8: owned package output/receipt/tmux server与 package Docker residue被清理；source、Step4
  release root、candidate/final pointers和原工作区不变。Durable evidence只含 safe facts/counts/status，
  privacy scan通过。
- [ ] AC-9: component PASS后，原 format-remediation AC-1..AC-9与 Implementation Result被完整回写为
  `READY_FOR_SIGNOFF`；本 proof work item同样转 `READY_FOR_SIGNOFF`，随后只冻结独立 canonical Step5
  rehearsal Cplan，不在同一授权下启动它。

## Contract / Data / Security Constraints

- API or event contract: 不修改 production API/SPI、CI/workflow、package/receipt/pointer schema或
  Bean/config/event contract。
- data and migration: 无业务数据或 schema migration；只允许 run-owned fixture/container，Step4 release
  root是 package的 read-only authority input。
- compatibility and rollback: 文档与 fail/pass evidence追加保留；package output删除即完成 runtime
  rollback。失败不得回滚历史或伪造成功。
- permissions and secrets: tmux control directory mode 0700；Git仅保存 sanitized status/counts/booleans和
  必需 provenance SHA，不保存 raw environment、credential、endpoint、image/container ID或临时路径。

## Test and Evidence Obligations

| Item | Risk | Required Validation | Required Evidence |
|---|---|---|---|
| AC-1..AC-3 | blocker | Git/topology/source/manifests/r10/Docker/residue preflight | plan/activation/Cfreeze identities and safe preflight facts |
| AC-4 | blocker | one fresh Step4 release all-lane execution | canonical release receipts, totals, source and cleanup |
| AC-5..AC-7 | blocker | one direct receipted package command and bounded adjudication | invocation count, category, receipt-valid or verified booleans |
| AC-8 | blocker | output/Docker/tmux cleanup, source/Step4/pointer invariance, privacy review | safe cleanup/invariance checklist |
| AC-9 | critical | predecessor result closure and separate rehearsal planning | updated work items/evidence; no rehearsal runtime output |

验证成本与循环控制：

- `<5m`: Git/seal/manifest/r10/package-contract/residue/tmux preflight；输入不变时一次。
- `5-30m`: package invocation、manifest/internal-verify replay、cleanup与 bounded evidence；实际 Maven/
  Docker耗时可能更长，但不允许第二次 package invocation。
- `>60m`: fresh Step4 release successor；预计 60-120分钟，唯一 run ID/attempt。
- reusable evidence: r10 formal terminal evidence、accepted source repair与 Docker-free regression closure只作
  provenance；不能替代新的 release successor或 package proof。
- invalidated evidence: r8/r9 runtime outputs、r44/r45 package failures与 r6 pre-remediation proof均不得拼接。
- rerun trigger: 任一 source/tool/policy/threshold/selector/environment digest变化使本授权失效；不得在本
  Cplan下修补。
- maximum attempts: Step4 release一次、package一次；任一 pane nonzero、receipt/identity/cleanup不完整或
  component非PASS均立即 `NEEDS_REPLAN`，不得自动发起新 run。

## Bug Context

- bug_source: acceptance-found runtime-image inspect format defect.
- severity: blocker to canonical Step5 rehearsal.
- environment: PR #124 successor head；Cfreeze `5c57e537...`；r10 formal PASS；local WSL Docker/Maven。
- current_behavior: repaired inspect template已通过 Docker-free/static closure和 fresh formal，但尚未在
  package release-input context中得到 post-remediation runtime proof。
- expected_behavior: fresh Step4 release input上的一次 package command通过 strict image identity readback，
  保持 fixed receipt、cleanup和 source/report seals；结果仍不构成 Step5 authority。
- reproduction_steps: 先运行 exact-source Step4 release successor，再以其 canonical same-root input运行
  唯一 receipted package command。
- reproduction_status: repaired source confirmed statically; runtime package result pending.
- existing_evidence: accepted r6 E_IMAGE reproduction、format repair/static closure、fresh Step4 chain和 r10
  formal PASS record。
- existing_tests: package Docker-free 117-case negative/self-test、Step4/5/6 integrity closure及 r10 all-lane。
- regression_protection: required; existing source-level regressions remain unchanged，本 work item补真实 runtime
  proof。
- waiver_reason_and_risk: N/A.

## Risks and Open Questions

- known_risks:
  - Step4 release是高成本，但 package tool的 release-only contract禁止复用 r10 formal artifact；
  - tmux隔离 Codex误杀，但不能抵御 host reboot、显式 kill-server或宿主机资源故障；
  - package success仍可能暴露后续 canonical Step5 archive/pointer问题，因此不能越级签收 Step5；
  - package过程会暂时生成 Maven JAR和 Docker image，但工具必须恢复/quarantine并精确清理。
- open_questions: none.

## Ultra Execution Contract

- 先提交并 push 本 APPROVED Cplan；再创建唯一 activation commit，仅修改本文件 status/readiness，
  clean/pushed后才能运行。
- 建议共享 run ID：
  `step4-v934-inspect-format-post-remediation-package-proof-release-20260723-r11`。
- 使用 fresh clone detached exact Cfreeze。Step4 release和package分别使用独立 tmux socket/session，
  `remain-on-exit=on`，pane command以 `exec` 启动唯一 canonical process；Codex运行中只读观察。
- Step4 release未 terminal PASS时不得启动package；package只允许一次，不得运行canonical Step5 runner。
- 结果分类和清理完成后写 durable evidence并更新两个 work item。只有 component PASS才可另行冻结
  canonical Step5 rehearsal Cplan；不得在本授权内激活或执行该 rehearsal。
- 任一 gate失败，将本文件设为 `NEEDS_REPLAN`，记录 failed/excluded safe evidence并停止。
- 完成后设 `READY_FOR_SIGNOFF`；不得自行设置 `ACCEPTED`。

## Implementation Result

> Pending activation and the one governed release-successor/package proof.

- implementation_summary: pending
- changed_paths: pending
- tests_and_results: pending
- manual_or_experience_evidence: pending
- deviations: none
- residual_risks: pending
- readiness: APPROVED — runner not yet authorized until the separate activation commit is pushed.

## References

- controlling release item:
  `docs/9.3.4/workitems/FEATURE-v934-release-authority-and-ci.md`
- source remediation:
  `docs/9.3.4/workitems/BUG-step5-runtime-image-inspect-format-remediation.md`
- accepted pre-remediation diagnosis:
  `docs/9.3.4/workitems/BUG-step5-runtime-image-inspect-r6-receipt-target-package-context-diagnosis.md`
- fresh formal recovery:
  `docs/9.3.4/workitems/BUG-v934-fsscript-formal-tmux-owner-recovery-replan.md`
