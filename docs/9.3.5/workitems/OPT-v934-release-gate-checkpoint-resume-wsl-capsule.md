---
doc_type: delivery-spec
delivery_type: optimization
version: 9.3.5
ticket: v934-release-gate-checkpoint-resume-wsl-capsule
status: APPROVED
execution_gate: 9.3.4-version-signoff-then-9.3.5-gate0
canonical: true
execution_mode: ultra
approved_by: repository-owner-via-user-request
approved_at: 2026-07-20
updated_at: 2026-07-20
open_questions: []
---

# Delivery Spec: v934 Release Gate Checkpoint Resume and WSL Execution Capsule

## Document Purpose

- intended_for: normal-analysis / ultra-implementation / independent-signoff
- purpose: 固定 v934 release gate 的 dry-run、受治理 canary、断点恢复、证据依赖失效、单 run 编译检查点、WSL execution capsule、不可变数据库种子与后续实例池边界；本规划会话不修改任何 runner。
- canonical_path: `docs/9.3.5/workitems/OPT-v934-release-gate-checkpoint-resume-wsl-capsule.md`
- execution_prerequisites:
  1. 9.3.4 完成 version signoff；
  2. 9.3.5 Gate 0 classification-debt migration 按版本入口要求关闭；
  3. 当前推进 v934 runner 的会话完成交接，相关路径无并行 owner。

## Goal

- version_goal: 为 9.3.5 的高频回归和最终 authority 提供 fail-closed、可审计、可恢复的 release evidence workflow，避免末段失败后无条件重跑全部已通过阶段。
- target_outcome:
  - unchanged inputs 可从最细有效 checkpoint 继续；
  - changed inputs 由依赖图计算最早失效点，不接受人工随意 skip；
  - full run 前可先执行无副作用 dry-run 和固定样本 canary，尽早发现配置、编排、容器、清理与证据生成问题；
  - Step 4 已封存但 Step 5 失败时，可在新 attempt 中复用 Step 4 authority；
  - 同一 run 中 main/test bytecode 只进行契约要求的 fresh 构建，后续 Unit、Integration、DB 和 external lane 消费同一 sealed compile identity，不因重复清理无条件重编译；
  - WSL 可预热镜像、只读依赖缓存和不可变数据库 seed，但 authority run 必须从 seed 创建新的 run-scoped 可写数据库资源；
  - 每次运行可进入独立 WSL execution capsule，后续可扩展为受治理实例池；
  - 完整 fresh run 的既有覆盖率、测试库存和 release authority 强度不下降。

## Scope

- in_scope:
  - 引入 `workflow_id + attempt_id`，失败 run 保持 immutable，新 attempt 通过 manifest SHA 引用有效 checkpoint。
  - 定义 Exact Resume、Dependency Resume、Debug Retry 三种语义及其 authority 资格。
  - 定义 dry-run、canary、rehearsal、authority 四级执行模式，以及各自的副作用、证据和准入边界。
  - 建立版本化 canary catalog，以固定、可复现的小样本覆盖每个 step 和每个必需 lane 的真实执行路径。
  - 将输入身份拆为 runtime source、test source、build model、Step 4 tooling/contract、Step 5 tooling/contract、toolchain、container image 等独立摘要。
  - 为 main bytecode、Unit、Integration、Step 3 child/cell/lane、coverage report、model gate、Step 4 final、package、publish 建立 checkpoint 和依赖图。
  - 建立 run-scoped compile checkpoint：一次 fresh main compile、一次 fresh full-reactor test-compile、完整产物/输入 manifest、执行前后 hash 校验和 mutation fail-closed。
  - Unit、Integration 与 release 路径 DB matrix 首批复用 sealed compile checkpoint；external lane 后续通过不清理 sealed 产物或基于同一 capsule 创建 reflink/COW workspace 消除重叠模块重复构建。
  - 为 WSL READY 模板定义可预热资产：Docker image layer、JDK/Maven、只读依赖缓存、版本绑定且不可变的数据库 dump/backup/seed；每次 authority attempt 创建独立 container/network/volume 并验证 image/seed/schema identity。
  - 首批先实现 Step 4 Final -> Step 5 package/release 恢复，再扩展 exact child resume、lane resume 和 dependency-aware invalidation。
  - 建立单 run 独立 WSL execution capsule；稳定后设计 READY/RUNNING/FAILED/RESETTING/BROKEN 实例池。
  - 为共享 Docker Desktop daemon 建立 run-scoped name/label/network/volume/port 和 cleanup 边界；是否采用独立 dockerd 留作后续受控决策。
  - 在恢复语义稳定后，实施 compile checkpoint；完成可写目录隔离后再评估 DB/external cell 和 reporter 双重 replay 并行。
