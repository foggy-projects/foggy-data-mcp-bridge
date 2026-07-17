---
evidence_type: failed-diagnostic
version: 9.3.4
step: 4
run_id: step4-coverage-20260717-diagnostic-r17
tested_commit: 316a71f753827f8f34063b0eb0669271f696c5ee
status: failed
failure_class: unit-mysql57-final-mysqld-handoff-readiness
decision: excluded-from-step4-exit
focused_repro_id: step4-unit-lifecycle-repro-20260717-r1
focused_repro_status: passed-non-authority
focused_current_run_id: step4-unit-lifecycle-handoff-current-20260717-r2
focused_current_status: passed-non-authority
root_cause_status: confirmed
remediation_status: quality-passed-replacement-cdiag-pending
recorded_at: 2026-07-17
---

# Step 4 diagnostic-r17 Unit MySQL final mysqld handoff fail-closed evidence

## Decision

fresh diagnostic `step4-coverage-20260717-diagnostic-r17` 在 clean/pushed Cdiag
`316a71f753827f8f34063b0eb0669271f696c5ee` 上于 Unit child fail closed：outer=
`last_phase=child-unit / exit 1`，Unit=`last_phase=unit-mysql57-lifecycle-negative / exit 1`。
r17 永久标记为 `excluded-from-step4-exit / non-reusable`；不得生成 threshold candidate，不得参与
freeze，也不能证明此前 QueryModel DCL deterministic remediation 已通过 all-lane diagnostic。

## Exact failure

Unit lifecycle 前两个真实 probe 已到达 callback，并分别按 INT/TERM 以 `130/143` 退出且完成清理。
第三个 `signal-hup` probe 的 child 为 `unit-mysql57-49a76dbc70c3bd98`：

- pinned MySQL 5.7 image/digest、fresh project/volume、`127.0.0.1:13306` 与
  `foggy_test|5.7.44-log` identity 均已验证；
- cell `status.env` 为 `last_phase=fixture-first-apply / exit_code=1 / cleanup_status=passed`；
- `fixture-first.txt` 与 `unit-lifecycle-ready.env` 均 absent，说明 callback 尚未开始；
- `callback-failure` 与 `leader-kill-fallback` 两个后续 probe 未执行；
- canonical `mysql57-fixture-lifecycle-negative.json`、fixture manifest/negative、Unit TEST XML 与
  JaCoCo exec 均 absent。

r17 `run.log` 中失败后的两个 `status=passed` JSON 只来自 Unit EXIT trap：第一个是
`cleanup-lifecycle` 对五个派生资源名的零残留检查，第二个是预创建 main fixture scope 的 fallback
cleanup。它们不表示 lifecycle `5/5` 或正常 Unit fixture 通过。

## Confirmed root cause

原 run-owned authority Compose 继承 stock `mysqladmin ping` healthcheck。该探针也会在 MySQL 官方
entrypoint 的 temporary initialization server 上成功。父 provisioner 随后只等待 business identity 与
四条 `preagg_watermark`；这些 watermark 在最后 init SQL
`11-preagg-testdata.sql:414-425` 已可见，而同一脚本在 `428-442` 仍有工作，entrypoint 之后还要停止
temporary server，再 `exec` final PID 1 `mysqld`。因此 watermark 可见不等于 final-server handoff 已完成。

使用当前未修复配置运行 fresh `step4-mysql57-handoff-red-20260717-r1`，直接捕获到：

- samples `15..40`：Docker health=`healthy`，但 `/proc/1/comm=docker-entrypoi`；
- sample `41`：才切换为 health=`healthy`、`/proc/1/comm=mysqld`；
- exact run-owned container/volume/network 清理完成，原 demo MySQL57 精确 ID 恢复成功。

该 RED 直接证明 premature-healthy 窗口存在，且与 r17 在 readiness 后、callback 前的
`fixture-first-apply` rc=1 完全一致。r17 launcher 将 provisioner stdout/stderr 指向 `DEVNULL`，因此证据
不能再声称具体是 12/13/14 哪一条 SQL 或哪个 MySQL errno；这不影响 handoff readiness 根因成立。

## Focused control and remediation boundary

未改代码时的独立 control `step4-unit-lifecycle-repro-20260717-r1` 为 `5/5` PASS，expected/actual
exit=`130/143/129/17/137`，每项 cleanup=`0/0/0` 且 port free，receipt SHA-256=
`c1e6033a2fd22db3975860ba3639646be341be632a318e9f76952f530f23e748`。该单次绿色只说明竞态没有
每次命中，不覆盖 r17，也不是 Step 4 authority。

remediation 限于测试证据基础设施：

1. Step 4 declared amendment 在 run-owned authority override 中让 MySQL5.7/8 healthcheck 同时要求
   `/proc/1/comm=mysqld` 与原 ping；冻结 Step 3 provisioner 不改。
2. provisioner 仍在 health 之后验证 business identity 与四条 watermark，形成
   `final PID1 + ping + identity + fixture marker` 的组合 gate。
3. Unit lifecycle launcher 对每个 probe 使用 run-owned no-clobber combined log；unexpected failure
   保留，成功 probe 删除，不改变成功 receipt schema。
4. patched MySQL57 对称 GREEN 首次 healthy 即为 `PID1=mysqld`（sample `42`），premature healthy=`0`；
   patched MySQL8 对称 GREEN 首次 healthy 同样为 `PID1=mysqld`（sample `124`），premature
   healthy=`0`。两轮 cleanup/restore 均成功。
