---
doc_role: version_execution_index
version: 9.3.5
status: ready / speed-forward
entry_gate: 9.3.4-owner-carried-forward
first_execution_gate: Gate-0-classification-debt-migration
validation_mode: affected-tests
final_acceptance: deferred-to-9.4.0-integrated-acceptance
recorded_at: 2026-07-24
---

# 9.3.5 引擎阶段与公共 API

## 当前决定

9.3.5 已按 repository owner 的 speed-forward 决定开放实施。9.3.4 使用既有证据
carry forward，不再等待 replacement Step 7、final authority pointer 或独立 version signoff。
Gate 0 仍是第一个开发切片，但通过其受影响测试后即可立即进入后续 API 工作。

## 已确认目标

- 沿用 `QueryExecutionPhase`，不再造与 `QueryStageType` 混淆的第二套阶段枚举。
- 外部查询入口收敛到稳定的 QueryFacade request/result DTO；context、JDBC engine、managed relation
  下沉为 internal/advanced port。
- 清点并消除未批准的 controller/runtime/addon 直接 loader 或模型查询绕过。
- 通过 port 拆解 pivot/compose/semantic 的反向依赖，并按
  planner/compiler/executor/catalog/adapters 组织实现。

## 推进顺序

1. 关闭 Gate 0：将已知 DB consumers 迁入受治理 DB lane，或去除外部 DB 依赖，并删除临时例外。
2. 收敛 QueryFacade request/result DTO 和外部调用入口。
3. 下沉 internal/advanced ports，清除 controller/runtime/addon bypass。
4. 拆解 pivot/compose/semantic 反向依赖和过大的实现边界。

每个切片运行受影响模块的编译、单元测试、必要集成测试；涉及公共签名或依赖方向时，
加跑对应 compatibility/architecture tests。测试通过即进入下一切片，不等待大型 authority。

## 基线材料

- [只读代码与 API 基线](code-inventory.md)
- [Gate 0 分类债务决策包](gate-0-classification-decision-record.md)
- 版本路线图：[9.3.1 → 9.4.0 迭代顺序评审](../9.3.1/roadmap-9.3.1-to-9.4.0.md)
- Gate 0 债务：[Unit MySQL 5.7 fixture 分类迁移](../9.3.4/workitems/DEBT-unit-mysql57-fixture-classification-migration.md)
- Canonical 交付契约：
  [9.3.4 → 9.4.0 Speed-Forward](../9.4.0/workitems/FEATURE-v934-v940-speed-forward.md)

## 开发完成条件

classification debt 关闭；无未批准 bypass、无新增包循环；公共 API compatibility test、
阶段轨迹回归和受影响测试通过。达到这些条件后直接进入 9.4.0，不单独启动版本级大型验收。