- affected_modules:
  - root v934 verification entrypoints；
  - `scripts/v934/step2`、`step3`、`step4`、`step5` authority/evidence tooling；
  - coverage reporter build support；
  - WSL host-side orchestration and runner lifecycle tooling；
  - `docs/9.3.5`。
- external_dependencies: WSL2 host control、Docker Desktop/Engine、Maven/JDK/Python、现有 required database/external images。

## Non-Goals

- out_of_scope:
  - 本 work item 落档时不修改脚本、POM、workflow、Docker 配置或运行中的证据目录。
  - 不改变 9.3.4 当前 release authority、历史 run 结论或 fresh revalidation 要求。
  - 不允许将 Debug Retry 或 changed-input 的旧 checkpoint 宣称为 final authority。
  - dry-run 或 canary 通过不代表 full inventory、coverage 或 release authority 通过。
  - 不实施 9.3.5 QueryFacade/public API 重构或 9.4.0 模块拆分。
  - 不在第一阶段要求多 WSL 并发，也不默认给每个 WSL 启动独立 dockerd。
  - 不以持续运行、跨 run 共享且可写的 MySQL/PostgreSQL/SQL Server 实例替代 authority 的 fresh run-scoped database cell。
  - 不把未验证的历史 `target/`、宿主工作区或 canary 编译产物直接作为 authority compile checkpoint。
- do_not_touch:
  - 不降低 coverage threshold、required inventory、F/E/S=0、sensitive scan、cleanup、source/toolchain seal 或 pointer no-clobber 约束。
  - 不直接启用 Maven `-T`、Surefire/Failsafe generic parallel 或 `forkCount > 1`。
  - 不重新打开或覆盖失败 attempt 的原目录，不允许手工复制零散 exec/report 伪造 checkpoint。
  - 不允许随机抽样、按发现顺序取前 N 个测试，或为使 canary 通过而降低 production coverage threshold、required inventory 或失败约束。
  - canary 产生的测试证据、coverage exec、report 或 checkpoint 不得拼入 rehearsal/authority。
  - 当前另一个会话完成 v934 runner 前，不进入实现。
  - 不允许并发 lane 共享可写 `target/`、Surefire/Failsafe report、JaCoCo exec、固定端口或可写数据库 volume。

## Confirmed Decisions

