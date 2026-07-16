---
evidence_type: failed-diagnostic
version: 9.3.4
step: 4
run_id: step4-coverage-20260717-diagnostic-r10
tested_commit: 47e0c027cd205a49d40db400ba26b99e6f97d60e
status: failed
failure_class: sensitive-scan-authorization-context-false-positive
decision: excluded-from-step4-exit
recorded_at: 2026-07-17
---

# Step 4 coverage diagnostic r10 sensitive scan fail-closed evidence

## Decision

`step4-coverage-20260717-diagnostic-r10` 从 clean/pushed
`HEAD == origin/main == 47e0c027cd205a49d40db400ba26b99e6f97d60e` 启动。所有 required
lane、Step 4 report inventory、`23 exec / 48 sessions`、aggregate exact union、coverage
observation、source-after seal 与 run-owned resource cleanup 均已完成，随后在
`sensitive-scan` 阶段以 exit code `1` fail closed。

扫描器只在 `run.log` 发现一处由 demo 日志复用 credential label 造成的误报；它在写入
`sensitive-scan.env` 之前失败，`summary.env` 同样 absent。因此 r10 的结论是
`excluded-from-step4-exit / non-reusable`：不得把它的 lane、exec、aggregate 或 coverage
observation 与后续 run 拼接，也不得用它冻结 threshold。

## Passed boundary before failure

同一个 outer authority window 内已完成：

- Unit：`681 executions + 55 structural / 4,941 testcase / F0E0S0`；MySQL 5.7
  fixture negatives=`36/36`，cleanup=`0/0/0`；
- Integration：`6 variants / 47 executions + 4 structural / 320 testcase / F0E0S0`；
- database：`7 variants / 29 reports / 370 testcase / F0E0S0`，state negatives=
  `18/18`、report negatives=`14/14`；
- external：`7 variants / 16 reports / 76 testcase / F0E0S0`；
- Addon companion：`2 reports / 6 testcase / F0E0S0`，继续与 required union 分离；
- Step 3 required：`45 reports / 446 testcase / F0E0S0`，synthetic negatives=
  `17/17`；
- Step 4 report inventory：required=`773 positive + 59 structural / 5,707 testcase /
  F0E0S0`，Addon companion=`2/6`，report inventory negatives=`30/30`；
- execution inputs：`23/23 exec / 48 sessions / 16,947 unique JaCoCo class-ID
  identities`；exec scope/aggregate negatives=`17/17`；
- fresh class universe：`24 modules / 2,098 production bytecode classes`；aggregate report
  universe=`24 groups / 2,066 reportable classes`；
- aggregate exact union：`23 inputs / 48 sessions / 16,947 class-ID identities`，raw exec、
  merged exec、XML 与 provenance 均已生成；
- coverage observation：aggregate line=`54,478/76,830`（`70.9071977092%`），branch=
  `25,980/44,870`（`57.9006017384%`）；critical classes=`12`，candidate floor 以下=`9`，
  not-applicable metrics=`1`；XML negatives=`9/9`。

这些是定位失败边界的同 run partial evidence，不构成 Step 4 exit、threshold freeze 或 formal
证据。`coverage-observation.json` 明确记录 `thresholds_frozen_by_observe=false`。

## Sensitive-safe failure evidence

只记录脱敏形态，不保存或复述原始匹配文本：

| Field | Value |
|---|---|
| matched file | `run.log` |
| match count | `1` |
| producer | `DemoSecurityIdentityResolver` identity-source diagnostic |
| redacted shape | `authorization=<NON_CREDENTIAL_CONTEXT>` |
| exact matched substring SHA-256（不含换行） | `d375d94172c0dbded90d08b61f8425e5bb8ed28d8b43141ef3e7cacc80d06c59` |
| whole `run.log` SHA-256 | `9407de21dd8e421d0772495d34ede0cc122d5cf650324e8fcc219c44a763d2c0` |

该值只描述 identity resolution result，不含认证 scheme、token、secret 或 credential
payload；但 producer 使用了保留的 credential-shaped `authorization:` label。扫描器按既有
fail-closed 契约把任意非 null authorization 字段判为敏感，行为正确；修复应消除 producer
标签碰撞而不是放宽规则。扫描器只输出 matched file path 后调用 outer `fail`；没有新增包含
原始 match 的 durable artifact。

## Immutable result and absence boundary

- outer：`failed / exit_code=1 / last_phase=sensitive-scan`；
- tested commit：`47e0c027cd205a49d40db400ba26b99e6f97d60e`；
- started/finished：`2026-07-16T19:30:01Z` / `2026-07-16T20:34:41Z`；
- source-before SHA-256=source-after SHA-256=
  `7c60f5ce6a7174487d77ea64881be37d68408129bf3c570972918a818a04a000`；
