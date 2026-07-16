---
evidence_type: failed-diagnostic
version: 9.3.4
step: 4
run_id: step4-coverage-20260717-diagnostic-r9
tested_commit: a0466ec04c51c436413e85836a7dee6153e18010
status: failed
failure_class: jacoco-execution-class-identity-scope-drift
decision: excluded-from-step4-exit
recorded_at: 2026-07-17
---

# Step 4 coverage diagnostic r9 exec class scope fail-closed evidence

## Decision

`step4-coverage-20260717-diagnostic-r9` 从 clean/pushed
`HEAD == origin/main == a0466ec04c51c436413e85836a7dee6153e18010` 启动。Unit、
Integration、Step 3 required、Step 4 report inventory 与 23 份 JaCoCo exec 均完成，随后
在 `coverage-report` 的 exec inventory 校验中以 `E_CLASS_ID_MISMATCH` fail closed。

该错误发生在 `exec-manifest.json` 发布之前；`source-after.tsv`、aggregate exec/XML、
coverage observation、threshold candidate 与 `summary.env` 均未生成。因此 r9 的结论是
`excluded-from-step4-exit / non-reusable`，不得把它的 lane 绿色与后续 run 拼接，也不得用
它冻结阈值。

## Passed boundary before failure

同一个 outer authority window 内已完成：

- Unit：`681 executions + 55 structural / 4,941 testcase / F0E0S0`；MySQL 5.7
  fixture negatives=`36/36`，cleanup=`0/0/0`；
- Integration：`6 variants / 47 executions + 4 structural / 320 testcase / F0E0S0`；
- database：`7 variants / 29 reports / 370 testcase / F0E0S0`，state negatives=
  `18/18`、report negatives=`14/14`；
- external：`7 variants / 16 reports / 76 testcase / F0E0S0`，Redis/Mongo/MySQL/
  Milvus required lanes 与 negatives 全部通过；
- Addon companion：`2 reports / 6 testcase / F0E0S0`，继续与 required union 分离；
- Step 3 required：`45 reports / 446 testcase / F0E0S0`，synthetic negatives=
  `17/17`；
- Step 4 report inventory：required=`773 positive + 59 structural / 5,707 testcase /
  F0E0S0`，Addon companion=`2/6`；
- execution inputs：ledger 要求的 `23/23 exec` 与 `48 sessions` 均已生成；fresh class
  universe=`24 modules / 2,098 production classes`。

这些是定位失败边界的同 run 证据，不构成 Step 4 exit 或 threshold freeze 证据。

## Failure and root cause

原 verifier 先按 class binary name 聚合所有 exec 中的 ID，再要求每个同名类只有一个 ID：

```text
[v934-coverage-exec] ERROR E_CLASS_ID_MISMATCH: class IDs differ across exec files
```

该规则错误地把 class name 当成 JaCoCo execution identity。JaCoCo 的 execution data 以
CRC64 class ID 为主身份；同名不同 ID 可以同时存在，例如不同依赖版本、不同 classloader
以及每个 Spring context 生成的 CGLIB bytecode。只有进入冻结 24 模块生产 class tree 的
名称，才必须与当前 production `.class` 的 JaCoCo ID 精确一致。

对 r9 的 23 份 raw exec 使用同一 inspector 做只读重算：

- class records=`106,546`；
- unique class names=`16,693`；
- unique `(name,id)` / JaCoCo execution identities=`16,939`；
- 同名多 ID=`135`：Spring CGLIB=`41`、Foggy test class=`1`、第三方/依赖=`93`；
- frozen production class intersection=`2,098`，其中同名多 ID=`0`。

因此只加 `*$$SpringCGLIB$$*` exclusion 仍会留下 94 个冲突，也会把身份判定交给不可靠
的名称 glob。正确修复边界是：raw exec 与 aggregate 保留全部 class ID；跨 exec 的生产
class-ID 一致性只由 frozen production class universe 决定；aggregate exact union 按
JaCoCo class ID 合并，同 ID 仍严格校验 name/probe shape 与 bitmap OR。

## Immutable result and absence boundary

- outer：`failed / exit_code=1 / last_phase=coverage-report`；
- tested commit：`a0466ec04c51c436413e85836a7dee6153e18010`；
- source-before SHA-256=
  `3e07c3de0a7c804d2aa4c36b64bfb0c8c2f2d5910f54c335376bbe206c8b49b4`；