| Decision | Rationale | Compatibility / Constraint |
|---|---|---|
| 恢复能力优先于全量提速 | 主要损失来自末段失败后的整链重跑 | fresh run 性能优化排在恢复语义之后 |
| 失败 attempt immutable，新 attempt 引用旧 checkpoint | 保留 no-clobber 和完整审计链 | 不原地追加、修补或覆盖失败 run |
| Exact Resume 只接受完全一致输入 | transient failure 可安全续跑 | source/toolchain/contract/image 任一依赖漂移即失效 |
| Dependency Resume 使用拆分摘要和显式依赖图 | 允许仅改变 Step 5 tooling 时复用 Step 4 | 依赖不明一律 fail closed，从更早 checkpoint 重跑 |
| Debug Retry 永远 non-authoritative | 允许开发者快速定位问题 | 不生成 final pointer，不进入 release publication |
| 首个交付切口是 Step 4 Final -> Step 5 | 收益最大且边界最清晰 | 必须重新验证 sealed Step 4 artifact 和当前 runtime tree binding |
| checkpoint 细化到 DB cell 和 external lane | 单 lane 失败不应强迫重跑全部矩阵 | final inventory 仍要求 exact set/cardinality 和统一 source/class universe |
| WSL 是 execution capsule，不是证据本体 | workspace 生命周期与 checkpoint 生命周期解耦 | checkpoint 必须可导出到持久化 evidence store 后独立验证 |
| 第一版 WSL 复用共享 Docker daemon | 复用镜像缓存并降低运维成本 | 固定端口、容器名、network、volume、cleanup 必须实例作用域化 |
| WSL 池从 2 个预创建实例起步 | 避免每次 import/export 的冷启动成本 | 每实例同一时间只运行一个 authority workflow |
| dry-run 与 canary 是不同模式 | dry-run 验证计划但不执行副作用；canary 真实执行最小样本 | 两者都不具备 release authority 资格 |
| canary 使用固定、版本化目录清单 | 避免随机样本导致偶现漏检和不可复现 | catalog 变更必须评审并产生独立摘要 |
| canary 复用 full run 的执行与清理路径 | 样本应验证真实编排，而不是另一套简化脚本 | 只允许参数化 inventory，不允许复制一套旁路 runner |
| canary evidence 与 full evidence 隔离 | 样本覆盖率和库存不完整 | 可复用只读依赖/镜像缓存，不得复用 authority 测试产物或 checkpoint |
| 单 run 中每个 compile identity 只进行一次 fresh test-compile | Unit 与 Integration 当前重复删除并生成同一批测试字节码 | 当前 Unit/Integration/DB identity 应共用；任何 mutation 或 compile-effective profile/build-model 漂移均失效或派生新 identity |
| 首批继续走 Maven lifecycle | 降低跳过 resource/generator/plugin binding 的风险 | compile/testCompile 应成为经验证的增量 no-op；直接调用 Surefire/Failsafe 仅可作为后续独立决策 |
| sealed compile capsule 不可被 lane 共享写入 | Maven plugin、资源复制或 clean 可能污染后续证据 | 串行阶段执行前后校验 hash；并发阶段必须使用 reflink/COW 独立 workspace 和独立报告路径 |
| authority WSL 只预热不可变资产 | 镜像下载、依赖下载和数据库 fixture 可提前准备 | 可预热 image/cache/seed，不得复用跨 run 可写数据库状态或 authority 测试证据 |
| 数据库 seed 每次恢复到新 volume | 兼顾启动/fixture 成本与 run freshness | 校验 image digest、seed digest、DB kind/version、schema sentinel 和 run ownership，cleanup 仍为硬门 |
| 数据库并行优先于共享常驻数据库 | 正向实例启动合计约两分钟，测试执行才是主要成本 | 动态端口、报告、coverage、workspace 和 Docker resource 隔离完成前不得并发 authority |

## Measured Baseline and Bottleneck Hypothesis

以下数据来自 `step5-rehearsal-20260720-r2` 单次代表性运行，只用于确定优化顺序，不是性能验收基线；实施前后仍须各取得至少三次 warm host/cache 的可比数据。

| Phase | Observed Duration | Current Cost Hypothesis |
|---|---:|---|
| full observed run | 46:48 | 末段失败会放大整链重跑成本 |
| Unit | 11:10 | 进入真实测试前约 3:11，主要为 full-reactor test-compile 及少量 seal/report 准备 |
| Integration | 8:28 | 再次出现约 3:11 的相同 test-compile 前置区间 |
| Addon | 1:55 | lane 执行与 fixture |
| Database Matrix | 12:30 | 测试 callback 约 6:36、负向探针约 2:00、正向数据库启动/初始化合计约 2:13；release 路径未全量删除测试字节码 |
| External Matrix | 9:57 | 四条 lane 串行 clean 重叠 module closure，后续 `verify` 重复构建成本混入 lane 时间 |

已确认优化假设：Unit/Integration 的第二次 full-reactor test-compile 是首个确定收益，约可减少 3 分钟；DB release 路径的增量 test-compile 预计只占几十秒；external lane 的重复构建可能再减少数分钟，但必须通过 compile/test/startup/test/cleanup 分段计时后才能形成承诺。总体 6–9 分钟仅作为容量估算，不作为 acceptance threshold。

## Compile Checkpoint Contract

- producer identity: runtime source、test source、build model/effective POM、active Maven profile、JDK/Maven/compiler、generated-source producer、resource inputs 和 relevant environment 均进入摘要。
- compile realm: 只有 compile-effective 输入完全一致的 consumer 才能共用 capsule；仅改变数据库连接等 runtime property 不派生新 identity，改变 generator、resource、compiler 或 profile binding 时必须产生独立 identity，且每个 identity 仍只构建一次。
- sealed outputs: 至少包括每个受治理 module 的 `target/classes`、`target/test-classes`、generated sources/resources、compiler status，以及 exact file inventory、size 和 SHA256；不能只封存 `.class`。
- lifecycle behavior: 首批保留现有 Maven test/verify lifecycle；在 sealed 产物有效时 compile/testCompile 必须经 instrumentation 证明为 no-op 或仅执行允许的确定性校验。
- lane behavior: lane 只能清理自己的 reports、exec、temp 和 run-scoped fixture；不得删除或修改 sealed output。执行前后产物 hash 不一致时，本 lane 及所有后续 checkpoint 失效。
- external behavior: external lane 若无法在共享 workspace 中保持 sealed output 不变，必须从同一 compile capsule 创建独立 reflink/COW workspace；不得让一个 lane 的 `clean` 删除其他 lane 将要复用的产物。
- concurrency boundary: 串行复用稳定前不引入 compile consumer 并发；并发时每 lane 使用独立 writable overlay、reports、JaCoCo exec 和 temp root，只共享只读 capsule identity。

