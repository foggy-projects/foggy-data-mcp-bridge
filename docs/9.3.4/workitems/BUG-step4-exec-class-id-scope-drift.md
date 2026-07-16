---
type: bug
bug_source: diagnostic-found
version: 9.3.4
ticket: BUG-934-STEP4-EXEC-CLASS-ID-SCOPE-DRIFT
severity: blocker
status: in-progress
reproduction_status: confirmed
product_regression: false
test_strategy: jacoco-exec-scope-and-aggregate-identity-regression
automation_decision: required
owner: step4-coverage-tooling
---

# Step 4 exec verifier 把所有同名加载类误判为同一 JaCoCo identity

## Background

fresh diagnostic `step4-coverage-20260717-diagnostic-r9` 在 clean/pushed commit
`a0466ec04c51c436413e85836a7dee6153e18010` 上完成所有 required lane、报告 inventory、
`23 exec / 48 sessions` 后，于 `coverage-report` 报
`E_CLASS_ID_MISMATCH`。`exec-manifest.json` 尚未发布，outer 正确 fail closed。

Immutable evidence：

- `docs/9.3.4/evidence/step-4/step4-coverage-diagnostic-r9-exec-class-scope-fail-closed-20260717.md`。

## Expected vs Actual

- Expected：JaCoCo execution data 以 class ID 为主 identity；同名不同 ID 的测试类、依赖
  类和运行时生成类必须完整保留并参与 aggregate exact union。
- Expected：凡名称命中 frozen 24-module production class universe 的 execution data，
  observed ID 集必须精确等于当前 production `.class` 的 CRC64 ID；正确 ID 与 forged/旧
  ID 并存也必须 fail closed。
- Expected：aggregate 对每个 class ID 精确 OR probe bitmap；同 ID 的 name 或 probe
  count 漂移必须拒绝，任何 input ID 丢失也必须拒绝。
- Actual：inventory 与 aggregate verifier 都以 binary name 为 key，对所有已加载类要求
  同名单 ID；不同依赖版本、classloader 与 CGLIB bytecode 被误判为生产 class drift。

## Root Cause

`coverage_exec_tool.py` 已有 frozen production class tree，其中每个 class 绑定 owning
module、class SHA-256 与 JaCoCo CRC64 ID，也已有逐条 production ID 对比；但旧代码在取得
该 universe 之前，先对 23 份 exec 中的所有 class name 执行全局单 ID 检查。aggregate
verifier 又以 name 建 dict，继续把同名不同 ID 折叠为 shape error。

r9 只读诊断得到 `16,693` 个唯一名称、`16,939` 个唯一 `(name,id)`，135 个同名多 ID；
其中 frozen production class 冲突为 0。按 CGLIB 名称 exclusion 只能排除 41/135，剩余
94 个测试/依赖冲突仍会失败；继续扩大 glob 还可能静默排除生产覆盖，因此不采用。

## Fix Strategy

1. 在 coverage contract 的 `jacoco.class_id_consistency_scope` 冻结
   `frozen-24-module-production-class-universe`，validator 与 mutation probe 拒绝漂移。
2. `verify_exec_set()` 先加载并验证 fresh class universe，再仅对命中 production name 的
   ID 集要求精确等于 frozen ID；保留每份 exec 至少命中一个 production class 的规则。
3. raw exec 不做 package/name exclusion；manifest 的 `unique_execution_classes` 按唯一
   JaCoCo class-ID identity 计数。
4. `verify_merged_execution_data()` 以 class ID 为 key；同 ID 要求 name/probe count
   compatible 并对 bitmap 做 exact OR；同名不同 ID 分别保留。
5. aggregate provenance 显式记录 production consistency scope 与
   `exact-session-and-jacoco-class-id-probe-bitmap-union`。
6. XML/provenance verifier 对新增 scope、manifest schema 与 merge semantics 做 exact
   validation，避免 downstream 忽略语义漂移。

## Regression Test Decision

`automation_decision=required`。该缺陷位于 mandatory coverage evidence reader，若放宽过度
会漏掉生产 bytecode drift，若维持旧规则则所有真实 all-lane run 都无法形成 aggregate。
自动化必须覆盖：

- non-production 同名两个 ID：inventory 允许；
- production 正确 ID：允许；
- production 正确+forged ID、仅 forged ID：`E_CLASS_ID_MISMATCH`；
- 名称看似 CGLIB、但属于 production universe：仍必须拒绝 forged ID；
- aggregate 同名不同 ID：两个 ID 完整保留；
- aggregate 丢一个 runtime ID：`E_AGGREGATE_CLASS_SET`；
- 同 ID name/probe shape 漂移：`E_AGGREGATE_CLASS_SHAPE`；
- bitmap 非精确 OR：`E_AGGREGATE_PROBE_UNION`；
- contract scope 漂移：full structure validator fail closed。

## Focused closure

- r9 raw exec 只读复算：`23 exec / 16,939 class-ID identities / 135 same-name
  multi-ID / 0 frozen-production conflict`；
- exec scope/aggregate positive+negative=`17/17`；
- coverage contract mutations=`21/21`，新增 production scope drift probe；
- XML identity/provenance fast negative=`68/68`，新增 valid identity 正例与 manifest scope、
  aggregate scope、merge semantics、aggregate/manifest class-ID count 四个 stable-code probe；
- successor overlay positive 通过、negative=`12/12`；
- top manifest=`60/60`、successor manifest=`14/14`（最终 SHA 在提交前质量收口后回写）。

Focused 结果只证明修复逻辑，不能替代 fresh all-lane diagnostic 或 formal。

## Fix Checklist

- [x] r9 immutable failure、absence boundary、cleanup 与 demo restoration 已封存。
- [x] production class-ID scope 进入 versioned coverage contract。
- [x] exec inventory 改为 frozen production scope strict match。
- [x] aggregate 改为 JaCoCo class ID exact union，保留同名不同 ID。
- [x] manifest/XML/provenance verifier 同步新 schema 与语义。
- [x] exec focused regression=`17/17`，contract mutation=`21/21`，XML=`68/68`，
      overlay=`12/12`。
- [x] 完成三路独立实现质量闸门，最终 B/H/M/L=`0/0/0/0`。
- [ ] commit/push 并证明 clean `HEAD == origin/main`。
- [ ] 使用新 run ID 完成 fresh all-lane diagnostic。
- [ ] fresh formal、最终质量、coverage evidence audit 与 acceptance 通过后关闭本 BUG。

当前 `status=in-progress`；r9 不可复用，Step 4/5、threshold freeze、formal、coverage audit
与 acceptance 保持关闭。

## References

- `scripts/v934/step4/coverage-contract.json`
- `scripts/v934/step4/coverage_tool.py`
- `scripts/v934/step4/coverage_contract_negative_tool.py`
- `scripts/v934/step4/coverage_exec_tool.py`
- `scripts/v934/step4/coverage_xml_tool.py`
- `scripts/v934/step4/successor/overlay-contract.json`
- `scripts/v934/step4/successor/overlay_tool.py`
- `target/v934-step4-coverage/runs/step4-coverage-20260717-diagnostic-r9/`