- `sensitive-scan.env=absent`，`summary.env=absent`；
- coverage gate、threshold candidate 与 final/candidate manifest：全部 absent；
- run-owned container/volume/network residue=`0/0/0`。

关键 artifact SHA-256：

| Artifact | SHA-256 |
|---|---|
| `run-status.env` | `f7dc8e819ec565e01719751faeb364530d92d2149f1e480137834ada227bfecb` |
| `cleanup.env` | `5524f0c68afb6ff1d13358f8fa3d2d19f7c1270454f2dfed1d2fe72cfe334ff1` |
| `run.log` | `9407de21dd8e421d0772495d34ede0cc122d5cf650324e8fcc219c44a763d2c0` |
| `report-inventory.json` | `74b9ff26c826c27e6301691f3690f1a2a32a15cbce65baaabd4f5868c6bd4b7e` |
| `child-lifecycle.json` | `2690383b0216e987566cf2d39e07e2ad3a519a66db637c83496f8342e7788860` |
| `toolchain-receipt.json` | `af71795045f2c79f670d4ba05a8b4fa4b67fae364027ee588253ae18d675af5b` |
| `class-universe.json` | `d053afea415f39608fa22b8e43a63c9980f4bd3e19f4d7ea4d927ed73e80d6c6` |
| `exec-manifest.json` | `1fec21e697bc6acc702495a059ee491aa0854012dd19f4eacb69dd0b0ca1878b` |
| `report/jacoco-aggregate.exec` | `51d7365edb9859f54baeb74a6fd4e8371dd0fccacd2a61cda600edca03ba9c46` |
| `report/jacoco-aggregate/jacoco.xml` | `b5b883772541bda70f84d1cfdc4e0a7a849ceca165fc0e11fe51db6c4b3d0d51` |
| `report/aggregate-provenance.json` | `4e39142014fea625102cd1b7bbf4f1169b1f68a0842a72a1fb7831ece5d75a7f` |
| `report/report-provenance.json` | `0ec5111511da13253b4ee40767126dff804148d4a9ba5f88564f2dde155e1385` |
| `coverage-observation.json` | `4838694b36a271314319a7c7eae0a3779090c53d257a4bbf99f081f2ab411195` |
| `run-context.json` | `2481d490cdea05037d15a700eff7a752ee86d7e1c09fe4b832d5cf376dfa421b` |

Canonical ignored evidence root：

- `target/v934-step4-coverage/runs/step4-coverage-20260717-diagnostic-r10/`。

## Cleanup and external restoration

outer finalizer 证明 r10 run-owned container/volume/network=`0/0/0`。完整退出后，四个 repo
demo DB exact container 在 evidence window 外按原 ID 恢复，复核均为 `running/healthy`：

| Container | Port | Exact container ID |
|---|---:|---|
| `foggy-demo-mysql` | `13306` | `0c50bf7e8684950ee1f9c3c257d3b2a8ba1ace8fbc85f2aa57fd35ff0fe5c166` |
| `foggy-demo-mysql8` | `13308` | `f603e63eaf82f0c2243bef89d68cfe4ff59b60caf93dc4dbf44ea6f183c7816a` |
| `foggy-demo-postgres` | `15432` | `a4d6a278b0b99ff6a427b1870f24e7ebef4958341af327053eae76d4eb681d07` |
| `foggy-demo-sqlserver` | `11433` | `7881fa53039f1251ddd7ae8c43fd3cff549174a2cbaf07b4247c57495b8bf1fd` |

该恢复不是 runner 接管 ambient listener，不属于 run-owned cleanup receipt。

## Next gate

- [x] 封存 r10 failure、单一脱敏命中、absence boundary、cleanup 与 external restoration；
- [x] 登记 `BUG-934-STEP4-SENSITIVE-SCAN-AUTHORIZATION-CONTEXT-FALSE-POSITIVE`；
- [x] producer 改为非 credential-shaped demo identity 日志，五条扫描规则保持不变且禁止按
      path/class/具体值 whitelist；
- [x] 添加 `7` 个危险 fixture、`3` 个安全 fixture 与 `rg rc>1` fail-closed 回归，证明真实
      env/secret/token/header/URI/CLI password 仍 fail closed；
- [ ] 完成 focused regression、contract mutation 与实现质量复核；
- [ ] commit/push 并证明 clean `HEAD == origin/main`；
- [ ] 使用全新 run ID 完成 fresh r11 all-lane diagnostic；
- [ ] 仅在 fresh diagnostic 成功后冻结 exact-observed threshold，再执行 fresh formal、最终质量、
      coverage evidence audit 与 acceptance。

当前 threshold=`diagnostic-pending`，`can_enter_coverage_audit=no`；r10 不可复用，Step 4 未
通过，Step 5、formal、coverage audit 与 acceptance 保持关闭。