## WSL Database Preparation Contract

- READY instance 可包含：已拉取且 digest 已记录的 vendor images、JDK/Maven、只读 Maven 基础缓存、数据库初始化工具，以及不可变/version-bound seed archive、logical dump 或 backup。
- authority attempt 必须创建新的 run-scoped container、network 和 writable volume；seed restore/clone 是本 attempt 的 producer，并生成 resource ownership、image digest、seed digest、restore result 和 schema sentinel receipt。
- MySQL datadir snapshot 仅在精确 engine/image/version 绑定且证明 crash-consistent 时允许；无法证明时使用 logical dump。PostgreSQL 使用受版本治理的 dump/base archive，SQL Server 使用受治理 `.bak`，SQLite 使用不可变种子文件复制。
- READY WSL 中持续运行或跨 attempt 复用的可写数据库只允许用于 debug/canary，必须标记 `authority_eligible=false`；authority planner 遇到此类资源必须拒绝或重新创建 fresh cell。
- seed、镜像和依赖缓存是性能资产，不是测试通过证据；其生命周期不得绕过负向 probe、schema/data validation、cleanup 和 evidence export。
- 数据库 cell 并行必须等待动态端口、独立 Docker resource、独立 report/exec、独立 workspace/fixture 和确定性 aggregate 全部通过双实例 collision/cleanup 验证。

## Dry-Run and Canary Contract

| Mode | Real Maven / Docker execution | Inventory | Authority eligible | Required behavior |
|---|---|---|---|---|
| dry-run | 否 | 仅解析计划 | 否 | 校验输入、依赖图、checkpoint、工具链和资源分配；输出脱敏执行计划，不修改 canonical run/evidence/pointer |
| canary | 是 | 固定、版本化的小样本 | 否 | 使用真实 runner、容器、清理和证据代码；任一失败、跳过或残留均失败 |
| rehearsal | 是 | full exact inventory | 否 | 完整预演，不写 final pointer、不发布外部 release |
| authority | 是 | full exact inventory | 是 | 满足全部 release contract 后才可生成 final authority |

dry-run 必须至少输出将执行的 step DAG、输入摘要、checkpoint 复用/失效原因、预计命令、WSL/容器/网络/volume/动态端口分配和预期证据路径。默认不得启动 Maven、Docker container 或测试 JVM，也不得创建或更新 candidate/final pointer；如保留计划 receipt，只能写入独立 non-authoritative plan root。

canary 不是“成功概率较高的快跑”，而是 full chain 的受治理探针。其 catalog 必须固定名称、selector、fixture/image、预期结果和 catalog SHA；不得随机挑选或依赖文件系统/Maven discovery 顺序。canary 可以预热只读依赖和镜像缓存，但其 classes、exec、report、checkpoint、package 和状态文件不得被 rehearsal/authority 当作已通过证据复用。

### Governed Canary Coverage

| Step / Lane | Minimum Canary Obligation |
|---|---|
| source/toolchain seal | 真实执行 repository、contract、schema、toolchain 和 source identity 校验 |
| compile/test-compile | 编译固定的代表性 reactor closure，并验证 sealed output 与 class universe 生成路径 |
| Unit | 固定覆盖 plain unit、Spring/context 与数据库 fixture consumer 的代表性测试 |
| Integration | 固定覆盖一个 hermetic Failsafe 用例和一个 lifecycle/database integration 用例 |
| DB matrix | 每个 required DB kind 都实际启动/probe 精确镜像，执行至少一个固定 dialect/query assertion，并完成清理 |
| External lanes | Redis、Mongo、MySQL、Vector 每条必需 lane 各执行固定 health + representative assertion，并完成清理 |
| Addon | SQLite 与 MySQL addon 路径各执行固定代表样本 |
| Coverage/report | 聚合 canary exec，验证 session/inventory/provenance 和 deterministic replay；production coverage threshold 标记为 not-applicable，不得降低阈值 |
| Model gate | 用冻结 fixture 验证 plugin wiring 和 fail-closed 行为；不得把样本 coverage 宣称为 production threshold pass |
| Package/publish | 仅验证 non-release canary package 和本地 sandbox publication path；禁止远端发布及 candidate/final pointer 更新 |