- `source_after_sha256` 为空，`summary_sha256=absent`；
- `exec-manifest.json`、`report/jacoco-aggregate.exec`、aggregate/report provenance、
  aggregate XML/HTML、coverage observation、coverage gate、candidate/final manifest：全部
  absent；
- run-owned container/volume/network residue=`0/0/0`。

关键 artifact SHA-256：

| Artifact | SHA-256 |
|---|---|
| `run-status.env` | `7c50cafd8f28716db7ad996b31204934eaa6ae04804eb455bc442bb7e72070de` |
| `cleanup.env` | `5524f0c68afb6ff1d13358f8fa3d2d19f7c1270454f2dfed1d2fe72cfe334ff1` |
| `run.log` | `765fb22f81ef2e4c2aca7eaad899e365f6cbb098d5a0d87e61b5df05d90a928c` |
| `report-inventory.json` | `13dd63dcd49fc0eec445ca6b558c13b841da9ecb97a7a9ad089a8c994b8074d0` |
| `child-lifecycle.json` | `43a4046ef35bcb0c5b6bf02ed6db47a99dfc23103358652ae66269e3fce2b064` |
| `toolchain-receipt.json` | `82ba200a5ffa1a14bacb38aa75c0cb99c0d740b150db2824f23d4becbdd8acb8` |
| `class-universe.json` | `f065f06f6c6fdfed9973528557e9fb8bb536c2139c455a7d7f53c176538573ba` |
| `run-context.json` | `1b73582b7ef132ba0f4e1007ee8bc2c0ddb9b7e944c2b5002942b2d46d33c796` |

Canonical ignored evidence root：

- `target/v934-step4-coverage/runs/step4-coverage-20260717-diagnostic-r9/`。

## Cleanup and external restoration

outer finalizer 证明 r9 run-owned container/volume/network=`0/0/0`。完整退出后，四个 repo
demo DB exact container 在 evidence window 外按原 ID 恢复，均为 `running/healthy`：

| Container | Port | Exact container ID |
|---|---:|---|
| `foggy-demo-mysql` | `13306` | `0c50bf7e8684950ee1f9c3c257d3b2a8ba1ace8fbc85f2aa57fd35ff0fe5c166` |
| `foggy-demo-mysql8` | `13308` | `f603e63eaf82f0c2243bef89d68cfe4ff59b60caf93dc4dbf44ea6f183c7816a` |
| `foggy-demo-postgres` | `15432` | `a4d6a278b0b99ff6a427b1870f24e7ebef4958341af327053eae76d4eb681d07` |
| `foggy-demo-sqlserver` | `11433` | `7881fa53039f1251ddd7ae8c43fd3cff549174a2cbaf07b4247c57495b8bf1fd` |

该恢复不是 runner 接管 ambient listener，不属于 run-owned cleanup receipt。

## Next gate

- [x] 封存 r9 failure、absence boundary、cleanup 与 external restoration；
- [x] 登记 `BUG-934-STEP4-EXEC-CLASS-ID-SCOPE-DRIFT`；
- [x] 将生产 class-ID 校验限定到 frozen 24-module production universe；
- [x] aggregate exact union 改为 JaCoCo class ID identity，并保留全部 runtime/test/
  dependency execution data；
- [x] focused regression：exec scope/aggregate=`17/17`、contract mutation=`21/21`、
  XML identity/provenance=`68/68`；
- [x] 登记并实现 `BUG-934-STEP4-LIFECYCLE-SEMANTIC-VALIDATOR-BYPASS` 的 coded
  executable-stream regression；
- [x] lifecycle semantic remediation 两路独立复核与全量正式质量达到
  B/H/M/L=`0/0/0/0`；
- [ ] commit/push 并证明 clean `HEAD == origin/main`；
- [ ] 使用全新 run ID 完成 fresh r10 all-lane diagnostic；
- [ ] 仅在 fresh diagnostic 成功后冻结 exact-observed threshold，再执行 fresh formal、
  最终质量与 coverage evidence audit。

当前 threshold=`diagnostic-pending`，`can_enter_coverage_audit=no`；Step 4 未通过，Step 5、
formal、coverage audit 与 acceptance 保持关闭。
