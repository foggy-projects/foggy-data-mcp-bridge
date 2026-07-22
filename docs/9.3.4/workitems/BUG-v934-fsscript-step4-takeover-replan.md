---
doc_type: delivery-spec
delivery_type: bug
version: 9.3.4
ticket: BUG-V934-FSSCRIPT-STEP4-TAKEOVER-REPLAN
status: NEEDS_REPLAN
canonical: true
execution_mode: ultra
approved_by: project-owner-via-runner-pr-takeover-authorization
approved_at: 2026-07-22
controlling_item: FEATURE-V934-RELEASE-AUTHORITY-AND-CI
predecessors:
  - BUG-FSSCRIPT-SHARED-DEFAULT-SPRING-DESTROY
  - BUG-STEP4-R7-SINGLE-ACTIVATION-CDIAG-FRESH-DIAGNOSTIC
open_questions: []
---

# Delivery Spec: FSScript Step 4 takeover replan

## Document Purpose

- intended_for: Ultra execution and independent release-authority signoff.
- purpose: 在不丢弃原 runner owner 已形成的诊断历史、也不绕过 Step 4
  受保护源码漂移检查的前提下，把已独立签收的 FSScript 生命周期修复纳入新的
  fresh Step 4 → Step 5-7 收口链。
- canonical_path:
  `docs/9.3.4/workitems/BUG-v934-fsscript-step4-takeover-replan.md`

## Goal

- version_goal: 恢复 PR #124 的 v9.3.4 release-authority，使 required CI 和本地
  authority 都验证当前已签收产品源码，而不是旧 source seal。
- target_outcome: PR 头同时包含既有 R7 runner/diagnostic 修复历史和 FSScript
  接受提交；Step 4 对两个受保护生产文件形成显式 source amendment，并从唯一新
  Cdiag 完成 fresh diagnostic、freeze、formal 及后续 Step 5-7。

## Scope

- in_scope:
  - 以 `origin/codex/v934-release-authority` 当前接受头为第一父线，显式合并
    `origin/codex/v934-step5-postrun-step4-state-reset-diagnostic`，保留双方完整历史；
  - 手工解决双方在 Step 5/6 manifest、package tool 和 CI contract 上的有限重叠，
    保留诊断分支已接受的 package integrity/runtime-inspect 修复，并保留 release 线已接受的
    portable evidence、CI `rg`、fixture diagnostics 和 Docker image-store 修复；
  - 将旧 `BUG-STEP4-R7-SINGLE-ACTIVATION-CDIAG-FRESH-DIAGNOSTIC` 标记为
    `NEEDS_REPLAN`，禁止复用其 Cdiag、run ID、candidate、capsule 或 authority；
  - 把 `DefaultExpFactory.java` 与 `FunTable.java` 的已签收生产变更加入 Step 4
    declared amendments，并同步 overlay contract/tool 和所有直接/传递 hash bindings；
  - 保留新增 lifecycle regression 为产品回归证据；它不是旧 parent tree 中的受保护
    tracked path，不伪造为 parent amendment；
  - 把 Step 4/5/6 machine workflow 恢复为新的 diagnostic-ready 基线；
  - 在 clean/pushed integration commit 之后创建唯一单父、单路径、两字段 activation
    Cdiag，并从 fresh non-shallow clone 执行一次新 diagnostic；
  - diagnostic 成功后按现有受治理顺序完成 candidate/capsule 独立复核、direct-child
    Cfreeze、formal、Step 5 package/release gate、Step 6 required CI 和 Step 7 最终签收。
- affected_modules:
  - `foggy-fsscript` 已签收源码和测试；
  - `scripts/v934/step4`、`scripts/v934/step5`、`scripts/v934/step6`；
  - `.github/workflows/test-ci-evidence-chain.yml`；
  - `docs/9.3.4` workitems/evidence/acceptance 索引。
- external_dependencies: GitHub PR #124、GitHub Actions、既有 Docker/Compose、Maven
  和五数据库 authority 环境；不发布制品、不部署业务环境。

## Non-Goals

- out_of_scope:
  - 改写、squash 或删除旧诊断分支的 49 个独有提交；
  - 对 FSScript 方案 A 再设计、扩大为方案 B/C，或修改其已签收生产语义；
  - 降低 coverage floor、扩大 exclusion、改变 selector、skip、fork、测试顺序或
    required DB set；
  - 复用旧 r1-r4 的 partial lane、candidate、capsule、threshold 或 formal authority；
  - v9.3.5/9.4.0 工作、制品发布或版本号发布动作。
