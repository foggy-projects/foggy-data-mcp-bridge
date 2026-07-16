---
evidence_type: failed-diagnostic
version: 9.3.4
step: 4
run_id: step4-coverage-20260716-diagnostic-r6
tested_commit: eb10d9c10a73f379db9ce4fa3d05ff340b489fd4
status: failed
failure_class: environment-precondition
decision: excluded-from-step4-exit
recorded_at: 2026-07-16
---

# Step 4 coverage diagnostic r6 environment fail-closed evidence

## Decision

`step4-coverage-20260716-diagnostic-r6` 在 clean、pushed commit
`eb10d9c10a73f379db9ce4fa3d05ff340b489fd4` 上建立了 run-owned Git/source seal，完成
Unit、Integration 与 Addon companion 后，在 Step 3 required child 的 database-state
dynamic precondition 处 fail closed：

```text
[v934-db-state] ERROR: E_DYNAMIC_PRECONDITION: frozen mysql57 port 13306 is occupied; the dynamic state probes did not start or change the listener
```

outer 最终为 `failed / exit_code=1 / last_phase=child-step3-required`。诊断时端口 `13306`
由仓库 demo compose project `foggy-dataset-demo` 的长期运行容器 `foggy-demo-mysql`
占用；container label 的 config path 指向
`foggy-dataset-demo/docker/docker-compose.yml`，service=`mysql`，port binding=
`13306 -> 3306`。这是执行环境前置条件阻塞，不是产品回归，也没有复现 r5 的 successor
authority selector 缺陷。runner 按冻结规则没有接管、复用或改变既有 listener。

r6 没有执行 database cells、external matrix、all-lane aggregate、threshold observation 或
summary，故明确标记为 `excluded-from-step4-exit`。已通过的 Unit、Integration 与 Addon
只属于本失败 run，禁止与 r1-r5、focused 结果或后续 r7 拼接、复用。

## Immutable outer result

- started/finished：`2026-07-16T12:38:05Z`—`2026-07-16T13:03:28Z`；
- tested commit：`eb10d9c10a73f379db9ce4fa3d05ff340b489fd4`，运行时
  `HEAD == origin/main`；
- source-before：`3,974` files，SHA-256=
  `3a4322e8442646c58ed522c0d4fb52071b3219cc1c2f204c209299bd8acc1cff`；
- source-after、outer summary、aggregate observation、coverage gate、threshold observation、
  candidate：全部 absent；
- JaCoCo exec：仅 `9/23`，即 Unit `1`、Integration `6`、Addon `2`；
- class universe：`24 modules / 2,098 classes`；
- outer cleanup：run-owned container/volume/network residue=`0/0/0`。该值只证明 r6 未新增
  run-owned residue，不表示预存的 `foggy-demo-mysql` 不存在。

关键 outer artifact SHA-256：

- `run-context.json`：
  `21251c5ab1b16e401a18bd8ecbfefefb4d5059cc202d70f1d879cc60f49db332`；
- `run-status.env`：
  `4029bb382ffea564ee3b8c2fc20021b0d4fd4d928a631bc94ca947019463ac10`；
- `run.log`：
  `80d635a23dd5160e617770db0d5d262851bb0180cbe19976cd588ee2cf5e3371`；
- `cleanup.env`：
  `5524f0c68afb6ff1d13358f8fa3d2d19f7c1270454f2dfed1d2fe72cfe334ff1`；
- `toolchain-receipt.json`：
  `3a101e4041b8186366c1d3c834f1c15e4a4c7ef47d4ed1b40221517188a65f20`；
- `class-universe.json`：
  `3aeaabd75ba0a75e99801f8b50e8a7453d512989da50f49afaddc35ba523e926`。

## Partial child results — non-reusable

