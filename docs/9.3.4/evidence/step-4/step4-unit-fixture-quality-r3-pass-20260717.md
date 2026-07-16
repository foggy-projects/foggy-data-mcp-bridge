---
version: 9.3.4
step: 4
record_kind: unit-remediation-evidence
recorded_at: 2026-07-17
run_id: step4-unit-fixture-quality-20260716-r3
tested_commit: 50161a0a869430e353f3933d9bb00dda59d9c4b1
decision: passed-unit-remediation-subgate
step4_exit: false
can_enter_coverage_audit: false
---

# Step 4 Unit fixture quality r3 pass

## Decision

`step4-unit-fixture-quality-20260716-r3` 是 profile-scoped datasource repair 后的
fresh 完整 Unit replacement authority。该 subgate 通过；它证明 Unit remediation 可以进入
正式实现质量闸门，但不等于 Step 4 all-lane diagnostic、threshold freeze、formal、coverage
audit 或 Step 4 exit。

## Immutable identity

- tested commit：`50161a0a869430e353f3933d9bb00dda59d9c4b1`；
- source seal：`3,982 files`，before=after=
  `1db06cc18bb86c288ffa79e7094e3b9e509d866ddc23b6c93bcaaa0422b99eb2`；
- outer run：`step4-unit-fixture-quality-20260716-r3`；
- fixture run：`unit-mysql57-f2e5eb122fdc7d28`；
- project：`v934db-mysql57-faa645a69d5c`；
- image：`mysql@sha256:4bc6bc963e6d8443453676cae56536f4b8156d78bae03c0145cbe47c2aad73bb`；
- database identity：`foggy_test|5.7.44-log`；host port=`13306`。

## Executed result

- 唯一 Surefire Maven invocation：`681 positive execution + 55 structural = 736 raw
  reports`；
- testcase：`4,941`；failures/errors/skipped=`0/0/0`；
- final report inventory 与 frozen Unit successor 精确一致，无 missing/extra；
- run status=`passed`，fixture status=`passed`；source-before/source-after 相同。

## Fixture and connection receipt

- `M_ETL_TEST` before/after SHA-256 均为
  `93a9a8d51c8e8188173ce905965293adbd163e2d1e21c12d2f1f8637bbe4da0d`，
  row count=`0`；
- connection receipt：`observation_scope=unit-maven-invocation`、
  `observation_closed=true`、status=`passed`；
- closed window 内记录 `18` 条连接，connection id=`21..38`，全部 observed user=
  `v934_unit@172.29.0.1`；callback 返回后的 provisioner `foggy` 控制连接位于该窗口外；
- fixture negatives=`36/36`：原 fixture/manifest=`20/20`、connection typed=`7/7`、
  atomic publisher=`3/3`、profile boundary=`6/6`；negative receipt schema/tamper 另为
  `4/4`；
- 真实 lifecycle=`5/5`：INT/TERM/HUP=`130/143/129`、callback failure=`17`、leader-kill
  fallback=`137`，每项 cleanup 均为 `0/0/0` 且 port free。

## Cleanup and external restoration

- evidence window 结束时 run-owned container/volume/network=`0/0/0`，`13306=free`；
- evidence window 外恢复原演示 MySQL exact container
  `0c50bf7e8684950ee1f9c3c257d3b2a8ba1ace8fbc85f2aa57fd35ff0fe5c166`；复核为同一
  container id、`running/healthy`；
- worktree source 未被运行修改，完成后 `git status --porcelain` 为空。

## Evidence hashes

| Artifact | SHA-256 |
|---|---|
| `run-status.env` | `047d5563ca55fb1270d9110b9290f031a44bc5e8b628604b2131f46bdd98e9f0` |
| `summary.env` | `52608dd67c2a1045f6289580f9230fb08724433ecc1155ce60c3ea4a0769d135` |
| `final/report-manifest.json` | `3b6a06d77b530f87f68d9e4583ca2d0c1a86d9fbff7fd37312c147583a8871b9` |
| `mysql57-fixture-manifest.json` | `45b6e71ba64303e043248a994cf7d839009e41afa804ba7d5969d772c97b3c48` |
| `mysql57-fixture-negative.json` | `a5620aa80ac122a9489b14f8fc5352bf685c61e2fcd2426fdadfd36fb882212d` |
| `mysql57-fixture-lifecycle-negative.json` | `7124ab9c9c022b935a3579c6fe006c79e0ae78b15d7af6ae2fd0221b34421ff2` |
| `unit-connection-receipt.json` | `e4c65660af140f60564cc779133c92dcf06d6ea67d5e5f2511aece45edd5f622` |
| fixture `status.env` | `38098bbe826d66b9223d53051e13e676d764cac903748ebf8b496f42d1608900` |
| fixture `cleanup.env` | `04d08071601d088e847c363f3014495e3c671d29a20d23253d6540d4edfd0967` |
| outer `cleanup.sentinel` | `7f894e72345f172a5f660049e16644d5ae105a6dc8b0bbe21bacc606e773942b` |

Canonical evidence roots：

- `target/v934-step2-unit/runs/step4-unit-fixture-quality-20260716-r3/`；
- `target/v934-step3-database-matrix/runs/unit-mysql57-f2e5eb122fdc7d28/cells/mysql57/`；
- `target/v934-step4-coverage/runs/step4-unit-fixture-quality-20260716-r3/`。

## Next gate

- [x] fresh Unit remediation r3；
- [x] formal remediation implementation quality，B/H/M/L=`0/0/0/0`；
- [ ] commit/push，并证明 clean `HEAD == origin/main`；
- [ ] fresh Step 4 all-lane r8 diagnostic；
- [ ] exact observed threshold freeze、fresh formal 与最终实现质量；
- [ ] test evidence coverage audit 与 9.3.4 acceptance。

当前 `can_enter_coverage_audit=no`；Step 5 保持关闭。