- do_not_touch:
  - 原工作区 `docs/9.3.5/README.md` 和 `docs/9.3.5/workitems/`；
  - FSScript public API/SPI、Bean 名称/类型和显式 `clear()`；
  - 已签收历史 evidence 的内容；只允许新增 supersession/replan 记录；
  - 未知 host 资源、凭证、endpoint、业务数据和非 run-owned 容器/卷/网络。

## Confirmed Decisions

| Decision | Rationale | Compatibility / Constraint |
|---|---|---|
| 使用 merge 保存两条历史 | release 线包含已签收 FSScript/CI 修复，diagnostic 线包含 49 个 package/Step4 状态修复；任一侧 cherry-pick 扁平化都会削弱审计谱系 | merge 第一父必须是 PR 当前接受头；不得 force-drop 任何一侧历史 |
| 旧 r4 失效并重建 Cdiag | r4 明确冻结“unchanged R7 source”，与后加入的两个受保护生产文件冲突 | 旧 run/证据不可复用；新 Cdiag 只允许 canonical spec 两字段激活 diff |
| FSScript 生产文件走 source amendment | `E_UNDECLARED_DRIFT` 是正确的 fail-closed 行为，不是 CI 假失败 | amendment 必须绑定 parent/current SHA 和 Git mode，并说明已独立签收语义 |
| 新测试不伪造 parent amendment | lifecycle test 在 frozen parent commit 不存在，当前 protected-path 算法不会把它列为 parent tracked drift | 保留并执行该测试；不扩大保护算法来迎合单个文件 |
| 冲突按语义合并并重算 hash | diagnostic package tool 已演进，release 线还有独立 portability/CI 修复 | 不选择整文件 ours/theirs；每个保留语义必须有 focused static/negative validation |
| 一次 fresh authority | source identity、tooling和拓扑均变化，旧产品测试证据不足以替代 release authority | expensive run 最多一次；任何失败进入 `NEEDS_REPLAN`，不得自动 retry |

## Acceptance Criteria

- [x] AC-1: 存在一个 clean/pushed integration merge，第一父为接管前 PR 接受头，
  第二父为接管时 diagnostic 头；双方独有提交均为 ancestors，原工作区保持未改。
- [x] AC-2: 旧 r4 work item 明确为 `NEEDS_REPLAN`，且没有复用其 run root、candidate、
  capsule、Cfreeze 或 formal authority。
- [x] AC-3: overlay protected drift 精确等于 declared amendments；新增两行分别绑定
  `DefaultExpFactory.java` 和 `FunTable.java` 的 frozen parent/current blob SHA 与 mode，
  `overlay_tool.py validate`、manifest 和 negative contract 均通过。
- [x] AC-4: 合并后的 Step 5 package integrity/runtime image inspect、portable artifact
  semantics、Step 6 CI contract 和工作流修复同时存在；Step 4/5/6 manifests 及嵌套
  digest bindings 全部闭合，无 conflict marker 或 stale hash。
- [x] AC-5: integration commit 为 diagnostic-ready，随后唯一 Cdiag 是其单父子提交；
  Cdiag 仅修改本文件的 `status: APPROVED -> ULTRA_EXECUTING` 和
  `readiness: APPROVED -> ULTRA_EXECUTING`，fresh clone 精确检出该头。
- [ ] AC-6: 新 diagnostic 一次完成全部 required lanes、source before/after seal、coverage
  observation、cleanup、candidate/capsule 和独立复核；F/E/S、cardinality、coverage 和
  privacy/ownership checks 全部满足既有 frozen contract。
- [ ] AC-7: direct-child Cfreeze、fresh formal、Step 5 package/release gate、Step 6 required
  CI 和 Step 7 signoff 按现有契约顺序完成；PR #124 最终 required checks 绿色。
- [ ] AC-8: FSScript 已签收 focused/normal/reverse/launcher 证据仍与最终测试产物对应；
  不修改 public API/SPI、POM、coverage floor/exclusion、skip 或测试执行顺序。

## Contract / Data / Security Constraints

- API or event contract: 不修改产品 public API/SPI、Spring Bean contract、发布事件或
  evidence schema；只扩展现有 declared amendment 实例和更新其 hash bindings。
