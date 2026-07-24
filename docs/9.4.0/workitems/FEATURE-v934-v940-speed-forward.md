---
doc_type: delivery-spec
delivery_type: cross-module
version: 9.3.4-to-9.4.0
ticket: v934-v940-speed-forward
status: APPROVED
canonical: true
assurance_level: standard
execution_mode: speed-first
approved_by: repository-owner-via-user-request
approved_at: 2026-07-24
supersedes_active_release_authority_gates: true
final_integrated_acceptance: deferred-until-9.4.0-development-complete
open_questions: []
---

# Delivery Spec: 9.3.4 → 9.4.0 Speed-Forward

## Goal

在不继续消耗大型 release-authority/replay 运行的前提下，关闭 9.3.4 的开发阶段，
依次完成 9.3.5 公共 API 收敛和 9.4.0 SPI v2/模块化；每个开发切片由实际受影响测试
通过后直接进入下一切片，最终只在 9.4.0 开发目标完成后安排一次跨版本整体验收。

## Owner Decision

- 9.3.4 既有 Step 1–5、数据库矩阵、覆盖率、package/image 和 semantic replay repair
  证据足以支持继续开发。
- 不再执行 replacement Step 7、portable replay authority、source-seal replacement、
  final authority pointer 或 ordered multi-review chain。
- 上述未完成项作为 owner 明确接受的流程风险保留，不等同于功能缺陷，也不阻断 9.3.5。
- GitHub CI、tag、release、publish、branch protection 和 required checks 仍不在本交付范围。
- 本契约覆盖旧文档中与之冲突的 active gate、attempt budget、必须重跑和版本 entry 语句；
  历史运行事实、失败记录和既有 acceptance 文件保持不变。

## Version Outcomes

### 9.3.4：测试基础完成并转入延后整体验收

- 保留 Surefire/Failsafe 分层、五数据库/外部集成矩阵、聚合覆盖率、package/image
  和 deterministic semantic replay 修复。
- 状态为 `development-complete / owner-carried-forward`，不是新签发的 release authority。
- 不创建 final authority pointer，不再启动大型 Step 5/Step 7 runner。

### 9.3.5：引擎阶段与公共 API

1. 先关闭 Unit MySQL fixture classification debt，并删除对应临时例外。
2. 沿用 `QueryExecutionPhase`，不新增第二套阶段枚举。
3. 外部查询入口收敛到稳定的 QueryFacade request/result DTO。
4. context、JDBC engine、managed relation 下沉为 internal/advanced port。
5. 消除 controller/runtime/addon 未批准的 loader 或 `model.query()` 绕过。
6. 通过 port 拆解 pivot/compose/semantic 反向依赖，并向
   planner/compiler/executor/catalog/adapters 收敛。

### 9.4.0：SPI v2 与模块化

1. 按 `model-api → model-core → model-jdbc → model-starter → model-web` 渐进拆分。
2. `model-api` 不依赖 Spring、JDBC、implementation 或 web。
3. 引入小型、稳定的 backend/provider port，避免 mega-interface。
4. 旧 `foggy-dataset-model` 保留一个兼容周期，作为聚合/转发层。
5. 建立与实际迁移面匹配的 Addon TCK、starter context 和 launcher smoke。
6. 提供兼容基线与迁移说明，避免无过渡期破坏现有调用方。

## Delivery Rules

- 一个切片只解决一个明确边界；实现完成后运行受影响模块的编译、单元测试和必要集成测试。
- 受影响测试通过即可进入下一切片，不等待独立 authority、pointer、portable archive 或多层签收。
- 公共 API/SPI 或模块依赖发生变化时，必须同时运行对应 compatibility/architecture tests。
- 未实际运行的测试不得宣称通过；失败必须修复或记录为明确风险。
- 默认不运行完整 Step 4/5/7、五库全矩阵、全量 coverage authority 或全仓 release rehearsal。
- 可复用现有证据；文档、注释或无运行时影响的机械调整不要求大型测试。
- 每个版本只维护简短完成记录，不再为同一事实复制 evidence tree 或重建 source seal。

## Stage Acceptance Criteria

### 9.3.4 Carry-Forward

- [x] 核心测试分层和 required 数据库/外部测试机制已有实际成功证据。
- [x] semantic portable replay 工具冲突已修复且受影响正负测试通过。
- [x] replacement Step 5 已成功并完成既有签收。
- [x] owner 明确豁免新的 Step 7/final pointer，允许进入 9.3.5。

### 9.3.5 Development Complete

- [x] classification debt 关闭，临时例外删除，相关测试通过。
- [x] 外部查询入口和 DTO 收敛，未批准 bypass 清零。
- [ ] 无新增包循环；pivot/compose/semantic 的目标反向依赖已拆除。
- [ ] 受影响模块测试、公共 API compatibility tests 和阶段轨迹回归通过。

### 9.4.0 Development Complete

- [ ] 目标模块已按单向依赖顺序建立，依赖图无环。
- [ ] `model-api` 依赖边界满足约束。
- [ ] SPI/provider 迁移具有兼容转发层和迁移文档。
- [ ] 与实际改动对应的 Addon TCK、starter context、launcher smoke 通过。
- [ ] 达到 `READY_FOR_SIGNOFF`，随后才启动一次整体验收。

## Validation Budget

| 阶段 | 默认验证 | 大型运行 |
|---|---|---|
| 9.3.4 收尾 | 文档一致性、既有证据复用 | `0` 次 |
| 9.3.5 开发切片 | affected compile/unit/IT/API compatibility | `0` 次 |
| 9.4.0 开发切片 | affected reactor/module/TCK/smoke | `0` 次 |
| 9.4.0 最终候选 | 一次跨版本整体验收 | 最多 `1` 个候选运行 |

任何预计超过 30 分钟的 pre-final 验证都需要 owner 重新明确授权。最终整体验收失败时，
先修复并重跑失败项/受影响项；只有候选重新稳定后才决定是否重启整体候选，不自动进入
无限 authority retry。

## Final Integrated Acceptance

最终候选至少覆盖：

- 全 reactor 编译与测试；
- API/SPI compatibility 和模块依赖/循环检查；
- 受影响 Addon TCK、starter context 和 launcher packaging/smoke；
- 对实际改动涉及的数据库/外部路径执行必要集成回归；
- 迁移文档、兼容转发层和残余风险复核。

该验收不要求复活 9.3.4 Step 5/Step 7 authority、portable replay pointer 或 GitHub CI。
实现方只推进到 `READY_FOR_SIGNOFF`，不得自行签署 `ACCEPTED`。

## Stop Conditions

只有以下情况停止并请求重新授权：

- 目标超出 9.3.5 API 收敛或 9.4.0 SPI/模块化范围；
- 需要无兼容期地破坏生产 API/SPI；
- 需要降低安全、租户隔离、数据正确性或制品完整性检查；
- 必须引入新的外部基础设施或发布动作。

单个测试失败、实现返工或文档差异不是重规划理由，应在当前切片内直接修复。

## Implementation Result

- contract_frozen_at: 2026-07-24
- repository_state: 基于当日最新 `origin/main` 的独立 speed-forward 工作树。
- production_changes: none
- tests: docs-only contract change；未运行产品测试。
- readiness: `APPROVED` for implementation；9.3.4 已允许 carry-forward，9.3.5 可开始。