准入规则：dry-run 失败则不得进入 canary；canary 失败则不得进入 rehearsal/authority。canary 通过只表示脚本、环境和代表路径具备继续运行的条件，不表示 full run 必然通过。支持 focused canary 时，也必须明确其未覆盖 step，并保持 non-authoritative。

## Checkpoint and Invalidation Contract

| Checkpoint | Required Inputs | Invalidated By |
|---|---|---|
| source/toolchain seal | repository inventory、Git identity、JDK/Maven/JaCoCo | 任一身份或内容漂移 |
| main bytecode | runtime source、build model、compiler realm | production/POM/compiler drift |
| test bytecode | test source、main bytecode、test build model | test/main/POM/compiler drift |
| Unit / Integration | sealed classes、selectors、fixture/image contract | 对应 class/config/image/runner drift |
| Step 3 cell/lane | sealed classes、cell/lane contract、image digest | 对应输入漂移或 cleanup 不完整 |
| coverage report | exact exec set、class universe、reporter contract | exec/class/reporter drift |
| model gate | aggregate exec、threshold/model plugin | aggregate/threshold/plugin drift |
| Step 4 final | 全部 Step 4 manifests 和 final verifier | 任一上游 checkpoint 无效 |
| package | Step 4 final、runtime tree、Step 5 tooling | runtime/package tool/contract drift |
| publish/pointer | verified package、policy、target state | package/policy/remote state drift |

默认规则：无法证明某 checkpoint 不依赖已变化输入时，该 checkpoint 及所有下游必须失效。

## Delivery Phases

1. Baseline and schema：补齐 compile/test/startup/fixture/test/cleanup 分段计时、execution plan、canary catalog、checkpoint schema、dependency graph 和 negative fixtures；建立至少三次可比 fresh baseline。
2. Dry-run：完成无副作用计划解析、资源冲突预检、checkpoint 复用解释和脱敏 plan receipt。
3. Canary：以同一 runner 路径完成每 step/lane 的固定样本链、证据隔离、失败阻断和清理验证。
4. Step 5 recovery：新 attempt 可消费同源 sealed Step 4 Final，从 package/verify/publish 继续。
5. Compile checkpoint：一次 fresh full-reactor test-compile 后封存完整输出；Unit/Integration 先复用，随后接入 release DB matrix，并以 mutation negative suite 验证 fail-closed。
6. Exact child resume：Unit、Integration、Step 3、coverage report、model gate 支持 unchanged-input 恢复。
7. External compile reuse：消除四条 external lane 对重叠 module closure 的重复构建；必要时使用同一 capsule 派生 reflink/COW workspace。
8. Granular resume：DB cell、external lane 和 reporter replay 支持独立 checkpoint。
9. Dependency Resume：拆分输入摘要，按依赖图计算 earliest invalid checkpoint。
10. WSL capsule and database seed：模板、不可变 seed、按 attempt 创建 fresh database resource、领取、执行、失败保留、证据导出、reset/recycle 生命周期闭环。
11. WSL pool and optional concurrency：2 实例起步；完成 Docker/port/report/coverage/workspace 隔离后再并发 DB/external cell，禁止以共享常驻可写数据库实现提速。

阶段 2、3 必须先独立验收，随后优先交付 Step 5 recovery；不得以等待完整 WSL 池为由延迟该恢复能力。

## Acceptance Criteria

