---
evidence_type: successful-diagnostic-governed-high-water-gap
version: 9.3.4
step: 4
run_id: step4-coverage-20260717-diagnostic-r18
tested_commit: 5be1edaa16c5883cde2f66396ac26a1ae113430b
status: diagnostic-observed
decision: threshold-candidate-not-authorized
candidate_status: absent
recorded_at: 2026-07-17
---

# Step 4 coverage diagnostic r18 governed high-water gap

## Decision

`step4-coverage-20260717-diagnostic-r18` 从 clean/pushed
`HEAD == origin/main == 5be1edaa16c5883cde2f66396ac26a1ae113430b` 启动并完整退出：
`exit_code=0`、`last_phase=completed`、`status=diagnostic-observed`。同一 authority window
完成全部 required lanes、source before/after seal、report inventory、23 exec / 48 sessions、
两次 deterministic aggregate report replay、coverage observation、model external gate、
sensitive scan 与 cleanup。public `validate-diagnostic-run` 复算通过：

```text
[v934-coverage-xml] DIAGNOSTIC VALID run=step4-coverage-20260717-diagnostic-r18 observation=7a02bfaaec7d6d1afeac4a5cff20c708fb8ff1092185c25a31ac588b6845dd76
```

该 PASS 证明 r18 是完整、可重算的 diagnostic evidence，但不自动授权 threshold freeze。
r18 相对上一个 reviewed diagnostic r16 的 aggregate exact high-water 少 `2 line / 4 branch`；
差异只位于两个未改动的 Pivot production class，并精确落在 NULL row/column axis 路径。
现有测试库存没有确定性业务 oracle 保证这些路径每次执行，因此不能把 r18 较低 observation
投影成新阈值来掩盖 coverage-oracle gap。

本次 decision 固定为 `threshold-candidate-not-authorized`。未运行 `freeze-thresholds`，且
`docs/9.3.4/evidence/step-4/step4-coverage-diagnostic-r18-threshold-candidate-20260717.json`
保持 absent。r18 不得进入 threshold review、Cfreeze 或 fresh formal；这不是工具生成失败，
而是治理层在 deterministic regression 完成前主动拒绝降低 exact high-water。

## Complete sealed result

- run window：`2026-07-17T11:45:28Z` 至 `2026-07-17T12:52:41Z`；
- source-before=source-after SHA-256=
  `63fdcfb876ed038b2ec8527fa58fd00e01dccdec8571541dc28f8b1980ba1650`；
- required reports：`773 positive + 59 structural / 5,707 testcase / F0E0S0`；
- Addon companion：`2 reports / 6 testcase / F0E0S0`；
- Unit：`681 positive + 55 structural / 4,941 testcase / F0E0S0`；
- Integration：`47 positive + 4 structural / 320 testcase / F0E0S0`；
- Step 3 required：`45 reports / 446 testcase / F0E0S0`；
- database matrix：`7 variants / 29 reports / 370 testcase / F0E0S0`；
- external matrix：`7 variants / 16 reports / 76 testcase / F0E0S0`；
- exec：`23 files / 48 sessions / 16,940 unique execution class identities`；
- fresh class universe：`24 modules / 2,098 bytecode classes`；
- workspace class tree SHA-256=
  `a72007a8beecace7a21770d5e0b47f979aabb64f69bac7e7ede90fbf333f99be`；
- aggregate instruction=`252,719/352,456`；
- aggregate line=`54,622/76,830`（71.0946244956%）；
- aggregate branch=`26,107/44,870`（58.1836416314%）；
- aggregate method=`9,068/12,701`、class=`1,503/1,716`、complexity=`17,654/35,571`；
- critical classes=`12`，below floor=`0`，structural N/A metric=`1`；
- model external gate=`passed`；
- sensitive scan：5 patterns，extensions=`log,env,json,tsv,xml`，status=`passed`；
- cleanup：container/volume/network residue=`0/0/0`。

## Immutable artifact identities

| Artifact | SHA-256 |
|---|---|
| `run-status.env` | `1f7c59652bcc17c90e1dc1309f71a2f358d51361827242aa93a8d036cdf2865a` |
| `run.log` | `85a99c238933d745f7854ee5cffad39763a97ce0fb901b46138f629e90843285` |
| `run-context.json` | `99cb76dc095959db031c348611ae1a32233035bb845a93d7aa56e63087656218` |
| `summary.env` | `46b7318a54bfe76f051009f5e9aa5e8a69cc2eb3018c370442409995c2e869bd` |
| `cleanup.env` | `5524f0c68afb6ff1d13358f8fa3d2d19f7c1270454f2dfed1d2fe72cfe334ff1` |
| `sensitive-scan.env` | `b4f3df2931ee6b0b1e529d0e267242eb0b2d3b5e5ab71498b1bdbbab98674701` |
| `toolchain-receipt.json` | `a8a6655a5a2a97bdf684433dcc5e19d098b95659e55c4d6453ca783d815bc5cd` |
| `child-lifecycle.json` | `f46e3bb7dc869f14f359d118403c161b8973c0fd7f463882fbe8fbc4bdc17ea1` |
| `model-gate.env` | `3730d015cf0ba0a48e476ed50104f89cdc21683b0d58bce2a0dd45b6e5c8583e` |
| `class-universe.json` | `b48cae966171e2b97b17ecbb4b04ee0721967cf29f6238b8089e6cb5387e1faf` |
| `report-inventory.json` | `58a28d89240230a5b6dd1df0466c6adfa6e341c6a0460fb190b8d26b726aee72` |
| `exec-manifest.json` | `dd9b53bbf14e3f0b5f38c95dd33ea4eb77aeb7508ce0098b06430c28f5f83f18` |
| `report/jacoco-aggregate.exec` | `2e24b026b7a188187d5e6a48a2da543046273a6c09b602d397ea7824edeab791` |
| `report/aggregate-provenance.json` | `9791867c76eccaad0e465d1ab08c0e525753a1ba1e56bdbfa27bb4ea93e5fae2` |
| `report/report-provenance.json` | `86f9e3a666e636996bbd26adf8cf5ff468565ab50ccee10e23d53475a49866a1` |
| `report/jacoco-aggregate/jacoco.xml` | `d5f3a22e8ce7f37e1744e03802f258c042ed23b2665392450afb4fd195605381` |
| `coverage-observation.json` | `7a02bfaaec7d6d1afeac4a5cff20c708fb8ff1092185c25a31ac588b6845dd76` |

