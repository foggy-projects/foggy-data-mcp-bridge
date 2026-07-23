---
doc_type: delivery-spec
delivery_type: bug
version: 9.3.4
ticket: BUG-STEP5-PACKAGE-PROOF-CLEAN-MAVEN-ENV-SUCCESSOR
status: APPROVED
canonical: true
execution_mode: ultra
approved_by: project-owner-via-explicit-package-only-reuse-authorization
approved_at: 2026-07-23
controlling_item: FEATURE-V934-RELEASE-AUTHORITY-AND-CI
predecessors:
  - BUG-STEP5-INSPECT-FORMAT-POST-REMEDIATION-PACKAGE-PROOF-REPLAN
  - BUG-STEP5-RUNTIME-IMAGE-INSPECT-FORMAT-REMEDIATION
open_questions: []
---

# Delivery Spec: Step 5 package proof clean Maven environment successor

## Document Purpose

- intended_for: ultra-implementation / v9.3.4 release-authority owner /
  independent signoff.
- purpose: 在 r11 fresh Step4 release successor 已完整通过、唯一 package
  invocation 仅因 `package-preflight / E_MAVEN_ENV` fail closed 后，复用同一
  immutable Step4 authority，授权一次显式净化 Maven/JVM control environment 的
  package-only successor；不重跑 Step4，不运行 canonical Step5 gate。
- canonical_path:
  `docs/9.3.4/workitems/BUG-step5-package-proof-clean-maven-env-successor.md`

## Goal

- version_goal: 关闭 runtime-image inspect format remediation 的真实 package
  context proof 缺口，同时避免因不影响 r11 Step4 artifact identity 的 runner
  environment 启动错误重复执行 5709 个测试。
- target_outcome: 从保留的 exact r11 execution clone 和 canonical same-root Step4
  release root 启动一次 clean-environment direct package command。成功结果只能分类为
  `package-context-passed-non-authoritative`；任何失败、receipt/identity/cleanup 不完整
  或 artifact 漂移均 fail closed。

## Scope

- in_scope:
  - 本 APPROVED Cplan 作为 r11 `package-preflight / E_MAVEN_ENV` fail-closed
    checkpoint 的唯一 package-only successor authority；
  - 创建只改变本文件 status/readiness 的 clean/pushed activation commit；
  - 复核 predecessor evidence、Cfreeze、r11 Step4 terminal chain、public artifact、
    source/class/report/toolchain bindings和 package negative/self-test closure；
  - 复用 r11 exact run ID
    `step4-v934-inspect-format-post-remediation-package-proof-release-20260723-r11`
    及其 canonical same-root Step4 release root，不复制、移动、补写或重新运行 Step4；
  - 在独立 0700 tmux control directory/socket/session 中，以进程边界
    `env -u` 清除 package tool 明确禁止的八个 Maven/JVM control variables：
    `MAVEN_ARGS`、`MAVEN_BASEDIR`、`MAVEN_CONFIG`、`MAVEN_OPTS`、
    `MAVEN_SKIP_RC`、`JAVA_TOOL_OPTIONS`、`JDK_JAVA_OPTIONS`、
    `_JAVA_OPTIONS`；
  - 在净化后的进程中直接调用一次 `release_package_tool.py package`，使用
    canonical r11 Step4 root、既定 output path 和 fixed sibling failure receipt；
  - package success 时验证内部 `verified=true`、manifest/JAR/image/embedded-JAR、
    source/classes/reports before-after seals和 package-owned cleanup；
  - package failure 时只读取并独立验证 fixed nine-field safe receipt，不读取或持久化
    raw Maven/Docker output；
  - 结果裁决后删除 package output/receipt、关闭 owned tmux server，验证 source、
    Step4 authority、candidate/final pointers、Docker residue和受保护原工作区不变；
  - 仅 component PASS 时关闭 predecessor remediation/proof work items并另行冻结
    canonical Step5 rehearsal Cplan；本 Cplan 不授权 rehearsal invocation。
- affected_modules:
  - `docs/9.3.4` governance/evidence；
  - 既有 Step4 release artifacts 和 Step5 package tool 仅作 immutable input；
  - host-local retained r11 execution clone、owned tmux control和临时 package output。
- external_dependencies: tmux、Maven、Docker Engine、local Maven cache、已缓存 frozen
  base image。无 registry pull、GitHub publication、release/tag或部署。

## Non-Goals

- out_of_scope:
  - 重跑 Step4 diagnostic/formal/release 或任何 Surefire/Failsafe/database/external
    test lane；
  - 修改 production、test、POM、Dockerfile、runner、package tool、receipt schema、
    manifests、coverage threshold/exclusion、selector、skip、API/SPI或 FSScript/Spring
    生命周期语义；
  - 放宽 `validate_maven_environment`、允许 ambient control variables、把代理值写入
    package tool、或修改工具来适配当前 shell；
  - 调用 `verify-v934-release-gate.sh`、portable replay、archive、candidate/final pointer、
    Step6/Step7、release dry-run或 GitHub workflow；
  - package失败后的自动 retry、第二次 invocation、instrumentation或现场修补；
  - 把 r11 preflight failure 当成 component result，或把本 proof 冒充 Step5 authority。
