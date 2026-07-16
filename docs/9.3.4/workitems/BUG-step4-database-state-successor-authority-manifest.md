---
type: bug
bug_source: regression-found
version: 9.3.4
ticket: BUG-934-STEP4-DATABASE-STATE-SUCCESSOR-AUTHORITY-MANIFEST
severity: major
status: in-progress
reproduction_status: confirmed
test_strategy: successor-runtime-binding-test
automation_decision: required
owner: step3-database-matrix
---

# Step 4 database-state companion 绕过 successor authority manifest

## Background

Step 4 diagnostic r5 已完成 run-owned source seal、Unit、Integration 与 Addon companion，
但 Step 3 database child 在 `database-state-negative` preflight 失败：

```text
[v934-db-state] ERROR: E_AUTHORITY_MANIFEST: stale authority artifact: foggy-dataset-model/pom.xml
```

r5 因此为 `excluded-from-step4-exit`。已通过的子结果不得复用；database cells 与 external
matrix 未执行，aggregate/threshold/summary 全 absent。

## Reproduction

successor database report contract 本身有效：

```bash
python3 scripts/v934/step4/successor/database_matrix_report_tool.py validate
```

但 frozen state tool 仍选择 predecessor contract：

```bash
python3 scripts/v934/step3/database_state_negative_tool.py validate
```

结果为 exit `1`，错误精确命中 model POM stale authority。successor contract 的 authority
manifest 已包含当前 model POM SHA，故问题不在 successor manifest 内容，而在 runtime
contract selector。

## Root Cause

存在两处同类硬编码和一处间接消费：

1. `scripts/verify-v934-database-matrix.sh` 的 `STATE_NEGATIVE_TOOL` 原先指向 frozen
   Step 3 state tool；
2. frozen `step3_required_report_tool.py` 在 `collect_database()` 最终复核 state manifest 时
   也直接构造 frozen state tool 路径；
3. Step 4 `report_inventory_tool.py` 原先直接选择 frozen required report tool，因而即使
   required child 自身修复，后续 inventory 复核仍会重新进入第 2 项硬编码。

frozen `database_state_negative_tool.py` 的 `MATRIX_CONTRACT_PATH` 固定为
`scripts/v934/step3/database-matrix-contract.json`。该 predecessor contract 合理地绑定
predecessor authority manifest，但 Step 4 已有受治理的 POM/runner amendments。于是 database
report wrapper 选择 successor contract，而 state companion 退回 predecessor contract，形成
同一 child 内部 authority split。

## Expected vs Actual

- Expected：Step 4 database positive matrix、state companion、required final verifier 使用同一
  successor database contract SHA，并共同绑定 successor authority manifest。
- Expected：frozen Step 3 files保持字节不变；Step 4 的选择差异由 successor adapter、contract
  binding 与 overlay manifest 显式声明。
- Expected：若 adapter、runner selector、contract SHA 或 authority manifest 任一漂移，
  preflight fail closed。
- Actual：database report path 使用 successor，state run/verify 与 required final verifier
  使用 predecessor，r5 在第一个 state preflight 终止。

## Test Strategy

`automation_decision=required`：

1. successor state `validate` 必须通过，并输出当前 successor database-contract SHA；
2. required-report adapter 必须把精确 frozen state-tool argv 改写为 successor state adapter；
3. 非 state-tool verifier argv 必须保持不变；
4. database runner、required runner 与 Step 4 report inventory 必须各有且仅有一个
   successor adapter selector；
5. successor required contract 必须把 `required_report_tool` 绑定到 successor adapter；
6. original frozen state `validate` 在当前 amended source 上继续以 stale predecessor authority
   fail closed，证明没有偷偷改写 predecessor contract/tool；
7. 两个 adapters 必须进入 successor exact manifest，overlay positive 运行真实 adapters，
   overlay negative 与 top exact manifest 继续通过；
8. 新 diagnostic 必须从 clean、pushed commit 运行完整 `23 exec / 48 sessions`，不得复用 r5。

## Remediation Verification

当前 focused/static remediation：

