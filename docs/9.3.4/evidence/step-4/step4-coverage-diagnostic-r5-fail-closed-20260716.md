---
evidence_type: failed-diagnostic
version: 9.3.4
step: 4
run_id: step4-coverage-20260716-diagnostic-r5
tested_commit: a35b99cb08f42817d8e75c440f18910b6961841b
status: failed
decision: excluded-from-step4-exit
recorded_at: 2026-07-16
---

# Step 4 coverage diagnostic r5 fail-closed evidence

## Decision

`step4-coverage-20260716-diagnostic-r5` 在 clean、pushed commit
`a35b99cb08f42817d8e75c440f18910b6961841b` 上建立了 run-owned Git/source seal，随后
完成 Unit、Integration 与 Addon companion。Step 3 required child 启动 database matrix
时，database-state companion 的 preflight 仍选择 frozen Step 3 database contract，因其
authority manifest 中 `foggy-dataset-model/pom.xml` 为旧 SHA 而 fail closed：

```text
[v934-db-state] ERROR: E_AUTHORITY_MANIFEST: stale authority artifact: foggy-dataset-model/pom.xml
```

outer 最终为 `failed / exit_code=1 / last_phase=child-step3-required`。本 run 没有完成
database cells、external matrix、all-lane aggregate、threshold observation 或 summary，故
明确标记为 `excluded-from-step4-exit`。已通过的 Unit、Integration 与 Addon 只属于本失败
run，不得与后续运行拼接或复用。

## Immutable outer result

- started/finished：`2026-07-16T11:41:05Z`—`2026-07-16T12:05:32Z`；
- tested commit：`a35b99cb08f42817d8e75c440f18910b6961841b`；
- source-before：`3,970` files，SHA-256=
  `1d8fedc784e3f3a2f70d21666e38417de8aba76d55a51df7cf76919d78c1ad17`；
- source-after、outer summary、aggregate observation、coverage gate、threshold observation、
  candidate：全部 absent；
- JaCoCo exec：仅 `9/23`，即 Unit `1`、Integration `6`、Addon `2`；
- class universe：`24 modules / 2,098 classes`；
- cleanup：container/volume/network=`0/0/0`，当前复查也无 r5 进程或 Docker residue。

关键 outer artifact SHA-256：

- `run-context.json`：
  `3b10a32536ca19a5036a13feaa0ccb7301345ad0fcefd98da65ffefbd5909704`；
- `run-status.env`：
  `06dbbebdbbf4af157c423573aadc83055851261ebb8a32f5f254f48911d33b94`；
- `run.log`：
  `a56fbe99dbfc75c7825f02f245a9a9c6345846c294c9bc44055b10a0b21675a2`；
- `cleanup.env`：
  `5524f0c68afb6ff1d13358f8fa3d2d19f7c1270454f2dfed1d2fe72cfe334ff1`；
- `toolchain-receipt.json`：
  `167741337793cb2efaa512edfc7073fb14787a65a2d44cd6b112337bd3a82017`；
- `class-universe.json`：
  `c7ee68f66e7c04f9dd288e161be16929e8739dc91a10502f1b56a487189face1`。

## Partial child results — non-reusable

| Child | Result | Durable evidence SHA-256 |
|---|---|---|
| Unit | `681 positive + 55 structural / 4,941 testcase / F0E0S0` | status `cc51f6d9ab025a9f226d69382b03dda0f6e146073b101025ab1a42f5151ec5e7`; summary `2c0c0c85383f4133177e6ba789a2d8c03e838feeed1b272aca286c1ce3162c28`; final `6b3255f2615a56af804a1df33e876e5b42d0d33e18722be795a74bfb7fbaccf2` |
| Integration | `47 positive + 4 structural / 320 testcase / F0E0S0` | status `8044ed8df3e4a8e81da647dc70fa30cf55465e070f64182c74784915e4ae0914`; summary `149d0798e2d0963971f7f7d0be54c7102728260f903eaec8536602c5915a93ef`; final `83ee155be7e5df9b41dba6e85a9428669ee9d644d501dd034e8a5ed223919c29` |
| Addon companion | `2 reports / 6 nodes / F0E0S0 / residue 0/0/0` | status `8e7644b065050a6b73c5cbf1d7d0fad9e05fc1fd1e207d0d154b6c47562b7a51`; summary `6801f8d5f1c46ff9d4dc5a710ad75bd8f2dfb78f0fe46855efa3834a53a85ac3`; candidate `285272c9afd0f248bc7408fa4c15f00da61ed24f7848040ad8d57af117560ddf` |

Integration 的六个 variants 为 caffeine=`2`、hermetic=`3`、sqlite-broad=`308`、
sqlite-harness=`1`、sqlite-lifecycle=`4`、sqlite-refresh=`2`。这些数字只描述失败轮次已
完成的子运行，不形成 coverage authority。

## Database failure boundary

database child 的 contract validator 在 state companion 前打印的
`7 variants / 29 reports / 370 nodes / status=passed` 只是 successor contract 的 frozen
exact totals，不是 r5 已执行的数据库结果。r5 database run root 不含任何 cell evidence、
`summary.env` 或 candidate；实际 database cell 执行为 `0`。external matrix 未启动。

- database status：
  `ecf07860ec5e871621c10bf3e2cdd688e44f816ab7cb99cfb43e6a25c5e57420`；
- database log：
  `77b96207083ac6e8765f9505b8af0320b679c16927bf038c3d0826489a92678d`；
- required status：
  `a92df55f1b1f9716d64def15e63df44d78ff5373cef64b48e54b6229355c6fa7`；
- required log：
  `a47d1b3f2a15027196c1bb9591a69baa31199102373e5bc09a2e869ab4392990`。

## Root cause and remediation boundary

database runner 原先直接调用
`scripts/v934/step3/database_state_negative_tool.py`。该 frozen tool 固定加载 frozen
`scripts/v934/step3/database-matrix-contract.json`；其 authority manifest 中 model POM
仍为 predecessor SHA。required report tool 在最终汇总时还有第二处相同硬编码，因此只
修 database runner 会在 finalize 再次失败。

修复保留全部 frozen Step 3 字节，新增两个由 Step 4 successor manifest 约束的薄适配器：

- database-state adapter 只把 frozen state harness 的 matrix contract 选择切到 Step 4
  successor；
- required-report adapter 只把 frozen required verifier 的 state-tool argv 精确改写为
  上述 adapter，其他 verifier argv 保持不变；
- database/required runners、Step 4 report inventory、successor contracts、authority
  manifest、overlay 激活语义与 top manifests 全部绑定同一组 adapter 字节；
- successor state validate、required contract validate、state argv 正/反例、frozen
  default fail-closed 对照与 overlay positive/negative 均须通过。

## Next gate

- [x] r5 failed evidence、partial child boundary 与 cleanup 已封存；
- [x] 两处 frozen state verifier 硬编码及 report inventory 间接消费链均已定位；
- [x] successor adapters 与 runner selectors 已实现；
- [x] focused successor state/required/overlay 回归通过；
- [x] 完整 Step 4 static matrix 与实现质量闸门通过，B/H/M/L=`0/0/0/0`；
- [ ] commit/push 后证明 clean `HEAD == origin/main`；
- [ ] 使用全新 `step4-coverage-20260716-diagnostic-r6` 完成 fresh all-lane diagnostic。

在 r6 完整成功、reviewed exact threshold freeze、fresh formal、最终质量闸门与测试证据
覆盖审计完成前，`can_enter_coverage_audit=no`，Step 5 保持关闭。