| Child | Result | Durable evidence SHA-256 |
|---|---|---|
| Unit | `681 positive + 55 structural / 4,941 testcase / F0E0S0` | status `4cfd30da8a2e2f305775a1a3088bb5e61736d7e9b1bf85abc466c126683aae9d`; summary `eb06923134113731ef7b7ba4a9dae85edefc98242e72cbc7ac31ec34d2cb3e16`; final `82ef2a8bbaae6186202e7146e9b4a86d2b97115647575e5eb19ad186ced54d29` |
| Integration | `47 positive + 4 structural / 320 testcase / F0E0S0` | status `d5c7115873c5f0dc9041d801e1728014d0596834f17b6f27a5a5afac875734db`; summary `2ff08528b4e9ad412ea536276f9ae3342fd2c0464f560e5e36349a348dd5e3f6`; final `8f69ab44d5c6531669ab6fd2cda2e850c91e8b2dcea59e49c384f6c930a5e8a2` |
| Addon companion | `2 reports / 6 nodes / F0E0S0 / residue 0/0/0` | status `198d863addec4f58c9f208a6fcccff90c19cf79e426e78107f5da05f6f2b7735`; summary `557c31ffc91a7e83b6252170b2c1022c064963c8a7e58e2bef6079c2ca61df7b`; candidate `fb471a4e8b71af3c66e010ee8f2bcf7aa01448fe797ea18b8d39ea35cd4fcbe2` |

Integration 的六个 variants 为 caffeine=`2`、hermetic=`3`、sqlite-broad=`308`、
sqlite-harness=`1`、sqlite-lifecycle=`4`、sqlite-refresh=`2`。这些数字只描述失败轮次已
完成的子运行，不形成 coverage authority。

## Database and environment boundary

database child 在 state companion 前打印的
`7 variants / 29 reports / 370 nodes / status=passed` 是 successor contract 的 frozen exact
totals，不是 r6 已执行的数据库结果。r6 database run root 没有任何 cell evidence、
`summary.env` 或 candidate；实际 database cell 执行为 `0`。external matrix 未启动，
required 与 outer aggregate/finalize 均未完成。

- database status：
  `d3242f7595a9ccbcea26ffe02746ab3265fba93460ae951b0d958e2e103fdf29`；
- database log：
  `539a305fbfe8499fb67dfa28c163a10fb3d423f17b642460b0d3794c820df4b5`；
- required status：
  `c199a97ed35c56662a8cc25d7484d55d1feb87f6c5d6ada7e40ba5107caf1321`；
- required log：
  `f4b0a9e9529502b0b1f841ab517b3525007f267eefa9964c53863afbee960c2c`。

诊断时只读环境核对结果：

```text
container=foggy-demo-mysql
compose_project=foggy-dataset-demo
compose_service=mysql
config_file=/home/sa/workspace/foggy-data-mcp/foggy-data-mcp-bridge/foggy-dataset-demo/docker/docker-compose.yml
binding=0.0.0.0:13306->3306/tcp
created_at=2026-07-13T10:39:54.562004007Z
```

该环境观察解释端口所有者，但不是可替代 run-owned status 的产品测试结果。不得通过放宽
`E_DYNAMIC_PRECONDITION`、复用未知 listener 或让 runner 删除预存容器来转绿。

## Product regression separation

r5 的产品缺陷发生在 successor adapter 选择前，错误为 `E_AUTHORITY_MANIFEST`。r6 已通过
同一 successor database contract 的 authority validate，随后才命中
`E_DYNAMIC_PRECONDITION`，证明 r5 selector remediation 已进入预期动态检查路径。该事实
不等于 Step 4 产品/coverage 通过；原 successor BUG 仍待 fresh r7 all-lane 与后续 formal
证明后关闭。

本轮不修改 production、test runner、contract、threshold 或 exclusion。r6 完整退出后、r7
证据窗口开始前，operator 仅停止四个保留原 ID 的 repo demo DB 容器；未删除容器、卷或网络，
也未改变冻结端口。`13306/13308/15432/11433` 随后均经只读检查确认为无 listener。

## Next gate

- [x] r6 failed evidence、partial child boundary 与 run-owned cleanup 已封存；
- [x] `E_DYNAMIC_PRECONDITION` 的既有 listener 已定位到 repo demo container；
- [x] 确认这是环境前置条件阻塞，未复现 r5 产品回归；
- [x] 在 r7 证据窗口外停止四个 repo demo DB 容器，并证明四个冻结端口均无 listener；
- [ ] 使用全新 `step4-coverage-20260716-diagnostic-r7` 完成 fresh all-lane diagnostic；
- [ ] 完成 reviewed threshold freeze、fresh formal 与最终实现质量复核后关闭相关 workitem。

在 r7 完整成功、reviewed exact threshold freeze、fresh formal 与最终质量闸门完成前，
`can_enter_coverage_audit=no`，测试证据覆盖审计不得启动，Step 5 保持关闭。