- do_not_touch:
  - 原工作区 `docs/9.3.5/README.md` 与 `docs/9.3.5/workitems/`；
  - Cfreeze tracked bytes、r11 Step4 release root内容、历史失败 evidence和 authority pointers；
  - 非本 invocation owned 的 tmux session、process、container、image、network或 volume。

## Confirmed Decisions

| Decision | Rationale | Compatibility / Constraint |
|---|---|---|
| Reuse exact r11 Step4 release | r11 is terminal `release-passed / release-candidate-ready / release-final`, public-valid, source exact and cleanup-closed; the package attempt failed before Step4/package validation or Maven execution | package tool must revalidate the full Step4 authority before and after packaging |
| Do not rerun the 5709-test Step4 chain | the changed input is only the package process environment; no source, test, report, class universe, toolchain or Step4 artifact changed | any immutable input drift invalidates reuse and stops execution |
| Sanitize externally with `env -u` | package tool intentionally rejects ambient Maven/JVM controls as hidden inputs | tool bytes and policy remain unchanged; all eight names must be absent in the package process |
| Keep the r11 run ID and canonical root | package tool binds Step4 authority to `repo/target/v934-step4-coverage/runs/<run-id>` | no copied or renamed Step4 root |
| One package-only attempt | predecessor exhausted its own allowance; this successor grants exactly one new attempt | no wrapper retry or second tmux invocation |
| Success remains non-authoritative | it proves only the repaired package component boundary | separate canonical Step5 rehearsal is still mandatory |

## Acceptance Criteria

- [ ] AC-1: 本 Cplan 是 pushed fail-closed checkpoint
  `ecd668b96a13cb087f7898ee56f7b888d409f0e5` 的 clean single-parent child；
  activation 又是 Cplan 的 clean/pushed single-parent child，diff仅为本文件
  `APPROVED -> ULTRA_EXECUTING` 与 readiness。
- [ ] AC-2: 原工作区仍为
  `9743f97d9d935d5e26311b78c158755bca51f17a`，v9.3.5 用户改动原样保留；
  governance 和 execution roots均无未授权 tracked drift，remote包含 activation。
- [ ] AC-3: r11 Step4 root在原 canonical location保持 immutable；run status、summary、
  final manifest和 public verifier仍为
  `release-passed / release-candidate-ready / release-final / VALID`，required=
  `774 executions + 59 structural / 5709 tests / F0E0S0`、Addon=`2/6`。
- [ ] AC-4: preflight确认 package tool/Step4/5/6 manifests和 negative/self-test closure；
  output/receipt/control/session不存在，frozen base image cached，required package resources
  无同 run-owned residue。净化 probe只记录八个受禁变量 absent，不记录 parent/raw value。
- [ ] AC-5: 唯一 package invocation由独立 tmux pane `exec env -u ... python3` 持有；
  run ID、repo root、Step4 root、output和 fixed sibling receipt精确绑定。Codex在 pane
  live期间只读观察，不运行 Step4或 canonical Step5 runner。
- [ ] AC-6: package success必须同时满足 exit=0、无 failure receipt、内部
  `verified=true`、package/image manifest与 app/embedded JAR identity一致、source/classes/
  reports before-after一致、Step4 authority复验一致和 package-owned cleanup exact；最终
  category只能是 `package-context-passed-non-authoritative`。
- [ ] AC-7: package failure必须有可独立验证的 fixed safe receipt并形成允许的
  fail-closed category；missing/invalid receipt、tmux/identity不一致、cleanup或 invariance
  失败均为 inconclusive。任一非PASS结果都转 `NEEDS_REPLAN`且不得重试。
- [ ] AC-8: owned package output/receipt/tmux/control和 package Docker residue被清理；
  source、r11 Step4 root、candidate/final pointers和原工作区不变。Durable evidence只含
  safe facts/counts/status，privacy scan通过。
- [ ] AC-9: component PASS后，两个 predecessor work item的 AC/Implementation Result被
  完整回写为 `READY_FOR_SIGNOFF`；本 work item同样转 `READY_FOR_SIGNOFF`，随后只冻结
  独立 canonical Step5 rehearsal Cplan，不在同一授权下启动它。

## Contract / Data / Security Constraints

- API or event contract: 不修改 production API/SPI、CI/workflow、package/receipt/pointer
  schema或 Bean/config/event contract。
- data and migration: 无业务数据或 schema migration；r11 Step4 root是 read-only
  authority input，package output为临时 owned material。
- compatibility and rollback: 无产品语义变化。删除 package output/receipt/control即完成
  runtime rollback；失败保留 sanitized evidence，不回滚或改写历史。
- permissions and secrets: tmux control directory mode 0700；Git证据可记录变量名称和
  absent count，但不得记录任何 raw environment value、endpoint、credential、temporary
  absolute path、image/container ID或 package digest。

## Test and Evidence Obligations

