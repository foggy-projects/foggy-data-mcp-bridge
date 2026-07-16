---
type: bug
bug_source: diagnostic-found
version: 9.3.4
ticket: BUG-934-STEP4-THRESHOLD-FREEZE-OBSERVATION-APPLICABILITY-GAP
severity: blocker
status: in-progress
reproduction_status: confirmed
product_regression: false
test_strategy: real-observation-freeze-and-structural-na-regression
automation_decision: required
owner: step4-coverage-tooling
---

# Step 4 threshold freeze 无法消费真实 critical observation 与结构性 N/A

## Background

fresh all-lane diagnostic `step4-coverage-20260717-diagnostic-r12` 在 clean/pushed
commit `05351ecab0d7fc43d12dfa307ffecf81feb41539` 上完整通过，发布了 sealed
`coverage-observation.json`、`summary.env`、`sensitive-scan.env` 与 cleanup receipt。官方
`validate-diagnostic-run` 复核通过，observation SHA-256=
`e58dcdbc59e78f45d4fb5f3aedab271132bcb0405286f521f6fd38382141e0f8`。

随后按计划执行 `freeze-thresholds`，工具在生成 candidate 前 fail closed：真实 critical
metric 除基础 counter 外还包含 `floor/outcome/gap`，而 freeze consumer 只接受基础字段。
即使绕过该不兼容，`NamespaceScope.branch` 的生产字节码没有分支，合法 observation 为
`total=0 / outcome=not-applicable`，旧 freeze/confirmed/formal schema 同样无法表达。

Immutable evidence：

- `docs/9.3.4/evidence/step-4/step4-coverage-diagnostic-r12-pass-with-threshold-gaps-20260717.md`。

## Expected vs Actual

- Expected：freeze consumer 严格消费 producer 发布的 exact enriched observation schema，
  再把 lossless `covered/total/fraction` 投影到 reviewed threshold。
- Expected：critical metric 默认必须 `required-positive-total`；只有机器契约预声明的 exact
  FQCN/module/metric 才可使用结构性 N/A。
- Expected：`NamespaceScope#branch` 以 exact `0/0/null` 观察值和 `minimum=null` 表示；formal
  必须保持同一 applicability 与零分母。
- Expected：aggregate zero、未声明 critical zero、N/A exception 扩张、wrong identity/metric、
  N/A nonzero 或 formal applicability drift 全部 fail closed。
- Actual：第一次 freeze 返回 `E_FREEZE_ZERO_COUNTER`，错误详情为 critical metric keys 与仅
  基础 counter schema 不同；candidate 文件未生成。

## Root Cause

synthetic freeze fixture 只构造了简化 counter，没有复用 `critical_observations()` 的真实
producer shape，因此测试未覆盖 producer/consumer schema。observation producer 已显式记录
`not-applicable_metric_count=1`，但 candidate、confirmed threshold 与 formal verifier 仍只接受
positive-total exact counter，形成 lifecycle 断点。

这不是降低门槛的理由。r12 同时如实发现 9/12 个 critical class 低于 0.80/0.70；这些差距
必须补测试，不能用 N/A、exclusion 或降阈值规避。

## Fix Strategy

1. freeze 使用真实 enriched critical metric schema，逐项校验 arithmetic、ratio、fraction、
   floor、outcome 与 gap 后再投影，不忽略未知字段。
2. 在 `coverage-contract.json` 预声明 applicability policy：默认
   `required-positive-total`；唯一 exception 是
   `NamespaceScope / foggy-dataset-model / branch / not-applicable-zero-total-only`。
3. candidate/confirmed 对 required metric 保存 exact observed/minimum；对声明 N/A 保存
   `observed={covered:0,total:0,fraction:null}` 与 `minimum=null`。
4. formal 对 applicability 漂移使用稳定 `E_FORMAL_APPLICABILITY_DRIFT`；未声明 zero 继续
   `E_FREEZE_ZERO_COUNTER`。
5. 为 9 个 below-floor 类补业务边界测试，使 line/branch 达到 0.80/0.70；不改生产逻辑、
   critical set、exclusion 或 floor。
6. 所有修复进入新的 diagnostic commit 并 fresh all-lane 重跑；r12 不得直接用于 Cfreeze。

## Regression Test Decision

`automation_decision=required`：

- enriched critical observation positive；
- below-floor 仍返回 `E_FREEZE_FLOOR`；
- undeclared critical zero 仍返回 `E_FREEZE_ZERO_COUNTER`；
- exact `NamespaceScope#branch` structural N/A positive；
- exception expansion、wrong FQCN/module/metric、N/A nonzero、non-null minimum negative；
- formal applicable/N/A 双向漂移返回 `E_FORMAL_APPLICABILITY_DRIFT`；
- fresh diagnostic 必须显示 `below_floor_class_count=0`、N/A count=`1`，再生成并验证 candidate。

## Fix Checklist

- [x] r12 sealed diagnostic、artifact hashes、cleanup 与 external restoration 已封存。
- [x] 失败命令可重复复现，candidate absent，未修改 threshold。
- [x] 确认 9 个 critical coverage gaps 与唯一结构性 N/A。
- [x] 完成 enriched-schema / exact N/A machine policy 与 negatives。
- [x] 完成 9 类 coverage tests；focused union 已达到 floor，仍等待 fresh JaCoCo authority 重算。
- [x] 刷新 machine manifest并运行 focused gates；正式实现质量复核 B/H/M/L=`0/0/0/0`。
- [ ] commit/push、clean `HEAD == origin/main` 后执行 fresh diagnostic。
- [ ] 仅在 fresh diagnostic/candidate 通过后创建 direct-single-parent Cfreeze。

当前实现已额外关闭预提交审查发现的 JSON numeric alias：`false`、`0.0` 不得冒充
canonical N/A integer zero，`gap:false` 不得冒充 `0.0`；frozen replay receipt 现在由完整
schema、strict JSON identity 与 direct formal-call binding 保护。XML fast negatives=
`118/118`，contract mutations=`27/27`，threshold/frozen replay policy=`12/12`；九类 focused
test=`136/F0E0S0`，既有 report/testcase cardinality 不变。

当前 `status=in-progress`；fresh diagnostic 尚未执行，因此 Step 4、formal、coverage audit、
acceptance 与 Step 5 保持关闭。

## References

- `scripts/v934/step4/coverage_xml_tool.py`
- `scripts/v934/step4/coverage_tool.py`
- `scripts/v934/step4/coverage-contract.json`
- `scripts/v934/step4/coverage_xml_negative_tool.py`
- `scripts/v934/step4/coverage_contract_negative_tool.py`