- data and migration: 无数据库 schema/业务数据迁移；仅 run-owned 临时 fixture/evidence。
- compatibility and rollback: merge/replan 可由独立提交 revert，但回滚会恢复 PR 线与
  diagnostic 线分叉及 `E_UNDECLARED_DRIFT`；不得回滚已签收 FSScript 修复来使 CI 变绿。
- permissions and secrets: 使用接管授权操作 v934 runner、证据目录和 PR #124；持久证据
  继续只保存受治理的 hash/count/status，不记录凭证、host path 或 raw sensitive output。

## Test and Evidence Obligations

| Item | Risk | Required Validation | Required Evidence |
|---|---|---|---|
| AC-1..AC-2 | blocker | merge topology、ancestor、changed-path、旧 r4 authority absence audit | exact SHA/topology/status 摘要 |
| AC-3 | blocker | overlay validate/source-hash/negative suite | 精确命令、exit、amendment tuple |
| AC-4 | critical | Step 4/5/6 manifest validators、package/CI focused tests、conflict-marker scan | digest closure 和 test counts |
| AC-5 | blocker | Cdiag parent/path/diff/fresh-clone preflight | exact Cint/Cdiag SHA 与 diff |
| AC-6 | blocker | 一次完整 fresh diagnostic | run ID、finalizer、lane counts、coverage/capsule reviews |
| AC-7 | blocker | Cfreeze/formal/Step5-7 和 GitHub required checks | commit/run IDs、verdict、PR check summary |
| AC-8 | critical | 产物 identity + affected FSScript/launcher lane 复核，允许复用未受输入变化影响的独立签收证据 | accepted commit/JAR SHA 与必要重验结果 |

验证成本与循环控制：

- `<5m`：Git topology、JSON/TSV schema、manifest、overlay、CI/package focused static/negative；
  每次相关契约字节变化后重跑。
- `5-30m`：受影响 Python/shell test、FSScript focused/launcher trigger；focused 绿且字节
  冻结后各运行一次。
- `>60m`：fresh Step 4 diagnostic、formal 和后续完整 release-authority；每个 governed
  run ID 最多一次。只有源码、tooling、policy、selector、环境假设或产物绑定变化才使
  对应证据失效。
- 任一昂贵运行失败均停止并把本事项设置为 `NEEDS_REPLAN`；不得复用 partial lanes，
  不得自行开始第二次昂贵运行。

## Bug Context

- bug_source: acceptance-found
- severity: critical
- environment: PR #124；required CI run `29919094436`；release head
  `280db2fcdc1686943411b3b6ec5e1db5d0d3b79f`；diagnostic head
  `7f1a114c93aba809bdaff7715265660b3cce0500`（均于 2026-07-22 接管时复核）。
- current_behavior: CI 在 inventory/unit 前置校验以 `E_UNDECLARED_DRIFT` 拒绝
  `DefaultExpFactory.java`；旧 diagnostic r4 同时要求源码不变，无法合法验证当前 PR 头。
- expected_behavior: 已签收 FSScript 变更被显式纳入 Step 4 source amendment，旧诊断被
  supersede，新 fresh authority 验证合并后的真实 PR 字节。
- reproduction_steps:
  1. 在 PR 头运行 Step4 successor overlay validation；
  2. 观察首个未声明受保护漂移为 `DefaultExpFactory.java`；
  3. 枚举 protected changed set，确认 `FunTable.java` 同样漂移；
  4. 比较旧 r4 spec，确认其 unchanged-source 和唯一 Cdiag 前提已失效。
- reproduction_status: confirmed
- existing_evidence: CI run `29919094436`；FSScript fix `2f2b2d8b` 与独立 signoff
  `280db2fc`；diagnostic 分支 49 个独有提交和 R7 workflow reset acceptance。
- existing_tests: overlay fail-closed validation、Step 4/5/6 validators、FSScript lifecycle
  2/2、FSScript 403/403 normal/reverse、launcher 7/7、6/6、20/20。
- regression_protection: required
- waiver_reason_and_risk: N/A

## Risks and Open Questions

- known_risks:
  - merge 后 source seal、manifest 和 CI/package contract 形成传递 digest 链，漏更新任一层
    会在 preflight fail closed；
  - diagnostic 分支的 package tool 大幅演进，冲突解决必须保留双方已接受语义，不能只以
    文本无冲突为完成；
  - fresh Step 4 和数据库矩阵耗时长、依赖本机 Docker/端口，任何环境异常也按一次性
    authority 规则进入 replan；
  - GitHub required CI 可能在本地 authority 之后发现 runner-image 差异，仍需按事实停止。