- [ ] AC-1: 每个 checkpoint 都有固定 schema、producer attempt、输入依赖、输出清单、SHA256、完成状态和 cleanup 状态；缺字段、重复字段、symlink、路径越界或内容漂移均拒绝。
- [ ] AC-2: `workflow_id` 下的 attempt 不可覆盖；resume 创建新 attempt，并通过父 manifest hash 形成可独立复算的 lineage。
- [ ] AC-3: 在 Unit、Integration、Step 3、coverage report、model gate 和 Step 5 注入失败后，unchanged-input Exact Resume 只重跑失败 checkpoint 及其下游。
- [ ] AC-4: production/test/POM/toolchain/contract/image 分别发生漂移时，planner 精确失效其依赖 checkpoint；无法归类的漂移从 source/toolchain seal 重跑。
- [ ] AC-5: Step 4 已成功封存而 Step 5 失败时，新 attempt 不重新执行 Step 4；它必须重新验证 Step 4 final artifact、source/runtime binding、toolchain/contract 和 sensitive/cleanup receipts 后再 package。
- [ ] AC-6: DB 或 external 单 cell/lane 失败时，仅在 source/class/contract/image 未变且 cleanup 完整的条件下复用其他 lane；最终 exact inventory 和 aggregate totals 不变。
- [ ] AC-7: Debug Retry 明确标记 non-authoritative，不能生成 candidate/final pointer、release archive 或 success summary。
- [ ] AC-8: fresh 和 resumed final authority 均保持既有 exact `23 exec / 48 sessions`、required report/testcase inventory、coverage threshold、class universe、F0/E0/S0 和 deterministic replay 约束；契约数字若被后续 accepted baseline 替换，应引用唯一新基线而非硬编码双源。
- [ ] AC-9: 单 WSL capsule 在实例内 ext4 workspace 执行，失败后可保留并 resume；成功证据导出后可从宿主独立 verify，销毁实例不影响证据有效性。
- [ ] AC-10: 共享 Docker daemon 模式下，两个实例的 name/label/network/volume/port/cleanup 无碰撞、无跨 run 删除；固定端口未完成作用域化前禁止并发 authority。
- [ ] AC-11: WSL 池至少证明 READY -> RUNNING -> FAILED/RESETTING -> READY 及 BROKEN 隔离；一个实例不能同时领取两个 workflow。
- [ ] AC-12: full fresh run 中位耗时不得因 checkpoint 框架回退超过 10%；优化阶段以约 40–50 分钟为目标而非降低证据强度的硬门。
- [ ] AC-13: tampered/stale/cross-workflow/cross-source/cross-toolchain/cross-image/cross-attempt checkpoint、残留容器、占用端口、共享 Maven 可写缓存冲突全部有自动化 fail-closed negative coverage。
- [ ] AC-14: dry-run 不启动 Maven test、Docker container 或测试 JVM，不修改 canonical run/evidence/pointer；相同输入生成确定性的脱敏执行计划，配置缺失、资源冲突、无效 checkpoint 或无法解析的依赖均 non-zero。
- [ ] AC-15: canary catalog 固定且版本化，覆盖每个 step 和每个 required DB/external/addon lane；不存在随机抽样、隐式 discovery 顺序或“取前 N 个”行为。
- [ ] AC-16: canary 通过真实 runner、容器和 cleanup 路径执行；任一样本失败、意外 skip、清理残留、证据缺失或 deterministic replay 不一致均 non-zero。
- [ ] AC-17: dry-run/canary 输出统一标记 `authority_eligible=false`；canary 不生成 release archive、candidate/final pointer 或远端 publication，其任何执行证据/checkpoint 都不能被 rehearsal/authority 复用。
- [ ] AC-18: dry-run 或 canary 失败会阻断 full run；通过时仅解除预检阻断，不减少 authority 的 exact inventory、coverage、F/E/S、cleanup 或 seal 要求。
- [ ] AC-19: 在 warm host/cache 的可比条件下，dry-run 目标中位耗时不超过 1 分钟，full-chain canary 目标中位耗时不超过 15 分钟；超过目标需记录瓶颈和 catalog 调整依据，但不得删减 required lane 覆盖。
- [ ] AC-20: fresh authority attempt 中每个 compile-effective identity 的 main/test compile producer 各只执行一次；当前 Unit、Integration 与 release DB matrix 必须证明并消费相同 sealed compile manifest，后续阶段不得删除或重新生成该 manifest 管辖的产物。
- [ ] AC-21: compile checkpoint 覆盖 classes、test-classes、generated sources/resources、compiler/build/toolchain 输入和 exact hashes；篡改文件、Maven profile/POM/source/toolchain 漂移或 lane 写入 sealed output 均自动失效并 non-zero。
- [ ] AC-22: external lane 不再串行删除并重复构建同一 sealed module closure；如使用 reflink/COW workspace，各 lane 必须证明 parent capsule identity 相同、writable output 隔离且 aggregate class universe/coverage 不变。
- [ ] AC-23: READY WSL 可预热 image/cache/immutable seed，但每个 authority attempt 都创建新的 writable DB volume/container/network，并生成 image/seed/version/schema/ownership/cleanup receipts；跨 run 可写数据库不得 authority eligible。
- [ ] AC-24: DB seed stale/tampered/version-mismatch、复用旧 volume/container、restore 不完整、schema sentinel 不符和 cleanup 残留均有 fail-closed negative coverage。
- [ ] AC-25: 性能报告分别记录 compile、DB startup/restore、negative probe、test callback 和 cleanup；至少三次前后对比，并分别报告确定收益与未证实估算，不得用降低 inventory 或复用可写状态达成绩效目标。
- [ ] AC-26: 如启用 DB/external 并行，两个 WSL/attempt 及同 attempt 多 cell 在动态端口、container/network/volume、workspace、report、JaCoCo exec 和 aggregate 顺序上均无碰撞；任一隔离项未证明前默认串行。