| Item | Risk | Required Validation | Required Evidence |
|---|---|---|---|
| AC-1..AC-3 | blocker | Git/topology/source and public r11 Step4 verification | plan/activation/Cfreeze and bounded terminal facts |
| AC-4 | blocker | clean-environment probe, package negative/self-test, Docker/cache/residue preflight | absent count, safe status/counts only |
| AC-5..AC-7 | blocker | exactly one receipted package command | invocation count, exit/category, verified or receipt-valid booleans |
| AC-8 | blocker | output/tmux/Docker cleanup and source/Step4/pointer invariance | safe cleanup/invariance checklist |
| AC-9 | critical | predecessor closure and separate rehearsal planning | updated work items/evidence; no rehearsal runtime output |

验证成本与循环控制：

- `<5m`: Git/source/Step4 public verify、manifest/self-test、sanitized environment和
  residue preflight；输入不变时一次。
- `5-30m`: 唯一 package invocation、internal verification、cleanup和 bounded evidence；
  Maven/Docker耗时可能更长。
- `>60m`: none；r11 Step4 release evidence明确复用，不得重跑。
- reusable evidence: r11 exact Step4 release root、terminal/public verification、
  all-lane totals、source/class/report/toolchain bindings和 cleanup closure。
- invalidated evidence: r11 package preflight failure不是 component evidence；任何 Cfreeze/
  source/Step4 artifact/class/report/toolchain identity漂移都会使本 successor失效。
- rerun trigger: none within this Cplan。任何 package nonzero、receipt/identity/cleanup异常
  都停止并转 `NEEDS_REPLAN`。
- maximum attempts: package一次；无 Step4 invocation。

## Bug Context

- bug_source: acceptance-found runner-environment preflight failure.
- severity: blocker to runtime package proof and canonical Step5 rehearsal.
- environment: retained r11 execution clone at Cfreeze
  `5c57e537603004d47be936f74e92f873de5fe431`，public-valid r11 Step4 release，
  local WSL Maven/Docker。
- current_behavior: predecessor package command在任何 Maven/Docker/package output之前因
  ambient Maven/JVM control environment被 package tool以 `E_MAVEN_ENV`拒绝。
- expected_behavior: package process在显式 clean environment中启动，随后由既有 package
  tool严格验证 r11 Step4 authority并执行真实 Maven/Docker package path。
- reproduction_steps: 在保留任一受禁 control variable的环境调用 package tool即在
  `package-preflight` fail closed；清除全部八个名称后再执行唯一 package-only successor。
- reproduction_status: confirmed by valid fixed receipt and source-level preflight ordering.
- existing_evidence:
  `docs/9.3.4/evidence/step-5/step5-inspect-format-post-remediation-package-proof-r11-fail-closed-20260723.md`
- existing_tests: package Docker-free negative/self-test、Step4 public verifier及 package
  pre/post authority revalidation。
- regression_protection: existing package tool policy already automates fail-closed protection；
  本事项只修正 runner invocation environment，不修改代码。
- waiver_reason_and_risk: N/A.

## Risks and Open Questions

- known_risks:
  - 清除 proxy-bearing control variable后，若 Maven cache不完整，package可能因网络不可达
    fail closed；该结果不得自动重试；
  - r11 Step4 root是 host-local immutable authority，若被删除、移动或改变则必须重新规划；
  - package success仍不覆盖 canonical Step5 archive/pointer/portable replay路径。
- open_questions: none.

## Ultra Execution Contract

- 先提交并 push 本 APPROVED Cplan；再创建唯一 activation commit，仅修改本文件
  status/readiness，clean/pushed后才能运行。
- 保留 r11 run ID和 canonical Step4 root。禁止重新运行、复制、重命名或补写 Step4。
- package runner使用独立 tmux socket/session、`remain-on-exit=on`，pane command以
  `exec env -u` 清除全部八个名称后启动唯一 canonical package process；Codex运行中只读观察。
- package失败时独立验证 fixed receipt，删除 owned output并停止；不得在本 Cplan下修补或重试。
- 结果分类和清理完成后写 durable evidence并更新本 work item。只有 component PASS才可关闭
  predecessors并另行冻结 canonical Step5 rehearsal Cplan；不得在本授权内激活或执行 rehearsal。
- 完成后设 `READY_FOR_SIGNOFF`；不得自行设置 `ACCEPTED`。

## Implementation Result

> Pending activation and the one governed clean-environment package-only proof.

- implementation_summary: pending
- changed_paths: pending
- tests_and_results: pending
- manual_or_experience_evidence: pending
- deviations: none
- residual_risks: pending
- readiness: APPROVED — execution requires a separate clean/pushed activation commit.

## References

- controlling release item:
  `docs/9.3.4/workitems/FEATURE-v934-release-authority-and-ci.md`
- predecessor package proof:
  `docs/9.3.4/workitems/BUG-step5-inspect-format-post-remediation-package-proof-replan.md`
- source remediation:
  `docs/9.3.4/workitems/BUG-step5-runtime-image-inspect-format-remediation.md`
- predecessor fail-closed evidence:
  `docs/9.3.4/evidence/step-5/step5-inspect-format-post-remediation-package-proof-r11-fail-closed-20260723.md`