- successor state validate：`status=passed / probes=18 / database contract=`
  `553dabf2b4c266b531fb4ce36f4a498dce223b6449106274a3a2b103ccb775ea`；
- required-report rewrite self-test：`1/1`，并包含非目标 argv 不改写控制；
- successor required validate：`45 reports / 446 nodes / F0E0S0`，contract SHA=
  `893ac03231cb4f6fd8ae427c01aa3f9f04267c96e3945814b9b70a3445a58af5`；
- original frozen state control：exit `1`，精确错误仍为 model POM stale authority；
- overlay positive + negative=`12/12`；successor manifest=`14/14`，SHA-256=
  `9fa9ddb23aa36c48961e54393f1fe747bf5d0433645cb1a0529e607db4f211cb`；
- top manifest=`57/57`，SHA-256=
  `831af049e51060031bebf838f54d4a24cfcac47d45bda7ee18b73af6658f8513`；
- diagnostic/formal coverage contract SHA-256=
  `5999d68d15e4e2873be26666c8ea4f415eeadb6e0696df290d6617f53457b684` /
  `c1bb3dcc2e1eea224bfcf36439065fb521fa2603e32146c3978fe3a6a903ba28`。

这些结果只证明 successor runtime binding 修复，不改变 r5 的 failed decision，也不是
all-lane coverage evidence。

r6 在 clean/pushed commit `eb10d9c10a73f379db9ce4fa3d05ff340b489fd4` 实际越过上述
successor authority validate，随后才因 repo demo container 占用 frozen port `13306` 而以
`E_DYNAMIC_PRECONDITION` 终止。这证明 r5 selector 缺陷未复现，但 r6 是环境阻塞的
excluded run，不能据此关闭本 BUG。r7 又在更早的 Unit hidden MySQL dependency 处终止，
仍未进入 database successor dynamic path；须 fresh r8 all-lane 与后续 formal 证明。

## Fix Checklist

- [x] 封存 r5 immutable failure、partial lanes 与 residue evidence。
- [x] 复现 predecessor state tool stale authority 错误。
- [x] 定位 database runner、required final verifier 与 report inventory 消费链。
- [x] 保留 frozen Step 3 字节并新增 database-state successor adapter。
- [x] 新增 required-report successor adapter，精确重写 state verifier argv。
- [x] runner selectors、successor contracts、authority manifest 与 declared amendments 联动。
- [x] adapters 纳入 successor/top exact manifests 与 overlay activation semantics。
- [x] 补 successor state、required rewrite 正/反例、required validate 和 frozen fail-closed 对照。
- [x] 完整 static matrix 与正式实现质量闸门通过，B/H/M/L=`0/0/0/0`。
- [x] commit/push 后从 clean HEAD 启动 fresh r6，并实际进入 successor dynamic state
  precondition。
- [x] 排除 r6 环境端口阻塞后启动 fresh r7；r7 因独立 Unit hermeticity BUG 在 database
  child 前 fail closed，不能作为本 BUG 的动态证明。
- [ ] fresh r8 all-lane 与后续 formal 证明同一 successor state binding 后关闭 BUG。

## References

- `docs/9.3.4/evidence/step-4/step4-coverage-diagnostic-r5-fail-closed-20260716.md`
- `docs/9.3.4/evidence/step-4/step4-coverage-diagnostic-r6-environment-fail-closed-20260716.md`
- `docs/9.3.4/workitems/BLOCKER-step4-r6-mysql57-port-occupation.md`
- `docs/9.3.4/evidence/step-4/step4-coverage-diagnostic-r7-unit-hidden-mysql-fail-closed-20260716.md`
- `docs/9.3.4/workitems/BUG-step4-unit-hidden-mysql-environment-dependency.md`
- `scripts/v934/step4/successor/database_state_negative_tool.py`
- `scripts/v934/step4/successor/step3_required_report_tool.py`
- `scripts/verify-v934-database-matrix.sh`
- `scripts/verify-v934-step3-required-matrix.sh`
- `scripts/v934/step4/successor/overlay_tool.py`
