---
doc_role: version_preexecution_index
version: 9.3.5
status: queued / planning-baseline-only
entry_gate: 9.3.4-version-signoff then Gate-0-classification-debt-closure
baseline_commit: 26081a3b4853914de8e6effe9a21b1353d590917
recorded_at: 2026-07-20
---

# 9.3.5 引擎阶段与公共 API

## 当前决定

本目录只记录只读代码基线和未来验收边界，**不**授权修改生产代码、测试 lane、POM、执行库存或模块边界。
9.3.5 仍为 `queued`：必须先完成 9.3.4 version signoff，随后关闭 Gate 0 的 Unit MySQL fixture
classification debt，才可进入实现。

## 已确认目标

- 沿用 `QueryExecutionPhase`，不再造与 `QueryStageType` 混淆的第二套阶段枚举。
- 外部查询入口收敛到稳定的 QueryFacade request/result DTO；context、JDBC engine、managed relation
  下沉为 internal/advanced port。
- 清点并消除未批准的 controller/runtime/addon 直接 loader 或模型查询绕过。
- 通过 port 拆解 pivot/compose/semantic 的反向依赖，并按
  planner/compiler/executor/catalog/adapters 组织实现。

## 硬门与禁止项

1. Step 4 feature signoff 只是 9.3.4 的前置；9.3.5 开工仍须等待 **9.3.4 version signoff**。
2. Gate 0 必须在 9.3.5 version acceptance 前完成：将已知 DB consumers 迁入受治理 DB lane，或去除
   它们的外部 DB 依赖，并删除临时例外。
3. 在这些门完成前，不得改动 QueryFacade 签名、迁移调用路径、引入 phase/stage、改变测试执行库存，或
   把任何结果宣称为 9.3.5 实现完成。

## 基线材料

- [只读代码与 API 基线](code-inventory.md)
- 版本路线图：[9.3.1 → 9.4.0 迭代顺序评审](../9.3.1/roadmap-9.3.1-to-9.4.0.md)
- Gate 0 债务：[Unit MySQL 5.7 fixture 分类迁移](../9.3.4/workitems/DEBT-unit-mysql57-fixture-classification-migration.md)

## 将来的准出

无未批准 bypass、无新增包循环、公共 API compatibility test 与阶段轨迹回归全绿；这些都是将来
9.3.5 实施后的验收项，而非本次静态基线的结论。