- open_questions: none

## Ultra Execution Contract

- 先读取本文件、根 `CLAUDE.md`、FSScript signoff、旧 r4 work item、R7 reset acceptance、
  Step 4/5/6 contracts 和双方独有提交。
- 先提交本 `APPROVED` Cplan；它不授权 runner。之后只允许一个 integration merge，
  完成历史接续、冲突语义合并、source amendment 和 diagnostic-ready reset。
- integration merge 静态/负向验证通过并 clean/pushed 后，创建唯一 activation Cdiag；
  其 diff 必须只含本文件两字段变化。
- 只从 Cdiag fresh clone 启动一次新 run；严守现有 foreground、ownership、privacy、cleanup、
  candidate、capsule 和 direct-child Cfreeze 规则。
- 如需改变产品语义、public API/SPI、coverage/selector/DB set、安全边界，或昂贵运行失败，
  将本文件设为 `NEEDS_REPLAN` 并停止。
- 完成实现后填写 `Implementation Result` 并设为 `READY_FOR_SIGNOFF`；不得自行设置
  `ACCEPTED`。最终 release signoff 必须独立完成。

## Implementation Result

> 由接管执行会话填写。本次唯一 fresh diagnostic 已按 fail-closed 结束，
> 本事项不再授权 runner。

- implementation_summary: 已完成双历史 integration、两个 FSScript protected source
  amendments、传递 hash closure、focused validation、唯一 activation Cdiag 和 fresh-clone
  preflight。唯一授权 diagnostic 在 Unit `report-verify` 阶段以 `E_EXTRA_REPORT` 失败；
  新增且已通过的 `DefaultExpFactorySpringLifecycleTest` 未被冻结的 Step2 execution
  inventory 接纳，因此未进入 coverage observation、candidate、capsule、Cfreeze 或 formal。
- changed_paths: integration/Cdiag 历史见 `f67ed0534074a9182f7df8d15e1eafba69f40364`
  和 `54c774e636e33971b6cae612b8b89fb1acdb33c6`；本次 fail-closed 记录只修改本事项并新增
  `docs/9.3.4/evidence/step-4/step4-v934-fsscript-amendment-diagnostic-20260722-r5-report-inventory-fail-closed.md`。
- tests_and_results: integration focused validators 全绿，包括 overlay 20/20 negative、
  coverage 28/28 negative、package 117/117 negative、artifact negative 105/105、CI
  86/86 negative 及 Step4/5/6 manifests；fresh diagnostic run
  `step4-v934-fsscript-amendment-diagnostic-20260722-r5` 唯一执行并以 exit 1 结束。
  Unit Surefire XML 扫描无 failure/error/skipped，精确只读分类为
  `E_EXTRA_REPORT: com.foggyframework.fsscript.exp.DefaultExpFactorySpringLifecycleTest`；
  cleanup container/volume/network residue 均为 0，authority artifacts 均未产生。
- manual_or_experience_evidence: fresh clone 为 clean、non-shallow、精确 Cdiag；固定端口、
  pinned images、Docker/Compose、source seal、manifest、overlay、CI 和 sanitized-env
  preflight 全部通过。runner raw terminal output 未读取、未保留；终止后身份/权限检查通过并删除。
- deviations: none
- residual_risks: Step2 successor report inventory/discovery/cardinality 及 Step4 coverage totals、
  manifests 和所有传递 bindings 尚未纳入新增 lifecycle test。必须先形成并批准新的 replan，
  使用新的 Cdiag 和 run ID；不得 retry 或复用本次 partial lane。
- readiness: NEEDS_REPLAN

## References

- FSScript spec:
  `docs/9.3.4/workitems/BUG-fsscript-shared-default-spring-destroy.md`
- FSScript signoff:
  `docs/9.3.4/acceptance/BUG-fsscript-shared-default-spring-destroy-signoff-20260722.md`
- superseded diagnostic:
  `docs/9.3.4/workitems/BUG-step4-r7-single-activation-cdiag-fresh-diagnostic.md`
- controlling release item:
  `docs/9.3.4/workitems/FEATURE-v934-release-authority-and-ci.md`