## Contract / Data / Security Constraints

- API or event contract: checkpoint、lineage、resume plan、pool lease 和 evidence export schema 均视为版本化 build/release contract；未知 schema version 必须拒绝。
- data and migration: 无 production data migration；已有 run 目录只读保留，新布局通过新 workflow root 引入，不原地迁移历史 authority。
- compatibility and rollback: 保留 fresh-only 入口作为回滚路径；resume planner 或 capsule 异常时可退回完整 fresh run，但不得降级为人工 skip。
- permissions and secrets: WSL、日志、checkpoint、Docker metadata 和导出 archive 继续执行 sensitive scan；不得固化 token/password/credential 或把宿主私有路径作为 portable identity。

## Test and Evidence Obligations

| Item | Risk | Required Validation | Required Evidence |
|---|---|---|---|
| AC-1/AC-2/AC-4 | critical | schema/lineage/invalidation unit + mutation matrix | exact commands、negative case inventory、planner output and manifest hashes |
| AC-3/AC-5/AC-6 | critical | real fault injection followed by resume | parent/new attempt IDs、skipped/replayed checkpoint list、raw status and final verifier |
| AC-7/AC-8 | critical | authority qualification and full fresh/resumed comparison | exact inventory、coverage XML、class universe、pointer state、F/E/S totals |
| AC-9/AC-10/AC-11 | critical | WSL lifecycle, kill/restart, two-instance Docker collision and cleanup tests | host lease records、instance state transitions、Docker resource inventory、portable replay |
| AC-12 | major | at least three comparable fresh runs before/after | phase timing ledger、median comparison、CPU/memory/container observations |
| AC-13 | critical | stale/tamper/splice/port/cache/resource negative suite | expected non-zero exits and durable fail-closed receipts |
| AC-14/AC-15 | critical | deterministic dry-run replay、catalog mutation/order/randomness negative tests | execution-plan、catalog SHA、zero-side-effect proof、expected non-zero exits |
| AC-16/AC-17/AC-18 | critical | real canary positive/fault/skip/cleanup run followed by authority isolation checks | canary manifest、sample inventory、resource cleanup、pointer/archive absence、reuse rejection receipt |
| AC-19 | major | at least three warm dry-run and full-chain canary runs | phase timing ledger、median and slowest-step analysis |
| AC-20/AC-21 | critical | repeated lifecycle、bytecode/resource mutation、profile/POM/source/toolchain drift suite | compile invocation ledger、sealed inventory/hash、before/after mutation receipts、expected non-zero exits |
| AC-22 | critical | four external lanes against shared capsule or isolated COW workspaces | parent capsule hashes、per-lane workspace/report/exec inventory、class universe and aggregate comparison |
| AC-23/AC-24 | critical | immutable seed positive restore plus stale/tamper/version/reuse/cleanup negative matrix | image/seed digests、new volume/container ownership、schema sentinel、cleanup and rejection receipts |
| AC-25/AC-26 | major | at least three comparable fresh runs and optional two-instance/multi-cell concurrency run | phase timing ledger、median/delta、CPU/IO observations、collision inventory and deterministic aggregate |

所有声明通过的测试必须实际运行并记录精确命令、结果、run/workflow/attempt ID 和证据路径。无法运行的项目不得标记完成。

## Risks and Open Questions