5. 修复后旧 launcher 字节使用三个唯一 run ID 完成真实 lifecycle=`15/15`：
   `step4-unit-lifecycle-handoff-green-20260717-r1/r2/r3` receipt SHA-256 分别为
   `8a3900678a718a2df5b604854ad43a5622273128b94f18413ddc6a5979fdf5f2`、
   `eb41ee0675c6c2677de708390125e1095178772e4849aece332be3eb89bb6da4`、
   `dca7439a316899c064f231411ee4a48e052c0e1b1f28d25da5c8041ca8ddd48a`；每轮均为
   INT/TERM/HUP/callback-failure/leader-kill `5/5`，wrapper restore=`runner_rc=0 / restore_rc=0`。
6. diagnostics 加固的 penultimate 字节再以
   `step4-unit-lifecycle-handoff-current-20260717-r1` 完成 `5/5`，receipt SHA-256=
   `159fbe80595933e29f05f13c0f1d82e9b65d7f947dddec253477f0b3f3876799`；成功 probe 的
   `provisioner.log` 全部 absent，run-owned residue=`0/0/0`、demo restore=`0/0`。随后又增加
   callback OSError 与成功日志存在性诊断，因此该 receipt 不得冒充 latest current bytes；最新字节
   已由 `step4-unit-lifecycle-handoff-current-20260717-r2` 再完成 `5/5`，receipt SHA-256=
   `e3bad41ad9ec634c1702ffe20f4c0ddbff3050a227956e2ba9054a11f6b606c7`；所有成功 probe 的
   `provisioner.log` absent，demo exact restore=`runner_rc=0 / restore_rc=0` 且 healthy/listening。
7. 当前静态回归均 PASS：successor overlay negative=`12/12` / SHA-256=
   `cf98e3306fcf5650ca4aad9475beb0e986b5363d15e4f3d326568753edd92e06`；Unit fixture
   negative=`36/36` / SHA-256=
   `a5620aa80ac122a9489b14f8fc5352bf685c61e2fcd2426fdadfd36fb882212d`；coverage
   negative=`27 + source/Git 22 + replay 12` / current r2 SHA-256=
   `0f8f5c7bbd6b8fcf18363f979b9948bd396b39b436f94b30c1ca697204fc6856`。

上述 focused/static 证据已验证 current handoff 修复字节，但没有形成完整 Unit PASS：full Unit 必须由新的
clean/pushed replacement Cdiag 上 fresh diagnostic 证明。pre-Cdiag formal implementation quality 已
`PASS / 0/0/0/0`，record=
`docs/9.3.4/quality/step4-diagnostic-r17-recovery-implementation-quality.md`；replacement Cdiag 与
fresh all-lane diagnostic 尚未完成，因此 remediation 与 Step 4 仍为 `in-progress`。

## Missing success-only evidence

以下 r17 success-only artifact 均 absent，必须保持 absent：

- outer `source-after.tsv`、`sensitive-scan.env`、`model-gate.env`、`child-lifecycle.json`；
- `report-inventory.json`、`exec-manifest.json`、aggregate exec/XML；
- `coverage-observation.json`、`summary.env`、coverage gate；
- threshold candidate/review、candidate/final manifest。

`validate-diagnostic-run` 对 r17 正确 fail closed：`E_RUN_SOURCE: cannot strictly read source-after
inventory`。

## Immutable artifact hashes

| artifact | SHA-256 |
|---|---|
| outer `run-status.env` | `5dbb27053fd9a5c58a98a96edc20a2c2986ce64816c2cfc182a19705ddcec38e` |
| outer `run.log` | `d922e8737cb57defc057414ec4d193ffcbe22597e08ebf3e0180dda00fc00172` |
| outer `run-context.json` | `f042d8263510856c1926c204001e43b4a2d3d85a5d532a1c0a83d05044700fed` |
| outer `source-before.tsv` | `e9e8f156f4f582d4fe6b0ed2cb0b7416968d1130d81034ced731d62173c149f8` |
| outer `toolchain-receipt.json` | `84e54fc72ebfe27020cab40b30185f83d02ad6b324f66d76d31e0af98638d27f` |
| outer `class-universe.json` | `2b5ce566b27231dfb7c1d792e6403437bff6469b9540c973a084bee29b519dc1` |
| outer `child-ready/unit.json` | `ec9b96a4762c52527d8097e3cf735da947e390409fc6db332422b01e2e7f4838` |
| outer `cleanup.env` | `5524f0c68afb6ff1d13358f8fa3d2d19f7c1270454f2dfed1d2fe72cfe334ff1` |
| Unit `run-status.env` | `96870d8fa8b970349de448e6ec6af44dd68e324edcaed6d718f3d33084ad4f11` |
| Unit `run.log` | `aba41adc6f29e47c8f5175c0dd168907352a6f8d102f6b4811a0d1c701c3e922` |
| HUP cell `status.env` | `77dff66806e4fce269827ea90fce79af8d78b921d394cd313e7c6d7cee3fdd8a` |
| HUP cell `cleanup.env` | `bb44c7ab896159d1e3a06e8178f39d7c30aad21f86914169f93c1e51fe12d09b` |

## Environment restoration and next gate

r17 outer wrapper=`runner_rc=1 / restore_rc=0`。四个原 demo database container 精确 ID 均已恢复为
`running/healthy`，`13306/13308/15432/11433` 均监听；r17 自有 residue=`0/0/0`。

下一许可链只剩：replacement Cdiag commit/push/clean -> fresh diagnostic。只有 fresh diagnostic PASS
后才允许 candidate/review，再形成该 Cdiag 的 direct-child
Cfreeze 并运行 fresh formal。当前 machine=`diagnostic-ready/diagnostic-pending`；Step 5、coverage
audit 与 acceptance 全部保持关闭。