## Exact r18 / r16 delta

r16 reviewed diagnostic 的 aggregate exact observation 是 line=`54,624/76,830`、
branch=`26,111/44,870`；r18 分母与 production class tree 不变，但 line=
`54,622/76,830`、branch=`26,107/44,870`，即 `-2 covered line / -4 covered branch`。
逐 class 比较后只有以下两类变化：

| Production class | r16 line | r18 line | r16 branch | r18 branch | Delta |
|---|---:|---:|---:|---:|---:|
| `BaselineRatioCalculator` | `98/120` | `96/120` | `57/88` | `54/88` | `-2 line / -3 branch` |
| `ResultShaper` | `109/115` | `109/115` | `54/63` | `53/63` | `0 line / -1 branch` |

r16 与 r18 两个 commit 之间这两个 production source byte-identical；class universe 都是
`24/2,098`，class tree SHA-256 都是 `a72007a8...99be`。变化不是 denominator、exclusion、
production bytecode 或报告库存漂移。

## Source-line localization

| Source | Line | Semantics | r16 | r18 | Exact loss |
|---|---:|---|---:|---:|---:|
| `BaselineRatioCalculator.java` | 76 | `val == null`，识别 NULL column-axis member | branch `2/2` | branch `1/2` | `-1 branch` |
| `BaselineRatioCalculator.java` | 77 | `hasNull = true` | line covered | line missed | `-1 line` |
| `BaselineRatioCalculator.java` | 78 | NULL column 后退出 axis tuple 扫描 | line covered | line missed | `-1 line` |
| `BaselineRatioCalculator.java` | 82 | `if (hasNull) continue`，NULL tuple 不进入 first/last domain | branch `2/2` | branch `1/2` | `-1 branch` |
| `BaselineRatioCalculator.java` | 209 | NULL coordinate 的 key fallback | branch `2/2` | branch `1/2` | `-1 branch` |
| `ResultShaper.java` | 91 | NULL row-axis member 映射到 `__null__` tree node | branch `2/2` | branch `1/2` | `-1 branch` |

这六行完整解释 aggregate `-2/-4`。现有 `PivotSqlParityIT` 的 S12 使用真实 LEFT JOIN
结果做 baseline parity，但没有自己构造或断言 unmatched row/column axis member；NULL 路径是否
被命中仍可能取决于其他测试共享状态或某一 lane 的 fixture 数据。r16 命中而 r18 未命中，且两次
业务断言均绿色，证明 coverage high-water 依赖 incidental data path，而不是确定性自动化 oracle。
这是一项 blocker 级测试证据缺口；当前证据不证明 production regression，也不允许下调阈值。

## Cleanup and external restoration

runner-owned cleanup receipt 证明 run-owned container/volume/network=`0/0/0`。runner 完整退出后，
外层编排 marker 为 `runner_rc=0 / restore_rc=0`，并按开跑前绑定恢复四个 exact demo database
container。以下均为 evidence-window 外部观察，不伪装成 `target/` 内 runner-owned artifact：

| Service | Port | Exact container ID | Observed state |
|---|---:|---|---|
| MySQL 5.7 demo | 13306 | `0c50bf7e8684950ee1f9c3c257d3b2a8ba1ace8fbc85f2aa57fd35ff0fe5c166` | running / healthy |
| MySQL 8 demo | 13308 | `f603e63eaf82f0c2243bef89d68cfe4ff59b60caf93dc4dbf44ea6f183c7816a` | running / healthy |
| PostgreSQL demo | 15432 | `a4d6a278b0b99ff6a427b1870f24e7ebef4958341af327053eae76d4eb681d07` | running / healthy |
| SQL Server demo | 11433 | `7881fa53039f1251ddd7ae8c43fd3cff549174a2cbaf07b4247c57495b8bf1fd` | running / healthy |

## Required next chain

- [x] r18 all-lane PASS、public validation、source seal、cleanup 与 external restoration 已封存；
- [x] r18/r16 exact delta 已定位到 2 个 class、6 个 NULL-axis source line；
- [x] r18 threshold candidate 保持 absent，Cfreeze/formal 未获授权；
- [x] 在既有测试节点内建立 deterministic NULL row/column axis correctness oracle；
- [x] 三次 fresh JVM/JaCoCo focused 均 `1/F0E0S0`，两类 probe bitmap `3/3 identical`；
- [x] pre-Cdiag implementation quality=`PASS / 0/0/0/0`，只授权新的 commit/push/clean Cdiag；
- [ ] replacement fresh diagnostic 完整 PASS，aggregate 不低于 r16 reviewed high-water；
- [ ] replacement candidate/public verification/independent review 通过；
- [ ] Cfreeze 作为新 Cdiag 的 direct-single-parent child commit/push；
- [ ] fresh formal PASS 后再进入 final implementation quality、coverage audit 与 acceptance。

Current `can_freeze_r18=no`、`can_enter_formal=no`、`can_enter_coverage_audit=no`、
`can_enter_acceptance=no`；formal、Step 4 exit 与 Step 5 remain pending。