- known_risks:
  - checkpoint 依赖声明过窄会错误复用旧证据；默认必须偏向失效而非复用。
  - attempt lineage 若只校验路径、不校验内容身份，会重新引入 cross-run splice 风险。
  - 独立 WSL filesystem 不等于独立 Docker daemon；固定宿主端口是实例池并发的首要阻断点。
  - 多实例共享可写 Maven repository 可能产生部分下载、metadata 或 plugin cache 竞争；基础缓存应预热，每实例保留独立可写层。
  - 实例失败现场长期保留会消耗 VHD/镜像空间；池必须有 TTL、容量上限和人工 pin 机制。
  - canary 通过可能带来错误安全感；UI/summary 必须显著显示 sampled、non-authoritative 和未覆盖 inventory。
  - canary 若复制一套简化 runner 会与 full path 漂移；必须共享编排、资源隔离、cleanup 和 evidence producer，仅替换受治理 inventory。
  - canary catalog 随时间膨胀可能退化为第二套 full run；catalog 变更需同时记录代表性理由与耗时预算。
  - Maven lifecycle 中的 generator、resource copy 或 plugin binding 可能改写 sealed output；未完成 mutation inventory 前不能假定 `verify` 是只读 consumer。
  - 共享可写 `target/` 会让并发 lane 互相污染或删除产物；并发必须使用只读 capsule 加独立 writable overlay。
  - MySQL/PostgreSQL 数据目录快照与 engine/image patch version 强耦合，且可能不是 crash-consistent；无法证明时退回逻辑 dump/backup restore。
  - 数据库预热的直接收益受限：当前正向实例启动/初始化合计约两分钟，若不并行测试 callback，不能把 WSL 常驻数据库当作主要优化来源。
- open_questions: none

## Ultra Execution Contract

- 先读取本文件、届时已签收的 `docs/9.3.4` final authority 文档、`docs/9.3.5/README.md`、项目 `CLAUDE.md` 和相关专项技能。
- 开工前确认 execution prerequisites 全部满足，并确认当前没有其他会话修改 v934 runner；否则不得修改脚本。
- 在 scope 内自主决定 checkpoint 文件布局、planner 实现语言、WSL host controller 结构和局部模块归属，但必须保持 confirmed decisions 与依赖失效语义。
- 优先按 Delivery Phases 分批交付；每批都需独立 positive/negative evidence，不能用最终 full run 掩盖 checkpoint 级缺口。
- dry-run、canary 和 full run 必须共享同一执行图及 producer；若只能通过复制旁路 runner、随机抽样、降低 production threshold，或让 canary evidence 进入 authority 才能实现，设置 `NEEDS_REPLAN` 并停止。
- compile checkpoint 必须先证明完整输入/输出身份和 lifecycle mutation 边界；若只能通过共享可写 `target/`、跳过未建模的 generator/resource/plugin binding，或接受 hash 漂移才能复用，设置 `NEEDS_REPLAN` 并停止。
- WSL database preparation 必须保持每 attempt 新建可写资源；若只能通过跨 run 复用持续运行的可写数据库、旧 volume 或未绑定版本的 datadir 才能提速，设置 `NEEDS_REPLAN` 并停止。
- 如实现需要降低既有 authority 契约、改变 required inventory/coverage minima、允许 changed-input 旧测试成为 final authority、默认独立 dockerd，或提前改变 9.3.4 运行中脚本，设置 `NEEDS_REPLAN` 并停止。
- 完成后填写 `Implementation Result`，记录 changed paths、精确测试、attempt lineage、WSL/Docker evidence、deviations 和 residual risks，并将状态改为 `READY_FOR_SIGNOFF`；不得自行设置 `ACCEPTED`。

## Implementation Result

> 由 Ultra 执行会话填写。

- implementation_summary: pending
- changed_paths: pending
- tests_and_results: pending
- manual_or_experience_evidence: pending
- deviations: none
- residual_risks: pending
- readiness: pending

## References

- requirement / issue: 用户确认的 v934 gate 耗时、dry-run/canary、末段失败全量重跑、单 run 编译复用和 WSL 数据库预准备规划（2026-07-20）
- architecture / glossary: `docs/9.3.4/contract/test-lane-evidence-contract.md`, `docs/9.3.4/workitems/FEATURE-v934-release-authority-and-ci.md`
- related work items: `docs/9.3.4/workitems/DEBT-unit-mysql57-fixture-classification-migration.md`, `docs/9.2.9/workitems/P1-wsl-docker-test-environment-stabilization.md`
