---
type: execution-blocker
blocker_source: environment-precondition
version: 9.3.4
ticket: BLOCKER-934-STEP4-R6-MYSQL57-PORT-OCCUPATION
severity: blocker
status: in-progress
reproduction_status: confirmed
product_regression: false
test_strategy: fresh-r7-after-exclusive-port
automation_decision: existing-fail-closed-guard-sufficient
owner: execution-environment
---

# Step 4 r6 frozen MySQL 5.7 port 被 repo demo container 占用

## Background

Step 4 diagnostic r6 在 clean、pushed commit
`eb10d9c10a73f379db9ce4fa3d05ff340b489fd4` 完成 source seal、Unit、Integration 与 Addon
companion 后，database-state dynamic precondition 拒绝继续：

```text
[v934-db-state] ERROR: E_DYNAMIC_PRECONDITION: frozen mysql57 port 13306 is occupied; the dynamic state probes did not start or change the listener
```

r6 因此为 `excluded-from-step4-exit`。三条已绿 lane 均为 partial/non-reusable；database
cells、external、aggregate、threshold、source-after 与 summary 全部 absent。

## Classification

这是执行环境阻塞，不是生产代码、测试逻辑或 successor authority contract 的回归：

- r6 已越过 r5 失败的 `E_AUTHORITY_MANIFEST` 边界，证明 successor state adapter 被实际
  选择；
- state guard 在运行任何动态 probe 前发现冻结端口已有 listener，并按契约 fail closed；
- listener 属于仓库 demo compose project `foggy-dataset-demo` 的容器
  `foggy-demo-mysql`，service=`mysql`，binding=`13306 -> 3306`；
- runner 没有启动、接管、复用、停止或改变该 listener，run-owned cleanup residue=
  `0/0/0`。

因此本 workitem 不授权修改 production、runner、contract、threshold 或 exclusion，也不把
r6 partial 绿色结果升级为测试证据。

## Expected vs Actual

- Expected：fresh database authority 启动前，冻结端口 `13306` 无任何 listener；runner
  自己启动并验证 MySQL 5.7 state fixture。
- Expected：若端口被既有进程占用，必须在动态 probe 之前 fail closed，且不得改变既有
  listener。
- Actual：`foggy-demo-mysql` 长期占用 `13306`；guard 正确拒绝执行，r6 在
  `database-state-negative` 终止。

## Resolution Plan

1. 在不删除用户数据的前提下协调停止或迁移 repo demo MySQL container；
2. r7 启动前用只读端口检查证明 `13306` 无 listener；该项已完成；
3. 不复用 r6 的 Unit/Integration/Addon XML 或 exec，以全新 run id 从 source seal 开始；
4. r7 随后因独立的 Unit hermeticity BUG fail closed；修复后 fresh r8 必须完成同一 run 的
   `23 exec / 48 sessions`、全部 database/external lane、aggregate、
   critical gates、source-after 与 summary；
5. r8 通过后继续 reviewed threshold freeze 与 fresh formal；在此之前本 blocker 与 r5
   successor BUG 均保持 `in-progress`。

## Pre-r7 operator maintenance

r6 已完整退出并完成 `0/0/0` cleanup 后，operator 在 r7 evidence window 之外仅停止下列
四个 repo demo DB 容器；所有 exact container ID 与 config/mount identity 均已记录，容器、
卷、网络未删除，待 outer r7 完整退出后用相同 ID 恢复：

| Container | Frozen port | Exact ID prefix | Config/mount SHA-256 |
|---|---:|---|---|
| `foggy-demo-mysql` | `13306` | `0c50bf7e8684` | `e1e9c7d989d806b1441142a7489693471550709b7461c72d8bd2512656313d19` |
| `foggy-demo-mysql8` | `13308` | `f603e63eaf82` | `4fa12bf274a8fc9870226007b858bdbb33a411338c279880e1e0321427a44816` |
| `foggy-demo-postgres` | `15432` | `a4d6a278b0b9` | `ef1d5fea0505ac84a1f76ac7bdb09aa87dcbd4f501226ad3bf6fa0aff6a044d5` |
| `foggy-demo-sqlserver` | `11433` | `7881fa53039f` | `cf6d1cb5d2c2fad533fd0c4c8dd8495d6ffeb8ae19012769f084e53353b984f0` |

停止后四个 exact ID 仍存在且状态为 `exited`，`13306/13308/15432/11433` 均为 `free`。
该维护动作不是 runner 自动接管 existing listener，也不属于 r7 run-owned resource。

## Verification Checklist

- [x] 保存 r6 immutable failed evidence。
- [x] 确认 frozen port `13306` 已有 listener。
- [x] listener 精确定位为 `foggy-demo-mysql` / `foggy-dataset-demo` / `mysql`。
- [x] 确认 r6 未启动或改变动态 listener，run-owned residue=`0/0/0`。
- [x] 将 r6 partial lanes 标为 non-reusable，禁止拼接。
- [x] 在 r7 证据窗口外停止四个 repo demo DB 容器，并证明四个冻结端口无 listener。
- [x] fresh r7 在四个 frozen ports 无 listener 的环境启动；其后在 Unit 阶段暴露独立的
  hidden MySQL dependency BUG，故没有形成 all-lane authority。
- [ ] Unit fixture remediation 后 fresh r8 all-lane diagnostic 通过。
- [ ] reviewed threshold freeze、fresh formal 与最终质量复核完成后关闭 blocker。

## References

- `docs/9.3.4/evidence/step-4/step4-coverage-diagnostic-r6-environment-fail-closed-20260716.md`
- `docs/9.3.4/evidence/step-4/step4-coverage-diagnostic-r5-fail-closed-20260716.md`
- `docs/9.3.4/workitems/BUG-step4-database-state-successor-authority-manifest.md`
- `docs/9.3.4/evidence/step-4/step4-coverage-diagnostic-r7-unit-hidden-mysql-fail-closed-20260716.md`
- `docs/9.3.4/workitems/BUG-step4-unit-hidden-mysql-environment-dependency.md`
- `target/v934-step4-coverage/runs/step4-coverage-20260716-diagnostic-r6/run-status.env`
- `target/v934-step3-database-matrix/runs/step4-coverage-20260716-diagnostic-r6/run.log`
