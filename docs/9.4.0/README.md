---
doc_role: version_execution_index
version: 9.4.0
status: queued-behind-9.3.5-development / speed-forward
entry_gate: 9.3.5-development-complete
validation_mode: affected-tests-then-one-integrated-acceptance
recorded_at: 2026-07-24
---

# 9.4.0 SPI v2 与模块化

## 当前决定

9.4.0 在 9.3.5 开发目标完成后立即开始，不等待独立的 9.3.5 version acceptance。
实施按小切片推进，每个切片通过受影响测试即可继续；只在全部 9.4.0 开发目标完成后
安排一次跨版本整体验收。

## 开工依赖

1. 9.3.4 已由 owner carry forward；
2. 9.3.5 完成公共 API 瘦身、外部入口收敛和去环，相关测试通过；
3. 9.3.2 的 auto-configuration/back-off 边界继续保留，并在受影响 starter tests 中复证。

## 已确认目标模块顺序

`model-api → model-core → model-jdbc → model-starter → model-web`

旧 `foggy-dataset-model` 只在兼容期保留为聚合/转发层。后续不得先物理拆分再定义 API，或让
`model-api` 引入 Spring、JDBC、implementation 或 web 依赖。

## 基线材料

- [模块与 SPI 静态盘点](code-inventory.md)
- 版本路线图：[9.3.1 → 9.4.0 迭代顺序评审](../9.3.1/roadmap-9.3.1-to-9.4.0.md)
- 9.3.5 前置：[引擎阶段与公共 API 基线](../9.3.5/README.md)
- Canonical 交付契约：
  [9.3.4 → 9.4.0 Speed-Forward](workitems/FEATURE-v934-v940-speed-forward.md)

## 开发与最终准出

开发阶段要求 Maven 依赖单向无环，`model-api` 无 Spring/JDBC/impl/web 依赖；每个切片只运行
受影响 reactor/module、compatibility、TCK、starter context 或 launcher smoke。

全部目标完成后形成 `READY_FOR_SIGNOFF` 候选，再执行一次跨版本整体验收。该验收不复活
9.3.4 Step 5/Step 7 authority、portable replay pointer 或 GitHub CI。
